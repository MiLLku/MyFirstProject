package io.jbnu.test;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen extends ScreenAdapter
{
    private boolean isPaused = false;
    private Texture pauseTexture;
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFont gameClearFont; // 게임 클리어용 폰트
    private OrthographicCamera uiCamera;
    private final float FORCE_MULTIPLIER = 100.0f;
    private final Box2DDebugRenderer box2DDebugRenderer;
    private final World world;
    private final Body player;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer;

    private boolean isDragging = false;
    private final Vector3 touchStartPos = new Vector3();
    private final Vector2 defaultGravity = new Vector2(0, -5.0f);
    private final float MAX_DRAG_DISTANCE = 3.0f;
    private int jumpCount = 0;
    private final int MAX_JUMPS = 2;
    private Array<Body> grounds = new Array<>();
    private float nextGroundX = 5f;
    private final float MIN_GROUND_Y = 1f;
    private final float MAX_GROUND_Y = 6f;
    private final float GROUND_HEIGHT = 0.5f;
    private final float GENERATE_DISTANCE = 0f;
    private final float REMOVE_DISTANCE = 5f;
    private int score = 0;
    private int stage = 1;
    private final int SCORE_PER_STAGE = 500;
    private final int CLEAR_SCORE = 2000;
    private boolean isGameClear = false;
    private final float MAX_ROTATION_ANGLE_STAGE_2 = MathUtils.PI / 8;
    private final float MAX_ROTATION_ANGLE_STAGE_3 = MathUtils.PI / 6;

    public GameScreen()
    {
        camera = new OrthographicCamera();
        float worldWidth = 20f;
        float worldHeight = worldWidth * (Gdx.graphics.getHeight() / (float) Gdx.graphics.getWidth());
        viewport = new FitViewport(worldWidth, worldHeight, camera);
        camera.position.set(worldWidth / 4f, worldHeight / 2f, 0);

        uiCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCamera.setToOrtho(false);

        world = new World(defaultGravity, true);
        world.setContactListener(new GameContactListener(this));
        box2DDebugRenderer = new Box2DDebugRenderer();
        shapeRenderer = new ShapeRenderer();

        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);

        // 게임 클리어 폰트 초기화
        gameClearFont = new BitmapFont();
        gameClearFont.setColor(Color.YELLOW);
        gameClearFont.getData().setScale(3.0f); // 폰트 크기 3배

        pauseTexture = new Texture(Gdx.files.internal("pause.png"));

        player = createPlayer();
        createInitialGrounds();
    }
    private void handlePauseInput()
    {
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE) && !isGameClear)
        {
            isPaused = !isPaused;
        }
    }

    private void createInitialGrounds()
    {
        Body startGround = Ground.createGround(world, 2, 2, 4, GROUND_HEIGHT, 0, 0.6f);
        grounds.add(startGround);
        nextGroundX = startGround.getPosition().x + 2f + MathUtils.random(1.0f, 3.0f);
    }

    public void resetJumpCount() {
        jumpCount = 0;
    }

    public void addScore(int amount)
    {
        if (isGameClear) return;

        score += amount;
        Gdx.app.log("GameScreen", "Score: " + score);

        if (score >= CLEAR_SCORE)
        {
            isGameClear = true;
            isPaused = true;
            Gdx.app.log("GameScreen", "Game Clear!");
        }

        if (!isGameClear)
        {
            int calculatedStage = (score / SCORE_PER_STAGE) + 1;
            if (calculatedStage > stage)
            {
                stage = calculatedStage;
                Gdx.app.log("GameScreen", "Stage Up! Current Stage: " + stage);
            }
        }
    }


    private Body createPlayer()
    {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(2, 5);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        float playerHalfWidth = 0.4f;
        float playerHalfHeight = 0.4f;
        shape.setAsBox(playerHalfWidth, playerHalfHeight);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;
        fixtureDef.friction = 0.7f;
        fixtureDef.restitution = 0.1f;

        body.createFixture(fixtureDef).setUserData("player");
        shape.dispose();
        return body;
    }

    private void update(float delta)
    {
        handleInput();
        world.step(1 / 60f, 6, 2);

        generateGrounds();
        removeOldGrounds();

        float targetX = player.getPosition().x + viewport.getWorldWidth() / 4f;
        camera.position.x += (targetX - camera.position.x) * 0.1f;
        camera.update();

        if (player.getPosition().y < camera.position.y - viewport.getWorldHeight() / 2f - 2f)
        {
            gameOver();
        }
    }


    private void generateGrounds()
    {
        while (nextGroundX < camera.position.x + viewport.getWorldWidth() / 2f + GENERATE_DISTANCE)
        {
            float width = MathUtils.random(1.5f, 4.0f);
            float x = nextGroundX + width / 2;
            float y = MathUtils.random(MIN_GROUND_Y, MAX_GROUND_Y);
            float angle = 0;
            float friction;

            float currentMaxAngle = 0;
            if (stage == 2)
            {
                currentMaxAngle = MAX_ROTATION_ANGLE_STAGE_2;
            } else if (stage >= 3)
            {
                currentMaxAngle = MAX_ROTATION_ANGLE_STAGE_3;
            }

            int randType = MathUtils.random(0, 99);

            if (stage >= 2 && randType < 20)
            {
                friction = Ground.FRICTION_HIGH;
                if (currentMaxAngle > 0 && MathUtils.randomBoolean(0.4f))
                {
                    angle = MathUtils.random(-currentMaxAngle, currentMaxAngle);
                }
            }
            else if (randType < 50)
            {
                friction = Ground.FRICTION_LOW;
            }
            else
            {
                friction = Ground.FRICTION_NORMAL;
            }

            if (currentMaxAngle > 0 && friction != Ground.FRICTION_HIGH && MathUtils.randomBoolean(0.3f))
            {
                angle = MathUtils.random(-currentMaxAngle, currentMaxAngle);
            }

            Body groundBody = Ground.createGround(world, x, y, width, GROUND_HEIGHT, angle, friction);
            grounds.add(groundBody);

            float minGap = 1.0f + (stage - 1) * 0.2f;
            float maxGap = 3.0f + (stage - 1) * 0.3f;
            nextGroundX += width + MathUtils.random(minGap, maxGap);
        }
    }

    private void removeOldGrounds()
    {
        for (int i = grounds.size - 1; i >= 0; i--)
        {
            Body groundBody = grounds.get(i);
            float groundRightEdge = groundBody.getPosition().x + 2.0f;
            try
            {
                if (groundBody.getUserData() instanceof Ground.GroundUserData)
                {
                    Ground.GroundUserData data = (Ground.GroundUserData) groundBody.getUserData();
                    groundRightEdge = groundBody.getPosition().x + data.width / 2f;
                }
            }
            catch (Exception e)
            {
                groundRightEdge = groundBody.getPosition().x + 2.0f;
            }

            if (groundRightEdge < camera.position.x - viewport.getWorldWidth() / 2f - REMOVE_DISTANCE)
            {
                final Body bodyToRemove = groundBody;
                Gdx.app.postRunnable(() ->
                {
                    if (!world.isLocked())
                    {
                        if (grounds.contains(bodyToRemove, true)) {
                            grounds.removeValue(bodyToRemove, true);
                            world.destroyBody(bodyToRemove);
                        }
                    }
                    else
                    {


                    }
                });
            }
        }
    }

    private void gameOver()
    {
        Gdx.app.log("GameScreen", "게임 오버! 최종 점수: " + score + ", 최종 스테이지: " + stage);
        ((Game) Gdx.app.getApplicationListener()).setScreen(new GameScreen());
    }


    @Override
    public void render(float delta)
    {
        handlePauseInput();

        if (!isPaused)
        {
            update(delta);
        }

        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeType.Filled);
        for (Body groundBody : grounds)
        {
            Fixture fixture = groundBody.getFixtureList().first();
            if (fixture.getUserData() instanceof Ground.GroundUserData)
            {
                Ground.GroundUserData data = (Ground.GroundUserData) fixture.getUserData();
                shapeRenderer.setColor(data.color);

                Vector2 position = groundBody.getPosition();
                float angle = groundBody.getAngle() * MathUtils.radiansToDegrees;
                float halfWidth = data.width / 2f;
                float halfHeight = data.height / 2f;

                shapeRenderer.identity();
                shapeRenderer.translate(position.x, position.y, 0);
                shapeRenderer.rotate(0, 0, 1, angle);
                shapeRenderer.rect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2);

            }
            else
            {
                shapeRenderer.setColor(Color.GRAY);
                Vector2 position = groundBody.getPosition();
                float angle = groundBody.getAngle() * MathUtils.radiansToDegrees;
                shapeRenderer.identity();
                shapeRenderer.translate(position.x, position.y, 0);
                shapeRenderer.rotate(0, 0, 1, angle);
                shapeRenderer.rect(-1f, -0.25f, 2f, 0.5f);
            }
        }
        shapeRenderer.end();

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);

        Vector2 playerPos = player.getPosition();
        float playerAngle = player.getAngle() * MathUtils.radiansToDegrees;
        float playerHalfWidth = 0.4f;
        float playerHalfHeight = 0.4f;

        shapeRenderer.identity();
        shapeRenderer.translate(playerPos.x, playerPos.y, 0);
        shapeRenderer.rotate(0, 0, 1, playerAngle);
        shapeRenderer.rect(-playerHalfWidth, -playerHalfHeight, playerHalfWidth * 2, playerHalfHeight * 2);

        shapeRenderer.end();

        box2DDebugRenderer.render(world, camera.combined);

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        font.draw(batch, "Stage: " + stage,
            uiCamera.viewportWidth - 100,
            uiCamera.viewportHeight - 20,
            100, Align.left, false);

        font.draw(batch, "Score: " + score,
            20,
            uiCamera.viewportHeight - 20,
            100, Align.left, false);

        if (isPaused && !isGameClear)
        {
            float texWidth = pauseTexture.getWidth();
            float texHeight = pauseTexture.getHeight();
            batch.draw(pauseTexture,
                (uiCamera.viewportWidth - texWidth) / 2,
                (uiCamera.viewportHeight - texHeight) / 2);
        }

        // 게임 클리어 텍스트 렌더링
        if (isGameClear)
        {
            gameClearFont.draw(batch, "GAME CLEAR",
                0,
                uiCamera.viewportHeight / 2,
                uiCamera.viewportWidth,
                Align.center, false);
        }

        batch.end();
    }

    private void handleInput()
    {
        if (Gdx.input.isKeyPressed(Keys.SPACE))
        {
            world.setGravity(defaultGravity.cpy().scl(2.0f));
        }
        else
        {
            world.setGravity(defaultGravity);
        }

        if (Gdx.input.isButtonPressed(Buttons.LEFT))
        {
            Vector3 currentTouchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentTouchPos);

            if (!isDragging)
            {
                boolean canJump = jumpCount < MAX_JUMPS;
                boolean touchingPlayer = player.getFixtureList().first().testPoint(currentTouchPos.x, currentTouchPos.y);
                if (canJump && touchingPlayer)
                {
                    isDragging = true;
                    touchStartPos.set(player.getPosition().x, player.getPosition().y, 0);
                }
            }
        }
        else if (isDragging)
        {
            isDragging = false;
            jumpCount++;

            Vector3 touchEndPos3D = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchEndPos3D);

            Vector2 dragVector = new Vector2(touchStartPos.x, touchStartPos.y).sub(touchEndPos3D.x, touchEndPos3D.y);

            if (dragVector.len() > MAX_DRAG_DISTANCE) {

                dragVector.setLength(MAX_DRAG_DISTANCE);
            }

            float forceMagnitude = dragVector.len() * FORCE_MULTIPLIER;

            Vector2 force = dragVector.setLength(forceMagnitude);
            player.setLinearVelocity(0, 0);
            player.applyForceToCenter(force, true);
        }
    }

    @Override
    public void resize(int width, int height)
    {
        viewport.update(width, height, true);
        uiCamera.viewportWidth = width;
        uiCamera.viewportHeight = height;
        uiCamera.position.set(width / 2f, height / 2f, 0);
        uiCamera.update();
    }

    @Override
    public void dispose() {
        world.dispose();
        box2DDebugRenderer.dispose();
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
        gameClearFont.dispose();
        pauseTexture.dispose();
    }

    @Override
    public void show() {}

    @Override
    public void hide() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}
}

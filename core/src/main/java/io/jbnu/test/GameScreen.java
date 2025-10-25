package io.jbnu.test;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
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
    private SpriteBatch batch;
    private BitmapFont font;
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
    private final float GENERATE_DISTANCE = 20f;
    private final float REMOVE_DISTANCE = 25f;
    private int score = 0;
    private int stage = 1;
    private final int SCORE_PER_STAGE = 1000;
    private final float MAX_ROTATION_ANGLE = MathUtils.PI / 6;

    public GameScreen()
    {
        camera = new OrthographicCamera();
        float worldWidth = 20f;
        float worldHeight = worldWidth * (Gdx.graphics.getHeight() / (float) Gdx.graphics.getWidth()); // 화면 비율에 맞춤
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

        player = createPlayer();
        createInitialGrounds();
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
        score += amount;
        Gdx.app.log("GameScreen", "Score: " + score);
        int calculatedStage = (score / SCORE_PER_STAGE) + 1;
        if (calculatedStage > stage)
        {
            stage = calculatedStage;
            Gdx.app.log("GameScreen", "Stage Up! Current Stage: " + stage);
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
        fixtureDef.friction = 0.5f;
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

            int randType = MathUtils.random(0, 99); // 0~99 사이 난수 생성

            if (stage >= 2 && randType < 20)
            {
                friction = Ground.FRICTION_HIGH;
                if (MathUtils.randomBoolean(0.4f))
                {
                    angle = MathUtils.random(-MAX_ROTATION_ANGLE, MAX_ROTATION_ANGLE);
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

            if (stage >= 2 && friction != Ground.FRICTION_HIGH && MathUtils.randomBoolean(0.3f))
            {
                angle = MathUtils.random(-MAX_ROTATION_ANGLE, MAX_ROTATION_ANGLE);
            }

            Body groundBody = Ground.createGround(world, x, y, width, GROUND_HEIGHT, angle, friction);
            grounds.add(groundBody);

            nextGroundX += width + MathUtils.random(1.0f, 3.0f);
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
    public void render(float delta) {
        update(delta);

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

        if (isDragging)
        {
            Vector3 currentPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentPos);

            Vector2 lineVec = new Vector2(currentPos.x - touchStartPos.x, currentPos.y - touchStartPos.y);
            if (lineVec.len() > MAX_DRAG_DISTANCE)
            {
                lineVec.setLength(MAX_DRAG_DISTANCE);
            }

            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.circle(touchStartPos.x, touchStartPos.y, MAX_DRAG_DISTANCE, 30);

            shapeRenderer.setColor(Color.RED);
            shapeRenderer.line(touchStartPos.x, touchStartPos.y, touchStartPos.x - lineVec.x, touchStartPos.y - lineVec.y);
            shapeRenderer.end();
        }

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.draw(batch, "Stage: " + stage, Gdx.graphics.getWidth() - 100, Gdx.graphics.getHeight() - 20, 100, Align.left, false);
        font.draw(batch, "Score: " + score, 20, Gdx.graphics.getHeight() - 20, 100, Align.left, false);
        batch.end();
    }

    private void handleInput()
    {
        if (Gdx.input.isKeyPressed(Keys.SPACE))
        {
            world.setGravity(defaultGravity.cpy().scl(2.0f)); // scl 오타 수정
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

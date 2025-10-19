package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen extends ScreenAdapter {
    private final Box2DDebugRenderer box2DDebugRenderer;
    private final World world;
    private final Body player;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final Ground ground;
    private final ShapeRenderer shapeRenderer;

    private boolean isDragging = false;
    private final Vector3 touchStartPos = new Vector3();
    private final Vector2 defaultGravity = new Vector2(0, -2.0f);
    private final float MAX_DRAG_DISTANCE = 3.0f;

    // 더블 점프 기능 추가
    private int jumpCount = 0;
    private final int MAX_JUMPS = 2;

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(16, 9, camera);
        camera.setToOrtho(false, 16, 9);

        world = new World(defaultGravity, true);
        world.setContactListener(new GameContactListener(this)); // 충돌 감지 리스너 설정
        box2DDebugRenderer = new Box2DDebugRenderer();
        shapeRenderer = new ShapeRenderer();

        player = createPlayer();
        ground = new Ground(world);
    }

    // 더블 점프 횟수를 리셋하는 메서드
    public void resetJumpCount() {
        jumpCount = 0;
    }

    private Body createPlayer() {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(2, 5);
        bodyDef.fixedRotation = true;

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(0.5f, 0.5f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;
        fixtureDef.friction = 0.5f;
        fixtureDef.restitution = 0.1f;

        // 플레이어 Fixture에 "player" 식별자 설정
        body.createFixture(fixtureDef).setUserData("player");
        shape.dispose();
        return body;
    }

    @Override
    public void render(float delta) {
        handleInput();
        world.step(1 / 60f, 6, 2);

        camera.position.set(player.getPosition().x, player.getPosition().y, 0);
        camera.update();

        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (isDragging) {
            Vector3 currentPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentPos);

            Vector2 lineVec = new Vector2(currentPos.x - touchStartPos.x, currentPos.y - touchStartPos.y);
            if (lineVec.len() > MAX_DRAG_DISTANCE) {
                lineVec.setLength(MAX_DRAG_DISTANCE);
            }

            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

            // 최대 범위 원 (노란색)
            shapeRenderer.setColor(1, 1, 0, 1);
            shapeRenderer.circle(touchStartPos.x, touchStartPos.y, MAX_DRAG_DISTANCE, 30);

            // 당기는 방향과 힘 표시선 (빨간색)
            shapeRenderer.setColor(1, 0, 0, 1);
            shapeRenderer.line(touchStartPos.x, touchStartPos.y, touchStartPos.x - lineVec.x, touchStartPos.y - lineVec.y);
            shapeRenderer.end();
        }

        box2DDebugRenderer.render(world, camera.combined);
    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Keys.SPACE)) {
            world.setGravity(defaultGravity.cpy().scl(2.0f));
        } else {
            world.setGravity(defaultGravity);
        }

        if (Gdx.input.isButtonPressed(Buttons.LEFT)) {
            Vector3 currentTouchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentTouchPos);

            if (!isDragging) {
                // 점프 횟수가 남아있을 때만 드래그 시작 가능
                if (jumpCount < MAX_JUMPS && player.getFixtureList().first().testPoint(currentTouchPos.x, currentTouchPos.y)) {
                    isDragging = true;
                    touchStartPos.set(player.getPosition().x, player.getPosition().y, 0);
                }
            }
        } else if (isDragging) {
            isDragging = false;
            jumpCount++; // 날릴 때마다 점프 횟수 증가

            Vector3 touchEndPos3D = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchEndPos3D);

            Vector2 dragVector = new Vector2(touchStartPos.x, touchStartPos.y).sub(touchEndPos3D.x, touchEndPos3D.y);

            if (dragVector.len() > MAX_DRAG_DISTANCE) {
                dragVector.setLength(MAX_DRAG_DISTANCE);
            }

            // 힘 배수 상향
            float forceMagnitude = dragVector.len() * 50.0f;

            Vector2 force = dragVector.setLength(forceMagnitude);
            player.setLinearVelocity(0, 0);
            player.applyForceToCenter(force, true);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void dispose() {
        world.dispose();
        box2DDebugRenderer.dispose();
        shapeRenderer.dispose();
    }
}

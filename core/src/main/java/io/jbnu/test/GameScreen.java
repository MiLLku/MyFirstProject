package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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

    private Vector2 dragStartPos = null; // 드래그 시작 위치

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(16, 9, camera); // 게임 월드 크기 설정
        camera.setToOrtho(false, 16, 9);

        world = new World(new Vector2(0, -9.8f), true); // 중력 설정
        box2DDebugRenderer = new Box2DDebugRenderer(); // 물리 객체 시각화 렌더러

        player = createPlayer(); // 플레이어 생성
        createGround(); // 바닥 생성
    }

    /**
     * 플레이어 사각형을 생성합니다.
     */
    private Body createPlayer() {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody; // 움직이는 객체
        bodyDef.position.set(2, 5); // 초기 위치

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(0.5f, 0.5f); // 1x1 크기의 사각형

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;     // 밀도
        fixtureDef.friction = 0.5f;    // 마찰력
        fixtureDef.restitution = 0.1f; // 반발력 (튕기는 정도)

        body.createFixture(fixtureDef);
        shape.dispose();
        return body;
    }

    /**
     * 바닥을 생성합니다.
     */
    private void createGround() {
        // 일반 바닥
        createStaticBody(0, 0, 12, 1, 0.6f); // x, y, 너비, 높이, 마찰력
        // 미끄러운 바닥 (얼음)
        createStaticBody(14, 0, 10, 1, 0.1f);
    }

    /**
     * 고정된 물리 객체(바닥)를 생성하는 헬퍼 함수
     */
    private void createStaticBody(float x, float y, float width, float height, float friction) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody; // 움직이지 않는 객체
        bodyDef.position.set(x + width / 2, y + height / 2);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2, height / 2);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = friction; // 바닥마다 다른 마찰력 설정

        body.createFixture(fixtureDef);
        shape.dispose();
    }

    @Override
    public void render(float delta) {
        // 입력 처리
        handleInput();

        // 물리 시뮬레이션
        world.step(1 / 60f, 6, 2);

        // 화면 클리어
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 카메라 업데이트
        camera.update();

        // Box2D 객체 그리기 (디버그용)
        box2DDebugRenderer.render(world, camera.combined);
    }

    /**
     * 사용자 입력을 처리합니다.
     */
    private void handleInput() {
        if (Gdx.input.isTouched()) {
            Vector3 touchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchPos); // 화면 좌표를 게임 월드 좌표로 변환

            if (Gdx.input.justTouched()) {
                // 터치가 시작되면 드래그 시작 위치 기록
                if (player.getFixtureList().first().testPoint(touchPos.x, touchPos.y)) {
                    dragStartPos = new Vector2(touchPos.x, touchPos.y);
                }
            }
        } else if (dragStartPos != null) {
            // 터치가 끝나면 (손을 떼면) 힘을 계산하고 적용
            Vector3 touchEndPos3D = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchEndPos3D);
            Vector2 touchEndPos = new Vector2(touchEndPos3D.x, touchEndPos3D.y);

            // 드래그 방향과 거리 계산
            Vector2 dragVector = new Vector2(dragStartPos).sub(touchEndPos);

            // 힘의 크기를 제한하여 너무 강하게 날아가지 않도록 조절
            float forceMagnitude = Math.min(dragVector.len() * 5.0f, 150.0f); // 힘의 배수와 최대 힘 설정

            // 드래그 방향과 계산된 힘으로 발사
            Vector2 force = dragVector.setLength(forceMagnitude);
            player.applyForceToCenter(force, true);

            dragStartPos = null; // 드래그 상태 초기화
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
    }
}

package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen extends ScreenAdapter
{
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

    public GameScreen() {
        camera = new OrthographicCamera();
        float worldWidth = 20f;
        float worldHeight = worldWidth * (9f / 16f);
        viewport = new FitViewport(worldWidth, worldHeight, camera);
        camera.setToOrtho(false, worldWidth, worldHeight);
        camera.position.set(worldWidth / 4f, worldHeight / 2f, 0);


        world = new World(defaultGravity, true);
        world.setContactListener(new GameContactListener(this));
        box2DDebugRenderer = new Box2DDebugRenderer();
        shapeRenderer = new ShapeRenderer();

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

    public void addScore(int amount) {
        score += amount;
        Gdx.app.log("GameScreen", "Score: " + score);
        // 스테이지 업데이트 확인
        if (score >= stage * SCORE_PER_STAGE) {
            stage++;
            Gdx.app.log("GameScreen", "Stage Up! Current Stage: " + stage);
            // TODO: 스테이지 변경에 따른 효과 (배경 변경, 난이도 조절 등)
        }
    }


    private Body createPlayer() {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(2, 5); // 시작 위치 조정
        bodyDef.fixedRotation = true; // 플레이어가 회전하지 않도록 설정

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        // 플레이어 크기 조정 (월드 단위 기준)
        float playerHalfWidth = 0.4f;
        float playerHalfHeight = 0.4f;
        shape.setAsBox(playerHalfWidth, playerHalfHeight);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f; // 밀도
        fixtureDef.friction = 0.5f; // 마찰력
        fixtureDef.restitution = 0.1f; // 반발력 (바닥에 닿았을 때 살짝 튀는 정도)

        // 플레이어 Fixture에 "player" 식별자 설정
        body.createFixture(fixtureDef).setUserData("player");
        shape.dispose();
        return body;
    }

    private void update(float delta) {
        // 입력 처리 (중력 변경은 handleInput으로 이동)
        handleInput();
        // 물리 월드 업데이트
        world.step(1 / 60f, 6, 2);

        // 맵 생성 및 제거
        generateGrounds();
        removeOldGrounds();

        // 카메라 이동 (플레이어를 따라 부드럽게)
        float targetX = player.getPosition().x + viewport.getWorldWidth() / 4f; // 카메라가 플레이어보다 약간 앞에 위치하도록
        // lerp를 사용하여 부드러운 카메라 이동
        camera.position.x += (targetX - camera.position.x) * 0.1f;
        // camera.position.y = player.getPosition().y; // Y축은 플레이어를 바로 따라갈지, 고정할지, lerp할지 결정
        camera.update();

        // 게임 오버 체크
        if (player.getPosition().y < camera.position.y - viewport.getWorldHeight() / 2f - 2f) { // 화면 하단보다 더 아래로 떨어지면
            gameOver();
        }
    }


    private void generateGrounds() {
        while (nextGroundX < camera.position.x + viewport.getWorldWidth() / 2f + GENERATE_DISTANCE) {
            float width = MathUtils.random(1.5f, 4.0f);
            float x = nextGroundX + width / 2;
            float y = MathUtils.random(MIN_GROUND_Y, MAX_GROUND_Y);
            float angle = 0;
            float friction;

            // 마찰력 무작위 선택
            int randType = MathUtils.random(0, 99); // 0~99 사이 난수 생성

            if (stage >= 2 && randType < 20) { // 스테이지 2 이상이고 20% 확률로 높은 마찰력
                friction = Ground.FRICTION_HIGH;
                // 스테이지 2 이상이면 높은 마찰력 발판도 기울어질 수 있음 (선택 사항)
                if (MathUtils.randomBoolean(0.4f)) { // 40% 확률로 회전
                    angle = MathUtils.random(-MAX_ROTATION_ANGLE, MAX_ROTATION_ANGLE);
                }
            } else if (randType < 50) { // 30% 확률 (20~49) 로 낮은 마찰력
                friction = Ground.FRICTION_LOW;
            } else { // 나머지 50% 확률 (50~99) 로 일반 마찰력
                friction = Ground.FRICTION_NORMAL;
            }

            // 스테이지 2 이상이고, 높은 마찰력이 아닐 때만 일반적인 회전 적용
            if (stage >= 2 && friction != Ground.FRICTION_HIGH && MathUtils.randomBoolean(0.3f)) { // 30% 확률로 회전
                angle = MathUtils.random(-MAX_ROTATION_ANGLE, MAX_ROTATION_ANGLE);
            }

            // Ground 생성 시 width, height 전달
            Body groundBody = Ground.createGround(world, x, y, width, GROUND_HEIGHT, angle, friction);
            grounds.add(groundBody);

            nextGroundX += width + MathUtils.random(1.0f, 3.0f);
        }
    }

    private void removeOldGrounds() {
        // Iterator 대신 인덱스를 역순으로 순회하며 제거 (안전)
        for (int i = grounds.size - 1; i >= 0; i--) {
            Body groundBody = grounds.get(i);
            // Ground의 오른쪽 끝 x 좌표 계산 (대략적)
            float groundRightEdge = groundBody.getPosition().x + 2.0f; // 너비를 UserData에 저장했다면 더 정확히 계산 가능
            try {
                if (groundBody.getUserData() instanceof Ground.GroundUserData) {
                    // 정확한 너비 정보를 얻기 어렵지만, 대략적으로 AABB를 사용할 수 있음 (회전 시 부정확)
                    Fixture f = groundBody.getFixtureList().first();
                    if (f != null && f.getShape() instanceof PolygonShape) {
                        // AABB는 회전 안된 상태의 경계 박스이므로 실제 오른쪽 끝과 다를 수 있음
                        // groundRightEdge = groundBody.getPosition().x + (f.getAABB(0).upperBound.x - f.getAABB(0).lowerBound.x) / 2;
                        // 그냥 위치 + 최대 너비 정도로 계산하는 것이 더 간단할 수 있음
                        groundRightEdge = groundBody.getPosition().x + 4.0f; // 최대 너비 고려
                    }
                }
            } catch (Exception e) {
                // UserData가 없거나 형식이 다를 경우 대비
                groundRightEdge = groundBody.getPosition().x + 2.0f; // 기본값 사용
            }


            // 카메라 왼쪽 경계보다 일정 거리 뒤에 있으면 제거
            if (groundRightEdge < camera.position.x - viewport.getWorldWidth() / 2f - REMOVE_DISTANCE) {
                // 중요: 물리 업데이트 단계(world.step) 외부에서 Body를 제거해야 함
                // 여기서는 render 루프 내이므로 괜찮지만, ContactListener 등에서는 바로 제거하면 안 됨
                final Body bodyToRemove = groundBody; // final 변수로 복사
                Gdx.app.postRunnable(() -> { // 다음 프레임 시작 시 실행되도록 예약
                    if (world.isLocked()) {
                        Gdx.app.log("RemoveGround", "World is locked, delaying removal");
                        // 월드가 잠겨 있으면 다음 프레임에 다시 시도하도록 할 수 있지만, 복잡해짐
                        // 일반적으로 render 루프 끝에서 제거하면 문제는 적음
                    } else {
                        if (grounds.contains(bodyToRemove, true)) { // 아직 배열에 있는지 확인
                            grounds.removeValue(bodyToRemove, true);
                            world.destroyBody(bodyToRemove);
                            Gdx.app.log("RemoveGround", "Ground removed");
                        }
                    }
                });
                // 직접 제거 시도 (World가 잠겨있지 않다면 성공)
                // world.destroyBody(groundBody);
                // grounds.removeIndex(i);
                // Gdx.app.log("RemoveGround", "Ground removed");
            }
        }
    }

    private void gameOver() {
        Gdx.app.log("GameScreen", "Game Over! Final Score: " + score + ", Stage: " + stage);
        // TODO: 게임 오버 화면으로 전환하는 코드 구현
        // 예: ((Game)Gdx.app.getApplicationListener()).setScreen(new GameOverScreen(score));
        // 임시로 애플리케이션 종료
        Gdx.app.exit();
    }


    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeType.Filled);
        for (Body groundBody : grounds) {
            Fixture fixture = groundBody.getFixtureList().first();
            if (fixture.getUserData() instanceof Ground.GroundUserData) {
                Ground.GroundUserData data = (Ground.GroundUserData) fixture.getUserData();
                shapeRenderer.setColor(data.color); // UserData에서 색상 가져오기

                Vector2 position = groundBody.getPosition();
                float angle = groundBody.getAngle() * MathUtils.radiansToDegrees;
                // UserData에 저장된 너비와 높이 사용
                float halfWidth = data.width / 2f;
                float halfHeight = data.height / 2f;

                shapeRenderer.identity();
                shapeRenderer.translate(position.x, position.y, 0);
                shapeRenderer.rotate(0, 0, 1, angle);
                // 정확한 크기로 사각형 그리기
                shapeRenderer.rect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2);

            } else {
                // UserData가 없는 경우 (오류 상황 대비)
                shapeRenderer.setColor(Color.GRAY);
                Vector2 position = groundBody.getPosition();
                float angle = groundBody.getAngle() * MathUtils.radiansToDegrees;
                // 임시 크기로 그리기
                shapeRenderer.identity();
                shapeRenderer.translate(position.x, position.y, 0);
                shapeRenderer.rotate(0, 0, 1, angle);
                shapeRenderer.rect(-1f, -0.25f, 2f, 0.5f); // 예시 크기
            }
        }
        shapeRenderer.end();

        // 드래그 선 그리기 (기존 코드 유지)
        if (isDragging) {
            Vector3 currentPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentPos);

            Vector2 lineVec = new Vector2(currentPos.x - touchStartPos.x, currentPos.y - touchStartPos.y);
            if (lineVec.len() > MAX_DRAG_DISTANCE) {
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

        // Box2D 디버그 렌더러
        box2DDebugRenderer.render(world, camera.combined);

        // TODO: UI 렌더링 (점수, 스테이지 등)
    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Keys.SPACE)) {
            // 스페이스바 누르면 중력 증가 (기존 로직 유지)
            world.setGravity(defaultGravity.cpy().scl(2.0f));
        } else {
            world.setGravity(defaultGravity);
        }

        if (Gdx.input.isButtonPressed(Buttons.LEFT)) {
            Vector3 currentTouchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(currentTouchPos);

            if (!isDragging) {
                // 점프 횟수가 남아있고 플레이어를 클릭했을 때만 드래그 시작
                if (jumpCount < MAX_JUMPS && player.getFixtureList().first().testPoint(currentTouchPos.x, currentTouchPos.y)) {
                    isDragging = true;
                    // 드래그 시작 위치를 플레이어의 현재 위치로 설정
                    touchStartPos.set(player.getPosition().x, player.getPosition().y, 0);
                }
            }
        } else if (isDragging) { // 마우스 버튼을 뗐을 때
            isDragging = false;
            jumpCount++; // 날릴 때마다 점프 횟수 증가

            Vector3 touchEndPos3D = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchEndPos3D);

            // 드래그 시작점에서 끝점까지의 벡터 계산 (방향 반대)
            Vector2 dragVector = new Vector2(touchStartPos.x, touchStartPos.y).sub(touchEndPos3D.x, touchEndPos3D.y);

            // 최대 드래그 거리 제한
            if (dragVector.len() > MAX_DRAG_DISTANCE) {
                dragVector.setLength(MAX_DRAG_DISTANCE);
            }

            // 힘 계산 (거리에 비례, 배수 상향 조정)
            float forceMagnitude = dragVector.len() * FORCE_MULTIPLIER; // 힘 배수 조절 가능

            Vector2 force = dragVector.setLength(forceMagnitude);
            player.setLinearVelocity(0, 0); // 기존 속도 초기화 (선택 사항)
            player.applyForceToCenter(force, true); // 중앙에 힘 적용
        }
    }

    @Override
    public void resize(int width, int height) {
        // 화면 크기가 변경될 때 Viewport 업데이트
        viewport.update(width, height, true); // true: 카메라 위치 중앙 유지
    }

    @Override
    public void dispose() {
        // 사용한 LibGDX 리소스 해제
        world.dispose();
        box2DDebugRenderer.dispose();
        shapeRenderer.dispose();
        // TODO: Font, Texture 등 다른 리소스들도 여기서 dispose() 호출 필요
    }

    // 사용하지 않는 ScreenAdapter 메서드들 (필요시 오버라이드)
    @Override
    public void show() {}

    @Override
    public void hide() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}
}

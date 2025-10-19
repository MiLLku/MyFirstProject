package io.jbnu.test;

import com.badlogic.gdx.physics.box2d.*;

public class Ground {

    private final World world;

    public Ground(World world) {
        this.world = world;
        create();
    }

    private void create() {
        // 일반 바닥
        createStaticBody(0, 0, 12, 1, 0.6f); // x, y, 너비, 높이, 마찰력
        // 미끄러운 바닥 (얼음)
        createStaticBody(14, 0, 10, 1, 0.1f);
    }

    private void createStaticBody(float x, float y, float width, float height, float friction) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x + width / 2, y + height / 2);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2, height / 2);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = friction;

        // 생성된 바닥 Fixture에 "ground"라는 식별자(UserData)를 설정
        body.createFixture(fixtureDef).setUserData("ground");
        shape.dispose();
    }
}

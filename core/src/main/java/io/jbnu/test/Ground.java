// Ground.java 수정 예시
package io.jbnu.test;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.*;

public class Ground {

    public static final float FRICTION_NORMAL = 0.6f;
    public static final float FRICTION_HIGH = 100.0f;
    public static final float FRICTION_LOW = 0.05f;

    public static class GroundUserData
    {
        public float friction;
        public Color color;
        public boolean touched;
        public String type = "ground";
        public float width;
        public float height;

        public GroundUserData(float friction, float width, float height)
        {
            this.friction = friction;
            this.width = width;
            this.height = height;
            this.touched = false;

            if (friction == FRICTION_HIGH)
            {
                this.color = Color.BLACK;
            }
            else if (friction == FRICTION_LOW)
            {
                this.color = Color.CYAN;
            }
            else
            {
                this.color = Color.GREEN;
            }
        }
    }

    public static Body createGround(World world, float x, float y, float width, float height, float angle, float friction)
    {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2, height / 2);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = friction;

        GroundUserData userData = new GroundUserData(friction, width, height);
        body.createFixture(fixtureDef).setUserData(userData);
        shape.dispose();
        return body;
    }
}

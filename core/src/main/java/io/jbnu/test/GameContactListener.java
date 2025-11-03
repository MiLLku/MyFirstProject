package io.jbnu.test;

import com.badlogic.gdx.physics.box2d.*;

public class GameContactListener implements ContactListener {


    private final GameScreen gameScreen;

    public GameContactListener(GameScreen gameScreen) {


        this.gameScreen = gameScreen;
    }

    @Override
    public void beginContact(Contact contact)
    {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Fixture playerFixture = null;
        Fixture groundFixture = null;
        Ground.GroundUserData groundUserData = null;

        if (isFixturePlayer(fixtureA) && isFixtureGround(fixtureB))
        {
            playerFixture = fixtureA;
            groundFixture = fixtureB;
            if (fixtureB.getUserData() instanceof Ground.GroundUserData)
            {
                groundUserData = (Ground.GroundUserData) fixtureB.getUserData();
            }
        }
        else if (isFixtureGround(fixtureA) && isFixturePlayer(fixtureB))
        {
            playerFixture = fixtureB;
            groundFixture = fixtureA;
            if (fixtureA.getUserData() instanceof Ground.GroundUserData)
            {
                groundUserData = (Ground.GroundUserData) fixtureA.getUserData();
            }
        }

        if (playerFixture != null && groundUserData != null)
        {
            gameScreen.resetJumpCount();

            if (!groundUserData.touched) {
                gameScreen.addScore(100);
                groundUserData.touched = true;
            }
        }
    }

    private boolean isFixturePlayer(Fixture fixture)
    {
        return "player".equals(fixture.getUserData());
    }

    private boolean isFixtureGround(Fixture fixture)
    {
        if (fixture.getUserData() instanceof Ground.GroundUserData)
        {
            return "ground".equals(((Ground.GroundUserData) fixture.getUserData()).type);
        }
        return false;
    }

    @Override
    public void endContact(Contact contact) { }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) { }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) { }
}

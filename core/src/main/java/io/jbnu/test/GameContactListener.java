package io.jbnu.test;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

public class GameContactListener implements ContactListener {

    private final GameScreen gameScreen;

    public GameContactListener(GameScreen gameScreen) {
        this.gameScreen = gameScreen;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        // 두 충돌체 중 하나가 'player'이고 다른 하나가 'ground'인지 확인
        if (isFixturePlayer(fixtureA) && isFixtureGround(fixtureB) ||
            isFixtureGround(fixtureA) && isFixturePlayer(fixtureB)) {
            // 플레이어가 땅에 닿으면 점프 횟수 초기화
            gameScreen.resetJumpCount();
        }
    }

    private boolean isFixturePlayer(Fixture fixture) {
        return "player".equals(fixture.getUserData());
    }

    private boolean isFixtureGround(Fixture fixture) {
        return "ground".equals(fixture.getUserData());
    }

    @Override
    public void endContact(Contact contact) { }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) { }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) { }
}

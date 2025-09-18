package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

@Getter
public class Projectile extends GameEntity {
    private static int nextProjectileId = 1;
    private final int ownerId;
    private final int damage;
    private final double maxRange;
    private final Vector2 startPosition;
    private double distanceTraveled = 0;

    public Projectile(int ownerId, double x, double y, double vx, double vy, int damage, double maxRange) {
        super(nextProjectileId++, createProjectileBody(x, y, vx, vy), 1.0);
        this.ownerId = ownerId;
        this.damage = damage;
        this.maxRange = maxRange;
        this.startPosition = new Vector2(x, y);
    }

    private static Body createProjectileBody(double x, double y, double vx, double vy) {
        Body body = new Body();
        Circle circle = new Circle(2.0); // Small projectile
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);
        body.setLinearVelocity(vx, vy);
        body.setBullet(true); // Enable continuous collision detection
        return body;
    }

    @Override
    public void update(double deltaTime) {
        Vector2 currentPos = getPosition();
        distanceTraveled = startPosition.distance(currentPos);

        // Deactivate if traveled too far
        if (distanceTraveled > maxRange) {
            active = false;
        }

        lastUpdateTime = System.currentTimeMillis();
    }
}

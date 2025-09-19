package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

@Getter
public class Projectile extends GameEntity {
    private final int ownerId;
    private final double damage;
    private double timeToLive; // Time in seconds before projectile is removed

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange) {
        super(Config.nextId(), createProjectileBody(x, y, vx, vy), 1.0);
        this.ownerId = ownerId;
        this.damage = damage;

        // Calculate time to live based on range and speed
        double speed = new Vector2(vx, vy).getMagnitude();
        if (speed > 0) {
            this.timeToLive = maxRange / speed;
        } else {
            this.timeToLive = 0; // Deactivate immediately if speed is zero
        }
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
        if (!active) {
            return;
        }

        timeToLive -= deltaTime;
        if (timeToLive <= 0) {
            active = false;
        }

        lastUpdateTime = System.currentTimeMillis();
    }
}

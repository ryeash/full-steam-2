package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

/**
 * Headquarters entity - a destructible structure that teams must protect/attack.
 * Similar to Obstacle but with team ownership and scoring mechanics.
 * Each team has one headquarters in their spawn zone that can be shot to score points.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Headquarters extends OwnedGameEntity {
    private static final double HQ_WIDTH = Config.PLAYER_RADIUS * 4;
    private static final double HQ_HEIGHT = Config.PLAYER_RADIUS * 3;
    private static final double HQ_TURRET_RADIUS = Config.PLAYER_RADIUS * .75;

    private final Vector2 homePosition;
    private final double maxHealth;

    public Headquarters(int id, int ownerTeam, double x, double y, double maxHealth) {
        super(id, createHeadquartersBody(x, y), maxHealth, id, ownerTeam);
        this.homePosition = new Vector2(x, y);
        this.maxHealth = maxHealth;
    }

    private static Body createHeadquartersBody(double x, double y) {
        Body body = new Body();
        Rectangle rect = new Rectangle(HQ_WIDTH, HQ_HEIGHT);
        body.addFixture(rect);
        double halfWidth = HQ_WIDTH / 2.0;
        double halfHeight = HQ_HEIGHT / 2.0;
        double[][] turretCorners = {
                {-halfWidth, -halfHeight}, // Top-left
                {halfWidth, -halfHeight}, // Top-right
                {halfWidth, halfHeight}, // Bottom-right
                {-halfWidth, halfHeight}  // Bottom-left
        };
        for (double[] corner : turretCorners) {
            Circle turret = new Circle(HQ_TURRET_RADIUS);
            turret.translate(corner[0], corner[1]);
            body.addFixture(turret);
        }

        body.setMass(MassType.INFINITE); // Static structure
        body.getTransform().setTranslation(x, y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Apply damage to headquarters and track total damage for scoring.
     */
    @Override
    public boolean takeDamage(double damage) {
        if (!active) {
            return false;
        }
        health -= damage;
        if (health <= 0) {
            health = 0;
            active = false;
            return true; // Headquarters destroyed!
        }
        return false;
    }
}


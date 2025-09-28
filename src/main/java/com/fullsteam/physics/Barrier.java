package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

/**
 * Temporary barrier/wall that provides cover and blocks movement
 */
@Getter
@Setter
public class Barrier extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double lifespan;
    private final double width;
    private final double height;
    
    private double timeRemaining;

    public Barrier(int id, int ownerId, int ownerTeam, Vector2 position, Vector2 direction, double lifespan) {
        super(id, createBarrierBody(position, direction), 100.0); // 100 HP barrier
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.lifespan = lifespan;
        this.timeRemaining = lifespan;
        this.width = 80.0; // 80 units wide
        this.height = 10.0; // 10 units thick
    }

    private static Body createBarrierBody(Vector2 position, Vector2 direction) {
        Body body = new Body();
        
        // Create a rectangular barrier
        Rectangle rectangle = new Rectangle(80.0, 10.0); // width x height
        body.addFixture(rectangle);
        body.setMass(MassType.INFINITE); // Stationary
        body.getTransform().setTranslation(position.x, position.y);
        
        // Orient the barrier perpendicular to the direction
        if (direction.getMagnitude() > 0) {
            double angle = Math.atan2(direction.y, direction.x);
            body.getTransform().setRotation(angle + Math.PI / 2); // Perpendicular to aim direction
        }
        
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Update lifespan
        timeRemaining -= deltaTime;
        if (timeRemaining <= 0) {
            active = false;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Check if the barrier has expired
     */
    public boolean isExpired() {
        return !active || timeRemaining <= 0;
    }

    /**
     * Get the remaining lifespan as a percentage
     */
    public double getLifespanPercent() {
        return lifespan > 0 ? Math.max(0, timeRemaining / lifespan) : 0;
    }

    /**
     * Override damage handling - barriers can be destroyed
     */
    @Override
    public boolean takeDamage(double damage) {
        if (!active) {
            return false;
        }

        boolean wasActive = active;
        health -= damage;
        if (health <= 0) {
            active = false;
        }
        return wasActive && !active; // Return true if barrier was destroyed
    }
}

package com.fullsteam.model;

import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Represents a temporary field effect in the game world (explosions, fire, electric fields, etc.)
 * Now uses physics bodies with sensor fixtures for optimized collision detection.
 */
@Getter
public class FieldEffectCircle extends FieldEffect {

    protected double radius;
    protected final double initialRadius;
    protected final double maxRadius;

    public FieldEffectCircle(int ownerId,
                             FieldEffectType type,
                             Vector2 position,
                             double radius,
                             double maxRadius,
                             double damage,
                             double duration,
                             long armingTime,
                             int ownerTeam) {
        super(ownerId, ownerTeam, createFieldEffectCircle(position, radius), Double.POSITIVE_INFINITY, type, damage, armingTime);
        this.radius = radius;
        this.initialRadius = radius;
        this.maxRadius = maxRadius;
        this.expires = (long) (System.currentTimeMillis() + (duration * 1000)); // duration in seconds
        this.active = true;
    }

    protected static Body createFieldEffectCircle(Vector2 position, double radius) {
        Body body = new Body();
        Circle circle = new Circle(radius);
        BodyFixture fixture = body.addFixture(circle);
        fixture.setSensor(true);
        body.setMass(MassType.INFINITE); // Make it static
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (!isActive()) {
            return;
        }

        if (radius < maxRadius) {
            double oldRadius = radius;
            long elapsed = System.currentTimeMillis() - created;
            long duration = expires - created;
            double progress = elapsed / (double) duration;

            // Grow over first 50% of lifetime, then stabilize
            if (progress < 0.5) {
                radius = initialRadius + (maxRadius - initialRadius) * (progress / 0.5);
            } else {
                radius = maxRadius;
            }

            // Update physics body if radius changed (more frequent updates for smoother growth)
            if (Math.abs(radius - oldRadius) > 0.1) {
                updateBodyRadius(radius);
            }
        }

        if (System.currentTimeMillis() > expires) {
            active = false;
        }
    }

    /**
     * Update the physics body's sensor radius (for growing effects)
     */
    private void updateBodyRadius(double newRadius) {
        Body body = getBody();
        body.removeFixture(0);
        // Add new fixture with updated radius
        Circle circle = new Circle(newRadius);
        BodyFixture fixture = body.addFixture(circle);
        fixture.setSensor(true);
    }
}

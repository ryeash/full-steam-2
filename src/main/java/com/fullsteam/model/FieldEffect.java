package com.fullsteam.model;

import com.fullsteam.Config;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a temporary field effect in the game world (explosions, fire, electric fields, etc.)
 * Now uses physics bodies with sensor fixtures for optimized collision detection.
 */
@Getter
public class FieldEffect extends GameEntity {
    private final int ownerId;
    private final FieldEffectType type;
    private double radius;
    private final double initialRadius;
    private final double maxRadius;
    private final double damage;
    private final int ownerTeam;
    private final long armingTime;
    private final Set<Integer> affectedEntities; // Track which entities have been affected
    private final Map<Integer, Long> lastDamageTime; // Track last damage time for each player (in milliseconds)

    public FieldEffect(int ownerId,
                       FieldEffectType type,
                       Vector2 position,
                       double radius,
                       double maxRadius,
                       double damage,
                       double duration,
                       long armingTime,
                       int ownerTeam) {
        super(Config.nextEntityId(), createFieldEffectCircle(position, radius), Double.POSITIVE_INFINITY); // Field effects are indestructible
        this.ownerId = ownerId;
        this.type = type;
        this.initialRadius = radius;
        this.radius = radius;
        this.maxRadius = maxRadius;
        this.damage = damage;
        this.expires = (long) (System.currentTimeMillis() + (duration * 1000)); // duration in seconds
        this.armingTime = armingTime;
        this.ownerTeam = ownerTeam;
        this.affectedEntities = new HashSet<>();
        this.lastDamageTime = new HashMap<>();
        this.active = true;
    }

    private static Body createFieldEffectCircle(Vector2 position, double radius) {
        Body body = new Body();
        Circle circle = new Circle(radius);
        BodyFixture fixture = body.addFixture(circle);
        fixture.setSensor(true);
        body.setMass(MassType.INFINITE); // Make it static
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    public void update(double deltaTime) {
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
        // Remove old fixture
        if (body.getFixtureCount() > 0) {
            body.removeFixture(0);
        }
        // Add new fixture with updated radius
        Circle circle = new Circle(newRadius);
        BodyFixture fixture = body.addFixture(circle);
        fixture.setSensor(true);
    }

    public boolean isInRange(Vector2 targetPosition) {
        return getPosition().distance(targetPosition) <= radius;
    }

    public boolean canAffect(GameEntity entity) {
        // SMOKE affects ALL players/turrets regardless of team or ownership
        if (type == FieldEffectType.SMOKE) {
            return entity instanceof Player || entity instanceof Turret;
        }

        if (!active
                || entity == null
                || !isArmed() // delayed effects deal no damage until they fire
                || type == FieldEffectType.WARNING_ZONE
                || !isInRange(entity.getPosition())
                // For instantaneous effects, check if already affected
                || (type.isInstantaneous() && affectedEntities.contains(entity.getId()))) {
            return false;
        }

        if (entity instanceof Player player) {
            // for own-team/self targeting
            if (type == FieldEffectType.HEAL_ZONE || type == FieldEffectType.SPEED_BOOST) {
                // In FFA mode (team 0), can only help self
                if (ownerTeam == 0 || player.getTeam() == 0) {
                    return ownerId == player.getId();
                }
                // In team mode, can help teammates AND the owner
                return ownerTeam == player.getTeam();
            }

            // Team-based damage rules (same as projectiles)
            // Can't damage self (though this should be rare for field effects)
            if (player.getId() == ownerId) {
                return false;
            }

            // In FFA mode (team 0), can damage anyone
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return true;
            }

            // In team mode, can only damage players on different teams
            return ownerTeam != player.getTeam();
        }

        return true;
    }

    public void markAsAffected(GameEntity entity) {
        affectedEntities.add(entity.getId());
    }

    public double getDamageAtPosition(Vector2 targetPosition) {
        return damage * getIntensityAtPosition(targetPosition);
    }

    public double getIntensityAtPosition(Vector2 targetPosition) {
        if (!isInRange(targetPosition)) {
            return 0.0;
        }
        return switch (type) {
            // most damaging fields degrade with distance from center
            case EXPLOSION, FIRE, ELECTRIC, FREEZE, POISON -> {
                double distance = getPosition().distance(targetPosition);
                double intensity = 0.5 + (0.5 * (distance / radius));
                yield Math.max(0.0, intensity);
            }
            // everything else is uniform
            default -> 1.0;
        };
    }

    public long getDuration() {
        return Math.max(expires - activePhaseStart(), 0);
    }

    public long getTimeRemaining() {
        return Math.max(expires - System.currentTimeMillis(), 0);
    }

    public double getProgress() {
        long start = activePhaseStart();
        long duration = expires - start;
        long elapsed = System.currentTimeMillis() - start;
        return (duration > 0)
                ? Math.max(0.0, Math.min(1.0, elapsed / (double) duration))
                : 1.0;
    }

    /**
     * Start of the visible/active phase. For a delayed effect that's its arming
     * time (so duration/progress reflect the post-delay window, not the wait);
     * for a normal effect it's just when it was created.
     */
    private long activePhaseStart() {
        return Math.max(created, armingTime);
    }

    /**
     * Check if the mine is armed (for proximity mines)
     */
    public boolean isArmed() {
        return System.currentTimeMillis() > armingTime;
    }
}

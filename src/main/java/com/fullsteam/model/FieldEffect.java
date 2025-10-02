package com.fullsteam.model;

import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
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
    private final double radius;
    private final double damage;
    private final int ownerTeam;
    private final long expires;
    private final long armingTime;
    private final Set<Integer> affectedEntities; // Track which entities have been affected
    private final Map<Integer, Long> lastDamageTime; // Track last damage time for each player (in milliseconds)

    public FieldEffect(int id, int ownerId, FieldEffectType type, Vector2 position, double radius, double damage, double duration, int ownerTeam) {
        super(id, createFieldEffectBody(position, radius), Double.POSITIVE_INFINITY); // Field effects are indestructible
        this.ownerId = ownerId;
        this.type = type;
        this.radius = radius;
        this.damage = damage;
        this.expires = (long) (System.currentTimeMillis() + (duration * 1000)); // duration in seconds
        this.ownerTeam = ownerTeam;
        this.affectedEntities = new HashSet<>();
        this.lastDamageTime = new HashMap<>();
        this.active = true;

        // Initialize proximity mine specific fields
        if (type == FieldEffectType.PROXIMITY_MINE) {
            this.armingTime = System.currentTimeMillis() + 1000; // 1 second arming time
        } else {
            this.armingTime = 0;
        }

        getBody().setUserData(this);
    }

    private static Body createFieldEffectBody(Vector2 position, double radius) {
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
        if (System.currentTimeMillis() > expires) {
            active = false;
        }
    }

    public boolean isInRange(Vector2 targetPosition) {
        return getPosition().distance(targetPosition) <= radius;
    }

    public boolean canAffect(GameEntity entity) {
        if (!active || entity == null) {
            return false;
        }

        // Check if entity is in range
        if (!isInRange(entity.getPosition())) {
            return false;
        }

        // For instantaneous effects, check if already affected
        if (type.isInstantaneous() && affectedEntities.contains(entity.getId())) {
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

    public double getIntensityAtPosition(Vector2 targetPosition) {
        if (!isInRange(targetPosition)) {
            return 0.0;
        }
        return switch (type) {
            // explosions degrade with distance from center
            case EXPLOSION -> {
                double distance = getPosition().distance(targetPosition);
                double intensity = 0.2 + (0.8 - (distance / radius)); // Linear falloff
                yield Math.max(0.0, intensity);
            }
            default -> 1.0;
        };
    }

    public double getDamageAtPosition(Vector2 targetPosition) {
        return damage * getIntensityAtPosition(targetPosition);
    }

    public boolean isExpired() {
        return !active || System.currentTimeMillis() > expires;
    }

    public long getDuration() {
        return Math.max(expires - created, 0);
    }

    public long getTimeRemaining() {
        return Math.max(expires - System.currentTimeMillis(), 0);
    }

    public double getProgress() {
        long duration = expires - created;
        long timeRemaining = expires - System.currentTimeMillis();
        return (duration > 0 && timeRemaining > 0)
                ? (double) (duration - timeRemaining) / duration
                : 1.0;
    }

    /**
     * Trigger the mine and create an explosion field effect (for proximity mines)
     */
//    public FieldEffect trigger() {
//        if (type != FieldEffectType.PROXIMITY_MINE || hasTriggered) {
//            return null;
//        }
//
//        hasTriggered = true;
//        active = false;
//
//        // Create explosion field effect
//        return new FieldEffect(
//                getId() + 10000, // Offset ID to avoid conflicts
//                ownerId,
//                FieldEffectType.EXPLOSION,
//                getPosition(),
//                explosionRadius,
//                explosionDamage,
//                FieldEffectType.EXPLOSION.getDefaultDuration(),
//                ownerTeam
//        );
//    }

    /**
     * Get the arming progress as a percentage
     */
    public double getArmingPercent() {
        if (armingTime == 0) {
            return 1.0;
        }
        if (System.currentTimeMillis() > armingTime) {
            return 1.0;
        }
        long armingTimeRemaining = armingTime - System.currentTimeMillis();
        return (armingTime > 0 && armingTimeRemaining > 0)
                ? Math.max(0, (armingTime - armingTimeRemaining) / armingTime)
                : 1.0;
    }

    /**
     * Check if the mine is armed (for proximity mines)
     */
    public boolean isArmed() {
        return System.currentTimeMillis() > armingTime;
    }
}

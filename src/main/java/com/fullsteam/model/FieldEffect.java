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
    private final double duration;
    private final int ownerTeam;
    private final Set<Integer> affectedEntities; // Track which entities have been affected
    private final Map<Integer, Long> lastDamageTime; // Track last damage time for each player (in milliseconds)

    // Proximity mine specific fields
    private final double armingTime; // Time before mine becomes active (for PROXIMITY_MINE type)
    private final double triggerRadius; // Radius for triggering mine (for PROXIMITY_MINE type)
    private final double explosionDamage; // Damage for explosion (for PROXIMITY_MINE type)
    private final double explosionRadius; // Radius for explosion (for PROXIMITY_MINE type)
    
    private double timeRemaining;
    private double armingTimeRemaining;
    private boolean active;
    private boolean isArmed = false;
    private boolean hasTriggered = false;

    public FieldEffect(int id, int ownerId, FieldEffectType type, Vector2 position, double radius, double damage, double duration, int ownerTeam) {
        super(id, createFieldEffectBody(position, getEffectiveRadius(type, radius)), Double.POSITIVE_INFINITY); // Field effects are indestructible
        this.ownerId = ownerId;
        this.type = type;
        this.radius = radius;
        this.damage = damage;
        this.duration = duration;
        this.timeRemaining = duration;
        this.ownerTeam = ownerTeam;
        this.affectedEntities = new HashSet<>();
        this.lastDamageTime = new HashMap<>();
        this.active = true;
        
        // Initialize proximity mine specific fields
        if (type == FieldEffectType.PROXIMITY_MINE) {
            this.armingTime = 1.0; // 1 second arming time
            this.armingTimeRemaining = armingTime;
            this.triggerRadius = 40.0; // Trigger when enemy is within 40 units
            this.explosionDamage = 60.0; // High damage
            this.explosionRadius = 80.0; // Large explosion radius
        } else {
            this.armingTime = 0.0;
            this.armingTimeRemaining = 0.0;
            this.triggerRadius = 0.0;
            this.explosionDamage = 0.0;
            this.explosionRadius = 0.0;
            this.isArmed = true; // Non-mine effects are always "armed"
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
    
    private static double getEffectiveRadius(FieldEffectType type, double radius) {
        // For proximity mines, use a smaller body radius (visual size) but larger trigger radius
        if (type == FieldEffectType.PROXIMITY_MINE) {
            return 6.0; // Small mine visual size
        }
        return radius;
    }

    public void update(double deltaTime) {
        if (!active || hasTriggered) {
            return;
        }

        // Handle arming time for proximity mines
        if (type == FieldEffectType.PROXIMITY_MINE && !isArmed) {
            armingTimeRemaining -= deltaTime;
            if (armingTimeRemaining <= 0) {
                isArmed = true;
            }
        }

        timeRemaining -= deltaTime;
        if (timeRemaining <= 0) {
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
        return !active || timeRemaining <= 0;
    }

    public double getProgress() {
        return duration > 0 ? (duration - timeRemaining) / duration : 1.0;
    }
    
    // ===== Proximity Mine Specific Methods =====
    
    /**
     * Check if any enemy players are within trigger range (for proximity mines)
     */
    public boolean checkForTrigger(java.util.List<Player> players) {
        if (type != FieldEffectType.PROXIMITY_MINE || !isArmed || hasTriggered || !active) {
            return false;
        }

        Vector2 minePos = getPosition();
        
        for (Player player : players) {
            if (!canTriggerOnPlayer(player)) {
                continue;
            }

            double distance = minePos.distance(player.getPosition());
            if (distance <= triggerRadius) {
                return true; // Mine should explode
            }
        }

        return false;
    }
    
    /**
     * Check if a player can trigger this mine (for proximity mines)
     */
    private boolean canTriggerOnPlayer(Player player) {
        if (type != FieldEffectType.PROXIMITY_MINE) {
            return false;
        }
        
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }

        // Can't be triggered by the owner
        if (player.getId() == ownerId) {
            return false;
        }

        // In FFA mode (team 0), can be triggered by anyone except owner
        if (ownerTeam == 0 || player.getTeam() == 0) {
            return true;
        }

        // In team mode, can only be triggered by players on different teams
        return ownerTeam != player.getTeam();
    }
    
    /**
     * Trigger the mine and create an explosion field effect (for proximity mines)
     */
    public FieldEffect trigger() {
        if (type != FieldEffectType.PROXIMITY_MINE || hasTriggered) {
            return null;
        }

        hasTriggered = true;
        active = false;

        // Create explosion field effect
        return new FieldEffect(
                getId() + 10000, // Offset ID to avoid conflicts
                ownerId,
                FieldEffectType.EXPLOSION,
                getPosition(),
                explosionRadius,
                explosionDamage,
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                ownerTeam
        );
    }
    
    /**
     * Override damage handling - mines explode when damaged (for proximity mines)
     */
    @Override
    public boolean takeDamage(double damage) {
        if (type == FieldEffectType.PROXIMITY_MINE) {
            if (!active || hasTriggered) {
                return false;
            }
            // Any damage causes the mine to explode immediately
            health = 0;
            active = false;
            return true; // Mine was destroyed/exploded
        }
        
        // For other field effects, use default behavior
        return super.takeDamage(damage);
    }
    
    /**
     * Check if the mine has been triggered (for proximity mines)
     */
    public boolean hasTriggered() {
        return hasTriggered;
    }
    
    /**
     * Get the arming progress as a percentage (for proximity mines)
     */
    public double getArmingPercent() {
        if (type != FieldEffectType.PROXIMITY_MINE) {
            return 1.0;
        }
        
        if (isArmed) {
            return 1.0;
        }
        return armingTime > 0 ? Math.max(0, (armingTime - armingTimeRemaining) / armingTime) : 1.0;
    }
    
    /**
     * Check if the mine is armed (for proximity mines)
     */
    public boolean isArmed() {
        return isArmed;
    }
}

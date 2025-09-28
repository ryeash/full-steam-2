package com.fullsteam.physics;

import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Proximity mine that explodes when enemies get too close
 */
@Getter
@Setter
public class ProximityMine extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double triggerRadius;
    private final double explosionDamage;
    private final double explosionRadius;
    private final double lifespan;
    private final double armingTime; // Time before mine becomes active
    
    private double timeRemaining;
    private double armingTimeRemaining;
    private boolean isArmed = false;
    private boolean hasExploded = false;

    public ProximityMine(int id, int ownerId, int ownerTeam, Vector2 position, double lifespan) {
        super(id, createMineBody(position), 1.0); // Mines are fragile - 1 HP
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.triggerRadius = 40.0; // Trigger when enemy is within 40 units
        this.explosionDamage = 60.0; // High damage
        this.explosionRadius = 80.0; // Large explosion radius
        this.lifespan = lifespan;
        this.timeRemaining = lifespan;
        this.armingTime = 1.0; // 1 second arming time
        this.armingTimeRemaining = armingTime;
    }

    private static Body createMineBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(6.0); // Small mine
        body.addFixture(circle);
        body.setMass(MassType.INFINITE); // Stationary
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active || hasExploded) {
            return;
        }

        // Update arming time
        if (!isArmed) {
            armingTimeRemaining -= deltaTime;
            if (armingTimeRemaining <= 0) {
                isArmed = true;
            }
        }

        // Update lifespan
        timeRemaining -= deltaTime;
        if (timeRemaining <= 0) {
            active = false;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Check if any enemy players are within trigger range
     */
    public boolean checkForTrigger(java.util.List<Player> players) {
        if (!isArmed || hasExploded || !active) {
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
     * Check if a player can trigger this mine
     */
    private boolean canTriggerOnPlayer(Player player) {
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
     * Explode the mine and create a field effect
     */
    public FieldEffect explode() {
        if (hasExploded) {
            return null;
        }

        hasExploded = true;
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
     * Override damage handling - mines explode when damaged
     */
    @Override
    public boolean takeDamage(double damage) {
        if (!active || hasExploded) {
            return false;
        }

        // Any damage causes the mine to explode immediately
        health = 0;
        active = false;
        return true; // Mine was destroyed/exploded
    }

    /**
     * Check if the mine has expired
     */
    public boolean isExpired() {
        return !active || timeRemaining <= 0 || hasExploded;
    }

    /**
     * Get the remaining lifespan as a percentage
     */
    public double getLifespanPercent() {
        return lifespan > 0 ? Math.max(0, timeRemaining / lifespan) : 0;
    }

    /**
     * Get the arming progress as a percentage
     */
    public double getArmingPercent() {
        if (isArmed) {
            return 1.0;
        }
        return armingTime > 0 ? Math.max(0, (armingTime - armingTimeRemaining) / armingTime) : 1.0;
    }
}

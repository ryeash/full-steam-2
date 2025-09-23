package com.fullsteam.model;

import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a temporary field effect in the game world (explosions, fire, electric fields, etc.)
 */
@Getter
public class FieldEffect {
    private final int id;
    private final int ownerId;
    private final FieldEffectType type;
    private final Vector2 position;
    private final double radius;
    private final double damage;
    private final double duration;
    private final int ownerTeam;
    private final Set<Integer> affectedEntities; // Track which entities have been affected

    private double timeRemaining;
    private boolean active;

    public FieldEffect(int id, int ownerId, FieldEffectType type, Vector2 position, double radius, double damage, double duration, int ownerTeam) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.position = position.copy();
        this.radius = radius;
        this.damage = damage;
        this.duration = duration;
        this.timeRemaining = duration;
        this.ownerTeam = ownerTeam;
        this.affectedEntities = new HashSet<>();
        this.active = true;
    }

    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        timeRemaining -= deltaTime;
        if (timeRemaining <= 0) {
            active = false;
        }
    }

    public boolean isInRange(Vector2 targetPosition) {
        return position.distance(targetPosition) <= radius;
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

        // Team-based damage rules (same as projectiles)
        if (entity instanceof Player player) {

            // Can't damage self (though this should be rare for field effects)
            if (player.getId() == id) {
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

        double distance = position.distance(targetPosition);
        double intensity = 0.2 + (0.8 - (distance / radius)); // Linear falloff
        return Math.max(0.0, intensity);
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
}

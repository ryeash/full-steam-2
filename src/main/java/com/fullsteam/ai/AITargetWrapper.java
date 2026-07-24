package com.fullsteam.ai;

import com.fullsteam.physics.OwnedGameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;

/**
 * Wrapper class that adapts game entities for AI targeting.
 * This keeps AI-specific logic isolated to the AI package.
 */
@Getter
public class AITargetWrapper {
    private final OwnedGameEntity entity;
    private final TargetType type;

    public enum TargetType {
        PLAYER, TURRET
    }

    private AITargetWrapper(OwnedGameEntity entity, TargetType type) {
        this.entity = entity;
        this.type = type;
    }

    /**
     * Create a wrapper for a player
     */
    public static AITargetWrapper fromPlayer(Player player) {
        return new AITargetWrapper(player, TargetType.PLAYER);
    }

    /**
     * Create a wrapper for a turret
     */
    public static AITargetWrapper fromTurret(Turret turret) {
        return new AITargetWrapper(turret, TargetType.TURRET);
    }

    // Delegate methods to the wrapped entity
    public int getId() {
        return entity.getId();
    }

    public Vector2 getPosition() {
        return entity.getPosition();
    }

    public boolean isActive() {
        return entity.isActive();
    }

    public double getHealth() {
        return entity.getHealth();
    }

    public int getTeam() {
        if (entity instanceof Player player) {
            return player.getTeam();
        } else if (entity instanceof Turret turret) {
            return turret.getOwnerTeam();
        }
        return 0; // Default to FFA
    }

    public double getMaxHealth() {
        double maxHealth = entity.getMaxHealth();
        // Guard against divide-by-zero in callers that compute health ratios
        return maxHealth > 0 ? maxHealth : entity.getHealth();
    }

    /**
     * Current health as a fraction of max (0..1), safe against a zero max.
     */
    public double healthPercent() {
        return Math.max(0, getHealth() / getMaxHealth());
    }

    public int getOwnerId() {
        return this.entity.getOwnerId();
    }

    public Vector2 getVelocity() {
        if (entity instanceof Player player) {
            return player.getVelocity();
        }
        return new Vector2(0, 0); // Turrets and others don't move
    }

    public double getTargetPriority() {
        return switch (type) {
            case PLAYER -> 1.0; // Players are highest priority
            case TURRET -> 0.8; // Turrets are important but lower priority
        };
    }

    /**
     * Check whether this target is a teammate of (i.e. should NOT be attacked by) the given AI.
     * In FFA mode (team 0) the only "teammate" is the AI's own turret; everyone else is fair game.
     * In team mode, targets on the same team are teammates.
     */
    public boolean isTeammateOf(AIPlayer aiPlayer) {
        return this.entity.isFriendy(aiPlayer);
    }

    /**
     * Check if this wrapper represents a player
     */
    public boolean isPlayer() {
        return type == TargetType.PLAYER;
    }

    /**
     * Check if this wrapper represents a turret
     */
    public boolean isTurret() {
        return type == TargetType.TURRET;
    }
}

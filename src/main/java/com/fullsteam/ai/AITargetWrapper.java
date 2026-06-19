package com.fullsteam.ai;

import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import org.dyn4j.geometry.Vector2;

/**
 * Wrapper class that adapts game entities for AI targeting.
 * This keeps AI-specific logic isolated to the AI package.
 */
public class AITargetWrapper {
    private final GameEntity entity;
    private final TargetType type;

    public enum TargetType {
        PLAYER, TURRET
    }

    private AITargetWrapper(GameEntity entity, TargetType type) {
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

    public int getOwnerId() {
        if (entity instanceof Player player) {
            return player.getId(); // Players own themselves
        } else if (entity instanceof Turret turret) {
            return turret.getOwnerId(); // Turrets have an owner
        }
        return entity.getId(); // Fallback
    }

    public boolean isMovable() {
        return type == TargetType.PLAYER; // Only players can move
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

    public TargetType getType() {
        return type;
    }

    /**
     * Get the underlying entity (use sparingly)
     */
    public GameEntity getEntity() {
        return entity;
    }

    /**
     * Check whether this target is a teammate of (i.e. should NOT be attacked by) the given AI.
     * In FFA mode (team 0) the only "teammate" is the AI's own turret; everyone else is fair game.
     * In team mode, targets on the same team are teammates.
     */
    public boolean isTeammateOf(AIPlayer aiPlayer) {
        if (aiPlayer.getTeam() == 0) {
            return isTurret() && getOwnerId() == aiPlayer.getId();
        }
        return aiPlayer.getTeam() == getTeam();
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

    /**
     * Get as Player (throws if not a player)
     */
    public Player asPlayer() {
        if (!(entity instanceof Player player)) {
            throw new IllegalStateException("Entity is not a Player");
        }
        return player;
    }

    /**
     * Get as Turret (throws if not a turret)
     */
    public Turret asTurret() {
        if (!(entity instanceof Turret turret)) {
            throw new IllegalStateException("Entity is not a Turret");
        }
        return turret;
    }
}

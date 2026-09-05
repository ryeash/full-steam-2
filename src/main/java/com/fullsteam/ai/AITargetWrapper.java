package com.fullsteam.ai;

import com.fullsteam.physics.OwnedGameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Zombie;
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
        PLAYER, TURRET, ZOMBIE
    }

    public AITargetWrapper(OwnedGameEntity entity, TargetType type) {
        this.entity = entity;
        this.type = type;
    }

    /**
     * Create or retrieve a cached wrapper for a player
     */
    public static AITargetWrapper fromPlayer(Player player) {
        return player != null ? player.getTargetWrapper() : null;
    }

    /**
     * Create or retrieve a cached wrapper for a turret
     */
    public static AITargetWrapper fromTurret(Turret turret) {
        return turret != null ? turret.getTargetWrapper() : null;
    }

    /**
     * Create or retrieve a cached wrapper for a zombie
     */
    public static AITargetWrapper fromZombie(Zombie zombie) {
        return zombie != null ? zombie.getTargetWrapper() : null;
    }

    public static AITargetWrapper createDirect(OwnedGameEntity entity, TargetType type) {
        return new AITargetWrapper(entity, type);
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

    /**
     * Check if this target is visible (active and not obscured by smoke)
     */
    public boolean isVisible() {
        if (!entity.isActive()) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isVisionObscured();
        }
        return true;
    }

    public double getHealth() {
        return entity.getHealth();
    }

    public int getTeam() {
        if (entity instanceof Player player) {
            return player.getTeam();
        } else if (entity instanceof Turret turret) {
            return turret.getOwnerTeam();
        } else if (entity instanceof Zombie zombie) {
            return zombie.getOwnerTeam();
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
        } else if (entity instanceof Zombie zombie) {
            return zombie.getVelocity();
        }
        return new Vector2(0, 0); // Turrets and others don't move
    }

    public double getTargetPriority() {
        return switch (type) {
            case PLAYER -> 1.0; // Players are highest priority
            case TURRET -> 0.8; // Turrets are important but lower priority
            case ZOMBIE -> 0.9; // Zombies are immediate threats
        };
    }

    public boolean isPlayer() {
        return type == TargetType.PLAYER;
    }

    public boolean isTurret() {
        return type == TargetType.TURRET;
    }

    public boolean isZombie() {
        return type == TargetType.ZOMBIE;
    }

    public boolean isTeammateOf(Player player) {
        if (player == null || player.getTeam() == 0 || getTeam() == 0) {
            return false;
        }
        return getTeam() == player.getTeam();
    }

    /**
     * Safely downcast to Player. Returns null if this target is not a player.
     */
    public Player asPlayer() {
        return (entity instanceof Player player) ? player : null;
    }

    /**
     * Safely downcast to Turret. Returns null if this target is not a turret.
     */
    public Turret asTurret() {
        return (entity instanceof Turret turret) ? turret : null;
    }

    /**
     * Safely downcast to Zombie. Returns null if this target is not a zombie.
     */
    public Zombie asZombie() {
        return (entity instanceof Zombie zombie) ? zombie : null;
    }

    /**
     * Returns an estimated attack/threat range for this entity.
     * Turrets and Players return weapon range; Zombies return their melee lunger distance.
     */
    public double getAttackRange() {
        if (entity instanceof Player player && player.getCurrentWeapon() != null) {
            return player.getCurrentWeapon().getRange();
        } else if (entity instanceof Turret turret && turret.getWeapon() != null) {
            return turret.getWeapon().getRange();
        } else if (entity instanceof Zombie zombie) {
            return zombie.getLungeDistance() + 20.0;
        }
        return 100.0; // Default fallback distance
    }
}

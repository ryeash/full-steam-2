package com.fullsteam.model;

import com.fullsteam.Config;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.OwnedGameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public abstract class FieldEffect extends OwnedGameEntity {
    protected final FieldEffectType type;
    protected final double damage;
    protected final long armingTime;
    protected final Set<Integer> affectedEntities; // Track which entities have been affected
    protected final Map<Integer, Long> lastDamageTime; // Track last damage time for each player (in milliseconds)

    public FieldEffect(int ownerId,
                       int ownerTeam,
                       Body body,
                       double health,
                       FieldEffectType type,
                       double damage,
                       long armingTime) {
        super(Config.nextEntityId(), body, health, ownerId, ownerTeam);
        this.type = type;
        this.damage = damage;
        this.armingTime = armingTime;
        this.affectedEntities = new HashSet<>();
        this.lastDamageTime = new HashMap<>();
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
//                || !isInRange(entity.getPosition())
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

        if (entity instanceof Turret turret) {
            if (turret.getOwnerId() == ownerId) {
                return false;
            }
            if (ownerTeam == 0 || turret.getOwnerTeam() == 0) {
                return true;
            }
            return ownerTeam != turret.getOwnerTeam();
        }

        return true;
    }

    public void markAsAffected(GameEntity entity) {
        affectedEntities.add(entity.getId());
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

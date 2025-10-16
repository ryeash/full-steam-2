package com.fullsteam.physics;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Beam weapon class for all beam types (laser, plasma, heal beam, etc.)
 * Beams are instantaneous line-of-sight weapons that can apply damage in different ways
 * Similar to Projectile.java, this single class handles multiple beam types via Ordinance
 */
@Getter
@Setter
public class Beam extends GameEntity {
    protected final Vector2 startPoint;
    protected Vector2 endPoint;
    protected Vector2 effectiveEndPoint; // Actual end point after obstacle collision
    protected final Vector2 direction;
    protected final double range;
    protected final double damage;
    protected final int ownerId;
    protected final int ownerTeam;
    protected final DamageApplicationType damageApplicationType;
    protected final double damageInterval;
    protected final Ordinance ordinance; // Type of beam (laser, plasma, heal, etc.)
    protected final Set<BulletEffect> bulletEffects; // Special effects this beam has

    // Track affected players for DOT beams
    protected final Set<Integer> affectedPlayers = new HashSet<>();
    protected final Map<Integer, Long> lastDamageTime = new HashMap<>();


    public Beam(int id, Vector2 startPoint, Vector2 direction, double range, double damage,
                int ownerId, int ownerTeam, Ordinance ordinance, Set<BulletEffect> bulletEffects) {
        super(id, createBeamBody(startPoint, direction, range), Double.POSITIVE_INFINITY); // Beams don't have health
        this.startPoint = startPoint.copy();
        this.direction = direction.copy();
        this.direction.normalize();
        this.range = range;
        this.damage = damage;
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.ordinance = ordinance;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.damageApplicationType = ordinance.getDamageApplicationType();
        this.damageInterval = ordinance.getDamageInterval();
        this.expires = (long) (System.currentTimeMillis() + (1000 * ordinance.getBeamDuration()));

        // Calculate end point
        Vector2 offset = this.direction.copy();
        offset = offset.multiply(range);
        this.endPoint = startPoint.copy();
        this.endPoint.add(offset);

        // Initially, effective end point is the same as end point
        // This will be updated by GameManager after obstacle collision detection
        this.effectiveEndPoint = this.endPoint.copy();
    }

    private static Body createBeamBody(Vector2 startPoint, Vector2 direction, double range) {
        Body body = new Body();
        // Create a thin rectangle representing the beam line for collision detection
        Rectangle rectangle = new Rectangle(range, 2.0); // Very thin beam
        BodyFixture bodyFixture = body.addFixture(rectangle);
        bodyFixture.setSensor(true);
        body.setMass(MassType.INFINITE); // Stationary

        // Position and orient the beam
        Vector2 center = startPoint.copy();
        Vector2 offset = direction.copy();
        offset = offset.multiply(range / 2.0);
        center.add(offset);

        body.getTransform().setTranslation(center.x, center.y);
        body.getTransform().setRotation(Math.atan2(direction.y, direction.x));

        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        if (isExpired()) {
            active = false;
            return;
        }

        // Handle damage application based on type
        switch (damageApplicationType) {
            case INSTANT:
                // Instant damage is applied once during creation
                break;
            case DAMAGE_OVER_TIME:
                // DOT damage is now handled by GameManager with proper collision detection
                break;
        }
        super.update(deltaTime);
    }


    /**
     * Check if this beam can affect a specific player
     */
    public boolean canAffectPlayer(Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }

        // Can't affect the owner (unless it's a heal beam)
        if (player.getId() == ownerId && !isHealingBeam()) {
            return false;
        }

        // Team-based logic
        if (isHealingBeam()) {
            // Healing beams only affect allies (or self in FFA)
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return player.getId() == ownerId; // Only heal self in FFA
            }
            return ownerTeam == player.getTeam(); // Heal teammates
        } else {
            // Damage beams affect enemies
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return player.getId() != ownerId; // Damage anyone except self in FFA
            }
            return ownerTeam != player.getTeam(); // Damage enemies
        }
    }

    /**
     * Process continuous damage over time
     * Returns the damage amount to be applied by GameManager (negative for healing)
     */
    public double processContinuousDamage(Player player, double deltaTime) {
        if (!canAffectPlayer(player)) {
            return 0.0;
        }
        return switch (ordinance) {
            case PLASMA_BEAM -> damage * deltaTime;
            case HEAL_BEAM -> -(damage * deltaTime);
            default -> 0.0;
        };
    }


    /**
     * Check if this is a healing beam
     */
    public boolean isHealingBeam() {
        return ordinance == Ordinance.HEAL_BEAM;
    }

    /**
     * Check if this beam can pierce through players
     */
    public boolean canPiercePlayers() {
        return switch (ordinance) {
            case LASER, RAILGUN, PLASMA_BEAM, HEAL_BEAM ->
                    true; // Laser, Railgun, Plasma beam, and Heal beam pierce through players
            default -> false; // Other beams stop at first player hit
        };
    }

    /**
     * Check if this beam can pierce through obstacles
     */
    public boolean canPierceObstacles() {
        return ordinance == Ordinance.RAILGUN;
    }

    /**
     * Get all bullet effects for this beam
     */
    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }
}

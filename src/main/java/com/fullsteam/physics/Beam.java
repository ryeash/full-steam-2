package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Beam weapon class for all beam types (laser, plasma)
 * Beams are instantaneous line-of-sight weapons that can apply damage in different ways
 * Similar to Projectile.java, this single class handles multiple beam types via Ordinance
 */
@Getter
@Setter
public class Beam extends GameEntity {
    protected final Vector2 startPoint;
    protected Vector2 endPoint;
    protected Vector2 effectiveEndPoint;
    protected List<Vector2> path;
    protected final Vector2 direction;
    protected final double range;
    protected final double damage;
    protected final int ownerId;
    protected final int ownerTeam;
    protected final Ordinance ordinance;
    protected final Set<BulletEffect> bulletEffects;
    protected final double caliber;

    // Track affected players for DOT beams
    protected final Set<Integer> affectedPlayers = new HashSet<>();
    protected final Map<Integer, Long> lastDamageTime = new HashMap<>();

    public Beam(Vector2 startPoint,
                Vector2 direction,
                double range,
                double damage,
                int ownerId,
                int ownerTeam,
                Ordinance ordinance,
                Set<BulletEffect> bulletEffects,
                double caliber) {
        super(Config.nextEntityId(), createBeamBody(startPoint, direction, range, caliber), Double.POSITIVE_INFINITY);
        this.startPoint = startPoint.copy();
        this.direction = direction.copy();
        this.direction.normalize();
        this.range = range;
        this.damage = damage;
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.ordinance = ordinance;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.caliber = caliber;
        this.expires = (long) (System.currentTimeMillis() + (1000 * ordinance.getBeamDuration()));
        this.endPoint = startPoint.copy().add(this.direction.copy().multiply(range));
        this.effectiveEndPoint = this.endPoint.copy();
        this.path = new ArrayList<>(List.of(this.startPoint.copy(), this.endPoint.copy()));
    }

    /**
     * Set a single-segment effective endpoint (no bounces). Keeps {@link #path} in
     * sync as a straight [start, end] polyline. Used by straight beams and the
     * defense laser.
     */
    public void setEffectiveEndPoint(Vector2 effectiveEndPoint) {
        this.effectiveEndPoint = effectiveEndPoint;
        this.path = new ArrayList<>(List.of(startPoint.copy(), effectiveEndPoint.copy()));
    }

    /**
     * Set the full reflected polyline (BOUNCY beams). The last vertex becomes the
     * effective endpoint so existing single-point consumers keep working.
     */
    public void setPath(List<Vector2> path) {
        this.path = path;
        if (path != null && !path.isEmpty()) {
            this.effectiveEndPoint = path.get(path.size() - 1).copy();
        }
    }

    /**
     * Min interval between AOE-effect spawns for a continuous (DOT) beam.
     */
    private static final long AREA_EFFECT_INTERVAL_MS = 350;
    /**
     * Last time this beam spawned its AOE effects (for the DOT throttle).
     */
    private long lastAreaEffectTime = 0L;

    /**
     * Throttle for spawning a continuous beam's AOE field effects (fire, poison,
     * smoke, ...). Without this a DOT beam would spawn a fresh field every physics
     * tick; instead it leaves a bounded trail. Returns true — and arms the next
     * window — at most once per {@link #AREA_EFFECT_INTERVAL_MS}.
     */
    public boolean tryEmitAreaEffect(long now) {
        if (now - lastAreaEffectTime < AREA_EFFECT_INTERVAL_MS) {
            return false;
        }
        lastAreaEffectTime = now;
        return true;
    }

    /**
     * Base beam width at caliber 1.0; CALIBER is the only size input (matches render).
     */
    private static final double BASE_WIDTH = 2.0;

    /**
     * Rendered/physical beam width — driven entirely by the weapon's caliber.
     */
    public double getSize() {
        return BASE_WIDTH * caliber;
    }

    private static Body createBeamBody(Vector2 startPoint, Vector2 direction, double range, double caliber) {
        Body body = new Body();
        // Create a thin rectangle representing the beam line for collision detection;
        // caliber widens the beam.
        Rectangle rectangle = new Rectangle(range, BASE_WIDTH * caliber);
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
        super.update(deltaTime);
    }


    /**
     * Check if this beam can affect a specific player
     */
    public boolean canAffectPlayer(Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }
        // Can't affect the owner
        if (player.getId() == ownerId) {
            return false;
        }
        return ownerTeam == 0
                || player.getTeam() == 0
                || ownerTeam != player.getTeam();
    }

    /**
     * Check if this beam can affect a specific turret (same team/owner rules as canAffectPlayer).
     */
    public boolean canAffectTurret(Turret turret) {
        if (!turret.isActive()) {
            return false;
        }
        if (turret.getOwnerId() == ownerId) {
            return false;
        }
        if (ownerTeam == 0 || turret.getOwnerTeam() == 0) {
            return true;
        }
        return ownerTeam != turret.getOwnerTeam();
    }

    /**
     * Process continuous damage over time
     * Returns the damage amount to be applied by GameManager (negative for healing)
     */
    public double processContinuousDamage(Player player, double deltaTime) {
        if (!canAffectPlayer(player)) {
            return 0.0;
        }
        if (Objects.requireNonNull(ordinance) == Ordinance.PLASMA_BEAM) {
            return damage * deltaTime;
        }
        return 0.0;
    }

    /**
     * Get all bullet effects for this beam
     */
    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }
}

package com.fullsteam.model;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A beam weapon modelled as a {@link FieldEffect}.
 *
 * <p>LASER beams ({@link FieldEffectType#LASER}) are instantaneous: they apply full
 * damage once to every entity they intersect and then linger visually until they expire.
 * PLASMA beams ({@link FieldEffectType#PLASMA}) are continuous DOT weapons that deal
 * {@code damage * deltaTime} each physics tick to every entity in their path.
 *
 * <p>Both types support BOUNCY reflections and PIERCING pass-through via the
 * {@link #bulletEffects} set. The {@link #path} polyline records each reflected segment so
 * raycasting in WeaponSystem / GameManager covers the full travel path.
 *
 * <p>{@link #startPoint}, {@link #direction}, and {@link #endPoint} are intentionally
 * mutable {@link Vector2} instances so that {@code DefenseLaser} can rotate the arm beams
 * in-place each tick without allocating new objects.
 */
@Getter
@Setter
public class FieldEffectBeam extends FieldEffect {

    /**
     * Base beam width at caliber 1.0; caliber is the only size input (matches render).
     */
    public static final double BASE_WIDTH = 2.0;
    private static final long AREA_EFFECT_INTERVAL_MS = 350;

    protected final Vector2 startPoint;
    protected Vector2 endPoint;
    protected Vector2 effectiveEndPoint;
    protected List<Vector2> path;
    protected final Vector2 direction;
    protected final double range;
    protected final Set<BulletEffect> bulletEffects;
    protected final double caliber;

    private long lastAreaEffectTime = 0L;

    /**
     * Primary constructor matching the former {@code Beam} signature but using
     * {@link FieldEffectType} to select LASER vs. PLASMA.
     *
     * @param startPoint    origin of the beam in world coordinates
     * @param direction     unit (or non-unit) direction vector — normalised internally
     * @param range         maximum travel distance (units)
     * @param damage        instantaneous hit damage (LASER) or damage-per-second base (PLASMA)
     * @param ownerId       entity id of the firer
     * @param ownerTeam     team number of the firer (0 = FFA)
     * @param type          {@link FieldEffectType#LASER} or {@link FieldEffectType#PLASMA}
     * @param bulletEffects secondary effects (BOUNCY, PIERCING, INCENDIARY, …)
     * @param caliber       beam width multiplier (1.0 = {@link #BASE_WIDTH})
     */
    public FieldEffectBeam(Vector2 startPoint,
                           Vector2 direction,
                           double range,
                           double damage,
                           int ownerId,
                           int ownerTeam,
                           FieldEffectType type,
                           Set<BulletEffect> bulletEffects,
                           double caliber) {
        super(ownerId, ownerTeam,
                createBeamBody(startPoint, direction, range, caliber),
                Double.POSITIVE_INFINITY,
                type,
                damage,
                0L);
        this.startPoint = startPoint.copy();
        this.direction = direction.copy();
        this.direction.normalize();
        this.range = range;
        this.caliber = caliber;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.expires = (long) (System.currentTimeMillis() + (type.getDefaultDuration() * 1000));
        this.endPoint = this.startPoint.copy().add(this.direction.copy().multiply(range));
        this.effectiveEndPoint = this.endPoint.copy();
        this.path = new ArrayList<>(List.of(this.startPoint.copy(), this.endPoint.copy()));
        this.active = true;
    }

    // ── Path management ────────────────────────────────────────────────────────

    /**
     * Set a single-segment effective endpoint (no bounces). Keeps {@link #path} in
     * sync as a straight [start, end] polyline. Used by straight beams and the
     * DefenseLaser.
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
            this.effectiveEndPoint = path.getLast().copy();
        }
    }

    /**
     * Returns the {@link Ordinance} corresponding to this beam's type.
     * LASER → {@link Ordinance#LASER},
     * PLASMA → {@link Ordinance#PLASMA_BEAM}.
     */
    public Ordinance getOrdinance() {
        return type == FieldEffectType.PLASMA ? Ordinance.PLASMA_BEAM : Ordinance.LASER;
    }

    /**
     * Rendered/physical beam width — driven entirely by the weapon's caliber.
     */
    public double getSize() {
        return BASE_WIDTH * caliber;
    }

    /**
     * Alias for {@link #affectedEntities} so call sites that previously used
     * {@code beam.getAffectedPlayers()} continue to work without change.
     */
    public Set<Integer> getAffectedPlayers() {
        return affectedEntities;
    }

    /**
     * Throttle for spawning a continuous beam's AOE field effects (fire, poison,
     * smoke, …). Returns {@code true} — and arms the next window — at most once per
     * {@link #AREA_EFFECT_INTERVAL_MS}.
     */
    public boolean tryEmitAreaEffect(long now) {
        if (now - lastAreaEffectTime < AREA_EFFECT_INTERVAL_MS) {
            return false;
        }
        lastAreaEffectTime = now;
        return true;
    }

    // ── Targeting checks ──────────────────────────────────────────────────────

    /**
     * Whether this beam can affect a player — same owner/team rules as the former
     * {@code Beam.canAffectPlayer}.
     */
    public boolean canAffectPlayer(com.fullsteam.physics.Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }
        if (player.getId() == ownerId) {
            return false;
        }
        return ownerTeam == 0 || player.getTeam() == 0 || ownerTeam != player.getTeam();
    }

    /**
     * Whether this beam can affect a turret — same owner/team rules as the former
     * {@code Beam.canAffectTurret}.
     */
    public boolean canAffectTurret(com.fullsteam.physics.Turret turret) {
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

    private static Body createBeamBody(Vector2 startPoint, Vector2 direction, double range, double caliber) {
        Body body = new Body();
        Rectangle rectangle = new Rectangle(range, BASE_WIDTH * caliber);
        BodyFixture bodyFixture = body.addFixture(rectangle);
        bodyFixture.setSensor(true);
        body.setMass(MassType.INFINITE);

        Vector2 normDir = direction.copy();
        normDir.normalize();
        Vector2 center = startPoint.copy().add(normDir.multiply(range / 2.0));
        body.getTransform().setTranslation(center.x, center.y);
        body.getTransform().setRotation(Math.atan2(normDir.y, normDir.x));

        return body;
    }
}

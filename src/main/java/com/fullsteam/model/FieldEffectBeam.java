package com.fullsteam.model;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.Epsilon;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

/**
 * A beam weapon modelled as a {@link FieldEffect}.
 *
 * <p>LASER beams ({@link FieldEffectType#LASER}) are instantaneous: they apply full
 * damage once to every entity they intersect and then linger visually until they expire.
 * PLASMA beams ({@link FieldEffectType#PLASMA}) are continuous DOT weapons that deal
 * {@code damage * deltaTime} each physics tick to every entity in their path.
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

    protected Vector2 startPoint;
    protected Vector2 endPoint;
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
        this.active = true;
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
     * Recalculates and updates the physics body's translation, rotation, and fixture shape
     * based on the current {@link #startPoint} and {@link #endPoint}.
     */
    public void updateBodyTransform() {
        Vector2 start = startPoint;
        Vector2 end = endPoint;
        double currentLength = start.distance(end);
        Vector2 dir = end.copy().subtract(start);

        if (dir.getMagnitudeSquared() > Epsilon.E) {
            dir.normalize();
            this.direction.set(dir);
        } else {
            dir = this.direction.copy();
            if (dir.getMagnitudeSquared() > Epsilon.E) {
                dir.normalize();
            } else {
                dir = new Vector2(1, 0);
            }
        }

        Vector2 center = start.copy().add(dir.copy().multiply(currentLength / 2.0));
        double angle = Math.atan2(dir.y, dir.x);

        Body body = getBody();
        body.getTransform().setTranslation(center.x, center.y);
        body.getTransform().setRotation(angle);

        double rectLength = Math.max(0.1, currentLength);
        if (body.getFixtureCount() > 0) {
            body.removeFixture(0);
        }
        Rectangle rect = new Rectangle(rectLength, BASE_WIDTH * caliber);
        BodyFixture fixture = body.addFixture(rect);
        fixture.setSensor(true);
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

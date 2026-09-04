package com.fullsteam.games.weapon;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Zombie;
import org.dyn4j.Epsilon;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.DetectFilter;
import org.dyn4j.world.World;
import org.dyn4j.world.result.RaycastResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates beam raycasting, obstacle intersections, and reflections/bounces.
 */
public class BeamPathCalculator {
    public static final double BEAM_RANGE_PENALTY = 0.6;
    private static final int MAX_BEAM_BOUNCES = 3;
    private static final double BEAM_BOUNCE_EPSILON = 0.5;

    private final World<Body> world;

    public BeamPathCalculator(World<Body> world) {
        this.world = world;
    }

    /**
     * Compute a beam's full path as a polyline of vertices [start, …, end].
     */
    public List<Vector2> computeBeamPath(FieldEffectBeam beam) {
        List<Vector2> path = new ArrayList<>();
        Vector2 p = beam.getStartPoint().copy();
        Vector2 d = beam.getDirection().copy();
        d.normalize();
        double remaining = beam.getRange();
        boolean bouncy = beam.getBulletEffects().contains(BulletEffect.BOUNCY);
        int maxBounces = bouncy ? MAX_BEAM_BOUNCES : 0;

        path.add(p.copy());
        for (int bounce = 0; ; bounce++) {
            RaycastResult<Body, ?> hit = closestBlockingObstacle(beam, p, d, remaining);
            if (hit == null) {
                path.add(p.copy().add(d.copy().multiply(remaining)));
                break;
            }
            Vector2 hitPoint = hit.getRaycast().getPoint().copy();
            path.add(hitPoint.copy());
            if (bounce >= maxBounces) {
                break;
            }
            Vector2 n = hit.getRaycast().getNormal().copy();
            if (n.getMagnitude() <= Epsilon.E) {
                break;
            }
            n.normalize();
            d = d.subtract(n.multiply(2 * d.dot(n)));
            d.normalize();
            remaining -= p.distance(hitPoint);
            if (remaining <= BEAM_BOUNCE_EPSILON) {
                break;
            }
            p = hitPoint.copy().add(d.copy().multiply(BEAM_BOUNCE_EPSILON));
        }
        return path;
    }

    private RaycastResult<Body, ?> closestBlockingObstacle(FieldEffectBeam beam, Vector2 p, Vector2 d, double maxDistance) {
        return world.raycast(new Ray(p, d), maxDistance, new DetectFilter<>(false, true, null))
                .stream()
                .filter(result -> shouldEntityBlockBeam(beam, result.getBody().getUserData(), d))
                .min(Comparator.comparing(result -> result.getRaycast().getDistance()))
                .orElse(null);
    }

    private boolean shouldEntityBlockBeam(FieldEffectBeam beam, Object entity, Vector2 d) {
        boolean isArmorPiercing = beam.getBulletEffects().contains(BulletEffect.PIERCING);
        return switch (entity) {
            case FieldEffect fieldEffect -> {
                if (fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER) {
                    yield !isArmorPiercing;
                }
                yield false;
            }
            case Obstacle _ -> true;
            case Player player ->
                    !isArmorPiercing && beam.canAffect(player) && CollisionProcessor.isBlockedByRiotShield(player, d);
            case Projectile _, NetProjectile _, Turret _, Oddball _, Zombie _,
                 KothZone _, DefenseLaser _, Headquarters _, Flag _ -> false;
            case null -> false;
            default -> true;
        };
    }
}

package com.fullsteam.games;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.DetectFilter;
import org.dyn4j.world.World;
import org.dyn4j.world.result.RaycastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Manages all weapon-related functionality including primary weapons, projectiles, and beams.
 * This system handles weapon firing, ammunition, reloading, and projectile/beam creation.
 */
public class WeaponSystem {
    private static final Logger log = LoggerFactory.getLogger(WeaponSystem.class);

    private final GameEntities gameEntities;
    private final World<Body> world;
    private final BulletEffectProcessor bulletEffectProcessor;
    private final BiConsumer<Player, Integer> killCallback;

    public WeaponSystem(GameEntities gameEntities, World<Body> world, BiConsumer<Player, Integer> killCallback) {
        this.gameEntities = gameEntities;
        this.world = world;
        this.killCallback = killCallback;
        this.bulletEffectProcessor = new BulletEffectProcessor(gameEntities);
    }

    /**
     * Process primary weapon input for a player.
     * Handles both projectile-based and beam-based weapons.
     */
    public void handlePrimaryFire(Player player, PlayerInput input) {
        if (!input.isLeft()) {
            return;
        }

        if (player.getCurrentWeapon().getOrdinance().isBeamType()) {
            handleBeamFire(player);
        } else {
            handleProjectileFire(player);
        }
    }

    /**
     * Handle firing of beam weapons. Supports multiple beams per shot.
     * Each beam has its path computed (straight, obstacle-blocked, or BOUNCY-reflected),
     * is registered as a FieldEffect, and — for LASER — has its instant damage applied
     * immediately.
     */
    private void handleBeamFire(Player player) {
        for (FieldEffectBeam beam : player.shootBeam()) {
            handleBeamFire(beam);
        }
    }

    /**
     * Handles ray-casting and bouncy/piercing traits of the beam.
     */
    public void handleBeamFire(FieldEffectBeam beam) {
        List<Vector2> vector2s = computeBeamPath(beam);
        for (int i = 0; i < vector2s.size() - 1; i++) {
            Vector2 start = vector2s.get(i);
            Vector2 end = vector2s.get(i + 1);
            FieldEffectBeam beamSegment = createBeamSegment(start, end, beam);
            gameEntities.add(beamSegment);
        }
    }

    public FieldEffectBeam createBeamSegment(Vector2 start, Vector2 end, FieldEffectBeam beam) {
        double range = start.distance(end);
        Vector2 direction = end.copy().subtract(start);
        return new FieldEffectBeam(start, direction, range, beam.getDamage(),
                beam.getOwnerId(), beam.getOwnerTeam(), beam.getType(), beam.getBulletEffects(), beam.getCaliber());
    }

    /**
     * Handle firing of projectile weapons.
     */
    private void handleProjectileFire(Player player) {
        List<Projectile> projectiles = player.shoot();

        for (Projectile projectile : projectiles) {
            if (projectile != null) {
                gameEntities.add(projectile);
            }
        }

        if (!projectiles.isEmpty()) {
            log.debug("Player {} fired {} projectile(s): {}",
                    player.getId(), projectiles.size(), player.getCurrentWeapon().getName());
        }
    }

    /**
     * Find where a beam intersects with obstacles.
     * Returns the effective end point of the beam (either full range or obstacle intersection).
     */
    public Vector2 findBeamObstacleIntersection(Vector2 startPoint, Vector2 endPoint) {
        Vector2 direction = endPoint.copy().subtract(startPoint);
        double maxDistance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(startPoint, direction);

        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                maxDistance,
                new DetectFilter<>(true, true, null)
        );

        boolean hit = !results.isEmpty();

        if (!hit) {
            return endPoint; // No obstacles, beam reaches full range
        }

        double closestDistance = maxDistance;
        for (RaycastResult<Body, ?> result : results) {
            Body body = result.getBody();
            Object userData = body.getUserData();

            if (userData instanceof Obstacle) {
                double distance = result.getRaycast().getDistance();
                if (distance < closestDistance) {
                    closestDistance = distance;
                }
            }
        }

        Vector2 effectiveEnd = startPoint.copy();
        effectiveEnd.add(direction.copy().multiply(closestDistance));
        return effectiveEnd;
    }

    /**
     * Max reflections for a BOUNCY beam, and the nudge off a surface after a bounce.
     */
    private static final int MAX_BEAM_BOUNCES = 3;
    private static final double BEAM_BOUNCE_EPSILON = 0.5;

    /**
     * Compute a beam's full path as a polyline of vertices [start, …, end].
     *
     * <p>Normal beams produce a straight two-point path (start → closest blocking
     * obstacle, or full range). A BOUNCY beam (that isn't also PIERCING — piercing
     * passes through obstacles and wins) reflects off each obstacle's surface
     * normal, accumulating a vertex per bounce, up to {@link #MAX_BEAM_BOUNCES} or
     * until its range is spent. Only obstacles reflect; players/turrets are passed
     * through here and damaged later in the per-segment damage pass.
     */
    public List<Vector2> computeBeamPath(FieldEffectBeam beam) {
        List<Vector2> path = new ArrayList<>();
        Vector2 p = beam.getStartPoint().copy();
        Vector2 d = beam.getDirection().copy();
        d.normalize();
        double remaining = beam.getRange();
        boolean bouncy = beam.getBulletEffects().contains(BulletEffect.BOUNCY)
                && !beam.getBulletEffects().contains(BulletEffect.PIERCING); // piercing wins
        int maxBounces = bouncy ? MAX_BEAM_BOUNCES : 0;

        path.add(p.copy());
        for (int bounce = 0; ; bounce++) {
            RaycastResult<Body, ?> hit = closestBlockingObstacle(beam, p, d, remaining);
            if (hit == null) {
                // Nothing to stop it — beam runs to the end of its remaining range.
                path.add(p.copy().add(d.copy().multiply(remaining)));
                break;
            }
            Vector2 hitPoint = hit.getRaycast().getPoint().copy();
            path.add(hitPoint.copy());
            if (bounce >= maxBounces) {
                break; // out of bounces (or a non-bouncy beam stops at the wall)
            }
            Vector2 n = hit.getRaycast().getNormal().copy();
            if (n.getMagnitude() == 0) {
                break; // degenerate surface normal; stop here
            }
            n.normalize();
            // Reflect: r = d − 2(d·n)n
            d = d.subtract(n.multiply(2 * d.dot(n)));
            d.normalize();
            remaining -= p.distance(hitPoint);
            if (remaining <= BEAM_BOUNCE_EPSILON) {
                break;
            }
            // Resume just off the surface so we don't immediately re-hit it.
            p = hitPoint.copy().add(d.copy().multiply(BEAM_BOUNCE_EPSILON));
        }
        return path;
    }

    /**
     * Closest obstacle that blocks {@code beam} along ray (p, d) within maxDistance, or null.
     */
    private RaycastResult<Body, ?> closestBlockingObstacle(FieldEffectBeam beam, Vector2 p, Vector2 d, double maxDistance) {
        Ray ray = new Ray(p, d);
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray, maxDistance, new DetectFilter<>(true, true, null));
        RaycastResult<Body, ?> closest = null;
        double closestDistance = maxDistance;
        for (RaycastResult<Body, ?> result : results) {
            if (shouldEntityBlockBeam(beam, result.getBody().getUserData())) {
                double distance = result.getRaycast().getDistance();
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = result;
                }
            }
        }
        return closest;
    }

    /**
     * Check if an entity should block a beam based on the beam's piercing behavior.
     */
    private boolean shouldEntityBlockBeam(FieldEffectBeam beam, Object entity) {
        return switch (entity) {
            case Obstacle _ -> !beam.getBulletEffects().contains(BulletEffect.PIERCING);
            case FieldEffect fieldEffect -> {
                if (fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER) {
                    yield !beam.getBulletEffects().contains(BulletEffect.PIERCING);
                }
                yield false;
            }
            case Player _, Projectile _, NetProjectile _, Turret _, Oddball _ -> false;
            case null -> false;
            default -> true;
        };
    }

    /**
     * Shared firing helper used by Turret, Oddball, and any other non-player entity.
     * Returns the list of newly created game entities (Projectile or FieldEffectBeam).
     */
    public static List<GameEntity> fireWeapon(int ownerId, int ownerTeam, Weapon weapon, Vector2 position, Vector2 direction) {
        double baseAngle = Math.atan2(direction.y, direction.x);
        double spread = (1.0 - weapon.getAccuracy()) * 0.17;
        int shots = Math.max(1, weapon.getBulletsPerShot());
        List<GameEntity> fired = new ArrayList<>(shots);
        double angle = baseAngle;
        for (int i = 0; i < shots; i++) {
            angle += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * spread;
            Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));
            Vector2 jitter = new Vector2(
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0,
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0);
            fired.add(weapon.getOrdinance().isBeamType()
                    ? fireBeam(ownerId, ownerTeam, weapon, position, aimDir)
                    : fireProjectile(ownerId, ownerTeam, weapon, position.copy().add(jitter), aimDir));
        }
        return fired;
    }

    private static FieldEffectBeam fireBeam(int ownerId, int ownerTeam, Weapon weapon, Vector2 pos, Vector2 dir) {
        FieldEffectType type = weapon.getOrdinance() == Ordinance.PLASMA_BEAM
                ? FieldEffectType.PLASMA
                : FieldEffectType.LASER;
        FieldEffectBeam beam = new FieldEffectBeam(
                pos,
                dir,
                weapon.getRange(),
                weapon.getDamage(),
                ownerId,
                ownerTeam,
                type,
                weapon.getBulletEffects(),
                weapon.getCaliber()
        );
        // TODO: how to path this?
//        beam.setPath(List.of(pos.copy(), pos.copy().add(dir.copy().multiply(weapon.getRange()))));
        return beam;
    }

    private static Projectile fireProjectile(int ownerId, int ownerTeam, Weapon weapon, Vector2 pos, Vector2 dir) {
        Vector2 vel = dir.copy().multiply(weapon.getProjectileSpeed());
        return new Projectile(
                ownerId,
                pos,
                vel,
                weapon.getDamagePerBullet(),
                weapon.getRange(),
                ownerTeam,
                weapon.getLinearDamping(),
                weapon.getBulletEffects(),
                weapon.getOrdinance(),
                weapon.getCaliber(),
                weapon.getKnockbackPerBullet()
        );
    }
}

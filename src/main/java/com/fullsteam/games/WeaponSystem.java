package com.fullsteam.games;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import org.dyn4j.Epsilon;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages all weapon-related functionality including primary weapons, projectiles, and beams.
 * This system handles weapon firing, ammunition, reloading, and projectile/beam creation.
 */
public class WeaponSystem {
    private static final Logger log = LoggerFactory.getLogger(WeaponSystem.class);

    // flat 60% penalty for beams
    public static final double BEAM_RANGE_PENALTY = 0.6;

    private final GameEntities gameEntities;
    private final World<Body> world;

    public WeaponSystem(GameEntities gameEntities, World<Body> world) {
        this.gameEntities = gameEntities;
        this.world = world;
    }

    /**
     * Process primary weapon input for a player.
     * Handles both projectile-based and beam-based weapons.
     */
    public void handlePrimaryFire(Player player, PlayerInput input) {
        // TODO: embrace HasWeapon?
        if (!input.isLeft()) {
            return;
        }
        Weapon weapon = player.getCurrentWeapon(); // Always use primary weapon
        if (!player.canShoot()) {
            if (!player.isReloading() && weapon.getCurrentAmmo() <= 0) {
                player.startReload();
            }
            return;
        }
        player.setLastShotTime(System.currentTimeMillis());
        Vector2 pos = player.getPosition();
        Vector2 baseDirection = player.getAimDirection().copy();
        baseDirection.normalize();
        double radius = player.getRadius();
        Vector2 startPos = pos.copy().add(baseDirection.copy().multiply(radius));
        int bulletsPerShot = weapon.getBulletsPerShot();
        int actualBulletsToFire = Math.min(bulletsPerShot, weapon.getCurrentAmmo());
        weapon.setCurrentAmmo(weapon.getCurrentAmmo() - actualBulletsToFire);
        List<GameEntity> ordinance = WeaponSystem.fireWeapon(player.getOwnerId(), player.getOwnerTeam(), player.getWeapon(), startPos, baseDirection);
        handleOrdinanceFiring(ordinance);
    }

    public void handleTurretFire(Turret turret) {
        if (turret.getCurrentTarget() == null || !turret.canFire()) {
            return;
        }
        turret.setLastShotTime(System.currentTimeMillis());
        Vector2 targetPos = turret.getCurrentTarget().getPosition();
        Vector2 turretPos = turret.getPosition();
        Vector2 fireDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
        if (fireDirection.getMagnitude() == 0) {
            return;
        }
        fireDirection.normalize();
        double baseAngle = Math.atan2(fireDirection.y, fireDirection.x);
        turret.setRotation(baseAngle);
        List<GameEntity> ordinance = WeaponSystem.fireWeapon(turret.getOwnerId(), turret.getOwnerTeam(), turret.getWeapon(), turretPos, fireDirection);
        handleOrdinanceFiring(ordinance);
    }

    public void handleOddballFire(Oddball oddball) {
        if (oddball.getCurrentTarget() == null || !oddball.isActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - oddball.getLastShotTime() < (long) (1000.0 / oddball.getWeapon().getFireRate())) {
            return;
        }
        oddball.setLastShotTime(now);

        Vector2 myPos = oddball.getPosition();
        Vector2 targetPos = oddball.getCurrentTarget().getPosition();
        boolean beamType = oddball.getWeapon().getOrdinance().isBeamType();

        // Beams are hitscan, so aim straight at the target. Projectiles travel at a
        // finite speed, so lead the target based on its velocity to intercept it.
        Vector2 dir = beamType
                ? new Vector2(targetPos.x - myPos.x, targetPos.y - myPos.y)
                : predictInterceptDirection(myPos, targetPos, oddball.getCurrentTarget().getVelocity(), oddball.getWeapon().getProjectileSpeed());
        if (dir.getMagnitude() == 0) {
            return;
        }
        List<GameEntity> ordinance = WeaponSystem.fireWeapon(-oddball.getId(), 0, oddball.getWeapon(), myPos, dir.getNormalized());
        handleOrdinanceFiring(ordinance);
    }

    private void handleOrdinanceFiring(List<GameEntity> ordinance) {
        for (GameEntity gameEntity : ordinance) {
            if (gameEntity instanceof FieldEffectBeam beam) {
                handleBeamFire(beam);
            } else {
                gameEntities.add(gameEntity);
            }
        }
    }

    /**
     * Handles ray-casting and bouncy/piercing traits of the beam.
     */
    private void handleBeamFire(FieldEffectBeam beam) {
        List<Vector2> vector2s = computeBeamPath(beam);
        for (int i = 0; i < vector2s.size() - 1; i++) {
            Vector2 start = vector2s.get(i);
            Vector2 end = vector2s.get(i + 1);
            double range = start.distance(end);
            Vector2 direction = end.copy().subtract(start);
            FieldEffectBeam beamSegment = new FieldEffectBeam(
                    start,
                    direction,
                    range,
                    beam.getDamage(),
                    beam.getOwnerId(),
                    beam.getOwnerTeam(),
                    beam.getType(),
                    beam.getBulletEffects(),
                    beam.getCaliber());
            gameEntities.add(beamSegment);
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
                new DetectFilter<>(false, true, null)
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
            if (n.getMagnitude() <= Epsilon.E) {
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
        return world.raycast(new Ray(p, d), maxDistance, new DetectFilter<>(false, true, null))
                .stream()
                .filter(result -> shouldEntityBlockBeam(beam, result.getBody().getUserData()))
                .min(Comparator.comparing(result -> result.getRaycast().getDistance()))
                .orElse(null);
    }

    /**
     * Check if an entity should block a beam based on the beam's piercing behavior.
     */
    private boolean shouldEntityBlockBeam(FieldEffectBeam beam, Object entity) {
        // nothing stops the piercing beams
        if (beam.getBulletEffects().contains(BulletEffect.PIERCING)) {
            return false;
        }
        return switch (entity) {
            case FieldEffect fieldEffect -> fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER;
            case Obstacle _ -> true;
            case Player _, Projectile _, NetProjectile _, Turret _, Oddball _, KothZone _, DefenseLaser _, Headquarters _, Flag _ -> false;
            case null -> false;
            default -> true; // the world boundaries
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
        double rolledDamage = weapon.rollDamage();
        double rolledDamagePerBullet = Weapon.damagePerBullet(rolledDamage, shots);
        List<GameEntity> fired = new ArrayList<>(shots);
        double angle = baseAngle;
        for (int i = 0; i < shots; i++) {
            angle += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * spread;
            Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));
            Vector2 jitter = new Vector2(
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0,
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0);
            fired.add(weapon.getOrdinance().isBeamType()
                    ? fireBeam(ownerId, ownerTeam, weapon, position, aimDir, rolledDamage)
                    : fireProjectile(ownerId, ownerTeam, weapon, position.copy().add(jitter), aimDir, rolledDamagePerBullet));
        }
        return fired;
    }

    private static FieldEffectBeam fireBeam(int ownerId, int ownerTeam, Weapon weapon, Vector2 pos, Vector2 dir, double damage) {
        return new FieldEffectBeam(
                pos,
                dir,
                weapon.getRange() * BEAM_RANGE_PENALTY,
                damage,
                ownerId,
                ownerTeam,
                weapon.getOrdinance() == Ordinance.PLASMA_BEAM
                        ? FieldEffectType.PLASMA
                        : FieldEffectType.LASER,
                weapon.getBulletEffects(),
                weapon.getCaliber()
        );
    }

    private static Projectile fireProjectile(int ownerId, int ownerTeam, Weapon weapon, Vector2 pos, Vector2 dir, double damagePerBullet) {
        return new Projectile(
                ownerId,
                pos,
                dir.copy().multiply(weapon.getProjectileSpeed()),
                damagePerBullet,
                weapon.getRange(),
                ownerTeam,
                weapon.getLinearDamping(),
                weapon.getBulletEffects(),
                weapon.getOrdinance(),
                weapon.getCaliber(),
                weapon.getKnockbackPerBullet()
        );
    }

    /**
     * Compute the aim direction that leads a moving target so a projectile fired at
     * {@code projectileSpeed} intercepts it. Solves the quadratic for the earliest
     * positive intercept time; falls back to aiming at the target's current position
     * when no valid intercept exists (e.g. target outrunning the projectile).
     */
    private Vector2 predictInterceptDirection(Vector2 shooterPos, Vector2 targetPos, Vector2 targetVel, double projectileSpeed) {
        Vector2 toTarget = new Vector2(targetPos.x - shooterPos.x, targetPos.y - shooterPos.y);
        if (projectileSpeed <= 0.0) {
            return toTarget; // no meaningful travel time; aim directly
        }

        // Solve |toTarget + targetVel * t| = projectileSpeed * t for the smallest t > 0.
        double a = targetVel.dot(targetVel) - projectileSpeed * projectileSpeed;
        double b = 2.0 * toTarget.dot(targetVel);
        double c = toTarget.dot(toTarget);

        double t;
        if (Math.abs(a) < Epsilon.E) {
            // Target speed ~= projectile speed: quadratic degenerates to linear.
            if (Math.abs(b) < Epsilon.E) {
                return toTarget;
            }
            t = -c / b;
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc < 0.0) {
                return toTarget; // no real intercept
            }
            double sqrtDisc = Math.sqrt(disc);
            double t1 = (-b - sqrtDisc) / (2.0 * a);
            double t2 = (-b + sqrtDisc) / (2.0 * a);
            // Prefer the earliest positive intercept time.
            t = smallestPositive(t1, t2);
        }

        if (t <= 0.0 || !Double.isFinite(t)) {
            return toTarget;
        }

        return new Vector2(
                targetPos.x + targetVel.x * t - shooterPos.x,
                targetPos.y + targetVel.y * t - shooterPos.y);
    }

    private static double smallestPositive(double t1, double t2) {
        return t1 > 0.0 && t2 > 0.0
                ? Math.min(t1, t2)
                : Math.max(t1, t2);
    }
}

package com.fullsteam.games;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import lombok.Setter;
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
    @Setter
    private BiConsumer<Player, Player> killCallback;

    public WeaponSystem(GameEntities gameEntities, World<Body> world) {
        this.gameEntities = gameEntities;
        this.world = world;
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

        // Check if weapon fires beams or projectiles
        if (player.getCurrentWeapon().getOrdinance().isBeamType()) {
            handleBeamFire(player);
        } else {
            handleProjectileFire(player);
        }
    }

    /**
     * Handle firing of beam weapons. Supports multiple beams per shot.
     */
    private void handleBeamFire(Player player) {
        List<Beam> beams = player.shootBeam();

        for (Beam beam : beams) {
            // Compute the beam's path — a straight [start, end] for normal beams, or
            // a reflected polyline for BOUNCY beams. setPath keeps effectiveEndPoint
            // (the last vertex) in sync for single-point consumers.
            beam.setPath(computeBeamPath(beam));
            gameEntities.add(beam);
            if (beam.getOrdinance() == Ordinance.LASER) {
                processStandardBeamHit(beam);
            }
        }

        if (!beams.isEmpty()) {
            log.debug("Player {} fired {} beam(s): {}", player.getId(),
                    beams.size(), player.getCurrentWeapon().getName());
        }
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

        // Raycast to find obstacles
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                maxDistance,
                new DetectFilter<>(true, true, null)
        );

        boolean hit = !results.isEmpty();

        if (!hit) {
            return endPoint; // No obstacles, beam reaches full range
        }

        // Find the closest obstacle intersection
        double closestDistance = maxDistance;
        for (RaycastResult<Body, ?> result : results) {
            Body body = result.getBody();
            Object userData = body.getUserData();

            // Only obstacles block beams (not players or other entities)
            if (userData instanceof Obstacle) {
                double distance = result.getRaycast().getDistance();
                if (distance < closestDistance) {
                    closestDistance = distance;
                }
            }
        }

        // Calculate effective end point
        Vector2 effectiveEnd = startPoint.copy();
        effectiveEnd.add(direction.copy().multiply(closestDistance));
        return effectiveEnd;
    }

    /**
     * Find where a beam intersects with obstacles, considering beam-specific piercing behavior.
     * Returns the effective end point of the beam (either full range or obstacle intersection).
     */
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
    private List<Vector2> computeBeamPath(Beam beam) {
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
    private RaycastResult<Body, ?> closestBlockingObstacle(Beam beam, Vector2 p, Vector2 d, double maxDistance) {
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
    private boolean shouldEntityBlockBeam(Beam beam, Object entity) {
        return switch (entity) {
            // Projectiles should never block beams - they're small, fast-moving objects
            // Net projectiles should never block beams
            case Player _, Projectile _, NetProjectile _, Turret _ -> false;
            case null -> false;
            case Obstacle _ -> !beam.getBulletEffects().contains(BulletEffect.PIERCING);
            case FieldEffect fieldEffect -> {
                // Handle shield barriers - block non-piercing beams
                if (fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER) {
                    // Shield barriers block non-piercing beams
                    // but allow piercing beams to pass through
                    yield !beam.getBulletEffects().contains(BulletEffect.PIERCING);
                }
                // Other field effects don't block beams
                yield false;
                // Other field effects don't block beams
            }
            default -> true;
        };
    }

    /**
     * Process standard beam hits (laser, etc.)
     */
    public void processStandardBeamHit(Beam beam) {
        // Walk each segment of the beam's path (one for a straight beam, more for a
        // BOUNCY beam). The beam's affectedPlayers set dedups entities that lie on
        // more than one segment so nothing is hit twice by the same beam.
        List<Vector2> path = beam.getPath();
        if (path == null || path.size() < 2) {
            return;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            damageAlongSegment(beam, path.get(i), path.get(i + 1));
        }
    }

    /**
     * Apply a beam's instant damage to every affectable entity along one segment.
     */
    private void damageAlongSegment(Beam beam, Vector2 segStart, Vector2 segEnd) {
        Vector2 direction = segEnd.copy().subtract(segStart);
        double distance = direction.getMagnitude();
        if (distance <= 0) {
            return;
        }
        direction.normalize();

        Ray ray = new Ray(segStart, direction);
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray, distance, new DetectFilter<>(true, true, null));
        if (results.isEmpty()) {
            return;
        }
        // Sort by distance for proper piercing order along this segment.
        results.sort(Comparator.comparingDouble(r -> r.getRaycast().getDistance()));

        for (RaycastResult<Body, ?> result : results) {
            Object userData = result.getBody().getUserData();
            if (userData instanceof Player player) {
                // applyBeamDamage records the id; skip if already hit this beam.
                if (beam.canAffectPlayer(player) && !beam.getAffectedPlayers().contains(player.getId())) {
                    applyBeamDamage(beam, player);
                }
            } else if (userData instanceof Turret turret) {
                if (turret.isActive() && !beam.getAffectedPlayers().contains(turret.getId())) {
                    applyBeamDamage(beam, turret);
                }
            } else if (userData instanceof Obstacle) {
                // Stop this segment at an obstacle unless the beam pierces. (For a
                // bouncy beam the path already ends the segment at the wall, so this
                // is the piercing/terminal guard for the final straight run.)
                if (!beam.getBulletEffects().contains(BulletEffect.PIERCING)) {
                    break;
                }
            }
        }
    }

    /**
     * Apply beam damage to a player
     */
    private void applyBeamDamage(Beam beam, GameEntity entity) {
        beam.getAffectedPlayers().add(entity.getId());
        boolean killed = entity.takeDamage(beam.getDamage());
        // Process AOE bullet effects for beam weapons
        bulletEffectProcessor.processBeamEffectHit(beam, entity.getPosition());
        // Handle kill if player died
        if (entity instanceof Player p && killed && killCallback != null) {
            Player killer = gameEntities.getPlayer(beam.getOwnerId());
            killCallback.accept(p, killer);
        }
    }

    /**
     * Spawn a continuous (DOT) beam's AOE field effects at a point. Throttling is
     * the caller's responsibility (see {@link Beam#tryEmitAreaEffect}). Instant
     * beams spawn their effects per-hit via {@link #applyBeamDamage}; this is the
     * equivalent entry point for the continuous-damage loop in GameManager.
     */
    public void processBeamAreaEffects(Beam beam, Vector2 position) {
        bulletEffectProcessor.processBeamEffectHit(beam, position);
    }

}

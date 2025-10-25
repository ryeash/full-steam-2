package com.fullsteam.games;

import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
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
     * Handle firing of beam weapons.
     */
    private void handleBeamFire(Player player) {
        Beam beam = player.shootBeam();
        if (beam == null) {
            return;
        }

        // Update beam's effective end point based on obstacle collisions
        Vector2 effectiveEnd = findBeamObstacleIntersection(beam);
        beam.setEffectiveEndPoint(effectiveEnd);

        gameEntities.addBeam(beam);
        world.addBody(beam.getBody());

        // Process initial hit for instant damage beams
        if (beam.getDamageApplicationType() == DamageApplicationType.INSTANT) {
            processStandardBeamHit(beam);
        }

        log.debug("Player {} fired beam weapon: {}", player.getId(),
                player.getCurrentWeapon().getName());
    }

    /**
     * Handle firing of projectile weapons.
     */
    private void handleProjectileFire(Player player) {
        List<Projectile> projectiles = player.shoot();

        for (Projectile projectile : projectiles) {
            if (projectile != null) {
                gameEntities.addProjectile(projectile);
                world.addBody(projectile.getBody());
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
    private Vector2 findBeamObstacleIntersection(Beam beam) {
        Vector2 startPoint = beam.getStartPoint();
        Vector2 endPoint = beam.getEndPoint();
        Vector2 direction = endPoint.copy().subtract(startPoint);
        double maxDistance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(startPoint, direction);

        // Raycast to find all entities
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                maxDistance,
                new DetectFilter<>(true, true, null)
        );

        if (results.isEmpty()) {
            return endPoint; // No entities, beam reaches full range
        }

        // Find the closest blocking entity based on beam type
        double closestDistance = maxDistance;
        for (RaycastResult<Body, ?> result : results) {
            Body body = result.getBody();
            Object userData = body.getUserData();
            double distance = result.getRaycast().getDistance();

            // Check if this entity blocks the beam based on beam type
            if (shouldEntityBlockBeam(beam, userData)) {
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
     * Check if an entity should block a beam based on the beam's piercing behavior.
     */
    private boolean shouldEntityBlockBeam(Beam beam, Object entity) {
        if (entity instanceof Obstacle) {
            return !beam.canPierceObstacles();
        } else if (entity instanceof Player) {
            return !beam.canPiercePlayers();
        } else if (entity instanceof Projectile) {
            // Projectiles should never block beams - they're small, fast-moving objects
            return false;
        } else if (entity instanceof NetProjectile) {
            // Net projectiles should never block beams
            return false;
        } else if (entity instanceof FieldEffect fieldEffect) {
            // Handle shield barriers - block non-piercing beams
            if (fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER) {
                // Shield barriers block non-piercing beams (LASER, PLASMA_BEAM) 
                // but allow piercing beams (RAILGUN) to pass through
                return !beam.canPierceObstacles();
            }
            // Other field effects don't block beams
            return false;
        } else if (entity.getClass().getSimpleName().equals("Turret")) {
            return !beam.canPiercePlayers();
        }
        // Default: block unknown entities
        return true;
    }

    /**
     * Process standard beam hits (laser, railgun, etc.)
     */
    public void processStandardBeamHit(Beam beam) {
        Vector2 startPoint = beam.getStartPoint();
        Vector2 endPoint = beam.getEffectiveEndPoint();
        Vector2 direction = endPoint.copy().subtract(startPoint);
        double distance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(startPoint, direction);

        // Raycast to find all entities in beam path
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                distance,
                new DetectFilter<>(true, true, null)
        );

        if (results.isEmpty()) {
            return;
        }

        // Sort results by distance for proper piercing order
        results.sort((r1, r2) -> Double.compare(r1.getRaycast().getDistance(), r2.getRaycast().getDistance()));

        // Process each hit based on beam piercing behavior
        for (RaycastResult<Body, ?> result : results) {
            Body body = result.getBody();
            Object userData = body.getUserData();

            if (userData instanceof Player player) {
                if (beam.canAffectPlayer(player)) {
                    applyBeamDamageToPlayer(beam, player);

                    // Stop at first player if beam doesn't pierce players
                    if (!beam.canPiercePlayers()) {
                        break;
                    }
                }
            } else if (userData instanceof Obstacle obstacle) {
                // Apply damage to player-created obstacles if beam can damage them
                if (obstacle.getType() == Obstacle.ObstacleType.PLAYER_BARRIER &&
                    canBeamDamageObstacle(beam, obstacle)) {
                    obstacle.takeDamage(beam.getDamage());
                }

                // Stop at obstacle if beam doesn't pierce obstacles
                if (!beam.canPierceObstacles()) {
                    break;
                }
            } else if (userData.getClass().getSimpleName().equals("Turret")) {
                // Stop at turret if beam doesn't pierce turrets
                if (!beam.canPiercePlayers()) {
                    break;
                }
            }
        }
    }


    /**
     * Apply beam damage to a player
     */
    private void applyBeamDamageToPlayer(Beam beam, Player player) {
        beam.getAffectedPlayers().add(player.getId());
        boolean killed = player.takeDamage(beam.getDamage());

        // Process AOE bullet effects for beam weapons
        bulletEffectProcessor.processBeamEffectHit(beam, player.getPosition());

        // Handle kill if player died
        if (killed && killCallback != null) {
            Player killer = gameEntities.getPlayer(beam.getOwnerId());
            killCallback.accept(player, killer);
        }

        log.debug("Beam {} hit player {} for {} damage (killed: {})",
                beam.getId(), player.getId(), beam.getDamage(), killed);
    }

    /**
     * Check if a beam can damage an obstacle based on team rules
     */
    private boolean canBeamDamageObstacle(Beam beam, Obstacle obstacle) {
        // Can't damage obstacles created by the same player
        if (beam.getOwnerId() == obstacle.getOwnerId()) {
            return false;
        }

        // In FFA mode (team 0), can damage any obstacle except own
        if (beam.getOwnerTeam() == 0 || obstacle.getOwnerTeam() == 0) {
            return true;
        }

        // In team mode, can only damage obstacles created by different teams
        return beam.getOwnerTeam() != obstacle.getOwnerTeam();
    }

    /**
     * Get statistics about active weapons.
     */
    public WeaponStats getStats() {
        int totalProjectiles = gameEntities.getProjectiles().size();
        int totalBeams = gameEntities.getBeams().size();

        return new WeaponStats(totalProjectiles, totalBeams);
    }

    /**
     * Statistics about active weapons in the game.
     */
    public record WeaponStats(
            int totalProjectiles,
            int totalBeams
    ) {
    }
}

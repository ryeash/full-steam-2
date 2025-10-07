package com.fullsteam.games;

import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
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

/**
 * Manages all weapon-related functionality including primary weapons, projectiles, and beams.
 * This system handles weapon firing, ammunition, reloading, and projectile/beam creation.
 */
public class WeaponSystem {
    private static final Logger log = LoggerFactory.getLogger(WeaponSystem.class);

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
        Vector2 effectiveEnd = findBeamObstacleIntersection(beam.getStartPoint(), beam.getEndPoint());
        beam.setEffectiveEndPoint(effectiveEnd);

        gameEntities.addBeam(beam);
        world.addBody(beam.getBody());

        // Process initial hit for instant damage beams
        if (beam.getDamageApplicationType() == DamageApplicationType.INSTANT) {
            processBeamInitialHit(beam);
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
    private Vector2 findBeamObstacleIntersection(Vector2 startPoint, Vector2 endPoint) {
        Vector2 direction = endPoint.copy().subtract(startPoint);
        double maxDistance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(startPoint, direction);

        // Raycast to find obstacles
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                maxDistance,
                new DetectFilter<Body, BodyFixture>(true, true, null)
        );

        boolean hit = !results.isEmpty();

        if (!hit || results.isEmpty()) {
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
     * Process initial hit for instant damage beams.
     * Applies damage to the first entity hit by the beam.
     */
    private void processBeamInitialHit(Beam beam) {
        Vector2 startPoint = beam.getStartPoint();
        Vector2 endPoint = beam.getEffectiveEndPoint();
        Vector2 direction = endPoint.copy().subtract(startPoint);
        double distance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(startPoint, direction);

        // Raycast to find first entity hit
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(
                ray,
                distance,
                new DetectFilter<Body, BodyFixture>(true, true, null)
        );

        boolean hit = !results.isEmpty();

        if (!hit || results.isEmpty()) {
            return;
        }

        // Find the closest player hit
        double closestDistance = distance;
        Player closestPlayer = null;

        for (RaycastResult<Body, ?> result : results) {
            Body body = result.getBody();
            Object userData = body.getUserData();

            if (userData instanceof Player player) {
                // Check if beam can affect this player (team rules)
                if (beam.canAffectPlayer(player)) {
                    double hitDistance = result.getRaycast().getDistance();
                    if (hitDistance < closestDistance) {
                        closestDistance = hitDistance;
                        closestPlayer = player;
                    }
                }
            }
        }

        // Apply damage to the closest player hit
        if (closestPlayer != null) {
            beam.getAffectedPlayers().add(closestPlayer.getId());
            boolean killed = closestPlayer.takeDamage(beam.getDamage());
            
            log.debug("Beam {} hit player {} for {} damage (killed: {})",
                    beam.getId(), closestPlayer.getId(), beam.getDamage(), killed);

            // Note: Kill handling is done by GameManager through collision system
        }
    }

    /**
     * Update all homing projectiles to track their targets.
     * This is handled by BulletEffectProcessor in CollisionProcessor.
     * Keeping this method for potential future use.
     */
    public void updateHomingProjectiles() {
        // Homing behavior is now handled by BulletEffectProcessor
        // This method is kept for API compatibility
    }

    /**
     * Check if a player can shoot their weapon.
     */
    public boolean canShoot(Player player) {
        return player.canShoot();
    }

    /**
     * Check if a player needs to reload.
     */
    public boolean needsReload(Player player) {
        return player.getCurrentWeapon().getCurrentAmmo() <= 0 && !player.isReloading();
    }

    /**
     * Update weapon states for all players (cooldowns, reloading, etc).
     * Should be called once per game loop.
     */
    public void updateWeaponStates(double deltaTime) {
        // Weapon state updates (reloading, cooldowns) are handled by Player class
        // This method is kept for potential future weapon system features
    }

    /**
     * Find beam obstacle intersection - exposed for utility weapon use.
     * Public method for GameManager to use when creating utility beams.
     */
    public Vector2 findBeamObstacleIntersectionPublic(Vector2 beamStart, Vector2 beamEnd) {
        return findBeamObstacleIntersection(beamStart, beamEnd);
    }

    /**
     * Process beam initial hit - exposed for utility weapon use.
     * Public method for GameManager to use when creating instant damage beams.
     */
    public void processBeamInitialHitPublic(Beam beam) {
        processBeamInitialHit(beam);
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
    ) {}
}

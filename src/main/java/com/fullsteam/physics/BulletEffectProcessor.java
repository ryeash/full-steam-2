package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.HOMING_DISTANCE;

/**
 * Handles the processing of bullet effects when projectiles hit targets or obstacles
 */
public class BulletEffectProcessor {
    private static final Logger log = LoggerFactory.getLogger(BulletEffectProcessor.class);

    private final World<Body> world;
    private final GameEntities gameEntities;

    public BulletEffectProcessor(GameEntities gameEntities) {
        this.world = gameEntities.getWorld();
        this.gameEntities = gameEntities;
    }

    public void processEffectHit(Projectile projectile, Vector2 hitPosition) {
        // for FRAGMENTING rounds, the other effects will be attached to the fragments
        if (projectile.hasBulletEffect(BulletEffect.FRAGMENTING)) {
            createFragmentation(projectile, hitPosition);
            return;
        }
        for (BulletEffect effect : projectile.getBulletEffects()) {
            switch (effect) {
                case EXPLOSIVE:
                    createExplosion(projectile, hitPosition);
                    break;
                case INCENDIARY:
                    createFireEffect(projectile, hitPosition);
                    break;
                case ELECTRIC:
                    createElectricEffect(projectile, hitPosition);
                    break;
                case FREEZING:
                    createFreezeEffect(projectile, hitPosition);
                    break;
                case FRAGMENTING:
                    throw new UnsupportedOperationException();
                case POISON:
                    createPoisonEffect(projectile, hitPosition);
                    break;
                case PIERCING:
                    // Piercing is handled in collision detection - projectile continues
                    break;
                case HOMING:
                    // Homing is handled during projectile flight
                    break;
                case BOUNCY:
                    // Bouncy is handled in collision detection
                    break;
            }
        }
    }

    public void createExplosion(Projectile projectile, Vector2 position) {
        FieldEffect explosion = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.EXPLOSION,
                position,
                BulletEffect.EXPLOSIVE.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.EXPLOSIVE.calculateDamage(projectile.getDamage()),
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        world.addBody(explosion.getBody());
        gameEntities.addFieldEffect(explosion);
    }

    public void createFireEffect(Projectile projectile, Vector2 position) {
        FieldEffect fire = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.FIRE,
                position,
                BulletEffect.INCENDIARY.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.INCENDIARY.calculateDamage(projectile.getDamage()),
                FieldEffectType.FIRE.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        world.addBody(fire.getBody());
        gameEntities.addFieldEffect(fire);
    }

    public void createElectricEffect(Projectile projectile, Vector2 position) {
        FieldEffect electric = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.ELECTRIC,
                position,
                BulletEffect.ELECTRIC.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.ELECTRIC.calculateDamage(projectile.getDamage()),
                FieldEffectType.ELECTRIC.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        world.addBody(electric.getBody());
        gameEntities.addFieldEffect(electric);
    }

    public void createFreezeEffect(Projectile projectile, Vector2 position) {
        FieldEffect freeze = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.FREEZE,
                position,
                BulletEffect.FREEZING.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.FREEZING.calculateDamage(projectile.getDamage()),
                FieldEffectType.FREEZE.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        world.addBody(freeze.getBody());
        gameEntities.addFieldEffect(freeze);
    }

    public void createPoisonEffect(Projectile projectile, Vector2 position) {
        FieldEffect poison = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.POISON,
                position,
                BulletEffect.POISON.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.POISON.calculateDamage(projectile.getDamage()),
                FieldEffectType.POISON.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        world.addBody(poison.getBody());
        gameEntities.addFieldEffect(poison);
        log.debug("Created poison field effect at ({}, {}) with radius {} and damage {}",
                position.x, position.y, poison.getRadius(), poison.getDamage());
    }

    private void createFragmentation(Projectile projectile, Vector2 position) {
        // Create visual fragmentation effect first
        FieldEffect fragmentation = new FieldEffect(
                Config.nextId(),
                projectile.getOwnerId(),
                FieldEffectType.FRAGMENTATION,
                position,
                BulletEffect.FRAGMENTING.calculateRadius(projectile.getDamage(), projectile.getOrdinance()),
                BulletEffect.FRAGMENTING.calculateDamage(projectile.getDamage()),
                FieldEffectType.FRAGMENTATION.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.addFieldEffect(fragmentation);

        // Create multiple smaller projectiles
        int fragmentCount = 3 + (int) (projectile.getDamage() / 15); // More fragments for higher damage
        double fragmentDamage = projectile.getDamage() * 0.4; // Each fragment does less damage
        double fragmentSpeed = projectile.getBody().getLinearVelocity().getMagnitude() * 0.6;
        double randomStartAngle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);

        for (int i = 0; i < fragmentCount; i++) {
            double angle = randomStartAngle + ((2 * Math.PI * i) / fragmentCount);
            double vx = Math.cos(angle) * fragmentSpeed;
            double vy = Math.sin(angle) * fragmentSpeed;

            Set<BulletEffect> childEffects = new HashSet<>(projectile.getBulletEffects());
            childEffects.remove(BulletEffect.FRAGMENTING);

            // Create fragment projectile (smaller, shorter range)
            Projectile fragment = new Projectile(
                    projectile.getOwnerId(),
                    position.x,
                    position.y,
                    vx,
                    vy,
                    fragmentDamage,
                    100.0, // Short range for fragments
                    projectile.getOwnerTeam(),
                    projectile.getLinearDamping(),
                    childEffects,
                    Ordinance.DART // Small, fast fragments
            );

            world.addBody(fragment.getBody());
            gameEntities.addProjectile(fragment);
        }
    }

    /**
     * Check if a projectile should pierce through the target
     */
    public boolean shouldPierceTarget(Projectile projectile, GameEntity target) {
        return projectile.hasBulletEffect(BulletEffect.PIERCING);
    }

    /**
     * Check if a projectile should bounce off obstacles
     */
    public boolean shouldBounceOffObstacle(Projectile projectile, Obstacle obstacle) {
        return projectile.hasBulletEffect(BulletEffect.BOUNCY);
    }

    /**
     * Apply homing behavior to a projectile (called during projectile update)
     */
    public void applyHomingBehavior(Projectile projectile) {
        if (!projectile.hasBulletEffect(BulletEffect.HOMING)) {
            return;
        }

        // Find nearest enemy player
        Player nearestEnemy = findNearestEnemy(projectile);
        if (nearestEnemy == null) {
            return;
        }

        Vector2 projectilePos = projectile.getPosition().copy();
        Vector2 targetPos = nearestEnemy.getPosition();
        Vector2 direction = targetPos.copy().subtract(projectilePos);

        double distance = direction.getMagnitude();
        if (distance > HOMING_DISTANCE) {
            return;
        }

        direction.normalize();

        // Get current velocity to calculate perpendicular steering
        Vector2 currentVelocity = projectile.getBody().getLinearVelocity();
        if (currentVelocity.getMagnitude() < 0.1) {
            // no-homing
            return;
        } else {
            // Calculate perpendicular steering force
            Vector2 velocityDirection = currentVelocity.copy();
            velocityDirection.normalize();

            // Calculate the perpendicular direction (90 degrees to velocity)
            // This creates a steering effect rather than direct attraction
            Vector2 perpendicularDirection = new Vector2(-velocityDirection.y, velocityDirection.x);

            // Determine which side to steer toward (left or right of velocity)
            // Use dot product to determine if target is to the left or right
            double crossProduct = velocityDirection.cross(direction);
            if (crossProduct < 0) {
                // Target is to the right, steer right
                perpendicularDirection.multiply(-1);
            }
            // If crossProduct > 0, target is to the left, steer left (no change needed)

            // Apply perpendicular steering force
            double steeringForce = 3000.0;
            projectile.getBody().applyForce(perpendicularDirection.multiply(steeringForce));
        }
    }

    private Player findNearestEnemy(Projectile projectile) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : gameEntities.getAllPlayers()) {
            if (!player.isActive() || !projectile.canDamage(player)) {
                continue; // Skip teammates and self
            }
            double distance = projectile.getPosition().distance(player.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * Process bullet effects for beam weapons when they hit targets
     */
    public void processBeamEffectHit(Beam beam, Vector2 hitPosition) {
        // Process AOE effects for beam weapons
        for (BulletEffect effect : beam.getBulletEffects()) {
            switch (effect) {
                case EXPLOSIVE:
                    createExplosionForBeam(beam, hitPosition);
                    break;
                case INCENDIARY:
                    createFireEffectForBeam(beam, hitPosition);
                    break;
                case ELECTRIC:
                    createElectricEffectForBeam(beam, hitPosition);
                    break;
                case FREEZING:
                    createFreezeEffectForBeam(beam, hitPosition);
                    break;
                case POISON:
                    createPoisonEffectForBeam(beam, hitPosition);
                    break;
                // these don't apply to beams
                case PIERCING:
                case HOMING:
                case BOUNCY:
                case FRAGMENTING:
                    break;
            }
        }
    }

    /**
     * Create explosion effect for beam weapons
     */
    private void createExplosionForBeam(Beam beam, Vector2 position) {
        FieldEffect explosion = new FieldEffect(
                Config.nextId(),
                beam.getOwnerId(),
                FieldEffectType.EXPLOSION,
                position,
                BulletEffect.EXPLOSIVE.calculateRadius(beam.getDamage(), beam.getOrdinance()),
                BulletEffect.EXPLOSIVE.calculateDamage(beam.getDamage()),
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        world.addBody(explosion.getBody());
        gameEntities.addFieldEffect(explosion);
    }

    /**
     * Create fire effect for beam weapons
     */
    private void createFireEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect fire = new FieldEffect(
                Config.nextId(),
                beam.getOwnerId(),
                FieldEffectType.FIRE,
                position,
                BulletEffect.INCENDIARY.calculateRadius(beam.getDamage(), beam.getOrdinance()),
                BulletEffect.INCENDIARY.calculateDamage(beam.getDamage()),
                FieldEffectType.FIRE.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        world.addBody(fire.getBody());
        gameEntities.addFieldEffect(fire);
    }

    /**
     * Create electric effect for beam weapons
     */
    private void createElectricEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect electric = new FieldEffect(
                Config.nextId(),
                beam.getOwnerId(),
                FieldEffectType.ELECTRIC,
                position,
                BulletEffect.ELECTRIC.calculateRadius(beam.getDamage(), beam.getOrdinance()),
                BulletEffect.ELECTRIC.calculateDamage(beam.getDamage()),
                FieldEffectType.ELECTRIC.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        world.addBody(electric.getBody());
        gameEntities.addFieldEffect(electric);
    }

    /**
     * Create freeze effect for beam weapons
     */
    private void createFreezeEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect freeze = new FieldEffect(
                Config.nextId(),
                beam.getOwnerId(),
                FieldEffectType.FREEZE,
                position,
                BulletEffect.FREEZING.calculateRadius(beam.getDamage(), beam.getOrdinance()),
                BulletEffect.FREEZING.calculateDamage(beam.getDamage()),
                FieldEffectType.FREEZE.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        world.addBody(freeze.getBody());
        gameEntities.addFieldEffect(freeze);
    }

    /**
     * Create poison effect for beam weapons
     */
    private void createPoisonEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect poison = new FieldEffect(
                Config.nextId(),
                beam.getOwnerId(),
                FieldEffectType.POISON,
                position,
                BulletEffect.POISON.calculateRadius(beam.getDamage(), beam.getOrdinance()),
                BulletEffect.POISON.calculateDamage(beam.getDamage()),
                FieldEffectType.POISON.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        world.addBody(poison.getBody());
        gameEntities.addFieldEffect(poison);
    }
}

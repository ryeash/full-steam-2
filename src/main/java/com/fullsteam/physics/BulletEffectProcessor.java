package com.fullsteam.physics;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Weapon;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.HOMING_DISTANCE;

/**
 * Handles the processing of bullet effects when projectiles hit targets or obstacles
 */
public class BulletEffectProcessor {
    /**
     * Strike Beacon detonation parameters (the STRIKE bullet effect).
     */
    private static final double STRIKE_RADIUS = 120.0;
    private static final double STRIKE_DAMAGE = 80.0;
    private static final double STRIKE_DELAY_SECONDS = 2.0;

    private final GameEntities gameEntities;

    public BulletEffectProcessor(GameEntities gameEntities) {
        this.gameEntities = gameEntities;
    }

    public void processEffectHit(Projectile projectile, Vector2 hitPosition) {
        for (BulletEffect effect : projectile.getBulletEffects()) {
            switch (effect) {
                case EXPLOSIVE -> createExplosion(projectile, hitPosition);
                case INCENDIARY -> createFireEffect(projectile, hitPosition);
                case ELECTRIC -> createElectricEffect(projectile, hitPosition);
                case FREEZING -> createFreezeEffect(projectile, hitPosition);
                case FRAGMENTING -> createFragmentation(projectile, hitPosition);
                case POISON -> createPoisonEffect(projectile, hitPosition);
                case SMOKE -> createSmokeEffect(projectile, hitPosition);
                case STRIKE -> createStrikeEffect(projectile, hitPosition);
                case PIERCING -> {
                    // Piercing is handled in collision detection - projectile continues
                }
                case HOMING -> {
                    // Homing is handled during projectile flight
                }
                case BOUNCY -> {
                    // Bouncy is handled in collision detection
                }
            }
        }
    }

    public void createExplosion(Projectile projectile, Vector2 position) {
        FieldEffect explosion = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.EXPLOSION,
                position,
                BulletEffect.EXPLOSIVE.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.EXPLOSIVE.calculateDamage(projectile.getDamage()),
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(explosion);
    }

    public void createFireEffect(Projectile projectile, Vector2 position) {
        FieldEffect fire = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.FIRE,
                position,
                BulletEffect.INCENDIARY.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.INCENDIARY.calculateDamage(projectile.getDamage()),
                FieldEffectType.FIRE.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(fire);
    }

    public void createElectricEffect(Projectile projectile, Vector2 position) {
        FieldEffect electric = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.ELECTRIC,
                position,
                BulletEffect.ELECTRIC.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.ELECTRIC.calculateDamage(projectile.getDamage()),
                FieldEffectType.ELECTRIC.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(electric);
    }

    public void createFreezeEffect(Projectile projectile, Vector2 position) {
        FieldEffect freeze = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.FREEZE,
                position,
                BulletEffect.FREEZING.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.FREEZING.calculateDamage(projectile.getDamage()),
                FieldEffectType.FREEZE.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(freeze);
    }

    public void createPoisonEffect(Projectile projectile, Vector2 position) {
        FieldEffect poison = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.POISON,
                position,
                BulletEffect.POISON.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.POISON.calculateDamage(projectile.getDamage()),
                FieldEffectType.POISON.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(poison);
    }

    public void createSmokeEffect(Projectile projectile, Vector2 position) {
        FieldEffect smoke = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.SMOKE,
                position,
                BulletEffect.SMOKE.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                0.0,
                FieldEffectType.SMOKE.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(smoke);
    }

    /**
     * Strike Beacon detonation (the STRIKE bullet effect, fired on dismissal where
     * the beacon lands): an immediate non-damaging WARNING_ZONE telegraph, plus a
     * delayed EXPLOSION that stays inert/hidden until it fires after the warning
     * window. The delay lives in the FieldEffect itself — no scheduling needed.
     */
    private void createStrikeEffect(Projectile projectile, Vector2 position) {
        int owner = projectile.getOwnerId();
        int team = projectile.getOwnerTeam();
        // Telegraph: warning zone for the full delay window.
        gameEntities.add(new FieldEffect(owner,
                FieldEffectType.WARNING_ZONE,
                position.copy(),
                STRIKE_RADIUS,
                0.0,
                STRIKE_DELAY_SECONDS,
                team));
        // The strike: a delayed explosion that detonates when the warning ends.
        gameEntities.add(new FieldEffect(owner,
                FieldEffectType.EXPLOSION,
                position.copy(),
                STRIKE_RADIUS,
                STRIKE_DAMAGE,
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                STRIKE_DELAY_SECONDS,
                team));
    }

    private void createFragmentation(Projectile projectile, Vector2 position) {
        // Create visual fragmentation effect first
        FieldEffect fragmentation = new FieldEffect(
                projectile.getOwnerId(),
                FieldEffectType.FRAGMENTATION,
                position,
                BulletEffect.FRAGMENTING.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber()),
                BulletEffect.FRAGMENTING.calculateDamage(projectile.getDamage()),
                FieldEffectType.FRAGMENTATION.getDefaultDuration(),
                projectile.getOwnerTeam()
        );
        gameEntities.add(fragmentation);

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
                    Ordinance.PROJECTILE, // Small, fast fragments
                    // Fragments are a fraction of the parent's caliber, floored so
                    // they never shrink to nothing.
                    Math.max(0.4, projectile.getCaliber() * 0.5),
                    // Fragments split the parent's knockback the same way pellets do:
                    // the total across all fragments stays near-constant (~1.2x the
                    // parent) rather than stacking per-fragment.
                    Weapon.knockbackPerBullet(projectile.getKnockback(), fragmentCount)
            );
            gameEntities.add(fragment);
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
            double steeringForce = 4000.0;
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
                case SMOKE:
                    createSmokeEffectForBeam(beam, hitPosition);
                    break;
                // nothing else triggers
                default:
                    break;
            }
        }
    }

    /**
     * Create explosion effect for beam weapons
     */
    private void createExplosionForBeam(Beam beam, Vector2 position) {
        FieldEffect explosion = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.EXPLOSION,
                position,
                BulletEffect.EXPLOSIVE.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                BulletEffect.EXPLOSIVE.calculateDamage(beam.getDamage()),
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(explosion);
    }

    /**
     * Create fire effect for beam weapons
     */
    private void createFireEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect fire = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.FIRE,
                position,
                BulletEffect.INCENDIARY.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                BulletEffect.INCENDIARY.calculateDamage(beam.getDamage()),
                FieldEffectType.FIRE.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(fire);
    }

    /**
     * Create electric effect for beam weapons
     */
    private void createElectricEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect electric = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.ELECTRIC,
                position,
                BulletEffect.ELECTRIC.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                BulletEffect.ELECTRIC.calculateDamage(beam.getDamage()),
                FieldEffectType.ELECTRIC.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(electric);
    }

    /**
     * Create freeze effect for beam weapons
     */
    private void createFreezeEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect freeze = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.FREEZE,
                position,
                BulletEffect.FREEZING.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                BulletEffect.FREEZING.calculateDamage(beam.getDamage()),
                FieldEffectType.FREEZE.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(freeze);
    }

    /**
     * Create poison effect for beam weapons
     */
    private void createPoisonEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect poison = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.POISON,
                position,
                BulletEffect.POISON.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                BulletEffect.POISON.calculateDamage(beam.getDamage()),
                FieldEffectType.POISON.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(poison);
    }

    /**
     * Create a vision-blocking smoke cloud at a beam's impact point
     */
    private void createSmokeEffectForBeam(Beam beam, Vector2 position) {
        FieldEffect smoke = new FieldEffect(
                beam.getOwnerId(),
                FieldEffectType.SMOKE,
                position,
                BulletEffect.SMOKE.calculateRadius(beam.getDamage(), beam.getOrdinance(), beam.getCaliber()),
                0.0,
                FieldEffectType.SMOKE.getDefaultDuration(),
                beam.getOwnerTeam()
        );
        gameEntities.add(smoke);
    }
}

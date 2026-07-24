package com.fullsteam.physics;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectCircle;
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
            if (effect == BulletEffect.STRIKE) {
                createStrikeEffect(projectile, hitPosition);
            } else if (effect == BulletEffect.FRAGMENTING) {
                createFragmentation(projectile, hitPosition);
            } else {
                spawnEffect(projectile, projectile.getOrdinance(), effect, projectile.getDamage(), projectile.getCaliber(), hitPosition);
            }
        }
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
        gameEntities.add(new FieldEffectCircle(owner,
                FieldEffectType.WARNING_ZONE,
                position.copy(),
                STRIKE_RADIUS,
                STRIKE_RADIUS,
                0.0,
                STRIKE_DELAY_SECONDS,
                0,
                team));
        // The strike: a delayed explosion that detonates when the warning ends.
        gameEntities.add(new FieldEffectCircle(owner,
                FieldEffectType.EXPLOSION,
                position.copy(),
                STRIKE_RADIUS,
                STRIKE_RADIUS,
                STRIKE_DAMAGE,
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                (long) (System.currentTimeMillis() + STRIKE_DELAY_SECONDS * 1000),
                team));
    }

    private void createFragmentation(Projectile projectile, Vector2 position) {
        // Create visual fragmentation effect first
        double radius = BulletEffect.FRAGMENTING.calculateRadius(projectile.getDamage(), projectile.getOrdinance(), projectile.getCaliber());
        gameEntities.add(new FieldEffectCircle(
                projectile.getOwnerId(),
                FieldEffectType.FRAGMENTATION,
                position,
                radius,
                radius,
                BulletEffect.FRAGMENTING.calculateDamage(projectile.getDamage()),
                FieldEffectType.FRAGMENTATION.getDefaultDuration(),
                0,
                projectile.getOwnerTeam()
        ));

        // Create multiple smaller projectiles
        int fragmentCount = 3 + (int) (projectile.getDamage() / 15); // More fragments for higher damage
        double fragmentDamage = projectile.getDamage() * 0.3; // Each fragment does less damage
        double fragmentSpeed = projectile.getBody().getLinearVelocity().getMagnitude() * 0.6; // each fragment is slower
        double randomStartAngle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);

        for (int i = 0; i < fragmentCount; i++) {
            double angle = randomStartAngle + ((2 * Math.PI * i) / fragmentCount);
            double vx = Math.cos(angle) * fragmentSpeed;
            double vy = Math.sin(angle) * fragmentSpeed;

            // Spawn slightly ahead along each fragment's own direction so the
            // physics body doesn't immediately overlap the player that was hit.
            double spawnOffset = 8.0;
            double spawnX = position.x + Math.cos(angle) * spawnOffset;
            double spawnY = position.y + Math.sin(angle) * spawnOffset;

            Set<BulletEffect> childEffects = new HashSet<>(projectile.getBulletEffects());
            childEffects.remove(BulletEffect.FRAGMENTING);

            // Create fragment projectile (smaller, shorter range)
            Projectile fragment = new Projectile(
                    projectile.getOwnerId(),
                    new Vector2(spawnX, spawnY),
                    new Vector2(vx, vy),
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

        // Find nearest enemy player (**only** players, not turrets, NPCs, etc.)
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
        if (currentVelocity.getMagnitude() > 0.1) {
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
    public void processBeamEffectHit(FieldEffectBeam beam, Vector2 hitPosition) {
        for (BulletEffect effect : beam.getBulletEffects()) {
            spawnEffect(beam, beam.getOrdinance(), effect, beam.getDamage(), beam.getCaliber(), hitPosition);
        }
        beam.getBulletEffects().clear();
    }

    private void spawnEffect(OwnedGameEntity source, Ordinance ordinance, BulletEffect bulletEffect, double damage, double caliber, Vector2 position) {
        FieldEffectType effectType = bulletEffect.getEffectType();
        if (effectType != null) {
            double radius = bulletEffect.calculateRadius(damage, ordinance, caliber);
            gameEntities.add(new FieldEffectCircle(
                    source.getOwnerId(),
                    effectType,
                    position,
                    radius,
                    effectType.maxRadius(radius),
                    bulletEffect.calculateDamage(damage),
                    effectType.getDefaultDuration(),
                    0,
                    source.getOwnerTeam()
            ));
        }
    }
}

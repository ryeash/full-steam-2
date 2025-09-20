package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles the processing of bullet effects when projectiles hit targets or obstacles
 */
public class BulletEffectProcessor {
    
    private final GameEntities gameEntities;
    private final List<FieldEffect> pendingFieldEffects;
    private final List<Projectile> pendingProjectiles;

    public BulletEffectProcessor(GameEntities gameEntities) {
        this.gameEntities = gameEntities;
        this.pendingFieldEffects = new ArrayList<>();
        this.pendingProjectiles = new ArrayList<>();
    }

    /**
     * Process bullet effects when a projectile hits a player
     */
    public void processEffectsOnPlayerHit(Projectile projectile, Player player) {
        Set<BulletEffect> effects = projectile.getBulletEffects();
        Vector2 hitPosition = player.getPosition();
        
        for (BulletEffect effect : effects) {
            switch (effect) {
                case EXPLODES_ON_IMPACT:
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
                    createFragmentation(projectile, hitPosition);
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

    /**
     * Process bullet effects when a projectile hits an obstacle
     */
    public void processEffectsOnObstacleHit(Projectile projectile, Vector2 hitPosition) {
        Set<BulletEffect> effects = projectile.getBulletEffects();
        
        for (BulletEffect effect : effects) {
            switch (effect) {
                case EXPLODES_ON_IMPACT:
                    createExplosion(projectile, hitPosition);
                    break;
                case INCENDIARY:
                    createFireEffect(projectile, hitPosition);
                    break;
                case FRAGMENTING:
                    createFragmentation(projectile, hitPosition);
                    break;
                case ELECTRIC:
                    createElectricEffect(projectile, hitPosition);
                    break;
                case FREEZING:
                    createFreezeEffect(projectile, hitPosition);
                    break;
                case PIERCING:
                    // Piercing allows projectile to continue through obstacles
                    break;
                case BOUNCY:
                    // Bouncy is handled in physics collision response
                    break;
                case HOMING:
                    // Homing doesn't apply to obstacle hits
                    break;
            }
        }
    }

    private void createExplosion(Projectile projectile, Vector2 position) {
        double explosionRadius = 80.0 + (projectile.getDamage() * 0.5); // Scale with damage
        double explosionDamage = projectile.getDamage() * 1.5; // 50% more damage for explosion
        
        FieldEffect explosion = new FieldEffect(
            Config.nextId(),
            FieldEffectType.EXPLOSION,
            position,
            explosionRadius,
            explosionDamage,
            0.5, // Short duration - 0.5 seconds for explosion
            projectile.getOwnerTeam()
        );
        
        pendingFieldEffects.add(explosion);
    }

    private void createFireEffect(Projectile projectile, Vector2 position) {
        double fireRadius = 40.0;
        double fireDamage = projectile.getDamage() * 0.3; // Lower damage but over time
        
        FieldEffect fire = new FieldEffect(
            Config.nextId(),
            FieldEffectType.FIRE,
            position,
            fireRadius,
            fireDamage,
            3.0, // 3 seconds of fire
            projectile.getOwnerTeam()
        );
        
        pendingFieldEffects.add(fire);
    }

    private void createElectricEffect(Projectile projectile, Vector2 position) {
        double electricRadius = 60.0;
        double electricDamage = projectile.getDamage() * 0.8; // High damage but shorter duration
        
        FieldEffect electric = new FieldEffect(
            Config.nextId(),
            FieldEffectType.ELECTRIC,
            position,
            electricRadius,
            electricDamage,
            0.4, // 0.4 seconds of electric damage - quick chain effect
            projectile.getOwnerTeam()
        );
        
        pendingFieldEffects.add(electric);
    }

    private void createFreezeEffect(Projectile projectile, Vector2 position) {
        double freezeRadius = 50.0;
        double freezeDamage = projectile.getDamage() * 0.2; // Low damage but slowing effect
        
        FieldEffect freeze = new FieldEffect(
            Config.nextId(),
            FieldEffectType.FREEZE,
            position,
            freezeRadius,
            freezeDamage,
            2.0, // 2 seconds of freeze
            projectile.getOwnerTeam()
        );
        
        pendingFieldEffects.add(freeze);
    }

    private void createFragmentation(Projectile projectile, Vector2 position) {
        // Create visual fragmentation effect first
        FieldEffect fragmentation = new FieldEffect(
            Config.nextId(),
            FieldEffectType.FRAGMENTATION,
            position,
            60.0, // Fragmentation radius
            0.0, // No damage from the visual effect itself
            0.3, // Very short duration - 0.3 seconds for fragmentation visual
            projectile.getOwnerTeam()
        );
        
        pendingFieldEffects.add(fragmentation);
        
        // Create multiple smaller projectiles
        int fragmentCount = 5 + (int)(projectile.getDamage() / 20); // More fragments for higher damage
        double fragmentDamage = projectile.getDamage() * 0.4; // Each fragment does less damage
        double fragmentSpeed = projectile.getBody().getLinearVelocity().getMagnitude() * 0.6;
        
        for (int i = 0; i < fragmentCount; i++) {
            double angle = (2 * Math.PI * i) / fragmentCount;
            double vx = Math.cos(angle) * fragmentSpeed;
            double vy = Math.sin(angle) * fragmentSpeed;
            
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
                Set.of(), // Fragments don't have effects to prevent infinite recursion
                Ordinance.DART // Small, fast fragments
            );
            
            pendingProjectiles.add(fragment);
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
    public void applyHomingBehavior(Projectile projectile, double deltaTime) {
        if (!projectile.hasBulletEffect(BulletEffect.HOMING)) return;
        
        // Find nearest enemy player
        Player nearestEnemy = findNearestEnemy(projectile);
        if (nearestEnemy == null) return;
        
        Vector2 projectilePos = new Vector2(projectile.getBody().getTransform().getTranslationX(),
                                          projectile.getBody().getTransform().getTranslationY());
        Vector2 targetPos = nearestEnemy.getPosition();
        Vector2 direction = targetPos.copy().subtract(projectilePos);
        
        double distance = direction.getMagnitude();
        if (distance > 200.0) return; // Homing only works within 200 units
        
        direction.normalize();
        
        // Apply gentle steering force (not instant tracking)
        Vector2 currentVelocity = projectile.getBody().getLinearVelocity();
        double homingStrength = 0.3; // 30% steering per second
        
        Vector2 desiredVelocity = direction.multiply(currentVelocity.getMagnitude());
        Vector2 steeringForce = desiredVelocity.subtract(currentVelocity).multiply(homingStrength * deltaTime);
        
        Vector2 newVelocity = currentVelocity.add(steeringForce);
        projectile.getBody().setLinearVelocity(newVelocity);
    }

    private Player findNearestEnemy(Projectile projectile) {
        Vector2 projectilePos = new Vector2(projectile.getBody().getTransform().getTranslationX(),
                                          projectile.getBody().getTransform().getTranslationY());
        
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : gameEntities.getAllPlayers()) {
            if (!player.isActive()) continue;
            if (!projectile.canDamage(player)) continue; // Skip teammates and self
            
            double distance = projectilePos.distance(player.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }

    /**
     * Get pending field effects to be added to the game world
     */
    public List<FieldEffect> getPendingFieldEffects() {
        List<FieldEffect> effects = new ArrayList<>(pendingFieldEffects);
        pendingFieldEffects.clear();
        return effects;
    }

    /**
     * Get pending projectiles to be added to the game world
     */
    public List<Projectile> getPendingProjectiles() {
        List<Projectile> projectiles = new ArrayList<>(pendingProjectiles);
        pendingProjectiles.clear();
        return projectiles;
    }
}

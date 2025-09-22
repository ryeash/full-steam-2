package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Projectile extends GameEntity {
    private final int ownerId;
    private final int ownerTeam; // Team of the player who fired this projectile
    private final double damage;
    private double timeToLive; // Time in seconds before projectile is removed
    private final double linearDamping; // How much the projectile slows down over time
    private final Set<BulletEffect> bulletEffects; // Special effects this projectile has
    private final Ordinance ordinance; // Type of projectile (bullet, rocket, grenade, etc.)
    private boolean hasExploded = false; // Track if explosive projectiles have already exploded
    private final double minimumVelocity; // Minimum velocity before projectile is dismissed
    private boolean dismissedByVelocity = false; // Track if dismissed due to low velocity
    
    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects, Ordinance ordinance) {
        super(Config.nextId(), createProjectileBody(x, y, vx, vy, linearDamping, ordinance), 1.0);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.damage = damage;
        this.linearDamping = linearDamping;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;
        this.minimumVelocity = calculateMinimumVelocity(ordinance);

        // Calculate time to live based on range and speed
        double speed = new Vector2(vx, vy).getMagnitude();
        if (speed > 0) {
            this.timeToLive = maxRange / speed;
        } else {
            this.timeToLive = 0; // Deactivate immediately if speed is zero
        }
    }

    private static Body createProjectileBody(double x, double y, double vx, double vy) {
        return createProjectileBody(x, y, vx, vy, 0.0, Ordinance.BULLET);
    }

    private static Body createProjectileBody(double x, double y, double vx, double vy, double linearDamping) {
        return createProjectileBody(x, y, vx, vy, linearDamping, Ordinance.BULLET);
    }
    
    private static Body createProjectileBody(double x, double y, double vx, double vy, double linearDamping, Ordinance ordinance) {
        Body body = new Body();
        // Use ordinance size for projectile physics
        Circle circle = new Circle(ordinance.getSize());
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);
        body.setLinearVelocity(vx, vy);
        body.setBullet(true); // Enable continuous collision detection
        body.setLinearDamping(linearDamping); // Apply linear damping for bullet slowdown
        return body;
    }
    
    /**
     * Calculate minimum velocity threshold based on ordinance type.
     * Different projectile types have different minimum velocities before dismissal.
     */
    private double calculateMinimumVelocity(Ordinance ordinance) {
        switch (ordinance) {
            case ROCKET:
                return 50.0; // Rockets need higher velocity to maintain thrust
            case GRENADE:
                return 20.0; // Grenades explode when they slow down significantly
            case CANNONBALL:
                return 30.0; // Heavy projectiles need some momentum
            case PLASMA:
                return 40.0; // Energy projectiles dissipate when slowing
            case FLAMETHROWER:
                return 10.0; // Fire streams extinguish quickly
            case BULLET:
            case DART:
            case LASER:
            default:
                return 5.0; // Standard projectiles have low minimum velocity
        }
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Check time to live
        timeToLive -= deltaTime;
        if (timeToLive <= 0) {
            active = false;
            return;
        }

        // Check velocity threshold for dismissal
        Vector2 velocity = body.getLinearVelocity();
        double currentSpeed = velocity.getMagnitude();
        
        if (currentSpeed < minimumVelocity && !dismissedByVelocity) {
            // Mark as dismissed by velocity to trigger effects
            dismissedByVelocity = true;
            active = false;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Check if this projectile can damage the given player.
     * Projectiles cannot damage teammates (unless FFA mode).
     *
     * @param player The player to check
     * @return true if projectile can damage this player, false if teammate or self
     */
    public boolean canDamage(Player player) {
        if (player == null) {
            return false;
        }

        // Can't damage self
        if (player.getId() == ownerId) {
            return false;
        }

        // In FFA mode (team 0), can damage anyone except self
        if (ownerTeam == 0 || player.getTeam() == 0) {
            return true;
        }

        // In team mode, can only damage players on different teams
        return ownerTeam != player.getTeam();
    }

    public boolean hasBulletEffect(BulletEffect effect) {
        return bulletEffects.contains(effect);
    }

    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }

    public boolean isBounces() {
        return hasBulletEffect(BulletEffect.BOUNCY);
    }

    public void markAsExploded() {
        this.hasExploded = true;
    }
    
    /**
     * Check if this projectile was dismissed due to low velocity.
     * This can be used to trigger special effects like explosions.
     */
    public boolean wasDismissedByVelocity() {
        return dismissedByVelocity;
    }
    
    /**
     * Check if this projectile should trigger effects on dismissal.
     * This includes explosive effects, electric discharges, etc.
     */
    public boolean shouldTriggerEffectsOnDismissal() {
        if (!wasDismissedByVelocity()) {
            return false;
        }
        
        // Don't trigger effects if already exploded
        if (hasExploded) {
            return false;
        }
        
        // Check if this ordinance type should trigger effects
        switch (ordinance) {
            case ROCKET:
            case GRENADE:
                return true; // Explosive projectiles explode on low velocity
            case PLASMA:
                return hasBulletEffect(BulletEffect.ELECTRIC); // Plasma with electric effect discharges
            case FLAMETHROWER:
                return hasBulletEffect(BulletEffect.INCENDIARY); // Flame projectiles with incendiary spread fire
            default:
                // Other projectiles only trigger effects if they have special bullet effects
                return hasBulletEffect(BulletEffect.ELECTRIC) || 
                       hasBulletEffect(BulletEffect.INCENDIARY) ||
                       hasBulletEffect(BulletEffect.FREEZING) ||
                       hasBulletEffect(BulletEffect.EXPLODES_ON_IMPACT);
        }
    }
    
    /**
     * Get the current velocity magnitude of this projectile.
     */
    public double getCurrentSpeed() {
        if (body == null) {
            return 0.0;
        }
        return body.getLinearVelocity().getMagnitude();
    }
}

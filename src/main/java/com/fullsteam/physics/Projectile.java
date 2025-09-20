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

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange, int ownerTeam) {
        this(ownerId, x, y, vx, vy, damage, maxRange, ownerTeam, 0.0, new HashSet<>(), Ordinance.BULLET); // Default values
    }

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects) {
        this(ownerId, x, y, vx, vy, damage, maxRange, ownerTeam, linearDamping, bulletEffects, Ordinance.BULLET); // Default ordinance
    }
    
    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects, Ordinance ordinance) {
        super(Config.nextId(), createProjectileBody(x, y, vx, vy, linearDamping, ordinance), 1.0);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.damage = damage;
        this.linearDamping = linearDamping;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;

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

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        timeToLive -= deltaTime;
        if (timeToLive <= 0) {
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
}

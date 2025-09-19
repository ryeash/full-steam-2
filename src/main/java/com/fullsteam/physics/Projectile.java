package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

@Getter
public class Projectile extends GameEntity {
    private final int ownerId;
    private final int ownerTeam; // Team of the player who fired this projectile
    private final double damage;
    private double timeToLive; // Time in seconds before projectile is removed
    private final boolean bounces;

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange) {
        this(ownerId, x, y, vx, vy, damage, maxRange, false, 0); // Default to not bouncing, FFA team
    }
    
    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange, boolean bounces) {
        this(ownerId, x, y, vx, vy, damage, maxRange, bounces, 0); // Default to FFA team
    }

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange, boolean bounces, int ownerTeam) {
        super(Config.nextId(), createProjectileBody(x, y, vx, vy), 1.0);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.damage = damage;
        this.bounces = bounces;

        // Calculate time to live based on range and speed
        double speed = new Vector2(vx, vy).getMagnitude();
        if (speed > 0) {
            this.timeToLive = maxRange / speed;
        } else {
            this.timeToLive = 0; // Deactivate immediately if speed is zero
        }
    }

    private static Body createProjectileBody(double x, double y, double vx, double vy) {
        Body body = new Body();
        Circle circle = new Circle(2.0); // Small projectile
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);
        body.setLinearVelocity(vx, vy);
        body.setBullet(true); // Enable continuous collision detection
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
}

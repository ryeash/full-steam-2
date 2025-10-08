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
    private boolean dismissedByVelocity = false; // Track if dismissed due to low velocity
    private boolean dismissedByRange = false; // Track if dismissed due to reaching max range/time

    // prevent double hits
    private final Set<Integer> affectedPlayers;
    private final Set<Integer> affectedObstacles;

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects, Ordinance ordinance) {
        super(Config.nextId(), createProjectileBody(x, y, vx, vy, linearDamping, ordinance, bulletEffects), 1.0);
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
        this.affectedPlayers = new HashSet<>();
        this.affectedObstacles = new HashSet<>();
    }

    private static Body createProjectileBody(double x, double y, double vx, double vy, double linearDamping, Ordinance ordinance, Set<BulletEffect> bulletEffects) {
        Body body = new Body();
        Circle circle = new Circle(ordinance.getSize());
        body.addFixture(circle);
        
        // Set restitution for bouncy projectiles
        if (bulletEffects.contains(BulletEffect.BOUNCY)) {
            body.getFixture(0).setRestitution(0.8); // High bounce - retains 80% of velocity
        } else {
            body.getFixture(0).setRestitution(0.0); // No bounce for non-bouncy projectiles
        }
        
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);
        body.setLinearVelocity(vx, vy);
        body.setBullet(true);
        body.setLinearDamping(linearDamping);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Check time to live
        timeToLive -= deltaTime;
        if (timeToLive <= 0) {
            // Mark as dismissed by range to trigger effects
            dismissedByRange = true;
            active = false;
            return;
        }

        // Check velocity threshold for dismissal
        double currentSpeed = body.getLinearVelocity().getMagnitude();
        if (currentSpeed < ordinance.getMinimumVelocity() && !dismissedByVelocity) {
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

    public Set<Integer> getAffectedObstacles() {
        return affectedObstacles;
    }

    public void markAsExploded() {
        this.hasExploded = true;
    }
    
    /**
     * Check if this projectile should trigger effects on dismissal.
     * This includes explosive effects, electric discharges, etc.
     */
    public boolean shouldTriggerEffectsOnDismissal() {
        // Don't trigger effects if already exploded
        return (dismissedByVelocity || dismissedByRange) && !hasExploded;
    }
}

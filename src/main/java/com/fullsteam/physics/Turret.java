package com.fullsteam.physics;

import com.fullsteam.model.Ordinance;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automated defense turret that targets enemies within range
 */
@Getter
@Setter
public class Turret extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double detectionRange;
    private final double fireRate; // shots per second
    private final double damage;
    private final double projectileSpeed;
    private final long expires;
    private long lastShotTime = 0;
    private Player currentTarget;
    private Vector2 aimDirection = new Vector2(1, 0);

    public Turret(int id, int ownerId, int ownerTeam, Vector2 position, double lifespan) {
        super(id, createTurretBody(position), 50.0); // 50 HP turret
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.detectionRange = 400.0; // Detection range
        this.fireRate = 3.0; // 3 shots per second
        this.damage = 15.0; // Moderate damage
        this.projectileSpeed = 400.0; // Fast projectiles
        this.expires = (long) (System.currentTimeMillis() + (lifespan * 1000));
        this.setRotation(Math.random() * 2 * Math.PI);
    }

    private static Body createTurretBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(15.0); // Slightly smaller than player
        body.addFixture(circle);
        body.setMass(MassType.INFINITE); // Stationary
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Update lifespan
        if (System.currentTimeMillis() > expires) {
            active = false;
            return;
        }

        // Update target validity
        if (currentTarget != null && (!currentTarget.isActive() || !isValidTarget(currentTarget))) {
            currentTarget = null;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Find and acquire a target from the list of players
     */
    public void acquireTarget(List<Player> players) {
        if (currentTarget != null && isValidTarget(currentTarget)) {
            return; // Keep current target if still valid
        }

        Player closestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : players) {
            if (!isValidTarget(player)) {
                continue;
            }

            double distance = getPosition().distance(player.getPosition());
            if (distance <= detectionRange && distance < closestDistance) {
                closestTarget = player;
                closestDistance = distance;
            }
        }

        currentTarget = closestTarget;

        // Update aim direction if we have a target
        if (currentTarget != null) {
            Vector2 turretPos = getPosition();
            Vector2 targetPos = currentTarget.getPosition();
            aimDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
            if (aimDirection.getMagnitude() > 0) {
                aimDirection.normalize();
                setRotation(Math.atan2(aimDirection.y, aimDirection.x));
            }
        }
    }

    /**
     * Check if a player is a valid target for this turret
     */
    private boolean isValidTarget(Player player) {
        double distance = getPosition().distance(player.getPosition());
        if (!player.isActive() || player.getHealth() <= 0 || distance > detectionRange) {
            return false;
        }

        // Can't target the owner
        if (player.getId() == ownerId) {
            return false;
        }

        // In FFA mode (team 0), can target anyone except owner
        if (ownerTeam == 0 || player.getTeam() == 0) {
            return true;
        }

        // In team mode, can only target players on different teams
        return ownerTeam != player.getTeam();
    }

    /**
     * Attempt to fire at the current target
     */
    public Projectile tryFire() {
        if (currentTarget == null || !canFire()) {
            return null;
        }

        lastShotTime = System.currentTimeMillis();

        // Predict target movement for better accuracy
        Vector2 targetPos = predictTargetPosition();
        Vector2 turretPos = getPosition();

        Vector2 fireDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
        if (fireDirection.getMagnitude() == 0) {
            return null;
        }

        fireDirection.normalize();

        // Update turret rotation to face the target when firing
        setRotation(Math.atan2(fireDirection.y, fireDirection.x));

        Vector2 velocity = fireDirection.multiply(projectileSpeed);

        // Add slight inaccuracy to make it less overpowered
        double inaccuracy = 0.1; // 10% inaccuracy
        double angleOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * inaccuracy;
        double currentAngle = Math.atan2(velocity.y, velocity.x);
        double newAngle = currentAngle + angleOffset;
        velocity = new Vector2(Math.cos(newAngle) * projectileSpeed, Math.sin(newAngle) * projectileSpeed);

        return new Projectile(
                ownerId, // Use owner's ID for kill attribution
                turretPos.x,
                turretPos.y,
                velocity.x,
                velocity.y,
                damage,
                detectionRange + 100, // Use detection range as projectile range
                ownerTeam,
                0.02, // Slight linear damping
                Set.of(), // No special bullet effects
                Ordinance.BULLET  // Standard bullet ordinance
        );
    }

    /**
     * Predict where the target will be when the projectile reaches them
     */
    private Vector2 predictTargetPosition() {
        if (currentTarget == null) {
            return getPosition();
        }

        Vector2 targetPos = currentTarget.getPosition();
        Vector2 targetVel = currentTarget.getVelocity();
        Vector2 turretPos = getPosition();

        // Simple prediction: assume target continues at current velocity
        double distance = turretPos.distance(targetPos);
        double timeToHit = distance / projectileSpeed;

        // Predict target position
        Vector2 predictedPos = targetPos.copy();
        predictedPos.add(targetVel.x * timeToHit, targetVel.y * timeToHit);

        return predictedPos;
    }

    /**
     * Check if the turret can fire
     */
    public boolean canFire() {
        if (!active || currentTarget == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / fireRate;
        return (now - lastShotTime) >= fireInterval;
    }

    /**
     * Check if the turret has expired
     */
    public boolean isExpired() {
        return !active || System.currentTimeMillis() > expires;
    }

    /**
     * Get the remaining lifespan as a percentage
     */
    public double getLifespanPercent() {
        long lifespan = expires - created;
        long timeRemaining = expires - System.currentTimeMillis();
        return lifespan > 0 ? Math.max(0, timeRemaining / lifespan) : 0;
    }
}

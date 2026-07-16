package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Projectile extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double damage;
    private final Vector2 initialPosition;
    private double timeToLive;
    private final double linearDamping;
    private final Set<BulletEffect> bulletEffects;
    private final Ordinance ordinance;
    private boolean hasExploded = false;
    private boolean dismissedByVelocity = false;
    private boolean dismissedByRange = false;

    /**
     * Size multiplier from the weapon's CALIBER attribute (1.0 = baseline).
     */
    private final double caliber;

    /**
     * Per-hit impulse from the weapon's KNOCKBACK attribute (0 = no shove).
     */
    private final double knockback;

    // prevent double hits
    private final Set<Integer> affectedPlayers;
    private final Set<Integer> affectedObstacles;

    public Projectile(int ownerId, double x, double y, double vx, double vy, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects, Ordinance ordinance,
                      double caliber, double knockback) {
        super(Config.nextEntityId(), createProjectileBody(x, y, vx, vy, linearDamping, bulletEffects, caliber), 1.0);
        this.initialPosition = new Vector2(x, y);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.damage = damage;
        this.linearDamping = linearDamping;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;
        this.caliber = caliber;
        this.knockback = knockback;

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

    /**
     * Base projectile radius at caliber 1.0; CALIBER is the only size input.
     */
    private static final double BASE_RADIUS = 2.0;

    private static Body createProjectileBody(double x, double y, double vx, double vy, double linearDamping, Set<BulletEffect> bulletEffects, double caliber) {
        Body body = new Body();
        // Radius comes entirely from the weapon's caliber (baseline ×1.0 = BASE_RADIUS).
        Circle circle = new Circle(BASE_RADIUS * caliber);
        BodyFixture bodyFixture = body.addFixture(circle);

        // Set restitution for bouncy projectiles
        if (bulletEffects.contains(BulletEffect.BOUNCY)) {
            bodyFixture.setRestitution(0.8); // High bounce - retains 80% of velocity
        } else {
            bodyFixture.setRestitution(0.0); // No bounce for non-bouncy projectiles
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

        // Check velocity threshold for dismissal. Bigger-caliber (heavier) rounds
        // carry momentum, so they persist to a lower speed before dismissal.
        double currentSpeed = body.getLinearVelocity().getMagnitude();
        if (currentSpeed < ordinance.getMinimumVelocity() / caliber && !dismissedByVelocity) {
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

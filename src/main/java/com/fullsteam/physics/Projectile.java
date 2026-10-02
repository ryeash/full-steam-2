package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Projectile extends OwnedGameEntity {
    private final double damage;
    private final Vector2 initialPosition;
    private double timeToLive;
    private final double linearDamping;
    private final Set<BulletEffect> bulletEffects;
    private final Ordinance ordinance;
    private boolean hasExploded = false;
    private boolean dismissedByVelocity = false;
    private boolean dismissedByRange = false;
    private final double caliber;
    private final double knockback;
    private final double maxRange;

    // Missile-specific flight and steering dynamics
    private double heading;
    private final double targetSpeed;
    private final double topSpeed;
    private final double thrustAcceleration;
    private final double lateralDampingRate;

    // prevent double hits
    private final Set<Integer> affectedPlayers;
    private final Set<Integer> affectedObstacles;

    public Projectile(int ownerId, Vector2 position, Vector2 velocity, double damage, double maxRange,
                      int ownerTeam, double linearDamping, Set<BulletEffect> bulletEffects, Ordinance ordinance,
                      double caliber, double knockback) {
        super(Config.nextEntityId(), createProjectileBody(position, velocity, linearDamping, bulletEffects, ordinance, caliber), 1.0, ownerId, ownerTeam);
        this.initialPosition = position.copy();
        this.damage = damage;
        this.linearDamping = linearDamping;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;
        this.caliber = caliber;
        this.knockback = knockback;
        this.maxRange = maxRange;

        double speed = velocity.copy().getMagnitude();
        this.heading = Math.atan2(velocity.y, velocity.x);

        if (ordinance == Ordinance.MISSILE) {
            this.targetSpeed = speed > 0 ? speed : 300.0;
            // Linear damping coupling:
            // Baseline damping is 0.09 (0 pts). Higher investment (lower damping 0.09 -> 0.00) boosts thrust.
            // Lower investment (higher damping 0.09 -> 0.39) reduces thrust and top speed.
            double boostFactor = 1.0 + (0.09 - linearDamping) * 1.5;
            this.topSpeed = this.targetSpeed * Math.max(0.6, 0.9 + 0.3 * boostFactor);
            double launchSpeed = Math.max(80.0, this.targetSpeed * 0.25);

            // Scale initial velocity to launch speed (start slow)
            Vector2 launchDir = speed > 0 ? velocity.copy() : new Vector2(Math.cos(heading), Math.sin(heading));
            launchDir.normalize();
            this.body.setLinearVelocity(launchDir.multiply(launchSpeed));
            this.body.getTransform().setRotation(heading);

            // Forward thrust acceleration from launch speed to top speed over ~0.85s
            this.thrustAcceleration = Math.max(150.0, ((topSpeed - launchSpeed) / 0.85) * Math.max(0.5, boostFactor));
            // Natural lateral slide damping: lower damping attribute investment lets missile slide slightly longer
            this.lateralDampingRate = Math.max(2.0, 3.5 / Math.max(0.5, boostFactor));

            // Time to live based on average speed over the range
            double avgSpeed = (launchSpeed + topSpeed) * 0.5;
            this.timeToLive = avgSpeed > 0 ? maxRange / avgSpeed : 0;
        } else {
            this.targetSpeed = speed;
            this.topSpeed = speed;
            this.thrustAcceleration = 0.0;
            this.lateralDampingRate = 0.0;
            if (speed > 0) {
                this.timeToLive = maxRange / speed;
            } else {
                this.timeToLive = 0; // Deactivate immediately if speed is zero
            }
        }
        this.affectedPlayers = new HashSet<>();
        this.affectedObstacles = new HashSet<>();
    }

    /**
     * Base projectile radius at caliber 1.0; CALIBER is the only size input.
     */
    private static final double BASE_RADIUS = 2.0;

    private static Body createProjectileBody(Vector2 position, Vector2 velocity, double linearDamping,
                                             Set<BulletEffect> bulletEffects, Ordinance ordinance, double caliber) {
        Body body = new Body();
        BodyFixture bodyFixture;
        if (ordinance == Ordinance.MISSILE) {
            // Triangular body for missile: nose points along +X, length 12 * caliber, width 6 * caliber
            double halfLength = 6.0 * caliber;
            double halfWidth = 3.0 * caliber;
            // CCW vertices: front tip, rear-top, rear-bottom
            Vector2 tip = new Vector2(halfLength, 0.0);
            Vector2 rearTop = new Vector2(-halfLength, halfWidth);
            Vector2 rearBottom = new Vector2(-halfLength, -halfWidth);
            Polygon triangle = new Polygon(tip, rearTop, rearBottom);
            bodyFixture = body.addFixture(triangle);
        } else {
            Circle circle = new Circle(BASE_RADIUS * caliber);
            bodyFixture = body.addFixture(circle);
        }

        // Set restitution for bouncy projectiles
        if (bulletEffects.contains(BulletEffect.BOUNCY)) {
            bodyFixture.setRestitution(0.8); // High bounce - retains 80% of velocity
        } else {
            bodyFixture.setRestitution(0.0); // No bounce for non-bouncy projectiles
        }

        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(position);
        double angle = Math.atan2(velocity.y, velocity.x);
        body.getTransform().setRotation(angle);
        body.setLinearVelocity(velocity);
        body.setBullet(true);
        body.setLinearDamping(ordinance == Ordinance.MISSILE ? 0.0 : linearDamping);
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

        if (ordinance == Ordinance.MISSILE) {
            updateMissilePhysics(deltaTime);
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

    private void updateMissilePhysics(double deltaTime) {
        Vector2 vel = body.getLinearVelocity();
        double speed = vel.getMagnitude();

        // Bounce recovery for bouncy missiles: if velocity flipped backwards against heading, align heading to bounce
        if (bulletEffects.contains(BulletEffect.BOUNCY) && speed > 10.0) {
            Vector2 forward = new Vector2(Math.cos(heading), Math.sin(heading));
            if (vel.dot(forward) < -speed * 0.2) {
                heading = Math.atan2(vel.y, vel.x);
            }
        }

        Vector2 forward = new Vector2(Math.cos(heading), Math.sin(heading));
        Vector2 lateral = new Vector2(-Math.sin(heading), Math.cos(heading));

        double vForward = vel.dot(forward);
        double vLateral = vel.dot(lateral);

        // Forward rocket propulsion accelerating toward top speed; always thrusts forward along heading
        vForward = Math.min(topSpeed, Math.max(0.0, vForward) + thrustAcceleration * deltaTime);

        // Natural aerodynamic lateral slide decay
        vLateral *= Math.max(0.0, 1.0 - lateralDampingRate * deltaTime);

        Vector2 newVel = forward.multiply(vForward).add(lateral.multiply(vLateral));
        body.setLinearVelocity(newVel);
        body.getTransform().setRotation(heading);

        // Range check from initial launch position
        if (initialPosition.distance(getPosition()) >= maxRange) {
            dismissedByRange = true;
            active = false;
        }
    }

    public boolean isMissile() {
        return ordinance == Ordinance.MISSILE;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    /**
     * Check if this projectile can damage the given player.
     * Projectiles cannot damage teammates (unless FFA mode).
     *
     * @param player The player to check
     * @return true if projectile can damage this player, false if teammate or self
     */
    public boolean canDamage(Player player) {
        if (player == null || !player.isActive() || player.getHealth() <= 0 || player.getId() == ownerId) {
            return false;
        }
        return ownerTeam == 0 || player.getTeam() == 0 || ownerTeam != player.getTeam();
    }

    /**
     * Check if this projectile can damage the given damageable entity.
     *
     * @param target The damageable entity to check
     * @return true if projectile can damage this entity, false otherwise
     */
    public boolean canDamage(Damageable target) {
        if (target == null || !target.isActive() || target.getHealth() <= 0) {
            return false;
        }
        if (target instanceof Player player) {
            return canDamage(player);
        } else if (target instanceof Turret turret) {
            if (ownerId == turret.getOwnerId()) return false;
            if (ownerTeam == 0 || turret.getOwnerTeam() == 0) return true;
            return ownerTeam != turret.getOwnerTeam();
        } else if (target instanceof Headquarters hq) {
            return ownerTeam != hq.getOwnerTeam();
        } else if (target instanceof Zombie) {
            if (ownerId < 0) {
                return false; // Zombie projectiles pass through and do not damage other zombies
            }
            return true; // Zombies are hostile to all
        } else if (target instanceof Oddball) {
            return ownerId > 0; // Player-fired shots score on Oddballs
        }
        return true;
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

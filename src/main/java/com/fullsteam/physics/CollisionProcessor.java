package com.fullsteam.physics;

import com.fullsteam.games.GameManager;
import com.fullsteam.model.FieldEffect;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Dyn4j collision listener and coordinator.
 * Delegates specific entity interactions to modular impact pipelines:
 * {@link ProjectileImpactHandler}, {@link FieldEffectImpactHandler},
 * {@link MeleeImpactHandler}, {@link ObjectiveImpactHandler}, and {@link NetProjectileImpactHandler}.
 */
public class CollisionProcessor implements CollisionListener<Body, BodyFixture> {

    private static final Logger log = LoggerFactory.getLogger(CollisionProcessor.class);

    private final GameManager gameManager;
    private final GameEntities gameEntities;

    @Getter
    private final BulletEffectProcessor bulletEffectProcessor;

    @Getter
    private final ProjectileImpactHandler projectileHandler;

    @Getter
    private final FieldEffectImpactHandler fieldEffectHandler;

    @Getter
    private final MeleeImpactHandler meleeHandler;

    @Getter
    private final ObjectiveImpactHandler objectiveHandler;

    @Getter
    private final NetProjectileImpactHandler netHandler;

    public CollisionProcessor(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
        this.bulletEffectProcessor = new BulletEffectProcessor(gameEntities);

        this.projectileHandler = new ProjectileImpactHandler(gameManager, gameEntities, bulletEffectProcessor);
        this.fieldEffectHandler = new FieldEffectImpactHandler(gameManager, gameEntities);
        this.meleeHandler = new MeleeImpactHandler(gameManager, gameEntities);
        this.objectiveHandler = new ObjectiveImpactHandler(gameManager, gameEntities);
        this.netHandler = new NetProjectileImpactHandler();
    }

    @Override
    public boolean collision(BroadphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(NarrowphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData<Body, BodyFixture> collision) {
        Body body1 = collision.getBody1();
        Body body2 = collision.getBody2();

        Object userData1 = body1.getUserData();
        Object userData2 = body2.getUserData();

        // 1. Boundary collisions
        if ((userData1 instanceof Projectile || userData1 instanceof NetProjectile) && "boundary".equals(userData2)) {
            ((GameEntity) userData1).setActive(false);
            return false;
        }
        if ((userData2 instanceof Projectile || userData2 instanceof NetProjectile) && "boundary".equals(userData1)) {
            ((GameEntity) userData2).setActive(false);
            return false;
        }

        // 2. Entity-to-entity collisions
        if (userData1 instanceof GameEntity a && userData2 instanceof GameEntity b) {
            if (a.isActive() && b.isActive()) {
                return handleEntityCollision(a, b);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Dispatches physical collision between two game entities to the appropriate impact pipeline.
     *
     * @param a First entity
     * @param b Second entity
     * @return true if physics resolution should proceed, false to ignore or absorb
     */
    public boolean handleEntityCollision(GameEntity a, GameEntity b) {
        if (!a.isActive() || !b.isActive()) {
            return false;
        }

        // 1. Projectile impacts
        if (a instanceof Projectile p1 && b instanceof Projectile p2) {
            return projectileHandler.handleProjectileProjectileImpact(p1, p2);
        }
        if (a instanceof Projectile p && b instanceof Damageable d) {
            return projectileHandler.handleProjectileDamageableImpact(p, d);
        }
        if (b instanceof Projectile p && a instanceof Damageable d) {
            return projectileHandler.handleProjectileDamageableImpact(p, d);
        }
        if (a instanceof Projectile p && b instanceof Obstacle o) {
            return projectileHandler.handleProjectileObstacleImpact(p, o);
        }
        if (b instanceof Projectile p && a instanceof Obstacle o) {
            return projectileHandler.handleProjectileObstacleImpact(p, o);
        }
        if ((a instanceof Projectile && b instanceof DefenseLaser) || (b instanceof Projectile && a instanceof DefenseLaser)) {
            return false; // DefenseLaser is invincible to projectiles
        }

        // 2. FieldEffect overlaps
        if (a instanceof FieldEffect fe && b instanceof Damageable d) {
            fieldEffectHandler.handleFieldEffectDamageableOverlap(fe, d);
            return !(d instanceof Oddball); // Oddball lets beams pass through without resolving physics
        }
        if (b instanceof FieldEffect fe && a instanceof Damageable d) {
            fieldEffectHandler.handleFieldEffectDamageableOverlap(fe, d);
            return !(d instanceof Oddball);
        }
        if (a instanceof FieldEffect fe && b instanceof Projectile p) {
            return fieldEffectHandler.handleFieldEffectProjectileOverlap(fe, p);
        }
        if (b instanceof FieldEffect fe && a instanceof Projectile p) {
            return fieldEffectHandler.handleFieldEffectProjectileOverlap(fe, p);
        }

        // 3. Melee impacts (e.g. Zombie vs Player / HQ / Turret)
        if (a instanceof MeleeAttacker m && b instanceof Damageable d) {
            return meleeHandler.handleMeleeDamageableImpact(m, d);
        }
        if (b instanceof MeleeAttacker m && a instanceof Damageable d) {
            return meleeHandler.handleMeleeDamageableImpact(m, d);
        }

        // 4. Net Projectile impacts
        if (a instanceof NetProjectile net) {
            return netHandler.handleNetImpact(net, b);
        }
        if (b instanceof NetProjectile net) {
            return netHandler.handleNetImpact(net, a);
        }

        // 5. Objective interactions (Flags, KOTH Zones)
        if (a instanceof Player player && b instanceof Flag flag) {
            return objectiveHandler.handlePlayerFlagCollision(player, flag);
        }
        if (b instanceof Player player && a instanceof Flag flag) {
            return objectiveHandler.handlePlayerFlagCollision(player, flag);
        }
        if (a instanceof Player player && b instanceof KothZone zone) {
            return objectiveHandler.handlePlayerKothZoneCollision(player, zone);
        }
        if (b instanceof Player player && a instanceof KothZone zone) {
            return objectiveHandler.handlePlayerKothZoneCollision(player, zone);
        }

        return true;
    }

    /**
     * Update all KOTH zones - called once per physics step with proper deltaTime.
     */
    public void updateKothZones(double deltaTime) {
        objectiveHandler.updateKothZones(deltaTime);
    }

    /**
     * Determines whether an attack is blocked by a player's riot shield.
     * Blocks attacks within a 120-degree frontal cone (cos(60 deg) = 0.5).
     *
     * @param victim          Player receiving the attack
     * @param attackDirection Vector of the incoming attack
     * @return true if successfully blocked by the riot shield
     */
    public static boolean isBlockedByRiotShield(Player victim, Vector2 attackDirection) {
        if (victim == null || !victim.isRiotShieldActive() || attackDirection == null) {
            return false;
        }
        Vector2 aimDir = victim.getAimDirection().getNormalized();
        if (aimDir.getMagnitude() == 0) {
            return false;
        }
        Vector2 attackDir = attackDirection.getNormalized();
        if (attackDir.getMagnitude() == 0) {
            return false;
        }
        double dot = aimDir.dot(attackDir.copy().negate());
        return dot >= 0.5; // cos(60 deg) = 0.5 => 120 degree cone
    }
}

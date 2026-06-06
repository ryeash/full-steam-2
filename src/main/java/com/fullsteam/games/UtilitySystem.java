package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.UtilityActivation;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Manages all utility weapon functionality including field effects, entity-based utilities, and utility beams.
 * This system handles utility activation, placement, and special utility behaviors.
 */
public class UtilitySystem {
    private static final Logger log = LoggerFactory.getLogger(UtilitySystem.class);

    private final GameEntities gameEntities;
    private final World<Body> world;
    private final BiFunction<Vector2, Double, Boolean> isPositionClearCheck;

    public UtilitySystem(GameEntities gameEntities,
                         World<Body> world,
                         BiFunction<Vector2, Double, Boolean> isPositionClearCheck) {
        this.gameEntities = gameEntities;
        this.world = world;
        this.isPositionClearCheck = isPositionClearCheck;
    }

    /**
     * Process utility weapon activation and create appropriate effects.
     */
    public void handleUtilityActivation(UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon();
        if (utility.isFieldEffectBased()) {
            createFieldEffectUtility(activation);
        } else if (utility.isEntityBased()) {
            createEntityUtility(activation);
        }
    }

    /**
     * Create a FieldEffect for utility weapons that use the field effect system.
     */
    private void createFieldEffectUtility(UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon();
        FieldEffectType effectType = utility.getFieldEffectType();
        Vector2 targetPos = activation.position().copy();
        if (utility.getRange() > 0) {
            Vector2 offset = activation.direction().copy();
            offset.multiply(utility.getRange());
            targetPos.add(offset);
        }
        FieldEffect fieldEffect = new FieldEffect(
                activation.playerId(),
                effectType,
                targetPos,
                utility.getRadius(),
                utility.getDamage(),
                effectType.getDefaultDuration(),
                activation.team()
        );
        gameEntities.add(fieldEffect);
    }

    /**
     * Create custom entities for utility weapons that need complex behavior.
     */
    private void createEntityUtility(UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon();
        switch (utility) {
            case TURRET_CONSTRUCTOR:
                createTurret(activation);
                break;
            case NET_LAUNCHER:
                createNetProjectile(activation);
                break;
            case MINE_LAYER:
                createProximityMine(activation);
                break;
            case DEFENSE_LASER:
                createDefenseLaser(activation);
                break;
            case SMOKE_GRENADE:
                createSmokeProjectile(activation);
                break;
            default:
                log.warn("Unknown entity-based utility weapon: {}", utility.getDisplayName());
                break;
        }
    }

    /**
     * Create a turret entity.
     */
    private void createTurret(UtilityActivation activation) {
        Vector2 placement = activation.position().copy();
        Vector2 offset = activation.direction().copy();
        offset.multiply(50.0); // Place 50 units in front
        placement.add(offset);
        double turretRadius = 15.0;
        if (!isPositionClearCheck.apply(placement, turretRadius)) {
            Player player = gameEntities.getPlayer(activation.playerId());
            if (player != null) {
                player.refundUtilityCooldown();
            }
            return;
        }
        Turret turret = new Turret(
                activation.playerId(),
                activation.team(),
                placement,
                15.0
        );
        gameEntities.add(turret);
    }

    /**
     * Create a net projectile entity.
     */
    private void createNetProjectile(UtilityActivation activation) {
        Vector2 velocity = activation.direction().copy();
        velocity.multiply(300.0);
        NetProjectile netProjectile = new NetProjectile(
                Config.nextEntityId(),
                activation.playerId(),
                activation.team(),
                activation.position(),
                velocity,
                2.0
        );
        gameEntities.add(netProjectile);
    }

    /**
     * Create a proximity mine entity.
     */
    private void createProximityMine(UtilityActivation activation) {
        FieldEffect mine = new FieldEffect(
                activation.playerId(),
                FieldEffectType.PROXIMITY_MINE,
                activation.position(),
                45.0,
                45.0,
                1.0,
                15.0,
                System.currentTimeMillis() + 1000,
                activation.team()
        );
        gameEntities.add(mine);
    }

    /**
     * Create a defense laser entity.
     */
    private void createDefenseLaser(UtilityActivation activation) {
        Vector2 placement = activation.position().copy();
        Vector2 offset = activation.direction().copy();
        offset.multiply(60.0);
        placement.add(offset);

        double laserRadius = 20.0;
        if (!isPositionClearCheck.apply(placement, laserRadius)) {
            Player player = gameEntities.getPlayer(activation.playerId());
            if (player != null) {
                player.refundUtilityCooldown();
            }
            return;
        }
        DefenseLaser defenseLaser = new DefenseLaser(
                activation.playerId(),
                activation.team(),
                placement,
                20.0,
                world
        );
        gameEntities.add(defenseLaser);
        for (Beam beam : defenseLaser.getBeams()) {
            gameEntities.add(beam);
        }
    }

    /**
     * Create a smoke grenade using the standard Projectile system with GRENADE ordinance
     * and SMOKE bullet effect. The projectile arcs, slows, and detonates into a SMOKE
     * field effect via BulletEffectProcessor when dismissed.
     */
    private void createSmokeProjectile(UtilityActivation activation) {
        Vector2 velocity = activation.direction().copy();
        velocity.multiply(250.0);
        Projectile grenade = new Projectile(
                activation.playerId(),
                activation.position().x,
                activation.position().y,
                velocity.x,
                velocity.y,
                0.0,
                activation.utilityWeapon().getRange(),
                activation.team(),
                0.87,
                Set.of(BulletEffect.SMOKE),
                Ordinance.GRENADE
        );
        gameEntities.add(grenade);
    }
}

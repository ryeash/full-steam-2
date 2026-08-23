package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
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
    public static final long PROXIMITY_MINE_ARMING_TIME = 1;
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
        gameEntities.add(new FieldEffectCircle(
                activation.playerId(),
                effectType,
                targetPos,
                utility.getRadius(),
                utility.getRadius(),
                utility.getDamage(),
                effectType.getDefaultDuration(),
                0,
                activation.team()
        ));
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
            case STRIKE_BEACON:
                createStrikeBeacon(activation);
                break;
            case RIOT_SHIELD:
                createRiotShield(activation);
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
                15.0,
                WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon()
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
        gameEntities.add(new FieldEffectCircle(
                activation.playerId(),
                FieldEffectType.WARNING_ZONE,
                activation.position(),
                45.0,
                45.0,
                0,
                PROXIMITY_MINE_ARMING_TIME,
                0,
                activation.team()
        ));
        gameEntities.add(new FieldEffectCircle(
                activation.playerId(),
                FieldEffectType.PROXIMITY_MINE,
                activation.position(),
                45.0,
                45.0,
                0,
                15.0,
                System.currentTimeMillis() + PROXIMITY_MINE_ARMING_TIME * 1000,
                activation.team()
        ));
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
        for (FieldEffectBeam beam : defenseLaser.getBeams()) {
            gameEntities.add(beam);
        }
    }

    /**
     * Create a smoke grenade using the standard Projectile system (PROJECTILE ordinance,
     * caliber-sized) and SMOKE bullet effect. The projectile slows and detonates into a SMOKE
     * field effect via BulletEffectProcessor when dismissed.
     */
    private void createSmokeProjectile(UtilityActivation activation) {
        gameEntities.add(new Projectile(
                activation.playerId(),
                activation.position().add(activation.direction().getNormalized().multiply(Config.PLAYER_RADIUS)),
                activation.direction().copy().multiply(250.0),
                0.0,
                activation.utilityWeapon().getRange(),
                activation.team(),
                0.87,
                Set.of(BulletEffect.SMOKE),
                Ordinance.PROJECTILE,
                2.0,
                0.0
        ));
    }

    /**
     * Create a Strike Beacon: a thrown, no-damage projectile that lobs like the
     * smoke grenade and lands via damping/range. It carries the (utility-only)
     * STRIKE bullet effect, so on dismissal BulletEffectProcessor drops the warning
     * zone + delayed explosion at the landing spot — no special-casing here. The
     * STRIKE effect also drives the client's blinking-beacon render.
     */
    private void createStrikeBeacon(UtilityActivation activation) {
        Vector2 velocity = activation.direction().copy();
        velocity.multiply(350.0);
        Projectile beacon = new Projectile(
                activation.playerId(),
                activation.position(),
                velocity,
                0.0,
                activation.utilityWeapon().getRange(),
                activation.team(),
                0.87,
                Set.of(BulletEffect.STRIKE),
                Ordinance.PROJECTILE,
                1.5,
                0.0
        );
        gameEntities.add(beacon);
    }

    private void createRiotShield(UtilityActivation activation) {
        Player player = gameEntities.getPlayer(activation.playerId());
        if (player != null) {
            StatusEffectManager.applyRiotShield(player, 6.0);
        }
    }
}

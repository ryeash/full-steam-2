package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeleportPad;
import com.fullsteam.physics.Turret;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Manages all utility weapon functionality including field effects, entity-based utilities, and utility beams.
 * This system handles utility activation, placement, and special utility behaviors.
 */
public class UtilitySystem {
    private static final Logger log = LoggerFactory.getLogger(UtilitySystem.class);

    private final GameEntities gameEntities;
    private final World<Body> world;
    private final Function<Vector2, Boolean> isPositionClearCheck;
    private final BiConsumer<String, String> gameEventBroadcaster;
    private final WeaponSystem weaponSystem;

    public UtilitySystem(
            GameEntities gameEntities,
            World<Body> world,
            Function<Vector2, Boolean> isPositionClearCheck,
            BiConsumer<String, String> gameEventBroadcaster,
            WeaponSystem weaponSystem) {
        this.gameEntities = gameEntities;
        this.world = world;
        this.isPositionClearCheck = isPositionClearCheck;
        this.gameEventBroadcaster = gameEventBroadcaster;
        this.weaponSystem = weaponSystem;
    }

    /**
     * Process utility weapon activation and create appropriate effects.
     */
    public void handleUtilityActivation(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;

        if (utility.isFieldEffectBased()) {
            // Create FieldEffect for area-based utilities
            createFieldEffectUtility(activation);
        } else if (utility.isEntityBased()) {
            // Create custom entity for complex utilities
            createEntityUtility(activation);
        } else if (utility.isBeamBased()) {
            // Create beam for line-of-sight utilities
            createBeamUtility(activation);
        }
    }

    /**
     * Create a FieldEffect for utility weapons that use the field effect system.
     */
    private void createFieldEffectUtility(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;
        FieldEffectType effectType = utility.getFieldEffectType();

        // Calculate target position based on utility range and aim direction
        Vector2 targetPos = activation.position.copy();
        if (utility.getRange() > 0) {
            Vector2 offset = activation.direction.copy();
            offset.multiply(utility.getRange());
            targetPos.add(offset);
        }

        // Create the field effect
        FieldEffect fieldEffect = new FieldEffect(
                Config.nextId(),
                activation.playerId,
                effectType,
                targetPos,
                utility.getRadius(),
                utility.getDamage(),
                effectType.getDefaultDuration(),
                activation.team
        );

        gameEntities.addFieldEffect(fieldEffect);
        world.addBody(fieldEffect.getBody());

        log.debug("Created {} field effect at ({}, {}) with radius {} for player {}",
                effectType.name(), targetPos.x, targetPos.y, utility.getRadius(), activation.playerId);
    }

    /**
     * Create custom entities for utility weapons that need complex behavior.
     */
    private void createEntityUtility(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;

        switch (utility) {
            case TURRET_CONSTRUCTOR:
                createTurret(activation);
                break;
            case WALL_BUILDER:
                createBarrier(activation);
                break;
            case NET_LAUNCHER:
                createNetProjectile(activation);
                break;
            case MINE_LAYER:
                createProximityMine(activation);
                break;
            case TELEPORTER:
                createTeleportPad(activation);
                break;
            default:
                log.warn("Unknown entity-based utility weapon: {}", utility.getDisplayName());
                break;
        }
    }

    /**
     * Create a turret entity.
     */
    private void createTurret(Player.UtilityActivation activation) {
        // Calculate placement position slightly in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(50.0); // Place 50 units in front
        placement.add(offset);

        // Check if placement position is clear of obstacles
        double turretRadius = 15.0; // Turret radius from Turret class
        if (!isPositionClear(placement, turretRadius)) {
            log.debug("Player {} tried to place turret at ({}, {}) but position is blocked by obstacle",
                    activation.playerId, placement.x, placement.y);

            // Send feedback to player that placement failed
            Player player = gameEntities.getPlayer(activation.playerId);
            if (player != null) {
                // Refund the cooldown since placement failed
                player.refundUtilityCooldown();
            }
            return;
        }

        Turret turret = new Turret(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                15.0 // 15 second lifespan
        );

        gameEntities.addTurret(turret);
        world.addBody(turret.getBody());
        log.debug("Player {} deployed turret at ({}, {})", activation.playerId, placement.x, placement.y);
    }

    /**
     * Create a barrier/wall entity.
     */
    private void createBarrier(Player.UtilityActivation activation) {
        // Calculate placement position in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(40.0); // Place 40 units in front
        placement.add(offset);

        Obstacle barrier = Obstacle.createPlayerBarrier(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                activation.direction,
                20.0 // 20 second lifespan
        );

        gameEntities.addObstacle(barrier);
        world.addBody(barrier.getBody());

        log.debug("Player {} created barrier at ({}, {})", activation.playerId, placement.x, placement.y);
    }

    /**
     * Create a net projectile entity.
     */
    private void createNetProjectile(Player.UtilityActivation activation) {
        // Fire net projectile in aim direction
        Vector2 velocity = activation.direction.copy();
        velocity.multiply(300.0); // Net projectile speed

        NetProjectile netProjectile = new NetProjectile(
                Config.nextId(),
                activation.playerId,
                activation.team,
                activation.position,
                velocity,
                5.0 // 5 second time to live
        );

        gameEntities.addNetProjectile(netProjectile);
        world.addBody(netProjectile.getBody());

        log.debug("Player {} launched net projectile", activation.playerId);
    }

    /**
     * Create a proximity mine entity.
     */
    private void createProximityMine(Player.UtilityActivation activation) {
        // Place mine at player's current position
        FieldEffect mine = new FieldEffect(
                Config.nextId(),
                activation.playerId,
                FieldEffectType.PROXIMITY_MINE,
                activation.position,
                45.0, // Small visual radius for mine
                1.0, // Damage (not used for mines, explosion handles damage)
                15.0, // 15 second lifespan
                activation.team
        );

        gameEntities.addFieldEffect(mine);
        world.addBody(mine.getBody());
        log.debug("Player {} placed proximity mine at ({}, {})",
                activation.playerId, activation.position.x, activation.position.y);
    }

    /**
     * Create a teleport pad entity.
     */
    private void createTeleportPad(Player.UtilityActivation activation) {
        // Calculate placement position slightly in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(30.0); // Place 30 units in front
        placement.add(offset);

        TeleportPad teleportPad = new TeleportPad(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                60.0 // 60 second lifespan
        );

        gameEntities.addTeleportPad(teleportPad);
        world.addBody(teleportPad.getBody());

        // Try to link with existing teleport pad from same player
        linkTeleportPads(teleportPad, activation.playerId);

        log.debug("Player {} placed teleport pad at ({}, {})",
                activation.playerId, placement.x, placement.y);
    }

    /**
     * Link teleport pads from the same player.
     */
    private void linkTeleportPads(TeleportPad newPad, int playerId) {
        // Find existing unlinked teleport pad from same player
        for (TeleportPad existingPad : gameEntities.getAllTeleportPads()) {
            if (existingPad.getId() != newPad.getId() &&
                existingPad.getOwnerId() == playerId &&
                !existingPad.isLinked() &&
                existingPad.isActive()) {

                // Link the pads
                newPad.linkTo(existingPad);
                log.debug("Linked teleport pads {} and {} for player {}",
                        newPad.getId(), existingPad.getId(), playerId);
                break; // Only link to one pad
            }
        }
    }

    /**
     * Create a beam for utility weapons that use the beam system.
     */
    private void createBeamUtility(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;
        Ordinance beamOrdinance = utility.getBeamOrdinance();

        // Create beam using the utility's beam ordinance
        Beam utilityBeam = new Beam(
                Config.nextId(),
                activation.position,
                activation.direction,
                utility.getRange(),
                utility.getDamage(),
                activation.playerId,
                activation.team,
                beamOrdinance,
                Set.of() // Utility beams don't have bullet effects
        );

        // Update beam's effective end point based on obstacle collisions
        Vector2 effectiveEnd = weaponSystem.findBeamObstacleIntersectionPublic(
                utilityBeam.getStartPoint(),
                utilityBeam.getEndPoint()
        );
        utilityBeam.setEffectiveEndPoint(effectiveEnd);

        gameEntities.addBeam(utilityBeam);
        world.addBody(utilityBeam.getBody());

        // Process initial hit for instant damage beams
        if (utilityBeam.getDamageApplicationType() == DamageApplicationType.INSTANT) {
            weaponSystem.processBeamInitialHitPublic(utilityBeam);
        }

        log.debug("Player {} fired utility beam: {}", activation.playerId, utility.getDisplayName());
    }

    /**
     * Check if a position is clear of obstacles.
     */
    private boolean isPositionClear(Vector2 position, double radius) {
        return isPositionClearCheck.apply(position);
    }

    /**
     * Broadcast a warning message to players.
     */
    private void broadcastWarning(String message) {
        gameEventBroadcaster.accept(message, "WARNING");
    }

    /**
     * Get statistics about active utilities.
     */
    public UtilityStats getStats() {
        int totalTurrets = gameEntities.getAllTurrets().size();
        int totalTeleportPads = gameEntities.getAllTeleportPads().size();
        int totalNetProjectiles = gameEntities.getAllNetProjectiles().size();
        int totalFieldEffects = gameEntities.getFieldEffects().size();

        return new UtilityStats(totalTurrets, totalTeleportPads, totalNetProjectiles, totalFieldEffects);
    }

    /**
     * Statistics about active utilities in the game.
     */
    public record UtilityStats(
            int totalTurrets,
            int totalTeleportPads,
            int totalNetProjectiles,
            int totalFieldEffects
    ) {
    }
}

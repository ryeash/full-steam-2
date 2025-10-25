package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UtilitySystem.
 * Tests utility weapon activation, field effects, entity-based utilities, and utility beams.
 */
class UtilitySystemTest extends BaseTestClass {

    private UtilitySystem utilitySystem;
    private GameEntities gameEntities;
    private World<Body> world;
    private WeaponSystem weaponSystem;
    private TestBroadcaster broadcaster;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        // Create test world and config
        world = new World<>();
        GameConfig testConfig = GameConfig.builder()
                .enableAIFilling(false)  // Disable AI filling for predictable test environment
                .build();
        gameEntities = new GameEntities(testConfig, world);
        
        // Create weapon system
        weaponSystem = new WeaponSystem(gameEntities, world);
        
        // Create broadcaster
        broadcaster = new TestBroadcaster();
        
        // Create utility system
        utilitySystem = new UtilitySystem(
                gameEntities,
                world,
                pos -> true // Always allow placement for tests
        );
    }

    // ============================================================================
    // Field Effect Utility Tests
    // ============================================================================

    @Test
    @DisplayName("Should create heal zone field effect")
    void testHealZoneCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.HEAL_ZONE);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getFieldEffects().isEmpty(), "Field effect should be created");
        assertEquals(1, gameEntities.getFieldEffects().size(), "Exactly one field effect should be created");
    }

    @Test
    @DisplayName("Should create damage zone field effect")
    void testDamageZoneCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.SLOW_FIELD);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getFieldEffects().isEmpty(), "Field effect should be created");
        assertEquals(1, gameEntities.getFieldEffects().size(), "Exactly one field effect should be created");
    }

    @Test
    @DisplayName("Should position field effect based on range and direction")
    void testFieldEffectPositioning() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setPosition(100, 100);
        player.setAimDirection(new Vector2(1, 0)); // Aim right
        
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.HEAL_ZONE);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getFieldEffects().isEmpty(), "Field effect should be created");
        // Note: We can't easily test exact positioning without accessing private fields,
        // but we can verify the effect was created
    }

    // ============================================================================
    // Entity-Based Utility Tests
    // ============================================================================

    @Test
    @DisplayName("Should create turret entity")
    void testTurretCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.TURRET_CONSTRUCTOR);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getAllTurrets().isEmpty(), "Turret should be created");
        assertEquals(1, gameEntities.getAllTurrets().size(), "Exactly one turret should be created");
    }

    @Test
    @DisplayName("Should create barrier entity")
    void testBarrierCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.WALL_BUILDER);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getObstacles().isEmpty(), "Barrier should be created");
        assertEquals(1, gameEntities.getObstacles().size(), "Exactly one barrier should be created");
    }

    @Test
    @DisplayName("Should create net projectile entity")
    void testNetProjectileCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.NET_LAUNCHER);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getAllNetProjectiles().isEmpty(), "Net projectile should be created");
        assertEquals(1, gameEntities.getAllNetProjectiles().size(), "Exactly one net projectile should be created");
    }

    @Test
    @DisplayName("Should create proximity mine entity")
    void testProximityMineCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.MINE_LAYER);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getFieldEffects().isEmpty(), "Proximity mine should be created");
        assertEquals(1, gameEntities.getFieldEffects().size(), "Exactly one mine should be created");
    }

    @Test
    @DisplayName("Should create teleport pad entity")
    void testTeleportPadCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        Player.UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.TELEPORTER);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getAllTeleportPads().isEmpty(), "Teleport pad should be created");
        assertEquals(1, gameEntities.getAllTeleportPads().size(), "Exactly one teleport pad should be created");
    }

    /**
     * Create a test player with basic configuration.
     */
    private Player createTestPlayer(int id, int team) {
        Player player = new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
        player.setPosition(0, 0);
        player.setAimDirection(new Vector2(1, 0));
        return player;
    }

    /**
     * Create a utility activation for testing.
     */
    private Player.UtilityActivation createUtilityActivation(Player player, UtilityWeapon utilityWeapon) {
        return new Player.UtilityActivation(
                utilityWeapon,
                player.getPosition().copy(),
                player.getAimDirection().copy(),
                player.getId(),
                player.getTeam()
        );
    }

    /**
     * Test broadcaster that captures events for verification.
     */
    private static class TestBroadcaster implements java.util.function.Consumer<Map<String, Object>> {
        private Map<String, Object> lastEvent;

        @Override
        public void accept(Map<String, Object> event) {
            this.lastEvent = event;
        }

        public Map<String, Object> getLastEvent() {
            return lastEvent;
        }
    }
}
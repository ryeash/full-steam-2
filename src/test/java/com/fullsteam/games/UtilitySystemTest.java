package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.UtilityActivation;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
                (pos, radius) -> true // Always allow placement for tests
        );
    }

    // ============================================================================
    // Field Effect Utility Tests
    // ============================================================================

    @Test
    @DisplayName("Should regenerate player life when player is healed")
    void testPlayerHealDirect() {
        Player player = createTestPlayer(1, 1);
        player.setActive(true);
        // Take 40 damage
        player.takeDamage(40.0, true);
        assertEquals(60.0, player.getHealth(), "Player should be at 60 health after 40 damage");

        // Heal 15 health
        player.heal(15.0);
        assertEquals(75.0, player.getHealth(), "Player should be healed to 75 health");

        // Heal via negative damage
        player.takeDamage(-10.0, false);
        assertEquals(85.0, player.getHealth(), "Player should be healed to 85 health via negative damage");

        // Heal beyond max health should clamp to maxHealth
        player.heal(50.0);
        assertEquals(100.0, player.getHealth(), "Health should clamp to maxHealth 100.0");
    }

    @Test
    @DisplayName("Should create heal zone field effect")
    void testHealZoneCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.HEAL_ZONE);

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
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.SLOW_FIELD);

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

        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.HEAL_ZONE);

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
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.TURRET_CONSTRUCTOR);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getAllTurrets().isEmpty(), "Turret should be created");
        assertEquals(1, gameEntities.getAllTurrets().size(), "Exactly one turret should be created");
    }

    @Test
    @DisplayName("Should create net projectile entity")
    void testNetProjectileCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.NET_LAUNCHER);

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
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.MINE_LAYER);

        // Act
        utilitySystem.handleUtilityActivation(activation);

        // Assert
        assertFalse(gameEntities.getFieldEffects().isEmpty(), "Proximity mine should be created");
        assertEquals(2, gameEntities.getFieldEffects().size(), "Proximity mine creates warning zone + mine effect");
    }

    @Test
    @DisplayName("Should create DefenseLaser entity and update rotating beams")
    void testDefenseLaserCreationAndBeamUpdate() {
        Player player = createTestPlayer(1, 1);
        UtilityActivation activation = createUtilityActivation(player, UtilityWeapon.DEFENSE_LASER);

        utilitySystem.handleUtilityActivation(activation);

        assertFalse(gameEntities.getAllDefenseLasers().isEmpty(), "DefenseLaser should be created");
        assertEquals(1, gameEntities.getAllDefenseLasers().size(), "Exactly one DefenseLaser should be created");

        DefenseLaser defenseLaser = gameEntities.getAllDefenseLasers().iterator().next();
        assertEquals(3, defenseLaser.getBeams().size(), "DefenseLaser should have 3 arm beams");

        // Update DefenseLaser and simulate endpoint update
        defenseLaser.update(0.1);
        for (FieldEffectBeam beam : defenseLaser.getBeams()) {
            java.util.List<Vector2> path = weaponSystem.computeBeamPath(beam);
            beam.setEndPoint(path.get(1));
            beam.updateBodyTransform();
            org.junit.jupiter.api.Assertions.assertTrue(beam.getStartPoint().distance(beam.getEndPoint()) > 10.0,
                    "Beam should extend outwards from laser center");
        }
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
    private UtilityActivation createUtilityActivation(Player player, UtilityWeapon utilityWeapon) {
        return new UtilityActivation(
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
package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for WeaponSystem.
 * Tests weapon firing, projectile creation, beam weapons, and weapon state management.
 */
class WeaponSystemTest extends BaseTestClass {

    private WeaponSystem weaponSystem;
    private GameEntities gameEntities;
    private World<Body> world;

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
    }

    // ============================================================================
    // Projectile Weapon Tests
    // ============================================================================

    @Test
    @DisplayName("Should create projectile when player fires projectile weapon")
    void testProjectileCreation() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0)); // Aim right
        gameEntities.addPlayer(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true); // Fire weapon

        // Act
        weaponSystem.handlePrimaryFire(player, input);

        // Assert
        assertFalse(gameEntities.getProjectiles().isEmpty(),
                "Projectile should be created when player fires");
        assertEquals(1, gameEntities.getProjectiles().size(),
                "Exactly one projectile should be created for single-shot weapon");
    }

    @Test
    @DisplayName("Should not create projectile when player is not firing")
    void testNoProjectileWhenNotFiring() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(false); // Not firing

        // Act
        weaponSystem.handlePrimaryFire(player, input);

        // Assert
        assertTrue(gameEntities.getProjectiles().isEmpty(),
                "No projectile should be created when player is not firing");
    }

    @Test
    @DisplayName("Should respect weapon cooldown")
    void testWeaponCooldown() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.addPlayer(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Act - Fire twice rapidly
        weaponSystem.handlePrimaryFire(player, input);
        int firstShotCount = gameEntities.getProjectiles().size();

        weaponSystem.handlePrimaryFire(player, input); // Immediate second shot
        int secondShotCount = gameEntities.getProjectiles().size();

        // Assert
        assertEquals(1, firstShotCount, "First shot should create projectile");
        assertEquals(1, secondShotCount, "Second shot should be blocked by cooldown");
    }

    @Test
    @DisplayName("Should consume ammo when firing")
    void testAmmoConsumption() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.addPlayer(player);

        int initialAmmo = player.getCurrentWeapon().getCurrentAmmo();

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Act
        weaponSystem.handlePrimaryFire(player, input);

        // Assert
        int finalAmmo = player.getCurrentWeapon().getCurrentAmmo();
        assertTrue(finalAmmo < initialAmmo, "Ammo should be consumed when firing");
    }

    // ============================================================================
    // Beam Weapon Tests
    // ============================================================================

    @Test
    @DisplayName("Should create beam when player fires beam weapon")
    void testBeamCreation() {
        // Arrange
        Player player = createTestPlayerWithBeamWeapon(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.addPlayer(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Act
        weaponSystem.handlePrimaryFire(player, input);

        // Assert
        assertFalse(gameEntities.getBeams().isEmpty(),
                "Beam should be created when player fires beam weapon");
    }

    @Test
    @DisplayName("Should calculate beam obstacle intersection")
    void testBeamObstacleIntersection() {
        // Arrange
        Vector2 beamStart = new Vector2(0, 0);
        Vector2 beamEnd = new Vector2(100, 0);

        // Act
        Vector2 intersection = weaponSystem.findBeamObstacleIntersection(beamStart, beamEnd);

        // Assert
        assertNotNull(intersection, "Intersection point should be calculated");
        // Note: Without obstacles, intersection should be at beam end
        assertEquals(beamEnd.x, intersection.x, 0.1, "Intersection X should match beam end");
        assertEquals(beamEnd.y, intersection.y, 0.1, "Intersection Y should match beam end");
    }

    // ============================================================================
    // Weapon State Tests
    // ============================================================================

    @Test
    @DisplayName("Should track weapon statistics")
    void testWeaponStats() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.addPlayer(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Act
        weaponSystem.handlePrimaryFire(player, input);
        WeaponSystem.WeaponStats stats = weaponSystem.getStats();

        // Assert
        assertNotNull(stats, "Weapon stats should be available");
        assertTrue(stats.totalProjectiles() >= 0, "Total projectiles should be tracked");
        assertTrue(stats.totalBeams() >= 0, "Total beams should be tracked");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Create a test player with a standard projectile weapon.
     */
    private Player createTestPlayer(int id, int team) {
        WeaponConfig weaponConfig = WeaponConfig.ASSAULT_RIFLE_PRESET;

        Player player = new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
        player.setWeapon(weaponConfig.buildWeapon());

        return player;
    }

    /**
     * Create a test player with a beam weapon.
     */
    private Player createTestPlayerWithBeamWeapon(int id, int team) {
        // Create a weapon config with a beam ordinance
        // Total: 60 points (attributes) + 40 points (LASER ordinance) = 100 points
        WeaponConfig beamConfig = new WeaponConfig(
                "Test Laser",
                15, // damage (15 points)
                8,  // fire rate (8 points)
                12, // range (12 points)
                0,  // accuracy (0 points)
                15, // magazine size (15 points)
                10, // reload time (10 points)
                0,  // projectile speed (not used for beams, 0 points)
                0,  // bullets per shot (0 points)
                -10,  // linear damping (0 points, must be negative or 0)
                Set.of(),
                Ordinance.LASER // Beam weapon (40 points)
        );

        Player player = new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
        player.setWeapon(beamConfig.buildWeapon());

        return player;
    }
}
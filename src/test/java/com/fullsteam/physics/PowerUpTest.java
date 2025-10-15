package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PowerUp entity and power-up functionality.
 * Tests power-up creation, collection mechanics, effects, and lifecycle.
 */
class PowerUpTest extends BaseTestClass {

    private GameManager gameManager;
    private GameConfig gameConfig;
    private Player testPlayer;
    private Workshop testWorkshop;

    @BeforeEach
    void setUp() {
        // Create test configuration with workshops enabled
        Rules rules = Rules.builder()
                .addWorkshops(true)
                .workshopCraftTime(5.0)
                .workshopCraftRadius(80.0)
                .maxPowerUpsPerWorkshop(3)
                .build();

        gameConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .enableAIFilling(false)
                .rules(rules)
                .build();

        // Create game manager
        gameManager = new GameManager("test_game", gameConfig, null);
        
        // Create a test player
        testPlayer = new Player(1, "TestPlayer", 0, 0, 1, 100.0);
        gameManager.getGameEntities().addPlayer(testPlayer);
        
        // Get the test workshop
        testWorkshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
    }

    @Test
    @DisplayName("PowerUp creation with correct properties")
    void testPowerUpCreation() {
        Vector2 spawnPos = new Vector2(100, 100);
        
        PowerUp powerUp = new PowerUp(
                1,
                spawnPos,
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.5
        );
        
        assertNotNull(powerUp);
        assertTrue(powerUp.isActive());
        assertEquals(PowerUp.PowerUpType.SPEED_BOOST, powerUp.getType());
        assertEquals(testWorkshop.getId(), powerUp.getWorkshopId());
        assertEquals(30.0, powerUp.getDuration()); // Duration of effect
        assertEquals(spawnPos, powerUp.getPosition());
        assertTrue(powerUp.getBody().getFixture(0).isSensor()); // Should be a sensor
    }

    @Test
    @DisplayName("All power-up types can be created")
    void testAllPowerUpTypes() {
        Vector2 spawnPos = new Vector2(100, 100);
        
        for (PowerUp.PowerUpType type : PowerUp.PowerUpType.values()) {
            PowerUp powerUp = new PowerUp(
                    1,
                    spawnPos,
                    type,
                    testWorkshop.getId(),
                    30.0,
                    1.0
            );
            
            assertNotNull(powerUp);
            assertEquals(type, powerUp.getType());
            assertNotNull(type.getDisplayName());
            assertNotNull(type.getRenderHint());
        }
    }

    @Test
    @DisplayName("PowerUp effect creation")
    void testPowerUpEffect() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.DAMAGE_BOOST,
                testWorkshop.getId(),
                25.0,
                2.0
        );
        
        PowerUp.PowerUpEffect effect = powerUp.getEffect();
        
        assertNotNull(effect);
        assertEquals(PowerUp.PowerUpType.DAMAGE_BOOST, effect.getType());
        assertEquals(25.0, effect.getDuration());
        assertEquals(2.0, effect.getStrength());
    }

    @Test
    @DisplayName("PowerUp collection detection")
    void testPowerUpCollection() {
        Vector2 playerPos = new Vector2(100, 100);
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        PowerUp powerUp = new PowerUp(
                1,
                playerPos, // Same position as player
                PowerUp.PowerUpType.HEALTH_REGENERATION,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        // Should be collectible by the player
        assertTrue(powerUp.canBeCollectedBy(testPlayer));
        
        // Test with inactive player
        testPlayer.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
        
        // Test with dead player
        testPlayer.setActive(true);
        testPlayer.setHealth(0);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
    }

    @Test
    @DisplayName("PowerUp update with active state")
    void testPowerUpUpdate() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.DAMAGE_RESISTANCE,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        // Update power-up
        powerUp.update(5.0);
        
        // Should still be active
        assertTrue(powerUp.isActive());
        
        // Deactivate power-up
        powerUp.setActive(false);
        powerUp.update(10.0);
        
        // Should still be inactive
        assertFalse(powerUp.isActive());
    }

    @Test
    @DisplayName("PowerUp display names and render hints")
    void testPowerUpDisplayProperties() {
        assertEquals("Speed Boost", PowerUp.PowerUpType.SPEED_BOOST.getDisplayName());
        assertEquals("⚡", PowerUp.PowerUpType.SPEED_BOOST.getRenderHint());
        
        assertEquals("Health Regen", PowerUp.PowerUpType.HEALTH_REGENERATION.getDisplayName());
        assertEquals("❤️", PowerUp.PowerUpType.HEALTH_REGENERATION.getRenderHint());
        
        assertEquals("Damage Boost", PowerUp.PowerUpType.DAMAGE_BOOST.getDisplayName());
        assertEquals("⚔️", PowerUp.PowerUpType.DAMAGE_BOOST.getRenderHint());
        
        assertEquals("Damage Resist", PowerUp.PowerUpType.DAMAGE_RESISTANCE.getDisplayName());
        assertEquals("🛡️", PowerUp.PowerUpType.DAMAGE_RESISTANCE.getRenderHint());
        
        assertEquals("Berserker", PowerUp.PowerUpType.BERSERKER_MODE.getDisplayName());
        assertEquals("🔥", PowerUp.PowerUpType.BERSERKER_MODE.getRenderHint());
    }

    @Test
    @DisplayName("PowerUp sensor behavior")
    void testPowerUpSensorBehavior() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        // Power-up should be a sensor (players can walk through it)
        assertTrue(powerUp.getBody().getFixture(0).isSensor());
        
        // Power-up should have infinite mass (stationary, no bouncing)
        assertSame(powerUp.getBody().getMass().getType(), MassType.INFINITE);
    }

    @Test
    @DisplayName("PowerUp collection with different player states")
    void testPowerUpCollectionStates() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        // Position player at power-up location
        testPlayer.getBody().getTransform().setTranslation(100, 100);
        
        // Active player with health should be able to collect
        testPlayer.setActive(true);
        testPlayer.setHealth(100);
        assertTrue(powerUp.canBeCollectedBy(testPlayer));
        
        // Inactive player should not be able to collect
        testPlayer.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
        
        // Dead player should not be able to collect
        testPlayer.setActive(true);
        testPlayer.setHealth(0);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
        
        // Inactive power-up should not be collectible
        testPlayer.setHealth(100);
        powerUp.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
    }

    @Test
    @DisplayName("PowerUp workshop association")
    void testPowerUpWorkshopAssociation() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        assertEquals(testWorkshop.getId(), powerUp.getWorkshopId());
        
        // Test with different workshop ID
        PowerUp powerUp2 = new PowerUp(
                2,
                new Vector2(200, 200),
                PowerUp.PowerUpType.DAMAGE_BOOST,
                999, // Different workshop ID
                30.0,
                1.0
        );
        
        assertEquals(999, powerUp2.getWorkshopId());
        assertNotEquals(powerUp.getWorkshopId(), powerUp2.getWorkshopId());
    }

    @Test
    @DisplayName("PowerUp effect strength variations")
    void testPowerUpEffectStrength() {
        PowerUp weakPowerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                0.5 // Weak effect
        );
        
        PowerUp strongPowerUp = new PowerUp(
                2,
                new Vector2(200, 200),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                3.0 // Strong effect
        );
        
        assertEquals(0.5, weakPowerUp.getEffectStrength());
        assertEquals(3.0, strongPowerUp.getEffectStrength());
        
        assertEquals(0.5, weakPowerUp.getEffect().getStrength());
        assertEquals(3.0, strongPowerUp.getEffect().getStrength());
    }
}

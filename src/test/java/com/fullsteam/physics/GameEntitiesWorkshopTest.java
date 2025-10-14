package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameEntities workshop and power-up management.
 * Tests the GameEntities class methods for managing workshops and power-ups.
 */
class GameEntitiesWorkshopTest extends BaseTestClass {

    private GameEntities gameEntities;
    private GameConfig gameConfig;

    @BeforeEach
    void setUp() {
        // Create test configuration
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

        gameEntities = new GameEntities(gameConfig, null);
    }

    @Test
    @DisplayName("Add and get workshop")
    void testAddAndGetWorkshop() {
        Workshop workshop = new Workshop(
                1,
                new Vector2(100, 100),
                80.0,
                5.0,
                3
        );
        
        // Initially no workshops
        assertEquals(0, gameEntities.getAllWorkshops().size());
        assertNull(gameEntities.getWorkshop(1));
        
        // Add workshop
        gameEntities.addWorkshop(workshop);
        
        // Should be able to get workshop
        assertEquals(1, gameEntities.getAllWorkshops().size());
        assertNotNull(gameEntities.getWorkshop(1));
        assertEquals(workshop, gameEntities.getWorkshop(1));
    }

    @Test
    @DisplayName("Remove workshop")
    void testRemoveWorkshop() {
        Workshop workshop = new Workshop(
                1,
                new Vector2(100, 100),
                80.0,
                5.0,
                3
        );
        
        gameEntities.addWorkshop(workshop);
        assertEquals(1, gameEntities.getAllWorkshops().size());
        
        // Remove workshop
        gameEntities.removeWorkshop(1);
        
        // Should be removed
        assertEquals(0, gameEntities.getAllWorkshops().size());
        assertNull(gameEntities.getWorkshop(1));
    }

    @Test
    @DisplayName("Add and get power-up")
    void testAddAndGetPowerUp() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                1, // workshop ID
                30.0,
                1.0
        );
        
        // Initially no power-ups
        assertEquals(0, gameEntities.getAllPowerUps().size());
        assertNull(gameEntities.getPowerUp(1));
        
        // Add power-up
        gameEntities.addPowerUp(powerUp);
        
        // Should be able to get power-up
        assertEquals(1, gameEntities.getAllPowerUps().size());
        assertNotNull(gameEntities.getPowerUp(1));
        assertEquals(powerUp, gameEntities.getPowerUp(1));
    }

    @Test
    @DisplayName("Remove power-up")
    void testRemovePowerUp() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                1,
                30.0,
                1.0
        );
        
        gameEntities.addPowerUp(powerUp);
        assertEquals(1, gameEntities.getAllPowerUps().size());
        
        // Remove power-up
        gameEntities.removePowerUp(1);
        
        // Should be removed
        assertEquals(0, gameEntities.getAllPowerUps().size());
        assertNull(gameEntities.getPowerUp(1));
    }

    @Test
    @DisplayName("Get power-ups for specific workshop")
    void testGetPowerUpsForWorkshop() {
        Workshop workshop1 = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        Workshop workshop2 = new Workshop(2, new Vector2(200, 200), 80.0, 5.0, 3);
        
        PowerUp powerUp1 = new PowerUp(1, new Vector2(110, 110), PowerUp.PowerUpType.SPEED_BOOST, 1, 30.0, 1.0);
        PowerUp powerUp2 = new PowerUp(2, new Vector2(120, 120), PowerUp.PowerUpType.DAMAGE_BOOST, 1, 30.0, 1.0);
        PowerUp powerUp3 = new PowerUp(3, new Vector2(210, 210), PowerUp.PowerUpType.HEALTH_REGENERATION, 2, 30.0, 1.0);
        
        gameEntities.addWorkshop(workshop1);
        gameEntities.addWorkshop(workshop2);
        gameEntities.addPowerUp(powerUp1);
        gameEntities.addPowerUp(powerUp2);
        gameEntities.addPowerUp(powerUp3);
        
        // Get power-ups for workshop 1
        Collection<PowerUp> workshop1PowerUps = gameEntities.getPowerUpsForWorkshop(1);
        assertEquals(2, workshop1PowerUps.size());
        assertTrue(workshop1PowerUps.contains(powerUp1));
        assertTrue(workshop1PowerUps.contains(powerUp2));
        assertFalse(workshop1PowerUps.contains(powerUp3));
        
        // Get power-ups for workshop 2
        Collection<PowerUp> workshop2PowerUps = gameEntities.getPowerUpsForWorkshop(2);
        assertEquals(1, workshop2PowerUps.size());
        assertTrue(workshop2PowerUps.contains(powerUp3));
        assertFalse(workshop2PowerUps.contains(powerUp1));
        assertFalse(workshop2PowerUps.contains(powerUp2));
        
        // Get power-ups for non-existent workshop
        Collection<PowerUp> nonExistentWorkshopPowerUps = gameEntities.getPowerUpsForWorkshop(999);
        assertEquals(0, nonExistentWorkshopPowerUps.size());
    }

    @Test
    @DisplayName("Update all workshops and power-ups")
    void testUpdateAllWorkshopsAndPowerUps() {
        Workshop workshop = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        PowerUp powerUp = new PowerUp(1, new Vector2(110, 110), PowerUp.PowerUpType.SPEED_BOOST, 1, 30.0, 1.0);
        
        gameEntities.addWorkshop(workshop);
        gameEntities.addPowerUp(powerUp);
        
        // Start crafting on workshop
        workshop.startCrafting(1);
        
        double initialProgress = workshop.getCraftingProgress(1);
        
        // Update all entities
        gameEntities.updateAll(1.0); // 1 second
        
        // Workshop progress should have increased
        assertTrue(workshop.getCraftingProgress(1) > initialProgress);
        
        // Power-up should still be active (no expiration in current implementation)
        assertTrue(powerUp.isActive());
    }

    @Test
    @DisplayName("Multiple workshops management")
    void testMultipleWorkshopsManagement() {
        Workshop workshop1 = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        Workshop workshop2 = new Workshop(2, new Vector2(200, 200), 80.0, 5.0, 3);
        Workshop workshop3 = new Workshop(3, new Vector2(300, 300), 80.0, 5.0, 3);
        
        gameEntities.addWorkshop(workshop1);
        gameEntities.addWorkshop(workshop2);
        gameEntities.addWorkshop(workshop3);
        
        assertEquals(3, gameEntities.getAllWorkshops().size());
        
        // Remove middle workshop
        gameEntities.removeWorkshop(2);
        
        assertEquals(2, gameEntities.getAllWorkshops().size());
        assertNotNull(gameEntities.getWorkshop(1));
        assertNull(gameEntities.getWorkshop(2));
        assertNotNull(gameEntities.getWorkshop(3));
    }

    @Test
    @DisplayName("Multiple power-ups management")
    void testMultiplePowerUpsManagement() {
        PowerUp powerUp1 = new PowerUp(1, new Vector2(100, 100), PowerUp.PowerUpType.SPEED_BOOST, 1, 30.0, 1.0);
        PowerUp powerUp2 = new PowerUp(2, new Vector2(200, 200), PowerUp.PowerUpType.DAMAGE_BOOST, 1, 30.0, 1.0);
        PowerUp powerUp3 = new PowerUp(3, new Vector2(300, 300), PowerUp.PowerUpType.HEALTH_REGENERATION, 2, 30.0, 1.0);
        
        gameEntities.addPowerUp(powerUp1);
        gameEntities.addPowerUp(powerUp2);
        gameEntities.addPowerUp(powerUp3);
        
        assertEquals(3, gameEntities.getAllPowerUps().size());
        
        // Remove middle power-up
        gameEntities.removePowerUp(2);
        
        assertEquals(2, gameEntities.getAllPowerUps().size());
        assertNotNull(gameEntities.getPowerUp(1));
        assertNull(gameEntities.getPowerUp(2));
        assertNotNull(gameEntities.getPowerUp(3));
    }

    @Test
    @DisplayName("Workshop and power-up cleanup")
    void testWorkshopAndPowerUpCleanup() {
        Workshop workshop = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        PowerUp powerUp = new PowerUp(1, new Vector2(110, 110), PowerUp.PowerUpType.SPEED_BOOST, 1, 30.0, 1.0);
        
        gameEntities.addWorkshop(workshop);
        gameEntities.addPowerUp(powerUp);
        
        assertEquals(1, gameEntities.getAllWorkshops().size());
        assertEquals(1, gameEntities.getAllPowerUps().size());
        
        // Deactivate entities
        workshop.setActive(false);
        powerUp.setActive(false);
        
        // Entities should still exist but be inactive
        assertEquals(1, gameEntities.getAllWorkshops().size());
        assertEquals(1, gameEntities.getAllPowerUps().size());
        assertFalse(workshop.isActive());
        assertFalse(powerUp.isActive());
    }

    @Test
    @DisplayName("Workshop capacity tracking")
    void testWorkshopCapacityTracking() {
        Workshop workshop = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        gameEntities.addWorkshop(workshop);
        
        // Add power-ups up to capacity
        for (int i = 1; i <= 3; i++) {
            PowerUp powerUp = new PowerUp(
                    i,
                    new Vector2(100 + i * 10, 100 + i * 10),
                    PowerUp.PowerUpType.SPEED_BOOST,
                    1,
                    30.0,
                    1.0
            );
            gameEntities.addPowerUp(powerUp);
        }
        
        // Should be at capacity
        assertEquals(3, gameEntities.getPowerUpsForWorkshop(1).size());
        
        // Try to add one more (should be allowed by GameEntities, but workshop logic should prevent spawning)
        PowerUp extraPowerUp = new PowerUp(4, new Vector2(140, 140), PowerUp.PowerUpType.DAMAGE_BOOST, 1, 30.0, 1.0);
        gameEntities.addPowerUp(extraPowerUp);
        
        // GameEntities allows it, but workshop capacity logic should handle the limit
        assertEquals(4, gameEntities.getAllPowerUps().size());
        assertEquals(4, gameEntities.getPowerUpsForWorkshop(1).size());
    }

    @Test
    @DisplayName("Workshop and power-up with same ID")
    void testWorkshopAndPowerUpSameId() {
        Workshop workshop = new Workshop(1, new Vector2(100, 100), 80.0, 5.0, 3);
        PowerUp powerUp = new PowerUp(1, new Vector2(110, 110), PowerUp.PowerUpType.SPEED_BOOST, 1, 30.0, 1.0);
        
        gameEntities.addWorkshop(workshop);
        gameEntities.addPowerUp(powerUp);
        
        // Both should exist with same ID (different collections)
        assertNotNull(gameEntities.getWorkshop(1));
        assertNotNull(gameEntities.getPowerUp(1));
        assertEquals(workshop, gameEntities.getWorkshop(1));
        assertEquals(powerUp, gameEntities.getPowerUp(1));
        
        // Remove workshop, power-up should still exist
        gameEntities.removeWorkshop(1);
        assertNull(gameEntities.getWorkshop(1));
        assertNotNull(gameEntities.getPowerUp(1));
        
        // Remove power-up
        gameEntities.removePowerUp(1);
        assertNull(gameEntities.getWorkshop(1));
        assertNull(gameEntities.getPowerUp(1));
    }
}

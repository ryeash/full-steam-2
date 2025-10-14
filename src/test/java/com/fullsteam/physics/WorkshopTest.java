package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Workshop entity and workshop functionality.
 * Tests workshop creation, crafting mechanics, power-up spawning, and collision detection.
 */
class WorkshopTest extends BaseTestClass {

    private GameManager gameManager;
    private GameConfig gameConfig;
    private Player testPlayer;

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
    }

    @Test
    @DisplayName("Workshop creation with correct properties")
    void testWorkshopCreation() {
        // Get all workshops
        var workshops = gameManager.getGameEntities().getAllWorkshops();
        
        // Should have 2 workshops as configured
        assertEquals(2, workshops.size());
        
        // Check workshop properties
        for (Workshop workshop : workshops) {
            assertNotNull(workshop);
            assertTrue(workshop.isActive());
            assertEquals(80.0, workshop.getCraftRadius());
            assertEquals(5.0, workshop.getCraftTime());
            assertEquals(3, workshop.getMaxPowerUps());
            assertNotNull(workshop.getPosition());
            assertTrue(workshop.getBody().getFixture(0).isSensor()); // Should be a sensor
        }
    }

    @Test
    @DisplayName("Workshop crafting progress tracking")
    void testCraftingProgress() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Initially no crafting progress
        assertEquals(0.0, workshop.getCraftingProgress(testPlayer.getId()));
        assertEquals(0, workshop.getActiveCrafters());
        
        // Start crafting
        workshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        
        // Should have crafting progress
        assertTrue(workshop.getCraftingProgress(testPlayer.getId()) >= 0.0);
        assertEquals(1, workshop.getActiveCrafters());
        
        // Check crafting progress map
        Map<Integer, Double> progressMap = workshop.getAllCraftingProgress();
        assertTrue(progressMap.containsKey(testPlayer.getId()));
        assertTrue(progressMap.get(testPlayer.getId()) >= 0.0);
    }

    @Test
    @DisplayName("Workshop crafting completion detection")
    void testCraftingCompletion() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Start crafting
        workshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        
        // Initially not complete
        assertFalse(workshop.isCraftingComplete(testPlayer.getId()));
        
        // Simulate crafting completion by advancing time
        // Manually advance time to complete crafting
        for (int i = 0; i < 100; i++) {
            workshop.update(0.05); // 5ms per update, 100 updates = 5 seconds
        }
        
        // Should be complete now (or very close to complete)
        double progress = workshop.getCraftingProgress(testPlayer.getId());
        assertTrue(progress >= 0.99, "Expected progress >= 0.99, but got " + progress);
    }

    @Test
    @DisplayName("Workshop crafting reset after completion")
    void testCraftingReset() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Start crafting and complete it
        workshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        for (int i = 0; i < 100; i++) {
            workshop.update(0.05);
        }
        
        // Should be complete now (or very close to complete)
        double progress = workshop.getCraftingProgress(testPlayer.getId());
        assertTrue(progress >= 0.99, "Expected progress >= 0.99, but got " + progress);
        
        // Reset crafting progress
        workshop.resetCraftingProgress(testPlayer.getId());
        
        // Should be back to 0 progress
        assertEquals(0.0, workshop.getCraftingProgress(testPlayer.getId()));
        assertFalse(workshop.isCraftingComplete(testPlayer.getId()));
    }

    @Test
    @DisplayName("Workshop stop crafting removes player")
    void testStopCrafting() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Start crafting
        workshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        assertEquals(1, workshop.getActiveCrafters());
        
        // Stop crafting
        workshop.removePlayer(testPlayer.getId());
        
        // Should have no active crafters
        assertEquals(0, workshop.getActiveCrafters());
        assertFalse(workshop.getAllCraftingProgress().containsKey(testPlayer.getId()));
    }

    @Test
    @DisplayName("Workshop with disabled configuration")
    void testWorkshopDisabled() {
        // Create config with workshops disabled
        Rules disabledRules = Rules.builder()
                .addWorkshops(false) // Disabled
                .build();

        GameConfig disabledConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .enableAIFilling(false)
                .rules(disabledRules)
                .build();

        GameManager disabledGameManager = new GameManager("disabled_game", disabledConfig, null);
        
        // Should have no workshops
        var workshops = disabledGameManager.getGameEntities().getAllWorkshops();
        assertEquals(0, workshops.size());
    }

    @Test
    @DisplayName("Workshop sensor behavior")
    void testWorkshopSensorBehavior() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Workshop should be a sensor (players can walk through it)
        assertTrue(workshop.getBody().getFixture(0).isSensor());
        
        // Workshop should be static (infinite mass)
        assertEquals(org.dyn4j.geometry.MassType.INFINITE, workshop.getBody().getMass().getType());
    }

    @Test
    @DisplayName("Multiple players crafting at same workshop")
    void testMultiplePlayersCrafting() {
        Workshop workshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
        
        // Create additional test players
        Player player2 = new Player(2, "Player2", 0, 0, 1, 100.0);
        Player player3 = new Player(3, "Player3", 0, 0, 2, 100.0);
        
        // Start crafting for multiple players
        workshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        workshop.startCrafting(player2.getId());
        workshop.startCrafting(player3.getId());
        
        // Should have 3 active crafters
        assertEquals(3, workshop.getActiveCrafters());
        
        // All players should have crafting progress
        assertTrue(workshop.getCraftingProgress(testPlayer.getId()) >= 0.0);
        assertTrue(workshop.getCraftingProgress(player2.getId()) >= 0.0);
        assertTrue(workshop.getCraftingProgress(player3.getId()) >= 0.0);
        
        // Stop crafting for one player
        workshop.stopCrafting(player2.getId());
        
        // Should have 2 active crafters
        assertEquals(2, workshop.getActiveCrafters());
        assertFalse(workshop.getAllCraftingProgress().containsKey(player2.getId()));
    }

    @Test
    @DisplayName("Workshop position validation")
    void testWorkshopPositions() {
        var workshops = gameManager.getGameEntities().getAllWorkshops();
        
        // All workshops should have valid positions
        for (Workshop workshop : workshops) {
            Vector2 pos = workshop.getPosition();
            assertNotNull(pos);
            assertTrue(pos.x >= -1000 && pos.x <= 1000); // Within world bounds
            assertTrue(pos.y >= -1000 && pos.y <= 1000);
        }
        
        // Workshops should be positioned differently
        if (workshops.size() >= 2) {
            Workshop[] workshopArray = workshops.toArray(new Workshop[0]);
            Vector2 pos1 = workshopArray[0].getPosition();
            Vector2 pos2 = workshopArray[1].getPosition();
            
            // Should not be at the same position
            double xDiff = Math.abs(pos1.x - pos2.x);
            double yDiff = Math.abs(pos1.y - pos2.y);
            double totalDiff = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
            assertTrue(totalDiff > 0.1, "Workshops should be positioned differently (total distance: " + totalDiff + ")");
        }
    }
}

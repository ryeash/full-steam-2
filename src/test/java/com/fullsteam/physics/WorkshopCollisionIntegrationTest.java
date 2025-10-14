package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Workshop and PowerUp collision detection.
 * Tests the CollisionProcessor integration with workshops and power-ups.
 */
class WorkshopCollisionIntegrationTest extends BaseTestClass {

    private GameManager gameManager;
    private GameConfig gameConfig;
    private Player testPlayer;
    private Workshop testWorkshop;
    private CollisionProcessor collisionProcessor;

    @BeforeEach
    void setUp() {
        // Create test configuration with workshops enabled
        Rules rules = Rules.builder()
                .addWorkshops(true)
                .workshopCraftTime(2.0) // Short craft time for testing
                .workshopCraftRadius(100.0)
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
        
        // Create collision processor
        collisionProcessor = new CollisionProcessor(gameManager, gameManager.getGameEntities());
        
        // Create a test player
        testPlayer = new Player(1, "TestPlayer", 0, 0, 1, 100.0);
        gameManager.getGameEntities().addPlayer(testPlayer);
        
        // Get the test workshop
        testWorkshop = gameManager.getGameEntities().getAllWorkshops().iterator().next();
    }

    @Test
    @DisplayName("Player-workshop collision detection")
    void testPlayerWorkshopCollision() {
        // Position player within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50, // Within 100 unit radius
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Initially no crafting progress
        assertEquals(0.0, testWorkshop.getCraftingProgress(testPlayer.getId()));
        
        // Simulate collision detection
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should start crafting
        assertTrue(testWorkshop.getCraftingProgress(testPlayer.getId()) >= 0.0);
        assertEquals(1, testWorkshop.getActiveCrafters());
    }

    @Test
    @DisplayName("Player-workshop collision outside radius")
    void testPlayerWorkshopCollisionOutsideRadius() {
        // Position player outside workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 150, // Outside 100 unit radius
                workshopPos.y + 150
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Start crafting first
        testWorkshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        assertEquals(1, testWorkshop.getActiveCrafters());
        
        // Simulate collision detection
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should stop crafting
        assertEquals(0, testWorkshop.getActiveCrafters());
        assertFalse(testWorkshop.getAllCraftingProgress().containsKey(testPlayer.getId()));
    }

    @Test
    @DisplayName("Workshop power-up spawning through collision")
    void testWorkshopPowerUpSpawning() {
        // Position player within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50,
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Start crafting and complete it
        testWorkshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        
        // Simulate crafting completion
        for (int i = 0; i < 50; i++) {
            testWorkshop.update(0.04); // 2 seconds total
        }
        
        assertTrue(testWorkshop.isCraftingComplete(testPlayer.getId()));
        
        // Simulate collision detection to trigger power-up spawning
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should have spawned a power-up
        Collection<PowerUp> powerUps = gameManager.getGameEntities().getPowerUpsForWorkshop(testWorkshop.getId());
        assertEquals(1, powerUps.size());
        
        PowerUp spawnedPowerUp = powerUps.iterator().next();
        assertNotNull(spawnedPowerUp);
        assertTrue(spawnedPowerUp.isActive());
        assertEquals(testWorkshop.getId(), spawnedPowerUp.getWorkshopId());
    }

    @Test
    @DisplayName("Power-up collection through collision")
    void testPowerUpCollection() {
        // Create a power-up at player position
        Vector2 playerPos = new Vector2(100, 100);
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        PowerUp powerUp = new PowerUp(
                1,
                playerPos,
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        
        gameManager.getGameEntities().addPowerUp(powerUp);
        gameManager.getGameEntities().getWorld().addBody(powerUp.getBody());
        
        // Initially power-up should exist
        assertEquals(1, gameManager.getGameEntities().getAllPowerUps().size());
        
        // Simulate collision detection
        collisionProcessor.handlePlayerPowerUpCollision(testPlayer, powerUp);
        
        // Power-up should be collected and removed
        assertEquals(0, gameManager.getGameEntities().getAllPowerUps().size());
        assertFalse(powerUp.isActive());
    }

    @Test
    @DisplayName("Workshop capacity limits")
    void testWorkshopCapacityLimits() {
        // Position player within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50,
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Spawn multiple power-ups to reach capacity
        for (int i = 0; i < 3; i++) {
            PowerUp powerUp = new PowerUp(
                    i + 1,
                    new Vector2(workshopPos.x + i * 20, workshopPos.y + i * 20),
                    PowerUp.PowerUpType.SPEED_BOOST,
                    testWorkshop.getId(),
                    30.0,
                    1.0
            );
            gameManager.getGameEntities().addPowerUp(powerUp);
            gameManager.getGameEntities().getWorld().addBody(powerUp.getBody());
        }
        
        // Should be at capacity
        assertEquals(3, gameManager.getGameEntities().getPowerUpsForWorkshop(testWorkshop.getId()).size());
        
        // Complete crafting
        testWorkshop.addPlayer(testPlayer.getId(), testPlayer.getTeam());
        for (int i = 0; i < 50; i++) {
            testWorkshop.update(0.04);
        }
        
        // Try to spawn another power-up
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should still be at capacity (no new power-up spawned)
        assertEquals(3, gameManager.getGameEntities().getPowerUpsForWorkshop(testWorkshop.getId()).size());
    }

    @Test
    @DisplayName("Multiple players at same workshop")
    void testMultiplePlayersAtWorkshop() {
        // Create additional players
        Player player2 = new Player(2, "Player2", 0, 0, 1, 100.0);
        Player player3 = new Player(3, "Player3", 0, 0, 2, 100.0);
        
        gameManager.getGameEntities().addPlayer(player2);
        gameManager.getGameEntities().addPlayer(player3);
        
        // Position all players within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        testPlayer.getBody().getTransform().setTranslation(workshopPos.x + 50, workshopPos.y + 50);
        player2.getBody().getTransform().setTranslation(workshopPos.x + 60, workshopPos.y + 60);
        player3.getBody().getTransform().setTranslation(workshopPos.x + 70, workshopPos.y + 70);
        
        // Simulate collision detection for all players
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        collisionProcessor.handlePlayerWorkshopCollision(player2, testWorkshop);
        collisionProcessor.handlePlayerWorkshopCollision(player3, testWorkshop);
        
        // All players should be crafting
        assertEquals(3, testWorkshop.getActiveCrafters());
        assertTrue(testWorkshop.getCraftingProgress(testPlayer.getId()) >= 0.0);
        assertTrue(testWorkshop.getCraftingProgress(player2.getId()) >= 0.0);
        assertTrue(testWorkshop.getCraftingProgress(player3.getId()) >= 0.0);
    }

    @Test
    @DisplayName("Inactive player collision handling")
    void testInactivePlayerCollision() {
        // Position player within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50,
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Make player inactive
        testPlayer.setActive(false);
        
        // Simulate collision detection
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should not start crafting
        assertEquals(0, testWorkshop.getActiveCrafters());
        assertEquals(0.0, testWorkshop.getCraftingProgress(testPlayer.getId()));
    }

    @Test
    @DisplayName("Dead player collision handling")
    void testDeadPlayerCollision() {
        // Position player within workshop craft radius
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50,
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Make player dead
        testPlayer.setHealth(0);
        
        // Simulate collision detection
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        
        // Should not start crafting
        assertEquals(0, testWorkshop.getActiveCrafters());
        assertEquals(0.0, testWorkshop.getCraftingProgress(testPlayer.getId()));
    }

    @Test
    @DisplayName("Inactive power-up collection handling")
    void testInactivePowerUpCollection() {
        // Create an inactive power-up
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUp.PowerUpType.SPEED_BOOST,
                testWorkshop.getId(),
                30.0,
                1.0
        );
        powerUp.setActive(false);
        
        testPlayer.getBody().getTransform().setTranslation(100, 100);
        
        // Simulate collision detection
        collisionProcessor.handlePlayerPowerUpCollision(testPlayer, powerUp);
        
        // Should not be collected
        assertFalse(powerUp.isActive());
    }

    @Test
    @DisplayName("Workshop collision with different player states")
    void testWorkshopCollisionPlayerStates() {
        Vector2 workshopPos = testWorkshop.getPosition();
        Vector2 playerPos = new Vector2(
                workshopPos.x + 50,
                workshopPos.y + 50
        );
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
        
        // Test with active, healthy player
        testPlayer.setActive(true);
        testPlayer.setHealth(100);
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        assertEquals(1, testWorkshop.getActiveCrafters());
        
        // Test with inactive player
        testPlayer.setActive(false);
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        assertEquals(0, testWorkshop.getActiveCrafters());
        
        // Test with dead player
        testPlayer.setActive(true);
        testPlayer.setHealth(0);
        collisionProcessor.handlePlayerWorkshopCollision(testPlayer, testWorkshop);
        assertEquals(0, testWorkshop.getActiveCrafters());
    }
}

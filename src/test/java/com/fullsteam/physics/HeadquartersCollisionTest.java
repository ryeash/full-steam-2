package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Headquarters game interactions.
 * Tests HQ damage handling and scoring through the GameManager API.
 */
class HeadquartersCollisionTest extends BaseTestClass {

    private GameManager gameManager;
    private GameEntities gameEntities;
    private Player team1Player;
    private Player team2Player;
    private Headquarters team1HQ;
    private Headquarters team2HQ;

    @BeforeEach
    void setUp() {
        // Create test configuration with headquarters enabled
        Rules rules = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1)
                .headquartersDestructionBonus(100)
                .headquartersDestructionEndsGame(false) // Don't end game for testing
                .build();

        GameConfig gameConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .enableAIFilling(false)
                .rules(rules)
                .build();

        gameManager = new GameManager("test_game", gameConfig, null);
        gameEntities = gameManager.getGameEntities();
        
        // Create test players
        team1Player = new Player(1, "Team1Player", 0, 0, 1, 100.0);
        team2Player = new Player(2, "Team2Player", 100, 100, 2, 100.0);
        gameEntities.addPlayer(team1Player);
        gameEntities.addPlayer(team2Player);
        
        // Get headquarters
        team1HQ = gameEntities.getTeamHeadquarters(1);
        team2HQ = gameEntities.getTeamHeadquarters(2);
        
        assertNotNull(team1HQ, "Team 1 HQ should exist");
        assertNotNull(team2HQ, "Team 2 HQ should exist");
    }

    @Test
    @DisplayName("GameManager handles headquarters damage correctly")
    void testHeadquartersDamageHandling() {
        double initialHealth = team1HQ.getHealth();
        
        // Deal damage (takeDamage must be called first)
        team1HQ.takeDamage(100.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 100.0, false);
        
        // HQ should have taken damage
        assertTrue(team1HQ.getHealth() < initialHealth);
        assertEquals(900.0, team1HQ.getHealth());
        assertEquals(100.0, team1HQ.getTotalDamageTaken());
    }

    @Test
    @DisplayName("GameManager handles headquarters destruction")
    void testHeadquartersDestruction() {
        // Destroy HQ
        boolean destroyed = team1HQ.takeDamage(1000.0);
        assertTrue(destroyed);
        
        // Handle through GameManager
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 1000.0, true);
        
        // Verify HQ is destroyed
        assertFalse(team1HQ.isActive());
        assertEquals(0.0, team1HQ.getHealth());
    }

    @Test
    @DisplayName("Multiple damage instances tracked correctly")
    void testMultipleDamageInstances() {
        // Deal damage multiple times
        team1HQ.takeDamage(100.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 100.0, false);
        team1HQ.takeDamage(150.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 150.0, false);
        team1HQ.takeDamage(200.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 200.0, false);
        
        // Total: 450 damage
        assertEquals(550.0, team1HQ.getHealth());
        assertEquals(450.0, team1HQ.getTotalDamageTaken());
    }

    @Test
    @DisplayName("Destruction creates explosion effect")
    void testDestructionExplosion() {
        int initialEffectCount = gameEntities.getAllFieldEffects().size();
        
        // Destroy HQ
        boolean destroyed = team1HQ.takeDamage(1000.0);
        assertTrue(destroyed);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 1000.0, true);
        
        // Should have created an explosion field effect
        int finalEffectCount = gameEntities.getAllFieldEffects().size();
        assertEquals(initialEffectCount + 1, finalEffectCount);
    }

    @Test
    @DisplayName("Both teams can attack each other's HQs")
    void testSimultaneousHQAttacks() {
        // Team 1 attacks Team 2's HQ
        team2HQ.takeDamage(200.0);
        gameManager.handleHeadquartersDamage(team2HQ, team1Player, 200.0, false);
        
        // Team 2 attacks Team 1's HQ
        team1HQ.takeDamage(300.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 300.0, false);
        
        // Both HQs should have taken their respective damage
        assertEquals(800.0, team2HQ.getHealth());
        assertEquals(700.0, team1HQ.getHealth());
    }
}

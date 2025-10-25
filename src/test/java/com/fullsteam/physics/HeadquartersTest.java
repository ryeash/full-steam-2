package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Headquarters entity.
 * Tests HQ creation, damage tracking, team ownership, and destruction mechanics.
 */
class HeadquartersTest extends BaseTestClass {

    private GameManager gameManager;
    private GameConfig gameConfig;
    private Player team1Player;
    private Player team2Player;

    @BeforeEach
    void setUp() {
        // Create test configuration with headquarters enabled
        Rules rules = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1) // 1 point per 10 damage
                .headquartersDestructionBonus(100)
                .headquartersDestructionEndsGame(true)
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
        
        // Create test players on different teams
        team1Player = new Player(1, "Team1Player", 0, 0, 1, 100.0);
        team2Player = new Player(2, "Team2Player", 100, 100, 2, 100.0);
        gameManager.getGameEntities().addPlayer(team1Player);
        gameManager.getGameEntities().addPlayer(team2Player);
    }

    @Test
    @DisplayName("Headquarters creation with correct properties")
    void testHeadquartersCreation() {
        // Get all headquarters
        var headquarters = gameManager.getGameEntities().getAllHeadquarters();
        
        // Should have 2 headquarters (one per team)
        assertEquals(2, headquarters.size());
        
        // Check HQ properties
        for (Headquarters hq : headquarters) {
            assertNotNull(hq);
            assertTrue(hq.isActive());
            assertEquals(1000.0, hq.getMaxHealth());
            assertEquals(1000.0, hq.getHealth());
            assertTrue(hq.getTeamNumber() >= 1 && hq.getTeamNumber() <= 2);
            assertNotNull(hq.getPosition());
            assertEquals(0.0, hq.getTotalDamageTaken());
        }
    }

    @Test
    @DisplayName("Each team has exactly one headquarters")
    void testTeamHeadquartersAssignment() {
        // Get HQ for each team
        Headquarters team1HQ = gameManager.getGameEntities().getTeamHeadquarters(1);
        Headquarters team2HQ = gameManager.getGameEntities().getTeamHeadquarters(2);
        
        assertNotNull(team1HQ);
        assertNotNull(team2HQ);
        assertEquals(1, team1HQ.getTeamNumber());
        assertEquals(2, team2HQ.getTeamNumber());
        assertNotEquals(team1HQ.getId(), team2HQ.getId());
    }

    @Test
    @DisplayName("Headquarters takes damage correctly")
    void testHeadquartersDamage() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        double initialHealth = hq.getHealth();
        assertEquals(1000.0, initialHealth);
        assertEquals(0.0, hq.getTotalDamageTaken());
        
        // Apply damage
        boolean destroyed = hq.takeDamage(250.0);
        
        assertFalse(destroyed); // Should not be destroyed yet
        assertEquals(750.0, hq.getHealth());
        assertEquals(250.0, hq.getTotalDamageTaken());
        assertTrue(hq.isActive());
    }

    @Test
    @DisplayName("Headquarters destruction when health reaches zero")
    void testHeadquartersDestruction() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        // Apply enough damage to destroy
        boolean destroyed = hq.takeDamage(1000.0);
        
        assertTrue(destroyed);
        assertEquals(0.0, hq.getHealth());
        assertEquals(1000.0, hq.getTotalDamageTaken());
        assertFalse(hq.isActive()); // Should be inactive after destruction
    }

    @Test
    @DisplayName("Headquarters tracks total damage for scoring")
    void testDamageTracking() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        // Apply multiple hits
        hq.takeDamage(100.0);
        assertEquals(100.0, hq.getTotalDamageTaken());
        
        hq.takeDamage(150.0);
        assertEquals(250.0, hq.getTotalDamageTaken());
        
        hq.takeDamage(200.0);
        assertEquals(450.0, hq.getTotalDamageTaken());
        assertEquals(550.0, hq.getHealth());
    }

    @Test
    @DisplayName("Headquarters cannot take damage when inactive")
    void testInactiveHeadquartersIgnoreDamage() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        // Destroy the HQ
        hq.takeDamage(1000.0);
        assertFalse(hq.isActive());
        
        // Try to apply more damage
        double totalDamage = hq.getTotalDamageTaken();
        boolean destroyed = hq.takeDamage(100.0);
        
        assertFalse(destroyed); // Already destroyed
        assertEquals(0.0, hq.getHealth()); // Health stays at 0
        assertEquals(totalDamage, hq.getTotalDamageTaken()); // Damage tracking doesn't change
    }

    @Test
    @DisplayName("Headquarters has proper shape data for rendering")
    void testHeadquartersShapeData() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        var shapeData = hq.getShapeData();
        
        assertNotNull(shapeData);
        assertEquals("RECTANGLE", shapeData.get("shapeCategory"));
        assertEquals(80.0, shapeData.get("width"));
        assertEquals(60.0, shapeData.get("height"));
    }

    @Test
    @DisplayName("Headquarters positioned in team spawn zones")
    void testHeadquartersPositioning() {
        // Get both HQs
        Headquarters team1HQ = gameManager.getGameEntities().getTeamHeadquarters(1);
        Headquarters team2HQ = gameManager.getGameEntities().getTeamHeadquarters(2);
        
        assertNotNull(team1HQ);
        assertNotNull(team2HQ);
        
        Vector2 pos1 = team1HQ.getPosition();
        Vector2 pos2 = team2HQ.getPosition();
        
        // HQs should be positioned away from center (defensive position)
        // They should be far apart (in different spawn zones)
        double distance = pos1.distance(pos2);
        assertTrue(distance > 300, "HQs should be far apart in different spawn zones");
        
        // Both should be within world bounds
        assertTrue(Math.abs(pos1.x) < 1000);
        assertTrue(Math.abs(pos1.y) < 1000);
        assertTrue(Math.abs(pos2.x) < 1000);
        assertTrue(Math.abs(pos2.y) < 1000);
    }

    @Test
    @DisplayName("Headquarters update does not throw exceptions")
    void testHeadquartersUpdate() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        // Update should not throw
        assertDoesNotThrow(() -> hq.update(0.016)); // ~60 FPS
        assertDoesNotThrow(() -> hq.update(1.0));   // 1 second
        
        // Update should work even when inactive
        hq.takeDamage(1000.0);
        assertFalse(hq.isActive());
        assertDoesNotThrow(() -> hq.update(0.016));
    }

    @Test
    @DisplayName("Headquarters home position is stored correctly")
    void testHomePosition() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        
        Vector2 homePos = hq.getHomePosition();
        Vector2 currentPos = hq.getPosition();
        
        assertNotNull(homePos);
        assertEquals(homePos.x, currentPos.x, 0.01);
        assertEquals(homePos.y, currentPos.y, 0.01);
    }

    @Test
    @DisplayName("No headquarters created when feature is disabled")
    void testDisabledHeadquarters() {
        // Create new config with HQ disabled
        Rules rulesNoHQ = Rules.builder()
                .addHeadquarters(false)
                .build();

        GameConfig configNoHQ = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .enableAIFilling(false)
                .rules(rulesNoHQ)
                .build();

        GameManager gmNoHQ = new GameManager("test_no_hq", configNoHQ, null);
        
        // Should have no headquarters
        assertTrue(gmNoHQ.getGameEntities().getAllHeadquarters().isEmpty());
    }

    @Test
    @DisplayName("No headquarters created in FFA mode")
    void testNoHeadquartersInFFA() {
        // Create FFA config (teamCount = 0)
        Rules rulesFFA = Rules.builder()
                .addHeadquarters(true) // Enabled but should be ignored
                .build();

        GameConfig configFFA = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(0) // FFA mode
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .enableAIFilling(false)
                .rules(rulesFFA)
                .build();

        GameManager gmFFA = new GameManager("test_ffa", configFFA, null);
        
        // Should have no headquarters in FFA
        assertTrue(gmFFA.getGameEntities().getAllHeadquarters().isEmpty());
    }
}


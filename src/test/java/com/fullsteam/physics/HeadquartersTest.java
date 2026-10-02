package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Headquarters entity.
 * Tests HQ creation, damage tracking, team ownership, and destruction mechanics.
 */
class HeadquartersTest extends BaseTestClass {

    private GameManager gameManager;

    @BeforeEach
    void setUp() {
        // Create test configuration with headquarters enabled
        Rules rules = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1) // 1 point per 10 damage
                .headquartersDestructionBonus(100)
                .build();

        GameConfig gameConfig = GameConfig.builder()
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
        Player team1Player = new Player(1, "Team1Player", 0, 0, 1, 100.0);
        Player team2Player = new Player(2, "Team2Player", 100, 100, 2, 100.0);
        gameManager.getGameEntities().add(team1Player);
        gameManager.getGameEntities().add(team2Player);
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
            assertTrue(hq.getOwnerTeam() >= 1 && hq.getOwnerTeam() <= 2);
            assertNotNull(hq.getPosition());
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
        assertEquals(1, team1HQ.getOwnerTeam());
        assertEquals(2, team2HQ.getOwnerTeam());
        assertNotEquals(team1HQ.getId(), team2HQ.getId());
    }

    @Test
    @DisplayName("Headquarters takes damage correctly")
    void testHeadquartersDamage() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();

        double initialHealth = hq.getHealth();
        assertEquals(1000.0, initialHealth);

        // Apply damage
        boolean destroyed = hq.takeDamage(250.0);

        assertFalse(destroyed); // Should not be destroyed yet
        assertEquals(750.0, hq.getHealth());
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
        assertFalse(hq.isActive()); // Should be inactive after destruction
    }

    @Test
    @DisplayName("Headquarters tracks total damage for scoring")
    void testDamageTracking() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();

        // Apply multiple hits
        hq.takeDamage(100.0);

        hq.takeDamage(150.0);

        hq.takeDamage(200.0);
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
        boolean destroyed = hq.takeDamage(100.0);

        assertFalse(destroyed); // Already destroyed
        assertEquals(0.0, hq.getHealth()); // Health stays at 0
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

    @Test
    @DisplayName("Headquarters physics body contains rectangle wall fixture and 4 corner turret circle fixtures")
    void testHeadquartersPhysicsFixtures() {
        Headquarters hq = gameManager.getGameEntities().getAllHeadquarters().iterator().next();
        org.dyn4j.dynamics.Body body = hq.getBody();

        assertEquals(5, body.getFixtureCount(), "Headquarters should have 1 wall polygon + 4 corner turret circle fixtures");

        int polygonCount = 0;
        int circleCount = 0;
        for (int i = 0; i < body.getFixtureCount(); i++) {
            var shape = body.getFixture(i).getShape();
            if (shape instanceof org.dyn4j.geometry.Polygon) {
                polygonCount++;
            } else if (shape instanceof org.dyn4j.geometry.Circle circle) {
                circleCount++;
                assertEquals(15.0, circle.getRadius(), 0.001, "Turret circle radius should be 15.0");
                assertTrue(Math.abs(Math.abs(circle.getCenter().x) - 40.0) < 0.001);
                assertTrue(Math.abs(Math.abs(circle.getCenter().y) - 30.0) < 0.001);
            }
        }
        assertEquals(1, polygonCount, "Should have 1 wall rectangle fixture");
        assertEquals(4, circleCount, "Should have 4 corner turret circle fixtures");
    }

    @Test
    @DisplayName("Headquarters initial game state serialization includes team, ownerTeam, and shapes with circle turrets")
    @SuppressWarnings("unchecked")
    void testHeadquartersInitialGameStateSerialization() {
        Player player = gameManager.getGameEntities().getPlayer(1);
        var initialState = gameManager.getGameStateSerializer().createInitialGameState(player);

        assertTrue(initialState.containsKey("headquarters"));
        var hqList = (java.util.List<java.util.Map<String, Object>>) initialState.get("headquarters");
        assertEquals(2, hqList.size());

        for (var hqMap : hqList) {
            assertNotNull(hqMap.get("id"));
            assertNotNull(hqMap.get("team"));
            assertNotNull(hqMap.get("ownerTeam"));
            assertEquals(hqMap.get("team"), hqMap.get("ownerTeam"));

            String shapes = (String) hqMap.get("shapes");
            assertNotNull(shapes);
            assertFalse(shapes.isEmpty());

            // Shapes string should contain 5 fixtures joined by ';' (1 polygon + 4 circles)
            String[] fixtures = shapes.split(";");
            assertEquals(5, fixtures.length, "Shapes should serialize all 5 fixtures (1 wall + 4 turrets)");

            int circleFixtures = 0;
            for (String fixture : fixtures) {
                if (fixture.matches("\\([-0-9.]+,[-0-9.]+,15\\)")) {
                    circleFixtures++;
                }
            }
            assertEquals(4, circleFixtures, "Should serialize 4 corner turret circles with radius 15");
        }
    }
}


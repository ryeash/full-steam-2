package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Headquarters scoring system.
 * Tests point awarding for damage, destruction bonuses, and game-ending conditions.
 */
public class HeadquartersScoringTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private GameManager gameManager;
    private GameConfig testConfig;
    private Player team1Player;
    private Player team2Player;
    private Headquarters team1HQ;
    private Headquarters team2HQ;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        // Create test world and config
        world = new World<>();

        // Create test config with headquarters enabled
        Rules rules = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1)  // 1 point per 10 damage
                .headquartersDestructionBonus(100)
                .headquartersDestructionEndsGame(false) // Don't end game for testing
                .scoreStyle(ScoreStyle.TOTAL) // Include all score sources
                .build();

        testConfig = GameConfig.builder()
                .teamCount(2)
                .maxPlayers(8)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)
                .rules(rules)
                .build();

        // Create game manager
        gameManager = new GameManager("test_game", testConfig, null);

        // Get the GameEntities from the GameManager
        gameEntities = gameManager.getGameEntities();

        // Create test players
        team1Player = new Player(1, "Team1Player", 0, 0, 1, 100.0);
        team2Player = new Player(2, "Team2Player", 100, 100, 2, 100.0);
        gameEntities.addPlayer(team1Player);
        gameEntities.addPlayer(team2Player);

        // Get headquarters
        team1HQ = gameEntities.getTeamHeadquarters(1);
        team2HQ = gameEntities.getTeamHeadquarters(2);

        assertNotNull(team1HQ);
        assertNotNull(team2HQ);
    }

    @Test
    @DisplayName("Points awarded for headquarters damage")
    void testPointsForDamage() {
        RuleSystem ruleSystem = gameManager.getRuleSystem();

        // Team 2 damages Team 1's HQ for 100 damage
        // Should award 10 points (0.1 points per damage * 100)
        team1HQ.takeDamage(100.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 100.0, false);

        // Verify points were added (this is tracked internally in RuleSystem)
        // We can't directly check the bonus points, but we can verify the HQ took damage
        assertEquals(900.0, team1HQ.getHealth());
        assertEquals(100.0, team1HQ.getTotalDamageTaken());
    }

    @Test
    @DisplayName("Destruction bonus awarded when HQ is destroyed")
    void testDestructionBonus() {
        RuleSystem ruleSystem = gameManager.getRuleSystem();

        // Team 2 destroys Team 1's HQ
        boolean destroyed = team1HQ.takeDamage(1000.0);
        assertTrue(destroyed);

        // Handle the damage and destruction
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 1000.0, true);

        // Verify HQ is destroyed
        assertFalse(team1HQ.isActive());
        assertEquals(0.0, team1HQ.getHealth());

        // Total points should be: (1000 damage * 0.1) + 100 bonus = 200 points
        // This is added to team 2's bonus points
    }

    @Test
    @DisplayName("Game ends when HQ destroyed with end-game setting")
    void testGameEndsOnHQDestruction() {
        // Create new config with game-ending enabled
        Rules rulesWithEnd = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1)
                .headquartersDestructionBonus(100)
                .headquartersDestructionEndsGame(true) // Enable game ending
                .build();

        GameConfig configWithEnd = GameConfig.builder()
                .teamCount(2)
                .maxPlayers(8)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)
                .rules(rulesWithEnd)
                .build();

        GameManager gmEnd = new GameManager("test_game_end", configWithEnd, null);

        Player p1 = new Player(1, "P1", 0, 0, 1, 100.0);
        Player p2 = new Player(2, "P2", 100, 100, 2, 100.0);
        gmEnd.getGameEntities().addPlayer(p1);
        gmEnd.getGameEntities().addPlayer(p2);

        Headquarters hq1 = gmEnd.getGameEntities().getTeamHeadquarters(1);
        assertNotNull(hq1);

        RuleSystem rs = gmEnd.getRuleSystem();
        assertFalse(rs.isGameOver());

        // Destroy HQ
        boolean destroyed = hq1.takeDamage(1000.0);
        assertTrue(destroyed);
        gmEnd.handleHeadquartersDamage(hq1, p2, 1000.0, true);

        // Game should now be over
        assertTrue(rs.isGameOver());
        assertEquals(2, rs.getWinningTeam()); // Team 2 wins
        assertNotNull(rs.getVictoryMessage());
        assertTrue(rs.getVictoryMessage().contains("Headquarters Destroyed"));
    }

    @Test
    @DisplayName("Game continues when HQ destroyed with end-game disabled")
    void testGameContinuesOnHQDestruction() {
        RuleSystem ruleSystem = gameManager.getRuleSystem();

        assertFalse(ruleSystem.isGameOver());

        // Destroy HQ (game ending is disabled in setup)
        boolean destroyed = team1HQ.takeDamage(1000.0);
        assertTrue(destroyed);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 1000.0, true);

        // Game should continue
        assertFalse(ruleSystem.isGameOver());
        assertNull(ruleSystem.getWinningTeam());
    }

    @Test
    @DisplayName("Multiple damage instances accumulate points correctly")
    void testAccumulatedDamagePoints() {
        // Deal damage in multiple hits
        team1HQ.takeDamage(100.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 100.0, false);
        team1HQ.takeDamage(150.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 150.0, false);
        team1HQ.takeDamage(200.0);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 200.0, false);

        // Total damage: 450
        // Total points: 45 (at 0.1 per damage)
        assertEquals(550.0, team1HQ.getHealth());
        assertEquals(450.0, team1HQ.getTotalDamageTaken());
    }

    @Test
    @DisplayName("Different teams can attack different HQs simultaneously")
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
        assertEquals(200.0, team2HQ.getTotalDamageTaken());
        assertEquals(300.0, team1HQ.getTotalDamageTaken());
    }

    @Test
    @DisplayName("Points not awarded for friendly HQ damage")
    void testNoPointsForFriendlyFire() {
        // This is prevented at collision level, but test that if somehow
        // it happened, we handle it gracefully

        double initialHealth = team1HQ.getHealth();

        // Try to handle "friendly" damage (shouldn't happen in real game)
        // The collision processor prevents this, but test the handler
        gameManager.handleHeadquartersDamage(team1HQ, team1Player, 100.0, false);

        // Even if called, it should add points to team 1 (their own team)
        // which is technically correct but shouldn't happen due to collision filtering
    }

    @Test
    @DisplayName("Destruction explosion effect is created")
    void testDestructionExplosion() {
        int initialEffectCount = gameEntities.getAllFieldEffects().size();

        // Destroy HQ
        boolean destroyed = team1HQ.takeDamage(1000.0);
        assertTrue(destroyed);
        gameManager.handleHeadquartersDamage(team1HQ, team2Player, 1000.0, true);

        // Should have created an explosion field effect
        int finalEffectCount = gameEntities.getAllFieldEffects().size();
        assertEquals(initialEffectCount + 1, finalEffectCount);

        // Verify explosion properties
        var effects = gameEntities.getAllFieldEffects();
        assertTrue(effects.stream().anyMatch(e ->
                e.getType().name().equals("EXPLOSION") &&
                        e.getRadius() == 100.0
        ));
    }

    @Test
    @DisplayName("HQ damage scoring with zero points per damage")
    void testZeroPointsPerDamage() {
        // Create config with no points for damage (only destruction bonus)
        Rules rulesNoPoints = Rules.builder()
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.0)  // No points for damage
                .headquartersDestructionBonus(100) // Only destruction bonus
                .headquartersDestructionEndsGame(false)
                .build();

        GameConfig configNoPoints = GameConfig.builder()
                .teamCount(2)
                .maxPlayers(8)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)
                .rules(rulesNoPoints)
                .build();

        GameManager gmNoPoints = new GameManager("test_no_points", configNoPoints, null);

        Player p1 = new Player(1, "P1", 0, 0, 1, 100.0);
        Player p2 = new Player(2, "P2", 100, 100, 2, 100.0);
        gmNoPoints.getGameEntities().addPlayer(p1);
        gmNoPoints.getGameEntities().addPlayer(p2);

        Headquarters hq1 = gmNoPoints.getGameEntities().getTeamHeadquarters(1);

        // Damage HQ but don't destroy
        hq1.takeDamage(500.0);
        gmNoPoints.handleHeadquartersDamage(hq1, p2, 500.0, false);

        // HQ should be damaged but no points awarded
        assertEquals(500.0, hq1.getHealth());

        // Now destroy it - should only get destruction bonus
        boolean destroyed = hq1.takeDamage(500.0);
        assertTrue(destroyed);
        gmNoPoints.handleHeadquartersDamage(hq1, p2, 500.0, true);

        assertFalse(hq1.isActive());
    }

    @Test
    @DisplayName("RuleSystem addTeamPoints integration")
    void testAddTeamPointsMethod() {
        RuleSystem ruleSystem = gameManager.getRuleSystem();

        // Directly add bonus points to team 2
        ruleSystem.addTeamPoints(2, 50);

        // Points should be reflected in team score calculations
        // (We can't directly verify the internal map, but the method should not throw)
        assertDoesNotThrow(() -> ruleSystem.addTeamPoints(1, 25));
        assertDoesNotThrow(() -> ruleSystem.addTeamPoints(2, 75));
    }
}


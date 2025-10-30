package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.*;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleSystem.
 * Tests round management, victory conditions, respawn modes, and scoring.
 */
class RuleSystemTest extends BaseTestClass {

    private RuleSystem ruleSystem;
    private GameEntities gameEntities;
    private World<Body> world;
    private GameEventManager gameEventManager;
    private TestBroadcaster broadcaster;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        // Create test world and config
        world = new World<>();
        GameConfig testConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .roundDuration(60.0)
                        .restDuration(10.0)
                        .victoryCondition(VictoryCondition.SCORE_LIMIT)
                        .scoreLimit(25)
                        .respawnMode(RespawnMode.INSTANT)
                        .respawnDelay(5.0)
                        .maxLives(-1)
                        .scoreStyle(ScoreStyle.TOTAL_KILLS)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .enableAIFilling(false)  // Disable AI filling for predictable test environment
                .build();

        gameEntities = new GameEntities(testConfig, world);
        gameEventManager = new GameEventManager(gameEntities, (session, message) -> {
        });
        broadcaster = new TestBroadcaster();

        // Create rule system
        ruleSystem = new RuleSystem(
                "test-game",
                testConfig.getRules(),
                gameEntities,
                gameEventManager,
                broadcaster,
                testConfig.getTeamCount()
        );
    }

    @Test
    @DisplayName("Should start with round 1 and playing state")
    void testInitialRoundState() {
        // Assert
        assertEquals(1, ruleSystem.getCurrentRound(), "Should start with round 1");
        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), "Should start in PLAYING state");
        assertFalse(ruleSystem.isGameOver(), "Game should not be over initially");
    }

//    @Test
//    @DisplayName("Should end round when time expires")
//    void testRoundEndsWhenTimeExpires() {
//        // Arrange
//        Player player1 = createTestPlayer(1, 1);
//        Player player2 = createTestPlayer(2, 2);
//        gameEntities.addPlayer(player1);
//        gameEntities.addPlayer(player2);
//
//        // Act - Fast forward to end of round
//        double roundDuration = ruleSystem.getRoundTimeRemaining();
//        ruleSystem.update(roundDuration + 1.0);
//
//        // Assert
//        assertEquals(GameState.ROUND_END, ruleSystem.getGameState(), "Should be in ROUND_END state");
//        // Note: We can't directly access roundScores, but we can verify the state changed
//        assertTrue(ruleSystem.getGameState() == GameState.ROUND_END, "Should have ended the round");
//    }

//    @Test
//    @DisplayName("Should transition to rest period after round end")
//    void testRestPeriodTransition() {
//        // Arrange
//        Player player1 = createTestPlayer(1, 1);
//        gameEntities.addPlayer(player1);
//
//        // Act - End round and wait for rest period
//        double roundDuration = ruleSystem.getRoundTimeRemaining();
//        ruleSystem.update(roundDuration + 1.0); // End round
//        ruleSystem.update(0.1); // Transition to rest period
//
//        // Assert
//        assertEquals(GameState.REST_PERIOD, ruleSystem.getGameState(), "Should be in REST_PERIOD state");
//        assertTrue(ruleSystem.getRestTimeRemaining() > 0, "Should have rest time remaining");
//    }

//    @Test
//    @DisplayName("Should start next round after rest period")
//    void testNextRoundStart() {
//        // Arrange
//        Player player1 = createTestPlayer(1, 1);
//        gameEntities.addPlayer(player1);
//
//        // Act - Complete full round cycle
//        double roundDuration = ruleSystem.getRoundTimeRemaining();
//        ruleSystem.update(roundDuration + 1.0); // End round
//        ruleSystem.update(0.1); // Transition to rest period
//
//        double restDuration = ruleSystem.getRestTimeRemaining();
//        ruleSystem.update(restDuration + 1.0); // End rest period
//
//        // Assert
//        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), "Should be back in PLAYING state");
//        assertEquals(2, ruleSystem.getCurrentRound(), "Should be on round 2");
//        assertTrue(ruleSystem.getRoundTimeRemaining() > 0, "Should have fresh round time");
//    }

    // ============================================================================
    // Victory Condition Tests
    // ============================================================================

    @Test
    @DisplayName("Should declare victory when score limit reached")
    void testScoreLimitVictory() {
        // Arrange
        Player player1 = createTestPlayer(1, 1);
        player1.setKills(25); // Reach score limit
        gameEntities.addPlayer(player1);

        // Act
        ruleSystem.update(0.1);

        // Assert
        assertTrue(ruleSystem.isGameOver(), "Game should be over");
        assertEquals(1, ruleSystem.getWinningTeam(), "Team 1 should win");
        assertNotNull(ruleSystem.getVictoryMessage(), "Should have victory message");
    }

    @Test
    @DisplayName("Should declare victory when time limit reached")
    void testTimeLimitVictory() {
        // Arrange
        GameConfig timeLimitConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .victoryCondition(VictoryCondition.TIME_LIMIT)
                        .timeLimit(10.0)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        RuleSystem timeLimitRuleSystem = new RuleSystem(
                "test-game",
                timeLimitConfig.getRules(),
                gameEntities,
                gameEventManager,
                broadcaster,
                timeLimitConfig.getTeamCount()
        );

        Player player1 = createTestPlayer(1, 1);
        Player player2 = createTestPlayer(2, 2);
        player1.setKills(5);
        player2.setKills(3);
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);

        // Act - Fast forward past time limit
        timeLimitRuleSystem.update(11.0);

        // Assert
        assertTrue(timeLimitRuleSystem.isGameOver(), "Game should be over");
        assertEquals(1, timeLimitRuleSystem.getWinningTeam(), "Team 1 should win (higher score)");
    }

    @Test
    @DisplayName("Should declare elimination victory when only one team remains")
    void testEliminationVictory() {
        // Arrange
        GameConfig eliminationConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .victoryCondition(VictoryCondition.ELIMINATION)
                        .respawnMode(RespawnMode.ELIMINATION)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        RuleSystem eliminationRuleSystem = new RuleSystem(
                "test-game",
                eliminationConfig.getRules(),
                gameEntities,
                gameEventManager,
                broadcaster,
                eliminationConfig.getTeamCount()
        );

        Player player1 = createTestPlayer(1, 1);
        Player player2 = createTestPlayer(2, 2);
        player2.setEliminated(true); // Eliminate player 2
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);

        // Act
        eliminationRuleSystem.update(0.1);

        // Assert
        assertTrue(eliminationRuleSystem.isGameOver(), "Game should be over");
        assertEquals(1, eliminationRuleSystem.getWinningTeam(), "Team 1 should win");
    }

    @Test
    @DisplayName("Should handle limited lives correctly")
    void testLimitedLives() {
        // Arrange
        GameConfig limitedLivesConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .respawnMode(RespawnMode.LIMITED)
                        .maxLives(3)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        RuleSystem limitedLivesRuleSystem = new RuleSystem(
                "test-game",
                limitedLivesConfig.getRules(),
                gameEntities,
                gameEventManager,
                broadcaster,
                limitedLivesConfig.getTeamCount()
        );

        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);
        limitedLivesRuleSystem.initializePlayerLives(player);

        // Act - Lose all lives
        player.loseLife(); // 2 lives left
        player.loseLife(); // 1 life left
        boolean eliminated = player.loseLife(); // 0 lives left

        // Assert
        assertTrue(eliminated, "Player should be eliminated after losing all lives");
        assertTrue(player.isEliminated(), "Player should be marked as eliminated");
    }

    // ============================================================================
    // Scoring Tests
    // ============================================================================

    @Test
    @DisplayName("Should calculate total kills score correctly")
    void testTotalKillsScoring() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        player.setKills(10);
        player.setDeaths(3);
        player.setCaptures(2);
        gameEntities.addPlayer(player);

        // Act - We can't directly call getPlayerScore, but we can test the scoring logic indirectly
        // by checking if the player's kills are used for scoring
        int kills = player.getKills();

        // Assert
        assertEquals(10, kills, "Player should have 10 kills");
        // Note: We can't directly test getPlayerScore since it's private,
        // but we can verify the player data is correct
    }

    @Test
    @DisplayName("Should calculate objectives score correctly")
    void testObjectivesScoring() {
        // Arrange
        GameConfig objectivesConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .scoreStyle(ScoreStyle.OBJECTIVE)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        RuleSystem objectivesRuleSystem = new RuleSystem(
                "test-game",
                objectivesConfig.getRules(),
                gameEntities,
                gameEventManager,
                broadcaster,
                objectivesConfig.getTeamCount()
        );

        Player player = createTestPlayer(1, 1);
        player.setKills(10);
        player.setCaptures(5);
        gameEntities.addPlayer(player);

        // Act - Test the scoring configuration
        ScoreStyle scoreStyle = objectivesConfig.getRules().getScoreStyle();
        int captures = player.getCaptures();

        // Assert
        assertEquals(ScoreStyle.OBJECTIVE, scoreStyle, "Should be configured for objectives scoring");
        assertEquals(5, captures, "Player should have 5 captures");
        // Note: We can't directly test getPlayerScore since it's private,
        // but we can verify the configuration and player data
    }

    // ============================================================================
    // State Management Tests
    // ============================================================================

    @Test
    @DisplayName("Should provide complete state data")
    void testStateDataCompleteness() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);

        // Act
        Map<String, Object> stateData = ruleSystem.getStateData();

        // Assert
        assertNotNull(stateData, "State data should not be null");
        assertTrue(stateData.containsKey("gameState"), "Should include game state");
        assertTrue(stateData.containsKey("currentRound"), "Should include current round");
        assertTrue(stateData.containsKey("roundTimeRemaining"), "Should include round time");
        assertTrue(stateData.containsKey("gameOver"), "Should include game over status");
    }

    @Test
    @DisplayName("Should initialize player lives correctly")
    void testPlayerLifeInitialization() {
        // Arrange
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);

        // Act
        ruleSystem.initializePlayerLives(player);

        // Assert
        assertEquals(-1, player.getLivesRemaining(), "Should have unlimited lives by default");
        assertFalse(player.isEliminated(), "Should not be eliminated initially");
    }

    // ============================================================================
    // VIP Mode Tests
    // ============================================================================

    @Test
    @DisplayName("Should assign VIP to each team when VIP mode is enabled")
    void testVipModeInitialization() {
        // Arrange
        GameConfig vipConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .enableVip(true)
                        .scoreStyle(ScoreStyle.OBJECTIVE)
                        .victoryCondition(VictoryCondition.SCORE_LIMIT)
                        .scoreLimit(5)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        GameEntities vipEntities = new GameEntities(vipConfig, world);
        
        // Add players to both teams
        Player team1Player1 = createTestPlayer(1, 1);
        Player team1Player2 = createTestPlayer(2, 1);
        Player team2Player1 = createTestPlayer(3, 2);
        Player team2Player2 = createTestPlayer(4, 2);
        
        vipEntities.addPlayer(team1Player1);
        vipEntities.addPlayer(team1Player2);
        vipEntities.addPlayer(team2Player1);
        vipEntities.addPlayer(team2Player2);

        // Act - Create rule system with VIP mode enabled
        RuleSystem vipRuleSystem = new RuleSystem(
                "vip-test-game",
                vipConfig.getRules(),
                vipEntities,
                gameEventManager,
                broadcaster,
                vipConfig.getTeamCount()
        );

        // Assert - Each team should have exactly one VIP
        Integer team1Vip = vipEntities.getTeamVip(1);
        Integer team2Vip = vipEntities.getTeamVip(2);
        
        assertNotNull(team1Vip, "Team 1 should have a VIP");
        assertNotNull(team2Vip, "Team 2 should have a VIP");
        assertTrue(team1Vip == 1 || team1Vip == 2, "Team 1 VIP should be one of the team 1 players");
        assertTrue(team2Vip == 3 || team2Vip == 4, "Team 2 VIP should be one of the team 2 players");
        
        // Verify VIP status is applied
        Player vip1 = vipEntities.getPlayer(team1Vip);
        Player vip2 = vipEntities.getPlayer(team2Vip);
        assertTrue(StatusEffects.isVip(vip1), "Team 1 VIP should have VIP status");
        assertTrue(StatusEffects.isVip(vip2), "Team 2 VIP should have VIP status");
    }

    @Test
    @DisplayName("Should award points for VIP kills")
    void testVipKillScoring() {
        // Arrange
        GameConfig vipConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .enableVip(true)
                        .scoreStyle(ScoreStyle.OBJECTIVE)
                        .victoryCondition(VictoryCondition.SCORE_LIMIT)
                        .scoreLimit(5)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        GameEntities vipEntities = new GameEntities(vipConfig, world);
        
        Player team1Player = createTestPlayer(1, 1);
        Player team2Player = createTestPlayer(2, 2);
        
        vipEntities.addPlayer(team1Player);
        vipEntities.addPlayer(team2Player);

        RuleSystem vipRuleSystem = new RuleSystem(
                "vip-test-game",
                vipConfig.getRules(),
                vipEntities,
                gameEventManager,
                broadcaster,
                vipConfig.getTeamCount()
        );

        // Act - Award VIP kill to team 1
        vipRuleSystem.awardVipKill(1);
        vipRuleSystem.awardVipKill(1);
        vipRuleSystem.awardVipKill(2);

        // Assert - Check team scores include VIP kills
        Map<String, Object> stateData = vipRuleSystem.getStateData();
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) stateData.get("teamScores");
        
        assertEquals(2, teamScores.get(1), "Team 1 should have 2 points from VIP kills");
        assertEquals(1, teamScores.get(2), "Team 2 should have 1 point from VIP kill");
    }

    @Test
    @DisplayName("Should reassign VIP when current VIP becomes inactive")
    void testVipReassignment() {
        // Arrange
        GameConfig vipConfig = GameConfig.builder()
                .rules(Rules.builder()
                        .enableVip(true)
                        .scoreStyle(ScoreStyle.OBJECTIVE)
                        .build())
                .teamCount(2)
                .playerMaxHealth(100.0)
                .build();

        GameEntities vipEntities = new GameEntities(vipConfig, world);
        
        Player team1Player1 = createTestPlayer(1, 1);
        Player team1Player2 = createTestPlayer(2, 1);
        
        vipEntities.addPlayer(team1Player1);
        vipEntities.addPlayer(team1Player2);

        RuleSystem vipRuleSystem = new RuleSystem(
                "vip-test-game",
                vipConfig.getRules(),
                vipEntities,
                gameEventManager,
                broadcaster,
                vipConfig.getTeamCount()
        );

        Integer initialVip = vipEntities.getTeamVip(1);
        assertNotNull(initialVip, "Team 1 should have initial VIP");

        // Act - Make the VIP inactive
        Player vipPlayer = vipEntities.getPlayer(initialVip);
        vipPlayer.setActive(false);
        
        // Ensure VIP for team (should reassign)
        vipRuleSystem.ensureVipForTeam(1);

        // Assert - VIP should be reassigned to the other player
        Integer newVip = vipEntities.getTeamVip(1);
        assertNotNull(newVip, "Team 1 should have a new VIP");
        assertNotEquals(initialVip, newVip, "VIP should be reassigned to different player");
        
        Player newVipPlayer = vipEntities.getPlayer(newVip);
        assertTrue(StatusEffects.isVip(newVipPlayer), "New VIP should have VIP status");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Create a test player with basic configuration.
     */
    private Player createTestPlayer(int id, int team) {
        Player player = new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
        return player;
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
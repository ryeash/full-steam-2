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
 * Unit tests for the stock/default RuleSystem configuration.
 * Tests the default rules that are used when no custom configuration is provided.
 * 
 * Stock Rules (from Rules.java @Builder.Default):
 * - roundDuration = 120.0 (2 minutes)
 * - restDuration = 10.0 (10 seconds)
 * - flagsPerTeam = 0 (no flags)
 * - scoreStyle = ScoreStyle.TOTAL_KILLS
 * - victoryCondition = VictoryCondition.ENDLESS
 * - scoreLimit = 50
 * - timeLimit = 600.0 (10 minutes)
 * - suddenDeath = false
 * - respawnMode = RespawnMode.INSTANT
 * - respawnDelay = 5.0
 * - maxLives = -1 (unlimited)
 * - waveRespawnInterval = 30.0
 * - kothZones = 0 (no KOTH zones)
 * - kothPointsPerSecond = 1.0
 */
class StockRuleSystemTest extends BaseTestClass {

    private RuleSystem ruleSystem;
    private GameEntities gameEntities;
    private World<Body> world;
    private GameEventManager gameEventManager;
    private TestBroadcaster broadcaster;
    private Rules stockRules;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        // Create test world
        world = new World<>();
        
        // Create stock rules using the default builder (no custom configuration)
        stockRules = Rules.builder().build();
        
        // Create game config with stock rules
        GameConfig stockConfig = GameConfig.builder()
                .rules(stockRules)
                .teamCount(2)
                .playerMaxHealth(100.0)
                .enableAIFilling(false)  // Disable AI filling for predictable test environment
                .build();
        
        gameEntities = new GameEntities(stockConfig, world);
        gameEventManager = new GameEventManager(gameEntities, (session, message) -> {});
        broadcaster = new TestBroadcaster();
        
        // Create rule system with stock configuration
        ruleSystem = new RuleSystem(
                "stock-test-game",
                stockRules,
                gameEntities,
                gameEventManager,
                broadcaster,
                stockConfig.getTeamCount()
        );
    }

    // ============================================================================
    // Stock Configuration Validation Tests
    // ============================================================================

    @Test
    @DisplayName("Should use stock round duration (120 seconds)")
    void testStockRoundDuration() {
        assertEquals(120.0, stockRules.getRoundDuration(), 
                "Stock round duration should be 120 seconds");
    }

    @Test
    @DisplayName("Should use stock rest duration (10 seconds)")
    void testStockRestDuration() {
        assertEquals(10.0, stockRules.getRestDuration(), 
                "Stock rest duration should be 10 seconds");
    }

    @Test
    @DisplayName("Should use stock victory condition (ENDLESS)")
    void testStockVictoryCondition() {
        assertEquals(VictoryCondition.ENDLESS, stockRules.getVictoryCondition(), 
                "Stock victory condition should be ENDLESS");
    }

    @Test
    @DisplayName("Should use stock score style (TOTAL_KILLS)")
    void testStockScoreStyle() {
        assertEquals(ScoreStyle.TOTAL_KILLS, stockRules.getScoreStyle(), 
                "Stock score style should be TOTAL_KILLS");
    }

    @Test
    @DisplayName("Should use stock respawn mode (INSTANT)")
    void testStockRespawnMode() {
        assertEquals(RespawnMode.INSTANT, stockRules.getRespawnMode(), 
                "Stock respawn mode should be INSTANT");
    }

    @Test
    @DisplayName("Should use stock respawn delay (5 seconds)")
    void testStockRespawnDelay() {
        assertEquals(5.0, stockRules.getRespawnDelay(), 
                "Stock respawn delay should be 5 seconds");
    }

    @Test
    @DisplayName("Should use stock max lives (-1 = unlimited)")
    void testStockMaxLives() {
        assertEquals(-1, stockRules.getMaxLives(), 
                "Stock max lives should be -1 (unlimited)");
    }

    @Test
    @DisplayName("Should use stock flags per team (0 = no flags)")
    void testStockFlagsPerTeam() {
        assertEquals(0, stockRules.getFlagsPerTeam(), 
                "Stock flags per team should be 0 (no flags)");
        assertFalse(stockRules.hasFlags(), "Stock rules should not have flags");
    }

    @Test
    @DisplayName("Should use stock KOTH zones (0 = disabled)")
    void testStockKothZones() {
        assertEquals(0, stockRules.getKothZones(), 
                "Stock KOTH zones should be 0 (disabled)");
        assertFalse(stockRules.hasKothZones(), "Stock rules should not have KOTH zones");
    }

    @Test
    @DisplayName("Should use stock sudden death (false)")
    void testStockSuddenDeath() {
        assertFalse(stockRules.isSuddenDeath(), 
                "Stock sudden death should be false");
    }

    @Test
    @DisplayName("Should use stock score limit (50)")
    void testStockScoreLimit() {
        assertEquals(50, stockRules.getScoreLimit(), 
                "Stock score limit should be 50");
    }

    @Test
    @DisplayName("Should use stock time limit (600 seconds)")
    void testStockTimeLimit() {
        assertEquals(600.0, stockRules.getTimeLimit(), 
                "Stock time limit should be 600 seconds (10 minutes)");
    }

    @Test
    @DisplayName("Should use stock wave respawn interval (30 seconds)")
    void testStockWaveRespawnInterval() {
        assertEquals(30.0, stockRules.getWaveRespawnInterval(), 
                "Stock wave respawn interval should be 30 seconds");
    }

    @Test
    @DisplayName("Should use stock KOTH points per second (1.0)")
    void testStockKothPointsPerSecond() {
        assertEquals(1.0, stockRules.getKothPointsPerSecond(), 
                "Stock KOTH points per second should be 1.0");
    }

    // ============================================================================
    // Stock Rule System Behavior Tests
    // ============================================================================

    @Test
    @DisplayName("Should start with round 1 and playing state using stock rules")
    void testStockInitialState() {
        assertEquals(1, ruleSystem.getCurrentRound(), "Should start with round 1");
        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), "Should start in PLAYING state");
        assertFalse(ruleSystem.isGameOver(), "Game should not be over initially");
    }

    @Test
    @DisplayName("Should countdown round time correctly with stock 120-second rounds")
    void testStockRoundCountdown() {
        // Initial state
        assertEquals(120.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should start with 120 seconds remaining");
        
        // After 10 seconds
        ruleSystem.update(10.0);
        assertEquals(110.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should have 110 seconds remaining after 10 seconds");
        
        // After 60 seconds
        ruleSystem.update(50.0);
        assertEquals(60.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should have 60 seconds remaining after 60 seconds total");
    }

    @Test
    @DisplayName("Should end round after stock 120 seconds and enter rest period")
    void testStockRoundEndAndRest() {
        // Add a player to make the round meaningful
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);
        
        // Fast forward past round duration
        ruleSystem.update(120.0);
        
        // Should be in ROUND_END state initially
        assertEquals(GameState.ROUND_END, ruleSystem.getGameState(), 
                "Should be in ROUND_END state after round ends");
        
        // Update once more to transition to REST_PERIOD
        ruleSystem.update(0.1);
        assertEquals(GameState.REST_PERIOD, ruleSystem.getGameState(), 
                "Should be in REST_PERIOD state after transition");
        assertEquals(10.0, ruleSystem.getRestTimeRemaining(), 0.1, 
                "Should have 10 seconds rest time remaining");
    }

    @Test
    @DisplayName("Should start next round after stock 10-second rest period")
    void testStockRestPeriodAndNextRound() {
        // Add a player
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);
        
        // End first round
        ruleSystem.update(120.0);
        assertEquals(GameState.ROUND_END, ruleSystem.getGameState());
        
        // Transition to rest period
        ruleSystem.update(0.1);
        assertEquals(GameState.REST_PERIOD, ruleSystem.getGameState());
        
        // Fast forward through rest period
        ruleSystem.update(10.0);
        
        // Should start round 2
        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), 
                "Should be in PLAYING state for round 2");
        assertEquals(2, ruleSystem.getCurrentRound(), 
                "Should be on round 2");
        assertEquals(120.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should have 120 seconds for round 2");
    }

    @Test
    @DisplayName("Should handle instant respawn with stock 5-second delay")
    void testStockInstantRespawn() {
        // Add a player
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);
        
        // Kill the player
        player.die();
        assertFalse(player.isActive(), "Player should be dead");
        
        // Handle death with stock instant respawn
        RuleSystem.RespawnAction action = ruleSystem.handlePlayerDeath(player);
        assertEquals(RuleSystem.RespawnAction.RESPAWN_AFTER_DELAY, action, 
                "Should use RESPAWN_AFTER_DELAY for instant mode");
        
        // Player should have respawn time set to stock delay
        assertEquals(5.0, player.getRespawnTime(), 0.1, 
                "Player should have 5-second respawn delay");
    }

    @Test
    @DisplayName("Should not have limited lives with stock unlimited lives")
    void testStockUnlimitedLives() {
        assertFalse(stockRules.hasLimitedLives(), 
                "Stock rules should not have limited lives");
        assertEquals(-1, stockRules.getMaxLives(), 
                "Stock max lives should be -1 (unlimited)");
    }

    @Test
    @DisplayName("Should allow respawn with stock instant respawn mode")
    void testStockAllowsRespawn() {
        assertTrue(stockRules.allowsRespawn(), 
                "Stock rules should allow respawn");
        assertEquals(RespawnMode.INSTANT, stockRules.getRespawnMode(), 
                "Stock respawn mode should be INSTANT");
    }

    @Test
    @DisplayName("Should not use wave respawn with stock instant mode")
    void testStockNoWaveRespawn() {
        assertFalse(stockRules.usesWaveRespawn(), 
                "Stock rules should not use wave respawn");
        assertEquals(RespawnMode.INSTANT, stockRules.getRespawnMode(), 
                "Stock respawn mode should be INSTANT, not WAVE");
    }

    @Test
    @DisplayName("Should not have time limit with stock ENDLESS victory condition")
    void testStockNoTimeLimit() {
        assertFalse(stockRules.hasTimeLimit(), 
                "Stock rules should not have time limit with ENDLESS victory");
        assertEquals(VictoryCondition.ENDLESS, stockRules.getVictoryCondition(), 
                "Stock victory condition should be ENDLESS");
    }

    @Test
    @DisplayName("Should not have score limit with stock ENDLESS victory condition")
    void testStockNoScoreLimit() {
        assertFalse(stockRules.hasScoreLimit(), 
                "Stock rules should not have score limit with ENDLESS victory");
        assertEquals(VictoryCondition.ENDLESS, stockRules.getVictoryCondition(), 
                "Stock victory condition should be ENDLESS");
    }

    @Test
    @DisplayName("Should provide complete state data with stock configuration")
    void testStockStateData() {
        // Add some players
        Player player1 = createTestPlayer(1, 1);
        Player player2 = createTestPlayer(2, 2);
        player1.setKills(5);
        player2.setKills(3);
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);
        
        // Update the system
        ruleSystem.update(30.0);
        
        // Get state data
        Map<String, Object> stateData = ruleSystem.getStateData();
        
        // Verify stock configuration is reflected in state
        assertNotNull(stateData, "State data should not be null");
        assertEquals(1, stateData.get("currentRound"), "Should be on round 1");
        assertEquals("PLAYING", stateData.get("gameState"), "Should be in PLAYING state");
        assertEquals(90.0, (Double) stateData.get("roundTimeRemaining"), 0.1, 
                "Should have 90 seconds remaining after 30 seconds");
        assertFalse((Boolean) stateData.get("gameOver"), "Game should not be over");
    }

    @Test
    @DisplayName("Should handle multiple rounds with stock configuration")
    void testStockMultipleRounds() {
        // Add a player
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);
        
        // Complete first round (120s) + transition + rest period (10s)
        ruleSystem.update(120.0);  // End round
        ruleSystem.update(0.1);   // Transition to REST_PERIOD
        ruleSystem.update(10.0);   // Complete rest period
        
        // Should be on round 2
        assertEquals(2, ruleSystem.getCurrentRound(), "Should be on round 2");
        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), "Should be playing");
        assertEquals(120.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should have 120 seconds for round 2");
        
        // Complete second round + transition + rest period
        ruleSystem.update(120.0);  // End round 2
        ruleSystem.update(0.1);   // Transition to REST_PERIOD
        ruleSystem.update(10.0);   // Complete rest period
        
        // Should be on round 3
        assertEquals(3, ruleSystem.getCurrentRound(), "Should be on round 3");
        assertEquals(GameState.PLAYING, ruleSystem.getGameState(), "Should be playing");
        assertEquals(120.0, ruleSystem.getRoundTimeRemaining(), 0.1, 
                "Should have 120 seconds for round 3");
    }

    @Test
    @DisplayName("Should maintain stock configuration consistency across multiple games")
    void testStockConfigurationConsistency() {
        // Create multiple rule systems with stock configuration
        Rules rules1 = Rules.builder().build();
        Rules rules2 = Rules.builder().build();
        Rules rules3 = Rules.builder().build();
        
        // All should have identical stock values
        assertEquals(rules1.getRoundDuration(), rules2.getRoundDuration());
        assertEquals(rules2.getRoundDuration(), rules3.getRoundDuration());
        assertEquals(120.0, rules1.getRoundDuration());
        
        assertEquals(rules1.getVictoryCondition(), rules2.getVictoryCondition());
        assertEquals(rules2.getVictoryCondition(), rules3.getVictoryCondition());
        assertEquals(VictoryCondition.ENDLESS, rules1.getVictoryCondition());
        
        assertEquals(rules1.getScoreStyle(), rules2.getScoreStyle());
        assertEquals(rules2.getScoreStyle(), rules3.getScoreStyle());
        assertEquals(ScoreStyle.TOTAL_KILLS, rules1.getScoreStyle());
        
        assertEquals(rules1.getRespawnMode(), rules2.getRespawnMode());
        assertEquals(rules2.getRespawnMode(), rules3.getRespawnMode());
        assertEquals(RespawnMode.INSTANT, rules1.getRespawnMode());
    }

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

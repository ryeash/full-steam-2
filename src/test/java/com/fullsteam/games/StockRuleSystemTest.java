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
 * <p>
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
        gameEventManager = new GameEventManager(gameEntities, (session, message) -> {
        });
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
    @DisplayName("Should handle instant respawn with stock 5-second delay")
    void testStockInstantRespawn() {
        // Add a player
        Player player = createTestPlayer(1, 1);
        gameEntities.addPlayer(player);

        // Kill the player
        player.die();
        assertFalse(player.isActive(), "Player should be dead");
        
        // Set respawn time to 1 second
        player.setRespawnTime(1L);
        
        // Advance time past the respawn time
        ruleSystem.update(2.0);

        // Note: Respawn time is set by GameManager.killPlayer(), not by RuleSystem.handlePlayerDeath()
        // This test verifies that the correct action is returned
        assertTrue(ruleSystem.shouldPlayerRespawn(player),
                "Player should be allowed to respawn with instant mode");
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
        return new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
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

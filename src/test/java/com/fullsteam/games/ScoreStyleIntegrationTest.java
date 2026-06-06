package com.fullsteam.games;

import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test integration between ScoreStyle and KOTH zone scoring.
 */
class ScoreStyleIntegrationTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private RuleSystem ruleSystem;
    private TestBroadcaster broadcaster;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
        gameEntities = new GameEntities(createTestConfig(), world);
        broadcaster = new TestBroadcaster();
        ruleSystem = new RuleSystem("test-game", createTestRules(), gameEntities, null, broadcaster, 2);
    }

    private Rules createTestRules() {
        return Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.OBJECTIVE)
                .kothZones(2)
                .kothPointsPerSecond(5.0)
                .build();
    }

    private GameConfig createTestConfig() {
        return GameConfig.builder()
                .teamCount(2)
                .maxPlayers(8)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)  // Disable AI filling for predictable test environment
                .build();
    }

    @Test
    @DisplayName("OBJECTIVE score style should count objectives (captures + KOTH zones), not kills")
    void testObjectiveScoreStyle() {
        // Create players with kills and captures
        Player player1 = new Player(1, "Player1", 100, 100, 1,100.0);
        Player player2 = new Player(2, "Player2", 200, 200, 2,100.0);
        player1.addKill(); // Player 1 has 1 kill
        player1.addCapture(); // Player 1 has 1 capture
        player2.addKill(); // Player 2 has 1 kill
        player2.addKill(); // Player 2 has 2 kills total
        player2.addCapture(); // Player 2 has 1 capture
        player2.addCapture(); // Player 2 has 2 captures
        
        gameEntities.add(player1);
        gameEntities.add(player2);

        // Create KOTH zones with scores
        KothZone zone1 = new KothZone(101, 0, 0, 0, 5.0);
        KothZone zone2 = new KothZone(102, 1, 100, 100, 5.0);
        
        // Team 1 controls zone 1 (5 points)
        zone1.awardPointsToTeam(1, 5.0);
        
        // Team 2 controls zone 2 (10 points)
        zone2.awardPointsToTeam(2, 10.0);
        
        gameEntities.add(zone1);
        gameEntities.add(zone2);

        // Calculate team scores
        Map<String, Object> stateData = ruleSystem.getStateData();
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) stateData.get("teamScores");
        
        // With OBJECTIVE score style:
        // - Team 1: 1 capture + 5 KOTH points = 6 points (kills ignored)
        // - Team 2: 2 captures + 10 KOTH points = 12 points (kills ignored)
        assertEquals(6, teamScores.get(1));
        assertEquals(12, teamScores.get(2));
    }

    @Test
    @DisplayName("TOTAL score style should include both player scores and KOTH scores")
    void testTotalScoreStyle() {
        // Change to TOTAL score style
        Rules totalRules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.TOTAL)
                .kothZones(2)
                .kothPointsPerSecond(5.0)
                .build();
        ruleSystem = new RuleSystem("test-game", totalRules, gameEntities, null, broadcaster, 2);

        // Create players with kills and captures
        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        Player player2 = new Player(2, "Player2", 200, 200, 2, 100.0);
        player1.addKill(); // Player 1 has 1 kill
        player1.addCapture(); // Player 1 has 1 capture
        player2.addKill(); // Player 2 has 1 kill
        player2.addKill(); // Player 2 has 2 kills total
        
        gameEntities.add(player1);
        gameEntities.add(player2);

        // Create KOTH zones with scores
        KothZone zone1 = new KothZone(101, 0, 0, 0, 5.0);
        KothZone zone2 = new KothZone(102, 1, 100, 100, 5.0);
        
        // Team 1 controls zone 1 (5 points)
        zone1.awardPointsToTeam(1, 5.0);
        
        // Team 2 controls zone 2 (10 points)
        zone2.awardPointsToTeam(2, 10.0);
        
        gameEntities.add(zone1);
        gameEntities.add(zone2);

        // Calculate team scores
        Map<String, Object> stateData = ruleSystem.getStateData();
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) stateData.get("teamScores");
        
        // With TOTAL score style:
        // - Team 1: 1 kill + 1 capture + 5 KOTH points = 7 points
        // - Team 2: 2 kills + 0 captures + 10 KOTH points = 12 points
        assertEquals(7, teamScores.get(1));
        assertEquals(12, teamScores.get(2));
    }

    @Test
    @DisplayName("TOTAL_KILLS score style should ignore objectives (KOTH and captures)")
    void testTotalKillsScoreStyle() {
        // Change to TOTAL_KILLS score style
        Rules killsRules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.TOTAL_KILLS)
                .kothZones(2)
                .kothPointsPerSecond(5.0)
                .build();
        ruleSystem = new RuleSystem("test-game", killsRules, gameEntities, null, broadcaster, 2);

        // Create players with kills and captures
        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        Player player2 = new Player(2, "Player2", 200, 200, 2, 100.0);
        player1.addKill(); // Player 1 has 1 kill
        player1.addCapture(); // Player 1 has 1 capture (should be ignored)
        player2.addKill(); // Player 2 has 1 kill
        player2.addKill(); // Player 2 has 2 kills total
        player2.addCapture(); // Player 2 has 1 capture (should be ignored)
        
        gameEntities.add(player1);
        gameEntities.add(player2);

        // Create KOTH zones with scores
        KothZone zone1 = new KothZone(101, 0, 0, 0, 5.0);
        KothZone zone2 = new KothZone(102, 1, 100, 100, 5.0);
        
        // Team 1 controls zone 1 (5 points, should be ignored)
        zone1.awardPointsToTeam(1, 5.0);
        
        // Team 2 controls zone 2 (10 points, should be ignored)
        zone2.awardPointsToTeam(2, 10.0);
        
        gameEntities.add(zone1);
        gameEntities.add(zone2);

        // Calculate team scores
        Map<String, Object> stateData = ruleSystem.getStateData();
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) stateData.get("teamScores");
        
        // With TOTAL_KILLS score style:
        // - Team 1: 1 kill (objectives and KOTH scores ignored)
        // - Team 2: 2 kills (objectives and KOTH scores ignored)
        assertEquals(1, teamScores.get(1));
        assertEquals(2, teamScores.get(2));
    }

    @Test
    @DisplayName("pointsPerFlagCapture multiplies captures in OBJECTIVE score style")
    void testPointsPerFlagCaptureWithObjectiveStyle() {
        Rules rules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.OBJECTIVE)
                .flagsPerTeam(1)
                .pointsPerFlagCapture(10)
                .kothZones(0)
                .build();
        ruleSystem = new RuleSystem("test-game", rules, gameEntities, null, broadcaster, 2);

        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        Player player2 = new Player(2, "Player2", 200, 200, 2, 100.0);
        player1.addCapture(); // 1 capture = 10 pts
        player2.addCapture();
        player2.addCapture(); // 2 captures = 20 pts
        gameEntities.add(player1);
        gameEntities.add(player2);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) ruleSystem.getStateData().get("teamScores");

        assertEquals(10, teamScores.get(1), "Team 1: 1 capture * 10 pts");
        assertEquals(20, teamScores.get(2), "Team 2: 2 captures * 10 pts");
    }

    @Test
    @DisplayName("pointsPerFlagCapture is additive with kills under TOTAL score style")
    void testPointsPerFlagCaptureWithTotalStyle() {
        Rules rules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.TOTAL)
                .flagsPerTeam(1)
                .pointsPerFlagCapture(5)
                .kothZones(0)
                .build();
        ruleSystem = new RuleSystem("test-game", rules, gameEntities, null, broadcaster, 2);

        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        Player player2 = new Player(2, "Player2", 200, 200, 2, 100.0);
        player1.addKill();
        player1.addKill();
        player1.addCapture(); // 2 kills (worth 1 each) + 1 capture * 5 = 7
        player2.addKill(); // 1 kill, no captures = 1
        gameEntities.add(player1);
        gameEntities.add(player2);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) ruleSystem.getStateData().get("teamScores");

        assertEquals(7, teamScores.get(1), "Team 1: 2 kills + 1 capture * 5 pts");
        assertEquals(1, teamScores.get(2), "Team 2: 1 kill only");
    }

    @Test
    @DisplayName("pointsPerFlagCapture has no effect under TOTAL_KILLS score style")
    void testPointsPerFlagCaptureIgnoredByTotalKills() {
        Rules rules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.TOTAL_KILLS)
                .flagsPerTeam(1)
                .pointsPerFlagCapture(50)
                .build();
        ruleSystem = new RuleSystem("test-game", rules, gameEntities, null, broadcaster, 2);

        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        player1.addKill();
        player1.addCapture(); // captures should be ignored entirely
        gameEntities.add(player1);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) ruleSystem.getStateData().get("teamScores");

        assertEquals(1, teamScores.get(1),
                "TOTAL_KILLS ignores captures, so the per-capture multiplier should not contribute");
    }

    @Test
    @DisplayName("Default pointsPerFlagCapture preserves legacy 1:1 capture-to-point behavior")
    void testDefaultPointsPerFlagCapturePreservesLegacyScoring() {
        // No explicit pointsPerFlagCapture - relies on the Builder.Default of 1
        Rules rules = Rules.builder()
                .roundDuration(60.0)
                .restDuration(10.0)
                .scoreStyle(ScoreStyle.OBJECTIVE)
                .flagsPerTeam(1)
                .build();
        assertEquals(1, rules.getPointsPerFlagCapture(),
                "Default pointsPerFlagCapture should be 1 for backwards compatibility");

        ruleSystem = new RuleSystem("test-game", rules, gameEntities, null, broadcaster, 2);

        Player player1 = new Player(1, "Player1", 100, 100, 1, 100.0);
        player1.addCapture();
        player1.addCapture();
        player1.addCapture();
        gameEntities.add(player1);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> teamScores = (Map<Integer, Integer>) ruleSystem.getStateData().get("teamScores");

        assertEquals(3, teamScores.get(1),
                "With default multiplier, 3 captures -> 3 points (the prior behavior)");
    }

    private static class TestBroadcaster implements Consumer<Map<String, Object>> {
        @Override
        public void accept(Map<String, Object> message) {
            // No-op for testing
        }
    }
}

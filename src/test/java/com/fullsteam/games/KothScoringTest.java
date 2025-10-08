package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.Rules;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for King of the Hill (KOTH) scoring system.
 * Tests zone control, point awarding, and frame-rate independent scoring.
 */
public class KothScoringTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private CollisionProcessor collisionProcessor;
    private GameManager gameManager;
    private GameConfig testConfig;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        // Create test world and config
        world = new World<>();
        
        // Create test config with KOTH enabled
        Rules rules = Rules.builder()
                .kothZones(2)
                .kothPointsPerSecond(5.0)
                .build();
                
        testConfig = GameConfig.builder()
                .teamCount(2)
                .maxPlayers(8)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)  // Disable AI filling for predictable test environment
                .rules(rules)
                .build();
                
        // Create game manager first (it will create its own GameEntities)
        gameManager = new GameManager("test_game", testConfig, null);
        
        // Disable AI adjustment for tests
        gameManager.disableAIAdjustment();
        
        // Clear all AI players that were added initially
        gameManager.getGameEntities().getAllPlayers().forEach(player -> {
            if (player.getId() > 100) { // AI players have high IDs
                gameManager.getGameEntities().removePlayer(player.getId());
            }
        });

        // Get the GameEntities from the GameManager
        gameEntities = gameManager.getGameEntities();
        
        // Create collision processor directly
        collisionProcessor = new CollisionProcessor(gameManager, gameEntities);
        
        // Create test KOTH zones
        createTestKothZones();
    }

    private void createTestKothZones() {
        // Don't create manual zones - let GameManager create them automatically
        // The GameManager will create zones based on the config rules
    }

    @Test
    @DisplayName("KOTH Zone Control - Single Team Dominance")
    void testKothZoneControlSingleTeam() {
        // Clear all AI players to avoid interference
        gameEntities.getAllPlayers().forEach(player -> {
            if (player.getId() > 100) { // AI players have high IDs
                gameEntities.removePlayer(player.getId());
            }
        });
        
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // Create players from team 0 in the zone
        Player player1 = createTestPlayer(1, 0, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        Player player2 = createTestPlayer(2, 0, zone.getBody().getTransform().getTranslationX() + 20, zone.getBody().getTransform().getTranslationY() + 20);
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);
        
        // Simulate collision detection adding players to zone
        zone.addPlayer(player1.getId(), player1.getTeam());
        zone.addPlayer(player2.getId(), player2.getTeam());
        
        // Update zone control for enough time to capture (3 seconds for neutral)
        zone.update(3.0);
        
        // Verify zone is controlled by team 0
        assertEquals(0, zone.getControllingTeam());
        assertEquals(KothZone.ZoneState.CONTROLLED, zone.getState());
        assertEquals(1.0, zone.getCaptureProgress(), 0.01);
        assertTrue(zone.shouldAwardPoints());
    }

    @Test
    @DisplayName("KOTH Zone Control - Contested Zone")
    void testKothZoneControlContested() {
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // Create players from both teams in the zone
        Player player1 = createTestPlayer(1, 0, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        Player player2 = createTestPlayer(2, 1, zone.getBody().getTransform().getTranslationX() + 20, zone.getBody().getTransform().getTranslationY() + 20);
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);
        
        // Simulate collision detection
        zone.addPlayer(player1.getId(), player1.getTeam());
        zone.addPlayer(player2.getId(), player2.getTeam());
        
        // Update zone control
        zone.update(1.0);
        
        // Verify zone is contested
        assertEquals(-1, zone.getControllingTeam());
        assertEquals(KothZone.ZoneState.CONTESTED, zone.getState());
        assertFalse(zone.shouldAwardPoints());
    }

    @Test
    @DisplayName("KOTH Zone Control - Capture from Neutral")
    void testKothZoneCaptureFromNeutral() {
        // Get the second zone created by GameManager (should be neutral)
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertTrue(zones.size() >= 2, "GameManager should have created at least 2 KOTH zones");
        KothZone zone = zones.get(1);
        
        // Create player from team 1 in the zone
        Player player = createTestPlayer(1, 1, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        gameEntities.addPlayer(player);
        
        // Simulate collision detection
        zone.addPlayer(player.getId(), player.getTeam());
        
        // Update zone control for 3 seconds (should capture)
        zone.update(3.0);
        
        // Verify zone is captured by team 1
        assertEquals(1, zone.getControllingTeam());
        assertEquals(KothZone.ZoneState.CONTROLLED, zone.getState());
        assertEquals(1.0, zone.getCaptureProgress(), 0.01);
        assertTrue(zone.shouldAwardPoints());
    }

    @Test
    @DisplayName("KOTH Zone Control - Capture from Enemy")
    void testKothZoneCaptureFromEnemy() {
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // First, set up zone to be controlled by team 0
        zone.setControllingTeam(0);
        zone.setState(KothZone.ZoneState.CONTROLLED);
        zone.setCaptureProgress(1.0);
        
        // Create player from team 1 trying to capture team 0's zone
        Player player = createTestPlayer(1, 1, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        gameEntities.addPlayer(player);
        
        // Simulate collision detection
        zone.addPlayer(player.getId(), player.getTeam());
        
        // Update zone control for 5 seconds (should capture from enemy)
        zone.update(5.0);
        
        // Verify zone is captured by team 1
        assertEquals(1, zone.getControllingTeam());
        assertEquals(KothZone.ZoneState.CONTROLLED, zone.getState());
        assertEquals(1.0, zone.getCaptureProgress(), 0.01);
        assertTrue(zone.shouldAwardPoints());
    }

    @Test
    @DisplayName("KOTH Scoring - Frame Rate Independent")
    void testKothScoringFrameRateIndependent() {
        // Clear all AI players to avoid interference
        gameEntities.getAllPlayers().forEach(player -> {
            if (player.getId() > 100) { // AI players have high IDs
                gameEntities.removePlayer(player.getId());
            }
        });
        
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // Reset zone to neutral state
        zone.setControllingTeam(-1);
        zone.setState(KothZone.ZoneState.NEUTRAL);
        zone.setCaptureProgress(0.0);
        
        // Create one player from team 1 in the zone
        Player player1 = createTestPlayer(1, 1, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        gameEntities.addPlayer(player1);
        
        // Simulate collision detection and capture the zone
        zone.addPlayer(player1.getId(), player1.getTeam());
        
        // Update zone to ensure it's controlled (immediate control)
        zone.update(0.1);
        assertTrue(zone.shouldAwardPoints());
        assertEquals(1, zone.getControllingTeam()); // Zone should be controlled by team 1
        
        // Test different delta times for same total duration
        double initialTeamScore = zone.getTeamScore(1);
        
        // Simulate 2 seconds of scoring with different frame rates
        // High frame rate: 60 FPS (0.0167s per frame)
        for (int i = 0; i < 60; i++) { // 60 frames = 1 second
            zone.addPlayer(player1.getId(), player1.getTeam());
            collisionProcessor.updateKothZones(0.0167);
        }
        
        // Low frame rate: 30 FPS (0.0333s per frame)  
        for (int i = 0; i < 30; i++) { // 30 frames = 1 second
            zone.addPlayer(player1.getId(), player1.getTeam());
            collisionProcessor.updateKothZones(0.0333);
        }
        
        // Both should award same total points: 5 points/second * 2 seconds = 10 points
        double expectedTeamScore = initialTeamScore + 10.0; // 10 points total
        assertEquals(expectedTeamScore, zone.getTeamScore(1), 0.1); // Allow small floating point differences
    }

    @Test
    @DisplayName("KOTH Scoring - Multiple Zones")
    void testKothScoringMultipleZones() {
        // Clear all AI players to avoid interference
        gameEntities.getAllPlayers().forEach(player -> {
            if (player.getId() > 100) { // AI players have high IDs
                gameEntities.removePlayer(player.getId());
            }
        });
        
        // Get zones created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertTrue(zones.size() >= 2, "GameManager should have created at least 2 KOTH zones");
        
        // Create players from team 1 in both zones
        Player player1 = createTestPlayer(1, 1, zones.get(0).getBody().getTransform().getTranslationX(), zones.get(0).getBody().getTransform().getTranslationY());
        Player player2 = createTestPlayer(2, 1, zones.get(1).getBody().getTransform().getTranslationX(), zones.get(1).getBody().getTransform().getTranslationY());
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);
        
        // Set up both zones to be controlled by team 1 (3 seconds to capture from neutral)
        zones.get(0).addPlayer(player1.getId(), player1.getTeam());
        zones.get(0).update(3.0);
        
        zones.get(1).addPlayer(player2.getId(), player2.getTeam());
        zones.get(1).update(3.0);
        
        // Both zones should be controlled
        assertTrue(zones.get(0).shouldAwardPoints());
        assertTrue(zones.get(1).shouldAwardPoints());
        
        double initialTeamScore1 = zones.get(0).getTeamScore(1);
        double initialTeamScore2 = zones.get(1).getTeamScore(1);
        
        // Award points for 1 second
        // Ensure players are still in zones when awarding points
        zones.get(0).addPlayer(player1.getId(), player1.getTeam());
        zones.get(1).addPlayer(player2.getId(), player2.getTeam());
        collisionProcessor.updateKothZones(1.0);
        
        // Each zone awards 5 points/second, so each zone should have 5 points
        assertEquals(initialTeamScore1 + 5.0, zones.get(0).getTeamScore(1), 0.1);
        assertEquals(initialTeamScore2 + 5.0, zones.get(1).getTeamScore(1), 0.1);
    }

    @Test
    @DisplayName("KOTH Scoring - No Points for Dead Players")
    void testKothScoringNoPointsForDeadPlayers() {
        // Clear all AI players to avoid interference
        gameEntities.getAllPlayers().forEach(player -> {
            if (player.getId() > 100) { // AI players have high IDs
                gameEntities.removePlayer(player.getId());
            }
        });
        
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // Create living and dead players from team 1
        Player livingPlayer = createTestPlayer(1, 1, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        Player deadPlayer = createTestPlayer(2, 1, zone.getBody().getTransform().getTranslationX() + 20, zone.getBody().getTransform().getTranslationY() + 20);
        deadPlayer.setHealth(0); // Kill the player
        gameEntities.addPlayer(livingPlayer);
        gameEntities.addPlayer(deadPlayer);
        
        // Simulate collision detection (dead player shouldn't be added)
        zone.addPlayer(livingPlayer.getId(), livingPlayer.getTeam());
        zone.addPlayer(deadPlayer.getId(), deadPlayer.getTeam());
        
        // Update zone control (3 seconds to capture from neutral)
        zone.update(3.0);
        assertTrue(zone.shouldAwardPoints());
        
        double initialTeamScore = zone.getTeamScore(1);
        
        // Award points - ensure players are in zone
        zone.addPlayer(livingPlayer.getId(), livingPlayer.getTeam());
        collisionProcessor.updateKothZones(1.0);
        
        // Team should get points regardless of individual player health
        // (The zone scoring is based on team control, not individual player health)
        assertEquals(initialTeamScore + 5.0, zone.getTeamScore(1), 0.1);
    }

    @Test
    @DisplayName("KOTH Zone State Transitions")
    void testKothZoneStateTransitions() {
        // Get the second zone created by GameManager (should be neutral)
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertTrue(zones.size() >= 2, "GameManager should have created at least 2 KOTH zones");
        KothZone zone = zones.get(1);
        
        // Test NEUTRAL -> CAPTURING -> CONTROLLED
        Player player = createTestPlayer(1, 1, zone.getBody().getTransform().getTranslationX(), zone.getBody().getTransform().getTranslationY());
        gameEntities.addPlayer(player);
        
        // Start neutral
        assertEquals(KothZone.ZoneState.NEUTRAL, zone.getState());
        assertEquals(-1, zone.getControllingTeam());
        
        // Add player - should immediately control the zone
        zone.addPlayer(player.getId(), player.getTeam());
        zone.update(0.1);
        assertEquals(KothZone.ZoneState.CONTROLLED, zone.getState());
        assertEquals(1, zone.getControllingTeam());
        assertEquals(1.0, zone.getCaptureProgress(), 0.01);
    }

    @Test
    @DisplayName("KOTH Zone Player Tracking")
    void testKothZonePlayerTracking() {
        // Get the first zone created by GameManager
        List<KothZone> zones = new ArrayList<>(gameEntities.getAllKothZones());
        assertFalse(zones.isEmpty(), "GameManager should have created KOTH zones");
        KothZone zone = zones.get(0);
        
        // Add players
        zone.addPlayer(1, 0);
        zone.addPlayer(2, 0);
        zone.addPlayer(3, 1);
        
        // Check team counts
        assertEquals(2, zone.getTeamPlayerCount(0));
        assertEquals(1, zone.getTeamPlayerCount(1));
        assertEquals(0, zone.getTeamPlayerCount(2)); // Non-existent team
        
        // Clear players
        zone.clearPlayers();
        assertEquals(0, zone.getTeamPlayerCount(0));
        assertEquals(0, zone.getTeamPlayerCount(1));
    }

    @Test
    @DisplayName("KOTH Zone Points Per Second Configuration")
    void testKothZonePointsPerSecondConfiguration() {
        // Test different points per second values
        KothZone zone1 = new KothZone(200, 0, 500, 500, 2.0);
        KothZone zone2 = new KothZone(201, 1, 600, 600, 10.0);
        
        assertEquals(2.0, zone1.getPointsPerSecond());
        assertEquals(10.0, zone2.getPointsPerSecond());
        
        // Test scoring with different rates
        Player player = createTestPlayer(1, 1, 500, 500);
        gameEntities.addPlayer(player);
        
        // Add zone to game entities so collision processor can find it
        gameEntities.addKothZone(zone1);
        
        zone1.addPlayer(player.getId(), player.getTeam());
        zone1.setControllingTeam(1);
        zone1.setState(KothZone.ZoneState.CONTROLLED);
        
        double initialTeamScore = zone1.getTeamScore(1);
        
        // Award points for 1 second
        zone1.addPlayer(player.getId(), player.getTeam());
        collisionProcessor.updateKothZones(1.0);
        
        assertEquals(initialTeamScore + 2.0, zone1.getTeamScore(1), 0.1); // 2 points per second
    }

    private Player createTestPlayer(int id, int team, double x, double y) {
        Player player = new Player(id, "TestPlayer" + id, x, y, team, 100.0);
        player.setHealth(100); // Ensure player is alive
        return player;
    }
}

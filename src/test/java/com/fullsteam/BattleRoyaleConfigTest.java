package com.fullsteam;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
public class BattleRoyaleConfigTest extends BaseTestClass {

    @Inject
    Validator validator;

    @Test
    public void testBattleRoyaleConfiguration() {
        // Create the Battle Royale configuration as defined in lobby.html
        Rules rules = Rules.builder()
                .roundDuration(0.0)
                .restDuration(10.0)
                .flagsPerTeam(0)
                .scoreStyle(ScoreStyle.TOTAL_KILLS)
                .victoryCondition(VictoryCondition.ELIMINATION)
                .scoreLimit(50)
                .timeLimit(1200.0)  // 20 minutes
                .suddenDeath(false)
                .lockGameAfterSeconds(30.0)  // Lock after 30 seconds
                .respawnMode(RespawnMode.ELIMINATION)
                .respawnDelay(0.0)
                .waveRespawnInterval(30.0)
                .maxLives(-1)
                .kothZones(0)
                .kothPointsPerSecond(1.0)
                .addWorkshops(true)  // Workshops for loot
                .workshopCraftTime(8.0)
                .workshopCraftRadius(100.0)
                .maxPowerUpsPerWorkshop(4)
                .obstacleDensity(ObstacleDensity.CHOKED)  // Dense cover
                .enableRandomEvents(true)  // Random events
                .randomEventInterval(60.0)
                .randomEventIntervalVariance(0.4)
                .eventWarningDuration(4.0)
                .enableOddball(false)
                .addHeadquarters(false)
                .build();

        GameConfig config = GameConfig.builder()
                .maxPlayers(50)  // 50 players for BR
                .teamCount(0)    // FFA
                .worldWidth(4000.0)  // Large world
                .worldHeight(4000.0)
                .playerMaxHealth(100.0)
                .aiCheckIntervalMs(10000)
                .enableAIFilling(true)
                .rules(rules)
                .build();

        // Validate the configuration
        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        
        if (!violations.isEmpty()) {
            System.out.println("Validation violations found:");
            for (ConstraintViolation<GameConfig> violation : violations) {
                System.out.println("  - " + violation.getPropertyPath() + ": " + violation.getMessage());
            }
        }
        
        assertTrue(violations.isEmpty(), "Battle Royale configuration should be valid");
        
        // Verify key Battle Royale characteristics
        assertEquals(50, config.getMaxPlayers(), "Should support 50 players");
        assertEquals(0, config.getTeamCount(), "Should be FFA (teamCount = 0)");
        assertEquals(4000.0, config.getWorldWidth(), "Should have large world width");
        assertEquals(4000.0, config.getWorldHeight(), "Should have large world height");
        assertTrue(config.isFreeForAll(), "Should be in FFA mode");
        assertFalse(config.isTeamMode(), "Should not be in team mode");
        
        // Verify rules
        assertEquals(RespawnMode.ELIMINATION, rules.getRespawnMode(), "Should use elimination respawn");
        assertEquals(VictoryCondition.ELIMINATION, rules.getVictoryCondition(), "Should use elimination victory");
        assertEquals(ObstacleDensity.CHOKED, rules.getObstacleDensity(), "Should have choked obstacle density");
        assertTrue(rules.hasWorkshops(), "Should have workshops for loot");
        assertTrue(rules.isEnableRandomEvents(), "Should have random events enabled");
        assertEquals(30.0, rules.getLockGameAfterSeconds(), "Should lock game after 30 seconds");
        assertFalse(rules.hasHeadquarters(), "Should not have headquarters");
        assertFalse(rules.hasOddball(), "Should not have oddball");
        assertFalse(rules.hasKothZones(), "Should not have KOTH zones");
        assertFalse(rules.hasFlags(), "Should not have flags");
    }

    @Test
    public void testBattleRoyaleScaling() {
        // Test that the configuration scales appropriately
        GameConfig config = GameConfig.builder()
                .maxPlayers(50)
                .teamCount(0)
                .worldWidth(4000.0)
                .worldHeight(4000.0)
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "Should support 50 players with 4000x4000 world");
        
        // Calculate area per player
        double totalArea = config.getWorldWidth() * config.getWorldHeight();
        double areaPerPlayer = totalArea / config.getMaxPlayers();
        
        // With 4000x4000 = 16,000,000 total area / 50 players = 320,000 area per player
        // That's roughly equivalent to 565x565 per player, which is good spacing
        assertTrue(areaPerPlayer >= 300000, "Should have adequate space per player for BR gameplay");
        
        System.out.println("Battle Royale Stats:");
        System.out.println("  Total World Area: " + totalArea);
        System.out.println("  Players: " + config.getMaxPlayers());
        System.out.println("  Area per Player: " + areaPerPlayer);
        System.out.println("  Approx. Space per Player: ~" + Math.sqrt(areaPerPlayer) + "x" + Math.sqrt(areaPerPlayer));
    }
}


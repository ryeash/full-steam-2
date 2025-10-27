package com.fullsteam;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.ObstacleDensity;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.model.VictoryCondition;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
public class ValidationTest extends BaseTestClass {

    @Inject
    Validator validator;

    @Test
    public void testValidRules() {
        // Valid rules should pass validation
        Rules rules = Rules.builder()
                .roundDuration(120.0)
                .restDuration(10.0)
                .flagsPerTeam(2)
                .scoreLimit(50)
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertTrue(violations.isEmpty(), "Valid rules should have no violations");
    }

    @Test
    public void testInvalidRoundDuration() {
        // Round duration exceeds max
        Rules rules = Rules.builder()
                .roundDuration(10000.0) // Max is 7200
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for invalid roundDuration");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("roundDuration")));
    }

    @Test
    public void testInvalidNegativeValues() {
        // Negative values where not allowed
        Rules rules = Rules.builder()
                .respawnDelay(-5.0) // Min is 0
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for negative respawnDelay");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("respawnDelay")));
    }

    @Test
    public void testInvalidScoreLimit() {
        // Score limit below minimum
        Rules rules = Rules.builder()
                .scoreLimit(0) // Min is 1
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for invalid scoreLimit");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("scoreLimit")));
    }

    @Test
    public void testInvalidVarianceFactor() {
        // Variance factor out of range
        Rules rules = Rules.builder()
                .randomEventIntervalVariance(1.5) // Max is 1.0
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for invalid variance");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("randomEventIntervalVariance")));
    }

    @Test
    public void testInvalidKothZones() {
        // KOTH zones exceeds max
        Rules rules = Rules.builder()
                .kothZones(10) // Max is 4
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for invalid kothZones");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("kothZones")));
    }

    @Test
    public void testValidGameConfig() {
        // Valid game config should pass validation
        GameConfig config = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .playerMaxHealth(100.0)
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "Valid game config should have no violations");
    }

    @Test
    public void testInvalidMaxPlayers() {
        // Max players exceeds limit
        GameConfig config = GameConfig.builder()
                .maxPlayers(200) // Max is 100
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "Should have violations for invalid maxPlayers");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxPlayers")));
    }

    @Test
    public void testInvalidTeamCount() {
        // Team count exceeds max
        GameConfig config = GameConfig.builder()
                .teamCount(10) // Max is 4
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "Should have violations for invalid teamCount");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("teamCount")));
    }

    @Test
    public void testInvalidWorldDimensions() {
        // World dimensions too small
        GameConfig config = GameConfig.builder()
                .worldWidth(100.0) // Min is 500.0
                .worldHeight(100.0) // Min is 500.0
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "Should have violations for invalid world dimensions");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("worldWidth")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("worldHeight")));
    }

    @Test
    public void testInvalidPlayerMaxHealth() {
        // Player health too low
        GameConfig config = GameConfig.builder()
                .playerMaxHealth(5.0) // Min is 10.0
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "Should have violations for invalid playerMaxHealth");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("playerMaxHealth")));
    }

    @Test
    public void testNestedValidation() {
        // Test that nested Rules validation works through GameConfig
        Rules invalidRules = Rules.builder()
                .roundDuration(10000.0) // Invalid - exceeds max
                .build();

        GameConfig config = GameConfig.builder()
                .rules(invalidRules)
                .build();

        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "Should have violations for nested invalid rules");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("rules")));
    }

    @Test
    public void testMultipleViolations() {
        // Test multiple violations at once
        Rules rules = Rules.builder()
                .roundDuration(-1.0) // Invalid - negative
                .scoreLimit(0) // Invalid - below min
                .kothZones(10) // Invalid - exceeds max
                .randomEventIntervalVariance(2.0) // Invalid - exceeds max
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertTrue(violations.size() >= 4, "Should have at least 4 violations");
    }

    @Test
    public void testBoundaryValues() {
        // Test boundary values - should all be valid
        Rules rules = Rules.builder()
                .roundDuration(0.0) // Min boundary
                .restDuration(60.0) // Max boundary (actual max is 60, not 300)
                .scoreLimit(1) // Min boundary
                .maxLives(-1) // Min boundary (special case for unlimited)
                .kothZones(0) // Min boundary
                .flagsPerTeam(3) // Max boundary (actual max is 3, not 10)
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        if (!violations.isEmpty()) {
            System.out.println("Boundary test violations:");
            for (ConstraintViolation<Rules> v : violations) {
                System.out.println("  " + v.getPropertyPath() + ": " + v.getMessage() + " (value: " + v.getInvalidValue() + ")");
            }
        }
        assertTrue(violations.isEmpty(), "Boundary values should be valid");
    }

    @Test
    public void testNullEnumValues() {
        // Test that enum fields cannot be null
        Rules rules = Rules.builder()
                .scoreStyle(null)
                .build();

        Set<ConstraintViolation<Rules>> violations = validator.validate(rules);
        assertFalse(violations.isEmpty(), "Should have violations for null enum");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("scoreStyle")));
    }

    @Test
    public void testDefaultValues() {
        // Test that default values are all valid
        Rules rules = Rules.builder().build();
        Set<ConstraintViolation<Rules>> rulesViolations = validator.validate(rules);
        assertTrue(rulesViolations.isEmpty(), "Default Rules values should be valid");

        GameConfig config = GameConfig.builder().build();
        Set<ConstraintViolation<GameConfig>> configViolations = validator.validate(config);
        assertTrue(configViolations.isEmpty(), "Default GameConfig values should be valid");
    }
}


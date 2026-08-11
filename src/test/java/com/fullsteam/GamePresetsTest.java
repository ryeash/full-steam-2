package com.fullsteam;

import com.fullsteam.controller.GameController;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.GamePresets;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
public class GamePresetsTest extends BaseTestClass {

    @Inject
    Validator validator;

    @Inject
    GameController gameController;

    @Test
    @DisplayName("Should contain all 12 preset game configurations")
    public void testPresetsListCount() {
        List<GamePresets.Preset> presets = GamePresets.ALL_PRESETS;
        assertEquals(12, presets.size(), "Should have 12 game presets");
    }

    @Test
    @DisplayName("All presets should be valid GameConfig instances without constraint violations")
    public void testAllPresetsValidation() {
        for (GamePresets.Preset preset : GamePresets.ALL_PRESETS) {
            assertNotNull(preset.id(), "Preset ID should not be null");
            assertNotNull(preset.label(), "Preset label should not be null");
            assertNotNull(preset.description(), "Preset description should not be null");
            assertTrue(!preset.description().isBlank(), "Preset description should not be blank");
            assertNotNull(preset.config(), "Preset config should not be null");

            Set<ConstraintViolation<GameConfig>> violations = validator.validate(preset.config());
            assertTrue(violations.isEmpty(), "Preset '" + preset.id() + "' should have no validation violations");
        }
    }

    @Test
    @DisplayName("Should retrieve preset by ID")
    public void testGetPresetById() {
        assertTrue(GamePresets.getPreset("team-battle").isPresent());
        assertTrue(GamePresets.getPreset("ffa").isPresent());
        assertTrue(GamePresets.getPreset("ctf").isPresent());
        assertTrue(GamePresets.getPreset("domination").isPresent());
        assertTrue(GamePresets.getPreset("elimination").isPresent());
        assertTrue(GamePresets.getPreset("stock-battle").isPresent());
        assertTrue(GamePresets.getPreset("headquarters-assault").isPresent());
        assertTrue(GamePresets.getPreset("chaos-mode").isPresent());
        assertTrue(GamePresets.getPreset("oddball").isPresent());
        assertTrue(GamePresets.getPreset("vip-assassination").isPresent());
        assertTrue(GamePresets.getPreset("kingpin").isPresent());
        assertTrue(GamePresets.getPreset("last-stand").isPresent());
        assertTrue(GamePresets.getPreset("non-existent").isEmpty());
    }

    @Test
    @DisplayName("GameController should expose game presets endpoint")
    public void testGameControllerPresetEndpoints() {
        List<GamePresets.Preset> presets = gameController.getGamePresets();
        assertEquals(12, presets.size());
        assertEquals(12, gameController.getGameConfigPresets().size());
    }
}

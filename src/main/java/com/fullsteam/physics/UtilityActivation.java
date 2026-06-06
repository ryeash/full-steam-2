package com.fullsteam.physics;

import com.fullsteam.model.UtilityWeapon;
import org.dyn4j.geometry.Vector2;

/**
 * Data class for utility weapon activation
 */
public record UtilityActivation(
        UtilityWeapon utilityWeapon,
        Vector2 position,
        Vector2 direction,
        int playerId,
        int team
) {
}

package com.fullsteam.util;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Weapon;

import java.util.Comparator;
import java.util.Set;

/**
 * Utility class for formatting weapon information for display purposes.
 * Handles generating human-readable weapon names, especially for custom weapons.
 */
public final class WeaponFormatter {

    private WeaponFormatter() {
    }

    /**
     * Generate a display-friendly name for a weapon.
     * For preset weapons, returns the original name.
     * For custom weapons, generates a descriptive name based on ordinance type and effects.
     *
     * @param weapon The weapon to format
     * @return Display name for the weapon
     */
    public static String getDisplayName(Weapon weapon) {
        String weaponName = weapon.getName();

        // If it's a preset weapon (not "Custom Weapon"), use the original name
        if (!"Custom Weapon".equals(weaponName)) {
            return weaponName;
        }

        // For custom weapons, generate a name based on ordinance and effects
        StringBuilder displayName = new StringBuilder();

        // Add ordinance name
        displayName.append(formatOrdinanceName(weapon.getOrdinance()));

        // Add primary bullet effects
        Set<BulletEffect> effects = weapon.getBulletEffects();
        if (!effects.isEmpty()) {
            displayName.append(" ");
            effects.stream()
                    .min(Comparator.comparing(BulletEffect::ordinal))
                    .map(e -> "(" + formatEffectName(e) + ")")
                    .ifPresent(displayName::append);
        }

        return displayName.toString();
    }

    /**
     * Format ordinance type name for display.
     *
     * @param ordinance The ordinance type
     * @return Human-readable ordinance name
     */
    private static String formatOrdinanceName(com.fullsteam.model.Ordinance ordinance) {
        return switch (ordinance) {
            case BULLET -> "Bullet";
            case ROCKET -> "Rocket";
            case GRENADE -> "Grenade";
            case PLASMA -> "Plasma";
            case DART -> "Dart";
            case FLAMETHROWER -> "Flamethrower";
            case LASER -> "Laser";
            case PLASMA_BEAM -> "Plasma Beam";
            case HEAL_BEAM -> "Heal Beam";
            case RAILGUN -> "Railgun";
            default -> ordinance.name();
        };
    }

    /**
     * Format bullet effect name for display.
     *
     * @param effect The bullet effect
     * @return Human-readable effect name
     */
    private static String formatEffectName(BulletEffect effect) {
        return effect.toString().toLowerCase();
    }
}


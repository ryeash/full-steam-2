package com.fullsteam.ai;

import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for randomly selecting weapon presets for AI players.
 * Provides various selection strategies to ensure AI players have diverse and interesting weapons.
 */
public class AIWeaponSelector {

    // All available weapon presets grouped by category for strategic selection
    private static final List<WeaponConfig> BASIC_WEAPONS = List.of(
            WeaponConfig.ASSAULT_RIFLE_PRESET,
            WeaponConfig.HAND_CANNON_PRESET,
            WeaponConfig.PLASMA_RIFLE_PRESET,
            WeaponConfig.TWIN_SIXES_PRESET
    );

    private static final List<WeaponConfig> ORDINANCE_WEAPONS = List.of(
            WeaponConfig.SIEGE_CANNON_PRESET,
            WeaponConfig.PRECISION_DART_GUN_PRESET,
            WeaponConfig.FLAME_PROJECTOR_PRESET
    );

    private static final List<WeaponConfig> EFFECT_WEAPONS = List.of(
            WeaponConfig.BOUNCY_SMG_PRESET,
            WeaponConfig.PIERCING_RIFLE_PRESET,
            WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
            WeaponConfig.SEEKER_DART_PRESET,
            WeaponConfig.ARC_PISTOL_PRESET,
            WeaponConfig.TOXIC_SPRAYER_PRESET,
            WeaponConfig.ICE_CANNON_PRESET
    );

    private static final List<WeaponConfig> EXPLOSIVE_WEAPONS = List.of(
            WeaponConfig.EXPLOSIVE_SNIPER_PRESET,
            WeaponConfig.ROCKET_LAUNCHER_PRESET,
            WeaponConfig.GRENADE_LAUNCHER_PRESET,
            WeaponConfig.CLUSTER_MORTAR_PRESET
    );

    private static final List<WeaponConfig> BEAM_WEAPONS = List.of(
            WeaponConfig.LASER_RIFLE_PRESET,
            WeaponConfig.PLASMA_CANNON_PRESET,
            WeaponConfig.RAIL_CANNON_PRESET
    );

    // Combined list of all weapon presets for completely random selection
    private static final List<WeaponConfig> ALL_WEAPONS = List.of(
            // Basic weapons
            WeaponConfig.ASSAULT_RIFLE_PRESET,
            WeaponConfig.HAND_CANNON_PRESET,
            WeaponConfig.PLASMA_RIFLE_PRESET,
            WeaponConfig.TWIN_SIXES_PRESET,

            // Ordinance weapons
            WeaponConfig.SIEGE_CANNON_PRESET,
            WeaponConfig.PRECISION_DART_GUN_PRESET,
            WeaponConfig.FLAME_PROJECTOR_PRESET,

            // Effect weapons
            WeaponConfig.BOUNCY_SMG_PRESET,
            WeaponConfig.PIERCING_RIFLE_PRESET,
            WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
            WeaponConfig.SEEKER_DART_PRESET,
            WeaponConfig.ARC_PISTOL_PRESET,
            WeaponConfig.TOXIC_SPRAYER_PRESET,
            WeaponConfig.ICE_CANNON_PRESET,

            // Explosive weapons
            WeaponConfig.EXPLOSIVE_SNIPER_PRESET,
            WeaponConfig.ROCKET_LAUNCHER_PRESET,
            WeaponConfig.GRENADE_LAUNCHER_PRESET,
            WeaponConfig.CLUSTER_MORTAR_PRESET,

            // Beam weapons
            WeaponConfig.LASER_RIFLE_PRESET,
            WeaponConfig.PLASMA_CANNON_PRESET,
            WeaponConfig.RAIL_CANNON_PRESET
    );

    /**
     * Select a completely random weapon preset from all available presets.
     *
     * @return Random weapon preset
     */
    public static WeaponConfig selectRandomWeapon() {
        return ALL_WEAPONS.get(ThreadLocalRandom.current().nextInt(ALL_WEAPONS.size()));
    }

    /**
     * Select a random weapon preset based on AI personality.
     * Different personalities prefer different weapon categories.
     *
     * @param personality The AI personality to select for
     * @return Weapon preset suitable for the personality
     */
    public static WeaponConfig selectWeaponForPersonality(AIPersonality personality) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        switch (personality.getPersonalityType()) {
            case "Berserker":
                // Berserkers prefer explosive and high-damage weapons
                if (random.nextDouble() < 0.7) {
                    return EXPLOSIVE_WEAPONS.get(random.nextInt(EXPLOSIVE_WEAPONS.size()));
                } else {
                    return EFFECT_WEAPONS.get(random.nextInt(EFFECT_WEAPONS.size()));
                }

            case "Sniper":
                // Snipers prefer long-range precision weapons
                List<WeaponConfig> sniperWeapons = List.of(
                        WeaponConfig.PIERCING_RIFLE_PRESET,
                        WeaponConfig.EXPLOSIVE_SNIPER_PRESET,
                        WeaponConfig.PRECISION_DART_GUN_PRESET,
                        WeaponConfig.SIEGE_CANNON_PRESET,
                        WeaponConfig.LASER_RIFLE_PRESET,
                        WeaponConfig.RAIL_CANNON_PRESET
                );
                return sniperWeapons.get(random.nextInt(sniperWeapons.size()));

            case "Rusher":
                // Rushers prefer close-range, high-mobility weapons
                List<WeaponConfig> rusherWeapons = List.of(
                        WeaponConfig.BOUNCY_SMG_PRESET,
                        WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
                        WeaponConfig.FLAME_PROJECTOR_PRESET,
                        WeaponConfig.ARC_PISTOL_PRESET,
                        WeaponConfig.ASSAULT_RIFLE_PRESET,
                        WeaponConfig.TWIN_SIXES_PRESET
                );
                return rusherWeapons.get(random.nextInt(rusherWeapons.size()));

            case "Strategist":
                // Strategists prefer tactical weapons and special ordinance
                if (random.nextDouble() < 0.5) {
                    return ORDINANCE_WEAPONS.get(random.nextInt(ORDINANCE_WEAPONS.size()));
                } else {
                    // Tactical weapons with special effects
                    List<WeaponConfig> tacticalWeapons = List.of(
                            WeaponConfig.SEEKER_DART_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.CLUSTER_MORTAR_PRESET,
                            WeaponConfig.ARC_PISTOL_PRESET
                    );
                    return tacticalWeapons.get(random.nextInt(tacticalWeapons.size()));
                }

            case "Support":
                // Support AIs prefer utility weapons and area effects
                if (random.nextDouble() < 0.6) {
                    // Area denial and utility weapons
                    List<WeaponConfig> supportWeapons = List.of(
                            WeaponConfig.TOXIC_SPRAYER_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.ARC_PISTOL_PRESET,
                            WeaponConfig.CLUSTER_MORTAR_PRESET
                    );
                    return supportWeapons.get(random.nextInt(supportWeapons.size()));
                } else {
                    return BASIC_WEAPONS.get(random.nextInt(BASIC_WEAPONS.size()));
                }

            case "Guardian":
                // Guardians prefer defensive weapons and area denial
                if (random.nextDouble() < 0.5) {
                    return BASIC_WEAPONS.get(random.nextInt(BASIC_WEAPONS.size()));
                } else {
                    // Area denial weapons
                    List<WeaponConfig> guardianWeapons = List.of(
                            WeaponConfig.TOXIC_SPRAYER_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.FLAME_PROJECTOR_PRESET,
                            WeaponConfig.SIEGE_CANNON_PRESET
                    );
                    return guardianWeapons.get(random.nextInt(guardianWeapons.size()));
                }

            case "Soldier":
            default:
                // Soldiers and fallback get balanced weapon selection
                return selectRandomWeapon();
        }
    }

    /**
     * Select two different weapon presets for primary and secondary weapons.
     * Ensures AI players have diverse loadouts.
     *
     * @return Array with [primary, secondary] weapon configs
     */
    public static WeaponConfig[] selectWeaponLoadout() {
        // Select primary weapon from any category
        WeaponConfig primary = selectRandomWeapon();

        // Select secondary weapon from a different category if possible
        WeaponConfig secondary;
        int maxAttempts = 10;
        int attempts = 0;

        do {
            secondary = selectRandomWeapon();
            attempts++;
        } while (primary.equals(secondary) && attempts < maxAttempts);

        return new WeaponConfig[]{primary, secondary};
    }

    /**
     * Select weapons for a personality with diverse loadout.
     *
     * @param personality The AI personality
     * @return Array with [primary, secondary] weapon configs
     */
    public static WeaponConfig[] selectWeaponLoadoutForPersonality(AIPersonality personality) {
        WeaponConfig primary = selectWeaponForPersonality(personality);

        // For secondary, either pick complementary weapon or random
        WeaponConfig secondary;
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            // 30% chance to pick a complementary weapon type
            secondary = selectComplementaryWeapon(primary);
        } else {
            // 70% chance to pick any other weapon
            secondary = selectRandomWeapon();
            // Ensure they're different
            if (primary.equals(secondary)) {
                secondary = selectRandomWeapon();
            }
        }

        return new WeaponConfig[]{primary, secondary};
    }

    /**
     * Select a weapon that complements the given primary weapon.
     *
     * @param primary The primary weapon
     * @return A complementary secondary weapon
     */
    private static WeaponConfig selectComplementaryWeapon(WeaponConfig primary) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // If primary is long-range, pick short-range secondary
        if (isLongRangeWeapon(primary)) {
            List<WeaponConfig> shortRange = List.of(
                    WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
                    WeaponConfig.FLAME_PROJECTOR_PRESET,
                    WeaponConfig.TOXIC_SPRAYER_PRESET,
                    WeaponConfig.BOUNCY_SMG_PRESET,
                    WeaponConfig.ARC_PISTOL_PRESET,
                    WeaponConfig.TWIN_SIXES_PRESET
            );
            return shortRange.get(random.nextInt(shortRange.size()));
        }

        // If primary is short-range, pick long-range secondary
        if (isShortRangeWeapon(primary)) {
            List<WeaponConfig> longRange = List.of(
                    WeaponConfig.PIERCING_RIFLE_PRESET,
                    WeaponConfig.EXPLOSIVE_SNIPER_PRESET,
                    WeaponConfig.PRECISION_DART_GUN_PRESET,
                    WeaponConfig.SIEGE_CANNON_PRESET,
                    WeaponConfig.LASER_RIFLE_PRESET,
                    WeaponConfig.RAIL_CANNON_PRESET
            );
            return longRange.get(random.nextInt(longRange.size()));
        }

        // For medium-range weapons, pick anything different
        return selectRandomWeapon();
    }

    private static boolean isLongRangeWeapon(WeaponConfig weapon) {
        return weapon.range >= 15 || weapon == WeaponConfig.PIERCING_RIFLE_PRESET
               || weapon == WeaponConfig.EXPLOSIVE_SNIPER_PRESET
               || weapon == WeaponConfig.SIEGE_CANNON_PRESET
               || weapon == WeaponConfig.LASER_RIFLE_PRESET
               || weapon == WeaponConfig.RAIL_CANNON_PRESET;
    }

    private static boolean isShortRangeWeapon(WeaponConfig weapon) {
        return weapon.range <= 6 || weapon == WeaponConfig.INCENDIARY_SHOTGUN_PRESET
               || weapon == WeaponConfig.FLAME_PROJECTOR_PRESET
               || weapon == WeaponConfig.TOXIC_SPRAYER_PRESET
               || weapon == WeaponConfig.TWIN_SIXES_PRESET;
    }

    /**
     * Select a random utility weapon for AI players.
     *
     * @return A randomly selected utility weapon
     */
    public static UtilityWeapon selectRandomUtilityWeapon() {
        UtilityWeapon[] allUtilities = UtilityWeapon.values();
        return allUtilities[ThreadLocalRandom.current().nextInt(allUtilities.length)];
    }

    /**
     * Select a utility weapon based on AI personality.
     * Different personalities prefer different utility categories.
     *
     * @param personality The AI personality to select for
     * @return Utility weapon suitable for the personality
     */
    public static UtilityWeapon selectUtilityWeaponForPersonality(AIPersonality personality) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return switch (personality.getPersonalityType()) {
            case "Berserker" -> {
                // Berserkers prefer offensive and crowd control utilities
                List<UtilityWeapon> berserkerUtilities = List.of(
                        UtilityWeapon.GRAVITY_WELL,
                        UtilityWeapon.SLOW_FIELD,
                        UtilityWeapon.NET_LAUNCHER,
                        UtilityWeapon.MINE_LAYER
                );
                yield berserkerUtilities.get(random.nextInt(berserkerUtilities.size()));
            }
            case "Sniper" -> {
                // Snipers prefer tactical and defensive utilities
                List<UtilityWeapon> sniperUtilities = List.of(
                        UtilityWeapon.TURRET_CONSTRUCTOR,
                        UtilityWeapon.WALL_BUILDER,
                        UtilityWeapon.MINE_LAYER,
                        UtilityWeapon.TELEPORTER
                );
                yield sniperUtilities.get(random.nextInt(sniperUtilities.size()));
            }
            case "Rusher" -> {
                // Rushers prefer mobility and quick deployment utilities
                List<UtilityWeapon> rusherUtilities = List.of(
                        UtilityWeapon.SPEED_BOOST_PAD,
                        UtilityWeapon.NET_LAUNCHER,
                        UtilityWeapon.TELEPORTER
                );
                yield rusherUtilities.get(random.nextInt(rusherUtilities.size()));
            }
            case "Strategist" -> {
                // Strategists prefer area control and support utilities
                List<UtilityWeapon> strategistUtilities = List.of(
                        UtilityWeapon.HEAL_ZONE,
                        UtilityWeapon.SHIELD_GENERATOR,
                        UtilityWeapon.TURRET_CONSTRUCTOR,
                        UtilityWeapon.GRAVITY_WELL,
                        UtilityWeapon.SLOW_FIELD,
                        UtilityWeapon.TELEPORTER
                );
                yield strategistUtilities.get(random.nextInt(strategistUtilities.size()));
            }
            case "Guardian" -> {
                // Guardians prefer defensive and support utilities
                List<UtilityWeapon> guardianUtilities = List.of(
                        UtilityWeapon.HEAL_ZONE,
                        UtilityWeapon.SHIELD_GENERATOR,
                        UtilityWeapon.WALL_BUILDER,
                        UtilityWeapon.TURRET_CONSTRUCTOR,
                        UtilityWeapon.MINE_LAYER,
                        UtilityWeapon.SPEED_BOOST_PAD
                );
                yield guardianUtilities.get(random.nextInt(guardianUtilities.size()));
            }
            default -> selectRandomUtilityWeapon();
        };
    }
}

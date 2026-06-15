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

    // Available weapon presets grouped by role for strategic selection.
    private static final List<WeaponConfig> BASIC_WEAPONS = List.of(
            WeaponConfig.ASSAULT_RIFLE_PRESET,
            WeaponConfig.HAND_CANNON_PRESET,
            WeaponConfig.SNIPER_RIFLE_PRESET,
            WeaponConfig.TWIN_SIXES_PRESET,
            WeaponConfig.SHOTGUN_PRESET,
            WeaponConfig.MINIGUN_PRESET
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
            WeaponConfig.ROCKET_LAUNCHER_PRESET,
            WeaponConfig.CLUSTER_MORTAR_PRESET,
            WeaponConfig.SHRAPNEL_CANNON_PRESET
    );

    private static final List<WeaponConfig> BEAM_WEAPONS = List.of(
            WeaponConfig.LASER_RIFLE_PRESET,
            WeaponConfig.PLASMA_CANNON_PRESET,
            WeaponConfig.ARC_LASER_PRESET,
            WeaponConfig.RAILGUN_PRESET
    );

    private static final List<WeaponConfig> COMBO_WEAPONS = List.of(
            WeaponConfig.NAPALM_LAUNCHER_PRESET,
            WeaponConfig.STORM_CALLER_PRESET,
            WeaponConfig.VENOM_NEEDLER_PRESET,
            WeaponConfig.FROST_LANCE_PRESET,
            WeaponConfig.PHANTOM_NEEDLES_PRESET
    );

    private static final List<WeaponConfig> ALL_WEAPONS = List.of(
            // Kinetic
            WeaponConfig.ASSAULT_RIFLE_PRESET,
            WeaponConfig.HAND_CANNON_PRESET,
            WeaponConfig.SNIPER_RIFLE_PRESET,
            WeaponConfig.TWIN_SIXES_PRESET,
            WeaponConfig.SHOTGUN_PRESET,
            WeaponConfig.MINIGUN_PRESET,

            // Single-effect
            WeaponConfig.ROCKET_LAUNCHER_PRESET,
            WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
            WeaponConfig.ARC_PISTOL_PRESET,
            WeaponConfig.ICE_CANNON_PRESET,
            WeaponConfig.TOXIC_SPRAYER_PRESET,
            WeaponConfig.PIERCING_RIFLE_PRESET,
            WeaponConfig.BOUNCY_SMG_PRESET,
            WeaponConfig.SEEKER_DART_PRESET,
            WeaponConfig.CLUSTER_MORTAR_PRESET,

            // Beams
            WeaponConfig.LASER_RIFLE_PRESET,
            WeaponConfig.PLASMA_CANNON_PRESET,
            WeaponConfig.ARC_LASER_PRESET,
            WeaponConfig.RAILGUN_PRESET,

            // Combos
            WeaponConfig.NAPALM_LAUNCHER_PRESET,
            WeaponConfig.STORM_CALLER_PRESET,
            WeaponConfig.VENOM_NEEDLER_PRESET,
            WeaponConfig.FROST_LANCE_PRESET,
            WeaponConfig.SHRAPNEL_CANNON_PRESET,
            WeaponConfig.PHANTOM_NEEDLES_PRESET
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
            case "Berserker": {
                // Berserkers prefer explosive and high-damage weapons
                double roll = random.nextDouble();
                if (roll < 0.5) {
                    return EXPLOSIVE_WEAPONS.get(random.nextInt(EXPLOSIVE_WEAPONS.size()));
                } else if (roll < 0.8) {
                    return EFFECT_WEAPONS.get(random.nextInt(EFFECT_WEAPONS.size()));
                } else {
                    List<WeaponConfig> berserkerCombos = List.of(
                            WeaponConfig.MINIGUN_PRESET,
                            WeaponConfig.NAPALM_LAUNCHER_PRESET,
                            WeaponConfig.SHRAPNEL_CANNON_PRESET,
                            WeaponConfig.VENOM_NEEDLER_PRESET
                    );
                    return berserkerCombos.get(random.nextInt(berserkerCombos.size()));
                }
            }

            case "Sniper": {
                // Snipers prefer long-range precision weapons including beams
                List<WeaponConfig> sniperWeapons = List.of(
                        WeaponConfig.SNIPER_RIFLE_PRESET,
                        WeaponConfig.PIERCING_RIFLE_PRESET,
                        WeaponConfig.LASER_RIFLE_PRESET,
                        WeaponConfig.VENOM_NEEDLER_PRESET,
                        WeaponConfig.FROST_LANCE_PRESET
                );
                return sniperWeapons.get(random.nextInt(sniperWeapons.size()));
            }

            case "Rusher": {
                // Rushers prefer close-range, high-mobility weapons
                List<WeaponConfig> rusherWeapons = List.of(
                        WeaponConfig.MINIGUN_PRESET,
                        WeaponConfig.BOUNCY_SMG_PRESET,
                        WeaponConfig.INCENDIARY_SHOTGUN_PRESET,
                        WeaponConfig.SHOTGUN_PRESET,
                        WeaponConfig.ARC_PISTOL_PRESET,
                        WeaponConfig.ASSAULT_RIFLE_PRESET,
                        WeaponConfig.TWIN_SIXES_PRESET,
                        WeaponConfig.STORM_CALLER_PRESET
                );
                return rusherWeapons.get(random.nextInt(rusherWeapons.size()));
            }

            case "Strategist": {
                // Strategists prefer tactical weapons with special effects
                if (random.nextDouble() < 0.5) {
                    List<WeaponConfig> tacticalWeapons = List.of(
                            WeaponConfig.SEEKER_DART_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.CLUSTER_MORTAR_PRESET,
                            WeaponConfig.ARC_PISTOL_PRESET
                    );
                    return tacticalWeapons.get(random.nextInt(tacticalWeapons.size()));
                } else {
                    List<WeaponConfig> strategistCombos = List.of(
                            WeaponConfig.PHANTOM_NEEDLES_PRESET,
                            WeaponConfig.VENOM_NEEDLER_PRESET,
                            WeaponConfig.FROST_LANCE_PRESET
                    );
                    return strategistCombos.get(random.nextInt(strategistCombos.size()));
                }
            }

            case "Support": {
                // Support AIs prefer utility weapons and area effects
                double roll = random.nextDouble();
                if (roll < 0.55) {
                    List<WeaponConfig> supportWeapons = List.of(
                            WeaponConfig.TOXIC_SPRAYER_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.ARC_PISTOL_PRESET,
                            WeaponConfig.CLUSTER_MORTAR_PRESET,
                            WeaponConfig.PLASMA_CANNON_PRESET
                    );
                    return supportWeapons.get(random.nextInt(supportWeapons.size()));
                } else if (roll < 0.8) {
                    return BASIC_WEAPONS.get(random.nextInt(BASIC_WEAPONS.size()));
                } else {
                    List<WeaponConfig> supportCombos = List.of(
                            WeaponConfig.STORM_CALLER_PRESET,
                            WeaponConfig.FROST_LANCE_PRESET,
                            WeaponConfig.VENOM_NEEDLER_PRESET
                    );
                    return supportCombos.get(random.nextInt(supportCombos.size()));
                }
            }

            case "Guardian": {
                // Guardians prefer defensive weapons and area denial
                double roll = random.nextDouble();
                if (roll < 0.4) {
                    return BASIC_WEAPONS.get(random.nextInt(BASIC_WEAPONS.size()));
                } else if (roll < 0.7) {
                    List<WeaponConfig> guardianWeapons = List.of(
                            WeaponConfig.TOXIC_SPRAYER_PRESET,
                            WeaponConfig.ICE_CANNON_PRESET,
                            WeaponConfig.SHOTGUN_PRESET
                    );
                    return guardianWeapons.get(random.nextInt(guardianWeapons.size()));
                } else {
                    List<WeaponConfig> guardianCombos = List.of(
                            WeaponConfig.FROST_LANCE_PRESET,
                            WeaponConfig.VENOM_NEEDLER_PRESET,
                            WeaponConfig.SHRAPNEL_CANNON_PRESET
                    );
                    return guardianCombos.get(random.nextInt(guardianCombos.size()));
                }
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
                List<UtilityWeapon> berserkerUtilities = List.of(
                        UtilityWeapon.GRAVITY_WELL,
                        UtilityWeapon.SLOW_FIELD,
                        UtilityWeapon.NET_LAUNCHER,
                        UtilityWeapon.MINE_LAYER
                );
                yield berserkerUtilities.get(random.nextInt(berserkerUtilities.size()));
            }
            case "Sniper" -> {
                List<UtilityWeapon> sniperUtilities = List.of(
                        UtilityWeapon.TURRET_CONSTRUCTOR,
                        UtilityWeapon.MINE_LAYER
                );
                yield sniperUtilities.get(random.nextInt(sniperUtilities.size()));
            }
            case "Rusher" -> {
                List<UtilityWeapon> rusherUtilities = List.of(
                        UtilityWeapon.SPEED_BOOST_PAD,
                        UtilityWeapon.NET_LAUNCHER,
                        UtilityWeapon.SMOKE_GRENADE
                );
                yield rusherUtilities.get(random.nextInt(rusherUtilities.size()));
            }
            case "Strategist" -> {
                List<UtilityWeapon> strategistUtilities = List.of(
                        UtilityWeapon.HEAL_ZONE,
                        UtilityWeapon.SHIELD_GENERATOR,
                        UtilityWeapon.TURRET_CONSTRUCTOR,
                        UtilityWeapon.GRAVITY_WELL,
                        UtilityWeapon.SLOW_FIELD,
                        UtilityWeapon.SMOKE_GRENADE
                );
                yield strategistUtilities.get(random.nextInt(strategistUtilities.size()));
            }
            case "Guardian" -> {
                List<UtilityWeapon> guardianUtilities = List.of(
                        UtilityWeapon.HEAL_ZONE,
                        UtilityWeapon.SHIELD_GENERATOR,
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

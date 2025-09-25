package com.fullsteam.model;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.physics.Player;

/**
 * Collection of common status effects that can be applied to players.
 * These are pre-configured attribute modifications for common game scenarios.
 */
public class StatusEffects {

    /**
     * Apply a speed boost effect to a player.
     */
    public static void applySpeedBoost(Player player, double speedMultiplier, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "speed_sparks:#00FFFF:sparkle:true:Speed Boost";
            }

            @Override
            public void update(Player player, double delta) {
                player.setMaxSpeed(Config.PLAYER_SPEED * speedMultiplier);
            }

            @Override
            public void revert(Player player) {
                player.setMaxSpeed(Config.PLAYER_SPEED);
            }
        });
    }

    /**
     * Apply health regeneration effect to a player.
     */
    public static void applyHealthRegeneration(Player player, double healthPerSecond, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "healing_sparkles:#00FFFF:pulse:true:Regenerating";
            }

            @Override
            public void update(Player player, double delta) {
                double healthRecovered = healthPerSecond * (delta / 1000);
                player.setHealth(Math.min(player.getHealth() + healthRecovered, 100));
            }
        });
    }

    public static void applyBurning(GameManager gameManager, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
        applyHealthDegeneration(gameManager, "fire:#00FFFF:flame:true:Burning", player, damagePerSecond, durationSeconds, effectOwner);
    }

    public static void applyPoison(GameManager gameManager, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
        applyHealthDegeneration(gameManager, "poison:#00FFFF:pulse:true:Poison", player, damagePerSecond, durationSeconds, effectOwner);
    }

    /**
     * Apply health degeneration (poison/bleed) effect to a player.
     */
    public static void applyHealthDegeneration(GameManager gameManager, String renderHint, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return renderHint;
            }

            @Override
            public void update(Player player, double delta) {
                double damage = damagePerSecond * (delta / 1000);
                if (player.takeDamage(damage)) {
                    gameManager.killPlayer(player, gameManager.getGameEntities().getPlayer(effectOwner));
                }
            }
        });
    }

    /**
     * Apply damage resistance effect to a player.
     */
    public static void applyDamageResistance(Player player, double resistancePercentage, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "shield_shimmer:#FFD700:shield:true:Protected!";
            }

            @Override
            public double modifyDamageReceived(double damage) {
                return super.modifyDamageReceived(damage * (resistancePercentage / 100));
            }
        });
    }

//    /**
//     * Apply rapid fire effect to a player's weapon.
//     */
//    public static AttributeModification applyRapidFire(Player player, double fireRateMultiplier, double durationSeconds, String source) {
//        AttributeModification.AttributeModificationVisuals visuals = AttributeModification.AttributeModificationVisuals.builder()
//                .particleEffect("muzzle_enhancement")
//                .color("#FF4500")
//                .animation("flash")
//                .showFloatingText(true)
//                .displayText("Rapid Fire!")
//                .build();
//
//        return player.applyAttributeModification(
//                AttributeModificationType.WEAPON_FIRE_RATE,
//                fireRateMultiplier,
//                ModificationMethod.MULTIPLY,
//                durationSeconds,
//                source,
//                -1,
//                false,
//                visuals
//        );
//    }

    /**
     * Apply weapon damage boost effect.
     */
    public static void applyDamageBoost(Player player, double damageMultiplier, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "power_aura:#FF0000:pulse:true:Damage Boost!";
            }

            @Override
            public Weapon update(Weapon weapon) {
                return new Weapon(weapon) {
                    @Override
                    public double getDamage() {
                        return super.getDamage() * damageMultiplier;
                    }
                };
            }
        });
    }

//    /**
//     * Apply precision mode - increased accuracy but reduced fire rate.
//     */
//    public static void applyPrecisionMode(Player player, double durationSeconds, String source) {
//        // Increase accuracy
//        AttributeModification.AttributeModificationVisuals accuracyVisuals = AttributeModification.AttributeModificationVisuals.builder()
//                .particleEffect("precision_reticle")
//                .color("#00FFFF")
//                .animation("focus")
//                .showFloatingText(true)
//                .displayText("Precision Mode")
//                .build();
//
//        player.applyAttributeModification(
//                AttributeModificationType.WEAPON_ACCURACY,
//                1.3, // 30% accuracy boost
//                ModificationMethod.MULTIPLY,
//                durationSeconds,
//                source,
//                -1,
//                false,
//                accuracyVisuals
//        );
//
//        // Reduce fire rate
//        player.applyAttributeModification(
//                AttributeModificationType.WEAPON_FIRE_RATE,
//                0.7, // 30% fire rate reduction
//                ModificationMethod.MULTIPLY,
//                durationSeconds,
//                source,
//                -1,
//                false,
//                null // No separate visual for fire rate reduction
//        );
//    }

    /**
     * Apply berserker mode - increased damage and speed, reduced defense.
     */
    public static void applyBerserkerMode(Player player, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "power_aura:#FF0000:pulse:true:Damage Boost!";
            }

            @Override
            public Weapon update(Weapon weapon) {
                return new Weapon(weapon) {
                    @Override
                    public double getDamage() {
                        return super.getDamage() * 1.5;
                    }
                };
            }

            @Override
            public void update(Player player, double delta) {
                player.setMaxSpeed(Config.PLAYER_SPEED * 1.4);
            }

            @Override
            public void revert(Player player) {
                player.setMaxSpeed(Config.PLAYER_SPEED);
            }

            @Override
            public double modifyDamageReceived(double damage) {
                return damage * 2; // take double damage
            }
        });
    }

    /**
     * Apply slowing effect to a player.
     */
    public static void applySlowEffect(Player player, double speedMultiplier, double durationSeconds, String source) {
        player.getAttributeModifications().add(new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String renderHint() {
                return "slow_debuff:#0066CC:slow:true:Slowed";
            }

            @Override
            public void update(Player player, double delta) {
                player.setMaxSpeed(Config.PLAYER_SPEED * speedMultiplier);
            }

            @Override
            public void revert(Player player) {
                player.setMaxSpeed(Config.PLAYER_SPEED);
            }
        });
    }
}

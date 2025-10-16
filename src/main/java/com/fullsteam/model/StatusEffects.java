package com.fullsteam.model;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.physics.Player;

/**
 * Collection of common status effects that can be applied to players.
 * These are pre-configured attribute modifications for common game scenarios.
 */
public class StatusEffects {

    public static void applyEffect(Player player, AttributeModification attributeModification) {
        player.getAttributeModifications().removeIf(am -> am.uniqueKey().equals(attributeModification.uniqueKey()));
        player.getAttributeModifications().add(attributeModification);
    }

    /**
     * Apply a speed boost effect to a player.
     */
    public static void applySpeedBoost(Player player, double speedMultiplier, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "speedBoost";
            }

            @Override
            public String renderHint() {
                return "speed_sparks:#00FFFF:sparkle:true:Speed Boost";
            }

            @Override
            public void update(Player player, double delta) {
                player.getBody().setLinearDamping(0);
            }

            @Override
            public void revert(Player player) {
                player.getBody().setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
            }
        });
    }

    /**
     * Apply health regeneration effect to a player.
     */
    public static void applyHealthRegeneration(Player player, double healthPerSecond, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "healthRegen";
            }

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
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "healthDegen";
            }

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
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "damageResist";
            }

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

    /**
     * Apply weapon damage boost effect.
     */
    public static void applyDamageBoost(Player player, double damageMultiplier, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "damageBoost";
            }

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

    /**
     * Apply berserker mode - increased damage and speed, reduced defense.
     */
    public static void applyBerserkerMode(Player player, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "berserk";
            }

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
    public static void applySlowEffect(Player player, double linearDamping, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "slow";
            }

            @Override
            public String renderHint() {
                return "slow_debuff:#0066CC:slow:true:Slowed";
            }

            @Override
            public void update(Player player, double delta) {
                player.getBody().setLinearDamping(linearDamping);
            }

            @Override
            public void revert(Player player) {
                player.getBody().setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
            }
        });
    }
}

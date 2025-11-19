package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.BaseAttributeModification;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.Player;
import com.fullsteam.util.GameConstants;

/**
 * Manages status effects that can be applied to players during gameplay.
 * This class provides pre-configured attribute modifications for common game scenarios.
 * 
 * Status effects include:
 * - Buffs: Speed boost, damage boost, health regeneration, damage resistance, invincibility
 * - Debuffs: Burning, poison, slow
 * - Game mode effects: Ball carrier (Oddball), VIP status
 * 
 * <h2>RenderHint Format</h2>
 * Status effects use a declarative renderHint string to control visual appearance on the client:
 * <pre>
 * "effect_name:#HEXCOLOR:animation_type:show_icon:Display Name:params"
 * </pre>
 * 
 * <h3>Fields:</h3>
 * <ul>
 *   <li><b>effect_name</b>: Internal identifier (e.g., "poison", "fire", "speed_sparks")</li>
 *   <li><b>#HEXCOLOR</b>: Color in hex format (e.g., "#FF4500" for orange-red)</li>
 *   <li><b>animation_type</b>: Visual effect type - see Animation Types below</li>
 *   <li><b>show_icon</b>: "true" to show badge icon, "false" to hide</li>
 *   <li><b>Display Name</b>: Human-readable name shown to player</li>
 *   <li><b>params</b>: (Optional) JSON object with animation parameters</li>
 * </ul>
 * 
 * <h3>Animation Types:</h3>
 * <ul>
 *   <li><b>pulse/sparkle</b>: Pulsing ring with rotating particles
 *       <br>Params: {particles, radius, particleDistance, particleSize}</li>
 *   <li><b>shield</b>: Polygonal shield pattern
 *       <br>Params: {sides, size}</li>
 *   <li><b>slow</b>: Dripping effect for debuffs
 *       <br>Params: {drops, radius, dropSize, dripAmount}</li>
 *   <li><b>cloud</b>: Billowing cloud effect (poison)
 *       <br>Params: {radius, puffs, wisps}</li>
 *   <li><b>flame</b>: Flickering fire particles (burning)
 *       <br>Params: {count, radius, height}</li>
 *   <li><b>star</b>: Orbiting stars (special status)
 *       <br>Params: {count, radius, size}</li>
 *   <li><b>crown</b>: VIP crown with sparkles</li>
 * </ul>
 * 
 * <h3>Examples:</h3>
 * <pre>
 * // Simple format (no params)
 * "poison:#8BC34A:cloud:true:Poison"
 * 
 * // Enhanced format with parameters
 * "fire:#FF4500:flame:true:Burning:{\"count\":12,\"radius\":22,\"height\":10}"
 * "speed_sparks:#00FFFF:sparkle:true:Speed Boost:{\"particles\":12,\"particleDistance\":28}"
 * </pre>
 */
public final class StatusEffectManager {

    private StatusEffectManager() {
    }

    /**
     * Apply an effect to a player, replacing any existing effect with the same unique key.
     */
    private static void applyEffect(Player player, AttributeModification attributeModification) {
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
                return "speed_sparks:#00FFFF:sparkle:true:Speed Boost:{\"particles\":12,\"particleDistance\":28}";
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
                return "shield_shimmer:#FFD700:shield:true:Protected!:{\"sides\":8,\"size\":24}";
            }

            @Override
            public double modifyDamageReceived(double damage) {
                return super.modifyDamageReceived(damage * (resistancePercentage / 100));
            }
        });
    }

    /**
     * Apply invincibility effect to a player (spawn protection).
     * Invincible players take no damage from any source.
     */
    public static void applyInvincibility(Player player, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "invincible";
            }

            @Override
            public String renderHint() {
                return "invincibility:#00FFFF:shield:true:Invincible";
            }

            @Override
            public double modifyDamageReceived(double damage) {
                return 0.0; // No damage taken
            }
        });
    }

    /**
     * Apply spawn invincibility to a player using the default duration.
     * This is the standard spawn protection applied when players join or respawn.
     */
    public static void applySpawnInvincibility(Player player) {
        applyInvincibility(player, GameConstants.SPAWN_INVINCIBILITY_DURATION, "spawn_protection");
    }

    /**
     * Check if a player is currently invincible.
     */
    public static boolean isInvincible(Player player) {
        return player.getAttributeModifications().stream()
                .anyMatch(am -> "invincible".equals(am.uniqueKey()));
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
        applyEffect(player, new BaseAttributeModification((long) (System.currentTimeMillis() + (durationSeconds * 1000))) {
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
                player.getBody().setLinearDamping(0);
            }

            @Override
            public void revert(Player player) {
                player.getBody().setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
            }

            @Override
            public double modifyDamageReceived(double damage) {
                return damage * 2; // take double damage
            }
        });
    }

    /**
     * Apply infinite ammo effect to a player.
     * During this effect, the player's magazine never depletes when firing.
     */
    public static void applyInfiniteAmmo(Player player, double durationSeconds, String source) {
        applyEffect(player, new BaseAttributeModification(System.currentTimeMillis() + (long) (durationSeconds * 1000)) {
            @Override
            public String uniqueKey() {
                return "infiniteAmmo";
            }

            @Override
            public String renderHint() {
                return "infinite_ammo:#FFA500:star:true:Infinite Ammo:{\"count\":8,\"radius\":26,\"size\":4}";
            }

            @Override
            public Weapon update(Weapon weapon) {
                return new Weapon(weapon) {
                    @Override
                    public int getCurrentAmmo() {
                        // Always return full magazine
                        return getMagazineSize();
                    }
                };
            }
        });
    }

    /**
     * Apply burning effect to a player.
     */
    public static void applyBurning(GameManager gameManager, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
        applyHealthDegeneration(gameManager, "fire:#FF4500:flame:true:Burning:{\"count\":12,\"radius\":22,\"height\":10}", player, damagePerSecond, durationSeconds, effectOwner);
    }

    /**
     * Apply poison effect to a player.
     */
    public static void applyPoison(GameManager gameManager, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
        applyHealthDegeneration(gameManager, "poison:#8BC34A:cloud:true:Poison", player, damagePerSecond, durationSeconds, effectOwner);
    }

    /**
     * Apply health degeneration (poison/bleed) effect to a player.
     */
    private static void applyHealthDegeneration(GameManager gameManager, String renderHint, Player player, double damagePerSecond, double durationSeconds, int effectOwner) {
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

    /**
     * Apply ball carrier effect to a player (for Oddball mode).
     * Ball carriers cannot fire weapons but score points over time.
     */
    public static void applyBallCarrier(Player player) {
        applyEffect(player, new BaseAttributeModification(Long.MAX_VALUE) { // No expiration - removed when ball is dropped
            @Override
            public String uniqueKey() {
                return "ballCarrier";
            }

            @Override
            public String renderHint() {
                return "oddball_carrier:#FFD700:star:true:Ball Carrier";
            }

            @Override
            public Weapon update(Weapon weapon) {
                // Return a disabled weapon that can't fire by setting fire rate to 0
                return new Weapon(weapon) {
                    @Override
                    public double getFireRate() {
                        return 0.0; // Cannot fire (no shots per second)
                    }

                    @Override
                    public double getDamage() {
                        return 0.0; // No damage even if somehow fired
                    }

                    @Override
                    public int getCurrentAmmo() {
                        // Return magazine size so AI doesn't think it needs to reload
                        return getMagazineSize();
                    }
                };
            }

            @Override
            public void update(Player player, double delta) {
                player.setLastShotTime(Long.MAX_VALUE);
                super.update(player, delta);
            }

            @Override
            public void revert(Player player) {
                player.setLastShotTime(0L);
                super.revert(player);
            }
        });
    }

    /**
     * Remove ball carrier effect from a player.
     */
    public static void removeBallCarrier(Player player) {
        player.getAttributeModifications().removeIf(am -> "ballCarrier".equals(am.uniqueKey()));
    }

    /**
     * Check if a player is carrying the ball.
     */
    public static boolean isBallCarrier(Player player) {
        return player.getAttributeModifications().stream()
                .anyMatch(am -> "ballCarrier".equals(am.uniqueKey()));
    }

    /**
     * Apply VIP status to a player (for VIP mode).
     * VIP players are high-value targets - only their kills count towards objective scoring.
     */
    public static void applyVipStatus(Player player) {
        applyEffect(player, new BaseAttributeModification(Long.MAX_VALUE) { // No expiration - removed when VIP status changes
            @Override
            public String uniqueKey() {
                return "vipStatus";
            }

            @Override
            public String renderHint() {
                return "vip_crown:#FFD700:crown:true:VIP";
            }
        });
    }

    /**
     * Remove VIP status from a player.
     */
    public static void removeVipStatus(Player player) {
        player.getAttributeModifications().removeIf(am -> "vipStatus".equals(am.uniqueKey()));
    }

    /**
     * Check if a player has VIP status.
     */
    public static boolean isVip(Player player) {
        return player.getAttributeModifications().stream()
                .anyMatch(am -> "vipStatus".equals(am.uniqueKey()));
    }
}


package com.fullsteam.physics;

import com.fullsteam.games.GameManager;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import org.dyn4j.geometry.Vector2;

/**
 * Handles physical melee interactions delivered by MeleeAttacker entities (e.g. Zombies)
 * against Damageable targets (Players, Headquarters, Turrets).
 */
public class MeleeImpactHandler {

    private final GameManager gameManager;
    private final GameEntities gameEntities;

    public MeleeImpactHandler(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
    }

    /**
     * Handle physical contact between a MeleeAttacker and a Damageable victim.
     */
    public boolean handleMeleeDamageableImpact(MeleeAttacker attacker, Damageable victim) {
        if (!attacker.isActive() || attacker.getHealth() <= 0 || !victim.isActive() || victim.getHealth() <= 0) {
            return true;
        }

        if (attacker.canMeleeAttack()) {
            double meleeDamage = attacker.getMeleeDamage();
            int attackerId = -attacker.getId(); // Negative ID denotes hostile NPC attacker

            if (victim instanceof Player player) {
                Vector2 attackerPos = attacker instanceof GameEntity ge ? ge.getPosition() : player.getPosition();
                Vector2 attackDir = player.getPosition().subtract(attackerPos);
                if (attackDir.getMagnitude() < 1e-4) {
                    attackDir = player.getAimDirection().copy().negate();
                }

                if (CollisionProcessor.isBlockedByRiotShield(player, attackDir)) {
                    player.damageRiotShield(meleeDamage);
                    gameEntities.recordDamageHit(player.getPosition().x, player.getPosition().y, meleeDamage, attackerId, player.getId(), false, true);
                } else {
                    boolean killed = player.takeDamage(meleeDamage);
                    boolean armorMitigated = player.isLastDamageArmorMitigated();
                    if (killed) {
                        gameManager.killPlayer(player, attackerId);
                    }
                    gameEntities.recordDamageHit(player.getPosition().x, player.getPosition().y, meleeDamage, attackerId, player.getId(), killed, armorMitigated);
                }
            } else if (victim instanceof Headquarters hq) {
                boolean destroyed = hq.takeDamage(meleeDamage);
                gameEntities.recordDamageHit(hq.getPosition().x, hq.getPosition().y, meleeDamage, attackerId, hq.getId(), destroyed);
                if (destroyed) {
                    gameManager.handleHeadquartersDamage(hq, null, meleeDamage, true);
                }
            } else if (victim instanceof Turret turret) {
                boolean destroyed = turret.takeDamage(meleeDamage);
                gameEntities.recordDamageHit(turret.getPosition().x, turret.getPosition().y, meleeDamage, attackerId, turret.getId(), destroyed);
                if (destroyed) {
                    createTurretDestructionExplosion(turret);
                }
            }

            attacker.recordMeleeAttack();
        }
        return true;
    }

    private void createTurretDestructionExplosion(Turret turret) {
        double radius = turret.getBody().getFixture(0).getShape().getRadius();
        gameEntities.add(new FieldEffectCircle(
                turret.getOwnerId(),
                FieldEffectType.EXPLOSION,
                turret.getPosition(),
                radius,
                radius,
                0.0,
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                0,
                turret.getOwnerTeam()
        ));
    }
}

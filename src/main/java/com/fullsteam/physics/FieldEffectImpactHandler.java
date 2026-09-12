package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import org.dyn4j.geometry.Vector2;

import java.util.Optional;

import static com.fullsteam.Config.GRAVITY_WELL_CONSTANT;

/**
 * Handles physical and spatial overlaps of FieldEffects (beams, zones, explosions, hazards)
 * with Damageable entities and projectiles.
 */
public class FieldEffectImpactHandler {

    private final GameManager gameManager;
    private final GameEntities gameEntities;

    public FieldEffectImpactHandler(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
    }

    /**
     * Handle FieldEffect overlapping any Damageable entity.
     */
    public void handleFieldEffectDamageableOverlap(FieldEffect fieldEffect, Damageable victim) {
        if (!fieldEffect.isArmed() || !victim.isActive() || victim.getHealth() <= 0) {
            return;
        }

        if (victim instanceof Player player) {
            handlePlayerFieldEffect(fieldEffect, player);
        } else if (victim instanceof Turret turret) {
            handleTurretFieldEffect(fieldEffect, turret);
        } else if (victim instanceof Headquarters hq) {
            handleHeadquartersFieldEffect(fieldEffect, hq);
        } else if (victim instanceof Zombie zombie) {
            handleZombieFieldEffect(fieldEffect, zombie);
        } else if (victim instanceof Oddball oddball) {
            handleOddballFieldEffect(fieldEffect, oddball);
        }
    }

    /**
     * Handle FieldEffect intersecting a Projectile (e.g. Shield barrier blocking, Gravity well pull).
     */
    public boolean handleFieldEffectProjectileOverlap(FieldEffect fieldEffect, Projectile projectile) {
        switch (fieldEffect.getType()) {
            case SHIELD_BARRIER -> {
                if (projectile.getBulletEffects().contains(BulletEffect.PIERCING)) {
                    return true;
                }
                Vector2 projectilePos = projectile.getInitialPosition();
                Vector2 shieldCenter = fieldEffect.getPosition();
                if (shieldCenter.distance(projectilePos) > fieldEffect.getRadius()) {
                    projectile.setActive(false);
                }
            }
            case GRAVITY_WELL -> {
                Vector2 delta = fieldEffect.getPosition().subtract(projectile.getPosition());
                double distance = delta.getMagnitudeSquared();
                if (distance > 10) {
                    double distanceSq = Math.max(distance, 100.0);
                    double force = GRAVITY_WELL_CONSTANT * 0.07 / distanceSq;
                    projectile.getBody().applyForce(delta.getNormalized().multiply(force));
                }
            }
        }
        return true;
    }

    private void handlePlayerFieldEffect(FieldEffect fieldEffect, Player player) {
        if (!fieldEffect.canAffect(player)) {
            return;
        }

        Vector2 attackDir;
        if (fieldEffect instanceof FieldEffectBeam beam) {
            attackDir = beam.getDirection();
        } else {
            attackDir = player.getPosition().subtract(fieldEffect.getPosition());
            if (attackDir.getMagnitude() < 1e-4) {
                attackDir = player.getAimDirection().copy().negate();
            }
        }

        boolean isPiercing = fieldEffect instanceof FieldEffectBeam beam && beam.getBulletEffects().contains(BulletEffect.PIERCING);
        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        // Riot shield blocking check for damaging effects
        if (isDamagingEffect(fieldEffect.getType()) && !isPiercing && CollisionProcessor.isBlockedByRiotShield(player, attackDir)) {
            if (isInstantEffect(fieldEffect.getType())) {
                if (!fieldEffect.getAffectedEntities().contains(player.getId())) {
                    fieldEffect.markAsAffected(player);
                    double dmg = fieldEffect.getDamage();
                    player.damageRiotShield(dmg);
                    gameEntities.recordDamageHit(player.getPosition().x, player.getPosition().y, dmg, fieldEffect.getOwnerId(), player.getId(), false, true);
                }
            } else {
                double dmg = fieldEffect.getDamage() * deltaTime;
                player.damageRiotShield(dmg);
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, dmg, fieldEffect.getOwnerId(), player.getId(), false, true);
            }
            return;
        }

        switch (fieldEffect.getType()) {
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().contains(player.getId())) {
                    fieldEffect.markAsAffected(player);
                    double damage = fieldEffect.getDamage();
                    boolean killed = player.takeDamage(damage, isPiercing);
                    boolean armorMitigated = player.isLastDamageArmorMitigated();
                    if (killed) {
                        gameManager.killPlayer(player, fieldEffect.getOwnerId());
                    }
                    gameEntities.recordDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
                }
            }
            case PLASMA, FIRE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage, isPiercing);
                boolean armorMitigated = player.isLastDamageArmorMitigated();
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
            }
            case ELECTRIC -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage, isPiercing);
                boolean armorMitigated = player.isLastDamageArmorMitigated();
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 2.0, 0.5,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Electric Field"));
            }
            case FREEZE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage, isPiercing);
                boolean armorMitigated = player.isLastDamageArmorMitigated();
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 3.0, 1.0,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Freeze Field"));
            }
            case POISON -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage, isPiercing);
                boolean armorMitigated = player.isLastDamageArmorMitigated();
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
                StatusEffectManager.applyPoison(gameManager, player, fieldEffect.getDamage() * 0.2, 1.5, fieldEffect.getOwnerId());
            }
            case EARTHQUAKE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage, isPiercing);
                boolean armorMitigated = player.isLastDamageArmorMitigated();
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameEntities.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed, armorMitigated);
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 1.5, 0.7, "Earthquake");
            }
            case HEAL_ZONE -> {
                double healAmount = fieldEffect.getDamage() * deltaTime;
                player.heal(healAmount);
            }
            case SLOW_FIELD -> {
                String source = Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Slow Field");
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 4.0, 1.0, source);
            }
            case SHIELD_BARRIER -> {
                // Players pass through barrier
            }
            case GRAVITY_WELL -> {
                Vector2 delta = fieldEffect.getPosition().subtract(player.getPosition());
                double distance = delta.getMagnitudeSquared();
                if (distance > 0) {
                    double distanceSq = Math.max(distance, 100.0);
                    player.getBody().applyForce(delta.getNormalized().multiply(GRAVITY_WELL_CONSTANT / distanceSq));
                }
            }
            case SPEED_BOOST ->
                    StatusEffectManager.applySpeedBoost(player, Config.PLAYER_LINEAR_DAMPING * 0.25, 2.0, String.valueOf(fieldEffect.getOwnerId()));
            case SMOKE -> player.setVisionObscured(true);
            case PROXIMITY_MINE -> {
                fieldEffect.setActive(false);
                double radius = 80.0;
                gameEntities.add(new FieldEffectCircle(
                        fieldEffect.getOwnerId(),
                        FieldEffectType.EXPLOSION,
                        fieldEffect.getPosition(),
                        radius,
                        radius,
                        60.0,
                        FieldEffectType.EXPLOSION.getDefaultDuration(),
                        0,
                        fieldEffect.getOwnerTeam()
                ));
            }
        }
    }

    private void handleTurretFieldEffect(FieldEffect fieldEffect, Turret turret) {
        if (!fieldEffect.canAffect(turret) || fieldEffect.isFriendy(turret)) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().contains(turret.getId())) {
                    double damage = fieldEffect.getDamage();
                    boolean destroyed = turret.takeDamage(damage);
                    if (destroyed) {
                        createTurretDestructionExplosion(turret);
                    }
                    gameEntities.recordDamageHit(turret.getPosition().x, turret.getPosition().y, damage, fieldEffect.getOwnerId(), turret.getId(), destroyed);
                }
            }
            case FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE, PLASMA -> {
                if (fieldEffect.getDamage() > 0) {
                    double frameDamage = fieldEffect.getDamage() * deltaTime;
                    boolean destroyed = turret.takeDamage(frameDamage);
                    if (destroyed) {
                        createTurretDestructionExplosion(turret);
                    }
                    gameEntities.recordDotDamageHit(turret.getPosition().x, turret.getPosition().y, frameDamage, fieldEffect.getOwnerId(), turret.getId(), destroyed);
                }
            }
            default -> {
            }
        }
    }

    private void handleHeadquartersFieldEffect(FieldEffect fieldEffect, Headquarters hq) {
        if (fieldEffect.isFriendy(hq)) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            case LASER -> {
                if (!fieldEffect.getAffectedEntities().contains(hq.getId())) {
                    double damageDealt = fieldEffect.getDamage();
                    boolean destroyed = hq.takeDamage(damageDealt);
                    fieldEffect.markAsAffected(hq);
                    Player attacker = gameEntities.getPlayer(fieldEffect.getOwnerId());
                    if (attacker != null) {
                        gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, destroyed);
                    }
                    gameEntities.recordDamageHit(hq.getPosition().x, hq.getPosition().y, damageDealt, fieldEffect.getOwnerId(), hq.getId(), destroyed);
                }
            }
            case PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE, EXPLOSION, FRAGMENTATION -> {
                double damageDealt = fieldEffect.getDamage() * deltaTime;
                boolean destroyed = hq.takeDamage(damageDealt);
                Player attacker = gameEntities.getPlayer(fieldEffect.getOwnerId());
                if (attacker != null) {
                    gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, destroyed);
                }
                gameEntities.recordDotDamageHit(hq.getPosition().x, hq.getPosition().y, damageDealt, fieldEffect.getOwnerId(), hq.getId(), destroyed);
            }
            default -> {
            }
        }
    }

    private void handleZombieFieldEffect(FieldEffect fieldEffect, Zombie zombie) {
        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();
        int attackerId = fieldEffect.getOwnerId();
        Player attacker = gameEntities.getPlayer(attackerId);

        switch (fieldEffect.getType()) {
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (fieldEffect.getAffectedEntities().add(zombie.getId())) {
                    double damage = fieldEffect.getDamage();
                    boolean killed = zombie.takeDamage(damage);
                    if (killed) {
                        zombie.onDeath(gameEntities);
                    }
                    if (killed && attacker != null && attacker.isActive()) {
                        attacker.getScoring().addZombieKill();
                    }
                    gameEntities.recordDamageHit(zombie.getPosition().x, zombie.getPosition().y, damage, attackerId, zombie.getId(), killed);
                }
            }
            case PLASMA, FIRE, POISON, EARTHQUAKE -> {
                if (fieldEffect.getDamage() > 0) {
                    double damage = fieldEffect.getDamage() * deltaTime;
                    boolean killed = zombie.takeDamage(damage);
                    if (killed) {
                        zombie.onDeath(gameEntities);
                    }
                    if (killed && attacker != null && attacker.isActive()) {
                        attacker.getScoring().addZombieKill();
                    }
                    gameEntities.recordDotDamageHit(zombie.getPosition().x, zombie.getPosition().y, damage, attackerId, zombie.getId(), killed);
                }
            }
            case ELECTRIC, FREEZE -> {
                if (fieldEffect.getDamage() > 0) {
                    double damage = fieldEffect.getDamage() * deltaTime;
                    boolean killed = zombie.takeDamage(damage);
                    if (killed) {
                        zombie.onDeath(gameEntities);
                    }
                    if (killed && attacker != null && attacker.isActive()) {
                        attacker.getScoring().addZombieKill();
                    }
                    gameEntities.recordDotDamageHit(zombie.getPosition().x, zombie.getPosition().y, damage, attackerId, zombie.getId(), killed);
                    Vector2 vel = zombie.getVelocity();
                    zombie.getBody().applyForce(vel.multiply(-Config.PLAYER_LINEAR_DAMPING * 1.5));
                }
            }
            default -> {
            }
        }
    }

    private void handleOddballFieldEffect(FieldEffect fieldEffect, Oddball npc) {
        if (fieldEffect.getOwnerId() <= 0) {
            return;
        }

        Player attacker = gameEntities.getPlayer(fieldEffect.getOwnerId());
        if (attacker == null || !attacker.isActive()) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().add(npc.getId())) {
                    return;
                }
                double damage = fieldEffect.getDamage();
                double points = damage
                        * npc.getPointsMultiplier()
                        * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
                attacker.getScoring().addOddball(points);
                gameEntities.recordDamageHit(npc.getPosition().x, npc.getPosition().y, damage, fieldEffect.getOwnerId(), npc.getId(), false);
            }
            case PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE -> {
                if (fieldEffect.getDamage() > 0) {
                    double frameDamage = fieldEffect.getDamage() * deltaTime;
                    double points = frameDamage
                            * npc.getPointsMultiplier()
                            * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
                    attacker.getScoring().addOddball(points);
                    gameEntities.recordDotDamageHit(npc.getPosition().x, npc.getPosition().y, frameDamage, fieldEffect.getOwnerId(), npc.getId(), false);
                }
            }
            default -> {
            }
        }
    }

    private static boolean isDamagingEffect(FieldEffectType type) {
        return switch (type) {
            case EXPLOSION, FRAGMENTATION, LASER, PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE -> true;
            default -> false;
        };
    }

    private static boolean isInstantEffect(FieldEffectType type) {
        return switch (type) {
            case EXPLOSION, FRAGMENTATION, LASER -> true;
            default -> false;
        };
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

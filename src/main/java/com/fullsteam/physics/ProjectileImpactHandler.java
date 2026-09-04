package com.fullsteam.physics;

import com.fullsteam.games.GameManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import org.dyn4j.geometry.Vector2;

/**
 * Handles physical impacts involving Projectiles against damageable entities,
 * obstacles, and other projectiles.
 */
public class ProjectileImpactHandler {

    private final GameManager gameManager;
    private final GameEntities gameEntities;
    private final BulletEffectProcessor bulletEffectProcessor;

    public ProjectileImpactHandler(GameManager gameManager,
                                   GameEntities gameEntities,
                                   BulletEffectProcessor bulletEffectProcessor) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
        this.bulletEffectProcessor = bulletEffectProcessor;
    }

    /**
     * Handle projectile hitting any Damageable entity.
     * Returns true if dyn4j physics should resolve, false if projectile passes through or is absorbed.
     */
    public boolean handleProjectileDamageableImpact(Projectile projectile, Damageable victim) {
        if (!victim.isActive() || victim.getHealth() <= 0) {
            return true;
        }

        // 1. Team-awareness / damage eligibility check
        if (!canProjectileDamage(projectile, victim)) {
            return false; // Friendly fire or invalid target: pass through
        }

        // 2. Multi-hit prevention for piercing projectiles
        if (!trackAffectedTarget(projectile, victim)) {
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, (GameEntity) victim);
            return !shouldPierce;
        }

        Vector2 hitPosition = projectile.getPosition();
        boolean isArmorPiercing = projectile.getBulletEffects().contains(BulletEffect.PIERCING);

        // 3. Riot shield frontal block (Player victim only)
        if (victim instanceof Player player) {
            if (!isArmorPiercing && CollisionProcessor.isBlockedByRiotShield(player, projectile.getBody().getLinearVelocity())) {
                bulletEffectProcessor.processEffectHit(projectile, hitPosition);
                projectile.markAsExploded();
                projectile.setActive(false);
                double dmg = projectile.getDamage();
                player.damageRiotShield(dmg);
                gameEntities.recordDamageHit(hitPosition.x, hitPosition.y, dmg, projectile.getOwnerId(), player.getId(), false, true);
                return false;
            }
        }

        // 4. Trigger bullet effects (explosions, status fields, etc.)
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // 5. Knockback application
        double knockback = projectile.getKnockback();
        if (knockback > 0 && victim instanceof GameEntity gameEntity) {
            Vector2 dir = projectile.getBody().getLinearVelocity().getNormalized();
            gameEntity.getBody().applyImpulse(dir.multiply(knockback));
        }

        // 6. Apply damage and process scoring
        double damage = projectile.getDamage();
        boolean killed = false;
        boolean armorMitigated = false;

        if (victim instanceof Player player) {
            killed = player.takeDamage(damage, isArmorPiercing);
            armorMitigated = player.isLastDamageArmorMitigated();
            if (killed) {
                gameManager.killPlayer(player, projectile.getOwnerId());
            }
        } else if (victim instanceof Turret turret) {
            killed = turret.takeDamage(damage);
            if (killed) {
                createTurretDestructionExplosion(turret);
            }
        } else if (victim instanceof Headquarters hq) {
            killed = hq.takeDamage(damage);
            Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
            if (attacker != null) {
                gameManager.handleHeadquartersDamage(hq, attacker, damage, killed);
            }
        } else if (victim instanceof Zombie zombie) {
            killed = zombie.takeDamage(damage);
            Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
            if (killed && attacker != null && attacker.isActive()) {
                attacker.getScoring().addZombieKill();
            }
        } else if (victim instanceof Oddball oddball) {
            // Oddball NPC awards score proportional to damage but does not lose health
            Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
            if (attacker != null && attacker.isActive()) {
                double points = damage
                        * oddball.getPointsMultiplier()
                        * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
                attacker.getScoring().addOddball(points);
            }
            projectile.setActive(false);
            gameEntities.recordDamageHit(hitPosition.x, hitPosition.y, damage, projectile.getOwnerId(), oddball.getId(), false);
            return false;
        }

        // 7. Record damage hit for client telemetry
        gameEntities.recordDamageHit(hitPosition.x, hitPosition.y, damage, projectile.getOwnerId(), victim.getId(), killed, armorMitigated);

        // 8. Pierce vs deactivation
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, (GameEntity) victim);
        if (!shouldPierce) {
            projectile.setActive(false);
            return false;
        }
        return true;
    }

    /**
     * Handle projectile colliding with an obstacle.
     */
    public boolean handleProjectileObstacleImpact(Projectile projectile, Obstacle obstacle) {
        if (!projectile.getAffectedObstacles().add(obstacle.getId())) {
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, obstacle);
            return !shouldPierce;
        }

        Vector2 hitPosition = projectile.getBody().getTransform().getTranslation().copy();
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        boolean shouldBounce = bulletEffectProcessor.shouldBounceOffObstacle(projectile, obstacle);
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, obstacle);

        if (shouldPierce) {
            return false;
        } else if (shouldBounce) {
            return true;
        } else {
            projectile.setActive(false);
            return false;
        }
    }

    /**
     * Handle two projectiles colliding.
     */
    public boolean handleProjectileProjectileImpact(Projectile p1, Projectile p2) {
        return p1.getBulletEffects().contains(BulletEffect.BOUNCY) || p2.getBulletEffects().contains(BulletEffect.BOUNCY);
    }

    private boolean canProjectileDamage(Projectile projectile, Damageable victim) {
        if (victim instanceof Player player) {
            return projectile.canDamage(player);
        } else if (victim instanceof Turret turret) {
            if (projectile.getOwnerId() == turret.getOwnerId()) return false;
            if (projectile.getOwnerTeam() == 0 || turret.getOwnerTeam() == 0) return true;
            return projectile.getOwnerTeam() != turret.getOwnerTeam();
        } else if (victim instanceof Headquarters hq) {
            return projectile.getOwnerTeam() != hq.getOwnerTeam();
        } else if (victim instanceof Zombie) {
            return true; // Zombies are hostile to all
        } else if (victim instanceof Oddball) {
            return projectile.getOwnerId() > 0; // Player-fired shots score on Oddballs
        }
        return true;
    }

    private boolean trackAffectedTarget(Projectile projectile, Damageable victim) {
        if (victim instanceof Player player) {
            return projectile.getAffectedPlayers().add(player.getId());
        }
        return projectile.getAffectedObstacles().add(victim.getId());
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

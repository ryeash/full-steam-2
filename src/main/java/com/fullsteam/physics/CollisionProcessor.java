package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.StatusEffects;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.contact.Contact;
import org.dyn4j.dynamics.contact.SolvedContact;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ContactCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;
import org.dyn4j.world.listener.ContactListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


public class CollisionProcessor implements CollisionListener<Body, BodyFixture>, ContactListener<Body> {

    private static final Logger log = LoggerFactory.getLogger(CollisionProcessor.class);

    private final GameManager gameManager;
    private final GameEntities gameEntities;
    @Getter
    private final BulletEffectProcessor bulletEffectProcessor;

    public CollisionProcessor(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
        this.bulletEffectProcessor = new BulletEffectProcessor(gameEntities);
    }

    @Override
    public boolean collision(BroadphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(NarrowphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData<Body, BodyFixture> collision) {
        Body body1 = collision.getBody1();
        Body body2 = collision.getBody2();

        Object userData1 = body1.getUserData();
        Object userData2 = body2.getUserData();

        // Check for projectile hitting a world boundary
        if (userData1 instanceof Projectile && "boundary".equals(userData2)) {
            ((Projectile) userData1).setActive(false);
            return false; // Prevent bounce
        }
        if (userData2 instanceof Projectile && "boundary".equals(userData1)) {
            ((Projectile) userData2).setActive(false);
            return false; // Prevent bounce
        }

        // Check for entity-entity collisions
        if (userData1 instanceof GameEntity && userData2 instanceof GameEntity) {
            return handleEntityCollision((GameEntity) userData1, (GameEntity) userData2);
        }

        // For any other collision types, let physics handle them normally
        return true;
    }

    private boolean handleEntityCollision(GameEntity entity1, GameEntity entity2) {
        // let the bouncy bullets interact with bullets
        if (entity1 instanceof Projectile p1 && entity2 instanceof Projectile p2) {
            return p1.getBulletEffects().contains(BulletEffect.BOUNCY) || p2.getBulletEffects().contains(BulletEffect.BOUNCY);// Disable collision resolution between projectiles
        }

        if (entity1 instanceof Player player && entity2 instanceof Projectile projectile) {
            handlePlayerProjectileCollision(player, projectile);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Player player) {
            handlePlayerProjectileCollision(player, projectile);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Obstacle obstacle) {
            return handleProjectileObstacleCollision(projectile, obstacle);
        } else if (entity1 instanceof Obstacle obstacle && entity2 instanceof Projectile projectile) {
            return handleProjectileObstacleCollision(projectile, obstacle);
        } else if (entity1 instanceof Player player && entity2 instanceof FieldEffect fieldEffect) {
            handlePlayerFieldEffectCollision(player, fieldEffect);
            return true; // Allow physics to handle overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof FieldEffect fieldEffect && entity2 instanceof Player player) {
            handlePlayerFieldEffectCollision(player, fieldEffect);
            return true; // Allow physics to handle overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof FieldEffect fieldEffect && entity2 instanceof Projectile projectile) {
            return handleProjectileFieldEffectCollision(projectile, fieldEffect);
        } else if (entity2 instanceof FieldEffect fieldEffect && entity1 instanceof Projectile projectile) {
            return handleProjectileFieldEffectCollision(projectile, fieldEffect);
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Turret turret) {
            return handleProjectileTurretCollision(projectile, turret);
        } else if (entity1 instanceof Turret turret && entity2 instanceof Projectile projectile) {
            return handleProjectileTurretCollision(projectile, turret);
        } else if (entity1 instanceof NetProjectile net) {
            return handleNetCollision(net, entity2);
        } else if (entity2 instanceof NetProjectile net) {
            return handleNetCollision(net, entity1);
        }
        return true;
    }

    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
        if (!player.isActive() || !projectile.isActive()) {
            return;
        }

        // if the player has already been hit with this projectile, skip them
        // particularly important for piercing projectiles
        if (!projectile.getAffectedPlayers().add(player.getId())) {
            return;
        }

        // Use the projectile's team-aware damage logic
        if (!projectile.canDamage(player)) {
            return; // Can't damage self or teammates
        }

        // Process bullet effects before handling the hit
        bulletEffectProcessor.processEffectHit(projectile, player.getPosition());

        if (player.takeDamage(projectile.getDamage())) {
            Player killer = gameEntities.getPlayer(projectile.getOwnerId());
            gameManager.killPlayer(player, killer);
        }

        // Check if projectile should pierce through the target
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, player);
        // Deactivate projectile unless it pierces
        if (!shouldPierce) {
            projectile.setActive(false);
        }
    }

    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
        if (!projectile.isActive()) {
            return true;
        }

        // Get hit position for effects
        org.dyn4j.geometry.Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on obstacle hit
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // Check if projectile should bounce
        boolean shouldBounce = bulletEffectProcessor.shouldBounceOffObstacle(projectile, obstacle);

        // Check if projectile should pierce through obstacles
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, obstacle);

        if (shouldPierce) {
            return false; // Don't resolve collision, let projectile pass through
        } else if (shouldBounce) {
            return true; // Let physics handle the bounce
        } else {
            projectile.setActive(false);
            return false; // Stop the projectile
        }
    }

    private void handlePlayerFieldEffectCollision(Player player, FieldEffect fieldEffect) {
        if (!player.isActive()
            || !fieldEffect.isActive()
            || !fieldEffect.canAffect(player)) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            // instant damage
            case EXPLOSION, FRAGMENTATION -> {
                double damage = fieldEffect.getDamageAtPosition(player.getPosition());
                if (damage > 0) {
                    // Instant damage - only apply once per effect
                    if (!fieldEffect.getAffectedEntities().contains(player.getId())) {
                        boolean playerKilled = player.takeDamage(damage);
                        fieldEffect.markAsAffected(player);

                        if (playerKilled) {
                            gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                        }
                    }
                }
            }
            case FIRE -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                StatusEffects.applyBurning(gameManager, player, effectValue * 0.3, 1.0, fieldEffect.getOwnerId());

            }
            case ELECTRIC -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                StatusEffects.applySlowEffect(player, 5, 0.5,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Electric Field"));

            }
            case FREEZE -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                StatusEffects.applySlowEffect(player, 5, 1.0,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Freeze Field"));

            }
            case POISON -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                StatusEffects.applyPoison(gameManager, player, effectValue * 0.2, 1.5, fieldEffect.getOwnerId());
            }
            case HEAL_ZONE -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                double healAmount = effectValue * deltaTime;
                player.setHealth(Math.min(gameManager.getGameConfig().getPlayerMaxHealth(), player.getHealth() + healAmount));
            }
            case SMOKE_CLOUD -> {
                // TODO: fix vision
            }
            case SLOW_FIELD -> {
                StatusEffects.applySlowEffect(player, 10, 1.0,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Slow Field"));
            }
            case SHIELD_BARRIER -> {
            }
            case GRAVITY_WELL -> {
                Vector2 forceDirection = fieldEffect.getPosition()
                        .subtract(player.getPosition())
                        .getNormalized()
                        .multiply(200000.0); // TODO: why does this have to be so high to actually affect players?
                if (!forceDirection.isZero()) {
                    player.getBody().applyForce(forceDirection);
                }
            }
            case SPEED_BOOST -> {
                StatusEffects.applySpeedBoost(player, 0, 2.0, String.valueOf(fieldEffect.getOwnerId()));
            }
            case PROXIMITY_MINE -> {
                gameEntities.addPostUpdateHook(() -> {
                    fieldEffect.setActive(false);
                    gameEntities.removeFieldEffect(fieldEffect.getId());
                    gameEntities.getWorld().removeBody(fieldEffect.getBody());
                    FieldEffect explosion = new FieldEffect(
                            Config.nextId(), // Offset ID to avoid conflicts
                            fieldEffect.getOwnerId(),
                            FieldEffectType.EXPLOSION,
                            fieldEffect.getPosition(),
                            80.0,
                            60.0,
                            FieldEffectType.EXPLOSION.getDefaultDuration(),
                            fieldEffect.getOwnerTeam()
                    );
                    gameEntities.addFieldEffect(explosion);
                    gameEntities.getWorld().addBody(explosion.getBody());
                });
            }
        }
    }

    private boolean handleProjectileFieldEffectCollision(Projectile projectile, FieldEffect fieldEffect) {
        if (fieldEffect.getType() == FieldEffectType.SHIELD_BARRIER) {
            // Only stop projectiles that are moving toward the shield center
            // This allows players inside the shield to fire outward
            Vector2 projectilePos = projectile.getPosition();
            Vector2 shieldCenter = fieldEffect.getPosition();
            Vector2 projectileVelocity = projectile.getBody().getLinearVelocity();

            // Calculate direction from projectile to shield center
            Vector2 toShieldCenter = shieldCenter.copy().subtract(projectilePos);

            // Check if projectile is moving toward the shield center
            // Use dot product: if positive, projectile is moving toward center
            if (toShieldCenter.getMagnitude() > 0 && projectileVelocity.getMagnitude() > 0) {
                toShieldCenter.normalize();
                Vector2 velocityDirection = projectileVelocity.copy();
                velocityDirection.normalize();

                double dotProduct = toShieldCenter.dot(velocityDirection);

                // Only stop projectile if it's moving toward the shield center (dot product > 0)
                if (dotProduct > 0) {
                    projectile.setVelocity(Vector2.create(0, 0));
                    return false; // Prevent physics resolution
                }
            }

            // Projectile is moving away from shield center, let it pass through
            return true;
        } else if (fieldEffect.getType() == FieldEffectType.GRAVITY_WELL) {
            Vector2 forceDirection = fieldEffect.getPosition()
                    .subtract(projectile.getPosition())
                    .getNormalized()
                    .multiply(10000.0);
            projectile.getBody().applyForce(forceDirection);
        }
        return true;
    }

    private boolean handleProjectileTurretCollision(Projectile projectile, Turret turret) {
        if (!projectile.isActive() || !turret.isActive()) {
            return true;
        }

        // Check if projectile can damage the turret (team rules)
        if (!canProjectileDamageTurret(projectile, turret)) {
            return false; // Let projectile pass through
        }

        // Get hit position for effects
        org.dyn4j.geometry.Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on turret hit
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // Apply damage to turret
        boolean turretDestroyed = turret.takeDamage(projectile.getDamage());

        if (turretDestroyed) {
            // Turret was destroyed, could trigger explosion or other effects
            log.debug("Turret {} destroyed by projectile from player {}",
                    turret.getId(), projectile.getOwnerId());
        }

        // Check if projectile should pierce through the turret
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, turret);

        // Deactivate projectile unless it pierces
        if (!shouldPierce) {
            projectile.setActive(false);
            return false; // Stop the projectile
        }

        return true; // Let projectile continue if piercing
    }

    private boolean canProjectileDamageTurret(Projectile projectile, Turret turret) {
        // Can't damage own turret
        if (projectile.getOwnerId() == turret.getOwnerId()) {
            return false;
        }

        // In FFA mode (team 0), can damage any turret except own
        if (projectile.getOwnerTeam() == 0 || turret.getOwnerTeam() == 0) {
            return true;
        }

        // In team mode, can only damage turrets on different teams
        return projectile.getOwnerTeam() != turret.getOwnerTeam();
    }

    private boolean handleNetCollision(NetProjectile net, GameEntity entity) {
        if (entity instanceof Player player) {
            if (net.isActive() && net.canAffectPlayer(player)) {
                net.hitPlayer(player);
                return false;
            }
            return true;
        } else if (entity instanceof Obstacle) {
            net.setActive(false);
            return false;
        } else {
            return !(entity instanceof Projectile);
        }
    }

    // ContactListener methods

    @Override
    public void begin(ContactCollisionData<Body> collision, Contact contact) {
    }

    @Override
    public void persist(ContactCollisionData<Body> collision, Contact oldContact, Contact newContact) {

    }

    @Override
    public void end(ContactCollisionData<Body> collision, Contact contact) {
    }

    @Override
    public void destroyed(ContactCollisionData<Body> collision, Contact contact) {

    }

    @Override
    public void collision(ContactCollisionData<Body> collision) {

    }

    @Override
    public void preSolve(ContactCollisionData<Body> collision, Contact contact) {

    }

    @Override
    public void postSolve(ContactCollisionData<Body> collision, SolvedContact contact) {

    }
}

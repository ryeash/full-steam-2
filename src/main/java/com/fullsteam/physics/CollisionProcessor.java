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
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


public class CollisionProcessor implements CollisionListener<Body, BodyFixture> {

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
        if ((userData1 instanceof Projectile || userData1 instanceof NetProjectile) && "boundary".equals(userData2)) {
            ((GameEntity) userData1).setActive(false);
            return false; // Prevent bounce
        }
        if ((userData2 instanceof Projectile || userData2 instanceof NetProjectile) && "boundary".equals(userData1)) {
            ((GameEntity) userData2).setActive(false);
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
        // Early exit: ignore collisions involving inactive/dead players
        if (entity1 instanceof Player player1 && !player1.isActive()) {
            return false; // Don't process collisions for dead players
        }
        if (entity2 instanceof Player player2 && !player2.isActive()) {
            return false; // Don't process collisions for dead players
        }

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
        } else if (entity1 instanceof Player player && entity2 instanceof Flag flag) {
            handlePlayerFlagCollision(player, flag);
            return true; // Flags are sensors, no physics resolution
        } else if (entity1 instanceof Flag flag && entity2 instanceof Player player) {
            handlePlayerFlagCollision(player, flag);
            return true; // Flags are sensors, no physics resolution
        } else if (entity1 instanceof Player player && entity2 instanceof KothZone zone) {
            handlePlayerKothZoneCollision(player, zone);
            return true; // KOTH zones are sensors, no physics resolution
        } else if (entity1 instanceof KothZone zone && entity2 instanceof Player player) {
            handlePlayerKothZoneCollision(player, zone);
            return true; // KOTH zones are sensors, no physics resolution
        } else if (entity1 instanceof Player player && entity2 instanceof Workshop workshop) {
            handlePlayerWorkshopCollision(player, workshop);
            return true; // Workshops are sensors, no physics resolution
        } else if (entity1 instanceof Workshop workshop && entity2 instanceof Player player) {
            handlePlayerWorkshopCollision(player, workshop);
            return true; // Workshops are sensors, no physics resolution
        } else if (entity1 instanceof Player player && entity2 instanceof PowerUp powerUp) {
            handlePlayerPowerUpCollision(player, powerUp);
            return true; // Power-ups are sensors, no physics resolution
        } else if (entity1 instanceof PowerUp powerUp && entity2 instanceof Player player) {
            handlePlayerPowerUpCollision(player, powerUp);
            return true; // Power-ups are sensors, no physics resolution
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Headquarters hq) {
            return handleProjectileHeadquartersCollision(projectile, hq);
        } else if (entity1 instanceof Headquarters hq && entity2 instanceof Projectile projectile) {
            return handleProjectileHeadquartersCollision(projectile, hq);
        } else if (entity1 instanceof Beam beam && entity2 instanceof Headquarters hq) {
            handleBeamHeadquartersCollision(beam, hq);
            return true; // Beams continue through structures
        } else if (entity1 instanceof Headquarters hq && entity2 instanceof Beam beam) {
            handleBeamHeadquartersCollision(beam, hq);
            return true; // Beams continue through structures
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
        log.debug("Projectile {} hit player {} at ({}, {}) - processing effects",
                projectile.getId(), player.getId(), player.getPosition().x, player.getPosition().y);
        bulletEffectProcessor.processEffectHit(projectile, player.getPosition());

        // Apply direct status effects from bullet on hit
        applyDirectBulletEffects(player, projectile);

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

    /**
     * Apply direct status effects from bullet hits (burn, freeze, poison).
     * These are applied immediately on hit, separate from area effects.
     */
    private void applyDirectBulletEffects(Player player, Projectile projectile) {
        Set<BulletEffect> effects = projectile.getBulletEffects();
        
        // Incendiary bullets apply burn status directly
        if (effects.contains(BulletEffect.INCENDIARY)) {
            double burnDamage = projectile.getDamage() * 0.15; // 15% of projectile damage per second
            double burnDuration = 3.0; // 3 seconds of burning
            StatusEffects.applyBurning(gameManager, player, burnDamage, burnDuration, projectile.getOwnerId());
            log.debug("Applied burn status to player {} from incendiary projectile ({}dps for {}s)",
                    player.getId(), burnDamage, burnDuration);
        }
        
        // Freezing bullets apply slow status directly
        if (effects.contains(BulletEffect.FREEZING)) {
            double slowAmount = 0.5; // 50% speed reduction
            double slowDuration = 2.0; // 2 seconds
            Player shooter = gameEntities.getPlayer(projectile.getOwnerId());
            String source = shooter != null ? shooter.getPlayerName() : "Freezing Projectile";
            StatusEffects.applySlowEffect(player, slowAmount, slowDuration, source);
            log.debug("Applied freeze slow to player {} from freezing projectile", player.getId());
        }
        
        // Poison bullets apply poison status directly
        if (effects.contains(BulletEffect.POISON)) {
            double poisonDamage = projectile.getDamage() * 0.1; // 10% of projectile damage per second
            double poisonDuration = 4.0; // 4 seconds of poison
            StatusEffects.applyPoison(gameManager, player, poisonDamage, poisonDuration, projectile.getOwnerId());
            log.debug("Applied poison status to player {} from poison projectile ({}dps for {}s)",
                    player.getId(), poisonDamage, poisonDuration);
        }
        
        // Electric bullets apply brief slow from shock
        if (effects.contains(BulletEffect.ELECTRIC)) {
            double slowAmount = 0.3; // 30% speed reduction
            double slowDuration = 1.0; // 1 second shock
            Player shooter = gameEntities.getPlayer(projectile.getOwnerId());
            String source = shooter != null ? shooter.getPlayerName() : "Electric Projectile";
            StatusEffects.applySlowEffect(player, slowAmount, slowDuration, source);
            log.debug("Applied electric shock slow to player {} from electric projectile", player.getId());
        }
    }

    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
        if (!projectile.isActive()) {
            return true;
        }

        // Check if this projectile has already hit this obstacle
        // This prevents piercing bullets from triggering effects multiple times on the same obstacle
        if (!projectile.getAffectedObstacles().add(obstacle.getId())) {
            // Already hit this obstacle, skip effect processing
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, obstacle);
            return !shouldPierce; // Don't resolve collision if piercing
        }

        // Get hit position for effects
        Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on obstacle hit (only on first hit)
        log.debug("Projectile {} hit obstacle {} at ({}, {}) - processing effects",
                projectile.getId(), obstacle.getId(), hitPosition.x, hitPosition.y);
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // Apply damage to player-created obstacles (barriers)
        if (obstacle.getType() == Obstacle.ObstacleType.PLAYER_BARRIER) {
            // Check if projectile can damage this obstacle (team rules)
            if (canProjectileDamageObstacle(projectile, obstacle)) {
                boolean obstacleDestroyed = obstacle.takeDamage(projectile.getDamage());
                if (obstacleDestroyed) {
                    log.debug("Projectile {} destroyed player barrier {} (owner: {})",
                            projectile.getId(), obstacle.getId(), obstacle.getOwnerId());
                }
            }
        }

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

        // Check if this projectile has already hit this turret
        // This prevents piercing bullets from triggering effects multiple times on the same turret
        if (!projectile.getAffectedObstacles().add(turret.getId())) {
            // Already hit this turret, skip effect processing
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, turret);
            return !shouldPierce; // Don't resolve collision if piercing
        }

        // Check if projectile can damage the turret (team rules)
        if (!canProjectileDamageTurret(projectile, turret)) {
            return false; // Let projectile pass through
        }

        // Get hit position for effects
        Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on turret hit (only on first hit)
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // Apply damage to turret
        boolean turretDestroyed = turret.takeDamage(projectile.getDamage());

        if (turretDestroyed) {
            // Create zero-damage explosion field effect
            FieldEffect explosion = new FieldEffect(
                    Config.nextId(),
                    turret.getOwnerId(), // Use turret owner for attribution
                    FieldEffectType.EXPLOSION,
                    turret.getPosition(),
                    turret.getBody().getFixture(0).getShape().getRadius(), // Same size as turret,
                    0.0, // Zero damage - purely visual
                    FieldEffectType.EXPLOSION.getDefaultDuration(),
                    turret.getOwnerTeam()
            );
            gameEntities.getWorld().addBody(explosion.getBody());
            gameEntities.addFieldEffect(explosion);
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
        } else if (entity instanceof FieldEffect fe) {
            if (fe.getType() == FieldEffectType.SHIELD_BARRIER) {
                net.setActive(false);
                return false;
            }
            return true;
        } else {
            return !(entity instanceof Projectile);
        }
    }

    /**
     * Handle player touching a flag - pickup or capture logic.
     */
    private void handlePlayerFlagCollision(Player player, Flag flag) {
        if (!player.isActive()) {
            return;
        }

        int playerTeam = player.getTeam();
        int flagTeam = flag.getOwnerTeam();

        // Check if player is already carrying a flag
        boolean alreadyCarrying = gameEntities.getAllFlags().stream()
                .anyMatch(f -> f.isCarried() && f.getCarriedByPlayerId() == player.getId());

        if (alreadyCarrying) {
            // Player is already carrying a flag, check if they're in their own base for capture
            if (playerTeam == flagTeam && flag.isAtHome()) {
                // Player is in their own base with enemy flag - CAPTURE!
                captureFlag(player, flag);
            }
            return;
        }

        // Try to pick up the flag
        if (flag.canBeCapturedBy(playerTeam)) {
            pickUpFlag(player, flag);
        } else if (playerTeam == flagTeam && !flag.isAtHome()) {
            // Player touched their own flag that's not at home - return it
            returnFlag(flag);
        }
    }

    /**
     * Player picks up an enemy flag.
     */
    private void pickUpFlag(Player player, Flag flag) {
        flag.pickUp(player.getId());

        log.info("Player {} (team {}) picked up flag {} (team {})",
                player.getId(), player.getTeam(), flag.getId(), flag.getOwnerTeam());

        // Broadcast pickup event
        gameManager.broadcastGameEvent(
                String.format("%s picked up %s flag!",
                        player.getPlayerName(),
                        getTeamName(flag.getOwnerTeam())),
                "FLAG_PICKUP",
                "#ffaa00"
        );
    }

    /**
     * Player captures a flag (brings enemy flag to own base).
     */
    private void captureFlag(Player player, Flag homeFlag) {
        // Find the flag the player is carrying
        Optional<Flag> carriedFlagOpt = gameEntities.getAllFlags().stream()
                .filter(f -> f.isCarried() && f.getCarriedByPlayerId() == player.getId())
                .findFirst();

        if (carriedFlagOpt.isEmpty()) {
            return;
        }

        Flag carriedFlag = carriedFlagOpt.get();
        carriedFlag.capture();

        // Award points to player
        gameManager.awardCapture(player, carriedFlag.getOwnerTeam());

        log.info("Player {} (team {}) captured flag {} (team {})!",
                player.getId(), player.getTeam(), carriedFlag.getId(), carriedFlag.getOwnerTeam());

        // Broadcast capture event
        gameManager.broadcastGameEvent(
                String.format("%s captured the %s flag! +1 CAPTURE",
                        player.getPlayerName(),
                        getTeamName(carriedFlag.getOwnerTeam())),
                "FLAG_CAPTURE",
                "#00ff00"
        );
    }

    /**
     * Return a flag to its home position.
     */
    private void returnFlag(Flag flag) {
        flag.returnToHome();

        log.info("Flag {} (team {}) returned to home", flag.getId(), flag.getOwnerTeam());

        // Broadcast return event
        gameManager.broadcastGameEvent(
                String.format("%s flag returned!", getTeamName(flag.getOwnerTeam())),
                "FLAG_RETURN",
                "#4444ff"
        );
    }

    /**
     * Get team name for display.
     */
    private String getTeamName(int team) {
        return "Team " + team;
    }

    /**
     * Handle player entering/staying in a KOTH zone.
     * Tracks player presence for zone control calculations.
     */
    private void handlePlayerKothZoneCollision(Player player, KothZone zone) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return;
        }
        zone.addPlayer(player);
    }

    /**
     * Handle player entering/staying near a workshop.
     * Triggers crafting mechanics when player is within craft radius.
     */
    void handlePlayerWorkshopCollision(Player player, Workshop workshop) {
        if (!workshop.isActive() || !player.isActive()) {
            return;
        }
        workshop.addPlayer(player);
        boolean completed = workshop.incrementProgress(player, gameEntities.getWorld().getTimeStep().getDeltaTime());
        if (completed) {
            spawnPowerUpForPlayer(workshop, player);
            workshop.removePlayer(player);
        }
    }

    /**
     * Handle player collecting a power-up.
     * Applies the power-up effect to the player and removes the power-up.
     */
    void handlePlayerPowerUpCollision(Player player, PowerUp powerUp) {
        if (!player.isActive() || player.getHealth() <= 0 || !powerUp.isActive()) {
            return;
        }

        // Check if power-up can be collected by this player
        if (powerUp.canBeCollectedBy(player)) {
            // Apply the power-up effect
            PowerUp.PowerUpEffect effect = powerUp.getEffect();
            applyPowerUpEffect(player, effect);

            // Remove the power-up from the game
            powerUp.setActive(false);
            gameEntities.removePowerUp(powerUp.getId());
            gameEntities.getWorld().removeBody(powerUp.getBody());

            log.info("Player {} collected power-up {} (type: {})",
                    player.getId(), powerUp.getId(), powerUp.getType());
        }
    }

    /**
     * Handle projectile hitting a headquarters.
     */
    private boolean handleProjectileHeadquartersCollision(Projectile projectile, Headquarters hq) {
        if (!projectile.isActive() || !hq.isActive()) {
            return true;
        }

        // Check if this projectile has already hit this HQ
        if (!projectile.getAffectedObstacles().add(hq.getId())) {
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, hq);
            return !shouldPierce;
        }

        // Check if projectile can damage this headquarters (team rules)
        if (!canProjectileDamageHeadquarters(projectile, hq)) {
            return false; // Friendly fire protection - let projectile pass through
        }

        // Get hit position for effects
        Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on HQ hit
        bulletEffectProcessor.processEffectHit(projectile, hitPosition);

        // Apply damage and score points
        double damageDealt = projectile.getDamage();
        boolean hqDestroyed = hq.takeDamage(damageDealt);

        // Award points to the attacking team
        Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
        if (attacker != null) {
            gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, hqDestroyed);
        }

        // Check if projectile should pierce
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, hq);
        if (!shouldPierce) {
            projectile.setActive(false);
            return false;
        }

        return true;
    }

    /**
     * Handle beam hitting a headquarters.
     */
    private void handleBeamHeadquartersCollision(Beam beam, Headquarters hq) {
        if (!beam.isActive() || !hq.isActive()) {
            return;
        }

        // Check if beam can damage this headquarters
        if (!canBeamDamageHeadquarters(beam, hq)) {
            return; // Friendly fire protection
        }

        // Apply damage (beams deal damage continuously)
        double damageDealt = beam.getDamage();
        boolean hqDestroyed = hq.takeDamage(damageDealt);

        // Award points to the attacking team
        Player attacker = gameEntities.getPlayer(beam.getOwnerId());
        if (attacker != null) {
            gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, hqDestroyed);
        }
    }

    /**
     * Check if a projectile can damage a headquarters (team protection).
     */
    private boolean canProjectileDamageHeadquarters(Projectile projectile, Headquarters hq) {
        // Can't damage own team's headquarters
        return projectile.getOwnerTeam() != hq.getTeamNumber();
    }

    /**
     * Check if a beam can damage a headquarters (team protection).
     */
    private boolean canBeamDamageHeadquarters(Beam beam, Headquarters hq) {
        // Can't damage own team's headquarters
        return beam.getOwnerTeam() != hq.getTeamNumber();
    }

    /**
     * Update all KOTH zones - called once per physics step with proper deltaTime.
     * This ensures scoring is frame-rate independent.
     */
    public void updateKothZones(double deltaTime) {
        for (KothZone zone : gameEntities.getAllKothZones()) {
            if (zone.shouldAwardPoints()) {
                double points = zone.getPointsPerSecond() * deltaTime;
                if (points > 0) {
                    zone.awardPointsToTeam(zone.getControllingTeam(), points);
                }
            }
            zone.clearPlayers();
        }
    }

    public void updateWorkshops(double deltaTime) {
        for (Workshop workshop : gameEntities.getAllWorkshops()) {
            if (!workshop.isActive()) {
                continue;
            }
            // all present players should have been resolved
            // remove progress for players who are not preset
            Set<Integer> presentIds = workshop.getPresentPlayers().stream().map(GameEntity::getId).collect(Collectors.toSet());
            workshop.getPlayerProgress().keySet().removeIf(id -> !presentIds.contains(id));
            // then clear the present players so the set can be re-calculated next world step
            workshop.getPresentPlayers().clear();
        }
    }

    /**
     * Check if a projectile can damage an obstacle based on team rules.
     * Projectiles cannot damage obstacles created by teammates.
     */
    private boolean canProjectileDamageObstacle(Projectile projectile, Obstacle obstacle) {
        // Can't damage obstacles created by the same player
        if (projectile.getOwnerId() == obstacle.getOwnerId()) {
            return false;
        }

        // In FFA mode (team 0), can damage any obstacle except own
        if (projectile.getOwnerTeam() == 0 || obstacle.getOwnerTeam() == 0) {
            return true;
        }

        // In team mode, can only damage obstacles created by different teams
        return projectile.getOwnerTeam() != obstacle.getOwnerTeam();
    }

    /**
     * Spawn a power-up for a player at a workshop.
     */
    private void spawnPowerUpForPlayer(Workshop workshop, Player player) {
        // Check if workshop has reached max power-ups
        if (gameEntities.getPowerUpsForWorkshop(workshop.getId()).size() >= workshop.getMaxPowerUps()) {
            return; // Workshop is full
        }

        // Randomly select a power-up type
        PowerUp.PowerUpType[] powerUpTypes = PowerUp.PowerUpType.values();
        PowerUp.PowerUpType selectedType = powerUpTypes[ThreadLocalRandom.current().nextInt(powerUpTypes.length)];

        // Calculate spawn position around the workshop
        Vector2 workshopPos = workshop.getPosition();
        double spawnRadius = 40.0 + ThreadLocalRandom.current().nextDouble(20.0); // 40-60 units from workshop
        double spawnAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);

        Vector2 spawnPos = new Vector2(
                workshopPos.x + Math.cos(spawnAngle) * spawnRadius,
                workshopPos.y + Math.sin(spawnAngle) * spawnRadius
        );

        // Create the power-up
        PowerUp powerUp = new PowerUp(
                Config.nextId(),
                spawnPos,
                selectedType,
                workshop.getId(),
                12.0,
                1.0   // Normal effect strength
        );

        // Add to game world
        gameEntities.addPowerUp(powerUp);
        gameEntities.getWorld().addBody(powerUp.getBody());
    }

    /**
     * Apply a power-up effect to a player.
     */
    private void applyPowerUpEffect(Player player, PowerUp.PowerUpEffect effect) {
        switch (effect.getType()) {
            case SPEED_BOOST:
                StatusEffects.applySpeedBoost(player, effect.getStrength(), effect.getDuration(), "Workshop Power-up");
                break;
            case HEALTH_REGENERATION:
                StatusEffects.applyHealthRegeneration(player, effect.getStrength(), effect.getDuration(), "Workshop Power-up");
                break;
            case DAMAGE_BOOST:
                StatusEffects.applyDamageBoost(player, effect.getStrength(), effect.getDuration(), "Workshop Power-up");
                break;
            case DAMAGE_RESISTANCE:
                StatusEffects.applyDamageResistance(player, effect.getStrength(), effect.getDuration(), "Workshop Power-up");
                break;
            case BERSERKER_MODE:
                StatusEffects.applyBerserkerMode(player, effect.getDuration(), "Workshop Power-up");
                break;
        }
    }
}

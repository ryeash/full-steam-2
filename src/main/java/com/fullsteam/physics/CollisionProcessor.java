package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.fullsteam.Config.GRAVITY_WELL_CONSTANT;


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

        if ((userData1 instanceof Projectile || userData1 instanceof NetProjectile) && "boundary".equals(userData2)) {
            ((GameEntity) userData1).setActive(false);
            return false;
        }
        if ((userData2 instanceof Projectile || userData2 instanceof NetProjectile) && "boundary".equals(userData1)) {
            ((GameEntity) userData2).setActive(false);
            return false;
        }
        if (userData1 instanceof GameEntity a && userData2 instanceof GameEntity b) {
            return handleEntityCollision(a, b);
        }
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
            return p1.getBulletEffects().contains(BulletEffect.BOUNCY) || p2.getBulletEffects().contains(BulletEffect.BOUNCY);
        }

        Collision c = new Collision(entity1, entity2);

        if (c.rectify(Player.class, Projectile.class)
                instanceof TypedCollision<Player, Projectile>(Player a, Projectile b)) {
            handlePlayerProjectileCollision(a, b);
            return false;

        } else if ((entity1 instanceof Projectile || entity1 instanceof Beam) && entity2 instanceof Workshop) {
            return false; // Projectiles and beams pass through workshops (they're sensors)
        } else if (entity1 instanceof Workshop && (entity2 instanceof Projectile || entity2 instanceof Beam)) {
            return false; // Projectiles and beams pass through workshops (they're sensors)

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

        } else if (c.rectify(Projectile.class, Turret.class)
                instanceof TypedCollision<Projectile, Turret>(Projectile a, Turret b)) {
            return handleProjectileTurretCollision(a, b);

        } else if (c.rectify(Projectile.class, DefenseLaser.class) != null) {
            return false;

        } else if (c.rectify(Beam.class, Turret.class)
                instanceof TypedCollision<Beam, Turret>(Beam a, Turret b)) {
            handleBeamTurretCollision(a, b);
            return true;

        } else if (c.rectify(Turret.class, FieldEffect.class)
                instanceof TypedCollision<Turret, FieldEffect>(Turret a, FieldEffect b)) {
            handleTurretFieldEffectCollision(a, b);
            return true;

        } else if (c.rectify(NetProjectile.class, GameEntity.class)
                instanceof TypedCollision<NetProjectile, GameEntity>(NetProjectile a, GameEntity b)) {
            return handleNetCollision(a, b);

        } else if (c.rectify(Player.class, Flag.class)
                instanceof TypedCollision<Player, Flag>(Player a, Flag b)) {
            return handlePlayerFlagCollision(a, b);

        } else if (c.rectify(Player.class, KothZone.class)
                instanceof TypedCollision<Player, KothZone>(Player a, KothZone b)) {
            return handlePlayerKothZoneCollision(a, b);

        } else if (c.rectify(Player.class, Workshop.class)
                instanceof TypedCollision<Player, Workshop>(Player a, Workshop b)) {
            return handlePlayerWorkshopCollision(a, b);

        } else if (c.rectify(Player.class, PowerUp.class)
                instanceof TypedCollision<Player, PowerUp>(Player a, PowerUp b)) {
            return handlePlayerPowerUpCollision(a, b);

        } else if (c.rectify(Projectile.class, Headquarters.class)
                instanceof TypedCollision<Projectile, Headquarters>(Projectile a, Headquarters b)) {
            return handleProjectileHeadquartersCollision(a, b);

        } else if (c.rectify(Beam.class, Headquarters.class)
                instanceof TypedCollision<Beam, Headquarters>(Beam a, Headquarters b)) {
            return handleBeamHeadquartersCollision(a, b);

        } else if (c.rectify(Projectile.class, Oddball.class)
                instanceof TypedCollision<Projectile, Oddball>(Projectile a, Oddball b)) {
            return handleProjectileOddballCollision(a, b);

        } else if (c.rectify(Beam.class, Oddball.class)
                instanceof TypedCollision<Beam, Oddball>(Beam a, Oddball b)) {
            handleBeamOddballNpcCollision(a, b);
            return false; // beam passes through; NPC is invincible
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

        // Apply direct status effects from bullet on hit
        applyDirectBulletEffects(player, projectile);

        // Knockback: shove the victim along the projectile's travel direction
        // (like NetProjectile's pushback). Applied before the damage call so a
        // killing shot still imparts its impulse.
        double knockback = projectile.getKnockback();
        if (knockback > 0) {
            Vector2 dir = projectile.getBody().getLinearVelocity().getNormalized();
            player.getBody().applyImpulse(dir.multiply(knockback));
        }

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
            StatusEffectManager.applyBurning(gameManager, player, burnDamage, burnDuration, projectile.getOwnerId());
        }

        // Freezing bullets apply slow status directly
        if (effects.contains(BulletEffect.FREEZING)) {
            double slowAmount = 0.5; // 50% speed reduction
            double slowDuration = 2.0; // 2 seconds
            Player shooter = gameEntities.getPlayer(projectile.getOwnerId());
            String source = shooter != null ? shooter.getPlayerName() : "Freezing Projectile";
            StatusEffectManager.applySlowEffect(player, slowAmount, slowDuration, source);
        }

        // Poison bullets apply poison status directly
        if (effects.contains(BulletEffect.POISON)) {
            double poisonDamage = projectile.getDamage() * 0.1; // 10% of projectile damage per second
            double poisonDuration = 4.0; // 4 seconds of poison
            StatusEffectManager.applyPoison(gameManager, player, poisonDamage, poisonDuration, projectile.getOwnerId());
        }

        // Electric bullets apply brief slow from shock
        if (effects.contains(BulletEffect.ELECTRIC)) {
            double slowAmount = 0.3; // 30% speed reduction
            double slowDuration = 1.0; // 1 second shock
            Player shooter = gameEntities.getPlayer(projectile.getOwnerId());
            String source = shooter != null ? shooter.getPlayerName() : "Electric Projectile";
            StatusEffectManager.applySlowEffect(player, slowAmount, slowDuration, source);
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
        if (!player.isActive() || !fieldEffect.isActive() || !fieldEffect.canAffect(player)) {
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
                StatusEffectManager.applyBurning(gameManager, player, effectValue * 0.3, 1.0, fieldEffect.getOwnerId());

            }
            case ELECTRIC -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                StatusEffectManager.applySlowEffect(player, 5, 0.5,
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
                StatusEffectManager.applySlowEffect(player, 5, 1.0,
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
                StatusEffectManager.applyPoison(gameManager, player, effectValue * 0.2, 1.5, fieldEffect.getOwnerId());
            }
            case EARTHQUAKE -> {
                double effectValue = fieldEffect.getDamageAtPosition(player.getPosition());
                if (effectValue <= 0) {
                    return;
                }
                // Apply damage over time
                if (player.takeDamage(effectValue * deltaTime)) {
                    gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                }
                // Apply strong slowing effect (ground shaking makes movement difficult)
                StatusEffectManager.applySlowEffect(player, 5, 0.7, "Earthquake");
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
                String source = Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Slow Field");
                StatusEffectManager.applySlowEffect(player, 10, 1.0, source);
            }
            case SHIELD_BARRIER -> {
            }
            case GRAVITY_WELL -> {
                Vector2 delta = fieldEffect.getPosition().subtract(player.getPosition());
                double distance = delta.getMagnitudeSquared();
                if (distance > 0) {
                    double distanceSq = Math.max(distance, 100.0); // clamp: min 10 units
                    player.getBody().applyForce(delta.getNormalized().multiply(GRAVITY_WELL_CONSTANT / distanceSq));
                }
            }
            case SPEED_BOOST ->
                    StatusEffectManager.applySpeedBoost(player, 0, 2.0, String.valueOf(fieldEffect.getOwnerId()));
            case SMOKE -> player.setVisionObscured(true);
            case PROXIMITY_MINE -> {
                fieldEffect.setActive(false);
                FieldEffect explosion = new FieldEffect(
                        fieldEffect.getOwnerId(),
                        FieldEffectType.EXPLOSION,
                        fieldEffect.getPosition(),
                        80.0,
                        60.0,
                        FieldEffectType.EXPLOSION.getDefaultDuration(),
                        fieldEffect.getOwnerTeam()
                );
                gameEntities.add(explosion);
            }
        }
    }

    private boolean handleProjectileFieldEffectCollision(Projectile projectile, FieldEffect fieldEffect) {
        switch (fieldEffect.getType()) {
            case SHIELD_BARRIER -> {
                Vector2 projectilePos = projectile.getInitialPosition();
                Vector2 shieldCenter = fieldEffect.getPosition();
                if (shieldCenter.distance(projectilePos) > fieldEffect.getBody().getRotationDiscRadius()) {
                    projectile.setActive(false);
                }
            }
            case GRAVITY_WELL -> {
                Vector2 delta = fieldEffect.getPosition().subtract(projectile.getPosition());
                double distance = delta.getMagnitudeSquared();
                if (distance > 10) {
                    double distanceSq = Math.max(distance, 100.0); // clamp: min 10 units
                    double force = GRAVITY_WELL_CONSTANT * (0.07) / distanceSq;
                    projectile.getBody().applyForce(delta.getNormalized().multiply(force));
                }
            }
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
            createTurretDestructionExplosion(turret);
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

    /**
     * Handle beam hitting a turret.
     */
    private void handleBeamTurretCollision(Beam beam, Turret turret) {
        if (!beam.isActive() || !turret.isActive() || !beam.canAffectTurret(turret)) {
            return;
        }
        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();
        if (turret.takeDamage(beam.getDamage() * deltaTime)) {
            createTurretDestructionExplosion(turret);
        }
    }

    /**
     * Handle field effect hitting a turret.
     */
    private void handleTurretFieldEffectCollision(Turret turret, FieldEffect fieldEffect) {
        if (!turret.isActive() || !fieldEffect.isActive() || !fieldEffect.canAffect(turret)) {
            return;
        }

        // Check if field effect can damage the turret (team rules)
        if (!canFieldEffectDamageTurret(fieldEffect, turret)) {
            return; // Friendly fire protection
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            // Instant damage effects
            case EXPLOSION, FRAGMENTATION -> {
                double damage = fieldEffect.getDamageAtPosition(turret.getPosition());
                if (damage > 0) {
                    // Instant damage - only apply once per effect
                    if (!fieldEffect.getAffectedEntities().contains(turret.getId())) {
                        boolean turretDestroyed = turret.takeDamage(damage);
                        fieldEffect.markAsAffected(turret);

                        if (turretDestroyed) {
                            createTurretDestructionExplosion(turret);
                        }
                    }
                }
            }
            // Damage over time effects
            case FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE -> {
                double effectValue = fieldEffect.getDamageAtPosition(turret.getPosition());
                if (effectValue > 0) {
                    boolean turretDestroyed = turret.takeDamage(effectValue * deltaTime);
                    if (turretDestroyed) {
                        createTurretDestructionExplosion(turret);
                    }
                }
            }
            // Non-damaging effects that turrets should ignore
            case HEAL_ZONE, SLOW_FIELD, SHIELD_BARRIER, GRAVITY_WELL, SPEED_BOOST, WARNING_ZONE, PROXIMITY_MINE -> {
                // Turrets are not affected by these
            }
        }
    }

    /**
     * Check if a field effect can damage a turret (team protection).
     */
    private boolean canFieldEffectDamageTurret(FieldEffect fieldEffect, Turret turret) {
        // Can't damage own turret
        if (fieldEffect.getOwnerId() == turret.getOwnerId()) {
            return false;
        }

        // In FFA mode (team 0), can damage any turret except own
        if (fieldEffect.getOwnerTeam() == 0 || turret.getOwnerTeam() == 0) {
            return true;
        }

        // In team mode, can only damage turrets on different teams
        return fieldEffect.getOwnerTeam() != turret.getOwnerTeam();
    }

    /**
     * Create a visual explosion effect when a turret is destroyed.
     */
    private void createTurretDestructionExplosion(Turret turret) {
        FieldEffect explosion = new FieldEffect(
                turret.getOwnerId(),
                FieldEffectType.EXPLOSION,
                turret.getPosition(),
                turret.getBody().getFixture(0).getShape().getRadius(),
                0.0, // Zero damage - purely visual
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                turret.getOwnerTeam()
        );
        gameEntities.add(explosion);
    }

    private boolean handleNetCollision(NetProjectile net, GameEntity entity) {
        switch (entity) {
            case Player player -> {
                if (net.isActive() && net.canAffectPlayer(player)) {
                    net.hitPlayer(player);
                    return false;
                }
                return true;
            }
            case Obstacle obstacle -> {
                net.setActive(false);
                return false;
            }
            case FieldEffect fe -> {
                if (fe.getType() == FieldEffectType.SHIELD_BARRIER) {
                    net.setActive(false);
                    return false;
                }
                return true;
            }
            case null, default -> {
                return !(entity instanceof Projectile);
            }
        }
    }

    /**
     * Handle player touching a flag - pickup or capture logic.
     */
    private boolean handlePlayerFlagCollision(Player player, Flag flag) {
        if (!player.isActive()) {
            return true;
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
            return true;
        }

        // Try to pick up the flag
        if (flag.canBeCapturedBy(playerTeam)) {
            pickUpFlag(player, flag);
        } else if (playerTeam == flagTeam && !flag.isAtHome()) {
            // Player touched their own flag that's not at home - return it
            // (doesn't apply to oddball)
            returnFlag(flag);
        }
        return true;
    }

    /**
     * Player picks up an enemy flag.
     */
    private void pickUpFlag(Player player, Flag flag) {
        flag.pickUp(player.getId());

        log.debug("Player {} (team {}) picked up flag {} (team {})",
                player.getId(), player.getTeam(), flag.getId(), flag.getOwnerTeam());

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

        log.debug("Player {} (team {}) captured flag {} (team {})!",
                player.getId(), player.getTeam(), carriedFlag.getId(), carriedFlag.getOwnerTeam());

        // Broadcast capture event. When the rules give a flag more than one point
        // AND the active score style actually awards capture points, surface the
        // actual value so the player sees how much that capture was worth.
        Rules rules = gameManager.getGameConfig().getRules();
        ScoreStyle style = rules.getScoreStyle();
        boolean stylePointsCaptures = style == ScoreStyle.OBJECTIVE || style == ScoreStyle.TOTAL;
        int capturePoints = rules.getPointsPerFlagCapture();
        String captureText = (stylePointsCaptures && capturePoints > 1)
                ? String.format("+1 CAPTURE (+%d pts)", capturePoints)
                : "+1 CAPTURE";
        gameManager.broadcastGameEvent(
                String.format("%s captured the %s flag! %s",
                        player.getPlayerName(),
                        getTeamName(carriedFlag.getOwnerTeam()),
                        captureText),
                "FLAG_CAPTURE",
                "#00ff00"
        );
    }

    /**
     * Return a flag to its home position.
     */
    private void returnFlag(Flag flag) {
        flag.returnToHome();

        log.debug("Flag {} (team {}) returned to home", flag.getId(), flag.getOwnerTeam());

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
    private boolean handlePlayerKothZoneCollision(Player player, KothZone zone) {
        if (player.isActive()) {
            zone.addPlayer(player);
        }
        return true;
    }

    /**
     * Handle player entering/staying near a workshop.
     * Triggers crafting mechanics when player is within craft radius.
     */
    boolean handlePlayerWorkshopCollision(Player player, Workshop workshop) {
        if (!workshop.isActive() || !player.isActive()) {
            return true;
        }
        workshop.addPlayer(player);
        boolean completed = workshop.incrementProgress(player, gameEntities.getWorld().getTimeStep().getDeltaTime());
        if (completed) {
            spawnPowerUpForPlayer(workshop, player);
            workshop.removePlayer(player);
        }
        return true;
    }

    /**
     * Handle player collecting a power-up.
     * Applies the power-up effect to the player and removes the power-up.
     */
    boolean handlePlayerPowerUpCollision(Player player, PowerUp powerUp) {
        if (!player.isActive() || player.getHealth() <= 0 || !powerUp.isActive()) {
            return true;
        }

        // Check if power-up can be collected by this player
        if (powerUp.canBeCollectedBy(player)) {
            PowerUpEffect effect = powerUp.getEffect();
            applyPowerUpEffect(player, effect);
            powerUp.setActive(false);
        }
        return true;
    }

    /**
     * Handle a player's projectile hitting an oddball NPC.
     * Awards oddball points to the attacker; oddball is invincible (never deactivated).
     * NPC-generated projectiles (ownerId < 0) are ignored — they can't score on each other.
     */
    private boolean handleProjectileOddballCollision(Projectile projectile, Oddball npc) {
        if (!projectile.isActive() || !npc.isActive()) return true;

        // Only player-fired projectiles generate score
        if (projectile.getOwnerId() <= 0) return true;

        Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
        if (attacker != null && attacker.isActive()) {
            double points = projectile.getDamage()
                    * npc.getPointsMultiplier()
                    * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
            attacker.getScoring().addOddball(points);
        }

        projectile.setActive(false);
        return false; // Prevent physics resolution; oddball body absorbs the hit
    }

    /**
     * Award instant oddball points when a LASER beam physically intersects an OddballNPC body.
     * PLASMA_BEAM continuous DOT scoring is handled per-tick in the GameManager beam loop instead.
     */
    private void handleBeamOddballNpcCollision(Beam beam, Oddball npc) {
        if (!beam.isActive() || !npc.isActive()) return;
        if (beam.getOwnerId() <= 0) return; // NPC-fired beams don't generate score
        if (beam.getOrdinance() != Ordinance.LASER) return; // PLASMA_BEAM DOT is in GameManager
        if (!beam.getAffectedPlayers().add(npc.getId())) return; // already scored this hit

        Player attacker = gameEntities.getPlayer(beam.getOwnerId());
        if (attacker != null && attacker.isActive()) {
            double points = beam.getDamage()
                    * npc.getPointsMultiplier()
                    * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
            attacker.getScoring().addOddball(points);
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
    private boolean handleBeamHeadquartersCollision(Beam beam, Headquarters hq) {
        if (!beam.isActive() || !hq.isActive() || !canBeamDamageHeadquarters(beam, hq)) {
            return true;
        }

        // Apply damage (beams deal damage continuously)
        double damageDealt = beam.getDamage();
        boolean hqDestroyed = hq.takeDamage(damageDealt);

        // Award points to the attacking team
        Player attacker = gameEntities.getPlayer(beam.getOwnerId());
        if (attacker != null) {
            gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, hqDestroyed);
        }
        return false;
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
                    awardZonePoints(zone, points);
                }
            }
            zone.clearPlayers();
        }
    }

    /**
     * Credit a controlled zone's points to the player(s) who earned them.
     * FFA: the sole controlling player. Team mode: split equally among the living
     * controlling-team players in the zone, so the team total still equals the
     * zone's points-per-second.
     */
    private void awardZonePoints(KothZone zone, double points) {
        if (zone.getControllingPlayerId() >= 0) {
            Player controller = gameEntities.getPlayer(zone.getControllingPlayerId());
            if (controller != null) {
                controller.getScoring().addKingOfTheHillPoints(points);
            }
            return;
        }

        if (zone.getControllingTeam() >= 0) {
            var holders = zone.getPlayersInZone().stream()
                    .filter(p -> p.isActive() && p.getHealth() > 0)
                    .filter(p -> p.getTeam() == zone.getControllingTeam())
                    .toList();
            if (!holders.isEmpty()) {
                double share = points / holders.size();
                for (Player holder : holders) {
                    holder.getScoring().addKingOfTheHillPoints(share);
                }
            }
        }
    }

    /**
     * Update oddball scoring - called once per physics step with proper deltaTime.
     * Awards points to the player currently carrying the oddball.
     */

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
     * Spawn a power-up for a player at a workshop.
     */
    private void spawnPowerUpForPlayer(Workshop workshop, Player player) {
        // Check if workshop has reached max power-ups
        if (gameEntities.getPowerUpsForWorkshop(workshop.getId()).size() >= workshop.getMaxPowerUps()) {
            return; // Workshop is full
        }

        // Randomly select a power-up type
        PowerUpType[] powerUpTypes = PowerUpType.values();
        PowerUpType selectedType = powerUpTypes[ThreadLocalRandom.current().nextInt(powerUpTypes.length)];

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
                Config.nextEntityId(),
                spawnPos,
                selectedType,
                workshop.getId(),
                12.0,
                1.0   // Normal effect strength
        );

        // Add to game world
        gameEntities.add(powerUp);
    }

    /**
     * Apply a power-up effect to a player.
     */
    private void applyPowerUpEffect(Player player, PowerUpEffect effect) {
        switch (effect.type()) {
            case SPEED_BOOST:
                StatusEffectManager.applySpeedBoost(player, effect.strength(), effect.duration(), "Workshop Power-up");
                break;
            case HEALTH_REGENERATION:
                StatusEffectManager.applyHealthRegeneration(player, effect.strength(), effect.duration(), "Workshop Power-up");
                break;
            case DAMAGE_BOOST:
                StatusEffectManager.applyDamageBoost(player, effect.strength(), effect.duration(), "Workshop Power-up");
                break;
            case DAMAGE_RESISTANCE:
                StatusEffectManager.applyDamageResistance(player, effect.strength(), effect.duration(), "Workshop Power-up");
                break;
            case BERSERKER_MODE:
                StatusEffectManager.applyBerserkerMode(player, effect.duration(), "Workshop Power-up");
                break;
            case INFINITE_AMMO:
                StatusEffectManager.applyInfiniteAmmo(player, effect.duration(), "Workshop Power-up");
                break;
        }
    }


    record Collision(Object a, Object b) {
        public <A, B> TypedCollision<A, B> rectify(Class<A> typeA, Class<B> typeB) {
            if (typeA.isInstance(a) && typeB.isInstance(b)) {
                return new TypedCollision<>(typeA.cast(a), typeB.cast(b));
            }
            if (typeA.isInstance(b) && typeB.isInstance(a)) {
                return new TypedCollision<>(typeA.cast(b), typeB.cast(a));
            }
            return null;
        }
    }

    record TypedCollision<A, B>(A a, B b) {
    }
}

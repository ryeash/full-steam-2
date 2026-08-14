package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.GameEvent;
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
            if (a.isActive() && b.isActive()) {
                return handleEntityCollision(a, b);
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean handleEntityCollision(GameEntity entity1, GameEntity entity2) {
        Collision c = new Collision(entity1, entity2);

        if (c.rectify(Projectile.class, Projectile.class)
                instanceof TypedCollision<Projectile, Projectile>(Projectile a, Projectile b)) {
            return handleProjectileProjectileCollision(a, b);
        } else if (c.rectify(Player.class, Projectile.class)
                instanceof TypedCollision<Player, Projectile>(Player a, Projectile b)) {
            handlePlayerProjectileCollision(a, b);
            return false;

        } else if (c.rectify(Projectile.class, Obstacle.class)
                instanceof TypedCollision<Projectile, Obstacle>(Projectile a, Obstacle b)) {
            return handleProjectileObstacleCollision(a, b);

        } else if (c.rectify(Player.class, FieldEffect.class)
                instanceof TypedCollision<Player, FieldEffect>(Player a, FieldEffect b)) {
            handlePlayerFieldEffectCollision(a, b);
            return true; // Allow physics to handle overlaps (sensors should not resolve anyway)

        } else if (c.rectify(Projectile.class, FieldEffect.class)
                instanceof TypedCollision<Projectile, FieldEffect>(Projectile a, FieldEffect b)) {
            return handleProjectileFieldEffectCollision(a, b);

        } else if (c.rectify(Projectile.class, Turret.class)
                instanceof TypedCollision<Projectile, Turret>(Projectile a, Turret b)) {
            return handleProjectileTurretCollision(a, b);

        } else if (c.rectify(Projectile.class, DefenseLaser.class) != null) {
            // DefenseLaser is invincible to projectiles
            return false;

        } else if (c.rectify(Turret.class, FieldEffect.class)
                instanceof TypedCollision<Turret, FieldEffect>(Turret a, FieldEffect b)) {
            handleTurretFieldEffectCollision(a, b);
            return true;

        } else if (c.rectify(FieldEffect.class, Headquarters.class)
                instanceof TypedCollision<FieldEffect, Headquarters>(FieldEffect a, Headquarters b)) {
            handleFieldEffectHeadquartersCollision(a, b);
            return true;

        } else if (c.rectify(FieldEffect.class, Oddball.class)
                instanceof TypedCollision<FieldEffect, Oddball>(FieldEffect a, Oddball b)) {
            handleFieldEffectOddballCollision(a, b);
            return false; // beam passes through; NPC is invincible

        } else if (c.rectify(NetProjectile.class, GameEntity.class)
                instanceof TypedCollision<NetProjectile, GameEntity>(NetProjectile a, GameEntity b)) {
            return handleNetCollision(a, b);

        } else if (c.rectify(Player.class, Flag.class)
                instanceof TypedCollision<Player, Flag>(Player a, Flag b)) {
            return handlePlayerFlagCollision(a, b);

        } else if (c.rectify(Player.class, KothZone.class)
                instanceof TypedCollision<Player, KothZone>(Player a, KothZone b)) {
            return handlePlayerKothZoneCollision(a, b);

        } else if (c.rectify(Projectile.class, Headquarters.class)
                instanceof TypedCollision<Projectile, Headquarters>(Projectile a, Headquarters b)) {
            return handleProjectileHeadquartersCollision(a, b);

        } else if (c.rectify(Projectile.class, Oddball.class)
                instanceof TypedCollision<Projectile, Oddball>(Projectile a, Oddball b)) {
            return handleProjectileOddballCollision(a, b);
        }
        return true;
    }

    private boolean handleProjectileProjectileCollision(Projectile projectile1, Projectile projectile2) {
        // let the bouncy bullets interact with bullets
        return projectile1.getBulletEffects().contains(BulletEffect.BOUNCY) || projectile2.getBulletEffects().contains(BulletEffect.BOUNCY);
    }

    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
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

        // Knockback: shove the victim along the projectile's travel direction
        // (like NetProjectile's pushback). Applied before the damage call so a
        // killing shot still imparts its impulse.
        double knockback = projectile.getKnockback();
        if (knockback > 0) {
            Vector2 dir = projectile.getBody().getLinearVelocity().getNormalized();
            player.getBody().applyImpulse(dir.multiply(knockback));
        }

        double damage = projectile.getDamage();
        boolean killed = player.takeDamage(damage);
        if (killed) {
            gameManager.killPlayer(player, projectile.getOwnerId());
        }

        Vector2 hitPos = projectile.getPosition();
        gameManager.recordDamageHit(hitPos.x, hitPos.y, damage, projectile.getOwnerId(), player.getId(), killed);

        // Check if projectile should pierce through the target
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, player);
        // Deactivate projectile unless it pierces
        if (!shouldPierce) {
            projectile.setActive(false);
        }
    }

    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
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
        if (!fieldEffect.canAffect(player)) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            // Instant damage - only apply once per effect
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().contains(player.getId())) {
                    fieldEffect.markAsAffected(player);
                    double damage = fieldEffect.getDamage();
                    boolean killed = player.takeDamage(damage);
                    if (killed) {
                        gameManager.killPlayer(player, fieldEffect.getOwnerId());
                    }
                    gameManager.recordDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
                }
            }
            case PLASMA, FIRE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage);
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameManager.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
            }
            case ELECTRIC -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage);
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameManager.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 2.0, 0.5,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Electric Field"));
            }
            case FREEZE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage);
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameManager.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 3.0, 1.0,
                        Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Freeze Field"));
            }
            case POISON -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage);
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameManager.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
                StatusEffectManager.applyPoison(gameManager, player, fieldEffect.getDamage() * 0.2, 1.5, fieldEffect.getOwnerId());
            }
            case EARTHQUAKE -> {
                double damage = fieldEffect.getDamage() * deltaTime;
                boolean killed = player.takeDamage(damage);
                if (killed) {
                    gameManager.killPlayer(player, fieldEffect.getOwnerId());
                }
                gameManager.recordDotDamageHit(player.getPosition().x, player.getPosition().y, damage, fieldEffect.getOwnerId(), player.getId(), killed);
                // Apply slowing effect (ground shaking makes movement difficult)
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 1.5, 0.7, "Earthquake");
            }
            case HEAL_ZONE -> {
                double healAmount = fieldEffect.getDamage() * deltaTime;
                player.takeDamage(-healAmount);
            }
            case SLOW_FIELD -> {
                String source = Optional.ofNullable(gameEntities.getPlayer(fieldEffect.getOwnerId())).map(Player::getPlayerName).orElse("Slow Field");
                // big slowdown for the dedicated utility weapon
                StatusEffectManager.applySlowEffect(player, Config.PLAYER_LINEAR_DAMPING * 4.0, 1.0, source);
            }
            case SHIELD_BARRIER -> {
                // players pass through without issue
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

    private boolean handleProjectileFieldEffectCollision(Projectile projectile, FieldEffect fieldEffect) {
        switch (fieldEffect.getType()) {
            case SHIELD_BARRIER -> {
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
                    double distanceSq = Math.max(distance, 100.0); // clamp: min 10 units
                    double force = GRAVITY_WELL_CONSTANT * (0.07) / distanceSq;
                    projectile.getBody().applyForce(delta.getNormalized().multiply(force));
                }
            }
        }
        return true;
    }

    private boolean handleProjectileTurretCollision(Projectile projectile, Turret turret) {
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
        double damage = projectile.getDamage();
        boolean turretDestroyed = turret.takeDamage(damage);
        if (turretDestroyed) {
            createTurretDestructionExplosion(turret);
        }
        gameManager.recordDamageHit(hitPosition.x, hitPosition.y, damage, projectile.getOwnerId(), turret.getId(), turretDestroyed);

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
     * Handle field effect hitting a turret.
     */
    private void handleTurretFieldEffectCollision(Turret turret, FieldEffect fieldEffect) {
        if (!fieldEffect.canAffect(turret)) {
            return;
        }

        // Check if field effect can damage the turret (team rules)
        if (fieldEffect.isFriendy(turret)) {
            return; // Friendly fire protection
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            // Instant damage effects
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().contains(turret.getId())) {
                    double damage = fieldEffect.getDamage();
                    boolean destroyed = turret.takeDamage(damage);
                    if (destroyed) {
                        createTurretDestructionExplosion(turret);
                    }
                    gameManager.recordDamageHit(turret.getPosition().x, turret.getPosition().y, damage, fieldEffect.getOwnerId(), turret.getId(), destroyed);
                }
            }
            // Damage over time effects
            case FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE, PLASMA -> {
                if (fieldEffect.getDamage() > 0) {
                    double frameDamage = fieldEffect.getDamage() * deltaTime;
                    boolean destroyed = turret.takeDamage(frameDamage);
                    if (destroyed) {
                        createTurretDestructionExplosion(turret);
                    }
                    gameManager.recordDotDamageHit(turret.getPosition().x, turret.getPosition().y, frameDamage, fieldEffect.getOwnerId(), turret.getId(), destroyed);
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
    private boolean canFieldEffectDamage(FieldEffect fieldEffect, OwnedGameEntity turret) {
        // Can't damage own entity
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
        double radius = turret.getBody().getFixture(0).getShape().getRadius();
        gameEntities.add(new FieldEffectCircle(
                turret.getOwnerId(),
                FieldEffectType.EXPLOSION,
                turret.getPosition(),
                radius,
                radius,
                0.0, // Zero damage - purely visual
                FieldEffectType.EXPLOSION.getDefaultDuration(),
                0,
                turret.getOwnerTeam()
        ));
    }

    private boolean handleNetCollision(NetProjectile net, GameEntity entity) {
        switch (entity) {
            case Player player -> {
                if (net.canAffectPlayer(player)) {
                    net.hitPlayer(player);
                    return false;
                }
                return true;
            }
            case Obstacle _ -> {
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
        int playerTeam = player.getTeam();
        int flagTeam = flag.getOwnerTeam();

        // Check if player is already carrying a flag
        boolean alreadyCarrying = gameEntities.getAllFlags()
                .stream()
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
                GameEvent.EventCategory.INFO,
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
        gameManager.awardCapture(player);

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
                GameEvent.EventCategory.CAPTURE,
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
                GameEvent.EventCategory.INFO,
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
        zone.addPlayer(player);
        return true;
    }

    /**
     * Handle a player's projectile hitting an oddball NPC.
     * Awards oddball points to the attacker; oddball is invincible (never deactivated).
     * NPC-generated projectiles (ownerId < 0) are ignored — they can't score on each other.
     */
    private boolean handleProjectileOddballCollision(Projectile projectile, Oddball npc) {
        // Only player-fired projectiles generate score
        if (projectile.getOwnerId() <= 0) {
            return true;
        }

        Player attacker = gameEntities.getPlayer(projectile.getOwnerId());
        if (attacker != null && attacker.isActive()) {
            double damage = projectile.getDamage();
            double points = damage
                    * npc.getPointsMultiplier()
                    * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
            attacker.getScoring().addOddball(points);
            Vector2 hitPos = projectile.getPosition();
            gameManager.recordDamageHit(hitPos.x, hitPos.y, damage, projectile.getOwnerId(), npc.getId(), false);
        }

        projectile.setActive(false);
        return false; // Prevent physics resolution; oddball body absorbs the hit
    }

    /**
     * Handle a field effect hitting a headquarters.
     * LASER deals instant damage once; PLASMA deals DOT each tick.
     */
    private void handleFieldEffectHeadquartersCollision(FieldEffect fieldEffect, Headquarters hq) {
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
                    gameManager.recordDamageHit(hq.getPosition().x, hq.getPosition().y, damageDealt, fieldEffect.getOwnerId(), hq.getId(), destroyed);
                }
            }
            case PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE, EXPLOSION, FRAGMENTATION -> {
                double damageDealt = fieldEffect.getDamage() * deltaTime;
                boolean destroyed = hq.takeDamage(damageDealt);
                Player attacker = gameEntities.getPlayer(fieldEffect.getOwnerId());
                if (attacker != null) {
                    gameManager.handleHeadquartersDamage(hq, attacker, damageDealt, destroyed);
                }
                gameManager.recordDotDamageHit(hq.getPosition().x, hq.getPosition().y, damageDealt, fieldEffect.getOwnerId(), hq.getId(), destroyed);
            }
            default -> { /* Non-damaging field effects don't affect HQ */ }
        }
    }

    /**
     * Handle a FieldEffect (beam or field) physically intersecting an Oddball NPC body.
     * Awards oddball points to the firer and logs damage hits for UI.
     * Instant effects (LASER, EXPLOSION, FRAGMENTATION) award points once per effect instance.
     * Continuous effects (PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE) award DOT points per tick.
     */
    private void handleFieldEffectOddballCollision(FieldEffect fieldEffect, Oddball npc) {
        if (fieldEffect.getOwnerId() <= 0) {
            return; // NPC-fired beams/field effects don't generate score
        }

        Player attacker = gameEntities.getPlayer(fieldEffect.getOwnerId());
        if (attacker == null || !attacker.isActive()) {
            return;
        }

        double deltaTime = gameEntities.getWorld().getTimeStep().getDeltaTime();

        switch (fieldEffect.getType()) {
            case EXPLOSION, FRAGMENTATION, LASER -> {
                if (!fieldEffect.getAffectedEntities().add(npc.getId())) {
                    return; // Already scored this instant effect
                }
                double damage = fieldEffect.getDamage();
                double points = damage
                        * npc.getPointsMultiplier()
                        * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
                attacker.getScoring().addOddball(points);
                gameManager.recordDamageHit(npc.getPosition().x, npc.getPosition().y, damage, fieldEffect.getOwnerId(), npc.getId(), false);
            }
            case PLASMA, FIRE, ELECTRIC, FREEZE, POISON, EARTHQUAKE -> {
                if (fieldEffect.getDamage() > 0) {
                    double frameDamage = fieldEffect.getDamage() * deltaTime;
                    double points = frameDamage
                            * npc.getPointsMultiplier()
                            * gameManager.getGameConfig().getRules().getOddballNpcPointsPerDamage();
                    attacker.getScoring().addOddball(points);
                    gameManager.recordDotDamageHit(npc.getPosition().x, npc.getPosition().y, frameDamage, fieldEffect.getOwnerId(), npc.getId(), false);
                }
            }
            default -> { /* Non-damaging field effects don't affect Oddballs */ }
        }
    }

    /**
     * Handle projectile hitting a headquarters.
     */
    private boolean handleProjectileHeadquartersCollision(Projectile projectile, Headquarters hq) {
        // Check if projectile can damage this headquarters (team rules)
        if (!canProjectileDamageHeadquarters(projectile, hq)) {
            return false; // Friendly fire protection - let projectile pass through
        }

        // Check if this projectile has already hit this HQ
        if (!projectile.getAffectedObstacles().add(hq.getId())) {
            boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, hq);
            return !shouldPierce;
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
        gameManager.recordDamageHit(hitPosition.x, hitPosition.y, damageDealt, projectile.getOwnerId(), hq.getId(), hqDestroyed);

        // Check if projectile should pierce
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, hq);
        if (!shouldPierce) {
            projectile.setActive(false);
            return false;
        }

        return true;
    }

    /**
     * Check if a projectile can damage a headquarters (team protection).
     */
    private boolean canProjectileDamageHeadquarters(Projectile projectile, Headquarters hq) {
        // Can't damage own team's headquarters
        return projectile.getOwnerTeam() != hq.getOwnerTeam();
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

package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class Player extends GameEntity {
    private String playerName;
    private int team; // 0 = no team (FFA), 1+ = team number
    private Weapon weapon;
    private UtilityWeapon utilityWeapon;
    private boolean isReloading = false;
    private double reloadTimeRemaining = 0;
    private Vector2 aimDirection = new Vector2(1, 0);
    private long lastShotTime = 0;
    private long lastUtilityUseTime = 0;
    private int kills = 0;
    private int deaths = 0;
    private int captures = 0; // Flag captures in CTF mode
    private long respawnTime = 0;
    private Vector2 respawnPoint;
    private double maxSpeed = Config.PLAYER_SPEED;
    private final Set<AttributeModification> attributeModifications = new HashSet<>();
    
    // Respawn rules tracking
    private int livesRemaining = -1; // -1 = unlimited, 0 = eliminated
    /**
     * -- GETTER --
     *  Check if player is permanently eliminated.
     */
    private boolean eliminated = false; // Permanently eliminated (no more respawns)

    public Player(int id, String playerName, double x, double y, int team, double maxHealth) {
        super(id, createPlayerBody(x, y), maxHealth);
        this.playerName = playerName != null ? playerName : "Player " + id;
        this.team = team;
        this.respawnPoint = new Vector2(x, y);

        // Default weapons
        this.weapon = WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon();
        this.utilityWeapon = UtilityWeapon.HEAL_ZONE; // Default utility weapon
    }

    private static Body createPlayerBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS);
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);

        // Physics-based movement configuration
        body.setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
        body.setAngularDamping(Config.PLAYER_ANGULAR_DAMPING);
        body.setAngularVelocity(0.0);

        return body;
    }

    @Override
    public void update(double deltaTime) {
        attributeModifications.removeIf(am -> {
            if (am.isExpired()) {
                am.revert(this);
                return true; // Remove expired modifications
            }
            am.update(this, deltaTime);
            return false; // Keep active modifications
        });

        // Handle reloading
        if (isReloading) {
            reloadTimeRemaining -= deltaTime;
            if (reloadTimeRemaining <= 0) {
                getCurrentWeapon().reload();
                isReloading = false;
            }
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    public void processInput(PlayerInput input) {
        if (!active) {
            return;
        }

        // Physics-based dynamic movement
        Vector2 moveVector = new Vector2(input.getMoveX(), input.getMoveY());
        processMovement(moveVector);

        // Aiming
        Vector2 playerPos = getPosition();
        aimDirection = new Vector2(
                input.getWorldX() - playerPos.x,
                input.getWorldY() - playerPos.y
        );
        if (aimDirection.getMagnitude() > 0) {
            aimDirection.normalize();
            setRotation(Math.atan2(aimDirection.y, aimDirection.x));
            // Ensure no residual angular velocity interferes with controlled rotation
            body.setAngularVelocity(0.0);
        }

        // Reloading
        if (Boolean.TRUE.equals(input.getReload()) && !isReloading) {
            startReload();
        }

        // Handle legacy right-click mapping to altFire
        if (input.isRight()) {
            input.setAltFire(true);
        }
    }

    /**
     * Physics-based movement using forces for realistic acceleration and deceleration
     */
    private void processMovement(Vector2 moveVector) {
        if (moveVector.getMagnitude() > 0) {
            // Player wants to move - apply force toward target velocity
            moveVector.normalize();
            Vector2 targetVelocity = moveVector.multiply(maxSpeed);
            Vector2 currentVelocity = getVelocity();
            Vector2 velocityDiff = targetVelocity.subtract(currentVelocity);

            // Apply force proportional to velocity difference (PD controller)
            Vector2 force = velocityDiff.multiply(Config.PLAYER_ACCELERATION);
            body.applyForce(force);
        } else {
            // Player wants to stop - apply braking force
            Vector2 currentVelocity = getVelocity();
            if (currentVelocity.getMagnitude() > 1.0) { // Only brake if moving significantly
                Vector2 brakingForce = currentVelocity.multiply(-Config.PLAYER_BRAKING_FORCE);
                body.applyForce(brakingForce);
            }
        }
    }

    public void applyWeaponConfig(WeaponConfig primary, UtilityWeapon utility) {
        if (primary != null) {
            weapon = primary.buildWeapon();
            weapon.reload();
        }
        if (utility != null) {
            this.utilityWeapon = utility;
        }
    }

    public boolean canShoot() {
        Weapon weapon = this.weapon;
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
        // Check if we have enough ammo for at least one bullet (partial bursts are allowed)
        return isActive()
               && health > 0
               && !isReloading
               && weapon.getCurrentAmmo() > 0
               && (now - lastShotTime) >= fireInterval;
    }

    public boolean canUseUtility() {
        if (!isActive() || health <= 0 || utilityWeapon == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        double cooldownMs = utilityWeapon.getCooldown() * 1000.0;
        return (now - lastUtilityUseTime) >= cooldownMs;
    }
    
    /**
     * Refund the utility cooldown (e.g. when placement fails).
     * Resets the cooldown timer to allow immediate reuse.
     */
    public void refundUtilityCooldown() {
        lastUtilityUseTime = 0;
    }

    public List<Projectile> shoot() {
        Weapon weapon = this.weapon; // Always use primary weapon
        if (!canShoot()) {
            if (!isReloading && weapon.getCurrentAmmo() <= 0) {
                startReload();
            }
            return List.of();
        }

        lastShotTime = System.currentTimeMillis();

        Vector2 pos = getPosition();
        Vector2 baseDirection = aimDirection.copy();
        double baseAngle = Math.atan2(baseDirection.y, baseDirection.x);

        int bulletsPerShot = weapon.getBulletsPerShot();
        int actualBulletsToFire = Math.min(bulletsPerShot, weapon.getCurrentAmmo());
        weapon.setCurrentAmmo(weapon.getCurrentAmmo() - actualBulletsToFire);

        // Calculate maximum accuracy-based spread for each bullet
        double maxAccuracySpread = (1.0 - weapon.getAccuracy()) * 0.17; // Reduced from 0.2 for multi-shot

        List<Projectile> toFire = new LinkedList<>();
        // Store additional projectiles for GameManager to retrieve
        double angle = baseAngle;
        for (int i = 0; i < actualBulletsToFire; i++) {
            // Apply random accuracy spread independently for each bullet
            angle += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * maxAccuracySpread;

            Vector2 direction = new Vector2(Math.cos(angle), Math.sin(angle));
            Vector2 velocity = direction.multiply(weapon.getProjectileSpeed());

            toFire.add(new Projectile(
                    id,
                    pos.x + ((i > 0) ? ThreadLocalRandom.current().nextDouble(-3, 3) : 0),
                    pos.y + ((i > 0) ? ThreadLocalRandom.current().nextDouble(-3, 3) : 0),
                    velocity.x,
                    velocity.y,
                    weapon.getDamage(),
                    weapon.getRange(),
                    team,
                    weapon.getLinearDamping(),
                    weapon.getBulletEffects(),
                    weapon.getOrdinance()
            ));

        }
        return toFire;
    }

    /**
     * Shoot a beam weapon instead of projectiles
     *
     * @return Beam object if weapon can fire beams and conditions are met, null otherwise
     */
    public Beam shootBeam() {
        Weapon weapon = this.weapon; // Always use primary weapon
        if (!canShoot() || !weapon.getOrdinance().isBeamType()) {
            if (!isReloading && weapon.getCurrentAmmo() <= 0) {
                startReload();
            }
            return null;
        }

        lastShotTime = System.currentTimeMillis();
        weapon.setCurrentAmmo(weapon.getCurrentAmmo() - 1); // Beams consume 1 ammo

        Vector2 pos = getPosition();
        Vector2 direction = aimDirection.copy();
        direction.normalize();

        Ordinance ordinance = weapon.getOrdinance();
        double range = weapon.getRange() * 0.6; // Beams have 60% the range of bullets
        double damage = weapon.getDamage();

        // Create the appropriate beam type based on ordinance
        return createBeamFromOrdinance(ordinance, pos, direction, range, damage);
    }

    /**
     * Factory method to create a beam based on ordinance (simplified single-class approach)
     */
    private Beam createBeamFromOrdinance(Ordinance ordinance, Vector2 startPoint, Vector2 direction,
                                         double range, double damage) {
        int beamId = Config.nextId();

        // Single Beam class handles all beam types via ordinance
        return new Beam(beamId, startPoint, direction, range, damage, getId(), getTeam(), ordinance, weapon.getBulletEffects());
    }

    /**
     * Use the utility weapon. Returns data needed to create the utility effect.
     *
     * @return UtilityActivation data, or null if utility cannot be used
     */
    public UtilityActivation useUtility() {
        if (!canUseUtility()) {
            return null;
        }

        lastUtilityUseTime = System.currentTimeMillis();
        Vector2 pos = getPosition();

        return new UtilityActivation(
                utilityWeapon,
                pos.copy(),
                aimDirection.copy(),
                id,
                team
        );
    }

    /**
     * Data class for utility weapon activation
     */
    public static class UtilityActivation {
        public final UtilityWeapon utilityWeapon;
        public final Vector2 position;
        public final Vector2 direction;
        public final int playerId;
        public final int team;

        public UtilityActivation(UtilityWeapon utilityWeapon, Vector2 position, Vector2 direction, int playerId, int team) {
            this.utilityWeapon = utilityWeapon;
            this.position = position;
            this.direction = direction;
            this.playerId = playerId;
            this.team = team;
        }
    }

    private void startReload() {
        Weapon weapon = this.weapon; // Always reload primary weapon
        if (weapon.needsReload()) {
            isReloading = true;
            reloadTimeRemaining = weapon.getReloadTime();
        }
    }

    public void die() {
        active = false;
        deaths++;
        health = 0;
    }

    /**
     * Respawn at a specific location (for team-based spawning).
     *
     * @param newRespawnPoint New spawn location
     */
    public void respawnAt(Vector2 newRespawnPoint) {
        this.respawnPoint = newRespawnPoint.copy();
        respawn();
    }

    public void addKill() {
        kills++;
    }
    
    public void addCapture() {
        captures++;
    }
    
    /**
     * Initialize lives for LIMITED respawn mode.
     */
    public void initializeLives(int maxLives) {
        this.livesRemaining = maxLives;
        this.eliminated = false;
    }
    
    /**
     * Consume one life. Returns true if player is now eliminated.
     */
    public boolean loseLife() {
        if (livesRemaining > 0) {
            livesRemaining--;
            if (livesRemaining == 0) {
                eliminated = true;
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if player has lives remaining.
     */
    public boolean hasLivesRemaining() {
        return livesRemaining != 0; // -1 (unlimited) or > 0
    }

    /**
     * Mark player as eliminated (for ELIMINATION mode).
     */
    public void eliminate() {
        this.eliminated = true;
        this.active = false;
    }

    public Weapon getCurrentWeapon() {
        // Always return primary weapon (utility weapons are handled separately)
        Weapon w = weapon;
        for (AttributeModification attributeModification : attributeModifications) {
            w = attributeModification.update(w);
        }
        return w;
    }

    /**
     * Check if this player is on the same team as another player.
     *
     * @param otherPlayer The other player to check
     * @return true if on same team, false if enemies or FFA
     */
    public boolean isTeammate(Player otherPlayer) {
        if (otherPlayer == null || this.team == 0 || otherPlayer.team == 0) {
            return false; // FFA mode or null player
        }
        return this.team == otherPlayer.team;
    }

    /**
     * Override damage handling to account for damage resistance.
     */
    @Override
    public boolean takeDamage(double damage) {
        if (!active) {
            return false;
        }

        double modifiedDamage = damage;
        for (AttributeModification attributeModification : attributeModifications) {
            modifiedDamage = attributeModification.modifyDamageReceived(modifiedDamage);
        }

        return super.takeDamage(modifiedDamage);
    }

    /**
     * Respawn the player with proper health and state reset.
     * Note: This method is now deprecated - respawn logic is handled by GameManager.
     */
    @Deprecated
    public void respawn() {
        active = true;
        health = 100;
        setPosition(respawnPoint.x, respawnPoint.y);
        setVelocity(0, 0);
        weapon.reload();
        isReloading = false;
    }
}
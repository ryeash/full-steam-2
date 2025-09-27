package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.PlayerInput;
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
    private int team = 0; // 0 = no team (FFA), 1+ = team number
    private Weapon primaryWeapon;
    private Weapon secondaryWeapon;
    private int currentWeapon = 0; // 0 = primary, 1 = secondary
    private boolean isReloading = false;
    private double reloadTimeRemaining = 0;
    private Vector2 aimDirection = new Vector2(1, 0);
    private long lastShotTime = 0;
    private int kills = 0;
    private int deaths = 0;
    private double respawnTime = 0;
    private Vector2 respawnPoint;
    private double maxSpeed = Config.PLAYER_SPEED;
    private final Set<AttributeModification> attributeModifications = new HashSet<>();

    public Player(int id, String playerName, double x, double y) {
        this(id, playerName, x, y, 0); // Default to no team (FFA)
    }

    public Player(int id, String playerName, double x, double y, int team) {
        super(id, createPlayerBody(x, y), 100.0);
        this.playerName = playerName != null ? playerName : "Player " + id;
        this.team = team;
        this.respawnPoint = new Vector2(x, y);

        // Default weapons
        this.primaryWeapon = WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon();
        this.secondaryWeapon = WeaponConfig.HAND_CANNON_PRESET.buildWeapon();
    }

    private static Body createPlayerBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS);
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);
        body.setLinearDamping(0.97);

        // Prevent physics engine from affecting rotation
        body.setAngularDamping(0.0);
        body.setAngularVelocity(0.0);

        return body;
    }

    @Override
    public void update(double deltaTime) {
        attributeModifications.removeIf(am -> {
            if (am.isExpired()) {
                am.revert(this);
                return false;
            }
            am.update(this, deltaTime);
            return false;
        });

        // Handle reloading
        if (isReloading) {
            reloadTimeRemaining -= deltaTime;
            if (reloadTimeRemaining <= 0) {
                getCurrentWeapon().reload();
                isReloading = false;
            }
        }

        // Handle respawning
        if (!active && respawnTime > 0) {
            respawnTime -= deltaTime;
            if (respawnTime <= 0) {
                respawn();
            }
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    public void processInput(PlayerInput input) {
        if (!active) {
            return;
        }

        Vector2 moveVector = new Vector2(input.getMoveX(), input.getMoveY());
        if (moveVector.getMagnitude() > 0) {
            moveVector.normalize();
            double speed = maxSpeed;
            setVelocity(moveVector.x * speed, moveVector.y * speed);
        } else {
            setVelocity(0, 0);
        }

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

        // Weapon switching
        if (input.getWeaponSwitch() != null) {
            currentWeapon = input.getWeaponSwitch() % 2;
        }

        // Reloading
        if (Boolean.TRUE.equals(input.getReload()) && !isReloading) {
            startReload();
        }
    }

    public void applyWeaponConfig(WeaponConfig primary, WeaponConfig secondary) {
        if (primary != null) {
            try {
                System.out.println("DEBUG: Building primary weapon from config: " + primary.type +
                                   ", bulletsPerShot points: " + primary.bulletsPerShot +
                                   ", damage points: " + primary.damage +
                                   ", fireRate points: " + primary.fireRate);
                // Use the enhanced buildWeapon() method that includes effects and ordinance
                primaryWeapon = primary.buildWeapon();
                System.out.println("DEBUG: Built primary weapon: " + primaryWeapon.getName() +
                                   ", bulletsPerShot: " + primaryWeapon.getBulletsPerShot() +
                                   ", damage: " + primaryWeapon.getDamage() +
                                   ", fireRate: " + primaryWeapon.getFireRate());
                if (primaryWeapon.getName() == null || primaryWeapon.getName().equals("null")) {
                    // Fallback name if not set
                    primaryWeapon = primary.buildWeapon();
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Exception building weapon: " + e.getMessage());
                e.printStackTrace();
                // Fallback to legacy method if new method fails
                primaryWeapon = WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon();
            }
        }
        if (secondary != null) {
            try {
                // Use the enhanced buildWeapon() method that includes effects and ordinance
                secondaryWeapon = secondary.buildWeapon();
                if (secondaryWeapon.getName() == null || secondaryWeapon.getName().equals("null")) {
                    // Fallback name if not set
                    secondaryWeapon = secondary.buildWeapon();
                }
            } catch (Exception e) {
                // Fallback to legacy method if new method fails
                secondaryWeapon = WeaponConfig.HAND_CANNON_PRESET.buildWeapon();
            }
        }
    }

    public boolean canShoot() {
        Weapon weapon = getCurrentWeapon();
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
        // Check if we have enough ammo for at least one bullet (partial bursts are allowed)
        return isActive()
               && health > 0
               && !isReloading
               && weapon.getCurrentAmmo() > 0
               && (now - lastShotTime) >= fireInterval;
    }

    public List<Projectile> shoot() {
        Weapon weapon = getCurrentWeapon();
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

    private void startReload() {
        Weapon weapon = getCurrentWeapon();
        if (weapon.needsReload()) {
            isReloading = true;
            reloadTimeRemaining = weapon.getReloadTime();
        }
    }

    public void die() {
        active = false;
        deaths++;
        respawnTime = 5.0; // 5 second respawn
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

    public Weapon getCurrentWeapon() {
        Weapon w = currentWeapon == 0 ? primaryWeapon : secondaryWeapon;
        for (AttributeModification attributeModification : attributeModifications) {
            w = attributeModification.update(w);
        }
        return w;
    }

    public int getCurrentWeaponIndex() {
        return currentWeapon;
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
     * Check if this player is an enemy of another player.
     *
     * @param otherPlayer The other player to check
     * @return true if enemies (different teams or FFA), false if teammates
     */
    public boolean isEnemy(Player otherPlayer) {
        return !isTeammate(otherPlayer) && otherPlayer != null && otherPlayer.getId() != this.getId();
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
     */
    public void respawn() {
        active = true;
        health = 100;
        setPosition(respawnPoint.x, respawnPoint.y);
        setVelocity(0, 0);
        primaryWeapon.reload();
        secondaryWeapon.reload();
        isReloading = false;
    }
}
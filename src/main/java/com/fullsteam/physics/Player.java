package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

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
    private List<Projectile> additionalProjectiles = new ArrayList<>();

    public Player(int id, String playerName, double x, double y) {
        this(id, playerName, x, y, 0); // Default to no team (FFA)
    }
    
    public Player(int id, String playerName, double x, double y, int team) {
        super(id, createPlayerBody(x, y), 100.0);
        this.playerName = playerName != null ? playerName : "Player " + id;
        this.team = team;
        this.respawnPoint = new Vector2(x, y);

        // Default weapons
        this.primaryWeapon = new Weapon("Assault Rifle", Weapon.ASSAULT_RIFLE_PRESET);
        this.secondaryWeapon = new Weapon("Pistol", Weapon.HAND_CANNON_PRESET);
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
            double speed = input.isShift() ? Config.PLAYER_SPEED * 1.5 : Config.PLAYER_SPEED;
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
                    primaryWeapon = new Weapon(
                        primary.type != null ? primary.type : "Custom Primary", 
                        primary.buildPoints(), 
                        primary.buildBulletEffects(), 
                        primary.buildOrdinance()
                    );
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Exception building weapon: " + e.getMessage());
                e.printStackTrace();
                // Fallback to legacy method if new method fails
                primaryWeapon = new Weapon(primary.type != null ? primary.type : "Custom Primary", primary.buildPoints());
            }
        }
        if (secondary != null) {
            try {
                // Use the enhanced buildWeapon() method that includes effects and ordinance
                secondaryWeapon = secondary.buildWeapon();
                if (secondaryWeapon.getName() == null || secondaryWeapon.getName().equals("null")) {
                    // Fallback name if not set
                    secondaryWeapon = new Weapon(
                        secondary.type != null ? secondary.type : "Custom Secondary", 
                        secondary.buildPoints(), 
                        secondary.buildBulletEffects(), 
                        secondary.buildOrdinance()
                    );
                }
            } catch (Exception e) {
                // Fallback to legacy method if new method fails
                secondaryWeapon = new Weapon(secondary.type != null ? secondary.type : "Custom Secondary", secondary.buildPoints());
            }
        }
    }

    public boolean canShoot() {
        Weapon weapon = getCurrentWeapon();
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
        // Check if we have enough ammo for at least one bullet (partial bursts are allowed)
        return !isReloading && weapon.getAmmo() > 0 && (now - lastShotTime) >= fireInterval;
    }

    public Projectile shoot() {
        if (!canShoot()) {
            if (getCurrentWeapon().getAmmo() == 0) {
                startReload();
            }
            return null;
        }

        Weapon weapon = getCurrentWeapon();
        weapon.fire();
        lastShotTime = System.currentTimeMillis();

        Vector2 pos = getPosition();
        Vector2 baseDirection = aimDirection.copy();
        double baseAngle = Math.atan2(baseDirection.y, baseDirection.x);
        
        // For multi-shot weapons, we'll return the first projectile and let GameManager handle the rest
        // This maintains compatibility with existing code while supporting multi-shot
        int bulletsPerShot = weapon.getBulletsPerShot();
        
        // Limit bullets to available ammo (weapon.fire() will handle the ammo reduction)
        int actualBulletsToFire = Math.min(bulletsPerShot, weapon.getAmmo());
        
        // Debug logging
        System.out.println("DEBUG: Player " + id + " shooting with weapon: " + weapon.getName() + 
                          ", bulletsPerShot: " + bulletsPerShot + 
                          ", currentAmmo: " + weapon.getAmmo() +
                          ", actualBulletsToFire: " + actualBulletsToFire +
                          ", damage: " + weapon.getDamage() + 
                          ", fireRate: " + weapon.getFireRate());
        
        // Calculate spread pattern for multi-shot
        double totalSpread = actualBulletsToFire > 1 ? 0.3 : 0.0; // 0.3 radians total spread for multi-shot
        double spreadStep = actualBulletsToFire > 1 ? totalSpread / (actualBulletsToFire - 1) : 0.0;
        double startAngle = baseAngle - (totalSpread / 2.0);
        
        // Create the first projectile (this one gets returned)
        Vector2 direction = new Vector2();
        double angle = startAngle;
        
        // Add accuracy-based spread to each bullet
        double accuracySpread = (1.0 - weapon.getAccuracy()) * 0.1; // Reduced from 0.2 for multi-shot
        angle += (Math.random() - 0.5) * accuracySpread;
        
        direction.set(Math.cos(angle), Math.sin(angle));
        Vector2 velocity = direction.multiply(weapon.getProjectileSpeed());
        
        Projectile firstProjectile = new Projectile(
                id,
                pos.x,
                pos.y,
                velocity.x,
                velocity.y,
                weapon.getDamage(),
                weapon.getRange(),
                team,
                weapon.getLinearDamping(),
                weapon.getBulletEffects(),
                weapon.getOrdinance()
        );
        
        // Store additional projectiles for GameManager to retrieve
        if (actualBulletsToFire > 1) {
            additionalProjectiles.clear();
            for (int i = 1; i < actualBulletsToFire; i++) {
                angle = startAngle + (i * spreadStep);
                angle += (Math.random() - 0.5) * accuracySpread;
                
                direction.set(Math.cos(angle), Math.sin(angle));
                velocity = direction.multiply(weapon.getProjectileSpeed());
                
                Projectile additionalProjectile = new Projectile(
                        id,
                        pos.x,
                        pos.y,
                        velocity.x,
                        velocity.y,
                        weapon.getDamage(),
                        weapon.getRange(),
                        team,
                        weapon.getLinearDamping(),
                        weapon.getBulletEffects(),
                        weapon.getOrdinance()
                );
                
                additionalProjectiles.add(additionalProjectile);
            }
        }
        
        return firstProjectile;
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

    public void respawn() {
        active = true;
        health = 100.0;
        setPosition(respawnPoint.x, respawnPoint.y);
        setVelocity(0, 0);
        primaryWeapon.reload();
        secondaryWeapon.reload();
        isReloading = false;
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
        return currentWeapon == 0 ? primaryWeapon : secondaryWeapon;
    }

    public int getCurrentWeaponIndex() {
        return currentWeapon;
    }
    
    /**
     * Get and clear additional projectiles from multi-shot weapons.
     * This should be called by GameManager after calling shoot().
     * @return List of additional projectiles, empty if single-shot weapon
     */
    public List<Projectile> getAndClearAdditionalProjectiles() {
        List<Projectile> result = new ArrayList<>(additionalProjectiles);
        additionalProjectiles.clear();
        return result;
    }
    
    /**
     * Check if this player is on the same team as another player.
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
     * @param otherPlayer The other player to check
     * @return true if enemies (different teams or FFA), false if teammates
     */
    public boolean isEnemy(Player otherPlayer) {
        return !isTeammate(otherPlayer) && otherPlayer != null && otherPlayer.getId() != this.getId();
    }
}

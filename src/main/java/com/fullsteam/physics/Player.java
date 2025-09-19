package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

@Getter
@Setter
public class Player extends GameEntity {
    private String playerName;
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

    public Player(int id, String playerName, double x, double y) {
        super(id, createPlayerBody(x, y), 100.0);
        this.playerName = playerName != null ? playerName : "Player " + id;
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
            primaryWeapon = new Weapon(primary.type != null ? primary.type : "Custom Primary", primary.buildPoints());
        }
        if (secondary != null) {
            secondaryWeapon = new Weapon(secondary.type != null ? secondary.type : "Custom Secondary", secondary.buildPoints());
        }
    }

    public boolean canShoot() {
        Weapon weapon = getCurrentWeapon();
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
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
        Vector2 direction = aimDirection.copy();
        // Add some spread for accuracy
        double spread = (1.0 - weapon.getAccuracy()) * 0.2; // Max 0.2 radians spread
        double angle = Math.atan2(direction.y, direction.x);
        angle += (Math.random() - 0.5) * spread;
        direction.set(Math.cos(angle), Math.sin(angle));
        // Calculate velocity vector
        Vector2 velocity = direction.multiply(weapon.getProjectileSpeed());
        return new Projectile(
                id,
                pos.x,
                pos.y,
                velocity.x,
                velocity.y,
                weapon.getDamage(),
                weapon.getRange()
        );
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

    public void addKill() {
        kills++;
    }

    public Weapon getCurrentWeapon() {
        return currentWeapon == 0 ? primaryWeapon : secondaryWeapon;
    }

    public int getCurrentWeaponIndex() {
        return currentWeapon;
    }

    public Vector2 getAimDirection() {
        return aimDirection.copy();
    }

    public Vector2 getRespawnPoint() {
        return respawnPoint.copy();
    }

    public void setRespawnPoint(Vector2 point) {
        this.respawnPoint = point.copy();
    }
}

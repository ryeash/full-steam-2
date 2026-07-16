package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.HasWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automated defense turret that targets enemies within range.
 * <p>
 * The turret's firing behaviour is fully described by a {@link Weapon} object,
 * which normalises damage, fire-rate, range, accuracy, projectile speed,
 * bullet effects, ordinance, and caliber through the same stat-resolution
 * pipeline used for player weapons. Magazine / reload mechanics are intentionally
 * bypassed — the turret fires continuously on its fire-rate clock.
 * <p>
 * Constructing with a custom {@link Weapon} (or {@link WeaponConfig}) is the
 * extension point for future "base-defender" NPC turrets with varied firing
 * systems (e.g., beam turrets, explosive-shell turrets).
 */
@Getter
@Setter
public class Turret extends GameEntity implements HasWeapon {
    private final int ownerId;
    private final int ownerTeam;
    private final Weapon weapon;
    private long lastShotTime = 0;
    private Player currentTarget;
    private Vector2 aimDirection = new Vector2(1, 0);

    public Turret(int ownerId, int ownerTeam, Vector2 position, double lifespan, Weapon weapon) {
        super(Config.nextEntityId(), createTurretBody(position), 50.0);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.weapon = weapon;
        this.expires = (long) (System.currentTimeMillis() + (lifespan * 1000));
        this.setRotation(Math.random() * 2 * Math.PI);
    }

    private static Body createTurretBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS * .75);
        body.addFixture(circle);
        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (!active) {
            return;
        }
        if (currentTarget != null && (!currentTarget.isActive() || !isValidTarget(currentTarget))) {
            currentTarget = null;
        }
    }

    /**
     * Find and acquire the nearest valid target from the supplied player list.
     */
    public void acquireTarget(List<Player> players) {
        if (currentTarget != null && isValidTarget(currentTarget)) {
            return;
        }

        Player closestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : players) {
            if (!isValidTarget(player)) {
                continue;
            }
            double distance = getPosition().distance(player.getPosition());
            if (distance <= weapon.getRange() && distance < closestDistance) {
                closestTarget = player;
                closestDistance = distance;
            }
        }

        currentTarget = closestTarget;

        if (currentTarget != null) {
            Vector2 turretPos = getPosition();
            Vector2 targetPos = currentTarget.getPosition();
            aimDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
            if (aimDirection.getMagnitude() > 0) {
                aimDirection.normalize();
                setRotation(Math.atan2(aimDirection.y, aimDirection.x));
            }
        }
    }

    /**
     * Attempt to fire at the current target.
     *
     * @return a {@link Projectile} or {@link Beam} depending on the weapon's
     * ordinance, or {@code null} if the turret cannot fire this tick.
     */
    public GameEntity tryFire() {
        if (currentTarget == null || !canFire()) {
            return null;
        }

        lastShotTime = System.currentTimeMillis();

        Vector2 targetPos = currentTarget.getPosition();
        Vector2 turretPos = getPosition();
        Vector2 fireDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
        if (fireDirection.getMagnitude() == 0) {
            return null;
        }
        fireDirection.normalize();
        setRotation(Math.atan2(fireDirection.y, fireDirection.x));

        // Apply accuracy spread using the same formula as Player.java
        double spread = (1.0 - weapon.getAccuracy()) * 0.17;
        double angleOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * spread;
        double firedAngle = Math.atan2(fireDirection.y, fireDirection.x) + angleOffset;
        Vector2 firedDir = new Vector2(Math.cos(firedAngle), Math.sin(firedAngle));

        if (weapon.getOrdinance().isBeamType()) {
            return fireBeam(turretPos, firedDir);
        } else {
            return fireProjectile(turretPos, firedDir);
        }
    }

    private Beam fireBeam(Vector2 pos, Vector2 dir) {
        Vector2 endPoint = pos.copy().add(dir.copy().multiply(weapon.getRange()));
        Beam beam = new Beam(pos, dir, weapon.getRange(), weapon.getDamage(),
                ownerId, ownerTeam, weapon.getOrdinance(), weapon.getBulletEffects(),
                weapon.getCaliber());
        beam.setPath(List.of(pos.copy(), endPoint));
        return beam;
    }

    private Projectile fireProjectile(Vector2 pos, Vector2 dir) {
        Vector2 velocity = dir.copy().multiply(weapon.getProjectileSpeed());
        return new Projectile(
                ownerId,
                pos.x,
                pos.y,
                velocity.x,
                velocity.y,
                weapon.getDamagePerBullet(),
                weapon.getRange() * 1.1,
                ownerTeam,
                0.02,
                weapon.getBulletEffects(),
                weapon.getOrdinance(),
                weapon.getCaliber(),
                weapon.getKnockbackPerBullet()
        );
    }

    private boolean isValidTarget(Player player) {
        double distance = getPosition().distance(player.getPosition());
        if (!player.isActive() || player.getHealth() <= 0 || distance > weapon.getRange()) {
            return false;
        }
        if (player.getId() == ownerId) {
            return false;
        }
        if (ownerTeam == 0 || player.getTeam() == 0) {
            return true;
        }
        return ownerTeam != player.getTeam();
    }

    public boolean canFire() {
        if (!active || currentTarget == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
        return (now - lastShotTime) >= fireInterval;
    }
}

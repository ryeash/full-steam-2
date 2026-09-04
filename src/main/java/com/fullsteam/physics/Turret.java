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

import java.util.Collection;
import java.util.List;

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
public class Turret extends OwnedGameEntity implements HasWeapon {
    public static final double TURRET_FIRE_RATE_PENALTY = 2;

    private final Weapon weapon;
    private long lastShotTime = 0;
    private OwnedGameEntity currentTarget;
    private Vector2 aimDirection = new Vector2(1, 0);

    public Turret(int ownerId, int ownerTeam, Vector2 position, double lifespan, Weapon weapon) {
        super(Config.nextEntityId(), createTurretBody(position), 50.0, ownerId, ownerTeam);
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
     * Find and acquire the nearest valid target from players and zombies.
     */
    public void acquireTarget(Collection<Player> players, Collection<Zombie> zombies) {
        if (currentTarget != null && isValidTarget(currentTarget)) {
            return;
        }

        OwnedGameEntity closestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        if (players != null) {
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
        }

        if (zombies != null) {
            for (Zombie zombie : zombies) {
                if (!isValidTarget(zombie)) {
                    continue;
                }
                double distance = getPosition().distance(zombie.getPosition());
                if (distance <= weapon.getRange() && distance < closestDistance) {
                    closestTarget = zombie;
                    closestDistance = distance;
                }
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
     * Overload for backwards compatibility.
     */
    public void acquireTarget(List<Player> players) {
        acquireTarget(players, List.of());
    }

    private boolean isValidTarget(OwnedGameEntity target) {
        if (target == null || !target.isActive() || target.getHealth() <= 0) {
            return false;
        }
        double distance = getPosition().distance(target.getPosition());
        if (distance > weapon.getRange()) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.isVisionObscured() || player.getId() == ownerId) {
                return false;
            }
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return true;
            }
            return ownerTeam != player.getTeam();
        } else if (target instanceof Zombie) {
            return true; // Hostile to all zombies
        }
        return false;
    }

    public boolean canFire() {
        if (!active || currentTarget == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / (weapon.getFireRate() / TURRET_FIRE_RATE_PENALTY);
        return (now - lastShotTime) >= fireInterval;
    }
}

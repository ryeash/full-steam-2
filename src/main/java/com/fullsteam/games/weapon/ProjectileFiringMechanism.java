package com.fullsteam.games.weapon;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Firing mechanism for standard projectile weapons (e.g. bullets, pellets, rockets).
 */
public class ProjectileFiringMechanism implements FiringMechanism {

    private final GameEntities gameEntities;

    public ProjectileFiringMechanism(GameEntities gameEntities) {
        this.gameEntities = gameEntities;
    }

    @Override
    public void handlePlayerFire(Player player, PlayerInput input) {
        if (player.hasActivePlasmaBeam()) {
            player.stopPlasmaBeam();
        }

        if (!input.isLeft()) {
            return;
        }

        Weapon weapon = player.getCurrentWeapon();
        if (!player.canShoot()) {
            if (!player.isReloading() && weapon.getCurrentAmmo() <= 0) {
                player.startReload();
            }
            return;
        }

        player.setLastShotTime(System.currentTimeMillis());
        Vector2 pos = player.getPosition();
        Vector2 baseDirection = player.getAimDirection().copy();
        baseDirection.normalize();

        double radius = player.getRadius();
        Vector2 startPos = pos.copy().add(baseDirection.copy().multiply(radius));

        int bulletsPerShot = weapon.getBulletsPerShot();
        int actualBulletsToFire = Math.min(bulletsPerShot, weapon.getCurrentAmmo());
        weapon.setCurrentAmmo(weapon.getCurrentAmmo() - actualBulletsToFire);

        List<GameEntity> fired = fire(player.getOwnerId(), player.getOwnerTeam(), weapon, startPos, baseDirection);
        for (GameEntity entity : fired) {
            gameEntities.add(entity);
        }
    }

    @Override
    public List<GameEntity> fire(int ownerId, int ownerTeam, Weapon weapon, Vector2 position, Vector2 direction) {
        double baseAngle = Math.atan2(direction.y, direction.x);
        double spread = (1.0 - weapon.getAccuracy()) * 0.17;
        int shots = Math.max(1, weapon.getBulletsPerShot());
        List<GameEntity> fired = new ArrayList<>(shots);

        double angle = baseAngle;
        for (int i = 0; i < shots; i++) {
            angle += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * spread;
            Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));
            Vector2 jitter = new Vector2(
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0,
                    (i > 0) ? ThreadLocalRandom.current().nextDouble(-5, 5) : 0);
            double rolledDamage = weapon.rollDamagePerBullet();

            fired.add(new Projectile(
                    ownerId,
                    position.copy().add(jitter),
                    aimDir.copy().multiply(weapon.getProjectileSpeed()),
                    rolledDamage,
                    weapon.getRange(),
                    ownerTeam,
                    weapon.getLinearDamping(),
                    weapon.getBulletEffects(),
                    weapon.getOrdinance(),
                    weapon.getCaliber(),
                    weapon.getKnockbackPerBullet()
            ));
        }

        return fired;
    }
}

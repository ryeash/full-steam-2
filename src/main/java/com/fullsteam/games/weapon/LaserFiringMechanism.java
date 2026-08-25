package com.fullsteam.games.weapon;

import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Firing mechanism for instantaneous hitscan laser weapons ({@link com.fullsteam.model.Ordinance#LASER}).
 */
public class LaserFiringMechanism implements FiringMechanism {

    private final GameEntities gameEntities;
    private final BeamPathCalculator beamPathCalculator;
    private final BulletEffectProcessor bulletEffectProcessor;

    public LaserFiringMechanism(GameEntities gameEntities,
                                BeamPathCalculator beamPathCalculator,
                                BulletEffectProcessor bulletEffectProcessor) {
        this.gameEntities = gameEntities;
        this.beamPathCalculator = beamPathCalculator;
        this.bulletEffectProcessor = bulletEffectProcessor;
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

        List<GameEntity> firedBeams = fire(player.getOwnerId(), player.getOwnerTeam(), weapon, startPos, baseDirection);
        for (GameEntity entity : firedBeams) {
            if (entity instanceof FieldEffectBeam beam) {
                processAndAddLaserBeam(beam);
            }
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
            double rolledDamage = weapon.rollDamagePerBullet();

            fired.add(new FieldEffectBeam(
                    position,
                    aimDir,
                    weapon.getRange() * BeamPathCalculator.BEAM_RANGE_PENALTY,
                    rolledDamage,
                    ownerId,
                    ownerTeam,
                    FieldEffectType.LASER,
                    weapon.getBulletEffects(),
                    weapon.getCaliber()
            ));
        }

        return fired;
    }

    public void processAndAddLaserBeam(FieldEffectBeam beam) {
        List<Vector2> vector2s = beamPathCalculator.computeBeamPath(beam);
        for (int i = 0; i < vector2s.size() - 1; i++) {
            Vector2 start = vector2s.get(i);
            Vector2 end = vector2s.get(i + 1);
            double range = start.distance(end);
            Vector2 direction = end.copy().subtract(start);
            FieldEffectBeam beamSegment = new FieldEffectBeam(
                    start,
                    direction,
                    range,
                    beam.getDamage(),
                    beam.getOwnerId(),
                    beam.getOwnerTeam(),
                    beam.getType(),
                    beam.getBulletEffects(),
                    beam.getCaliber());
            gameEntities.add(beamSegment);
        }
        if (!vector2s.isEmpty() && !beam.getBulletEffects().isEmpty()) {
            Vector2 endPoint = vector2s.getLast();
            bulletEffectProcessor.processBeamEffectHit(beam, endPoint);
        }
    }
}

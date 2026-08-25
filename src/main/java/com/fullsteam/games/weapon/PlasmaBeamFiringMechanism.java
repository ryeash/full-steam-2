package com.fullsteam.games.weapon;

import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import org.dyn4j.Epsilon;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Continuous hold-to-fire mechanism for plasma beam weapons ({@link com.fullsteam.model.Ordinance#PLASMA_BEAM}).
 */
public class PlasmaBeamFiringMechanism implements FiringMechanism {

    private final GameEntities gameEntities;
    private final BeamPathCalculator beamPathCalculator;
    private final BulletEffectProcessor bulletEffectProcessor;

    public PlasmaBeamFiringMechanism(GameEntities gameEntities,
                                     BeamPathCalculator beamPathCalculator,
                                     BulletEffectProcessor bulletEffectProcessor) {
        this.gameEntities = gameEntities;
        this.beamPathCalculator = beamPathCalculator;
        this.bulletEffectProcessor = bulletEffectProcessor;
    }

    @Override
    public void handlePlayerFire(Player player, PlayerInput input) {
        Weapon weapon = player.getCurrentWeapon();

        boolean wantsToFire = Boolean.TRUE.equals(input.isLeft())
                && player.isActive()
                && player.getHealth() > 0
                && !player.isReloading();

        if (!wantsToFire) {
            if (player.hasActivePlasmaBeam()) {
                player.stopPlasmaBeam();
            }
            if (Boolean.TRUE.equals(input.isLeft()) && !player.isReloading() && weapon.getCurrentAmmo() <= 0) {
                player.startReload();
            }
            return;
        }

        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / Math.max(0.1, weapon.getFireRate());
        int shotsPerInterval = Math.max(1, weapon.getBulletsPerShot());

        if (!player.hasActivePlasmaBeam() || player.getLastShotTime() == 0) {
            if (weapon.getCurrentAmmo() <= 0) {
                player.startReload();
                return;
            }
            int actualShots = Math.min(shotsPerInterval, weapon.getCurrentAmmo());
            weapon.setCurrentAmmo(weapon.getCurrentAmmo() - actualShots);
            player.setLastShotTime(now);
            player.setActivePlasmaBeamShotCount(actualShots);
            player.setActivePlasmaBeamDamageRates(rollPlasmaBeamDamageRates(weapon, actualShots));
            updatePlasmaBeam(player, actualShots);
        } else {
            if (now - player.getLastShotTime() >= fireInterval) {
                if (weapon.getCurrentAmmo() <= 0) {
                    player.stopPlasmaBeam();
                    player.startReload();
                    return;
                }
                int actualShots = Math.min(shotsPerInterval, weapon.getCurrentAmmo());
                weapon.setCurrentAmmo(weapon.getCurrentAmmo() - actualShots);
                player.setLastShotTime(now);
                player.setActivePlasmaBeamShotCount(actualShots);
                player.setActivePlasmaBeamDamageRates(rollPlasmaBeamDamageRates(weapon, actualShots));
                updatePlasmaBeam(player, actualShots);
            } else {
                updatePlasmaBeam(player, Math.max(1, player.getActivePlasmaBeamShotCount()));
            }
        }
    }

    @Override
    public List<GameEntity> fire(int ownerId, int ownerTeam, Weapon weapon, Vector2 position, Vector2 direction) {
        double baseAngle = Math.atan2(direction.y, direction.x);
        int shots = Math.max(1, weapon.getBulletsPerShot());
        double spread = Math.max((shots - 1) * 0.08, (1.0 - weapon.getAccuracy()) * 0.17);

        List<GameEntity> fired = new ArrayList<>(shots);

        for (int i = 0; i < shots; i++) {
            double angleOffset = (shots == 1) ? 0.0 : -spread / 2.0 + i * (spread / (shots - 1));
            double angle = baseAngle + angleOffset;
            Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));
            double perBeamDamageRate = weapon.rollDamagePerBullet() * weapon.getFireRate();

            fired.add(new FieldEffectBeam(
                    position,
                    aimDir,
                    weapon.getRange() * BeamPathCalculator.BEAM_RANGE_PENALTY,
                    perBeamDamageRate,
                    ownerId,
                    ownerTeam,
                    FieldEffectType.PLASMA,
                    weapon.getBulletEffects(),
                    weapon.getCaliber()
            ));
        }

        return fired;
    }

    private double[] rollPlasmaBeamDamageRates(Weapon weapon, int actualShots) {
        int numBeams = Math.max(1, actualShots);
        double[] rates = new double[numBeams];
        for (int k = 0; k < numBeams; k++) {
            rates[k] = weapon.rollDamagePerBullet() * weapon.getFireRate();
        }
        return rates;
    }

    private void updatePlasmaBeam(Player player, int activeShotCount) {
        Weapon weapon = player.getCurrentWeapon();
        Vector2 pos = player.getPosition();
        Vector2 baseDirection = player.getAimDirection().copy();
        if (baseDirection.getMagnitudeSquared() <= Epsilon.E) {
            baseDirection = new Vector2(1, 0);
        } else {
            baseDirection.normalize();
        }

        double accuracy = weapon.getAccuracy();
        double timeSeconds = System.currentTimeMillis() / 1000.0;
        double swayAngle = calculateBeamSwayOffset(accuracy, player.getId(), timeSeconds);
        double baseAngle = Math.atan2(baseDirection.y, baseDirection.x) + swayAngle;

        int shots = Math.max(1, weapon.getBulletsPerShot());
        int numBeams = Math.max(1, Math.min(shots, activeShotCount));

        double[] damageRates = player.getActivePlasmaBeamDamageRates();
        if (damageRates == null || damageRates.length != numBeams) {
            damageRates = rollPlasmaBeamDamageRates(weapon, numBeams);
            player.setActivePlasmaBeamDamageRates(damageRates);
        }

        double spread = Math.max((shots - 1) * 0.08, (1.0 - accuracy) * 0.17);
        double beamRange = weapon.getRange() * BeamPathCalculator.BEAM_RANGE_PENALTY;

        List<FieldEffectBeam> activeBeams = player.getActivePlasmaBeams();
        int beamSegmentIndex = 0;

        for (int k = 0; k < numBeams; k++) {
            double angleOffset = (shots == 1) ? 0.0 : -spread / 2.0 + k * (spread / (shots - 1));
            double angle = baseAngle + angleOffset;
            Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));
            Vector2 startPos = pos.copy().add(aimDir.copy().multiply(player.getRadius()));
            double perBeamDamageRate = damageRates[k];

            FieldEffectBeam spec = new FieldEffectBeam(
                    startPos,
                    aimDir,
                    beamRange,
                    perBeamDamageRate,
                    player.getOwnerId(),
                    player.getOwnerTeam(),
                    FieldEffectType.PLASMA,
                    weapon.getBulletEffects(),
                    weapon.getCaliber()
            );

            List<Vector2> vector2s = beamPathCalculator.computeBeamPath(spec);
            int numSegments = Math.max(1, vector2s.size() - 1);

            for (int i = 0; i < numSegments; i++) {
                Vector2 segStart = vector2s.get(i);
                Vector2 segEnd = vector2s.get(Math.min(i + 1, vector2s.size() - 1));
                Vector2 segDir = segEnd.copy().subtract(segStart);
                if (segDir.getMagnitudeSquared() > Epsilon.E) {
                    segDir.normalize();
                } else {
                    segDir = aimDir.copy();
                }

                if (beamSegmentIndex < activeBeams.size()) {
                    FieldEffectBeam beam = activeBeams.get(beamSegmentIndex);
                    beam.getStartPoint().set(segStart);
                    beam.getEndPoint().set(segEnd);
                    beam.getDirection().set(segDir);
                    beam.setDamage(perBeamDamageRate);
                    beam.setExpires(System.currentTimeMillis() + 300);
                    beam.setActive(true);
                    beam.updateBodyTransform();
                } else {
                    FieldEffectBeam beam = new FieldEffectBeam(
                            segStart,
                            segDir,
                            segStart.distance(segEnd),
                            perBeamDamageRate,
                            player.getOwnerId(),
                            player.getOwnerTeam(),
                            FieldEffectType.PLASMA,
                            weapon.getBulletEffects(),
                            weapon.getCaliber()
                    );
                    beam.setEndPoint(segEnd);
                    beam.setExpires(System.currentTimeMillis() + 300);
                    beam.updateBodyTransform();
                    gameEntities.add(beam);
                    activeBeams.add(beam);
                }
                beamSegmentIndex++;
            }

            if (!vector2s.isEmpty() && !spec.getBulletEffects().isEmpty()) {
                Vector2 finalEndPoint = vector2s.getLast();
                bulletEffectProcessor.processBeamEffectHit(spec, finalEndPoint);
            }
        }

        while (activeBeams.size() > beamSegmentIndex) {
            FieldEffectBeam extra = activeBeams.removeLast();
            extra.setActive(false);
        }
    }

    /**
     * Calculate continuous weapon sway offset in radians based on accuracy attribute and time.
     * Returns 0.0 if accuracy >= 1.0 (perfect accuracy).
     */
    public static double calculateBeamSwayOffset(double accuracy, int entityId, double timeSeconds) {
        if (accuracy >= 1.0) {
            return 0.0;
        }
        double maxSwayAngle = (1.0 - accuracy) * 0.17;
        double swayFactor = Math.sin(timeSeconds * 3.5 + entityId) * 0.65
                + Math.sin(timeSeconds * 8.3 + entityId * 1.7) * 0.35;
        return swayFactor * maxSwayAngle;
    }
}

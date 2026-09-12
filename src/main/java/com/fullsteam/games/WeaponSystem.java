package com.fullsteam.games;

import com.fullsteam.games.weapon.BeamPathCalculator;
import com.fullsteam.games.weapon.FiringMechanism;
import com.fullsteam.games.weapon.LaserFiringMechanism;
import com.fullsteam.games.weapon.PlasmaBeamFiringMechanism;
import com.fullsteam.games.weapon.ProjectileFiringMechanism;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.OwnedGameEntity;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Zombie;
import org.dyn4j.Epsilon;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Facade for all weapon-related functionality. Delegated to specialized
 * {@link FiringMechanism} strategies based on {@link Ordinance}.
 */
public class WeaponSystem {
    private static final Logger log = LoggerFactory.getLogger(WeaponSystem.class);

    public static final double BEAM_RANGE_PENALTY = BeamPathCalculator.BEAM_RANGE_PENALTY;

    private final GameEntities gameEntities;
    private final World<Body> world;
    private final BulletEffectProcessor bulletEffectProcessor;
    private final BeamPathCalculator beamPathCalculator;
    private final Map<Ordinance, FiringMechanism> mechanisms = new EnumMap<>(Ordinance.class);

    public WeaponSystem(GameEntities gameEntities, World<Body> world) {
        this.gameEntities = gameEntities;
        this.world = world;
        this.bulletEffectProcessor = new BulletEffectProcessor(gameEntities);
        this.beamPathCalculator = new BeamPathCalculator(world);

        mechanisms.put(Ordinance.PROJECTILE, new ProjectileFiringMechanism(gameEntities));
        mechanisms.put(Ordinance.LASER, new LaserFiringMechanism(gameEntities, beamPathCalculator, bulletEffectProcessor));
        mechanisms.put(Ordinance.PLASMA_BEAM, new PlasmaBeamFiringMechanism(gameEntities, beamPathCalculator, bulletEffectProcessor));
    }

    public FiringMechanism getFiringMechanism(Ordinance ordinance) {
        return mechanisms.getOrDefault(ordinance, mechanisms.get(Ordinance.PROJECTILE));
    }

    /**
     * Process primary weapon input for a player.
     * Delegates to the appropriate {@link FiringMechanism}.
     */
    public void handlePrimaryFire(Player player, PlayerInput input) {
        Weapon weapon = player.getCurrentWeapon();
        if (weapon != null) {
            getFiringMechanism(weapon.getOrdinance()).handlePlayerFire(player, input);
        }
    }

    public void handleTurretFire(Turret turret) {
        if (turret.getCurrentTarget() == null || !turret.canFire()) {
            return;
        }
        turret.setLastShotTime(System.currentTimeMillis());
        Vector2 targetPos = turret.getCurrentTarget().getPosition();
        Vector2 turretPos = turret.getPosition();
        Vector2 fireDirection = new Vector2(targetPos.x - turretPos.x, targetPos.y - turretPos.y);
        if (fireDirection.getMagnitude() == 0) {
            return;
        }
        fireDirection.normalize();
        double baseAngle = Math.atan2(fireDirection.y, fireDirection.x);
        turret.setRotation(baseAngle);

        List<GameEntity> ordinance = fireWeapon(turret.getOwnerId(), turret.getOwnerTeam(), turret.getWeapon(), turretPos, fireDirection);
        handleOrdinanceFiring(ordinance);
    }

    public void handleOddballFire(Oddball oddball) {
        if (oddball.getCurrentTarget() == null || !oddball.isActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - oddball.getLastShotTime() < (long) (1000.0 / oddball.getWeapon().getFireRate())) {
            return;
        }
        oddball.setLastShotTime(now);

        Vector2 myPos = oddball.getPosition();
        Vector2 targetPos = oddball.getCurrentTarget().getPosition();
        boolean beamType = oddball.getWeapon().getOrdinance().isBeamType();

        // Beams are hitscan, so aim straight at the target. Projectiles travel at a
        // finite speed, so lead the target based on its velocity to intercept it.
        Vector2 dir = beamType
                ? new Vector2(targetPos.x - myPos.x, targetPos.y - myPos.y)
                : predictInterceptDirection(myPos, targetPos, oddball.getCurrentTarget().getVelocity(), oddball.getWeapon().getProjectileSpeed());
        if (dir.getMagnitude() == 0) {
            return;
        }
        List<GameEntity> ordinance = fireWeapon(-oddball.getId(), 0, oddball.getWeapon(), myPos, dir.getNormalized());
        handleOrdinanceFiring(ordinance);
    }

    public void handleZombieFire(Zombie zombie) {
        if (zombie == null || !zombie.canFire()) {
            return;
        }

        OwnedGameEntity target = zombie.getCurrentTargetEntity();
        if (target == null || !target.isActive() || target.getHealth() <= 0) {
            return;
        }

        Vector2 myPos = zombie.getPosition();
        Vector2 targetPos = target.getPosition();
        double dist = myPos.distance(targetPos);
        if (dist > zombie.getWeapon().getRange()) {
            return;
        }

        zombie.setLastShotTime(System.currentTimeMillis());

        Vector2 targetVel = (target instanceof GameEntity ge) ? ge.getVelocity() : new Vector2(0, 0);
        Vector2 dir = predictInterceptDirection(myPos, targetPos, targetVel, zombie.getWeapon().getProjectileSpeed());
        if (dir.getMagnitude() == 0) {
            dir = targetPos.subtract(myPos);
        }
        if (dir.getMagnitude() == 0) {
            return;
        }

        Vector2 fireDir = dir.getNormalized();
        zombie.setAimDirection(fireDir);

        Vector2 spawnPos = myPos.copy().add(fireDir.copy().multiply(zombie.getRadius() + 2.0));
        List<GameEntity> ordinance = fireWeapon(-zombie.getId(), zombie.getOwnerTeam(), zombie.getWeapon(), spawnPos, fireDir);
        handleOrdinanceFiring(ordinance);
    }

    private void handleOrdinanceFiring(List<GameEntity> ordinance) {
        for (GameEntity gameEntity : ordinance) {
            if (gameEntity instanceof FieldEffectBeam beam && beam.getOrdinance() == Ordinance.LASER) {
                FiringMechanism mech = getFiringMechanism(Ordinance.LASER);
                if (mech instanceof LaserFiringMechanism laserMech) {
                    laserMech.processAndAddLaserBeam(beam);
                }
            } else {
                gameEntities.add(gameEntity);
            }
        }
    }

    /**
     * Shared firing helper used by Turret, Oddball, and any other entity.
     */
    public List<GameEntity> fireWeapon(int ownerId, int ownerTeam, Weapon weapon, Vector2 position, Vector2 direction) {
        return getFiringMechanism(weapon.getOrdinance()).fire(ownerId, ownerTeam, weapon, position, direction);
    }

    public List<Vector2> computeBeamPath(FieldEffectBeam beam) {
        return beamPathCalculator.computeBeamPath(beam);
    }

    public static double calculateBeamSwayOffset(double accuracy, int entityId, double timeSeconds) {
        return PlasmaBeamFiringMechanism.calculateBeamSwayOffset(accuracy, entityId, timeSeconds);
    }

    /**
     * Compute the aim direction that leads a moving target so a projectile fired at
     * {@code projectileSpeed} intercepts it.
     */
    private Vector2 predictInterceptDirection(Vector2 shooterPos, Vector2 targetPos, Vector2 targetVel, double projectileSpeed) {
        Vector2 toTarget = new Vector2(targetPos.x - shooterPos.x, targetPos.y - shooterPos.y);
        if (projectileSpeed <= 0.0) {
            return toTarget;
        }

        double a = targetVel.dot(targetVel) - projectileSpeed * projectileSpeed;
        double b = 2.0 * toTarget.dot(targetVel);
        double c = toTarget.dot(toTarget);

        double t;
        if (Math.abs(a) < Epsilon.E) {
            if (Math.abs(b) < Epsilon.E) {
                return toTarget;
            }
            t = -c / b;
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc < 0.0) {
                return toTarget;
            }
            double sqrtDisc = Math.sqrt(disc);
            double t1 = (-b - sqrtDisc) / (2.0 * a);
            double t2 = (-b + sqrtDisc) / (2.0 * a);
            t = smallestPositive(t1, t2);
        }

        if (t <= 0.0 || !Double.isFinite(t)) {
            return toTarget;
        }

        return new Vector2(
                targetPos.x + targetVel.x * t - shooterPos.x,
                targetPos.y + targetVel.y * t - shooterPos.y);
    }

    private static double smallestPositive(double t1, double t2) {
        return t1 > 0.0 && t2 > 0.0
                ? Math.min(t1, t2)
                : Math.max(t1, t2);
    }
}

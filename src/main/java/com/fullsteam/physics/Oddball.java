package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.HasWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invincible NPC oddball that bounces around the map, shoots at players, and
 * awards points to anyone who hits it. Personality determines speed, damage,
 * and targeting behaviour. BehaviorMode is the extensible hook for future AI
 * strategies (flee/pursue/track/charge).
 */
@Getter
@Setter
public class Oddball extends GameEntity implements HasWeapon {

    public enum Personality {
        RAMPAGE,  // Slow, heavy, high DPS — primary scoring target
        SEEKER    // Fast, light, low DPS — persistent harasser
    }

    public enum BehaviorMode {
        ROAM,         // Wander with periodic random direction changes
        HUNT_LEADER,  // Chase the highest-scoring player (Seeker default)
        FLEE,         // Run from nearest threat
        CHARGE        // Barrel directly at a player cluster (Rampage default)
    }

    // ── Personality constants ──────────────────────────────────────────────────

    private static final double RAMPAGE_RADIUS = 20.0;
    private static final double SEEKER_RADIUS = 12.0;
    private static final double RAMPAGE_DENSITY = 3.0;
    private static final double SEEKER_DENSITY = 0.8;
    private static final double RAMPAGE_DAMPING = 1.5;
    private static final double SEEKER_DAMPING = 0.8;
    private static final double RAMPAGE_FORCE = 600_000.0;
    private static final double SEEKER_FORCE = 60_000.0;
    private static final double RAMPAGE_MAX_SPEED = 130.0;
    private static final double SEEKER_MAX_SPEED = 260.0;
    private static final double DETECTION_RANGE = 700.0;
    private static final double SEEKER_FLEE_RANGE = 120.0;
    private static final double ROAM_CHANGE_MIN = 2.5;
    private static final double ROAM_CHANGE_MAX = 5.5;
    private static final double MIN_SPEED = 5.0;

    // ── Weapon pool ────────────────────────────────────────────────────────────

    private static final Weapon[] SEEKER_WEAPONS = {
        WeaponConfig.SEEKER_PLASMA_WHIP_PRESET.buildWeapon(),
        WeaponConfig.SEEKER_SHOCK_BEAM_PRESET.buildWeapon(),
        WeaponConfig.SEEKER_SEAR_LANCE_PRESET.buildWeapon(),
    };

    private static final Weapon[] RAMPAGE_WEAPONS = {
        WeaponConfig.RAMPAGE_MORTAR_PRESET.buildWeapon(),
        WeaponConfig.RAMPAGE_INCENDIARY_SHELL_PRESET.buildWeapon(),
        WeaponConfig.RAMPAGE_FRAG_SHELL_PRESET.buildWeapon(),
    };

    // ── State ─────────────────────────────────────────────────────────────────

    private final Personality personality;
    private final double pointsMultiplier;
    private final Weapon weapon;
    private BehaviorMode behaviorMode = BehaviorMode.ROAM;
    private Player currentTarget;
    private Vector2 roamDirection;
    private double roamChangeTimer = 0.0;
    private double roamChangeInterval;
    private long lastShotTime = 0L;

    // ── Construction ──────────────────────────────────────────────────────────

    public Oddball(Personality personality, double x, double y) {
        super(Config.nextEntityId(), buildBody(personality, x, y), Double.MAX_VALUE);
        this.personality = personality;
        this.pointsMultiplier = personality == Personality.RAMPAGE ? 2.0 : 1.0;
        this.roamDirection = randomDirection();
        this.roamChangeInterval = randomInterval();
        Weapon[] pool = personality == Personality.RAMPAGE ? RAMPAGE_WEAPONS : SEEKER_WEAPONS;
        this.weapon = pool[ThreadLocalRandom.current().nextInt(pool.length)];

        double initSpeed = personality == Personality.RAMPAGE
                ? RAMPAGE_MAX_SPEED * 0.5
                : SEEKER_MAX_SPEED * 0.5;
        body.setLinearVelocity(roamDirection.copy().multiply(initSpeed));
    }

    private static Body buildBody(Personality personality, double x, double y) {
        Body body = new Body();
        double radius = personality == Personality.RAMPAGE ? RAMPAGE_RADIUS : SEEKER_RADIUS;
        double density = personality == Personality.RAMPAGE ? RAMPAGE_DENSITY : SEEKER_DENSITY;
        double damping = personality == Personality.RAMPAGE ? RAMPAGE_DAMPING : SEEKER_DAMPING;

        BodyFixture fixture = body.addFixture(new Circle(radius));
        fixture.setDensity(density);
        fixture.setRestitution(0.8);
        fixture.setFriction(0.1);
        body.setMass(MassType.NORMAL);

        body.setLinearDamping(damping);
        body.setAngularDamping(10.0);
        body.getTransform().setTranslation(x, y);
        return body;
    }

    // ── HasWeapon ─────────────────────────────────────────────────────────────

    @Override
    public Weapon getWeapon() {
        return weapon;
    }

    /**
     * Human-readable identifier for kill-feed attribution (e.g. "Rampage Oddball").
     */
    public String getDisplayName() {
        return switch (personality) {
            case RAMPAGE -> "Rampage Oddball";
            case SEEKER -> "Seeker Oddball";
        };
    }

    // ── Per-frame update (GameEntities.updateAll) ─────────────────────────────

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (!active) {
            return;
        }

        Vector2 curVel = body.getLinearVelocity();
        if (curVel.getMagnitude() > MIN_SPEED) {
            roamDirection = curVel.getNormalized();
        }

        roamChangeTimer += deltaTime;
        if (behaviorMode == BehaviorMode.ROAM && roamChangeTimer >= roamChangeInterval) {
            roamDirection = randomDirection();
            roamChangeInterval = randomInterval();
            roamChangeTimer = 0.0;
        }

        if (curVel.getMagnitude() < MIN_SPEED) {
            double kickSpeed = personality == Personality.RAMPAGE
                    ? RAMPAGE_MAX_SPEED * 0.4
                    : SEEKER_MAX_SPEED * 0.4;
            body.setLinearVelocity(randomDirection().multiply(kickSpeed));
        }
    }

    // ── AI tick (GameManager.updateUtilityEntities) ───────────────────────────

    /**
     * Decide targeting / movement and apply steering force.
     * Called by GameManager once per game loop tick.
     */
    public void tickAI(Collection<Player> players) {
        if (!active) {
            return;
        }

        Player nearest = nearestPlayer(players);

        switch (personality) {
            case RAMPAGE -> tickRampage(players, nearest);
            case SEEKER -> tickSeeker(players, nearest);
        }

        double force = personality == Personality.RAMPAGE ? RAMPAGE_FORCE : SEEKER_FORCE;
        double maxSpeed = personality == Personality.RAMPAGE ? RAMPAGE_MAX_SPEED : SEEKER_MAX_SPEED;

        body.applyForce(roamDirection.copy().multiply(force));

        Vector2 vel = body.getLinearVelocity();
        if (vel.getMagnitude() > maxSpeed) {
            body.setLinearVelocity(vel.getNormalized().multiply(maxSpeed));
        }
    }

    private void tickRampage(Collection<Player> players, Player nearest) {
        if (nearest == null) {
            behaviorMode = BehaviorMode.ROAM;
            currentTarget = null;
            return;
        }
        behaviorMode = BehaviorMode.CHARGE;
        currentTarget = nearest;
        Vector2 cluster = clusterCenter(players);
        Vector2 targetDir = directionTo(cluster);
        roamDirection = roamDirection.copy().multiply(0.4).add(targetDir.multiply(0.6)).getNormalized();
    }

    private void tickSeeker(Collection<Player> players, Player nearest) {
        if (nearest == null) {
            behaviorMode = BehaviorMode.ROAM;
            currentTarget = null;
            return;
        }

        double distToNearest = getPosition().distance(nearest.getPosition());

        if (distToNearest < SEEKER_FLEE_RANGE) {
            behaviorMode = BehaviorMode.FLEE;
            currentTarget = nearest;
            Vector2 awayDir = directionTo(nearest.getPosition()).multiply(-1.0);
            roamDirection = roamDirection.copy().multiply(0.3).add(awayDir.multiply(0.7)).getNormalized();
            return;
        }

        Player leader = scoreLeader(players);
        behaviorMode = BehaviorMode.HUNT_LEADER;
        currentTarget = leader != null ? leader : nearest;
        Vector2 targetDir = directionTo(currentTarget.getPosition());
        roamDirection = roamDirection.copy().multiply(0.5).add(targetDir.multiply(0.5)).getNormalized();
    }

    /**
     * Attempt to fire at the current target using the NPC's assigned weapon.
     * Rate-gated by weapon fire rate; returns null if not ready or no target.
     * Returned entity has ownerId = -getId() (NPC sentinel, never matches a player)
     * and ownerTeam = 0 (damages all teams equally).
     */
    public GameEntity tryFire() {
        if (currentTarget == null || !currentTarget.isActive()) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastShotTime < (long) (1000.0 / weapon.getFireRate())) {
            return null;
        }
        lastShotTime = now;

        Vector2 myPos = getPosition();
        Vector2 targetPos = currentTarget.getPosition();
        boolean beamType = weapon.getOrdinance().isBeamType();

        // Beams are hitscan, so aim straight at the target. Projectiles travel at a
        // finite speed, so lead the target based on its velocity to intercept it.
        Vector2 dir = beamType
                ? new Vector2(targetPos.x - myPos.x, targetPos.y - myPos.y)
                : predictInterceptDirection(myPos, targetPos, currentTarget.getVelocity(), weapon.getProjectileSpeed());
        if (dir == null || dir.getMagnitude() == 0) {
            return null;
        }
        dir.normalize();

        double spread = (1.0 - weapon.getAccuracy()) * 0.17;
        double angleOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * spread;
        double angle = Math.atan2(dir.y, dir.x) + angleOffset;
        Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));

        if (beamType) {
            return fireBeam(myPos, aimDir);
        } else {
            return fireProjectile(myPos, aimDir);
        }
    }

    /**
     * Compute the aim direction that leads a moving target so a projectile fired at
     * {@code projectileSpeed} intercepts it. Solves the quadratic for the earliest
     * positive intercept time; falls back to aiming at the target's current position
     * when no valid intercept exists (e.g. target outrunning the projectile).
     */
    private Vector2 predictInterceptDirection(Vector2 shooterPos, Vector2 targetPos, Vector2 targetVel, double projectileSpeed) {
        Vector2 toTarget = new Vector2(targetPos.x - shooterPos.x, targetPos.y - shooterPos.y);
        if (projectileSpeed <= 0.0) {
            return toTarget; // no meaningful travel time; aim directly
        }

        // Solve |toTarget + targetVel * t| = projectileSpeed * t for the smallest t > 0.
        double a = targetVel.dot(targetVel) - projectileSpeed * projectileSpeed;
        double b = 2.0 * toTarget.dot(targetVel);
        double c = toTarget.dot(toTarget);

        double t;
        if (Math.abs(a) < 1e-6) {
            // Target speed ~= projectile speed: quadratic degenerates to linear.
            if (Math.abs(b) < 1e-6) {
                return toTarget;
            }
            t = -c / b;
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc < 0.0) {
                return toTarget; // no real intercept
            }
            double sqrtDisc = Math.sqrt(disc);
            double t1 = (-b - sqrtDisc) / (2.0 * a);
            double t2 = (-b + sqrtDisc) / (2.0 * a);
            // Prefer the earliest positive intercept time.
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
        if (t1 > 0.0 && t2 > 0.0) {
            return Math.min(t1, t2);
        }
        return Math.max(t1, t2);
    }

    private Beam fireBeam(Vector2 pos, Vector2 dir) {
        Vector2 endPoint = pos.copy().add(dir.copy().multiply(weapon.getRange()));
        Beam beam = new Beam(
                pos,
                dir,
                weapon.getRange(),
                weapon.getDamage(),
                -getId(),
                0,
                weapon.getOrdinance(),
                weapon.getBulletEffects(),
                weapon.getCaliber()
        );
        beam.setPath(List.of(pos.copy(), endPoint));
        return beam;
    }

    private Projectile fireProjectile(Vector2 pos, Vector2 dir) {
        Vector2 vel = dir.copy().multiply(weapon.getProjectileSpeed());
        return new Projectile(
                -getId(),
                pos.x, pos.y,
                vel.x, vel.y,
                weapon.getDamagePerBullet(),
                weapon.getRange(),
                0,
                0.0,
                weapon.getBulletEffects(),
                weapon.getOrdinance(),
                weapon.getCaliber(),
                weapon.getKnockbackPerBullet()
        );
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Player nearestPlayer(Collection<Player> players) {
        Player nearest = null;
        double nearestDist = DETECTION_RANGE;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            double d = myPos.distance(p.getPosition());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private Vector2 clusterCenter(Collection<Player> players) {
        double sumX = 0, sumY = 0;
        int count = 0;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            if (myPos.distance(p.getPosition()) < DETECTION_RANGE) {
                sumX += p.getPosition().x;
                sumY += p.getPosition().y;
                count++;
            }
        }
        return count > 0 ? new Vector2(sumX / count, sumY / count) : myPos.copy();
    }

    private Player scoreLeader(Collection<Player> players) {
        Player leader = null;
        double bestScore = -1;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            if (myPos.distance(p.getPosition()) >= DETECTION_RANGE) {
                continue;
            }
            double score = p.getScoring().getOddball() + p.getScoring().getKills();
            if (score > bestScore) {
                bestScore = score;
                leader = p;
            }
        }
        return leader;
    }

    private Vector2 directionTo(Vector2 target) {
        Vector2 myPos = getPosition();
        Vector2 d = new Vector2(target.x - myPos.x, target.y - myPos.y);
        return d.getMagnitude() > 0 ? d.getNormalized() : randomDirection();
    }

    private static Vector2 randomDirection() {
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        return new Vector2(Math.cos(angle), Math.sin(angle));
    }

    private static double randomInterval() {
        return ROAM_CHANGE_MIN
                + ThreadLocalRandom.current().nextDouble() * (ROAM_CHANGE_MAX - ROAM_CHANGE_MIN);
    }
}

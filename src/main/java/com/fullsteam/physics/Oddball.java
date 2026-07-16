package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invincible NPC oddball that bounces around the map, shoots at players, and
 * awards points to anyone who hits it. Personality determines speed, damage,
 * and targeting behaviour. BehaviorMode is the extensible hook for future AI
 * strategies (flee/pursue/track/charge).
 */
@Getter
@Setter
public class Oddball extends GameEntity {

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
    private static final double SEEKER_FLEE_RANGE = 120.0;  // SEEKER backs away when player is closer than this
    private static final double ROAM_CHANGE_MIN = 2.5;    // seconds between direction changes
    private static final double ROAM_CHANGE_MAX = 5.5;
    private static final double MIN_SPEED = 5.0;    // below this we consider stalled

    // ── Weapon presets ────────────────────────────────────────────────────────

    private enum WeaponPreset {
        // SEEKER: sustained plasma beams with status effects — fires while fleeing or hunting
        PLASMA_WHIP(
                Ordinance.PLASMA_BEAM, Set.of(BulletEffect.FREEZING),
                460.0, 10.0, 1.8, 1.0, 0.0, 0.04),
        SHOCK_BEAM(
                Ordinance.PLASMA_BEAM, Set.of(BulletEffect.ELECTRIC),
                400.0, 12.0, 1.4, 1.1, 0.0, 0.03),
        SEAR_LANCE(
                Ordinance.PLASMA_BEAM, Set.of(BulletEffect.INCENDIARY),
                500.0, 8.0, 2.0, 1.0, 0.0, 0.05),

        // RAMPAGE: slow, heavy explosive projectiles — area denial / burst damage
        MORTAR(
                Ordinance.PROJECTILE, Set.of(BulletEffect.EXPLOSIVE),
                0.0, 42.0, 3.0, 1.6, 160.0, 0.07),
        INCENDIARY_SHELL(
                Ordinance.PROJECTILE, Set.of(BulletEffect.INCENDIARY),
                0.0, 28.0, 2.2, 1.2, 220.0, 0.05),
        FRAG_SHELL(
                Ordinance.PROJECTILE, Set.of(BulletEffect.FRAGMENTING, BulletEffect.EXPLOSIVE),
                0.0, 22.0, 2.5, 1.0, 200.0, 0.06);

        final Ordinance ordinance;
        final Set<BulletEffect> effects;
        final double beamRange;   // 0 for projectiles
        final double damage;      // per-second for beams; per-hit for projectiles
        final double cooldown;    // seconds between shots
        final double caliber;
        final double projSpeed;   // 0 for beams
        final double inaccuracy;  // angle spread in radians

        WeaponPreset(Ordinance ordinance, Set<BulletEffect> effects,
                     double beamRange, double damage, double cooldown,
                     double caliber, double projSpeed, double inaccuracy) {
            this.ordinance = ordinance;
            this.effects = effects;
            this.beamRange = beamRange;
            this.damage = damage;
            this.cooldown = cooldown;
            this.caliber = caliber;
            this.projSpeed = projSpeed;
            this.inaccuracy = inaccuracy;
        }
    }

    private static final WeaponPreset[] SEEKER_PRESETS =
            {WeaponPreset.PLASMA_WHIP, WeaponPreset.SHOCK_BEAM, WeaponPreset.SEAR_LANCE};
    private static final WeaponPreset[] RAMPAGE_PRESETS =
            {WeaponPreset.MORTAR, WeaponPreset.INCENDIARY_SHELL, WeaponPreset.FRAG_SHELL};

    // ── State ─────────────────────────────────────────────────────────────────

    private final Personality personality;
    private final double pointsMultiplier;   // damage × this = oddball score awarded per hit
    private final WeaponPreset weaponPreset;
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
        WeaponPreset[] pool = personality == Personality.RAMPAGE ? RAMPAGE_PRESETS : SEEKER_PRESETS;
        this.weaponPreset = pool[ThreadLocalRandom.current().nextInt(pool.length)];

        // Kick-start movement so the ball isn't stationary at spawn
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

    // ── Per-frame update (GameEntities.updateAll) ─────────────────────────────

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (!active) return;

        // Sync roamDirection from actual post-physics velocity so wall/obstacle bounces
        // redirect the AI regardless of current behavior mode
        Vector2 curVel = body.getLinearVelocity();
        if (curVel.getMagnitude() > MIN_SPEED) {
            roamDirection = curVel.getNormalized();
        }

        // Periodic random turns while roaming
        roamChangeTimer += deltaTime;
        if (behaviorMode == BehaviorMode.ROAM && roamChangeTimer >= roamChangeInterval) {
            roamDirection = randomDirection();
            roamChangeInterval = randomInterval();
            roamChangeTimer = 0.0;
        }

        // Anti-stall: if nearly stopped, give a random kick
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
        if (!active) return;

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

    /**
     * RAMPAGE behavior: CHARGE toward the center of the nearest player cluster.
     * Targeting the cluster centroid (rather than one player) maximizes area impact.
     * Blend is weighted toward the target so the heavy ball commits to its charge.
     */
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
        // 40% momentum preservation, 60% committed to target
        roamDirection = roamDirection.copy().multiply(0.4).add(targetDir.multiply(0.6)).getNormalized();
    }

    /**
     * SEEKER behavior:
     * - FLEE when a player is within SEEKER_FLEE_RANGE — back away while still firing.
     * - HUNT_LEADER otherwise — chase the highest-scoring player in range.
     * The flee direction is heavily weighted so the nimble ball actually escapes.
     */
    private void tickSeeker(Collection<Player> players, Player nearest) {
        if (nearest == null) {
            behaviorMode = BehaviorMode.ROAM;
            currentTarget = null;
            return;
        }

        double distToNearest = getPosition().distance(nearest.getPosition());

        if (distToNearest < SEEKER_FLEE_RANGE) {
            behaviorMode = BehaviorMode.FLEE;
            currentTarget = nearest; // still fires at the threat while fleeing
            // direction away from the incoming player, heavily weighted
            Vector2 awayDir = directionTo(nearest.getPosition()).multiply(-1.0);
            roamDirection = roamDirection.copy().multiply(0.3).add(awayDir.multiply(0.7)).getNormalized();
            return;
        }

        // Hunt the score leader — the player accumulating the most oddball points
        Player leader = scoreLeader(players);
        behaviorMode = BehaviorMode.HUNT_LEADER;
        currentTarget = leader != null ? leader : nearest;
        Vector2 targetDir = directionTo(currentTarget.getPosition());
        roamDirection = roamDirection.copy().multiply(0.5).add(targetDir.multiply(0.5)).getNormalized();
    }

    /**
     * Attempt to fire a weapon at the current target using the NPC's randomized preset.
     * Rate-gated by the preset cooldown; returns null if not ready or no target.
     * Returned entity has ownerId = -getId() (NPC sentinel, never matches a player)
     * and ownerTeam = 0 (damages all teams equally).
     */
    public GameEntity tryFire() {
        if (currentTarget == null || !currentTarget.isActive()) return null;

        long now = System.currentTimeMillis();
        if (now - lastShotTime < (long) (weaponPreset.cooldown * 1000.0)) return null;
        lastShotTime = now;

        Vector2 myPos = getPosition();
        Vector2 targetPos = currentTarget.getPosition();
        Vector2 dir = new Vector2(targetPos.x - myPos.x, targetPos.y - myPos.y);
        if (dir.getMagnitude() == 0) return null;
        dir.normalize();

        double angle = Math.atan2(dir.y, dir.x)
                + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * weaponPreset.inaccuracy;
        Vector2 aimDir = new Vector2(Math.cos(angle), Math.sin(angle));

        if (weaponPreset.ordinance.isBeamType()) {
            Vector2 endPoint = myPos.copy().add(aimDir.copy().multiply(weaponPreset.beamRange));
            Beam beam = new Beam(
                    myPos,
                    aimDir,
                    weaponPreset.beamRange,
                    weaponPreset.damage,
                    -getId(),               // negative sentinel → no kill-credit match
                    0,                      // ownerTeam 0 → damages all teams
                    weaponPreset.ordinance,
                    weaponPreset.effects,
                    weaponPreset.caliber
            );
            // Path must be set so GameManager's getPlayersInBeamPath raycast can find targets.
            // Player-fired beams get this from WeaponSystem.computeBeamPath; NPC beams set it here.
            beam.setPath(List.of(myPos.copy(), endPoint));
            return beam;
        } else {
            Vector2 vel = aimDir.copy().multiply(weaponPreset.projSpeed);
            return new Projectile(
                    -getId(),
                    myPos.x, myPos.y,
                    vel.x, vel.y,
                    weaponPreset.damage,
                    DETECTION_RANGE * 1.1,
                    0,                      // ownerTeam 0 → damages all teams
                    0.0,
                    weaponPreset.effects,
                    weaponPreset.ordinance,
                    weaponPreset.caliber,
                    0.0
            );
        }
    }

    public double getRadius() {
        return getBody().getRotationDiscRadius();
    }

    /**
     * Returns the closest active player within DETECTION_RANGE, or null.
     */
    private Player nearestPlayer(Collection<Player> players) {
        Player nearest = null;
        double nearestDist = DETECTION_RANGE;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) continue;
            double d = myPos.distance(p.getPosition());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * Returns the centroid of all active players within DETECTION_RANGE.
     * RAMPAGE uses this so it charges toward the densest group rather than
     * being kited by a single player.
     */
    private Vector2 clusterCenter(Collection<Player> players) {
        double sumX = 0, sumY = 0;
        int count = 0;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) continue;
            if (myPos.distance(p.getPosition()) < DETECTION_RANGE) {
                sumX += p.getPosition().x;
                sumY += p.getPosition().y;
                count++;
            }
        }
        return count > 0 ? new Vector2(sumX / count, sumY / count) : myPos.copy();
    }

    /**
     * Returns the highest-scoring active player within DETECTION_RANGE, ranked
     * by oddball points (the primary currency in NPC oddball mode) plus kills as
     * a tiebreaker. Returns null if no players are in range.
     */
    private Player scoreLeader(Collection<Player> players) {
        Player leader = null;
        double bestScore = -1;
        Vector2 myPos = getPosition();
        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) continue;
            if (myPos.distance(p.getPosition()) >= DETECTION_RANGE) continue;
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

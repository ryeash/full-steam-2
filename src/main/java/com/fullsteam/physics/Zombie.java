package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieType;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hostile NPC Zombie that moves via dynamic physics forces (similar to {@link Player}),
 * navigates toward targets with specialized attack patterns, lunges when close,
 * and inflicts melee damage on contact.
 */
@Getter
@Setter
public class Zombie extends OwnedGameEntity implements Damageable, MeleeAttacker {

    private final ZombieType type;
    private ZombieAttackPattern attackPattern;

    private double baseSpeed;
    private double maxSpeed;
    private double acceleration;
    private double brakingForce;
    private double lungeDistance;
    private double lungeSpeedMultiplier;
    private boolean isLunging = false;

    private double meleeDamage;
    private double meleeCooldownSeconds;
    private long lastMeleeAttackTime = 0L;

    private Vector2 aimDirection = new Vector2(1, 0);

    private Integer targetEntityId = null;
    private double retargetTimer = 0.0;
    private Vector2 wanderDirection = new Vector2(0, 0);
    private double wanderTimer = 0.0;

    public Zombie(int id, ZombieType type, ZombieAttackPattern attackPattern, double x, double y) {
        super(id, createZombieBody(x, y, type.getRadius()), type.getDefaultHealth(), id, 0);
        this.type = type;
        this.attackPattern = attackPattern != null ? attackPattern : ZombieAttackPattern.NEAREST_PLAYER;

        // Apply variance to individual zombie attributes
        double speedVar = 0.88 + ThreadLocalRandom.current().nextDouble() * 0.24; // 88% - 112%
        this.baseSpeed = type.getDefaultSpeed() * speedVar;
        this.maxSpeed = this.baseSpeed;

        double accelVar = 0.90 + ThreadLocalRandom.current().nextDouble() * 0.20;
        this.acceleration = Config.PLAYER_ACCELERATION * accelVar;
        this.brakingForce = Config.PLAYER_BRAKING_FORCE;

        double lungeVar = 0.85 + ThreadLocalRandom.current().nextDouble() * 0.30;
        this.lungeDistance = type.getDefaultLungeDistance() * lungeVar;
        this.lungeSpeedMultiplier = type.getLungeSpeedMultiplier();

        double dmgVar = 0.90 + ThreadLocalRandom.current().nextDouble() * 0.20;
        this.meleeDamage = type.getDefaultMeleeDamage() * dmgVar;
        this.meleeCooldownSeconds = type.getMeleeCooldownSeconds();

        this.retargetTimer = ThreadLocalRandom.current().nextDouble() * 2.0;
    }

    public Zombie(ZombieType type, double x, double y) {
        this(Config.nextEntityId(), type, selectRandomAttackPattern(), x, y);
    }

    public static ZombieAttackPattern selectRandomAttackPattern() {
        ZombieAttackPattern[] patterns = ZombieAttackPattern.values();
        return patterns[ThreadLocalRandom.current().nextInt(patterns.length)];
    }

    public static ZombieType selectRandomZombieType() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 45) {
            return ZombieType.WALKER; // 45%
        } else if (roll < 70) {
            return ZombieType.RUNNER; // 25%
        } else if (roll < 85) {
            return ZombieType.LUNGER; // 15%
        } else if (roll < 95) {
            return ZombieType.TANK;   // 10%
        } else {
            return ZombieType.STALKER; // 5%
        }
    }

    private static Body createZombieBody(double x, double y, double radius) {
        Body body = new Body();
        Circle circle = new Circle(radius);
        BodyFixture fixture = body.addFixture(circle);
        fixture.setDensity(1.0);
        fixture.setFriction(0.2);
        fixture.setRestitution(0.2);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);

        body.setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
        body.setAngularDamping(Config.PLAYER_ANGULAR_DAMPING);
        body.setAngularVelocity(0.0);
        return body;
    }

    public double getRadius() {
        return type.getRadius();
    }

    public String getDisplayName() {
        return switch (type) {
            case WALKER -> "Walker Zombie";
            case RUNNER -> "Runner Zombie";
            case TANK -> "Tank Zombie";
            case LUNGER -> "Lunger Zombie";
            case STALKER -> "Stalker Zombie";
        };
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (!active) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Physics-based movement using forces for acceleration, deceleration, and lunging.
     * Matches the PD-controller mechanics in Player.java.
     */
    public void processMovement(Vector2 moveVector) {
        if (!active) {
            return;
        }

        if (moveVector != null && moveVector.getMagnitude() > 0.001) {
            Vector2 norm = moveVector.getNormalized();
            double targetSpeed = isLunging ? (maxSpeed * lungeSpeedMultiplier) : maxSpeed;
            Vector2 targetVelocity = norm.multiply(targetSpeed);
            Vector2 currentVelocity = getVelocity();
            Vector2 velocityDiff = targetVelocity.subtract(currentVelocity);

            Vector2 force = velocityDiff.multiply(acceleration);
            body.applyForce(force);
        } else {
            Vector2 currentVelocity = getVelocity();
            if (currentVelocity.getMagnitude() > 1.0) {
                Vector2 braking = currentVelocity.multiply(-brakingForce);
                body.applyForce(braking);
            }
        }
    }

    public void setAimDirection(Vector2 direction) {
        if (direction != null && direction.getMagnitude() > 0.001) {
            this.aimDirection = direction.getNormalized();
            setRotation(Math.atan2(this.aimDirection.y, this.aimDirection.x));
            body.setAngularVelocity(0.0);
        }
    }

    public boolean canMeleeAttack() {
        if (!active || health <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        return (now - lastMeleeAttackTime) >= (long) (meleeCooldownSeconds * 1000.0);
    }

    public void recordMeleeAttack() {
        this.lastMeleeAttackTime = System.currentTimeMillis();
    }

    /**
     * AI tick: targeting, pathing, lunge detection, and force-based movement.
     */
    public void tickAI(GameEntities gameEntities, double deltaTime) {
        if (!active || health <= 0) {
            return;
        }

        retargetTimer -= deltaTime;
        Vector2 targetPosition = evaluateTargetPosition(gameEntities);

        Vector2 myPos = getPosition();

        if (targetPosition != null) {
            Vector2 delta = targetPosition.copy().subtract(myPos);
            double distance = delta.getMagnitude();

            // Check if within lunger distance threshold for speed surge
            this.isLunging = distance <= lungeDistance;

            if (distance > 5.0) {
                Vector2 moveDir = delta.getNormalized();

                // Add slight wandering jitter to avoid perfect linear stacking
                double jitter = Math.sin(System.currentTimeMillis() / 300.0 + id) * 0.2;
                Vector2 perp = new Vector2(-moveDir.y, moveDir.x);
                moveDir.add(perp.multiply(jitter)).normalize();

                setAimDirection(moveDir);
                processMovement(moveDir);
            } else {
                processMovement(new Vector2(0, 0));
            }
        } else {
            // No target found; wander
            this.isLunging = false;
            wanderTimer -= deltaTime;
            if (wanderTimer <= 0) {
                double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                wanderDirection = new Vector2(Math.cos(angle), Math.sin(angle));
                wanderTimer = 2.0 + ThreadLocalRandom.current().nextDouble() * 3.0;
            }
            setAimDirection(wanderDirection);
            processMovement(wanderDirection.copy().multiply(0.4));
        }
    }

    private Vector2 evaluateTargetPosition(GameEntities gameEntities) {
        Collection<Player> players = gameEntities.getAllPlayers();
        Collection<Headquarters> hqs = gameEntities.getAllHeadquarters();
        Collection<Turret> turrets = gameEntities.getAllTurrets();

        // 1. If obsessed with a specific player/entity, keep tracking it until dead
        if (attackPattern == ZombieAttackPattern.OBSESSED_PLAYER && targetEntityId != null) {
            Player obsessedPlayer = gameEntities.getPlayer(targetEntityId);
            if (obsessedPlayer != null && obsessedPlayer.isActive() && obsessedPlayer.getHealth() > 0) {
                return obsessedPlayer.getPosition();
            } else {
                targetEntityId = null; // Obsession ended, acquire new target
            }
        }

        // 2. Select target based on attack pattern
        return switch (attackPattern) {
            case HEADQUARTERS -> {
                Headquarters hq = findNearestHeadquarters(hqs);
                if (hq != null) {
                    yield hq.getPosition();
                }
                yield findNearestPlayerPosition(players);
            }
            case TURRET -> {
                Turret turret = findNearestTurret(turrets);
                if (turret != null) {
                    yield turret.getPosition();
                }
                yield findNearestPlayerPosition(players);
            }
            case LOWEST_HEALTH -> {
                Player lowest = findLowestHealthPlayer(players);
                if (lowest != null) {
                    yield lowest.getPosition();
                }
                yield findNearestPlayerPosition(players);
            }
            case SWARM_CLUSTER -> {
                Vector2 cluster = findClusterCenter(players, hqs);
                if (cluster != null) {
                    yield cluster;
                }
                yield findNearestPlayerPosition(players);
            }
            case OBSESSED_PLAYER -> {
                Player nearest = findNearestPlayer(players);
                if (nearest != null) {
                    this.targetEntityId = nearest.getId();
                    yield nearest.getPosition();
                }
                yield null;
            }
            case NEAREST_PLAYER -> findNearestPlayerPosition(players);
        };
    }

    private Vector2 findNearestPlayerPosition(Collection<Player> players) {
        Player p = findNearestPlayer(players);
        return p != null ? p.getPosition() : null;
    }

    private Player findNearestPlayer(Collection<Player> players) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            double dist = myPos.distance(p.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private Headquarters findNearestHeadquarters(Collection<Headquarters> hqs) {
        Headquarters nearest = null;
        double minDist = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Headquarters hq : hqs) {
            if (!hq.isActive() || hq.getHealth() <= 0) {
                continue;
            }
            double dist = myPos.distance(hq.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = hq;
            }
        }
        return nearest;
    }

    private Turret findNearestTurret(Collection<Turret> turrets) {
        Turret nearest = null;
        double minDist = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Turret t : turrets) {
            if (!t.isActive() || t.getHealth() <= 0) {
                continue;
            }
            double dist = myPos.distance(t.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = t;
            }
        }
        return nearest;
    }

    private Player findLowestHealthPlayer(Collection<Player> players) {
        Player lowest = null;
        double lowestHealth = Double.MAX_VALUE;

        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            if (p.getHealth() < lowestHealth) {
                lowestHealth = p.getHealth();
                lowest = p;
            }
        }
        return lowest;
    }

    private Vector2 findClusterCenter(Collection<Player> players, Collection<Headquarters> hqs) {
        double sumX = 0, sumY = 0;
        int count = 0;

        for (Player p : players) {
            if (p.isActive() && p.getHealth() > 0) {
                sumX += p.getPosition().x;
                sumY += p.getPosition().y;
                count++;
            }
        }

        for (Headquarters hq : hqs) {
            if (hq.isActive() && hq.getHealth() > 0) {
                sumX += hq.getPosition().x * 2.0; // Weighted higher
                sumY += hq.getPosition().y * 2.0;
                count += 2;
            }
        }

        if (count > 0) {
            return new Vector2(sumX / count, sumY / count);
        }
        return null;
    }
}

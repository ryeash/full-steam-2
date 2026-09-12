package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.HasWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
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
public class Zombie extends OwnedGameEntity implements Damageable, MeleeAttacker, HasWeapon {

    public static final double BOOMER_WARNING_RADIUS = 110.0;
    public static final double BOOMER_WARNING_DURATION_SECONDS = 1.5;
    public static final double BOOMER_POISON_BASE_RADIUS = 55.0; // expands to 110.0 via FieldEffectType.POISON.maxRadius
    public static final double BOOMER_POISON_DAMAGE = 45.0;
    public static final double BOOMER_POISON_DURATION_SECONDS = 4.0;
    public static final double ZOMBIE_FIRE_RATE_PENALTY = 1.15;

    private final ZombieType type;
    private ZombieAttackPattern attackPattern;

    private final Weapon weapon;
    private long lastShotTime = 0L;
    private boolean deathHandled = false;

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

        if (this.type == ZombieType.SPITTER) {
            this.weapon = WeaponConfig.SPITTER_SPIT_PRESET.buildWeapon();
        } else {
            this.weapon = null;
        }

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
        if (roll < 35) {
            return ZombieType.WALKER; // 35%
        } else if (roll < 55) {
            return ZombieType.RUNNER; // 20%
        } else if (roll < 70) {
            return ZombieType.LUNGER; // 15%
        } else if (roll < 80) {
            return ZombieType.TANK;   // 10%
        } else if (roll < 90) {
            return ZombieType.BOOMER; // 10%
        } else if (roll < 97) {
            return ZombieType.SPITTER;// 7%
        } else {
            return ZombieType.STALKER;// 3%
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
            case BOOMER -> "Boomer Zombie";
            case SPITTER -> "Spitter Zombie";
        };
    }

    public void onDeath(GameEntities gameEntities) {
        if (deathHandled) {
            return;
        }
        deathHandled = true;

        if (type == ZombieType.BOOMER) {
            triggerBoomerExplosion(gameEntities);
        }
    }

    private void triggerBoomerExplosion(GameEntities gameEntities) {
        if (gameEntities == null) {
            return;
        }
        Vector2 pos = getPosition().copy();
        int ownerId = -getId();
        int ownerTeam = getOwnerTeam();

        long armingTime = System.currentTimeMillis() + (long) (BOOMER_WARNING_DURATION_SECONDS * 1000);

        // 1. Telegraph: Warning zone indicating it is about to explode
        gameEntities.add(new FieldEffectCircle(
                ownerId,
                FieldEffectType.WARNING_ZONE,
                pos.copy(),
                BOOMER_WARNING_RADIUS,
                BOOMER_WARNING_RADIUS,
                0.0,
                BOOMER_WARNING_DURATION_SECONDS,
                0,
                ownerTeam
        ));

        // 2. Delayed poison explosion effect where the arm time matches the warning zone time
        double poisonMaxRadius = FieldEffectType.POISON.maxRadius(BOOMER_POISON_BASE_RADIUS);
        gameEntities.add(new FieldEffectCircle(
                ownerId,
                FieldEffectType.POISON,
                pos.copy(),
                BOOMER_POISON_BASE_RADIUS,
                poisonMaxRadius,
                BOOMER_POISON_DAMAGE,
                BOOMER_POISON_DURATION_SECONDS,
                armingTime,
                ownerTeam
        ));
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

    public boolean canFire() {
        if (!active || health <= 0 || weapon == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double effectiveFireRate = Math.max(0.1, weapon.getFireRate()) / ZOMBIE_FIRE_RATE_PENALTY;
        double fireInterval = 1000.0 / effectiveFireRate;
        return (now - lastShotTime) >= (long) fireInterval;
    }

    private OwnedGameEntity currentTargetEntity;
    private Vector2 staticTargetPosition;
    private transient com.fullsteam.ai.AITargetWrapper targetWrapper;

    public com.fullsteam.ai.AITargetWrapper getTargetWrapper() {
        if (targetWrapper == null) {
            targetWrapper = com.fullsteam.ai.AITargetWrapper.createDirect(this, com.fullsteam.ai.AITargetWrapper.TargetType.ZOMBIE);
        }
        return targetWrapper;
    }

    /**
     * AI tick: targeting, pathing, lunge detection, and force-based movement.
     */
    public void tickAI(GameEntities gameEntities, double deltaTime) {
        if (!active || health <= 0) {
            return;
        }

        retargetTimer -= deltaTime;
        boolean targetValid = currentTargetEntity != null && currentTargetEntity.isActive() && currentTargetEntity.getHealth() > 0;

        if (attackPattern == ZombieAttackPattern.OBSESSED_PLAYER) {
            if (!targetValid) {
                targetEntityId = null;
                acquireTarget(gameEntities);
            }
        } else if (!targetValid || retargetTimer <= 0) {
            acquireTarget(gameEntities);
            retargetTimer = 0.25 + ThreadLocalRandom.current().nextDouble() * 0.15;
        }

        Vector2 targetPosition = null;
        if (currentTargetEntity != null && currentTargetEntity.isActive() && currentTargetEntity.getHealth() > 0) {
            targetPosition = currentTargetEntity.getPosition();
        } else if (staticTargetPosition != null) {
            targetPosition = staticTargetPosition;
        }

        Vector2 myPos = getPosition();

        if (targetPosition != null) {
            Vector2 delta = targetPosition.copy().subtract(myPos);
            double distance = delta.getMagnitude();

            if (type == ZombieType.SPITTER) {
                // Spitter behavior: keep distance between 350 and 600 units while aiming at target
                this.isLunging = false;
                Vector2 targetDir = delta.getNormalized();
                setAimDirection(targetDir);

                double preferredMin = 350.0;
                double preferredMax = 600.0;

                if (distance > preferredMax) {
                    processMovement(targetDir);
                } else if (distance < preferredMin) {
                    processMovement(targetDir.copy().negate());
                } else {
                    double strafeSign = (id % 2 == 0) ? 1.0 : -1.0;
                    Vector2 strafeDir = new Vector2(-targetDir.y * strafeSign, targetDir.x * strafeSign);
                    processMovement(strafeDir.multiply(0.4));
                }
            } else {
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

    private void acquireTarget(GameEntities gameEntities) {
        Collection<Player> players = gameEntities.getAllPlayers();
        Collection<Headquarters> hqs = gameEntities.getAllHeadquarters();
        Collection<Turret> turrets = gameEntities.getAllTurrets();

        currentTargetEntity = null;
        staticTargetPosition = null;

        // 1. If obsessed with a specific player/entity, keep tracking it until dead
        if (attackPattern == ZombieAttackPattern.OBSESSED_PLAYER && targetEntityId != null) {
            Player obsessedPlayer = gameEntities.getPlayer(targetEntityId);
            if (obsessedPlayer != null && obsessedPlayer.isActive() && obsessedPlayer.getHealth() > 0) {
                currentTargetEntity = obsessedPlayer;
                return;
            } else {
                targetEntityId = null;
            }
        }

        // 2. Select target based on attack pattern
        switch (attackPattern) {
            case HEADQUARTERS -> {
                Headquarters hq = findNearestHeadquarters(hqs);
                if (hq != null) {
                    currentTargetEntity = hq;
                } else {
                    currentTargetEntity = findNearestPlayer(players);
                }
            }
            case TURRET -> {
                Turret turret = findNearestTurret(turrets);
                if (turret != null) {
                    currentTargetEntity = turret;
                } else {
                    currentTargetEntity = findNearestPlayer(players);
                }
            }
            case LOWEST_HEALTH -> {
                Player lowest = findLowestHealthPlayer(players);
                if (lowest != null) {
                    currentTargetEntity = lowest;
                } else {
                    currentTargetEntity = findNearestPlayer(players);
                }
            }
            case SWARM_CLUSTER -> {
                Vector2 cluster = findClusterCenter(players, hqs);
                if (cluster != null) {
                    staticTargetPosition = cluster;
                } else {
                    currentTargetEntity = findNearestPlayer(players);
                }
            }
            case OBSESSED_PLAYER -> {
                Player nearest = findNearestPlayer(players);
                if (nearest != null) {
                    this.targetEntityId = nearest.getId();
                    this.currentTargetEntity = nearest;
                }
            }
            case NEAREST_PLAYER -> {
                currentTargetEntity = findNearestPlayer(players);
            }
        }
    }

    private Player findNearestPlayer(Collection<Player> players) {
        Player nearest = null;
        double minDistSq = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Player p : players) {
            if (!p.isActive() || p.getHealth() <= 0) {
                continue;
            }
            double distSq = myPos.distanceSquared(p.getPosition());
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private Headquarters findNearestHeadquarters(Collection<Headquarters> hqs) {
        Headquarters nearest = null;
        double minDistSq = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Headquarters hq : hqs) {
            if (!hq.isActive() || hq.getHealth() <= 0) {
                continue;
            }
            double distSq = myPos.distanceSquared(hq.getPosition());
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = hq;
            }
        }
        return nearest;
    }

    private Turret findNearestTurret(Collection<Turret> turrets) {
        Turret nearest = null;
        double minDistSq = Double.MAX_VALUE;
        Vector2 myPos = getPosition();

        for (Turret t : turrets) {
            if (!t.isActive() || t.getHealth() <= 0) {
                continue;
            }
            double distSq = myPos.distanceSquared(t.getPosition());
            if (distSq < minDistSq) {
                minDistSq = distSq;
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

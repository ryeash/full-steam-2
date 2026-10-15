package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieType;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ZombieTest {

    @Test
    @DisplayName("Zombie should initialize with randomized attributes within type bounds")
    public void testZombieInitialization() {
        Zombie zombie = new Zombie(1, ZombieType.RUNNER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 200.0);

        assertEquals(1, zombie.getId());
        assertEquals(ZombieType.RUNNER, zombie.getType());
        assertEquals(ZombieAttackPattern.NEAREST_PLAYER, zombie.getAttackPattern());
        assertEquals(ZombieType.RUNNER.getRadius(), zombie.getRadius(), 0.001);
        assertEquals(ZombieType.RUNNER.getDefaultHealth(), zombie.getHealth(), 0.001);
        assertEquals(ZombieType.RUNNER.getDefaultHealth(), zombie.getMaxHealth(), 0.001);
        assertTrue(zombie.getBaseSpeed() > 0);
        assertTrue(zombie.getLungeDistance() > 0);
        assertTrue(zombie.getMeleeDamage() > 0);
        assertTrue(zombie.isActive());
        assertEquals(1, zombie.getOwnerId());
        assertEquals(0, zombie.getOwnerTeam());
        assertEquals(new Vector2(100.0, 200.0), zombie.getPosition());
    }

    @Test
    @DisplayName("Zombie movement applies force rather than directly setting velocity")
    public void testForceBasedMovement() {
        World<Body> world = new World<>();
        world.setGravity(new Vector2(0, 0));
        Zombie zombie = new Zombie(2, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        world.addBody(zombie.getBody());

        Vector2 initialVelocity = zombie.getVelocity().copy();
        assertEquals(0.0, initialVelocity.x, 0.001);
        assertEquals(0.0, initialVelocity.y, 0.001);

        // Process movement with directional input
        zombie.processMovement(new Vector2(1.0, 0.0));
        assertTrue(zombie.getBody().getAccumulatedForce().x > 0, "Force should be accumulated in +X direction");

        // Step physics simulation
        world.step(1, 0.05);

        // Movement applied force to the dyn4j Body, causing acceleration
        Vector2 velocityAfterForce = zombie.getVelocity();
        assertTrue(velocityAfterForce.x > 0, "Velocity should increase in direction of force after physics step");
        assertEquals(0.0, velocityAfterForce.y, 0.001);
    }

    @Test
    @DisplayName("Lunger mechanic triggers speed boost when target enters lunger distance")
    public void testLungerSpeedBoost() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(1000).worldHeight(1000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie zombie = new Zombie(3, ZombieType.LUNGER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        entities.add(zombie);

        double lungerDistance = zombie.getLungeDistance();

        // Target outside lunger distance
        Player distantPlayer = new Player(10, "Target", 0.0, lungerDistance + 50.0, 1, 100.0);
        distantPlayer.setActive(true);
        entities.add(distantPlayer);

        zombie.tickAI(entities, 0.05);
        assertFalse(zombie.isLunging(), "Should not lunge when target is outside lunger distance");

        // Target inside lunger distance
        distantPlayer.setPosition(0.0, lungerDistance - 20.0);
        zombie.tickAI(entities, 0.05);
        assertTrue(zombie.isLunging(), "Should trigger lunge when target is within lunger distance");
    }

    @Test
    @DisplayName("Melee cooldown prevents continuous damage on every frame")
    public void testMeleeAttackCooldown() {
        Zombie zombie = new Zombie(4, ZombieType.TANK, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);

        assertTrue(zombie.canMeleeAttack(), "Zombie should be able to attack initially");
        zombie.recordMeleeAttack();
        assertFalse(zombie.canMeleeAttack(), "Zombie should be on cooldown immediately after attack");

        // Advance time past cooldown
        try {
            Thread.sleep((long) (ZombieType.TANK.getMeleeCooldownSeconds() * 1000) + 10);
        } catch (InterruptedException ignored) {}

        assertTrue(zombie.canMeleeAttack(), "Zombie should be ready to attack after cooldown expires");
    }

    @Test
    @DisplayName("Zombie AI targets nearest player under NEAREST_PLAYER attack pattern")
    public void testNearestPlayerTargeting() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(1000).worldHeight(1000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie zombie = new Zombie(5, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        entities.add(zombie);

        Player farPlayer = new Player(11, "Far", 300.0, 0.0, 1, 100.0);
        farPlayer.setActive(true);
        Player nearPlayer = new Player(12, "Near", 100.0, 0.0, 1, 100.0);
        nearPlayer.setActive(true);
        entities.add(farPlayer);
        entities.add(nearPlayer);

        zombie.tickAI(entities, 0.05);

        // Zombie should aim towards the nearer player (positive X direction, rotation ~ 0)
        double aimX = Math.cos(zombie.getRotation());
        assertTrue(aimX > 0.9, "Zombie should aim toward nearest player");
    }

    @Test
    @DisplayName("Zombie AI targets lowest health player under LOWEST_HEALTH pattern")
    public void testLowestHealthTargeting() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(1000).worldHeight(1000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie zombie = new Zombie(6, ZombieType.WALKER, ZombieAttackPattern.LOWEST_HEALTH, 0.0, 0.0);
        entities.add(zombie);

        Player healthyClosePlayer = new Player(13, "Healthy", 50.0, 0.0, 1, 100.0);
        healthyClosePlayer.setActive(true);
        Player injuredFarPlayer = new Player(14, "Injured", -150.0, 0.0, 1, 100.0);
        injuredFarPlayer.setActive(true);
        injuredFarPlayer.setHealth(20.0); // 20% health

        entities.add(healthyClosePlayer);
        entities.add(injuredFarPlayer);

        zombie.tickAI(entities, 0.05);

        // Zombie should aim towards the injured player (negative X direction)
        double aimX = Math.cos(zombie.getRotation());
        assertTrue(aimX < -0.9, "Zombie should prioritize lower health player even if further away");
    }

    @Test
    @DisplayName("Zombie AI obsesses over initial target under OBSESSED_PLAYER pattern")
    public void testObsessedPlayerTargeting() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(1000).worldHeight(1000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie zombie = new Zombie(7, ZombieType.STALKER, ZombieAttackPattern.OBSESSED_PLAYER, 0.0, 0.0);
        entities.add(zombie);

        Player playerA = new Player(15, "A", 100.0, 0.0, 1, 100.0);
        playerA.setActive(true);
        Player playerB = new Player(16, "B", 120.0, 0.0, 1, 100.0);
        playerB.setActive(true);

        entities.add(playerA);
        entities.add(playerB);

        // First tick acquires target
        zombie.tickAI(entities, 0.05);
        assertEquals(15, zombie.getTargetEntityId());

        // Player B moves much closer than Player A
        playerB.setPosition(20.0, 0.0);
        playerA.setPosition(200.0, 0.0);

        // Second tick should still pursue obsessed player A
        zombie.tickAI(entities, 0.05);
        assertEquals(15, zombie.getTargetEntityId());
        assertTrue(zombie.getAimDirection().x > 0);
    }

    @Test
    @DisplayName("Zombie takes damage and deactivates upon death")
    public void testDamageAndDeath() {
        Zombie zombie = new Zombie(8, ZombieType.RUNNER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        double initialHealth = zombie.getHealth();

        zombie.takeDamage(20.0);
        assertEquals(initialHealth - 20.0, zombie.getHealth(), 0.001);
        assertTrue(zombie.isActive());

        zombie.takeDamage(initialHealth);
        assertTrue(zombie.getHealth() <= 0.0, "Health should be depleted");
        assertFalse(zombie.isActive(), "Zombie should become inactive when health is depleted");
    }

    @Test
    @DisplayName("Boomer zombie initializes with high health, bloated radius, and correct display name")
    public void testBoomerInitialization() {
        Zombie boomer = new Zombie(50, ZombieType.BOOMER, ZombieAttackPattern.NEAREST_PLAYER, 10.0, 20.0);
        assertEquals(ZombieType.BOOMER, boomer.getType());
        assertEquals("Boomer Zombie", boomer.getDisplayName());
        assertEquals(ZombieType.BOOMER.getRadius(), boomer.getRadius(), 0.001);
        assertEquals(ZombieType.BOOMER.getDefaultHealth(), boomer.getMaxHealth(), 0.001);
        assertNull(boomer.getWeapon(), "Boomers are melee attackers and carry no projectile weapon");
    }

    @Test
    @DisplayName("Boomer death triggers 2 field effects: warning zone and delayed poison explosion")
    public void testBoomerDeathTriggersWarningZoneAndDelayedPoisonExplosion() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(1000).worldHeight(1000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie boomer = new Zombie(51, ZombieType.BOOMER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 150.0);
        entities.add(boomer);

        long beforeDeath = System.currentTimeMillis();
        boomer.onDeath(entities);

        var fieldEffects = entities.getAllFieldEffects();
        assertEquals(2, fieldEffects.size(), "Boomer death must trigger exactly 2 field effects");

        FieldEffect warningZone = null;
        FieldEffect poisonExplosion = null;
        for (FieldEffect fe : fieldEffects) {
            if (fe.getType() == FieldEffectType.WARNING_ZONE) {
                warningZone = fe;
            } else if (fe.getType() == FieldEffectType.POISON) {
                poisonExplosion = fe;
            }
        }

        assertNotNull(warningZone, "Must produce a WARNING_ZONE field effect");
        assertNotNull(poisonExplosion, "Must produce a POISON field effect");

        // Verify warning zone properties
        assertTrue(warningZone instanceof FieldEffectCircle);
        FieldEffectCircle wzCircle = (FieldEffectCircle) warningZone;
        assertEquals(100.0, wzCircle.getPosition().x, 0.001);
        assertEquals(150.0, wzCircle.getPosition().y, 0.001);
        assertEquals(Zombie.BOOMER_WARNING_RADIUS, wzCircle.getRadius(), 0.001);
        assertEquals(0.0, warningZone.getDamage(), 0.001);
        assertTrue(warningZone.isArmed(), "Warning zone should be armed/active immediately");

        // Verify poison explosion properties
        assertTrue(poisonExplosion instanceof FieldEffectCircle);
        FieldEffectCircle poisonCircle = (FieldEffectCircle) poisonExplosion;
        assertEquals(100.0, poisonCircle.getPosition().x, 0.001);
        assertEquals(150.0, poisonCircle.getPosition().y, 0.001);
        assertEquals(Zombie.BOOMER_POISON_BASE_RADIUS, poisonCircle.getInitialRadius(), 0.001);
        assertEquals(Zombie.BOOMER_POISON_BASE_RADIUS * 2.0, poisonCircle.getMaxRadius(), 0.001);
        assertEquals(Zombie.BOOMER_POISON_DAMAGE, poisonExplosion.getDamage(), 0.001);

        // Arming time must match warning zone time (1.5s in future)
        long expectedMinArmTime = beforeDeath + (long) (Zombie.BOOMER_WARNING_DURATION_SECONDS * 1000);
        assertTrue(poisonExplosion.getArmingTime() >= expectedMinArmTime, "Poison explosion arming time must match warning duration");
        assertFalse(poisonExplosion.isArmed(), "Poison explosion should be inert until warning zone expires");

        // Subsequent onDeath calls must be idempotent
        boomer.onDeath(entities);
        assertEquals(2, entities.getAllFieldEffects().size(), "onDeath must be idempotent and not spawn duplicate effects");
    }

    @Test
    @DisplayName("Spitter zombie initializes with long range, high caliber, slow moving, poison weapon")
    public void testSpitterInitializationAndWeaponStats() {
        Zombie spitter = new Zombie(60, ZombieType.SPITTER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        assertEquals(ZombieType.SPITTER, spitter.getType());
        assertEquals("Spitter Zombie", spitter.getDisplayName());
        assertNotNull(spitter.getWeapon(), "Spitter must be initialized with a projectile weapon");

        Weapon weapon = spitter.getWeapon();
        assertTrue(weapon.getBulletEffects().contains(BulletEffect.POISON), "Spitter weapon must have POISON effect");
        assertTrue(weapon.getRange() >= 900.0, "Spitter weapon must have long range (>= 900 units)");
        assertTrue(weapon.getCaliber() >= 1.5, "Spitter weapon must have high caliber (>= 1.5 multiplier)");
        assertTrue(weapon.getProjectileSpeed() <= 400.0, "Spitter weapon must have slow moving projectile (<= 400 speed)");
        assertTrue(weapon.getFireRate() <= 0.6, "Spitter weapon should have a slow base fire rate (<= 0.6 shots/sec)");
        assertTrue(spitter.canFire(), "Spitter should be able to fire initially");
    }

    @Test
    @DisplayName("Spitter fire cooldown prevents rapid fire spam")
    public void testSpitterFireCooldown() {
        Zombie spitter = new Zombie(65, ZombieType.SPITTER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        assertTrue(spitter.canFire(), "Initially can fire");

        spitter.setLastShotTime(System.currentTimeMillis());
        assertFalse(spitter.canFire(), "Cannot fire immediately after shooting");

        // After 500ms, still cannot fire (slowed cadence)
        spitter.setLastShotTime(System.currentTimeMillis() - 500L);
        assertFalse(spitter.canFire(), "Cannot fire after only 500ms");

        // After 2500ms, cooldown has elapsed and can fire again
        spitter.setLastShotTime(System.currentTimeMillis() - 2500L);
        assertTrue(spitter.canFire(), "Can fire after ~2.3s cooldown has elapsed");
    }

    @Test
    @DisplayName("Spitter kiting AI maintains combat distance without lunging")
    public void testSpitterKitingBehavior() {
        World<Body> world = new World<>();
        GameConfig config = GameConfig.builder().worldWidth(2000).worldHeight(2000).build();
        GameEntities entities = new GameEntities(config, world);

        Zombie spitter = new Zombie(61, ZombieType.SPITTER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        entities.add(spitter);

        // Case 1: Target far away (> 600) -> Spitter moves forward to close distance
        Player farPlayer = new Player(101, "Far", 800.0, 0.0, 1, 100.0);
        farPlayer.setActive(true);
        entities.add(farPlayer);

        spitter.tickAI(entities, 0.05);
        assertFalse(spitter.isLunging(), "Spitter should never lunge");
        assertTrue(spitter.getAimDirection().x > 0.9, "Spitter should aim at target");

        // Case 2: Target too close (< 350) -> Spitter backs away
        farPlayer.setPosition(200.0, 0.0);
        spitter.tickAI(entities, 0.05);
        assertFalse(spitter.isLunging(), "Spitter should never lunge");
        assertTrue(spitter.getAimDirection().x > 0.9, "Spitter should still face/aim at target while retreating");
    }
}

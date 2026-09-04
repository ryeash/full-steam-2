package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
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
}

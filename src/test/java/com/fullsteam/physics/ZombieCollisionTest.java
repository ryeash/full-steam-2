package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Rules;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieType;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ZombieCollisionTest extends BaseTestClass {

    private GameManager gameManager;
    private GameEntities gameEntities;
    private CollisionProcessor collisionProcessor;
    private Player player;

    @BeforeEach
    public void setUp() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombiePointsPerKill(2)
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .headquartersPointsPerDamage(0.1)
                .build();

        GameConfig gameConfig = GameConfig.builder()
                .rules(rules)
                .worldWidth(2000)
                .worldHeight(2000)
                .maxPlayers(4)
                .teamCount(2)
                .enableAIFilling(false)
                .build();

        gameManager = new GameManager("test-zombie-collision", gameConfig, null);
        gameEntities = gameManager.getGameEntities();
        collisionProcessor = new CollisionProcessor(gameManager, gameEntities);

        player = new Player(1, "Hero", 100.0, 100.0, 1, 100.0);
        player.setActive(true);
        gameEntities.add(player);
    }

    @Test
    @DisplayName("Zombie delivers melee damage to player on collision")
    public void testPlayerZombieMeleeCollision() {
        Zombie zombie = new Zombie(10, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 100.0);
        gameEntities.add(zombie);

        double initialHealth = player.getHealth();
        collisionProcessor.handleEntityCollision(player, zombie);

        assertTrue(player.getHealth() < initialHealth, "Player should take melee damage from zombie");
        assertEquals(initialHealth - zombie.getMeleeDamage(), player.getHealth(), 0.001);
        assertFalse(zombie.canMeleeAttack(), "Zombie melee should enter cooldown after hit");

        // Second immediate collision during cooldown should not deal double damage
        double healthAfterFirstHit = player.getHealth();
        collisionProcessor.handleEntityCollision(player, zombie);
        assertEquals(healthAfterFirstHit, player.getHealth(), 0.001);
    }

    @Test
    @DisplayName("Riot shield blocks zombie melee damage from frontal attack")
    public void testRiotShieldBlocksZombieMelee() {
        Zombie zombie = new Zombie(11, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 150.0, 100.0);
        gameEntities.add(zombie);

        // Player facing East (+X direction, rotation = 0) with riot shield
        player.setAimDirection(new Vector2(1.0, 0.0));
        StatusEffectManager.applyRiotShield(player, 6.0);
        assertTrue(player.isRiotShieldActive());
        double initialHp = player.getHealth();

        collisionProcessor.handleEntityCollision(player, zombie);

        assertEquals(initialHp, player.getHealth(), "Riot shield should block frontal zombie melee damage");
    }

    @Test
    @DisplayName("Zombie delivers melee damage to Headquarters")
    public void testZombieHeadquartersMeleeCollision() {
        Headquarters hq = gameEntities.getTeamHeadquarters(1);
        assertNotNull(hq);

        Zombie zombie = new Zombie(12, ZombieType.TANK, ZombieAttackPattern.HEADQUARTERS, hq.getPosition().x, hq.getPosition().y);
        gameEntities.add(zombie);

        double initialHealth = hq.getHealth();
        collisionProcessor.handleEntityCollision(hq, zombie);

        assertTrue(hq.getHealth() < initialHealth, "HQ should take melee damage from zombie");
        assertEquals(initialHealth - zombie.getMeleeDamage(), hq.getHealth(), 0.001);
    }

    @Test
    @DisplayName("Zombie delivers melee damage to Turret")
    public void testZombieTurretMeleeCollision() {
        Turret turret = new Turret(1, 1, new Vector2(200.0, 200.0), 100.0, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        turret.setActive(true);
        gameEntities.add(turret);

        Zombie zombie = new Zombie(13, ZombieType.WALKER, ZombieAttackPattern.TURRET, 200.0, 200.0);
        gameEntities.add(zombie);

        double initialHealth = turret.getHealth();
        collisionProcessor.handleEntityCollision(turret, zombie);

        assertTrue(turret.getHealth() < initialHealth, "Turret should take melee damage from zombie");
    }

    @Test
    @DisplayName("Projectile damages zombie and awards zombie kill to shooting player upon death")
    public void testProjectileZombieCollision() {
        Zombie zombie = new Zombie(14, ZombieType.RUNNER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 100.0);
        gameEntities.add(zombie);

        double zombieInitialHealth = zombie.getHealth();

        // High damage projectile that kills zombie in one hit
        Projectile projectile = new Projectile(
                1,
                new Vector2(100.0, 100.0),
                new Vector2(500.0, 0.0),
                zombieInitialHealth + 50.0,
                1000.0,
                player.getId(),
                0.0,
                Set.of(BulletEffect.EXPLOSIVE),
                Ordinance.PROJECTILE,
                10.0,
                1.0
        );
        gameEntities.add(projectile);

        assertEquals(0, player.getScoring().getZombieKills());

        collisionProcessor.handleEntityCollision(projectile, zombie);

        assertFalse(projectile.isActive(), "Projectile should deactivate on impact");
        assertFalse(zombie.isActive(), "Zombie should be eliminated");
        assertEquals(1, player.getScoring().getZombieKills(), "Player should receive a zombie kill credit");
    }

    @Test
    @DisplayName("Field effect damages zombie over time")
    public void testFieldEffectZombieCollision() {
        Zombie zombie = new Zombie(15, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 100.0);
        gameEntities.add(zombie);

        FieldEffectCircle poisonZone = new FieldEffectCircle(
                1,
                FieldEffectType.POISON,
                new Vector2(100.0, 100.0),
                50.0,
                30.0, // 30 dmg
                5.0,
                10.0,
                player.getId(),
                player.getTeam()
        );
        poisonZone.setActive(true);
        gameEntities.add(poisonZone);

        double initialHealth = zombie.getHealth();
        collisionProcessor.handleEntityCollision(poisonZone, zombie);

        assertTrue(zombie.getHealth() < initialHealth, "Damage field effect should harm zombie");
    }

    @Test
    @DisplayName("Boomer killed by player projectile triggers warning zone and delayed poison explosion")
    public void testBoomerKilledByProjectileTriggersExplosion() {
        Zombie boomer = new Zombie(20, ZombieType.BOOMER, ZombieAttackPattern.NEAREST_PLAYER, 100.0, 100.0);
        gameEntities.add(boomer);

        int initialZombieKills = player.getScoring().getZombieKills();

        // Lethal projectile from player
        Projectile lethalShot = new Projectile(
                player.getId(),
                new Vector2(100.0, 100.0),
                new Vector2(1.0, 0.0),
                boomer.getMaxHealth() + 50.0,
                500.0,
                player.getTeam(),
                0.0,
                Set.of(),
                Ordinance.PROJECTILE,
                1.0,
                0.0
        );
        gameEntities.add(lethalShot);

        collisionProcessor.handleEntityCollision(lethalShot, boomer);

        assertFalse(boomer.isActive(), "Boomer should be dead");
        assertEquals(initialZombieKills + 1, player.getScoring().getZombieKills(), "Player should receive zombie kill credit");

        // Verify warning zone and delayed poison explosion were spawned
        boolean hasWarningZone = gameEntities.getAllFieldEffects().stream()
                .anyMatch(fe -> fe.getType() == FieldEffectType.WARNING_ZONE);
        boolean hasPoisonExplosion = gameEntities.getAllFieldEffects().stream()
                .anyMatch(fe -> fe.getType() == FieldEffectType.POISON && fe.getArmingTime() > 0);

        assertTrue(hasWarningZone, "Boomer death should spawn WARNING_ZONE");
        assertTrue(hasPoisonExplosion, "Boomer death should spawn delayed POISON explosion");
    }

    @Test
    @DisplayName("Spitter fires poison projectile that passes through friendly zombies and damages player")
    public void testSpitterFiringAndZombiePassthrough() {
        Zombie spitter = new Zombie(21, ZombieType.SPITTER, ZombieAttackPattern.NEAREST_PLAYER, 0.0, 0.0);
        Zombie walker = new Zombie(22, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 50.0, 0.0);
        gameEntities.add(spitter);
        gameEntities.add(walker);

        // Player at (200, 0)
        player.setPosition(200.0, 0.0);

        // Target acquisition
        spitter.tickAI(gameEntities, 0.05);
        assertEquals(player, spitter.getCurrentTargetEntity());

        // Fire weapon through WeaponSystem
        gameManager.getWeaponSystem().handleZombieFire(spitter);

        // Verify projectile was created
        var projectiles = gameEntities.getProjectiles().values();
        assertEquals(1, projectiles.size(), "Spitter should have fired a projectile");

        // Immediate second fire attempt should be blocked by cooldown
        gameManager.getWeaponSystem().handleZombieFire(spitter);
        assertEquals(1, projectiles.size(), "Spitter cannot fire again immediately during cooldown");

        Projectile spit = projectiles.iterator().next();
        assertEquals(-spitter.getId(), spit.getOwnerId());
        assertTrue(spit.getBulletEffects().contains(BulletEffect.POISON), "Spit projectile must have POISON effect");

        // Check collision between zombie projectile and another zombie: should pass through
        double walkerHealthBefore = walker.getHealth();
        boolean walkerHitResolved = collisionProcessor.handleEntityCollision(spit, walker);
        assertFalse(walkerHitResolved, "Zombie projectile must pass through friendly zombies");
        assertEquals(walkerHealthBefore, walker.getHealth(), 0.001, "Walker should not take damage from friendly spit");

        // Now collide with enemy player: should deal damage
        double playerHealthBefore = player.getHealth();
        collisionProcessor.handleEntityCollision(spit, player);
        assertTrue(player.getHealth() < playerHealthBefore, "Player must take damage from Spitter spit");
    }
}

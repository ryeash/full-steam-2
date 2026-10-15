package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.model.ZombieType;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomingNpcTargetingTest {

    private GameEntities gameEntities;
    private BulletEffectProcessor processor;

    @BeforeEach
    void setUp() {
        GameConfig config = GameConfig.builder().build();
        World<Body> world = new World<>();
        world.setBounds(new AxisAlignedBounds(config.getWorldWidth(), config.getWorldHeight()));
        gameEntities = new GameEntities(config, world);
        processor = new BulletEffectProcessor(gameEntities);
    }

    @Test
    @DisplayName("Homing missile tracks Oddball NPC")
    void testHomingMissileTracksOddball() {
        // Player fires missile from (0, 0) heading East
        Projectile missile = new Projectile(
                1, // id
                new Vector2(0, 0),
                new Vector2(300, 0), // heading East (angle 0)
                20.0,
                1000.0,
                1, // player ownerId > 0
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0 // heading = 0
        );
        gameEntities.add(missile);

        // Place an Oddball North-East of the missile at (200, 150)
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 200.0, 150.0);
        gameEntities.add(oddball);

        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        double headingAfter = missile.getHeading();

        assertTrue(headingAfter > headingBefore,
                "Missile heading should turn toward Oddball NPC (North-East, positive angle): before="
                        + headingBefore + ", after=" + headingAfter);
    }

    @Test
    @DisplayName("Standard homing bullet tracks Oddball NPC")
    void testStandardHomingBulletTracksOddball() {
        // Player fires standard homing bullet heading North
        Projectile bullet = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(0, 400),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.PROJECTILE,
                1.0,
                0.0
        );
        gameEntities.add(bullet);

        // Place an Oddball to the East at (150, 50)
        Oddball oddball = new Oddball(Oddball.Personality.SEEKER, 150.0, 50.0);
        gameEntities.add(oddball);
        gameEntities.runPostUpdateHooks();

        // Bullet moves purely in +Y. Steering force toward East should apply +X force and velocity
        processor.applyHomingBehavior(bullet, 0.05);
        gameEntities.getWorld().updatev(0.05);

        assertTrue(bullet.getBody().getLinearVelocity().x > 0.0,
                "Homing bullet should steer toward Oddball NPC: vx=" + bullet.getBody().getLinearVelocity().x);
    }

    @Test
    @DisplayName("Homing missile tracks Zombie NPC")
    void testHomingMissileTracksZombie() {
        // Player fires missile from (0, 0) heading East (angle 0)
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        gameEntities.add(missile);

        // Place a hostile Zombie at (200, 120)
        Zombie zombie = new Zombie(ZombieType.WALKER, 200.0, 120.0);
        gameEntities.add(zombie);

        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        double headingAfter = missile.getHeading();

        assertTrue(headingAfter > headingBefore,
                "Missile heading should turn toward Zombie NPC: before="
                        + headingBefore + ", after=" + headingAfter);
    }

    @Test
    @DisplayName("Homing missile tracks enemy Turret but ignores friendly Turret")
    void testHomingMissileTracksEnemyTurret() {
        // Missile fired by Team 1 (6th arg ownerTeam = 1)
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1, // ownerTeam = 1
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0 // knockback
        );
        gameEntities.add(missile);

        // Friendly Turret at (150, 100) - Team 1
        Turret friendlyTurret = new Turret(1, 1, new Vector2(150.0, 100.0), 60.0, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        gameEntities.add(friendlyTurret);

        // Should NOT track friendly turret
        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        assertEquals(headingBefore, missile.getHeading(), 0.0001,
                "Missile should not home toward friendly turret");

        // Now add enemy Turret at (150, -100) - Team 2
        Turret enemyTurret = new Turret(2, 2, new Vector2(150.0, -100.0), 60.0, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        gameEntities.add(enemyTurret);

        processor.applyHomingBehavior(missile, 0.05);
        double headingAfterEnemy = missile.getHeading();

        assertTrue(headingAfterEnemy < headingBefore,
                "Missile should turn toward enemy turret (South-East, negative angle): before="
                        + headingBefore + ", after=" + headingAfterEnemy);
    }

    @Test
    @DisplayName("Homing missile prefers closest valid target among Players, Oddballs, and Zombies")
    void testHomingPrefersClosestTarget() {
        // Missile fired from (0, 0) heading East
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        gameEntities.add(missile);

        // Enemy Player far North at (100, 300) -> distance ~316
        Player enemyPlayer = new Player(2, "Enemy", 100.0, 300.0, 2, 100.0);
        enemyPlayer.setActive(true);
        gameEntities.add(enemyPlayer);

        // Oddball close South at (100, -80) -> distance ~128
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 100.0, -80.0);
        gameEntities.add(oddball);

        processor.applyHomingBehavior(missile, 0.05);
        double headingAfter = missile.getHeading();

        // Oddball is closer than Player, so missile should steer South towards Oddball (negative angle)
        assertTrue(headingAfter < 0.0,
                "Missile should steer toward closer Oddball (South-East) instead of distant Player (North-East): heading="
                        + headingAfter);
    }

    @Test
    @DisplayName("Homing does not track inactive or dead NPCs")
    void testHomingIgnoresInactiveOrDeadNPCs() {
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        gameEntities.add(missile);

        // Inactive Oddball
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 150.0, 100.0);
        oddball.setActive(false);
        gameEntities.add(oddball);

        // Dead Zombie
        Zombie zombie = new Zombie(ZombieType.WALKER, 150.0, -100.0);
        zombie.setHealth(0.0);
        zombie.setActive(false);
        gameEntities.add(zombie);

        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        assertEquals(headingBefore, missile.getHeading(), 0.0001,
                "Missile should not track inactive or dead NPCs");
    }

    @Test
    @DisplayName("Oddball-fired homing does not track itself or other Oddballs")
    void testOddballFiredHomingDoesNotTrackOddballs() {
        Oddball shootingOddball = new Oddball(Oddball.Personality.RAMPAGE, 0.0, 0.0);
        gameEntities.add(shootingOddball);

        // Oddball-fired projectile has negative ownerId (-shootingOddball.getId())
        Projectile oddballMissile = new Projectile(
                -shootingOddball.getId(), // ownerId
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                0, // ownerTeam
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        gameEntities.add(oddballMissile);

        // Another Oddball nearby
        Oddball otherOddball = new Oddball(Oddball.Personality.SEEKER, 150.0, 100.0);
        gameEntities.add(otherOddball);

        double headingBefore = oddballMissile.getHeading();
        processor.applyHomingBehavior(oddballMissile, 0.05);
        assertEquals(headingBefore, oddballMissile.getHeading(), 0.0001,
                "Oddball-fired homing missile should not track other Oddballs");

        // When an enemy Player is present, it SHOULD track the player
        Player player = new Player(1, "TargetPlayer", 150.0, 100.0, 1, 100.0);
        player.setActive(true);
        gameEntities.add(player);

        processor.applyHomingBehavior(oddballMissile, 0.05);
        double headingAfter = oddballMissile.getHeading();

        assertTrue(headingAfter > headingBefore,
                "Oddball-fired homing missile should track players: heading=" + headingAfter);
    }

    @Test
    @DisplayName("Zombie-fired homing does not track other Zombies")
    void testZombieFiredHomingDoesNotTrackZombies() {
        Zombie shooter = new Zombie(ZombieType.SPITTER, 0.0, 0.0);
        gameEntities.add(shooter);

        // Zombie projectile has negative ownerId (-shooter.getId())
        Projectile zombieMissile = new Projectile(
                -shooter.getId(), // ownerId
                new Vector2(0, 0),
                new Vector2(300, 0),
                20.0,
                1000.0,
                0, // ownerTeam
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        gameEntities.add(zombieMissile);

        // Friendly zombie nearby
        Zombie friendlyZombie = new Zombie(ZombieType.WALKER, 150.0, 100.0);
        gameEntities.add(friendlyZombie);

        double headingBefore = zombieMissile.getHeading();
        processor.applyHomingBehavior(zombieMissile, 0.05);
        assertEquals(headingBefore, zombieMissile.getHeading(), 0.0001,
                "Zombie-fired homing projectile should not track other zombies");
    }
}

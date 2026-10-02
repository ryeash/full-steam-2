package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MissileOrdinanceTest {

    @Test
    @DisplayName("Missile ordinance properties and classification")
    public void testMissileOrdinanceProperties() {
        assertEquals(10, Ordinance.MISSILE.getPointCost());
        assertFalse(Ordinance.MISSILE.isBeamType());
        assertTrue(Ordinance.MISSILE.isMissile());
        assertEquals("Missile", Ordinance.MISSILE.getDisplayName());
        assertEquals(1.0, Ordinance.MISSILE.getSpeedMultiplier());
        assertEquals(1.0, Ordinance.MISSILE.getAreaOfEffectModification());
    }

    @Test
    @DisplayName("Missile creates triangular physics body while standard projectile creates circle")
    public void testTriangularPhysicsBody() {
        Projectile standard = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(500, 0),
                25.0,
                1000.0,
                1,
                0.09,
                Set.of(),
                Ordinance.PROJECTILE,
                1.0,
                0.0
        );
        assertTrue(standard.getBody().getFixtures().get(0).getShape() instanceof Circle);
        assertFalse(standard.isMissile());

        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(500, 0),
                25.0,
                1000.0,
                1,
                0.09,
                Set.of(),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        assertTrue(missile.isMissile());
        assertTrue(missile.getBody().getFixtures().get(0).getShape() instanceof Polygon);

        Polygon poly = (Polygon) missile.getBody().getFixtures().get(0).getShape();
        assertEquals(3, poly.getVertices().length, "Missile physics body must be triangular (3 vertices)");
    }

    @Test
    @DisplayName("Missile starts slow and speeds up as it travels")
    public void testMissileStartsSlowAndAccelerates() {
        double targetSpeed = 600.0;
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(targetSpeed, 0),
                25.0,
                2000.0,
                1,
                0.09,
                Set.of(),
                Ordinance.MISSILE,
                1.0,
                0.0
        );

        double initialSpeed = missile.getBody().getLinearVelocity().getMagnitude();
        assertTrue(initialSpeed <= targetSpeed * 0.35,
                "Missile must start slow: initial " + initialSpeed + " vs target " + targetSpeed);

        // Update over 0.4 seconds
        missile.update(0.4);
        double midSpeed = missile.getBody().getLinearVelocity().getMagnitude();
        assertTrue(midSpeed > initialSpeed + 100.0,
                "Missile must accelerate forward: mid " + midSpeed + " > initial " + initialSpeed);

        // Update to 1.2 seconds total
        missile.update(0.8);
        double topSpeed = missile.getBody().getLinearVelocity().getMagnitude();
        assertTrue(topSpeed >= targetSpeed * 0.95,
                "Missile must reach top speed: top " + topSpeed + " vs target " + targetSpeed);
    }

    @Test
    @DisplayName("Linear damping attribute is coupled to missile thrust acceleration and top speed")
    public void testLinearDampingCoupling() {
        double targetSpeed = 600.0;

        // Low damping (high player investment, e.g. 0.0) -> high thrust, higher top speed
        Projectile zippyMissile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(targetSpeed, 0),
                25.0,
                2000.0,
                1,
                0.0, // 0 drag
                Set.of(),
                Ordinance.MISSILE,
                1.0,
                0.0
        );

        // High damping (negative player investment, e.g. 0.39) -> draggy, lower thrust, lower top speed
        Projectile draggyMissile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(targetSpeed, 0),
                25.0,
                2000.0,
                1,
                0.39, // high drag
                Set.of(),
                Ordinance.MISSILE,
                1.0,
                0.0
        );

        assertTrue(zippyMissile.getThrustAcceleration() > draggyMissile.getThrustAcceleration(),
                "Zippy missile should have higher thrust acceleration ("
                        + zippyMissile.getThrustAcceleration() + ") than draggy missile ("
                        + draggyMissile.getThrustAcceleration() + ")");

        assertTrue(zippyMissile.getTopSpeed() > draggyMissile.getTopSpeed(),
                "Zippy missile should have higher top speed ("
                        + zippyMissile.getTopSpeed() + ") than draggy missile ("
                        + draggyMissile.getTopSpeed() + ")");
    }

    @Test
    @DisplayName("Missile has extended homing range and exhibits natural slide/drift during turns")
    public void testMissileSeekingAndSlide() {
        GameConfig config = GameConfig.builder().build();
        GameManager gm = new GameManager("test-missile", config, null);
        GameEntities entities = gm.getGameEntities();
        BulletEffectProcessor processor = gm.getCollisionProcessor().getBulletEffectProcessor();

        // Target enemy player at distance ~364 (within tuned missile 420 range, beyond standard 300 range)
        Player enemy = new Player(2, "Enemy", 350.0, 100.0, 2, 100.0);
        enemy.setActive(true);
        entities.add(enemy);

        // Standard projectile with HOMING at origin facing East (1, 0)
        Projectile bullet = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(500, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.PROJECTILE,
                1.0,
                0.0
        );
        entities.add(bullet);

        // Missile with HOMING at origin facing East (1, 0)
        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(500, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        entities.add(missile);

        // Standard bullet should NOT detect enemy beyond 300 distance
        Vector2 bulletVelBefore = bullet.getBody().getLinearVelocity().copy();
        processor.applyHomingBehavior(bullet, 0.05);
        Vector2 bulletVelAfter = bullet.getBody().getLinearVelocity().copy();
        assertEquals(bulletVelBefore.y, bulletVelAfter.y, 0.001,
                "Standard bullet should not home outside 300 units");

        // Missile SHOULD detect enemy at ~364 distance and turn heading
        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        double headingAfter = missile.getHeading();

        assertTrue(headingAfter > headingBefore,
                "Missile heading should turn toward enemy (North-East, positive angle): before="
                        + headingBefore + ", after=" + headingAfter);

        // Verify natural slide:
        // Heading is now angled upward, but body velocity still carries horizontal momentum.
        // Thus, velocity has a non-zero lateral component relative to the new heading!
        Vector2 vel = missile.getBody().getLinearVelocity();
        Vector2 lateral = new Vector2(-Math.sin(headingAfter), Math.cos(headingAfter));
        double vLateral = vel.dot(lateral);
        assertTrue(Math.abs(vLateral) > 1.0,
                "Missile should exhibit sideways slide against its aiming heading: vLateral=" + vLateral);

        // Run update to advance forward thrust and lateral damping
        missile.update(0.1);
        Vector2 velAfterUpdate = missile.getBody().getLinearVelocity();
        double vLateralAfter = velAfterUpdate.dot(lateral);
        assertTrue(Math.abs(vLateralAfter) < Math.abs(vLateral),
                "Lateral slide should dampen over time: before=" + vLateral + ", after=" + vLateralAfter);
    }

    @Test
    @DisplayName("Check collision between two missiles")
    public void testTwoMissilesCollision() {
        GameConfig config = GameConfig.builder().build();
        GameManager gm = new GameManager("test-missile-col", config, null);

        Player enemy = new Player(2, "Enemy", 250.0, 50.0, 2, 10.0); // Low health
        enemy.setActive(true);
        gm.getGameEntities().add(enemy);

        WeaponConfig killerBees = WeaponConfig.KILLER_BEES_PRESET;
        Vector2 dir = new Vector2(1, 0);
        List<GameEntity> fired = new com.fullsteam.games.weapon.ProjectileFiringMechanism(gm.getGameEntities())
                .fire(1, 1, killerBees.buildWeapon(), new Vector2(0, 0), dir);

        for (GameEntity e : fired) {
            gm.getGameEntities().add(e);
        }
        gm.getGameEntities().runPostUpdateHooks();

        System.out.println("Fired " + fired.size() + " missiles");

        double dt = 1.0 / 60.0;
        for (int i = 0; i < 110; i++) {
            // Manual step with fixed dt
            gm.getGameEntities().updateAll(dt);
            gm.getGameEntities().getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                if (!projectile.isActive()) {
                    if (projectile.shouldTriggerEffectsOnDismissal()) {
                        projectile.markAsExploded();
                        gm.getCollisionProcessor().getBulletEffectProcessor().processEffectHit(projectile, projectile.getPosition());
                    }
                    gm.getWorld().removeBody(projectile.getBody());
                    return true;
                }
                gm.getCollisionProcessor().getBulletEffectProcessor().applyHomingBehavior(projectile, dt);
                return false;
            });
            gm.getWorld().updatev(dt);

            // Active missiles must never stop dead in place
            for (GameEntity e : fired) {
                Projectile p = (Projectile) e;
                if (p.isActive()) {
                    assertTrue(p.getBody().getLinearVelocity().getMagnitude() > 50.0,
                            "Missile should never stop dead or freeze in place: speed=" + p.getBody().getLinearVelocity().getMagnitude());
                }
            }
        }

        // Enemy was killed by the volley
        assertFalse(enemy.isActive(), "Enemy should be dead");
        assertEquals(0.0, enemy.getHealth(), "Enemy health should be 0");

        // The remaining active missiles flew past the enemy position (x=250)
        long activeMissilesPastEnemy = fired.stream()
                .map(e -> (Projectile) e)
                .filter(p -> p.isActive() && p.getPosition().x > 250.0)
                .count();
        assertTrue(activeMissilesPastEnemy >= 1, "At least one following missile should fly past the dead enemy position");
    }
    @Test
    @DisplayName("Test two missiles crossing paths do not stop dead or impede each other")
    public void testTwoMissilesCrossingPaths() {
        GameConfig config = GameConfig.builder().build();
        GameManager gm = new GameManager("test-crossing", config, null);

        // M1 moving East
        Projectile m1 = new Projectile(
                1,
                new Vector2(100, 100),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                java.util.Collections.emptySet(),
                Ordinance.MISSILE,
                1.6,
                0.0
        );
        // M2 moving West directly toward M1
        Projectile m2 = new Projectile(
                2,
                new Vector2(160, 100),
                new Vector2(-300, 0),
                20.0,
                1000.0,
                2,
                0.09,
                java.util.Collections.emptySet(),
                Ordinance.MISSILE,
                1.6,
                0.0
        );

        gm.getGameEntities().add(m1);
        gm.getGameEntities().add(m2);
        gm.getGameEntities().runPostUpdateHooks();

        double dt = 1.0 / 60.0;
        for (int i = 0; i < 50; i++) {
            gm.getGameEntities().updateAll(dt);
            gm.getWorld().updatev(dt);
            // Missiles should maintain forward velocity and pass through each other
            assertTrue(m1.getBody().getLinearVelocity().x > 50.0,
                    "M1 should not lose forward velocity when passing M2: " + m1.getBody().getLinearVelocity());
            assertTrue(m2.getBody().getLinearVelocity().x < -50.0,
                    "M2 should not lose forward velocity when passing M1: " + m2.getBody().getLinearVelocity());
        }
        // Both missiles successfully crossed paths without stopping dead
        assertTrue(m1.getPosition().x > 160.0, "M1 should pass beyond 160: actual=" + m1.getPosition().x);
        assertTrue(m2.getPosition().x < 100.0, "M2 should pass beyond 100: actual=" + m2.getPosition().x);
    }

    @Test
    @DisplayName("Test missile arriving after target is killed")
    public void testMissileArrivingAfterTargetKilled() {
        GameConfig config = GameConfig.builder().build();
        GameManager gm = new GameManager("test-target-killed", config, null);

        Player enemy = new Player(2, "Enemy", 200.0, 100.0, 2, 10.0); // 10 HP
        enemy.setActive(true);
        gm.getGameEntities().add(enemy);

        // M1 is close, will hit enemy on tick 2 or 3
        Projectile m1 = new Projectile(
                1,
                new Vector2(180, 100),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.6,
                0.0
        );
        // M2 is right behind M1, will arrive after enemy dies
        Projectile m2 = new Projectile(
                1,
                new Vector2(150, 100),
                new Vector2(300, 0),
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.6,
                0.0
        );

        gm.getGameEntities().add(m1);
        gm.getGameEntities().add(m2);
        gm.getGameEntities().runPostUpdateHooks();

        double dt = 1.0 / 60.0;
        for (int i = 0; i < 40; i++) {
            gm.getGameEntities().updateAll(dt);
            gm.getGameEntities().getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                if (!projectile.isActive()) {
                    gm.getWorld().removeBody(projectile.getBody());
                    return true;
                }
                gm.getCollisionProcessor().getBulletEffectProcessor().applyHomingBehavior(projectile, dt);
                return false;
            });
            gm.getWorld().updatev(dt);
        }

        assertFalse(enemy.isActive(), "Enemy should have been killed");
        assertEquals(0.0, enemy.getHealth(), "Enemy health should be 0");
        assertFalse(m1.isActive(), "M1 should have hit and detonated");
        assertTrue(m2.getPosition().x > 220.0,
                "M2 should have flown past the dead enemy position (200.0) without stopping dead: actual=" + m2.getPosition().x);
        assertTrue(m2.getBody().getLinearVelocity().getMagnitude() > 100.0,
                "M2 should maintain forward propulsion: actual=" + m2.getBody().getLinearVelocity().getMagnitude());
    }

    @Test
    @DisplayName("Tuned homing limits")
    public void testTunedHomingDistanceLimits() {
        GameConfig config = GameConfig.builder().build();
        GameManager gm = new GameManager("test-homing-tuned", config, null);
        GameEntities entities = gm.getGameEntities();
        BulletEffectProcessor processor = gm.getCollisionProcessor().getBulletEffectProcessor();

        assertEquals(420.0, BulletEffectProcessor.MISSILE_HOMING_DISTANCE);
        assertEquals(3.5, BulletEffectProcessor.MISSILE_TURN_RATE);

        // Place enemy at 460 distance (outside new 420 limit, whereas old 550 limit would have acquired)
        Player farEnemy = new Player(2, "FarEnemy", 460.0, 0.0, 2, 100.0);
        farEnemy.setActive(true);
        entities.add(farEnemy);

        Projectile missile = new Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(0, 500), // pointing North
                20.0,
                1000.0,
                1,
                0.09,
                Set.of(BulletEffect.HOMING),
                Ordinance.MISSILE,
                1.0,
                0.0
        );
        entities.add(missile);

        double headingBefore = missile.getHeading();
        processor.applyHomingBehavior(missile, 0.05);
        double headingAfter = missile.getHeading();

        assertEquals(headingBefore, headingAfter, 0.0001,
                "Missile should not acquire target beyond tuned 420 distance limit");
    }

    @Test
    @DisplayName("Killer Bees preset has correct triple-shot, high caliber, homing missile launcher properties")
    public void testKillerBeesPreset() {
        WeaponConfig config = WeaponConfig.KILLER_BEES_PRESET;
        assertNotNull(config);
        assertEquals("Killer Bees", config.getType());
        assertEquals(Ordinance.MISSILE, config.getOrdinance());
        assertTrue(config.getBulletEffects().contains(BulletEffect.HOMING));
        assertFalse(config.getBulletEffects().contains(BulletEffect.EXPLOSIVE), "Must be non-explosive");

        Weapon weapon = config.buildWeapon();
        assertEquals(3, weapon.getBulletsPerShot(), "Must be triple shot");
        assertTrue(weapon.getCaliber() > 1.5, "Must be high caliber (>1.5x)");
        assertTrue(weapon.getDamage() > 20.0, "Must have high damage");

        int attrPoints = config.getAttributePoints();
        int fxPoints = config.getBulletEffects().stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordPoints = config.getOrdinance().getPointCost();
        int varPoints = config.getVarianceFormula() != null ? config.getVarianceFormula().getPointCost() : 0;
        assertEquals(100, attrPoints + fxPoints + ordPoints + varPoints, "Point budget must equal 100");
    }
}

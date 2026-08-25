package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlasmaBeamHoldToFireTest extends BaseTestClass {

    private WeaponSystem weaponSystem;
    private GameEntities gameEntities;
    private World<Body> world;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
        GameConfig testConfig = GameConfig.builder()
                .enableAIFilling(false)
                .build();
        gameEntities = new GameEntities(testConfig, world);
        weaponSystem = new WeaponSystem(gameEntities, world);
    }

    private Player createPlasmaPlayer(int id, int team) {
        Player player = new Player(id, "PlasmaPlayer" + id, 100, 100, team, 100.0);
        player.setActive(true);
        player.setWeapon(WeaponConfig.PLASMA_CANNON_PRESET.buildWeapon());
        return player;
    }

    @Test
    @DisplayName("Plasma beam spawns and tracks player position/aim while fire is held")
    void testPlasmaBeamHoldsAndTracksPlayer() {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0)); // Aim right
        gameEntities.add(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Tick 1: hold fire
        weaponSystem.handlePrimaryFire(player, input);

        assertTrue(player.hasActivePlasmaBeam(), "Player should have an active plasma beam while holding fire");
        assertEquals(1, player.getActivePlasmaBeams().size());

        var beam = player.getActivePlasmaBeams().get(0);
        assertEquals(Ordinance.PLASMA_BEAM, beam.getOrdinance());
        assertTrue(beam.isActive());

        Vector2 initialStart = beam.getStartPoint().copy();

        // Move player and change aim direction to (0, 1) [aim down]
        player.setPosition(200, 200);
        player.setAimDirection(new Vector2(0, 1));

        // Tick 2: still holding fire
        weaponSystem.handlePrimaryFire(player, input);

        assertTrue(player.hasActivePlasmaBeam(), "Beam should remain active while holding fire");
        assertEquals(1, player.getActivePlasmaBeams().size());

        var updatedBeam = player.getActivePlasmaBeams().get(0);
        assertFalse(initialStart.equals(updatedBeam.getStartPoint()), "Beam start point should update with player movement");
        assertEquals(200.0, updatedBeam.getStartPoint().x, 1e-3);
    }

    @Test
    @DisplayName("Plasma beam is dismissed immediately when fire button is released")
    void testPlasmaBeamDismissesOnRelease() {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Start firing
        weaponSystem.handlePrimaryFire(player, input);
        assertTrue(player.hasActivePlasmaBeam());

        // Release fire
        input.setLeft(false);
        weaponSystem.handlePrimaryFire(player, input);

        assertFalse(player.hasActivePlasmaBeam(), "Plasma beam should be dismissed when fire button is released");
    }

    @Test
    @DisplayName("Plasma beam drains ammo over time based on fire rate interval")
    void testPlasmaBeamAmmoDrainAndReload() throws InterruptedException {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        // Set ammo to 2 for quick testing
        player.getCurrentWeapon().setCurrentAmmo(2);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Initial trigger spends 1 ammo immediately (2 -> 1)
        weaponSystem.handlePrimaryFire(player, input);
        assertEquals(1, player.getCurrentWeapon().getCurrentAmmo(), "Initial trigger should immediately consume 1 ammo");
        assertTrue(player.hasActivePlasmaBeam());

        // Simulate time passing beyond fire interval (interval = 1000 / fireRate)
        double fireRate = player.getCurrentWeapon().getFireRate();
        long intervalMs = (long) (1000.0 / fireRate) + 50;

        // Second interval spends remaining ammo (1 -> 0)
        Thread.sleep(intervalMs);
        weaponSystem.handlePrimaryFire(player, input);
        assertEquals(0, player.getCurrentWeapon().getCurrentAmmo(), "Ammo should decrease to 0 after fire interval");
        assertTrue(player.hasActivePlasmaBeam(), "Beam should remain active for the duration of the paid ammo interval");

        // Third interval: no ammo left to pay for next interval, beam stops and reload starts
        Thread.sleep(intervalMs);
        weaponSystem.handlePrimaryFire(player, input);
        assertEquals(0, player.getCurrentWeapon().getCurrentAmmo());
        assertFalse(player.hasActivePlasmaBeam(), "Beam should stop when out of ammo for next interval");
        assertTrue(player.isReloading(), "Player should start reloading when ammo is depleted");
    }

    @Test
    @DisplayName("Tapping fire button spends at least one ammo per tap")
    void testTappingFireButtonDrainsAmmo() {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        int initialAmmo = player.getCurrentWeapon().getCurrentAmmo();

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Tap 1: press fire
        weaponSystem.handlePrimaryFire(player, input);
        assertEquals(initialAmmo - 1, player.getCurrentWeapon().getCurrentAmmo(), "First tap should consume 1 ammo");
        assertTrue(player.hasActivePlasmaBeam());

        // Release fire
        input.setLeft(false);
        weaponSystem.handlePrimaryFire(player, input);
        assertFalse(player.hasActivePlasmaBeam());

        // Tap 2: press fire again
        input.setLeft(true);
        weaponSystem.handlePrimaryFire(player, input);
        assertEquals(initialAmmo - 2, player.getCurrentWeapon().getCurrentAmmo(), "Second tap should consume another ammo");
        assertTrue(player.hasActivePlasmaBeam());
    }

    @Test
    @DisplayName("Plasma beam stops when player dies")
    void testPlasmaBeamStopsOnDeath() {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        weaponSystem.handlePrimaryFire(player, input);
        assertTrue(player.hasActivePlasmaBeam());

        player.die();
        assertFalse(player.hasActivePlasmaBeam(), "Plasma beam should stop when player dies");
    }

    @Test
    @DisplayName("Plasma beam sway applies when accuracy < 1.0 and is zero when accuracy >= 1.0")
    void testBeamAccuracySway() {
        // Perfect accuracy -> 0 sway
        assertEquals(0.0, WeaponSystem.calculateBeamSwayOffset(1.0, 1, 12.34), 1e-6);

        // Imperfect accuracy -> sway within max bound
        double imperfectAccuracy = 0.5;
        double maxSway = (1.0 - imperfectAccuracy) * 0.17;

        double sway = WeaponSystem.calculateBeamSwayOffset(imperfectAccuracy, 1, 10.0);
        assertTrue(Math.abs(sway) <= maxSway, "Sway should be within max bounds");

        // Verify sway changes smoothly across time
        double swayLater = WeaponSystem.calculateBeamSwayOffset(imperfectAccuracy, 1, 10.2);
        assertTrue(Math.abs(sway - swayLater) > 1e-4, "Sway should vary over time");
    }

    @Test
    @DisplayName("Multi-shot plasma beam creates multiple fanned beams and consumes bulletsPerShot ammo")
    void testMultiShotPlasmaBeam() {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        // Configure weapon to fire 3 bullets per shot (10 points in BULLETS_PER_SHOT = 3 bullets)
        WeaponConfig multiBeamConfig = new WeaponConfig(
                "Triple Plasma Cannon",
                8, 15,
                com.fullsteam.model.DamageVarianceFormula.UNIFORM,
                8, 5, 0, 5, 9, 0,
                10, // 10 points = 3 bullets per shot
                -10, -5, 8, 0,
                java.util.Set.of(),
                Ordinance.PLASMA_BEAM
        );
        player.setWeapon(multiBeamConfig.buildWeapon());

        int initialAmmo = player.getCurrentWeapon().getCurrentAmmo();

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        weaponSystem.handlePrimaryFire(player, input);

        assertEquals(initialAmmo - 3, player.getCurrentWeapon().getCurrentAmmo(), "3 bullets should be consumed per shot");
        assertTrue(player.hasActivePlasmaBeam());
        assertEquals(3, player.getActivePlasmaBeams().size(), "3 distinct fanned beams should be active");

        // Verify angular fan spread across the 3 beams
        var beams = player.getActivePlasmaBeams();
        double dir0Angle = Math.atan2(beams.get(0).getDirection().y, beams.get(0).getDirection().x);
        double dir1Angle = Math.atan2(beams.get(1).getDirection().y, beams.get(1).getDirection().x);
        double dir2Angle = Math.atan2(beams.get(2).getDirection().y, beams.get(2).getDirection().x);

        assertTrue(dir0Angle < dir1Angle, "Beam 0 should be angled lower than beam 1");
        assertTrue(dir1Angle < dir2Angle, "Beam 1 should be angled lower than beam 2");
    }

    @Test
    @DisplayName("Plasma beam damage rate rolls variance per ammo pulse using DamageVarianceFormula")
    void testPlasmaBeamDamageVariance() throws InterruptedException {
        Player player = createPlasmaPlayer(1, 1);
        player.setAimDirection(new Vector2(1, 0));
        gameEntities.add(player);

        // Configure weapon with MIN_OR_MAX variance formula
        WeaponConfig varianceConfig = new WeaponConfig(
                "Variable Plasma Beam",
                5, 25, // Min damage 5, Max damage 25
                com.fullsteam.model.DamageVarianceFormula.MIN_OR_MAX,
                5, 5, 0, 5, 9, 0,
                0, // 1 bullet per shot
                -10, -5, 8, 0,
                java.util.Set.of(),
                Ordinance.PLASMA_BEAM
        );
        player.setWeapon(varianceConfig.buildWeapon());

        PlayerInput input = new PlayerInput();
        input.setLeft(true);

        // Pulse 1
        weaponSystem.handlePrimaryFire(player, input);
        assertTrue(player.hasActivePlasmaBeam());
        double beam1Damage = player.getActivePlasmaBeams().get(0).getDamage();

        double minExpectedRate = player.getCurrentWeapon().getMinDamage() * player.getCurrentWeapon().getFireRate();
        double maxExpectedRate = player.getCurrentWeapon().getMaxDamage() * player.getCurrentWeapon().getFireRate();

        assertTrue(Math.abs(beam1Damage - minExpectedRate) < 1e-3 || Math.abs(beam1Damage - maxExpectedRate) < 1e-3,
                "Beam damage rate should evaluate to either min or max damage rate: " + beam1Damage);

        // Simulate time passing beyond fire interval for next ammo pulse
        double fireRate = player.getCurrentWeapon().getFireRate();
        long intervalMs = (long) (1000.0 / fireRate) + 50;
        Thread.sleep(intervalMs);

        // Pulse 2
        weaponSystem.handlePrimaryFire(player, input);
        assertTrue(player.hasActivePlasmaBeam());
        double beam2Damage = player.getActivePlasmaBeams().get(0).getDamage();

        assertTrue(Math.abs(beam2Damage - minExpectedRate) < 1e-3 || Math.abs(beam2Damage - maxExpectedRate) < 1e-3,
                "Subsequent beam damage rate should also evaluate according to MIN_OR_MAX formula: " + beam2Damage);
    }
}

package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.Rules;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomWeaponsTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private GameEventManager gameEventManager;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
        GameConfig config = GameConfig.builder()
                .worldWidth(2000)
                .worldHeight(2000)
                .teamCount(2)
                .build();
        gameEntities = new GameEntities(config, world);
        gameEventManager = new GameEventManager(gameEntities, (session, msg) -> {});
    }

    @Test
    void testRandomWeaponsDisabledByDefault() {
        Rules rules = Rules.builder().build();
        assertFalse(rules.hasRandomWeapons());
        assertFalse(rules.isEnableRandomWeapons());
    }

    @Test
    void testRandomWeaponsConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(45.0)
                .build();

        assertTrue(rules.hasRandomWeapons());
        assertTrue(rules.isEnableRandomWeapons());
        assertEquals(45.0, rules.getRandomWeaponInterval());
    }

    @Test
    void testWeaponRotationInitialization() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(30.0)
                .build();

        // Just verify RuleSystem initializes without errors
        RuleSystem ruleSystem = new RuleSystem(
                "test-game",
                rules,
                gameEntities,
                gameEventManager,
                msg -> {},
                2
        );

        assertNotNull(ruleSystem);
    }

    @Test
    void testWeaponRotationDoesNotOccurImmediately() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(30.0)
                .build();

        RuleSystem ruleSystem = new RuleSystem(
                "test-game",
                rules,
                gameEntities,
                gameEventManager,
                msg -> {},
                2
        );

        // Add test players
        Player player1 = new Player(1, "Player1", 0, 0, 1, 100);
        Player player2 = new Player(2, "Player2", 100, 100, 2, 100);
        player1.setActive(true);
        player2.setActive(true);
        gameEntities.addPlayer(player1);
        gameEntities.addPlayer(player2);

        String initialWeapon1 = player1.getWeapon().getName();
        String initialWeapon2 = player2.getWeapon().getName();

        // Update immediately - weapons should NOT rotate yet
        ruleSystem.update(0.016); // One frame

        assertEquals(initialWeapon1, player1.getWeapon().getName());
        assertEquals(initialWeapon2, player2.getWeapon().getName());
    }

    @Test
    void testWeaponRotationExcludesHealingWeapons() {
        // Run rotation many times to ensure healing weapons never appear
        Set<String> assignedWeapons = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            WeaponConfig weapon = com.fullsteam.ai.AIWeaponSelector.selectRandomNonHealingWeapon();
            assignedWeapons.add(weapon.getType());
        }

        // Verify Medic Beam never appears
        assertFalse(assignedWeapons.contains("Medic Beam"),
                "Healing weapons should be excluded from random weapon rotation");

        // Verify we got a good variety of weapons
        assertTrue(assignedWeapons.size() >= 10,
                "Should have variety of weapons, got: " + assignedWeapons.size());
    }

    @Test
    void testInactivePlayersDoNotReceiveWeapons() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(0.1) // Very short interval for testing
                .build();

        RuleSystem ruleSystem = new RuleSystem(
                "test-game",
                rules,
                gameEntities,
                gameEventManager,
                msg -> {},
                2
        );

        // Add active and inactive players
        Player activePlayer = new Player(1, "Active", 0, 0, 1, 100);
        Player inactivePlayer = new Player(2, "Inactive", 100, 100, 2, 100);
        activePlayer.setActive(true);
        inactivePlayer.setActive(false);
        gameEntities.addPlayer(activePlayer);
        gameEntities.addPlayer(inactivePlayer);

        String initialInactiveWeapon = inactivePlayer.getWeapon().getName();

        // Wait for rotation
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            fail("Sleep interrupted");
        }

        ruleSystem.update(0.016);

        // Inactive player should still have original weapon
        assertEquals(initialInactiveWeapon, inactivePlayer.getWeapon().getName());
    }

    @Test
    void testMultiplePlayersReceiveDifferentWeapons() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(0.1)
                .build();

        RuleSystem ruleSystem = new RuleSystem(
                "test-game",
                rules,
                gameEntities,
                gameEventManager,
                msg -> {},
                2
        );

        // Add multiple players
        for (int i = 1; i <= 10; i++) {
            Player player = new Player(i, "Player" + i, i * 10, i * 10, (i % 2) + 1, 100);
            player.setActive(true);
            gameEntities.addPlayer(player);
        }

        // Wait for rotation
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            fail("Sleep interrupted");
        }

        ruleSystem.update(0.016);

        // Collect all assigned weapons
        Set<String> weaponTypes = new HashSet<>();
        for (Player player : gameEntities.getAllPlayers()) {
            weaponTypes.add(player.getWeapon().getName());
        }

        // With 10 players and random selection, we should have some variety
        // (not guaranteed all different, but should have at least a few unique weapons)
        assertTrue(weaponTypes.size() >= 2,
                "Multiple players should receive varied weapons, got: " + weaponTypes);
    }

    @Test
    void testWeaponRotationInterval() {
        Rules rules = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(5.0)
                .build();

        assertEquals(5.0, rules.getRandomWeaponInterval());

        // Test validation constraints
        Rules fastRotation = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(5.0) // Minimum
                .build();

        assertEquals(5.0, fastRotation.getRandomWeaponInterval());

        Rules slowRotation = Rules.builder()
                .enableRandomWeapons(true)
                .randomWeaponInterval(300.0) // Maximum
                .build();

        assertEquals(300.0, slowRotation.getRandomWeaponInterval());
    }
}


package com.fullsteam.ai;

import com.fullsteam.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.WeaponSystem;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIPlasmaBeamTest extends BaseTestClass {

    private GameEntities gameEntities;
    private World<Body> world;
    private WeaponSystem weaponSystem;
    private AIPlayerManager aiPlayerManager;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
        GameConfig config = GameConfig.builder()
                .enableAIFilling(false)
                .build();
        gameEntities = new GameEntities(config, world);
        weaponSystem = new WeaponSystem(gameEntities, world);
        aiPlayerManager = new AIPlayerManager(config);
    }

    private AIPlayer createPlasmaAI(int id, int team, double x, double y, double skill) {
        AIPersonality personality = AIPersonality.createForType(AIPersonality.Type.aggressive, skill);
        AIPlayer ai = new AIPlayer(id, "PlasmaAI" + id, x, y, personality, team, 100.0);
        ai.setActive(true);
        ai.setWeapon(WeaponConfig.PLASMA_CANNON_PRESET.buildWeapon());
        gameEntities.add(ai);
        aiPlayerManager.addAIPlayer(ai);
        return ai;
    }

    private Player createEnemyPlayer(int id, int team, double x, double y) {
        Player enemy = new Player(id, "Enemy" + id, x, y, team, 100.0);
        enemy.setActive(true);
        enemy.setWeapon(WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        gameEntities.add(enemy);
        return enemy;
    }

    @Test
    @DisplayName("AI player holds fire continuously across multiple consecutive ticks when engaging enemy")
    void testAIPlayerHoldsFireContinuously() {
        // AI at (100, 100), enemy at (250, 100) - within weapon range (~400)
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.8);
        Player enemy = createEnemyPlayer(2, 2, 250, 100);

        CombatBehavior combat = new CombatBehavior();
        ai.setCurrentBehavior(combat);

        double dt = 1.0 / 60.0;

        // Tick 0: AI generates initial combat input
        PlayerInput input0 = combat.generateInput(ai, gameEntities, dt);
        assertTrue(Boolean.TRUE.equals(input0.isLeft()), "AI should press fire button on initial engagement");

        // Fire initial shot through WeaponSystem to activate plasma beam
        ai.processInput(input0);
        weaponSystem.handlePrimaryFire(ai, input0);
        assertTrue(ai.hasActivePlasmaBeam(), "AI should now have an active plasma beam");

        // Verify across consecutive frames (e.g. 10 ticks) that AI KEEPS holding fire
        for (int frame = 1; frame <= 10; frame++) {
            PlayerInput inputFrame = combat.generateInput(ai, gameEntities, dt);
            assertTrue(Boolean.TRUE.equals(inputFrame.isLeft()),
                    "AI must keep holding fire button on frame " + frame + ", not tap and release");

            ai.processInput(inputFrame);
            weaponSystem.handlePrimaryFire(ai, inputFrame);
            assertTrue(ai.hasActivePlasmaBeam(),
                    "Plasma beam must remain active on frame " + frame);
        }
    }

    @Test
    @DisplayName("AI player smoothly tracks moving enemy while holding plasma beam")
    void testAIPlayerSmoothlyTracksMovingEnemyWhileHolding() {
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.9);
        Player enemy = createEnemyPlayer(2, 2, 250, 100);

        CombatBehavior combat = new CombatBehavior();
        ai.setCurrentBehavior(combat);

        double dt = 1.0 / 60.0;

        // Frame 0: Engage and fire
        PlayerInput input0 = combat.generateInput(ai, gameEntities, dt);
        ai.processInput(input0);
        weaponSystem.handlePrimaryFire(ai, input0);
        assertTrue(ai.hasActivePlasmaBeam());

        double initialWorldY = input0.getWorldY();

        // Move enemy upward in Y across frames: from (250, 100) to (250, 200)
        double prevWorldY = initialWorldY;
        for (int step = 1; step <= 5; step++) {
            enemy.setPosition(250, 100 + step * 20); // Enemy moves northward

            PlayerInput inputStep = combat.generateInput(ai, gameEntities, dt);
            assertTrue(Boolean.TRUE.equals(inputStep.isLeft()), "AI must maintain fire while tracking");

            // Aim position in Y should smoothly track upward toward enemy's new Y position
            assertTrue(inputStep.getWorldY() > prevWorldY - 5.0,
                    "Aim point Y must track upward toward moving enemy (prev=" + prevWorldY + ", cur=" + inputStep.getWorldY() + ")");

            ai.processInput(inputStep);
            weaponSystem.handlePrimaryFire(ai, inputStep);
            assertTrue(ai.hasActivePlasmaBeam(), "Plasma beam must remain active while aim tracking");

            prevWorldY = inputStep.getWorldY();
        }

        // Verify the beam direction rotated to track the target
        Vector2 finalAimDir = ai.getAimDirection();
        assertTrue(finalAimDir.y > 0.1, "Aim direction Y should be turned upward toward tracked enemy: " + finalAimDir.y);
    }

    @Test
    @DisplayName("AI player releases fire and plasma beam stops when target dies")
    void testAIPlayerReleasesFireWhenTargetDies() {
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.8);
        Player enemy = createEnemyPlayer(2, 2, 250, 100);

        CombatBehavior combat = new CombatBehavior();
        ai.setCurrentBehavior(combat);

        double dt = 1.0 / 60.0;

        // Start firing beam
        PlayerInput input = combat.generateInput(ai, gameEntities, dt);
        ai.processInput(input);
        weaponSystem.handlePrimaryFire(ai, input);
        assertTrue(ai.hasActivePlasmaBeam());

        // Target dies
        enemy.setHealth(0.0);
        enemy.setActive(false);

        // Next frame: AI notices target is dead
        PlayerInput nextInput = combat.generateInput(ai, gameEntities, dt);
        assertFalse(Boolean.TRUE.equals(nextInput.isLeft()), "AI should release fire button when target dies");

        ai.processInput(nextInput);
        weaponSystem.handlePrimaryFire(ai, nextInput);
        assertFalse(ai.hasActivePlasmaBeam(), "Plasma beam should stop immediately when fire button is released");
    }

    @Test
    @DisplayName("AI player releases fire when target moves out of range")
    void testAIPlayerReleasesFireWhenTargetMovesOutOfRange() {
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.8);
        Player enemy = createEnemyPlayer(2, 2, 250, 100);

        CombatBehavior combat = new CombatBehavior();
        ai.setCurrentBehavior(combat);

        double dt = 1.0 / 60.0;

        // Start firing
        PlayerInput input = combat.generateInput(ai, gameEntities, dt);
        ai.processInput(input);
        weaponSystem.handlePrimaryFire(ai, input);
        assertTrue(ai.hasActivePlasmaBeam());

        // Enemy moves far out of range (e.g. 1500 units away)
        enemy.setPosition(1500, 100);

        // AI should release fire
        PlayerInput nextInput = combat.generateInput(ai, gameEntities, dt);
        assertFalse(Boolean.TRUE.equals(nextInput.isLeft()),
                "AI should release fire button when target moves beyond range");

        ai.processInput(nextInput);
        weaponSystem.handlePrimaryFire(ai, nextInput);
        assertFalse(ai.hasActivePlasmaBeam(), "Plasma beam should dismiss when out of range");
    }

    @Test
    @DisplayName("AIPlayerManager personality modifiers do not interrupt active plasma beam")
    void testAIPlayerManagerDoesNotDropActivePlasmaBeam() {
        // Patient personality (> 0.7 patience)
        AIPersonality patientPersonality = AIPersonality.builder()
                .patience(0.95) // Very patient
                .accuracy(0.8)
                .aggressiveness(0.8)
                .mobility(0.5)
                .reactionSpeed(0.8)
                .teamwork(0.5)
                .strategicThinking(0.8)
                .build();

        AIPlayer ai = new AIPlayer(1, "PatientAI", 100, 100, patientPersonality, 1, 100.0);
        ai.setActive(true);
        ai.setWeapon(WeaponConfig.PLASMA_CANNON_PRESET.buildWeapon());
        gameEntities.add(ai);
        aiPlayerManager.addAIPlayer(ai);

        Player enemy = createEnemyPlayer(2, 2, 200, 100);

        // Start beam
        PlayerInput initialInput = new PlayerInput();
        initialInput.setLeft(true);
        initialInput.setWorldX(200);
        initialInput.setWorldY(100);
        ai.processInput(initialInput);
        weaponSystem.handlePrimaryFire(ai, initialInput);
        assertTrue(ai.hasActivePlasmaBeam());

        // Update AIPlayerManager across multiple frames
        for (int frame = 0; frame < 20; frame++) {
            aiPlayerManager.update(gameEntities, 0.016);
            PlayerInput genInput = aiPlayerManager.getAllPlayerInputs().get(ai.getId());
            assertNotNull(genInput);
            assertTrue(Boolean.TRUE.equals(genInput.isLeft()),
                    "Active plasma beam must not be dropped by patience check on frame " + frame);
        }
    }

    @Test
    @DisplayName("HeadquartersBehavior maintains continuous plasma beam fire against enemy HQ")
    void testHeadquartersBehaviorHoldsBeam() {
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.8);
        com.fullsteam.physics.Headquarters enemyHQ = new com.fullsteam.physics.Headquarters(
                100, 2, 250, 100, 1000.0
        );
        gameEntities.add(enemyHQ);

        HeadquartersBehavior hqBehavior = new HeadquartersBehavior();
        ai.setCurrentBehavior(hqBehavior);

        double dt = 1.0 / 60.0;

        // Tick 0 sets initial role / target evaluation
        hqBehavior.generateInput(ai, gameEntities, dt);

        // Frame 1: Attack enemy HQ in range
        PlayerInput input1 = hqBehavior.generateInput(ai, gameEntities, dt);
        assertTrue(Boolean.TRUE.equals(input1.isLeft()), "AI should attack enemy HQ in range");

        ai.processInput(input1);
        weaponSystem.handlePrimaryFire(ai, input1);
        assertTrue(ai.hasActivePlasmaBeam());

        // Subsequent frames: AI should hold fire on HQ
        for (int frame = 2; frame <= 10; frame++) {
            PlayerInput inputFrame = hqBehavior.generateInput(ai, gameEntities, dt);
            assertTrue(Boolean.TRUE.equals(inputFrame.isLeft()),
                    "HQ attacker must maintain continuous plasma beam hold on frame " + frame);

            ai.processInput(inputFrame);
            weaponSystem.handlePrimaryFire(ai, inputFrame);
            assertTrue(ai.hasActivePlasmaBeam());
        }
    }

    @Test
    @DisplayName("KothBehavior maintains continuous plasma beam fire against enemies in zone")
    void testKothBehaviorHoldsBeamOnZoneEnemy() {
        AIPlayer ai = createPlasmaAI(1, 1, 100, 100, 0.8);
        com.fullsteam.physics.KothZone zone = new com.fullsteam.physics.KothZone(50, 0, 150, 100, 1.0);
        gameEntities.add(zone);

        Player enemy = createEnemyPlayer(2, 2, 150, 100); // Inside zone

        KothBehavior kothBehavior = new KothBehavior();
        ai.setCurrentBehavior(kothBehavior);

        double dt = 1.0 / 60.0;

        // Frame 0: Engage enemy in zone
        PlayerInput input0 = kothBehavior.generateInput(ai, gameEntities, dt);
        assertTrue(Boolean.TRUE.equals(input0.isLeft()), "Contester should shoot at enemy in zone");

        ai.processInput(input0);
        weaponSystem.handlePrimaryFire(ai, input0);
        assertTrue(ai.hasActivePlasmaBeam());

        // Subsequent frames: hold beam
        for (int frame = 1; frame <= 10; frame++) {
            PlayerInput inputFrame = kothBehavior.generateInput(ai, gameEntities, dt);
            assertTrue(Boolean.TRUE.equals(inputFrame.isLeft()),
                    "KOTH contester must hold plasma beam fire on frame " + frame);

            ai.processInput(inputFrame);
            weaponSystem.handlePrimaryFire(ai, inputFrame);
            assertTrue(ai.hasActivePlasmaBeam());
        }
    }
}

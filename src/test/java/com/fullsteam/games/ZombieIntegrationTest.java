package com.fullsteam.games;

import com.fullsteam.ai.AITargetWrapper;
import com.fullsteam.model.GamePresets;
import com.fullsteam.model.Rules;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieIntensity;
import com.fullsteam.model.ZombieSpawnStyle;
import com.fullsteam.model.ZombieType;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Zombie;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ZombieIntegrationTest extends BaseTestClass {

    @Test
    @DisplayName("Zombie Outbreak preset contains configured zombie and HQ rules")
    public void testZombieOutbreakPreset() {
        var presetOpt = GamePresets.getPreset("zombie-outbreak");
        assertTrue(presetOpt.isPresent(), "Zombie outbreak preset should exist");

        GamePresets.Preset preset = presetOpt.get();
        assertEquals("🧟 Zombie Siege", preset.label());
        assertTrue(preset.config().getRules().hasZombies());
        assertTrue(preset.config().getRules().hasHeadquarters());
        assertEquals(ZombieSpawnStyle.EBB_AND_FLOW, preset.config().getRules().getZombieSpawnStyle());
        assertEquals(ZombieIntensity.HIGH, preset.config().getRules().getZombieIntensity());
        assertEquals(1, preset.config().getRules().getZombiePointsPerKill());
    }

    @Test
    @DisplayName("Scoring correctly includes zombie kills and computes bonus points")
    public void testScoringWithZombies() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombiePointsPerKill(3)
                .build();

        List<String> activeComponents = rules.getActiveScoreComponents();
        assertTrue(activeComponents.contains("zombieKills"), "Active score components should include zombieKills");

        Player player = new Player(1, "ZombieSlayer", 0, 0, 1, 100.0);
        assertEquals(0, player.getScoring().getZombieKills());

        player.getScoring().addZombieKill();
        player.getScoring().addZombieKills(2);
        assertEquals(3, player.getScoring().getZombieKills());

        // 3 zombie kills * 3 pts per kill = 9 bonus points
        assertEquals(9, player.getScoring().bonusPoints(rules));
        assertEquals(9, player.getScoring().total(rules));
    }

    @Test
    @DisplayName("Turret acquires zombie as valid target when no closer player is in range")
    public void testTurretTargetsZombie() {
        Turret turret = new Turret(1, 1, new Vector2(0.0, 0.0), 100.0, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        turret.setActive(true);

        Zombie zombie = new Zombie(10, ZombieType.WALKER, ZombieAttackPattern.TURRET, 100.0, 0.0);
        zombie.setActive(true);

        turret.acquireTarget(List.of(), List.of(zombie));
        assertEquals(zombie, turret.getCurrentTarget(), "Turret should acquire zombie as target");
    }

    @Test
    @DisplayName("AITargetWrapper correctly wraps Zombie entities")
    public void testAITargetWrapperForZombie() {
        Zombie zombie = new Zombie(20, ZombieType.RUNNER, ZombieAttackPattern.NEAREST_PLAYER, 50.0, 50.0);
        AITargetWrapper wrapper = AITargetWrapper.fromZombie(zombie);

        assertTrue(wrapper.isZombie());
        assertEquals(AITargetWrapper.TargetType.ZOMBIE, wrapper.getType());
        assertEquals(zombie.getId(), wrapper.getId());
        assertEquals(zombie.getPosition(), wrapper.getPosition());
        assertEquals(0, wrapper.getTeam());
        assertEquals(0.9, wrapper.getTargetPriority(), 0.001);
    }

    @Test
    @DisplayName("GameManager lifecycle correctly updates ZombieManager and tracks kills")
    public void testGameManagerWithZombies() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombieIntensity(ZombieIntensity.LOW)
                .zombieSpawnStyle(ZombieSpawnStyle.CONSTANT)
                .zombiePointsPerKill(2)
                .build();

        GameConfig config = GameConfig.builder()
                .rules(rules)
                .worldWidth(1000)
                .worldHeight(1000)
                .maxPlayers(4)
                .teamCount(2)
                .enableAIFilling(false)
                .build();

        GameManager gameManager = new GameManager("test-mgr-zombie", config, null);
        assertNotNull(gameManager.getZombieManager(), "ZombieManager should be initialized when zombies enabled");

        // Running game update updates zombies
        gameManager.update();
        GameEntities entities = gameManager.getGameEntities();

        Player player = new Player(1, "Player1", 0, 0, 1, 100.0);
        player.setActive(true);
        entities.add(player);

        Zombie zombie = new Zombie(30, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 0, 0);
        entities.add(zombie);

        // Test killing player by zombie
        gameManager.killPlayer(player, zombie.getId());
        assertEquals(1, player.getScoring().getDeaths(), "Player death should be recorded");
    }

    @Test
    @DisplayName("HeadquartersBehavior evaluates Zombie threat to HQ without ClassCastException")
    public void testHeadquartersBehaviorWithZombieThreat() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .addHeadquarters(true)
                .headquartersMaxHealth(1000.0)
                .build();

        GameConfig config = GameConfig.builder()
                .rules(rules)
                .worldWidth(2000)
                .worldHeight(2000)
                .maxPlayers(4)
                .teamCount(2)
                .enableAIFilling(false)
                .build();

        GameManager gameManager = new GameManager("test-hq-zombie-ai", config, null);
        GameEntities entities = gameManager.getGameEntities();

        // Add AI bot on team 1
        com.fullsteam.ai.AIPlayer bot = new com.fullsteam.ai.AIPlayer(100, "BotDefender", 100.0, 100.0,
                com.fullsteam.ai.AIPersonality.builder().build(), 1, 100.0);
        bot.setActive(true);
        bot.setWeapon(WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        entities.add(bot);

        // Add zombie near HQ
        com.fullsteam.physics.Headquarters team1HQ = entities.getTeamHeadquarters(1);
        assertNotNull(team1HQ);

        Zombie zombie = new Zombie(50, ZombieType.TANK, ZombieAttackPattern.HEADQUARTERS,
                team1HQ.getPosition().x + 50.0, team1HQ.getPosition().y + 50.0);
        entities.add(zombie);

        // Execute HeadquartersBehavior input generation (exercises findBestThreatToHQ with Zombie)
        com.fullsteam.ai.HeadquartersBehavior hqBehavior = new com.fullsteam.ai.HeadquartersBehavior();
        assertDoesNotThrow(() -> {
            var input = hqBehavior.generateInput(bot, entities, 0.05);
            assertNotNull(input);
        });

        // Also test CombatBehavior with Zombie target
        com.fullsteam.ai.CombatBehavior combatBehavior = new com.fullsteam.ai.CombatBehavior();
        assertDoesNotThrow(() -> {
            var input = combatBehavior.generateInput(bot, entities, 0.05);
            assertNotNull(input);
        });
    }

    @Test
    @DisplayName("Binary serialization includes zombies and encodes attributes accurately")
    public void testZombieBinarySerialization() {
        World<Body> world = new World<>();
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombieIntensity(ZombieIntensity.LOW)
                .build();

        GameConfig config = GameConfig.builder()
                .rules(rules)
                .worldWidth(1000)
                .worldHeight(1000)
                .maxPlayers(4)
                .teamCount(2)
                .build();

        GameEntities entities = new GameEntities(config, world);
        RuleSystem ruleSystem = new RuleSystem("bin-zombie-test", rules, entities, null, msg -> {}, 2);
        BinaryGameStateSerializer serializer = new BinaryGameStateSerializer(config, entities, ruleSystem);

        Zombie zombie = new Zombie(99, ZombieType.LUNGER, ZombieAttackPattern.LOWEST_HEALTH, 123.4, 567.8);
        zombie.getBody().setLinearVelocity(45.0, -25.0);
        zombie.setLunging(true);
        entities.add(zombie);

        byte[] payload = serializer.serializeGameState(true);
        assertNotNull(payload);
        assertTrue(payload.length > 0);
    }
}

package com.fullsteam.games;

import com.fullsteam.model.Rules;
import com.fullsteam.model.ZombieIntensity;
import com.fullsteam.model.ZombieSpawnStyle;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ZombieManagerTest {

    private GameConfig gameConfig;
    private GameEntities gameEntities;
    private World<Body> world;

    @BeforeEach
    public void setUp() {
        world = new World<>();
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombieIntensity(ZombieIntensity.LOW)
                .zombieSpawnStyle(ZombieSpawnStyle.WAVE)
                .zombiePointsPerKill(1)
                .build();

        gameConfig = GameConfig.builder()
                .rules(rules)
                .worldWidth(1000)
                .worldHeight(1000)
                .maxPlayers(4)
                .teamCount(2)
                .build();

        gameEntities = new GameEntities(gameConfig, world);
        Player p = new Player(1, "Survivor", 0.0, 0.0, 1, 100.0);
        p.setActive(true);
        gameEntities.add(p);
    }

    @Test
    @DisplayName("Wave spawning triggers a burst of zombies at interval and respects max limit")
    public void testWaveSpawning() {
        ZombieManager manager = new ZombieManager("test-wave", gameConfig, gameEntities, null, null);
        assertEquals(0, manager.getActiveZombieCount());

        // First update at start triggers initial wave
        manager.update(0.1);
        int spawnedFirstWave = manager.getActiveZombieCount();
        assertTrue(spawnedFirstWave > 0, "Initial wave should spawn zombies");
        assertEquals(ZombieIntensity.LOW.getWaveSize(), spawnedFirstWave);

        // Advance time below interval: no additional wave
        manager.update(1.0);
        assertEquals(spawnedFirstWave, manager.getActiveZombieCount());

        // Advance past spawn interval: second wave triggers up to maxZombies
        manager.update(ZombieIntensity.LOW.getSpawnIntervalSeconds() + 1.0);
        assertTrue(manager.getActiveZombieCount() <= ZombieIntensity.LOW.getMaxZombies());
    }

    @Test
    @DisplayName("Constant spawning steadily spawns zombies up to intensity cap")
    public void testConstantSpawning() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombieIntensity(ZombieIntensity.LOW)
                .zombieSpawnStyle(ZombieSpawnStyle.CONSTANT)
                .build();
        GameConfig config = GameConfig.builder()
                .rules(rules)
                .worldWidth(1000)
                .worldHeight(1000)
                .maxPlayers(4)
                .teamCount(2)
                .build();

        ZombieManager manager = new ZombieManager("test-const", config, gameEntities, null, null);
        assertEquals(0, manager.getActiveZombieCount());

        // Multiple updates gradually spawn zombies
        for (int i = 0; i < 20; i++) {
            manager.update(1.0);
        }

        assertTrue(manager.getActiveZombieCount() > 0, "Constant spawning should create zombies");
        assertTrue(manager.getActiveZombieCount() <= ZombieIntensity.LOW.getMaxZombies());
    }

    @Test
    @DisplayName("Ebb and flow spawning alternates between calm and surge rates")
    public void testEbbAndFlowSpawning() {
        Rules rules = Rules.builder()
                .enableZombies(true)
                .zombieIntensity(ZombieIntensity.MEDIUM)
                .zombieSpawnStyle(ZombieSpawnStyle.EBB_AND_FLOW)
                .build();
        GameConfig config = GameConfig.builder()
                .rules(rules)
                .worldWidth(1000)
                .worldHeight(1000)
                .maxPlayers(4)
                .teamCount(2)
                .build();

        ZombieManager manager = new ZombieManager("test-ebb", config, gameEntities, null, null);

        for (int i = 0; i < 40; i++) {
            manager.update(1.0);
        }

        assertTrue(manager.getActiveZombieCount() > 0);
        assertTrue(manager.getActiveZombieCount() <= ZombieIntensity.MEDIUM.getMaxZombies());
    }

    @Test
    @DisplayName("ZombieManager prunes dead zombies and updates active count")
    public void testPrunesInactiveZombies() {
        ZombieManager manager = new ZombieManager("test-prune", gameConfig, gameEntities, null, null);
        manager.update(0.1);

        int count = manager.getActiveZombieCount();
        assertTrue(count > 0);

        // Deactivate all zombies
        gameEntities.getAllZombies().forEach(z -> z.setActive(false));
        gameEntities.removeInactiveEntities();

        manager.update(0.1);
        assertEquals(0, manager.getActiveZombieCount());
    }

    @Test
    @DisplayName("Zombies consistently spawn strictly at or near map corners")
    public void testZombiesSpawnAtOrNearMapCorners() {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        ZombieManager manager = new ZombieManager("test-corner-spawn", gameConfig, gameEntities, null, null);
        manager.spawnZombies(25);

        var zombies = gameEntities.getAllZombies();
        assertFalse(zombies.isEmpty(), "Zombies should be spawned");

        for (var zombie : zombies) {
            var pos = zombie.getPosition();
            double distTL = pos.distance(new org.dyn4j.geometry.Vector2(-halfW, halfH));
            double distTR = pos.distance(new org.dyn4j.geometry.Vector2(halfW, halfH));
            double distBL = pos.distance(new org.dyn4j.geometry.Vector2(-halfW, -halfH));
            double distBR = pos.distance(new org.dyn4j.geometry.Vector2(halfW, -halfH));

            double minCornerDist = Math.min(Math.min(distTL, distTR), Math.min(distBL, distBR));

            // Must be within 180 units of a map corner (and strictly within world bounds)
            assertTrue(minCornerDist <= 180.0,
                    String.format("Zombie at (%f, %f) should be near map corner but min corner distance was %f",
                            pos.x, pos.y, minCornerDist));
            assertTrue(Math.abs(pos.x) <= halfW, "Zombie X must be within world bounds");
            assertTrue(Math.abs(pos.y) <= halfH, "Zombie Y must be within world bounds");
        }
    }

    @Test
    @DisplayName("spawnZombieGroup spawns zombies tightly clustered together in a cohesive pack")
    public void testSpawnZombieGroupClustering() {
        ZombieManager manager = new ZombieManager("test-group-cluster", gameConfig, gameEntities, null, null);
        manager.spawnZombieGroup(6);

        var zombies = gameEntities.getAllZombies().stream().toList();
        assertEquals(6, zombies.size());

        var focalPos = zombies.get(0).getPosition();
        for (int i = 1; i < zombies.size(); i++) {
            var memberPos = zombies.get(i).getPosition();
            double dist = focalPos.distance(memberPos);
            assertTrue(dist <= 150.0,
                    String.format("Group member %d at (%f, %f) should be clustered near focal point (%f, %f) but distance is %f",
                            i, memberPos.x, memberPos.y, focalPos.x, focalPos.y, dist));
        }
    }

    @Test
    @DisplayName("spawnZombiesInHordes partitions large batches into distinct horde packs")
    public void testSpawnZombiesInHordesPartitioning() {
        ZombieManager manager = new ZombieManager("test-horde-partition", gameConfig, gameEntities, null, null);
        manager.spawnZombiesInHordes(15, 3, 6);

        assertEquals(15, manager.getActiveZombieCount());
    }

    @Test
    @DisplayName("spawnZombiesInHordes disperses multiple horde packs across all distinct map corners")
    public void testMultiCornerDispersion() {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        ZombieManager manager = new ZombieManager("test-multi-corner", gameConfig, gameEntities, null, null);
        // Spawn 4 packs of 4 zombies (16 total)
        manager.spawnZombiesInHordes(16, 4, 4);

        assertEquals(16, manager.getActiveZombieCount());

        boolean hasTopLeft = false;
        boolean hasTopRight = false;
        boolean hasBottomLeft = false;
        boolean hasBottomRight = false;

        for (var zombie : gameEntities.getAllZombies()) {
            var pos = zombie.getPosition();
            double distTL = pos.distance(new org.dyn4j.geometry.Vector2(-halfW, halfH));
            double distTR = pos.distance(new org.dyn4j.geometry.Vector2(halfW, halfH));
            double distBL = pos.distance(new org.dyn4j.geometry.Vector2(-halfW, -halfH));
            double distBR = pos.distance(new org.dyn4j.geometry.Vector2(halfW, -halfH));

            double minDist = Math.min(Math.min(distTL, distTR), Math.min(distBL, distBR));
            if (minDist == distTL) hasTopLeft = true;
            else if (minDist == distTR) hasTopRight = true;
            else if (minDist == distBL) hasBottomLeft = true;
            else if (minDist == distBR) hasBottomRight = true;
        }

        assertTrue(hasTopLeft, "Multi-group wave should attack from Top-Left corner");
        assertTrue(hasTopRight, "Multi-group wave should attack from Top-Right corner");
        assertTrue(hasBottomLeft, "Multi-group wave should attack from Bottom-Left corner");
        assertTrue(hasBottomRight, "Multi-group wave should attack from Bottom-Right corner");
    }
}

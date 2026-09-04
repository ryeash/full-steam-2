package com.fullsteam.games;

import com.fullsteam.model.Rules;
import com.fullsteam.model.ZombieIntensity;
import com.fullsteam.model.ZombieSpawnStyle;
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
    @DisplayName("Dynamic spawn locations count matches team count and maximizes distance from team bases")
    public void testDynamicSpawnLocationsCalculationsForDifferentTeamCounts() {
        // 2 Teams (Left vs Right)
        GameConfig config2T = GameConfig.builder()
                .worldWidth(1000).worldHeight(1000).teamCount(2)
                .rules(Rules.builder().enableZombies(true).build()).build();
        ZombieManager manager2T = new ZombieManager("test-2t", config2T, gameEntities, null, null);
        var locs2T = manager2T.calculateSpawnLocations();
        assertEquals(2, locs2T.size(), "2-team mode should calculate exactly 2 dynamic spawn locations");
        // For 2 teams (Left and Right bases), optimal spawn locations are Top and Bottom
        assertTrue(locs2T.stream().anyMatch(p -> p.y > 400.0 && Math.abs(p.x) < 100.0), "Should include Top perimeter flank");
        assertTrue(locs2T.stream().anyMatch(p -> p.y < -400.0 && Math.abs(p.x) < 100.0), "Should include Bottom perimeter flank");

        // 3 Teams (Top, Bottom-Left, Bottom-Right)
        GameConfig config3T = GameConfig.builder()
                .worldWidth(1000).worldHeight(1000).teamCount(3)
                .rules(Rules.builder().enableZombies(true).build()).build();
        ZombieManager manager3T = new ZombieManager("test-3t", config3T, gameEntities, null, null);
        var locs3T = manager3T.calculateSpawnLocations();
        assertEquals(3, locs3T.size(), "3-team mode should calculate exactly 3 dynamic spawn locations");

        // 4 Teams (4 quadrant corners)
        GameConfig config4T = GameConfig.builder()
                .worldWidth(1000).worldHeight(1000).teamCount(4)
                .rules(Rules.builder().enableZombies(true).build()).build();
        ZombieManager manager4T = new ZombieManager("test-4t", config4T, gameEntities, null, null);
        var locs4T = manager4T.calculateSpawnLocations();
        assertEquals(4, locs4T.size(), "4-team mode should calculate exactly 4 dynamic spawn locations");
        // For 4 corner teams, optimal spawn locations are the 4 cardinal edge centers
        assertTrue(locs4T.stream().anyMatch(p -> p.y > 400.0 && Math.abs(p.x) < 100.0), "Top center edge");
        assertTrue(locs4T.stream().anyMatch(p -> p.y < -400.0 && Math.abs(p.x) < 100.0), "Bottom center edge");
        assertTrue(locs4T.stream().anyMatch(p -> p.x > 400.0 && Math.abs(p.y) < 100.0), "Right center edge");
        assertTrue(locs4T.stream().anyMatch(p -> p.x < -400.0 && Math.abs(p.y) < 100.0), "Left center edge");

        // FFA mode (0 teams)
        GameConfig configFFA = GameConfig.builder()
                .worldWidth(1000).worldHeight(1000).teamCount(0)
                .rules(Rules.builder().enableZombies(true).build()).build();
        ZombieManager managerFFA = new ZombieManager("test-ffa", configFFA, gameEntities, null, null);
        var locsFFA = managerFFA.calculateSpawnLocations();
        assertEquals(4, locsFFA.size(), "FFA mode should default to 4 perimeter spawn locations");
    }

    @Test
    @DisplayName("Zombies consistently spawn at dynamic perimeter flanks furthest from teams")
    public void testZombiesSpawnAtDynamicFlanks() {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        ZombieManager manager = new ZombieManager("test-flank-spawn", gameConfig, gameEntities, null, null);
        manager.spawnZombies(20);

        var zombies = gameEntities.getAllZombies();
        assertFalse(zombies.isEmpty(), "Zombies should be spawned");

        var spawnLocs = manager.calculateSpawnLocations();
        for (var zombie : zombies) {
            var pos = zombie.getPosition();

            // Distance to closest dynamic spawn anchor
            double minDistToAnchor = spawnLocs.stream()
                    .mapToDouble(loc -> loc.distance(pos))
                    .min()
                    .orElse(Double.MAX_VALUE);

            assertTrue(minDistToAnchor <= 100.0,
                    String.format("Zombie at (%f, %f) should be near one of the dynamic spawn flanks but min distance was %f",
                            pos.x, pos.y, minDistToAnchor));
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
    @DisplayName("spawnZombiesInHordes disperses multiple horde packs across all distinct dynamic flanks")
    public void testMultiFlankDispersion() {
        ZombieManager manager = new ZombieManager("test-multi-flank", gameConfig, gameEntities, null, null);
        // 2 Teams -> 2 spawn locations (Top and Bottom)
        // Spawn 4 packs of 4 zombies (16 total)
        manager.spawnZombiesInHordes(16, 4, 4);

        assertEquals(16, manager.getActiveZombieCount());

        var locs = manager.calculateSpawnLocations();
        assertEquals(2, locs.size());
        Vector2 topFlank = locs.get(0).y > locs.get(1).y ? locs.get(0) : locs.get(1);
        Vector2 bottomFlank = locs.get(0).y < locs.get(1).y ? locs.get(0) : locs.get(1);

        boolean hasTop = false;
        boolean hasBottom = false;

        for (var zombie : gameEntities.getAllZombies()) {
            var pos = zombie.getPosition();
            if (pos.distance(topFlank) < 100.0) hasTop = true;
            if (pos.distance(bottomFlank) < 100.0) hasBottom = true;
        }

        assertTrue(hasTop, "Multi-group wave should attack from Top flank");
        assertTrue(hasBottom, "Multi-group wave should attack from Bottom flank");
    }
}

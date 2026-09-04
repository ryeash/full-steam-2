package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieIntensity;
import com.fullsteam.model.ZombieSpawnStyle;
import com.fullsteam.model.ZombieType;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Zombie;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages NPC Zombie lifecycle, spawning strategies (WAVE, CONSTANT, EBB_AND_FLOW),
 * intensity scaling, and AI tick coordination.
 */
public class ZombieManager {
    private static final Logger log = LoggerFactory.getLogger(ZombieManager.class);

    private final String gameId;
    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final GameEventManager gameEventManager;
    private final TerrainGenerator terrainGenerator;

    @Getter
    private int waveNumber = 0;
    private long nextWaveTime = 0L;
    private double constantSpawnTimer = 0.0;

    // Ebb and Flow tracking
    public enum EbbFlowPhase {
        LULL,
        SURGE
    }

    @Getter
    private EbbFlowPhase ebbFlowPhase = EbbFlowPhase.LULL;
    private double phaseTimer = 0.0;
    private static final double LULL_DURATION = 20.0;
    private static final double SURGE_DURATION = 14.0;

    public ZombieManager(String gameId,
                         GameConfig gameConfig,
                         GameEntities gameEntities,
                         GameEventManager gameEventManager,
                         TerrainGenerator terrainGenerator) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.gameEventManager = gameEventManager;
        this.terrainGenerator = terrainGenerator;

        // Schedule initial wave immediately on match start
        this.nextWaveTime = 0L;
    }

    /**
     * Update zombie spawning timers, spawn new zombies according to rules, and tick zombie AI.
     */
    public void update(double deltaTime) {
        Rules rules = gameConfig.getRules();
        if (!rules.hasZombies()) {
            return;
        }

        // 1. Spawning logic
        switch (rules.getZombieSpawnStyle()) {
            case WAVE -> updateWaveSpawning(rules);
            case CONSTANT -> updateConstantSpawning(rules, deltaTime);
            case EBB_AND_FLOW -> updateEbbAndFlowSpawning(rules, deltaTime);
        }

        // 2. AI coordination
        for (Zombie zombie : gameEntities.getAllZombies()) {
            if (zombie.isActive()) {
                zombie.tickAI(gameEntities, deltaTime);
            }
        }
    }

    private void updateWaveSpawning(Rules rules) {
        long now = System.currentTimeMillis();
        if (now >= nextWaveTime) {
            waveNumber++;
            ZombieIntensity intensity = rules.getZombieIntensity();
            int currentActive = (int) gameEntities.getAllZombies().stream().filter(Zombie::isActive).count();
            int maxZombies = intensity.getMaxZombies();
            int waveSize = intensity.getWaveSize();

            int toSpawn = Math.min(waveSize, Math.max(0, maxZombies - currentActive));

            if (toSpawn > 0) {
                spawnZombiesInHordes(toSpawn, 4, 8);
                String msg = String.format("🧟 Wave %d: %d Zombies approaching!", waveNumber, toSpawn);
                if (gameEventManager != null) {
                    gameEventManager.broadcastEvent(GameEvent.builder()
                            .message(msg)
                            .category(GameEvent.EventCategory.WARNING)
                            .color("#ff4444")
                            .target(GameEvent.EventTarget.builder().type(GameEvent.EventTarget.TargetType.ALL).build())
                            .displayDuration(3000L)
                            .build());
                }
            }

            double interval = intensity.getSpawnIntervalSeconds();
            nextWaveTime = now + (long) (interval * 1000);
        }
    }

    private void updateConstantSpawning(Rules rules, double deltaTime) {
        constantSpawnTimer += deltaTime;
        if (constantSpawnTimer >= 2.5) {
            constantSpawnTimer = 0.0;
            ZombieIntensity intensity = rules.getZombieIntensity();
            int currentActive = (int) gameEntities.getAllZombies().stream().filter(Zombie::isActive).count();
            int maxZombies = intensity.getMaxZombies();

            int deficit = maxZombies - currentActive;
            if (deficit > 0) {
                int batch = Math.min(deficit, Math.max(1, (int) Math.round(2 * intensity.getSpawnRateMultiplier())));
                // Constant spawning spawns small packs (2-4) or single stragglers
                spawnZombiesInHordes(batch, 2, 4);
            }
        }
    }

    private void updateEbbAndFlowSpawning(Rules rules, double deltaTime) {
        phaseTimer += deltaTime;
        ZombieIntensity intensity = rules.getZombieIntensity();
        int currentActive = (int) gameEntities.getAllZombies().stream().filter(Zombie::isActive).count();

        if (ebbFlowPhase == EbbFlowPhase.LULL) {
            if (phaseTimer >= LULL_DURATION) {
                // Switch to Surge
                ebbFlowPhase = EbbFlowPhase.SURGE;
                phaseTimer = 0.0;
                if (gameEventManager != null) {
                    gameEventManager.broadcastEvent(GameEvent.builder()
                            .message("⚠️ The Zombie horde is surging!")
                            .category(GameEvent.EventCategory.WARNING)
                            .color("#ff2222")
                            .target(GameEvent.EventTarget.builder().type(GameEvent.EventTarget.TargetType.ALL).build())
                            .displayDuration(3500L)
                            .build());
                }
            } else {
                // Lull trickle: maintain low population with sporadic scouts/pairs
                constantSpawnTimer += deltaTime;
                if (constantSpawnTimer >= 4.0) {
                    constantSpawnTimer = 0.0;
                    int lullCap = Math.max(3, intensity.getMaxZombies() / 3);
                    if (currentActive < lullCap) {
                        spawnZombiesInHordes(1, 1, 2);
                    }
                }
            }
        } else { // SURGE phase
            if (phaseTimer >= SURGE_DURATION) {
                // Switch back to Lull
                ebbFlowPhase = EbbFlowPhase.LULL;
                phaseTimer = 0.0;
                if (gameEventManager != null) {
                    gameEventManager.broadcastSystemMessage("🌿 The horde subsides... for now.");
                }
            } else {
                // Rapid surge spawning: dense horde swarms breaching together
                constantSpawnTimer += deltaTime;
                if (constantSpawnTimer >= 1.8) {
                    constantSpawnTimer = 0.0;
                    int maxZombies = intensity.getMaxZombies();
                    int deficit = maxZombies - currentActive;
                    if (deficit > 0) {
                        int batch = Math.min(deficit, Math.max(2, (int) Math.round(4 * intensity.getSpawnRateMultiplier())));
                        spawnZombiesInHordes(batch, 3, 6);
                    }
                }
            }
        }
    }

    /**
     * Spawn a specific number of zombies, grouped into clusters / horde packs.
     */
    public void spawnZombies(int count) {
        spawnZombiesInHordes(count, 3, 6);
    }

    /**
     * Spawns totalCount zombies partitioned into horde groups between minGroupSize and maxGroupSize,
     * deterministically dispersing groups across distinct perimeter flanks.
     */
    public void spawnZombiesInHordes(int totalCount, int minGroupSize, int maxGroupSize) {
        if (totalCount <= 0) return;

        List<Integer> groupSizes = new ArrayList<>();
        int remaining = totalCount;
        while (remaining > 0) {
            int groupSize;
            if (remaining <= maxGroupSize) {
                groupSize = remaining;
            } else {
                groupSize = ThreadLocalRandom.current().nextInt(minGroupSize, maxGroupSize + 1);
                if (remaining - groupSize < minGroupSize && remaining - groupSize > 0) {
                    groupSize = Math.max(minGroupSize, remaining / 2);
                }
            }
            groupSize = Math.max(1, Math.min(groupSize, remaining));
            groupSizes.add(groupSize);
            remaining -= groupSize;
        }

        // Multi-corner dispersion: shuffle all 4 map corners (0: Top-Left, 1: Top-Right, 2: Bottom-Left, 3: Bottom-Right)
        // so multi-group waves attack simultaneously from different map corners.
        List<Integer> availableCorners = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(availableCorners, ThreadLocalRandom.current());

        for (int i = 0; i < groupSizes.size(); i++) {
            if (i > 0 && i % availableCorners.size() == 0) {
                Collections.shuffle(availableCorners, ThreadLocalRandom.current());
            }
            int corner = availableCorners.get(i % availableCorners.size());
            spawnZombieGroup(groupSizes.get(i), corner);
        }
        log.debug("Spawned {} zombies across {} horde groups for game {}", totalCount, groupSizes.size(), gameId);
    }

    /**
     * Spawns a cluster / horde pack of zombies breaching together near a map corner.
     */
    public void spawnZombieGroup(int groupSize) {
        int randomCorner = ThreadLocalRandom.current().nextInt(4);
        spawnZombieGroup(groupSize, randomCorner);
    }

    /**
     * Spawns a cluster / horde pack of zombies breaching together in a specific map corner
     * (0: Top-Left, 1: Top-Right, 2: Bottom-Left, 3: Bottom-Right).
     */
    public void spawnZombieGroup(int groupSize, int cornerIndex) {
        if (groupSize <= 0) return;

        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        // 1. Pick a primary cluster center in the designated map corner
        Vector2 clusterCenter = findValidSpawnPosition(35.0, cornerIndex);

        // Pack behavior: 65% chance the pack shares an attack focus
        boolean packSharesTarget = ThreadLocalRandom.current().nextDouble() < 0.65;
        ZombieAttackPattern sharedPattern = Zombie.selectRandomAttackPattern();

        for (int i = 0; i < groupSize; i++) {
            ZombieType type = Zombie.selectRandomZombieType();
            ZombieAttackPattern pattern = packSharesTarget ? sharedPattern : Zombie.selectRandomAttackPattern();

            Vector2 spawnPos;
            if (i == 0) {
                spawnPos = clusterCenter.copy();
            } else {
                spawnPos = findGroupMemberPosition(clusterCenter, halfW, halfH, type.getRadius());
            }

            Zombie zombie = new Zombie(Config.nextEntityId(), type, pattern, spawnPos.x, spawnPos.y);
            gameEntities.add(zombie);
        }
    }

    /**
     * Finds a candidate position clustered near the cluster center within the corner zone.
     */
    private Vector2 findGroupMemberPosition(Vector2 clusterCenter,
                                            double halfW,
                                            double halfH,
                                            double clearRadius) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double offsetX = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * (20.0 + attempt * 4.0);
            double offsetY = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * (20.0 + attempt * 4.0);

            double cx = clusterCenter.x + offsetX;
            double cy = clusterCenter.y + offsetY;

            // Clamp strictly within inner corner perimeter
            double clampedX = Math.max(-halfW + 35.0, Math.min(halfW - 35.0, cx));
            double clampedY = Math.max(-halfH + 35.0, Math.min(halfH - 35.0, cy));
            Vector2 candidate = new Vector2(clampedX, clampedY);

            // Verify corner proximity constraint (must stay within corner zone)
            double distTL = candidate.distance(new Vector2(-halfW, halfH));
            double distTR = candidate.distance(new Vector2(halfW, halfH));
            double distBL = candidate.distance(new Vector2(-halfW, -halfH));
            double distBR = candidate.distance(new Vector2(halfW, -halfH));
            double minCornerDist = Math.min(Math.min(distTL, distTR), Math.min(distBL, distBR));

            if (minCornerDist <= 170.0) {
                if (terrainGenerator == null || terrainGenerator.isPositionClear(candidate, clearRadius)) {
                    return candidate;
                }
            }
        }
        return clusterCenter.copy();
    }

    /**
     * Spawns a single zombie with specific properties.
     */
    public Zombie spawnZombie(ZombieType type, ZombieAttackPattern pattern, Vector2 position) {
        Zombie zombie = new Zombie(Config.nextEntityId(), type, pattern, position.x, position.y);
        gameEntities.add(zombie);
        return zombie;
    }

    /**
     * Find a valid clear spawn position along map corners,
     * avoiding immediate player proximity. All zombies spawn strictly at or near map corners.
     */
    private Vector2 findValidSpawnPosition(double clearRadius) {
        return findValidSpawnPosition(clearRadius, null);
    }

    /**
     * Find a valid clear spawn position in a specific corner (0: Top-Left, 1: Top-Right, 2: Bottom-Left, 3: Bottom-Right),
     * or any corner if targetCorner is null.
     */
    private Vector2 findValidSpawnPosition(double clearRadius, Integer targetCorner) {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        Collection<Player> players = gameEntities.getAllPlayers();

        // 1. Primary attempts: strictly on designated corner zone + clear of terrain + clear of players
        for (int attempt = 0; attempt < 50; attempt++) {
            int corner = (targetCorner != null) ? targetCorner : ThreadLocalRandom.current().nextInt(4);
            Vector2 candidate = generateCornerCandidate(halfW, halfH, corner);

            if (terrainGenerator != null && !terrainGenerator.isPositionClear(candidate, clearRadius)) {
                continue;
            }

            boolean tooCloseToPlayer = false;
            for (Player p : players) {
                if (p.isActive() && candidate.distance(p.getPosition()) < 180.0) {
                    tooCloseToPlayer = true;
                    break;
                }
            }

            if (!tooCloseToPlayer) {
                return candidate;
            }
        }

        // 2. Secondary attempts: strictly on designated corner zone + clear of terrain (relaxing player proximity)
        for (int attempt = 0; attempt < 25; attempt++) {
            int corner = (targetCorner != null) ? targetCorner : ThreadLocalRandom.current().nextInt(4);
            Vector2 candidate = generateCornerCandidate(halfW, halfH, corner);
            if (terrainGenerator == null || terrainGenerator.isPositionClear(candidate, clearRadius)) {
                return candidate;
            }
        }

        // 3. Fallback: pick a point on designated corner perimeter
        int fallbackCorner = (targetCorner != null) ? targetCorner : ThreadLocalRandom.current().nextInt(4);
        return generateCornerCandidate(halfW, halfH, fallbackCorner);
    }

    private static Vector2 generateCornerCandidate(double halfW, double halfH, int cornerIndex) {
        // Distance inset from the outer corner walls (between 35 and 90 units inside)
        double xInset = 35.0 + ThreadLocalRandom.current().nextDouble() * 55.0;
        double yInset = 35.0 + ThreadLocalRandom.current().nextDouble() * 55.0;

        double x, y;
        switch (cornerIndex % 4) {
            case 0 -> { // Top-Left corner
                x = -halfW + xInset;
                y = halfH - yInset;
            }
            case 1 -> { // Top-Right corner
                x = halfW - xInset;
                y = halfH - yInset;
            }
            case 2 -> { // Bottom-Left corner
                x = -halfW + xInset;
                y = -halfH + yInset;
            }
            default -> { // Bottom-Right corner
                x = halfW - xInset;
                y = -halfH + yInset;
            }
        }
        return new Vector2(x, y);
    }

    public int getActiveZombieCount() {
        return (int) gameEntities.getAllZombies().stream().filter(Zombie::isActive).count();
    }
}

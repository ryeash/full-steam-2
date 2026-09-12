package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieIntensity;
import com.fullsteam.model.ZombieType;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeamSpawnArea;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.Zombie;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final TeamSpawnManager teamSpawnManager;

    @Getter
    @Setter
    private WeaponSystem weaponSystem;

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
                         TerrainGenerator terrainGenerator,
                         TeamSpawnManager teamSpawnManager) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.gameEventManager = gameEventManager;
        this.terrainGenerator = terrainGenerator;
        this.teamSpawnManager = teamSpawnManager != null
                ? teamSpawnManager
                : new TeamSpawnManager(gameConfig.getWorldWidth(), gameConfig.getWorldHeight(), gameConfig.getTeamCount());

        // Schedule initial wave immediately on match start
        this.nextWaveTime = 0L;
    }

    public ZombieManager(String gameId,
                         GameConfig gameConfig,
                         GameEntities gameEntities,
                         GameEventManager gameEventManager,
                         TerrainGenerator terrainGenerator,
                         TeamSpawnManager teamSpawnManager,
                         WeaponSystem weaponSystem) {
        this(gameId, gameConfig, gameEntities, gameEventManager, terrainGenerator, teamSpawnManager);
        this.weaponSystem = weaponSystem;
    }

    public ZombieManager(String gameId,
                         GameConfig gameConfig,
                         GameEntities gameEntities,
                         GameEventManager gameEventManager,
                         TerrainGenerator terrainGenerator) {
        this(gameId, gameConfig, gameEntities, gameEventManager, terrainGenerator, null, null);
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

        // 2. AI coordination, firing, and death handling
        for (Zombie zombie : gameEntities.getAllZombies()) {
            if (zombie.isActive()) {
                zombie.tickAI(gameEntities, deltaTime);
                if (weaponSystem != null && zombie.getWeapon() != null) {
                    weaponSystem.handleZombieFire(zombie);
                }
            } else if (!zombie.isDeathHandled()) {
                zombie.onDeath(gameEntities);
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
     * Calculates the dynamic zombie spawn locations based on team count and world dimensions.
     * The number of spawn locations equals the team count for team modes (k >= 2), positioned
     * along the map perimeter on the angular bisectors between adjacent team spawn zones
     * to maximize distance from all team bases. For FFA (teamCount < 2), defaults to 4 cardinal perimeter flanks.
     */
    public List<Vector2> calculateSpawnLocations() {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;
        double inset = 45.0; // Inset from outer map boundary walls

        double boxW = halfW - inset;
        double boxH = halfH - inset;

        if (teamSpawnManager != null && teamSpawnManager.isTeamSpawningEnabled()) {
            Map<Integer, TeamSpawnArea> teamAreas = teamSpawnManager.getTeamAreas();
            if (!teamAreas.isEmpty()) {
                // Collect and sort team area centers by angle around the map center
                List<Double> teamAngles = new ArrayList<>();
                for (TeamSpawnArea area : teamAreas.values()) {
                    Vector2 center = area.getCenter();
                    double angle = Math.atan2(center.y, center.x);
                    if (angle < 0) {
                        angle += 2.0 * Math.PI;
                    }
                    teamAngles.add(angle);
                }
                Collections.sort(teamAngles);

                int k = teamAngles.size();
                List<Vector2> spawnLocations = new ArrayList<>(k);
                for (int i = 0; i < k; i++) {
                    double a1 = teamAngles.get(i);
                    double a2 = teamAngles.get((i + 1) % k);
                    if (a2 <= a1) {
                        a2 += 2.0 * Math.PI;
                    }
                    double bisectorAngle = (a1 + a2) / 2.0;

                    Vector2 perimeterPoint = projectAngleToBox(bisectorAngle, boxW, boxH);
                    spawnLocations.add(perimeterPoint);
                }
                return spawnLocations;
            }
        }

        // FFA fallback: 4 cardinal perimeter points (Top, Right, Bottom, Left)
        return List.of(
                new Vector2(0, boxH),
                new Vector2(boxW, 0),
                new Vector2(0, -boxH),
                new Vector2(-boxW, 0)
        );
    }

    private static Vector2 projectAngleToBox(double angle, double boxW, double boxH) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double tx = (Math.abs(cos) > 1e-6) ? (cos > 0 ? boxW / cos : -boxW / cos) : Double.MAX_VALUE;
        double ty = (Math.abs(sin) > 1e-6) ? (sin > 0 ? boxH / sin : -boxH / sin) : Double.MAX_VALUE;

        double t = Math.min(tx, ty);
        return new Vector2(t * cos, t * sin);
    }

    /**
     * Gets the number of dynamic spawn locations (equal to teamCount for team modes, or 4 for FFA).
     */
    public int getSpawnLocationCount() {
        return calculateSpawnLocations().size();
    }

    /**
     * Spawns totalCount zombies partitioned into horde groups between minGroupSize and maxGroupSize,
     * deterministically dispersing groups across distinct dynamic spawn locations (furthest from team spawn zones).
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

        int numLocations = getSpawnLocationCount();
        List<Integer> availableLocations = new ArrayList<>();
        for (int i = 0; i < numLocations; i++) {
            availableLocations.add(i);
        }
        Collections.shuffle(availableLocations, ThreadLocalRandom.current());

        for (int i = 0; i < groupSizes.size(); i++) {
            if (i > 0 && i % availableLocations.size() == 0) {
                Collections.shuffle(availableLocations, ThreadLocalRandom.current());
            }
            int locationIndex = availableLocations.get(i % availableLocations.size());
            spawnZombieGroup(groupSizes.get(i), locationIndex);
        }
        log.debug("Spawned {} zombies across {} horde groups for game {}", totalCount, groupSizes.size(), gameId);
    }

    /**
     * Spawns a cluster / horde pack of zombies breaching together near a random dynamic spawn location.
     */
    public void spawnZombieGroup(int groupSize) {
        int randomLocation = ThreadLocalRandom.current().nextInt(getSpawnLocationCount());
        spawnZombieGroup(groupSize, randomLocation);
    }

    /**
     * Spawns a cluster / horde pack of zombies breaching together at a specific spawn location index.
     */
    public void spawnZombieGroup(int groupSize, int locationIndex) {
        if (groupSize <= 0) return;

        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        // 1. Pick a primary cluster center at the designated dynamic spawn location
        Vector2 clusterCenter = findValidSpawnPosition(35.0, locationIndex);

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
     * Finds a candidate position clustered near the cluster center within the spawn zone.
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

            // Clamp strictly within inner map perimeter
            double clampedX = Math.max(-halfW + 35.0, Math.min(halfW - 35.0, cx));
            double clampedY = Math.max(-halfH + 35.0, Math.min(halfH - 35.0, cy));
            Vector2 candidate = new Vector2(clampedX, clampedY);

            if (terrainGenerator == null || terrainGenerator.isPositionClear(candidate, clearRadius)) {
                return candidate;
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
     * Find a valid clear spawn position along map perimeter flanks,
     * avoiding immediate player proximity.
     */
    private Vector2 findValidSpawnPosition(double clearRadius) {
        return findValidSpawnPosition(clearRadius, null);
    }

    /**
     * Find a valid clear spawn position at a specific dynamic spawn location index,
     * or any location if targetLocationIndex is null.
     */
    private Vector2 findValidSpawnPosition(double clearRadius, Integer targetLocationIndex) {
        double width = gameConfig.getWorldWidth();
        double height = gameConfig.getWorldHeight();
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        List<Vector2> spawnLocations = calculateSpawnLocations();
        int numLocations = spawnLocations.size();

        Collection<Player> players = gameEntities.getAllPlayers();

        // 1. Primary attempts: strictly on designated spawn location + clear of terrain + clear of players
        for (int attempt = 0; attempt < 50; attempt++) {
            int locIndex = (targetLocationIndex != null) ? (targetLocationIndex % numLocations) : ThreadLocalRandom.current().nextInt(numLocations);
            Vector2 anchor = spawnLocations.get(locIndex);
            Vector2 candidate = generateCandidateNearLocation(anchor, halfW, halfH);

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

        // 2. Secondary attempts: strictly on designated spawn location + clear of terrain (relaxing player proximity)
        for (int attempt = 0; attempt < 25; attempt++) {
            int locIndex = (targetLocationIndex != null) ? (targetLocationIndex % numLocations) : ThreadLocalRandom.current().nextInt(numLocations);
            Vector2 anchor = spawnLocations.get(locIndex);
            Vector2 candidate = generateCandidateNearLocation(anchor, halfW, halfH);
            if (terrainGenerator == null || terrainGenerator.isPositionClear(candidate, clearRadius)) {
                return candidate;
            }
        }

        // 3. Fallback: pick the anchor point directly
        int fallbackIndex = (targetLocationIndex != null) ? (targetLocationIndex % numLocations) : ThreadLocalRandom.current().nextInt(numLocations);
        return spawnLocations.get(fallbackIndex).copy();
    }

    private static Vector2 generateCandidateNearLocation(Vector2 anchor, double halfW, double halfH) {
        // Spread candidates along perimeter around anchor point (up to +/- 45 units)
        double jitterX = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * 45.0;
        double jitterY = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * 45.0;
        double minInset = 35.0;

        double clampedX = Math.max(-halfW + minInset, Math.min(halfW - minInset, anchor.x + jitterX));
        double clampedY = Math.max(-halfH + minInset, Math.min(halfH - minInset, anchor.y + jitterY));
        return new Vector2(clampedX, clampedY);
    }

    public int getActiveZombieCount() {
        return (int) gameEntities.getAllZombies().stream().filter(Zombie::isActive).count();
    }
}

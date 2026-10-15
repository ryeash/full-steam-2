package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeamSpawnArea;
import com.fullsteam.physics.TeamSpawnManager;
import org.dyn4j.geometry.Vector2;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages spawn point selection for players.
 * Handles team-based spawning, FFA spawning, and ensures spawn points are safe and clear of obstacles.
 */
public class SpawnPointManager {
    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final TeamSpawnManager teamSpawnManager;
    private final TerrainGenerator terrainGenerator;

    public SpawnPointManager(GameConfig gameConfig, GameEntities gameEntities,
                             TeamSpawnManager teamSpawnManager, TerrainGenerator terrainGenerator) {
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.teamSpawnManager = teamSpawnManager;
        this.terrainGenerator = terrainGenerator;
    }

    /**
     * Find a varied spawn point for a team, trying to avoid clustering with
     * other active teammates. Only considers same-team players so that the
     * anti-clustering logic doesn't push spawns away from the team zone.
     *
     * @param team Team number
     * @return Spawn point for the team
     */
    public Vector2 findVariedSpawnPointForTeam(int team) {
        Vector2 bestSpawnPoint = null;
        double maxMinDistance = -1;

        for (int attempt = 0; attempt < 5; attempt++) {
            Vector2 candidate = findSpawnPointForTeam(team);

            double minDistanceToTeammate = Double.MAX_VALUE;
            for (Player player : gameEntities.getAllPlayers()) {
                if (player.getId() != -1 && player.getTeam() == team && player.isActive()) {
                    double distance = candidate.distance(player.getPosition());
                    minDistanceToTeammate = Math.min(minDistanceToTeammate, distance);
                }
            }

            if (minDistanceToTeammate > maxMinDistance) {
                maxMinDistance = minDistanceToTeammate;
                bestSpawnPoint = candidate;
            }
        }

        return bestSpawnPoint != null ? bestSpawnPoint : findSpawnPointForTeam(team);
    }

    /**
     * Find a spawn point for a specific team.
     * Uses team-based spawn areas if team mode is enabled, otherwise FFA spawning.
     * Always keeps team players within their own team zone — never falls back to
     * FFA spawning for team-mode players.
     *
     * @param team Team number (0 for FFA)
     * @return Spawn point for the team
     */
    public Vector2 findSpawnPointForTeam(int team) {
        if (gameConfig.isFreeForAll() || team == 0) {
            return findFFASpawnPoint();
        }

        if (teamSpawnManager.isTeamSpawningEnabled()) {
            // Only avoid same-team players so that enemies in a different zone
            // don't cause unnecessary spawn failures in ours.
            List<Player> teammates = gameEntities.getAllPlayers().stream()
                    .filter(p -> p.getTeam() == team)
                    .toList();
            Vector2 teamSpawnPoint = teamSpawnManager.getSafeTeamSpawnPoint(team, teammates, 100.0);

            if (terrainGenerator.isPositionClear(teamSpawnPoint, 50.0)) {
                return teamSpawnPoint;
            }

            // If that's blocked, try more candidates within the team area
            TeamSpawnArea teamArea = teamSpawnManager.getTeamArea(team);
            if (teamArea != null) {
                // First pass: full terrain clearance
                for (int attempts = 0; attempts < 15; attempts++) {
                    Vector2 candidate = teamArea.generateSpawnPoint();
                    if (terrainGenerator.isPositionClear(candidate, 50.0)) {
                        return candidate;
                    }
                }

                // Second pass: relaxed clearance radius so we still stay in the
                // correct team zone rather than falling back to FFA
                for (int attempts = 0; attempts < 10; attempts++) {
                    Vector2 candidate = teamArea.generateSpawnPoint();
                    if (terrainGenerator.isPositionClear(candidate, 20.0)) {
                        return candidate;
                    }
                }

                // Third pass: minimum player radius clearance within team area
                for (int attempts = 0; attempts < 20; attempts++) {
                    Vector2 candidate = teamArea.generateSpawnPoint();
                    if (terrainGenerator.isPositionClear(candidate, Config.PLAYER_RADIUS)) {
                        return candidate;
                    }
                }

                // Search outward from team area center in concentric rings to find closest clear spot
                Vector2 center = teamArea.getCenter();
                double maxRadius = Math.max(teamArea.getWidth(), teamArea.getHeight());
                for (double r = 30.0; r <= maxRadius; r += 20.0) {
                    for (int angleDeg = 0; angleDeg < 360; angleDeg += 30) {
                        double rad = Math.toRadians(angleDeg);
                        Vector2 candidate = new Vector2(center.x + r * Math.cos(rad), center.y + r * Math.sin(rad));
                        if (terrainGenerator.isPositionClear(candidate, Config.PLAYER_RADIUS)) {
                            return candidate;
                        }
                    }
                }

                // Final guaranteed-clear fallback: use terrainGenerator's safe spawn position
                return terrainGenerator.getSafeSpawnPosition(Config.PLAYER_RADIUS);
            }
        }

        // Only reached if team spawning is not enabled — true FFA fallback
        return findFFASpawnPoint();
    }

    /**
     * Find a safe spawn point for Free For All mode.
     *
     * @return FFA spawn point
     */
    public Vector2 findFFASpawnPoint() {
        if (teamSpawnManager != null) {
            Vector2 ffaSpawnPoint = teamSpawnManager.getSafeFFASpawnPoint(gameEntities.getAllPlayers(), 100.0);

            // Verify it's clear of terrain obstacles
            if (terrainGenerator.isPositionClear(ffaSpawnPoint, 50.0)) {
                return ffaSpawnPoint;
            }
        }

        // Use terrain generator's safe spawn position method
        Vector2 terrainSafeSpawn = terrainGenerator.getSafeSpawnPosition(50.0);

        // Double-check it's not too close to existing players
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.isActive() && terrainSafeSpawn.distance(player.getPosition()) < 100.0) {
                // Try legacy spawn as final fallback
                return findLegacySpawnPoint();
            }
        }

        return terrainSafeSpawn;
    }

    /**
     * Legacy spawn point logic for backward compatibility.
     *
     * @return Legacy spawn point
     */
    public Vector2 findLegacySpawnPoint() {
        for (int attempts = 0; attempts < 20; attempts++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldWidth() - 100);
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldHeight() - 100);
            Vector2 candidate = new Vector2(x, y);

            if (!terrainGenerator.isPositionClear(candidate, Config.PLAYER_RADIUS)) {
                continue;
            }

            boolean tooClose = false;
            for (Player other : gameEntities.getAllPlayers()) {
                if (other.isActive() && other.getPosition().distance(candidate) < 100) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                return candidate;
            }
        }

        return terrainGenerator.getSafeSpawnPosition(Config.PLAYER_RADIUS);
    }
}


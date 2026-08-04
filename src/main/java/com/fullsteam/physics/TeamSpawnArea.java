package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a team's spawn area and future base location.
 * Provides spawn points within the team's designated area and will support
 * future base features like vehicle garages, supply depots, etc.
 */
@Getter
public class TeamSpawnArea {
    private final int teamNumber;
    private final Vector2 center;
    private final double width;
    private final double height;
    private final Vector2 minBounds;
    private final Vector2 maxBounds;

    public TeamSpawnArea(int teamNumber, Vector2 center, double width, double height) {
        this.teamNumber = teamNumber;
        this.center = center.copy();
        this.width = width;
        this.height = height;

        // Calculate bounds for spawn area (slightly smaller than full area for safety)
        double spawnWidth = width * 0.8; // 80% of area width
        double spawnHeight = height * 0.8; // 80% of area height

        this.minBounds = new Vector2(
                center.x - spawnWidth / 2.0,
                center.y - spawnHeight / 2.0
        );
        this.maxBounds = new Vector2(
                center.x + spawnWidth / 2.0,
                center.y + spawnHeight / 2.0
        );
    }

    /**
     * Generate a random spawn point within this team's area.
     * Ensures players spawn safely within their team's territory.
     *
     * @return Random spawn point within team area
     */
    public Vector2 generateSpawnPoint() {
        double x = minBounds.x + ThreadLocalRandom.current().nextDouble() * (maxBounds.x - minBounds.x);
        double y = minBounds.y + ThreadLocalRandom.current().nextDouble() * (maxBounds.y - minBounds.y);
        return new Vector2(x, y);
    }

    /**
     * Find a safe spawn point within the team area, avoiding other players.
     *
     * @param existingPlayers List of existing players to avoid
     * @param minDistance     Minimum distance from other players
     * @return Safe spawn point, or random point if no safe location found
     */
    public Vector2 findSafeSpawnPoint(Collection<Player> existingPlayers, double minDistance) {
        for (int attempts = 0; attempts < 20; attempts++) {
            Vector2 candidate = generateSpawnPoint();

            boolean isSafe = true;
            for (Player player : existingPlayers) {
                if (player.isActive() && candidate.distance(player.getPosition()) < minDistance) {
                    isSafe = false;
                    break;
                }
            }

            if (isSafe) {
                return candidate;
            }
        }

        // If no safe spot found, return random point in area
        return generateSpawnPoint();
    }
}

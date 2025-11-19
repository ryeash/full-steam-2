package com.fullsteam.games;

import com.fullsteam.model.EntityWorldDensity;
import com.fullsteam.physics.Obstacle;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural terrain and obstacle generation system.
 * Creates varied terrain biomes with appropriate obstacles, cover, and visual elements.
 */
@Getter
public class TerrainGenerator {

    private final double worldWidth;
    private final double worldHeight;
    private final boolean reserveCenterForOddball;
    private final EntityWorldDensity configuredDensity;
    private final List<Obstacle> generatedObstacles = new ArrayList<>();

    public TerrainGenerator(double worldWidth, double worldHeight) {
        this(worldWidth, worldHeight, false, EntityWorldDensity.RANDOM);
    }

    public TerrainGenerator(double worldWidth, double worldHeight, boolean reserveCenterForOddball, EntityWorldDensity configuredDensity) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.reserveCenterForOddball = reserveCenterForOddball;
        this.configuredDensity = configuredDensity;
        generateObstacles();
    }

    /**
     * Generate obstacles appropriate for the terrain type.
     */
    private void generateObstacles() {
        int targetObstacleCount = calculateObstacleCountForWorldSize(configuredDensity);
        int attemptsPerObstacle = 50;
        int successfulPlacements = 0;
        for (int i = 0; i < targetObstacleCount && successfulPlacements < targetObstacleCount; i++) {
            Obstacle obstacle = generateObstacleWithCollisionCheck(attemptsPerObstacle);
            if (obstacle != null) {
                generatedObstacles.add(obstacle);
                successfulPlacements++;
            }
        }
    }

    private int calculateObstacleCountForWorldSize(EntityWorldDensity density) {
        double worldArea = worldWidth * worldHeight;
        double baseObstaclesPerUnit = 0.000005; // Base density per square unit
        double densityMultiplier = density.getMultiplier();
        int baseCount = (int) (worldArea * baseObstaclesPerUnit * densityMultiplier);
        int variation = (int) (baseCount * 0.2);
        int finalCount = baseCount + ThreadLocalRandom.current().nextInt(variation * 2 + 1) - variation;
        return Math.max(3, Math.min(finalCount, (int) (worldArea * 0.0001))); // Max 1 obstacle per 10,000 square units
    }

    /**
     * Generate an obstacle with collision checking to prevent overlaps.
     */
    private Obstacle generateObstacleWithCollisionCheck(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * (worldWidth - 100);
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * (worldHeight - 100);
            Obstacle candidate = Obstacle.createChaoticObstacle(x, y);
            if (isObstaclePositionClear(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Check if an obstacle position is clear of overlaps with existing obstacles and terrain features.
     */
    private boolean isObstaclePositionClear(Obstacle obstacle) {
        Vector2 position = obstacle.getPosition();
        double radius = obstacle.getBoundingRadius();

        // If oddball is enabled, exclude center area (100 unit radius to be safe)
        if (reserveCenterForOddball) {
            double distanceFromCenter = position.distance(new Vector2(0, 0));
            double oddballClearZone = 100.0; // Clear 100 units around center for oddball
            if (distanceFromCenter < oddballClearZone + radius) {
                return false; // Too close to oddball spawn
            }
        }

        // Add minimum spacing buffer to prevent tight packing
        double spacing = Math.max(10.0, radius * 0.2); // At least 10 units or 20% of radius
        double totalRadius = radius + spacing;

        // Check against existing obstacles
        for (Obstacle existingObstacle : generatedObstacles) {
            double distance = position.distance(existingObstacle.getPosition());
            double existingRadius = existingObstacle.getBoundingRadius();
            double existingSpacing = Math.max(10.0, existingRadius * 0.2);
            double minDistance = totalRadius + existingRadius + existingSpacing;

            if (distance < minDistance) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a position is suitable for placing objects (avoids terrain features).
     */
    public boolean isPositionClear(Vector2 position, double radius) {
        double spacing = Math.max(5.0, radius * 0.1); // At least 5 units or 10% of radius
        double totalRadius = radius + spacing;
        for (Obstacle obstacle : generatedObstacles) {
            double distance = position.distance(obstacle.getPosition());
            double obstacleRadius = obstacle.getBoundingRadius(); // Use the proper bounding radius
            double obstacleSpacing = Math.max(5.0, obstacleRadius * 0.1);
            if (distance < obstacleRadius + totalRadius + obstacleSpacing) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a safe spawn position that avoids terrain features.
     */
    public Vector2 getSafeSpawnPosition(double radius) {
        for (int attempt = 0; attempt < 50; attempt++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldWidth * 0.8;
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldHeight * 0.8;
            Vector2 candidate = new Vector2(x, y);

            if (isPositionClear(candidate, radius)) {
                return candidate;
            }
        }
        // Fallback to center if no clear position found
        return new Vector2(0, 0);
    }

    /**
     * Get terrain data for client rendering.
     */
    public Map<String, Object> getTerrainData() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> obstacles = new ArrayList<>();
        for (Obstacle obstacle : generatedObstacles) {
            Map<String, Object> obstacleData = new HashMap<>();
            obstacleData.put("id", obstacle.getId());
            obstacleData.put("x", obstacle.getPosition().x);
            obstacleData.put("y", obstacle.getPosition().y);
            obstacleData.put("radius", obstacle.getBoundingRadius());
            obstacleData.put("type", obstacle.getType());
            obstacles.add(obstacleData);
        }
        data.put("obstacles", obstacles);
        return data;
    }
}

package com.fullsteam.games;

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

    // Generated terrain data
    private final List<Obstacle> generatedObstacles = new ArrayList<>();

    public TerrainGenerator(double worldWidth, double worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        generateTerrain();
    }

    /**
     * Main terrain generation method.
     */
    private void generateTerrain() {
        generateObstacles();
    }

    /**
     * Generate obstacles appropriate for the terrain type.
     */
    private void generateObstacles() {
        ObstacleDensity density = getRandomObstacleDensity();
        int targetObstacleCount = calculateObstacleCountForWorldSize(density);
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

    public enum ObstacleDensity {
        SPARSE,   // Fewer obstacles, more open space
        DENSE,    // Normal obstacle density
        CHOKED    // Many obstacles, cramped battlefield
    }

    private ObstacleDensity getRandomObstacleDensity() {
        ObstacleDensity[] densities = ObstacleDensity.values();
        return densities[ThreadLocalRandom.current().nextInt(densities.length)];
    }

    private int calculateObstacleCountForWorldSize(ObstacleDensity density) {
        // Calculate base count based on world area
        double worldArea = worldWidth * worldHeight;
        double baseObstaclesPerUnit = 0.000005; // Base density per square unit

        // Adjust density multiplier based on selected density
        double densityMultiplier = switch (density) {
            case SPARSE -> 0.6 + ThreadLocalRandom.current().nextDouble() * 0.3;  // 0.6-0.9x
            case DENSE -> 1.2 + ThreadLocalRandom.current().nextDouble() * 0.6;   // 1.2-1.8x
            case CHOKED -> 2.0 + ThreadLocalRandom.current().nextDouble();  // 2.0-3.0x
        };

        int baseCount = (int) (worldArea * baseObstaclesPerUnit * densityMultiplier);

        // Add some randomness (±20%)
        int variation = (int) (baseCount * 0.2);
        int finalCount = baseCount + ThreadLocalRandom.current().nextInt(variation * 2 + 1) - variation;

        // Ensure minimum and maximum bounds
        return Math.max(3, Math.min(finalCount, (int) (worldArea * 0.0001))); // Max 1 obstacle per 10,000 square units
    }

    /**
     * Generate an obstacle with collision checking to prevent overlaps.
     */
    private Obstacle generateObstacleWithCollisionCheck(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Allow obstacles to spawn closer to edges for more dramatic placement
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * (worldWidth - 100);
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * (worldHeight - 100);

            // Create obstacle at this position
            Obstacle candidate = Obstacle.createChaoticObstacle(x, y);

            // Check if this position is clear
            if (isObstaclePositionClear(candidate)) {
                return candidate;
            }
        }

        // Could not find a clear position after maxAttempts
        return null;
    }

    /**
     * Check if an obstacle position is clear of overlaps with existing obstacles and terrain features.
     */
    private boolean isObstaclePositionClear(Obstacle obstacle) {
        Vector2 position = obstacle.getPosition();
        double radius = obstacle.getBoundingRadius();

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
        // Add minimum spacing buffer
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
            obstacleData.put("type", "Boulder");
            obstacles.add(obstacleData);
        }
        data.put("obstacles", obstacles);
        return data;
    }
}

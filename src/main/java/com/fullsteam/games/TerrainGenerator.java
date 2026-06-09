package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.EntityWorldDensity;
import com.fullsteam.physics.Obstacle;
import lombok.Getter;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Geometry;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural terrain and obstacle generation system.
 * Creates varied terrain biomes with appropriate obstacles, cover, and visual elements.
 */
@Getter
public class TerrainGenerator {

    private final World<Body> world;
    private final double worldWidth;
    private final double worldHeight;
    private final boolean reserveCenterForOddball;
    private final EntityWorldDensity configuredDensity;
    private final List<Obstacle> generatedObstacles = new ArrayList<>();

    public TerrainGenerator(World<Body> world, GameConfig gameConfig) {
        this.world = world;
        this.worldWidth = ((AxisAlignedBounds) world.getBounds()).getWidth();
        this.worldHeight = ((AxisAlignedBounds) world.getBounds()).getHeight();
        this.reserveCenterForOddball = gameConfig.getRules().hasOddball();
        this.configuredDensity = gameConfig.getRules().getObstacleDensity();
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
            Obstacle candidate = createChaoticObstacle(x, y);
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

    public void moveToOpenPlace(Body bodyToPlace) {
        Vector2 initialPosition = bodyToPlace.getTransform().getTranslation();
        boolean isPositionClear = isPositionClear(initialPosition, bodyToPlace.getRotationDiscRadius());
        for (int i = 0; i < 20 && !isPositionClear; i++) {
            double offsetX = (Math.random() - 0.5) * (2 * bodyToPlace.getRotationDiscRadius());
            double offsetY = (Math.random() - 0.5) * (2 * bodyToPlace.getRotationDiscRadius());
            bodyToPlace.getTransform().setTranslation(initialPosition.copy().add(offsetX, offsetY));
            isPositionClear = isPositionClear(initialPosition, bodyToPlace.getRotationDiscRadius());
        }
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
     * Create a physics body for the obstacle based on its type.
     */
    public static Body createObstacleBody(Obstacle.ObstacleType type) {
        Body body = new Body();
        List<Convex> shapes = createShapeForType(type);
        for (Convex shape : shapes) {
            BodyFixture bodyFixture = body.addFixture(shape);
            bodyFixture.setRestitution(0.6);
        }
        body.setMass(MassType.INFINITE);
        double rotation = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        body.getTransform().setRotation(rotation);
        return body;
    }

    /**
     * Create the appropriate shape based on obstacle type.
     */
    private static List<Convex> createShapeForType(Obstacle.ObstacleType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (type) {
            case BOULDER -> List.of(createCircularShape(random));
            case HOUSE -> List.of(createRectangularShape(random));
            case WALL_SEGMENT -> List.of(createWallShape(random));
            case TRIANGLE_ROCK -> List.of(createTriangularShape(random));
            case POLYGON_DEBRIS -> List.of(createIrregularPolygon(random));
            case HEXAGON_CRYSTAL -> List.of(createRegularPolygon(ThreadLocalRandom.current().nextInt(4, 9), random));
            case DIAMOND_STONE -> List.of(createDiamondShape(random));
            case L_SHAPED_WALL -> createLShape(random);
            case CROSS_BARRIER -> createCrossShape(random);
        };
    }

    private static Convex createCircularShape(ThreadLocalRandom random) {
        double radius = random.nextDouble(25, 100);
        return new Circle(radius);
    }

    private static Convex createRectangularShape(ThreadLocalRandom random) {
        double width = random.nextDouble(80, 240);
        double height = random.nextDouble(60, 200);
        return new Rectangle(width, height);
    }

    private static Convex createWallShape(ThreadLocalRandom random) {
        double length = random.nextDouble(80, 180);
        double thickness = random.nextDouble(16, 35);
        return Geometry.createRectangle(length, thickness);
    }

    private static Convex createTriangularShape(ThreadLocalRandom random) {
        double baseSize = random.nextDouble(45, 180);
        double type = random.nextDouble();
        if (type < .33) {
            return Geometry.createEquilateralTriangle(baseSize);
        } else if (type < .66) {
            return Geometry.createIsoscelesTriangle(baseSize, baseSize / 2);
        } else {
            return Geometry.createRightTriangle(baseSize, baseSize / 2, random.nextBoolean());
        }
    }

    private static Convex createIrregularPolygon(ThreadLocalRandom random) {
        double size = random.nextDouble(45, 130);
        double choice = random.nextDouble();
        if (choice < .33) {
            return Geometry.createPolygonalEllipse(10, size, size / 3);
        } else if (choice < .66) {
            return Geometry.createPolygonalHalfEllipse(5, size, size / 2);
        } else {
            return Geometry.createPolygonalCapsule(2, size, size / 2);
        }
    }

    private static Convex createRegularPolygon(int sides, ThreadLocalRandom random) {
        double radius = random.nextDouble(50, 150);
        return Geometry.createPolygonalCircle(sides, radius);
    }

    private static Convex createDiamondShape(ThreadLocalRandom random) {
        double width = random.nextDouble(35, 150);
        double height = random.nextDouble(35, 150);
        return Geometry.createPolygonalEllipse(4, width, height);
    }

    private static List<Convex> createLShape(ThreadLocalRandom random) {
        double size = random.nextDouble(35, 180);
        Rectangle lower = Geometry.createRectangle(size, size / 4);
        lower.translate(size / 2, 0);
        Rectangle upper = Geometry.createRectangle(size / 4, size);
        upper.translate(0, size / 2);
        return List.of(upper, lower);
    }

    private static List<Convex> createCrossShape(ThreadLocalRandom random) {
        double size = random.nextDouble(50, 120);
        return List.of(
                Geometry.createRectangle(size, size / 4),
                Geometry.createRectangle(size / 4, size));
    }

    /**
     * Factory method to create extra chaotic obstacles with maximum randomization.
     */
    public static Obstacle createChaoticObstacle(double x, double y) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Obstacle.ObstacleType type = Obstacle.ObstacleType.values()[ThreadLocalRandom.current().nextInt(Obstacle.ObstacleType.values().length)];
        double xOffset = random.nextGaussian() * 15;
        double yOffset = random.nextGaussian() * 15;
        return new Obstacle(Config.nextEntityId(), x + xOffset, y + yOffset, type, createObstacleBody(type));
    }
}

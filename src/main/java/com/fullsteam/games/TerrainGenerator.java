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
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    private final EntityWorldDensity configuredDensity;
    private final List<Obstacle> generatedObstacles = new ArrayList<>();

    public TerrainGenerator(World<Body> world, GameConfig gameConfig) {
        this.world = world;
        this.worldWidth = ((AxisAlignedBounds) world.getBounds()).getWidth();
        this.worldHeight = ((AxisAlignedBounds) world.getBounds()).getHeight();
        this.configuredDensity = gameConfig.getRules().getObstacleDensity();
        generateObstacles();
    }

    /**
     * Generate obstacles appropriate for the terrain type.
     *
     * <p>Obstacles are only placed within a single (positive) world quadrant and
     * then mirrored across the x-axis, the y-axis, and both axes. The result is a
     * world with four-fold (90-degree) symmetry so that every team faces an
     * identical layout. Because each placed base obstacle yields four obstacles,
     * the per-quadrant target is scaled down accordingly.
     */
    private void generateObstacles() {
        int targetObstacleCount = calculateObstacleCountForWorldSize(configuredDensity);
        int quadrantTarget = Math.max(1, targetObstacleCount / 4);
        int attemptsPerObstacle = 50;
        for (int i = 0; i < quadrantTarget; i++) {
            Obstacle base = generateQuadrantObstacleWithCollisionCheck(attemptsPerObstacle);
            if (base != null) {
                addObstacleWithSymmetricMirrors(base);
            }
        }
    }

    /**
     * Add a base obstacle along with its three mirror images, producing a set of
     * four obstacles that are symmetric across both world axes.
     */
    private void addObstacleWithSymmetricMirrors(Obstacle base) {
        generatedObstacles.add(base);
        generatedObstacles.add(createMirroredObstacle(base, true, false));  // across the x-axis
        generatedObstacles.add(createMirroredObstacle(base, false, true));  // across the y-axis
        generatedObstacles.add(createMirroredObstacle(base, true, true));   // across both axes
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
     * Generate a base obstacle within a single quadrant, with collision checking
     * to prevent overlaps once the obstacle is mirrored into the other quadrants.
     */
    private Obstacle generateQuadrantObstacleWithCollisionCheck(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = ThreadLocalRandom.current().nextDouble() * (worldWidth / 2.0 - 50);
            double y = ThreadLocalRandom.current().nextDouble() * (worldHeight / 2.0 - 50);
            Obstacle candidate = createChaoticObstacle(x, y);
            if (isQuadrantObstaclePositionClear(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Check that a base obstacle is a valid placement for a symmetric world.
     *
     * <p>In addition to the normal overlap checks, the obstacle must stay clear of
     * both axes by at least its radius plus spacing so that its own mirror images
     * (reflected across each axis) do not overlap it. Because the existing
     * obstacle set is always kept symmetric, validating the base candidate alone
     * guarantees that all of its mirrors are clear as well.
     */
    private boolean isQuadrantObstaclePositionClear(Obstacle obstacle) {
        Vector2 position = obstacle.getPosition();
        double radius = obstacle.getBoundingRadius();
        double spacing = Math.max(10.0, radius * 0.1);
        double axisClearance = radius + spacing;
        if (Math.abs(position.x) < axisClearance || Math.abs(position.y) < axisClearance) {
            return false; // Too close to an axis; would overlap its mirror image
        }
        return isObstaclePositionClear(obstacle);
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
     * Create a mirror image of the given obstacle by reflecting its geometry and
     * position across the x-axis, the y-axis, or both.
     *
     * <p>The source body's rotation and the reflection are baked directly into the
     * mirrored fixture vertices, so the resulting body needs no transform rotation
     * of its own. This keeps the four symmetric copies perfectly congruent.
     *
     * @param flipX reflect across the x-axis (negate the y coordinate)
     * @param flipY reflect across the y-axis (negate the x coordinate)
     */
    private Obstacle createMirroredObstacle(Obstacle source, boolean flipX, boolean flipY) {
        Body sourceBody = source.getBody();
        Transform transform = sourceBody.getTransform();
        Vector2 sourcePos = transform.getTranslation();
        double newX = flipY ? -sourcePos.x : sourcePos.x;
        double newY = flipX ? -sourcePos.y : sourcePos.y;
        Vector2 newPos = new Vector2(newX, newY);

        Body mirroredBody = new Body();
        for (int i = 0; i < sourceBody.getFixtureCount(); i++) {
            Convex mirroredShape = mirrorShape(sourceBody.getFixture(i).getShape(), transform, flipX, flipY, newPos);
            BodyFixture fixture = mirroredBody.addFixture(mirroredShape);
            fixture.setRestitution(0.6);
        }
        mirroredBody.setMass(MassType.INFINITE);
        return new Obstacle(Config.nextEntityId(), newX, newY, source.getType(), mirroredBody);
    }

    /**
     * Reflect a single convex shape from the source body's frame into the mirrored
     * body's local frame.
     *
     * <p>The source body transform (rotation + translation) is applied first to move
     * the shape into world space, the reflection is performed about the world origin
     * using dyn4j's {@link Geometry} flip helpers (which also correct the winding
     * order), and finally the shape is shifted into the mirrored body's local frame
     * (whose origin sits at {@code newPos}).
     */
    private Convex mirrorShape(Convex shape, Transform transform, boolean flipX, boolean flipY, Vector2 newPos) {
        if (shape instanceof Polygon polygon) {
            Vector2[] localVertices = polygon.getVertices();
            Vector2[] worldVertices = new Vector2[localVertices.length];
            for (int i = 0; i < localVertices.length; i++) {
                worldVertices[i] = transform.getTransformed(localVertices[i]);
            }
            Polygon mirrored = new Polygon(worldVertices);
            Vector2 origin = new Vector2(0, 0);
            if (flipX) {
                mirrored = Geometry.flipAlongTheXAxis(mirrored, origin);
            }
            if (flipY) {
                mirrored = Geometry.flipAlongTheYAxis(mirrored, origin);
            }
            mirrored.translate(-newPos.x, -newPos.y);
            return mirrored;
        } else if (shape instanceof Circle circle) {
            Vector2 worldCenter = transform.getTransformed(circle.getCenter());
            if (flipY) {
                worldCenter.x = -worldCenter.x;
            }
            if (flipX) {
                worldCenter.y = -worldCenter.y;
            }
            Circle mirrored = new Circle(circle.getRadius());
            mirrored.translate(worldCenter.x - newPos.x, worldCenter.y - newPos.y);
            return mirrored;
        }
        throw new IllegalArgumentException("Cannot mirror unsupported shape type: " + shape.getClass().getName());
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
        return switch (type) {
            case BOULDER -> List.of(createCircularShape());
            case HOUSE -> List.of(createRectangularShape());
            case WALL_SEGMENT -> List.of(createWallShape());
            case TRIANGLE_ROCK -> List.of(createTriangularShape());
            case POLYGON_DEBRIS -> List.of(createIrregularPolygon());
            case HEXAGON_CRYSTAL -> List.of(createRegularPolygon(ThreadLocalRandom.current().nextInt(4, 9)));
            case DIAMOND_STONE -> List.of(createDiamondShape());
            case L_SHAPED_WALL -> createLShape();
            case CROSS_BARRIER -> createCrossShape();
        };
    }

    private static Convex createCircularShape() {
        double radius = random().nextDouble(25, 100);
        return new Circle(radius);
    }

    private static Convex createRectangularShape() {
        double width = random().nextDouble(80, 240);
        double height = random().nextDouble(60, 200);
        return new Rectangle(width, height);
    }

    private static Convex createWallShape() {
        double length = random().nextDouble(80, 180);
        double thickness = random().nextDouble(16, 35);
        return Geometry.createRectangle(length, thickness);
    }

    private static Convex createTriangularShape() {
        double baseSize = random().nextDouble(45, 180);
        double type = random().nextDouble();
        if (type < .33) {
            return Geometry.createEquilateralTriangle(baseSize);
        } else if (type < .66) {
            return Geometry.createIsoscelesTriangle(baseSize, baseSize / 2);
        } else {
            return Geometry.createRightTriangle(baseSize, baseSize / 2, random().nextBoolean());
        }
    }

    private static Convex createIrregularPolygon() {
        double size = random().nextDouble(45, 130);
        double choice = random().nextDouble();
        if (choice < .33) {
            return Geometry.createPolygonalEllipse(10, size, size / 3);
        } else if (choice < .66) {
            return Geometry.createPolygonalHalfEllipse(5, size, size / 2);
        } else {
            return Geometry.createPolygonalCapsule(2, size, size / 2);
        }
    }

    private static Convex createRegularPolygon(int sides) {
        double radius = random().nextDouble(50, 150);
        return Geometry.createPolygonalCircle(sides, radius);
    }

    private static Convex createDiamondShape() {
        double width = random().nextDouble(35, 150);
        double height = random().nextDouble(35, 150);
        return Geometry.createPolygonalEllipse(4, width, height);
    }

    private static List<Convex> createLShape() {
        double size = random().nextDouble(35, 180);
        Rectangle lower = Geometry.createRectangle(size, size / 4);
        lower.translate(size / 2, 0);
        int multiplier = random().nextInt(1, 3);
        Rectangle upper = Geometry.createRectangle(size / 4, size / multiplier);
        upper.translate(0, size / 2 / multiplier);
        return List.of(upper, lower);
    }

    private static List<Convex> createCrossShape() {
        double size = random().nextDouble(50, 120);
        return List.of(
                Geometry.createRectangle(size, size / 4),
                Geometry.createRectangle(size / 4, size));
    }

    /**
     * Factory method to create extra chaotic obstacles with maximum randomization.
     */
    public static Obstacle createChaoticObstacle(double x, double y) {
        Obstacle.ObstacleType type = Obstacle.ObstacleType.values()[ThreadLocalRandom.current().nextInt(Obstacle.ObstacleType.values().length)];
        double xOffset = random().nextGaussian() * 15;
        double yOffset = random().nextGaussian() * 15;
        return new Obstacle(Config.nextEntityId(), x + xOffset, y + yOffset, type, createObstacleBody(type));
    }

    private static Random random() {
        return ThreadLocalRandom.current();
    }
}

package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Shape;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents obstacles in the game world that support various geometric shapes.
 */
public class Obstacle extends GameEntity {

    public enum ObstacleType {
        BOULDER,           // Circular rocks
        HOUSE,            // Rectangular buildings
        WALL_SEGMENT,     // Linear barriers
        TRIANGLE_ROCK,    // Triangular stone formations
        POLYGON_DEBRIS,   // Irregular polygon shapes
        HEXAGON_CRYSTAL,  // Regular hexagonal formations
        DIAMOND_STONE,    // Diamond/rhombus shaped rocks
        L_SHAPED_WALL,    // L-shaped structural obstacles
        CROSS_BARRIER,    // Cross/plus shaped obstacles
        PLAYER_BARRIER    // Temporary player-deployed barriers
    }

    public enum ShapeCategory {
        CIRCULAR,    // Circle-based shapes
        RECTANGULAR, // Rectangle-based shapes  
        TRIANGULAR,  // Triangle-based shapes
        POLYGONAL,   // Multi-sided polygon shapes
        COMPOUND     // Multiple connected shapes
    }

    @Getter
    private final ObstacleType type;
    @Getter
    private final ShapeCategory shapeCategory;
    @Getter
    private final Shape primaryShape;
    @Getter
    private final double boundingRadius;
    /**
     * -- GETTER --
     * Get shape-specific data for client rendering.
     * Returns a map containing shape information for the client.
     */
    @Getter
    private final Map<String, Object> shapeData;

    // Player barrier specific fields
    @Getter
    private final int ownerId; // Player who created this barrier (0 for map obstacles)
    @Getter
    private final int ownerTeam; // Team of the player who created this barrier (0 for map obstacles)
    @Getter
    private final double lifespan; // Total lifespan for temporary barriers (0 for permanent obstacles)

    private double timeRemaining; // Time remaining for temporary barriers

    public Obstacle(int id, double x, double y, ObstacleType type) {
        this(id, x, y, type, 0, 0, 0.0, Double.POSITIVE_INFINITY);
    }

    public Obstacle(int id, double x, double y, ObstacleType type, int ownerId, int ownerTeam, double lifespan, double health) {
        super(id, createObstacleBody(x, y, type), health);
        this.type = type;
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.lifespan = lifespan;
        this.timeRemaining = lifespan;
        this.primaryShape = getBody().getFixture(0).getShape();
        this.shapeCategory = determineShapeCategory(type);
        this.boundingRadius = calculateBoundingRadius();
        this.shapeData = generateShapeData();
        getBody().setMass(MassType.INFINITE);
        getBody().setUserData(this);
    }

    private ShapeCategory determineShapeCategory(ObstacleType type) {
        return switch (type) {
            case BOULDER -> ShapeCategory.CIRCULAR;
            case HOUSE, WALL_SEGMENT, PLAYER_BARRIER -> ShapeCategory.RECTANGULAR;
            case TRIANGLE_ROCK -> ShapeCategory.TRIANGULAR;
            case POLYGON_DEBRIS, HEXAGON_CRYSTAL, DIAMOND_STONE -> ShapeCategory.POLYGONAL;
            case L_SHAPED_WALL, CROSS_BARRIER -> ShapeCategory.COMPOUND;
            default -> ShapeCategory.CIRCULAR;
        };
    }

    /**
     * Create a physics body for the obstacle based on its type.
     */
    private static Body createObstacleBody(double x, double y, ObstacleType type) {
        Body body = new Body();
        Convex shape = createShapeForType(type);
        body.addFixture(shape);

        // Set restitution for obstacles - moderate bounce to make projectiles bounce off nicely
        body.getFixture(0).setRestitution(0.6); // Retains 60% of velocity on bounce

        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(x, y);

        // Add completely chaotic rotation - any angle possible
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double rotation = random.nextDouble(0, Math.PI * 2);

        // 30% chance for "tilted" angles that look more chaotic
        if (random.nextDouble() < 0.3) {
            rotation += random.nextGaussian() * 0.5; // Add some gaussian noise
        }

        body.getTransform().setRotation(rotation);

        return body;
    }

    /**
     * Create the appropriate shape based on obstacle type.
     */
    private static Convex createShapeForType(ObstacleType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (type) {
            case BOULDER -> createCircularShape(random);
            case HOUSE -> createRectangularShape(random);
            case WALL_SEGMENT -> createWallShape(random);
            case TRIANGLE_ROCK -> createTriangularShape(random);
            case POLYGON_DEBRIS -> createIrregularPolygon(random);
            case HEXAGON_CRYSTAL -> createRegularPolygon(ThreadLocalRandom.current().nextInt(4, 9), random);
            case DIAMOND_STONE -> createDiamondShape(random);
            case L_SHAPED_WALL -> createLShape(random);
            case CROSS_BARRIER -> createCrossShape(random);
            case PLAYER_BARRIER -> createPlayerBarrierShape();
            default -> createCircularShape(random);
        };
    }

    /**
     * Ensure vertices are in counter-clockwise winding order as required by Dyn4j.
     * Also validates that the polygon is valid (no duplicate points, minimum size, convex, etc.)
     */
    private static Vector2[] ensureCounterClockwiseWinding(Vector2[] vertices) {
        if (vertices.length < 3) return vertices;

        // Remove any duplicate consecutive vertices
        Vector2[] cleanVertices = removeDuplicateVertices(vertices);
        if (cleanVertices.length < 3) {
            // Fallback to a simple triangle if we don't have enough vertices
            return new Vector2[]{
                    new Vector2(0, 10),
                    new Vector2(-8.66, -5),
                    new Vector2(8.66, -5)
            };
        }

        // Calculate the signed area to determine winding order
        double signedArea = 0.0;
        int n = cleanVertices.length;

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            signedArea += (cleanVertices[j].x - cleanVertices[i].x) * (cleanVertices[j].y + cleanVertices[i].y);
        }

        // If signed area is positive, vertices are clockwise - reverse them
        Vector2[] finalVertices;
        if (signedArea > 0) {
            finalVertices = new Vector2[n];
            for (int i = 0; i < n; i++) {
                finalVertices[i] = cleanVertices[n - 1 - i];
            }
        } else {
            finalVertices = cleanVertices;
        }

        // Validate convexity - if not convex, return a simple convex shape
        if (!isConvex(finalVertices)) {
            // Fallback to a convex regular polygon with same number of vertices
            return createConvexFallback(finalVertices.length, calculateAverageRadius(finalVertices));
        }

        return finalVertices;
    }

    /**
     * Check if a polygon is convex by verifying all cross products have the same sign.
     */
    private static boolean isConvex(Vector2[] vertices) {
        if (vertices.length < 3) return true;

        int n = vertices.length;
        boolean hasPositive = false;
        boolean hasNegative = false;

        for (int i = 0; i < n; i++) {
            Vector2 p1 = vertices[i];
            Vector2 p2 = vertices[(i + 1) % n];
            Vector2 p3 = vertices[(i + 2) % n];

            // Calculate cross product of vectors (p2-p1) and (p3-p2)
            double crossProduct = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x);

            if (crossProduct > 1e-10) hasPositive = true;
            if (crossProduct < -1e-10) hasNegative = true;

            // If we have both positive and negative cross products, it's concave
            if (hasPositive && hasNegative) return false;
        }

        return true;
    }

    /**
     * Create a convex fallback polygon when the original is concave.
     */
    private static Vector2[] createConvexFallback(int sides, double radius) {
        Vector2[] vertices = new Vector2[Math.max(3, Math.min(sides, 8))]; // Limit to 3-8 sides
        int n = vertices.length;

        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i) / n;
            vertices[i] = new Vector2(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius
            );
        }

        return vertices;
    }

    /**
     * Calculate the average radius of vertices from the center.
     */
    private static double calculateAverageRadius(Vector2[] vertices) {
        if (vertices.length == 0) return 20.0;

        double totalRadius = 0.0;
        for (Vector2 vertex : vertices) {
            totalRadius += Math.sqrt(vertex.x * vertex.x + vertex.y * vertex.y);
        }

        return totalRadius / vertices.length;
    }

    /**
     * Remove duplicate consecutive vertices that could cause polygon validation issues.
     */
    private static Vector2[] removeDuplicateVertices(Vector2[] vertices) {
        if (vertices.length < 3) return vertices;

        java.util.List<Vector2> cleanList = new java.util.ArrayList<>();
        double tolerance = 1e-6; // Very small tolerance for floating point comparison

        for (int i = 0; i < vertices.length; i++) {
            Vector2 current = vertices[i];
            Vector2 next = vertices[(i + 1) % vertices.length];

            // Only add vertex if it's not too close to the next one
            double dx = current.x - next.x;
            double dy = current.y - next.y;
            if (dx * dx + dy * dy > tolerance * tolerance) {
                cleanList.add(current);
            }
        }

        return cleanList.toArray(new Vector2[0]);
    }

    private static Convex createCircularShape(ThreadLocalRandom random) {
        double radius = random.nextDouble(120, 200);
        return new Circle(radius);
    }

    private static Convex createRectangularShape(ThreadLocalRandom random) {
        double width = random.nextDouble(80, 340);
        double height = random.nextDouble(60, 300);
        return new Rectangle(width, height);
    }

    private static Convex createWallShape(ThreadLocalRandom random) {
        // More dramatic wall variations
        double length, thickness;

        if (random.nextDouble() < 0.2) {
            // 20% chance for massive walls
            length = random.nextDouble(150, 250);
            thickness = random.nextDouble(20, 35);
        } else if (random.nextDouble() < 0.3) {
            // 30% chance for thick defensive walls
            length = random.nextDouble(80, 140);
            thickness = random.nextDouble(25, 45);
        } else {
            // Normal walls with more variation
            length = random.nextDouble(40, 160);
            thickness = random.nextDouble(6, 25);
        }

        return new Rectangle(length, thickness);
    }

    private static Convex createTriangularShape(ThreadLocalRandom random) {
        double baseSize = random.nextDouble(15, 70);

        // 20% chance for massive jagged rocks
        if (random.nextDouble() < 0.2) {
            baseSize = random.nextDouble(60, 120);
        }

        // Create a more chaotic triangle with irregular proportions
        Vector2[] vertices = new Vector2[3];

        // Randomize the triangle shape significantly
        double heightVariation = 0.4 + random.nextDouble() * 0.8; // 0.4 to 1.2
        double widthVariation = 0.6 + random.nextDouble() * 0.8;   // 0.6 to 1.4
        double asymmetry = random.nextGaussian() * 0.3;            // Asymmetric offset

        vertices[0] = new Vector2(asymmetry * baseSize, baseSize * 0.577 * heightVariation); // Top vertex (offset)
        vertices[1] = new Vector2(-baseSize * 0.5 * widthVariation, -baseSize * 0.289); // Bottom left
        vertices[2] = new Vector2(baseSize * 0.5 * widthVariation, -baseSize * 0.289);  // Bottom right

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createIrregularPolygon(ThreadLocalRandom random) {
        int sides = random.nextInt(4, 12); // 4-11 sided for more variety
        double baseRadius = random.nextDouble(15, 100);

        // 15% chance for massive irregular formations
        if (random.nextDouble() < 0.15) {
            baseRadius = random.nextDouble(70, 130);
            sides = random.nextInt(6, 15); // More complex shapes for large obstacles
        }

        Vector2[] vertices = new Vector2[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2 * Math.PI * i) / sides;

            // Add chaotic angle variation
            angle += random.nextGaussian() * 0.2;

            // Much more dramatic radius variation for chaotic shapes
            double radiusVariation = 0.3 + random.nextDouble() * 1.4; // 0.3 to 1.7
            double radius = baseRadius * radiusVariation;

            // Add some noise to make it even more irregular
            radius += random.nextGaussian() * (baseRadius * 0.1);

            vertices[i] = new Vector2(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius
            );
        }

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createRegularPolygon(int sides, ThreadLocalRandom random) {
        double radius = random.nextDouble(18, 120);

        // 12% chance for massive crystal formations
        if (random.nextDouble() < 0.12) {
            radius = random.nextDouble(80, 150);
        }

        Vector2[] vertices = new Vector2[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2 * Math.PI * i) / sides;

            // Add slight irregularity even to "regular" polygons
            double radiusVariation = 1.0 + random.nextGaussian() * 0.05; // Very slight variation
            double actualRadius = radius * radiusVariation;

            vertices[i] = new Vector2(
                    Math.cos(angle) * actualRadius,
                    Math.sin(angle) * actualRadius
            );
        }

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createDiamondShape(ThreadLocalRandom random) {
        double width = random.nextDouble(20, 120);
        double height = random.nextDouble(20, 120);

        // 18% chance for massive diamond formations
        if (random.nextDouble() < 0.18) {
            width = random.nextDouble(80, 140);
            height = random.nextDouble(70, 120);
        }

        // Add asymmetry to make diamonds more chaotic
        double widthAsymmetry = 1.0 + random.nextGaussian() * 0.3;
        double heightAsymmetry = 1.0 + random.nextGaussian() * 0.3;
        double offsetX = random.nextGaussian() * (width * 0.1);
        double offsetY = random.nextGaussian() * (height * 0.1);

        Vector2[] vertices = new Vector2[4];
        vertices[0] = new Vector2(offsetX, height * 0.5 * heightAsymmetry); // Top
        vertices[1] = new Vector2(width * 0.5 * widthAsymmetry + offsetX, offsetY); // Right
        vertices[2] = new Vector2(offsetX, -height * 0.5 * heightAsymmetry); // Bottom
        vertices[3] = new Vector2(-width * 0.5 * widthAsymmetry + offsetX, offsetY); // Left

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createLShape(ThreadLocalRandom random) {
        // Since L-shapes are inherently concave, create a convex approximation
        // Use an irregular pentagon that suggests an L-shape but remains convex
        double size = random.nextDouble(35, 80);

        // 10% chance for massive L-shaped structures
        if (random.nextDouble() < 0.1) {
            size = random.nextDouble(100, 180);
        }

        // Create a convex pentagon that approximates an L-shape
        double variation = 0.8 + random.nextDouble() * 0.4; // 0.8 to 1.2

        Vector2[] vertices = new Vector2[5];
        vertices[0] = new Vector2(-size * 0.5, size * 0.5 * variation);        // Top-left
        vertices[1] = new Vector2(size * 0.3 * variation, size * 0.4);         // Top-right
        vertices[2] = new Vector2(size * 0.5 * variation, -size * 0.2);        // Right
        vertices[3] = new Vector2(size * 0.1, -size * 0.5 * variation);        // Bottom-right
        vertices[4] = new Vector2(-size * 0.5, -size * 0.3);                   // Bottom-left

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createCrossShape(ThreadLocalRandom random) {
        // Since cross shapes are inherently concave, create a convex approximation
        // Use a regular octagon with slight variations to suggest a cross
        double size = random.nextDouble(30, 70);

        // 8% chance for massive cross barriers
        if (random.nextDouble() < 0.08) {
            size = random.nextDouble(90, 160);
        }

        // Create a convex octagon that approximates a cross shape
        Vector2[] vertices = new Vector2[8];
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8;

            // Vary the radius to create a cross-like appearance while staying convex
            double radiusMultiplier;
            if (i % 2 == 0) {
                // "Arms" of the cross - extend further
                radiusMultiplier = 0.9 + random.nextDouble() * 0.2; // 0.9 to 1.1
            } else {
                // "Corners" between arms - pull in slightly
                radiusMultiplier = 0.6 + random.nextDouble() * 0.2; // 0.6 to 0.8
            }

            double radius = size * 0.5 * radiusMultiplier;
            vertices[i] = new Vector2(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius
            );
        }

        return new Polygon(ensureCounterClockwiseWinding(vertices));
    }

    private static Convex createPlayerBarrierShape() {
        // Player barriers are always rectangular, 80 units wide by 10 units thick
        return new Rectangle(80.0, 10.0);
    }

    /**
     * Calculate the bounding radius for this obstacle.
     */
    private double calculateBoundingRadius() {
        return getBody().getFixture(0).getShape().getRadius();
    }

    /**
     * Generate shape data for client rendering.
     */
    private Map<String, Object> generateShapeData() {
        Map<String, Object> data = new HashMap<>();
        Shape shape = getBody().getFixture(0).getShape();

        data.put("shapeType", getShapeCategory().name());
        data.put("obstacleType", getType().name());

        if (shape instanceof Circle circle) {
            data.put("radius", circle.getRadius());
        } else if (shape instanceof Rectangle rect) {
            data.put("width", rect.getWidth());
            data.put("height", rect.getHeight());
        } else if (shape instanceof Polygon poly) {
            List<Map<String, Double>> vertices = new ArrayList<>();
            for (Vector2 vertex : poly.getVertices()) {
                Map<String, Double> point = new HashMap<>();
                point.put("x", vertex.x);
                point.put("y", vertex.y);
                vertices.add(point);
            }
            data.put("vertices", vertices);
        }

        return data;
    }

    @Override
    public void update(double deltaTime) {
        // Handle temporary barriers
        if (type == ObstacleType.PLAYER_BARRIER && lifespan > 0) {
            if (!active) {
                return;
            }

            timeRemaining -= deltaTime;
            if (timeRemaining <= 0) {
                active = false;
            }

            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Factory method to create extra chaotic obstacles with maximum randomization.
     */
    public static Obstacle createChaoticObstacle(double x, double y) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Bias towards more interesting shapes for chaotic generation
        ObstacleType[] chaoticTypes = {
                ObstacleType.POLYGON_DEBRIS,
                ObstacleType.TRIANGLE_ROCK,
                ObstacleType.DIAMOND_STONE,
                ObstacleType.HEXAGON_CRYSTAL,
                ObstacleType.L_SHAPED_WALL,
                ObstacleType.CROSS_BARRIER,
                ObstacleType.BOULDER,
                ObstacleType.HOUSE,
                ObstacleType.WALL_SEGMENT
        };

        ObstacleType randomType = chaoticTypes[random.nextInt(chaoticTypes.length)];
        double xOffset = random.nextGaussian() * 15;
        double yOffset = random.nextGaussian() * 15;

        return new Obstacle(Config.nextId(), x + xOffset, y + yOffset, randomType);
    }

    /**
     * Factory method to create a player barrier.
     */
    public static Obstacle createPlayerBarrier(int id, int ownerId, int ownerTeam, Vector2 position, Vector2 direction, double lifespan) {
        Obstacle barrier = new Obstacle(id, position.x, position.y, ObstacleType.PLAYER_BARRIER, ownerId, ownerTeam, lifespan, 100.0);

        // Orient the barrier perpendicular to the direction (same as original Barrier class)
        if (direction.getMagnitude() > 0) {
            double angle = Math.atan2(direction.y, direction.x);
            barrier.getBody().getTransform().setRotation(angle + Math.PI / 2); // Perpendicular to aim direction
        }

        return barrier;
    }

    /**
     * Check if the obstacle has expired (for temporary barriers)
     */
    public boolean isExpired() {
        if (type != ObstacleType.PLAYER_BARRIER) {
            return false; // Permanent obstacles never expire
        }
        return !active || timeRemaining <= 0;
    }

    /**
     * Override damage handling - barriers can be destroyed, permanent obstacles cannot
     */
    @Override
    public boolean takeDamage(double damage) {
        if (type == ObstacleType.PLAYER_BARRIER) {
            if (!active) {
                return false;
            }
            health -= damage;
            if (health <= 0) {
                active = false;
            }
            return !active; // Return true if barrier was destroyed
        }
        return false;
    }
}
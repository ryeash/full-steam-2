package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Geometry;
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
@Getter
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
        CROSS_BARRIER     // Cross/plus shaped obstacles
    }

    public enum ShapeCategory {
        CIRCULAR,    // Circle-based shapes
        RECTANGULAR, // Rectangle-based shapes
        TRIANGULAR,  // Triangle-based shapes
        POLYGONAL,   // Multi-sided polygon shapes
        COMPOUND     // Multiple connected shapes
    }

    private final ObstacleType type = ObstacleType.values()[ThreadLocalRandom.current().nextInt(ObstacleType.values().length)];
    private final ShapeCategory shapeCategory;
    private final Shape primaryShape;
    private final double boundingRadius;
    private final Map<String, Object> shapeData;

    public Obstacle(int id, double x, double y, ObstacleType type) {
        super(id, createObstacleBody(x, y, type), Double.POSITIVE_INFINITY);
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
            case HOUSE, WALL_SEGMENT -> ShapeCategory.RECTANGULAR;
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
        List<Convex> shapes = createShapeForType(type);
        for (Convex shape : shapes) {
            BodyFixture bodyFixture = body.addFixture(shape);
            bodyFixture.setRestitution(0.6);
        }
        // Set restitution for obstacles - moderate bounce to make projectiles bounce off nicely
        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(x, y);
        // Add chaotic rotation - any angle possible
        double rotation = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        body.getTransform().setRotation(rotation);
        return body;
    }

    /**
     * Create the appropriate shape based on obstacle type.
     */
    private static List<Convex> createShapeForType(ObstacleType type) {
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
        double baseSize = random.nextDouble(45, 80);
        // 20% chance for massive jagged rocks
        if (random.nextDouble() < 0.2) {
            baseSize = random.nextDouble(80, 160);
        }
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
        double radius = random.nextDouble() < 0.12
                ? random.nextDouble(80, 150)
                : random.nextDouble(18, 120);
        return Geometry.createPolygonalCircle(sides, radius);
    }

    private static Convex createDiamondShape(ThreadLocalRandom random) {
        double width = random.nextDouble(20, 120);
        double height = random.nextDouble(20, 120);
        // 18% chance for massive diamond formations
        if (random.nextDouble() < 0.18) {
            width = random.nextDouble(80, 140);
            height = random.nextDouble(70, 120);
        }
        return Geometry.createPolygonalEllipse(4, width, height);
    }

    private static List<Convex> createLShape(ThreadLocalRandom random) {
        // Since L-shapes are inherently concave, create a convex approximation
        // Use an irregular pentagon that suggests an L-shape but remains convex
        double size = random.nextDouble(35, 80);

        // 10% chance for massive L-shaped structures
        if (random.nextDouble() < 0.1) {
            size = random.nextDouble(100, 180);
        }
        Rectangle lower = Geometry.createRectangle(size, size / 4);
        lower.translate(size / 2, 0);
        Rectangle upper = Geometry.createRectangle(size / 4, size);
        upper.translate(0, size / 2);
        return List.of(upper, lower);
    }

    private static List<Convex> createCrossShape(ThreadLocalRandom random) {
        double size = random.nextDouble(30, 70);
        if (random.nextDouble() < 0.08) {
            size = random.nextDouble(90, 160);
        }
        return List.of(Geometry.createRectangle(size, size / 4), Geometry.createRectangle(size / 4, size));
    }

    /**
     * Calculate the bounding radius for this obstacle.
     */
    private double calculateBoundingRadius() {
        return getBody().getRotationDiscRadius();
    }

    /**
     * Generate shape data for client rendering.
     */
    private Map<String, Object> generateShapeData() {
        Map<String, Object> data = new HashMap<>();
        Shape shape = getBody().getFixture(0).getShape();

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

    /**
     * Factory method to create extra chaotic obstacles with maximum randomization.
     */
    public static Obstacle createChaoticObstacle(double x, double y) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double xOffset = random.nextGaussian() * 15;
        double yOffset = random.nextGaussian() * 15;
        return new Obstacle(Config.nextEntityId(), x + xOffset, y + yOffset, ObstacleType.values()[random.nextInt(ObstacleType.values().length)]);
    }

    /**
     * Permanent map obstacles cannot be damaged.
     */
    @Override
    public boolean takeDamage(double damage) {
        return false;
    }
}
package com.fullsteam.physics;

import com.fullsteam.Config;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Concrete implementation of Obstacle that supports various geometric shapes.
 */
public class ShapedObstacle extends Obstacle {
    
    private final double boundingRadius;
    private final Map<String, Object> shapeData;

    public ShapedObstacle(int id, double x, double y, ObstacleType type) {
        super(id, createObstacleBody(x, y, type), type, null);
        this.boundingRadius = calculateBoundingRadius();
        this.shapeData = generateShapeData();
    }

    private ShapedObstacle(int id, Body body, ObstacleType type, double boundingRadius, Map<String, Object> shapeData) {
        super(id, body, type, body.getFixture(0).getShape());
        this.boundingRadius = boundingRadius;
        this.shapeData = shapeData;
    }

    /**
     * Create a physics body for the obstacle based on its type.
     */
    private static Body createObstacleBody(double x, double y, ObstacleType type) {
        Body body = new Body();
        Convex shape = createShapeForType(type);
        body.addFixture(shape);
        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(x, y);
        
        // Add random rotation for more variety
        double rotation = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
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
            case HEXAGON_CRYSTAL -> createRegularPolygon(6, random);
            case DIAMOND_STONE -> createDiamondShape(random);
            case L_SHAPED_WALL -> createLShape(random);
            case CROSS_BARRIER -> createCrossShape(random);
            default -> createCircularShape(random);
        };
    }

    private static Convex createCircularShape(ThreadLocalRandom random) {
        double radius = random.nextDouble(15, 35);
        return new Circle(radius);
    }

    private static Convex createRectangularShape(ThreadLocalRandom random) {
        double width = random.nextDouble(30, 60);
        double height = random.nextDouble(20, 40);
        return new Rectangle(width, height);
    }

    private static Convex createWallShape(ThreadLocalRandom random) {
        double length = random.nextDouble(60, 120);
        double thickness = random.nextDouble(8, 15);
        return new Rectangle(length, thickness);
    }

    private static Convex createTriangularShape(ThreadLocalRandom random) {
        double size = random.nextDouble(20, 40);
        
        // Create an equilateral triangle
        Vector2[] vertices = new Vector2[3];
        vertices[0] = new Vector2(0, size * 0.577); // Top vertex
        vertices[1] = new Vector2(-size * 0.5, -size * 0.289); // Bottom left
        vertices[2] = new Vector2(size * 0.5, -size * 0.289); // Bottom right
        
        return new Polygon(vertices);
    }

    private static Convex createIrregularPolygon(ThreadLocalRandom random) {
        int sides = random.nextInt(5, 9); // 5-8 sided irregular polygon
        double baseRadius = random.nextDouble(20, 35);
        
        Vector2[] vertices = new Vector2[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2 * Math.PI * i) / sides;
            // Add randomness to radius for irregular shape
            double radius = baseRadius * (0.7 + random.nextDouble() * 0.6);
            vertices[i] = new Vector2(
                Math.cos(angle) * radius,
                Math.sin(angle) * radius
            );
        }
        
        return new Polygon(vertices);
    }

    private static Convex createRegularPolygon(int sides, ThreadLocalRandom random) {
        double radius = random.nextDouble(20, 35);
        
        Vector2[] vertices = new Vector2[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2 * Math.PI * i) / sides;
            vertices[i] = new Vector2(
                Math.cos(angle) * radius,
                Math.sin(angle) * radius
            );
        }
        
        return new Polygon(vertices);
    }

    private static Convex createDiamondShape(ThreadLocalRandom random) {
        double width = random.nextDouble(25, 45);
        double height = random.nextDouble(25, 45);
        
        Vector2[] vertices = new Vector2[4];
        vertices[0] = new Vector2(0, height * 0.5); // Top
        vertices[1] = new Vector2(width * 0.5, 0); // Right
        vertices[2] = new Vector2(0, -height * 0.5); // Bottom
        vertices[3] = new Vector2(-width * 0.5, 0); // Left
        
        return new Polygon(vertices);
    }

    private static Convex createLShape(ThreadLocalRandom random) {
        // For compound shapes, we'll use a simple rectangle for now
        // In a full implementation, this would create multiple fixtures
        double size = random.nextDouble(30, 50);
        return new Rectangle(size, size * 0.6);
    }

    private static Convex createCrossShape(ThreadLocalRandom random) {
        // For compound shapes, we'll use a simple rectangle for now
        // In a full implementation, this would create multiple fixtures
        double size = random.nextDouble(25, 40);
        return new Rectangle(size, size);
    }

    /**
     * Calculate the bounding radius for this obstacle.
     */
    private double calculateBoundingRadius() {
        Shape shape = getBody().getFixture(0).getShape();
        
        if (shape instanceof Circle) {
            return ((Circle) shape).getRadius();
        } else if (shape instanceof Rectangle rect) {
            return Math.sqrt(rect.getWidth() * rect.getWidth() + rect.getHeight() * rect.getHeight()) / 2;
        } else if (shape instanceof Polygon poly) {
            double maxDistance = 0;
            for (Vector2 vertex : poly.getVertices()) {
                double distance = vertex.getMagnitude();
                maxDistance = Math.max(maxDistance, distance);
            }
            return maxDistance;
        }
        
        return 25.0; // Default fallback
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
    public double getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public Map<String, Object> getShapeData() {
        return new HashMap<>(shapeData);
    }

    /**
     * Factory method to create random obstacles.
     */
    public static ShapedObstacle createRandomObstacle(double x, double y) {
        ObstacleType[] types = ObstacleType.values();
        ObstacleType randomType = types[ThreadLocalRandom.current().nextInt(types.length)];
        return new ShapedObstacle(Config.nextId(), x, y, randomType);
    }

    /**
     * Factory method to create obstacles of a specific type.
     */
    public static ShapedObstacle createObstacle(double x, double y, ObstacleType type) {
        return new ShapedObstacle(Config.nextId(), x, y, type);
    }
}

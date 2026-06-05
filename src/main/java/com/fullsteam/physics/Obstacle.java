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

    private final ObstacleType type = ObstacleType.values()[ThreadLocalRandom.current().nextInt(ObstacleType.values().length)];
    private final double boundingRadius;
    private final Map<String, Object> shapeData;

    public Obstacle(int id, double x, double y, ObstacleType type) {
        super(id, createObstacleBody(x, y, type), Double.POSITIVE_INFINITY);
        this.boundingRadius = getBody().getRotationDiscRadius();
        this.shapeData = generateShapeData();
        getBody().setMass(MassType.INFINITE);
        getBody().setUserData(this);
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
        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(x, y);
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
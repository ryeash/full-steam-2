package com.fullsteam.physics;

import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Shape;
import lombok.Getter;

public abstract class Obstacle extends GameEntity {

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

    @Getter
    private final ObstacleType type;
    @Getter
    private final ShapeCategory shapeCategory;
    @Getter
    private final Shape primaryShape;

    public Obstacle(int id, Body body, ObstacleType type, Shape primaryShape) {
        super(id, body, Double.POSITIVE_INFINITY); // Obstacles are indestructible by default
        this.type = type;
        this.primaryShape = primaryShape;
        this.shapeCategory = determineShapeCategory(type);
        body.setMass(MassType.INFINITE); // Obstacles are static
        body.setUserData(this);
    }

    private ShapeCategory determineShapeCategory(ObstacleType type) {
        switch (type) {
            case BOULDER:
                return ShapeCategory.CIRCULAR;
            case HOUSE:
            case WALL_SEGMENT:
                return ShapeCategory.RECTANGULAR;
            case TRIANGLE_ROCK:
                return ShapeCategory.TRIANGULAR;
            case POLYGON_DEBRIS:
            case HEXAGON_CRYSTAL:
            case DIAMOND_STONE:
                return ShapeCategory.POLYGONAL;
            case L_SHAPED_WALL:
            case CROSS_BARRIER:
                return ShapeCategory.COMPOUND;
            default:
                return ShapeCategory.CIRCULAR;
        }
    }

    /**
     * Get the approximate radius for collision detection and positioning.
     * This provides a bounding circle radius for the obstacle.
     */
    public abstract double getBoundingRadius();

    /**
     * Get shape-specific data for client rendering.
     * Returns a map containing shape information for the client.
     */
    public abstract java.util.Map<String, Object> getShapeData();

    @Override
    public void update(double deltaTime) {
        // Obstacles are static and don't need updates by default
    }
}

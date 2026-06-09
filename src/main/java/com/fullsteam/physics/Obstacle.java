package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.MassType;

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

    private final ObstacleType type;
    private final double boundingRadius;

    public Obstacle(int id, double x, double y, ObstacleType type, Body body) {
        super(id, body, Double.POSITIVE_INFINITY);
        this.type = type;
        this.boundingRadius = getBody().getRotationDiscRadius();
        getBody().setMass(MassType.INFINITE);
        getBody().setUserData(this);
        getBody().translate(x, y);
    }

    /**
     * Permanent map obstacles cannot be damaged.
     */
    @Override
    public boolean takeDamage(double damage) {
        return false;
    }
}
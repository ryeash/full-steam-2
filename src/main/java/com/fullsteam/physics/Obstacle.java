package com.fullsteam.physics;

import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.MassType;

public abstract class Obstacle extends GameEntity {

    public enum ObstacleType {
        BOULDER,
        HOUSE,
        WALL_SEGMENT
    }

    private final ObstacleType type;

    public Obstacle(int id, Body body, ObstacleType type) {
        super(id, body, Double.POSITIVE_INFINITY); // Obstacles are indestructible by default
        this.type = type;
        body.setMass(MassType.INFINITE); // Obstacles are static
        body.setUserData(this);
    }

    public ObstacleType getType() {
        return type;
    }

    @Override
    public void update(double deltaTime) {
        // Obstacles are static and don't need updates by default
    }
}

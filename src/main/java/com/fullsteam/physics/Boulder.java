package com.fullsteam.physics;

import com.fullsteam.Config;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;

import java.util.Map;

public class Boulder extends Obstacle {

    public Boulder(double x, double y, double radius) {
        super(Config.nextId(), createBoulderBody(x, y, radius), ObstacleType.BOULDER, new Circle(radius));
    }

    private static Body createBoulderBody(double x, double y, double radius) {
        Body body = new Body();
        body.addFixture(new Circle(radius));
        body.getTransform().setTranslation(x, y);
        return body;
    }

    @Override
    public double getBoundingRadius() {
        return getPrimaryShape().getRadius();
    }

    @Override
    public java.util.Map<String, Object> getShapeData() {
        return Map.of("radius", getPrimaryShape().getRadius());
    }
}

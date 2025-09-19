package com.fullsteam.physics;

import com.fullsteam.Config;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;

public class Boulder extends Obstacle {

    public Boulder(double x, double y, double radius) {
        super(Config.nextId(), createBoulderBody(x, y, radius), ObstacleType.BOULDER);
    }

    private static Body createBoulderBody(double x, double y, double radius) {
        Body body = new Body();
        body.addFixture(new Circle(radius));
        body.getTransform().setTranslation(x, y);
        // MassType is set to INFINITE in the Obstacle constructor
        return body;
    }
}

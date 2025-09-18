package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

@Getter
public class StrategicLocation extends GameEntity {
    private int controllingPlayerId = 0;
    private double captureProgress = 0.0; // 0.0 to 1.0
    private int capturingPlayerId = 0;
    // Getters
    private final String locationName;

    public StrategicLocation(String locationName, double x, double y) {
        super(Config.nextId(), createLocationBody(x, y), 100.0);
        this.locationName = locationName;
    }

    private static Body createLocationBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(Config.CAPTURE_RADIUS);

        // Create fixture and make it a sensor (no physical collision response)
        var fixture = body.addFixture(circle);
        fixture.setSensor(true); // This allows collision detection but no physical response

        body.setMass(MassType.INFINITE); // Make it static
        body.getTransform().setTranslation(x, y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        // Capture progress naturally decays if no one is capturing
        if (capturingPlayerId == 0 && captureProgress > 0) {
            captureProgress = Math.max(0, captureProgress - deltaTime / Config.CAPTURE_TIME);
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    public void startCapture(int playerId) {
        // If different player starts capturing, reset progress
        if (capturingPlayerId != 0 && capturingPlayerId != playerId) {
            captureProgress = 0.0;
        }
        capturingPlayerId = playerId;
    }

    public void updateCapture(double deltaTime) {
        if (capturingPlayerId != 0) {
            captureProgress += deltaTime / Config.CAPTURE_TIME;

            if (captureProgress >= 1.0) {
                // Location captured!
                controllingPlayerId = capturingPlayerId;
                captureProgress = 1.0;
                capturingPlayerId = 0;
            }
        }
    }

    public void stopCapture() {
        capturingPlayerId = 0;
    }

    public boolean isPlayerInRange(Vector2 playerPosition) {
        return getPosition().distance(playerPosition) <= Config.CAPTURE_RADIUS;
    }

    public boolean isControlledBy(int playerId) {
        return controllingPlayerId != 0 && controllingPlayerId == playerId;
    }

    public boolean isBeingCapturedBy(int playerId) {
        return capturingPlayerId != 0 && capturingPlayerId == playerId;
    }

    public boolean isNeutral() {
        return controllingPlayerId == 0;
    }
}

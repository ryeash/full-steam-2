package com.fullsteam.physics;

import lombok.Data;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;

@Data
public abstract class GameEntity {
    protected final int id;
    protected final Body body;
    protected double health;
    protected boolean active = true;
    protected long lastUpdateTime;
    protected final long created = System.currentTimeMillis();

    public GameEntity(int id, Body body, double health) {
        this.id = id;
        this.body = body;
        this.health = health;
        this.lastUpdateTime = System.currentTimeMillis();
        body.setAtRest(false);
        body.setAtRestDetectionEnabled(false);
        body.setEnabled(true);
        body.setUserData(this);
    }

    public abstract void update(double deltaTime);

    public Vector2 getPosition() {
        return body.getTransform().getTranslation().copy();
    }

    public void setPosition(double x, double y) {
        body.getTransform().setTranslation(x, y);
    }

    public Vector2 getVelocity() {
        return body.getLinearVelocity().copy();
    }

    public void setVelocity(double x, double y) {
        body.setLinearVelocity(x, y);
    }

    public void setVelocity(Vector2 velocity) {
        body.setLinearVelocity(velocity);
    }

    public double getRotation() {
        return body.getTransform().getRotationAngle();
    }

    public void setRotation(double angle) {
        body.getTransform().setRotation(angle);
    }

    public boolean takeDamage(double damage) {
        boolean wasActive = active;
        health -= damage;
        if (health <= 0) {
            active = false;
        }
        return wasActive && !active; // Return true if entity became inactive
    }

    public void heal(double amount) {
        health = Math.min(100.0, health + amount);
    }
}



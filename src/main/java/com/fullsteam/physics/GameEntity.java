package com.fullsteam.physics;

import lombok.Data;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;

import java.util.Objects;

@Data
public abstract class GameEntity {
    protected final int id;
    protected final Body body;
    protected double health;
    protected double maxHealth;
    protected boolean active = true;
    protected long lastUpdateTime;
    protected final long created = System.currentTimeMillis();
    protected long expires = -1L;

    public GameEntity(int id, Body body, double health) {
        this.id = id;
        this.body = body;
        this.health = health;
        this.maxHealth = health;
        this.lastUpdateTime = System.currentTimeMillis();
        body.setAtRest(false);
        body.setAtRestDetectionEnabled(false);
        body.setEnabled(true);
        body.setUserData(this);
    }

    public double healthPercent() {
        return Math.max(0, (health / maxHealth));
    }

    public void update(double deltaTime) {
        lastUpdateTime = System.currentTimeMillis();
    }

    public Vector2 getPosition() {
        return body.getTransform().getTranslation().copy();
    }

    public void setPosition(double x, double y) {
        body.getTransform().setTranslation(x, y);
    }

    public Vector2 getVelocity() {
        return body.getLinearVelocity().copy();
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

    public double getRadius() {
        return body.getRotationDiscRadius();
    }

    public void heal(double amount) {
        if (!active || amount <= 0) {
            return;
        }
        health = Math.min(maxHealth, health + amount);
    }

    public boolean takeDamage(double damage) {
        if (damage < 0) {
            heal(-damage);
            return false;
        }
        boolean wasActive = active;
        health -= damage;
        if (health <= 0) {
            active = false;
        }
        return wasActive && !active; // Return true if entity became inactive
    }

    public boolean isExpired() {
        if (!active) {
            return true;
        } else if (expires > 0) {
            return System.currentTimeMillis() > expires;
        } else {
            return false;
        }
    }

    /**
     * Get the remaining duration as a percentage
     */
    public double getDurationPercent() {
        if (expires <= created) {
            return 0; // No duration set
        }
        long totalDuration = expires - created;
        long remainingTime = expires - System.currentTimeMillis();
        return Math.max(0, Math.min(1, (double) remainingTime / totalDuration));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GameEntity that = (GameEntity) o;
        return this.id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}



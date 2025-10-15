package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.Map;

/**
 * Headquarters entity - a destructible structure that teams must protect/attack.
 * Similar to Obstacle but with team ownership and scoring mechanics.
 * Each team has one headquarters in their spawn zone that can be shot to score points.
 */
@Getter
@Setter
public class Headquarters extends GameEntity {
    private static final double HQ_WIDTH = 80.0;
    private static final double HQ_HEIGHT = 60.0;
    
    private final int teamNumber;
    private final Vector2 homePosition;
    private final double maxHealth;
    private double totalDamageTaken = 0.0; // Track for scoring
    
    public Headquarters(int id, int teamNumber, double x, double y, double maxHealth) {
        super(id, createHeadquartersBody(x, y), maxHealth);
        this.teamNumber = teamNumber;
        this.homePosition = new Vector2(x, y);
        this.maxHealth = maxHealth;
    }
    
    private static Body createHeadquartersBody(double x, double y) {
        Body body = new Body();
        Rectangle rect = new Rectangle(HQ_WIDTH, HQ_HEIGHT);
        body.addFixture(rect);
        body.setMass(MassType.INFINITE); // Static structure
        body.getTransform().setTranslation(x, y);
        body.setUserData("headquarters");
        return body;
    }
    
    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Apply damage to headquarters and track total damage for scoring.
     */
    @Override
    public boolean takeDamage(double damage) {
        if (!active) {
            return false;
        }
        
        health -= damage;
        totalDamageTaken += damage;
        
        if (health <= 0) {
            health = 0;
            active = false;
            return true; // Headquarters destroyed!
        }
        
        return false;
    }
    
    /**
     * Get shape data for client rendering.
     */
    public Map<String, Object> getShapeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("shapeCategory", "RECTANGLE");
        data.put("width", HQ_WIDTH);
        data.put("height", HQ_HEIGHT);
        return data;
    }
}


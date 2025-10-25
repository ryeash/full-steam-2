package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Represents a capturable flag in capture-the-flag style gameplay.
 * Flags are sensor entities that can be picked up by opposing team players.
 */
@Getter
@Setter
public class Flag extends GameEntity {
    private static final double FLAG_RADIUS = 15.0; // Slightly larger than player for easy capture
    
    private final int ownerTeam; // Team that owns/protects this flag
    private final Vector2 homePosition; // Original spawn position
    
    private int carriedByPlayerId = -1; // -1 = not carried, otherwise player ID
    private FlagState state = FlagState.AT_HOME;
    private long lastCaptureTime = 0;
    private int captureCount = 0; // How many times this flag has been captured
    
    public Flag(int id, int ownerTeam, double x, double y) {
        super(id, createFlagBody(x, y), Double.POSITIVE_INFINITY); // Flags are indestructible
        this.ownerTeam = ownerTeam;
        this.homePosition = new Vector2(x, y);
    }
    
    private static Body createFlagBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(FLAG_RADIUS);
        var fixture = body.addFixture(circle);
        fixture.setSensor(true); // Flags don't collide physically, they're sensors
        body.setMass(MassType.INFINITE); // Flags don't move from physics
        body.getTransform().setTranslation(x, y);
        return body;
    }
    
    @Override
    public void update(double deltaTime) {
        // Flags don't need to update themselves - their state is managed by game logic
    }
    
    /**
     * Check if this flag can be captured by a player from the given team.
     */
    public boolean canBeCapturedBy(int team) {
        // Can't capture your own flag
        if (team == ownerTeam) {
            return false;
        }
        
        // Can only capture if flag is at home or dropped (not already being carried)
        return state == FlagState.AT_HOME || state == FlagState.DROPPED;
    }
    
    /**
     * Pick up the flag (start carrying it).
     */
    public void pickUp(int playerId) {
        this.carriedByPlayerId = playerId;
        this.state = FlagState.CARRIED;
    }
    
    /**
     * Drop the flag at current position.
     */
    public void drop() {
        this.carriedByPlayerId = -1;
        this.state = FlagState.DROPPED;
    }
    
    /**
     * Return flag to home position.
     */
    public void returnToHome() {
        this.carriedByPlayerId = -1;
        this.state = FlagState.AT_HOME;
        getBody().getTransform().setTranslation(homePosition.x, homePosition.y);
    }
    
    /**
     * Flag was successfully captured (reached enemy base).
     */
    public void capture() {
        this.captureCount++;
        this.lastCaptureTime = System.currentTimeMillis();
        returnToHome();
    }
    
    /**
     * Check if flag is being carried.
     */
    public boolean isCarried() {
        return state == FlagState.CARRIED;
    }
    
    /**
     * Check if flag is at home.
     */
    public boolean isAtHome() {
        return state == FlagState.AT_HOME;
    }
    
    /**
     * Check if flag is dropped.
     */
    public boolean isDropped() {
        return state == FlagState.DROPPED;
    }
    
    /**
     * Get distance from home position.
     */
    public double getDistanceFromHome() {
        return getPosition().distance(homePosition);
    }
    
    /**
     * Flag state enum
     */
    public enum FlagState {
        AT_HOME,    // Flag is at its home base
        CARRIED,    // Flag is being carried by a player
        DROPPED     // Flag was dropped and is waiting to be returned or captured
    }
}


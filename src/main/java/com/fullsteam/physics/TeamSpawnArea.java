package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a team's spawn area and future base location.
 * Provides spawn points within the team's designated area and will support
 * future base features like vehicle garages, supply depots, etc.
 */
@Getter
public class TeamSpawnArea {
    private final int teamNumber;
    private final Vector2 center;
    private final double width;
    private final double height;
    private final Vector2 minBounds;
    private final Vector2 maxBounds;
    
    // Future base features
    private boolean hasVehicleGarage = false;
    private boolean hasSupplyDepot = false;
    private boolean hasDefenseTurrets = false;
    
    public TeamSpawnArea(int teamNumber, Vector2 center, double width, double height) {
        this.teamNumber = teamNumber;
        this.center = center.copy();
        this.width = width;
        this.height = height;
        
        // Calculate bounds for spawn area (slightly smaller than full area for safety)
        double spawnWidth = width * 0.8; // 80% of area width
        double spawnHeight = height * 0.8; // 80% of area height
        
        this.minBounds = new Vector2(
            center.x - spawnWidth / 2.0,
            center.y - spawnHeight / 2.0
        );
        this.maxBounds = new Vector2(
            center.x + spawnWidth / 2.0,
            center.y + spawnHeight / 2.0
        );
    }
    
    /**
     * Generate a random spawn point within this team's area.
     * Ensures players spawn safely within their team's territory.
     * 
     * @return Random spawn point within team area
     */
    public Vector2 generateSpawnPoint() {
        double x = minBounds.x + ThreadLocalRandom.current().nextDouble() * (maxBounds.x - minBounds.x);
        double y = minBounds.y + ThreadLocalRandom.current().nextDouble() * (maxBounds.y - minBounds.y);
        return new Vector2(x, y);
    }
    
    /**
     * Find a safe spawn point within the team area, avoiding other players.
     * 
     * @param existingPlayers List of existing players to avoid
     * @param minDistance Minimum distance from other players
     * @return Safe spawn point, or random point if no safe location found
     */
    public Vector2 findSafeSpawnPoint(java.util.Collection<Player> existingPlayers, double minDistance) {
        for (int attempts = 0; attempts < 20; attempts++) {
            Vector2 candidate = generateSpawnPoint();
            
            boolean isSafe = true;
            for (Player player : existingPlayers) {
                if (player.isActive() && candidate.distance(player.getPosition()) < minDistance) {
                    isSafe = false;
                    break;
                }
            }
            
            if (isSafe) {
                return candidate;
            }
        }
        
        // If no safe spot found, return random point in area
        return generateSpawnPoint();
    }
    
    /**
     * Check if a position is within this team's spawn area.
     * 
     * @param position Position to check
     * @return true if position is within team area
     */
    public boolean containsPosition(Vector2 position) {
        return position.x >= minBounds.x && position.x <= maxBounds.x &&
               position.y >= minBounds.y && position.y <= maxBounds.y;
    }
    
    /**
     * Get the distance from a position to the center of this team area.
     * 
     * @param position Position to measure from
     * @return Distance to team area center
     */
    public double distanceToCenter(Vector2 position) {
        return position.distance(center);
    }
    
    /**
     * Get a defensive position near the edge of the team area, facing outward.
     * Useful for AI defensive positioning.
     * 
     * @return Defensive position vector
     */
    public Vector2 getDefensivePosition() {
        // Position 75% of the way from center to edge
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = Math.min(width, height) * 0.375; // 75% of half the smaller dimension
        
        return new Vector2(
            center.x + Math.cos(angle) * distance,
            center.y + Math.sin(angle) * distance
        );
    }
    
    // Future base feature methods (for extensibility)
    
    public void enableVehicleGarage() {
        this.hasVehicleGarage = true;
    }
    
    public void enableSupplyDepot() {
        this.hasSupplyDepot = true;
    }
    
    public void enableDefenseTurrets() {
        this.hasDefenseTurrets = true;
    }
    
    /**
     * Get the garage location for vehicle spawning (future feature).
     * 
     * @return Vehicle garage position
     */
    public Vector2 getVehicleGaragePosition() {
        // Position garage at back of base area
        return new Vector2(center.x, center.y - height * 0.3);
    }
    
    /**
     * Get the supply depot location for resource management (future feature).
     * 
     * @return Supply depot position
     */
    public Vector2 getSupplyDepotPosition() {
        // Position supply depot at side of base area
        return new Vector2(center.x + width * 0.3, center.y);
    }
    
    /**
     * Get positions for defensive turrets around the base (future feature).
     * 
     * @return Array of turret positions
     */
    public Vector2[] getDefenseTurretPositions() {
        return new Vector2[] {
            new Vector2(center.x - width * 0.4, center.y + height * 0.4),
            new Vector2(center.x + width * 0.4, center.y + height * 0.4),
            new Vector2(center.x, center.y + height * 0.4)
        };
    }
}

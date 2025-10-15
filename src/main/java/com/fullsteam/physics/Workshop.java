package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.Map;

/**
 * Workshop entity that allows players to craft power-ups by standing near it.
 * Players must remain within the craft radius for the required time to generate power-ups.
 * Extends Obstacle to inherit shape data serialization capabilities.
 */
@Getter
@Setter
public class Workshop extends Obstacle {
    private final double craftRadius;
    private final double craftTime;
    private final int maxPowerUps;
    
    // Crafting state - mimic KOTH zone approach
    private final Map<Integer, Integer> playersInRange = new HashMap<>(); // playerId -> teamNumber (like KOTH zones)
    private final Map<Integer, Double> playerCraftProgress = new HashMap<>(); // playerId -> progress (0.0 to 1.0)
    private final Map<Integer, Long> playerCraftStartTime = new HashMap<>(); // playerId -> start time
    private final Map<Integer, Double> playerCraftAccumulatedTime = new HashMap<>(); // playerId -> accumulated deltaTime for testing
    
    public Workshop(int id, Vector2 position, double craftRadius, double craftTime, int maxPowerUps) {
        // Create as a rectangular obstacle (HOUSE type) but make it a sensor
        super(id, position.x, position.y, ObstacleType.HOUSE);
        this.craftRadius = craftRadius;
        this.craftTime = craftTime;
        this.maxPowerUps = maxPowerUps;
        
        // Override the random rectangle with fixed workshop dimensions
        getBody().removeFixture(getBody().getFixture(0));
        org.dyn4j.geometry.Rectangle workshopRect = new org.dyn4j.geometry.Rectangle(50.0, 30.0);
        getBody().addFixture(workshopRect);
        
        // Make the fixture a sensor so players can walk through it
        getBody().getFixture(0).setSensor(true);
        getBody().setUserData("workshop");
    }
    
    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }
        updateCraftingProgress(deltaTime);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Update crafting progress for players near the workshop (mimic KOTH zone approach).
     */
    private void updateCraftingProgress(double deltaTime) {
        // Update progress for all players currently in range
        for (int playerId : playersInRange.keySet()) {
            double currentProgress = playerCraftProgress.getOrDefault(playerId, 0.0);
            
            if (currentProgress < 1.0) {
                // Accumulate deltaTime for more predictable testing
                double accumulatedTime = playerCraftAccumulatedTime.getOrDefault(playerId, 0.0) + deltaTime;
                playerCraftAccumulatedTime.put(playerId, accumulatedTime);
                
                double newProgress = Math.min(accumulatedTime / craftTime, 1.0);
                playerCraftProgress.put(playerId, newProgress);
            }
        }
    }
    
    /**
     * Add a player to the workshop range (mimic KOTH zone approach).
     */
    public void addPlayer(int playerId, int teamNumber) {
        playersInRange.put(playerId, teamNumber);
        // Initialize crafting progress if not already started
        if (!playerCraftProgress.containsKey(playerId)) {
            playerCraftProgress.put(playerId, 0.0);
            playerCraftStartTime.put(playerId, System.currentTimeMillis());
            playerCraftAccumulatedTime.put(playerId, 0.0);
        }
    }

    /**
     * Remove a player from the workshop range (mimic KOTH zone approach).
     */
    public void removePlayer(int playerId) {
        playersInRange.remove(playerId);
        // Remove progress data when player leaves
        playerCraftProgress.remove(playerId);
        playerCraftStartTime.remove(playerId);
        playerCraftAccumulatedTime.remove(playerId);
    }

    /**
     * Clear all players from the workshop (e.g., at round end).
     */
    public void clearPlayers() {
        playersInRange.clear();
        playerCraftProgress.clear();
        playerCraftStartTime.clear();
        playerCraftAccumulatedTime.clear();
    }

    /**
     * Check if a player is within crafting range.
     */
    public boolean isPlayerNearby(int playerId) {
        return playersInRange.containsKey(playerId);
    }
    
    /**
     * Start crafting for a player if they're in range.
     */
    public void startCrafting(int playerId) {
        if (!playerCraftProgress.containsKey(playerId)) {
            // Add to playersInRange with default team (needed for progress updates)
            playersInRange.put(playerId, 0);
            playerCraftProgress.put(playerId, 0.0);
            playerCraftStartTime.put(playerId, System.currentTimeMillis());
            playerCraftAccumulatedTime.put(playerId, 0.0);
        }
    }
    
    /**
     * Stop crafting for a player.
     */
    public void stopCrafting(int playerId) {
        playersInRange.remove(playerId);
        playerCraftProgress.remove(playerId);
        playerCraftStartTime.remove(playerId);
        playerCraftAccumulatedTime.remove(playerId);
    }
    
    /**
     * Check if a player has completed crafting.
     */
    public boolean isCraftingComplete(int playerId) {
        Double progress = playerCraftProgress.get(playerId);
        return progress != null && progress >= 1.0;
    }
    
    /**
     * Get crafting progress for a player (0.0 to 1.0).
     */
    public double getCraftingProgress(int playerId) {
        return playerCraftProgress.getOrDefault(playerId, 0.0);
    }
    
    /**
     * Reset crafting progress for a player (after they collect a power-up).
     */
    public void resetCraftingProgress(int playerId) {
        playerCraftProgress.put(playerId, 0.0);
        playerCraftStartTime.put(playerId, System.currentTimeMillis());
        playerCraftAccumulatedTime.put(playerId, 0.0);
    }
    
    /**
     * Get the number of active crafters (players who have started crafting).
     */
    public int getActiveCrafters() {
        // Count players who have started crafting (have progress entry)
        return playerCraftProgress.size();
    }
    
    /**
     * Get crafting progress for all players who have started crafting (not just those currently in range).
     * This ensures progress indicators persist even if players temporarily move out of range.
     */
    public Map<Integer, Double> getAllCraftingProgress() {
        // Return a copy of all crafting progress
        return new HashMap<>(playerCraftProgress);
    }
}

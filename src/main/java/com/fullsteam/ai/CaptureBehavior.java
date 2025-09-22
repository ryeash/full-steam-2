package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.StrategicLocation;
import org.dyn4j.geometry.Vector2;

/**
 * Objective-focused behavior for capturing and controlling strategic locations.
 * AI will move to strategic locations and attempt to capture them.
 */
public class CaptureBehavior implements AIBehavior {
    private int targetLocationId = -1;
    private double captureStartTime = 0;
    private boolean isCapturing = false;
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        // Find target location
        StrategicLocation targetLocation = findBestCaptureTarget(aiPlayer, gameEntities);
        
        if (targetLocation != null) {
            targetLocationId = targetLocation.getId();
            Vector2 playerPos = aiPlayer.getPosition();
            Vector2 locationPos = targetLocation.getPosition();
            Vector2 direction = locationPos.copy().subtract(playerPos);
            double distance = direction.getMagnitude();
            
            // Move towards the location
            if (distance > 25) { // Not in capture range yet
                direction.normalize();
                input.setMoveX(direction.x);
                input.setMoveY(direction.y);
                
                // Sprint if the location is being contested
                if (targetLocation.getCapturingPlayerId() != 0 && 
                    targetLocation.getCapturingPlayerId() != aiPlayer.getId()) {
                    input.setShift(true);
                }
                
                isCapturing = false;
            } else {
                // In capture range - stay still and defend
                isCapturing = true;
                if (!isCapturing) {
                    captureStartTime = System.currentTimeMillis() / 1000.0;
                }
            }
            
            // Aim towards potential threats while capturing
            Player nearestThreat = findNearestThreat(aiPlayer, gameEntities, locationPos, 150);
            if (nearestThreat != null) {
                Vector2 threatPos = nearestThreat.getPosition();
                input.setWorldX(threatPos.x);
                input.setWorldY(threatPos.y);
                
                // Shoot at threats if they're close enough
                double threatDistance = playerPos.distance(threatPos);
                if (threatDistance < 200 && aiPlayer.canShoot()) {
                    input.setLeft(true);
                }
            } else {
                // No immediate threats, aim outward from the location
                Vector2 aimDirection = playerPos.copy().subtract(locationPos);
                if (aimDirection.getMagnitude() > 0) {
                    aimDirection.normalize();
                    input.setWorldX(playerPos.x + aimDirection.x * 100);
                    input.setWorldY(playerPos.y + aimDirection.y * 100);
                } else {
                    // Default aim direction
                    input.setWorldX(playerPos.x + 100);
                    input.setWorldY(playerPos.y);
                }
            }
            
            // Reload when safe and necessary
            if (aiPlayer.getCurrentWeapon().getCurrentAmmo() <= 5 && nearestThreat == null && !aiPlayer.isReloading()) {
                input.setReload(true);
            }
        }
        
        return input;
    }
    
    @Override
    public void onEnter(AIPlayer aiPlayer) {
        targetLocationId = -1;
        isCapturing = false;
        captureStartTime = 0;
    }
    
    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        StrategicLocation targetLocation = findBestCaptureTarget(aiPlayer, gameEntities);
        
        // Continue if we have a good target location
        if (targetLocation != null) {
            return true;
        }
        
        // Continue if we're currently capturing something
        if (isCapturing) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        StrategicLocation bestTarget = findBestCaptureTarget(aiPlayer, gameEntities);
        if (bestTarget == null) {
            return 0;
        }
        
        Vector2 playerPos = aiPlayer.getPosition();
        double distance = playerPos.distance(bestTarget.getPosition());
        
        // Higher priority for closer locations and based on strategic importance
        int basePriority = (int) (60 * (1.0 - Math.min(distance / 500.0, 1.0)));
        
        // Bonus for neutral or enemy-controlled locations
        if (bestTarget.isNeutral() || !bestTarget.isControlledBy(aiPlayer.getId())) {
            basePriority += 20;
        }
        
        // Personality factor - strategic players prefer this behavior
        int personalityBonus = (int) (aiPlayer.getPersonality().getStrategicThinking() * 15);
        
        return Math.min(100, basePriority + personalityBonus);
    }
    
    @Override
    public String getName() {
        return "Capture";
    }
    
    private StrategicLocation findBestCaptureTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        StrategicLocation bestLocation = null;
        double bestScore = 0;
        
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            // Skip locations we already control
            if (location.isControlledBy(aiPlayer.getId())) {
                continue;
            }
            
            double distance = playerPos.distance(location.getPosition());
            if (distance > 600) { // Too far away
                continue;
            }
            
            // Score based on distance and strategic value
            double distanceScore = 1.0 - (distance / 600.0);
            double strategicScore = 1.0;
            
            // Higher value for neutral locations
            if (location.isNeutral()) {
                strategicScore += 0.5;
            }
            
            // Higher value for locations being captured by enemies
            if (location.getCapturingPlayerId() != 0 && location.getCapturingPlayerId() != aiPlayer.getId()) {
                strategicScore += 0.3;
            }
            
            double totalScore = (distanceScore * 0.6) + (strategicScore * 0.4);
            
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestLocation = location;
            }
        }
        
        return bestLocation;
    }
    
    private Player findNearestThreat(AIPlayer aiPlayer, GameEntities gameEntities, Vector2 locationPos, double maxRange) {
        Player nearestThreat = null;
        double nearestDistance = maxRange;
        
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }
            
            double distance = locationPos.distance(player.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestThreat = player;
            }
        }
        
        return nearestThreat;
    }
}

package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.StrategicLocation;
import org.dyn4j.geometry.Vector2;

/**
 * Defensive behavior focused on protecting controlled strategic locations.
 * AI will patrol and defend locations it or its team controls.
 */
public class DefenseBehavior implements AIBehavior {
    private int defendingLocationId = -1;
    private Vector2 patrolTarget;
    private double patrolTime = 0;
    private final double PATROL_CHANGE_INTERVAL = 4.0;
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        // Find location to defend
        StrategicLocation locationToDefend = findLocationToDefend(aiPlayer, gameEntities);
        
        if (locationToDefend != null) {
            defendingLocationId = locationToDefend.getId();
            Vector2 playerPos = aiPlayer.getPosition();
            Vector2 locationPos = locationToDefend.getPosition();
            
            // Check for nearby threats
            Player nearestThreat = findNearestThreat(aiPlayer, gameEntities, locationPos, 200);
            
            if (nearestThreat != null) {
                // Threat detected - engage
                Vector2 threatPos = nearestThreat.getPosition();
                Vector2 directionToThreat = threatPos.copy().subtract(playerPos);
                double threatDistance = directionToThreat.getMagnitude();
                
                // Position between threat and location
                Vector2 locationToThreat = threatPos.copy().subtract(locationPos);
                if (locationToThreat.getMagnitude() > 0) {
                    locationToThreat.normalize();
                    Vector2 idealPosition = locationPos.copy().add(locationToThreat.multiply(80));
                    Vector2 moveDirection = idealPosition.copy().subtract(playerPos);
                    
                    if (moveDirection.getMagnitude() > 10) {
                        moveDirection.normalize();
                        input.setMoveX(moveDirection.x);
                        input.setMoveY(moveDirection.y);
                    }
                }
                
                // Aim at threat
                input.setWorldX(threatPos.x);
                input.setWorldY(threatPos.y);
                
                // Shoot if in range and have a clear shot
                if (threatDistance < 250 && aiPlayer.canShoot()) {
                    input.setLeft(true);
                }
                
            } else {
                // No immediate threats - patrol around the location
                patrolAroundLocation(aiPlayer, locationPos, input, deltaTime);
            }
            
            // Reload when safe
            if (aiPlayer.getCurrentWeapon().getAmmo() <= 3 && nearestThreat == null && !aiPlayer.isReloading()) {
                input.setReload(true);
            }
        }
        
        return input;
    }
    
    @Override
    public void onEnter(AIPlayer aiPlayer) {
        defendingLocationId = -1;
        patrolTarget = null;
        patrolTime = 0;
    }
    
    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue if we have a location to defend
        StrategicLocation locationToDefend = findLocationToDefend(aiPlayer, gameEntities);
        return locationToDefend != null;
    }
    
    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        StrategicLocation locationToDefend = findLocationToDefend(aiPlayer, gameEntities);
        if (locationToDefend == null) {
            return 0;
        }
        
        Vector2 playerPos = aiPlayer.getPosition();
        double distance = playerPos.distance(locationToDefend.getPosition());
        
        // Higher priority for closer controlled locations
        int basePriority = (int) (50 * (1.0 - Math.min(distance / 300.0, 1.0)));
        
        // Bonus if there are threats nearby
        Player nearestThreat = findNearestThreat(aiPlayer, gameEntities, locationToDefend.getPosition(), 200);
        if (nearestThreat != null) {
            basePriority += 30;
        }
        
        // Personality factor - defensive players prefer this behavior
        int personalityBonus = (int) ((1.0 - aiPlayer.getPersonality().getAggressiveness()) * 15);
        
        return Math.min(100, basePriority + personalityBonus);
    }
    
    @Override
    public String getName() {
        return "Defense";
    }
    
    private StrategicLocation findLocationToDefend(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        StrategicLocation bestLocation = null;
        double bestScore = 0;
        
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            // Only defend locations we control
            if (!location.isControlledBy(aiPlayer.getId())) {
                continue;
            }
            
            double distance = playerPos.distance(location.getPosition());
            if (distance > 400) { // Too far to effectively defend
                continue;
            }
            
            // Score based on proximity and need for defense
            double distanceScore = 1.0 - (distance / 400.0);
            double threatScore = 0;
            
            // Check for nearby enemies
            for (Player player : gameEntities.getAllPlayers()) {
                if (player.getId() != aiPlayer.getId() && player.isActive()) {
                    double threatDistance = location.getPosition().distance(player.getPosition());
                    if (threatDistance < 250) {
                        threatScore += 1.0 - (threatDistance / 250.0);
                    }
                }
            }
            
            double totalScore = (distanceScore * 0.4) + (threatScore * 0.6);
            
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
    
    private void patrolAroundLocation(AIPlayer aiPlayer, Vector2 locationPos, PlayerInput input, double deltaTime) {
        Vector2 playerPos = aiPlayer.getPosition();
        
        // Update patrol target
        patrolTime += deltaTime;
        if (patrolTarget == null || patrolTime >= PATROL_CHANGE_INTERVAL) {
            generatePatrolTarget(locationPos);
            patrolTime = 0;
        }
        
        // Move towards patrol target
        Vector2 direction = patrolTarget.copy().subtract(playerPos);
        if (direction.getMagnitude() > 15) {
            direction.normalize();
            input.setMoveX(direction.x * 0.5); // Slower patrol movement
            input.setMoveY(direction.y * 0.5);
        }
        
        // Aim outward from the location while patrolling
        Vector2 aimDirection = playerPos.copy().subtract(locationPos);
        if (aimDirection.getMagnitude() > 0) {
            aimDirection.normalize();
            input.setWorldX(playerPos.x + aimDirection.x * 150);
            input.setWorldY(playerPos.y + aimDirection.y * 150);
        }
    }
    
    private void generatePatrolTarget(Vector2 locationPos) {
        // Generate a patrol point around the location
        double angle = Math.random() * 2 * Math.PI;
        double radius = 60 + Math.random() * 40; // 60-100 units from location
        
        patrolTarget = new Vector2(
            locationPos.x + Math.cos(angle) * radius,
            locationPos.y + Math.sin(angle) * radius
        );
    }
}

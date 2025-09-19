package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.StrategicLocation;
import org.dyn4j.geometry.Vector2;

import java.util.Random;

/**
 * Default behavior when AI has no specific objective.
 * AI will wander around and look for opportunities.
 */
public class IdleBehavior implements AIBehavior {
    private static final Random RANDOM = new Random();
    private Vector2 wanderTarget;
    private double wanderChangeTime = 0;
    private final double WANDER_CHANGE_INTERVAL = 3.0; // Change direction every 3 seconds
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        // Update wander behavior
        wanderChangeTime += deltaTime;
        if (wanderTarget == null || wanderChangeTime >= WANDER_CHANGE_INTERVAL) {
            generateNewWanderTarget(aiPlayer);
            wanderChangeTime = 0;
        }
        
        // Move towards wander target
        Vector2 playerPos = aiPlayer.getPosition();
        Vector2 direction = wanderTarget.copy().subtract(playerPos);
        
        if (direction.getMagnitude() > 10) { // Don't move if very close to target
            direction.normalize();
            input.setMoveX(direction.x);
            input.setMoveY(direction.y);
        }
        
        // Look for nearby strategic locations to aim at
        StrategicLocation nearestLocation = findNearestStrategicLocation(aiPlayer, gameEntities);
        if (nearestLocation != null) {
            Vector2 locationPos = nearestLocation.getPosition();
            input.setWorldX(locationPos.x);
            input.setWorldY(locationPos.y);
        } else {
            // Aim in movement direction if no strategic target
            input.setWorldX(playerPos.x + direction.x * 100);
            input.setWorldY(playerPos.y + direction.y * 100);
        }
        
        return input;
    }
    
    @Override
    public void onEnter(AIPlayer aiPlayer) {
        wanderTarget = null;
        wanderChangeTime = 0;
    }
    
    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        return 10; // Lowest priority - only when nothing else to do
    }
    
    @Override
    public String getName() {
        return "Idle";
    }
    
    private void generateNewWanderTarget(AIPlayer aiPlayer) {
        Vector2 playerPos = aiPlayer.getPosition();
        
        // Generate a random point within reasonable distance
        double angle = RANDOM.nextDouble() * 2 * Math.PI;
        double distance = 100 + RANDOM.nextDouble() * 200; // 100-300 units away
        
        wanderTarget = new Vector2(
            playerPos.x + Math.cos(angle) * distance,
            playerPos.y + Math.sin(angle) * distance
        );
        
        // Keep within world bounds (rough approximation)
        wanderTarget.x = Math.max(-900, Math.min(900, wanderTarget.x));
        wanderTarget.y = Math.max(-900, Math.min(900, wanderTarget.y));
    }
    
    private StrategicLocation findNearestStrategicLocation(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        StrategicLocation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            double distance = playerPos.distance(location.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = location;
            }
        }
        
        return nearest;
    }
}

package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.StrategicLocation;
import org.dyn4j.geometry.Vector2;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Default behavior when AI has no specific objective.
 * AI will wander around and look for opportunities.
 */
public class IdleBehavior implements AIBehavior {
    private static final double WANDER_CHANGE_INTERVAL = 2.0; // Change direction every 2 seconds for more dynamic movement
    private Vector2 wanderTarget;
    private double wanderChangeTime = 0;

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
        
        // Always move towards target, but slow down as we approach
        double distance = direction.getMagnitude();
        direction.normalize();
        
        // Speed scales with distance, but never goes to zero
        double moveSpeed = Math.max(0.3, Math.min(0.8, distance / 100.0));
        input.setMoveX(direction.x * moveSpeed);
        input.setMoveY(direction.y * moveSpeed);

        // First priority: Look for nearby enemies
        com.fullsteam.physics.Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
        if (nearestEnemy != null) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            input.setWorldX(enemyPos.x);
            input.setWorldY(enemyPos.y);

            // More aggressive enemy engagement in idle mode
            double enemyDistance = playerPos.distance(enemyPos);
            if (enemyDistance < 400 && aiPlayer.canShoot()) {
                input.setLeft(true);
                
                // Move towards enemy if they're within reasonable range
                if (enemyDistance > 150 && enemyDistance < 350) {
                    Vector2 toEnemy = enemyPos.copy().subtract(playerPos);
                    toEnemy.normalize();
                    input.setMoveX(toEnemy.x * 0.7);
                    input.setMoveY(toEnemy.y * 0.7);
                }
            }
        } else {
            // Second priority: Look for nearby strategic locations
            StrategicLocation nearestLocation = findNearestStrategicLocation(aiPlayer, gameEntities);
            if (nearestLocation != null) {
                Vector2 locationPos = nearestLocation.getPosition();
                input.setWorldX(locationPos.x);
                input.setWorldY(locationPos.y);
            } else {
                // Default: Aim in movement direction
                input.setWorldX(playerPos.x + direction.x * 100);
                input.setWorldY(playerPos.y + direction.y * 100);
            }
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
        // Check if there are any nearby enemies - if so, reduce priority to let combat take over
        com.fullsteam.physics.Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
        if (nearestEnemy != null) {
            return 5; // Very low priority when enemies are near
        }
        return 15; // Slightly higher priority when no enemies around
    }

    @Override
    public String getName() {
        return "Idle";
    }

    private void generateNewWanderTarget(AIPlayer aiPlayer) {
        Vector2 playerPos = aiPlayer.getPosition();

        // Generate a random point within reasonable distance - ensure minimum distance to keep moving
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = 150 + ThreadLocalRandom.current().nextDouble() * 250; // 150-400 units away (increased min)

        wanderTarget = new Vector2(
                playerPos.x + Math.cos(angle) * distance,
                playerPos.y + Math.sin(angle) * distance
        );

        // Keep within world bounds (rough approximation)
        wanderTarget.x = Math.max(-900, Math.min(900, wanderTarget.x));
        wanderTarget.y = Math.max(-900, Math.min(900, wanderTarget.y));
        
        // If target is too close to current position, extend it
        double actualDistance = playerPos.distance(wanderTarget);
        if (actualDistance < 100) {
            Vector2 direction = wanderTarget.copy().subtract(playerPos);
            direction.normalize();
            wanderTarget = playerPos.copy().add(direction.multiply(150));
        }
    }

    private com.fullsteam.physics.Player findNearestEnemy(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        com.fullsteam.physics.Player nearest = null;
        double nearestDistance = 400; // Only consider enemies within 400 units

        for (com.fullsteam.physics.Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            // Skip teammates - only target enemies
            if (aiPlayer.isTeammate(player)) {
                continue;
            }

            double distance = playerPos.distance(player.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
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

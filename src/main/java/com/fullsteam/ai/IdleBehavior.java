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
    private static final double WANDER_CHANGE_INTERVAL = 5.0; // Change direction every 5 seconds for smoother movement
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

        if (direction.getMagnitude() > 20) { // Increased threshold for smoother movement
            direction.normalize();
            input.setMoveX(direction.x * 0.6); // Slower idle movement
            input.setMoveY(direction.y * 0.6);
        }

        // First priority: Look for nearby enemies
        com.fullsteam.physics.Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
        if (nearestEnemy != null) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            input.setWorldX(enemyPos.x);
            input.setWorldY(enemyPos.y);

            // Shoot at enemies if they're reasonably close
            double enemyDistance = playerPos.distance(enemyPos);
            if (enemyDistance < 300 && aiPlayer.canShoot()) {
                input.setLeft(true);
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

        // Generate a random point within reasonable distance
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = 100 + ThreadLocalRandom.current().nextDouble() * 200; // 100-300 units away

        wanderTarget = new Vector2(
                playerPos.x + Math.cos(angle) * distance,
                playerPos.y + Math.sin(angle) * distance
        );

        // Keep within world bounds (rough approximation)
        wanderTarget.x = Math.max(-900, Math.min(900, wanderTarget.x));
        wanderTarget.y = Math.max(-900, Math.min(900, wanderTarget.y));
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

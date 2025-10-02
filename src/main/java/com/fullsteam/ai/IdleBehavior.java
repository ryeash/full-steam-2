package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
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

        // First priority: Look for nearby enemies (players and turrets)
        AITargetWrapper nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
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
            input.setWorldX(playerPos.x + direction.x * 100);
            input.setWorldY(playerPos.y + direction.y * 100);
        }

        // Always check for reload when idle - this is the missing piece!
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();

        // Reload if out of ammo or if ammo is low and we're not in immediate danger
        boolean shouldReload = false;
        if (currentAmmo == 0) {
            shouldReload = true; // Must reload when completely out
        } else if (currentAmmo <= 5 && nearestEnemy == null) {
            shouldReload = true; // Reload when safe and low on ammo
        } else if (currentAmmo < magazineSize * 0.4 && nearestEnemy == null) {
            shouldReload = true; // Reload when safe and less than 40% ammo
        }

        if (shouldReload && !aiPlayer.isReloading()) {
            input.setReload(true);
        }
        
        // Use support utilities when idle and safe
        evaluateIdleUtilityUsage(aiPlayer, input, nearestEnemy);

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
        AITargetWrapper nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
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

    private AITargetWrapper findNearestEnemy(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        AITargetWrapper nearest = null;
        double nearestDistance = 400; // Only consider enemies within 400 units

        // Check all enemy players
        for (Player player : gameEntities.getAllPlayers()) {
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
                nearest = AITargetWrapper.fromPlayer(player);
            }
        }
        
        // Check all enemy turrets
        for (Turret turret : gameEntities.getAllTurrets()) {
            if (!turret.isActive()) {
                continue;
            }
            
            // Skip friendly turrets - only target enemies
            AITargetWrapper turretWrapper = AITargetWrapper.fromTurret(turret);
            if (isTeammate(aiPlayer, turretWrapper)) {
                continue;
            }

            double distance = playerPos.distance(turret.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = turretWrapper;
            }
        }

        return nearest;
    }
    
    private boolean isTeammate(AIPlayer aiPlayer, AITargetWrapper target) {
        // In FFA mode (team 0), everyone is an enemy
        if (aiPlayer.getTeam() == 0 || target.getTeam() == 0) {
            return false;
        }
        
        // In team mode, check if they're on the same team
        return aiPlayer.getTeam() == target.getTeam();
    }
    
    /**
     * Evaluate utility weapon usage during idle behavior.
     * Focus on support and defensive utilities when safe.
     */
    private void evaluateIdleUtilityUsage(AIPlayer aiPlayer, PlayerInput input, AITargetWrapper nearestEnemy) {
        // Don't use utility if on cooldown or if enemies are very close
        if (!aiPlayer.canUseUtility()) {
            return;
        }
        
        if (nearestEnemy != null && aiPlayer.getPosition().distance(nearestEnemy.getPosition()) < 200) {
            return; // Too dangerous to use utilities
        }
        
        UtilityWeapon utility = aiPlayer.getUtilityWeapon();
        boolean shouldUseUtility = false;
        double usageChance = 0.0;
        
        switch (utility.getCategory()) {
            case SUPPORT:
                // Use support utilities when health is low or proactively
                if (aiPlayer.getHealth() < 80) {
                    usageChance = 0.3;
                    if (aiPlayer.getHealth() < 50) usageChance += 0.4;
                } else if (nearestEnemy == null) {
                    // Proactive support usage when completely safe
                    usageChance = 0.1;
                }
                break;
                
            case DEFENSIVE:
                // Use defensive utilities when health is low or preparing for combat
                if (aiPlayer.getHealth() < 70) {
                    usageChance = 0.4;
                } else if (nearestEnemy != null && aiPlayer.getPosition().distance(nearestEnemy.getPosition()) < 400) {
                    // Prepare defenses when enemy is at medium range
                    usageChance = 0.2;
                }
                break;
                
            case TACTICAL:
                // Use tactical utilities for map control when safe
                if (nearestEnemy == null || aiPlayer.getPosition().distance(nearestEnemy.getPosition()) > 300) {
                    usageChance = 0.15;
                }
                break;
                
            case CROWD_CONTROL:
                // Generally don't use crowd control when idle unless preparing for combat
                if (nearestEnemy != null && aiPlayer.getPosition().distance(nearestEnemy.getPosition()) < 350) {
                    usageChance = 0.1;
                }
                break;
        }
        
        // Personality modifiers
        double personalityMultiplier = 1.0;
        
        // Strategic personalities use utilities more proactively
        if (aiPlayer.getPersonality().getPatience() > 0.6) {
            personalityMultiplier += 0.4;
        }
        
        // Defensive personalities use defensive utilities more often
        if (utility.getCategory() == UtilityWeapon.UtilityCategory.DEFENSIVE && 
            aiPlayer.getPersonality().getAggressiveness() < 0.4) {
            personalityMultiplier += 0.3;
        }
        
        usageChance *= personalityMultiplier;
        
        // Random factor with lower base chance than combat
        shouldUseUtility = Math.random() < usageChance;
        
        if (shouldUseUtility) {
            input.setAltFire(true);
        }
    }
}

package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

/**
 * Aggressive behavior focused on engaging enemy players in combat.
 * AI will seek out and attack nearby players.
 */
public class CombatBehavior implements AIBehavior {
    private int targetPlayerId = -1;
    private double lastShotTime = 0;
    private double combatTimeout = 5.0; // Stop combat if no target for 5 seconds
    private double timeSinceLastTarget = 0;
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        // Find or update target
        Player target = findBestTarget(aiPlayer, gameEntities);
        
        if (target != null) {
            targetPlayerId = target.getId();
            timeSinceLastTarget = 0;
            
            Vector2 playerPos = aiPlayer.getPosition();
            Vector2 targetPos = target.getPosition();
            Vector2 direction = targetPos.copy().subtract(playerPos);
            double distance = direction.getMagnitude();
            
            // Movement strategy based on distance and personality
            if (distance > aiPlayer.getPersonality().getPreferredCombatRange()) {
                // Move towards target
                direction.normalize();
                input.setMoveX(direction.x);
                input.setMoveY(direction.y);
                
                // Use sprint if aggressive personality
                if (aiPlayer.getPersonality().getAggressiveness() > 0.7) {
                    input.setShift(true);
                }
            } else if (distance < aiPlayer.getPersonality().getPreferredCombatRange() * 0.5) {
                // Too close, back away while shooting
                direction.normalize();
                input.setMoveX(-direction.x * 0.5);
                input.setMoveY(-direction.y * 0.5);
            } else {
                // Good distance, strafe around target
                Vector2 strafeDirection = new Vector2(-direction.y, direction.x);
                strafeDirection.normalize();
                input.setMoveX(strafeDirection.x * 0.7);
                input.setMoveY(strafeDirection.y * 0.7);
            }
            
            // Aim at target with prediction based on target velocity
            Vector2 targetVelocity = target.getVelocity();
            double timeToTarget = distance / aiPlayer.getCurrentWeapon().getProjectileSpeed();
            Vector2 predictedPos = targetPos.copy().add(targetVelocity.copy().multiply(timeToTarget));
            
            input.setWorldX(predictedPos.x);
            input.setWorldY(predictedPos.y);
            
            // Shooting logic
            boolean shouldShoot = shouldShootAtTarget(aiPlayer, target, distance);
            if (shouldShoot && aiPlayer.canShoot()) {
                input.setLeft(true);
                lastShotTime = 0;
            }
            
            // Reload when necessary
            if (aiPlayer.getCurrentWeapon().getAmmo() <= 2 && !aiPlayer.isReloading()) {
                input.setReload(true);
            }
            
        } else {
            timeSinceLastTarget += deltaTime;
            targetPlayerId = -1;
        }
        
        lastShotTime += deltaTime;
        
        return input;
    }
    
    @Override
    public void onEnter(AIPlayer aiPlayer) {
        targetPlayerId = -1;
        timeSinceLastTarget = 0;
        lastShotTime = 0;
    }
    
    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue if we have a target or recently had one
        if (timeSinceLastTarget < combatTimeout) {
            return true;
        }
        
        // Continue if there are nearby enemies
        Player nearestEnemy = findBestTarget(aiPlayer, gameEntities);
        return nearestEnemy != null;
    }
    
    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        Player nearestEnemy = findBestTarget(aiPlayer, gameEntities);
        if (nearestEnemy == null) {
            return 0;
        }
        
        double distance = aiPlayer.getPosition().distance(nearestEnemy.getPosition());
        double maxCombatRange = 300; // Maximum engagement range
        
        if (distance > maxCombatRange) {
            return 0;
        }
        
        // Higher priority for closer enemies and more aggressive personalities
        int basePriority = (int) (80 * (1.0 - distance / maxCombatRange));
        int personalityBonus = (int) (aiPlayer.getPersonality().getAggressiveness() * 20);
        
        return Math.min(100, basePriority + personalityBonus);
    }
    
    @Override
    public String getName() {
        return "Combat";
    }
    
    private Player findBestTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        Player bestTarget = null;
        double bestScore = 0;
        
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }
            
            double distance = playerPos.distance(player.getPosition());
            if (distance > 400) { // Max engagement range
                continue;
            }
            
            // Score based on distance (closer is better) and health (lower health is better)
            double distanceScore = 1.0 - (distance / 400.0);
            double healthScore = 1.0 - (player.getHealth() / 100.0);
            double totalScore = (distanceScore * 0.7) + (healthScore * 0.3);
            
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestTarget = player;
            }
        }
        
        return bestTarget;
    }
    
    private boolean shouldShootAtTarget(AIPlayer aiPlayer, Player target, double distance) {
        // Don't shoot if reloading or no ammo
        if (aiPlayer.isReloading() || aiPlayer.getCurrentWeapon().getAmmo() <= 0) {
            return false;
        }
        
        // Consider accuracy at distance
        double accuracy = aiPlayer.getCurrentWeapon().getAccuracy();
        double effectiveRange = aiPlayer.getCurrentWeapon().getRange();
        
        // Reduce accuracy at longer ranges
        double rangeAccuracy = Math.max(0.1, 1.0 - (distance / effectiveRange));
        double finalAccuracy = accuracy * rangeAccuracy * aiPlayer.getPersonality().getAccuracy();
        
        // Random chance to shoot based on accuracy and personality
        return Math.random() < finalAccuracy;
    }
}

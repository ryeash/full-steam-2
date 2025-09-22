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
    private double combatTimeout = 8.0; // Longer combat persistence
    private double timeSinceLastTarget = 0;
    
    // Enhanced tactical state
    private Vector2 lastTargetPosition = new Vector2(0, 0);
    private double targetPursuitTime = 0;
    private boolean isRetreating = false;
    private double retreatStartTime = 0;
    private Vector2 retreatDirection = new Vector2(0, 0);
    private double lastHealthCheck = 100.0;
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        // Update timers
        lastShotTime += deltaTime;
        targetPursuitTime += deltaTime;
        
        // Check for retreat conditions
        evaluateRetreatConditions(aiPlayer, deltaTime);
        
        // Find target (current or new)
        Player target = findOrMaintainTarget(aiPlayer, gameEntities);
        
        if (target != null) {
            timeSinceLastTarget = 0;
            lastTargetPosition = target.getPosition().copy();
            
            // Generate tactical movement and combat
            generateTacticalMovement(aiPlayer, target, input, deltaTime);
            generateCombatActions(aiPlayer, target, input, deltaTime);
            
        } else if (timeSinceLastTarget < combatTimeout) {
            // Pursue last known position
            pursueLastKnownPosition(aiPlayer, input, deltaTime);
            timeSinceLastTarget += deltaTime;
        } else {
            // No target, reset state
            targetPlayerId = -1;
            isRetreating = false;
            timeSinceLastTarget += deltaTime;
        }
        
        return input;
    }
    
    /**
     * Enhanced tactical movement based on weapon range, health, and tactical situation.
     */
    private void generateTacticalMovement(AIPlayer aiPlayer, Player target, PlayerInput input, double deltaTime) {
        Vector2 playerPos = aiPlayer.getPosition();
        Vector2 targetPos = target.getPosition();
        Vector2 direction = targetPos.copy().subtract(playerPos);
        double distance = direction.getMagnitude();
        
        // Get optimal range for current weapon
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();
        double optimalRange = weaponRange * 0.7; // Stay at 70% of max range
        double minRange = Math.min(weaponRange * 0.3, 80); // Don't get too close
        
        // Health-based tactics
        double healthPercent = aiPlayer.getHealth() / 100.0;
        boolean shouldRetreat = isRetreating || healthPercent < 0.3;
        
        if (shouldRetreat && !isRetreating) {
            initiateRetreat(playerPos, targetPos);
        }
        
        Vector2 moveDirection = new Vector2(0, 0);
        
        if (isRetreating) {
            // Retreat while maintaining line of sight
            moveDirection = retreatDirection.copy();
            
            // Stop retreating if health recovered or enough distance
            if (healthPercent > 0.6 || distance > optimalRange * 1.5) {
                isRetreating = false;
            }
        } else if (distance > optimalRange + 50) {
            // Aggressive pursuit - close the distance
            direction.normalize();
            moveDirection = direction.copy();
            
            // Sprint if far away and aggressive
            if (distance > optimalRange * 1.8 && aiPlayer.getPersonality().getAggressiveness() > 0.5) {
                input.setShift(true);
            }
        } else if (distance < minRange) {
            // Too close - back away while fighting
            direction.normalize();
            moveDirection = direction.copy().multiply(-1);
        } else {
            // Optimal range - strafe around target
            Vector2 strafeDirection = new Vector2(-direction.y, direction.x);
            strafeDirection.normalize();
            
            // Dynamic strafing based on time and personality
            double strafeTime = targetPursuitTime * aiPlayer.getPersonality().getMobility();
            double strafePattern = Math.sin(strafeTime * 2.0) * 0.7 + Math.cos(strafeTime * 1.3) * 0.3;
            
            moveDirection = strafeDirection.copy().multiply(strafePattern);
            
            // Add small approach/retreat component to stay in optimal range
            double rangeError = distance - optimalRange;
            Vector2 rangeCorrection = direction.copy();
            rangeCorrection.normalize();
            rangeCorrection.multiply(-rangeError * 0.01);
            moveDirection.add(rangeCorrection);
        }
        
        // Apply movement with personality-based intensity
        double moveIntensity = 0.7 + (aiPlayer.getPersonality().getMobility() * 0.5);
        
        // Ensure AI always has some movement - never completely stop
        if (moveDirection.getMagnitude() < 0.1) {
            // Add subtle movement to prevent stopping
            Vector2 currentMovement = aiPlayer.getCurrentMovementDirection();
            if (currentMovement.getMagnitude() > 0.1) {
                // Continue in current direction with reduced intensity
                moveDirection = currentMovement.copy().multiply(0.3);
            } else {
                // Random subtle movement
                double randomAngle = Math.random() * Math.PI * 2;
                moveDirection = new Vector2(Math.cos(randomAngle), Math.sin(randomAngle)).multiply(0.2);
            }
        }
        
        moveDirection.normalize();
        input.setMoveX(moveDirection.x * moveIntensity);
        input.setMoveY(moveDirection.y * moveIntensity);
    }
    
    /**
     * Enhanced combat actions including aiming, shooting, and reloading.
     */
    private void generateCombatActions(AIPlayer aiPlayer, Player target, PlayerInput input, double deltaTime) {
        Vector2 playerPos = aiPlayer.getPosition();
        Vector2 targetPos = target.getPosition();
        double distance = playerPos.distance(targetPos);
        
        // Advanced target prediction
        Vector2 targetVelocity = target.getVelocity();
        double projectileSpeed = aiPlayer.getCurrentWeapon().getProjectileSpeed();
        double timeToTarget = distance / projectileSpeed;
        
        // Predict target movement with some uncertainty for realism
        Vector2 predictedPos = targetPos.copy().add(targetVelocity.copy().multiply(timeToTarget));
        
        // Add slight inaccuracy based on distance and personality
        double accuracy = aiPlayer.getPersonality().getAccuracy();
        double distanceInaccuracy = Math.max(0, distance - 200) * 0.05 * (1.0 - accuracy);
        double randomOffsetX = (Math.random() - 0.5) * distanceInaccuracy;
        double randomOffsetY = (Math.random() - 0.5) * distanceInaccuracy;
        
        predictedPos.add(randomOffsetX, randomOffsetY);
        
        // Set aim
        input.setWorldX(predictedPos.x);
        input.setWorldY(predictedPos.y);
        
        // Enhanced shooting decision
        boolean shouldShoot = shouldShootAtTarget(aiPlayer, target, distance);
        if (shouldShoot && aiPlayer.canShoot() && lastShotTime > 0.1) { // Minimum time between shots
            input.setLeft(true);
            lastShotTime = 0;
        }
        
        // Smart reloading
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();
        
        boolean shouldReload = false;
        if (currentAmmo == 0) {
            shouldReload = true; // Must reload
        } else if (currentAmmo <= 3 && distance > 300) {
            shouldReload = true; // Reload when safe and low ammo
        } else if (currentAmmo < magazineSize * 0.3 && !target.isActive()) {
            shouldReload = true; // Reload during lull in combat
        }
        
        if (shouldReload && !aiPlayer.isReloading()) {
            input.setReload(true);
        }
    }
    
    /**
     * Pursue last known target position when target is lost.
     */
    private void pursueLastKnownPosition(AIPlayer aiPlayer, PlayerInput input, double deltaTime) {
        Vector2 playerPos = aiPlayer.getPosition();
        Vector2 direction = lastTargetPosition.copy().subtract(playerPos);
        double distance = direction.getMagnitude();
        
        if (distance > 50) { // Move to last known position
            direction.normalize();
            input.setMoveX(direction.x * 0.6);
            input.setMoveY(direction.y * 0.6);
            
            // Sprint if aggressive and target was recently lost
            if (timeSinceLastTarget < 2.0 && aiPlayer.getPersonality().getAggressiveness() > 0.7) {
                input.setShift(true);
            }
        }
    }
    
    /**
     * Evaluate if AI should retreat based on health, ammunition, and tactical situation.
     */
    private void evaluateRetreatConditions(AIPlayer aiPlayer, double deltaTime) {
        double currentHealth = aiPlayer.getHealth();
        
        // Check if health dropped significantly
        if (currentHealth < lastHealthCheck - 20) {
            // Took significant damage, consider retreat
            if (currentHealth < 40 || aiPlayer.getPersonality().getPatience() > 0.7) {
                isRetreating = true;
                retreatStartTime = 0;
            }
        }
        
        lastHealthCheck = currentHealth;
        
        if (isRetreating) {
            retreatStartTime += deltaTime;
            // Stop retreating after some time or if health recovered
            if (retreatStartTime > 3.0 || currentHealth > 70) {
                isRetreating = false;
            }
        }
    }
    
    /**
     * Initiate tactical retreat.
     */
    private void initiateRetreat(Vector2 playerPos, Vector2 threatPos) {
        isRetreating = true;
        retreatStartTime = 0;
        
        // Retreat direction away from threat
        retreatDirection = playerPos.copy().subtract(threatPos);
        if (retreatDirection.getMagnitude() < 1.0) {
            // If positions are identical, retreat in random direction
            retreatDirection = new Vector2(Math.random() - 0.5, Math.random() - 0.5);
        }
        retreatDirection.normalize();
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
        double maxCombatRange = 500; // Increased engagement range
        
        if (distance > maxCombatRange) {
            return 0;
        }
        
        // Higher priority for closer enemies and more aggressive personalities
        int basePriority = (int) (90 * (1.0 - distance / maxCombatRange));
        int personalityBonus = (int) (aiPlayer.getPersonality().getAggressiveness() * 10);
        
        // Always prioritize combat when enemies are very close
        if (distance < 200) {
            basePriority = Math.max(basePriority, 85);
        }
        
        return Math.min(100, basePriority + personalityBonus);
    }
    
    @Override
    public String getName() {
        return "Combat";
    }
    
    /**
     * Find or maintain current target with enhanced persistence.
     */
    private Player findOrMaintainTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        // First, try to maintain current target if valid
        if (targetPlayerId != -1) {
            Player currentTarget = findPlayerById(gameEntities, targetPlayerId);
            if (currentTarget != null && currentTarget.isActive() && !aiPlayer.isTeammate(currentTarget)) {
                double distance = aiPlayer.getPosition().distance(currentTarget.getPosition());
                if (distance <= 800) { // Increased persistence range
                    return currentTarget;
                }
            }
        }
        
        // Find new target
        return findBestTarget(aiPlayer, gameEntities);
    }
    
    private Player findBestTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        Player bestTarget = null;
        double bestScore = 0;
        
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }
            
            // Skip teammates - only target enemies
            if (aiPlayer.isTeammate(player)) {
                continue;
            }
            
            double distance = playerPos.distance(player.getPosition());
            if (distance > 700) { // Slightly increased max engagement range
                continue;
            }
            
            // Enhanced scoring system
            double distanceScore = 1.0 - (distance / 700.0);
            double healthScore = 1.0 - (player.getHealth() / 100.0);
            
            // Strong bonus for injured enemies (pursue wounded)
            double injuryBonus = 0;
            if (player.getHealth() < 50) {
                injuryBonus = 0.4; // Aggressively pursue wounded enemies
            } else if (player.getHealth() < 75) {
                injuryBonus = 0.2;
            }
            
            // Continuity bonus for current target
            double continuityBonus = 0;
            if (targetPlayerId == player.getId()) {
                continuityBonus = 0.5; // Strong preference to maintain target
            }
            
            // Threat assessment - prioritize players aiming at us
            double threatBonus = 0;
            Vector2 playerToTarget = playerPos.copy().subtract(player.getPosition());
            Vector2 targetAimDirection = getPlayerAimDirection(player);
            if (targetAimDirection != null) {
                playerToTarget.normalize();
            double aimAlignment = playerToTarget.dot(targetAimDirection);
                if (aimAlignment > 0.7) { // Player is aiming at us
                    threatBonus = 0.3;
                }
            }
            
            // Weapon effectiveness at this range
            double weaponEffectiveness = calculateWeaponEffectiveness(aiPlayer, distance);
            
            double totalScore = (distanceScore * 0.4) + (healthScore * 0.15) + injuryBonus + 
                               continuityBonus + threatBonus + (weaponEffectiveness * 0.2);
            
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestTarget = player;
            }
        }
        
        return bestTarget;
    }
    
    private Player findPlayerById(GameEntities gameEntities, int playerId) {
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == playerId) {
                return player;
            }
        }
        return null;
    }
    
    private Vector2 getPlayerAimDirection(Player player) {
        // This would need to be implemented based on player's aim data
        // For now, return null as we don't have access to aim direction
        return null;
    }
    
    private double calculateWeaponEffectiveness(AIPlayer aiPlayer, double distance) {
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();
        if (distance > weaponRange) return 0.0;
        
        // Weapon is most effective at 60-80% of its range
        double optimalRangeStart = weaponRange * 0.6;
        double optimalRangeEnd = weaponRange * 0.8;
        
        if (distance >= optimalRangeStart && distance <= optimalRangeEnd) {
            return 1.0;
        } else if (distance < optimalRangeStart) {
            return 0.7 + (distance / optimalRangeStart) * 0.3;
        } else {
            return 0.7 * (weaponRange - distance) / (weaponRange - optimalRangeEnd);
        }
    }
    
    private boolean shouldShootAtTarget(AIPlayer aiPlayer, Player target, double distance) {
        // Don't shoot if reloading or no ammo
        if (aiPlayer.isReloading() || aiPlayer.getCurrentWeapon().getCurrentAmmo() <= 0) {
            return false;
        }
        
        // Consider accuracy at distance
        double accuracy = aiPlayer.getCurrentWeapon().getAccuracy();
        double effectiveRange = aiPlayer.getCurrentWeapon().getRange();
        
        // Reduce accuracy at longer ranges but be more generous
        double rangeAccuracy = Math.max(0.3, 1.0 - (distance / (effectiveRange * 1.5)));
        double personalityAccuracy = Math.max(0.4, aiPlayer.getPersonality().getAccuracy());
        double finalAccuracy = accuracy * rangeAccuracy * personalityAccuracy;
        
        // Be more aggressive - shoot more frequently
        double shootChance = Math.max(0.6, finalAccuracy); // At least 60% chance to shoot
        
        // Always shoot if very close
        if (distance < 100) {
            shootChance = 0.9;
        }
        
        return Math.random() < shootChance;
    }
}

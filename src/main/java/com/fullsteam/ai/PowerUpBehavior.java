package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.PowerUp;
import com.fullsteam.physics.Workshop;
import org.dyn4j.geometry.Vector2;

import java.util.List;

/**
 * Behavior for seeking and collecting power-ups from workshops.
 * AI will navigate to workshops, craft power-ups, and collect them strategically.
 */
public class PowerUpBehavior implements AIBehavior {
    private int targetPowerUpId = -1;
    private int targetWorkshopId = -1;
    private double evaluationTime = 0;
    private static final double EVALUATION_INTERVAL = 3.0; // Re-evaluate every 3 seconds
    
    // Per-AI randomization for circling patterns to prevent clustering
    private final double circleSpeedVariation;
    private final double circleRadiusVariation;
    private final double circleAngleOffset;
    
    public PowerUpBehavior() {
        // Initialize random variations per AI instance
        this.circleSpeedVariation = 0.7 + Math.random() * 0.6; // 0.7 to 1.3
        this.circleRadiusVariation = 0.6 + Math.random() * 0.5; // 0.6 to 1.1
        this.circleAngleOffset = Math.random() * Math.PI * 2; // 0 to 2π
    }
    
    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();
        
        evaluationTime += deltaTime;
        if (evaluationTime >= EVALUATION_INTERVAL) {
            evaluateTargets(aiPlayer, gameEntities);
            evaluationTime = 0;
        }
        
        Vector2 myPos = aiPlayer.getPosition();
        
        // First priority: collect nearby power-ups
        PowerUp targetPowerUp = findTargetPowerUp(aiPlayer, gameEntities);
        if (targetPowerUp != null) {
            moveTowardsPowerUp(aiPlayer, targetPowerUp, input, gameEntities);
            return input;
        }
        
        // Second priority: move to workshop to craft power-ups
        Workshop targetWorkshop = findTargetWorkshop(aiPlayer, gameEntities);
        if (targetWorkshop != null) {
            moveTowardsWorkshop(aiPlayer, targetWorkshop, input, gameEntities);
            return input;
        }
        
        // No power-ups or workshops available - wander slightly
        input.setMoveX(0.2);
        input.setMoveY(0.2);
        
        return input;
    }
    
    /**
     * Evaluate and select the best power-up or workshop to target.
     */
    private void evaluateTargets(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        double healthPercent = aiPlayer.getHealth() / 100.0;
        
        // Find best power-up
        PowerUp bestPowerUp = null;
        double bestPowerUpScore = -1;
        
        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            if (!powerUp.isActive() || !powerUp.canBeCollectedBy(aiPlayer)) {
                continue;
            }
            
            double distance = myPos.distance(powerUp.getPosition());
            if (distance > 400) {
                continue; // Too far
            }
            
            // Score based on need and distance
            double score = evaluatePowerUpValue(aiPlayer, powerUp.getType(), healthPercent);
            score *= (1.0 - (distance / 400.0)); // Closer is better
            
            if (score > bestPowerUpScore) {
                bestPowerUpScore = score;
                bestPowerUp = powerUp;
            }
        }
        
        if (bestPowerUp != null) {
            targetPowerUpId = bestPowerUp.getId();
            targetWorkshopId = -1; // Clear workshop target
        } else {
            targetPowerUpId = -1;
            
            // Find nearest workshop if we need power-ups
            Workshop nearestWorkshop = findNearestWorkshop(aiPlayer, gameEntities);
            if (nearestWorkshop != null) {
                targetWorkshopId = nearestWorkshop.getId();
            }
        }
    }
    
    /**
     * Evaluate how valuable a power-up type is for this AI based on current state.
     */
    private double evaluatePowerUpValue(AIPlayer aiPlayer, PowerUp.PowerUpType type, double healthPercent) {
        return switch (type) {
            case HEALTH_REGENERATION -> {
                // Very valuable when low health
                if (healthPercent < 0.3) yield 1.0;
                if (healthPercent < 0.6) yield 0.7;
                yield 0.3;
            }
            case DAMAGE_RESISTANCE -> {
                // Valuable when health is low or moderate
                if (healthPercent < 0.5) yield 0.9;
                yield 0.5;
            }
            case SPEED_BOOST -> {
                // Always moderately valuable for mobility
                yield 0.6 + (aiPlayer.getPersonality().getMobility() * 0.3);
            }
            case DAMAGE_BOOST -> {
                // Valuable for aggressive personalities
                yield 0.6 + (aiPlayer.getPersonality().getAggressiveness() * 0.4);
            }
            case BERSERKER_MODE -> {
                // Only valuable for very aggressive AIs with good health
                if (aiPlayer.getPersonality().getAggressiveness() > 0.7 && healthPercent > 0.6) {
                    yield 0.8;
                }
                yield 0.3;
            }
            case INFINITE_AMMO -> {
                // Valuable for aggressive AIs who want to maintain pressure
                // More valuable for weapons with small magazines
                yield 0.7 + (aiPlayer.getPersonality().getAggressiveness() * 0.2);
            }
        };
    }
    
    /**
     * Find the target power-up if it still exists and is collectible.
     */
    private PowerUp findTargetPowerUp(AIPlayer aiPlayer, GameEntities gameEntities) {
        if (targetPowerUpId == -1) {
            return null;
        }
        
        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            if (powerUp.getId() == targetPowerUpId && powerUp.isActive() && 
                powerUp.canBeCollectedBy(aiPlayer)) {
                return powerUp;
            }
        }
        
        return null;
    }
    
    /**
     * Find the target workshop if it still exists.
     */
    private Workshop findTargetWorkshop(AIPlayer aiPlayer, GameEntities gameEntities) {
        if (targetWorkshopId == -1) {
            return null;
        }
        
        for (Workshop workshop : gameEntities.getAllWorkshops()) {
            if (workshop.getId() == targetWorkshopId && workshop.isActive()) {
                // Check if it's our team's workshop or FFA
                if (aiPlayer.getTeam() == 0 || workshop.getOwnerTeam() == aiPlayer.getTeam()) {
                    return workshop;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find the nearest friendly workshop.
     */
    private Workshop findNearestWorkshop(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        Workshop nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Workshop workshop : gameEntities.getAllWorkshops()) {
            if (!workshop.isActive()) {
                continue;
            }
            
            // Only use our team's workshop (or any in FFA)
            if (aiPlayer.getTeam() != 0 && workshop.getOwnerTeam() != aiPlayer.getTeam()) {
                continue;
            }
            
            double distance = myPos.distance(workshop.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = workshop;
            }
        }
        
        return nearest;
    }
    
    /**
     * Move towards a power-up to collect it.
     */
    private void moveTowardsPowerUp(AIPlayer aiPlayer, PowerUp powerUp, PlayerInput input, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 powerUpPos = powerUp.getPosition();
        
        Vector2 direction = powerUpPos.copy().subtract(myPos);
        direction.normalize();
        
        // Apply hazard avoidance
        direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 100.0);
        
        // Move quickly towards power-up
        double moveIntensity = 0.9;
        input.setMoveX(direction.x * moveIntensity);
        input.setMoveY(direction.y * moveIntensity);
        
        // Look towards power-up
        input.setWorldX(powerUpPos.x);
        input.setWorldY(powerUpPos.y);
    }
    
    /**
     * Move towards a workshop to craft power-ups.
     */
    private void moveTowardsWorkshop(AIPlayer aiPlayer, Workshop workshop, PlayerInput input, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 workshopPos = workshop.getPosition();
        double distance = myPos.distance(workshopPos);
        
        // Get workshop craft radius
        double craftRadius = 80.0; // Default craft radius
        
        if (distance > craftRadius) {
            // Move towards workshop
            Vector2 direction = workshopPos.copy().subtract(myPos);
            direction.normalize();
            
            // Apply hazard avoidance
            direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 100.0);
            
            double moveIntensity = 0.7;
            input.setMoveX(direction.x * moveIntensity);
            input.setMoveY(direction.y * moveIntensity);
        } else {
            // Within craft radius - stay here and let workshop craft
            // Move slowly around workshop to avoid being stationary
            double circleAngle = (System.currentTimeMillis() / 4000.0) * circleSpeedVariation + circleAngleOffset;
            circleAngle = circleAngle % (Math.PI * 2);
            
            // Apply radius variation per AI
            double adjustedRadius = craftRadius * 0.7 * circleRadiusVariation;
            
            Vector2 circleOffset = new Vector2(
                Math.cos(circleAngle) * adjustedRadius,
                Math.sin(circleAngle) * adjustedRadius
            );
            Vector2 targetPos = workshopPos.copy().add(circleOffset);
            Vector2 direction = targetPos.copy().subtract(myPos);
            
            if (direction.getMagnitude() > 10) {
                direction.normalize();
                
                // Apply hazard avoidance
                direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 80.0);
                
                input.setMoveX(direction.x * 0.4);
                input.setMoveY(direction.y * 0.4);
            }
        }
        
        // Look around for threats while at workshop
        input.setWorldX(workshopPos.x + Math.cos(System.currentTimeMillis() / 1000.0) * 100);
        input.setWorldY(workshopPos.y + Math.sin(System.currentTimeMillis() / 1000.0) * 100);
    }
    
    @Override
    public void onEnter(AIPlayer aiPlayer) {
        targetPowerUpId = -1;
        targetWorkshopId = -1;
        evaluationTime = 0;
    }
    
    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue if there are power-ups to collect or workshops to use
        return !gameEntities.getAllPowerUps().isEmpty() || !gameEntities.getAllWorkshops().isEmpty();
    }
    
    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        // No power-ups or workshops = no priority
        if (gameEntities.getAllPowerUps().isEmpty() && gameEntities.getAllWorkshops().isEmpty()) {
            return 0;
        }
        
        Vector2 myPos = aiPlayer.getPosition();
        double healthPercent = aiPlayer.getHealth() / 100.0;
        
        // Higher priority when health is low and health regen is available
        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            if (!powerUp.isActive()) {
                continue;
            }
            
            double distance = myPos.distance(powerUp.getPosition());
            if (distance > 300) {
                continue;
            }
            
            // Health regen is high priority when low health
            if (powerUp.getType() == PowerUp.PowerUpType.HEALTH_REGENERATION && healthPercent < 0.4) {
                return 80; // High priority
            }
            
            // Damage boost is high priority for aggressive personalities
            if (powerUp.getType() == PowerUp.PowerUpType.DAMAGE_BOOST && 
                aiPlayer.getPersonality().getAggressiveness() > 0.7) {
                return 70;
            }
            
            // Other power-ups nearby
            if (distance < 150) {
                return 60; // Moderate-high priority when very close
            }
        }
        
        // Base priority for power-up collection
        int basePriority = 40;
        
        // Increase priority when not in combat
        if (healthPercent > 0.7) {
            basePriority += 15; // Safe to collect power-ups
        }
        
        // Strategic personalities value power-ups more
        basePriority += (int) (aiPlayer.getPersonality().getStrategicThinking() * 10);
        
        return basePriority;
    }
    
    @Override
    public String getName() {
        return "PowerUp";
    }
}


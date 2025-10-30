package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggressive behavior focused on engaging enemy players and turrets in combat.
 * AI will seek out and attack nearby enemies including players and turrets.
 */
public class CombatBehavior implements AIBehavior {
    private int targetId = -1;
    private boolean targetIsPlayer = true; // Track if target is a player or turret
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

        // Check for environmental hazards - high priority
        double areaDanger = HazardAvoidance.getAreaDangerRating(aiPlayer.getPosition(), 150.0, gameEntities);
        if (areaDanger > 0.7) {
            // Very dangerous area - flee to safety immediately
            Vector2 safePos = HazardAvoidance.findNearestSafePosition(aiPlayer.getPosition(), 200.0, gameEntities);
            if (safePos != null) {
                Vector2 fleeDirection = safePos.copy().subtract(aiPlayer.getPosition());
                fleeDirection.normalize();
                input.setMoveX(fleeDirection.x);
                input.setMoveY(fleeDirection.y);
                return input; // Skip combat, just flee
            }
        }

        // Check for retreat conditions
        evaluateRetreatConditions(aiPlayer, deltaTime);

        // Find target (current or new)
        AITargetWrapper target = findOrMaintainTarget(aiPlayer, gameEntities);

        if (target != null) {
            timeSinceLastTarget = 0;
            lastTargetPosition = target.getPosition().copy();

            // Generate tactical movement and combat
            generateTacticalMovement(aiPlayer, target, input, deltaTime, gameEntities);
            generateCombatActions(aiPlayer, target, input, deltaTime);

        } else if (timeSinceLastTarget < combatTimeout) {
            // Pursue last known position
            pursueLastKnownPosition(aiPlayer, input, deltaTime);
            timeSinceLastTarget += deltaTime;
        } else {
            // No target, reset state
            targetId = -1;
            isRetreating = false;
            timeSinceLastTarget += deltaTime;
        }

        return input;
    }

    /**
     * Enhanced tactical movement based on weapon range, health, and tactical situation.
     */
    private void generateTacticalMovement(AIPlayer aiPlayer, AITargetWrapper target, PlayerInput input, double deltaTime, GameEntities gameEntities) {
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
        
        // Apply hazard avoidance to final movement direction
        moveDirection = HazardAvoidance.calculateSafeMovement(playerPos, moveDirection, gameEntities, 100.0);
        
        input.setMoveX(moveDirection.x * moveIntensity);
        input.setMoveY(moveDirection.y * moveIntensity);
    }

    /**
     * Enhanced combat actions including aiming, shooting, and reloading.
     */
    private void generateCombatActions(AIPlayer aiPlayer, AITargetWrapper target, PlayerInput input, double deltaTime) {
        Vector2 playerPos = aiPlayer.getPosition();
        Vector2 targetPos = target.getPosition();
        double distance = playerPos.distance(targetPos);

        // Check if using beam weapon (instant hit, no prediction needed)
        boolean isBeamWeapon = isBeamWeapon(aiPlayer);
        
        Vector2 aimPos;
        if (isBeamWeapon) {
            // Beam weapons are instant hit - aim directly at target
            aimPos = targetPos.copy();
            
            // Add slight inaccuracy based on personality
            double accuracy = aiPlayer.getPersonality().getAccuracy();
            double spread = (1.0 - accuracy) * 15;
            aimPos.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread);
        } else {
            // Projectile weapons - predict target movement
            Vector2 targetVelocity = target.getVelocity();
            double projectileSpeed = aiPlayer.getCurrentWeapon().getProjectileSpeed();
            double timeToTarget = distance / projectileSpeed;

            // Predict target movement with some uncertainty for realism
            aimPos = targetPos.copy().add(targetVelocity.copy().multiply(timeToTarget));

            // Add slight inaccuracy based on distance and personality
            double accuracy = aiPlayer.getPersonality().getAccuracy();
            double distanceInaccuracy = Math.max(0, distance - 200) * 0.05 * (1.0 - accuracy);
            double randomOffsetX = (Math.random() - 0.5) * distanceInaccuracy;
            double randomOffsetY = (Math.random() - 0.5) * distanceInaccuracy;

            aimPos.add(randomOffsetX, randomOffsetY);
        }

        // Set aim
        input.setWorldX(aimPos.x);
        input.setWorldY(aimPos.y);

        // Enhanced shooting decision
        boolean shouldShoot = shouldShootAtTarget(aiPlayer, target, distance);
        if (shouldShoot && aiPlayer.canShoot() && lastShotTime > 0.1) { // Minimum time between shots
            input.setLeft(true);
            lastShotTime = 0;
        }

        // Smart reloading with improved ammo management
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();
        double ammoPercent = (double) currentAmmo / magazineSize;

        boolean shouldReload = false;
        if (currentAmmo == 0) {
            shouldReload = true; // Must reload
        } else if (isRetreating && ammoPercent < 0.5) {
            shouldReload = true; // Reload while retreating if below 50%
        } else if (currentAmmo <= 3 && distance > 300) {
            shouldReload = true; // Reload when safe and very low ammo
        } else if (ammoPercent < 0.25 && distance > 250) {
            shouldReload = true; // Reload when safe and below 25%
        } else if (ammoPercent < 0.4 && !target.isActive()) {
            shouldReload = true; // Reload during lull in combat
        }

        // Don't reload if enemy is very close and we have some ammo
        if (distance < 150 && currentAmmo > magazineSize * 0.15) {
            shouldReload = false;
        }

        if (shouldReload && !aiPlayer.isReloading()) {
            input.setReload(true);
            // Don't shoot while reloading
            input.setLeft(false);
        }
        
        // Utility weapon usage during combat
        evaluateUtilityWeaponUsage(aiPlayer, target, input, distance);
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
        targetId = -1;
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
        AITargetWrapper nearestEnemy = findBestTarget(aiPlayer, gameEntities);
        return nearestEnemy != null;
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        AITargetWrapper nearestEnemy = findBestTarget(aiPlayer, gameEntities);
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
    private AITargetWrapper findOrMaintainTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        // First, try to maintain current target if valid
        if (targetId != -1) {
            AITargetWrapper currentTarget = findTargetById(gameEntities, targetId, targetIsPlayer);
            if (currentTarget != null && currentTarget.isActive() && !isTeammate(aiPlayer, currentTarget)) {
                double distance = aiPlayer.getPosition().distance(currentTarget.getPosition());
                if (distance <= 800) { // Increased persistence range
                    return currentTarget;
                }
            }
        }

        // Find new target
        AITargetWrapper newTarget = findBestTarget(aiPlayer, gameEntities);
        if (newTarget != null) {
            targetId = newTarget.getId();
            targetIsPlayer = newTarget.isPlayer();
        }
        return newTarget;
    }

    private AITargetWrapper findBestTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 playerPos = aiPlayer.getPosition();
        AITargetWrapper bestTarget = null;
        double bestScore = 0;

        // Create list of all potential targets (players and turrets)
        List<AITargetWrapper> allTargets = new ArrayList<>();
        
        // Add all enemy players
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() != aiPlayer.getId() && player.isActive() && !isTeammate(aiPlayer, AITargetWrapper.fromPlayer(player))) {
                allTargets.add(AITargetWrapper.fromPlayer(player));
            }
        }
        
        // Add all enemy turrets
        for (Turret turret : gameEntities.getAllTurrets()) {
            if (turret.isActive() && !isTeammate(aiPlayer, AITargetWrapper.fromTurret(turret))) {
                allTargets.add(AITargetWrapper.fromTurret(turret));
            }
        }

        for (AITargetWrapper target : allTargets) {
            double distance = playerPos.distance(target.getPosition());
            if (distance > 700) { // Slightly increased max engagement range
                continue;
            }

            // Enhanced scoring system
            double distanceScore = 1.0 - (distance / 700.0);
            double healthScore = 1.0 - (target.getHealth() / target.getMaxHealth());

            // Strong bonus for injured enemies (pursue wounded)
            double injuryBonus = 0;
            double healthPercent = target.getHealth() / target.getMaxHealth();
            if (healthPercent < 0.5) {
                injuryBonus = 0.4; // Aggressively pursue wounded enemies
            } else if (healthPercent < 0.75) {
                injuryBonus = 0.2;
            }

            // Continuity bonus for current target
            double continuityBonus = 0;
            if (targetId == target.getId()) {
                continuityBonus = 0.5; // Strong preference to maintain target
            }

            // Target type priority (players are generally higher priority than turrets)
            double typePriority = target.getTargetPriority();

            // Weapon effectiveness at this range
            double weaponEffectiveness = calculateWeaponEffectiveness(aiPlayer, distance);

            double totalScore = (distanceScore * 0.3) + (healthScore * 0.15) + injuryBonus +
                                continuityBonus + (typePriority * 0.2) + (weaponEffectiveness * 0.2);

            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestTarget = target;
            }
        }

        return bestTarget;
    }

    private AITargetWrapper findTargetById(GameEntities gameEntities, int targetId, boolean isPlayer) {
        if (isPlayer) {
            for (Player player : gameEntities.getAllPlayers()) {
                if (player.getId() == targetId) {
                    return AITargetWrapper.fromPlayer(player);
                }
            }
        } else {
            for (Turret turret : gameEntities.getAllTurrets()) {
                if (turret.getId() == targetId) {
                    return AITargetWrapper.fromTurret(turret);
                }
            }
        }
        return null;
    }
    
    private boolean isTeammate(AIPlayer aiPlayer, AITargetWrapper target) {
        // In FFA mode (team 0), check if it's the AI's own turret
        if (aiPlayer.getTeam() == 0) {
            // Don't attack your own turrets in FFA
            if (target.isTurret() && target.getOwnerId() == aiPlayer.getId()) {
                return true;
            }
            // Everyone else is an enemy in FFA
            return false;
        }
        
        // In team mode, check if they're on the same team
        return aiPlayer.getTeam() == target.getTeam();
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

    private boolean shouldShootAtTarget(AIPlayer aiPlayer, AITargetWrapper target, double distance) {
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
    
    /**
     * Evaluate whether to use utility weapon during combat.
     */
    private void evaluateUtilityWeaponUsage(AIPlayer aiPlayer, AITargetWrapper target, PlayerInput input, double distance) {
        // Don't use utility if on cooldown
        if (!aiPlayer.canUseUtility()) {
            return;
        }
        
        UtilityWeapon utility = aiPlayer.getUtilityWeapon();
        double utilityRange = utility.getRange();
        
        // Check if target is within utility range
        if (distance > utilityRange) {
            return;
        }
        
        boolean shouldUseUtility = false;
        double usageChance = 0.0;
        
        switch (utility.getCategory()) {
            case CROWD_CONTROL:
                // Use crowd control when enemy is close or when retreating
                if (distance < 150 || isRetreating) {
                    usageChance = 0.4;
                    if (isRetreating) usageChance += 0.3;
                }
                break;
                
            case DEFENSIVE:
                // Use defensive utilities when health is low or under pressure
                if (aiPlayer.getHealth() < 60 || isRetreating) {
                    usageChance = 0.5;
                    if (aiPlayer.getHealth() < 30) usageChance += 0.3;
                }
                break;
                
            case SUPPORT:
                // Use support utilities when health is low or in good position
                if (aiPlayer.getHealth() < 70 && distance > 200) {
                    usageChance = 0.3;
                }
                break;
                
            case TACTICAL:
                // Use tactical utilities for positioning advantages
                if (distance > 100 && distance < 300) {
                    usageChance = 0.2;
                    // Higher chance if we're being aggressive
                    if (aiPlayer.getPersonality().getAggressiveness() > 0.6) {
                        usageChance += 0.2;
                    }
                }
                break;
        }
        
        // Personality modifiers
        double personalityMultiplier = 1.0;
        
        // Aggressive personalities use utilities more often
        if (aiPlayer.getPersonality().getAggressiveness() > 0.7) {
            personalityMultiplier += 0.3;
        }
        
        // Patient personalities are more strategic with utility usage
        if (aiPlayer.getPersonality().getPatience() > 0.6) {
            personalityMultiplier += 0.2;
        }
        
        usageChance *= personalityMultiplier;
        
        // Random factor to make behavior less predictable
        shouldUseUtility = Math.random() < usageChance;
        
        if (shouldUseUtility) {
            input.setAltFire(true);
        }
    }

    /**
     * Check if the AI is using a beam weapon (instant hit).
     */
    private boolean isBeamWeapon(AIPlayer aiPlayer) {
        String weaponType = aiPlayer.getCurrentWeapon().getName();
        return weaponType.contains("Laser") || weaponType.contains("Plasma Beam") 
               || weaponType.contains("Railgun") || weaponType.contains("Rail Cannon")
               || weaponType.contains("Medic Beam");
    }
}

package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

/**
 * Behavior for Headquarters attack and defense gameplay.
 * AI will defend their own HQ and attack enemy HQs.
 */
public class HeadquartersBehavior implements AIBehavior {
    private enum HQRole {
        ATTACKER,  // Attack enemy HQs
        DEFENDER   // Defend own HQ
    }

    private HQRole currentRole = HQRole.DEFENDER;
    private int targetHQId = -1;
    private double roleEvaluationTime = 0;
    private static final double ROLE_EVALUATION_INTERVAL = 8.0;

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        roleEvaluationTime += deltaTime;
        if (roleEvaluationTime >= ROLE_EVALUATION_INTERVAL) {
            evaluateRole(aiPlayer, gameEntities);
            roleEvaluationTime = 0;
        }

        switch (currentRole) {
            case ATTACKER:
                executeAttackerBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
            case DEFENDER:
                executeDefenderBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
        }

        return input;
    }

    /**
     * Evaluate and assign the best role based on game state.
     */
    private void evaluateRole(AIPlayer aiPlayer, GameEntities gameEntities) {
        int myTeam = aiPlayer.getTeam();
        Vector2 myPos = aiPlayer.getPosition();

        // Check own HQ status
        Headquarters myHQ = getTeamHeadquarters(myTeam, gameEntities);
        if (myHQ != null && myHQ.isActive()) {
            double hqHealthPercent = myHQ.getHealth() / myHQ.getMaxHealth();
            
            // If HQ is under heavy attack, prioritize defense
            if (hqHealthPercent < 0.5) {
                currentRole = HQRole.DEFENDER;
                targetHQId = myHQ.getId();
                return;
            }

            // Check if enemies are near our HQ
            if (areEnemiesNearHQ(aiPlayer, myHQ, gameEntities, 300)) {
                currentRole = HQRole.DEFENDER;
                targetHQId = myHQ.getId();
                return;
            }
        }

        // Check how many teammates are defending
        int defendersCount = countDefendersNearHQ(aiPlayer, myHQ, gameEntities);
        
        // Personality-based role assignment
        double aggressiveness = aiPlayer.getPersonality().getAggressiveness();
        double teamwork = aiPlayer.getPersonality().getTeamwork();
        double strategicThinking = aiPlayer.getPersonality().getStrategicThinking();

        // Strategic AIs balance attack and defense
        if (strategicThinking > 0.7) {
            if (defendersCount < 2) {
                currentRole = HQRole.DEFENDER;
            } else {
                currentRole = HQRole.ATTACKER;
            }
        }
        // Aggressive AIs prefer attacking
        else if (aggressiveness > 0.6) {
            if (defendersCount < 1) {
                currentRole = HQRole.DEFENDER;
            } else {
                currentRole = HQRole.ATTACKER;
            }
        }
        // Team players and defensive AIs prefer defending
        else if (teamwork > 0.6 || aggressiveness < 0.4) {
            currentRole = HQRole.DEFENDER;
        }
        // Default: balance based on team composition
        else {
            if (defendersCount < 2) {
                currentRole = HQRole.DEFENDER;
            } else {
                currentRole = HQRole.ATTACKER;
            }
        }

        // Set target based on role
        if (currentRole == HQRole.DEFENDER && myHQ != null) {
            targetHQId = myHQ.getId();
        } else {
            // Find nearest enemy HQ
            Headquarters enemyHQ = findNearestEnemyHQ(aiPlayer, gameEntities);
            if (enemyHQ != null) {
                targetHQId = enemyHQ.getId();
            }
        }
    }

    /**
     * Attacker behavior: Assault enemy headquarters.
     */
    private void executeAttackerBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Headquarters targetHQ = gameEntities.getHeadquarters(targetHQId);
        
        // Validate target
        if (targetHQ == null || !targetHQ.isActive() || targetHQ.getTeamNumber() == aiPlayer.getTeam()) {
            targetHQ = findNearestEnemyHQ(aiPlayer, gameEntities);
            if (targetHQ != null) {
                targetHQId = targetHQ.getId();
            }
        }

        if (targetHQ == null) {
            // No enemy HQ to attack, switch to defense
            currentRole = HQRole.DEFENDER;
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 hqPos = targetHQ.getPosition();
        double distance = myPos.distance(hqPos);
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();

        // Move to optimal attack position
        if (distance > weaponRange * 0.7) {
            // Approach HQ with tactical movement
            Vector2 direction = hqPos.copy().subtract(myPos);
            direction.normalize();

            // Add evasive movement
            double evasion = Math.sin(System.currentTimeMillis() / 400.0) * 0.4;
            Vector2 perpendicular = new Vector2(-direction.y, direction.x);
            direction.add(perpendicular.multiply(evasion));
            direction.normalize();

            // Apply hazard avoidance
            direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 100.0);

            double moveIntensity = 0.8;
            input.setMoveX(direction.x * moveIntensity);
            input.setMoveY(direction.y * moveIntensity);
        } else if (distance < weaponRange * 0.4) {
            // Too close, back away
            Vector2 direction = myPos.copy().subtract(hqPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.6);
            input.setMoveY(direction.y * 0.6);
        } else {
            // Good position, strafe around HQ
            double strafeAngle = (System.currentTimeMillis() / 2500.0) % (Math.PI * 2);
            Vector2 strafeDir = new Vector2(
                Math.cos(strafeAngle),
                Math.sin(strafeAngle)
            );
            input.setMoveX(strafeDir.x * 0.5);
            input.setMoveY(strafeDir.y * 0.5);
        }

        // Aim at HQ
        input.setWorldX(hqPos.x);
        input.setWorldY(hqPos.y);

        // Shoot at HQ if in range and no immediate threats
        Player nearbyEnemy = findNearestEnemy(aiPlayer, gameEntities, 250);
        if (nearbyEnemy != null) {
            // Prioritize nearby enemies over HQ
            engageEnemy(aiPlayer, nearbyEnemy, input);
        } else if (distance < weaponRange && aiPlayer.canShoot()) {
            input.setLeft(true);
        }

        // Use utility against HQ or defenders
        if (aiPlayer.canUseUtility() && distance < 200) {
            input.setAltFire(true);
        }

        // Smart reload
        smartReload(aiPlayer, input, nearbyEnemy == null && distance > 300);
    }

    /**
     * Defender behavior: Protect own headquarters.
     */
    private void executeDefenderBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Headquarters myHQ = gameEntities.getHeadquarters(targetHQId);
        
        // Validate target
        if (myHQ == null || !myHQ.isActive() || myHQ.getTeamNumber() != aiPlayer.getTeam()) {
            myHQ = getTeamHeadquarters(aiPlayer.getTeam(), gameEntities);
            if (myHQ != null) {
                targetHQId = myHQ.getId();
            }
        }

        if (myHQ == null) {
            // No HQ to defend, switch to attack
            currentRole = HQRole.ATTACKER;
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 hqPos = myHQ.getPosition();
        double distance = myPos.distance(hqPos);

        // Maintain defensive perimeter around HQ
        double optimalDefenseRadius = 200.0;

        if (distance > optimalDefenseRadius + 100) {
            // Too far, move closer
            Vector2 direction = hqPos.copy().subtract(myPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.8);
            input.setMoveY(direction.y * 0.8);
        } else if (distance < optimalDefenseRadius - 50) {
            // Too close, back away
            Vector2 direction = myPos.copy().subtract(hqPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.5);
            input.setMoveY(direction.y * 0.5);
        } else {
            // Good position, patrol around HQ
            double patrolAngle = (System.currentTimeMillis() / 4000.0) % (Math.PI * 2);
            
            // Add personality variation to patrol pattern
            double personalityOffset = aiPlayer.getPersonality().getMobility() * Math.PI * 0.5;
            patrolAngle += personalityOffset;
            
            Vector2 patrolOffset = new Vector2(
                Math.cos(patrolAngle) * optimalDefenseRadius,
                Math.sin(patrolAngle) * optimalDefenseRadius
            );
            Vector2 patrolTarget = hqPos.copy().add(patrolOffset);
            Vector2 direction = patrolTarget.copy().subtract(myPos);
            
            if (direction.getMagnitude() > 20) {
                direction.normalize();
                
                // Apply hazard avoidance
                direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 80.0);
                
                input.setMoveX(direction.x * 0.6);
                input.setMoveY(direction.y * 0.6);
            }
        }

        // Prioritize enemies threatening HQ
        Player bestThreat = findBestThreatToHQ(aiPlayer, myHQ, gameEntities);
        if (bestThreat != null) {
            engageEnemy(aiPlayer, bestThreat, input);
            
            // Use utility on threats
            double threatDistance = myPos.distance(bestThreat.getPosition());
            if (aiPlayer.canUseUtility() && threatDistance < 200) {
                input.setAltFire(true);
            }
        } else {
            // No immediate threats, look for distant enemies
            Player distantEnemy = findNearestEnemy(aiPlayer, gameEntities, 500);
            if (distantEnemy != null) {
                engageEnemy(aiPlayer, distantEnemy, input);
            }
        }

        // Smart reload when safe
        smartReload(aiPlayer, input, bestThreat == null);
    }

    /**
     * Engage an enemy player with aiming and shooting.
     */
    private void engageEnemy(AIPlayer aiPlayer, Player enemy, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 enemyPos = enemy.getPosition();
        Vector2 enemyVel = enemy.getVelocity();
        double distance = myPos.distance(enemyPos);

        // Lead target
        double projectileSpeed = aiPlayer.getCurrentWeapon().getProjectileSpeed();
        double timeToTarget = distance / projectileSpeed;
        Vector2 predictedPos = enemyPos.copy().add(enemyVel.copy().multiply(timeToTarget));

        // Add accuracy variation
        double accuracy = aiPlayer.getPersonality().getAccuracy();
        double spread = (1.0 - accuracy) * 25;
        predictedPos.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread);

        input.setWorldX(predictedPos.x);
        input.setWorldY(predictedPos.y);

        // Shoot if in range
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();
        if (distance < weaponRange * 0.85 && aiPlayer.canShoot()) {
            input.setLeft(true);
        }
    }

    /**
     * Find the best threat to the HQ (closest enemy or one attacking HQ).
     */
    private Player findBestThreatToHQ(AIPlayer aiPlayer, Headquarters hq, GameEntities gameEntities) {
        Vector2 hqPos = hq.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        Player bestThreat = null;
        double bestScore = -1;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() == myTeam) {
                continue;
            }

            double distanceToHQ = player.getPosition().distance(hqPos);
            double distanceToMe = aiPlayer.getPosition().distance(player.getPosition());

            // Score based on threat level
            double score = 0;

            // Closer to HQ = higher threat
            if (distanceToHQ < 200) {
                score += 50;
            } else if (distanceToHQ < 400) {
                score += 30;
            } else {
                score += 10;
            }

            // Closer to me = easier to engage
            score += Math.max(0, (400 - distanceToMe) / 400) * 30;

            // Low health enemies are easier to eliminate
            double healthPercent = player.getHealth() / 100.0;
            score += (1.0 - healthPercent) * 20;

            if (score > bestScore) {
                bestScore = score;
                bestThreat = player;
            }
        }

        return bestThreat;
    }

    /**
     * Smart reload - only reload when safe.
     */
    private void smartReload(AIPlayer aiPlayer, PlayerInput input, boolean isSafe) {
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();

        if (currentAmmo == 0) {
            input.setReload(true);
        } else if (isSafe && currentAmmo < magazineSize * 0.3) {
            input.setReload(true);
        }
    }

    @Override
    public void onEnter(AIPlayer aiPlayer) {
        currentRole = HQRole.DEFENDER;
        targetHQId = -1;
        roleEvaluationTime = 0;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue as long as HQs exist
        return !gameEntities.getAllHeadquarters().isEmpty();
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        // No HQs in game = no priority
        if (gameEntities.getAllHeadquarters().isEmpty()) {
            return 0;
        }

        int myTeam = aiPlayer.getTeam();
        Headquarters myHQ = getTeamHeadquarters(myTeam, gameEntities);

        if (myHQ == null || !myHQ.isActive()) {
            return 0; // Our HQ is destroyed
        }

        // Base priority
        int priority = 65;

        // Higher priority if HQ is damaged
        double hqHealthPercent = myHQ.getHealth() / myHQ.getMaxHealth();
        if (hqHealthPercent < 0.3) {
            priority += 25; // Critical - HQ nearly destroyed
        } else if (hqHealthPercent < 0.6) {
            priority += 15; // Moderate damage
        }

        // Higher priority if enemies are near HQ
        if (areEnemiesNearHQ(aiPlayer, myHQ, gameEntities, 300)) {
            priority += 20;
        }

        // Personality modifiers
        double strategicThinking = aiPlayer.getPersonality().getStrategicThinking();
        double teamwork = aiPlayer.getPersonality().getTeamwork();
        
        priority += (int) (strategicThinking * 8);
        priority += (int) (teamwork * 7);

        return Math.min(100, priority);
    }

    @Override
    public String getName() {
        return "HQ (" + currentRole + ")";
    }

    // Helper methods

    private Headquarters getTeamHeadquarters(int team, GameEntities gameEntities) {
        for (Headquarters hq : gameEntities.getAllHeadquarters()) {
            if (hq.getTeamNumber() == team && hq.isActive()) {
                return hq;
            }
        }
        return null;
    }

    private Headquarters findNearestEnemyHQ(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        Headquarters nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Headquarters hq : gameEntities.getAllHeadquarters()) {
            if (hq.getTeamNumber() != myTeam && hq.isActive()) {
                double distance = myPos.distance(hq.getPosition());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = hq;
                }
            }
        }

        return nearest;
    }

    private boolean areEnemiesNearHQ(AIPlayer aiPlayer, Headquarters hq, GameEntities gameEntities, double radius) {
        if (hq == null) return false;
        
        Vector2 hqPos = hq.getPosition();
        int myTeam = aiPlayer.getTeam();

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getTeam() == myTeam || !player.isActive()) {
                continue;
            }

            double distance = hqPos.distance(player.getPosition());
            if (distance < radius) {
                return true;
            }
        }

        return false;
    }

    private int countDefendersNearHQ(AIPlayer aiPlayer, Headquarters hq, GameEntities gameEntities) {
        if (hq == null) return 0;
        
        int count = 0;
        Vector2 hqPos = hq.getPosition();
        int myTeam = aiPlayer.getTeam();

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() == myTeam) {
                double distance = player.getPosition().distance(hqPos);
                if (distance < 300) {
                    count++;
                }
            }
        }

        return count;
    }

    private Player findNearestEnemy(AIPlayer aiPlayer, GameEntities gameEntities, double maxRange) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        Player nearest = null;
        double nearestDistance = maxRange;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() != myTeam) {
                double distance = myPos.distance(player.getPosition());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
        }

        return nearest;
    }
}


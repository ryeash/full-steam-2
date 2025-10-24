package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.Map;

/**
 * Behavior for King of the Hill (KOTH) zone control gameplay.
 * AI will capture and hold zones to earn points for their team.
 */
public class KothBehavior implements AIBehavior {
    private int targetZoneId = -1;
    private double zoneEvaluationTime = 0;
    private static final double ZONE_EVALUATION_INTERVAL = 5.0; // Re-evaluate target zone every 5 seconds
    
    // Track time spent in each zone for persistence
    private Map<Integer, Double> zoneCommitmentTime = new HashMap<>();
    private static final double ZONE_COMMITMENT_DURATION = 8.0; // Stay committed to a zone for 8 seconds

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        zoneEvaluationTime += deltaTime;
        
        // Update commitment times
        for (Integer zoneId : zoneCommitmentTime.keySet()) {
            zoneCommitmentTime.put(zoneId, zoneCommitmentTime.get(zoneId) + deltaTime);
        }

        // Re-evaluate target zone periodically or if we don't have one
        if (targetZoneId == -1 || zoneEvaluationTime >= ZONE_EVALUATION_INTERVAL) {
            evaluateTargetZone(aiPlayer, gameEntities);
            zoneEvaluationTime = 0;
        }

        KothZone targetZone = gameEntities.getKothZone(targetZoneId);
        if (targetZone == null) {
            // No valid zone, pick a new one
            evaluateTargetZone(aiPlayer, gameEntities);
            targetZone = gameEntities.getKothZone(targetZoneId);
        }

        if (targetZone != null) {
            executeZoneControl(aiPlayer, targetZone, gameEntities, input, deltaTime);
        }

        return input;
    }

    /**
     * Evaluate and select the best zone to target based on strategic value.
     */
    private void evaluateTargetZone(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        KothZone bestZone = null;
        double bestScore = -1;

        for (KothZone zone : gameEntities.getAllKothZones()) {
            double score = evaluateZoneScore(aiPlayer, zone, gameEntities);
            
            // Bonus for zone commitment (stick with current zone unless much better option)
            if (zone.getId() == targetZoneId) {
                Double commitmentTime = zoneCommitmentTime.get(targetZoneId);
                if (commitmentTime != null && commitmentTime < ZONE_COMMITMENT_DURATION) {
                    score += 30; // Strong bonus to stay committed
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestZone = zone;
            }
        }

        if (bestZone != null) {
            if (targetZoneId != bestZone.getId()) {
                // Switching zones, reset commitment
                zoneCommitmentTime.clear();
                zoneCommitmentTime.put(bestZone.getId(), 0.0);
            }
            targetZoneId = bestZone.getId();
        }
    }

    /**
     * Score a zone based on strategic value.
     */
    private double evaluateZoneScore(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        double distance = myPos.distance(zone.getPosition());

        double score = 0;

        // Distance factor (closer is better)
        double maxDistance = 800;
        double distanceScore = Math.max(0, (maxDistance - distance) / maxDistance) * 40;
        score += distanceScore;

        // Zone state factor
        switch (zone.getState()) {
            case NEUTRAL:
                score += 30; // Neutral zones are good targets
                break;
            case CONTESTED:
                score += 40; // Contested zones need help
                break;
            case CONTROLLED:
                if (zone.getControllingTeam() == myTeam) {
                    score += 15; // Our zone, moderate priority to maintain
                } else {
                    score += 35; // Enemy zone, good to contest
                }
                break;
        }

        // Teammate presence (more teammates = less need for us)
        int teammatesInZone = countTeammatesInZone(aiPlayer, zone, gameEntities);
        int enemiesInZone = countEnemiesInZone(aiPlayer, zone, gameEntities);
        
        if (teammatesInZone == 0 && enemiesInZone > 0) {
            score += 25; // No teammates but enemies present - high priority
        } else if (teammatesInZone > 2) {
            score -= 20; // Already well defended, look elsewhere
        }

        // Enemy presence (more enemies = more challenging but important)
        if (enemiesInZone > 0) {
            score += enemiesInZone * 10; // Each enemy increases importance
        }

        // Personality factors
        double aggressiveness = aiPlayer.getPersonality().getAggressiveness();
        double strategicThinking = aiPlayer.getPersonality().getStrategicThinking();
        
        if (zone.getState() == KothZone.ZoneState.CONTESTED) {
            score += aggressiveness * 15; // Aggressive AIs prefer contested zones
        }
        
        if (zone.getControllingTeam() != myTeam && zone.getState() == KothZone.ZoneState.CONTROLLED) {
            score += strategicThinking * 10; // Strategic AIs target enemy-controlled zones
        }

        return score;
    }

    /**
     * Execute zone control behavior - move to zone and hold it.
     */
    private void executeZoneControl(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 zonePos = zone.getPosition();
        double distance = myPos.distance(zonePos);
        double zoneRadius = 80.0; // KOTH zone radius

        if (distance > zoneRadius) {
            // Move towards zone
            moveTowardsZone(aiPlayer, zone, input);
        } else {
            // Inside zone - hold position and fight
            holdZonePosition(aiPlayer, zone, gameEntities, input, deltaTime);
        }

        // Always engage enemies
        engageEnemiesInZone(aiPlayer, zone, gameEntities, input);

        // Smart reload
        smartReload(aiPlayer, input, distance < zoneRadius && !areEnemiesNearby(aiPlayer, gameEntities, 200));
    }

    /**
     * Move towards the target zone with tactical movement.
     */
    private void moveTowardsZone(AIPlayer aiPlayer, KothZone zone, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 zonePos = zone.getPosition();
        
        Vector2 direction = zonePos.copy().subtract(myPos);
        double distance = direction.getMagnitude();
        direction.normalize();

        // Add tactical weaving when approaching contested zones
        if (zone.getState() == KothZone.ZoneState.CONTESTED) {
            double weave = Math.sin(System.currentTimeMillis() / 400.0) * 0.3;
            Vector2 perpendicular = new Vector2(-direction.y, direction.x);
            direction.add(perpendicular.multiply(weave));
            direction.normalize();
        }

        double moveIntensity = 0.8 + (aiPlayer.getPersonality().getMobility() * 0.2);
        input.setMoveX(direction.x * moveIntensity);
        input.setMoveY(direction.y * moveIntensity);
    }

    /**
     * Hold position within zone while fighting enemies.
     */
    private void holdZonePosition(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 zonePos = zone.getPosition();
        double distanceFromCenter = myPos.distance(zonePos);
        double zoneRadius = 80.0;

        // Stay within zone but move around for harder target
        if (distanceFromCenter > zoneRadius * 0.7) {
            // Moving towards edge, redirect towards center
            Vector2 toCenter = zonePos.copy().subtract(myPos);
            toCenter.normalize();
            input.setMoveX(toCenter.x * 0.5);
            input.setMoveY(toCenter.y * 0.5);
        } else {
            // Circle strafe within zone
            double strafeAngle = (System.currentTimeMillis() / 2000.0) % (Math.PI * 2);
            Vector2 strafeDirection = new Vector2(
                Math.cos(strafeAngle),
                Math.sin(strafeAngle)
            );
            
            // Adjust strafe to stay in zone
            Vector2 futurePos = myPos.copy().add(strafeDirection.copy().multiply(20));
            if (futurePos.distance(zonePos) > zoneRadius * 0.8) {
                // Would go too far, redirect
                strafeDirection = zonePos.copy().subtract(myPos);
                strafeDirection.normalize();
            }

            double moveIntensity = 0.6 * aiPlayer.getPersonality().getMobility();
            input.setMoveX(strafeDirection.x * moveIntensity);
            input.setMoveY(strafeDirection.y * moveIntensity);
        }
    }

    /**
     * Engage enemies in or near the zone.
     */
    private void engageEnemiesInZone(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 zonePos = zone.getPosition();
        
        Player bestTarget = null;
        double bestScore = -1;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (aiPlayer.isTeammate(player)) {
                continue;
            }

            double distance = myPos.distance(player.getPosition());
            double distanceToZone = player.getPosition().distance(zonePos);

            // Prioritize enemies in or near zone
            double score = 0;
            
            if (distanceToZone < 80) {
                score += 50; // In zone - highest priority
            } else if (distanceToZone < 200) {
                score += 30; // Near zone - high priority
            } else {
                score += 10; // Far from zone - low priority
            }

            // Closer enemies are easier to hit
            score += Math.max(0, (500 - distance) / 500) * 30;

            // Prioritize low health enemies
            double healthPercent = player.getHealth() / 100.0;
            score += (1.0 - healthPercent) * 20;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = player;
            }
        }

        if (bestTarget != null) {
            // Aim and shoot at target
            Vector2 targetPos = bestTarget.getPosition();
            Vector2 targetVel = bestTarget.getVelocity();
            double distance = myPos.distance(targetPos);

            // Lead target
            double projectileSpeed = aiPlayer.getCurrentWeapon().getProjectileSpeed();
            double timeToTarget = distance / projectileSpeed;
            Vector2 predictedPos = targetPos.copy().add(targetVel.copy().multiply(timeToTarget));

            // Add accuracy variation
            double accuracy = aiPlayer.getPersonality().getAccuracy();
            double spread = (1.0 - accuracy) * 30;
            predictedPos.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread);

            input.setWorldX(predictedPos.x);
            input.setWorldY(predictedPos.y);

            // Shoot if in range
            double weaponRange = aiPlayer.getCurrentWeapon().getRange();
            if (distance < weaponRange * 0.85 && aiPlayer.canShoot()) {
                input.setLeft(true);
            }

            // Use utility on clustered enemies
            if (aiPlayer.canUseUtility() && countEnemiesInZone(aiPlayer, zone, gameEntities) >= 2) {
                input.setAltFire(true);
            }
        }
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
        targetZoneId = -1;
        zoneEvaluationTime = 0;
        zoneCommitmentTime.clear();
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue as long as KOTH zones exist
        return !gameEntities.getAllKothZones().isEmpty();
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        // No zones in game = no priority
        if (gameEntities.getAllKothZones().isEmpty()) {
            return 0;
        }

        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();

        // Find nearest zone
        KothZone nearestZone = null;
        double nearestDistance = Double.MAX_VALUE;

        for (KothZone zone : gameEntities.getAllKothZones()) {
            double distance = myPos.distance(zone.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestZone = zone;
            }
        }

        if (nearestZone == null) {
            return 0;
        }

        // Base priority
        int priority = 70;

        // Higher priority if we're already in a zone
        if (nearestDistance < 80) {
            priority += 15;
        }

        // Higher priority if zone is contested or enemy-controlled
        if (nearestZone.getState() == KothZone.ZoneState.CONTESTED) {
            priority += 10;
        } else if (nearestZone.getState() == KothZone.ZoneState.CONTROLLED 
                   && nearestZone.getControllingTeam() != myTeam) {
            priority += 8;
        }

        // Personality modifiers
        double strategicThinking = aiPlayer.getPersonality().getStrategicThinking();
        double teamwork = aiPlayer.getPersonality().getTeamwork();
        
        priority += (int) (strategicThinking * 10);
        priority += (int) (teamwork * 5);

        return Math.min(100, priority);
    }

    @Override
    public String getName() {
        return "KOTH";
    }

    // Helper methods

    private int countTeammatesInZone(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities) {
        int count = 0;
        int myTeam = aiPlayer.getTeam();
        Vector2 zonePos = zone.getPosition();
        double zoneRadius = 80.0;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() == myTeam) {
                double distance = player.getPosition().distance(zonePos);
                if (distance < zoneRadius) {
                    count++;
                }
            }
        }

        return count;
    }

    private int countEnemiesInZone(AIPlayer aiPlayer, KothZone zone, GameEntities gameEntities) {
        int count = 0;
        int myTeam = aiPlayer.getTeam();
        Vector2 zonePos = zone.getPosition();
        double zoneRadius = 80.0;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() != myTeam) {
                double distance = player.getPosition().distance(zonePos);
                if (distance < zoneRadius) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean areEnemiesNearby(AIPlayer aiPlayer, GameEntities gameEntities, double radius) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (player.getTeam() != myTeam) {
                double distance = myPos.distance(player.getPosition());
                if (distance < radius) {
                    return true;
                }
            }
        }

        return false;
    }
}


package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

/**
 * Behavior for Oddball gameplay.
 * AI will try to grab the oddball, hold it to score points, and hunt down the ball carrier.
 */
public class OddballBehavior implements AIBehavior {
    private enum OddballRole {
        CARRIER,    // Currently holding the ball - evade and survive
        HUNTER,     // Chase the ball carrier
        GRABBER     // Go get the free ball
    }

    private OddballRole currentRole = OddballRole.GRABBER;
    private double roleChangeTime = 0;
    private static final double ROLE_CHANGE_INTERVAL = 2.0; // Re-evaluate frequently
    
    // For stable random movement when carrying ball
    private double randomMoveAngle = Math.random() * Math.PI * 2;
    private double randomMoveChangeTime = 0;
    private static final double RANDOM_MOVE_CHANGE_INTERVAL = 3.0; // Change direction every 3 seconds

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        roleChangeTime += deltaTime;
        if (roleChangeTime >= ROLE_CHANGE_INTERVAL) {
            evaluateRole(aiPlayer, gameEntities);
            roleChangeTime = 0;
        }

        switch (currentRole) {
            case CARRIER:
                executeCarrierBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
            case HUNTER:
                executeHunterBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
            case GRABBER:
                executeGrabberBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
        }

        return input;
    }

    /**
     * Determine the AI's role based on oddball state.
     */
    private void evaluateRole(AIPlayer aiPlayer, GameEntities gameEntities) {
        Flag oddball = findOddball(gameEntities);
        if (oddball == null) {
            currentRole = OddballRole.GRABBER;
            return;
        }

        // If we're carrying the ball, we're the carrier
        if (oddball.isCarried() && oddball.getCarriedByPlayerId() == aiPlayer.getId()) {
            currentRole = OddballRole.CARRIER;
            return;
        }

        // If ball is free, try to grab it
        if (!oddball.isCarried()) {
            currentRole = OddballRole.GRABBER;
            return;
        }

        // Someone else has the ball - hunt them
        currentRole = OddballRole.HUNTER;
    }

    /**
     * Carrier behavior - evade enemies and survive to score points.
     */
    private void executeCarrierBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        
        // Find nearest enemy
        Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
        
        if (nearestEnemy != null && nearestEnemy.isActive()) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            double distanceToEnemy = myPos.distance(enemyPos);
            
            // EVADE - run away from enemies
            Vector2 awayFromEnemy = myPos.difference(enemyPos);
            awayFromEnemy.normalize();
            
            // Apply hazard avoidance (critical when carrying ball!)
            awayFromEnemy = HazardAvoidance.calculateSafeMovement(myPos, awayFromEnemy, gameEntities, 120.0);
            
            // Move away from enemy
            input.setMoveX(awayFromEnemy.x);
            input.setMoveY(awayFromEnemy.y);
            
            // Look at enemy to track them
            input.setWorldX(enemyPos.x);
            input.setWorldY(enemyPos.y);
            
            // Can't shoot while carrying oddball, so just evade
            // The ball carrier status effect prevents shooting anyway
        } else {
            // No immediate threat - move toward center or safe area
            Vector2 centerPos = new Vector2(0, 0);
            Vector2 toCenter = centerPos.difference(myPos);
            double distanceToCenter = toCenter.getMagnitude();
            
            if (distanceToCenter > 100) {
                toCenter.normalize();
                input.setMoveX(toCenter.x * 0.5); // Move slowly toward center
                input.setMoveY(toCenter.y * 0.5);
            } else {
                // Near center - move in a stable random direction to avoid being stationary
                randomMoveChangeTime += deltaTime;
                if (randomMoveChangeTime >= RANDOM_MOVE_CHANGE_INTERVAL) {
                    randomMoveAngle = Math.random() * Math.PI * 2;
                    randomMoveChangeTime = 0;
                }
                input.setMoveX(Math.cos(randomMoveAngle) * 0.5);
                input.setMoveY(Math.sin(randomMoveAngle) * 0.5);
            }
        }
    }

    /**
     * Hunter behavior - chase and kill the ball carrier.
     */
    private void executeHunterBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Flag oddball = findOddball(gameEntities);
        if (oddball == null || !oddball.isCarried()) {
            // Ball is free now, switch to grabber
            currentRole = OddballRole.GRABBER;
            return;
        }

        // Find the ball carrier
        Player carrier = gameEntities.getPlayer(oddball.getCarriedByPlayerId());
        if (carrier == null || !carrier.isActive()) {
            currentRole = OddballRole.GRABBER;
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 carrierPos = carrier.getPosition();
        double distanceToCarrier = myPos.distance(carrierPos);

        // Move toward carrier
        Vector2 toCarrier = carrierPos.difference(myPos);
        toCarrier.normalize();
        
        // Apply hazard avoidance
        toCarrier = HazardAvoidance.calculateSafeMovement(myPos, toCarrier, gameEntities, 100.0);
        
        input.setMoveX(toCarrier.x);
        input.setMoveY(toCarrier.y);

        // Aim at carrier
        input.setWorldX(carrierPos.x);
        input.setWorldY(carrierPos.y);

        // Shoot if in range and have line of sight
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();
        if (distanceToCarrier < weaponRange * 0.9) {
            input.setLeft(true); // Fire!
        }

        // Reload if needed
        if (aiPlayer.getCurrentWeapon().getCurrentAmmo() < aiPlayer.getCurrentWeapon().getMagazineSize() * 0.3) {
            input.setReload(true);
        }
    }

    /**
     * Grabber behavior - rush to get the free oddball.
     */
    private void executeGrabberBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Flag oddball = findOddball(gameEntities);
        if (oddball == null) {
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 oddballPos = oddball.getPosition();
        double distanceToOddball = myPos.distance(oddballPos);

        // Rush toward oddball
        Vector2 toOddball = oddballPos.difference(myPos);
        toOddball.normalize();
        
        // Apply hazard avoidance
        toOddball = HazardAvoidance.calculateSafeMovement(myPos, toOddball, gameEntities, 100.0);
        
        input.setMoveX(toOddball.x);
        input.setMoveY(toOddball.y);

        // Look toward oddball
        input.setWorldX(oddballPos.x);
        input.setWorldY(oddballPos.y);

        // Shoot at enemies who might contest the ball
        Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);
        if (nearestEnemy != null && nearestEnemy.isActive()) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            double distanceToEnemy = myPos.distance(enemyPos);
            double enemyDistanceToOddball = enemyPos.distance(oddballPos);

            // If enemy is closer to ball or very close to us, shoot them
            if (enemyDistanceToOddball < distanceToOddball || distanceToEnemy < 150) {
                input.setWorldX(enemyPos.x);
                input.setWorldY(enemyPos.y);
                
                double weaponRange = aiPlayer.getCurrentWeapon().getRange();
                if (distanceToEnemy < weaponRange * 0.9) {
                    input.setLeft(true);
                }
            }
        }
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        Flag oddball = findOddball(gameEntities);
        
        // No oddball = no priority
        if (oddball == null) {
            return 0;
        }

        // VERY HIGH priority if we're carrying the ball
        if (oddball.isCarried() && oddball.getCarriedByPlayerId() == aiPlayer.getId()) {
            return 100; // Highest priority - we're scoring!
        }

        // High priority if ball is free and nearby
        if (!oddball.isCarried()) {
            double distance = aiPlayer.getPosition().distance(oddball.getPosition());
            if (distance < 300) {
                return 85; // Very high - go get it!
            }
            return 70; // Still high priority
        }

        // Medium-high priority to hunt the carrier
        return 75;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        Flag oddball = findOddball(gameEntities);
        
        // Continue if oddball exists
        if (oddball == null) {
            return false;
        }

        // Always continue if we're the carrier
        if (oddball.isCarried() && oddball.getCarriedByPlayerId() == aiPlayer.getId()) {
            return true;
        }

        // Continue if ball is nearby or we're hunting
        return true;
    }

    @Override
    public String getName() {
        return "Oddball";
    }

    /**
     * Find the oddball in the game.
     */
    private Flag findOddball(GameEntities gameEntities) {
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isOddball()) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Find the nearest enemy player.
     */
    private Player findNearestEnemy(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() != aiPlayer.getId() && player.isActive() && player.getTeam() != myTeam) {
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


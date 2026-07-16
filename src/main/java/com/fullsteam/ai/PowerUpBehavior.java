package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.PowerUp;
import com.fullsteam.physics.PowerUpType;
import org.dyn4j.geometry.Vector2;

/**
 * Behavior for seeking and collecting power-ups on the field.
 */
public class PowerUpBehavior implements AIBehavior {
    private int targetPowerUpId = -1;
    private double evaluationTime = 0;
    private static final double EVALUATION_INTERVAL = 3.0;

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        evaluationTime += deltaTime;
        if (evaluationTime >= EVALUATION_INTERVAL) {
            evaluateTargets(aiPlayer, gameEntities);
            evaluationTime = 0;
        }

        PowerUp targetPowerUp = findTargetPowerUp(aiPlayer, gameEntities);
        if (targetPowerUp != null) {
            moveTowardsPowerUp(aiPlayer, targetPowerUp, input, gameEntities);
            return input;
        }

        input.setMoveX(0.2);
        input.setMoveY(0.2);

        return input;
    }

    private void evaluateTargets(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        double healthPercent = aiPlayer.healthPercent();

        PowerUp bestPowerUp = null;
        double bestPowerUpScore = -1;

        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            if (!powerUp.isActive() || !powerUp.canBeCollectedBy(aiPlayer)) {
                continue;
            }

            double distance = myPos.distance(powerUp.getPosition());
            if (distance > 400) {
                continue;
            }

            double score = evaluatePowerUpValue(aiPlayer, powerUp.getType(), healthPercent);
            score *= (1.0 - (distance / 400.0));

            if (score > bestPowerUpScore) {
                bestPowerUpScore = score;
                bestPowerUp = powerUp;
            }
        }

        targetPowerUpId = bestPowerUp != null ? bestPowerUp.getId() : -1;
    }

    private double evaluatePowerUpValue(AIPlayer aiPlayer, PowerUpType type, double healthPercent) {
        return switch (type) {
            case HEALTH_REGENERATION -> {
                if (healthPercent < 0.3) yield 1.0;
                if (healthPercent < 0.6) yield 0.7;
                yield 0.3;
            }
            case DAMAGE_RESISTANCE -> {
                if (healthPercent < 0.5) yield 0.9;
                yield 0.5;
            }
            case SPEED_BOOST -> 0.6 + (aiPlayer.getPersonality().getMobility() * 0.3);
            case DAMAGE_BOOST -> 0.6 + (aiPlayer.getPersonality().getAggressiveness() * 0.4);
            case BERSERKER_MODE -> {
                if (aiPlayer.getPersonality().getAggressiveness() > 0.7 && healthPercent > 0.6) {
                    yield 0.8;
                }
                yield 0.3;
            }
            case INFINITE_AMMO -> 0.7 + (aiPlayer.getPersonality().getAggressiveness() * 0.2);
        };
    }

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

    private void moveTowardsPowerUp(AIPlayer aiPlayer, PowerUp powerUp, PlayerInput input, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 powerUpPos = powerUp.getPosition();

        Vector2 direction = powerUpPos.copy().subtract(myPos);
        direction.normalize();

        direction = HazardAvoidance.calculateSafeMovement(myPos, direction, gameEntities, 100.0);

        double moveIntensity = 0.9;
        input.setMoveX(direction.x * moveIntensity);
        input.setMoveY(direction.y * moveIntensity);

        input.setWorldX(powerUpPos.x);
        input.setWorldY(powerUpPos.y);
    }

    @Override
    public void onEnter(AIPlayer aiPlayer) {
        targetPowerUpId = -1;
        evaluationTime = 0;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        return !gameEntities.getAllPowerUps().isEmpty();
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        if (gameEntities.getAllPowerUps().isEmpty()) {
            return 0;
        }

        Vector2 myPos = aiPlayer.getPosition();
        double healthPercent = aiPlayer.healthPercent();

        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            if (!powerUp.isActive()) {
                continue;
            }

            double distance = myPos.distance(powerUp.getPosition());
            if (distance > 300) {
                continue;
            }

            if (powerUp.getType() == PowerUpType.HEALTH_REGENERATION && healthPercent < 0.4) {
                return 80;
            }

            if (powerUp.getType() == PowerUpType.DAMAGE_BOOST &&
                    aiPlayer.getPersonality().getAggressiveness() > 0.7) {
                return 70;
            }

            if (distance < 150) {
                return 60;
            }
        }

        int basePriority = 40;
        if (healthPercent > 0.7) {
            basePriority += 15;
        }
        basePriority += (int) (aiPlayer.getPersonality().getStrategicThinking() * 10);

        return basePriority;
    }

    @Override
    public String getName() {
        return "PowerUp";
    }
}

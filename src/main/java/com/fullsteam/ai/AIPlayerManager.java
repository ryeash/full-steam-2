package com.fullsteam.ai;

import com.fullsteam.Config;
import com.fullsteam.RandomNames;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Central manager for all AI players in a game.
 * Coordinates AI decision-making, behavior switching, and input generation.
 */
@Getter
public class AIPlayerManager {
    private static final Logger log = LoggerFactory.getLogger(AIPlayerManager.class);

    // How far ahead of an obstacle's surface the AI starts steering around it.
    private static final double OBSTACLE_LOOK_AHEAD = 70.0;

    private final Map<Integer, AIPlayer> aiPlayers = new ConcurrentSkipListMap<>();
    private final Map<Integer, List<AIBehavior>> availableBehaviors = new ConcurrentSkipListMap<>();
    private final Map<Integer, PlayerInput> generatedInputs = new ConcurrentSkipListMap<>();

    // Factories for the behaviors each AI can choose between. Each AI gets its own
    // fresh instances so behavior state (targets, timers, etc.) is independent.
    private static final List<Supplier<AIBehavior>> BEHAVIOR_FACTORIES = List.of(
            IdleBehavior::new,
            CombatBehavior::new,
            FlagBehavior::new,
            KothBehavior::new,
            HeadquartersBehavior::new,
            OddballBehavior::new,
            VipBehavior::new
    );

    private final GameConfig gameConfig;

    public AIPlayerManager(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Add an AI player to be managed by this system.
     */
    public void addAIPlayer(AIPlayer aiPlayer) {
        aiPlayers.put(aiPlayer.getId(), aiPlayer);
        List<AIBehavior> behaviors = new ArrayList<>();
        for (Supplier<AIBehavior> factory : BEHAVIOR_FACTORIES) {
            behaviors.add(factory.get());
        }
        availableBehaviors.put(aiPlayer.getId(), behaviors);
        log.debug("Added AI player {} ({}) with personality type: {}",
                aiPlayer.getId(), aiPlayer.getPlayerName(), aiPlayer.getPersonality().getPersonalityType());
    }

    /**
     * Remove an AI player from management.
     */
    public void removeAIPlayer(int playerId) {
        aiPlayers.remove(playerId);
        availableBehaviors.remove(playerId);
        generatedInputs.remove(playerId);

        log.debug("Removed AI player {}", playerId);
    }

    /**
     * Update all AI players and generate their inputs.
     */
    public void update(GameEntities gameEntities, double deltaTime) {
        // Update all AI players
        for (AIPlayer aiPlayer : aiPlayers.values()) {
            if (!aiPlayer.isActive()) {
                // Dead players are inactive - this is normal
                continue;
            }

            // Update AI memory with observations
            updateAIMemory(aiPlayer, gameEntities);

            // Check if AI needs to make a new decision
            if (aiPlayer.needsNewDecision()) {
                updateBehavior(aiPlayer, gameEntities);
                aiPlayer.resetDecisionTimer();
            }

            // Generate input for this AI player
            PlayerInput input = generatePlayerInput(aiPlayer, gameEntities, deltaTime);
            if (input != null) {
                // If stuck, override movement with an escape direction
                if (aiPlayer.isStuck()) {
                    applyUnstickMovement(aiPlayer, input);
                }

                // Steer around solid map obstacles. Applied here (after behavior and
                // unstick logic) so every movement path benefits, then smoothed.
                applyObstacleAvoidance(aiPlayer, input, gameEntities);

                // Apply movement smoothing for continuous motion
                aiPlayer.smoothMovement(input);
                generatedInputs.put(aiPlayer.getId(), input);
            }
        }
    }

    /**
     * Override movement input to escape when stuck against a wall or obstacle.
     * Picks a direction roughly opposite to the current (failed) movement,
     * with some randomization to avoid oscillating between two stuck states.
     */
    private void applyUnstickMovement(AIPlayer aiPlayer, PlayerInput input) {
        Vector2 stuckDirection = aiPlayer.getCurrentMovementDirection();

        Vector2 escapeDirection;
        if (stuckDirection.getMagnitude() > 0.05) {
            // Move roughly opposite to the stuck direction with a random offset
            // to avoid just hitting the same wall from a different angle
            double stuckAngle = Math.atan2(stuckDirection.y, stuckDirection.x);
            double offsetAngle = stuckAngle + Math.PI + (ThreadLocalRandom.current().nextDouble() - 0.5) * Math.PI * 0.8;
            escapeDirection = new Vector2(Math.cos(offsetAngle), Math.sin(offsetAngle));
        } else {
            // No clear stuck direction, pick random
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            escapeDirection = new Vector2(Math.cos(angle), Math.sin(angle));
        }

        input.setMoveX(escapeDirection.x);
        input.setMoveY(escapeDirection.y);
    }

    /**
     * Steer the AI's movement around nearby physical obstacles while preserving the
     * intended movement intensity (speed) that the behavior encoded in the vector length.
     */
    private void applyObstacleAvoidance(AIPlayer aiPlayer, PlayerInput input, GameEntities gameEntities) {
        Vector2 desired = new Vector2(input.getMoveX(), input.getMoveY());
        double intensity = desired.getMagnitude();
        if (intensity < 0.01) {
            return; // not trying to move, nothing to steer around
        }

        Vector2 steered = ObstacleAvoidance.steer(
                aiPlayer.getPosition(), desired, gameEntities,
                OBSTACLE_LOOK_AHEAD, Config.PLAYER_RADIUS);

        input.setMoveX(steered.x * intensity);
        input.setMoveY(steered.y * intensity);
    }

    /**
     * Get all generated inputs for all AI players.
     */
    public Map<Integer, PlayerInput> getAllPlayerInputs() {
        return new HashMap<>(generatedInputs);
    }

    /**
     * Check if a player is an AI player.
     */
    public boolean isAIPlayer(int playerId) {
        return aiPlayers.containsKey(playerId);
    }

    /**
     * Create an AI player with a name whose hash deterministically defines their personality and weapons.
     */
    public static AIPlayer createAIPlayerWithName(int id, String name, AIPersonality.Type personalityType, double x, double y, int team, double maxHealth) {
        AIPersonality personality = AIPersonality.createForType(personalityType);
        AIPlayer aiPlayer = new AIPlayer(id, name, x, y, personality, team, maxHealth);

        // Assign weapons based on personality
        WeaponConfig weapon = AIWeaponSelector.selectWeaponForPersonality(personality);
        UtilityWeapon utilityWeapon = AIWeaponSelector.selectUtilityWeaponForPersonality(personality);
        aiPlayer.applyWeaponConfig(weapon, utilityWeapon);
        log.debug("Assigned weapons to AI player {} ({} - {}): Primary={}, Utility={}",
                aiPlayer.getId(), name, personality.getPersonalityType(),
                weapon.getType(), utilityWeapon.getDisplayName());
        return aiPlayer;
    }

    private void updateAIMemory(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Update memory with observations of other players
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() != aiPlayer.getId() && player.isActive() && !player.isVisionObscured()) {
                // AI can "see" players within a certain range
                double distance = aiPlayer.getPosition().distance(player.getPosition());
                if (distance < 400) { // Sight range
                    aiPlayer.getMemory().observePlayer(player);
                }
            }
        }
    }

    private void updateBehavior(AIPlayer aiPlayer, GameEntities gameEntities) {
        List<AIBehavior> behaviors = availableBehaviors.get(aiPlayer.getId());
        if (behaviors == null || behaviors.isEmpty()) {
            return;
        }

        AIBehavior currentBehavior = aiPlayer.getCurrentBehavior();

        // Check if current behavior should continue with a bias to keep current behavior
        if (currentBehavior != null && currentBehavior.shouldContinue(aiPlayer, gameEntities)) {
            // Add some hysteresis - current behavior gets a priority bonus
            int currentPriority = currentBehavior.getPriority(aiPlayer, gameEntities) + 15; // Bonus for staying

            // Check if any other behavior has significantly higher priority
            int bestOtherPriority = -1;
            AIBehavior bestOtherBehavior = null;

            for (AIBehavior behavior : behaviors) {
                if (behavior != currentBehavior) {
                    int priority = behavior.getPriority(aiPlayer, gameEntities);
                    if (priority > bestOtherPriority) {
                        bestOtherPriority = priority;
                        bestOtherBehavior = behavior;
                    }
                }
            }

            // Only switch if the other behavior is significantly better
            if (bestOtherPriority > currentPriority + 10) {
                aiPlayer.setCurrentBehavior(bestOtherBehavior);
            }
            return;
        }

        // Find the best behavior based on priorities
        AIBehavior bestBehavior = null;
        int highestPriority = -1;

        for (AIBehavior behavior : behaviors) {
            int priority = behavior.getPriority(aiPlayer, gameEntities);
            if (priority > highestPriority) {
                highestPriority = priority;
                bestBehavior = behavior;
            }
        }

        // Switch to new behavior if it's different from current
        if (bestBehavior != null && bestBehavior != currentBehavior) {
            aiPlayer.setCurrentBehavior(bestBehavior);
            log.debug("AI player {} switched to {} behavior (priority: {})",
                    aiPlayer.getId(), bestBehavior.getName(), highestPriority);
        }
    }

    private PlayerInput generatePlayerInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        AIBehavior currentBehavior = aiPlayer.getCurrentBehavior();
        if (currentBehavior == null) {
            return new PlayerInput(); // Empty input if no behavior
        }

        PlayerInput input = currentBehavior.generateInput(aiPlayer, gameEntities, deltaTime);

        // Apply personality modifiers to the input
        applyPersonalityModifiers(aiPlayer, input);

        return input;
    }

    private void applyPersonalityModifiers(AIPlayer aiPlayer, PlayerInput input) {
        AIPersonality personality = aiPlayer.getPersonality();

        // Modify movement based on mobility trait
        if (personality.getMobility() < 0.3) {
            // Low mobility - reduce movement
            input.setMoveX(input.getMoveX() * 0.5);
            input.setMoveY(input.getMoveY() * 0.5);
        }

        // Modify shooting based on patience
        if (input.isLeft() && personality.getPatience() > 0.7) {
            // Patient personalities wait for better shots
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                input.setLeft(false);
            }
        }

        // Force reload if completely out of ammo - safety net
        if (aiPlayer.getCurrentWeapon().getCurrentAmmo() == 0 && !aiPlayer.isReloading()) {
            input.setReload(true);
            // Don't try to shoot when out of ammo
            input.setLeft(false);
            input.setAltFire(false);
        }

        // Apply reaction speed delays (not implemented in this simple version)
        // Could add input delays based on reaction speed trait
    }
}

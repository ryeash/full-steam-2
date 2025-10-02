package com.fullsteam.ai;

import com.fullsteam.RandomNames;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central manager for all AI players in a game.
 * Coordinates AI decision-making, behavior switching, and input generation.
 */
@Getter
public class AIPlayerManager {
    private static final Logger log = LoggerFactory.getLogger(AIPlayerManager.class);

    private final Map<Integer, AIPlayer> aiPlayers = new HashMap<>();
    private final Map<Integer, List<AIBehavior>> availableBehaviors = new HashMap<>();
    private final Map<Integer, PlayerInput> generatedInputs = new HashMap<>();

    // Available behavior types
    private final List<AIBehavior> behaviorTemplates = List.of(
            new IdleBehavior(),
            new CombatBehavior()
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

        // Initialize available behaviors for this AI
        List<AIBehavior> behaviors = new ArrayList<>();
        for (AIBehavior template : behaviorTemplates) {
            behaviors.add(createBehaviorInstance(template));
        }
        availableBehaviors.put(aiPlayer.getId(), behaviors);

        log.info("Added AI player {} ({}) with personality type: {}",
                aiPlayer.getId(), aiPlayer.getPlayerName(), aiPlayer.getPersonality().getPersonalityType());
    }

    /**
     * Remove an AI player from management.
     */
    public void removeAIPlayer(int playerId) {
        aiPlayers.remove(playerId);
        availableBehaviors.remove(playerId);
        generatedInputs.remove(playerId);

        log.info("Removed AI player {}", playerId);
    }

    /**
     * Update all AI players and generate their inputs.
     */
    public void update(GameEntities gameEntities, double deltaTime) {
        // Update all AI players
        for (AIPlayer aiPlayer : aiPlayers.values()) {
            if (!aiPlayer.isActive()) {
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
                // Apply movement smoothing for continuous motion
                aiPlayer.smoothMovement(input);
                generatedInputs.put(aiPlayer.getId(), input);
            }
        }
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
     * Create an AI player with a specific personality type, team, and personality-appropriate weapons.
     */
    public static AIPlayer createAIPlayerWithPersonality(int id, double x, double y, String personalityType, int team) {
        AIPersonality personality = switch (personalityType.toLowerCase()) {
            case "aggressive" -> AIPersonality.createAggressive();
            case "defensive" -> AIPersonality.createDefensive();
            case "sniper" -> AIPersonality.createSniper();
            case "rusher" -> AIPersonality.createRusher();
            default -> AIPersonality.createBalanced();
        };

        AIPlayer aiPlayer = new AIPlayer(id, RandomNames.randomName(), x, y, personality, team);

        // Assign weapons based on personality
        com.fullsteam.model.WeaponConfig[] weapons = AIWeaponSelector.selectWeaponLoadoutForPersonality(personality);
        UtilityWeapon utilityWeapon = AIWeaponSelector.selectUtilityWeaponForPersonality(personality);
        aiPlayer.applyWeaponConfig(weapons[0], utilityWeapon);

        log.info("Assigned weapons to AI player {} ({}): Primary={}, Utility={}",
                aiPlayer.getId(), aiPlayer.getPersonality().getPersonalityType(),
                weapons[0].getType(), utilityWeapon.getDisplayName());

        return aiPlayer;
    }

    private void updateAIMemory(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Update memory with observations of other players
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() != aiPlayer.getId() && player.isActive()) {
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
            input.setRight(false);
        }

        // Apply reaction speed delays (not implemented in this simple version)
        // Could add input delays based on reaction speed trait
    }

    private AIBehavior createBehaviorInstance(AIBehavior template) {
        // Create new instances of behaviors for each AI
        // This allows each AI to have independent behavior state
        if (template instanceof IdleBehavior) {
            return new IdleBehavior();
        } else if (template instanceof CombatBehavior) {
            return new CombatBehavior();
        }
        return new IdleBehavior(); // Fallback
    }
}

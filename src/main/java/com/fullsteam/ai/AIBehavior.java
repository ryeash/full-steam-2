package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;

/**
 * Base interface for AI behavior strategies.
 * Each behavior defines how an AI player should act in specific situations.
 */
public interface AIBehavior {
    
    /**
     * Generate player input based on the current game state and AI player state.
     *
     * @param aiPlayer The AI player executing this behavior
     * @param gameEntities Current game state containing all entities
     * @param deltaTime Time since last update
     * @return PlayerInput object that will be processed identically to human input
     */
    PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime);
    
    /**
     * Called when this behavior becomes active for the AI player.
     *
     * @param aiPlayer The AI player adopting this behavior
     */
    default void onEnter(AIPlayer aiPlayer) {
        // Override if needed
    }
    
    /**
     * Called when this behavior is being replaced by another behavior.
     *
     * @param aiPlayer The AI player leaving this behavior
     */
    default void onExit(AIPlayer aiPlayer) {
        // Override if needed
    }
    
    /**
     * Determine if this behavior should continue or if the AI should switch to another behavior.
     *
     * @param aiPlayer The AI player executing this behavior
     * @param gameEntities Current game state
     * @return true if this behavior should continue, false if AI should evaluate other behaviors
     */
    default boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        return true;
    }
    
    /**
     * Get the priority of this behavior. Higher values mean higher priority.
     * Used when multiple behaviors could be active to determine which one to use.
     *
     * @param aiPlayer The AI player evaluating this behavior
     * @param gameEntities Current game state
     * @return Priority value (0-100, where 100 is highest priority)
     */
    default int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        return 50; // Default moderate priority
    }
    
    /**
     * Get a human-readable name for this behavior (useful for debugging).
     */
    String getName();

    /**
     * Shared reload helper used by objective behaviors. Always reloads when the magazine is
     * empty, and reloads below 30% capacity when the situation is considered safe.
     *
     * @param aiPlayer The AI player
     * @param input    The input to populate
     * @param isSafe   Whether it is currently safe to reload (e.g. no nearby threats)
     */
    default void smartReload(AIPlayer aiPlayer, PlayerInput input, boolean isSafe) {
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();

        if (currentAmmo == 0) {
            input.setReload(true);
        } else if (isSafe && currentAmmo < magazineSize * 0.3) {
            input.setReload(true);
        }
    }
}

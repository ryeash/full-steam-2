package com.fullsteam.ai;

import com.fullsteam.games.GameManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for adding and managing AI players in games.
 * This provides convenient methods for common AI player scenarios.
 */
public class AIGameHelper {
    private static final Logger log = LoggerFactory.getLogger(AIGameHelper.class);
    
    /**
     * Add a balanced mix of AI players with different personalities.
     * 
     * @param gameManager The game manager to add AI players to
     * @param count Number of AI players to add
     * @return Number of AI players actually added
     */
    public static int addMixedAIPlayers(GameManager gameManager, int count) {
        String[] personalities = {"aggressive", "defensive", "sniper", "rusher", "balanced"};
        int added = 0;
        
        for (int i = 0; i < count; i++) {
            String personality = personalities[i % personalities.length];
            if (gameManager.addAIPlayer(personality)) {
                added++;
            } else {
                log.warn("Could not add AI player {} of {} - game may be full", i + 1, count);
                break;
            }
        }
        
        log.info("Added {} AI players to game {}", added, gameManager.getGameId());
        return added;
    }
    
    /**
     * Add aggressive AI players for combat-focused scenarios.
     * 
     * @param gameManager The game manager to add AI players to
     * @param count Number of aggressive AI players to add
     * @return Number of AI players actually added
     */
    public static int addAggressiveAIPlayers(GameManager gameManager, int count) {
        int added = 0;
        
        for (int i = 0; i < count; i++) {
            if (gameManager.addAIPlayer("aggressive")) {
                added++;
            } else {
                break;
            }
        }
        
        log.info("Added {} aggressive AI players to game {}", added, gameManager.getGameId());
        return added;
    }
    
    /**
     * Add defensive AI players for strategic gameplay scenarios.
     * 
     * @param gameManager The game manager to add AI players to
     * @param count Number of defensive AI players to add
     * @return Number of AI players actually added
     */
    public static int addDefensiveAIPlayers(GameManager gameManager, int count) {
        int added = 0;
        
        for (int i = 0; i < count; i++) {
            if (gameManager.addAIPlayer("defensive")) {
                added++;
            } else {
                break;
            }
        }
        
        log.info("Added {} defensive AI players to game {}", added, gameManager.getGameId());
        return added;
    }
    
    /**
     * Add random AI players with diverse personalities.
     * 
     * @param gameManager The game manager to add AI players to
     * @param count Number of random AI players to add
     * @return Number of AI players actually added
     */
    public static int addRandomAIPlayers(GameManager gameManager, int count) {
        int added = 0;
        
        for (int i = 0; i < count; i++) {
            if (gameManager.addAIPlayer()) {
                added++;
            } else {
                break;
            }
        }
        
        log.info("Added {} random AI players to game {}", added, gameManager.getGameId());
        return added;
    }
    
    /**
     * Fill a game with AI players up to a certain percentage of max capacity.
     * 
     * @param gameManager The game manager to add AI players to
     * @param fillPercentage Percentage of max players to fill with AI (0.0 to 1.0)
     * @return Number of AI players actually added
     */
    public static int fillGameWithAI(GameManager gameManager, double fillPercentage) {
        int maxPlayers = gameManager.getMaxPlayers();
        int currentPlayers = gameManager.getPlayerCount() + gameManager.getAIPlayerCount();
        int targetPlayers = (int) (maxPlayers * Math.min(1.0, Math.max(0.0, fillPercentage)));
        int aiPlayersToAdd = Math.max(0, targetPlayers - currentPlayers);

        return addMixedAIPlayers(gameManager, aiPlayersToAdd);
    }
    
    /**
     * Remove all AI players from a game.
     * 
     * @param gameManager The game manager to remove AI players from
     * @return Number of AI players removed
     */
    public static int removeAllAIPlayers(GameManager gameManager) {
        int removed = 0;
        
        // Get all current AI player IDs to avoid concurrent modification
        java.util.List<Integer> aiPlayerIds = new java.util.ArrayList<>();
        for (com.fullsteam.physics.Player player : gameManager.getGameEntities().getAllPlayers()) {
            if (gameManager.isAIPlayer(player.getId())) {
                aiPlayerIds.add(player.getId());
            }
        }
        
        // Remove each AI player
        for (Integer playerId : aiPlayerIds) {
            gameManager.removeAIPlayer(playerId);
            removed++;
        }
        
        log.info("Removed {} AI players from game {}", removed, gameManager.getGameId());
        return removed;
    }
}

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
     * @param count       Number of AI players to add
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
}

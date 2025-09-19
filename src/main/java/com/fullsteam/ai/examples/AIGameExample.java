package com.fullsteam.ai.examples;

import com.fullsteam.ai.AIGameHelper;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating how to create and configure games with AI players.
 * This shows various scenarios for AI player management.
 */
public class AIGameExample {
    private static final Logger log = LoggerFactory.getLogger(AIGameExample.class);
    
    /**
     * Example: Creating a game with automatic AI filling enabled.
     */
    public static GameManager createAutoFillGame() {
        GameConfig config = GameConfig.builder()
            .maxPlayers(8)
            .autoFillWithAI(true)
            .minAIFillPercentage(0.5) // Always maintain at least 50% capacity
            .maxAIFillPercentage(0.9) // Don't exceed 90% AI players
            .aiCheckIntervalMs(5000)  // Check every 5 seconds
            .build();
            
        GameManager gameManager = new GameManager("auto-fill-game", "deathmatch", config);
        
        log.info("Created auto-fill game with {} initial AI players", gameManager.getAIPlayerCount());
        return gameManager;
    }
    
    /**
     * Example: Creating a game with manual AI management.
     */
    public static GameManager createManualAIGame() {
        GameConfig config = GameConfig.builder()
            .maxPlayers(6)
            .autoFillWithAI(false) // Disable automatic AI management
            .build();
            
        GameManager gameManager = new GameManager("manual-ai-game", "tactical", config);
        
        // Manually add specific AI personalities
        gameManager.addAIPlayer("sniper");     // Long-range AI
        gameManager.addAIPlayer("aggressive"); // Close-combat AI
        gameManager.addAIPlayer("defensive");  // Strategic AI
        
        log.info("Created manual AI game with {} AI players", gameManager.getAIPlayerCount());
        return gameManager;
    }
    
    /**
     * Example: Creating a training game filled with mixed AI opponents.
     */
    public static GameManager createTrainingGame() {
        GameConfig config = GameConfig.builder()
            .maxPlayers(10)
            .autoFillWithAI(false) // We'll manually configure AI
            .build();
            
        GameManager gameManager = new GameManager("training-game", "training", config);
        
        // Fill with diverse AI personalities for training
        AIGameHelper.addMixedAIPlayers(gameManager, 7);
        
        log.info("Created training game with {} mixed AI players", gameManager.getAIPlayerCount());
        return gameManager;
    }
    
    /**
     * Example: Creating a competitive game with minimal AI.
     */
    public static GameManager createCompetitiveGame() {
        GameConfig config = GameConfig.builder()
            .maxPlayers(12)
            .autoFillWithAI(true)
            .minAIFillPercentage(0.25) // Only add AI if very few players
            .maxAIFillPercentage(0.4)  // Limit AI to maintain competitive balance
            .aiCheckIntervalMs(15000)  // Check less frequently
            .build();
            
        GameManager gameManager = new GameManager("competitive-game", "ranked", config);
        
        log.info("Created competitive game with {} AI players", gameManager.getAIPlayerCount());
        return gameManager;
    }
    
    /**
     * Example: Dynamic AI management during gameplay.
     */
    public static void demonstrateDynamicAI(GameManager gameManager) {
        log.info("=== Dynamic AI Management Demo ===");
        log.info("Initial state: {} total players ({} AI)", 
            gameManager.getPlayerCount() + gameManager.getAIPlayerCount(), 
            gameManager.getAIPlayerCount());
        
        // Enable auto-fill if not already enabled
        if (!gameManager.getGameConfig().isAutoFillWithAI()) {
            gameManager.enableAutoFillAI();
        }
        
        // Manually trigger AI adjustment
        gameManager.adjustAIPlayers();
        log.info("After adjustment: {} total players ({} AI)", 
            gameManager.getPlayerCount() + gameManager.getAIPlayerCount(), 
            gameManager.getAIPlayerCount());
        
        // Fill to 70% capacity
        AIGameHelper.fillGameWithAI(gameManager, 0.7);
        log.info("After filling to 70%: {} total players ({} AI)", 
            gameManager.getPlayerCount() + gameManager.getAIPlayerCount(), 
            gameManager.getAIPlayerCount());
        
        // Configure for more aggressive AI management
        gameManager.setAutoFillSettings(true, 0.6, 0.9);
        gameManager.adjustAIPlayers();
        log.info("After aggressive settings: {} total players ({} AI)", 
            gameManager.getPlayerCount() + gameManager.getAIPlayerCount(), 
            gameManager.getAIPlayerCount());
    }
    
    /**
     * Example: Creating different game modes with appropriate AI configurations.
     */
    public static void createGameModeExamples() {
        log.info("=== Game Mode AI Configuration Examples ===");
        
        // Fast-paced arcade mode
        GameConfig arcadeConfig = GameConfig.builder()
            .maxPlayers(16)
            .autoFillWithAI(true)
            .minAIFillPercentage(0.8) // Keep it active
            .maxAIFillPercentage(0.95) // Allow mostly AI
            .aiCheckIntervalMs(3000) // Quick adjustments
            .build();
        GameManager arcadeGame = new GameManager("arcade-mode", "arcade", arcadeConfig);
        log.info("Arcade mode: {} AI players for fast-paced action", arcadeGame.getAIPlayerCount());
        
        // Strategic mode
        GameConfig strategicConfig = GameConfig.builder()
            .maxPlayers(8)
            .autoFillWithAI(true)
            .minAIFillPercentage(0.4)
            .maxAIFillPercentage(0.6) // Balanced mix
            .aiCheckIntervalMs(20000) // Slower adjustments
            .build();
        GameManager strategicGame = new GameManager("strategic-mode", "strategic", strategicConfig);
        
        // Add specific AI personalities for strategic gameplay
        AIGameHelper.addDefensiveAIPlayers(strategicGame, 2);
        log.info("Strategic mode: {} AI players for tactical gameplay", strategicGame.getAIPlayerCount());
        
        // Practice mode
        GameConfig practiceConfig = GameConfig.builder()
            .maxPlayers(6)
            .autoFillWithAI(false) // Manual control for practice
            .build();
        GameManager practiceGame = new GameManager("practice-mode", "practice", practiceConfig);
        
        // Fill with easier AI for practice
        AIGameHelper.addRandomAIPlayers(practiceGame, 3);
        log.info("Practice mode: {} AI players for skill development", practiceGame.getAIPlayerCount());
    }
}

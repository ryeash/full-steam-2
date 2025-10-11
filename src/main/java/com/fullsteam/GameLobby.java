package com.fullsteam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.GameInfo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final Map<String, GameManager> activeGames = new ConcurrentSkipListMap<>();
    private final AtomicLong globalPlayerCount = new AtomicLong(0);
    private final AtomicLong gameIdCounter = new AtomicLong(1);

    // Game cleanup settings
    private static final long CLEANUP_CHECK_INTERVAL_MS = 30 * 1000; // 30 seconds
    private static final long AI_ONLY_GRACE_PERIOD_MS = 2 * 60 * 1000; // 2 minutes grace period for players to join

    private final ObjectMapper objectMapper;

    @Inject
    public GameLobby(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Config.EXECUTOR.scheduleAtFixedRate(this::cleanupAIOnlyGames, CLEANUP_CHECK_INTERVAL_MS, CLEANUP_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public List<GameInfo> getActiveGames() {
        return activeGames.values().stream()
                .map(GameManager::getGameInfo)
                .toList();
    }

    public long getGlobalPlayerCount() {
        return globalPlayerCount.get();
    }

    public GameManager createGame() {
        // Use default config
        GameConfig defaultConfig = GameConfig.builder()
                .maxPlayers(18)
                .teamCount(4)
                .worldHeight(2000)
                .worldWidth(3000)
                .build();
        return createGameWithConfig(defaultConfig);
    }

    public GameManager createGameWithConfig(GameConfig gameConfig) {
        if (activeGames.size() >= Config.MAX_GLOBAL_GAMES) {
            throw new IllegalStateException("Maximum number of games reached");
        }
        String gameId = "game_" + gameIdCounter.getAndIncrement();
        GameManager game = new GameManager(gameId, gameConfig, objectMapper);
        activeGames.put(gameId, game);
        log.info("Created new game: {} with config: maxPlayers={}, teamCount={}, world={}x{}",
                gameId, gameConfig.getMaxPlayers(), gameConfig.getTeamCount(),
                gameConfig.getWorldWidth(), gameConfig.getWorldHeight());
        return game;
    }

    public GameManager getGame(String gameId) {
        return activeGames.get(gameId);
    }

    public void removeGame(String gameId) {
        GameManager removed = activeGames.remove(gameId);
        if (removed != null) {
            log.info("Removed game: {}", gameId);
            removed.shutdown();
        }
    }

    public void incrementPlayerCount() {
        globalPlayerCount.incrementAndGet();
    }

    public void decrementPlayerCount() {
        globalPlayerCount.decrementAndGet();
    }

    /**
     * Clean up games that only contain AI players and have been running for too long.
     */
    private void cleanupAIOnlyGames() {
        long currentTime = System.currentTimeMillis();
        List<String> gamesToRemove = new ArrayList<>();

        for (Map.Entry<String, GameManager> entry : activeGames.entrySet()) {
            String gameId = entry.getKey();
            GameManager game = entry.getValue();
            long gameAge = currentTime - game.getGameStartTime();

            // Only cleanup AI-only games that have existed longer than the grace period
            // This gives players time to configure and join before the game is shut down
            if (!game.hasHumanPlayers() && gameAge > AI_ONLY_GRACE_PERIOD_MS) {
                gamesToRemove.add(gameId);
                log.info("Scheduling AI-only game {} for shutdown (running for {} minutes with {} AI players)",
                        gameId,
                        gameAge / 60000,
                        game.getAIPlayerCount());
            }
        }

        // Remove and shutdown the games
        for (String gameId : gamesToRemove) {
            GameManager game = activeGames.remove(gameId);
            if (game != null) {
                try {
                    game.shutdown();
                    log.info("Successfully shut down AI-only game: {}", gameId);
                } catch (Exception e) {
                    log.error("Error shutting down AI-only game {}: {}", gameId, e.getMessage());
                }
            }
        }
    }
}



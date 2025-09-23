package com.fullsteam;

import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.GameInfo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final Map<String, GameManager> activeGames = new ConcurrentHashMap<>();
    private final AtomicLong globalPlayerCount = new AtomicLong(0);
    private final AtomicLong gameIdCounter = new AtomicLong(1);

    // Game cleanup settings
    private static final long CLEANUP_CHECK_INTERVAL_MS = 60 * 1000; // 1 minute

    @Inject
    public GameLobby() {
        Config.EXECUTOR.scheduleAtFixedRate(this::cleanupAIOnlyGames, CLEANUP_CHECK_INTERVAL_MS, CLEANUP_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public List<String> getGameTypes() {
        return Arrays.asList("Battle Royale", "Team Deathmatch", "Capture Points");
    }

    public List<GameInfo> getActiveGames() {
        return activeGames.values().stream()
                .map(GameManager::getGameInfo)
                .toList();
    }

    public long getGlobalPlayerCount() {
        return globalPlayerCount.get();
    }

    public GameManager createGame(String gameType) {
        String gameId = "game_" + gameIdCounter.getAndIncrement();
        GameManager game = new GameManager(gameId, "Battle Royale", GameConfig.builder().build());
        activeGames.put(gameId, game);
        log.info("Created new game: {} ({})", gameId, gameType);
        return game;
    }

    public GameManager getGame(String gameId) {
        return activeGames.get(gameId);
    }

    public void removeGame(String gameId) {
        GameManager removed = activeGames.remove(gameId);
        if (removed != null) {
            log.info("Removed game: {}", gameId);
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

            // Check if game should be shut down
            if (!game.hasHumanPlayers()) {
                gamesToRemove.add(gameId);
                log.info("Scheduling AI-only game {} for shutdown (running for {} minutes with {} AI players)",
                        gameId,
                        (currentTime - game.getGameStartTime()) / 60000,
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



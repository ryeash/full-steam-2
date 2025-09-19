package com.fullsteam;

import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.GameInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final Map<String, GameManager> activeGames = new ConcurrentHashMap<>();
    private final AtomicLong globalPlayerCount = new AtomicLong(0);
    private final AtomicLong gameIdCounter = new AtomicLong(1);

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
}



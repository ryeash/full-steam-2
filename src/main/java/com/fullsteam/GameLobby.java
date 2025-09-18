package com.fullsteam;

import com.fullsteam.games.BattleRoyaleGame;
import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.model.GameInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);
    
    private final Map<String, AbstractGameStateManager> activeGames = new ConcurrentHashMap<>();
    private final AtomicLong globalPlayerCount = new AtomicLong(0);
    private final AtomicLong gameIdCounter = new AtomicLong(1);
    
    public List<String> getGameTypes() {
        return Arrays.asList("Battle Royale", "Team Deathmatch", "Capture Points");
    }
    
    public List<GameInfo> getActiveGames() {
        return activeGames.values().stream()
                .map(AbstractGameStateManager::getGameInfo)
                .toList();
    }
    
    public long getGlobalPlayerCount() {
        return globalPlayerCount.get();
    }
    
    public AbstractGameStateManager createGame(String gameType) {
        String gameId = "game_" + gameIdCounter.getAndIncrement();
        AbstractGameStateManager game = switch (gameType) {
            case "Battle Royale" -> new BattleRoyaleGame(gameId, gameType);
            case "Team Deathmatch" -> new BattleRoyaleGame(gameId, gameType); // Reuse for now
            case "Capture Points" -> new BattleRoyaleGame(gameId, gameType); // Reuse for now
            default -> new BattleRoyaleGame(gameId, "Battle Royale");
        };
        
        activeGames.put(gameId, game);
        log.info("Created new game: {} ({})", gameId, gameType);
        return game;
    }
    
    public AbstractGameStateManager getGame(String gameId) {
        return activeGames.get(gameId);
    }
    
    public void removeGame(String gameId) {
        AbstractGameStateManager removed = activeGames.remove(gameId);
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



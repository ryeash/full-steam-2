package com.fullsteam.controller;

import com.fullsteam.GameLobby;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.util.IdGenerator;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PlayerConnectionService {
    public static final String SESSION_KEY = "playerSession";
    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionService.class);

    private final GameLobby gameLobby;

    @Inject
    public PlayerConnectionService(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
    }

    public boolean connectPlayer(WebSocketSession session, String gameId) {
        return connectPlayer(session, gameId, false);
    }

    public boolean connectPlayer(WebSocketSession session, String gameId, boolean asSpectator) {
        try {
            int playerId = IdGenerator.nextPlayerId();
            PlayerSession playerSession = new PlayerSession(playerId, session);
            playerSession.setSpectator(asSpectator);

            // Get or create game
            GameManager game = gameLobby.getGame(gameId);
            if (game == null) {
                if (asSpectator) {
                    // Spectators can't join non-existent games
                    log.warn("Spectator {} attempted to join non-existent game {}", playerId, gameId);
                    return false;
                }
                game = gameLobby.createGame();
                gameId = game.getGameId();
            }

            // Add player/spectator to game
            if (game.addPlayer(playerSession)) {
                playerSession.setGame(game);
                session.put(SESSION_KEY, playerSession);
                
                // Only increment player count for actual players, not spectators
                if (!asSpectator) {
                    gameLobby.incrementPlayerCount();
                }

                log.info("{} {} connected to game {}", 
                    asSpectator ? "Spectator" : "Player", 
                    playerSession.getPlayerId(), 
                    gameId);
                return true;
            } else {
                log.warn("Failed to add {} {} to game {}", 
                    asSpectator ? "spectator" : "player",
                    playerSession.getPlayerId(), 
                    gameId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error connecting to game {}", gameId, e);
            return false;
        }
    }

    public void disconnectPlayer(WebSocketSession session) {
        PlayerSession playerSession = session.get(SESSION_KEY, PlayerSession.class).orElse(null);
        if (playerSession != null) {
            GameManager game = playerSession.getGame();
            if (game != null) {
                game.removePlayer(playerSession.getPlayerId());

                // Remove empty games
                if (game.getPlayerCount() == 0) {
                    gameLobby.removeGame(game.getGameId());
                }
            }

            // Only decrement player count for actual players, not spectators
            if (!playerSession.isSpectator()) {
                gameLobby.decrementPlayerCount();
            }
            
            log.info("{} {} disconnected", 
                playerSession.isSpectator() ? "Spectator" : "Player",
                playerSession.getPlayerId());
        }
    }
}



package com.fullsteam.controller;

import com.fullsteam.GameLobby;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.PlayerSession;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class PlayerConnectionService {
    public static final String SESSION_KEY = "playerSession";
    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionService.class);

    private final GameLobby gameLobby;
    private final AtomicInteger playerIdCounter = new AtomicInteger(1);

    @Inject
    public PlayerConnectionService(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
    }

    public boolean connectPlayer(WebSocketSession session, String gameId) {
        try {
            int playerId = playerIdCounter.getAndIncrement();
            PlayerSession playerSession = new PlayerSession(playerId, session);

            // Get or create game
            GameManager game = gameLobby.getGame(gameId);
            if (game == null) {
                game = gameLobby.createGame();
                gameId = game.getGameId();
            }

            // Add player to game
            if (game.addPlayer(playerSession)) {
                playerSession.setGame(game);
                session.put(SESSION_KEY, playerSession);
                gameLobby.incrementPlayerCount();

                log.info("Player {} connected to game {}", playerSession.getPlayerId(), gameId);
                return true;
            } else {
                log.warn("Failed to add player {} to game {}", playerSession.getPlayerId(), gameId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error connecting player to game {}", gameId, e);
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

            gameLobby.decrementPlayerCount();
            log.info("Player {} disconnected", playerSession.getPlayerId());
        }
    }
}



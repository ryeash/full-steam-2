package com.fullsteam.controller;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.GameManager.JoinRejectReason;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.PlayerSessionState;
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

    /**
     * Outcome of a {@link #connectPlayer} attempt. On success the session is
     * attached to the supplied WebSocket. On failure the caller is expected to
     * send a typed {@code joinRejected} message and close the socket.
     */
    public sealed interface ConnectResult {
        record Success() implements ConnectResult {
        }

        record Rejected(JoinRejectReason reason) implements ConnectResult {
        }
    }

    public ConnectResult connectPlayer(WebSocketSession session, String gameId, boolean asSpectator) {
        try {
            int playerId = Config.nextPlayerId();
            PlayerSession playerSession = new PlayerSession(playerId, session);
            playerSession.setState(asSpectator ? PlayerSessionState.SPECTATOR : PlayerSessionState.LOBBY);

            GameManager game = gameLobby.getGame(gameId);
            if (game == null) {
                // No more silent auto-create. Games are spawned explicitly via POST /api/games.
                log.warn("{} {} attempted to join unknown game {}",
                        asSpectator ? "Spectator" : "Player", playerId, gameId);
                return new ConnectResult.Rejected(JoinRejectReason.GAME_NOT_FOUND);
            }

            if (game.addPlayer(playerSession)) {
                playerSession.setGame(game);
                session.put(SESSION_KEY, playerSession);

                if (!asSpectator) {
                    gameLobby.incrementPlayerCount();
                }
                log.debug("{} {} connected to game {}",
                        asSpectator ? "Spectator" : "Player",
                        playerSession.getPlayerId(),
                        gameId);
                return new ConnectResult.Success();
            }

            JoinRejectReason reason = game.determineJoinRejectReason(playerSession);
            log.warn("Failed to add {} {} to game {} (reason: {})",
                    asSpectator ? "spectator" : "player",
                    playerSession.getPlayerId(),
                    gameId,
                    reason);
            return new ConnectResult.Rejected(reason);
        } catch (Exception e) {
            log.error("Error connecting to game {}", gameId, e);
            return new ConnectResult.Rejected(JoinRejectReason.GAME_NOT_FOUND);
        }
    }

    public void disconnectPlayer(WebSocketSession session) {
        session.get(SESSION_KEY, PlayerSession.class).ifPresent(playerSession -> {
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
            log.debug("{} {} disconnected",
                    playerSession.isSpectator() ? "Spectator" : "Player",
                    playerSession.getPlayerId());
        });
    }
}

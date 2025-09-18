package com.fullsteam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.fullsteam.controller.PlayerConnectionService.SESSION_KEY;

@ServerWebSocket("/game/{gameId}/{gameType}")
public class GameWebSocketEndpoint {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketEndpoint.class);

    private final PlayerConnectionService connectionService;
    private final ObjectMapper objectMapper;

    @Inject
    public GameWebSocketEndpoint(PlayerConnectionService connectionService, ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.objectMapper = objectMapper;
    }

    @OnOpen
    public void onOpen(WebSocketSession session, String gameId, String gameType) {
        log.info("WebSocket connection opened for gameId: {}, gameType: {}", gameId, gameType);
        if (!connectionService.connectPlayer(session, gameId, gameType)) {
            log.warn("Failed to connect player to game {}, closing session", gameId);
            session.close();
        } else {
            log.info("Player successfully connected to game {}", gameId);
        }
    }

    @OnMessage
    public void onMessage(byte[] message, WebSocketSession session) {
        PlayerSession playerSession = session.get(SESSION_KEY, PlayerSession.class).orElse(null);

        if (playerSession == null) {
            return; // No player session found
        }

        AbstractGameStateManager game = playerSession.getGame();
        int playerId = playerSession.getPlayerId();

        if (game == null) {
            log.warn("Received message from session without game context. Closing.");
            session.close();
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(message);
            String type = rootNode.path("type").asText("playerInput");

            switch (type) {
                case "ping":
                    game.send(session, Map.of("type", "pong"));
                    break;
                case "configChange":
                    PlayerConfigRequest request = objectMapper.treeToValue(rootNode, PlayerConfigRequest.class);
                    game.handlePlayerConfigChange(playerId, request);
                    break;
                case "playerInput":
                    PlayerInput input = objectMapper.treeToValue(rootNode, PlayerInput.class);
                    game.acceptPlayerInput(playerId, input);
                    break;
                default:
                    log.warn("Received unknown message type '{}' from player {}", type, playerId);
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing message from player {}: {}", playerId, e.getMessage());
        }
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        log.info("WebSocket connection closed for session: {}", session.getId());
        connectionService.disconnectPlayer(session);
    }
}

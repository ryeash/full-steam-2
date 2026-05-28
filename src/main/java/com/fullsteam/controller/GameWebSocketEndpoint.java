package com.fullsteam.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.controller.PlayerConnectionService.ConnectResult;
import com.fullsteam.games.GameManager;
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

@ServerWebSocket("/game/{gameId}")
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
    public void onOpen(WebSocketSession session, String gameId) {
        // Check if this is a spectator connection by parsing the request URI
        boolean asSpectator = false;
        try {
            String requestUri = session.getRequestURI().toString();
            asSpectator = requestUri.contains("spectate=true");
        } catch (Exception e) {
            log.debug("Could not parse spectate parameter from URI: {}", e.getMessage());
        }

        log.info("WebSocket connection opened for gameId: {} (spectator: {})", gameId, asSpectator);

        ConnectResult result = connectionService.connectPlayer(session, gameId, asSpectator);
        if (result instanceof ConnectResult.Rejected rejected) {
            log.warn("Failed to connect {} to game {} (reason: {}), closing session",
                    asSpectator ? "spectator" : "player", gameId, rejected.reason());
            sendJoinRejected(session, rejected.reason().name());
            session.close();
        } else {
            log.info("{} successfully connected to game {}",
                    asSpectator ? "Spectator" : "Player", gameId);
        }
    }

    private void sendJoinRejected(WebSocketSession session, String reason) {
        try {
            if (session.isOpen() && session.isWritable()) {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "joinRejected",
                        "reason", reason
                ));
                // Use sendSync where available so the client receives the
                // message before we close the socket; fall back to async otherwise.
                session.sendSync(json);
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializing joinRejected message", e);
        } catch (Exception e) {
            log.debug("Failed to send joinRejected before close: {}", e.getMessage());
        }
    }

    @OnMessage
    public void onMessage(byte[] message, WebSocketSession session) {
        PlayerSession playerSession = session.get(SESSION_KEY, PlayerSession.class).orElse(null);

        if (playerSession == null) {
            return; // No player session found
        }

        GameManager game = playerSession.getGame();
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
                    // Spectators can't change config
                    if (!playerSession.isSpectator()) {
                        PlayerConfigRequest request = objectMapper.treeToValue(rootNode, PlayerConfigRequest.class);
                        game.handlePlayerConfigChange(playerId, request);
                    }
                    break;
                case "readyToSpawn":
                    PlayerConfigRequest spawnRequest = objectMapper.treeToValue(rootNode, PlayerConfigRequest.class);
                    game.handleReadyToSpawn(playerId, spawnRequest);
                    break;
                case "playerInput":
                    // Spectators can't send player input
                    if (!playerSession.isSpectator()) {
                        PlayerInput input = objectMapper.treeToValue(rootNode, PlayerInput.class);
                        game.acceptPlayerInput(playerId, input);
                    }
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

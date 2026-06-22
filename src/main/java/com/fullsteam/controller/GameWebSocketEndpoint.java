package com.fullsteam.controller;

import com.fullsteam.controller.PlayerConnectionService.ConnectResult;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.fullsteam.controller.PlayerConnectionService.SESSION_KEY;

@ServerWebSocket("/game/{gameId}")
public class GameWebSocketEndpoint {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketEndpoint.class);

    private final PlayerConnectionService connectionService;
    private final ObjectMapper objectMapper;
    /** Permitted Origins for WS handshakes; empty = allow any (dev). See application.yml. */
    private final List<String> allowedOrigins;

    @Inject
    public GameWebSocketEndpoint(PlayerConnectionService connectionService,
                                 ObjectMapper objectMapper,
                                 @Value("${app.allowed-origins:}") List<String> allowedOrigins) {
        this.connectionService = connectionService;
        this.objectMapper = objectMapper;
        // An unset property binds the empty string as ["" ] (a one-element list),
        // not an empty list — strip blank entries so a blank config means
        // "no allowlist / allow any origin".
        this.allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @OnOpen
    public void onOpen(WebSocketSession session, String gameId, HttpRequest<?> request) {
        // Reject cross-site WebSocket handshakes when an origin allowlist is set.
        if (!isOriginAllowed(request)) {
            log.warn("Rejecting WS connection to game {} from disallowed origin '{}'",
                    gameId, request.getHeaders().get(HttpHeaders.ORIGIN));
            sendJoinRejected(session, "ORIGIN_NOT_ALLOWED");
            session.close();
            return;
        }

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
        if (result instanceof ConnectResult.Rejected(GameManager.JoinRejectReason reason)) {
            log.warn("Failed to connect {} to game {} (reason: {}), closing session",
                    asSpectator ? "spectator" : "player", gameId, reason);
            sendJoinRejected(session, reason.name());
            session.close();
        } else {
            log.info("{} successfully connected to game {}",
                    asSpectator ? "Spectator" : "Player", gameId);
        }
    }

    /**
     * Whether the handshake's Origin is permitted. When no allowlist is configured
     * (empty {@code app.allowed-origins}) all origins are allowed — convenient for
     * local dev. In production, set APP_ALLOWED_ORIGINS to your site origin(s).
     */
    private boolean isOriginAllowed(HttpRequest<?> request) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return true;
        }
        String origin = request.getHeaders().get(HttpHeaders.ORIGIN);
        return origin != null && allowedOrigins.contains(origin);
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
        } catch (Exception e) {
            log.debug("Failed to send joinRejected before close: {}", e.getMessage());
        }
    }

    // Pin the inbound frame cap well above any legit message (ping / input /
    // loadout are all < 2KB) while bounding abuse; the Netty default is 64KB.
    @OnMessage(maxPayloadLength = 8192)
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
            String type = rootNode.path("type").asString("playerInput");

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

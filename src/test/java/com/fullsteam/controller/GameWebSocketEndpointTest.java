package com.fullsteam.controller;

import com.fullsteam.BaseTestClass;
import com.fullsteam.GameLobby;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.PlayerSession;
import io.micronaut.websocket.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameWebSocketEndpointTest extends BaseTestClass {

    private GameLobby gameLobby;
    private PlayerConnectionService connectionService;
    private GameWebSocketEndpoint endpoint;
    private ObjectMapper objectMapper;
    private List<String> sentMessages;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        objectMapper = new ObjectMapper();
        gameLobby = new GameLobby(objectMapper);
        connectionService = new PlayerConnectionService(gameLobby);
        endpoint = new GameWebSocketEndpoint(connectionService, objectMapper, List.of());
        sentMessages = new ArrayList<>();
    }

    private WebSocketSession createMockWebSocketSession() {
        Map<String, Object> attributes = new HashMap<>();
        return (WebSocketSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) return true;
                    if ("isWritable".equals(method.getName())) return true;
                    if ("getAttributes".equals(method.getName())) return attributes;
                    if ("get".equals(method.getName())) {
                        Object val = attributes.get((String) args[0]);
                        return Optional.ofNullable(val);
                    }
                    if ("put".equals(method.getName())) {
                        attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("sendAsync".equals(method.getName())) {
                        sentMessages.add((String) args[0]);
                        return null;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Ping message with timestamp responds with pong containing same timestamp")
    void testPingWithTimestamp() throws Exception {
        GameManager game = gameLobby.createGameWithConfig(GameConfig.builder().enableAIFilling(false).build());
        String gameId = game.getGameId();

        WebSocketSession session = createMockWebSocketSession();
        PlayerConnectionService.ConnectResult result = connectionService.connectPlayer(session, gameId, false);
        assertTrue(result instanceof PlayerConnectionService.ConnectResult.Success);

        endpoint.onMessage("{\"type\":\"ping\",\"timestamp\":12345678}".getBytes(java.nio.charset.StandardCharsets.UTF_8), session);

        assertFalse(sentMessages.isEmpty(), "Expected pong message to be sent");
        String lastMessage = sentMessages.get(sentMessages.size() - 1);
        JsonNode root = objectMapper.readTree(lastMessage);
        assertEquals("pong", root.path("type").asText());
        assertEquals(12345678L, root.path("timestamp").asLong());
    }

    @Test
    @DisplayName("Ping message without timestamp responds with pong without timestamp")
    void testPingWithoutTimestamp() throws Exception {
        GameManager game = gameLobby.createGameWithConfig(GameConfig.builder().enableAIFilling(false).build());
        String gameId = game.getGameId();

        WebSocketSession session = createMockWebSocketSession();
        PlayerConnectionService.ConnectResult result = connectionService.connectPlayer(session, gameId, false);
        assertTrue(result instanceof PlayerConnectionService.ConnectResult.Success);

        endpoint.onMessage("{\"type\":\"ping\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8), session);

        assertFalse(sentMessages.isEmpty(), "Expected pong message to be sent");
        String lastMessage = sentMessages.get(sentMessages.size() - 1);
        JsonNode root = objectMapper.readTree(lastMessage);
        assertEquals("pong", root.path("type").asText());
        assertTrue(root.path("timestamp").isMissingNode());
    }
}

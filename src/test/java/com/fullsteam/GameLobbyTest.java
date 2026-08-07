package com.fullsteam;

import com.fullsteam.controller.PlayerConnectionService;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.PlayerSessionState;
import io.micronaut.websocket.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class GameLobbyTest extends BaseTestClass {

    private GameLobby gameLobby;
    private PlayerConnectionService connectionService;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        gameLobby = new GameLobby(new ObjectMapper());
        connectionService = new PlayerConnectionService(gameLobby);
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
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Newly created game is active, but is removed once human player leaves")
    void testGameDismissedWhenHumanPlayerLeaves() {
        GameManager game = gameLobby.createGameWithConfig(GameConfig.builder().enableAIFilling(true).build());
        String gameId = game.getGameId();

        // Game is freshly created and should appear in active games
        assertEquals(1, gameLobby.getActiveGames().size());
        assertFalse(game.hadHumanPlayers());

        // Connect a human player
        WebSocketSession ws = createMockWebSocketSession();
        PlayerConnectionService.ConnectResult result = connectionService.connectPlayer(ws, gameId, false);
        assertTrue(result instanceof PlayerConnectionService.ConnectResult.Success);
        assertTrue(game.hadHumanPlayers());
        assertTrue(game.hasHumanPlayers());
        assertEquals(1, gameLobby.getGlobalPlayerCount());

        // Player leaves / disconnects
        connectionService.disconnectPlayer(ws);

        // Game should be removed immediately from lobby
        assertNull(gameLobby.getGame(gameId));
        assertEquals(0, gameLobby.getActiveGames().size());
        assertEquals(0, gameLobby.getGlobalPlayerCount());
    }

    @Test
    @DisplayName("Global player count is decremented even if player was converted to spectator upon elimination")
    void testGlobalPlayerCountDecrementedWhenEliminatedPlayerLeaves() {
        GameManager game = gameLobby.createGameWithConfig(GameConfig.builder().enableAIFilling(false).build());
        String gameId = game.getGameId();

        WebSocketSession ws = createMockWebSocketSession();
        connectionService.connectPlayer(ws, gameId, false);
        assertEquals(1, gameLobby.getGlobalPlayerCount());

        // Retrieve session and simulate conversion to spectator (e.g. elimination in BR mode)
        PlayerSession session = ws.get(PlayerConnectionService.SESSION_KEY, PlayerSession.class).orElseThrow();
        session.setState(PlayerSessionState.SPECTATOR);

        // Player disconnects
        connectionService.disconnectPlayer(ws);

        assertEquals(0, gameLobby.getGlobalPlayerCount());
        assertNull(gameLobby.getGame(gameId));
    }

    @Test
    @DisplayName("Finished game is filtered out of getActiveGames")
    void testFinishedGameFilteredFromActiveGames() {
        GameManager game = gameLobby.createGameWithConfig(GameConfig.builder().enableAIFilling(false).build());

        assertEquals(1, gameLobby.getActiveGames().size());

        // End the game
        game.getRuleSystem().declareVictory(1, -1, "Test victory");

        // Finished game should not be listed in active games
        assertEquals(0, gameLobby.getActiveGames().size());
    }
}

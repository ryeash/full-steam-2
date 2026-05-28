package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.PlayerSessionState;
import com.fullsteam.model.Rules;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.Player;
import io.micronaut.websocket.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the LOBBY / readyToSpawn / lobby-timeout flow added in the
 * "yet-to-spawn lobby state" rework.
 */
class LobbyFlowTest extends BaseTestClass {

    private GameManager gameManager;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        Rules rules = Rules.builder().build();
        GameConfig config = GameConfig.builder()
                .maxPlayers(4)
                .teamCount(2)
                .worldWidth(2000)
                .worldHeight(2000)
                .enableAIFilling(false)
                .rules(rules)
                .build();
        gameManager = new GameManager("lobby_flow_test", config, null);
        // Tests below count Player entities to verify spawn behavior; drop any
        // AI players that snuck in during construction (none expected when
        // enableAIFilling=false, but be defensive about test isolation).
        new ArrayList<>(gameManager.getGameEntities().getAllPlayers()).forEach(p ->
                gameManager.getGameEntities().removePlayer(p.getId())
        );
    }

    // ---- helpers ----

    /**
     * Build a {@link PlayerSession} backed by a no-op WebSocketSession stub.
     * The stub returns false for {@code isOpen()} / {@code isWritable()} so
     * {@link GameManager#send} short-circuits and never touches a real socket.
     */
    private PlayerSession newSession(int playerId) {
        WebSocketSession stub = (WebSocketSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> defaultFor(method.getReturnType())
        );
        return new PlayerSession(playerId, stub);
    }

    private static Object defaultFor(Class<?> returnType) {
        if (returnType == boolean.class) return Boolean.FALSE;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0d;
        if (returnType == float.class) return 0.0f;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return (char) 0;
        return null;
    }

    private PlayerConfigRequest buildReadyRequest() {
        PlayerConfigRequest req = new PlayerConfigRequest();
        req.setType("readyToSpawn");
        req.setWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET);
        req.setUtilityWeapon(UtilityWeapon.HEAL_ZONE.name());
        return req;
    }

    // ---- addPlayer / lifecycle ----

    @Test
    @DisplayName("addPlayer places the session in LOBBY without creating a Player entity")
    void testAddPlayerStaysInLobby() {
        PlayerSession session = newSession(1);
        assertTrue(gameManager.addPlayer(session));

        assertEquals(PlayerSessionState.LOBBY, session.getState());
        assertNull(gameManager.getGameEntities().getPlayer(session.getPlayerId()),
                "LOBBY session should not have a Player entity yet");
        assertEquals(1, gameManager.getPlayingAndLobbyCount());
        assertEquals(0, gameManager.getGameEntities().getAllPlayers().size());
    }

    @Test
    @DisplayName("Spectator sessions don't consume player slots")
    void testSpectatorNotCountedAgainstMaxPlayers() {
        // Fill all 4 player slots with spectators
        for (int i = 1; i <= 4; i++) {
            PlayerSession spectator = newSession(i);
            spectator.setState(PlayerSessionState.SPECTATOR);
            assertTrue(gameManager.addPlayer(spectator),
                    "Spectator " + i + " should join despite max=4");
        }
        // A real player can still join
        PlayerSession player = newSession(5);
        assertTrue(gameManager.addPlayer(player),
                "Real player should still fit when slots are spectator-only");
        assertEquals(1, gameManager.getPlayingAndLobbyCount());
        assertEquals(4, gameManager.getSpectatorCount());
    }

    // ---- handleReadyToSpawn: LOBBY -> PLAYING ----

    @Test
    @DisplayName("handleReadyToSpawn promotes LOBBY -> PLAYING and creates a Player entity")
    void testReadyToSpawnFromLobby() {
        PlayerSession session = newSession(1);
        gameManager.addPlayer(session);
        assertEquals(PlayerSessionState.LOBBY, session.getState());

        gameManager.handleReadyToSpawn(session.getPlayerId(), buildReadyRequest());

        assertEquals(PlayerSessionState.PLAYING, session.getState());
        Player player = gameManager.getGameEntities().getPlayer(session.getPlayerId());
        assertNotNull(player, "spawnPlayerFromSession should have added a Player entity");
        assertEquals(1, gameManager.getGameEntities().getAllPlayers().size());
    }

    @Test
    @DisplayName("handleReadyToSpawn is idempotent for sessions already PLAYING")
    void testReadyToSpawnFromPlayingIsNoop() {
        PlayerSession session = newSession(1);
        gameManager.addPlayer(session);
        gameManager.handleReadyToSpawn(session.getPlayerId(), buildReadyRequest());

        Player firstPlayer = gameManager.getGameEntities().getPlayer(session.getPlayerId());
        assertNotNull(firstPlayer);
        long initialPlayerCount = gameManager.getGameEntities().getAllPlayers().size();

        // Second call should be a no-op
        gameManager.handleReadyToSpawn(session.getPlayerId(), buildReadyRequest());
        assertEquals(initialPlayerCount, gameManager.getGameEntities().getAllPlayers().size());
        assertSame(firstPlayer, gameManager.getGameEntities().getPlayer(session.getPlayerId()));
    }

    // ---- handleReadyToSpawn: SPECTATOR -> PLAYING ----

    @Test
    @DisplayName("Spectator ready-to-spawn succeeds when a slot is available")
    void testReadyToSpawnFromSpectatorWhenSlotFree() {
        PlayerSession session = newSession(1);
        session.setState(PlayerSessionState.SPECTATOR);
        gameManager.getGameEntities().addPlayerSession(session);

        gameManager.handleReadyToSpawn(session.getPlayerId(), buildReadyRequest());

        assertEquals(PlayerSessionState.PLAYING, session.getState());
        assertNotNull(gameManager.getGameEntities().getPlayer(session.getPlayerId()));
    }

    @Test
    @DisplayName("Spectator ready-to-spawn is rejected when game is full and AI fill is off")
    void testReadyToSpawnFromSpectatorFullGame() {
        // Fill all 4 slots with PLAYING sessions
        List<PlayerSession> filled = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            PlayerSession s = newSession(i);
            assertTrue(gameManager.addPlayer(s));
            gameManager.handleReadyToSpawn(s.getPlayerId(), buildReadyRequest());
            filled.add(s);
        }
        assertEquals(4, gameManager.getPlayingAndLobbyCount());

        // A spectator now arrives and tries to claim a slot - should be rejected
        PlayerSession spectator = newSession(99);
        spectator.setState(PlayerSessionState.SPECTATOR);
        gameManager.getGameEntities().addPlayerSession(spectator);
        gameManager.handleReadyToSpawn(spectator.getPlayerId(), buildReadyRequest());

        assertEquals(PlayerSessionState.SPECTATOR, spectator.getState(),
                "Spectator should remain a spectator when game is full");
        assertNull(gameManager.getGameEntities().getPlayer(spectator.getPlayerId()),
                "No Player entity should be created for a rejected spectator");
    }

    // ---- processLobbyTimeouts ----

    @Test
    @DisplayName("processLobbyTimeouts downgrades stale LOBBY sessions to SPECTATOR")
    void testProcessLobbyTimeoutsDowngrades() throws Exception {
        PlayerSession session = newSession(1);
        gameManager.addPlayer(session);
        // Pretend the session has been waiting longer than the timeout
        session.setLobbyEnteredAt(System.currentTimeMillis() - GameManager.LOBBY_TIMEOUT_MS - 1_000L);

        invokeProcessLobbyTimeouts();

        assertEquals(PlayerSessionState.SPECTATOR, session.getState(),
                "Stale LOBBY session should be downgraded after timeout");
        assertNull(gameManager.getGameEntities().getPlayer(session.getPlayerId()));
    }

    @Test
    @DisplayName("processLobbyTimeouts leaves fresh LOBBY sessions alone")
    void testProcessLobbyTimeoutsLeavesFreshSessions() throws Exception {
        PlayerSession session = newSession(1);
        gameManager.addPlayer(session);
        // lobbyEnteredAt was just set; well within the timeout window.

        invokeProcessLobbyTimeouts();

        assertEquals(PlayerSessionState.LOBBY, session.getState(),
                "Recent LOBBY session should not be downgraded yet");
    }

    private void invokeProcessLobbyTimeouts() throws Exception {
        Method m = GameManager.class.getDeclaredMethod("processLobbyTimeouts");
        m.setAccessible(true);
        m.invoke(gameManager);
    }
}

package com.fullsteam.model;

import com.fullsteam.RandomNames;
import com.fullsteam.games.GameManager;
import io.micronaut.websocket.WebSocketSession;
import lombok.Data;

@Data
public class PlayerSession {
    private final int playerId;
    private final WebSocketSession session;
    private GameManager game;
    private String playerName;

    /**
     * Lifecycle state. Defaults to {@link PlayerSessionState#LOBBY} - the
     * session has reserved a slot but has not yet sent its first
     * {@code readyToSpawn} message. Sessions that arrive with
     * {@code spectate=true} are flipped to {@link PlayerSessionState#SPECTATOR}
     * by {@link com.fullsteam.controller.PlayerConnectionService} immediately.
     */
    private PlayerSessionState state = PlayerSessionState.LOBBY;
    private boolean countedInGlobalPlayerCount = false;

    /**
     * Wall-clock timestamp (ms) when the session entered the {@code LOBBY}
     * state. Used by the per-tick lobby timeout sweep in
     * {@code GameManager.processLobbyTimeouts}.
     */
    private long lobbyEnteredAt = System.currentTimeMillis();

    public PlayerSession(int playerId, WebSocketSession session) {
        this.playerId = playerId;
        this.session = session;
        this.playerName = RandomNames.randomName();
    }

    /**
     * Backwards-compatible helper used by message handlers and broadcast paths
     * that only care whether the session can issue player actions.
     */
    public boolean isSpectator() {
        return state == PlayerSessionState.SPECTATOR;
    }

    /**
     * @return true if the session has a {@code Player} entity in the world.
     */
    public boolean isPlaying() {
        return state == PlayerSessionState.PLAYING;
    }

    /**
     * @return true if the session is reserving a slot but has not yet spawned.
     */
    public boolean isInLobby() {
        return state == PlayerSessionState.LOBBY;
    }

    /**
     * Setter retained for legacy call sites that wrote {@code setSpectator(boolean)}.
     * Maps {@code true} → SPECTATOR and {@code false} → LOBBY.
     */
    public void setSpectator(boolean spectator) {
        this.state = spectator ? PlayerSessionState.SPECTATOR : PlayerSessionState.LOBBY;
        if (this.state == PlayerSessionState.LOBBY) {
            this.lobbyEnteredAt = System.currentTimeMillis();
        }
    }
}

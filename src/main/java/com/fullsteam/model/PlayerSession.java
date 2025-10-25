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
    private boolean isSpectator;

    public PlayerSession(int playerId, WebSocketSession session) {
        this.playerId = playerId;
        this.session = session;
        this.isSpectator = false;
        this.playerName = RandomNames.randomName();
    }
}



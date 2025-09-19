package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import io.micronaut.websocket.WebSocketSession;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractGameStateManager {
    protected static final Logger log = LoggerFactory.getLogger(AbstractGameStateManager.class);

    // Getters
    @Getter
    protected final String gameId;
    @Getter
    protected final String gameType;
    protected final GameEntities gameEntities = new GameEntities();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    protected long gameStartTime;
    protected boolean gameRunning = false;

    public AbstractGameStateManager(String gameId, String gameType) {
        this.gameId = gameId;
        this.gameType = gameType;
        this.gameStartTime = System.currentTimeMillis();
        scheduler.scheduleAtFixedRate(this::update, 500, 24, TimeUnit.MILLISECONDS); // ~60 FPS
    }

    public boolean addPlayer(PlayerSession playerSession) {
        if (gameEntities.getPlayers().size() >= getMaxPlayers()) {
            return false;
        }
        gameEntities.addPlayerSession(playerSession);
        onPlayerJoined(playerSession);
        return true;
    }

    public void removePlayer(int playerId) {
        PlayerSession removed = gameEntities.getPlayerSessions().remove(playerId);
        gameEntities.clearPlayerEntities(playerId);
        if (removed != null) {
            onPlayerLeft(removed);
        }
    }

    public void acceptPlayerInput(int playerId, PlayerInput input) {
        gameEntities.getPlayerInput().put(playerId, input);
    }

    public void handlePlayerConfigChange(int playerId, PlayerConfigRequest request) {
        PlayerSession playerSession = gameEntities.getPlayerSession(playerId);
        if (playerSession != null) {
            if (request.getPlayerName() != null) {
                playerSession.setPlayerName(request.getPlayerName());
            }
            processPlayerConfigChange(playerSession, request);
        }
    }

    public void send(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendSync(json);
        } catch (JsonProcessingException e) {
            log.error("Error serializing message", e);
        }
    }

    public void broadcast(Object message) {
        gameEntities.getPlayerSessions().values().forEach(player -> {
            if (player.getSession().isOpen()) {
                send(player.getSession(), message);
            }
        });
    }

    public GameInfo getGameInfo() {
        return new GameInfo(
                gameId,
                gameType,
                gameEntities.getPlayers().size(),
                getMaxPlayers(),
                gameStartTime,
                gameRunning ? "running" : "waiting"
        );
    }

    // Abstract methods to be implemented by specific game types
    protected abstract void update();

    protected abstract void onPlayerJoined(PlayerSession player);

    protected abstract void onPlayerLeft(PlayerSession player);

    protected abstract void processPlayerInput(Player player, PlayerInput input);

    protected abstract void processPlayerConfigChange(PlayerSession player, PlayerConfigRequest request);

    protected abstract int getMaxPlayers();

    public int getPlayerCount() {
        return gameEntities.getPlayers().size();
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}



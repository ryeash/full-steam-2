package com.fullsteam.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Message sent to a player when they successfully join a game.
 * Contains all static information the client needs to initialize properly.
 */
@Data
@Builder
public class PlayerJoinedMessage {
    @Builder.Default
    private String type = "playerJoined";
    
    // Player-specific information
    private int playerId;
    private String playerName;
    private int team;
    private double spawnX;
    private double spawnY;
    
    // World information
    private double worldWidth;
    private double worldHeight;
    private String terrain;
    private String biome;
    
    // Static game objects that don't change during gameplay
    private List<ObstacleData> obstacles;
    private List<StrategicLocationData> strategicLocations;
    
    // Game settings
    private int maxPlayers;
    private String gameMode;
    private Map<String, Object> gameSettings;
    
    @Data
    @Builder
    public static class ObstacleData {
        private int id;
        private double x;
        private double y;
        private double width;
        private double height;
        private String type;
        private double rotation;
    }
    
    @Data
    @Builder
    public static class StrategicLocationData {
        private int id;
        private double x;
        private double y;
        private String type;
        private int team;
        private Map<String, Object> properties;
    }
}

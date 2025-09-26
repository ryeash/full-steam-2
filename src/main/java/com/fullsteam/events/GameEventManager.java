package com.fullsteam.events;

import com.fullsteam.model.GameEvent;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import io.micronaut.websocket.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Manages the broadcasting of game events to appropriate players.
 * Handles targeting logic and message delivery through WebSockets.
 */
public class GameEventManager {
    
    private static final Logger log = LoggerFactory.getLogger(GameEventManager.class);
    
    private final GameEntities gameEntities;
    private final BiConsumer<WebSocketSession, Object> messageSender;
    
    public GameEventManager(GameEntities gameEntities, BiConsumer<WebSocketSession, Object> messageSender) {
        this.gameEntities = gameEntities;
        this.messageSender = messageSender;
    }
    
    /**
     * Broadcast a game event to the appropriate recipients based on its targeting
     */
    public void broadcastEvent(GameEvent event) {
        if (event == null || event.getTarget() == null) {
            log.warn("Attempted to broadcast null event or event with null target");
            return;
        }
        
        Set<Integer> targetPlayerIds = determineTargetPlayers(event.getTarget());
        
        // Send the event to each target player
        for (Integer playerId : targetPlayerIds) {
            PlayerSession playerSession = gameEntities.getPlayerSession(playerId);
            if (playerSession != null && playerSession.getSession().isOpen()) {
                try {
                    messageSender.accept(playerSession.getSession(), event);
                    log.debug("Sent event to player {}: {}", playerId, event.getMessage());
                } catch (Exception e) {
                    log.error("Failed to send event to player {}: {}", playerId, e.getMessage());
                }
            }
        }
        
        log.info("Broadcasted event to {} players: {}", targetPlayerIds.size(), event.getMessage());
    }
    
    /**
     * Broadcast multiple events in sequence
     */
    public void broadcastEvents(Collection<GameEvent> events) {
        if (events != null) {
            events.forEach(this::broadcastEvent);
        }
    }
    
    /**
     * Determine which player IDs should receive the event based on targeting
     */
    private Set<Integer> determineTargetPlayers(GameEvent.EventTarget target) {
        Set<Integer> targetPlayers = new HashSet<>();
        
        switch (target.getType()) {
            case ALL:
                // All active players
                targetPlayers.addAll(getAllActivePlayerIds());
                break;
                
            case TEAM:
                // Players on specific teams
                if (target.getTeamIds() != null) {
                    for (Integer teamId : target.getTeamIds()) {
                        targetPlayers.addAll(getPlayersOnTeam(teamId));
                    }
                }
                break;
                
            case SPECIFIC:
                // Specific players
                if (target.getPlayerIds() != null) {
                    targetPlayers.addAll(target.getPlayerIds());
                }
                break;
                
            case SPECTATORS:
                // Only spectators
                targetPlayers.addAll(getSpectatorPlayerIds());
                break;
                
            default:
                log.warn("Unknown target type: {}", target.getType());
                break;
        }
        
        // Remove excluded players
        if (target.getExcludePlayerIds() != null) {
            targetPlayers.removeAll(target.getExcludePlayerIds());
        }
        
        // Ensure all target players are actually active and have sessions
        targetPlayers.removeIf(playerId -> {
            PlayerSession session = gameEntities.getPlayerSession(playerId);
            return session == null || !session.getSession().isOpen();
        });
        
        return targetPlayers;
    }
    
    /**
     * Get all active player IDs
     */
    private Set<Integer> getAllActivePlayerIds() {
        Set<Integer> playerIds = new HashSet<>();
        for (PlayerSession session : gameEntities.getPlayerSessions().values()) {
            if (session.getSession().isOpen()) {
                playerIds.add(session.getPlayerId());
            }
        }
        return playerIds;
    }
    
    /**
     * Get player IDs for a specific team
     */
    private Set<Integer> getPlayersOnTeam(int teamId) {
        Set<Integer> teamPlayers = new HashSet<>();
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getTeam() == teamId) {
                PlayerSession session = gameEntities.getPlayerSession(player.getId());
                if (session != null && session.getSession().isOpen()) {
                    teamPlayers.add(player.getId());
                }
            }
        }
        return teamPlayers;
    }
    
    /**
     * Get spectator player IDs
     */
    private Set<Integer> getSpectatorPlayerIds() {
        Set<Integer> spectators = new HashSet<>();
        for (PlayerSession session : gameEntities.getPlayerSessions().values()) {
            if (session.isSpectator() && session.getSession().isOpen()) {
                spectators.add(session.getPlayerId());
            }
        }
        return spectators;
    }
    
    /**
     * Convenience method to broadcast a kill event
     */
    public void broadcastKill(String killerName, String victimName, String weaponName) {
        broadcastEvent(GameEvent.createKillEvent(killerName, victimName, weaponName));
    }
    
    /**
     * Convenience method to broadcast a capture event
     */
    public void broadcastCapture(String playerName, String locationName) {
        broadcastEvent(GameEvent.createCaptureEvent(playerName, locationName));
    }
    
    /**
     * Convenience method to broadcast a system message
     */
    public void broadcastSystemMessage(String message) {
        broadcastEvent(GameEvent.createSystemEvent(message));
    }
    
    /**
     * Convenience method to broadcast a team message
     */
    public void broadcastTeamMessage(String message, int teamId, GameEvent.EventCategory category) {
        broadcastEvent(GameEvent.createTeamEvent(message, teamId, category));
    }
    
    /**
     * Convenience method to broadcast to a specific player
     */
    public void broadcastToPlayer(String message, int playerId, GameEvent.EventCategory category) {
        broadcastEvent(GameEvent.createPlayerEvent(message, playerId, category));
    }
    
    /**
     * Convenience method to broadcast an achievement
     */
    public void broadcastAchievement(String playerName, String achievement) {
        broadcastEvent(GameEvent.createAchievementEvent(playerName, achievement));
    }
    
    /**
     * Convenience method to broadcast a custom message with color
     */
    public void broadcastCustomMessage(String message, String color, GameEvent.EventTarget target) {
        broadcastEvent(GameEvent.createCustomEvent(message, color, target));
    }
    
    /**
     * Get statistics about current game state for debugging
     */
    public String getEventSystemStatus() {
        int totalPlayers = getAllActivePlayerIds().size();
        int spectators = getSpectatorPlayerIds().size();
        
        StringBuilder status = new StringBuilder();
        status.append("GameEventManager Status:\n");
        status.append("  Active Players: ").append(totalPlayers).append("\n");
        status.append("  Spectators: ").append(spectators).append("\n");
        
        // Team breakdown
        Set<Integer> teams = new HashSet<>();
        for (Player player : gameEntities.getAllPlayers()) {
            teams.add(player.getTeam());
        }
        
        for (Integer teamId : teams) {
            int teamSize = getPlayersOnTeam(teamId).size();
            status.append("  Team ").append(teamId).append(": ").append(teamSize).append(" players\n");
        }
        
        return status.toString();
    }
}

package com.fullsteam.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Represents a game event that can be broadcast to players.
 * Events can be targeted to all players, specific teams, or individual players.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEvent {
    
    /**
     * The type of message for WebSocket routing
     */
    @Builder.Default
    private String type = "gameEvent";
    
    /**
     * The event message to display
     */
    private String message;
    
    /**
     * Hex color code for the message (e.g., "#FF0000" for red)
     */
    private String color;
    
    /**
     * Event category for styling and filtering
     */
    private EventCategory category;
    
    /**
     * Targeting information - who should receive this event
     */
    private EventTarget target;
    
    /**
     * Timestamp when the event occurred
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    /**
     * Optional duration in milliseconds for how long to display the event
     * If null or 0, uses default display duration
     */
    private Long displayDuration;
    
    /**
     * Categories of game events for styling and organization
     */
    public enum EventCategory {
        KILL("#FF4444"),           // Red for kills/deaths
        CAPTURE("#00FF88"),        // Green for objectives
        SYSTEM("#FFAA00"),         // Orange for system messages
        ACHIEVEMENT("#FFD700"),    // Gold for achievements
        WARNING("#FF8800"),        // Orange for warnings
        INFO("#00AAFF"),          // Blue for information
        CHAT("#FFFFFF");          // White for chat messages
        
        private final String defaultColor;
        
        EventCategory(String defaultColor) {
            this.defaultColor = defaultColor;
        }
        
        public String getDefaultColor() {
            return defaultColor;
        }
    }
    
    /**
     * Targeting information for event delivery
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventTarget {
        /**
         * Target type - determines who receives the event
         */
        private TargetType type;
        
        /**
         * Specific player IDs to target (for SPECIFIC type)
         */
        private Set<Integer> playerIds;
        
        /**
         * Specific team IDs to target (for TEAM type)
         */
        private Set<Integer> teamIds;
        
        /**
         * Exclude specific player IDs from receiving the event
         */
        private Set<Integer> excludePlayerIds;
        
        public enum TargetType {
            ALL,        // All players in the game
            TEAM,       // Specific team(s)
            SPECIFIC,   // Specific player(s)
            SPECTATORS  // Only spectators
        }
    }
    
    // Convenience factory methods for common event types
    
    /**
     * Create a kill event
     */
    public static GameEvent createKillEvent(String killerName, String victimName, String weaponName) {
        return GameEvent.builder()
                .message(String.format("%s eliminated %s with %s", killerName, victimName, weaponName))
                .category(EventCategory.KILL)
                .color(EventCategory.KILL.getDefaultColor())
                .target(EventTarget.builder().type(EventTarget.TargetType.ALL).build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create a capture event
     */
    public static GameEvent createCaptureEvent(String playerName, String locationName) {
        return GameEvent.builder()
                .message(String.format("%s captured %s", playerName, locationName))
                .category(EventCategory.CAPTURE)
                .color(EventCategory.CAPTURE.getDefaultColor())
                .target(EventTarget.builder().type(EventTarget.TargetType.ALL).build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create a team-specific event
     */
    public static GameEvent createTeamEvent(String message, int teamId, EventCategory category) {
        return GameEvent.builder()
                .message(message)
                .category(category)
                .color(category.getDefaultColor())
                .target(EventTarget.builder()
                        .type(EventTarget.TargetType.TEAM)
                        .teamIds(Set.of(teamId))
                        .build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create a system announcement
     */
    public static GameEvent createSystemEvent(String message) {
        return GameEvent.builder()
                .message(message)
                .category(EventCategory.SYSTEM)
                .color(EventCategory.SYSTEM.getDefaultColor())
                .target(EventTarget.builder().type(EventTarget.TargetType.ALL).build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create a player-specific event
     */
    public static GameEvent createPlayerEvent(String message, int playerId, EventCategory category) {
        return GameEvent.builder()
                .message(message)
                .category(category)
                .color(category.getDefaultColor())
                .target(EventTarget.builder()
                        .type(EventTarget.TargetType.SPECIFIC)
                        .playerIds(Set.of(playerId))
                        .build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create an achievement event
     */
    public static GameEvent createAchievementEvent(String playerName, String achievement) {
        return GameEvent.builder()
                .message(String.format("%s earned: %s", playerName, achievement))
                .category(EventCategory.ACHIEVEMENT)
                .color(EventCategory.ACHIEVEMENT.getDefaultColor())
                .target(EventTarget.builder().type(EventTarget.TargetType.ALL).build())
                .displayDuration(3000L)
                .build();
    }
    
    /**
     * Create a custom event with full control
     */
    public static GameEvent createCustomEvent(String message, String color, EventTarget target) {
        return GameEvent.builder()
                .message(message)
                .color(color)
                .category(EventCategory.INFO)
                .target(target)
                .displayDuration(3000L)
                .build();
    }
}

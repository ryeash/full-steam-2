package com.fullsteam.games;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class GameConfig {
    @Builder.Default
    private int maxPlayers = 10;
    @Builder.Default
    private int teamCount = 2; // 0 = FFA, 1 = invalid, 2-4 = team modes
    @Builder.Default
    private double worldWidth = 2000.0;
    @Builder.Default
    private double worldHeight = 2000.0;
    @Builder.Default
    private double playerSpeed = 150.0; // pixels per second
    @Builder.Default
    private double playerSize = 10.0;
    @Builder.Default
    private int strategicLocationsCount = 5;
    @Builder.Default
    private double captureRadius = 50.0;
    @Builder.Default
    private double captureTime = 3.0; // seconds
    
    // AI Management Settings
    @Builder.Default
    private boolean autoFillWithAI = true;
    @Builder.Default
    private double minAIFillPercentage = 0.4; // 40%
    @Builder.Default
    private double maxAIFillPercentage = 0.8; // 80%
    @Builder.Default
    private long aiCheckIntervalMs = 10000; // 10 seconds
    
    /**
     * Validate and normalize team count.
     * @param teamCount Raw team count
     * @return Validated team count (0 for FFA, 2-4 for teams)
     */
    public static int validateTeamCount(int teamCount) {
        if (teamCount < 0) return 0; // Negative becomes FFA
        if (teamCount == 1) return 2; // Single team becomes 2 teams
        if (teamCount > 4) return 4; // Cap at 4 teams
        return teamCount;
    }
    
    /**
     * Check if this configuration uses teams.
     * @return true if team-based, false if FFA
     */
    public boolean isTeamMode() {
        return teamCount >= 2;
    }
    
    /**
     * Check if this configuration is Free For All mode.
     * @return true if FFA, false if team-based
     */
    public boolean isFreeForAll() {
        return teamCount == 0;
    }
}



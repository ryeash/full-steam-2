package com.fullsteam.games;

import com.fullsteam.model.Rules;
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
    private double playerMaxHealth = 100.0;
    @Builder.Default
    private int strategicLocationsCount = 5;
    @Builder.Default
    private double captureRadius = 50.0;
    @Builder.Default
    private double captureTime = 3.0; // seconds

    // AI Management Settings
    @Builder.Default
    private long aiCheckIntervalMs = 10000; // 10 seconds
    
    // Game Rules (rounds, timing, etc.)
    @Builder.Default
    private Rules rules = Rules.builder().build();

    /**
     * Check if this configuration uses teams.
     *
     * @return true if team-based, false if FFA
     */
    public boolean isTeamMode() {
        return teamCount >= 2;
    }

    /**
     * Check if this configuration is Free For All mode.
     *
     * @return true if FFA, false if team-based
     */
    public boolean isFreeForAll() {
        return teamCount == 0;
    }
    
    /**
     * Check if this configuration uses rounds (timed gameplay).
     *
     * @return true if rounds are enabled, false for infinite gameplay
     */
    public boolean hasRounds() {
        return rules != null && rules.getRoundDuration() > 0;
    }
}



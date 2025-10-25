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
    private double playerMaxHealth = 100.0;
    @Builder.Default
    private long aiCheckIntervalMs = 10000;
    @Builder.Default
    private boolean enableAIFilling = true;
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
}



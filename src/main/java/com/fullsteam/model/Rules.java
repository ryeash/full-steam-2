package com.fullsteam.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class Rules {
    /**
     * Duration of each round in seconds. 0 = infinite/no rounds
     */
    @Builder.Default
    private double roundDuration = 120.0;
    
    /**
     * Rest period between rounds in seconds
     */
    @Builder.Default
    private double restDuration = 10.0;
    
    /**
     * Number of flags each team has to protect/capture. 0 = no flags (traditional deathmatch)
     */
    @Builder.Default
    private int flagsPerTeam = 0;
    
    /**
     * How team/player scores are calculated
     */
    @Builder.Default
    private ScoreStyle scoreStyle = ScoreStyle.TOTAL_KILLS;
    
    /**
     * Check if this game mode uses flags.
     */
    public boolean hasFlags() {
        return flagsPerTeam > 0;
    }
}

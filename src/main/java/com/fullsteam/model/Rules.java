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
     * How the game is won
     */
    @Builder.Default
    private VictoryCondition victoryCondition = VictoryCondition.ENDLESS;
    
    /**
     * Score limit for SCORE_LIMIT victory condition.
     * First team/player to reach this score wins.
     */
    @Builder.Default
    private int scoreLimit = 50;
    
    /**
     * Time limit for TIME_LIMIT victory condition in seconds.
     * Team/player with most points when time expires wins.
     */
    @Builder.Default
    private double timeLimit = 600.0; // 10 minutes default
    
    /**
     * Enable sudden death mode when game ends in a tie.
     * Next score wins if scores are equal.
     */
    @Builder.Default
    private boolean suddenDeath = false;
    
    // ===== Respawn Rules =====
    
    /**
     * How players respawn after death.
     */
    @Builder.Default
    private RespawnMode respawnMode = RespawnMode.INSTANT;
    
    /**
     * Delay in seconds before player respawns (for INSTANT mode).
     */
    @Builder.Default
    private double respawnDelay = 5.0;
    
    /**
     * Maximum lives per player. -1 = unlimited lives.
     * Used in LIMITED respawn mode.
     */
    @Builder.Default
    private int maxLives = -1;
    
    /**
     * Interval in seconds between wave respawns (for WAVE mode).
     * All dead players respawn together at this interval.
     */
    @Builder.Default
    private double waveRespawnInterval = 30.0;
    
    /**
     * Check if this game mode uses flags.
     */
    public boolean hasFlags() {
        return flagsPerTeam > 0;
    }
    
    /**
     * Check if players have limited lives.
     */
    public boolean hasLimitedLives() {
        return respawnMode == RespawnMode.LIMITED && maxLives > 0;
    }
    
    /**
     * Check if this mode uses wave respawns.
     */
    public boolean usesWaveRespawn() {
        return respawnMode == RespawnMode.WAVE;
    }
    
    /**
     * Check if players can respawn at all.
     */
    public boolean allowsRespawn() {
        return respawnMode != RespawnMode.ELIMINATION;
    }
    
    /**
     * Check if this game has a time limit.
     */
    public boolean hasTimeLimit() {
        return victoryCondition == VictoryCondition.TIME_LIMIT && timeLimit > 0;
    }
    
    /**
     * Check if this game has a score limit.
     */
    public boolean hasScoreLimit() {
        return victoryCondition == VictoryCondition.SCORE_LIMIT && scoreLimit > 0;
    }
}

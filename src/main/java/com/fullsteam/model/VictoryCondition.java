package com.fullsteam.model;

/**
 * Defines how a game is won.
 */
public enum VictoryCondition {
    /**
     * First team/player to reach the score limit wins.
     */
    SCORE_LIMIT,
    
    /**
     * Team/player with the most points when time expires wins.
     */
    TIME_LIMIT,
    
    /**
     * Last team/player standing wins (no respawns).
     */
    ELIMINATION,
    
    /**
     * Complete a specific objective (e.g., capture all flags, hold point).
     * Victory is determined by scoreStyle (kills/captures/total).
     */
    OBJECTIVE,
    
    /**
     * No victory condition - game continues indefinitely until manually ended.
     * Useful for casual/sandbox modes.
     */
    ENDLESS
}


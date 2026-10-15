package com.fullsteam.model;

/**
 * Defines how a game is won.
 */
public enum VictoryCondition {
    /**
     * First team/player to reach the score limit wins (with time limit cap).
     */
    SCORE_LIMIT,

    /**
     * Team/player with the most points when time expires wins.
     */
    TIME_LIMIT,

    /**
     * Last team/player standing wins (no respawns, with time limit cap). Pair with {@link RespawnMode#LIMITED}.
     */
    ELIMINATION
}


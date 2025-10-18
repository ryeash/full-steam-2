package com.fullsteam.model;

/**
 * Defines how team/player scores are calculated.
 */
public enum ScoreStyle {
    /**
     * Score based only on kills (traditional deathmatch)
     */
    TOTAL_KILLS,
    
    /**
     * Score based only on objectives (flag captures, KOTH zones, and any future objectives)
     */
    OBJECTIVE,
    
    /**
     * Score based on everything: kills + objectives (captures, KOTH, etc.)
     */
    TOTAL
}


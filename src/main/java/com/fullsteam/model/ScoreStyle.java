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
     * Score based only on flag captures
     */
    CAPTURES,
    
    /**
     * Score based only on KOTH zone control
     */
    KOTH_ZONES,
    
    /**
     * Score based on everything: kills + captures + KOTH zones + any future scoring mechanisms
     */
    TOTAL
}


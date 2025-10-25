package com.fullsteam.model;

/**
 * Represents the current state of the game for round-based gameplay.
 */
public enum GameState {
    /**
     * Normal gameplay is active
     */
    PLAYING,
    
    /**
     * Round has ended, displaying scores
     */
    ROUND_END,
    
    /**
     * Rest period between rounds
     */
    REST_PERIOD
}


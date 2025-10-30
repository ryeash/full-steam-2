package com.fullsteam.model;

/**
 * Defines the density of obstacles in terrain generation.
 */
public enum ObstacleDensity {
    /**
     * Fewer obstacles, more open space for movement and combat.
     * Multiplier: 0.6-0.9x base density
     */
    SPARSE,
    
    /**
     * Normal obstacle density for balanced gameplay.
     * Multiplier: 1.2-1.8x base density
     */
    DENSE,
    
    /**
     * Many obstacles creating a cramped, cover-heavy battlefield.
     * Multiplier: 2.0-3.0x base density
     */
    CHOKED,
    
    /**
     * Randomly select one of the above densities each game.
     * Provides variety across different matches.
     */
    RANDOM
}



package com.fullsteam.model;

/**
 * Defines the density of environmental event instances.
 * Controls how many impact zones/locations spawn for each event type,
 * scaling with map size for consistent gameplay experience.
 */
public enum EventDensity {
    /**
     * Fewer event instances, more breathing room.
     * Multiplier: 0.6-0.9x base density
     */
    SPARSE,
    
    /**
     * Normal event density for balanced gameplay.
     * Multiplier: 1.2-1.8x base density
     */
    DENSE,
    
    /**
     * Many event instances creating intense, chaotic moments.
     * Multiplier: 2.0-3.0x base density
     */
    CHOKED,
    
    /**
     * Randomly select one of the above densities each time the event triggers.
     * Provides variety and unpredictability.
     */
    RANDOM;
    
    /**
     * Get a random multiplier for this density level.
     * 
     * @return A multiplier to apply to base event count
     */
    public double getMultiplier() {
        return switch (this) {
            case SPARSE -> 0.6 + (Math.random() * 0.3); // 0.6-0.9
            case DENSE -> 1.2 + (Math.random() * 0.6);  // 1.2-1.8
            case CHOKED -> 2.0 + (Math.random() * 1.0); // 2.0-3.0
            case RANDOM -> {
                // Pick a random density and get its multiplier
                EventDensity[] options = {SPARSE, DENSE, CHOKED};
                EventDensity chosen = options[(int) (Math.random() * options.length)];
                yield chosen.getMultiplier();
            }
        };
    }
}


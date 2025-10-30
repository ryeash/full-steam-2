package com.fullsteam.model;

public enum EntityWorldDensity {
    /**
     * Fewer event instances, more breathing room.
     * Multiplier: 0.6-0.9x base density
     */
    SPARSE(0.6, 0.9),

    /**
     * Normal event density for balanced gameplay.
     * Multiplier: 1.2-1.8x base density
     */
    DENSE(1.2, 1.8),

    /**
     * Many event instances creating intense, chaotic moments.
     * Multiplier: 2.0-3.0x base density
     */
    CHOKED(2.0, 3.0),

    /**
     * Randomly select one of the above densities each time the event triggers.
     * Provides variety and unpredictability.
     */
    RANDOM(0.6, 3.0);

    private final double min;
    private final double max;

    EntityWorldDensity(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Get a random multiplier for this density level.
     *
     * @return A multiplier to apply to base event count
     */
    public double getMultiplier() {
        double maxMult = max - min;
        return min + (Math.random() * maxMult);
    }
}

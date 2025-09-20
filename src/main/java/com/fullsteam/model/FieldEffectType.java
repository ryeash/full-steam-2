package com.fullsteam.model;

public enum FieldEffectType {
    EXPLOSION(0.5, true),      // Short duration, instant damage
    FIRE(3.0, false),          // Long duration, damage over time
    ELECTRIC(1.0, false),      // Medium duration, chain damage
    FREEZE(2.0, false),        // Medium duration, slowing effect
    FRAGMENTATION(0.3, true),  // Very short, creates multiple projectiles
    POISON(4.0, false);        // Long duration, damage over time

    private final double defaultDuration;
    private final boolean instantaneous;

    FieldEffectType(double defaultDuration, boolean instantaneous) {
        this.defaultDuration = defaultDuration;
        this.instantaneous = instantaneous;
    }

    public double getDefaultDuration() {
        return defaultDuration;
    }

    public boolean isInstantaneous() {
        return instantaneous;
    }
}

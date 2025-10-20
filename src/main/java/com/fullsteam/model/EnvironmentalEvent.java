package com.fullsteam.model;

/**
 * Types of random environmental events that can occur during gameplay.
 * These create hazards and dynamic moments like meteor showers and supply drops.
 */
public enum     EnvironmentalEvent {
    METEOR_SHOWER("Meteor Shower", "☄️", 15.0),
    SUPPLY_DROP("Supply Drop", "📦", 30.0),
    VOLCANIC_ERUPTION("Volcanic Eruption", "🌋", 20.0),
    EARTHQUAKE("Earthquake", "🌊", 12.0),
    SOLAR_FLARE("Solar Flare", "☀️", 10.0),  // Reduces visibility/adds damage
    ION_STORM("Ion Storm", "⚡", 15.0),      // Electric field effects
    BLIZZARD("Blizzard", "❄️", 20.0);       // Freeze effects and reduced movement

    private final String displayName;
    private final String icon;
    private final double baseDuration;

    EnvironmentalEvent(String displayName, String icon, double baseDuration) {
        this.displayName = displayName;
        this.icon = icon;
        this.baseDuration = baseDuration;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public double getBaseDuration() {
        return baseDuration;
    }

    public String getAnnouncementMessage() {
        return icon + " " + displayName + " incoming!";
    }
}



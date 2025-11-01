package com.fullsteam.model;

/**
 * Types of random environmental events that can occur during gameplay.
 * These create hazards and dynamic moments like meteor showers and supply drops.
 */
public enum EnvironmentalEvent {
    METEOR_SHOWER("Meteor Shower", "☄️", 15.0, 5000),
    SUPPLY_DROP("Supply Drop", "📦", 30.0, 5000),
    VOLCANIC_ERUPTION("Volcanic Eruption", "🌋", 20.0, 2000),
    EARTHQUAKE("Earthquake", "🌊", 12.0, 1000),
    ION_STORM("Ion Storm", "⚡", 15.0, 600),      // Electric field effects
    BLIZZARD("Blizzard", "❄️", 20.0, 4000);       // Freeze effects and reduced movement

    private final String displayName;
    private final String icon;
    private final double baseDuration;
    private final long staggerTime;

    EnvironmentalEvent(String displayName, String icon, double baseDuration, long staggerTime) {
        this.displayName = displayName;
        this.icon = icon;
        this.baseDuration = baseDuration;
        this.staggerTime = staggerTime;
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

    public long getStaggerTime() {
        return staggerTime;
    }

    public String getAnnouncementMessage() {
        return icon + " " + displayName + " incoming!";
    }
}



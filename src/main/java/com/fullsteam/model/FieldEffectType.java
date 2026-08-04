package com.fullsteam.model;

import lombok.Getter;

@Getter
public enum FieldEffectType {
    // Combat effects
    EXPLOSION(0.5, true),                       // Short duration, instant damage
    FIRE(3.0, false),                           // Long duration, damage over time
    ELECTRIC(1.0, false),                       // Medium duration, chain damage
    FREEZE(2.0, false),                         // Medium duration, slowing effect
    FRAGMENTATION(0.3, true),                   // Very short, creates multiple projectiles
    POISON(4.0, false),                         // Long duration, damage over time
    LASER(Ordinance.LASER.getBeamDuration(), true),          // Short instant beam damage
    PLASMA(Ordinance.PLASMA_BEAM.getBeamDuration(), false),  // damage over time beam damage

    // Utility effects
    HEAL_ZONE(5.0, false),     // Continuous healing area for allies
    SLOW_FIELD(6.0, false),    // Movement reduction field
    SHIELD_BARRIER(8.0, false), // Damage absorption zone
    GRAVITY_WELL(7.0, false),  // Pull entities toward center
    SPEED_BOOST(7.0, false),   // Increases ally movement speed
    PROXIMITY_MINE(15.0, false), // Proximity-triggered explosive mine

    SMOKE(5.0, false),         // Vision-blocking smoke cloud (no damage, affects all players)

    // Event/Hazard effects
    WARNING_ZONE(3.0, false),  // Visual indicator for incoming hazard (no damage)
    EARTHQUAKE(4.0, false);    // Ground shake with damage over time

    private final double defaultDuration;
    private final boolean instantaneous;

    FieldEffectType(double defaultDuration, boolean instantaneous) {
        this.defaultDuration = defaultDuration;
        this.instantaneous = instantaneous;
    }

    public double maxRadius(double radius) {
        return switch (this) {
            case FIRE -> radius * 1.75;
            case POISON -> radius * 2.0;
            case SMOKE -> radius * 1.25;
            default -> radius;
        };
    }
}

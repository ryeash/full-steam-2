package com.fullsteam.model;

import lombok.Getter;

@Getter
public enum Ordinance {
    PROJECTILE(0, "Standard projectile", 1.0, 0.7, 50.0, 0.0),
    LASER(50, "Instant-hit beam weapon", 2.0, 0.8, 0.0, 0.3),
    PLASMA_BEAM(45, "Continuous damage beam", 1.5, 1.0, 0.0, 0.8);

    private final int pointCost;
    private final String description;
    private final double speedMultiplier; // Affects projectile speed
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect
    private final double minimumVelocity;
    private final double beamDuration; // How long the beam lasts (seconds)

    Ordinance(int pointCost,
              String description,
              double speedMultiplier,
              double areaOfEffectModification,
              double minimumVelocity,
              double beamDuration) {
        this.pointCost = pointCost;
        this.description = description;
        this.speedMultiplier = speedMultiplier;
        this.areaOfEffectModification = areaOfEffectModification;
        this.minimumVelocity = minimumVelocity;
        this.beamDuration = beamDuration;
    }

    /**
     * Check if this ordinance type creates beams instead of projectiles
     */
    public boolean isBeamType() {
        return this == LASER || this == PLASMA_BEAM;
    }
}

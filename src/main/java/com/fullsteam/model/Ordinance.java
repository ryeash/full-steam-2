package com.fullsteam.model;

import lombok.Getter;

@Getter
public enum Ordinance {
    // Three firing modes. Projectile flavor (size, speed, AOE) now comes from the
    // weapon's attributes (CALIBER, PROJECTILE_SPEED) and bullet effects rather
    // than bundled sub-types — the legacy BULLET/ROCKET/GRENADE/PLASMA/DART types
    // collapsed into the single neutral PROJECTILE.

    // Projectile-based: a standard solid round. Its size comes entirely from the
    // weapon's CALIBER attribute (there is no per-ordinance size).
    PROJECTILE(0, "Standard projectile", 1.0, false, 0.7, 50.0, null, 0.0, 0.0),

    // Beam-based ordinance
    LASER(50, "Instant-hit beam weapon", 2.0, false, 0.8, 0.0, DamageApplicationType.INSTANT, 0.0, 0.3),
    PLASMA_BEAM(45, "Continuous damage beam", 1.5, false, 1.0, 0.0, DamageApplicationType.DAMAGE_OVER_TIME, 0.1, 0.8);

    private final int pointCost;
    private final String description;
    private final double speedMultiplier; // Affects projectile speed
    private final boolean hasTrail; // Whether to render a trail effect
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect
    private final double minimumVelocity;
    private final DamageApplicationType damageApplicationType; // null for projectiles
    private final double damageInterval; // For DOT beams (seconds between damage applications)
    private final double beamDuration; // How long the beam lasts (seconds)

    Ordinance(int pointCost,
              String description,
              double speedMultiplier,
              boolean hasTrail,
              double areaOfEffectModification,
              double minimumVelocity,
              DamageApplicationType damageApplicationType,
              double damageInterval,
              double beamDuration) {
        this.pointCost = pointCost;
        this.description = description;
        this.speedMultiplier = speedMultiplier;
        this.hasTrail = hasTrail;
        this.areaOfEffectModification = areaOfEffectModification;
        this.minimumVelocity = minimumVelocity;
        this.damageApplicationType = damageApplicationType;
        this.damageInterval = damageInterval;
        this.beamDuration = beamDuration;
    }

    /**
     * Check if this ordinance type creates beams instead of projectiles
     */
    public boolean isBeamType() {
        return damageApplicationType != null;
    }
}

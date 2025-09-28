package com.fullsteam.model;

public enum Ordinance {
    // Projectile-based ordinance
    BULLET(0, "Standard projectile", 2.0, 1.0, false, 0.3, 50.0, null, 0.0, 0.0),
    ROCKET(20, "High-speed explosive projectile", 4.0, 1.2, true, 1.4, 50.0, null, 0.0, 0.0),
    GRENADE(10, "Arcing explosive projectile", 3.0, 0.6, true, 1.2, 20.0, null, 0.0, 0.0),
    PLASMA(15, "Energy-based projectile", 2.5, 1.1, false, 0.8, 40.0, null, 0.0, 0.0),
    CANNONBALL(12, "Heavy, slow projectile", 6.0, 0.4, false, 1.0, 30.0, null, 0.0, 0.0),
    DART(5, "Small, fast projectile", 1.5, 1.4, false, 0.1, 50.0, null, 0.0, 0.0),
    FLAMETHROWER(8, "Short-range fire stream", 3.5, 0.8, false, 0.4, 10.0, null, 0.0, 0.0),
    
    // Beam-based ordinance
    LASER(25, "Instant-hit beam weapon", 1.0, 2.0, false, 0.8, 0.0, DamageApplicationType.INSTANT, 0.0, 1.5),
    PLASMA_BEAM(20, "Continuous damage beam", 1.5, 1.5, true, 1.0, 0.0, DamageApplicationType.DAMAGE_OVER_TIME, 0.1, 2.0),
    HEAL_BEAM(15, "Continuous healing beam", 1.0, 1.0, true, 1.2, 0.0, DamageApplicationType.DAMAGE_OVER_TIME, 0.05, 3.0),
    RAILGUN(30, "Piercing instant beam", 0.5, 3.0, false, 0.5, 0.0, DamageApplicationType.INSTANT, 0.0, 0.8),
    PULSE_LASER(22, "Burst damage beam", 1.2, 1.8, true, 0.9, 0.0, DamageApplicationType.BURST, 0.2, 1.5),
    ARC_BEAM(18, "Chain lightning beam", 1.3, 1.6, true, 1.1, 0.0, DamageApplicationType.BURST, 0.3, 2.5);

    private final int pointCost;
    private final String description;
    private final double size; // Radius for rendering and physics
    private final double speedMultiplier; // Affects projectile speed
    private final boolean hasTrail; // Whether to render a trail effect
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect
    private final double minimumVelocity;
    
    // Beam-specific properties
    private final DamageApplicationType damageApplicationType; // null for projectiles
    private final double damageInterval; // For DOT/BURST beams (seconds between damage applications)
    private final double beamDuration; // How long the beam lasts (seconds)

    Ordinance(int pointCost, String description, double size, double speedMultiplier, boolean hasTrail, 
              double areaOfEffectModification, double minimumVelocity, DamageApplicationType damageApplicationType, 
              double damageInterval, double beamDuration) {
        this.pointCost = pointCost;
        this.description = description;
        this.size = size;
        this.speedMultiplier = speedMultiplier;
        this.hasTrail = hasTrail;
        this.areaOfEffectModification = areaOfEffectModification;
        this.minimumVelocity = minimumVelocity;
        this.damageApplicationType = damageApplicationType;
        this.damageInterval = damageInterval;
        this.beamDuration = beamDuration;
    }

    public int getPointCost() {
        return pointCost;
    }

    public String getDescription() {
        return description;
    }

    public double getSize() {
        return size;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public boolean hasTrail() {
        return hasTrail;
    }

    public double getAreaOfEffectModification() {
        return areaOfEffectModification;
    }

    public double getMinimumVelocity() {
        return minimumVelocity;
    }

    public DamageApplicationType getDamageApplicationType() {
        return damageApplicationType;
    }

    public double getDamageInterval() {
        return damageInterval;
    }

    public double getBeamDuration() {
        return beamDuration;
    }

    /**
     * Check if this ordinance type creates beams instead of projectiles
     */
    public boolean isBeamType() {
        return damageApplicationType != null;
    }
}

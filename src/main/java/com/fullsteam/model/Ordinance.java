package com.fullsteam.model;

public enum Ordinance {
    // Projectile-based ordinance
    BULLET(0, "Standard projectile", 2.0, 1.0, false, 0.3, 50.0, null, 0.0, 0.0),
    ROCKET(20, "High-speed explosive projectile", 4.0, 1.2, true, 1.4, 50.0, null, 0.0, 0.0),
    GRENADE(10, "Arcing explosive projectile", 3.0, 0.6, true, 1.2, 20.0, null, 0.0, 0.0),
    PLASMA(15, "Energy-based projectile", 2.5, 1.1, false, 0.8, 40.0, null, 0.0, 0.0),
    DART(5, "Small, fast projectile", 1.5, 1.4, false, 0.1, 50.0, null, 0.0, 0.0),
    FLAMETHROWER(8, "Short-range fire stream", 3.5, 0.8, false, 0.4, 10.0, null, 0.0, 0.0),
    
    // Beam-based ordinance
    LASER(50, "Instant-hit beam weapon", 1.0, 2.0, false, 0.8, 0.0, DamageApplicationType.INSTANT, 0.0, 0.3),
    PLASMA_BEAM(45, "Continuous damage beam", 1.5, 1.5, false, 1.0, 0.0, DamageApplicationType.DAMAGE_OVER_TIME, 0.1, 0.8),
    HEAL_BEAM(35, "Continuous healing beam", 1.0, 1.0, false, 1.2, 0.0, DamageApplicationType.DAMAGE_OVER_TIME, 0.05, 1.0),
    RAILGUN(55, "Piercing instant beam", 0.5, 3.0, false, 0.5, 0.0, DamageApplicationType.INSTANT, 0.0, 0.2);

    private final int pointCost;
    private final String description;
    private final double size; // Radius for rendering and physics
    private final double speedMultiplier; // Affects projectile speed
    private final boolean hasTrail; // Whether to render a trail effect
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect
    private final double minimumVelocity;
    
    // Beam-specific properties
    private final DamageApplicationType damageApplicationType; // null for projectiles
    private final double damageInterval; // For DOT beams (seconds between damage applications)
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

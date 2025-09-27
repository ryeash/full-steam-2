package com.fullsteam.model;

public enum Ordinance {
    BULLET(0, "Standard projectile", 2.0, 1.0, false, 0.3, 50.0),
    ROCKET(20, "High-speed explosive projectile", 4.0, 1.2, true, 1.4, 50.0),
    GRENADE(10, "Arcing explosive projectile", 3.0, 0.6, true, 1.2, 20.0),
    PLASMA(15, "Energy-based projectile", 2.5, 1.1, false, 0.8, 40.0),
    CANNONBALL(12, "Heavy, slow projectile", 6.0, 0.4, false, 1.0, 30.0),
    DART(5, "Small, fast projectile", 1.5, 1.4, false, 0.1, 50.0),
    FLAMETHROWER(8, "Short-range fire stream", 3.5, 0.8, false, 0.4, 10.0);
    //    LASER(25, "Instant-hit beam weapon", 1.0, 2.0, false),

    private final int pointCost;
    private final String description;
    private final double size; // Radius for rendering and physics
    private final double speedMultiplier; // Affects projectile speed
    private final boolean hasTrail; // Whether to render a trail effect
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect
    private final double minimumVelocity;

    Ordinance(int pointCost, String description, double size, double speedMultiplier, boolean hasTrail, double areaOfEffectModification, double minimumVelocity) {
        this.pointCost = pointCost;
        this.description = description;
        this.size = size;
        this.speedMultiplier = speedMultiplier;
        this.hasTrail = hasTrail;
        this.areaOfEffectModification = areaOfEffectModification;
        this.minimumVelocity = minimumVelocity;
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
}

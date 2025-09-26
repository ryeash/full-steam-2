package com.fullsteam.model;

public enum Ordinance {
    BULLET(0, "Standard projectile", 2.0, 1.0, false, 0.4),
    ROCKET(20, "High-speed explosive projectile", 4.0, 1.5, true, 1.4),
    GRENADE(10, "Arcing explosive projectile", 3.0, 0.6, true, 1.2),
    PLASMA(15, "Energy-based projectile", 2.5, 1.2, false, 0.8),
    //    LASER(25, "Instant-hit beam weapon", 1.0, 2.0, false),
    CANNONBALL(12, "Heavy, slow projectile", 6.0, 0.4, false, 1.0),
    DART(5, "Small, fast projectile", 1.5, 1.8, false, 0.3),
    FLAMETHROWER(8, "Short-range fire stream", 3.5, 0.8, false, 0.6);

    private final int pointCost;
    private final String description;
    private final double size; // Radius for rendering and physics
    private final double speedMultiplier; // Affects projectile speed
    private final boolean hasTrail; // Whether to render a trail effect
    private final double areaOfEffectModification; // how much the ordinance alters the size of the AOE bullet effect

    Ordinance(int pointCost, String description, double size, double speedMultiplier, boolean hasTrail, double areaOfEffectModification) {
        this.pointCost = pointCost;
        this.description = description;
        this.size = size;
        this.speedMultiplier = speedMultiplier;
        this.hasTrail = hasTrail;
        this.areaOfEffectModification = areaOfEffectModification;
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
}

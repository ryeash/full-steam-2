package com.fullsteam.model;

public enum BulletEffect {
    EXPLODES_ON_IMPACT(25, "Projectiles explode on impact, dealing area damage"),
    BOUNCY(15, "Projectiles bounce off obstacles instead of stopping"),
    PIERCING(20, "Projectiles pass through enemies, hitting multiple targets"),
    INCENDIARY(18, "Projectiles set targets on fire, dealing damage over time"),
    FRAGMENTING(22, "Projectiles split into multiple smaller projectiles on impact"),
    HOMING(30, "Projectiles slightly track towards nearby enemies"),
    ELECTRIC(16, "Projectiles chain lightning damage to nearby enemies"),
    POISON(18, "Projectiles release poison gas, dealing area damage over time"),
    FREEZING(14, "Projectiles slow down hit targets temporarily");

    private final int pointCost;
    private final String description;

    BulletEffect(int pointCost, String description) {
        this.pointCost = pointCost;
        this.description = description;
    }

    public int getPointCost() {
        return pointCost;
    }

    public String getDescription() {
        return description;
    }
}

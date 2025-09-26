package com.fullsteam.model;

public enum BulletEffect {
    // AOE
    EXPLOSIVE(25, "Projectiles explode on impact, dealing area damage", 50.0, 1.5, 1.0),
    INCENDIARY(18, "Projectiles set targets on fire, dealing damage over time", 40.0, 0.6, 1.0),
    ELECTRIC(16, "Projectiles chain lightning damage to nearby enemies", 60.0, 0.8, 1.0),
    FREEZING(14, "Projectiles slow down hit targets temporarily", 35.0, 0.2, 1.0),
    POISON(18, "Projectiles release poison gas, dealing area damage over time", 70.0, 0.5, 1.0),

    // Special
    BOUNCY(15, "Projectiles bounce off obstacles instead of stopping", 0, 1.0, 1.0),
    PIERCING(20, "Projectiles pass through enemies, hitting multiple targets", 0, 1.0, 1.0),
    FRAGMENTING(22, "Projectiles split into multiple smaller projectiles on impact", 60, 0.0, 0.0),
    HOMING(30, "Projectiles slightly track towards nearby enemies", 0, 1.0, 1.0),
    ;

    private final int pointCost;
    private final String description;

    // these only apply to AOE effects
    // size of the effected area
    private final double baseRadius;
    // how much projectile damage the effect carries into it's area
    private final double damageModification;
    // how much the projectile damage effects the radius
    private final double damageModificationForSize;

    BulletEffect(int pointCost, String description, double baseRadius, double damageModification, double damageModificationForSize) {
        this.pointCost = pointCost;
        this.description = description;
        this.baseRadius = baseRadius;
        this.damageModification = damageModification;
        this.damageModificationForSize = damageModificationForSize;
    }

    public int getPointCost() {
        return pointCost;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseRadius() {
        return baseRadius;
    }

    public double getDamageModification() {
        return damageModification;
    }

    public double calculateRadius(double damage, Ordinance ordinance) {
        return (this.baseRadius * ordinance.getAreaOfEffectModification()) + (damage * damageModificationForSize);
    }

    public double calculateDamage(double damage) {
        return damage * damageModification;
    }
}

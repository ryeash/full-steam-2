package com.fullsteam.model;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum BulletEffect {
    // dangerous after dismissal effects
    EXPLOSIVE(25, "Projectiles explode on impact, dealing area damage", 50.0, 1.5, 1.2, true, true, FieldEffectType.EXPLOSION),
    INCENDIARY(18, "Projectiles set targets on fire, dealing damage over time", 40.0, 0.6, 1.0, true, true, FieldEffectType.FIRE),
    ELECTRIC(16, "Projectiles chain lightning damage to nearby enemies", 60.0, 0.8, 1.1, true, true, FieldEffectType.ELECTRIC),
    FREEZING(14, "Projectiles slow down hit targets temporarily", 35.0, 0.2, 1.0, true, true, FieldEffectType.FREEZE),
    POISON(22, "Projectiles release poison gas, dealing area damage over time", 50.0, 0.5, .9, true, true, FieldEffectType.POISON),

    // special ordinance behaviors
    BOUNCY(15, "Projectiles bounce off obstacles; beams reflect off walls", 0, 1.0, 1.0, true, true, null),
    PIERCING(20, "Projectiles pass through enemies, hitting multiple targets", 0, 1.0, 1.0, true, true, null),
    FRAGMENTING(22, "Projectiles split into multiple smaller projectiles on impact", 20, 0.0, 0.0, false, true, null),
    HOMING(30, "Projectiles track towards nearby enemies", 0, 1.0, 1.0, false, true, null),

    // utility-only effects
    STRIKE(0, "Calls in a delayed explosive strike where the projectile lands", 0, 0.0, 0.0, false, false, null),
    SMOKE(0, "Projectiles create a vision-blocking smoke cloud on impact", 125.0, 0.0, 0.0, true, false, FieldEffectType.SMOKE);

    private final int pointCost;
    private final String description;
    private final double baseRadius;
    private final double damageModification;
    private final double damageModificationForSize;
    private final boolean validForBeams;
    private final boolean selectable;
    private final FieldEffectType effectType;

    BulletEffect(int pointCost, String description, double baseRadius, double damageModification, double damageModificationForSize, boolean validForBeams, boolean selectable, FieldEffectType effectType) {
        this.pointCost = pointCost;
        this.description = description;
        this.baseRadius = baseRadius;
        this.damageModification = damageModification;
        this.damageModificationForSize = damageModificationForSize;
        this.validForBeams = validForBeams;
        this.selectable = selectable;
        this.effectType = effectType;
    }

    public double calculateRadius(double damage, Ordinance ordinance, double caliber) {
        // Bigger-caliber rounds produce proportionally bigger area effects.
        return ((this.baseRadius * ordinance.getAreaOfEffectModification()) + (Math.sqrt(damage) * damageModificationForSize)) * caliber;
    }

    public double calculateDamage(double damage) {
        return damage * damageModification;
    }

    /**
     * Filter a set of effects down to those meaningful for the given ordnance.
     * For beam ordnance this drops the flight-only behaviors (FRAGMENTING/
     * HOMING); projectiles keep everything. Single source of truth used by both
     * the customizer gating and server-side weapon construction.
     */
    public static Set<BulletEffect> validFor(Ordinance ordinance, Set<BulletEffect> effects) {
        if (ordinance == null || !ordinance.isBeamType()) {
            return effects;
        }
        return effects.stream()
                .filter(BulletEffect::isValidForBeams)
                .collect(Collectors.toSet());
    }
}

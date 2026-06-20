package com.fullsteam.model;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;

public enum BulletEffect {
    // AOE — these spawn a field effect at the impact point. They apply to both
    // projectiles and beams (the last flag, validForBeams).
    EXPLOSIVE(25, "Projectiles explode on impact, dealing area damage", 50.0, 1.5, 1.0, true),
    INCENDIARY(18, "Projectiles set targets on fire, dealing damage over time", 40.0, 0.6, 1.0, true),
    ELECTRIC(16, "Projectiles chain lightning damage to nearby enemies", 60.0, 0.8, 1.0, true),
    FREEZING(14, "Projectiles slow down hit targets temporarily", 35.0, 0.2, 1.0, true),
    POISON(22, "Projectiles release poison gas, dealing area damage over time", 50.0, 0.5, 1.0, true),
    SMOKE(0, "Projectiles create a vision-blocking smoke cloud on impact", 60.0, 0.0, 0.0, true),

    // Special — behavioral. PIERCING and BOUNCY both work for beams: piercing
    // controls beam pass-through, and bouncy reflects the beam off obstacles into
    // a multi-segment path. FRAGMENTING/HOMING are flight behaviors with no meaning
    // for an instant-hit ray, so they remain forbidden on beam ordnance.
    BOUNCY(15, "Projectiles bounce off obstacles; beams reflect off walls", 0, 1.0, 1.0, true),
    PIERCING(20, "Projectiles pass through enemies, hitting multiple targets", 0, 1.0, 1.0, true),
    FRAGMENTING(22, "Projectiles split into multiple smaller projectiles on impact", 20, 0.0, 0.0, false),
    HOMING(30, "Projectiles slightly track towards nearby enemies", 0, 1.0, 1.0, false);

    @Getter
    private final int pointCost;
    @Getter
    private final String description;

    // these only apply to AOE effects
    // size of the effected area
    @Getter
    private final double baseRadius;
    // how much projectile damage the effect carries into it's area
    @Getter
    private final double damageModification;
    // how much the projectile damage effects the radius
    private final double damageModificationForSize;
    // whether this effect is meaningful on beam ordnance (laser / plasma beam)
    @Getter
    private final boolean validForBeams;

    BulletEffect(int pointCost, String description, double baseRadius, double damageModification, double damageModificationForSize, boolean validForBeams) {
        this.pointCost = pointCost;
        this.description = description;
        this.baseRadius = baseRadius;
        this.damageModification = damageModification;
        this.damageModificationForSize = damageModificationForSize;
        this.validForBeams = validForBeams;
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
     * For beam ordnance this drops the flight-only behaviors (BOUNCY/FRAGMENTING/
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

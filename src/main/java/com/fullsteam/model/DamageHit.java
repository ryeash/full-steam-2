package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;

/**
 * Represents a damage event / hit instance for client UI display (e.g. floating damage numbers).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class DamageHit implements Comparable<DamageHit> {
    private static final Comparator<DamageHit> comparator = Comparator.comparing(DamageHit::getX)
            .thenComparing(DamageHit::getY)
            .thenComparing(DamageHit::getDamage)
            .thenComparing(DamageHit::getAttackerId)
            .thenComparing(DamageHit::getVictimId)
            .thenComparing(DamageHit::isKill)
            .thenComparing(DamageHit::isArmorMitigated);

    private double x;
    private double y;
    private double damage;
    private int attackerId;
    private int victimId;
    private boolean kill;
    private boolean armorMitigated;

    public DamageHit(double x, double y, double damage, int attackerId, int victimId, boolean kill) {
        this(x, y, damage, attackerId, victimId, kill, false);
    }

    @Override
    public int compareTo(DamageHit o) {
        return comparator.compare(this, o);
    }
}

package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a damage event / hit instance for client UI display (e.g. floating damage numbers).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class DamageHit {
    private double x;
    private double y;
    private double damage;
    private int attackerId;
    private int victimId;
    private boolean kill;
}

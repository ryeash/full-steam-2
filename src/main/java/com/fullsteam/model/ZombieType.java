package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;

@Getter
@Introspected
public enum ZombieType {
    WALKER(75.0, 120.0, 15.0, 130.0, 1.5, 20.0, 0.6),
    RUNNER(50.0, 190.0, 13.0, 160.0, 1.6, 15.0, 0.5),
    TANK(200.0, 80.0, 22.0, 100.0, 1.3, 35.0, 0.9),
    LUNGER(65.0, 135.0, 14.0, 220.0, 2.2, 22.0, 0.6),
    STALKER(85.0, 145.0, 15.0, 150.0, 1.7, 24.0, 0.5);

    private final double defaultHealth;
    private final double defaultSpeed;
    private final double radius;
    private final double defaultLungeDistance;
    private final double lungeSpeedMultiplier;
    private final double defaultMeleeDamage;
    private final double meleeCooldownSeconds;

    ZombieType(double defaultHealth,
               double defaultSpeed,
               double radius,
               double defaultLungeDistance,
               double lungeSpeedMultiplier,
               double defaultMeleeDamage,
               double meleeCooldownSeconds) {
        this.defaultHealth = defaultHealth;
        this.defaultSpeed = defaultSpeed;
        this.radius = radius;
        this.defaultLungeDistance = defaultLungeDistance;
        this.lungeSpeedMultiplier = lungeSpeedMultiplier;
        this.defaultMeleeDamage = defaultMeleeDamage;
        this.meleeCooldownSeconds = meleeCooldownSeconds;
    }
}

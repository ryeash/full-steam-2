package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum ZombieIntensity {
    LOW(10, 5, 25.0, 0.6),
    MEDIUM(20, 10, 20.0, 1.0),
    HIGH(35, 18, 15.0, 1.6),
    NIGHTMARE(55, 28, 10.0, 2.5);

    private final int maxZombies;
    private final int waveSize;
    private final double spawnIntervalSeconds;
    private final double spawnRateMultiplier;

    ZombieIntensity(int maxZombies, int waveSize, double spawnIntervalSeconds, double spawnRateMultiplier) {
        this.maxZombies = maxZombies;
        this.waveSize = waveSize;
        this.spawnIntervalSeconds = spawnIntervalSeconds;
        this.spawnRateMultiplier = spawnRateMultiplier;
    }

    public int getMaxZombies() {
        return maxZombies;
    }

    public int getWaveSize() {
        return waveSize;
    }

    public double getSpawnIntervalSeconds() {
        return spawnIntervalSeconds;
    }

    public double getSpawnRateMultiplier() {
        return spawnRateMultiplier;
    }
}

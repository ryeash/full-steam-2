package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum ZombieAttackPattern {
    NEAREST_PLAYER,
    HEADQUARTERS,
    TURRET,
    OBSESSED_PLAYER,
    LOWEST_HEALTH,
    SWARM_CLUSTER
}

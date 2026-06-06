package com.fullsteam.physics;

import lombok.Getter;

@Getter
public enum PowerUpType {
    SPEED_BOOST("Speed Boost", "⚡"),
    HEALTH_REGENERATION("Health Regen", "❤️"),
    DAMAGE_BOOST("Damage Boost", "⚔️"),
    DAMAGE_RESISTANCE("Damage Resist", "🛡️"),
    BERSERKER_MODE("Berserker", "🔥"),
    INFINITE_AMMO("Infinite Ammo", "∞");

    private final String displayName;
    private final String renderHint;

    PowerUpType(String displayName, String renderHint) {
        this.displayName = displayName;
        this.renderHint = renderHint;
    }
}

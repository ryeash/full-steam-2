package com.fullsteam.model;

import lombok.Getter;

/**
 * Defines how beam weapons apply their damage to targets
 */
@Getter
public enum DamageApplicationType {
    /**
     * Applies all damage immediately on hit (e.g., sniper laser)
     */
    INSTANT("Applies all damage immediately on hit"),

    /**
     * Applies damage continuously while beam is active (e.g., plasma beam, heal beam)
     */
    DAMAGE_OVER_TIME("Applies damage continuously while beam is active");

    private final String description;

    DamageApplicationType(String description) {
        this.description = description;
    }

}

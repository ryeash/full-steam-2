package com.fullsteam.model;

import lombok.Getter;

/**
 * Categories for organizing utility weapons in the UI
 */
@Getter
public enum UtilityCategory {
    SUPPORT("Support", "Utilities that help allies"),
    DEFENSIVE("Defensive", "Utilities that provide protection"),
    TACTICAL("Tactical", "Utilities that provide information or positioning"),
    CROWD_CONTROL("Crowd Control", "Utilities that control enemy movement");

    private final String displayName;
    private final String description;

    UtilityCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}

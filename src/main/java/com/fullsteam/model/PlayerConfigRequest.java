package com.fullsteam.model;

import lombok.Data;

@Data
public class PlayerConfigRequest {
    private String type = "configChange";
    private WeaponConfig weaponConfig;    // New unified weapon config
    private String utilityWeapon;         // Utility weapon name (e.g., "HEAL_ZONE")
    /** Chosen display name; validated server-side against the names.txt list. */
    private String playerName;
}



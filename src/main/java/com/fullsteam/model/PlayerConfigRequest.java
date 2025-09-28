package com.fullsteam.model;

import lombok.Data;

@Data
public class PlayerConfigRequest {
    private String type = "configChange";
    private String playerName;
    private WeaponConfig primaryWeapon;   // Legacy support
    private WeaponConfig secondaryWeapon; // Legacy support - now ignored
    private WeaponConfig weaponConfig;    // New unified weapon config
    private String utilityWeapon;         // Utility weapon name (e.g., "HEAL_BEAM")
}



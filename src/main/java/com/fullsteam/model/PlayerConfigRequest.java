package com.fullsteam.model;

import lombok.Data;

@Data
public class PlayerConfigRequest {
    private String type = "configChange";
    private String playerName;
    private WeaponConfig primaryWeapon;   // Legacy support
    private WeaponConfig secondaryWeapon; // Legacy support
    private WeaponConfig weaponConfig;    // New unified weapon config
}



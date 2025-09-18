package com.fullsteam.model;

import lombok.Data;

@Data
public class PlayerConfigRequest {
    private String type = "configChange";
    private String playerName;
    private WeaponConfig primaryWeapon;
    private WeaponConfig secondaryWeapon;
}



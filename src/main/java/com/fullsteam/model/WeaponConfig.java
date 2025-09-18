package com.fullsteam.model;

import lombok.Data;

@Data
public class WeaponConfig {
    public String type;
    public int damage = 25;
    public double fireRate = 5.0; // shots per second
    public double range = 300.0;
    public double accuracy = 0.9;
    public int magazineSize = 30;
    public double reloadTime = 2.0;
    public double projectileSpeed = 800.0;
}

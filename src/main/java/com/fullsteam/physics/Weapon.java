package com.fullsteam.physics;

import lombok.Data;

@Data
public class Weapon {
    private final String name;
    private final int damage;
    private final double fireRate;
    private final double range;
    private final double accuracy;
    private final int magazineSize;
    private final double reloadTime;
    private final double projectileSpeed;

    private int currentAmmo;

    public Weapon(String name, int damage, double fireRate, double range, double accuracy,
                  int magazineSize, double reloadTime, double projectileSpeed) {
        this.name = name;
        this.damage = damage;
        this.fireRate = fireRate;
        this.range = range;
        this.accuracy = accuracy;
        this.magazineSize = magazineSize;
        this.reloadTime = reloadTime;
        this.projectileSpeed = projectileSpeed;
        this.currentAmmo = magazineSize;
    }

    public void fire() {
        if (currentAmmo > 0) {
            currentAmmo--;
        }
    }

    public void reload() {
        currentAmmo = magazineSize;
    }

    public boolean needsReload() {
        return currentAmmo < magazineSize;
    }

    public int getAmmo() {
        return currentAmmo;
    }
}



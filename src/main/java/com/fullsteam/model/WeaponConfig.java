package com.fullsteam.model;

import com.fullsteam.physics.Weapon;
import lombok.Data;

import java.util.Map;

@Data
public class WeaponConfig {
    public String type;
    public int damage = 0;
    public int fireRate = 0;
    public int range = 0;
    public int accuracy = 0;
    public int magazineSize = 0;
    public int reloadTime = 0;
    public int projectileSpeed = 0;
    public int bulletsPerShot = 0;

    public Map<Weapon.WeaponAttribute, Integer> buildPoints() {
        return Map.of(
                Weapon.WeaponAttribute.DAMAGE, damage,
                Weapon.WeaponAttribute.FIRE_RATE, fireRate,
                Weapon.WeaponAttribute.RANGE, range,
                Weapon.WeaponAttribute.ACCURACY, accuracy,
                Weapon.WeaponAttribute.MAGAZINE_SIZE, magazineSize,
                Weapon.WeaponAttribute.RELOAD_TIME, reloadTime,
                Weapon.WeaponAttribute.PROJECTILE_SPEED, projectileSpeed,
                Weapon.WeaponAttribute.BULLETS_PER_SHOT, bulletsPerShot
        );
    }
}

package com.fullsteam.model;

import lombok.Data;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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
    public int linearDamping = 0;
    public Set<String> bulletEffects = new HashSet<>();
    public String ordinance = "BULLET"; // Default to standard bullets

    public Map<Weapon.WeaponAttribute, Integer> buildPoints() {
        return Map.of(
                Weapon.WeaponAttribute.DAMAGE, damage,
                Weapon.WeaponAttribute.FIRE_RATE, fireRate,
                Weapon.WeaponAttribute.RANGE, range,
                Weapon.WeaponAttribute.ACCURACY, accuracy,
                Weapon.WeaponAttribute.MAGAZINE_SIZE, magazineSize,
                Weapon.WeaponAttribute.RELOAD_TIME, reloadTime,
                Weapon.WeaponAttribute.PROJECTILE_SPEED, projectileSpeed,
                Weapon.WeaponAttribute.BULLETS_PER_SHOT, bulletsPerShot,
                Weapon.WeaponAttribute.LINEAR_DAMPING, linearDamping
        );
    }
    
    public Set<BulletEffect> buildBulletEffects() {
        Set<BulletEffect> effects = new HashSet<>();
        for (String effectName : bulletEffects) {
            try {
                BulletEffect effect = BulletEffect.valueOf(effectName.toUpperCase());
                effects.add(effect);
            } catch (IllegalArgumentException e) {
                // Skip invalid effect names
                System.err.println("Warning: Unknown bullet effect: " + effectName);
            }
        }
        return effects;
    }
    
    public Ordinance buildOrdinance() {
        try {
            return Ordinance.valueOf(ordinance.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: Unknown ordinance type: " + ordinance + ", defaulting to BULLET");
            return Ordinance.BULLET;
        }
    }
    
    public Weapon buildWeapon() {
        return new Weapon(type, buildPoints(), buildBulletEffects(), buildOrdinance());
    }
}

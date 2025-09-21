package com.fullsteam.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
    public Set<BulletEffect> bulletEffects = new HashSet<>();
    public Ordinance ordinance = Ordinance.BULLET;

    public Weapon buildWeapon() {
        return new Weapon(type,
                damage,
                fireRate,
                range,
                accuracy,
                magazineSize,
                reloadTime,
                projectileSpeed,
                bulletsPerShot,
                linearDamping,
                bulletEffects,
                ordinance);
    }

    public static final WeaponConfig ASSAULT_RIFLE_PRESET = new WeaponConfig(
            "Assault Rifle",
            20,
            25,
            10,
            0,
            25,
            10,
            10,
            0,
            0,
            Set.of(),
            Ordinance.BULLET
    );

    public static final WeaponConfig HAND_CANNON_PRESET = new WeaponConfig(
            "Hand Cannon",
            40,
            5,
            20,
            -5,
            5,
            15,
            20,
            0,
            0,
            Set.of(),
            Ordinance.BULLET
    );

    // Example: Explosive Sniper Rifle (75 points + 25 for explosive effect = 100 total)
    public static final WeaponConfig EXPLOSIVE_SNIPER_PRESET = new WeaponConfig(
            "Explosive Sniper Rifle",
            35,
            2,
            15,
            0,
            3,
            20,
            0,
            0,
            0,
            Set.of(BulletEffect.EXPLODES_ON_IMPACT),
            Ordinance.BULLET
    );

    // Example: Bouncy SMG with damping (85 points + 15 for bouncy effect = 100 total)
    public static final WeaponConfig BOUNCY_SMG_PRESET = new WeaponConfig(
            "Bouncy SMG",
            15,
            30,
            5,
            -5,
            20,
            5,
            10,
            0,
            -5,
            Set.of(BulletEffect.BOUNCY),
            Ordinance.BULLET
    );

    // Example: Rocket Launcher (55 points + 20 for rocket + 25 for explosive = 100 total)
    public static final WeaponConfig ROCKET_LAUNCHER_PRESET = new WeaponConfig(
            "Rocket Launcher",
            25,
            1,
            12,
            0,
            2,
            15,
            0,
            0,
            0,
            Set.of(BulletEffect.EXPLODES_ON_IMPACT),
            Ordinance.ROCKET
    );

    // Example: Grenade Launcher (68 points + 10 for grenade + 22 for fragmenting = 100 total)
    public static final WeaponConfig GRENADE_LAUNCHER_PRESET = new WeaponConfig(
            "Grenade Launcher",
            25,
            3,
            8,
            -2,
            6,
            18,
            5,
            0,
            -3,
            Set.of(BulletEffect.EXPLODES_ON_IMPACT),
            Ordinance.GRENADE
    );

    // Example: Plasma Rifle (65 points + 15 for plasma + 20 for piercing = 100 total)
    public static final WeaponConfig PLASMA_RIFLE_PRESET = new WeaponConfig(
            "Plasma Rifle",
            20,
            15,
            10,
            0,
            10,
            10,
            10,
            0,
            0,
            Set.of(),
            Ordinance.PLASMA
    );

    public int getAttributePoints() {
        return damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping;
    }
}

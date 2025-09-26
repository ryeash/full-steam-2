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
            15,
            0,
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
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.BULLET
    );

    // Example: Bouncy SMG with damping (85 points + 15 for bouncy effect = 100 total)
    public static final WeaponConfig BOUNCY_SMG_PRESET = new WeaponConfig(
            "Bouncy SMG",
            15,
            30,
            5,
            0,
            20,
            10,
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
            Set.of(BulletEffect.EXPLOSIVE),
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
            10,
            0,
            -3,
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.GRENADE
    );

    // Example: Plasma Rifle (65 points + 15 for plasma + 20 for piercing = 100 total)
    public static final WeaponConfig PLASMA_RIFLE_PRESET = new WeaponConfig(
            "Plasma Rifle",
            25,
            15,
            15,
            0,
            10,
            10,
            10,
            0,
            0,
            Set.of(),
            Ordinance.PLASMA
    );

    // Cannonball Ordinance Showcase - Heavy, slow, powerful shots
    public static final WeaponConfig SIEGE_CANNON_PRESET = new WeaponConfig(
            "Siege Cannon",
            35,     // High damage
            1,      // Very slow fire rate
            15,     // Good range
            -2,      // Neutral accuracy
            3,      // Small magazine
            25,     // Long reload
            11,      // Slow projectile
            0,      // Single shot
            0,      // No damping
            Set.of(),
            Ordinance.CANNONBALL  // 12 points, total: 84 + 12 = 96
    );

    // Dart Ordinance Showcase - Fast, precise, low damage
    public static final WeaponConfig PRECISION_DART_GUN_PRESET = new WeaponConfig(
            "Precision Dart Gun",
            15,     // Low damage
            20,     // Good fire rate
            12,     // Good range
            0,     // High accuracy
            15,     // Decent magazine
            13,      // Fast reload
            20,     // Fast projectile
            0,      // Single shot
            0,      // No damping
            Set.of(),
            Ordinance.DART  // 5 points, total: 95 + 5 = 100
    );

    // Flamethrower Ordinance Showcase - Short range, area denial
    public static final WeaponConfig FLAME_PROJECTOR_PRESET = new WeaponConfig(
            "Flame Projector",
            21,     // Good damage
            30,     // High fire rate
            3,      // Very short range
            -10,    // Poor accuracy (spread)
            30,     // Large magazine
            5,      // Fast reload
            8,      // Medium speed
            10,      // Multiple streams
            -5,     // Negative damping for spread
            Set.of(),
            Ordinance.FLAMETHROWER  // 8 points, total: 92 + 8 = 100
    );

    // Piercing Effect Showcase - Sniper that goes through enemies
    public static final WeaponConfig PIERCING_RIFLE_PRESET = new WeaponConfig(
            "Piercing Rifle",
            25,     // High damage
            8,      // Slow fire rate
            20,     // Long range
            0,     // Good accuracy
            5,      // Small magazine
            10,     // Medium reload
            12,      // Base speed
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.PIERCING),  // 20 points
            Ordinance.BULLET  // 0 points, total: 80 + 20 = 100
    );

    // Incendiary Effect Showcase - Sets targets on fire
    public static final WeaponConfig INCENDIARY_SHOTGUN_PRESET = new WeaponConfig(
            "Incendiary Shotgun",
            20,     // Medium damage
            10,      // Slow fire rate
            10,      // Short range
            -8,     // Poor accuracy (shotgun spread)
            8,      // Small magazine
            15,     // Long reload
            12,     // Medium speed
            15,      // Multiple pellets
            0,      // No damping
            Set.of(BulletEffect.INCENDIARY),  // 18 points
            Ordinance.BULLET  // 0 points, total: 64 + 18 = 82
    );

    // Fragmenting Effect Showcase - Projectiles split on impact
    public static final WeaponConfig CLUSTER_MORTAR_PRESET = new WeaponConfig(
            "Cluster Mortar",
            20,     // Medium damage
            4,      // Slow fire rate
            12,     // Good range
            -5,     // Poor accuracy (mortar arc)
            4,      // Small magazine
            20,     // Long reload
            16,      // Slow projectile
            0,      // Single shot
            -3,     // Negative damping for arc
            Set.of(BulletEffect.FRAGMENTING),  // 22 points
            Ordinance.GRENADE  // 10 points, total: 58 + 22 + 10 = 90
    );

    // Homing Effect Showcase - Tracking projectiles
    public static final WeaponConfig SEEKER_DART_PRESET = new WeaponConfig(
            "Seeker Dart",
            12,     // Low damage
            10,      // Medium fire rate
            10,     // Medium range
            0,      // Good accuracy
            6,      // Small magazine
            15,     // Medium reload
            12,     // Fast projectile
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.HOMING),  // 30 points
            Ordinance.DART  // 5 points, total: 65 + 30 + 5 = 100
    );

    // Electric Effect Showcase - Chain lightning
    public static final WeaponConfig ARC_PISTOL_PRESET = new WeaponConfig(
            "Arc Pistol",
            19,     // Medium damage
            15,     // Medium fire rate
            12,      // Short-medium range
            0,      // Decent accuracy
            8,      // Small magazine
            18,      // Fast reload
            12,     // Medium projectile speed
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.ELECTRIC),  // 16 points
            Ordinance.BULLET  // 0 points, total: 66 + 16 = 82
    );

    // Poison Effect Showcase - Area denial with gas
    public static final WeaponConfig TOXIC_SPRAYER_PRESET = new WeaponConfig(
            "Toxic Sprayer",
            12,     // Low direct damage
            24,     // High fire rate
            6,      // Short range
            -5,     // Poor accuracy (spray)
            20,     // Large magazine
            10,     // Medium reload
            8,      // Medium speed
            10,      // Multiple streams
            -3,     // Negative damping for spread
            Set.of(BulletEffect.POISON),  // 18 points
            Ordinance.BULLET  // 0 points, total: 75 + 18 = 93
    );

    // Freezing Effect Showcase - Slows enemies
    public static final WeaponConfig ICE_CANNON_PRESET = new WeaponConfig(
            "Ice Cannon",
            20,     // Medium damage
            10,     // Medium fire rate
            20,     // Medium range
            0,      // Good accuracy
            8,      // Small magazine
            18,     // Medium reload
            10,     // Medium speed
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.FREEZING),  // 14 points
            Ordinance.BULLET  // 0 points, total: 71 + 14 = 85
    );

    public int getAttributePoints() {
        return damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping;
    }
}

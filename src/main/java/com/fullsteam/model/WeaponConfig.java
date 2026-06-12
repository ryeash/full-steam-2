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
            30,
            5,
            0,
            36,
            5,
            4,
            0,
            0,
            Set.of(),
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    public static final WeaponConfig HAND_CANNON_PRESET = new WeaponConfig(
            "Hand Cannon",
            40,
            27,
            9,
            0,
            5,
            9,
            10,
            0,
            0,
            Set.of(),
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Pure Sniper Rifle - Maximizes range, damage, and projectile speed
    // Focus: Long-range precision with high damage per shot
    // Trade-offs: Very slow fire rate, small magazine, long reload
    public static final WeaponConfig SNIPER_RIFLE_PRESET = new WeaponConfig(
            "Sniper Rifle",
            40,     // damage 50
            2,      // Very slow fire rate (0.9 shots/sec)
            33,     // range ~1350 units
            0,      // Perfect accuracy (1.0)
            5,     // magazine 10 rounds
            5,     // reload ~2.55s
            15,     // speed ~799 units/sec
            0,      // Single shot
            0,      // No damping (bullets maintain speed)
            Set.of(),
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Minigun - Maximum suppressive fire with extreme fire rate and magazine
    // Focus: Overwhelming volume of fire, area suppression
    // Trade-offs: Low damage per bullet, terrible accuracy, slow projectiles
    public static final WeaponConfig MINIGUN_PRESET = new WeaponConfig(
            "Minigun",
            10,     // damage 20 (low per-bullet, compensated by volume)
            30,     // Maximum fire rate (insane rate of fire)
            14,     // range ~969 units
            -10,    // Terrible accuracy (massive spread)
            40,     // Maximum magazine size (never stop shooting)
            5,     // reload ~2.55s
            16,     // speed ~815 units/sec
            0,      // 5 bullets per shot (multi-barrel spin-up)
            -5,      // No damping
            Set.of(),
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Example: Explosive Sniper Rifle (90 pts total)
    public static final WeaponConfig EXPLOSIVE_SNIPER_PRESET = new WeaponConfig(
            "Explosive Sniper Rifle",
            40,
            2,
            9,
            0,
            8,
            16,
            0,
            0,
            0,
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.BULLET  // total: 100 pts (attr 75 + fx 25 + ord 0)
    );

    // Example: Bouncy SMG with damping (85 points + 15 for bouncy effect = 100 total)
    public static final WeaponConfig BOUNCY_SMG_PRESET = new WeaponConfig(
            "Bouncy SMG",
            15,
            30,
            2,
            0,
            34,
            5,
            4,
            0,
            -5,
            Set.of(BulletEffect.BOUNCY),
            Ordinance.BULLET  // total: 100 pts (attr 85 + fx 15 + ord 0)
    );

    // Example: Rocket Launcher (89 pts total)
    public static final WeaponConfig ROCKET_LAUNCHER_PRESET = new WeaponConfig(
            "Rocket Launcher",
            36,
            1,
            7,
            0,
            2,
            9,
            0,
            0,
            0,
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.ROCKET  // total: 100 pts (attr 55 + fx 25 + ord 20)
    );

    // Example: Grenade Launcher (84 pts total)
    public static final WeaponConfig GRENADE_LAUNCHER_PRESET = new WeaponConfig(
            "Grenade Launcher",
            40,
            3,
            4,
            -2,
            7,
            12,
            4,
            0,
            -3,
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.GRENADE  // total: 100 pts (attr 65 + fx 25 + ord 10)
    );

    // Example: Plasma Rifle (83 pts total)
    public static final WeaponConfig PLASMA_RIFLE_PRESET = new WeaponConfig(
            "Plasma Rifle",
            40,
            17,
            9,
            0,
            10,
            5,
            4,
            0,
            0,
            Set.of(),
            Ordinance.PLASMA  // total: 100 pts (attr 85 + fx 0 + ord 15)
    );

    // Dart Ordinance Showcase - Fast, precise, low damage
    public static final WeaponConfig PRECISION_DART_GUN_PRESET = new WeaponConfig(
            "Precision Dart Gun",
            26,     // damage 36
            30,     // fire rate ~7.1 shots/s
            7,     // range ~653 units
            0,     // High accuracy
            15,     // Decent magazine
            7,     // reload ~2.16s
            10,     // speed ~696 units/sec
            0,      // Single shot
            0,      // No damping
            Set.of(),
            Ordinance.DART  // total: 100 pts (attr 95 + fx 0 + ord 5)
    );

    public static final WeaponConfig SHOTGUN_PRESET = new WeaponConfig(
            "Shotgun",
            36,     // damage 46
            1,     // low fire rate
            5,     // range ~533 units
            -10,    // Poor accuracy (spread)
            40,     // magazine 45 rounds
            2,     // reload ~3.32s
            11,     // speed ~720 units/sec
            25,     // Multiple streams
            -10,     // Negative damping for spread
            Set.of(),
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Piercing Effect Showcase - Sniper that goes through enemies
    public static final WeaponConfig PIERCING_RIFLE_PRESET = new WeaponConfig(
            "Piercing Rifle",
            40,     // damage 50
            12,     // fire rate ~4.7 shots/s
            13,     // range ~933 units
            0,     // Good accuracy
            5,      // Small magazine
            5,     // reload ~2.55s
            5,     // speed ~539 units/sec
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.PIERCING),  // effects: 20 pts
            Ordinance.BULLET  // total: 100 pts (attr 80 + fx 20 + ord 0)
    );

    // Incendiary Effect Showcase - Sets targets on fire
    public static final WeaponConfig INCENDIARY_SHOTGUN_PRESET = new WeaponConfig(
            "Incendiary Shotgun",
            38,     // damage 48
            10,      // Slow fire rate
            5,     // range ~533 units
            -8,     // Poor accuracy (shotgun spread)
            8,      // Small magazine
            9,     // reload ~1.85s
            5,     // speed ~539 units/sec
            15,      // Multiple pellets
            0,      // No damping
            Set.of(BulletEffect.INCENDIARY),  // effects: 18 pts
            Ordinance.BULLET  // total: 100 pts (attr 82 + fx 18 + ord 0)
    );

    // Fragmenting Effect Showcase - Projectiles split on impact
    public static final WeaponConfig CLUSTER_MORTAR_PRESET = new WeaponConfig(
            "Cluster Mortar",
            38,     // damage 48
            4,      // Slow fire rate
            7,     // range ~653 units
            -5,     // Poor accuracy (mortar arc)
            4,      // Small magazine
            16,     // reload ~1.17s
            7,     // speed ~609 units/sec
            0,      // Single shot
            -3,     // Negative damping for arc
            Set.of(BulletEffect.FRAGMENTING),  // effects: 22 pts
            Ordinance.GRENADE  // total: 100 pts (attr 68 + fx 22 + ord 10)
    );

    // Homing Effect Showcase - Tracking projectiles
    public static final WeaponConfig SEEKER_DART_PRESET = new WeaponConfig(
            "Seeker Dart",
            30,     // damage 40
            10,      // Medium fire rate
            5,     // range ~533 units
            0,      // Good accuracy
            6,      // Small magazine
            9,     // reload ~1.85s
            5,     // speed ~539 units/sec
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.HOMING),  // effects: 30 pts
            Ordinance.DART  // total: 100 pts (attr 65 + fx 30 + ord 5)
    );

    // Electric Effect Showcase - Chain lightning
    public static final WeaponConfig ARC_PISTOL_PRESET = new WeaponConfig(
            "Arc Pistol",
            37,     // damage 47
            15,     // Medium fire rate
            7,     // range ~653 units
            0,      // Decent accuracy
            8,      // Small magazine
            12,     // reload ~1.50s
            5,     // speed ~539 units/sec
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.ELECTRIC),  // effects: 16 pts
            Ordinance.BULLET  // total: 100 pts (attr 84 + fx 16 + ord 0)
    );

    // Poison Effect Showcase - Area denial with gas
    public static final WeaponConfig TOXIC_SPRAYER_PRESET = new WeaponConfig(
            "Toxic Sprayer",
            12,     // Low direct damage
            30,     // fire rate ~7.1 shots/s
            3,     // range ~395 units
            -5,     // Poor accuracy (spray)
            23,     // magazine 28 rounds
            5,     // reload ~2.55s
            3,     // speed ~455 units/sec
            10,     // Multiple streams
            -3,     // Negative damping for spread
            Set.of(BulletEffect.POISON),  // effects: 22 pts
            Ordinance.BULLET  // total: 100 pts (attr 78 + fx 22 + ord 0)
    );

    // Freezing Effect Showcase - Slows enemies
    public static final WeaponConfig ICE_CANNON_PRESET = new WeaponConfig(
            "Ice Cannon",
            39,     // damage 49
            10,     // Medium fire rate
            13,     // range ~933 units
            0,      // Good accuracy
            8,      // Small magazine
            12,     // reload ~1.50s
            4,     // speed ~498 units/sec
            0,      // Single shot
            0,      // No damping
            Set.of(BulletEffect.FREEZING),  // effects: 14 pts
            Ordinance.BULLET  // total: 100 pts (attr 86 + fx 14 + ord 0)
    );

    // Twin Sixes - Dual-barrel high-damage weapon with spread
    public static final WeaponConfig TWIN_SIXES_PRESET = new WeaponConfig(
            "Twin Sixes",
            40,     // Very high damage
            30,     // fire rate ~7.1 shots/s
            7,     // range ~653 units
            -5,     // Imperfect accuracy (spread)
            9,     // magazine 14 rounds
            9,     // reload ~1.85s
            5,     // speed ~539 units/sec
            5,      // 2 bullets per shot
            0,      // No damping
            Set.of(),  // no effects
            Ordinance.BULLET  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Laser Rifle - Instant-hit precision beam weapon
    public static final WeaponConfig LASER_RIFLE_PRESET = new WeaponConfig(
            "Laser Rifle",
            29,     // damage 39
            6,      // Medium fire rate
            7,     // range ~653 units
            0,      // Perfect accuracy (beams are always accurate)
            15,     // Medium magazine
            3,     // reload ~3.04s
            0,     // speed ~300 units/sec
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // no effects
            Ordinance.LASER  // total: 100 pts (attr 50 + fx 0 + ord 50)
    );

    public static final WeaponConfig PRISM_GUN_PRESET = new WeaponConfig(
            "Prism Gun",
            29,     // damage 39
            6,      // Medium fire rate
            7,     // range ~653 units
            -10,      // scatter
            15,     // Medium magazine
            3,     // reload ~3.04s
            0,     // speed ~300 units/sec
            10,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // no effects
            Ordinance.LASER  // total: 100 pts (attr 50 + fx 0 + ord 50)
    );

    // Plasma Cannon - Continuous damage beam weapon
    public static final WeaponConfig PLASMA_CANNON_PRESET = new WeaponConfig(
            "Plasma Cannon",
            31,     // damage 41
            8,      // Medium fire rate
            5,     // range ~533 units
            0,      // Perfect accuracy
            12,     // Medium magazine
            9,     // reload ~1.85s
            0,     // speed ~300 units/sec
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // no effects
            Ordinance.PLASMA_BEAM  // total: 100 pts (attr 55 + fx 0 + ord 45)
    );

    // ===== ADVANCED COMBINATION WEAPONS =====

    // Storm Caller - Electric + Homing rapid-fire seeking electric projectiles
    public static final WeaponConfig STORM_CALLER_PRESET = new WeaponConfig(
            "Storm Caller",
            5,      // Low damage per shot
            29,     // fire rate ~7.0 shots/s
            4,     // range ~466 units
            0,      // Good accuracy
            12,     // Medium magazine
            2,     // reload ~3.32s
            7,     // speed ~609 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.HOMING),  // effects: 46 pts
            Ordinance.DART  // total: 100 pts (attr 49 + fx 46 + ord 5)
    );

    // Napalm Launcher - Incendiary + Explosive massive burning explosion zones
    public static final WeaponConfig NAPALM_LAUNCHER_PRESET = new WeaponConfig(
            "Napalm Launcher",
            31,     // damage 41
            2,      // Very slow fire rate
            5,     // range ~533 units
            -3,     // Poor accuracy
            3,      // Very small magazine
            9,     // reload ~1.85s
            3,     // speed ~455 units/sec
            0,      // Single shot
            -3,     // Negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.EXPLOSIVE),  // effects: 43 pts
            Ordinance.GRENADE  // total: 100 pts (attr 47 + fx 43 + ord 10)
    );

    // Cryo Shotgun - Freezing close-range freeze blast
    public static final WeaponConfig CRYO_SHOTGUN_PRESET = new WeaponConfig(
            "Cryo Shotgun",
            40,     // damage 50
            10,     // fire rate ~4.3 shots/s
            3,     // range ~395 units
            -10,    // Very poor accuracy
            6,      // Small magazine
            12,     // reload ~1.50s
            7,     // speed ~609 units/sec
            20,     // Many pellets
            -2,     // Negative damping
            Set.of(BulletEffect.FREEZING),  // effects: 14 pts
            Ordinance.BULLET  // total: 100 pts (attr 86 + fx 14 + ord 0)
    );

    // Venom Needler - Poison + Piercing precise needles that poison all targets
    public static final WeaponConfig VENOM_NEEDLER_PRESET = new WeaponConfig(
            "Venom Needler",
            10,     // Low direct damage
            27,     // fire rate ~6.7 shots/s
            9,     // range ~759 units
            0,      // Perfect accuracy
            12,     // Medium magazine
            4,     // reload ~2.78s
            1,     // speed ~356 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.PIERCING),  // effects: 42 pts
            Ordinance.DART  // total: 100 pts (attr 53 + fx 42 + ord 5)
    );

    // Thunderbolt Cannon - Electric + Explosive electric explosion rocket
    public static final WeaponConfig THUNDERBOLT_CANNON_PRESET = new WeaponConfig(
            "Thunderbolt Cannon",
            30,     // damage 40
            1,      // Very slow fire rate
            7,     // range ~653 units
            0,      // Perfect accuracy
            2,      // Tiny magazine
            7,     // reload ~2.16s
            2,     // speed ~407 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.EXPLOSIVE),  // effects: 41 pts
            Ordinance.ROCKET  // total: 100 pts (attr 39 + fx 41 + ord 20)
    );

    // Ricochet Rifle
    public static final WeaponConfig RICOCHET_RIFLE_PRESET = new WeaponConfig(
            "Ricochet Rifle",
            40,     // damage 50
            13,     // fire rate ~4.8 shots/s
            11,     // range ~852 units
            0,      // Good accuracy
            10,     // Medium magazine
            4,     // reload ~2.78s
            7,     // speed ~609 units/sec
            0,      // Single shot
            0,    // Negative damping
            Set.of(BulletEffect.BOUNCY),  // effects: 15 pts
            Ordinance.BULLET  // total: 100 pts (attr 85 + fx 15 + ord 0)
    );

    // Plague Mortar - Poison + Fragmenting splits into poison clouds
    public static final WeaponConfig PLAGUE_MORTAR_PRESET = new WeaponConfig(
            "Plague Mortar",
            31,     // damage 41
            3,      // Slow fire rate
            4,     // range ~466 units
            -7,     // Poor accuracy
            4,      // Small magazine
            12,     // reload ~1.50s
            4,     // speed ~498 units/sec
            0,      // Single shot
            -5,     // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.FRAGMENTING),  // effects: 44 pts
            Ordinance.GRENADE  // total: 100 pts (attr 46 + fx 44 + ord 10)
    );

    // Wildfire Sprayer - Incendiary + Bouncy bouncing fire streams
    public static final WeaponConfig WILDFIRE_SPRAYER_PRESET = new WeaponConfig(
            "Wildfire Sprayer",
            9,      // Low damage per stream
            18,     // fire rate ~5.6 shots/s
            2,     // range ~319 units
            -10,    // Poor accuracy
            40,     // magazine 45 rounds
            4,     // reload ~2.78s
            4,     // speed ~498 units/sec
            10,     // Many streams
            -10,    // High negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.BOUNCY),  // effects: 33 pts
            Ordinance.BULLET  // total: 100 pts (attr 67 + fx 33 + ord 0)
    );

    // Frost Lance - Freezing + Piercing ice beam that slows all in line
    public static final WeaponConfig FROST_LANCE_PRESET = new WeaponConfig(
            "Frost Lance",
            29,     // damage 39
            10,     // Medium fire rate
            5,     // range ~533 units
            0,      // Perfect accuracy
            7,      // Small magazine
            3,     // reload ~3.04s
            7,     // speed ~609 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FREEZING, BulletEffect.PIERCING),  // effects: 34 pts
            Ordinance.PLASMA  // total: 100 pts (attr 51 + fx 34 + ord 15)
    );

    // Shrapnel Cannon - Fragmenting + Explosive explosive fragments
    public static final WeaponConfig SHRAPNEL_CANNON_PRESET = new WeaponConfig(
            "Shrapnel Cannon",
            25,     // damage 35
            8,     // Slow fire rate
            5,     // range ~533 units
            -3,     // Slight inaccuracy
            3,      // Small magazine
            2,     // reload ~3.32s
            3,     // speed ~455 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FRAGMENTING, BulletEffect.EXPLOSIVE),  // effects: 47 pts
            Ordinance.ROCKET  // total: 100 pts (attr 33 + fx 47 + ord 20)
    );

    // Seeking Inferno - Incendiary + Homing heat-seeking fire darts
    public static final WeaponConfig SEEKING_INFERNO_PRESET = new WeaponConfig(
            "Seeking Inferno",
            10,      // Low damage
            25,     // fire rate ~6.5 shots/s
            5,     // range ~533 units
            0,      // Good accuracy
            9,     // Medium magazine
            4,     // reload ~2.78s
            4,     // speed ~498 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING),  // effects: 48 pts
            Ordinance.DART  // total: 100 pts (attr 47 + fx 48 + ord 5)
    );

    // EMP Burst Gun - Electric + Fragmenting splits into electric bursts
    public static final WeaponConfig EMP_BURST_GUN_PRESET = new WeaponConfig(
            "EMP Burst Gun",
            33,     // damage 43
            12,     // Slow fire rate
            7,     // range ~653 units
            0,      // Good accuracy
            8,      // Small magazine
            5,     // reload ~2.55s
            7,     // speed ~609 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.FRAGMENTING),  // effects: 38 pts
            Ordinance.BULLET  // total: 100 pts (attr 62 + fx 38 + ord 0)
    );

    // Glacial Mortar - Freezing + Explosive ice grenade area freeze
    public static final WeaponConfig GLACIAL_MORTAR_PRESET = new WeaponConfig(
            "Glacial Mortar",
            35,     // damage 45
            13,     // Slow fire rate
            7,     // range ~653 units
            -5,     // Poor accuracy
            4,      // Small magazine
            3,     // reload ~3.04s
            4,     // speed ~498 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FREEZING, BulletEffect.EXPLOSIVE),  // effects: 39 pts
            Ordinance.GRENADE  // total: 100 pts (attr 51 + fx 39 + ord 10)
    );

    // Phantom Needles - Homing + Bouncy tracking darts that bounce
    public static final WeaponConfig PHANTOM_NEEDLES_PRESET = new WeaponConfig(
            "Phantom Needles",
            6,      // Very low damage
            30,     // fire rate ~7.1 shots/s
            4,     // range ~466 units
            -5,      // Good accuracy
            17,     // magazine 22 rounds
            2,     // reload ~3.32s
            6,     // speed ~575 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.HOMING, BulletEffect.BOUNCY),  // effects: 45 pts
            Ordinance.DART  // total: 100 pts (attr 50 + fx 45 + ord 5)
    );

    // Corrosive Cannon - Poison + Explosive massive poison explosion
    public static final WeaponConfig CORROSIVE_CANNON_PRESET = new WeaponConfig(
            "Corrosive Cannon",
            26,     // damage 36
            5,      // Very slow fire rate
            7,     // range ~653 units
            0,      // Perfect accuracy
            0,      // Tiny magazine
            2,     // reload ~3.32s
            3,     // speed ~455 units/sec
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.EXPLOSIVE),  // effects: 47 pts
            Ordinance.ROCKET  // total: 100 pts (attr 33 + fx 47 + ord 20)
    );

    public int getAttributePoints() {
        return damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping;
    }
}

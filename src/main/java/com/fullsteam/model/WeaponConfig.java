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

    // Pure Sniper Rifle - Maximizes range, damage, and projectile speed
    // Focus: Long-range precision with high damage per shot
    // Trade-offs: Very slow fire rate, small magazine, long reload
    public static final WeaponConfig SNIPER_RIFLE_PRESET = new WeaponConfig(
            "Sniper Rifle",
            30,     // High damage (40 dmg total)
            2,      // Very slow fire rate (0.9 shots/sec)
            30,     // Very long range (1200 units)
            0,      // Perfect accuracy (1.0)
            3,      // Very small magazine (8 rounds)
            10,     // Medium reload (2.6 sec)
            25,     // High projectile speed (800 units/sec)
            0,      // Single shot
            0,      // No damping (bullets maintain speed)
            Set.of(),
            Ordinance.BULLET  // Total: 100 points
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

    // Twin Sixes - Dual-barrel high-damage weapon with spread
    public static final WeaponConfig TWIN_SIXES_PRESET = new WeaponConfig(
            "Twin Sixes",
            40,     // Very high damage
            14,     // Medium fire rate
            12,     // Good range
            -5,     // Imperfect accuracy (spread)
            7,      // 12 rounds per magazine
            15,     // Fast reload
            12,     // Medium projectile speed
            5,      // 2 bullets per shot
            0,      // No damping
            Set.of(),  // No effects
            Ordinance.BULLET  // 0 points, total: 100 + 0 = 100
    );

    // Laser Rifle - Instant-hit precision beam weapon
    public static final WeaponConfig LASER_RIFLE_PRESET = new WeaponConfig(
            "Laser Rifle",
            20,     // High damage
            6,      // Medium fire rate
            12,     // Long range
            0,      // Perfect accuracy (beams are always accurate)
            15,     // Medium magazine
            7,      // Fast reload
            0,      // Not used for beams
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // No effects
            Ordinance.LASER
    );

    // Plasma Cannon - Continuous damage beam weapon
    public static final WeaponConfig PLASMA_CANNON_PRESET = new WeaponConfig(
            "Plasma Cannon",
            20,     // Medium damage
            8,      // Medium fire rate
            10,     // Good range
            0,      // Perfect accuracy
            12,     // Medium magazine
            15,     // Medium reload
            0,      // Not used for beams
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // No effects
            Ordinance.PLASMA_BEAM
    );

    // Medic Beam - Healing support weapon
    public static final WeaponConfig MEDIC_BEAM_PRESET = new WeaponConfig(
            "Medic Beam",
            20,     // Low damage (healing focused)
            15,     // Medium fire rate
            12,     // Medium range
            0,      // Perfect accuracy
            20,     // Large magazine for sustained healing
            8,      // Very fast reload
            0,      // Not used for beams
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // No effects
            Ordinance.HEAL_BEAM
    );

    // Rail Cannon - Piercing instant beam weapon
    public static final WeaponConfig RAIL_CANNON_PRESET = new WeaponConfig(
            "Rail Cannon",
            15,     // High damage
            4,      // Slow fire rate
            15,     // Very long range
            0,      // Perfect accuracy
            6,      // Small magazine
            15,     // Long reload
            0,      // Not used for beams
            0,      // Single beam
            -10,      // Not used for beams
            Set.of(),  // No effects
            Ordinance.RAILGUN
    );

    // ===== ADVANCED COMBINATION WEAPONS =====

    // Storm Caller - Electric + Homing rapid-fire seeking electric projectiles
    public static final WeaponConfig STORM_CALLER_PRESET = new WeaponConfig(
            "Storm Caller",
            5,      // Low damage per shot
            15,     // High fire rate
            8,      // Medium range
            0,      // Good accuracy
            12,     // Medium magazine
            4,      // Fast reload
            15,     // Fast projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.HOMING),  // 16 + 30 = 46 points
            Ordinance.DART  // 5 points, total: 49 + 46 + 5 = 100
    );

    // Napalm Launcher - Incendiary + Explosive massive burning explosion zones
    public static final WeaponConfig NAPALM_LAUNCHER_PRESET = new WeaponConfig(
            "Napalm Launcher",
            15,     // Medium damage
            2,      // Very slow fire rate
            10,     // Medium range
            -3,     // Poor accuracy
            3,      // Very small magazine
            15,     // Medium reload
            8,      // Slow projectile
            0,      // Single shot
            -3,     // Negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.EXPLOSIVE),  // 29 + 14 = 43 points
            Ordinance.GRENADE  // 10 points, total: 47 + 43 + 10 = 100
    );

    // Cryo Shotgun - Freezing close-range freeze blast
    public static final WeaponConfig CRYO_SHOTGUN_PRESET = new WeaponConfig(
            "Cryo Shotgun",
            25,     // High damage per pellet
            8,      // Slow fire rate
            6,      // Short range
            -10,    // Very poor accuracy
            6,      // Small magazine
            18,     // Long reload
            15,     // Fast projectile
            20,     // Many pellets
            -2,     // Negative damping
            Set.of(BulletEffect.FREEZING),  // 14 points
            Ordinance.BULLET  // 0 points, total: 86 + 14 = 100
    );

    // Venom Needler - Poison + Piercing precise needles that poison all targets
    public static final WeaponConfig VENOM_NEEDLER_PRESET = new WeaponConfig(
            "Venom Needler",
            10,     // Low direct damage
            18,     // Good fire rate
            15,     // Good range
            0,      // Perfect accuracy
            12,     // Medium magazine
            10,     // Fast reload
            2,      // Very fast projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.PIERCING),  // 18 + 20 = 38 points
            Ordinance.DART  // 5 points, total: 57 + 38 + 5 = 100
    );

    // Thunderbolt Cannon - Electric + Explosive electric explosion rocket
    public static final WeaponConfig THUNDERBOLT_CANNON_PRESET = new WeaponConfig(
            "Thunderbolt Cannon",
            16,     // Medium damage
            1,      // Very slow fire rate
            12,     // Long range
            0,      // Perfect accuracy
            2,      // Tiny magazine
            13,     // Medium reload
            5,      // Slow projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.EXPLOSIVE),  // 16 + 25 = 41 points
            Ordinance.ROCKET  // 20 points, total: 39 + 41 + 20 = 100
    );

    // Ricochet Rifle
    public static final WeaponConfig RICOCHET_RIFLE_PRESET = new WeaponConfig(
            "Ricochet Rifle",
            23,     // Medium damage
            12,     // Medium fire rate
            17,     // Medium range
            0,      // Good accuracy
            10,     // Medium magazine
            8,      // Medium reload
            15,     // Fast projectile
            0,      // Single shot
            0,    // Negative damping
            Set.of(BulletEffect.BOUNCY),  // 15 + 20 = 35 points
            Ordinance.BULLET  // 0 points, total: 65 + 35 = 100
    );

    // Plague Mortar - Poison + Fragmenting splits into poison clouds
    public static final WeaponConfig PLAGUE_MORTAR_PRESET = new WeaponConfig(
            "Plague Mortar",
            15,     // Low damage
            3,      // Slow fire rate
            10,     // Medium range
            -7,     // Poor accuracy
            4,      // Small magazine
            20,     // Long reload
            10,     // Slow projectile
            0,      // Single shot
            -5,     // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.FRAGMENTING),  // 24 + 16 = 40 points
            Ordinance.GRENADE  // 10 points, total: 50 + 40 + 10 = 100
    );

    // Wildfire Sprayer - Incendiary + Bouncy bouncing fire streams
    public static final WeaponConfig WILDFIRE_SPRAYER_PRESET = new WeaponConfig(
            "Wildfire Sprayer",
            9,     // Low damage per stream
            20,     // High fire rate
            4,      // Very short range
            -10,     // Poor accuracy
            19,     // Large magazine
            8,      // Fast reload
            9,     // Medium speed
            10,     // Many streams
            -10,    // High negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.BOUNCY),  // 18 + 15 = 33 points
            Ordinance.FLAMETHROWER  // 8 points, total: 59 + 33 + 8 = 100
    );

    // Frost Lance - Freezing + Piercing ice beam that slows all in line
    public static final WeaponConfig FROST_LANCE_PRESET = new WeaponConfig(
            "Frost Lance",
            12,     // Medium damage
            10,     // Medium fire rate
            10,     // Long range
            0,      // Perfect accuracy
            7,      // Small magazine
            7,     // Medium reload
            15,     // Fast projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FREEZING, BulletEffect.PIERCING),  // 14 + 20 = 34 points
            Ordinance.PLASMA  // 15 points, total: 51 + 34 + 15 = 100
    );

    // Shrapnel Cannon - Fragmenting + Explosive explosive fragments
    public static final WeaponConfig SHRAPNEL_CANNON_PRESET = new WeaponConfig(
            "Shrapnel Cannon",
            14,     // Medium damage
            8,     // Slow fire rate
            9,     // Medium range
            -3,     // Slight inaccuracy
            3,      // Small magazine
            5,     // Long reload
            7,      // Slow projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FRAGMENTING, BulletEffect.EXPLOSIVE),  // 22 + 25 = 47 points
            Ordinance.ROCKET  // 20 points, total: 45 + 47 + 20 = 100
    );

    // Seeking Inferno - Incendiary + Homing heat-seeking fire darts
    public static final WeaponConfig SEEKING_INFERNO_PRESET = new WeaponConfig(
            "Seeking Inferno",
            10,      // Low damage
            12,     // Medium fire rate
            9,     // Medium range
            0,      // Good accuracy
            9,     // Medium magazine
            8,     // Medium reload
            9,     // Fast projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING),  // 18 + 30 = 48 points
            Ordinance.DART  // 5 points, total: 47 + 48 + 5 = 100
    );

    // EMP Burst Gun - Electric + Fragmenting splits into electric bursts
    public static final WeaponConfig EMP_BURST_GUN_PRESET = new WeaponConfig(
            "EMP Burst Gun",
            14,     // Low damage
            12,     // Slow fire rate
            12,     // Medium range
            0,      // Good accuracy
            8,      // Small magazine
            11,     // Medium reload
            15,     // Medium projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.ELECTRIC, BulletEffect.FRAGMENTING),  // 16 + 22 = 38 points
            Ordinance.BULLET  // 0 points, total: 62 + 38 = 100
    );

    // Glacial Mortar - Freezing + Explosive ice grenade area freeze
    public static final WeaponConfig GLACIAL_MORTAR_PRESET = new WeaponConfig(
            "Glacial Mortar",
            20,     // Medium damage
            13,     // Slow fire rate
            12,     // Good range
            -5,     // Poor accuracy
            4,      // Small magazine
            7,     // Long reload
            10,     // Slow projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.FREEZING, BulletEffect.EXPLOSIVE),  // 14 + 25 = 39 points
            Ordinance.GRENADE  // 10 points, total: 51 + 39 + 10 = 100
    );

    // Phantom Needles - Homing + Bouncy tracking darts that bounce
    public static final WeaponConfig PHANTOM_NEEDLES_PRESET = new WeaponConfig(
            "Phantom Needles",
            6,      // Very low damage
            20,     // High fire rate
            8,     // Medium range
            -5,      // Good accuracy
            13,     // Medium magazine
            5,     // Fast reload
            13,     // Fast projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.HOMING, BulletEffect.BOUNCY),  // 30 + 15 = 45 points
            Ordinance.DART  // 5 points, total: 50 + 45 + 5 = 100
    );

    // Corrosive Cannon - Poison + Explosive massive poison explosion
    public static final WeaponConfig CORROSIVE_CANNON_PRESET = new WeaponConfig(
            "Corrosive Cannon",
            13,     // Medium damage
            7,     // Very slow fire rate
            12,     // Medium range
            0,      // Perfect accuracy
            2,      // Tiny magazine
            5,     // Long reload
            8,      // Slow projectile
            0,      // Single shot
            -10,    // Negative damping
            Set.of(BulletEffect.POISON, BulletEffect.EXPLOSIVE),  // 18 + 25 = 43 points
            Ordinance.ROCKET  // 20 points, total: 51 + 43 + 20 = 100
    );

    public int getAttributePoints() {
        return damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping;
    }
}

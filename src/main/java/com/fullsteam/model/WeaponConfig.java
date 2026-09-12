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
    public int minDamage = 0;
    public int maxDamage = 0;
    public DamageVarianceFormula varianceFormula = DamageVarianceFormula.UNIFORM;
    public int fireRate = 0;
    public int range = 0;
    public int accuracy = 0;
    public int magazineSize = 0;
    public int reloadTime = 0;
    public int projectileSpeed = 0;
    public int bulletsPerShot = 0;
    public int linearDamping = 0;
    public int handling = 0;
    public int caliber = 0;
    public int knockback = 0;
    public Set<BulletEffect> bulletEffects = new HashSet<>();
    public Ordinance ordinance = Ordinance.PROJECTILE;

    public Weapon buildWeapon() {
        // Strip effects that don't apply to the chosen ordnance (e.g. HOMING on a
        // beam) so no weapon ever carries an inert effect into gameplay.
        Set<BulletEffect> effects = BulletEffect.validFor(ordinance, bulletEffects);
        return new Weapon(type,
                minDamage,
                maxDamage,
                varianceFormula != null ? varianceFormula : DamageVarianceFormula.UNIFORM,
                fireRate,
                range,
                accuracy,
                magazineSize,
                reloadTime,
                projectileSpeed,
                bulletsPerShot,
                linearDamping,
                handling,
                caliber,
                knockback,
                effects,
                ordinance);
    }

    public static final WeaponConfig ASSAULT_RIFLE_PRESET = new WeaponConfig(
            "Assault Rifle",
            10,
            10,
            DamageVarianceFormula.GAUSSIAN,
            30,
            5,
            0,
            36,
            5,
            4,
            0,
            0,
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    public static final WeaponConfig HAND_CANNON_PRESET = new WeaponConfig(
            "Hand Cannon",
            12,
            25,
            DamageVarianceFormula.MIN_OR_MAX,
            21,
            9,
            0,
            4,
            14,
            12,
            0,
            0,
            -5,
            3,
            5,
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Pure Sniper Rifle - Maximizes range, damage, and projectile speed
    public static final WeaponConfig SNIPER_RIFLE_PRESET = new WeaponConfig(
            "Sniper Rifle",
            21,
            21,
            DamageVarianceFormula.GAUSSIAN,
            2,
            27,
            0,
            5,
            5,
            25,
            0,
            -2,
            -5,
            0,
            1,
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Minigun - Maximum suppressive fire with extreme fire rate and magazine
    public static final WeaponConfig MINIGUN_PRESET = new WeaponConfig(
            "Minigun",
            3,
            11,     // 14 dmg pts - 4 var cost = 10 effective
            DamageVarianceFormula.MIN_WEIGHTED,
            30,     // Maximum fire rate
            10,     // range ~969 units
            -10,    // Terrible accuracy
            47,     // Maximum magazine size
            2,      // reload ~2.55s
            16,     // speed ~815 units/sec
            0,      // single stream
            -5,     // No damping
            0,      // handling
            0,      // caliber
            0,      // knockback
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Example: Bouncy SMG with damping
    public static final WeaponConfig BOUNCY_SMG_PRESET = new WeaponConfig(
            "Bouncy SMG",
            5,
            10,
            DamageVarianceFormula.INVERSE_GAUSSIAN,
            30,
            2,
            0,
            29,
            5,
            4,
            0,
            -5,
            5,     // handling
            0,     // caliber
            0,     // knockback
            Set.of(BulletEffect.BOUNCY),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Rocket Launcher
    public static final WeaponConfig ROCKET_LAUNCHER_PRESET = new WeaponConfig(
            "Rocket Launcher",
            15,
            16,
            DamageVarianceFormula.GAUSSIAN,
            1,
            7,
            0,
            7,
            9,
            0,     // projectile speed
            0,
            0,
            -5,     // handling
            20,     // caliber
            5,      // knockback
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    public static final WeaponConfig SHOTGUN_PRESET = new WeaponConfig(
            "Shotgun",
            12,
            20,     // 32 dmg pts + 4 var cost = 36 effective
            DamageVarianceFormula.MAX_WEIGHTED,
            1,      // low fire rate
            5,      // range ~533 units
            -10,    // Poor accuracy
            32,     // magazine
            2,      // reload
            11,     // speed
            25,     // Multiple streams
            -10,    // Negative damping
            0,      // handling
            0,      // caliber
            8,      // knockback
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Concussion Cannon
    public static final WeaponConfig CONCUSSION_CANNON_PRESET = new WeaponConfig(
            "Concussion Cannon",
            10,
            20,
            DamageVarianceFormula.INVERSE_GAUSSIAN,
            8,      // fire rate
            8,      // range
            0,      // accuracy
            16,     // magazine
            8,      // reload
            8,      // projectile speed
            0,      // single shot
            0,      // damping
            0,      // handling
            10,     // caliber
            12,     // knockback
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Piercing Effect Showcase
    public static final WeaponConfig PIERCING_RIFLE_PRESET = new WeaponConfig(
            "Piercing Rifle",
            20,
            20,
            DamageVarianceFormula.GAUSSIAN,
            8,      // fire rate
            13,     // range
            0,      // accuracy
            5,      // Small magazine
            5,      // reload
            5,      // speed
            0,      // Single shot
            0,      // No damping
            0,      // handling
            0,      // caliber
            4,      // knockback
            Set.of(BulletEffect.PIERCING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Incendiary Effect Showcase
    public static final WeaponConfig INCENDIARY_SHOTGUN_PRESET = new WeaponConfig(
            "Incendiary Shotgun",
            12,
            20,
            DamageVarianceFormula.UNIFORM,
            10,     // Slow fire rate
            5,      // range
            -8,     // Poor accuracy
            8,      // Small magazine
            9,      // reload
            5,      // speed
            15,     // Multiple pellets
            0,      // No damping
            0,      // handling
            0,      // caliber
            6,      // knockback
            Set.of(BulletEffect.INCENDIARY),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Fragmenting Effect Showcase
    public static final WeaponConfig CLUSTER_MORTAR_PRESET = new WeaponConfig(
            "Cluster Mortar",
            18,
            20,
            DamageVarianceFormula.GAUSSIAN,
            4,      // Slow fire rate
            7,      // range
            -5,     // Poor accuracy
            9,      // Small magazine
            12,     // reload
            7,      // projectile speed
            0,      // Single shot
            -3,     // Negative damping
            -5,     // handling
            10,     // caliber
            4,      // knockback
            Set.of(BulletEffect.FRAGMENTING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Homing Effect Showcase
    public static final WeaponConfig SEEKER_DART_PRESET = new WeaponConfig(
            "Seeker Dart",
            10,
            20,
            DamageVarianceFormula.UNIFORM,
            10,     // Medium fire rate
            5,      // range
            0,      // Good accuracy
            1,      // Small magazine
            9,      // reload
            15,     // projectile speed
            0,      // Single shot
            0,      // No damping
            5,      // handling
            -5,     // caliber
            0,      // knockback
            Set.of(BulletEffect.HOMING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Electric Effect Showcase
    public static final WeaponConfig ARC_PISTOL_PRESET = new WeaponConfig(
            "Arc Pistol",
            10,
            27,
            DamageVarianceFormula.MIN_OR_MAX,
            15,     // Medium fire rate
            7,      // range
            0,      // Decent accuracy
            3,      // Small magazine
            12,     // reload
            5,      // speed
            0,      // Single shot
            0,      // No damping
            5,      // handling
            0,      // caliber
            0,      // knockback
            Set.of(BulletEffect.ELECTRIC),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Poison Effect Showcase
    public static final WeaponConfig TOXIC_SPRAYER_PRESET = new WeaponConfig(
            "Toxic Sprayer",
            4,
            8,
            DamageVarianceFormula.UNIFORM,
            30,     // fire rate
            3,      // range
            -5,     // Poor accuracy
            23,     // magazine
            5,      // reload
            3,      // speed
            10,     // Multiple streams
            -3,     // Negative damping
            0,      // handling
            0,      // caliber
            0,      // knockback
            Set.of(BulletEffect.POISON),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Freezing Effect Showcase
    public static final WeaponConfig ICE_CANNON_PRESET = new WeaponConfig(
            "Ice Cannon",
            19,
            20,
            DamageVarianceFormula.GAUSSIAN,
            10,     // Medium fire rate
            13,     // range
            0,      // Good accuracy
            13,     // Small magazine
            12,     // reload
            4,      // speed
            0,      // Single shot
            0,      // No damping
            -5,     // handling
            0,      // caliber
            0,      // knockback
            Set.of(BulletEffect.FREEZING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Twin Sixes
    public static final WeaponConfig TWIN_SIXES_PRESET = new WeaponConfig(
            "Twin Sixes",
            15,
            25,
            DamageVarianceFormula.UNIFORM,
            26,     // fire rate
            7,      // range
            -5,     // Imperfect accuracy
            9,      // magazine
            4,      // reload
            5,      // speed
            5,      // 2 bullets per shot
            0,      // No damping
            5,      // handling
            0,      // caliber
            4,      // knockback
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Laser Rifle
    public static final WeaponConfig LASER_RIFLE_PRESET = new WeaponConfig(
            "Laser Rifle",
            12,
            13,
            DamageVarianceFormula.GAUSSIAN,
            6,      // Medium fire rate
            7,      // range
            0,      // Perfect accuracy
            15,     // Medium magazine
            3,      // reload
            0,      // speed
            0,      // Single beam
            -10,    // Not used for beams
            0,      // handling
            4,      // caliber
            0,      // knockback
            Set.of(),
            Ordinance.LASER  // total: 100 pts
    );

    // Plasma Cannon
    public static final WeaponConfig PLASMA_CANNON_PRESET = new WeaponConfig(
            "Plasma Cannon",
            8,
            15,
            DamageVarianceFormula.UNIFORM,
            8,      // Medium fire rate
            5,      // range
            0,      // Perfect accuracy
            17,     // Medium magazine
            9,      // reload
            0,      // speed
            0,      // Single beam
            -10,    // Not used for beams
            -5,     // handling
            8,      // caliber
            0,      // knockback
            Set.of(),
            Ordinance.PLASMA_BEAM  // total: 100 pts
    );

    // Railgun
    public static final WeaponConfig RAILGUN_PRESET = new WeaponConfig(
            "Railgun",
            11,
            11,
            DamageVarianceFormula.GAUSSIAN,
            1,      // Very slow fire rate
            9,      // range
            0,      // Perfect accuracy
            6,      // small magazine
            4,      // reload
            0,      // speed
            0,      // Single beam
            -10,    // Not used for beams
            -2,     // handling
            5,      // caliber
            0,      // knockback
            Set.of(BulletEffect.PIERCING),
            Ordinance.PLASMA_BEAM  // total: 100 pts
    );

    // Ricochet Laser
    public static final WeaponConfig RICOCHET_LASER_PRESET = new WeaponConfig(
            "Ricochet Laser",
            7,
            7,
            DamageVarianceFormula.GAUSSIAN,
            6,      // medium fire rate
            13,     // range
            0,      // perfect accuracy
            9,      // magazine
            3,      // reload
            0,      // speed
            0,      // single beam
            -10,    // not used for beams
            0,      // handling
            0,      // caliber
            0,      // knockback
            Set.of(BulletEffect.BOUNCY),
            Ordinance.LASER  // total: 100 pts
    );

    // ===== ADVANCED COMBINATION WEAPONS =====

    // Storm Caller
    public static final WeaponConfig STORM_CALLER_PRESET = new WeaponConfig(
            "Storm Caller",
            1,
            4,
            DamageVarianceFormula.UNIFORM,
            29,     // fire rate
            4,      // range
            0,      // Good accuracy
            12,     // Medium magazine
            2,      // reload
            17,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            0,      // handling
            -5,     // caliber
            0,      // knockback
            Set.of(BulletEffect.ELECTRIC, BulletEffect.HOMING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Napalm Launcher
    public static final WeaponConfig NAPALM_LAUNCHER_PRESET = new WeaponConfig(
            "Napalm Launcher",
            15,
            16,
            DamageVarianceFormula.GAUSSIAN,
            2,      // Very slow fire rate
            5,      // range
            -3,     // Poor accuracy
            8,      // Very small magazine
            9,      // reload
            3,      // projectile speed
            0,      // Single shot
            -3,     // Negative damping
            -5,     // handling
            10,     // caliber
            0,      // knockback
            Set.of(BulletEffect.INCENDIARY, BulletEffect.EXPLOSIVE),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Venom Needler
    public static final WeaponConfig VENOM_NEEDLER_PRESET = new WeaponConfig(
            "Venom Needler",
            5,
            5,
            DamageVarianceFormula.GAUSSIAN,
            27,     // fire rate
            9,      // range
            0,      // Perfect accuracy
            7,      // Medium magazine
            4,      // reload
            11,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            5,      // handling
            -5,     // caliber
            0,      // knockback
            Set.of(BulletEffect.POISON, BulletEffect.PIERCING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Frost Lance
    public static final WeaponConfig FROST_LANCE_PRESET = new WeaponConfig(
            "Frost Lance",
            14,
            15,
            DamageVarianceFormula.GAUSSIAN,
            10,     // Medium fire rate
            5,      // range
            0,      // Perfect accuracy
            7,      // Small magazine
            3,      // reload
            17,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            0,      // handling
            5,      // caliber
            0,      // knockback
            Set.of(BulletEffect.FREEZING, BulletEffect.PIERCING),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Shrapnel Cannon
    public static final WeaponConfig SHRAPNEL_CANNON_PRESET = new WeaponConfig(
            "Shrapnel Cannon",
            10,
            15,
            DamageVarianceFormula.UNIFORM,
            8,      // Slow fire rate
            5,      // range
            -3,     // Slight inaccuracy
            8,      // Small magazine
            2,      // reload
            3,      // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            -5,     // handling
            16,     // caliber
            4,      // knockback
            Set.of(BulletEffect.FRAGMENTING, BulletEffect.EXPLOSIVE),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Phantom Needles
    public static final WeaponConfig PHANTOM_NEEDLES_PRESET = new WeaponConfig(
            "Phantom Needles",
            2,
            4,
            DamageVarianceFormula.UNIFORM,
            30,     // fire rate
            4,      // range
            -5,     // Good accuracy
            12,     // magazine
            2,      // reload
            16,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            5,      // handling
            -5,     // caliber
            0,      // knockback
            Set.of(BulletEffect.HOMING, BulletEffect.BOUNCY),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    // Spitter Spit: Long range, high caliber, slow moving, poison projectile
    public static final WeaponConfig SPITTER_SPIT_PRESET = new WeaponConfig(
            "Poison Spit",
            12,
            18,
            DamageVarianceFormula.UNIFORM,
            0,      // fire rate (lowered to slow down spitter cadence)
            24,     // long range
            0,      // baseline accuracy
            10,     // magazine
            0,      // reload
            1,      // slow moving projectile speed
            0,      // single shot
            -5,     // negative damping
            0,      // handling
            18,     // high caliber
            0,      // knockback
            Set.of(BulletEffect.POISON),
            Ordinance.PROJECTILE  // total: 100 pts
    );

    public int getAttributePoints() {
        return minDamage + maxDamage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping + handling + caliber + knockback;
    }
}

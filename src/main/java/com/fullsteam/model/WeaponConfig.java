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
                damage,
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
            20,
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
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    public static final WeaponConfig HAND_CANNON_PRESET = new WeaponConfig(
            "Hand Cannon",
            40,
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
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Pure Sniper Rifle - Maximizes range, damage, and projectile speed
    // Focus: Long-range precision with high damage per shot
    // Trade-offs: Very slow fire rate, small magazine, long reload
    public static final WeaponConfig SNIPER_RIFLE_PRESET = new WeaponConfig(
            "Sniper Rifle",
            40,
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
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Minigun - Maximum suppressive fire with extreme fire rate and magazine
    // Focus: Overwhelming volume of fire, area suppression
    // Trade-offs: Low damage per bullet, terrible accuracy, slow projectiles
    public static final WeaponConfig MINIGUN_PRESET = new WeaponConfig(
            "Minigun",
            10,     // damage 20 (low per-bullet, compensated by volume)
            30,     // Maximum fire rate (insane rate of fire)
            10,     // range ~969 units
            -10,    // Terrible accuracy (massive spread)
            47,     // Maximum magazine size (never stop shooting)
            2,     // reload ~2.55s
            16,     // speed ~815 units/sec
            0,      // 5 bullets per shot (multi-barrel spin-up)
            -5,      // No damping
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Example: Bouncy SMG with damping (85 points + 15 for bouncy effect = 100 total)
    public static final WeaponConfig BOUNCY_SMG_PRESET = new WeaponConfig(
            "Bouncy SMG",
            15,
            30,
            2,
            0,
            29,
            5,
            4,
            0,
            -5,
            5,     // handling (nimble)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(BulletEffect.BOUNCY),
            Ordinance.PROJECTILE  // total: 100 pts (attr 85 + fx 15 + ord 0)
    );

    // Example: Rocket Launcher (89 pts total)
    public static final WeaponConfig ROCKET_LAUNCHER_PRESET = new WeaponConfig(
            "Rocket Launcher",
            31,     // damage (5 pts → knockback; explosion AOE compensates)
            1,
            7,
            0,
            7,
            9,
            0,     // projectile speed
            0,
            0,
            -5,     // handling (heavy)
            20,     // caliber ×2.00
            5,      // knockback → 200k (direct-hit shove)
            Set.of(BulletEffect.EXPLOSIVE),
            Ordinance.PROJECTILE  // total: 100 pts (attr 75 + fx 25 + ord 0)
    );

    public static final WeaponConfig SHOTGUN_PRESET = new WeaponConfig(
            "Shotgun",
            36,     // damage 46
            1,     // low fire rate
            5,     // range ~533 units
            -10,    // Poor accuracy (spread)
            32,     // magazine ~35 rounds (8 pts → knockback)
            2,     // reload ~3.32s
            11,     // speed ~720 units/sec
            25,     // Multiple streams
            -10,     // Negative damping for spread
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            8,      // knockback → 320k (per-pellet split ≈ 1.2× total blowback)
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Concussion Cannon - showcases KNOCKBACK: a heavy slug that physically shoves
    // targets back. High knockback + caliber make it sluggish to carry, since the
    // KNOCKBACK→HANDLING and DAMAGE→HANDLING couplings stack on its move speed.
    public static final WeaponConfig CONCUSSION_CANNON_PRESET = new WeaponConfig(
            "Concussion Cannon",
            30,     // damage
            8,      // fire rate
            8,      // range
            0,      // accuracy
            16,     // magazine
            8,      // reload
            8,      // projectile speed
            0,      // single shot
            0,      // damping
            0,      // handling (couplings drag it down)
            10,     // caliber ×1.50 (big slug)
            12,     // knockback → ~480k impulse per hit
            Set.of(),
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Piercing Effect Showcase - Sniper that goes through enemies
    public static final WeaponConfig PIERCING_RIFLE_PRESET = new WeaponConfig(
            "Piercing Rifle",
            40,     // damage 50
            8,      // fire rate ~3.6 shots/s (4 pts → knockback)
            13,     // range ~933 units
            0,     // Good accuracy
            5,      // Small magazine
            5,     // reload ~2.55s
            5,     // speed ~539 units/sec
            0,      // Single shot
            0,      // No damping
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            4,      // knockback → 160k (heavy AP round shoves each pierced target)
            Set.of(BulletEffect.PIERCING),  // effects: 20 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 80 + fx 20 + ord 0)
    );

    // Incendiary Effect Showcase - Sets targets on fire
    public static final WeaponConfig INCENDIARY_SHOTGUN_PRESET = new WeaponConfig(
            "Incendiary Shotgun",
            32,     // damage (6 pts → knockback; fire DoT carries it)
            10,      // Slow fire rate
            5,     // range ~533 units
            -8,     // Poor accuracy (shotgun spread)
            8,      // Small magazine
            9,     // reload ~1.85s
            5,     // speed ~539 units/sec
            15,      // Multiple pellets
            0,      // No damping
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            6,      // knockback → 240k (close-range blast, per-pellet split)
            Set.of(BulletEffect.INCENDIARY),  // effects: 18 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 82 + fx 18 + ord 0)
    );

    // Fragmenting Effect Showcase - Projectiles split on impact
    public static final WeaponConfig CLUSTER_MORTAR_PRESET = new WeaponConfig(
            "Cluster Mortar",
            38,     // damage 48
            4,      // Slow fire rate
            7,     // range ~653 units
            -5,     // Poor accuracy (mortar arc)
            9,      // Small magazine
            12,     // reload ~1.40s (4 pts → knockback)
            7,     // projectile speed
            0,      // Single shot
            -3,     // Negative damping for arc
            -5,     // handling (heavy)
            10,     // caliber ×1.50
            4,      // knockback → 160k (shell + fragments shove)
            Set.of(BulletEffect.FRAGMENTING),  // effects: 22 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 78 + fx 22 + ord 0)
    );

    // Homing Effect Showcase - Tracking projectiles
    public static final WeaponConfig SEEKER_DART_PRESET = new WeaponConfig(
            "Seeker Dart",
            30,     // damage 40
            10,      // Medium fire rate
            5,     // range ~533 units
            0,      // Good accuracy
            1,      // Small magazine
            9,     // reload ~1.85s
            15,     // projectile speed
            0,      // Single shot
            0,      // No damping
            5,     // handling (nimble)
            -5,     // caliber ×0.75
            0,      // knockback (no recoil)
            Set.of(BulletEffect.HOMING),  // effects: 30 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 70 + fx 30 + ord 0)
    );

    // Electric Effect Showcase - Chain lightning
    public static final WeaponConfig ARC_PISTOL_PRESET = new WeaponConfig(
            "Arc Pistol",
            37,     // damage 47
            15,     // Medium fire rate
            7,     // range ~653 units
            0,      // Decent accuracy
            3,      // Small magazine
            12,     // reload ~1.50s
            5,     // speed ~539 units/sec
            0,      // Single shot
            0,      // No damping
            5,     // handling (nimble)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(BulletEffect.ELECTRIC),  // effects: 16 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 84 + fx 16 + ord 0)
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
            0,      // handling (move-speed mult)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(BulletEffect.POISON),  // effects: 22 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 78 + fx 22 + ord 0)
    );

    // Freezing Effect Showcase - Slows enemies
    public static final WeaponConfig ICE_CANNON_PRESET = new WeaponConfig(
            "Ice Cannon",
            39,     // damage 49
            10,     // Medium fire rate
            13,     // range ~933 units
            0,      // Good accuracy
            13,      // Small magazine
            12,     // reload ~1.50s
            4,     // speed ~498 units/sec
            0,      // Single shot
            0,      // No damping
            -5,     // handling (heavy)
            0,      // caliber (size mult)
            0,      // knockback (no recoil)
            Set.of(BulletEffect.FREEZING),  // effects: 14 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 86 + fx 14 + ord 0)
    );

    // Twin Sixes - Dual-barrel high-damage weapon with spread
    public static final WeaponConfig TWIN_SIXES_PRESET = new WeaponConfig(
            "Twin Sixes",
            40,     // Very high damage
            26,     // fire rate ~6.6 shots/s (4 pts → knockback)
            7,     // range ~653 units
            -5,     // Imperfect accuracy (spread)
            9,     // magazine 12 rounds (two six-shooters)
            4,     // reload ~3.0s (slow, like reloading two revolvers)
            5,     // speed ~539 units/sec
            5,      // 2 bullets per shot
            0,      // No damping
            5,     // handling (nimble)
            0,      // caliber (size mult)
            4,      // knockback → 160k (revolver kick, split across 2 shots)
            Set.of(),  // no effects
            Ordinance.PROJECTILE  // total: 100 pts (attr 100 + fx 0 + ord 0)
    );

    // Laser Rifle - Instant-hit precision beam weapon
    public static final WeaponConfig LASER_RIFLE_PRESET = new WeaponConfig(
            "Laser Rifle",
            25,     // damage 35 (4 pts moved to caliber for a wider beam)
            6,      // Medium fire rate
            7,     // range ~653 units
            0,      // Perfect accuracy (beams are always accurate)
            15,     // Medium magazine
            3,     // reload ~3.04s
            0,     // speed ~300 units/sec
            0,      // Single beam
            -10,      // Not used for beams
            0,      // handling (move-speed mult)
            4,      // caliber ×1.20 → beam width 2.40
            0,      // knockback (no recoil)
            Set.of(),  // no effects
            Ordinance.LASER  // total: 100 pts (attr 50 + fx 0 + ord 50)
    );

    // Plasma Cannon - Continuous damage beam weapon
    public static final WeaponConfig PLASMA_CANNON_PRESET = new WeaponConfig(
            "Plasma Cannon",
            23,     // damage 33 (8 pts moved to caliber for a thick beam)
            8,      // Medium fire rate
            5,     // range ~533 units
            0,      // Perfect accuracy
            17,     // Medium magazine
            9,     // reload ~1.85s
            0,     // speed ~300 units/sec
            0,      // Single beam
            -10,      // Not used for beams
            -5,     // handling (heavy)
            8,      // caliber ×1.40 → beam width 2.80 (thicker than the laser)
            0,      // knockback (no recoil)
            Set.of(),  // no effects
            Ordinance.PLASMA_BEAM  // total: 100 pts (attr 55 + fx 0 + ord 45)
    );

    // Railgun - Slow, heavy piercing plasma beam that punches through a whole line
    public static final WeaponConfig RAILGUN_PRESET = new WeaponConfig(
            "Railgun",
            22,     // damage ~32
            1,      // Very slow fire rate
            9,      // range ~813 units (long)
            0,      // Perfect accuracy
            6,      // small magazine
            4,      // reload ~2.6s
            0,      // speed (unused for beams)
            0,      // Single beam
            -10,    // Not used for beams (reclaims budget)
            -2,     // handling (heavy)
            5,      // caliber ×1.25 → beam width 2.50
            0,      // knockback (no recoil)
            Set.of(BulletEffect.PIERCING),  // punches through everything in line
            Ordinance.PLASMA_BEAM  // total: 100 pts (attr 35 + fx 20 + ord 45)
    );

    // Ricochet Laser - Instant-hit laser that reflects off walls (BOUNCY beam).
    // Heavy range investment gives the beam the travel budget to bank around
    // corners through several bounces; thin (baseline caliber) and precise.
    public static final WeaponConfig RICOCHET_LASER_PRESET = new WeaponConfig(
            "Ricochet Laser",
            14,     // damage ~24
            6,      // medium fire rate
            13,     // range ~817 units (long, to fuel bounces)
            0,      // perfect accuracy (beams)
            9,      // magazine
            3,      // reload ~3.3s
            0,      // speed (unused for beams)
            0,      // single beam
            -10,    // not used for beams (reclaims budget)
            0,      // handling
            0,      // caliber (thin, precise beam)
            0,      // knockback (inert on beams)
            Set.of(BulletEffect.BOUNCY),  // reflects off walls; 15 pts
            Ordinance.LASER  // total: 100 pts (attr 35 + fx 15 + ord 50)
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
            17,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            0,      // handling (move-speed mult)
            -5,     // caliber ×0.75
            0,      // knockback (no recoil)
            Set.of(BulletEffect.ELECTRIC, BulletEffect.HOMING),  // effects: 46 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 54 + fx 46 + ord 0)
    );

    // Napalm Launcher - Incendiary + Explosive massive burning explosion zones
    public static final WeaponConfig NAPALM_LAUNCHER_PRESET = new WeaponConfig(
            "Napalm Launcher",
            31,     // damage 41
            2,      // Very slow fire rate
            5,     // range ~533 units
            -3,     // Poor accuracy
            8,      // Very small magazine
            9,     // reload ~1.85s
            3,     // projectile speed
            0,      // Single shot
            -3,     // Negative damping
            -5,     // handling (heavy)
            10,     // caliber ×1.50
            0,      // knockback (no recoil)
            Set.of(BulletEffect.INCENDIARY, BulletEffect.EXPLOSIVE),  // effects: 43 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 57 + fx 43 + ord 0)
    );

    // Venom Needler - Poison + Piercing precise needles that poison all targets
    public static final WeaponConfig VENOM_NEEDLER_PRESET = new WeaponConfig(
            "Venom Needler",
            10,     // Low direct damage
            27,     // fire rate ~6.7 shots/s
            9,     // range ~759 units
            0,      // Perfect accuracy
            7,     // Medium magazine
            4,     // reload ~2.78s
            11,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            5,     // handling (nimble)
            -5,     // caliber ×0.75
            0,      // knockback (no recoil)
            Set.of(BulletEffect.POISON, BulletEffect.PIERCING),  // effects: 42 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 58 + fx 42 + ord 0)
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
            17,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            0,      // handling (move-speed mult)
            5,     // caliber ×1.25
            0,      // knockback (no recoil)
            Set.of(BulletEffect.FREEZING, BulletEffect.PIERCING),  // effects: 34 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 66 + fx 34 + ord 0)
    );

    // Shrapnel Cannon - Fragmenting + Explosive explosive fragments
    public static final WeaponConfig SHRAPNEL_CANNON_PRESET = new WeaponConfig(
            "Shrapnel Cannon",
            25,     // damage 35
            8,     // Slow fire rate
            5,     // range ~533 units
            -3,     // Slight inaccuracy
            8,      // Small magazine
            2,     // reload ~3.32s
            3,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            -5,     // handling (heavy)
            16,     // caliber ×1.80 (4 pts → knockback)
            4,      // knockback → 160k (cannon blast; fragments inherit the punch)
            Set.of(BulletEffect.FRAGMENTING, BulletEffect.EXPLOSIVE),  // effects: 47 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 53 + fx 47 + ord 0)
    );

    // Phantom Needles - Homing + Bouncy tracking darts that bounce
    public static final WeaponConfig PHANTOM_NEEDLES_PRESET = new WeaponConfig(
            "Phantom Needles",
            6,      // Very low damage
            30,     // fire rate ~7.1 shots/s
            4,     // range ~466 units
            -5,      // Good accuracy
            12,     // magazine 22 rounds
            2,     // reload ~3.32s
            16,     // projectile speed
            0,      // Single shot
            -10,    // Negative damping
            5,     // handling (nimble)
            -5,     // caliber ×0.75
            0,      // knockback (no recoil)
            Set.of(BulletEffect.HOMING, BulletEffect.BOUNCY),  // effects: 45 pts
            Ordinance.PROJECTILE  // total: 100 pts (attr 55 + fx 45 + ord 0)
    );

    public int getAttributePoints() {
        return damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping + handling + caliber + knockback;
    }
}

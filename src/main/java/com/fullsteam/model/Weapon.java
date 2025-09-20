package com.fullsteam.model;

import lombok.Data;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Data
public class Weapon {
    public static final Map<WeaponAttribute, Integer> ASSAULT_RIFLE_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 20,
            WeaponAttribute.FIRE_RATE, 25,
            WeaponAttribute.RANGE, 10,
            WeaponAttribute.ACCURACY, 0,
            WeaponAttribute.MAGAZINE_SIZE, 25,
            WeaponAttribute.RELOAD_TIME, 10,
            WeaponAttribute.PROJECTILE_SPEED, 10,
            WeaponAttribute.BULLETS_PER_SHOT, 0,
            WeaponAttribute.LINEAR_DAMPING, 0
    );

    public static final Map<WeaponAttribute, Integer> HAND_CANNON_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 40,
            WeaponAttribute.FIRE_RATE, 5,
            WeaponAttribute.RANGE, 20,
            WeaponAttribute.ACCURACY, -5,
            WeaponAttribute.MAGAZINE_SIZE, 5,
            WeaponAttribute.RELOAD_TIME, 15,
            WeaponAttribute.PROJECTILE_SPEED, 20,
            WeaponAttribute.BULLETS_PER_SHOT, 0,
            WeaponAttribute.LINEAR_DAMPING, 0
    );
    
    // Example: Explosive Sniper Rifle (75 points + 25 for explosive effect = 100 total)
    public static final Map<WeaponAttribute, Integer> EXPLOSIVE_SNIPER_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 35,      // 45 damage
            WeaponAttribute.FIRE_RATE, 2,   // 2 shots/sec
            WeaponAttribute.RANGE, 15,      // 4000 range
            WeaponAttribute.ACCURACY, 0,    // 1.0 accuracy
            WeaponAttribute.MAGAZINE_SIZE, 3, // 8 rounds
            WeaponAttribute.RELOAD_TIME, 20, // 1.2 sec reload
            WeaponAttribute.PROJECTILE_SPEED, 0, // 200 speed
            WeaponAttribute.BULLETS_PER_SHOT, 0, // 1 bullet
            WeaponAttribute.LINEAR_DAMPING, 0    // No damping
    );
    
    // Example: Bouncy SMG with damping (85 points + 15 for bouncy effect = 100 total)
    public static final Map<WeaponAttribute, Integer> BOUNCY_SMG_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 15,      // 25 damage
            WeaponAttribute.FIRE_RATE, 30,  // 16 shots/sec
            WeaponAttribute.RANGE, 5,       // 2000 range
            WeaponAttribute.ACCURACY, -5,   // 0.9 accuracy
            WeaponAttribute.MAGAZINE_SIZE, 20, // 25 rounds
            WeaponAttribute.RELOAD_TIME, 5,  // 3.3 sec reload
            WeaponAttribute.PROJECTILE_SPEED, 10, // 700 speed
            WeaponAttribute.BULLETS_PER_SHOT, 0,  // 1 bullet
            WeaponAttribute.LINEAR_DAMPING, 5     // 0.2 damping
    );
    
    // Example: Rocket Launcher (55 points + 20 for rocket + 25 for explosive = 100 total)
    public static final Map<WeaponAttribute, Integer> ROCKET_LAUNCHER_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 25,      // 35 damage
            WeaponAttribute.FIRE_RATE, 1,   // 1.5 shots/sec
            WeaponAttribute.RANGE, 12,      // 3400 range
            WeaponAttribute.ACCURACY, 0,    // 1.0 accuracy
            WeaponAttribute.MAGAZINE_SIZE, 2, // 7 rounds
            WeaponAttribute.RELOAD_TIME, 15, // 1.9 sec reload
            WeaponAttribute.PROJECTILE_SPEED, 0, // 200 speed (x1.5 = 300 with rocket)
            WeaponAttribute.BULLETS_PER_SHOT, 0, // 1 bullet
            WeaponAttribute.LINEAR_DAMPING, 0    // No damping
    );
    
    // Example: Grenade Launcher (68 points + 10 for grenade + 22 for fragmenting = 100 total)
    public static final Map<WeaponAttribute, Integer> GRENADE_LAUNCHER_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 25,      // 35 damage
            WeaponAttribute.FIRE_RATE, 3,   // 2.5 shots/sec
            WeaponAttribute.RANGE, 8,       // 2600 range
            WeaponAttribute.ACCURACY, -2,   // 0.96 accuracy
            WeaponAttribute.MAGAZINE_SIZE, 6, // 11 rounds
            WeaponAttribute.RELOAD_TIME, 18, // 1.48 sec reload
            WeaponAttribute.PROJECTILE_SPEED, 5, // 450 speed (x0.6 = 270 with grenade)
            WeaponAttribute.BULLETS_PER_SHOT, 0, // 1 bullet
            WeaponAttribute.LINEAR_DAMPING, 3    // 0.12 damping
    );
    
    // Example: Plasma Rifle (65 points + 15 for plasma + 20 for piercing = 100 total)
    public static final Map<WeaponAttribute, Integer> PLASMA_RIFLE_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 20,      // 30 damage
            WeaponAttribute.FIRE_RATE, 15,  // 8.5 shots/sec
            WeaponAttribute.RANGE, 10,      // 3000 range
            WeaponAttribute.ACCURACY, 0,    // 1.0 accuracy
            WeaponAttribute.MAGAZINE_SIZE, 10, // 15 rounds
            WeaponAttribute.RELOAD_TIME, 10, // 2.6 sec reload
            WeaponAttribute.PROJECTILE_SPEED, 10, // 700 speed (x1.2 = 840 with plasma)
            WeaponAttribute.BULLETS_PER_SHOT, 0, // 1 bullet
            WeaponAttribute.LINEAR_DAMPING, 0    // No damping
    );

    private final String name;

    private final double damage;
    private final double fireRate;
    private final double range;
    private final double accuracy;
    private final int magazineSize;
    private final double reloadTime;
    private final double projectileSpeed;
    private final int bulletsPerShot;

    private int currentAmmo;
    private final double linearDamping;
    private final Set<BulletEffect> bulletEffects;
    private final Ordinance ordinance;

    public enum WeaponAttribute {
        // Damage: 10-50 (base 10 + 1 per point, max 40 points)
        DAMAGE(0, 40) {
            @Override
            public double compute(int points) {
                validate(points);
                int damagePoints = Math.min(points, 40);
                return 10 + damagePoints;
            }
        },
        // Fire Rate: 1-16 shots/sec (base 1 + 0.5 per point, max 30 points)
        FIRE_RATE(0, 30) {
            @Override
            public double compute(int points) {
                validate(points);
                return 1.0 + points * 0.5;
            }
        },
        // Range: 1000-2500 units (base 1000 + 200 per point, max 25 points)
        RANGE(-10, 25) {
            @Override
            public double compute(int points) {
                validate(points);
                return 1000 + points * 200;
            }
        },
        // Accuracy: 0.5-0.99 (base 0.5 + 0.02 per point, max 25 points)
        ACCURACY(-10, 0) {
            @Override
            public double compute(int points) {
                validate(points);
                return 1.0 + (points * 0.02);
            }
        },
        // Magazine Size: 5-35 rounds (base 5 + 1 per point, max 30 points)
        MAGAZINE_SIZE(0, 30) {
            @Override
            public double compute(int points) {
                validate(points);
                return 5 + points;
            }
        },
        // Reload Time: 0.5-4.0 seconds (base 4.0 - 0.15 per point, max 25 points)
        RELOAD_TIME(-7, 25) {
            @Override
            public double compute(int points) {
                validate(points);
                return 4.0 - (points * 0.14);
            }
        },
        // Projectile Speed: 2000-17000 units/sec (base 2000 + 500 per point, max 30 points)
        PROJECTILE_SPEED(0, 30) {
            @Override
            public double compute(int points) {
                validate(points);
                return 200 + (points * 50);
            }
        },
        // Bullets Per Shot: 1-12 bullets (base 1 + 1 per 3 points, max 15 points)
        BULLETS_PER_SHOT(0, 33) {
            @Override
            public double compute(int points) {
                validate(points);
                return 1 + ((double) points / 3);
            }
        },
        // Linear Damping: 0.0-0.8 (base 0.0 + 0.04 per point, max 20 points)
        // Higher values make projectiles slow down more over distance
        LINEAR_DAMPING(0, 20) {
            @Override
            public double compute(int points) {
                validate(points);
                return points * 0.04;
            }
        };

        private final int min;
        private final int max;

        WeaponAttribute(int min, int max) {
            this.min = min;
            this.max = max;
        }
        
        public int getMin() { return min; }
        public int getMax() { return max; }

        public void validate(int input) {
            if (input < min || input > max) {
                throw new IllegalArgumentException("points allocated to " + this + " is out of range [" + min + ", " + max + "]");
            }
        }

        public double compute(Map<WeaponAttribute, Integer> points) {
            return compute(points.getOrDefault(this, 0));
        }

        public abstract double compute(int points);
    }

    public Weapon(String name, Map<WeaponAttribute, Integer> points) {
        this(name, points, new HashSet<>(), Ordinance.BULLET);
    }
    
    public Weapon(String name, Map<WeaponAttribute, Integer> points, Set<BulletEffect> bulletEffects) {
        this(name, points, bulletEffects, Ordinance.BULLET);
    }
    
    public Weapon(String name, Map<WeaponAttribute, Integer> points, Set<BulletEffect> bulletEffects, Ordinance ordinance) {
        // Calculate total points including bullet effects and ordinance
        int attributePoints = points.values().stream().mapToInt(Integer::intValue).sum();
        int effectPoints = bulletEffects.stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordinancePoints = ordinance.getPointCost();
        int totalPoints = attributePoints + effectPoints + ordinancePoints;
        
        if (totalPoints > 100) {
            throw new IllegalArgumentException("Total points cannot exceed 100. Current total: " + totalPoints + 
                " (Attributes: " + attributePoints + ", Effects: " + effectPoints + ", Ordinance: " + ordinancePoints + ")");
        }
        
        this.name = name;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;

        // Calculate weapon stats based on point allocation
        // Each attribute has a base value + scaling based on points allocated
        this.damage = WeaponAttribute.DAMAGE.compute(points);
        this.fireRate = WeaponAttribute.FIRE_RATE.compute(points);
        this.range = WeaponAttribute.RANGE.compute(points);
        this.accuracy = WeaponAttribute.ACCURACY.compute(points);
        this.magazineSize = (int) WeaponAttribute.MAGAZINE_SIZE.compute(points);
        this.reloadTime = WeaponAttribute.RELOAD_TIME.compute(points);
        // Apply ordinance speed multiplier to projectile speed
        this.projectileSpeed = WeaponAttribute.PROJECTILE_SPEED.compute(points) * ordinance.getSpeedMultiplier();
        this.bulletsPerShot = (int) WeaponAttribute.BULLETS_PER_SHOT.compute(points);
        this.linearDamping = WeaponAttribute.LINEAR_DAMPING.compute(points);
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
    
    public boolean hasBulletEffect(BulletEffect effect) {
        return bulletEffects.contains(effect);
    }
    
    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }
    
    public int getTotalEffectPoints() {
        return bulletEffects.stream().mapToInt(BulletEffect::getPointCost).sum();
    }
    
    public int getTotalOrdinancePoints() {
        return ordinance.getPointCost();
    }
}
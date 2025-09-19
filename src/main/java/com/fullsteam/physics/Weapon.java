package com.fullsteam.physics;

import lombok.Data;

import java.util.Map;

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
            WeaponAttribute.BULLETS_PER_SHOT, 0
    );

    public static final Map<WeaponAttribute, Integer> HAND_CANNON_PRESET = Map.of(
            WeaponAttribute.DAMAGE, 40,
            WeaponAttribute.FIRE_RATE, 5,
            WeaponAttribute.RANGE, 20,
            WeaponAttribute.ACCURACY, -5,
            WeaponAttribute.MAGAZINE_SIZE, 5,
            WeaponAttribute.RELOAD_TIME, 15,
            WeaponAttribute.PROJECTILE_SPEED, 20,
            WeaponAttribute.BULLETS_PER_SHOT, 0
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
        };

        private final int min;
        private final int max;

        WeaponAttribute(int min, int max) {
            this.min = min;
            this.max = max;
        }

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
        int totalPoints = points.values().stream().mapToInt(Integer::intValue).sum();
        if (totalPoints > 100) {
            throw new IllegalArgumentException("Total points cannot exceed 100. Current total: " + totalPoints);
        }
        this.name = name;

        // Calculate weapon stats based on point allocation
        // Each attribute has a base value + scaling based on points allocated
        this.damage = WeaponAttribute.DAMAGE.compute(points);
        this.fireRate = WeaponAttribute.FIRE_RATE.compute(points);
        this.range = WeaponAttribute.RANGE.compute(points);
        this.accuracy = WeaponAttribute.ACCURACY.compute(points);
        this.magazineSize = (int) WeaponAttribute.MAGAZINE_SIZE.compute(points);
        this.reloadTime = WeaponAttribute.RELOAD_TIME.compute(points);
        this.projectileSpeed = WeaponAttribute.PROJECTILE_SPEED.compute(points);
        this.bulletsPerShot = (int) WeaponAttribute.BULLETS_PER_SHOT.compute(points);
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
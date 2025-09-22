package com.fullsteam.model;

import java.util.Map;

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
            return 0.3 + points * 0.3;
        }
    },
    // Range: 1000-2500 units (base 1000 + 200 per point, max 25 points)
    RANGE(-10, 25) {
        @Override
        public double compute(int points) {
            validate(points);
            return 300 + points * 300;
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
            return 120 + (points * 15);
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
    LINEAR_DAMPING(-10, 0) {
        @Override
        public double compute(int points) {
            validate(points);
            return 0.03 - (points * 0.04);
        }
    };

    private final int min;
    private final int max;

    WeaponAttribute(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public void validate(int input) {
        if (input < min || input > max) {
            throw new IllegalArgumentException("points allocated to " + this + " is out of range [" + min + ", " + max + "]");
        }
    }

    public abstract double compute(int points);
}

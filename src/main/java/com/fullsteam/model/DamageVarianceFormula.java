package com.fullsteam.model;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum DamageVarianceFormula {
    UNIFORM("Uniform", "Equal probability across the entire damage range", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            return min + ThreadLocalRandom.current().nextDouble() * (max - min);
        }
    },
    GAUSSIAN("Gaussian", "Bell curve centered at the average damage; extreme hits are rare", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            double mid = (min + max) / 2.0;
            // Standard deviation such that 99.7% of values fall within [min, max] (3 sigma)
            double stdDev = (max - min) / 6.0;
            double val = mid + ThreadLocalRandom.current().nextGaussian() * stdDev;
            return Math.max(min, Math.min(max, val));
        }
    },
    INVERSE_GAUSSIAN("Inverse Gaussian", "Bimodal distribution favoring hits near minimum or maximum damage", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double span = max - min;
            double stdDev = span / 6.0;
            if (rng.nextBoolean()) {
                // Skewed near min
                double val = min + Math.abs(rng.nextGaussian() * stdDev);
                return Math.max(min, Math.min(max, val));
            } else {
                // Skewed near max
                double val = max - Math.abs(rng.nextGaussian() * stdDev);
                return Math.max(min, Math.min(max, val));
            }
        }
    },
    MIN_OR_MAX("Min or Max", "Coin flip: deals strictly minimum or maximum damage with nothing in between", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            return ThreadLocalRandom.current().nextBoolean() ? min : max;
        }
    },
    MIN_WEIGHTED("Min Weighted", "Skewed toward minimum damage; refunds 4 points", -4) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            // Triangular distribution skewed to min
            double u = ThreadLocalRandom.current().nextDouble();
            return min + (max - min) * (1.0 - Math.sqrt(1.0 - u));
        }
    },
    MAX_WEIGHTED("Max Weighted", "Skewed toward maximum damage; costs 4 additional points", 4) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            // Triangular distribution skewed to max
            double u = ThreadLocalRandom.current().nextDouble();
            return min + (max - min) * Math.sqrt(u);
        }
    },
    CRIT_GAMBLE("Crit Gamble", "Steep power-law spike: most shots deal minimum damage with rare catastrophic criticals; refunds 6 points", -6) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            double u = ThreadLocalRandom.current().nextDouble();
            return min + (max - min) * (u * u * u);
        }
    },
    HEAVY_SLUG("Heavy Slug", "Heavy munitions heavily biased toward maximum damage with rare lower-damage grazing hits; costs 6 additional points", 6) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            double u = ThreadLocalRandom.current().nextDouble();
            double inv = 1.0 - u;
            return min + (max - min) * (1.0 - inv * inv * inv);
        }
    },
    MAGAZINE_RAMP("Magazine Ramp", "Damage ramps up progressively as the magazine empties, peaking on the final bullet; neutral cost", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            if (weapon == null) {
                return (min + max) / 2.0;
            }
            int magSize = weapon.getMagazineSize();
            if (magSize <= 1) {
                return max;
            }
            int r = Math.max(1, Math.min(magSize, roundNumber));
            double progress = (double) (r - 1) / (double) (magSize - 1);
            return min + (max - min) * progress;
        }
    },
    FIRST_STRIKE("First Strike", "The opening round of a fresh magazine deals maximum damage, with subsequent rounds tapering down; neutral cost", 0) {
        @Override
        public double evaluate(double min, double max, Weapon weapon, int roundNumber) {
            if (max <= min) {
                return min;
            }
            if (weapon == null) {
                return (min + max) / 2.0;
            }
            int magSize = weapon.getMagazineSize();
            if (magSize <= 1) {
                return max;
            }
            int r = Math.max(1, Math.min(magSize, roundNumber));
            double progress = 1.0 - (double) (r - 1) / (double) (magSize - 1);
            return min + (max - min) * progress;
        }
    };

    private final String displayName;
    private final String description;
    private final int pointCost;

    DamageVarianceFormula(String displayName, String description, int pointCost) {
        this.displayName = displayName;
        this.description = description;
        this.pointCost = pointCost;
    }

    /**
     * Backward-compatible evaluation without weapon context.
     */
    public double evaluate(double min, double max) {
        return evaluate(min, max, null, 1);
    }

    /**
     * Evaluation with weapon context; defaults round number from the weapon's magazine state.
     */
    public double evaluate(double min, double max, Weapon weapon) {
        int roundNumber = (weapon != null) ? weapon.getRoundNumberInMagazine() : 1;
        return evaluate(min, max, weapon, roundNumber);
    }

    /**
     * Full evaluation with weapon context and explicit round number in the current magazine.
     */
    public abstract double evaluate(double min, double max, Weapon weapon, int roundNumber);

    /**
     * Calculates the expected value (mean) of the damage distribution given min and max damage stat values.
     */
    public double expectedValue(double min, double max) {
        if (max <= min) {
            return min;
        }
        return switch (this) {
            case UNIFORM, GAUSSIAN, INVERSE_GAUSSIAN, MIN_OR_MAX, MAGAZINE_RAMP, FIRST_STRIKE -> (min + max) / 2.0;
            case MIN_WEIGHTED -> min + (max - min) / 3.0; // Mean of triangular dist (a=min, b=max, c=min) = (a+a+b)/3
            case MAX_WEIGHTED ->
                    min + 2.0 * (max - min) / 3.0; // Mean of triangular dist (a=min, b=max, c=max) = (a+b+b)/3
            case CRIT_GAMBLE -> min + (max - min) * 0.25; // Mean of cubic distribution u^3
            case HEAVY_SLUG -> min + (max - min) * 0.75; // Mean of inverted cubic 1 - (1-u)^3
        };
    }
}

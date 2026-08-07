package com.fullsteam.model;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum DamageVarianceFormula {
    UNIFORM("Uniform", "Equal probability across the entire damage range", 0) {
        @Override
        public double evaluate(double min, double max) {
            if (max <= min) {
                return min;
            }
            return min + ThreadLocalRandom.current().nextDouble() * (max - min);
        }
    },
    GAUSSIAN("Gaussian", "Bell curve centered at the average damage; extreme hits are rare", 0) {
        @Override
        public double evaluate(double min, double max) {
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
        public double evaluate(double min, double max) {
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
        public double evaluate(double min, double max) {
            if (max <= min) {
                return min;
            }
            return ThreadLocalRandom.current().nextBoolean() ? min : max;
        }
    },
    MIN_WEIGHTED("Min Weighted", "Skewed toward minimum damage; refunds 4 points", -4) {
        @Override
        public double evaluate(double min, double max) {
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
        public double evaluate(double min, double max) {
            if (max <= min) {
                return min;
            }
            // Triangular distribution skewed to max
            double u = ThreadLocalRandom.current().nextDouble();
            return min + (max - min) * Math.sqrt(u);
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

    public abstract double evaluate(double min, double max);

    /**
     * Calculates the expected value (mean) of the damage distribution given min and max damage stat values.
     */
    public double expectedValue(double min, double max) {
        if (max <= min) {
            return min;
        }
        return switch (this) {
            case UNIFORM, GAUSSIAN, INVERSE_GAUSSIAN, MIN_OR_MAX -> (min + max) / 2.0;
            case MIN_WEIGHTED -> min + (max - min) / 3.0; // Mean of triangular dist (a=min, b=max, c=min) = (a+a+b)/3
            case MAX_WEIGHTED ->
                    min + 2.0 * (max - min) / 3.0; // Mean of triangular dist (a=min, b=max, c=max) = (a+b+b)/3
        };
    }
}

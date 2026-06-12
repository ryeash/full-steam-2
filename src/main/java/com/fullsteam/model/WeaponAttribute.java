package com.fullsteam.model;

import lombok.Getter;

/**
 * A primary-weapon attribute and the curve mapping allocated <em>points</em> to
 * a gameplay <em>stat value</em>.
 *
 * <p>Points are the budgeted resource (a loadout's attribute points + effects +
 * ordinance must stay within {@code Weapon}'s 100-point cap), and the cost of a
 * point is always linear. The <b>curve shape</b> is therefore the build-diversity
 * lever, independent of cost:
 * <ul>
 *   <li><b>linear</b> — proportional; neutral.</li>
 *   <li><b>diminishing</b> ({@link #sqrtDim}, {@link #approach}, {@link #decay}) —
 *       the last points in a stat are weak, so players spread points → generalist
 *       builds. Used where gameplay value saturates (fire rate as a DPS multiplier,
 *       engagement range, projectile dodge-difficulty, reload time).</li>
 *   <li><b>stepped</b> ({@link #stepped}) — value jumps at point thresholds; used
 *       for inherently discrete stats (bullets per shot).</li>
 * </ul>
 *
 * <p>Some attributes are <b>sacrifice-only</b> (max point = 0): they start at their
 * best value with zero points spent and can only be dialed below baseline with
 * <em>negative</em> points to reclaim budget for other stats (ACCURACY,
 * LINEAR_DAMPING; RANGE can also be sacrificed down to its floor).
 *
 * <p>Curves are clamped at their output so edge points can never produce nonsense
 * (e.g. negative accuracy or sub-floor reload). The point bounds {@code [min,max]}
 * are unchanged from the legacy tuning so existing presets and the customization
 * sliders keep working; only the value each point buys has been re-shaped.
 */
@Getter
public enum WeaponAttribute {
    // Damage: 10 (0 pts) → 50 (40 pts). Linear; the non-linearity lives in
    // FIRE_RATE (DPS multiplier) and Weapon's damage/bullets^0.7 split.
    DAMAGE(0, 40, linear(10, 1)),

    // Fire rate (shots/sec): diminishing. fireRate is a DPS *multiplier*
    // (cooldownMs = 1000/fireRate), so sqrt keeps DPS from exploding.
    // 0 pts → 0.5, 10 → 4.3, 20 → 5.9, 30 → 7.1 shots/sec.
    FIRE_RATE(0, 30, sqrtDim(0.5, 1.2)),

    // Range (units of projectile travel = lifetime*speed): diminishing toward a
    // ~1500 soft cap. Engagement utility saturates, so each point past mid-range
    // buys less. Sacrifice floor of 30 units at negative points.
    // 0 pts → 150, 10 → 807, 20 → 1144, 35 → 1369 (asymptote 1300).
    RANGE(-3, 35, approach(150, 1150, 15).atLeast(30)),

    // Accuracy: feeds spread = (1 - accuracy) * 0.17 rad in Player.shoot, so 1.0
    // is perfect. Sacrifice-only (max 0): you start perfect and trade accuracy
    // for budget. Clamped to [0,1] so the worst build is 0.17 rad (~9.7°) spread
    // rather than the old nonsensical negative-accuracy / wider spread.
    // 0 pts → 1.0 (no spread), -5 → 0.35, -10 → 0.0 (max spread).
    ACCURACY(-10, 0, linear(1.0, 0.13).clamp(0.0, 1.0)),

    // Magazine size: 5 (0 pts) → 45 (40 pts). Linear; already integer-stepped and
    // self-balanced by the reload tradeoff (sustained DPS = mag/(mag/fr + reload)).
    MAGAZINE_SIZE(0, 47, linear(3, 1)),

    // Reload time (seconds): diminishing toward a 0.6s floor — the first points
    // saved feel great and reload can't be trivialized to ~0. Sacrifice (negative
    // points) lengthens reload, capped at 6s.
    // 0 pts → 4.33s, 5 → 2.55s, 15 → 1.24s, 25 → 0.81s.
    RELOAD_TIME(-7, 25, decay(4.33, 0.6, 9).atMost(6.33)),

    // Projectile speed (units/sec, before ordinance multiplier): diminishing
    // toward ~1000, since dodge-difficulty saturates at high speed.
    // 0 pts → 300, 10 → 695, 20 → 868, 30 → 943.
    PROJECTILE_SPEED(0, 30, approach(300, 700, 12)),

    // Bullets per shot: stepped — one extra pellet per 5 points (UI snaps the
    // slider to multiples of 5). 0 pts → 1, 5 → 2, … 35 → 8. Total damage is split
    // across pellets by Weapon's damage/bullets^0.7, so more pellets ≠ more DPS.
    BULLETS_PER_SHOT(0, 35, stepped(1, 1, 5)),

    // Linear damping (dyn4j coefficient → exponential velocity decay): low (0.03)
    // by default; sacrifice-only (max 0) — negative points add drag (projectiles
    // slow over distance) to reclaim budget. Clamped ≥ 0.
    // 0 pts → 0.03, -5 → 0.23, -10 → 0.43.
    LINEAR_DAMPING(-10, 0, linear(0.03, -0.04).atLeast(0.0));

    private final int min;
    private final int max;
    private final Curve curve;

    WeaponAttribute(int min, int max, Curve curve) {
        this.min = min;
        this.max = max;
        this.curve = curve;
    }

    public void validate(int input) {
        if (input < min || input > max) {
            throw new IllegalArgumentException("points allocated to " + this + " is out of range [" + min + ", " + max + "]");
        }
    }

    /**
     * Map allocated points to the gameplay stat value. Throws if {@code points}
     * is outside this attribute's {@code [min,max]} bound.
     */
    public double compute(int points) {
        validate(points);
        return curve.at(points);
    }

    // ===== Curve model =====

    /**
     * A points → stat-value mapping. Composable: the {@code clamp}/{@code atLeast}/
     * {@code atMost} decorators wrap a curve to bound its output.
     */
    @FunctionalInterface
    public interface Curve {
        double at(int points);

        default Curve clamp(double lo, double hi) {
            return p -> Math.max(lo, Math.min(hi, at(p)));
        }

        default Curve atLeast(double lo) {
            return p -> Math.max(lo, at(p));
        }

        default Curve atMost(double hi) {
            return p -> Math.min(hi, at(p));
        }
    }

    // ----- Curve factories -----

    /** {@code base + perPoint * points} — proportional. */
    static Curve linear(double base, double perPoint) {
        return p -> base + perPoint * p;
    }

    /**
     * {@code base + scale * sqrt(points)} — concave/diminishing. Negative points
     * are treated as 0 (this shape is for boost-only stats).
     */
    static Curve sqrtDim(double base, double scale) {
        return p -> base + scale * Math.sqrt(Math.max(0, p));
    }

    /**
     * Increasing curve that approaches {@code base + span} as points grow, with
     * diminishing marginal gain. {@code tau} sets how fast it saturates (larger =
     * more gradual). For negative points it dips below {@code base}; pair with
     * {@link Curve#atLeast} to floor the sacrifice region.
     */
    static Curve approach(double base, double span, double tau) {
        return p -> base + span * (1 - Math.exp(-p / tau));
    }

    /**
     * Decreasing curve that starts at {@code start} (0 points) and decays toward
     * {@code floor} as points grow, with diminishing marginal benefit. For
     * negative points it rises above {@code start}; pair with {@link Curve#atMost}
     * to cap the sacrifice region.
     */
    static Curve decay(double start, double floor, double tau) {
        return p -> floor + (start - floor) * Math.exp(-p / tau);
    }

    /** {@code base + perStep * floor(points / pointsPerStep)} — discrete tiers. */
    static Curve stepped(double base, double perStep, int pointsPerStep) {
        return p -> base + perStep * Math.floor((double) p / pointsPerStep);
    }
}

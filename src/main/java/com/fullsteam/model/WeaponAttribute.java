package com.fullsteam.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

/**
 * A primary-weapon attribute and the curve mapping allocated <em>points</em> to
 * a gameplay <em>stat value</em>.
 *
 * <p>Points are the budgeted resource (a loadout's attribute points + effects +
 * ordinance must stay within {@code Weapon}'s 100-point cap), and the cost of a
 * point is always linear. The <b>curve shape</b> is the build-diversity lever,
 * independent of cost: linear (neutral), diminishing ({@link #sqrtDim},
 * {@link #approach}, {@link #decay} — last points are weak, spreads builds), or
 * stepped ({@link #stepped} — discrete thresholds).
 *
 * <h2>Coupling ("consequential" attributes)</h2>
 * Investing in one attribute can intrinsically shift others (e.g. fire rate
 * costs accuracy; a big magazine reloads slower). Couplings act in
 * <b>point space</b>: a source's investment fraction adds/subtracts
 * <em>effective points</em> on a target, which then runs through the target's
 * own curve. This keeps everything in one vocabulary (points → curve → clamp),
 * makes counterplay unit-symmetric (buy back exactly what was taken), and lets
 * the consequence inherit the target's own diminishing/stepped shape for free.
 * Resolution is a single pass from allocated points (never from already-coupled
 * values), so it is deterministic and loop-free — see {@link #resolve}.
 *
 * <p>Curves are clamped at their output and effective points are clamped to the
 * attribute's {@code [min,max]} domain, so couplings can never produce nonsense.
 */
@Getter
public enum WeaponAttribute {
    // Damage: 10 (0 pts) → 50 (40 pts). Linear.
    DAMAGE(0, 40, linear(10, 1)),

    // Fire rate (shots/sec): diminishing DPS multiplier. 0→0.5, 10→4.3, 30→7.1.
    FIRE_RATE(0, 30, sqrtDim(0.5, 1.2)),

    // Range (units): diminishing toward a soft cap; sacrifice floor 30.
    RANGE(-3, 35, approach(150, 1150, 15).atLeast(30)),

    // Accuracy: spread = (1 - accuracy) * 0.17 rad, so 1.0 is perfect. NOW
    // TWO-SIDED: negative points sacrifice accuracy for budget; positive points
    // buy it back (only useful to offset a coupling penalty, since the output
    // clamps at 1.0 — surplus is wasted). 0 pts → 1.0, -5 → 0.35, -10 → 0.0.
    ACCURACY(-10, 25, linear(1.0, 0.13).clamp(0.0, 1.0)),

    // Magazine size: 3 (0 pts) → 50 (47 pts). Linear, integer.
    MAGAZINE_SIZE(0, 47, linear(3, 1)),

    // Reload time (seconds): diminishing toward a 0.6s floor; sacrifice up to 6.33s.
    RELOAD_TIME(-7, 25, decay(4.33, 0.6, 9).atMost(6.33)),

    // Projectile speed (units/sec, before ordinance multiplier): diminishing toward ~1000.
    PROJECTILE_SPEED(0, 30, approach(300, 700, 12)),

    // Bullets per shot: stepped, one extra pellet per 5 points. 0→1, 5→2, … 35→8.
    BULLETS_PER_SHOT(0, 35, stepped(1, 1, 5)),

    // Linear damping (dyn4j drag coefficient): NOW TWO-SIDED. 0 pts → 0.09 (light
    // drag baseline). Positive points buy zippier rounds toward 0 drag (and trigger
    // the LINEAR_DAMPING→HANDLING kickback coupling — fast rounds buck the frame);
    // negative points add drag (rounds slow over distance) and refund budget. The
    // negative end is tuned to land near the old values so existing presets barely move.
    // -10 → 0.39, -5 → 0.24, 0 → 0.09, +10 → 0.0.
    LINEAR_DAMPING(-10, 10, linear(0.09, -0.03).clamp(0.0, 0.45)),

    // Handling: a move-speed multiplier applied to the wielder. TWO-SIDED:
    // negative points = heavier/slower (refund budget), positive = nimble (costs).
    // 0 pts → 1.0, -10 → 0.8, 15 → 1.3. Targeted by the DAMAGE→HANDLING coupling
    // (heavy-hitting weapons handle worse).
    HANDLING(-10, 15, linear(1.0, 0.02).clamp(0.75, 1.30)),

    // Caliber: a projectile/beam SIZE multiplier (1.0 = baseline). TWO-SIDED:
    // positive = bigger rounds (easier to land, costs budget) and drive the
    // CALIBER→SPEED/→MAGAZINE couplings (big rounds are slower and fewer); negative
    // = smaller rounds (refund budget). 0 pts → 1.0, +20 → 2.0, -10 → 0.5.
    CALIBER(-10, 20, linear(1.0, 0.05).clamp(0.5, 2.0)),

    // Knockback: per-hit impulse (dyn4j units) applied to the victim along the
    // projectile's travel direction — see CollisionProcessor. One-sided: 0 pts =
    // no shove (opt-in), scaling linearly. Drives the KNOCKBACK→HANDLING coupling
    // (Newton's 3rd: a punchy weapon shoves you too). 0 pts → 0, 15 → 600k.
    KNOCKBACK(0, 15, linear(0, 40_000));

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
     * Whether this attribute does anything on beam ordnance. KNOCKBACK is a
     * physical impulse applied to projectile hits (see CollisionProcessor); beams
     * are instant rays and never apply it, so the customizer disables it for them.
     */
    public boolean appliesToBeams() {
        return this != KNOCKBACK;
    }

    /** Fraction (0..1) of this attribute's range that {@code points} represents — the default coupling driver. */
    public double frac(int points) {
        if (max == min) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (points - (double) min) / (max - min)));
    }

    /**
     * Fraction (0..1) of <em>positive</em> investment only (0 at or below the
     * zero-point baseline). Used by couplings that should bite only deliberately
     * paid-for investment, not the default loadout (e.g. kickback only on rounds
     * a player made extra-zippy).
     */
    public double positiveFrac(int points) {
        if (max <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, points / (double) max));
    }

    /**
     * Fraction (0..1) of <em>negative</em> investment only (0 at or above the
     * zero-point baseline, 1.0 at the minimum). The mirror of {@link #positiveFrac}:
     * used by couplings that bite only on deliberately heavier/sacrificed builds
     * (e.g. a heavy weapon — negative HANDLING points — is a steadier platform).
     */
    public double negativeFrac(int points) {
        if (min >= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, points / (double) min));
    }

    /**
     * Map allocated points to the gameplay stat value <em>without</em> couplings.
     * Throws if {@code points} is outside this attribute's {@code [min,max]} bound.
     * Most callers want {@link #resolve} instead, which applies couplings.
     */
    public double compute(int points) {
        validate(points);
        return curve.at(points);
    }

    // ===== Coupling system =====

    /**
     * Where a coupling applies its delta:
     * <ul>
     *   <li>{@link #POINTS} — add effective <em>points</em> on the target, before
     *       its curve runs (so the consequence inherits the target's curve shape).
     *       The default and right choice for most stats.</li>
     *   <li>{@link #STAT} — add a flat delta to the target's <em>final value</em>,
     *       after the curve. Used for stats whose curve is steep/inverse where a
     *       point delta would cliff (e.g. reload time): a flat-seconds penalty is
     *       predictable and bounded.</li>
     * </ul>
     */
    public enum CouplingSpace { POINTS, STAT }

    /**
     * A directed link: when {@code source} is invested in, {@code strength}
     * (signed) scaled by {@code driver(sourcePoints)} (0..1) is applied to
     * {@code target} in {@code space}. For {@code POINTS} the strength is in
     * target points; for {@code STAT} it is in the target's own stat units.
     */
    public record Coupling(WeaponAttribute source, WeaponAttribute target,
                           double strength, IntToDoubleFunction driver, CouplingSpace space) {
        /** Point-space, full-range driver. */
        public Coupling(WeaponAttribute source, WeaponAttribute target, double strength) {
            this(source, target, strength, source::frac, CouplingSpace.POINTS);
        }
        /** Point-space with a custom driver. */
        public Coupling(WeaponAttribute source, WeaponAttribute target, double strength, IntToDoubleFunction driver) {
            this(source, target, strength, driver, CouplingSpace.POINTS);
        }
    }

    /**
     * Coupling matrix. Antagonistic tradeoffs plus synergies; each has a clear
     * physical story a player can intuit.
     */
    public static final List<Coupling> COUPLINGS = List.of(
            // Recoil climb: heavy fire-rate investment sprays. (counter: buy ACCURACY)
            new Coupling(FIRE_RATE, ACCURACY, -8),
            // Bigger magazine takes longer to seat — a flat-seconds penalty (STAT
            // space) up to +1.2s at a full mag. Stat-space avoids the cliff a point
            // penalty caused against RELOAD_TIME's steep sacrifice curve.
            new Coupling(MAGAZINE_SIZE, RELOAD_TIME, +1.2, MAGAZINE_SIZE::frac, CouplingSpace.STAT),
            // Synergy: flatter, faster trajectory is easier to land.
            new Coupling(PROJECTILE_SPEED, ACCURACY, +5),
            // Weight: hard-hitting weapons are heavy and handle worse.
            new Coupling(DAMAGE, HANDLING, -6),
            // Kickback: rounds made extra-zippy (positive LINEAR_DAMPING investment,
            // low drag) buck the frame and hurt handling. Positive-investment-only,
            // so baseline and draggy builds pay nothing — only deliberate zippiness.
            new Coupling(LINEAR_DAMPING, HANDLING, -5, LINEAR_DAMPING::positiveFrac),
            // Spread: more pellets fan out into a wider natural cone (less accurate).
            new Coupling(BULLETS_PER_SHOT, ACCURACY, -5),
            // Synergy: a longer barrel (range investment) gives higher muzzle
            // velocity. Positive-investment-only so baseline weapons aren't bumped.
            new Coupling(RANGE, PROJECTILE_SPEED, +4, RANGE::positiveFrac),
            // Synergy: a heavy weapon is a stable firing platform. Negative-handling
            // (deliberately heavy) builds claw back accuracy — only meaningful for a
            // weapon that sacrificed accuracy, since baseline accuracy already clamps
            // at 1.0. Reads allocated handling points (not the DAMAGE→HANDLING result),
            // so it stays first-order and loop-free like every other coupling.
            new Coupling(HANDLING, ACCURACY, +5, HANDLING::negativeFrac),
            // Heft: bigger rounds are slower and fewer fit in a magazine.
            // Positive-investment-only — baseline/small calibers pay nothing.
            new Coupling(CALIBER, PROJECTILE_SPEED, -6, CALIBER::positiveFrac),
            new Coupling(CALIBER, MAGAZINE_SIZE, -8, CALIBER::positiveFrac),
            // Newton's 3rd: a high-knockback weapon shoves the wielder too, modeled
            // as a handling (move-speed) penalty. Positive-investment-only so
            // zero-knockback weapons pay nothing.
            new Coupling(KNOCKBACK, HANDLING, -5, KNOCKBACK::positiveFrac)
    );

    /** One coupling's contribution for a build, in its {@link CouplingSpace}'s units. */
    public record AppliedCoupling(WeaponAttribute source, WeaponAttribute target,
                                  double delta, CouplingSpace space) {}

    /**
     * Full breakdown of a resolved build: final (coupled) and base (un-coupled)
     * stat values per attribute, plus the individual nonzero coupling contributions.
     */
    public record Resolution(Map<WeaponAttribute, Double> values,
                             Map<WeaponAttribute, Double> baseValues,
                             List<AppliedCoupling> appliedCouplings) {}

    /**
     * Resolve allocated points into final stat values, applying {@link #COUPLINGS}.
     *
     * <p>Couplings are computed from the <em>allocated</em> points (never from
     * already-coupled values), so the result is order-independent and loop-free.
     * Point-space couplings adjust effective points before the curve; stat-space
     * couplings adjust the final value after the curve. Both are clamped.
     */
    public static Map<WeaponAttribute, Double> resolve(Map<WeaponAttribute, Integer> allocated) {
        return resolveDetailed(allocated).values();
    }

    /** Like {@link #resolve} but also reports base values and the coupling breakdown. */
    public static Resolution resolveDetailed(Map<WeaponAttribute, Integer> allocated) {
        EnumMap<WeaponAttribute, Double> effective = new EnumMap<>(WeaponAttribute.class);
        EnumMap<WeaponAttribute, Double> baseValues = new EnumMap<>(WeaponAttribute.class);
        for (WeaponAttribute a : values()) {
            int p = allocated.getOrDefault(a, 0);
            a.validate(p);
            effective.put(a, (double) p);
            baseValues.put(a, a.curve.at(p));
        }

        List<AppliedCoupling> applied = new ArrayList<>();

        // Phase 1: point-space couplings adjust effective points (pre-curve).
        for (Coupling c : COUPLINGS) {
            if (c.space() != CouplingSpace.POINTS) {
                continue;
            }
            double delta = c.strength() * c.driver().applyAsDouble(allocated.getOrDefault(c.source(), 0));
            if (delta != 0.0) {
                effective.merge(c.target(), delta, Double::sum);
                applied.add(new AppliedCoupling(c.source(), c.target(), delta, CouplingSpace.POINTS));
            }
        }

        EnumMap<WeaponAttribute, Double> values = new EnumMap<>(WeaponAttribute.class);
        for (WeaponAttribute a : values()) {
            double eff = Math.max(a.min, Math.min(a.max, effective.get(a)));
            values.put(a, a.curve.at(eff));
        }

        // Phase 2: stat-space couplings adjust final values (post-curve), clamped
        // to the target's value range (the curve evaluated at its point bounds).
        for (Coupling c : COUPLINGS) {
            if (c.space() != CouplingSpace.STAT) {
                continue;
            }
            double delta = c.strength() * c.driver().applyAsDouble(allocated.getOrDefault(c.source(), 0));
            if (delta != 0.0) {
                WeaponAttribute t = c.target();
                double lo = Math.min(t.curve.at(t.min), t.curve.at(t.max));
                double hi = Math.max(t.curve.at(t.min), t.curve.at(t.max));
                values.merge(t, delta, (v, d) -> Math.max(lo, Math.min(hi, v + d)));
                applied.add(new AppliedCoupling(c.source(), t, delta, CouplingSpace.STAT));
            }
        }

        return new Resolution(values, baseValues, applied);
    }

    // ===== Curve model =====

    /**
     * A points → stat-value mapping over a real-valued point input (couplings
     * produce fractional effective points). Composable via clamp decorators.
     */
    @FunctionalInterface
    public interface Curve {
        double at(double points);

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

    /** {@code base + scale * sqrt(points)} — concave/diminishing. Negative points treated as 0. */
    static Curve sqrtDim(double base, double scale) {
        return p -> base + scale * Math.sqrt(Math.max(0, p));
    }

    /**
     * Increasing curve approaching {@code base + span} with diminishing marginal
     * gain ({@code tau} controls saturation). Dips below {@code base} for negative
     * points; pair with {@link Curve#atLeast} to floor the sacrifice region.
     */
    static Curve approach(double base, double span, double tau) {
        return p -> base + span * (1 - Math.exp(-p / tau));
    }

    /**
     * Decreasing curve from {@code start} (0 points) toward {@code floor}, with
     * diminishing benefit. Rises above {@code start} for negative points; pair
     * with {@link Curve#atMost} to cap the sacrifice region.
     */
    static Curve decay(double start, double floor, double tau) {
        return p -> floor + (start - floor) * Math.exp(-p / tau);
    }

    /** {@code base + perStep * floor(points / pointsPerStep)} — discrete tiers. */
    static Curve stepped(double base, double perStep, int pointsPerStep) {
        return p -> base + perStep * Math.floor(p / pointsPerStep);
    }
}

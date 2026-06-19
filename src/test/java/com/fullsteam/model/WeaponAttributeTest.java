package com.fullsteam.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.fullsteam.model.WeaponAttribute.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the {@link WeaponAttribute} point→value curves and the coupling
 * resolver so balance/coupling changes show up as an explicit, reviewable diff.
 */
class WeaponAttributeTest {

    private static final double EPS = 0.01;

    /** Resolve a single attribute's final (coupled) value from a sparse allocation. */
    private static double resolved(WeaponAttribute target, Map<WeaponAttribute, Integer> alloc) {
        return WeaponAttribute.resolve(alloc).get(target);
    }

    // ===== Base curves (no couplings) =====

    @Test
    void baseCurveEndpoints() {
        assertEquals(10, DAMAGE.compute(0), EPS);
        assertEquals(50, DAMAGE.compute(40), EPS);
        assertEquals(0.5, FIRE_RATE.compute(0), EPS);
        assertEquals(7.07, FIRE_RATE.compute(30), EPS);
        assertEquals(150, RANGE.compute(0), EPS);
        assertEquals(1188, RANGE.compute(35), 1.0);
        assertEquals(30, RANGE.compute(-3), EPS);          // sacrifice floor
        assertEquals(3, MAGAZINE_SIZE.compute(0), EPS);
        assertEquals(50, MAGAZINE_SIZE.compute(47), EPS);
        assertEquals(4.33, RELOAD_TIME.compute(0), EPS);
        assertEquals(300, PROJECTILE_SPEED.compute(0), EPS);
        assertEquals(943, PROJECTILE_SPEED.compute(30), 1.0);
        assertEquals(1, BULLETS_PER_SHOT.compute(0), EPS);
        assertEquals(8, BULLETS_PER_SHOT.compute(35), EPS);
        // LINEAR_DAMPING is two-sided: 0 → 0.09 baseline, -10 → 0.39 (drag), +10 → 0.0 (zippy).
        assertEquals(0.09, LINEAR_DAMPING.compute(0), EPS);
        assertEquals(0.39, LINEAR_DAMPING.compute(-10), EPS);
        assertEquals(0.0, LINEAR_DAMPING.compute(10), EPS);
    }

    @Test
    void accuracyIsTwoSidedAndClamped() {
        assertEquals(1.0, ACCURACY.compute(0), EPS);    // perfect by default
        assertEquals(0.35, ACCURACY.compute(-5), EPS);  // sacrifice
        assertEquals(0.0, ACCURACY.compute(-10), EPS);  // clamped low
        assertEquals(1.0, ACCURACY.compute(10), EPS);   // positive points clamp at 1.0 (only useful vs couplings)
    }

    @Test
    void handlingIsTwoSidedMoveSpeedMultiplier() {
        assertEquals(1.0, HANDLING.compute(0), EPS);
        assertEquals(0.8, HANDLING.compute(-10), EPS);  // heavy / refund
        assertEquals(1.3, HANDLING.compute(15), EPS);   // nimble / costs
    }

    @Test
    void computeRejectsOutOfRangePoints() {
        assertThrows(IllegalArgumentException.class, () -> DAMAGE.compute(41));
        assertThrows(IllegalArgumentException.class, () -> DAMAGE.compute(-1));
        assertThrows(IllegalArgumentException.class, () -> ACCURACY.compute(-11));
        assertThrows(IllegalArgumentException.class, () -> ACCURACY.compute(26));
    }

    // ===== Couplings =====

    @Test
    void resolveWithoutInvestmentLeavesBaselines() {
        Map<WeaponAttribute, Integer> none = Map.of();
        assertEquals(1.0, resolved(ACCURACY, none), EPS);
        assertEquals(1.0, resolved(HANDLING, none), EPS);
        assertEquals(4.33, resolved(RELOAD_TIME, none), EPS);
    }

    @Test
    void fireRateDragsAccuracyDown() {
        // FIRE_RATE 15 = half its range → -4 effective accuracy points → 0.48.
        assertEquals(0.48, resolved(ACCURACY, Map.of(FIRE_RATE, 15)), EPS);
    }

    @Test
    void accuracyInvestmentOffsetsRecoilOneForOne() {
        // Same -4 recoil, but +4 allocated accuracy points cancel it exactly → perfect.
        assertEquals(1.0, resolved(ACCURACY, Map.of(FIRE_RATE, 15, ACCURACY, 4)), EPS);
    }

    @Test
    void projectileSpeedSynergyPartlyOffsetsRecoil() {
        // FIRE_RATE 30 (-8) + PROJECTILE_SPEED 30 (+5) → -3 net → 0.61.
        assertEquals(0.61, resolved(ACCURACY, Map.of(FIRE_RATE, 30, PROJECTILE_SPEED, 30)), EPS);
    }

    @Test
    void bigMagazineSlowsReload() {
        double base = resolved(RELOAD_TIME, Map.of());
        double big = resolved(RELOAD_TIME, Map.of(MAGAZINE_SIZE, 47));
        assertTrue(big > base, "large magazine should lengthen reload");
        // Stat-space flat-seconds coupling: +1.2s at a full mag, no cliff.
        assertEquals(base + 1.2, big, EPS);
    }

    @Test
    void heavyDamageReducesHandling() {
        assertEquals(0.94, resolved(HANDLING, Map.of(DAMAGE, 20)), EPS);
        assertEquals(0.88, resolved(HANDLING, Map.of(DAMAGE, 40)), EPS);
    }

    @Test
    void morePelletsWidenSpread() {
        // Max bullets (35 pts) → -5 effective accuracy points → 0.35.
        assertEquals(0.35, resolved(ACCURACY, Map.of(BULLETS_PER_SHOT, 35)), EPS);
    }

    @Test
    void longBarrelSynergyBoostsProjectileSpeed() {
        // Baseline (no range investment) gets no synergy bump.
        assertEquals(300, resolved(PROJECTILE_SPEED, Map.of()), EPS);
        // Max range → +4 effective speed points → ~498.
        assertEquals(498, resolved(PROJECTILE_SPEED, Map.of(RANGE, 35)), 2.0);
    }

    @Test
    void knockbackCurveEndpoints() {
        assertEquals(0.0, KNOCKBACK.compute(0), EPS);        // opt-in: no shove at baseline
        assertEquals(600_000.0, KNOCKBACK.compute(15), EPS); // max investment
    }

    @Test
    void knockbackSplitsHeavierThanEvenAcrossPellets() {
        // Single shot keeps the full value; multi-pellet shots hold the total near
        // ~1.2x split evenly, so each pellet is heavier than an even 1/n share.
        assertEquals(1000.0, Weapon.knockbackPerBullet(1000.0, 1), EPS);  // 1.0x
        assertEquals(600.0, Weapon.knockbackPerBullet(1000.0, 2), EPS);   // 0.6x each
        assertEquals(400.0, Weapon.knockbackPerBullet(1000.0, 3), EPS);   // 0.4x each
        assertEquals(240.0, Weapon.knockbackPerBullet(1000.0, 5), EPS);   // 0.24x each
    }

    @Test
    void knockbackShovesTheWielder() {
        // Newton's 3rd: investing in knockback drags your own handling down.
        // Baseline (0 knockback) pays nothing.
        assertEquals(1.0, resolved(HANDLING, Map.of(KNOCKBACK, 0)), EPS);
        // Max knockback (15) → full -5 handling points → 1.0 + 0.02*(-5) = 0.90.
        assertEquals(0.90, resolved(HANDLING, Map.of(KNOCKBACK, 15)), EPS);
    }

    @Test
    void caliberBaselineAndExtremes() {
        assertEquals(1.0, CALIBER.compute(0), EPS);   // baseline size
        assertEquals(2.0, CALIBER.compute(20), EPS);  // double size
        assertEquals(0.5, CALIBER.compute(-10), EPS); // half size (refund)
    }

    @Test
    void bigCaliberIsSlowerAndHoldsLess() {
        // With speed/magazine investment, large caliber drags both down.
        assertTrue(resolved(PROJECTILE_SPEED, Map.of(PROJECTILE_SPEED, 20, CALIBER, 20))
                        < resolved(PROJECTILE_SPEED, Map.of(PROJECTILE_SPEED, 20)),
                "big caliber should reduce projectile speed");
        assertTrue(resolved(MAGAZINE_SIZE, Map.of(MAGAZINE_SIZE, 20, CALIBER, 20))
                        < resolved(MAGAZINE_SIZE, Map.of(MAGAZINE_SIZE, 20)),
                "big caliber should reduce magazine size");
        // Positive-investment-only: small/baseline caliber pays nothing.
        assertEquals(resolved(MAGAZINE_SIZE, Map.of(MAGAZINE_SIZE, 20)),
                resolved(MAGAZINE_SIZE, Map.of(MAGAZINE_SIZE, 20, CALIBER, -10)), EPS);
    }

    @Test
    void kickbackOnlyBitesPositiveDampingInvestment() {
        // Baseline (0) and draggy (negative) damping → no kickback penalty.
        assertEquals(1.0, resolved(HANDLING, Map.of(LINEAR_DAMPING, 0)), EPS);
        assertEquals(1.0, resolved(HANDLING, Map.of(LINEAR_DAMPING, -10)), EPS);
        // Zippy rounds (max positive) → full -5 handling points → 1.0 + 0.02*(-5) = 0.90.
        assertEquals(0.90, resolved(HANDLING, Map.of(LINEAR_DAMPING, 10)), EPS);
    }

    @Test
    void heavyWeaponIsSteadierFiringPlatform() {
        // Baseline / nimble handling grants no accuracy synergy (negativeFrac == 0).
        assertEquals(1.0, resolved(ACCURACY, Map.of(HANDLING, 0)), EPS);
        assertEquals(1.0, resolved(ACCURACY, Map.of(HANDLING, 15)), EPS);

        // A heavy weapon (handling -10) that sacrificed accuracy claws it back:
        // -5 acc pts (=0.35) + full +5 synergy points → 0 effective → perfect again.
        assertEquals(0.35, resolved(ACCURACY, Map.of(ACCURACY, -5)), EPS);
        assertEquals(1.0, resolved(ACCURACY, Map.of(ACCURACY, -5, HANDLING, -10)), EPS);

        // Half-heavy (handling -5) → +2.5 synergy points → -2.5 effective → 0.675.
        assertEquals(0.675, resolved(ACCURACY, Map.of(ACCURACY, -5, HANDLING, -5)), EPS);

        // Synergy is wasted with no accuracy sacrifice — baseline already clamps at 1.0.
        assertEquals(1.0, resolved(ACCURACY, Map.of(HANDLING, -10)), EPS);
    }

    @Test
    void couplingsReadAllocatedNotCoupledValues() {
        // DAMAGE→HANDLING must not feed back: investing in HANDLING shouldn't change
        // the damage-driven penalty, only the starting point it's applied to.
        double h0 = resolved(HANDLING, Map.of(DAMAGE, 40));            // 0 + (-6) → 0.88
        double h5 = resolved(HANDLING, Map.of(DAMAGE, 40, HANDLING, 5)); // 5 + (-6) → -1 → 0.98
        assertEquals(0.88, h0, EPS);
        assertEquals(0.98, h5, EPS);
    }
}

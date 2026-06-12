package com.fullsteam.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the {@link WeaponAttribute} point→value curves so balance changes show
 * up as an explicit, reviewable diff (and so the doc-comment sample values can't
 * silently drift from the code again). Values mirror the comments in the enum.
 */
class WeaponAttributeTest {

    private static final double EPS = 0.01;

    @Test
    void linearStatsMatchEndpoints() {
        assertEquals(10, WeaponAttribute.DAMAGE.compute(0), EPS);
        assertEquals(50, WeaponAttribute.DAMAGE.compute(40), EPS);
        assertEquals(5, WeaponAttribute.MAGAZINE_SIZE.compute(0), EPS);
        assertEquals(45, WeaponAttribute.MAGAZINE_SIZE.compute(40), EPS);
    }

    @Test
    void fireRateIsDiminishing() {
        assertEquals(0.5, WeaponAttribute.FIRE_RATE.compute(0), EPS);
        assertEquals(4.29, WeaponAttribute.FIRE_RATE.compute(10), EPS);
        assertEquals(7.07, WeaponAttribute.FIRE_RATE.compute(30), EPS);
        // Concave: the second 10 points add less than the first 10.
        double g1 = WeaponAttribute.FIRE_RATE.compute(10) - WeaponAttribute.FIRE_RATE.compute(0);
        double g2 = WeaponAttribute.FIRE_RATE.compute(20) - WeaponAttribute.FIRE_RATE.compute(10);
        assertTrue(g2 < g1, "fire rate gains should diminish");
    }

    @Test
    void rangeApproachesSoftCapAndFloorsOnSacrifice() {
        assertEquals(150, WeaponAttribute.RANGE.compute(0), EPS);
        assertEquals(807, WeaponAttribute.RANGE.compute(10), 1.0);
        assertEquals(1144, WeaponAttribute.RANGE.compute(20), 1.0);
        assertEquals(1369, WeaponAttribute.RANGE.compute(35), 1.0);
        assertTrue(WeaponAttribute.RANGE.compute(35) < 1500, "stays under the ~1500 soft cap");
        // Negative points hit the sacrifice floor rather than going nonsensical.
        assertEquals(30, WeaponAttribute.RANGE.compute(-3), EPS);
    }

    @Test
    void accuracyIsClampedToUnitInterval() {
        assertEquals(1.0, WeaponAttribute.ACCURACY.compute(0), EPS);   // perfect by default
        assertEquals(0.35, WeaponAttribute.ACCURACY.compute(-5), EPS);
        assertEquals(0.0, WeaponAttribute.ACCURACY.compute(-10), EPS); // clamped (raw would be -0.3)
    }

    @Test
    void reloadDecaysTowardFloorAndCapsOnSacrifice() {
        assertEquals(4.0, WeaponAttribute.RELOAD_TIME.compute(0), EPS);
        assertEquals(2.55, WeaponAttribute.RELOAD_TIME.compute(5), EPS);
        assertEquals(0.81, WeaponAttribute.RELOAD_TIME.compute(25), EPS);
        assertTrue(WeaponAttribute.RELOAD_TIME.compute(25) > 0.6, "never drops below the 0.6s floor");
        assertEquals(6.0, WeaponAttribute.RELOAD_TIME.compute(-7), EPS); // sacrifice capped at 6s
    }

    @Test
    void projectileSpeedIsDiminishing() {
        assertEquals(300, WeaponAttribute.PROJECTILE_SPEED.compute(0), EPS);
        assertEquals(696, WeaponAttribute.PROJECTILE_SPEED.compute(10), 1.0);
        assertEquals(943, WeaponAttribute.PROJECTILE_SPEED.compute(30), 1.0);
    }

    @Test
    void bulletsPerShotStepEveryFivePoints() {
        assertEquals(1, WeaponAttribute.BULLETS_PER_SHOT.compute(0), EPS);
        assertEquals(2, WeaponAttribute.BULLETS_PER_SHOT.compute(5), EPS);
        assertEquals(8, WeaponAttribute.BULLETS_PER_SHOT.compute(35), EPS);
        // Points between thresholds round down to the lower tier.
        assertEquals(1, WeaponAttribute.BULLETS_PER_SHOT.compute(4), EPS);
    }

    @Test
    void linearDampingSacrificeAddsDragAndStaysNonNegative() {
        assertEquals(0.03, WeaponAttribute.LINEAR_DAMPING.compute(0), EPS);
        assertEquals(0.23, WeaponAttribute.LINEAR_DAMPING.compute(-5), EPS);
        assertEquals(0.43, WeaponAttribute.LINEAR_DAMPING.compute(-10), EPS);
    }

    @Test
    void computeRejectsOutOfRangePoints() {
        assertThrows(IllegalArgumentException.class, () -> WeaponAttribute.DAMAGE.compute(41));
        assertThrows(IllegalArgumentException.class, () -> WeaponAttribute.DAMAGE.compute(-1));
        assertThrows(IllegalArgumentException.class, () -> WeaponAttribute.ACCURACY.compute(1)); // sacrifice-only
    }
}

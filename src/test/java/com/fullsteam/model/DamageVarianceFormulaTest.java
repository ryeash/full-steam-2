package com.fullsteam.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DamageVarianceFormulaTest {

    private static final double EPS = 1e-4;

    private Weapon createTestWeapon(int magazineSize, DamageVarianceFormula formula) {
        return new Weapon(
                "Test Weapon",
                10, 30, // Min dmg: 20, Max dmg: 40 with attribute formula linear(10, 1)
                formula,
                10, 10, 0,
                magazineSize, // magazine size points
                5, 10,
                0, // 1 bullet per shot
                0, 0, 0, 0,
                Set.of(),
                Ordinance.PROJECTILE
        );
    }

    @Test
    @DisplayName("Point costs and expected values for all formulas are correct")
    void testPointCostsAndExpectedValues() {
        assertEquals(-6, DamageVarianceFormula.CRIT_GAMBLE.getPointCost());
        assertEquals(6, DamageVarianceFormula.HEAVY_SLUG.getPointCost());
        assertEquals(0, DamageVarianceFormula.MAGAZINE_RAMP.getPointCost());
        assertEquals(0, DamageVarianceFormula.FIRST_STRIKE.getPointCost());

        double min = 10.0;
        double max = 50.0;
        double span = 40.0;

        assertEquals(min + span * 0.25, DamageVarianceFormula.CRIT_GAMBLE.expectedValue(min, max), EPS);
        assertEquals(min + span * 0.75, DamageVarianceFormula.HEAVY_SLUG.expectedValue(min, max), EPS);
        assertEquals((min + max) / 2.0, DamageVarianceFormula.MAGAZINE_RAMP.expectedValue(min, max), EPS);
        assertEquals((min + max) / 2.0, DamageVarianceFormula.FIRST_STRIKE.expectedValue(min, max), EPS);
    }

    @Test
    @DisplayName("CRIT_GAMBLE evaluates within bounds and skews low with rare spikes")
    void testCritGambleEvaluation() {
        double min = 20.0;
        double max = 100.0;
        double sum = 0.0;
        int trials = 10_000;

        for (int i = 0; i < trials; i++) {
            double dmg = DamageVarianceFormula.CRIT_GAMBLE.evaluate(min, max);
            assertTrue(dmg >= min - EPS && dmg <= max + EPS, "Damage should be within bounds: " + dmg);
            sum += dmg;
        }

        double empiricalMean = sum / trials;
        double theoreticalMean = DamageVarianceFormula.CRIT_GAMBLE.expectedValue(min, max);
        assertEquals(theoreticalMean, empiricalMean, 3.0, "Empirical mean should converge to expected value");
    }

    @Test
    @DisplayName("HEAVY_SLUG evaluates within bounds and skews high")
    void testHeavySlugEvaluation() {
        double min = 20.0;
        double max = 100.0;
        double sum = 0.0;
        int trials = 10_000;

        for (int i = 0; i < trials; i++) {
            double dmg = DamageVarianceFormula.HEAVY_SLUG.evaluate(min, max);
            assertTrue(dmg >= min - EPS && dmg <= max + EPS, "Damage should be within bounds: " + dmg);
            sum += dmg;
        }

        double empiricalMean = sum / trials;
        double theoreticalMean = DamageVarianceFormula.HEAVY_SLUG.expectedValue(min, max);
        assertEquals(theoreticalMean, empiricalMean, 3.0, "Empirical mean should converge to expected value");
    }

    @Test
    @DisplayName("MAGAZINE_RAMP scales from min on first round to max on final round")
    void testMagazineRampEvaluation() {
        Weapon weapon = createTestWeapon(10, DamageVarianceFormula.MAGAZINE_RAMP);
        double min = weapon.getMinDamage();
        double max = weapon.getMaxDamage();
        int magSize = weapon.getMagazineSize();

        // Round 1 should be min damage
        double round1 = DamageVarianceFormula.MAGAZINE_RAMP.evaluate(min, max, weapon, 1);
        assertEquals(min, round1, EPS);

        // Final round should be max damage
        double roundFinal = DamageVarianceFormula.MAGAZINE_RAMP.evaluate(min, max, weapon, magSize);
        assertEquals(max, roundFinal, EPS);

        // Intermediate rounds should strictly increase
        double prev = round1;
        for (int r = 2; r <= magSize; r++) {
            double dmg = DamageVarianceFormula.MAGAZINE_RAMP.evaluate(min, max, weapon, r);
            assertTrue(dmg > prev, "Round " + r + " damage (" + dmg + ") should exceed previous (" + prev + ")");
            prev = dmg;
        }
    }

    @Test
    @DisplayName("FIRST_STRIKE scales from max on first round to min on final round")
    void testFirstStrikeEvaluation() {
        Weapon weapon = createTestWeapon(10, DamageVarianceFormula.FIRST_STRIKE);
        double min = weapon.getMinDamage();
        double max = weapon.getMaxDamage();
        int magSize = weapon.getMagazineSize();

        // Round 1 should be max damage
        double round1 = DamageVarianceFormula.FIRST_STRIKE.evaluate(min, max, weapon, 1);
        assertEquals(max, round1, EPS);

        // Final round should be min damage
        double roundFinal = DamageVarianceFormula.FIRST_STRIKE.evaluate(min, max, weapon, magSize);
        assertEquals(min, roundFinal, EPS);

        // Intermediate rounds should strictly decrease
        double prev = round1;
        for (int r = 2; r <= magSize; r++) {
            double dmg = DamageVarianceFormula.FIRST_STRIKE.evaluate(min, max, weapon, r);
            assertTrue(dmg < prev, "Round " + r + " damage (" + dmg + ") should be less than previous (" + prev + ")");
            prev = dmg;
        }
    }

    @Test
    @DisplayName("Weapon.rollDamage() tracks round progression as currentAmmo is consumed")
    void testWeaponRollDamageMagazineTracking() {
        Weapon rampWeapon = createTestWeapon(5, DamageVarianceFormula.MAGAZINE_RAMP);
        double min = rampWeapon.getMinDamage();
        double max = rampWeapon.getMaxDamage();

        // At full magazine, round number defaults to 1 -> min damage
        assertEquals(min, rampWeapon.rollDamage(), EPS);

        // Simulate firing rounds: decrement ammo by 1 each time
        rampWeapon.setCurrentAmmo(rampWeapon.getMagazineSize() - 1); // 1 round fired
        assertEquals(min, rampWeapon.rollDamage(), EPS);

        rampWeapon.setCurrentAmmo(0); // All rounds fired -> last round
        assertEquals(max, rampWeapon.rollDamage(), EPS);

        // Reload resets magazine
        rampWeapon.reload();
        assertEquals(rampWeapon.getMagazineSize(), rampWeapon.getCurrentAmmo());
        assertEquals(min, rampWeapon.rollDamage(), EPS);
    }

    @Test
    @DisplayName("Formula evaluation handles edge cases gracefully")
    void testEdgeCases() {
        // min >= max
        assertEquals(25.0, DamageVarianceFormula.CRIT_GAMBLE.evaluate(25.0, 25.0), EPS);
        assertEquals(30.0, DamageVarianceFormula.HEAVY_SLUG.evaluate(30.0, 20.0), EPS);
        assertEquals(20.0, DamageVarianceFormula.MAGAZINE_RAMP.evaluate(20.0, 10.0), EPS);
        assertEquals(20.0, DamageVarianceFormula.FIRST_STRIKE.evaluate(20.0, 10.0), EPS);

        // null weapon
        assertEquals(25.0, DamageVarianceFormula.MAGAZINE_RAMP.evaluate(20.0, 30.0, null), EPS);
        assertEquals(25.0, DamageVarianceFormula.FIRST_STRIKE.evaluate(20.0, 30.0, null), EPS);

        // Single-round magazine weapon edge case
        Weapon singleRoundWeapon = new Weapon(
                "Single Shot",
                10, 30,
                DamageVarianceFormula.MAGAZINE_RAMP,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Set.of(),
                Ordinance.PROJECTILE
        ) {
            @Override
            public int getMagazineSize() {
                return 1;
            }
        };
        assertEquals(1, singleRoundWeapon.getMagazineSize());
        assertEquals(singleRoundWeapon.getMaxDamage(), DamageVarianceFormula.MAGAZINE_RAMP.evaluate(20, 40, singleRoundWeapon, 1), EPS);
        assertEquals(singleRoundWeapon.getMaxDamage(), DamageVarianceFormula.FIRST_STRIKE.evaluate(20, 40, singleRoundWeapon, 1), EPS);
    }
}

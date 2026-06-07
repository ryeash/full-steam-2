package com.fullsteam.games;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weapon balance simulation that computes normalized DPS, range, AOE, and composite
 * power scores for every weapon preset and validates they fall within acceptable ratios.
 *
 * Run this test to get a formatted balance report printed to stdout.
 * Tune the threshold constants below to tighten or relax balance constraints.
 */
class WeaponBalanceSimulationTest {

    // --- Balance thresholds (tune these as you iterate) ---
    // DPS varies widely by design — shotguns/flamers sacrifice range for high DPS.
    // Set this high enough to allow archetype diversity, low enough to catch outliers.
    private static final double MAX_DPS_TO_MEDIAN_RATIO = 5.0;
    // Composite power score accounts for DPS + range + AOE + effects, so it should
    // be tighter than raw DPS since it normalizes archetype trade-offs.
    private static final double MAX_POWER_SCORE_RATIO = 3.5;

    // --- Composite power score weights ---
    private static final double W_DPS = 0.40;
    private static final double W_RANGE = 0.25;
    private static final double W_AOE = 0.20;
    private static final double W_EFFECT = 0.15;

    private static Map<String, WeaponConfig> ALL_PRESETS;

    @BeforeAll
    static void loadAllPresets() {
        ALL_PRESETS = new LinkedHashMap<>();
        for (Field field : WeaponConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == WeaponConfig.class
                    && field.getName().endsWith("_PRESET")) {
                try {
                    field.setAccessible(true);
                    WeaponConfig config = (WeaponConfig) field.get(null);
                    ALL_PRESETS.put(config.getType(), config);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to read preset: " + field.getName(), e);
                }
            }
        }
    }

    // ====================================================================
    // Balance assertions
    // ====================================================================

    @Test
    @DisplayName("All weapon presets build without exceeding point budget")
    void allWeaponPresetsRespectPointBudget() {
        assertFalse(ALL_PRESETS.isEmpty(), "Should discover at least one weapon preset");
        for (var entry : ALL_PRESETS.entrySet()) {
            assertDoesNotThrow(() -> entry.getValue().buildWeapon(),
                    "Weapon '" + entry.getKey() + "' exceeds 100 point budget");
        }
    }

    @Test
    @DisplayName("No weapon's sustained DPS exceeds " + MAX_DPS_TO_MEDIAN_RATIO + "x the median")
    void allWeaponDPSWithinBalanceRange() {
        List<Double> dpsList = new ArrayList<>();
        Map<String, Double> dpsMap = new LinkedHashMap<>();

        for (var entry : ALL_PRESETS.entrySet()) {
            Weapon w = entry.getValue().buildWeapon();
            double dps = calculateSustainedDPS(w);
            dpsList.add(dps);
            dpsMap.put(entry.getKey(), dps);
        }

        dpsList.sort(Double::compareTo);
        double median = dpsList.get(dpsList.size() / 2);

        for (var entry : dpsMap.entrySet()) {
            double ratio = entry.getValue() / median;
            assertTrue(ratio <= MAX_DPS_TO_MEDIAN_RATIO,
                    String.format("'%s' DPS (%.1f) is %.1fx the median (%.1f) — exceeds %.1fx threshold",
                            entry.getKey(), entry.getValue(), ratio, median, MAX_DPS_TO_MEDIAN_RATIO));
        }
    }

    @Test
    @DisplayName("Composite power score max/min ratio stays below " + MAX_POWER_SCORE_RATIO + "x")
    void allWeaponPowerScoresWithinBalanceRange() {
        List<WeaponMetrics> metrics = computeAllMetrics();
        normalizeMetrics(metrics);

        DoubleSummaryStatistics stats = metrics.stream()
                .mapToDouble(m -> m.powerScore)
                .summaryStatistics();

        double ratio = stats.getMax() / Math.max(stats.getMin(), 0.001);
        assertTrue(ratio <= MAX_POWER_SCORE_RATIO,
                String.format("Power score ratio %.2fx exceeds %.1fx threshold (max=%.3f, min=%.3f)",
                        ratio, MAX_POWER_SCORE_RATIO, stats.getMax(), stats.getMin()));
    }

    @Test
    @DisplayName("Weapon balance report")
    void printWeaponBalanceReport() {
        List<WeaponMetrics> allMetrics = computeAllMetrics();

        List<WeaponMetrics> combatMetrics = new ArrayList<>(allMetrics.stream().toList());

        normalizeMetrics(combatMetrics);
        combatMetrics.sort(Comparator.comparingDouble((WeaponMetrics m) -> m.powerScore).reversed());

        System.out.println();
        System.out.println("=".repeat(105));
        System.out.println("  WEAPON BALANCE REPORT  (" + combatMetrics.size() + " combat weapons)");
        System.out.println("=".repeat(105));
        System.out.printf("%-28s | %7s | %7s | %7s | %7s | %7s | %6s | %s%n",
                "Weapon", "DPS", "nDPS", "nRange", "nAOE", "nFX", "Power", "Budget");
        System.out.println("-".repeat(105));

        for (WeaponMetrics m : combatMetrics) {
            System.out.printf("%-28s | %7.1f | %7.2f | %7.2f | %7.2f | %7.2f | %6.3f | %d/%d/%d = %d%n",
                    m.name,
                    m.rawDPS,
                    m.normalizedDPS,
                    m.normalizedRange,
                    m.normalizedAOE,
                    m.normalizedEffect,
                    m.powerScore,
                    m.attrPoints, m.effectPoints, m.ordPoints, m.totalPoints);
        }

        System.out.println("-".repeat(105));

        DoubleSummaryStatistics dpsStats = combatMetrics.stream().mapToDouble(m -> m.rawDPS).summaryStatistics();
        DoubleSummaryStatistics powerStats = combatMetrics.stream().mapToDouble(m -> m.powerScore).summaryStatistics();
        String highestDPS = combatMetrics.stream().max(Comparator.comparingDouble(m -> m.rawDPS)).map(m -> m.name).orElse("?");
        String lowestDPS = combatMetrics.stream().min(Comparator.comparingDouble(m -> m.rawDPS)).map(m -> m.name).orElse("?");

        System.out.printf("  Highest DPS: %s (%.1f)%n", highestDPS, dpsStats.getMax());
        System.out.printf("  Lowest  DPS: %s (%.1f)%n", lowestDPS, dpsStats.getMin());
        System.out.printf("  DPS ratio (max/min):   %.2fx%n", dpsStats.getMax() / Math.max(dpsStats.getMin(), 0.001));

        List<Double> sortedDPS = combatMetrics.stream().mapToDouble(m -> m.rawDPS).sorted().boxed().toList();
        double median = sortedDPS.get(sortedDPS.size() / 2);
        System.out.printf("  DPS median:  %.1f%n", median);
        System.out.printf("  DPS ratio (max/median): %.2fx  (threshold: %.1fx)%n",
                dpsStats.getMax() / median, MAX_DPS_TO_MEDIAN_RATIO);

        System.out.printf("  Power range: %.3f - %.3f (ratio: %.2fx, threshold: %.1fx)%n",
                powerStats.getMin(), powerStats.getMax(),
                powerStats.getMax() / Math.max(powerStats.getMin(), 0.001),
                MAX_POWER_SCORE_RATIO);
        System.out.println();

        printCategoryBreakdown(combatMetrics, "Beam weapons",
                m -> m.weapon.getOrdinance().isBeamType());
        printCategoryBreakdown(combatMetrics, "AOE weapons",
                m -> !m.weapon.getBulletEffects().isEmpty()
                        && m.weapon.getBulletEffects().stream().anyMatch(e -> e.getBaseRadius() > 0));
        printCategoryBreakdown(combatMetrics, "Kinetic weapons (no effects)",
                m -> m.weapon.getBulletEffects().isEmpty() && !m.weapon.getOrdinance().isBeamType());

        System.out.println("=".repeat(105));
        System.out.println();
    }

    // ====================================================================
    // Metric computation
    // ====================================================================

    private List<WeaponMetrics> computeAllMetrics() {
        List<WeaponMetrics> results = new ArrayList<>();
        for (var entry : ALL_PRESETS.entrySet()) {
            WeaponConfig config = entry.getValue();
            Weapon w = config.buildWeapon();

            WeaponMetrics m = new WeaponMetrics();
            m.name = entry.getKey();
            m.config = config;
            m.weapon = w;
            m.rawDPS = calculateSustainedDPS(w);
            m.rawRange = calculateEffectiveRange(w);
            m.rawAOE = calculateAOEScore(w);
            m.rawEffect = calculateEffectValueScore(w);

            int effectPts = w.getBulletEffects().stream().mapToInt(BulletEffect::getPointCost).sum();
            int ordPts = w.getOrdinance().getPointCost();
            m.attrPoints = w.getAttributePoints();
            m.effectPoints = effectPts;
            m.ordPoints = ordPts;
            m.totalPoints = m.attrPoints + effectPts + ordPts;

            results.add(m);
        }
        return results;
    }

    private void normalizeMetrics(List<WeaponMetrics> metrics) {
        double maxDPS = metrics.stream().mapToDouble(m -> m.rawDPS).max().orElse(1);
        double maxRange = metrics.stream().mapToDouble(m -> m.rawRange).max().orElse(1);
        double maxAOE = metrics.stream().mapToDouble(m -> m.rawAOE).max().orElse(1);
        double maxEffect = metrics.stream().mapToDouble(m -> m.rawEffect).max().orElse(1);

        for (WeaponMetrics m : metrics) {
            m.normalizedDPS = m.rawDPS / maxDPS;
            m.normalizedRange = m.rawRange / maxRange;
            m.normalizedAOE = maxAOE > 0 ? m.rawAOE / maxAOE : 0;
            m.normalizedEffect = maxEffect > 0 ? m.rawEffect / maxEffect : 0;
            m.powerScore = W_DPS * m.normalizedDPS
                    + W_RANGE * m.normalizedRange
                    + W_AOE * m.normalizedAOE
                    + W_EFFECT * m.normalizedEffect;
        }
    }

    // ====================================================================
    // Balance calculators (pure functions)
    // ====================================================================

    /**
     * Sustained DPS accounting for magazine emptying time and reload downtime.
     * For beam weapons, uses beam duration and damage interval instead.
     */
    static double calculateSustainedDPS(Weapon w) {
        if (w.getOrdinance().isBeamType()) {
            return calculateBeamDPS(w);
        }

        double damagePerShot = w.getDamagePerBullet() * w.getBulletsPerShot();
        double shotsPerSecond = w.getFireRate();

        if (shotsPerSecond <= 0) return 0;

        double magazineDuration = w.getMagazineSize() / shotsPerSecond;
        double magazineDamage = damagePerShot * w.getMagazineSize();
        double fullCycleDuration = magazineDuration + w.getReloadTime();

        return fullCycleDuration > 0 ? magazineDamage / fullCycleDuration : 0;
    }

    private static double calculateBeamDPS(Weapon w) {
        Ordinance ord = w.getOrdinance();
        double beamDuration = ord.getBeamDuration();

        if (ord.getDamageApplicationType() == DamageApplicationType.INSTANT) {
            double damagePerShot = w.getDamagePerBullet();
            double shotsPerSecond = w.getFireRate();
            double magazineDuration = w.getMagazineSize() / Math.max(shotsPerSecond, 0.01);
            double magazineDamage = damagePerShot * w.getMagazineSize();
            double fullCycle = magazineDuration + w.getReloadTime();
            return fullCycle > 0 ? magazineDamage / fullCycle : 0;
        }

        // DOT beams apply damage every damageInterval for beamDuration seconds
        double damageInterval = ord.getDamageInterval();
        if (damageInterval <= 0 || beamDuration <= 0) return 0;

        double ticksPerBeam = beamDuration / damageInterval;
        double damagePerBeam = w.getDamagePerBullet() * ticksPerBeam;
        double beamsPerMag = w.getMagazineSize();
        double magDuration = beamsPerMag * beamDuration;
        double magDamage = damagePerBeam * beamsPerMag;
        double fullCycle = magDuration + w.getReloadTime();

        return fullCycle > 0 ? magDamage / fullCycle : 0;
    }

    /**
     * Effective range: base range scaled by accuracy (spread reduces effective range)
     * and projectile speed (faster projectiles are more effective at range).
     */
    static double calculateEffectiveRange(Weapon w) {
        double baseRange = w.getRange();

        if (w.getOrdinance().isBeamType()) {
            // Beams are instant-hit, accuracy is always perfect
            return baseRange;
        }

        double accuracyFactor = Math.max(0.3, w.getAccuracy());
        double speedFactor = Math.min(1.5, w.getProjectileSpeed() / 500.0);

        return baseRange * accuracyFactor * speedFactor;
    }

    /**
     * AOE score: sum across all AOE bullet effects of (radius * effectDamage * duration).
     * Weapons without AOE effects score 0 — they compensate via direct DPS and range.
     */
    static double calculateAOEScore(Weapon w) {
        double totalScore = 0;

        for (BulletEffect effect : w.getBulletEffects()) {
            if (effect.getBaseRadius() <= 0) {
                continue;
            }

            double radius = effect.calculateRadius(w.getDamage(), w.getOrdinance());
            double effectDamage = effect.calculateDamage(w.getDamage());

            FieldEffectType fieldType = effectToFieldType(effect);
            double duration = fieldType != null ? fieldType.getDefaultDuration() : 1.0;

            // Area covered * damage * duration gives a rough "total AOE threat"
            double area = Math.PI * radius * radius;
            totalScore += area * effectDamage * duration;
        }

        return totalScore;
    }

    /**
     * Effect value score: proxy for how much special utility a weapon's bullet effects
     * and ordinance provide (piercing, homing, bouncy, etc). Uses point cost as a
     * rough value metric since the point system was designed for balance.
     */
    static double calculateEffectValueScore(Weapon w) {
        double score = 0;
        for (BulletEffect effect : w.getBulletEffects()) {
            score += effect.getPointCost();
        }
        if (w.getOrdinance().getPointCost() > 0) {
            score += w.getOrdinance().getPointCost() * 0.5;
        }
        return score;
    }

    private static FieldEffectType effectToFieldType(BulletEffect effect) {
        return switch (effect) {
            case EXPLOSIVE -> FieldEffectType.EXPLOSION;
            case INCENDIARY -> FieldEffectType.FIRE;
            case ELECTRIC -> FieldEffectType.ELECTRIC;
            case FREEZING -> FieldEffectType.FREEZE;
            case POISON -> FieldEffectType.POISON;
            case FRAGMENTING -> FieldEffectType.FRAGMENTATION;
            default -> null;
        };
    }

    private void printCategoryBreakdown(List<WeaponMetrics> metrics, String label,
                                        java.util.function.Predicate<WeaponMetrics> filter) {
        List<WeaponMetrics> category = metrics.stream().filter(filter).toList();
        if (category.isEmpty()) return;

        DoubleSummaryStatistics dps = category.stream().mapToDouble(m -> m.rawDPS).summaryStatistics();
        DoubleSummaryStatistics power = category.stream().mapToDouble(m -> m.powerScore).summaryStatistics();

        System.out.printf("  [%s] count=%d  DPS=%.1f-%.1f  Power=%.3f-%.3f%n",
                label, category.size(), dps.getMin(), dps.getMax(), power.getMin(), power.getMax());
    }

    private static class WeaponMetrics {
        String name;
        WeaponConfig config;
        Weapon weapon;
        double rawDPS, rawRange, rawAOE, rawEffect;
        double normalizedDPS, normalizedRange, normalizedAOE, normalizedEffect;
        double powerScore;
        int attrPoints, effectPoints, ordPoints, totalPoints;
    }
}

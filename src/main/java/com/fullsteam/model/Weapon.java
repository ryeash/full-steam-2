package com.fullsteam.model;

import lombok.Data;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class Weapon {
    private final String name;
    private final double minDamage;
    private final double maxDamage;
    private final DamageVarianceFormula varianceFormula;
    private final double damage; // Expected average damage
    private final double damagePerBullet;
    private final double fireRate;
    private final double range;
    private final double accuracy;
    private final int magazineSize;
    private final double reloadTime;
    private final double projectileSpeed;
    private final int bulletsPerShot;
    private final double linearDamping;
    private final double handling;
    private final double caliber;
    private final double knockback;
    private final double knockbackPerBullet;
    private final Set<BulletEffect> bulletEffects;
    private final Ordinance ordinance;

    private final int attributePoints;
    private int currentAmmo;

    public Weapon(String name,
                  int minDamage,
                  int maxDamage,
                  DamageVarianceFormula varianceFormula,
                  int fireRate,
                  int range,
                  int accuracy,
                  int magazineSize,
                  int reloadTime,
                  int projectileSpeed,
                  int bulletsPerShot,
                  int linearDamping,
                  int handling,
                  int caliber,
                  int knockback,
                  Set<BulletEffect> bulletEffects,
                  Ordinance ordinance
    ) {
        // Calculate total points including bullet effects, ordinance, and variance formula cost
        this.attributePoints = minDamage + maxDamage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping + handling + caliber + knockback;
        int effectPoints = bulletEffects.stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordinancePoints = ordinance.getPointCost();
        int varianceCost = varianceFormula != null ? varianceFormula.getPointCost() : 0;
        int totalPoints = attributePoints + effectPoints + ordinancePoints + varianceCost;

        if (totalPoints > 100) {
            throw new IllegalArgumentException("Total points cannot exceed 100. Current total: " + totalPoints +
                    " (Attributes: " + attributePoints + ", Effects: " + effectPoints + ", Ordinance: " + ordinancePoints + ", Variance: " + varianceCost + ")");
        }

        this.name = name;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;
        this.varianceFormula = varianceFormula != null ? varianceFormula : DamageVarianceFormula.UNIFORM;

        // Resolve all attributes together so cross-attribute couplings (e.g. fire
        // rate → accuracy, magazine → reload, min/max damage → handling) are applied in a
        // single pass before each stat is read out.
        Map<WeaponAttribute, Integer> allocated = new EnumMap<>(WeaponAttribute.class);
        allocated.put(WeaponAttribute.MIN_DAMAGE, minDamage);
        allocated.put(WeaponAttribute.MAX_DAMAGE, maxDamage);
        allocated.put(WeaponAttribute.FIRE_RATE, fireRate);
        allocated.put(WeaponAttribute.RANGE, range);
        allocated.put(WeaponAttribute.ACCURACY, accuracy);
        allocated.put(WeaponAttribute.MAGAZINE_SIZE, magazineSize);
        allocated.put(WeaponAttribute.RELOAD_TIME, reloadTime);
        allocated.put(WeaponAttribute.PROJECTILE_SPEED, projectileSpeed);
        allocated.put(WeaponAttribute.BULLETS_PER_SHOT, bulletsPerShot);
        allocated.put(WeaponAttribute.LINEAR_DAMPING, linearDamping);
        allocated.put(WeaponAttribute.HANDLING, handling);
        allocated.put(WeaponAttribute.CALIBER, caliber);
        allocated.put(WeaponAttribute.KNOCKBACK, knockback);
        Map<WeaponAttribute, Double> stats = WeaponAttribute.resolve(allocated);

        double rawMin = stats.get(WeaponAttribute.MIN_DAMAGE);
        double rawMax = stats.get(WeaponAttribute.MAX_DAMAGE);
        this.minDamage = Math.min(rawMin, rawMax);
        this.maxDamage = Math.max(rawMin, rawMax);
        this.damage = this.varianceFormula.expectedValue(this.minDamage, this.maxDamage);

        this.fireRate = stats.get(WeaponAttribute.FIRE_RATE);
        this.range = stats.get(WeaponAttribute.RANGE);
        this.accuracy = stats.get(WeaponAttribute.ACCURACY);
        this.magazineSize = (int) Math.round(stats.get(WeaponAttribute.MAGAZINE_SIZE));
        this.reloadTime = stats.get(WeaponAttribute.RELOAD_TIME);
        this.projectileSpeed = stats.get(WeaponAttribute.PROJECTILE_SPEED) * ordinance.getSpeedMultiplier();
        this.bulletsPerShot = (int) stats.get(WeaponAttribute.BULLETS_PER_SHOT).doubleValue();
        this.damagePerBullet = damagePerBullet(this.damage, this.bulletsPerShot);
        this.linearDamping = stats.get(WeaponAttribute.LINEAR_DAMPING);
        this.handling = stats.get(WeaponAttribute.HANDLING);
        this.caliber = stats.get(WeaponAttribute.CALIBER);
        this.knockback = stats.get(WeaponAttribute.KNOCKBACK);
        this.knockbackPerBullet = knockbackPerBullet(this.knockback, this.bulletsPerShot);
        this.currentAmmo = this.magazineSize;
    }

    // clone constructor
    public Weapon(Weapon other) {
        this.name = other.name;
        this.minDamage = other.minDamage;
        this.maxDamage = other.maxDamage;
        this.varianceFormula = other.varianceFormula;
        this.damage = other.damage;
        this.damagePerBullet = other.damagePerBullet;
        this.fireRate = other.fireRate;
        this.range = other.range;
        this.accuracy = other.accuracy;
        this.magazineSize = other.magazineSize;
        this.reloadTime = other.reloadTime;
        this.projectileSpeed = other.projectileSpeed;
        this.bulletsPerShot = other.bulletsPerShot;
        this.linearDamping = other.linearDamping;
        this.handling = other.handling;
        this.caliber = other.caliber;
        this.knockback = other.knockback;
        this.knockbackPerBullet = other.knockbackPerBullet;
        this.currentAmmo = other.currentAmmo;
        this.bulletEffects = other.bulletEffects;
        this.ordinance = other.ordinance;
        this.attributePoints = other.attributePoints;
    }

    /**
     * Roll damage for a single trigger pull using the weapon's variance formula.
     */
    public double rollDamage() {
        return varianceFormula.evaluate(minDamage, maxDamage);
    }

    /**
     * Roll per-bullet damage for a single trigger pull.
     */
    public double rollDamagePerBullet() {
        return damagePerBullet(rollDamage(), bulletsPerShot);
    }

    /**
     * Per-bullet damage when total damage is split across pellets. Multi-pellet
     * shots divide by {@code bulletsPerShot^0.7} (a soft cap, so more pellets
     * isn't linearly more DPS). Shared by the constructor and the resolve endpoint.
     */
    public static double damagePerBullet(double damage, int bulletsPerShot) {
        return bulletsPerShot > 1 ? damage / Math.pow(bulletsPerShot, 0.7) : damage;
    }

    /**
     * Per-pellet knockback for multi-shot weapons. The total knockback delivered
     * by one trigger pull is held near-constant at ~1.2× a single bullet (rather
     * than stacking n× as a scatter shot otherwise would), split evenly across the
     * pellets — so each pellet is {@code min(1, 1.2/n)} of the weapon's knockback:
     * a bit heavier than an even {@code 1/n} share. Single-shot weapons get the
     * full value.
     */
    public static double knockbackPerBullet(double knockback, int bulletsPerShot) {
        return knockback * Math.min(1.0, 1.2 / Math.max(1, bulletsPerShot));
    }

    public void reload() {
        currentAmmo = magazineSize;
    }

    public boolean needsReload() {
        return currentAmmo < magazineSize;
    }

    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }

    public String getDisplayName() {
        // If it's a preset weapon (not "Custom Weapon"), use the original name
        if (!"Custom Weapon".equals(name)) {
            return name;
        }

        // For custom weapons, generate a name based on ordinance and effects
        StringBuilder displayName = new StringBuilder();

        // Add ordinance name
        String ordinanceTag = switch (ordinance) {
            case PROJECTILE -> "Projectile";
            case LASER -> "Laser";
            case PLASMA_BEAM -> "Plasma Beam";
        };
        displayName.append(ordinanceTag);

        // Add primary bullet effects
        if (!bulletEffects.isEmpty()) {
            displayName.append(" ");
            bulletEffects.stream()
                    .min(Comparator.comparing(BulletEffect::ordinal))
                    .map(e -> "(" + e.toString().toLowerCase() + ")")
                    .ifPresent(displayName::append);
        }

        return displayName.toString();
    }
}

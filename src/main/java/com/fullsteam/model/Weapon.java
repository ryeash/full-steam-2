package com.fullsteam.model;

import lombok.Data;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class Weapon {
    private final String name;
    private final double damage;
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
    private final Set<BulletEffect> bulletEffects;
    private final Ordinance ordinance;

    private final int attributePoints;
    private int currentAmmo;

    public Weapon(String name,
                  int damage,
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
                  Set<BulletEffect> bulletEffects,
                  Ordinance ordinance
    ) {
        // Calculate total points including bullet effects and ordinance
        this.attributePoints = damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping + handling + caliber;
        int effectPoints = bulletEffects.stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordinancePoints = ordinance.getPointCost();
        int totalPoints = attributePoints + effectPoints + ordinancePoints;

        if (totalPoints > 100) {
            throw new IllegalArgumentException("Total points cannot exceed 100. Current total: " + totalPoints +
                    " (Attributes: " + attributePoints + ", Effects: " + effectPoints + ", Ordinance: " + ordinancePoints + ")");
        }

        this.name = name;
        this.bulletEffects = new HashSet<>(bulletEffects);
        this.ordinance = ordinance;

        // Resolve all attributes together so cross-attribute couplings (e.g. fire
        // rate → accuracy, magazine → reload, damage → handling) are applied in a
        // single pass before each stat is read out.
        Map<WeaponAttribute, Integer> allocated = new EnumMap<>(WeaponAttribute.class);
        allocated.put(WeaponAttribute.DAMAGE, damage);
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
        Map<WeaponAttribute, Double> stats = WeaponAttribute.resolve(allocated);

        this.damage = stats.get(WeaponAttribute.DAMAGE);
        this.fireRate = stats.get(WeaponAttribute.FIRE_RATE);
        this.range = stats.get(WeaponAttribute.RANGE);
        this.accuracy = stats.get(WeaponAttribute.ACCURACY);
        this.magazineSize = (int) Math.round(stats.get(WeaponAttribute.MAGAZINE_SIZE));
        this.reloadTime = stats.get(WeaponAttribute.RELOAD_TIME);
        // Apply ordinance speed multiplier to projectile speed
        this.projectileSpeed = stats.get(WeaponAttribute.PROJECTILE_SPEED) * ordinance.getSpeedMultiplier();
        this.bulletsPerShot = (int) stats.get(WeaponAttribute.BULLETS_PER_SHOT).doubleValue();
        this.damagePerBullet = damagePerBullet(this.damage, this.bulletsPerShot);
        this.linearDamping = stats.get(WeaponAttribute.LINEAR_DAMPING);
        this.handling = stats.get(WeaponAttribute.HANDLING);
        this.caliber = stats.get(WeaponAttribute.CALIBER);
        // Start fully loaded to the computed magazine capacity (was previously set
        // from the raw point count — a latent bug).
        this.currentAmmo = this.magazineSize;
    }

    // clone constructor
    public Weapon(Weapon other) {
        this.name = other.name;
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
        this.currentAmmo = other.currentAmmo; // Preserve actual current ammo, not magazine size
        this.bulletEffects = other.bulletEffects;
        this.ordinance = other.ordinance;
        this.attributePoints = other.attributePoints;
    }

    /**
     * Per-bullet damage when total damage is split across pellets. Multi-pellet
     * shots divide by {@code bulletsPerShot^0.7} (a soft cap, so more pellets
     * isn't linearly more DPS). Shared by the constructor and the resolve endpoint.
     */
    public static double damagePerBullet(double damage, int bulletsPerShot) {
        return bulletsPerShot > 1 ? damage / Math.pow(bulletsPerShot, 0.7) : damage;
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
}
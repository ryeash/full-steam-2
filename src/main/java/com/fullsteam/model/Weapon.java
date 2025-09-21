package com.fullsteam.model;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class Weapon {
    private final String name;
    private final double damage;
    private final double fireRate;
    private final double range;
    private final double accuracy;
    private final int magazineSize;
    private final double reloadTime;
    private final double projectileSpeed;
    private final int bulletsPerShot;
    private final double linearDamping;
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
                  Set<BulletEffect> bulletEffects,
                  Ordinance ordinance
    ) {
        // Calculate total points including bullet effects and ordinance
        this.attributePoints = damage + fireRate + range + accuracy + magazineSize + reloadTime + projectileSpeed + bulletsPerShot + linearDamping;
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

        // Calculate weapon stats based on point allocation
        // Each attribute has a base value + scaling based on points allocated
        this.damage = WeaponAttribute.DAMAGE.compute(damage);
        this.fireRate = WeaponAttribute.FIRE_RATE.compute(fireRate);
        this.range = WeaponAttribute.RANGE.compute(range);
        this.accuracy = WeaponAttribute.ACCURACY.compute(accuracy);
        this.magazineSize = (int) WeaponAttribute.MAGAZINE_SIZE.compute(magazineSize);
        this.reloadTime = WeaponAttribute.RELOAD_TIME.compute(reloadTime);
        // Apply ordinance speed multiplier to projectile speed
        this.projectileSpeed = WeaponAttribute.PROJECTILE_SPEED.compute(projectileSpeed) * ordinance.getSpeedMultiplier();
        this.bulletsPerShot = (int) WeaponAttribute.BULLETS_PER_SHOT.compute(bulletsPerShot);
        this.linearDamping = WeaponAttribute.LINEAR_DAMPING.compute(linearDamping);
        this.currentAmmo = magazineSize;

        // Debug logging
        System.out.println("DEBUG: Created weapon '" + name + "' with magazineSize: " + magazineSize + ", currentAmmo: " + currentAmmo);
    }

    public void fire() {
        if (currentAmmo > 0) {
            // Reduce ammo by the number of bullets fired per shot
            int bulletsToFire = Math.min(bulletsPerShot, currentAmmo);
            currentAmmo -= bulletsToFire;
        }
    }

    public void reload() {
        currentAmmo = magazineSize;
    }

    public boolean needsReload() {
        return currentAmmo < magazineSize;
    }

    public int getAmmo() {
        return currentAmmo;
    }

    public boolean hasBulletEffect(BulletEffect effect) {
        return bulletEffects.contains(effect);
    }

    public Set<BulletEffect> getBulletEffects() {
        return new HashSet<>(bulletEffects);
    }

    public int getTotalEffectPoints() {
        return bulletEffects.stream().mapToInt(BulletEffect::getPointCost).sum();
    }

    public int getTotalOrdinancePoints() {
        return ordinance.getPointCost();
    }
}
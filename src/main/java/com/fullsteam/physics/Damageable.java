package com.fullsteam.physics;

import org.dyn4j.geometry.Vector2;

/**
 * Capability interface for any game entity that can receive damage,
 * be affected by weapons, or trigger damage-related score and visual events.
 */
public interface Damageable {
    int getId();

    default int getOwnerTeam() {
        return 0;
    }

    Vector2 getPosition();

    boolean isActive();

    double getHealth();

    /**
     * Apply damage to this entity.
     *
     * @param damage          Raw damage amount
     * @param isArmorPiercing Whether the attack bypasses armor/mitigation
     * @return true if the entity was destroyed/killed by this damage
     */
    default boolean takeDamage(double damage, boolean isArmorPiercing) {
        return takeDamage(damage);
    }

    boolean takeDamage(double damage);
}

package com.fullsteam.physics;

/**
 * Capability interface for any hostile game entity that inflicts
 * melee damage on physical contact.
 */
public interface MeleeAttacker {
    int getId();

    boolean isActive();

    double getHealth();

    boolean canMeleeAttack();

    double getMeleeDamage();

    void recordMeleeAttack();
}

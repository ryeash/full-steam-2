package com.fullsteam.model;

import com.fullsteam.physics.Player;

/**
 * Represents a temporary modification to player or weapon attributes.
 * This is the foundation for status effects, buffs, debuffs, and temporary enhancements.
 */
public interface AttributeModification {
    // stackable?
    // source?
    // uniqueness key?

    String uniqueKey();

    default String renderHint() {
        return "";
    }

    default Weapon update(Weapon weapon) {
        return weapon;
    }

    default void update(Player player, double delta) {
    }

    default double modifyDamageReceived(double damage) {
        return damage;
    }

    default void revert(Player player) {
    }

    boolean isExpired();
}

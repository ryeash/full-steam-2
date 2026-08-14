package com.fullsteam.model;

import com.fullsteam.physics.Player;

import java.util.Objects;

/**
 * Represents a temporary modification to player or weapon attributes.
 * This is the foundation for status effects, buffs, debuffs, and temporary enhancements.
 */
public interface AttributeModification extends Comparable<AttributeModification> {

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

    @Override
    default int compareTo(AttributeModification o) {
        return Objects.compare(this.uniqueKey(), o.uniqueKey(), String.CASE_INSENSITIVE_ORDER);
    }
}

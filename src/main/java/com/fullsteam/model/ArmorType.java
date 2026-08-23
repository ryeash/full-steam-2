package com.fullsteam.model;

import lombok.Getter;

@Getter
public enum ArmorType {
    NONE("No Armor", 0.0, 1.15),
    LIGHT("Light Armor", 50.0, 0.85),
    HEAVY("Heavy Armor", 100.0, 0.70);

    private final String displayName;
    private final double maxArmor;
    private final double handlingModifier;

    ArmorType(String displayName, double maxArmor, double handlingModifier) {
        this.displayName = displayName;
        this.maxArmor = maxArmor;
        this.handlingModifier = handlingModifier;
    }
}

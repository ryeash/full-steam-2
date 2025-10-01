package com.fullsteam.model;

/**
 * Enumeration of utility weapons that provide tactical support rather than direct damage.
 * Each utility weapon has predefined, non-customizable behavior that complements primary weapons.
 */
public enum UtilityWeapon {
    // FieldEffect-based utilities (area effects)
    HEAL_ZONE("Heal Zone", "Creates a stationary healing area that restores ally health over time",
            FieldEffectType.HEAL_ZONE, null, 3.0, 60.0, 45.0, 15.0, UtilityCategory.SUPPORT),

    SMOKE_GRENADE("Smoke Grenade", "Deploys a vision-blocking smoke cloud",
            FieldEffectType.SMOKE_CLOUD, null, 2.0, 80.0, 60.0, 0.0, UtilityCategory.TACTICAL),

    GRAVITY_WELL("Gravity Well", "Creates a field that pulls enemies toward the center",
            FieldEffectType.GRAVITY_WELL, null, 4.0, 170.0, 150.0, 1.0, UtilityCategory.CROWD_CONTROL),

    SLOW_FIELD("Slow Field", "Generates an area that reduces enemy movement speed",
            FieldEffectType.SLOW_FIELD, null, 3.5, 75.0, 55.0, 10.0, UtilityCategory.CROWD_CONTROL),

    SHIELD_GENERATOR("Shield Generator", "Creates a protective barrier that absorbs damage",
            FieldEffectType.SHIELD_BARRIER, null, 7.0, 0.0, 75.0, 0.0, UtilityCategory.SUPPORT),

    SPEED_BOOST_PAD("Speed Boost Pad", "Creates a zone that increases ally movement speed",
            FieldEffectType.SPEED_BOOST, null, 3.0, 65.0, 50.0, 0.0, UtilityCategory.SUPPORT),

    // Entity-based utilities (complex behaviors)
    TURRET_CONSTRUCTOR("Turret Constructor", "Deploys an automated defense turret",
            null, "Turret", 6.0, 40.0, 25.0, 30.0, UtilityCategory.DEFENSIVE),

    NET_LAUNCHER("Net Launcher", "Fires nets that immobilize enemies temporarily",
            null, "NetProjectile", 1.5, 90.0, 30.0, 20.0, UtilityCategory.CROWD_CONTROL),

    WALL_BUILDER("Wall Builder", "Constructs temporary barriers for cover",
            null, "Barrier", 4.0, 30.0, 20.0, 0.0, UtilityCategory.DEFENSIVE),

    TELEPORTER("Teleporter", "Creates linked portals for quick movement",
            null, "TeleportPad", 8.0, 120.0, 35.0, 0.0, UtilityCategory.TACTICAL),

    MINE_LAYER("Mine Layer", "Places proximity mines that explode when enemies approach",
            null, "ProximityMine", 2.0, 50.0, 40.0, 40.0, UtilityCategory.DEFENSIVE),

    // Beam-based utilities (line-of-sight effects)
    DISRUPTOR_BEAM("Disruptor Beam", "Targeted beam that disables enemy weapons temporarily",
            null, null, 5.0, 70.0, 6.0, 0.0, UtilityCategory.CROWD_CONTROL, Ordinance.LASER);

    private final String displayName;
    private final String description;
    private final FieldEffectType fieldEffectType; // null if entity-based or beam-based
    private final String entityClassName; // null if field-effect-based or beam-based
    private final double cooldown; // seconds between uses
    private final double range; // targeting/placement range
    private final double radius; // effect area radius
    private final double damage; // damage for offensive utilities (0 for non-damaging)
    private final UtilityCategory category;
    private final Ordinance beamOrdinance; // null if not beam-based

    // Constructor for FieldEffect and Entity-based utilities
    UtilityWeapon(String displayName, String description, FieldEffectType fieldEffectType,
                  String entityClassName, double cooldown, double range, double radius, double damage,
                  UtilityCategory category) {
        this.displayName = displayName;
        this.description = description;
        this.fieldEffectType = fieldEffectType;
        this.entityClassName = entityClassName;
        this.cooldown = cooldown;
        this.range = range;
        this.radius = radius;
        this.damage = damage;
        this.category = category;
        this.beamOrdinance = null; // Not beam-based
    }

    // Constructor for Beam-based utilities
    UtilityWeapon(String displayName, String description, FieldEffectType fieldEffectType,
                  String entityClassName, double cooldown, double range, double radius, double damage,
                  UtilityCategory category, Ordinance beamOrdinance) {
        this.displayName = displayName;
        this.description = description;
        this.fieldEffectType = fieldEffectType;
        this.entityClassName = entityClassName;
        this.cooldown = cooldown;
        this.range = range;
        this.radius = radius;
        this.damage = damage;
        this.category = category;
        this.beamOrdinance = beamOrdinance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public FieldEffectType getFieldEffectType() {
        return fieldEffectType;
    }

    public String getEntityClassName() {
        return entityClassName;
    }

    public double getCooldown() {
        return cooldown;
    }

    public double getRange() {
        return range;
    }

    public double getRadius() {
        return radius;
    }

    public double getDamage() {
        return damage;
    }

    public UtilityCategory getCategory() {
        return category;
    }

    public Ordinance getBeamOrdinance() {
        return beamOrdinance;
    }

    /**
     * @return true if this utility uses FieldEffect system
     */
    public boolean isFieldEffectBased() {
        return fieldEffectType != null;
    }

    /**
     * @return true if this utility needs custom entity implementation
     */
    public boolean isEntityBased() {
        return entityClassName != null;
    }

    /**
     * @return true if this utility uses beam weapon system
     */
    public boolean isBeamBased() {
        return beamOrdinance != null;
    }

    /**
     * Categories for organizing utility weapons in the UI
     */
    public enum UtilityCategory {
        SUPPORT("Support", "Utilities that help allies"),
        DEFENSIVE("Defensive", "Utilities that provide protection"),
        TACTICAL("Tactical", "Utilities that provide information or positioning"),
        CROWD_CONTROL("Crowd Control", "Utilities that control enemy movement");

        private final String displayName;
        private final String description;

        UtilityCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}

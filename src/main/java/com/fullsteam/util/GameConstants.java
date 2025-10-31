package com.fullsteam.util;

/**
 * Centralized game constants to replace magic numbers throughout the codebase.
 */
public final class GameConstants {
    private GameConstants() {
    }

    public static final int MAX_GLOBAL_PLAYERS = Integer.parseInt(System.getProperty("max.global.players", "100"));
    public static final int MAX_GLOBAL_GAMES = Integer.parseInt(System.getProperty("max.global.game", "10"));
    public static final double WORLD_BOUNDARY_THICKNESS = Double.parseDouble(System.getProperty("world.boundary.thickness", "50.0"));
    public static final double SPAWN_CLEARANCE_RADIUS = Double.parseDouble(System.getProperty("spawn.clearanceRadius", "100.0"));
    public static final double SPAWN_INVINCIBILITY_DURATION = Double.parseDouble(System.getProperty("spawn.invincibilityDuration", "3.0"));
}


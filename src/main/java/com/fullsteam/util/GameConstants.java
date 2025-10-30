package com.fullsteam.util;

/**
 * Centralized game constants to replace magic numbers throughout the codebase.
 */
public final class GameConstants {
    private GameConstants() {
        // Prevent instantiation
    }

    // ========== PHYSICS CONSTANTS ==========
    public static final double GRAVITY = 9.8;
    public static final double TIME_STEP = 1.0 / 60.0; // 60 FPS

    // ========== PLAYER CONSTANTS ==========
    public static final double PLAYER_RADIUS = 15.0;
    public static final double PLAYER_MASS = 1.0;
    public static final double PLAYER_LINEAR_DAMPING = 5.0;
    public static final double PLAYER_ANGULAR_DAMPING = 10.0;
    public static final double PLAYER_RESTITUTION = 0.1;
    public static final double PLAYER_FRICTION = 0.3;
    
    public static final double PLAYER_BASE_SPEED = 200.0;
    public static final double PLAYER_BASE_HEALTH = 100.0;
    public static final double PLAYER_BASE_FIRE_RATE = 5.0; // shots per second
    public static final double PLAYER_BASE_PROJECTILE_SPEED = 400.0;
    public static final int PLAYER_BASE_PROJECTILE_DAMAGE = 10;
    public static final double PLAYER_BASE_PROJECTILE_LIFETIME = 3.0; // seconds

    // ========== PROJECTILE CONSTANTS ==========
    public static final double PROJECTILE_RADIUS = 3.0;
    public static final double PROJECTILE_MASS = 0.1;
    public static final double PROJECTILE_RESTITUTION = 0.8;
    public static final double PROJECTILE_FRICTION = 0.1;

    // ========== OBSTACLE CONSTANTS ==========
    public static final double OBSTACLE_RESTITUTION = 0.5;
    public static final double OBSTACLE_FRICTION = 0.5;

    // ========== POWERUP CONSTANTS ==========
    public static final double POWERUP_RADIUS = 12.0;
    public static final double POWERUP_SPAWN_INTERVAL = 10.0; // seconds
    public static final int POWERUP_MAX_COUNT = 5;

    // ========== WORKSHOP CONSTANTS ==========
    public static final double WORKSHOP_RADIUS = 30.0;
    public static final double WORKSHOP_CAPTURE_RADIUS = 50.0;
    public static final double WORKSHOP_CAPTURE_TIME = 5.0; // seconds to capture

    // ========== KOTH ZONE CONSTANTS ==========
    public static final double KOTH_ZONE_RADIUS = 100.0;
    public static final double KOTH_POINTS_PER_SECOND = 1.0;

    // ========== ODDBALL CONSTANTS ==========
    public static final double ODDBALL_RADIUS = 15.0;
    public static final double ODDBALL_POINTS_PER_SECOND = 1.0;

    // ========== RESPAWN CONSTANTS ==========
    public static final double RESPAWN_DELAY_INSTANT = 0.0;
    public static final double RESPAWN_DELAY_SHORT = 3.0;
    public static final double RESPAWN_DELAY_MEDIUM = 5.0;
    public static final double RESPAWN_DELAY_LONG = 10.0;

    // ========== GAME LIMITS ==========
    public static final int MAX_GLOBAL_PLAYERS = 100;
    public static final int MAX_GLOBAL_GAMES = 10;
    public static final int MAX_PLAYERS_PER_GAME = 32;
    public static final int MIN_TEAM_COUNT = 2;
    public static final int MAX_TEAM_COUNT = 8;

    // ========== WORLD CONSTANTS ==========
    public static final int DEFAULT_WORLD_WIDTH = 3000;
    public static final int DEFAULT_WORLD_HEIGHT = 3000;
    public static final int MIN_WORLD_SIZE = 1000;
    public static final int MAX_WORLD_SIZE = 10000;
    public static final double WORLD_BOUNDARY_THICKNESS = 50.0;
    
    // ========== SPAWN CONSTANTS ==========
    public static final double MIN_SPAWN_SPACING = 150.0;
    public static final double SPAWN_CLEARANCE_RADIUS = 100.0;
    public static final double SPAWN_OBSTACLE_BUFFER = 5.0;

    // ========== TIMING CONSTANTS ==========
    public static final long GAME_UPDATE_INTERVAL_MS = 16; // ~60 FPS
    public static final long STATE_BROADCAST_INTERVAL_MS = 50; // 20 updates per second
    public static final long CLEANUP_CHECK_INTERVAL_MS = 30_000; // 30 seconds
    public static final long AI_ONLY_GRACE_PERIOD_MS = 120_000; // 2 minutes
    public static final long VIP_CHECK_INTERVAL_MS = 5000; // 5 seconds
    public static final double SPAWN_INVINCIBILITY_DURATION = 3.0; // seconds of invincibility after spawn/respawn

    // ========== COLLISION CATEGORIES ==========
    public static final long CATEGORY_PLAYER = 0x0001;
    public static final long CATEGORY_PROJECTILE = 0x0002;
    public static final long CATEGORY_OBSTACLE = 0x0004;
    public static final long CATEGORY_POWERUP = 0x0008;
    public static final long CATEGORY_WORKSHOP = 0x0010;
    public static final long CATEGORY_ZONE = 0x0020;
    public static final long CATEGORY_ODDBALL = 0x0040;
    public static final long CATEGORY_BOUNDARY = 0x0080;

    // ========== AI CONSTANTS ==========
    public static final double AI_UPDATE_INTERVAL = 0.1; // seconds
    public static final double AI_VISION_RANGE = 500.0;
    public static final double AI_SHOOT_RANGE = 400.0;
    public static final double AI_FLEE_HEALTH_THRESHOLD = 30.0;
}


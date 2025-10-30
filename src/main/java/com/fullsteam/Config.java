package com.fullsteam;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Config {
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(8);

    // Player physics configuration
    public static final double PLAYER_SPEED = 600.0; // pixels per second (max speed)
    public static final double PLAYER_RADIUS = 20.0;
    public static final double PLAYER_ACCELERATION = 1100.0; // Force applied to reach target velocity
    public static final double PLAYER_BRAKING_FORCE = 700.0; // Force applied when stopping
    public static final double PLAYER_LINEAR_DAMPING = 1.2; // Physics damping for responsive movement
    public static final double PLAYER_ANGULAR_DAMPING = 1.0; // Rotation control damping

    // Net projectile configuration
    public static final double NET_PUSHBACK_FORCE = 2_000_000.0; // Force applied when net hits player

    // the max distance from a target that a projectile with HOMING will start tracking
    public static final double HOMING_DISTANCE = 300.0;

}



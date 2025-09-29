package com.fullsteam;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class Config {
    public static final AtomicInteger GAME_ID_COUNTER = new AtomicInteger(1);

    public static int nextId() {
        int i = GAME_ID_COUNTER.incrementAndGet();
        // zero is magic, don't use it
        if (i == 0) {
            return GAME_ID_COUNTER.incrementAndGet();
        }
        return i;
    }

    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(8);

    public static final int MAX_GLOBAL_PLAYERS = 100;
    public static final int MAX_GLOBAL_GAMES = 10;
    
    // Player physics configuration
    public static final double PLAYER_SPEED = 550.0; // pixels per second (max speed)
    public static final double PLAYER_RADIUS = 20.0;
    public static final double PLAYER_ACCELERATION = 900.0; // Force applied to reach target velocity
    public static final double PLAYER_BRAKING_FORCE = 700.0; // Force applied when stopping
    public static final double PLAYER_LINEAR_DAMPING = 0.85; // Physics damping for responsive movement
    public static final double PLAYER_ANGULAR_DAMPING = 1.0; // Rotation control damping
    
    // Game constants
    public static final double CAPTURE_RADIUS = 50.0;
    public static final double CAPTURE_TIME = 3.0; // seconds
}



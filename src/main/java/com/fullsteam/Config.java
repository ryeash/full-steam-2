package com.fullsteam;

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

    public static final int MAX_GLOBAL_PLAYERS = 100;
    public static final int MAX_PLAYERS_PER_GAME = 10;
    public static final double WORLD_WIDTH = 2000.0;
    public static final double WORLD_HEIGHT = 2000.0;
    public static final int TICK_RATE = 60; // 60 TPS
    public static final double PLAYER_SPEED = 9150.0; // pixels per second
    public static final double PLAYER_RADIUS = 20.0;
    public static final int STRATEGIC_LOCATIONS_COUNT = 5;
    public static final double CAPTURE_RADIUS = 50.0;
    public static final double CAPTURE_TIME = 3.0; // seconds
}



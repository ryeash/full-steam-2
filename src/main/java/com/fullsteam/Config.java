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
    public static final double PLAYER_SPEED = 150.0; // pixels per second
    public static final double PLAYER_RADIUS = 20.0;
    public static final double CAPTURE_RADIUS = 50.0;
    public static final double CAPTURE_TIME = 3.0; // seconds
}



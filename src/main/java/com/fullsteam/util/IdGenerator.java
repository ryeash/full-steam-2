package com.fullsteam.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized ID generation for all game entities.
 * Provides thread-safe, unique ID generation for entities, games, and players.
 */
public final class IdGenerator {
    private static final AtomicInteger ENTITY_ID = new AtomicInteger(1);
    private static final AtomicLong GAME_ID = new AtomicLong(1);
    private static final AtomicInteger PLAYER_ID = new AtomicInteger(1);

    private IdGenerator() {
        // Prevent instantiation
    }

    /**
     * Generate next entity ID (for game objects like projectiles, obstacles, etc.)
     * Skips 0 as it's used as a magic value in some places.
     *
     * @return Unique entity ID
     */
    public static int nextEntityId() {
        int id = ENTITY_ID.incrementAndGet();
        // Zero is magic, don't use it
        if (id == 0) {
            id = ENTITY_ID.incrementAndGet();
        }
        return id;
    }

    /**
     * Generate next game ID with "game_" prefix.
     *
     * @return Unique game ID string
     */
    public static String nextGameId() {
        return "" + GAME_ID.getAndIncrement();
    }

    /**
     * Generate next player ID.
     *
     * @return Unique player ID
     */
    public static int nextPlayerId() {
        return PLAYER_ID.getAndIncrement();
    }
}


package com.fullsteam.model;

/**
 * Defines how players respawn after death.
 */
public enum RespawnMode {
    /**
     * Respawn immediately after a delay (current default behavior).
     * Most forgiving mode.
     */
    INSTANT,

    /**
     * All dead players respawn together in waves at set intervals.
     * Encourages team coordination and grouped pushes.
     */
    WAVE,

    /**
     * No respawn until the current round ends.
     * Die once = spectate until next round. Tactical, high-stakes gameplay.
     */
    NEXT_ROUND,

    /**
     * Limited number of lives per player.
     * Each death counts against your life pool. Once out of lives, eliminated.
     * Set maxLives = 1 for one-life / battle-royale style play.
     */
    LIMITED
}


package com.fullsteam.model;

/**
 * Defines how players respawn after death.
 */
public enum RespawnMode {
    /**
     * Respawn after a fixed delay (see {@code respawnDelay}). The default,
     * most-forgiving mode.
     */
    DELAYED,

    /**
     * All dead players respawn together in waves at set intervals.
     * Encourages team coordination and grouped pushes.
     */
    WAVE,

    /**
     * Limited number of lives per player.
     * Each death counts against your life pool. Once out of lives, eliminated.
     * Set maxLives = 1 for one-life / battle-royale style play.
     */
    LIMITED,

    /**
     * Event-driven "last one standing" respawn. Players have unlimited lives but
     * do not respawn on a timer — the dead are held out until the arena collapses
     * to a single survivor (FFA) or a single team with anyone still alive (team
     * mode), at which point everyone respawns together for the next skirmish.
     * Produces a rapid series of duels-to-the-death with no round timer or rest
     * period — a quicker battle-royale feel. Pairs with SCORE_LIMIT or TIME_LIMIT
     * (never ELIMINATION, which would end the game the instant the field collapses).
     */
    LAST_STANDING
}


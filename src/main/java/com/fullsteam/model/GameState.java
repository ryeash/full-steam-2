package com.fullsteam.model;

/**
 * Represents the current state of the game. Games now run continuously from
 * start to finish (no round cycle), so gameplay is always {@link #PLAYING}
 * until the game ends via its victory condition.
 */
public enum GameState {
    /**
     * Pre-game countdown / prep phase before gameplay begins.
     */
    COUNTDOWN,

    /**
     * Normal gameplay is active.
     */
    PLAYING
}


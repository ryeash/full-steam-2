package com.fullsteam.model;

/**
 * Lifecycle state for a connected {@link PlayerSession}.
 *
 * <ul>
 *   <li>{@link #LOBBY} - Connected and holding a player slot, but has not yet
 *       chosen a loadout. The client is showing the customization modal. No
 *       {@link com.fullsteam.physics.Player} entity exists yet.</li>
 *   <li>{@link #PLAYING} - Active player with a {@code Player} entity in the
 *       physics world.</li>
 *   <li>{@link #SPECTATOR} - Watching only. Either joined as a spectator from
 *       the URL, or was soft-downgraded after the lobby timeout. No
 *       {@code Player} entity, does not consume a player slot.</li>
 * </ul>
 */
public enum PlayerSessionState {
    LOBBY,
    PLAYING,
    SPECTATOR
}

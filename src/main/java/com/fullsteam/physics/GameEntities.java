package com.fullsteam.physics;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import lombok.Getter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * GameEntities is a centralized container for all game entity collections.
 * This makes it easy to pass entity data between different systems like
 * CollisionProcessor, game state managers, and other components that need
 * access to multiple entity types.
 */
@Getter
public class GameEntities {

    protected final Map<Integer, PlayerSession> playerSessions = new ConcurrentSkipListMap<>();
    protected final Map<Integer, PlayerInput> playerInput = new ConcurrentSkipListMap<>();
    private final Map<Integer, Player> players = new ConcurrentSkipListMap<>();
    private final Map<Integer, Projectile> projectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, StrategicLocation> strategicLocations = new ConcurrentSkipListMap<>();

    public void addPlayerSession(PlayerSession playerSession) {
        playerSessions.put(playerSession.getPlayerId(), playerSession);
    }

    public PlayerSession getPlayerSession(Integer id) {
        return playerSessions.get(id);
    }

    public PlayerInput getPlayerInput(Integer id) {
        return playerInput.get(id);
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }

    public void removePlayer(int playerId) {
        players.remove(playerId);
    }

    public Player getPlayer(int playerId) {
        return players.get(playerId);
    }

    public Collection<Player> getAllPlayers() {
        return players.values();
    }

    // ===== Projectile Management =====

    public void addProjectile(Projectile projectile) {
        projectiles.put(projectile.getId(), projectile);
    }

    public Projectile getProjectile(int projectileId) {
        return projectiles.get(projectileId);
    }

    public Collection<Projectile> getAllProjectiles() {
        return projectiles.values();
    }

    // ===== Strategic Location Management =====

    public void addStrategicLocation(StrategicLocation location) {
        strategicLocations.put(location.getId(), location);
    }

    public StrategicLocation getStrategicLocation(int locationId) {
        return strategicLocations.get(locationId);
    }

    public Collection<StrategicLocation> getAllStrategicLocations() {
        return strategicLocations.values();
    }

    /**
     * Remove inactive entities across all collections.
     * This is useful for cleanup operations.
     */
    public void removeInactiveEntities() {
        // Remove inactive players
        players.entrySet().removeIf(entry -> !entry.getValue().isActive());

        // Remove inactive projectiles
        projectiles.entrySet().removeIf(entry -> !entry.getValue().isActive());

        // Remove inactive strategic locations (unlikely but for completeness)
        strategicLocations.entrySet().removeIf(entry -> !entry.getValue().isActive());
    }

    /**
     * Update all entities in all collections.
     *
     * @param deltaTime Time since last update in seconds
     */
    public void updateAll(double deltaTime) {
        // Update all players
        players.values().forEach(player -> player.update(deltaTime));

        // Update all projectiles
        projectiles.values().forEach(projectile -> projectile.update(deltaTime));

        // Update all strategic locations
        strategicLocations.values().forEach(location -> location.update(deltaTime));
    }

    public void clearPlayerEntities(Integer playerId) {
        if (playerId != null) {
            playerSessions.remove(playerId);
            playerInput.remove(playerId);
            players.remove(playerId);
            projectiles.values().removeIf(p -> p.getOwnerId() == playerId);
        }
    }
}

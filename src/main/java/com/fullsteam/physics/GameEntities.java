package com.fullsteam.physics;

import com.fullsteam.model.FieldEffect;
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
    protected final Map<Integer, PlayerInput> playerInputs = new ConcurrentSkipListMap<>();
    private final Map<Integer, Player> players = new ConcurrentSkipListMap<>();
    private final Map<Integer, Projectile> projectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, StrategicLocation> strategicLocations = new ConcurrentSkipListMap<>();
    private final Map<Integer, Obstacle> obstacles = new ConcurrentSkipListMap<>();
    private final Map<Integer, FieldEffect> fieldEffects = new ConcurrentSkipListMap<>();

    // Utility entity collections
    private final Map<Integer, Turret> turrets = new ConcurrentSkipListMap<>();
    private final Map<Integer, Barrier> barriers = new ConcurrentSkipListMap<>();
    private final Map<Integer, NetProjectile> netProjectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, ProximityMine> proximityMines = new ConcurrentSkipListMap<>();
    private final Map<Integer, TeleportPad> teleportPads = new ConcurrentSkipListMap<>();
    private final Map<Integer, Beam> beams = new ConcurrentSkipListMap<>();

    public void addPlayerSession(PlayerSession playerSession) {
        playerSessions.put(playerSession.getPlayerId(), playerSession);
    }

    public PlayerSession getPlayerSession(Integer id) {
        return playerSessions.get(id);
    }

    public PlayerInput getPlayerInput(Integer id) {
        return playerInputs.get(id);
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

    // ===== Obstacle Management =====

    public void addObstacle(Obstacle obstacle) {
        obstacles.put(obstacle.getId(), obstacle);
    }

    public Collection<Obstacle> getAllObstacles() {
        return obstacles.values();
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

        // Remove expired field effects
        fieldEffects.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Remove expired utility entities
        turrets.entrySet().removeIf(entry -> entry.getValue().isExpired());
        barriers.entrySet().removeIf(entry -> entry.getValue().isExpired());
        netProjectiles.entrySet().removeIf(entry -> entry.getValue().isExpired());
        proximityMines.entrySet().removeIf(entry -> entry.getValue().isExpired());
        teleportPads.entrySet().removeIf(entry -> entry.getValue().isExpired());
        beams.entrySet().removeIf(entry -> entry.getValue().isExpired());
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

        // Update all field effects
        fieldEffects.values().forEach(effect -> effect.update(deltaTime));
        
        // Update all utility entities
        turrets.values().forEach(turret -> turret.update(deltaTime));
        barriers.values().forEach(barrier -> barrier.update(deltaTime));
        netProjectiles.values().forEach(net -> net.update(deltaTime));
        proximityMines.values().forEach(mine -> mine.update(deltaTime));
        teleportPads.values().forEach(pad -> pad.update(deltaTime));
        beams.values().forEach(beam -> beam.update(deltaTime));
    }

    public PlayerSession removePlayerSession(int playerId) {
        return playerSessions.remove(playerId);
    }

    // Field Effect management methods
    public void addFieldEffect(FieldEffect fieldEffect) {
        fieldEffects.put(fieldEffect.getId(), fieldEffect);
    }

    public FieldEffect getFieldEffect(int id) {
        return fieldEffects.get(id);
    }

    public FieldEffect removeFieldEffect(int id) {
        return fieldEffects.remove(id);
    }

    public Collection<FieldEffect> getAllFieldEffects() {
        return fieldEffects.values();
    }

    // ===== Utility Entity Management =====

    // Turret management
    public void addTurret(Turret turret) {
        turrets.put(turret.getId(), turret);
    }

    public Turret getTurret(int turretId) {
        return turrets.get(turretId);
    }

    public Collection<Turret> getAllTurrets() {
        return turrets.values();
    }

    // Barrier management
    public void addBarrier(Barrier barrier) {
        barriers.put(barrier.getId(), barrier);
    }

    public Barrier getBarrier(int barrierId) {
        return barriers.get(barrierId);
    }

    public Collection<Barrier> getAllBarriers() {
        return barriers.values();
    }

    // Net projectile management
    public void addNetProjectile(NetProjectile netProjectile) {
        netProjectiles.put(netProjectile.getId(), netProjectile);
    }

    public NetProjectile getNetProjectile(int netId) {
        return netProjectiles.get(netId);
    }

    public Collection<NetProjectile> getAllNetProjectiles() {
        return netProjectiles.values();
    }

    // Proximity mine management
    public void addProximityMine(ProximityMine mine) {
        proximityMines.put(mine.getId(), mine);
    }

    public ProximityMine getProximityMine(int mineId) {
        return proximityMines.get(mineId);
    }

    public Collection<ProximityMine> getAllProximityMines() {
        return proximityMines.values();
    }

    // TeleportPad management
    public void addTeleportPad(TeleportPad teleportPad) {
        teleportPads.put(teleportPad.getId(), teleportPad);
    }

    public TeleportPad getTeleportPad(int teleportPadId) {
        return teleportPads.get(teleportPadId);
    }

    public Collection<TeleportPad> getAllTeleportPads() {
        return teleportPads.values();
    }

    public void addBeam(Beam beam) {
        beams.put(beam.getId(), beam);
    }

    public Beam getBeam(int beamId) {
        return beams.get(beamId);
    }

    public Collection<Beam> getAllBeams() {
        return beams.values();
    }

}

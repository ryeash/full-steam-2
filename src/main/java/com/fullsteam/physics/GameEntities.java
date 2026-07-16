package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;

import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GameEntities is a centralized container for all game entity collections.
 * This makes it easy to pass entity data between different systems like
 * CollisionProcessor, game state managers, and other components that need
 * access to multiple entity types.
 */
@Getter
public class GameEntities {

    private final GameConfig config;
    private final World<Body> world;
    private final Map<Integer, PlayerSession> playerSessions = new ConcurrentSkipListMap<>();
    private final Map<Integer, PlayerInput> playerInputs = new ConcurrentSkipListMap<>();
    private final Map<Integer, Player> players = new ConcurrentSkipListMap<>();
    private final Map<Integer, Projectile> projectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, Obstacle> obstacles = new ConcurrentSkipListMap<>();
    private final Map<Integer, FieldEffect> fieldEffects = new ConcurrentSkipListMap<>();
    private final Map<Integer, Turret> turrets = new ConcurrentSkipListMap<>();
    private final Map<Integer, DefenseLaser> defenseLasers = new ConcurrentSkipListMap<>();
    private final Map<Integer, NetProjectile> netProjectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, Beam> beams = new ConcurrentSkipListMap<>();
    private final Map<Integer, Flag> flags = new ConcurrentSkipListMap<>();
    private final Map<Integer, Oddball> oddballNpcs = new ConcurrentSkipListMap<>();
    private final Map<Integer, KothZone> kothZones = new ConcurrentSkipListMap<>();
    private final Map<Integer, Workshop> workshops = new ConcurrentSkipListMap<>();
    private final Map<Integer, PowerUp> powerUps = new ConcurrentSkipListMap<>();
    private final Map<Integer, Headquarters> headquarters = new ConcurrentSkipListMap<>();

    private final Map<Integer, Integer> teamVips = new ConcurrentSkipListMap<>();
    private final Deque<Runnable> postWorldUpdateHooks = new ConcurrentLinkedDeque<>();

    public GameEntities(GameConfig config, World<Body> world) {
        this.config = config;
        this.world = world;
    }

    public void add(GameEntity gameEntity) {
        if (gameEntity != null) {
            switch (gameEntity) {
                case Player p -> players.put(p.getId(), p);
                case Projectile projectile -> projectiles.put(projectile.getId(), projectile);
                case Workshop workshop -> workshops.put(workshop.getId(), workshop);
                case Obstacle obstacle -> obstacles.put(obstacle.getId(), obstacle);
                case FieldEffect fieldEffect -> fieldEffects.put(fieldEffect.getId(), fieldEffect);
                case Turret turret -> {
                    turrets.put(turret.getId(), turret);
                    List<Turret> forOwner = turrets.values()
                            .stream()
                            .filter(tp -> tp.getOwnerId() == turret.getOwnerId())
                            .sorted(Comparator.comparing(Turret::getCreated))
                            .collect(Collectors.toCollection(LinkedList::new));
                    while (forOwner.size() > 2) {
                        Turret remove = forOwner.removeFirst();
                        remove.setActive(false);
                    }
                }
                case DefenseLaser defenseLaser -> defenseLasers.put(defenseLaser.getId(), defenseLaser);
                case NetProjectile netProjectile -> netProjectiles.put(netProjectile.getId(), netProjectile);
                case Beam beam -> beams.put(beam.getId(), beam);
                case Flag flag -> flags.put(flag.getId(), flag);
                case Oddball npc -> oddballNpcs.put(npc.getId(), npc);
                case KothZone kothZone -> kothZones.put(kothZone.getId(), kothZone);
                case PowerUp powerUp -> powerUps.put(powerUp.getId(), powerUp);
                case Headquarters hq -> headquarters.put(hq.getId(), hq);
                case null -> {
                    // noop
                }
                default -> throw new IllegalArgumentException("Unknown GameEntity type: " + gameEntity);
            }
            addPostUpdateHook(() -> world.addBody(gameEntity.getBody()));
        }
    }

    public void addPlayerSession(PlayerSession playerSession) {
        playerSessions.put(playerSession.getPlayerId(), playerSession);
    }

    public PlayerSession getPlayerSession(Integer id) {
        return playerSessions.get(id);
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

    public Collection<Projectile> getAllProjectiles() {
        return projectiles.values();
    }

    public Collection<Obstacle> getAllObstacles() {
        return obstacles.values();
    }

    public void removeInactiveEntities() {
        Stream.of(obstacles, fieldEffects, turrets, netProjectiles, defenseLasers, beams, powerUps)
                .forEach(map ->
                        map.entrySet().removeIf(entry -> {
                            GameEntity o = entry.getValue();
                            if (o.isExpired()) {
                                world.removeBody(o.getBody());
                                return true;
                            }
                            return false;
                        }));
    }

    public void updateAll(double deltaTime) {
        Stream.of(players, projectiles, fieldEffects, turrets, defenseLasers, netProjectiles, beams, kothZones, workshops, powerUps, headquarters, oddballNpcs)
                .flatMap(m -> m.values().stream())
                .forEach(e -> e.update(deltaTime));
    }

    public PlayerSession removePlayerSession(int playerId) {
        return playerSessions.remove(playerId);
    }

    public Collection<FieldEffect> getAllFieldEffects() {
        return fieldEffects.values();
    }

    public Collection<Turret> getAllTurrets() {
        return turrets.values();
    }

    public Collection<DefenseLaser> getAllDefenseLasers() {
        return defenseLasers.values();
    }

    public Collection<NetProjectile> getAllNetProjectiles() {
        return netProjectiles.values();
    }

    public Collection<Beam> getAllBeams() {
        return beams.values();
    }

    public Flag getFlag(int flagId) {
        return flags.get(flagId);
    }

    public Collection<Flag> getAllFlags() {
        return flags.values();
    }

    public Collection<Oddball> getAllOddballNpcs() {
        return oddballNpcs.values();
    }

    public Oddball getOddballNpc(int id) {
        return oddballNpcs.get(id);
    }

    public KothZone getKothZone(int zoneId) {
        return kothZones.get(zoneId);
    }

    public Collection<KothZone> getAllKothZones() {
        return kothZones.values();
    }

    /**
     * Remove all entities in the given map from the physics world, then clear the map.
     * Prevents orphaned physics bodies from continuing to trigger collision callbacks
     * after entities are logically removed (e.g., between rounds).
     */
    public <T extends GameEntity> void clearEntitiesFromWorld(Map<Integer, T> entityMap) {
        for (T entity : entityMap.values()) {
            world.removeBody(entity.getBody());
        }
        entityMap.clear();
    }

    public void addPostUpdateHook(Runnable runnable) {
        postWorldUpdateHooks.offer(Objects.requireNonNull(runnable));
    }

    public void runPostUpdateHooks() {
        Runnable hook;
        while ((hook = postWorldUpdateHooks.poll()) != null) {
            hook.run();
        }
    }

    public Workshop getWorkshop(int workshopId) {
        return workshops.get(workshopId);
    }

    public Collection<Workshop> getAllWorkshops() {
        return workshops.values();
    }

    public void removeWorkshop(int workshopId) {
        workshops.remove(workshopId);
    }

    public PowerUp getPowerUp(int powerUpId) {
        return powerUps.get(powerUpId);
    }

    public Collection<PowerUp> getAllPowerUps() {
        return powerUps.values();
    }

    public void removePowerUp(int powerUpId) {
        powerUps.remove(powerUpId);
    }

    public Collection<PowerUp> getPowerUpsForWorkshop(int workshopId) {
        return powerUps.values().stream()
                .filter(powerUp -> powerUp.getWorkshopId() == workshopId)
                .collect(Collectors.toList());
    }

    public Headquarters getHeadquarters(int hqId) {
        return headquarters.get(hqId);
    }

    public Collection<Headquarters> getAllHeadquarters() {
        return headquarters.values();
    }

    public Headquarters getTeamHeadquarters(int teamNumber) {
        return headquarters.values().stream()
                .filter(hq -> hq.getTeamNumber() == teamNumber)
                .findFirst()
                .orElse(null);
    }

    public void setTeamVip(int teamNumber, int playerId) {
        teamVips.put(teamNumber, playerId);
    }

    public Integer getTeamVip(int teamNumber) {
        return teamVips.get(teamNumber);
    }

    public boolean isPlayerVip(int playerId) {
        Player player = getPlayer(playerId);
        if (player == null) {
            return false;
        }
        Integer vipId = teamVips.get(player.getTeam());
        return vipId != null && vipId == playerId;
    }
}

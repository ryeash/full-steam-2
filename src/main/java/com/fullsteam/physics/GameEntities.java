package com.fullsteam.physics;

import com.fullsteam.games.GameConfig;
import com.fullsteam.model.DamageHit;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * GameEntities is a centralized container for all game entity collections.
 * This makes it easy to pass entity data between different systems like
 * CollisionProcessor, game state managers, and other components that need
 * access to multiple entity types.
 */
@Getter
public class GameEntities {
    private static final Logger log = LoggerFactory.getLogger(GameEntities.class);

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
    private final Map<Integer, Flag> flags = new ConcurrentSkipListMap<>();
    private final Map<Integer, Oddball> oddballNpcs = new ConcurrentSkipListMap<>();
    private final Map<Integer, KothZone> kothZones = new ConcurrentSkipListMap<>();
    private final Map<Integer, Headquarters> headquarters = new ConcurrentSkipListMap<>();
    private final Map<Integer, Zombie> zombies = new ConcurrentSkipListMap<>();
    private final Map<Integer, Zombie> recentZombies = new ConcurrentSkipListMap<>();

    private final Set<DamageHit> pendingDamageHits = new ConcurrentSkipListSet<>();
    private final Map<Long, Double> dotHitAccumulator = new ConcurrentSkipListMap<>();

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
                case Flag flag -> flags.put(flag.getId(), flag);
                case Oddball npc -> oddballNpcs.put(npc.getId(), npc);
                case KothZone kothZone -> kothZones.put(kothZone.getId(), kothZone);
                case Headquarters hq -> headquarters.put(hq.getId(), hq);
                case Zombie zombie -> zombies.put(zombie.getId(), zombie);
                default -> throw new IllegalArgumentException("Unknown GameEntity type: " + gameEntity);
            }
            addPostUpdateHook(() -> {
                try {
                    world.addBody(gameEntity.getBody());
                } catch (Throwable t) {
                    log.error("error adding body of {}:{}", gameEntity.getClass().getSimpleName(), gameEntity, t);
                }
            });
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

    private <T extends GameEntity> void removeExpiredFromMap(Map<?, T> map) {
        map.entrySet().removeIf(entry -> {
            GameEntity o = entry.getValue();
            if (o.isExpired()) {
                if (o instanceof Zombie z) {
                    recentZombies.put(z.getId(), z);
                    if (recentZombies.size() > 100) {
                        recentZombies.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().getLastUpdateTime() > 30000L);
                    }
                }
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });
    }

    public void removeInactiveEntities() {
        removeExpiredFromMap(obstacles);
        removeExpiredFromMap(fieldEffects);
        removeExpiredFromMap(turrets);
        removeExpiredFromMap(netProjectiles);
        removeExpiredFromMap(defenseLasers);
        removeExpiredFromMap(zombies);
    }

    public void updateAll(double deltaTime) {
        for (Player p : players.values()) {
            p.update(deltaTime);
        }
        for (Projectile proj : projectiles.values()) {
            proj.update(deltaTime);
        }
        for (FieldEffect fe : fieldEffects.values()) {
            fe.update(deltaTime);
        }
        for (Turret t : turrets.values()) {
            t.update(deltaTime);
        }
        for (DefenseLaser l : defenseLasers.values()) {
            l.update(deltaTime);
        }
        for (NetProjectile n : netProjectiles.values()) {
            n.update(deltaTime);
        }
        for (KothZone z : kothZones.values()) {
            z.update(deltaTime);
        }
        for (Headquarters h : headquarters.values()) {
            h.update(deltaTime);
        }
        for (Oddball o : oddballNpcs.values()) {
            o.update(deltaTime);
        }
        for (Zombie z : zombies.values()) {
            z.update(deltaTime);
        }
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

    public Turret getTurret(int id) {
        return turrets.get(id);
    }

    public Collection<DefenseLaser> getAllDefenseLasers() {
        return defenseLasers.values();
    }

    public Collection<NetProjectile> getAllNetProjectiles() {
        return netProjectiles.values();
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

    public void addPostUpdateHook(Runnable runnable) {
        postWorldUpdateHooks.offer(Objects.requireNonNull(runnable));
    }

    public void runPostUpdateHooks() {
        Runnable hook;
        while ((hook = postWorldUpdateHooks.poll()) != null) {
            hook.run();
        }
    }

    public Headquarters getHeadquarters(int hqId) {
        return headquarters.get(hqId);
    }

    public Collection<Headquarters> getAllHeadquarters() {
        return headquarters.values();
    }

    public Collection<Zombie> getAllZombies() {
        return zombies.values();
    }

    public Zombie getZombie(int id) {
        Zombie z = zombies.get(id);
        return z != null ? z : recentZombies.get(id);
    }

    public Headquarters getTeamHeadquarters(int teamNumber) {
        for (Headquarters hq : headquarters.values()) {
            if (hq.getOwnerTeam() == teamNumber) {
                return hq;
            }
        }
        return null;
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

    /**
     * Record a discrete damage hit for client UI display (e.g. floating damage numbers).
     * Damage is rounded to nearest integer (or 1 if < 1).
     */
    public void recordDamageHit(double x, double y, double damage, int attackerId, int victimId, boolean isKill, boolean armorMitigated) {
        if (damage <= 0.0) {
            return;
        }
        long displayDamage = Math.max(1, Math.round(damage));
        double rX = Math.round(x * 10.0) / 10.0;
        double rY = Math.round(y * 10.0) / 10.0;
        pendingDamageHits.add(new DamageHit(rX, rY, (double) displayDamage, attackerId, victimId, isKill, armorMitigated));
    }

    public void recordDamageHit(double x, double y, double damage, int attackerId, int victimId, boolean isKill) {
        recordDamageHit(x, y, damage, attackerId, victimId, isKill, false);
    }

    /**
     * Accumulate continuous DOT damage and record a hit once accumulated damage is significant.
     */
    public void recordDotDamageHit(double x, double y, double frameDamage, int attackerId, int victimId, boolean isKill, boolean armorMitigated) {
        if (frameDamage <= 0) {
            return;
        }
        long key = (((long) attackerId) << 32) | (victimId & 0xFFFFFFFFL);
        double total = dotHitAccumulator.getOrDefault(key, 0.0) + frameDamage;
        if (total >= 4.0 || isKill) {
            recordDamageHit(x, y, total, attackerId, victimId, isKill, armorMitigated);
            dotHitAccumulator.put(key, 0.0);
        } else {
            dotHitAccumulator.put(key, total);
        }
    }

    public void recordDotDamageHit(double x, double y, double frameDamage, int attackerId, int victimId, boolean isKill) {
        recordDotDamageHit(x, y, frameDamage, attackerId, victimId, isKill, false);
    }

    /**
     * Retrieve and clear damage hits collected during the tick for state serialization.
     */
    public List<DamageHit> getAndClearDamageHits() {
        if (pendingDamageHits.isEmpty()) {
            return List.of();
        }
        List<DamageHit> copy = new ArrayList<>(pendingDamageHits);
        pendingDamageHits.clear();
        return copy;
    }
}

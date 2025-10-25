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
    protected final Map<Integer, PlayerSession> playerSessions = new ConcurrentSkipListMap<>();
    protected final Map<Integer, PlayerInput> playerInputs = new ConcurrentSkipListMap<>();
    private final Map<Integer, Player> players = new ConcurrentSkipListMap<>();
    private final Map<Integer, Projectile> projectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, Obstacle> obstacles = new ConcurrentSkipListMap<>();
    private final Map<Integer, FieldEffect> fieldEffects = new ConcurrentSkipListMap<>();

    // Utility entity collections
    private final Map<Integer, Turret> turrets = new ConcurrentSkipListMap<>();
    private final Map<Integer, DefenseLaser> defenseLasers = new ConcurrentSkipListMap<>();
    private final Map<Integer, NetProjectile> netProjectiles = new ConcurrentSkipListMap<>();
    private final Map<Integer, TeleportPad> teleportPads = new ConcurrentSkipListMap<>();
    private final Map<Integer, Beam> beams = new ConcurrentSkipListMap<>();

    // Capture the Flag entities
    private final Map<Integer, Flag> flags = new ConcurrentSkipListMap<>();

    // King of the Hill entities
    private final Map<Integer, KothZone> kothZones = new ConcurrentSkipListMap<>();

    // Workshop entities
    private final Map<Integer, Workshop> workshops = new ConcurrentSkipListMap<>();
    private final Map<Integer, PowerUp> powerUps = new ConcurrentSkipListMap<>();

    // Headquarters entities
    private final Map<Integer, Headquarters> headquarters = new ConcurrentSkipListMap<>();

    private final Deque<Runnable> postWorldUpdateHooks = new ConcurrentLinkedDeque<>();

    public GameEntities(GameConfig config, World<Body> world) {
        this.config = config;
        this.world = world;
    }

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

    public void addObstacle(Obstacle obstacle) {
        obstacles.put(obstacle.getId(), obstacle);

        if (obstacle.getOwnerId() > 0) {
            List<Obstacle> forOwner = obstacles.values()
                    .stream()
                    .filter(tp -> tp.getOwnerId() == obstacle.getOwnerId())
                    .sorted(Comparator.comparing(Obstacle::getCreated))
                    .collect(Collectors.toCollection(LinkedList::new));
            while (forOwner.size() > 4) {
                Obstacle remove = forOwner.remove(0);
                remove.setActive(false);
            }
        }
    }

    public Collection<Obstacle> getAllObstacles() {
        return obstacles.values();
    }

    /**
     * Remove inactive entities across all collections.
     * This is useful for cleanup operations.
     * <p>
     * Note: Players and Projectiles are handled separately in GameManager.update()
     * for more fine-grained control over their lifecycle and effects.
     */
    public void removeInactiveEntities() {
        // Remove inactive players (commented out - handled by GameManager for player lifecycle management)
//        players.entrySet().removeIf(entry -> {
//            if (!entry.getValue().isActive()) {
//                world.removeBody(entry.getValue().getBody());
//            }
//            return !entry.getValue().isActive();
//        });

        // Remove inactive projectiles (commented out - handled by GameManager for explosion effects)
//        projectiles.entrySet().removeIf(entry -> {
//            Projectile projectile = entry.getValue();
//            if (!projectile.isActive()) {
//                if (projectile.shouldTriggerEffectsOnDismissal()) {
//                    projectile.markAsExploded();
//                    getCollisionProcessor().getBulletEffectProcessor().processEffectHit(projectile, projectile.getPosition());
//                }
//                world.removeBody(projectile.getBody());
//                return true;
//            }
//            return false;
//        });

        // Remove expired obstacles
        obstacles.entrySet().removeIf(entry -> {
            Obstacle o = entry.getValue();
            if (o.isExpired()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        // Remove expired field effects
        fieldEffects.entrySet().removeIf(entry -> {
            FieldEffect o = entry.getValue();
            if (o.isExpired() || !o.isActive()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        // Remove expired utility entities
        turrets.entrySet().removeIf(entry -> {
            Turret o = entry.getValue();
            if (o.isExpired()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        netProjectiles.entrySet().removeIf(entry -> {
            NetProjectile o = entry.getValue();
            if (o.isExpired()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        teleportPads.entrySet().removeIf(entry -> {
            TeleportPad o = entry.getValue();
            if (o.isExpired()) {
                o.destroy();
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        defenseLasers.entrySet().removeIf(entry -> {
            DefenseLaser o = entry.getValue();
            if (!o.isActive()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        beams.entrySet().removeIf(entry -> {
            Beam o = entry.getValue();
            if (o.isExpired()) {
                world.removeBody(o.getBody());
                return true;
            }
            return false;
        });

        // Note: Flags are intentionally NOT cleaned up here as they persist for the entire game
        // Flags are only removed when a game ends or when explicitly removed via removeFlag()
    }

    /**
     * Update all entities in all collections.
     *
     * @param deltaTime Time since last update in seconds
     */
    public void updateAll(double deltaTime) {
        players.values().forEach(player -> player.update(deltaTime));
        projectiles.values().forEach(projectile -> projectile.update(deltaTime));
        fieldEffects.values().forEach(effect -> effect.update(deltaTime));
        turrets.values().forEach(turret -> turret.update(deltaTime));
        defenseLasers.values().forEach(defenseLaser -> defenseLaser.update(deltaTime));
        netProjectiles.values().forEach(net -> net.update(deltaTime));
        teleportPads.values().forEach(pad -> pad.update(deltaTime));
        beams.values().forEach(beam -> beam.update(deltaTime));
        kothZones.values().forEach(zone -> zone.update(deltaTime));
        workshops.values().forEach(workshop -> workshop.update(deltaTime));
        powerUps.values().forEach(powerUp -> powerUp.update(deltaTime));
        headquarters.values().forEach(hq -> hq.update(deltaTime));
    }

    public PlayerSession removePlayerSession(int playerId) {
        return playerSessions.remove(playerId);
    }

    public void addFieldEffect(FieldEffect fieldEffect) {
        fieldEffects.put(fieldEffect.getId(), fieldEffect);
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

        List<Turret> forOwner = turrets.values()
                .stream()
                .filter(tp -> tp.getOwnerId() == turret.getOwnerId())
                .sorted(Comparator.comparing(Turret::getCreated))
                .collect(Collectors.toCollection(LinkedList::new));
        while (forOwner.size() > 4) {
            Turret remove = forOwner.remove(0);
            remove.setActive(false);
        }
    }

    public Collection<Turret> getAllTurrets() {
        return turrets.values();
    }

    // Defense laser management
    public void addDefenseLaser(DefenseLaser defenseLaser) {
        defenseLasers.put(defenseLaser.getId(), defenseLaser);
    }

    public Collection<DefenseLaser> getAllDefenseLasers() {
        return defenseLasers.values();
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


    // TeleportPad management
    public void addTeleportPad(TeleportPad teleportPad) {
        teleportPads.put(teleportPad.getId(), teleportPad);

        List<TeleportPad> padsForOwner = teleportPads.values()
                .stream()
                .filter(tp -> tp.getOwnerId() == teleportPad.getOwnerId())
                .sorted(Comparator.comparing(TeleportPad::getCreated))
                .collect(Collectors.toCollection(LinkedList::new));
        while (padsForOwner.size() > 4) {
            TeleportPad remove = padsForOwner.remove(0);
            remove.destroy();
        }
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

    // ===== Flag Management =====

    public void addFlag(Flag flag) {
        flags.put(flag.getId(), flag);
    }

    public void removeFlag(int flagId) {
        Flag flag = flags.remove(flagId);
        if (flag != null && flag.getBody() != null) {
            world.removeBody(flag.getBody());
        }
    }

    public Flag getFlag(int flagId) {
        return flags.get(flagId);
    }

    public Collection<Flag> getAllFlags() {
        return flags.values();
    }

    public Map<Integer, Flag> getFlags() {
        return flags;
    }

    // ===== KOTH Zone Management =====

    public void addKothZone(KothZone zone) {
        kothZones.put(zone.getId(), zone);
    }

    public void removeKothZone(int zoneId) {
        KothZone zone = kothZones.remove(zoneId);
        if (zone != null && zone.getBody() != null) {
            world.removeBody(zone.getBody());
        }
    }

    public KothZone getKothZone(int zoneId) {
        return kothZones.get(zoneId);
    }

    public Collection<KothZone> getAllKothZones() {
        return kothZones.values();
    }

    public Map<Integer, KothZone> getKothZones() {
        return kothZones;
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

    // ===== Workshop Management =====

    public void addWorkshop(Workshop workshop) {
        workshops.put(workshop.getId(), workshop);
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

    // ===== Power-Up Management =====

    public void addPowerUp(PowerUp powerUp) {
        powerUps.put(powerUp.getId(), powerUp);
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

    /**
     * Get power-ups spawned by a specific workshop.
     */
    public Collection<PowerUp> getPowerUpsForWorkshop(int workshopId) {
        return powerUps.values().stream()
                .filter(powerUp -> powerUp.getWorkshopId() == workshopId)
                .collect(Collectors.toList());
    }

    // ===== Headquarters Management =====

    public void addHeadquarters(Headquarters hq) {
        headquarters.put(hq.getId(), hq);
    }

    public Headquarters getHeadquarters(int hqId) {
        return headquarters.get(hqId);
    }

    public Collection<Headquarters> getAllHeadquarters() {
        return headquarters.values();
    }

    public void removeHeadquarters(int hqId) {
        headquarters.remove(hqId);
    }

    /**
     * Get headquarters for a specific team.
     */
    public Headquarters getTeamHeadquarters(int teamNumber) {
        return headquarters.values().stream()
                .filter(hq -> hq.getTeamNumber() == teamNumber)
                .findFirst()
                .orElse(null);
    }

}

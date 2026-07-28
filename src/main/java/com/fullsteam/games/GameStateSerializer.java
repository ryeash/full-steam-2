package com.fullsteam.games;

import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Rules;
import com.fullsteam.model.Scoring;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.Turret;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Handles serialization of game state for client communication.
 * Responsible for creating game state snapshots and initial state data.
 */
public class GameStateSerializer {

    private static final DecimalFormat DOUBLE_SHORTFORM = new DecimalFormat("#.##");

    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final RuleSystem ruleSystem;
    private final TeamSpawnManager teamSpawnManager;
    private final TerrainGenerator terrainGenerator;

    public GameStateSerializer(GameConfig gameConfig, GameEntities gameEntities,
                               RuleSystem ruleSystem, TeamSpawnManager teamSpawnManager,
                               TerrainGenerator terrainGenerator) {
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.ruleSystem = ruleSystem;
        this.teamSpawnManager = teamSpawnManager;
        this.terrainGenerator = terrainGenerator;
    }

    /**
     * Create a complete game state snapshot for broadcasting to all clients.
     *
     * <p>Obstacles are intentionally excluded — they are static and already
     * delivered once via {@code initialState} / {@code lobbyInit} /
     * {@code spectatorInit}. Transient collections (projectiles, beams, …)
     * are omitted when empty; the client treats an absent field the same as
     * an empty array and cleans up stale sprites accordingly.
     *
     * @return Map containing all current game state data
     */
    public Map<String, Object> createGameState() {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("type", "gameState");
        gameState.put("timestamp", System.currentTimeMillis());

        // Include rule system state (rounds, victory, respawns)
        gameState.putAll(ruleSystem.getStateData());

        // Players are always present in a running game
        gameState.put("players", createPlayerStates());

        // Transient collections — omit when empty to trim the payload
        gameState.put("projectiles", createProjectileStates());
        gameState.put("fieldEffects", createFieldEffectStates());
        gameState.put("turrets", createTurretStates());
        gameState.put("nets", createNetStates());
        gameState.put("defenseLasers", createDefenseLaserStates());

        // Add optional game mode states
        if (gameConfig.getRules().hasKothZones()) {
            gameState.put("kothZones", createKothZoneStates());
        }

        if (gameConfig.getRules().hasHeadquarters()) {
            gameState.put("headquarters", createHeadquartersStates());
        }

        if (gameConfig.getRules().hasFlags()) {
            gameState.put("flags", createFlagStates());
            gameState.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        if (gameConfig.getRules().hasOddballNpcs()) {
            gameState.put("oddballNpcs", createOddballNpcStates());
        }

        return gameState;
    }

    /**
     * Create initial game state for a newly joined player.
     *
     * @param player The player joining the game
     * @return Map containing initial state data
     */
    public Map<String, Object> createInitialGameState(Player player) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "initialState");
        state.put("playerId", player.getId());
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());

        // Add team spawn area information
        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }

        // Add obstacles
        state.put("obstacles", createObstacleStates());

        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createFlagStates());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }
        return state;
    }

    /**
     * Create initial game state for a session in {@code LOBBY} (yet-to-spawn)
     * state. Includes the static world (terrain, obstacles, flag homes, KOTH
     * zones, headquarters) so the client can render a minimal
     * preview behind the customization modal, but no player or projectile data.
     *
     * @param lobbyTimeoutMs Milliseconds the server will wait before
     *                       soft-downgrading the session to spectator; the
     *                       client uses this for an informational countdown.
     * @param assignedName   the server-chosen random name for this session;
     *                       the client pre-selects it in the name dropdown.
     */
    public Map<String, Object> createLobbyInitialState(long lobbyTimeoutMs, String assignedName) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "lobbyInit");
        state.put("awaitingSpawn", true);
        state.put("lobbyTimeoutMs", lobbyTimeoutMs);
        state.put("assignedName", assignedName);
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());

        // Static world data
        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }
        state.put("obstacles", createObstacleStates());

        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createFlagStates());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }
        return state;
    }

    /**
     * Strip dynamic entity collections from a full game state for delivery to a
     * {@code LOBBY} session. The modal sits over a static map preview, so we
     * only keep map geometry and objective-style entities (KOTH
     * zones, headquarters, flag positions). All player/projectile/utility data
     * is replaced with empty lists.
     *
     * <p>Mirrors the {@link #createBlindedGameState} pattern.
     *
     * @param fullState the unfiltered state produced by {@link #createGameState()}.
     */
    public Map<String, Object> createLobbyGameState(Map<String, Object> fullState) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "gameState");
        state.put("timestamp", System.currentTimeMillis());
        state.put("awaitingSpawn", true);

        // Carry through rule/round/score metadata, plus any objective-style entity
        // collections that aren't part of the dynamic-actor stripping below.
        for (Map.Entry<String, Object> entry : fullState.entrySet()) {
            String key = entry.getKey();
            if (key.equals("type") || key.equals("timestamp")) {
                continue;
            }
            if (key.equals("players") || key.equals("projectiles")
                    || key.equals("fieldEffects") || key.equals("beams")
                    || key.equals("turrets") || key.equals("nets")
                    || key.equals("defenseLasers") || key.equals("powerUps")) {
                continue;
            }
            state.put(key, entry.getValue());
        }

        // Empty out the dynamic actor collections the client expects to iterate
        state.put("players", List.of());
        state.put("projectiles", List.of());
        state.put("fieldEffects", List.of());
        state.put("beams", List.of());
        state.put("turrets", List.of());
        state.put("nets", List.of());
        state.put("defenseLasers", List.of());
        state.put("powerUps", List.of());

        return state;
    }

    /**
     * Create initial game state for spectators (no player entity).
     *
     * @param spectatorCount current number of spectators in this game session
     */
    public Map<String, Object> createSpectatorInitialState(int spectatorCount) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "spectatorInit");
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());
        state.put("spectatorMode", true);


        // Add team spawn area information
        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }

        // Add obstacles
        state.put("obstacles", createObstacleStates());

        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createFlagStates());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        state.put("spectatorData", Map.of("spectatorCount", spectatorCount));

        return state;
    }

    private List<Map<String, Object>> createPlayerStates() {
        List<Map<String, Object>> playerStates = new ArrayList<>();
        for (Player player : gameEntities.getAllPlayers()) {
            playerStates.add(serializePlayerState(player, false));
        }
        return playerStates;
    }

    /**
     * Serialize a single player's state.
     *
     * @param player        the player to serialize
     * @param stripPowerUps when {@code true} the {@code activePowerUps} list is
     *                      forced to empty (used for the smoke-blinded view so
     *                      the client can't infer information from power-up hints)
     */
    private Map<String, Object> serializePlayerState(Player player, boolean stripPowerUps) {
        Vector2 pos = player.getPosition();
        Map<String, Object> s = new HashMap<>();
        s.put("id", player.getId());
        s.put("name", player.getPlayerName());
        s.put("team", player.getTeam());
        s.put("x", pos.x);
        s.put("y", pos.y);
        s.put("rotation", player.getRotation());
        s.put("health", player.healthPercent());
        s.put("active", player.isActive());
        s.put("ammo", player.getCurrentWeapon().getCurrentAmmo());
        s.put("maxAmmo", player.getCurrentWeapon().getMagazineSize());
        s.put("reloading", player.isReloading());
        s.put("utilityCooldownPercent", player.getUtilityCooldownProgress());
        s.put("weaponRange", player.getCurrentWeapon().getRange());

        Scoring scoring = player.getScoring();
        Rules rules = gameConfig.getRules();
        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("kills", scoring.getKills());
        scoreData.put("deaths", scoring.getDeaths());
        scoreData.put("captures", scoring.getFlagCaptures());
        scoreData.put("koth", scoring.getKingOfTheHillPoints());
        scoreData.put("oddball", scoring.getOddball());
        scoreData.put("hqDamage", scoring.getHeadquarterDamage());
        scoreData.put("hqDestroyed", scoring.getHeadquartersDestroyed());
        scoreData.put("vipKills", scoring.getVipKills());
        scoreData.put("bonus", scoring.bonusPoints(rules));
        scoreData.put("total", scoring.total(rules));
        s.put("score", scoreData);

        // Top-level kills/deaths are read directly by scoreboard templates.
        s.put("kills", scoring.getKills());
        s.put("deaths", scoring.getDeaths());
        s.put("respawnTime", Math.max(0, ((double) player.getRespawnTime() - System.currentTimeMillis()) / 1000));
        // In LAST_STANDING the dead are parked (no timer) until the arena collapses to one
        // survivor, so the client should show a "waiting" message rather than a bogus countdown.
        boolean respawnWaiting = gameConfig.getRules().usesLastStanding()
                && !player.isActive()
                && !player.isEliminated()
                && player.getRespawnTime() > System.currentTimeMillis();
        s.put("respawnWaiting", respawnWaiting);
        s.put("livesRemaining", player.getLivesRemaining());
        s.put("eliminated", player.isEliminated());

        if (gameConfig.getRules().hasVip()) {
            s.put("isVip", StatusEffectManager.isVip(player));
        }

        List<String> activePowerUps = new ArrayList<>();
        if (!stripPowerUps) {
            for (AttributeModification mod : player.getAttributeModifications()) {
                String hint = mod.renderHint();
                if (hint != null && !hint.isEmpty()) {
                    activePowerUps.add(hint);
                }
            }
        }
        s.put("activePowerUps", activePowerUps);
        return s;
    }

    private List<Map<String, Object>> createProjectileStates() {
        List<Map<String, Object>> projectileStates = new ArrayList<>();
        for (Projectile projectile : gameEntities.getAllProjectiles()) {
            Vector2 pos = projectile.getPosition();
            Vector2 vel = projectile.getBody().getLinearVelocity();
            Map<String, Object> projState = new HashMap<>();
            projState.put("id", projectile.getId());
            projState.put("x", pos.x);
            projState.put("y", pos.y);
            projState.put("vx", vel.x);
            projState.put("vy", vel.y);
            projState.put("caliber", projectile.getCaliber());
            projState.put("bulletEffects", projectile.getBulletEffects().stream()
                    .map(Enum::name).collect(Collectors.toList()));
            projectileStates.add(projState);
        }
        return projectileStates;
    }

    private List<Map<String, Object>> createObstacleStates() {
        List<Map<String, Object>> obstacleStates = new ArrayList<>();
        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            Vector2 pos = obstacle.getPosition();
            Map<String, Object> obsState = new HashMap<>();
            obsState.put("id", obstacle.getId());
            obsState.put("x", pos.x);
            obsState.put("y", pos.y);
            obsState.put("type", obstacle.getType().name());
            obsState.put("rotation", obstacle.getBody().getTransform().getRotation().toRadians());
            obsState.put("shapes", verticesShorthand(obstacle.getBody()));
            obstacleStates.add(obsState);
        }
        return obstacleStates;
    }

    private String verticesShorthand(Body body) {
        if (body.getFixtureCount() == 0) {
            return "";
        }
        StringJoiner outer = new StringJoiner(";");
        for (int i = 0; i < body.getFixtureCount(); i++) {
            Convex convex = body.getFixture(i).getShape();
            StringJoiner joiner = new StringJoiner("/");
            if (convex instanceof Polygon polygon) {
                Vector2[] polyVertices = polygon.getVertices();
                for (Vector2 vertex : polyVertices) {
                    joiner.add("(" + DOUBLE_SHORTFORM.format(vertex.x) +
                            "," + DOUBLE_SHORTFORM.format(vertex.y) + ")");
                }
            } else if (convex instanceof Circle circle) {
                double radius = circle.getRadius();
                Vector2 center = circle.getCenter();
                joiner.add("(" + DOUBLE_SHORTFORM.format(center.x) +
                        "," + DOUBLE_SHORTFORM.format(center.y) +
                        "," + DOUBLE_SHORTFORM.format(radius) + ")");
            }
            outer.add(joiner.toString());
        }
        return outer.toString();
    }

    private List<Map<String, Object>> createFieldEffectStates() {
        List<Map<String, Object>> fieldEffectStates = new LinkedList<>();
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            if (effect.isArmed()) {
                Vector2 pos = effect.getPosition();
                Map<String, Object> effectState = new HashMap<>();
                effectState.put("id", effect.getId());
                effectState.put("type", effect.getType().name());
                effectState.put("ownerTeam", effect.getOwnerTeam());
                effectState.put("x", pos.x);
                effectState.put("y", pos.y);
                effectState.put("rotation", effect.getBody().getTransform().getRotation().toRadians());
                effectState.put("radius", effect.getRadius());
                effectState.put("progress", effect.getProgress());
                effectState.put("active", effect.isActive());
                effectState.put("isArmed", effect.isArmed());
                effectState.put("shapes", verticesShorthand(effect.getBody()));
                fieldEffectStates.add(effectState);
            }
        }
        return fieldEffectStates;
    }

    private List<Map<String, Object>> createTurretStates() {
        List<Map<String, Object>> turretStates = new ArrayList<>();
        for (Turret turret : gameEntities.getAllTurrets()) {
            Vector2 pos = turret.getPosition();
            Map<String, Object> turretState = new HashMap<>();
            turretState.put("id", turret.getId());
            turretState.put("type", "TURRET");
            turretState.put("x", pos.x);
            turretState.put("y", pos.y);
            turretState.put("rotation", turret.getBody().getTransform().getRotation().toRadians());
            turretState.put("health", turret.healthPercent());
            turretState.put("active", turret.isActive());
            turretState.put("ownerTeam", turret.getOwnerTeam());
            turretStates.add(turretState);
        }
        return turretStates;
    }

    private List<Map<String, Object>> createNetStates() {
        List<Map<String, Object>> netStates = new ArrayList<>();
        for (NetProjectile net : gameEntities.getAllNetProjectiles()) {
            Vector2 pos = net.getPosition();
            Map<String, Object> netState = new HashMap<>();
            netState.put("id", net.getId());
            netState.put("type", "NET");
            netState.put("x", pos.x);
            netState.put("y", pos.y);
            netState.put("rotation", net.getBody().getTransform().getRotation().toRadians());
            netState.put("active", net.isActive());
            netStates.add(netState);
        }
        return netStates;
    }

    private List<Map<String, Object>> createDefenseLaserStates() {
        List<Map<String, Object>> defenseLaserStates = new ArrayList<>();
        for (DefenseLaser defenseLaser : gameEntities.getAllDefenseLasers()) {
            Vector2 pos = defenseLaser.getPosition();
            Map<String, Object> laserState = new HashMap<>();
            laserState.put("id", defenseLaser.getId());
            laserState.put("type", "DEFENSE_LASER");
            laserState.put("x", pos.x);
            laserState.put("y", pos.y);
            laserState.put("rotation", defenseLaser.getCurrentRotation());
            laserState.put("active", defenseLaser.isActive());
            laserState.put("ownerTeam", defenseLaser.getOwnerTeam());
            defenseLaserStates.add(laserState);
        }
        return defenseLaserStates;
    }

    /**
     * Create a restricted game state for a player whose vision is obscured by smoke.
     * Only includes the player's own data and smoke field effects; all other entity
     * data is stripped to enforce server-authoritative blindness.
     *
     * @param blindedPlayer The player inside smoke
     * @param fullState     The full game state (reused for rule/round data)
     */
    public Map<String, Object> createBlindedGameState(Player blindedPlayer, Map<String, Object> fullState) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "gameState");
        state.put("timestamp", System.currentTimeMillis());
        state.put("visionObscured", true);

        // Include rule system state (rounds, scores, victory) from full state
        for (Map.Entry<String, Object> entry : fullState.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("type") && !key.equals("timestamp")
                    && !key.equals("players") && !key.equals("projectiles")
                    && !key.equals("fieldEffects") && !key.equals("beams")
                    && !key.equals("turrets") && !key.equals("nets")
                    && !key.equals("defenseLasers") && !key.equals("powerUps")) {
                state.put(key, entry.getValue());
            }
        }

        // Only include the blinded player's own data; strip power-up hints so
        // the client can't infer information about nearby enemies through smoke.
        Map<String, Object> playerState = serializePlayerState(blindedPlayer, true);
        state.put("players", List.of(playerState));

        // Only include SMOKE field effects (so the client can render the smoke cloud)
        List<Map<String, Object>> smokeEffects = new ArrayList<>();
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            if (effect.getType() == FieldEffectType.SMOKE) {
                Vector2 ePos = effect.getPosition();
                Map<String, Object> effectState = new HashMap<>();
                effectState.put("id", effect.getId());
                effectState.put("type", effect.getType().name());
                effectState.put("x", ePos.x);
                effectState.put("y", ePos.y);
                effectState.put("radius", effect.getRadius());
                effectState.put("progress", effect.getProgress());
                effectState.put("active", effect.isActive());
                smokeEffects.add(effectState);
            }
        }
        state.put("fieldEffects", smokeEffects);
        // Omit all other transient collections — absent field == empty array on the client.
        // Obstacles are excluded entirely: they're static and already on the client
        // from the initial-state payload.
        return state;
    }

    private List<Map<String, Object>> createKothZoneStates() {
        List<Map<String, Object>> zoneStates = new ArrayList<>();
        for (KothZone zone : gameEntities.getAllKothZones()) {
            Vector2 pos = zone.getPosition();
            Map<String, Object> zoneState = new HashMap<>();
            zoneState.put("id", zone.getId());
            zoneState.put("zoneNumber", zone.getZoneNumber());
            zoneState.put("x", pos.x);
            zoneState.put("y", pos.y);
            zoneState.put("radius", zone.getRadius());
            zoneState.put("controllingTeam", zone.getControllingTeam());
            zoneState.put("state", zone.getState().name());
            zoneState.put("playerCount", zone.getTotalPlayerCount());
            zoneStates.add(zoneState);
        }
        return zoneStates;
    }

    private List<Map<String, Object>> createHeadquartersStates() {
        List<Map<String, Object>> hqStates = new ArrayList<>();
        for (Headquarters hq : gameEntities.getAllHeadquarters()) {
            Vector2 pos = hq.getPosition();
            Map<String, Object> hqState = new HashMap<>();
            hqState.put("id", hq.getId());
            hqState.put("type", "HEADQUARTERS");
            hqState.put("team", hq.getOwnerTeam());
            hqState.put("x", pos.x);
            hqState.put("y", pos.y);
            hqState.put("health", hq.healthPercent());
            hqState.put("active", hq.isActive());
            hqState.put("shapes", verticesShorthand(hq.getBody()));
            hqStates.add(hqState);
        }
        return hqStates;
    }

    private List<Map<String, Object>> createOddballNpcStates() {
        List<Map<String, Object>> states = new ArrayList<>();
        for (Oddball npc : gameEntities.getAllOddballNpcs()) {
            if (!npc.isActive()) {
                continue;
            }
            org.dyn4j.geometry.Vector2 pos = npc.getPosition();
            Map<String, Object> state = new HashMap<>();
            state.put("id", npc.getId());
            state.put("x", pos.x);
            state.put("y", pos.y);
            state.put("personality", npc.getPersonality().name());
            state.put("radius", npc.getRadius());
            states.add(state);
        }
        return states;
    }

    private List<Map<String, Object>> createFlagStates() {
        List<Map<String, Object>> flagStates = new ArrayList<>();
        for (Flag flag : gameEntities.getAllFlags()) {
            Vector2 pos = flag.getPosition();
            Map<String, Object> flagState = new HashMap<>();
            flagState.put("id", flag.getId());
            flagState.put("x", pos.x);
            flagState.put("y", pos.y);
            flagState.put("ownerTeam", flag.getOwnerTeam());
            flagState.put("state", flag.getState().name());
            flagStates.add(flagState);
        }
        return flagStates;
    }
}

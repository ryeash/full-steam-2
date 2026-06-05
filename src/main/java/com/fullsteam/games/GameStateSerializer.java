package com.fullsteam.games;

import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.PowerUp;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Workshop;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Handles serialization of game state for client communication.
 * Responsible for creating game state snapshots and initial state data.
 */
public class GameStateSerializer {

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
     * @return Map containing all current game state data
     */
    public Map<String, Object> createGameState() {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("type", "gameState");
        gameState.put("timestamp", System.currentTimeMillis());

        // Include rule system state (rounds, victory, respawns)
        gameState.putAll(ruleSystem.getStateData());

        // Add all entity states
        gameState.put("players", createPlayerStates());
        gameState.put("projectiles", createProjectileStates());
        gameState.put("obstacles", createObstacleStates());
        gameState.put("fieldEffects", createFieldEffectStates());
        gameState.put("turrets", createTurretStates());
        gameState.put("nets", createNetStates());
        gameState.put("mines", createMineStates());
        gameState.put("defenseLasers", createDefenseLaserStates());
        gameState.put("beams", createBeamStates());
        gameState.put("powerUps", createPowerUpStates());

        // Add optional game mode states
        if (gameConfig.getRules().hasKothZones()) {
            gameState.put("kothZones", createKothZoneStates());
        }

        if (gameConfig.getRules().hasWorkshops()) {
            gameState.put("workshops", createWorkshopStates());
        }

        if (gameConfig.getRules().hasHeadquarters()) {
            gameState.put("headquarters", createHeadquartersStates());
        }

        if (gameConfig.getRules().hasFlags()) {
            gameState.put("flags", createFlagStates());
            gameState.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        // Include oddball states if oddball mode is enabled (even without CTF flags)
        if (gameConfig.getRules().hasOddball()) {
            List<Map<String, Object>> oddballStates = createOddballStates();
            if (!oddballStates.isEmpty()) {
                gameState.put("flags", oddballStates);
                gameState.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
            }
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

        // Add procedural terrain data
        state.put("terrain", terrainGenerator.getTerrainData());

        // Add obstacles
        state.put("obstacles", createObstacleStates());

        // Add flag information if flags are enabled
        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createInitialFlagStates());
            state.put("flagsPerTeam", gameConfig.getRules().getFlagsPerTeam());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        // Add oddball information if oddball mode is enabled
        if (gameConfig.getRules().hasOddball()) {
            Map<String, Object> oddballData = createInitialOddballState();
            if (oddballData != null) {
                state.put("oddball", oddballData);
            }
        }

        // Add VIP mode information if enabled
        if (gameConfig.getRules().hasVip()) {
            state.put("vipMode", true);
        }

        return state;
    }

    /**
     * Create initial game state for a session in {@code LOBBY} (yet-to-spawn)
     * state. Includes the static world (terrain, obstacles, flag homes, KOTH
     * zones, headquarters, workshops) so the client can render a minimal
     * preview behind the customization modal, but no player or projectile data.
     *
     * @param lobbyTimeoutMs Milliseconds the server will wait before
     *                       soft-downgrading the session to spectator; the
     *                       client uses this for an informational countdown.
     */
    public Map<String, Object> createLobbyInitialState(long lobbyTimeoutMs) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "lobbyInit");
        state.put("awaitingSpawn", true);
        state.put("lobbyTimeoutMs", lobbyTimeoutMs);
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());

        // Static world data
        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }
        state.put("terrain", terrainGenerator.getTerrainData());
        state.put("obstacles", createObstacleStates());

        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createInitialFlagStates());
            state.put("flagsPerTeam", gameConfig.getRules().getFlagsPerTeam());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }
        if (gameConfig.getRules().hasOddball()) {
            Map<String, Object> oddballData = createInitialOddballState();
            if (oddballData != null) {
                state.put("oddball", oddballData);
            }
        }
        if (gameConfig.getRules().hasVip()) {
            state.put("vipMode", true);
        }

        return state;
    }

    /**
     * Strip dynamic entity collections from a full game state for delivery to a
     * {@code LOBBY} session. The modal sits over a static map preview, so we
     * only keep map geometry and objective-style entities (workshops, KOTH
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
                    || key.equals("mines") || key.equals("defenseLasers")
                    || key.equals("powerUps")) {
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
        state.put("mines", List.of());
        state.put("defenseLasers", List.of());
        state.put("powerUps", List.of());

        return state;
    }

    /**
     * Create initial game state for spectators (no player entity).
     */
    public Map<String, Object> createSpectatorInitialState() {
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

        // Add procedural terrain data
        state.put("terrain", terrainGenerator.getTerrainData());

        // Add obstacles
        state.put("obstacles", createObstacleStates());

        // Add flag information if flags are enabled
        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createInitialFlagStates());
            state.put("flagsPerTeam", gameConfig.getRules().getFlagsPerTeam());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        // Add oddball information if oddball mode is enabled
        if (gameConfig.getRules().hasOddball()) {
            Map<String, Object> oddballData = createInitialOddballState();
            if (oddballData != null) {
                state.put("oddball", oddballData);
            }
        }

        // Add VIP mode information if enabled
        if (gameConfig.getRules().hasVip()) {
            state.put("vipMode", true);
        }

        // Spectator-specific data
        state.put("spectatorData", Map.of(
                "showAllHealth", true,
                "showAllLoadouts", true,
                "showDamageNumbers", true
        ));

        return state;
    }


    // ========== Player States ==========

    private List<Map<String, Object>> createPlayerStates() {
        List<Map<String, Object>> playerStates = new ArrayList<>();
        for (Player player : gameEntities.getAllPlayers()) {
            Vector2 pos = player.getPosition();
            Map<String, Object> playerState = new HashMap<>();
            playerState.put("id", player.getId());
            playerState.put("name", player.getPlayerName());
            playerState.put("team", player.getTeam());
            playerState.put("x", pos.x);
            playerState.put("y", pos.y);
            playerState.put("rotation", player.getRotation());
            playerState.put("health", player.healthPercent());
            playerState.put("active", player.isActive());
            playerState.put("ammo", player.getCurrentWeapon().getCurrentAmmo());
            playerState.put("maxAmmo", player.getCurrentWeapon().getMagazineSize());
            playerState.put("reloading", player.isReloading());
            playerState.put("weaponRange", player.getCurrentWeapon().getRange());
            playerState.put("kills", player.getKills());
            playerState.put("deaths", player.getDeaths());
            playerState.put("captures", player.getCaptures());
            playerState.put("respawnTime", Math.max(0, ((double) player.getRespawnTime() - System.currentTimeMillis()) / 1000));
            playerState.put("livesRemaining", player.getLivesRemaining());
            playerState.put("eliminated", player.isEliminated());

            // Include VIP status
            if (gameConfig.getRules().hasVip()) {
                playerState.put("isVip", StatusEffectManager.isVip(player));
            }

            // Include active power-up effects
            List<String> activePowerUps = new ArrayList<>();
            for (AttributeModification mod : player.getAttributeModifications()) {
                String hint = mod.renderHint();
                if (hint != null && !hint.isEmpty()) {
                    activePowerUps.add(hint);
                }
            }
            playerState.put("activePowerUps", activePowerUps);

            playerStates.add(playerState);
        }
        return playerStates;
    }

    // ========== Projectile States ==========

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
            projState.put("ownerId", projectile.getOwnerId());
            projState.put("ownerTeam", projectile.getOwnerTeam());
            projState.put("ordinance", projectile.getOrdinance().name());

            // Convert bullet effects to string list for JSON serialization
            List<String> effectNames = projectile.getBulletEffects().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            projState.put("bulletEffects", effectNames);

            projectileStates.add(projState);
        }
        return projectileStates;
    }

    // ========== Obstacle States ==========
    private static final DecimalFormat DOUBLE_SHORTFORM = new DecimalFormat("#.##");

    private List<Map<String, Object>> createObstacleStates() {
        List<Map<String, Object>> obstacleStates = new ArrayList<>();
        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            Vector2 pos = obstacle.getPosition();
            Map<String, Object> obsState = new HashMap<>();
            obsState.put("id", obstacle.getId());
            obsState.put("x", pos.x);
            obsState.put("y", pos.y);
            obsState.put("type", obstacle.getType().name());
            obsState.put("boundingRadius", obstacle.getBoundingRadius());
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

    // ========== Field Effect States ==========

    private List<Map<String, Object>> createFieldEffectStates() {
        List<Map<String, Object>> fieldEffectStates = new ArrayList<>();
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            Vector2 pos = effect.getPosition();
            Map<String, Object> effectState = new HashMap<>();
            effectState.put("id", effect.getId());
            effectState.put("type", effect.getType().name());
            effectState.put("x", pos.x);
            effectState.put("y", pos.y);
            effectState.put("radius", effect.getRadius());
            effectState.put("duration", effect.getDuration());
            effectState.put("timeRemaining", effect.getTimeRemaining());
            effectState.put("progress", effect.getProgress());
            effectState.put("active", effect.isActive());
            effectState.put("ownerTeam", effect.getOwnerTeam());
            fieldEffectStates.add(effectState);
        }
        return fieldEffectStates;
    }

    // ========== Utility Entity States ==========

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
            turretState.put("ownerId", turret.getOwnerId());
            turretState.put("ownerTeam", turret.getOwnerTeam());
            turretStates.add(turretState);
        }
        return turretStates;
    }

    private List<Map<String, Object>> createNetStates() {
        List<Map<String, Object>> netStates = new ArrayList<>();
        for (NetProjectile net : gameEntities.getAllNetProjectiles()) {
            Vector2 pos = net.getPosition();
            Vector2 vel = net.getVelocity();
            Map<String, Object> netState = new HashMap<>();
            netState.put("id", net.getId());
            netState.put("type", "NET");
            netState.put("x", pos.x);
            netState.put("y", pos.y);
            netState.put("vx", vel.x);
            netState.put("vy", vel.y);
            netState.put("rotation", net.getBody().getTransform().getRotation().toRadians());
            netState.put("active", net.isActive());
            netState.put("ownerId", net.getOwnerId());
            netState.put("ownerTeam", net.getOwnerTeam());
            netStates.add(netState);
        }
        return netStates;
    }

    private List<Map<String, Object>> createMineStates() {
        List<Map<String, Object>> mineStates = new ArrayList<>();
        for (FieldEffect fieldEffect : gameEntities.getAllFieldEffects()) {
            if (fieldEffect.getType() != FieldEffectType.PROXIMITY_MINE) {
                continue;
            }
            Vector2 pos = fieldEffect.getPosition();
            Map<String, Object> mineState = new HashMap<>();
            mineState.put("id", fieldEffect.getId());
            mineState.put("type", "MINE");
            mineState.put("x", pos.x);
            mineState.put("y", pos.y);
            mineState.put("active", fieldEffect.isActive());
            mineState.put("ownerId", fieldEffect.getOwnerId());
            mineState.put("ownerTeam", fieldEffect.getOwnerTeam());
            mineState.put("isArmed", fieldEffect.isArmed());
            mineStates.add(mineState);
        }
        return mineStates;
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
            laserState.put("health", defenseLaser.healthPercent());
            laserState.put("active", defenseLaser.isActive());
            laserState.put("ownerId", defenseLaser.getOwnerId());
            laserState.put("ownerTeam", defenseLaser.getOwnerTeam());
            defenseLaserStates.add(laserState);
        }
        return defenseLaserStates;
    }

    private List<Map<String, Object>> createBeamStates() {
        List<Map<String, Object>> beamStates = new ArrayList<>();
        for (Beam beam : gameEntities.getAllBeams()) {
            Vector2 startPos = beam.getStartPoint();
            Vector2 effectiveEndPos = beam.getEffectiveEndPoint(); // Use effective end point for rendering
            Map<String, Object> beamState = new HashMap<>();
            beamState.put("id", beam.getId());
            beamState.put("startX", startPos.x);
            beamState.put("startY", startPos.y);
            beamState.put("endX", effectiveEndPos.x);
            beamState.put("endY", effectiveEndPos.y);
            beamState.put("ownerId", beam.getOwnerId());
            beamState.put("ownerTeam", beam.getOwnerTeam());
            beamState.put("damage", beam.getDamage());
            beamState.put("damageType", beam.getDamageApplicationType().name());
            beamState.put("durationPercent", beam.getDurationPercent());
            beamState.put("isHealingBeam", beam.isHealingBeam());
            beamState.put("canPiercePlayers", beam.canPiercePlayers());
            beamState.put("canPierceObstacles", beam.canPierceObstacles());
            beamStates.add(beamState);
        }
        return beamStates;
    }

    private List<Map<String, Object>> createPowerUpStates() {
        List<Map<String, Object>> powerUpStates = new ArrayList<>();
        for (PowerUp powerUp : gameEntities.getAllPowerUps()) {
            Vector2 pos = powerUp.getPosition();
            Map<String, Object> powerUpState = new HashMap<>();
            powerUpState.put("id", powerUp.getId());
            powerUpState.put("type", "POWERUP"); // Frontend expects this to identify as utility entity
            powerUpState.put("powerUpType", powerUp.getType().name()); // Store the actual power-up type
            powerUpState.put("displayName", powerUp.getType().getDisplayName());
            powerUpState.put("renderHint", powerUp.getType().getRenderHint());
            powerUpState.put("x", pos.x);
            powerUpState.put("y", pos.y);
            powerUpState.put("workshopId", powerUp.getWorkshopId());
            powerUpState.put("duration", powerUp.getDuration());
            powerUpState.put("effectStrength", powerUp.getEffectStrength());
            powerUpState.put("radius", powerUp.getBody().getFixture(0).getShape().getRadius());
            powerUpStates.add(powerUpState);
        }
        return powerUpStates;
    }

    // ========== Blinded Game State ==========

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
                    && !key.equals("mines")
                    && !key.equals("defenseLasers") && !key.equals("powerUps")) {
                state.put(key, entry.getValue());
            }
        }

        // Only include the blinded player's own data
        List<Map<String, Object>> selfOnly = new ArrayList<>();
        Vector2 pos = blindedPlayer.getPosition();
        Map<String, Object> playerState = new HashMap<>();
        playerState.put("id", blindedPlayer.getId());
        playerState.put("name", blindedPlayer.getPlayerName());
        playerState.put("team", blindedPlayer.getTeam());
        playerState.put("x", pos.x);
        playerState.put("y", pos.y);
        playerState.put("rotation", blindedPlayer.getRotation());
        playerState.put("health", blindedPlayer.healthPercent());
        playerState.put("active", blindedPlayer.isActive());
        playerState.put("ammo", blindedPlayer.getCurrentWeapon().getCurrentAmmo());
        playerState.put("maxAmmo", blindedPlayer.getCurrentWeapon().getMagazineSize());
        playerState.put("reloading", blindedPlayer.isReloading());
        playerState.put("weaponRange", blindedPlayer.getCurrentWeapon().getRange());
        playerState.put("kills", blindedPlayer.getKills());
        playerState.put("deaths", blindedPlayer.getDeaths());
        playerState.put("captures", blindedPlayer.getCaptures());
        playerState.put("respawnTime", Math.max(0, ((double) blindedPlayer.getRespawnTime() - System.currentTimeMillis()) / 1000));
        playerState.put("livesRemaining", blindedPlayer.getLivesRemaining());
        playerState.put("eliminated", blindedPlayer.isEliminated());
        playerState.put("activePowerUps", List.of());
        selfOnly.add(playerState);
        state.put("players", selfOnly);

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
                effectState.put("duration", effect.getDuration());
                effectState.put("timeRemaining", effect.getTimeRemaining());
                effectState.put("progress", effect.getProgress());
                effectState.put("active", effect.isActive());
                effectState.put("ownerTeam", effect.getOwnerTeam());
                smokeEffects.add(effectState);
            }
        }
        state.put("fieldEffects", smokeEffects);

        // Empty all other entity lists
        state.put("projectiles", List.of());
        state.put("beams", List.of());
        state.put("turrets", List.of());
        state.put("nets", List.of());
        state.put("mines", List.of());
        state.put("defenseLasers", List.of());
        state.put("powerUps", List.of());

        // Keep obstacles (static map geometry doesn't reveal enemy positions)
        state.put("obstacles", fullState.get("obstacles"));

        return state;
    }

    // ========== Game Mode Specific States ==========

    private List<Map<String, Object>> createKothZoneStates() {
        List<Map<String, Object>> zoneStates = new ArrayList<>();
        for (KothZone zone : gameEntities.getAllKothZones()) {
            Vector2 pos = zone.getPosition();
            Map<String, Object> zoneState = new HashMap<>();
            zoneState.put("id", zone.getId());
            zoneState.put("zoneNumber", zone.getZoneNumber());
            zoneState.put("x", pos.x);
            zoneState.put("y", pos.y);
            zoneState.put("radius", 80.0); // ZONE_RADIUS from KothZone
            zoneState.put("controllingTeam", zone.getControllingTeam());
            zoneState.put("state", zone.getState().name());
            zoneState.put("captureProgress", zone.getCaptureProgress());
            zoneState.put("playerCount", zone.getTotalPlayerCount());
            zoneStates.add(zoneState);
        }
        return zoneStates;
    }

    private List<Map<String, Object>> createWorkshopStates() {
        List<Map<String, Object>> workshopStates = new ArrayList<>();
        for (Workshop workshop : gameEntities.getAllWorkshops()) {
            Vector2 pos = workshop.getPosition();
            Map<String, Object> workshopState = new HashMap<>();
            workshopState.put("id", workshop.getId());
            workshopState.put("type", "WORKSHOP");
            workshopState.put("x", pos.x);
            workshopState.put("y", pos.y);
            workshopState.put("craftRadius", workshop.getBoundingRadius());
            workshopState.put("craftTime", workshop.getCraftTime());
            workshopState.put("maxPowerUps", workshop.getMaxPowerUps());
            int activeCrafters = workshop.getActiveCrafters();
            workshopState.put("activeCrafters", activeCrafters);
            workshopState.put("craftingProgress", workshop.getAllCraftingProgress());
            workshopState.put("shapes", verticesShorthand(workshop.getBody()));
            workshopStates.add(workshopState);
        }
        return workshopStates;
    }

    private List<Map<String, Object>> createHeadquartersStates() {
        List<Map<String, Object>> hqStates = new ArrayList<>();
        for (Headquarters hq : gameEntities.getAllHeadquarters()) {
            Vector2 pos = hq.getPosition();
            Map<String, Object> hqState = new HashMap<>();
            hqState.put("id", hq.getId());
            hqState.put("type", "HEADQUARTERS");
            hqState.put("team", hq.getTeamNumber());
            hqState.put("x", pos.x);
            hqState.put("y", pos.y);
            hqState.put("health", hq.healthPercent());
            hqState.put("active", hq.isActive());

            // Add shape data for client rendering
            hqState.putAll(hq.getShapeData());

            hqStates.add(hqState);
        }
        return hqStates;
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
            flagState.put("carriedBy", flag.getCarriedByPlayerId());
            flagState.put("homeX", flag.getHomePosition().x);
            flagState.put("homeY", flag.getHomePosition().y);
            flagState.put("captureCount", flag.getCaptureCount());
            flagState.put("isOddball", flag.isOddball());
            flagStates.add(flagState);
        }
        return flagStates;
    }

    private List<Map<String, Object>> createOddballStates() {
        List<Map<String, Object>> flagStates = new ArrayList<>();
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isOddball()) {
                Vector2 pos = flag.getPosition();
                Map<String, Object> flagState = new HashMap<>();
                flagState.put("id", flag.getId());
                flagState.put("x", pos.x);
                flagState.put("y", pos.y);
                flagState.put("ownerTeam", flag.getOwnerTeam());
                flagState.put("state", flag.getState().name());
                flagState.put("carriedBy", flag.getCarriedByPlayerId());
                flagState.put("homeX", flag.getHomePosition().x);
                flagState.put("homeY", flag.getHomePosition().y);
                flagState.put("isOddball", true);
                flagStates.add(flagState);
            }
        }
        return flagStates;
    }

    private List<Map<String, Object>> createInitialFlagStates() {
        List<Map<String, Object>> flagsData = new ArrayList<>();
        for (Flag flag : gameEntities.getAllFlags()) {
            Map<String, Object> flagData = new HashMap<>();
            flagData.put("id", flag.getId());
            flagData.put("ownerTeam", flag.getOwnerTeam());
            flagData.put("homeX", flag.getHomePosition().x);
            flagData.put("homeY", flag.getHomePosition().y);
            flagData.put("isOddball", flag.isOddball());
            flagsData.add(flagData);
        }
        return flagsData;
    }

    private Map<String, Object> createInitialOddballState() {
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isOddball()) {
                Map<String, Object> oddballData = new HashMap<>();
                oddballData.put("id", flag.getId());
                oddballData.put("homeX", flag.getHomePosition().x);
                oddballData.put("homeY", flag.getHomePosition().y);
                oddballData.put("pointsPerSecond", gameConfig.getRules().getOddballPointsPerSecond());
                return oddballData;
            }
        }
        return null;
    }
}


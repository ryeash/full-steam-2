package com.fullsteam.games;

import com.fullsteam.model.VictoryCondition;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeamSpawnManager;
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

/**
 * Handles initial state metadata serialization for newly connected clients.
 *
 * <p>Delivers map dimensions, static obstacles, team spawn areas, and initial flag positions
 * for initialState, lobbyInit, and spectatorInit connections. Real-time game state tick broadcasts
 * are handled exclusively by {@link BinaryGameStateSerializer}.
 */
public class GameStateSerializer {

    private static final DecimalFormat DOUBLE_SHORTFORM = new DecimalFormat("#.##");

    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final TeamSpawnManager teamSpawnManager;

    public GameStateSerializer(GameConfig gameConfig, GameEntities gameEntities,
                               TeamSpawnManager teamSpawnManager) {
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.teamSpawnManager = teamSpawnManager;
    }

    /**
     * Create initial game state for a newly joined player.
     *
     * @param player The player joining the game
     * @return Map containing initial state data
     */
    public Map<String, Object> createInitialGameState(Player player) {
        Map<String, Object> state = createBaseInitialState();
        state.put("type", "initialState");
        state.put("playerId", player.getId());
        return state;
    }

    /**
     * Create initial game state for a session in {@code LOBBY} (yet-to-spawn) state.
     *
     * @param lobbyTimeoutMs Milliseconds before session soft-downgrades to spectator
     * @param assignedName   Server-chosen random name for this session
     * @return Map containing lobby init data
     */
    public Map<String, Object> createLobbyInitialState(long lobbyTimeoutMs, String assignedName) {
        Map<String, Object> state = createBaseInitialState();
        state.put("type", "lobbyInit");
        state.put("awaitingSpawn", true);
        state.put("lobbyTimeoutMs", lobbyTimeoutMs);
        state.put("assignedName", assignedName);
        return state;
    }

    /**
     * Create initial game state for spectators (no player entity).
     *
     * @param spectatorCount Current number of spectators in this game session
     * @return Map containing spectator init data
     */
    public Map<String, Object> createSpectatorInitialState(int spectatorCount) {
        Map<String, Object> state = createBaseInitialState();
        state.put("type", "spectatorInit");
        state.put("spectatorMode", true);
        state.put("spectatorData", Map.of("spectatorCount", spectatorCount));
        return state;
    }

    private Map<String, Object> createBaseInitialState() {
        Map<String, Object> state = new HashMap<>();
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());

        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }

        state.put("obstacles", createObstacleStates());

        if (gameConfig.getRules().hasFlags()) {
            state.put("flags", createFlagStates());
        }

        if (gameConfig.getRules().hasHeadquarters()) {
            state.put("headquarters", createHeadquartersStates());
        }

        if (gameConfig.getRules().hasKothZones()) {
            state.put("kothZones", createKothZoneStates());
        }

        state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        state.put("sortBy", gameConfig.getRules().getVictoryCondition() == VictoryCondition.ELIMINATION ? "placement" : "score");
        state.put("activeScoreComponents", gameConfig.getRules().getActiveScoreComponents());

        return state;
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

    private List<Map<String, Object>> createHeadquartersStates() {
        List<Map<String, Object>> hqStates = new ArrayList<>();
        for (Headquarters hq : gameEntities.getAllHeadquarters()) {
            Vector2 pos = hq.getPosition();
            Map<String, Object> hqState = new HashMap<>();
            hqState.put("id", hq.getId());
            hqState.put("x", pos.x);
            hqState.put("y", pos.y);
            hqState.put("ownerTeam", hq.getOwnerTeam());
            hqState.put("shapes", verticesShorthand(hq.getBody()));
            hqStates.add(hqState);
        }
        return hqStates;
    }

    private List<Map<String, Object>> createKothZoneStates() {
        List<Map<String, Object>> zoneStates = new ArrayList<>();
        for (KothZone zone : gameEntities.getAllKothZones()) {
            Vector2 pos = zone.getPosition();
            Map<String, Object> zState = new HashMap<>();
            zState.put("id", zone.getId());
            zState.put("zoneNumber", zone.getZoneNumber());
            zState.put("x", pos.x);
            zState.put("y", pos.y);
            zState.put("radius", zone.getRadius());
            zoneStates.add(zState);
        }
        return zoneStates;
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
}

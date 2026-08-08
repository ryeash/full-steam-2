package com.fullsteam;

import com.fullsteam.ai.AITargetWrapper;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameStateSerializer;
import com.fullsteam.games.RuleSystem;
import com.fullsteam.games.TerrainGenerator;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.Turret;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmokeVisibilityTest {

    private GameConfig gameConfig;
    private GameEntities gameEntities;
    private GameStateSerializer serializer;

    @BeforeEach
    public void setUp() {
        gameConfig = GameConfig.builder().build();
        World<Body> world = new World<>();
        world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));
        gameEntities = new GameEntities(gameConfig, world);
        RuleSystem ruleSystem = new RuleSystem("test-game", gameConfig.getRules(), gameEntities, null, msg -> {}, gameConfig.getTeamCount());
        TeamSpawnManager teamSpawnManager = new TeamSpawnManager(gameConfig.getWorldWidth(), gameConfig.getWorldHeight(), gameConfig.getTeamCount());
        TerrainGenerator terrainGenerator = new TerrainGenerator(world, gameConfig);
        serializer = new GameStateSerializer(gameConfig, gameEntities, ruleSystem, teamSpawnManager, terrainGenerator);
    }

    @Test
    @DisplayName("Players inside smoke fields should be omitted from createGameState for other players")
    public void testPlayerInSmokeOmittedFromGeneralState() {
        Player player1 = new Player(1, "Player 1", 0, 0, 1, 100);
        Player player2 = new Player(2, "Player 2", 100, 100, 2, 100);
        gameEntities.add(player1);
        gameEntities.add(player2);

        // Initially both players are visible
        Map<String, Object> state = serializer.createGameState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertEquals(2, players.size(), "Both players should be visible when neither is in smoke");

        // Player 1 enters smoke
        player1.setVisionObscured(true);

        state = serializer.createGameState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> playersWithSmoke = (List<Map<String, Object>>) state.get("players");
        assertEquals(1, playersWithSmoke.size(), "Player in smoke should be omitted from general game state");
        assertEquals(2, playersWithSmoke.get(0).get("id"), "Only Player 2 should be in general game state");

        // Blinded Player 1 should still see themselves in their own blinded state
        Map<String, Object> blindedState = serializer.createBlindedGameState(player1, state);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blindedPlayers = (List<Map<String, Object>>) blindedState.get("players");
        assertEquals(1, blindedPlayers.size(), "Blinded state should contain exactly 1 player");
        assertEquals(1, blindedPlayers.get(0).get("id"), "Blinded state should contain the blinded player themselves");
    }

    @Test
    @DisplayName("Turrets should not target players obscured by smoke")
    public void testTurretDoesNotTargetObscuredPlayer() {
        Player player = new Player(1, "Target", 50, 0, 2, 100);
        gameEntities.add(player);

        Turret turret = new Turret(10, 1, new Vector2(0, 0), 100, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        gameEntities.add(turret);

        turret.acquireTarget(List.of(player));
        assertNotNull(turret.getCurrentTarget(), "Turret should acquire visible target");

        // Obscure player in smoke
        player.setVisionObscured(true);
        turret.update(0.016); // Trigger turret update / target re-evaluation
        assertNull(turret.getCurrentTarget(), "Turret should drop target when player enters smoke");
    }

    @Test
    @DisplayName("AITargetWrapper isVisible returns false when player is vision obscured")
    public void testAITargetWrapperVisibility() {
        Player player = new Player(1, "Target", 0, 0, 2, 100);
        AITargetWrapper wrapper = AITargetWrapper.fromPlayer(player);

        assertTrue(wrapper.isVisible(), "Target should be visible initially");

        player.setVisionObscured(true);
        assertFalse(wrapper.isVisible(), "Target should not be visible when obscured by smoke");
    }
}

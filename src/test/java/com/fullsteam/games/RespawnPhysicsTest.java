package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.EntityWorldDensity;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.physics.FieldEffectImpactHandler;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.TeamSpawnManager;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RespawnPhysicsTest {

    private World<Body> world;
    private GameConfig config;
    private TerrainGenerator terrain;
    private GameEntities entities;
    private TeamSpawnManager teamSpawnManager;
    private SpawnPointManager spawnPointManager;

    @BeforeEach
    void setUp() {
        world = new World<>();
        config = GameConfig.builder()
                .teamCount(2)
                .build();
        world.setBounds(new AxisAlignedBounds(config.getWorldWidth(), config.getWorldHeight()));
        config.getRules().setObstacleDensity(EntityWorldDensity.CHOKED);

        terrain = new TerrainGenerator(world, config);
        entities = new GameEntities(config, world);
        teamSpawnManager = new TeamSpawnManager(config.getWorldWidth(), config.getWorldHeight(), 2);
        spawnPointManager = new SpawnPointManager(config, entities, teamSpawnManager, terrain);
    }

    @Test
    void testSpawnPointNeverOverlapsObstaclesInTeamMode() {
        for (int i = 0; i < 500; i++) {
            Vector2 spawn = spawnPointManager.findSpawnPointForTeam(1);
            assertTrue(terrain.isPositionClear(spawn, Config.PLAYER_RADIUS),
                    "Team spawn point at " + spawn + " overlaps an obstacle!");
        }
    }

    @Test
    void testSpawnPointNeverOverlapsObstaclesInFFAMode() {
        // Place players to trigger fallback paths
        entities.add(new Player(10, "Other1", 0, 0, 0, 100));
        entities.add(new Player(11, "Other2", 200, 200, 0, 100));

        for (int i = 0; i < 500; i++) {
            Vector2 spawn = spawnPointManager.findFFASpawnPoint();
            assertTrue(terrain.isPositionClear(spawn, Config.PLAYER_RADIUS),
                    "FFA spawn point at " + spawn + " overlaps an obstacle!");
        }
    }

    @Test
    void testLegacySpawnPointNeverOverlapsObstacles() {
        for (int i = 0; i < 500; i++) {
            Vector2 spawn = spawnPointManager.findLegacySpawnPoint();
            assertTrue(terrain.isPositionClear(spawn, Config.PLAYER_RADIUS),
                    "Legacy spawn point at " + spawn + " overlaps an obstacle!");
        }
    }

    @Test
    void testRespawnResetsPhysicsBodyStateAndDamping() {
        GameManager gameManager = new GameManager("test_respawn", config, null);
        Player player = new Player(1, "Hero", 0, 0, 1, 100);
        gameManager.getGameEntities().add(player);

        // Put body to sleep and corrupt physics state as if idle during death
        Body body = player.getBody();
        body.setAtRest(true);
        body.setLinearDamping(99.0);
        body.setAngularDamping(99.0);
        body.applyForce(new Vector2(500, 500));
        player.die();

        // Lingering slow effect applied after death
        StatusEffectManager.applySlowEffect(player, 10.0, 5.0, "TestSlow");
        assertEquals(1, player.getAttributeModifications().size(), "Debuff was applied");

        // Respawn player
        gameManager.respawnPlayer(player);

        // Assert all physics body states are completely restored
        assertTrue(player.isActive(), "Player must be active on respawn");
        assertEquals(config.getPlayerMaxHealth(), player.getHealth(), "Player health restored");
        assertFalse(body.isAtRest(), "Physics body must not be at rest (sleeping) on respawn");
        assertTrue(body.isEnabled(), "Physics body must be enabled on respawn");
        assertEquals(Config.PLAYER_LINEAR_DAMPING, body.getLinearDamping(), 1e-6,
                "Linear damping must be reset to Config.PLAYER_LINEAR_DAMPING");
        assertEquals(Config.PLAYER_ANGULAR_DAMPING, body.getAngularDamping(), 1e-6,
                "Angular damping must be reset to Config.PLAYER_ANGULAR_DAMPING");
        assertEquals(0, body.getLinearVelocity().getMagnitude(), 1e-6, "Linear velocity must be zeroed");
        assertEquals(0, body.getAngularVelocity(), 1e-6, "Angular velocity must be zeroed");

        // Verify transforms are synced
        assertEquals(body.getTransform().getTranslation().x, body.getPreviousTransform().getTranslation().x, 1e-6);
        assertEquals(body.getTransform().getTranslation().y, body.getPreviousTransform().getTranslation().y, 1e-6);

        // Verify lingering debuffs were cleared, and only spawn invincibility is present
        assertTrue(StatusEffectManager.isInvincible(player), "Spawn invincibility should be present");
        assertTrue(player.getAttributeModifications().stream().noneMatch(am -> "slow".equals(am.uniqueKey())),
                "Previous slow debuff should be wiped out on respawn");
    }

    @Test
    void testFieldEffectDoesNotApplyDebuffToDeadPlayer() {
        GameManager gameManager = new GameManager("test_fe_dead", config, null);
        FieldEffectImpactHandler handler = new FieldEffectImpactHandler(gameManager, gameManager.getGameEntities());

        Player victim = new Player(2, "Victim", 50, 50, 2, 10); // 10 HP
        gameManager.getGameEntities().add(victim);

        // Lethal freeze field effect (high damage rate over deltaTime)
        FieldEffectCircle freeze = new FieldEffectCircle(
                1,
                FieldEffectType.FREEZE,
                new Vector2(50, 50),
                100,
                100,
                10000.0,
                5.0,
                0,
                1
        );

        // Handle impact - lethal damage kills player
        handler.handleFieldEffectDamageableOverlap(freeze, victim);

        assertFalse(victim.isActive(), "Player should be dead");
        assertEquals(0, victim.getAttributeModifications().size(),
                "Dead player should not receive freeze slow debuff after death");
    }
}

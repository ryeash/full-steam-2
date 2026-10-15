package com.fullsteam.physics;

import com.fullsteam.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Rules;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OddballBeamCollisionTest extends BaseTestClass {

    private GameManager gameManager;
    private GameEntities gameEntities;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        Rules rules = Rules.builder()
                .enableOddballNpcs(true)
                .rampageBallCount(1)
                .seekerBallCount(0)
                .oddballNpcPointsPerDamage(1.0)
                .build();

        GameConfig config = GameConfig.builder()
                .rules(rules)
                .build();

        gameManager = new GameManager("test-oddball-beam", config, new ObjectMapper());
        gameEntities = gameManager.getGameEntities();
    }

    @AfterEach
    void tearDown() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    @Test
    @DisplayName("Plasma beam should damage Oddball NPC and award points")
    void testPlasmaBeamDamagesOddball() {
        Player player = new Player(1, "TestAttacker", 0.0, 0.0, 1, 100.0);
        player.setActive(true);
        gameEntities.add(player);

        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 0.0, 0.0);
        gameEntities.add(oddball);

        FieldEffectBeam plasmaBeam = new FieldEffectBeam(
                new Vector2(-50, 0),
                new Vector2(50, 0),
                100.0,
                100.0, // 100 DPS base damage
                player.getId(),
                player.getTeam(),
                FieldEffectType.PLASMA,
                Set.of(),
                1.0
        );
        gameEntities.add(plasmaBeam);

        // Run post update hooks to register bodies into the dyn4j world
        gameEntities.runPostUpdateHooks();

        // Step world to trigger dyn4j collision between beam and oddball
        World<Body> world = gameEntities.getWorld();
        world.step(1);

        assertTrue(player.getScoring().getOddball() > 0,
                "Attacker should receive oddball points from plasma beam damage");
    }

    @Test
    @DisplayName("Laser beam should damage Oddball NPC and award points")
    void testLaserBeamDamagesOddball() {
        Player player = new Player(1, "TestAttacker", 0.0, 0.0, 1, 100.0);
        player.setActive(true);
        gameEntities.add(player);

        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 0.0, 0.0);
        gameEntities.add(oddball);

        FieldEffectBeam laserBeam = new FieldEffectBeam(
                new Vector2(-50, 0),
                new Vector2(50, 0),
                100.0,
                50.0,
                player.getId(),
                player.getTeam(),
                FieldEffectType.LASER,
                Set.of(),
                1.0
        );
        gameEntities.add(laserBeam);

        // Run post update hooks to register bodies into the dyn4j world
        gameEntities.runPostUpdateHooks();

        World<Body> world = gameEntities.getWorld();
        world.step(1);

        assertTrue(player.getScoring().getOddball() > 0,
                "Attacker should receive oddball points from laser beam damage");
    }
}

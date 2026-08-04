package com.fullsteam.ai;

import com.fullsteam.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazardAvoidanceTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private AIPlayer aiPlayer;
    private AIPlayer teammate;
    private AIPlayer enemy;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
        world.setBounds(new AxisAlignedBounds(2000, 2000));

        GameConfig config = GameConfig.builder()
                .worldWidth(2000)
                .worldHeight(2000)
                .build();

        gameEntities = new GameEntities(config, world);

        // Player 1: Team 1
        aiPlayer = new AIPlayer(1, "AI_1", 0.0, 0.0, AIPersonality.builder().build(), 1, 100.0);
        // Player 2: Team 1 (Teammate)
        teammate = new AIPlayer(2, "AI_2", 50.0, 0.0, AIPersonality.builder().build(), 1, 100.0);
        // Player 3: Team 2 (Enemy)
        enemy = new AIPlayer(3, "Enemy", 100.0, 0.0, AIPersonality.builder().build(), 2, 100.0);

        gameEntities.add(aiPlayer);
        gameEntities.add(teammate);
        gameEntities.add(enemy);
    }

    @Test
    void testIsDangerousToIgnoresSelfOwnedEffects() {
        FieldEffect selfFire = new FieldEffectCircle(
                aiPlayer.getId(),
                FieldEffectType.FIRE,
                new Vector2(0, 0),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                aiPlayer.getTeam()
        );

        assertFalse(HazardAvoidance.isDangerousTo(aiPlayer, selfFire),
                "AI should ignore its own fire effect");
    }

    @Test
    void testIsDangerousToIgnoresTeammateOwnedEffects() {
        FieldEffect teammateFire = new FieldEffectCircle(
                teammate.getId(),
                FieldEffectType.FIRE,
                new Vector2(0, 0),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                teammate.getTeam()
        );

        assertFalse(HazardAvoidance.isDangerousTo(aiPlayer, teammateFire),
                "AI should ignore teammate's fire effect in team mode");
    }

    @Test
    void testIsDangerousToDetectsEnemyEffects() {
        FieldEffect enemyFire = new FieldEffectCircle(
                enemy.getId(),
                FieldEffectType.FIRE,
                new Vector2(0, 0),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                enemy.getTeam()
        );

        assertTrue(HazardAvoidance.isDangerousTo(aiPlayer, enemyFire),
                "AI should consider enemy fire effect as dangerous");
    }

    @Test
    void testIsDangerousToDetectsSystemEnvironmentalEvents() {
        FieldEffect meteorFire = new FieldEffectCircle(
                -1, // System event
                FieldEffectType.FIRE,
                new Vector2(0, 0),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                0 // No team
        );

        assertTrue(HazardAvoidance.isDangerousTo(aiPlayer, meteorFire),
                "AI should consider system environmental fire as dangerous");
    }

    @Test
    void testIsDangerousToFriendlyVsEnemyWarningZone() {
        FieldEffect friendlyWarning = new FieldEffectCircle(
                aiPlayer.getId(),
                FieldEffectType.WARNING_ZONE,
                new Vector2(0, 0),
                50.0,
                50.0,
                0.0,
                3.0,
                0,
                aiPlayer.getTeam()
        );

        FieldEffect enemyWarning = new FieldEffectCircle(
                enemy.getId(),
                FieldEffectType.WARNING_ZONE,
                new Vector2(0, 0),
                50.0,
                50.0,
                0.0,
                3.0,
                0,
                enemy.getTeam()
        );

        assertFalse(HazardAvoidance.isDangerousTo(aiPlayer, friendlyWarning),
                "Friendly strike warning zone should not be dangerous");
        assertTrue(HazardAvoidance.isDangerousTo(aiPlayer, enemyWarning),
                "Enemy strike warning zone should be dangerous");
    }

    @Test
    void testFindNearbyHazardsFiltersFriendlyEffects() {
        FieldEffect selfFire = new FieldEffectCircle(
                aiPlayer.getId(),
                FieldEffectType.FIRE,
                new Vector2(10, 10),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                aiPlayer.getTeam()
        );

        FieldEffect enemyFire = new FieldEffectCircle(
                enemy.getId(),
                FieldEffectType.FIRE,
                new Vector2(15, 15),
                50.0,
                50.0,
                20.0,
                3.0,
                0,
                enemy.getTeam()
        );

        gameEntities.add(selfFire);
        gameEntities.add(enemyFire);

        List<FieldEffect> hazardsForPlayer = HazardAvoidance.findNearbyHazards(aiPlayer, new Vector2(0, 0), 100.0, gameEntities);
        assertEquals(1, hazardsForPlayer.size(), "Should only find 1 hazard (the enemy fire)");
        assertEquals(enemy.getId(), hazardsForPlayer.getFirst().getOwnerId());
    }

    @Test
    void testIsPositionSafeWithFriendlyAndEnemyEffects() {
        FieldEffect selfExplosion = new FieldEffectCircle(
                aiPlayer.getId(),
                FieldEffectType.EXPLOSION,
                new Vector2(0, 0),
                50.0,
                50.0,
                50.0,
                0.5,
                0,
                aiPlayer.getTeam()
        );

        gameEntities.add(selfExplosion);

        assertTrue(HazardAvoidance.isPositionSafe(aiPlayer, new Vector2(0, 0), 10.0, gameEntities),
                "Position should be safe for AI standing inside its own explosion");

        FieldEffect enemyExplosion = new FieldEffectCircle(
                enemy.getId(),
                FieldEffectType.EXPLOSION,
                new Vector2(0, 0),
                50.0,
                50.0,
                50.0,
                0.5,
                0,
                enemy.getTeam()
        );

        gameEntities.add(enemyExplosion);

        assertFalse(HazardAvoidance.isPositionSafe(aiPlayer, new Vector2(0, 0), 10.0, gameEntities),
                "Position should not be safe for AI standing inside an enemy explosion");
    }
}

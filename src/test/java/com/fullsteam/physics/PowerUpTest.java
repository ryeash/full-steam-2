package com.fullsteam.physics;

import com.fullsteam.games.BaseTestClass;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.Rules;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PowerUp entity and power-up functionality.
 */
class PowerUpTest extends BaseTestClass {

    private GameManager gameManager;
    private Player testPlayer;

    @BeforeEach
    void setUp() {
        Rules rules = Rules.builder().build();

        GameConfig gameConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000.0)
                .worldHeight(2000.0)
                .enableAIFilling(false)
                .rules(rules)
                .build();

        gameManager = new GameManager("test_game", gameConfig, null);

        testPlayer = new Player(1, "TestPlayer", 0, 0, 1, 100.0);
        gameManager.getGameEntities().add(testPlayer);
    }

    @Test
    @DisplayName("PowerUp creation with correct properties")
    void testPowerUpCreation() {
        Vector2 spawnPos = new Vector2(100, 100);

        PowerUp powerUp = new PowerUp(
                1,
                spawnPos,
                PowerUpType.SPEED_BOOST,
                30.0,
                1.5
        );

        assertNotNull(powerUp);
        assertTrue(powerUp.isActive());
        assertEquals(PowerUpType.SPEED_BOOST, powerUp.getType());
        assertEquals(30.0, powerUp.getDuration());
        assertEquals(spawnPos, powerUp.getPosition());
        assertTrue(powerUp.getBody().getFixture(0).isSensor());
    }

    @Test
    @DisplayName("All power-up types can be created")
    void testAllPowerUpTypes() {
        Vector2 spawnPos = new Vector2(100, 100);

        for (PowerUpType type : PowerUpType.values()) {
            PowerUp powerUp = new PowerUp(
                    1,
                    spawnPos,
                    type,
                    30.0,
                    1.0
            );

            assertNotNull(powerUp);
            assertEquals(type, powerUp.getType());
            assertNotNull(type.getDisplayName());
            assertNotNull(type.getRenderHint());
        }
    }

    @Test
    @DisplayName("PowerUp effect creation")
    void testPowerUpEffect() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUpType.DAMAGE_BOOST,
                25.0,
                2.0
        );

        PowerUpEffect effect = powerUp.getEffect();

        assertNotNull(effect);
        assertEquals(PowerUpType.DAMAGE_BOOST, effect.type());
        assertEquals(25.0, effect.duration());
        assertEquals(2.0, effect.strength());
    }

    @Test
    @DisplayName("PowerUp collection detection")
    void testPowerUpCollection() {
        Vector2 playerPos = new Vector2(100, 100);
        testPlayer.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);

        PowerUp powerUp = new PowerUp(
                1,
                playerPos,
                PowerUpType.HEALTH_REGENERATION,
                30.0,
                1.0
        );

        assertTrue(powerUp.canBeCollectedBy(testPlayer));

        testPlayer.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));

        testPlayer.setActive(true);
        testPlayer.setHealth(0);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
    }

    @Test
    @DisplayName("PowerUp update with active state")
    void testPowerUpUpdate() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUpType.DAMAGE_RESISTANCE,
                30.0,
                1.0
        );

        powerUp.update(5.0);

        assertTrue(powerUp.isActive());

        powerUp.setActive(false);
        powerUp.update(10.0);

        assertFalse(powerUp.isActive());
    }

    @Test
    @DisplayName("PowerUp display names and render hints")
    void testPowerUpDisplayProperties() {
        assertEquals("Speed Boost", PowerUpType.SPEED_BOOST.getDisplayName());
        assertEquals("⚡", PowerUpType.SPEED_BOOST.getRenderHint());

        assertEquals("Health Regen", PowerUpType.HEALTH_REGENERATION.getDisplayName());
        assertEquals("❤️", PowerUpType.HEALTH_REGENERATION.getRenderHint());

        assertEquals("Damage Boost", PowerUpType.DAMAGE_BOOST.getDisplayName());
        assertEquals("⚔️", PowerUpType.DAMAGE_BOOST.getRenderHint());

        assertEquals("Damage Resist", PowerUpType.DAMAGE_RESISTANCE.getDisplayName());
        assertEquals("🛡️", PowerUpType.DAMAGE_RESISTANCE.getRenderHint());

        assertEquals("Berserker", PowerUpType.BERSERKER_MODE.getDisplayName());
        assertEquals("🔥", PowerUpType.BERSERKER_MODE.getRenderHint());
    }

    @Test
    @DisplayName("PowerUp sensor behavior")
    void testPowerUpSensorBehavior() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUpType.SPEED_BOOST,
                30.0,
                1.0
        );

        assertTrue(powerUp.getBody().getFixture(0).isSensor());
        assertSame(powerUp.getBody().getMass().getType(), MassType.INFINITE);
    }

    @Test
    @DisplayName("PowerUp collection with different player states")
    void testPowerUpCollectionStates() {
        PowerUp powerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUpType.SPEED_BOOST,
                30.0,
                1.0
        );

        testPlayer.getBody().getTransform().setTranslation(100, 100);

        testPlayer.setActive(true);
        testPlayer.setHealth(100);
        assertTrue(powerUp.canBeCollectedBy(testPlayer));

        testPlayer.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));

        testPlayer.setActive(true);
        testPlayer.setHealth(0);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));

        testPlayer.setHealth(100);
        powerUp.setActive(false);
        assertFalse(powerUp.canBeCollectedBy(testPlayer));
    }

    @Test
    @DisplayName("PowerUp effect strength variations")
    void testPowerUpEffectStrength() {
        PowerUp weakPowerUp = new PowerUp(
                1,
                new Vector2(100, 100),
                PowerUpType.SPEED_BOOST,
                30.0,
                0.5
        );

        PowerUp strongPowerUp = new PowerUp(
                2,
                new Vector2(200, 200),
                PowerUpType.SPEED_BOOST,
                30.0,
                3.0
        );

        assertEquals(0.5, weakPowerUp.getEffectStrength());
        assertEquals(3.0, strongPowerUp.getEffectStrength());

        assertEquals(0.5, weakPowerUp.getEffect().strength());
        assertEquals(3.0, strongPowerUp.getEffect().strength());
    }
}

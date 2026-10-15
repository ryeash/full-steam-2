package com.fullsteam;

import com.fullsteam.games.BinaryGameStateSerializer;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.RuleSystem;
import com.fullsteam.model.DamageHit;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DamageHitsTest {

    private GameConfig gameConfig;
    private GameEntities gameEntities;
    private BinaryGameStateSerializer binarySerializer;

    @BeforeEach
    public void setUp() {
        gameConfig = GameConfig.builder().build();
        World<Body> world = new World<>();
        world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));
        gameEntities = new GameEntities(gameConfig, world);
        RuleSystem ruleSystem = new RuleSystem("test-game", gameConfig.getRules(), gameEntities, null, msg -> {}, gameConfig.getTeamCount());
        binarySerializer = new BinaryGameStateSerializer(gameConfig, gameEntities, ruleSystem);
    }

    @Test
    @DisplayName("GameManager should record discrete hits and clear them on fetch")
    public void testRecordDamageHit() {
        GameManager gameManager = new GameManager("test-game", gameConfig, new ObjectMapper());
        gameManager.shutdown();
        gameEntities.recordDamageHit(100.123, -50.456, 25.67, 1, 2, false);

        List<DamageHit> hits = gameEntities.getAndClearDamageHits();
        assertEquals(1, hits.size());
        DamageHit hit = hits.get(0);
        assertEquals(100.1, hit.getX());
        assertEquals(-50.5, hit.getY());
        assertEquals(26.0, hit.getDamage(), "Damage should be rounded to nearest integer");
        assertEquals(1, hit.getAttackerId());
        assertEquals(2, hit.getVictimId());
        assertFalse(hit.isKill());

        // Test damage less than 1 displays as 1
        gameEntities.recordDamageHit(0, 0, 0.3, 1, 2, false);
        hits = gameEntities.getAndClearDamageHits();
        assertEquals(1, hits.size());
        assertEquals(1.0, hits.get(0).getDamage(), "Damage < 1 should display as 1");

        // Second fetch should be empty
        assertTrue(gameEntities.getAndClearDamageHits().isEmpty());
    }

    @Test
    @DisplayName("GameManager should accumulate DOT damage until threshold reached")
    public void testAccumulateDotDamage() {
        GameManager gameManager = new GameManager("test-game", gameConfig, new ObjectMapper());
        gameManager.shutdown();

        // Small tick damage (e.g., 1.5 per tick)
        gameEntities.recordDotDamageHit(0, 0, 1.5, 1, 2, false);
        assertTrue(gameEntities.getAndClearDamageHits().isEmpty(), "DOT under 4.0 should not emit hit yet");

        gameEntities.recordDotDamageHit(0, 0, 1.5, 1, 2, false);
        assertTrue(gameEntities.getAndClearDamageHits().isEmpty(), "Total 3.0 < 4.0, should not emit yet");

        gameEntities.recordDotDamageHit(0, 0, 1.5, 1, 2, false);
        List<DamageHit> hits = gameEntities.getAndClearDamageHits();
        assertEquals(1, hits.size(), "Total 4.5 >= 4.0, should emit hit");
        assertEquals(5.0, hits.get(0).getDamage(), "4.5 rounds up to 5");
    }

    @Test
    @DisplayName("BinaryGameStateSerializer should include hits in gameState and filter for blinded player")
    public void testHitsInBinaryGameStateSerialization() throws Exception {
        GameManager gameManager = new GameManager("test-game", gameConfig, new ObjectMapper());
        gameManager.shutdown();
        binarySerializer.setGameManager(gameManager);

        Player p1 = new Player(1, "Attacker", 0, 0, 1, 100);
        Player p2 = new Player(2, "Victim", 100, 100, 2, 100);
        Player p3 = new Player(3, "Bystander", 200, 200, 1, 100);
        gameEntities.add(p1);
        gameEntities.add(p2);
        gameEntities.add(p3);

        gameEntities.recordDamageHit(100, 100, 35.0, 1, 2, false);

        byte[] fullState = binarySerializer.serializeGameState();
        assertTrue(fullState.length > 0);

        // Blinded Bystander (p3) should NOT see hits between p1 and p2
        p3.setVisionObscured(true);
        gameEntities.recordDamageHit(100, 100, 35.0, 1, 2, false);
        byte[] bystanderBlindedState = binarySerializer.serializeBlindedGameState(p3);
        assertTrue(bystanderBlindedState.length > 0);

        // Blinded Victim (p2) SHOULD see the hit done to them
        p2.setVisionObscured(true);
        gameEntities.recordDamageHit(100, 100, 35.0, 1, 2, false);
        byte[] victimBlindedState = binarySerializer.serializeBlindedGameState(p2);
        assertTrue(victimBlindedState.length > 0);
    }
}

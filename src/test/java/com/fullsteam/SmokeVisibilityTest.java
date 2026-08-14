package com.fullsteam;

import com.fullsteam.ai.AITargetWrapper;
import com.fullsteam.games.BinaryGameStateSerializer;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.RuleSystem;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmokeVisibilityTest {

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

    private int readPlayerCountFromBinaryState(byte[] data) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        in.readNBytes(4); // magic
        in.readByte(); // headerFlags
        in.readByte(); // gameStateCode
        in.readLong(); // timestamp
        in.readFloat(); // timeRemaining
        in.readFloat(); // startCountdownRemaining
        in.readByte(); // winningTeam
        in.readShort(); // winningPlayerId

        // Score style & sorting strings
        int len1 = in.readByte() & 0xFF; in.readNBytes(len1);
        int len2 = in.readByte() & 0xFF; in.readNBytes(len2);
        int compCount = in.readByte() & 0xFF;
        for (int c = 0; c < compCount; c++) {
            int len = in.readByte() & 0xFF; in.readNBytes(len);
        }

        // Team scores
        int teamScoreCount = in.readByte() & 0xFF;
        for (int t = 0; t < teamScoreCount; t++) {
            in.readByte();
            in.readInt();
        }

        // Section 1: Players
        return in.readShort() & 0xFFFF;
    }

    @Test
    @DisplayName("Players inside smoke fields should be omitted from binary game state for other players")
    public void testPlayerInSmokeOmittedFromGeneralState() throws Exception {
        Player player1 = new Player(1, "Player 1", 0, 0, 1, 100);
        Player player2 = new Player(2, "Player 2", 100, 100, 2, 100);
        gameEntities.add(player1);
        gameEntities.add(player2);

        // Initially both players are visible
        byte[] data = binarySerializer.serializeGameState();
        assertEquals(2, readPlayerCountFromBinaryState(data), "Both players should be visible when neither is in smoke");

        // Player 1 enters smoke
        player1.setVisionObscured(true);

        data = binarySerializer.serializeGameState();
        assertEquals(1, readPlayerCountFromBinaryState(data), "Player in smoke should be omitted from general binary game state");

        // Blinded Player 1 should still see themselves in their own blinded state
        byte[] blindedData = binarySerializer.serializeBlindedGameState(player1);
        assertEquals(1, readPlayerCountFromBinaryState(blindedData), "Blinded state should contain exactly 1 player");
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

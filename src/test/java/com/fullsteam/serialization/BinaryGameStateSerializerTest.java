package com.fullsteam.serialization;

import com.fullsteam.games.*;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnManager;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BinaryGameStateSerializerTest {

    private GameConfig gameConfig;
    private GameEntities gameEntities;
    private RuleSystem ruleSystem;
    private BinaryGameStateSerializer binarySerializer;

    @BeforeEach
    public void setUp() {
        gameConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000)
                .worldHeight(2000)
                .build();

        World<Body> world = new World<>();
        world.setBounds(new AxisAlignedBounds(2000, 2000));

        gameEntities = new GameEntities(gameConfig, world);
        ruleSystem = new RuleSystem("test-binary", gameConfig.getRules(), gameEntities, null, msg -> {}, gameConfig.getTeamCount());
        TeamSpawnManager spawnManager = new TeamSpawnManager(2000, 2000, gameConfig.getTeamCount());
        TerrainGenerator terrainGen = new TerrainGenerator(world, gameConfig);

        binarySerializer = new BinaryGameStateSerializer(gameConfig, gameEntities, ruleSystem);

        // Populate test player
        Player p1 = new Player(1, "TestHero", 100.0, 200.0, 1, 100.0);
        p1.setActive(true);
        p1.setWeapon(WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        p1.getBody().setLinearVelocity(15.5, -10.2);
        p1.getScoring().addKill();
        gameEntities.add(p1);

        // Populate test projectile
        Projectile proj = new Projectile(
                1,
                new Vector2(100.0, 200.0),
                new Vector2(500.0, 0.0),
                25.0,
                1000.0,
                1,
                0.0,
                Set.of(BulletEffect.EXPLOSIVE),
                Ordinance.PROJECTILE,
                10.0,
                1.0
        );
        gameEntities.add(proj);

        // Populate test field effect
        FieldEffectCircle fe = new FieldEffectCircle(
                1,
                FieldEffectType.HEAL_ZONE,
                new Vector2(100.0, 200.0),
                50.0,
                50.0,
                5.0,
                10.0,
                0,
                1
        );
        fe.setActive(true);
        gameEntities.add(fe);
    }

    @Test
    @DisplayName("serializeGameState should produce valid FSB1 header and entities")
    public void testFullGameStateSerialization() throws Exception {
        byte[] data = binarySerializer.serializeGameState();
        assertNotNull(data);
        assertTrue(data.length > 18);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // Header check
        assertEquals('F', in.readByte());
        assertEquals('S', in.readByte());
        assertEquals('B', in.readByte());
        assertEquals(1, in.readByte());

        int headerFlags = in.readByte() & 0xFF;
        assertFalse((headerFlags & 1) != 0); // visionObscured = false
        assertFalse((headerFlags & 2) != 0); // awaitingSpawn = false

        int gameStateCode = in.readByte() & 0xFF;
        assertTrue(gameStateCode >= 0);

        long timestamp = in.readLong();
        assertTrue(timestamp > 0);

        float timeRemaining = in.readFloat();
        float startCountdownRemaining = in.readFloat();
        int winningTeam = in.readByte();
        short winningPlayerId = in.readShort();

        // Score Style & Scoring Config
        int scoreStyleLen = in.readByte() & 0xFF;
        in.readNBytes(scoreStyleLen); // scoreStyle
        int sortByLen = in.readByte() & 0xFF;
        in.readNBytes(sortByLen); // sortBy
        int compCount = in.readByte() & 0xFF;
        for (int c = 0; c < compCount; c++) {
            int compLen = in.readByte() & 0xFF;
            in.readNBytes(compLen);
        }

        // Team Scores
        int teamScoreCount = in.readByte() & 0xFF;
        for (int t = 0; t < teamScoreCount; t++) {
            in.readByte(); // teamId
            in.readInt(); // teamScore
        }

        // 1. Players Section
        int playerCount = in.readShort();
        assertEquals(1, playerCount);

        short pId = in.readShort();
        assertEquals(1, pId);

        int team = in.readByte();
        assertEquals(1, team);

        int pFlags = in.readByte();
        assertTrue((pFlags & 1) != 0); // active

        int nameLen = in.readByte() & 0xFF;
        byte[] nameBytes = in.readNBytes(nameLen);
        assertEquals("TestHero", new String(nameBytes));

        float x = in.readFloat();
        float y = in.readFloat();
        assertEquals(100.0f, x, 0.1f);
        assertEquals(200.0f, y, 0.1f);

        short vx = in.readShort();
        short vy = in.readShort();
        short rot = in.readShort();

        int healthByte = in.readByte() & 0xFF;
        assertEquals(100, healthByte); // 100% health

        int ammo = in.readByte() & 0xFF;
        int maxAmmo = in.readByte() & 0xFF;
        int reloadPct = in.readByte() & 0xFF;
        int utilityCooldownPct = in.readByte() & 0xFF;
        short range = in.readShort();

        float respawnTime = in.readFloat();
        byte livesRemaining = in.readByte();
        assertEquals(-1, livesRemaining); // -1 = unlimited lives

        // Scoring (10 shorts)
        for (int i = 0; i < 10; i++) {
            in.readShort();
        }

        // Active powerups
        int powerUpCount = in.readByte() & 0xFF;
        for (int i = 0; i < powerUpCount; i++) {
            int pLen = in.readByte() & 0xFF;
            in.readNBytes(pLen);
        }

        // 2. Projectiles Section
        int projCount = in.readShort();
        assertEquals(1, projCount);
        int projId = in.readInt();
        assertTrue(projId > 0);
        float px = in.readFloat();
        float py = in.readFloat();
        float pvx = in.readFloat();
        float pvy = in.readFloat();
        float pCaliber = in.readFloat();
        int effectMask = in.readShort() & 0xFFFF;

        // 3. Field Effects Section
        int feCount = in.readShort();
        assertEquals(1, feCount);
        int feId = in.readShort();
        assertTrue(feId > 0);
        in.readByte(); // type
        in.readByte(); // ownerTeam
        in.readFloat(); // x
        in.readFloat(); // y
        in.readFloat(); // rot
        in.readFloat(); // radius
        in.readFloat(); // progress
        in.readByte(); // flags

        // Binary Body Shapes
        int fixtureCount = in.readByte() & 0xFF;
        assertEquals(1, fixtureCount);
        int shapeType = in.readByte() & 0xFF;
        assertEquals(1, shapeType); // 1 = Circle
        float cx = in.readFloat();
        float cy = in.readFloat();
        float r = in.readFloat();
        assertTrue(r > 0);
    }

    @Test
    @DisplayName("serializeBlindedGameState should set visionObscured flag and strip other entities")
    public void testBlindedGameStateSerialization() throws Exception {
        Player p1 = gameEntities.getPlayer(1);
        assertNotNull(p1);

        byte[] data = binarySerializer.serializeBlindedGameState(p1);
        assertNotNull(data);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // Header
        in.readNBytes(4); // magic
        int headerFlags = in.readByte() & 0xFF;
        assertTrue((headerFlags & 1) != 0); // visionObscured = true
    }

    @Test
    @DisplayName("serializeLobbyGameState should set awaitingSpawn flag")
    public void testLobbyGameStateSerialization() throws Exception {
        byte[] data = binarySerializer.serializeLobbyGameState();
        assertNotNull(data);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // Header
        in.readNBytes(4); // magic
        int headerFlags = in.readByte() & 0xFF;
        assertTrue((headerFlags & 2) != 0); // awaitingSpawn = true
    }
}

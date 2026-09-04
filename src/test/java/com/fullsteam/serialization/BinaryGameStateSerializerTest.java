package com.fullsteam.serialization;

import com.fullsteam.games.*;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Rules;
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
    @DisplayName("serializeGameState(true) should produce valid FSB1 low-frequency payload")
    public void testLowFrequencyGameStateSerialization() throws Exception {
        byte[] data = binarySerializer.serializeGameState(true);
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
        assertTrue((headerFlags & 32) != 0); // hasLowFreq = true

        int gameStateCode = in.readByte() & 0xFF;
        assertTrue(gameStateCode >= 0);

        long timestamp = in.readLong();
        assertTrue(timestamp > 0);

        int winningTeam = in.readByte();
        short winningPlayerId = in.readShort();

        float timeRemaining = in.readFloat();

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

        float x = in.readFloat();
        float y = in.readFloat();
        assertEquals(100.0f, x, 0.1f);
        assertEquals(200.0f, y, 0.1f);

        short vx = in.readShort();
        short vy = in.readShort();
        short rot = in.readShort();

        int healthByte = in.readByte() & 0xFF;
        assertEquals(100, healthByte); // 100% health

        int armorByte = in.readByte() & 0xFF;
        assertEquals(0, armorByte); // 0% armor

        int ammo = in.readByte() & 0xFF;
        int maxAmmo = in.readByte() & 0xFF;
        int reloadPct = in.readByte() & 0xFF;
        int utilityCooldownPct = in.readByte() & 0xFF;

        float respawnTime = in.readFloat();
        byte livesRemaining = in.readByte();
        assertEquals(-1, livesRemaining); // -1 = unlimited lives

        int nameLen = in.readByte() & 0xFF;
        byte[] nameBytes = in.readNBytes(nameLen);
        assertEquals("TestHero", new String(nameBytes));

        short range = in.readShort();

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
    @DisplayName("serializeGameState(false) should produce compact high-frequency payload")
    public void testHighFrequencyGameStateSerialization() throws Exception {
        byte[] lowData = binarySerializer.serializeGameState(true); // reset tick counter / force flag
        byte[] data = binarySerializer.serializeGameState(false);
        assertNotNull(data);
        assertTrue(data.length < lowData.length); // significantly smaller payload

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // Header check
        in.readNBytes(4); // magic
        int headerFlags = in.readByte() & 0xFF;
        assertFalse((headerFlags & 32) != 0); // hasLowFreq = false
    }

    @Test
    @DisplayName("serializeBlindedGameState should set visionObscured flag")
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
    @DisplayName("Unified NPCs section should serialize both Oddballs and Zombies with category tags")
    public void testUnifiedNpcSerialization() throws Exception {
        Rules rules = Rules.builder()
                .enableOddballNpcs(true)
                .enableZombies(true)
                .build();
        GameConfig config = GameConfig.builder()
                .rules(rules)
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000)
                .worldHeight(2000)
                .build();

        World<Body> world = new World<>();
        GameEntities entities = new GameEntities(config, world);
        RuleSystem rulesSys = new RuleSystem("test-unified-npcs", rules, entities, null, msg -> {}, 2);
        BinaryGameStateSerializer serializer = new BinaryGameStateSerializer(config, entities, rulesSys);

        com.fullsteam.physics.Oddball oddball = new com.fullsteam.physics.Oddball(com.fullsteam.physics.Oddball.Personality.RAMPAGE, 10.0, 20.0);
        oddball.setActive(true);
        entities.add(oddball);

        com.fullsteam.physics.Zombie zombie = new com.fullsteam.physics.Zombie(50, com.fullsteam.model.ZombieType.TANK, com.fullsteam.model.ZombieAttackPattern.NEAREST_PLAYER, 30.0, 40.0);
        zombie.setActive(true);
        zombie.setLunging(true);
        entities.add(zombie);

        byte[] data = serializer.serializeGameState(true);
        assertNotNull(data);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // Skip header
        in.readNBytes(4); // FSB1
        in.readByte(); // flags
        in.readByte(); // gameStateCode
        in.readLong(); // timestamp
        in.readByte(); // winningTeam
        in.readShort(); // winningPlayerId
        in.readFloat(); // timeRemaining
        int teamScoreCount = in.readByte() & 0xFF;
        for (int t = 0; t < teamScoreCount; t++) {
            in.readByte();
            in.readInt();
        }

        // Section 1: Players (0)
        assertEquals(0, in.readShort());
        // Section 2: Projectiles (0)
        assertEquals(0, in.readShort());
        // Section 3: Field Effects (0)
        assertEquals(0, in.readShort());
        // Section 4: Turrets (0)
        assertEquals(0, in.readShort());
        // Section 5: Nets (0)
        assertEquals(0, in.readShort());
        // Section 6: Defense Lasers (0)
        assertEquals(0, in.readShort());
        // Section 7: KOTH (0)
        assertEquals(0, in.readShort());
        // Section 8: Headquarters (0)
        assertEquals(0, in.readShort());
        // Section 9: Flags (0)
        assertEquals(0, in.readShort());

        // Section 10: Unified NPCs (2 NPCs: 1 Oddball + 1 Zombie)
        int npcCount = in.readShort();
        assertEquals(2, npcCount);

        // NPC 1: Oddball
        short obId = in.readShort();
        assertEquals(oddball.getId(), obId);
        int obCategory = in.readByte() & 0xFF;
        assertEquals(0, obCategory); // 0 = ODDBALL
        int obPersonality = in.readByte() & 0xFF;
        assertEquals(com.fullsteam.physics.Oddball.Personality.RAMPAGE.ordinal(), obPersonality);
        in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat();
        in.readByte(); // healthPercent
        int obFlags = in.readByte() & 0xFF;
        assertEquals(0, obFlags);

        // NPC 2: Zombie
        short zId = in.readShort();
        assertEquals(50, zId);
        int zCategory = in.readByte() & 0xFF;
        assertEquals(1, zCategory); // 1 = ZOMBIE
        int zType = in.readByte() & 0xFF;
        assertEquals(com.fullsteam.model.ZombieType.TANK.ordinal(), zType);
        in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat(); in.readFloat();
        in.readByte(); // healthPercent
        int zFlags = in.readByte() & 0xFF;
        assertEquals(1, zFlags & 1); // isLunging = true

        // Section 11: Hits (0)
        assertEquals(0, in.readShort());
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

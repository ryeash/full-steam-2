package com.fullsteam;

import com.fullsteam.ai.AIMemory;
import com.fullsteam.ai.AIPersonality;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AITargetWrapper;
import com.fullsteam.ai.OddballBehavior;
import com.fullsteam.games.BinaryGameStateSerializer;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.RuleSystem;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.model.ZombieAttackPattern;
import com.fullsteam.model.ZombieType;
import com.fullsteam.physics.FieldEffectImpactHandler;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Zombie;
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
        com.fullsteam.model.Rules rules = com.fullsteam.model.Rules.builder()
                .enableOddballNpcs(true)
                .rampageBallCount(1)
                .seekerBallCount(1)
                .enableZombies(true)
                .build();
        gameConfig = GameConfig.builder().rules(rules).build();
        World<Body> world = new World<>();
        world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));
        gameEntities = new GameEntities(gameConfig, world);
        RuleSystem ruleSystem = new RuleSystem("test-game", gameConfig.getRules(), gameEntities, null, msg -> {}, gameConfig.getTeamCount());
        binarySerializer = new BinaryGameStateSerializer(gameConfig, gameEntities, ruleSystem);
    }

    private DataInputStream skipToSection1(byte[] data) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        in.readNBytes(4); // magic
        int headerFlags = in.readByte() & 0xFF;
        boolean isCountdown = (headerFlags & 4) != 0;
        boolean hasLowFreq = (headerFlags & 32) != 0;

        in.readByte(); // gameStateCode
        in.readLong(); // timestamp

        if (isCountdown) {
            in.readFloat(); // startCountdownRemaining
        }

        in.readByte(); // winningTeam
        in.readShort(); // winningPlayerId

        if (hasLowFreq) {
            in.readFloat(); // timeRemaining
            int teamScoreCount = in.readByte() & 0xFF;
            for (int t = 0; t < teamScoreCount; t++) {
                in.readByte();
                in.readInt();
            }
        }
        return in;
    }

    private int readPlayerCountFromBinaryState(byte[] data) throws Exception {
        DataInputStream in = skipToSection1(data);
        return in.readShort() & 0xFFFF;
    }

    private int readTurretCountFromBinaryState(byte[] data) throws Exception {
        DataInputStream in = skipToSection1(data);
        int playerCount = in.readShort() & 0xFFFF;
        assertEquals(0, playerCount);
        int projCount = in.readShort() & 0xFFFF;
        assertEquals(0, projCount);
        int feCount = in.readShort() & 0xFFFF;
        assertEquals(0, feCount);
        return in.readShort() & 0xFFFF;
    }

    private int readNpcCountFromBinaryState(byte[] data) throws Exception {
        DataInputStream in = skipToSection1(data);
        int playerCount = in.readShort() & 0xFFFF;
        assertEquals(0, playerCount);
        int projCount = in.readShort() & 0xFFFF;
        assertEquals(0, projCount);
        int feCount = in.readShort() & 0xFFFF;
        assertEquals(0, feCount);
        int turretCount = in.readShort() & 0xFFFF;
        for (int i = 0; i < turretCount; i++) {
            in.readShort();
            in.readByte();
            in.readFloat();
            in.readFloat();
            in.readFloat();
            in.readByte();
            in.readBoolean();
        }
        int netCount = in.readShort() & 0xFFFF;
        assertEquals(0, netCount);
        int laserCount = in.readShort() & 0xFFFF;
        assertEquals(0, laserCount);
        int kothCount = in.readShort() & 0xFFFF;
        assertEquals(0, kothCount);
        int hqCount = in.readShort() & 0xFFFF;
        assertEquals(0, hqCount);
        int flagCount = in.readShort() & 0xFFFF;
        assertEquals(0, flagCount);
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

    @Test
    @DisplayName("Homing projectiles and missiles should not track players obscured by smoke")
    public void testHomingDoesNotTargetObscuredPlayer() {
        Player player = new Player(2, "Target", 100, 0, 2, 100);
        gameEntities.add(player);

        com.fullsteam.physics.BulletEffectProcessor processor = new com.fullsteam.physics.BulletEffectProcessor(gameEntities);

        // Standard homing projectile
        com.fullsteam.physics.Projectile standardHoming = new com.fullsteam.physics.Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(0, 300), // heading North
                20.0,
                1000.0,
                1,
                0.09,
                java.util.Set.of(com.fullsteam.model.BulletEffect.HOMING),
                com.fullsteam.model.Ordinance.PROJECTILE,
                1.0,
                0.0
        );

        // Homing missile
        com.fullsteam.physics.Projectile homingMissile = new com.fullsteam.physics.Projectile(
                1,
                new Vector2(0, 0),
                new Vector2(0, 300), // heading North
                20.0,
                1000.0,
                1,
                0.09,
                java.util.Set.of(com.fullsteam.model.BulletEffect.HOMING),
                com.fullsteam.model.Ordinance.MISSILE,
                1.0,
                0.0
        );

        // Obscure player in smoke
        player.setVisionObscured(true);

        // Neither missile nor standard projectile should steer towards obscured player
        double missileHeadingBefore = homingMissile.getHeading();
        processor.applyHomingBehavior(homingMissile, 0.05);
        assertEquals(missileHeadingBefore, homingMissile.getHeading(), 0.0001,
                "Missile should not home toward player obscured by smoke");

        Vector2 bulletVelBefore = standardHoming.getBody().getLinearVelocity().copy();
        processor.applyHomingBehavior(standardHoming, 0.05);
        assertEquals(bulletVelBefore.x, standardHoming.getBody().getLinearVelocity().x, 0.0001,
                "Standard projectile should not steer toward player obscured by smoke");
    }

    @Test
    @DisplayName("Oddballs inside smoke fields should be omitted from binary game state")
    public void testOddballInSmokeOmittedFromGeneralState() throws Exception {
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 100, 100);
        oddball.setActive(true);
        gameEntities.add(oddball);

        byte[] data = binarySerializer.serializeGameState();
        assertEquals(1, readNpcCountFromBinaryState(data), "Oddball should be visible when not in smoke");

        oddball.setVisionObscured(true);
        data = binarySerializer.serializeGameState();
        assertEquals(0, readNpcCountFromBinaryState(data), "Oddball in smoke should be omitted from binary game state");
    }

    @Test
    @DisplayName("Zombies inside smoke fields should be omitted from binary game state")
    public void testZombieInSmokeOmittedFromGeneralState() throws Exception {
        Zombie zombie = new Zombie(50, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 100, 100);
        zombie.setActive(true);
        gameEntities.add(zombie);

        byte[] data = binarySerializer.serializeGameState();
        assertEquals(1, readNpcCountFromBinaryState(data), "Zombie should be visible when not in smoke");

        zombie.setVisionObscured(true);
        data = binarySerializer.serializeGameState();
        assertEquals(0, readNpcCountFromBinaryState(data), "Zombie in smoke should be omitted from binary game state");
    }

    @Test
    @DisplayName("Turrets inside smoke fields should be omitted from binary game state")
    public void testTurretInSmokeOmittedFromGeneralState() throws Exception {
        Turret turret = new Turret(10, 1, new Vector2(100, 100), 100, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        turret.setActive(true);
        gameEntities.add(turret);

        byte[] data = binarySerializer.serializeGameState();
        assertEquals(1, readTurretCountFromBinaryState(data), "Turret should be visible when not in smoke");

        turret.setVisionObscured(true);
        data = binarySerializer.serializeGameState();
        assertEquals(0, readTurretCountFromBinaryState(data), "Turret in smoke should be omitted from binary game state");
    }

    @Test
    @DisplayName("Smoke field effect sets visionObscured on Oddball, Zombie, and Turret, and resetVisionObscuredFlags clears them")
    public void testSmokeFieldEffectAppliesVisionObscuredToOddballZombieTurret() {
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 100, 100);
        Zombie zombie = new Zombie(50, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 100, 100);
        Turret turret = new Turret(10, 1, new Vector2(100, 100), 100, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        gameEntities.add(oddball);
        gameEntities.add(zombie);
        gameEntities.add(turret);

        FieldEffectCircle smoke = new FieldEffectCircle(
                0,
                FieldEffectType.SMOKE,
                new Vector2(100, 100),
                150.0,
                150.0,
                0.0,
                10.0,
                0L,
                0
        );

        assertTrue(smoke.canAffect(oddball), "Smoke should affect Oddball");
        assertTrue(smoke.canAffect(zombie), "Smoke should affect Zombie");
        assertTrue(smoke.canAffect(turret), "Smoke should affect Turret");

        FieldEffectImpactHandler handler = new FieldEffectImpactHandler(null, gameEntities);
        handler.handleFieldEffectDamageableOverlap(smoke, oddball);
        handler.handleFieldEffectDamageableOverlap(smoke, zombie);
        handler.handleFieldEffectDamageableOverlap(smoke, turret);

        assertTrue(oddball.isVisionObscured(), "Oddball in smoke must have visionObscured == true");
        assertTrue(zombie.isVisionObscured(), "Zombie in smoke must have visionObscured == true");
        assertTrue(turret.isVisionObscured(), "Turret in smoke must have visionObscured == true");

        gameEntities.resetVisionObscuredFlags();
        assertFalse(oddball.isVisionObscured(), "resetVisionObscuredFlags should reset Oddball visionObscured");
        assertFalse(zombie.isVisionObscured(), "resetVisionObscuredFlags should reset Zombie visionObscured");
        assertFalse(turret.isVisionObscured(), "resetVisionObscuredFlags should reset Turret visionObscured");
    }

    @Test
    @DisplayName("Blinded Oddball inside smoke cannot target players")
    public void testOddballBlindedInSmokeCannotTargetPlayers() {
        Player player = new Player(1, "Target", 50, 0, 1, 100);
        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 0, 0);
        oddball.setActive(true);

        oddball.tickAI(List.of(player));
        assertEquals(player, oddball.getCurrentTarget(), "Oddball should target player when not obscured");

        oddball.setVisionObscured(true);
        oddball.tickAI(List.of(player));
        assertNull(oddball.getCurrentTarget(), "Oddball in smoke should be blinded and cannot target players");
    }

    @Test
    @DisplayName("Blinded Zombie inside smoke cannot acquire target")
    public void testZombieBlindedInSmokeCannotTargetEntities() {
        Player player = new Player(1, "Target", 50, 0, 1, 100);
        gameEntities.add(player);

        Zombie zombie = new Zombie(50, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 0, 0);
        zombie.setActive(true);
        gameEntities.add(zombie);

        zombie.tickAI(gameEntities, 0.016);
        assertEquals(player, zombie.getCurrentTargetEntity(), "Zombie should acquire player when neither is obscured");

        zombie.setVisionObscured(true);
        zombie.tickAI(gameEntities, 0.016);
        assertNull(zombie.getCurrentTargetEntity(), "Zombie inside smoke cannot acquire or maintain target");
    }

    @Test
    @DisplayName("Turret should not target or shoot zombies obscured by smoke")
    public void testTurretDoesNotTargetObscuredZombie() {
        Zombie zombie = new Zombie(50, ZombieType.WALKER, ZombieAttackPattern.NEAREST_PLAYER, 50, 0);
        zombie.setActive(true);
        gameEntities.add(zombie);

        Turret turret = new Turret(10, 1, new Vector2(0, 0), 100, WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon());
        turret.setActive(true);
        gameEntities.add(turret);

        turret.acquireTarget(List.of(), List.of(zombie));
        assertNotNull(turret.getCurrentTarget(), "Turret should acquire visible zombie");

        zombie.setVisionObscured(true);
        turret.update(0.016);
        assertNull(turret.getCurrentTarget(), "Turret should drop target when zombie enters smoke");

        turret.acquireTarget(List.of(), List.of(zombie));
        assertNull(turret.getCurrentTarget(), "Turret should not acquire obscured zombie");
    }

    @Test
    @DisplayName("OddballBehavior AI should not target an Oddball NPC obscured by smoke")
    public void testOddballBehaviorDoesNotTargetObscuredOddball() {
        AIPlayer aiPlayer = new AIPlayer(1, "Bot", 0, 0, AIPersonality.createBalanced(), 1, 100);
        aiPlayer.setActive(true);
        gameEntities.add(aiPlayer);

        Oddball oddball = new Oddball(Oddball.Personality.RAMPAGE, 100, 100);
        oddball.setActive(true);
        gameEntities.add(oddball);

        OddballBehavior behavior = new OddballBehavior();
        behavior.generateInput(aiPlayer, gameEntities, 0.016);
        assertEquals(oddball.getId(), behavior.getTargetNpcId(), "AI should target visible oddball");

        oddball.setVisionObscured(true);
        behavior.generateInput(aiPlayer, gameEntities, 2.0); // Trigger re-eval interval (1.5s)
        assertEquals(-1, behavior.getTargetNpcId(), "AI should drop target when oddball enters smoke");
    }
}

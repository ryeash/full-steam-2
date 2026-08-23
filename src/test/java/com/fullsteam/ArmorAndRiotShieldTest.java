package com.fullsteam;

import com.fullsteam.games.BinaryGameStateSerializer;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.RuleSystem;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.games.GameManager;
import com.fullsteam.games.WeaponSystem;
import com.fullsteam.model.ArmorType;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Rules;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import tools.jackson.databind.ObjectMapper;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArmorAndRiotShieldTest extends BaseTestClass {

    @Test
    void testArmorValuesAndSpeedModifiers() {
        Player player = new Player(1, "Test", 0, 0, 1, 100);

        // Test NONE armor
        player.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.HEAL_ZONE, ArmorType.NONE);
        assertEquals(0.0, player.getArmor());
        assertEquals(0.0, player.getMaxArmor());
        assertEquals(0.0, player.armorPercent());
        double baseSpeed = Config.PLAYER_SPEED * WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon().getHandling();
        assertEquals(baseSpeed * ArmorType.NONE.getHandlingModifier(), player.getMaxSpeed(), 1e-4);

        // Test LIGHT armor (50 armor)
        player.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.HEAL_ZONE, ArmorType.LIGHT);
        assertEquals(50.0, player.getArmor());
        assertEquals(50.0, player.getMaxArmor());
        assertEquals(1.0, player.armorPercent());
        assertEquals(baseSpeed * ArmorType.LIGHT.getHandlingModifier(), player.getMaxSpeed(), 1e-4);

        // Test HEAVY armor (100 armor)
        player.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.HEAL_ZONE, ArmorType.HEAVY);
        assertEquals(100.0, player.getArmor());
        assertEquals(100.0, player.getMaxArmor());
        assertEquals(1.0, player.armorPercent());
        assertEquals(baseSpeed * ArmorType.HEAVY.getHandlingModifier(), player.getMaxSpeed(), 1e-4);
    }

    @Test
    void testArmorDamageAbsorption() {
        Player player = new Player(1, "Heavy Defender", 0, 0, 1, 100);
        player.setActive(true);
        player.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.HEAL_ZONE, ArmorType.HEAVY);

        assertEquals(100.0, player.getHealth());
        assertEquals(100.0, player.getArmor());

        // Take 40 damage - fully absorbed by armor
        boolean died = player.takeDamage(40.0);
        assertFalse(died);
        assertEquals(100.0, player.getHealth());
        assertEquals(60.0, player.getArmor());

        // Take 80 damage - 60 absorbed by armor, 20 hits health
        died = player.takeDamage(80.0);
        assertFalse(died);
        assertEquals(80.0, player.getHealth());
        assertEquals(0.0, player.getArmor());

        // Reset armor on respawn
        player.resetArmor();
        assertEquals(100.0, player.getArmor());
    }

    @Test
    void testRiotShieldFrontalBlocking() {
        Player victim = new Player(1, "Shield Bearer", 0, 0, 1, 100);
        victim.setActive(true);
        // Facing RIGHT (1, 0)
        victim.setAimDirection(new Vector2(1, 0));

        // Activate Riot Shield
        StatusEffectManager.applyRiotShield(victim, 6.0);
        assertTrue(victim.isRiotShieldActive());

        // Projectile moving LEFT (-1, 0) towards victim's front -> BLOCKED
        Vector2 frontalAttack = new Vector2(-10, 0);
        assertTrue(CollisionProcessor.isBlockedByRiotShield(victim, frontalAttack));

        // Projectile moving at 45 degrees (-cos45, -sin45) towards victim -> BLOCKED (inside 120 degree cone)
        Vector2 angledFrontalAttack = new Vector2(-1, -1);
        assertTrue(CollisionProcessor.isBlockedByRiotShield(victim, angledFrontalAttack));

        // Projectile moving RIGHT (1, 0) from behind victim -> NOT BLOCKED
        Vector2 rearAttack = new Vector2(10, 0);
        assertFalse(CollisionProcessor.isBlockedByRiotShield(victim, rearAttack));

        // Projectile moving directly UP (0, 1) perpendicular -> NOT BLOCKED (outside 120 degree cone)
        Vector2 flankAttack = new Vector2(0, 10);
        assertFalse(CollisionProcessor.isBlockedByRiotShield(victim, flankAttack));
    }

    @Test
    void testBinarySerializationOfArmorAndShield() {
        GameConfig gameConfig = GameConfig.builder()
                .maxPlayers(10)
                .teamCount(2)
                .worldWidth(2000)
                .worldHeight(2000)
                .build();
        World<Body> world = new World<>();
        GameEntities gameEntities = new GameEntities(gameConfig, world);
        RuleSystem ruleSystem = new RuleSystem("game1", gameConfig.getRules(), gameEntities, null, msg -> {}, gameConfig.getTeamCount());

        Player player = new Player(1, "Armor Player", 10, 20, 1, 100);
        player.setActive(true);
        player.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.RIOT_SHIELD, ArmorType.LIGHT);
        StatusEffectManager.applyRiotShield(player, 6.0);
        gameEntities.add(player);

        BinaryGameStateSerializer serializer = new BinaryGameStateSerializer(gameConfig, gameEntities, ruleSystem);
        byte[] bytes = serializer.serializeGameState(true);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertTrue(player.isRiotShieldActive());
        assertEquals(50.0, player.getArmor());
    }

    @Test
    void testAIArmorSelectionByArchetype() {
        var aiPlayer = com.fullsteam.ai.AIPlayerManager.createAIPlayerWithName(101, "Ironclad", com.fullsteam.ai.AIPersonality.Type.defensive, 0, 0, 1, 100);
        assertNotNull(aiPlayer.getArmorType());
        assertTrue(aiPlayer.getMaxArmor() >= 0);
    }

    @Test
    void testRiotShieldBlocksBeamWeaponsUnlessPiercing() {
        GameConfig gameConfig = GameConfig.builder().build();
        World<Body> world = new World<>();
        GameEntities gameEntities = new GameEntities(gameConfig, world);
        WeaponSystem weaponSystem = new WeaponSystem(gameEntities, world);

        // Victim at x=100, y=0, facing LEFT (-1, 0)
        Player victim = new Player(1, "Shield Bearer", 100, 0, 1, 100);
        victim.setActive(true);
        victim.setAimDirection(new Vector2(-1, 0));
        StatusEffectManager.applyRiotShield(victim, 6.0);
        gameEntities.add(victim);
        world.addBody(victim.getBody());

        // Non-piercing LASER beam fired from x=0, y=0 pointing RIGHT (1, 0) towards victim
        FieldEffectBeam nonPiercingBeam = new FieldEffectBeam(
                new Vector2(0, 0),
                new Vector2(1, 0),
                500.0,
                50.0,
                2,
                2,
                FieldEffectType.LASER,
                Set.of(),
                1.0
        );

        List<Vector2> path = weaponSystem.computeBeamPath(nonPiercingBeam);
        // Path should stop at victim's location (~100 units)
        assertTrue(path.get(path.size() - 1).x < 200.0, "Non-piercing beam should be stopped by riot shield");

        // Piercing LASER beam fired from x=0, y=0 pointing RIGHT (1, 0) towards victim
        FieldEffectBeam piercingBeam = new FieldEffectBeam(
                new Vector2(0, 0),
                new Vector2(1, 0),
                500.0,
                50.0,
                2,
                2,
                FieldEffectType.LASER,
                Set.of(BulletEffect.PIERCING),
                1.0
        );

        List<Vector2> piercingPath = weaponSystem.computeBeamPath(piercingBeam);
        // Path should extend to full range (500 units)
        assertEquals(500.0, piercingPath.get(piercingPath.size() - 1).x, 1.0, "Piercing beam should pass through riot shield");
    }

    @Test
    void testBulletEffectsTriggerOnRiotShieldImpact() {
        GameConfig gameConfig = GameConfig.builder().build();
        GameManager gameManager = new GameManager("test-shield-effects", gameConfig, new ObjectMapper());
        gameManager.shutdown();
        GameEntities gameEntities = gameManager.getGameEntities();

        Player victim = new Player(1, "Shield Bearer", 100, 0, 1, 100);
        victim.setActive(true);
        victim.setAimDirection(new Vector2(-1, 0));
        StatusEffectManager.applyRiotShield(victim, 6.0);
        gameEntities.add(victim);

        // Explosive projectile moving towards victim's frontal shield
        Projectile explosiveProjectile = new Projectile(
                2,
                new Vector2(80, 0),
                new Vector2(100, 0),
                30.0,
                500.0,
                2,
                0.0,
                Set.of(BulletEffect.EXPLOSIVE),
                com.fullsteam.model.Ordinance.PROJECTILE,
                1.0,
                0.0
        );
        gameEntities.add(explosiveProjectile);

        // Process collision with shield bearer
        gameEntities.runPostUpdateHooks();
        gameEntities.getWorld().step(1);

        // Projectile should be deactivated on shield
        assertFalse(explosiveProjectile.isActive());

        // Explosive field effect should have been spawned on impact
        boolean explosionSpawned = gameEntities.getAllFieldEffects().stream()
                .anyMatch(fe -> fe.getType() == FieldEffectType.EXPLOSION);

        assertTrue(explosionSpawned, "Explosion field effect should trigger on riot shield impact");
    }

    @Test
    void testArmorPiercingBypassesArmorAndRiotShield() {
        Player victim = new Player(1, "Armored Shield Bearer", 100, 0, 1, 100);
        victim.setActive(true);
        victim.applyWeaponConfig(WeaponConfig.ASSAULT_RIFLE_PRESET, UtilityWeapon.RIOT_SHIELD, ArmorType.HEAVY);
        victim.setAimDirection(new Vector2(-1, 0));
        StatusEffectManager.applyRiotShield(victim, 6.0);

        assertEquals(100.0, victim.getHealth());
        assertEquals(100.0, victim.getArmor());
        assertTrue(victim.isRiotShieldActive());

        // Regular damage (40) is absorbed by heavy armor
        victim.takeDamage(40.0, false);
        assertEquals(100.0, victim.getHealth());
        assertEquals(60.0, victim.getArmor());

        // Armor piercing damage (30) bypasses armor and damages health directly
        victim.takeDamage(30.0, true);
        assertEquals(70.0, victim.getHealth());
        assertEquals(60.0, victim.getArmor()); // Armor untouched
    }

    @Test
    void testRiotShieldHitpointsAndBreak() {
        Player player = new Player(1, "Shield Bearer", 0, 0, 1, 100);
        player.setActive(true);

        // Apply Riot Shield with default 150 HP
        StatusEffectManager.applyRiotShield(player, 6.0);
        assertTrue(player.isRiotShieldActive());
        assertEquals(150.0, player.getRiotShieldHealth());
        assertEquals(150.0, player.getRiotShieldMaxHealth());

        // Partial damage (50 HP)
        boolean damaged = player.damageRiotShield(50.0);
        assertTrue(damaged);
        assertTrue(player.isRiotShieldActive());
        assertEquals(100.0, player.getRiotShieldHealth());

        // Shield-breaking damage (100 HP)
        player.damageRiotShield(100.0);
        assertFalse(player.isRiotShieldActive());
        assertEquals(0.0, player.getRiotShieldHealth());
    }

    @Test
    void testRiotShieldSlowEffect() {
        Player player = new Player(1, "Shield Bearer", 0, 0, 1, 100);
        player.setActive(true);

        double defaultDamping = Config.PLAYER_LINEAR_DAMPING;
        assertEquals(defaultDamping, player.getBody().getLinearDamping(), 1e-4);

        // Apply Riot Shield
        StatusEffectManager.applyRiotShield(player, 6.0);
        assertTrue(player.isRiotShieldActive());

        // Update player to trigger attribute modifications
        player.update(0.1);
        double slowedDamping = Config.PLAYER_LINEAR_DAMPING * 2.0;
        assertEquals(slowedDamping, player.getBody().getLinearDamping(), 1e-4);

        // Breaking the shield should revert linear damping immediately back to default
        player.damageRiotShield(150.0);
        assertFalse(player.isRiotShieldActive());
        assertEquals(defaultDamping, player.getBody().getLinearDamping(), 1e-4);
    }
}

package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.physics.Player;
import com.fullsteam.util.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InvincibilityTest extends BaseTestClass {

    @Test
    void testSpawnInvincibilityApplied() {
        // Create a player
        Player player = new Player(1, "Test Player", 100, 100, 1, 100);
        
        // Apply spawn invincibility
        StatusEffectManager.applySpawnInvincibility(player);
        
        // Verify invincibility is active
        assertTrue(StatusEffectManager.isInvincible(player), "Player should be invincible after spawn");
    }

    @Test
    void testInvincibilityBlocksDamage() {
        // Create a player
        Player player = new Player(1, "Test Player", 100, 100, 1, 100);
        player.setActive(true);
        
        // Apply invincibility
        StatusEffectManager.applySpawnInvincibility(player);
        
        // Try to damage the player
        double initialHealth = player.getHealth();
        boolean died = player.takeDamage(50);
        
        // Verify no damage was taken
        assertFalse(died, "Player should not die while invincible");
        assertEquals(initialHealth, player.getHealth(), "Player health should not change while invincible");
    }

    @Test
    void testInvincibilityExpires() throws InterruptedException {
        // Create a player
        Player player = new Player(1, "Test Player", 100, 100, 1, 100);
        player.setActive(true);
        
        // Apply invincibility with short duration
        StatusEffectManager.applyInvincibility(player, 0.1, "test");
        
        // Verify invincibility is active
        assertTrue(StatusEffectManager.isInvincible(player), "Player should be invincible initially");
        
        // Wait for invincibility to expire
        Thread.sleep(150);
        
        // Update player to process expired effects
        player.update(0.016);
        
        // Verify invincibility expired
        assertFalse(StatusEffectManager.isInvincible(player), "Player should not be invincible after expiration");
        
        // Verify damage can now be taken
        double initialHealth = player.getHealth();
        player.takeDamage(30);
        assertTrue(player.getHealth() < initialHealth, "Player should take damage after invincibility expires");
    }

    @Test
    void testInvincibilityDuration() {
        // Verify the constant is set to a reasonable value
        assertTrue(GameConstants.SPAWN_INVINCIBILITY_DURATION > 0, "Spawn invincibility duration should be positive");
        assertTrue(GameConstants.SPAWN_INVINCIBILITY_DURATION <= 10, "Spawn invincibility duration should be reasonable (<=10 seconds)");
    }

    @Test
    void testInvincibilityRenderHint() {
        // Create a player
        Player player = new Player(1, "Test Player", 100, 100, 1, 100);
        
        // Apply invincibility
        StatusEffectManager.applySpawnInvincibility(player);
        
        // Verify render hint is present
        boolean hasRenderHint = player.getAttributeModifications().stream()
                .anyMatch(am -> am.renderHint().contains("Invincible"));
        
        assertTrue(hasRenderHint, "Invincibility should have a render hint for visual feedback");
    }

    @Test
    void testInvincibilityDoesNotStackWithOtherDamageModifiers() {
        // Create a player
        Player player = new Player(1, "Test Player", 100, 100, 1, 100);
        player.setActive(true);
        
        // Apply damage resistance first
        StatusEffectManager.applyDamageResistance(player, 50, 5.0, "test");
        
        // Apply invincibility (should replace damage resistance due to unique key)
        StatusEffectManager.applySpawnInvincibility(player);
        
        // Verify invincibility is active
        assertTrue(StatusEffectManager.isInvincible(player), "Player should be invincible");
        
        // Try to damage the player
        double initialHealth = player.getHealth();
        player.takeDamage(50);
        
        // Verify no damage was taken (invincibility, not just resistance)
        assertEquals(initialHealth, player.getHealth(), "Player should take no damage with invincibility");
    }
}


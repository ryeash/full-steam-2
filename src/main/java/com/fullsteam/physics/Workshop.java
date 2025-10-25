package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Workshop entity that allows players to craft power-ups by standing near it.
 * Players must remain within the craft radius for the required time to generate power-ups.
 * Extends Obstacle to inherit shape data serialization capabilities.
 */
@Getter
@Setter
public class Workshop extends Obstacle {
    private final double craftTime;
    private final int maxPowerUps;
    private final Set<Player> presentPlayers = new HashSet<>();
    private final Map<Integer, Double> playerProgress = new HashMap<>();

    public Workshop(int id, Vector2 position, double craftTime, int maxPowerUps) {
        super(id, position.x, position.y, ObstacleType.HOUSE);
        this.craftTime = craftTime;
        this.maxPowerUps = maxPowerUps;

        // Override the random shape with fixed workshop dimensions
        Body body = getBody();
        body.removeFixture(body.getFixture(0));
        Rectangle workshopRect = new Rectangle(Config.PLAYER_RADIUS * 4, Config.PLAYER_RADIUS * 2);
        BodyFixture bodyFixture = body.addFixture(workshopRect);
        bodyFixture.setSensor(true);
        body.setUserData(this);
    }

    /**
     * Add a player to the workshop range (mimic KOTH zone approach).
     */
    public void addPlayer(Player player) {
        presentPlayers.add(player);
        playerProgress.computeIfAbsent(player.getId(), v -> 0D);
    }

    public boolean incrementProgress(Player player, double deltaTime) {
        Double progress = playerProgress.computeIfPresent(player.getId(), (i, d) -> d + deltaTime);
        return progress != null && progress > craftTime;
    }

    public void removePlayer(Player player) {
        presentPlayers.remove(player);
        playerProgress.remove(player.getId());
    }

    /**
     * Get the number of active crafters (players who have started crafting).
     */
    public int getActiveCrafters() {
        return presentPlayers.size();
    }

    /**
     * Get crafting progress for all players who have started crafting (not just those currently in range).
     * This ensures progress indicators persist even if players temporarily move out of range.
     */
    public Map<Integer, Double> getAllCraftingProgress() {
        // Return a copy of all crafting progress
        Map<Integer, Double> map = new HashMap<>(playerProgress);
        map.replaceAll((id, progress) -> Math.min(1.0, (progress / craftTime)));
        return map;
    }
}

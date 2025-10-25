package com.fullsteam.ai;

import com.fullsteam.physics.Player;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.Map;

/**
 * AI memory system that tracks game state and player behavior over time.
 * This allows AI to make more informed decisions based on past observations.
 */
@Getter
public class AIMemory {
    // Track last known positions of players
    private final Map<Integer, Vector2> lastKnownPlayerPositions = new HashMap<>();
    private final Map<Integer, Long> lastSeenPlayerTimes = new HashMap<>();

    // Track location control history
    private final Map<Integer, Integer> locationControlHistory = new HashMap<>();
    private final Map<Integer, Long> locationLastChanged = new HashMap<>();

    // Track threat assessments
    private final Map<Integer, Double> playerThreatLevels = new HashMap<>();

    // Track player behavior patterns
    private final Map<Integer, PlayerBehaviorPattern> playerBehaviors = new HashMap<>();

    // Memory duration constants
    private static final long PLAYER_MEMORY_DURATION = 10000; // 10 seconds
    private static final long LOCATION_MEMORY_DURATION = 30000; // 30 seconds

    public void update(double deltaTime) {
        long currentTime = System.currentTimeMillis();

        // Clean up old memories
        lastSeenPlayerTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > PLAYER_MEMORY_DURATION);

        locationLastChanged.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > LOCATION_MEMORY_DURATION);
    }

    public void observePlayer(Player player) {
        int playerId = player.getId();
        long currentTime = System.currentTimeMillis();

        // Update last known position
        lastKnownPlayerPositions.put(playerId, player.getPosition().copy());
        lastSeenPlayerTimes.put(playerId, currentTime);

        // Update behavior pattern
        PlayerBehaviorPattern pattern = playerBehaviors.computeIfAbsent(playerId,
                k -> new PlayerBehaviorPattern());
        pattern.updateObservation(player);

        // Update threat level based on recent actions
        updateThreatLevel(playerId, player);
    }

    public Vector2 getLastKnownPosition(int playerId) {
        Vector2 pos = lastKnownPlayerPositions.get(playerId);
        return pos != null ? pos.copy() : null;
    }

    public boolean hasSeenPlayerRecently(int playerId, long withinMilliseconds) {
        Long lastSeen = lastSeenPlayerTimes.get(playerId);
        if (lastSeen == null) return false;
        return System.currentTimeMillis() - lastSeen <= withinMilliseconds;
    }

    public double getThreatLevel(int playerId) {
        return playerThreatLevels.getOrDefault(playerId, 0.5);
    }

    public PlayerBehaviorPattern getBehaviorPattern(int playerId) {
        return playerBehaviors.get(playerId);
    }

    public boolean isLocationHotlyContested(int locationId) {
        Long lastChanged = locationLastChanged.get(locationId);
        if (lastChanged == null) return false;

        // Consider a location contested if control changed recently
        return System.currentTimeMillis() - lastChanged < 30000; // 30 seconds
    }

    private void updateThreatLevel(int playerId, Player player) {
        double currentThreat = playerThreatLevels.getOrDefault(playerId, 0.5);

        // Factors that increase threat level
        if (player.getKills() > player.getDeaths()) {
            currentThreat += 0.1;
        }

        if (player.getHealth() > 80) {
            currentThreat += 0.05;
        }

        // Factors that decrease threat level
        if (player.getHealth() < 30) {
            currentThreat -= 0.1;
        }

        if (!player.isActive()) {
            currentThreat -= 0.2;
        }

        // Clamp between 0 and 1
        currentThreat = Math.max(0.0, Math.min(1.0, currentThreat));
        playerThreatLevels.put(playerId, currentThreat);
    }

    /**
     * Tracks behavior patterns of observed players.
     */
    @Getter
    public static class PlayerBehaviorPattern {
        private final Vector2 averagePosition = new Vector2(0, 0);
        private double averageSpeed = 0;
        private int observationCount = 0;
        private long lastObservationTime = 0;

        // Behavioral flags
        private boolean isAggressive = false;
        private boolean isDefensive = false;
        private boolean prefersLongRange = false;

        public void updateObservation(Player player) {
            observationCount++;
            lastObservationTime = System.currentTimeMillis();

            // Update average position
            Vector2 currentPos = player.getPosition();
            averagePosition.x = (averagePosition.x * (observationCount - 1) + currentPos.x) / observationCount;
            averagePosition.y = (averagePosition.y * (observationCount - 1) + currentPos.y) / observationCount;

            // Update average speed
            Vector2 velocity = player.getVelocity();
            double currentSpeed = velocity.getMagnitude();
            averageSpeed = (averageSpeed * (observationCount - 1) + currentSpeed) / observationCount;

            // Update behavioral flags based on observations
            updateBehavioralFlags(player);
        }

        private void updateBehavioralFlags(Player player) {
            // Simple heuristics for behavior classification
            double killDeathRatio = player.getDeaths() > 0 ?
                    (double) player.getKills() / player.getDeaths() : player.getKills();

            isAggressive = killDeathRatio > 1.5 && averageSpeed > 80;
            isDefensive = killDeathRatio < 0.8 && averageSpeed < 40;

            // Additional behavioral analysis could be added here
        }

        public boolean isStale(long maxAgeMillis) {
            return System.currentTimeMillis() - lastObservationTime > maxAgeMillis;
        }
    }
}

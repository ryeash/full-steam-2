package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.Map;

/**
 * King of the Hill zone - a circular area that awards points to the team with the most players inside.
 * Zones are sensor entities that detect player presence without physical collision.
 */
@Getter
@Setter
public class KothZone extends GameEntity {
    private static final double ZONE_RADIUS = 80.0; // Large enough for multiple players

    private final int zoneNumber; // 0-3 for up to 4 zones
    private final Vector2 homePosition; // Fixed position
    private final double pointsPerSecond; // Points awarded per second for controlling this zone

    // Control tracking
    private int controllingTeam = -1; // -1 = contested/neutral, 0+ = team number
    private ZoneState state = ZoneState.NEUTRAL;
    private double captureProgress = 0.0; // 0.0 to 1.0

    /**
     * -- GETTER --
     * Get the players currently in the zone.
     */
    // Player tracking
    private Map<Integer, Integer> playersInZone = new HashMap<>(); // playerId -> teamNumber
    
    // Team score tracking (persistent across frames)
    private final Map<Integer, Double> teamScores = new HashMap<>(); // teamNumber -> total points earned

    public KothZone(int id, int zoneNumber, double x, double y, double pointsPerSecond) {
        super(id, createZoneBody(x, y), Double.POSITIVE_INFINITY); // Zones are indestructible
        this.zoneNumber = zoneNumber;
        this.homePosition = new Vector2(x, y);
        this.pointsPerSecond = pointsPerSecond;
    }

    private static Body createZoneBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(ZONE_RADIUS);
        var fixture = body.addFixture(circle);
        fixture.setSensor(true); // Zones don't collide physically, they're sensors
        body.setMass(MassType.INFINITE); // Zones don't move
        body.getTransform().setTranslation(x, y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        // Update capture progress and state based on players in zone
        if (playersInZone.isEmpty()) {
            // No players - zone is neutral or stays with current controller
            if (state == ZoneState.CAPTURING) {
                // Decay capture progress when zone is empty
                captureProgress = Math.max(0, captureProgress - deltaTime * 0.5);
                if (captureProgress <= 0) {
                    state = ZoneState.NEUTRAL;
                    controllingTeam = -1;
                }
            }
            return;
        }

        // Count players per team
        Map<Integer, Integer> teamCounts = new HashMap<>();
        for (int team : playersInZone.values()) {
            teamCounts.put(team, teamCounts.getOrDefault(team, 0) + 1);
        }

        // Determine dominant team
        int dominantTeam = -1;
        int maxCount = 0;
        boolean contested = false;

        for (Map.Entry<Integer, Integer> entry : teamCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                dominantTeam = entry.getKey();
                maxCount = entry.getValue();
                contested = false;
            } else if (entry.getValue() == maxCount) {
                contested = true; // Multiple teams with same count
            }
        }

        if (contested) {
            // Zone is contested - no progress
            state = ZoneState.CONTESTED;
            // Slowly decay capture progress during contest
            captureProgress = Math.max(0, captureProgress - deltaTime * 0.2);
        } else if (dominantTeam == controllingTeam) {
            // Same team controlling - zone is controlled
            state = ZoneState.CONTROLLED;
            captureProgress = 1.0;
        } else {
            // Different team trying to capture
            state = ZoneState.CAPTURING;

            if (controllingTeam == -1) {
                // Capturing from neutral - faster
                captureProgress += deltaTime * 0.5; // 2 seconds to capture from neutral
            } else {
                // Capturing from enemy - slower
                captureProgress += deltaTime * 0.25; // 4 seconds to capture from enemy
            }

            if (captureProgress >= 1.0) {
                // Capture complete!
                captureProgress = 1.0;
                controllingTeam = dominantTeam;
                state = ZoneState.CONTROLLED;
            }
        }
    }

    /**
     * Add a player to the zone.
     */
    public void addPlayer(int playerId, int teamNumber) {
        playersInZone.put(playerId, teamNumber);
    }

    /**
     * Remove a player from the zone.
     */
    public void removePlayer(int playerId) {
        playersInZone.remove(playerId);
    }

    /**
     * Clear all players from the zone (e.g., at round end).
     */
    public void clearPlayers() {
        playersInZone.clear();
    }

    /**
     * Check if the zone should award points.
     * Only awards points when fully controlled (not contested or capturing).
     */
    public boolean shouldAwardPoints() {
        return state == ZoneState.CONTROLLED && controllingTeam >= 0;
    }

    /**
     * Get the players currently in the zone.
     */
    public Map<Integer, Integer> getPlayersInZone() {
        return playersInZone;
    }

    /**
     * Get the number of players from a specific team in the zone.
     */
    public int getTeamPlayerCount(int team) {
        return (int) playersInZone.values().stream()
                .filter(t -> t == team)
                .count();
    }

    /**
     * Get total player count in zone.
     */
    public int getTotalPlayerCount() {
        return playersInZone.size();
    }

    /**
     * Reset the zone to neutral state (e.g., at round start).
     */
    /**
     * Award points to a team for controlling this zone.
     * @param team The team number to award points to
     * @param points The number of points to award
     */
    public void awardPointsToTeam(int team, double points) {
        teamScores.put(team, teamScores.getOrDefault(team, 0.0) + points);
    }
    
    /**
     * Get the total points earned by a team from this zone.
     * @param team The team number
     * @return The total points earned by this team
     */
    public double getTeamScore(int team) {
        return teamScores.getOrDefault(team, 0.0);
    }
    
    /**
     * Get all team scores for this zone.
     * @return A map of team number to total points earned
     */
    public Map<Integer, Double> getAllTeamScores() {
        return new HashMap<>(teamScores);
    }
    
    /**
     * Reset all team scores for this zone.
     */
    public void resetTeamScores() {
        teamScores.clear();
    }

    public void reset() {
        controllingTeam = -1;
        state = ZoneState.NEUTRAL;
        captureProgress = 0.0;
        playersInZone.clear();
    }

    /**
     * Zone state enum
     */
    public enum ZoneState {
        NEUTRAL,      // No team controls the zone
        CAPTURING,    // A team is capturing the zone
        CONTROLLED,   // A team fully controls the zone
        CONTESTED     // Multiple teams fighting for control
    }
}

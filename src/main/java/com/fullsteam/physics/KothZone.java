package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /**
     * -- GETTER --
     * Get the players currently in the zone.
     */
    // Player tracking
    private Set<Player> playersInZone = new HashSet<>();
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
        if (playersInZone.isEmpty()) {
            state = ZoneState.NEUTRAL;
            controllingTeam = -1;
            return;
        }
        long teamCount = playersInZone.stream().map(Player::getTeam).distinct().count();
        boolean contested = teamCount > 1;
        if (contested) {
            // Zone is contested - no team gets points
            state = ZoneState.CONTESTED;
            controllingTeam = -1;
        } else {
            // One team has majority - they control the zone immediately
            state = ZoneState.CONTROLLED;
            controllingTeam = playersInZone.iterator().next().getTeam();
        }
    }

    /**
     * Add a player to the zone.
     */
    public void addPlayer(Player player) {
        playersInZone.add(player);
    }

    /**
     * Clear all players from the zone (e.g., at round end).
     */
    public void clearPlayers() {
        playersInZone.clear();
    }

    /**
     * Check if the zone should award points.
     * Awards points when a team has majority control (not contested).
     */
    public boolean shouldAwardPoints() {
        return state == ZoneState.CONTROLLED && controllingTeam >= 0;
    }

    /**
     * Get the number of players from a specific team in the zone.
     */
    public int getTeamPlayerCount(int team) {
        return (int) playersInZone.stream()
                .filter(t -> t.getTeam() == team)
                .count();
    }

    /**
     * Get total player count in zone.
     */
    public int getTotalPlayerCount() {
        return playersInZone.size();
    }

    /**
     * Get capture progress (1.0 = fully controlled, 0.0 = neutral/contested).
     * Since zones are controlled immediately when a team has majority, this returns 1.0 for controlled zones.
     */
    public double getCaptureProgress() {
        return state == ZoneState.CONTROLLED ? 1.0 : 0.0;
    }

    /**
     * Award points to a team for controlling this zone.
     *
     * @param team   The team number to award points to
     * @param points The number of points to award
     */
    public void awardPointsToTeam(int team, double points) {
        teamScores.put(team, teamScores.getOrDefault(team, 0.0) + points);
    }

    /**
     * Get the total points earned by a team from this zone.
     *
     * @param team The team number
     * @return The total points earned by this team
     */
    public double getTeamScore(int team) {
        return teamScores.getOrDefault(team, 0.0);
    }

    /**
     * Get all team scores for this zone.
     *
     * @return A map of team number to total points earned
     */
    public Map<Integer, Double> getAllTeamScores() {
        return new HashMap<>(teamScores);
    }

    /**
     * Zone state enum
     */
    public enum ZoneState {
        NEUTRAL,      // No team controls the zone
        CONTROLLED,   // A team controls the zone (has majority)
        CONTESTED     // Multiple teams fighting for control (tied)
    }
}

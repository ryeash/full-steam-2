package com.fullsteam.physics;

import com.fullsteam.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.HashSet;
import java.util.Set;

/**
 * King of the Hill zone - a circular area that awards points to the team with the most players inside.
 * Zones are sensor entities that detect player presence without physical collision.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KothZone extends GameEntity {
    /**
     * Zone state enum
     */
    public enum ZoneState {
        NEUTRAL,      // No team controls the zone
        CONTROLLED,   // A team controls the zone (has majority)
        CONTESTED     // Multiple teams fighting for control (tied)
    }

    private static final double ZONE_RADIUS = Config.PLAYER_RADIUS * 6; // Large enough for multiple players

    private final int zoneNumber; // 0-3 for up to 4 zones
    private final Vector2 homePosition; // Fixed position
    private final double pointsPerSecond; // Points awarded per second for controlling this zone

    // Control tracking
    private int controllingTeam = -1;   // -1 = contested/neutral, 1+ = team number (team mode)
    private int controllingPlayerId = -1; // used in FFA mode instead of controllingTeam
    private ZoneState state = ZoneState.NEUTRAL;

    // Player tracking
    private Set<Player> playersInZone = new HashSet<>();

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
            controllingPlayerId = -1;
            return;
        }

        // FFA detection: all players share team 0 in free-for-all mode.
        boolean isFfa = playersInZone.stream().allMatch(p -> p.getTeam() == 0);

        if (isFfa) {
            // In FFA each player is their own unit — sole occupant controls the zone.
            controllingTeam = -1;
            if (playersInZone.size() == 1) {
                state = ZoneState.CONTROLLED;
                controllingPlayerId = playersInZone.iterator().next().getId();
            } else {
                state = ZoneState.CONTESTED;
                controllingPlayerId = -1;
            }
        } else {
            controllingPlayerId = -1;
            long distinctTeams = playersInZone.stream().map(Player::getTeam).distinct().count();
            if (distinctTeams > 1) {
                state = ZoneState.CONTESTED;
                controllingTeam = -1;
            } else {
                state = ZoneState.CONTROLLED;
                controllingTeam = playersInZone.iterator().next().getTeam();
            }
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
     * Awards points when a single entity (team or player) controls the zone uncontested.
     */
    public boolean shouldAwardPoints() {
        return state == ZoneState.CONTROLLED && (controllingTeam >= 0 || controllingPlayerId >= 0);
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
}

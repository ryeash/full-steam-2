package com.fullsteam.physics;

import lombok.Getter;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages team spawn areas and base locations.
 * Divides the game field into equal sections for team-based gameplay.
 */
@Getter
public class TeamSpawnManager {
    private static final Logger log = LoggerFactory.getLogger(TeamSpawnManager.class);
    
    private final double worldWidth;
    private final double worldHeight;
    private final int teamCount;
    private final Map<Integer, TeamSpawnArea> teamAreas = new HashMap<>();
    
    public TeamSpawnManager(double worldWidth, double worldHeight, int teamCount) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.teamCount = teamCount;
        
        if (teamCount >= 2 && teamCount <= 4) {
            createTeamAreas();
        }
    }
    
    /**
     * Create team spawn areas by dividing the field into equal sections.
     */
    private void createTeamAreas() {
        switch (teamCount) {
            case 2:
                createTwoTeamAreas();
                break;
            case 3:
                createThreeTeamAreas();
                break;
            case 4:
                createFourTeamAreas();
                break;
            default:
                log.warn("Unsupported team count: {}", teamCount);
        }
        
        log.info("Created {} team spawn areas for {}x{} world", 
            teamAreas.size(), worldWidth, worldHeight);
    }
    
    /**
     * Two teams: Split field vertically (left vs right).
     */
    private void createTwoTeamAreas() {
        double areaWidth = worldWidth / 2.0;
        double areaHeight = worldHeight;
        
        // Team 1: Left side
        Vector2 team1Center = new Vector2(-worldWidth / 4.0, 0);
        teamAreas.put(1, new TeamSpawnArea(1, team1Center, areaWidth, areaHeight));
        
        // Team 2: Right side  
        Vector2 team2Center = new Vector2(worldWidth / 4.0, 0);
        teamAreas.put(2, new TeamSpawnArea(2, team2Center, areaWidth, areaHeight));
    }
    
    /**
     * Three teams: One at top, two at bottom (triangular layout).
     */
    private void createThreeTeamAreas() {
        double fullWidth = worldWidth;
        double halfHeight = worldHeight / 2.0;
        
        // Team 1: Top center
        Vector2 team1Center = new Vector2(0, worldHeight / 4.0);
        teamAreas.put(1, new TeamSpawnArea(1, team1Center, fullWidth * 0.8, halfHeight));
        
        // Team 2: Bottom left
        Vector2 team2Center = new Vector2(-worldWidth / 4.0, -worldHeight / 4.0);
        teamAreas.put(2, new TeamSpawnArea(2, team2Center, fullWidth / 2.0, halfHeight));
        
        // Team 3: Bottom right
        Vector2 team3Center = new Vector2(worldWidth / 4.0, -worldHeight / 4.0);
        teamAreas.put(3, new TeamSpawnArea(3, team3Center, fullWidth / 2.0, halfHeight));
    }
    
    /**
     * Four teams: Split field into quadrants.
     */
    private void createFourTeamAreas() {
        double areaWidth = worldWidth / 2.0;
        double areaHeight = worldHeight / 2.0;
        
        // Team 1: Top-left quadrant
        Vector2 team1Center = new Vector2(-worldWidth / 4.0, worldHeight / 4.0);
        teamAreas.put(1, new TeamSpawnArea(1, team1Center, areaWidth, areaHeight));
        
        // Team 2: Top-right quadrant
        Vector2 team2Center = new Vector2(worldWidth / 4.0, worldHeight / 4.0);
        teamAreas.put(2, new TeamSpawnArea(2, team2Center, areaWidth, areaHeight));
        
        // Team 3: Bottom-left quadrant
        Vector2 team3Center = new Vector2(-worldWidth / 4.0, -worldHeight / 4.0);
        teamAreas.put(3, new TeamSpawnArea(3, team3Center, areaWidth, areaHeight));
        
        // Team 4: Bottom-right quadrant
        Vector2 team4Center = new Vector2(worldWidth / 4.0, -worldHeight / 4.0);
        teamAreas.put(4, new TeamSpawnArea(4, team4Center, areaWidth, areaHeight));
    }
    
    /**
     * Get a spawn point for a specific team.
     * 
     * @param teamNumber Team number (1-4)
     * @return Spawn point within team's area, or center if invalid team
     */
    public Vector2 getTeamSpawnPoint(int teamNumber) {
        TeamSpawnArea area = teamAreas.get(teamNumber);
        if (area != null) {
            return area.generateSpawnPoint();
        }
        
        log.warn("No spawn area found for team {}, using world center", teamNumber);
        return new Vector2(0, 0);
    }
    
    /**
     * Get a safe spawn point for a specific team, avoiding other players.
     * 
     * @param teamNumber Team number
     * @param existingPlayers Collection of existing players to avoid
     * @param minDistance Minimum distance from other players
     * @return Safe spawn point within team's area
     */
    public Vector2 getSafeTeamSpawnPoint(int teamNumber, java.util.Collection<Player> existingPlayers, double minDistance) {
        TeamSpawnArea area = teamAreas.get(teamNumber);
        if (area != null) {
            return area.findSafeSpawnPoint(existingPlayers, minDistance);
        }
        
        return getTeamSpawnPoint(teamNumber);
    }
    
    /**
     * Get the team spawn area for a specific team.
     * 
     * @param teamNumber Team number
     * @return TeamSpawnArea or null if not found
     */
    public TeamSpawnArea getTeamArea(int teamNumber) {
        return teamAreas.get(teamNumber);
    }
    
    /**
     * Check if team-based spawning is enabled (2+ teams).
     * 
     * @return true if team spawning is active
     */
    public boolean isTeamSpawningEnabled() {
        return teamCount >= 2 && !teamAreas.isEmpty();
    }
    
    /**
     * Get a random spawn point for FFA mode (anywhere on the map).
     * 
     * @return Random spawn point
     */
    public Vector2 getFFASpawnPoint() {
        // Use a slightly smaller area to avoid edge spawns
        double spawnWidth = worldWidth * 0.8;
        double spawnHeight = worldHeight * 0.8;
        
        double x = (Math.random() - 0.5) * spawnWidth;
        double y = (Math.random() - 0.5) * spawnHeight;
        
        return new Vector2(x, y);
    }
    
    /**
     * Get a safe FFA spawn point avoiding other players.
     * 
     * @param existingPlayers Collection of existing players to avoid
     * @param minDistance Minimum distance from other players
     * @return Safe spawn point for FFA mode
     */
    public Vector2 getSafeFFASpawnPoint(java.util.Collection<Player> existingPlayers, double minDistance) {
        for (int attempts = 0; attempts < 20; attempts++) {
            Vector2 candidate = getFFASpawnPoint();
            
            boolean isSafe = true;
            for (Player player : existingPlayers) {
                if (player.isActive() && candidate.distance(player.getPosition()) < minDistance) {
                    isSafe = false;
                    break;
                }
            }
            
            if (isSafe) {
                return candidate;
            }
        }
        
        // If no safe spot found, return random point
        return getFFASpawnPoint();
    }
    
    /**
     * Get all team area information for client display.
     * 
     * @return Map of team areas with their bounds and centers
     */
    public Map<String, Object> getTeamAreaInfo() {
        Map<String, Object> info = new HashMap<>();
        Map<Integer, Map<String, Object>> areas = new HashMap<>();
        
        for (Map.Entry<Integer, TeamSpawnArea> entry : teamAreas.entrySet()) {
            TeamSpawnArea area = entry.getValue();
            Map<String, Object> areaInfo = new HashMap<>();
            
            areaInfo.put("teamNumber", area.getTeamNumber());
            areaInfo.put("centerX", area.getCenter().x);
            areaInfo.put("centerY", area.getCenter().y);
            areaInfo.put("width", area.getWidth());
            areaInfo.put("height", area.getHeight());
            areaInfo.put("minX", area.getMinBounds().x);
            areaInfo.put("minY", area.getMinBounds().y);
            areaInfo.put("maxX", area.getMaxBounds().x);
            areaInfo.put("maxY", area.getMaxBounds().y);
            
            // Future base features
            areaInfo.put("hasVehicleGarage", area.isHasVehicleGarage());
            areaInfo.put("hasSupplyDepot", area.isHasSupplyDepot());
            areaInfo.put("hasDefenseTurrets", area.isHasDefenseTurrets());
            
            areas.put(entry.getKey(), areaInfo);
        }
        
        info.put("teamAreas", areas);
        info.put("teamCount", teamCount);
        info.put("worldWidth", worldWidth);
        info.put("worldHeight", worldHeight);
        
        return info;
    }
}

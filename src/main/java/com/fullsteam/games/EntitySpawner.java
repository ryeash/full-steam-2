package com.fullsteam.games;

import com.fullsteam.physics.*;
import com.fullsteam.util.GameConstants;
import com.fullsteam.util.IdGenerator;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fullsteam.model.Rules;

/**
 * Handles creation and spawning of all game entities.
 * Responsible for world boundaries, obstacles, flags, zones, workshops, and headquarters.
 */
public class EntitySpawner {
    private static final Logger log = LoggerFactory.getLogger(EntitySpawner.class);

    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final World<Body> world;
    private final TeamSpawnManager teamSpawnManager;
    private final TerrainGenerator terrainGenerator;
    private final String gameId;

    public EntitySpawner(String gameId, GameConfig gameConfig, GameEntities gameEntities,
                        World<Body> world, TeamSpawnManager teamSpawnManager,
                        TerrainGenerator terrainGenerator) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.world = world;
        this.teamSpawnManager = teamSpawnManager;
        this.terrainGenerator = terrainGenerator;
    }

    /**
     * Create world boundaries (walls).
     */
    public void createWorldBoundaries() {
        double halfWidth = gameConfig.getWorldWidth() / 2.0;
        double halfHeight = gameConfig.getWorldHeight() / 2.0;
        double wallThickness = GameConstants.WORLD_BOUNDARY_THICKNESS;

        Body topWall = new Body();
        topWall.addFixture(new Rectangle(gameConfig.getWorldWidth() + wallThickness * 2, wallThickness));
        topWall.setMass(MassType.INFINITE);
        topWall.getTransform().setTranslation(0, halfHeight + wallThickness / 2.0);
        topWall.setUserData("boundary");
        world.addBody(topWall);

        Body bottomWall = new Body();
        bottomWall.addFixture(new Rectangle(gameConfig.getWorldWidth() + wallThickness * 2, wallThickness));
        bottomWall.setMass(MassType.INFINITE);
        bottomWall.getTransform().setTranslation(0, -halfHeight - wallThickness / 2.0);
        bottomWall.setUserData("boundary");
        world.addBody(bottomWall);

        Body leftWall = new Body();
        leftWall.addFixture(new Rectangle(wallThickness, gameConfig.getWorldHeight()));
        leftWall.setMass(MassType.INFINITE);
        leftWall.getTransform().setTranslation(-halfWidth - wallThickness / 2.0, 0);
        leftWall.setUserData("boundary");
        world.addBody(leftWall);

        Body rightWall = new Body();
        rightWall.addFixture(new Rectangle(wallThickness, gameConfig.getWorldHeight()));
        rightWall.setMass(MassType.INFINITE);
        rightWall.getTransform().setTranslation(halfWidth + wallThickness / 2.0, 0);
        rightWall.setUserData("boundary");
        world.addBody(rightWall);
    }

    /**
     * Create obstacles from terrain generator.
     */
    public void createObstacles() {
        // Use procedurally generated simple obstacles from terrain generator
        for (Obstacle obstacle : terrainGenerator.getGeneratedObstacles()) {
            gameEntities.addObstacle(obstacle);
            world.addBody(obstacle.getBody());
        }
    }

    /**
     * Create flags for capture-the-flag gameplay if configured.
     */
    public void createFlags() {
        if (!gameConfig.getRules().hasFlags()) {
            return; // No flags configured
        }

        if (gameConfig.isFreeForAll()) {
            log.warn("Flags are not supported in FFA mode");
            return;
        }

        int flagsPerTeam = gameConfig.getRules().getFlagsPerTeam();
        int teamCount = gameConfig.getTeamCount();

        log.info("Creating {} flags per team for {} teams", flagsPerTeam, teamCount);

        int flagId = 1;
        for (int team = 1; team <= teamCount; team++) {
            TeamSpawnArea teamArea = teamSpawnManager.getTeamArea(team);
            if (teamArea == null) {
                log.warn("No spawn area found for team {}, skipping flag creation", team);
                continue;
            }

            // Create flags for this team
            for (int i = 0; i < flagsPerTeam; i++) {
                Vector2 flagPosition = calculateFlagPosition(teamArea, i, flagsPerTeam);

                // Ensure flag position is clear of obstacles
                if (!terrainGenerator.isPositionClear(flagPosition, 30.0)) {
                    // Try alternative positions
                    for (int attempt = 0; attempt < 5; attempt++) {
                        flagPosition = teamArea.generateSpawnPoint();
                        if (terrainGenerator.isPositionClear(flagPosition, 30.0)) {
                            break;
                        }
                    }
                }

                Flag flag = new Flag(flagId++, team, flagPosition.x, flagPosition.y);
                gameEntities.addFlag(flag);
                world.addBody(flag.getBody());

                log.info("Created flag {} for team {} at position ({}, {})",
                        flag.getId(), team, flagPosition.x, flagPosition.y);
            }
        }
    }

    /**
     * Calculate flag position within a team's area.
     * For multiple flags, distributes them around the team center.
     */
    private Vector2 calculateFlagPosition(TeamSpawnArea teamArea, int flagIndex, int totalFlags) {
        Vector2 center = teamArea.getCenter();

        if (totalFlags == 1) {
            // Single flag: place at team center
            return center.copy();
        }

        // Multiple flags: distribute in a circle around center
        double radius = Math.min(teamArea.getWidth(), teamArea.getHeight()) * 0.3;
        double angleStep = (2 * Math.PI) / totalFlags;
        double angle = flagIndex * angleStep;

        double x = center.x + radius * Math.cos(angle);
        double y = center.y + radius * Math.sin(angle);

        return new Vector2(x, y);
    }

    /**
     * Create the oddball if oddball mode is enabled.
     * The oddball is a neutral flag (team 0) spawned at the world center.
     */
    public void createOddball() {
        if (!gameConfig.getRules().hasOddball()) {
            return; // Oddball not enabled
        }

        log.info("Creating oddball at world center for game {}", gameId);

        // Create oddball at world center (team 0 = neutral)
        Vector2 centerPosition = new Vector2(0, 0);

        // Ensure position is clear of obstacles
        if (!terrainGenerator.isPositionClear(centerPosition, 30.0)) {
            // Try to find a nearby clear position
            for (int attempt = 0; attempt < 10; attempt++) {
                double offsetX = (Math.random() - 0.5) * 200;
                double offsetY = (Math.random() - 0.5) * 200;
                Vector2 candidate = new Vector2(offsetX, offsetY);

                if (terrainGenerator.isPositionClear(candidate, 30.0)) {
                    centerPosition = candidate;
                    break;
                }
            }
        }

        // Use flag ID 9999 for oddball to distinguish it from regular flags
        Flag oddball = new Flag(9999, 0, centerPosition.x, centerPosition.y);
        gameEntities.addFlag(oddball);
        world.addBody(oddball.getBody());

        log.info("Created oddball at position ({}, {})", centerPosition.x, centerPosition.y);
    }

    /**
     * Create King of the Hill zones if enabled in rules.
     * Zones are placed strategically between team spawn areas for fair gameplay.
     */
    public void createKothZones() {
        Rules rules = gameConfig.getRules();
        if (!rules.hasKothZones() || !gameConfig.isTeamMode()) {
            return; // KOTH disabled
        }

        int zoneCount = rules.getKothZones();
        int teamCount = gameConfig.getTeamCount();

        log.info("Creating {} KOTH zones for game {}", zoneCount, gameId);

        int zoneId = IdGenerator.nextEntityId();

        // Calculate zone positions based on number of zones and teams
        for (int i = 0; i < zoneCount; i++) {
            Vector2 zonePosition = calculateKothZonePosition(i, zoneCount, teamCount);

            // Ensure zone position is clear of obstacles
            if (!terrainGenerator.isPositionClear(zonePosition, GameConstants.SPAWN_CLEARANCE_RADIUS)) {
                // Try to find a nearby clear position
                for (int attempt = 0; attempt < 10; attempt++) {
                    double offsetX = (Math.random() - 0.5) * 200;
                    double offsetY = (Math.random() - 0.5) * 200;
                    Vector2 candidate = new Vector2(zonePosition.x + offsetX, zonePosition.y + offsetY);

                    if (terrainGenerator.isPositionClear(candidate, GameConstants.SPAWN_CLEARANCE_RADIUS)) {
                        zonePosition = candidate;
                        break;
                    }
                }
            }

            KothZone zone = new KothZone(zoneId++, i, zonePosition.x, zonePosition.y, gameConfig.getRules().getKothPointsPerSecond());
            gameEntities.addKothZone(zone);
            world.addBody(zone.getBody());

            log.info("Created KOTH zone {} at position ({}, {})", i, zonePosition.x, zonePosition.y);
        }
    }

    /**
     * Calculate fair positioning for KOTH zones.
     * Zones are placed equidistant from team spawn areas to ensure no team has an advantage.
     */
    private Vector2 calculateKothZonePosition(int zoneIndex, int totalZones, int teamCount) {
        double worldWidth = gameConfig.getWorldWidth();
        double worldHeight = gameConfig.getWorldHeight();

        if (totalZones == 1) {
            // Single zone: place at world center
            return new Vector2(0, 0);
        }
        // Team mode: place zones in neutral space between team spawn areas
        if (teamCount == 2) {
            // aligned on the y-axis to avoid all team zones
            double increment = worldHeight / (totalZones + 1);
            double y = increment * (zoneIndex + 1) - (worldHeight / 2);
            return new Vector2(0, y);
        } else if (teamCount == 3) {
            // aligned on the x-axis to avoid all team zones
            double increment = worldWidth / (totalZones + 1);
            double x = increment * (zoneIndex + 1) - (worldWidth / 2);
            return new Vector2(x, 0);
        } else { // 4 teams
            // align on the longer axis
            if (worldHeight > worldWidth) {
                // aligned on the y-axis to avoid all team zones
                double increment = worldHeight / (totalZones + 1);
                double y = increment * (zoneIndex + 1) - (worldHeight / 2);
                return new Vector2(0, y);
            } else {
                // aligned on the x-axis to avoid all team zones
                double increment = worldWidth / (totalZones + 1);
                double x = increment * (zoneIndex + 1) - (worldWidth / 2);
                return new Vector2(x, 0);
            }
        }
    }

    /**
     * Create workshops if enabled in rules.
     * Each team gets one workshop placed in their spawn zone.
     */
    public void createWorkshops() {
        Rules rules = gameConfig.getRules();
        if (!rules.hasWorkshops() || !gameConfig.isTeamMode()) {
            return; // Workshops disabled or not in team mode
        }
        int teamCount = gameConfig.getTeamCount();
        for (int teamNumber = 1; teamNumber <= teamCount; teamNumber++) {
            TeamSpawnArea teamArea = teamSpawnManager.getTeamAreas().get(teamNumber);
            if (teamArea == null) {
                log.warn("No spawn area found for team {}, skipping workshop creation", teamNumber);
                continue;
            }
            Vector2 workshopPosition = teamArea.getCenter().copy();
            double offsetX = (Math.random() - 0.5) * 50; // ±25 units
            double offsetY = (Math.random() - 0.5) * 50; // ±25 units
            workshopPosition.add(offsetX, offsetY);

            // Ensure workshop position is clear of obstacles
            if (!terrainGenerator.isPositionClear(workshopPosition, 100.0)) {
                // Try to find a nearby clear position within the team area
                for (int attempt = 0; attempt < 10; attempt++) {
                    double randomX = teamArea.getMinBounds().x + Math.random() *
                            (teamArea.getMaxBounds().x - teamArea.getMinBounds().x);
                    double randomY = teamArea.getMinBounds().y + Math.random() *
                            (teamArea.getMaxBounds().y - teamArea.getMinBounds().y);
                    Vector2 candidate = new Vector2(randomX, randomY);

                    if (terrainGenerator.isPositionClear(candidate, 100.0)) {
                        workshopPosition = candidate;
                        break;
                    }
                }
            }

            Workshop workshop = new Workshop(
                    IdGenerator.nextEntityId(),
                    workshopPosition,
                    rules.getWorkshopCraftTime(),
                    rules.getMaxPowerUpsPerWorkshop()
            );
            gameEntities.addWorkshop(workshop);
            world.addBody(workshop.getBody());
        }
    }

    /**
     * Create headquarters if enabled in rules.
     * Each team gets one headquarters placed in their spawn zone (defensive position).
     */
    public void createHeadquarters() {
        Rules rules = gameConfig.getRules();
        if (!rules.hasHeadquarters() || !gameConfig.isTeamMode()) {
            return; // Headquarters disabled or not in team mode
        }
        int teamCount = gameConfig.getTeamCount();
        // Create one headquarters per team in their spawn zone
        for (int teamNumber = 1; teamNumber <= teamCount; teamNumber++) {
            TeamSpawnArea teamArea = teamSpawnManager.getTeamAreas().get(teamNumber);
            if (teamArea == null) {
                log.warn("No spawn area found for team {}, skipping HQ creation", teamNumber);
                continue;
            }

            // Place HQ at the back of team's spawn area (defensive position)
            Vector2 hqPosition = teamArea.getCenter().copy();

            // Offset towards the back of the spawn area (away from center of map)
            Vector2 mapCenter = new Vector2(0, 0);
            Vector2 awayFromCenter = hqPosition.copy().subtract(mapCenter);
            if (awayFromCenter.getMagnitude() > 0) {
                awayFromCenter.normalize();
                // Move 150 units further back
                hqPosition.add(awayFromCenter.multiply(150.0));
            }

            // Ensure HQ position is clear of obstacles (HQ needs more clearance due to size)
            double hqClearanceRadius = 100.0; // HQ is 80x60, so need larger clearance
            if (!terrainGenerator.isPositionClear(hqPosition, hqClearanceRadius)) {
                // Try to find a nearby clear position within the team area
                boolean foundClearPosition = false;
                for (int attempt = 0; attempt < 20; attempt++) {
                    double offsetX = (Math.random() - 0.5) * 300;
                    double offsetY = (Math.random() - 0.5) * 300;
                    Vector2 candidate = new Vector2(hqPosition.x + offsetX, hqPosition.y + offsetY);

                    if (terrainGenerator.isPositionClear(candidate, hqClearanceRadius)) {
                        hqPosition = candidate;
                        foundClearPosition = true;
                        break;
                    }
                }

                // If still no clear position found, try anywhere in team area
                if (!foundClearPosition) {
                    for (int attempt = 0; attempt < 30; attempt++) {
                        Vector2 candidate = teamArea.generateSpawnPoint();
                        if (terrainGenerator.isPositionClear(candidate, hqClearanceRadius)) {
                            hqPosition = candidate;
                            foundClearPosition = true;
                            break;
                        }
                    }
                }

                if (!foundClearPosition) {
                    log.warn("Could not find clear position for team {} headquarters after many attempts. Placing anyway.", teamNumber);
                }
            }

            Headquarters hq = new Headquarters(
                    IdGenerator.nextEntityId(),
                    teamNumber,
                    hqPosition.x,
                    hqPosition.y,
                    rules.getHeadquartersMaxHealth()
            );
            gameEntities.addHeadquarters(hq);
            world.addBody(hq.getBody());
        }
    }
}


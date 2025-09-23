package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.ai.AIGameHelper;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AIPlayerManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.BulletEffectProcessor;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.CompoundObstacle;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.StrategicLocation;
import com.fullsteam.physics.TeamSpawnArea;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.terrain.TerrainGenerator;
import io.micronaut.websocket.WebSocketSession;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.dynamics.TimeStep;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.PhysicsWorld;
import org.dyn4j.world.World;
import org.dyn4j.world.listener.StepListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameManager implements CollisionProcessor.CollisionHandler, StepListener<Body> {
    protected static final Logger log = LoggerFactory.getLogger(GameManager.class);

    @Getter
    protected final String gameId;
    @Getter
    protected final String gameType;
    @Getter
    protected final GameConfig gameConfig;
    @Getter
    protected final GameEntities gameEntities = new GameEntities();
    @Getter
    protected final AIPlayerManager aiPlayerManager = new AIPlayerManager();
    @Getter
    protected final TeamSpawnManager teamSpawnManager;
    @Getter
    protected final TerrainGenerator terrainGenerator;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * -- GETTER --
     * Get the game start time.
     */
    @Getter
    protected long gameStartTime;
    protected boolean gameRunning = false;

    // AI management settings - initialized from gameConfig
    private long aiCheckIntervalMs;
    private long lastAICheckTime = 0;

    private final World<Body> world;
    private final Queue<Body> bodiesToAdd = new ConcurrentLinkedQueue<>();
    private final Queue<Body> bodiesToRemove = new ConcurrentLinkedQueue<>();
    private final ScheduledFuture<?> shutdownHook;
    private double lastUpdateTime = System.nanoTime() / 1e9;

    public GameManager(String gameId, String gameType, GameConfig gameConfig) {
        this.gameId = gameId;
        this.gameType = gameType;
        this.gameConfig = gameConfig;
        this.gameStartTime = System.currentTimeMillis();

        // Initialize AI management settings from config
        this.aiCheckIntervalMs = gameConfig.getAiCheckIntervalMs();

        // Initialize team spawn manager
        this.teamSpawnManager = new TeamSpawnManager(
                gameConfig.getWorldWidth(),
                gameConfig.getWorldHeight(),
                gameConfig.getTeamCount()
        );

        // Initialize procedural terrain generator
        this.terrainGenerator = new TerrainGenerator(
                gameConfig.getWorldWidth(),
                gameConfig.getWorldHeight()
        );

        this.world = new World<>();
        Settings settings = new Settings();
        settings.setMaximumTranslation(300.0);
        this.world.setSettings(settings);
        this.world.setGravity(new Vector2(0, 0));
        this.world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));

        CollisionProcessor collisionProcessor = new CollisionProcessor(this.gameEntities, this);
        this.world.addCollisionListener(collisionProcessor);
        this.world.addStepListener(this);

        createWorldBoundaries();
//        createStrategicLocations();
        createObstacles();

        // Add initial AI players to make the game more interesting from the start
        int initialAICount = getMaxPlayers();
        int added = AIGameHelper.addMixedAIPlayers(this, initialAICount);
        if (added > 0) {
            log.info("Added {} initial AI players to game {} for better gameplay", added, gameId);
        }

        this.shutdownHook = Config.EXECUTOR.scheduleAtFixedRate(this::update, 0, 16, TimeUnit.MILLISECONDS);
    }

    public boolean addPlayer(PlayerSession playerSession) {
        if (gameEntities.getPlayerSessions().size() >= getMaxPlayers()) {
            return false;
        }
        gameEntities.addPlayerSession(playerSession);
        onPlayerJoined(playerSession);
        return true;
    }

    public void removePlayer(int playerId) {
        PlayerSession removed = gameEntities.removePlayerSession(playerId);
        if (removed != null) {
            onPlayerLeft(removed);
        }
    }

    public void acceptPlayerInput(int playerId, PlayerInput input) {
        if (input != null) {
            gameEntities.getPlayerInputs().put(playerId, input);
        }
    }

    public void handlePlayerConfigChange(int playerId, PlayerConfigRequest request) {
        PlayerSession playerSession = gameEntities.getPlayerSession(playerId);
        if (playerSession != null) {
            if (request.getPlayerName() != null) {
                playerSession.setPlayerName(request.getPlayerName());
            }
            processPlayerConfigChange(playerSession, request);
        }
    }

    public void send(WebSocketSession session, Object message) {
        try {
            if (session.isWritable() && session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendSync(json);
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializing message", e);
        }
    }

    public void broadcast(Object message) {
        gameEntities.getPlayerSessions().values().forEach(player -> {
            if (player.getSession().isOpen()) {
                send(player.getSession(), message);
            }
        });
    }

    public GameInfo getGameInfo() {
        return new GameInfo(
                gameId,
                gameType,
                gameEntities.getPlayerSessions().size(),
                getMaxPlayers(),
                gameStartTime,
                gameRunning ? "running" : "waiting"
        );
    }

    public int getPlayerCount() {
        return gameEntities.getPlayerSessions().size();
    }

    public void shutdown() {
        shutdownHook.cancel(true);
    }

    /**
     * Add an AI player to the game with a random personality.
     */
    public boolean addAIPlayer() {
        if (gameEntities.getAllPlayers().size() >= getMaxPlayers()) {
            return false;
        }

        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = findSpawnPointForTeam(assignedTeam);
        AIPlayer aiPlayer = AIPlayerManager.createRandomAIPlayer(Config.nextId(), spawnPoint.x, spawnPoint.y, assignedTeam);

        // Add to game entities
        gameEntities.addPlayer(aiPlayer);
        bodiesToAdd.add(aiPlayer.getBody());

        // Add to AI manager
        aiPlayerManager.addAIPlayer(aiPlayer);

        log.info("Added AI player {} ({}) with personality {} on team {} at spawn point ({}, {})",
                aiPlayer.getId(), aiPlayer.getPlayerName(), aiPlayer.getPersonality().getPersonalityType(),
                assignedTeam, spawnPoint.x, spawnPoint.y);

        return true;
    }

    /**
     * Add an AI player with a specific personality type.
     */
    public boolean addAIPlayer(String personalityType) {
        if (gameEntities.getAllPlayers().size() >= getMaxPlayers()) {
            return false;
        }

        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = findSpawnPointForTeam(assignedTeam);
        AIPlayer aiPlayer = AIPlayerManager.createAIPlayerWithPersonality(Config.nextId(), spawnPoint.x, spawnPoint.y, personalityType, assignedTeam);

        // Add to game entities
        gameEntities.addPlayer(aiPlayer);
        bodiesToAdd.add(aiPlayer.getBody());

        // Add to AI manager
        aiPlayerManager.addAIPlayer(aiPlayer);

        log.info("Added AI player {} ({}) with {} personality on team {} at spawn point ({}, {})",
                aiPlayer.getId(), aiPlayer.getPlayerName(), personalityType,
                assignedTeam, spawnPoint.x, spawnPoint.y);

        return true;
    }

    /**
     * Remove an AI player from the game.
     */
    public void removeAIPlayer(int playerId) {
        if (aiPlayerManager.isAIPlayer(playerId)) {
            Player player = gameEntities.getPlayer(playerId);
            if (player != null) {
                bodiesToRemove.add(player.getBody());
                gameEntities.removePlayer(playerId);
            }
            aiPlayerManager.removeAIPlayer(playerId);
            log.info("Removed AI player {}", playerId);
        }
    }

    /**
     * Get the number of AI players in the game.
     */
    public int getAIPlayerCount() {
        int count = 0;
        for (Player player : gameEntities.getAllPlayers()) {
            if (aiPlayerManager.isAIPlayer(player.getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if a player is an AI player.
     */
    public boolean isAIPlayer(int playerId) {
        return aiPlayerManager.isAIPlayer(playerId);
    }

    /**
     * Check if the game has any human players currently.
     */
    public boolean hasHumanPlayers() {
        return getPlayerCount() > 0;
    }

    /**
     * Assign a player to the team with the fewest members.
     *
     * @return Team number (0 for FFA, 1+ for team modes)
     */
    private int assignPlayerToTeam() {
        if (gameConfig.isFreeForAll()) {
            return 0; // FFA mode
        }

        // Count players on each team
        int[] teamCounts = new int[gameConfig.getTeamCount() + 1]; // +1 for index alignment
        for (Player player : gameEntities.getAllPlayers()) {
            int team = player.getTeam();
            if (team > 0 && team <= gameConfig.getTeamCount()) {
                teamCounts[team]++;
            }
        }

        // Find team with fewest players
        int bestTeam = 1;
        int minCount = teamCounts[1];
        for (int team = 2; team <= gameConfig.getTeamCount(); team++) {
            if (teamCounts[team] < minCount) {
                minCount = teamCounts[team];
                bestTeam = team;
            }
        }

        // Debug logging for team assignment
        log.info("Team assignment - Game has {} teams. Team counts: {}", 
                gameConfig.getTeamCount(), 
                java.util.Arrays.toString(teamCounts));
        log.info("Assigning player to team {} (counts: T1={}, T2={}, T3={}, T4={})", 
                bestTeam, 
                teamCounts.length > 1 ? teamCounts[1] : 0,
                teamCounts.length > 2 ? teamCounts[2] : 0, 
                teamCounts.length > 3 ? teamCounts[3] : 0,
                teamCounts.length > 4 ? teamCounts[4] : 0);

        return bestTeam;
    }

    /**
     * Get team counts for all teams.
     *
     * @return Map of team number to player count
     */
    public Map<Integer, Integer> getTeamCounts() {
        Map<Integer, Integer> teamCounts = new HashMap<>();

        for (Player player : gameEntities.getAllPlayers()) {
            int team = player.getTeam();
            teamCounts.put(team, teamCounts.getOrDefault(team, 0) + 1);
        }

        return teamCounts;
    }

    /**
     * Manually trigger AI player adjustment based on current settings.
     */
    public void adjustAIPlayers() {
        int totalPlayers = gameEntities.getAllPlayers().size();
        int humanPlayers = totalPlayers - getAIPlayerCount();

        // Calculate target player count
        // If we have very few human players, fill up to minimum
        if (totalPlayers < getMaxPlayers()) {
            int aiToAdd = getMaxPlayers() - totalPlayers;
            int added = AIGameHelper.addMixedAIPlayers(this, aiToAdd);
            if (added > 0) {
                log.info("Auto-filled {} AI players to reach minimum activity level (total: {})", added, totalPlayers + added);
            }
        }
        // If we have too many AI players compared to humans, remove some
        else if (humanPlayers > 0 && totalPlayers > getMaxPlayers()) {
            int aiToRemove = totalPlayers - getMaxPlayers();
            int removed = removeExcessAIPlayers(aiToRemove);
            if (removed > 0) {
                log.info("Removed {} excess AI players (total remaining: {})",
                        removed, totalPlayers - removed);
            }
        }
    }

    /**
     * Remove a specified number of AI players, prioritizing idle ones.
     */
    private int removeExcessAIPlayers(int count) {
        int removed = 0;
        List<Integer> aiPlayerIds = new ArrayList<>();

        // Collect all AI player IDs
        for (Player player : gameEntities.getAllPlayers()) {
            if (isAIPlayer(player.getId())) {
                aiPlayerIds.add(player.getId());
            }
        }

        // Remove AI players, up to the requested count
        for (int i = 0; i < Math.min(count, aiPlayerIds.size()); i++) {
            removeAIPlayer(aiPlayerIds.get(i));
            removed++;
        }

        return removed;
    }

    /**
     * Periodically check and adjust AI player count based on current game state.
     */
    private void checkAndAdjustAIPlayers() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAICheckTime >= aiCheckIntervalMs) {
            adjustAIPlayers();
            lastAICheckTime = currentTime;
        }
    }

    protected void update() {
        try {
            double currentTime = System.nanoTime() / 1e9;
            double deltaTime = currentTime - lastUpdateTime;
            lastUpdateTime = currentTime;

            // Update AI players and generate their inputs
            aiPlayerManager.update(gameEntities, deltaTime);

            // Add AI inputs to the main input queue
            aiPlayerManager.getAllPlayerInputs().forEach((playerId, input) -> {
                gameEntities.getPlayerInputs().put(playerId, input);
            });

            // Periodically check and adjust AI player count
            checkAndAdjustAIPlayers();

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);

            gameEntities.updateAll(deltaTime);
            gameEntities.getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                if (!projectile.isActive()) {
                    // Check if projectile should trigger effects on dismissal
                    if (projectile.shouldTriggerEffectsOnDismissal()) {
                        triggerProjectileDismissalEffects(projectile);
                    }
                    bodiesToRemove.add(projectile.getBody());
                    return true;
                }
                return false;
            });

            updateStrategicLocations(deltaTime);

            synchronized (world) {
                Body bodyToAdd;
                while ((bodyToAdd = bodiesToAdd.poll()) != null) {
                    world.addBody(bodyToAdd);
                }

                Body bodyToRemove;
                while ((bodyToRemove = bodiesToRemove.poll()) != null) {
                    world.removeBody(bodyToRemove);
                }

                world.updatev(deltaTime);
            }

            gameEntities.getFieldEffects().values().removeIf(FieldEffect::isExpired);

            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    protected void onPlayerJoined(PlayerSession playerSession) {
        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = findSpawnPointForTeam(assignedTeam);
        log.info("Player {} joining game {} at spawn point ({}, {}) on team {}",
                playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y, assignedTeam);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y, assignedTeam);
        gameEntities.addPlayer(player);
        bodiesToAdd.add(player.getBody());

        send(playerSession.getSession(), createInitialGameState());
        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());

        // Adjust AI players when a human player joins
        adjustAIPlayers();
    }

    protected void onPlayerLeft(PlayerSession playerSession) {
        gameEntities.getPlayerInputs().remove(playerSession.getPlayerId());
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            bodiesToRemove.add(player.getBody());
            gameEntities.removePlayer(player.getId());

            // Remove from AI manager if it's an AI player
            if (aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
                aiPlayerManager.removeAIPlayer(playerSession.getPlayerId());
            }
        }
        log.info("Player {} left game {}", playerSession.getPlayerId(), gameId);

        // Adjust AI players when a human player leaves
        if (!aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
            adjustAIPlayers();
        }
    }

    protected void processPlayerInput(Integer playerId, PlayerInput input) {
        Player player = gameEntities.getPlayer(playerId);
        if (player != null && input != null) {
            player.processInput(input);
            if (input.isLeft()) {
                for (Projectile projectile : player.shoot()) {
                    if (projectile != null) {
                        gameEntities.addProjectile(projectile);
                        bodiesToAdd.add(projectile.getBody());
                    }
                }
            }
        }
    }

    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            if (StringUtils.isNotEmpty(request.getPlayerName())) {
                player.setPlayerName(StringUtils.abbreviate(request.getPlayerName(), 26));
            }

            // Handle new unified weapon config or legacy separate weapons
            if (request.getWeaponConfig() != null) {
                // New unified weapon config - use for both primary and secondary
                log.info("Applying custom weapon config for player {}: {}", player.getPlayerName(), request.getWeaponConfig().type);
                player.applyWeaponConfig(request.getWeaponConfig(), request.getWeaponConfig());
            } else {
                // Legacy support for separate primary/secondary weapons
                log.info("Applying legacy weapon config for player {}", player.getPlayerName());
                player.applyWeaponConfig(request.getPrimaryWeapon(), request.getSecondaryWeapon());
            }
        }
    }

    public int getMaxPlayers() {
        return gameConfig.getMaxPlayers();
    }

    private void createWorldBoundaries() {
        double halfWidth = gameConfig.getWorldWidth() / 2.0;
        double halfHeight = gameConfig.getWorldHeight() / 2.0;
        double wallThickness = 50.0;

        Body topWall = new Body();
        topWall.addFixture(new Rectangle(gameConfig.getWorldWidth() + wallThickness * 2, wallThickness));
        topWall.setMass(MassType.INFINITE);
        topWall.getTransform().setTranslation(0, halfHeight + wallThickness / 2.0);
        topWall.setUserData("boundary");
        bodiesToAdd.add(topWall);

        Body bottomWall = new Body();
        bottomWall.addFixture(new Rectangle(gameConfig.getWorldWidth() + wallThickness * 2, wallThickness));
        bottomWall.setMass(MassType.INFINITE);
        bottomWall.getTransform().setTranslation(0, -halfHeight - wallThickness / 2.0);
        bottomWall.setUserData("boundary");
        bodiesToAdd.add(bottomWall);

        Body leftWall = new Body();
        leftWall.addFixture(new Rectangle(wallThickness, gameConfig.getWorldHeight()));
        leftWall.setMass(MassType.INFINITE);
        leftWall.getTransform().setTranslation(-halfWidth - wallThickness / 2.0, 0);
        leftWall.setUserData("boundary");
        bodiesToAdd.add(leftWall);

        Body rightWall = new Body();
        rightWall.addFixture(new Rectangle(wallThickness, gameConfig.getWorldHeight()));
        rightWall.setMass(MassType.INFINITE);
        rightWall.getTransform().setTranslation(halfWidth + wallThickness / 2.0, 0);
        rightWall.setUserData("boundary");
        bodiesToAdd.add(rightWall);
    }

    private void createStrategicLocations() {
        String[] locationNames = {"Alpha", "Beta", "Gamma", "Delta", "Echo"};
        for (int i = 0; i < gameConfig.getStrategicLocationsCount(); i++) {
            Vector2 locationPosition = findSafeLocationPosition();

            StrategicLocation location = new StrategicLocation(locationNames[i], locationPosition.x, locationPosition.y);
            gameEntities.addStrategicLocation(location);
            bodiesToAdd.add(location.getBody());
        }
    }

    /**
     * Find a safe position for strategic locations that avoids obstacles.
     */
    private Vector2 findSafeLocationPosition() {
        for (int attempts = 0; attempts < 20; attempts++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldWidth() - 200);
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldHeight() - 200);
            Vector2 candidate = new Vector2(x, y);

            // Check if position is clear of terrain obstacles (larger radius for strategic locations)
            if (terrainGenerator.isPositionClear(candidate, 80.0)) {
                // Also check against existing strategic locations
                boolean tooCloseToOther = false;
                for (StrategicLocation existing : gameEntities.getAllStrategicLocations()) {
                    if (candidate.distance(existing.getPosition()) < 150.0) {
                        tooCloseToOther = true;
                        break;
                    }
                }

                if (!tooCloseToOther) {
                    return candidate;
                }
            }
        }

        // Fallback to terrain generator's safe spawn method
        return terrainGenerator.getSafeSpawnPosition(80.0);
    }

    private void createObstacles() {
        // Use procedurally generated simple obstacles from terrain generator
        for (Obstacle obstacle : terrainGenerator.getGeneratedObstacles()) {
            gameEntities.addObstacle(obstacle);
            bodiesToAdd.add(obstacle.getBody());
        }

        // Add compound obstacles (complex multi-body structures)
        for (CompoundObstacle compoundObstacle : terrainGenerator.getCompoundObstacles()) {
            // Add all bodies from the compound obstacle to the physics world
            bodiesToAdd.addAll(compoundObstacle.getBodies());
        }

        log.info("Created {} simple obstacles and {} compound structures for {} terrain",
                terrainGenerator.getGeneratedObstacles().size(),
                terrainGenerator.getCompoundObstacles().size(),
                terrainGenerator.getTerrainType().getDisplayName());
    }

    private void updateStrategicLocations(double deltaTime) {
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            location.update(deltaTime);

            Set<Integer> playersInRange = new HashSet<>();
            for (Player player : gameEntities.getAllPlayers()) {
                if (player.isActive() && location.isPlayerInRange(player.getPosition())) {
                    playersInRange.add(player.getId());
                }
            }

            if (playersInRange.size() == 1) {
                Integer playerId = playersInRange.iterator().next();
                if (!location.isControlledBy(playerId)) {
                    location.startCapture(playerId);
                    location.updateCapture(deltaTime);
                }
            } else {
                location.stopCapture();
            }
        }
    }

    private void sendGameState() {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("type", "gameState");
        gameState.put("timestamp", System.currentTimeMillis());

        List<Map<String, Object>> playerStates = new ArrayList<>();
        for (Player player : gameEntities.getAllPlayers()) {
            Vector2 pos = player.getPosition();
            Map<String, Object> playerState = new HashMap<>();
            playerState.put("id", player.getId());
            playerState.put("name", player.getPlayerName());
            playerState.put("team", player.getTeam());
            playerState.put("x", pos.x);
            playerState.put("y", pos.y);
            playerState.put("rotation", player.getRotation());
            playerState.put("health", player.getHealth());
            playerState.put("active", player.isActive());
            playerState.put("weapon", player.getCurrentWeaponIndex());
            playerState.put("ammo", player.getCurrentWeapon().getCurrentAmmo());
            playerState.put("maxAmmo", player.getCurrentWeapon().getMagazineSize());
            playerState.put("reloading", player.isReloading());
            playerState.put("kills", player.getKills());
            playerState.put("deaths", player.getDeaths());
            playerState.put("respawnTime", player.getRespawnTime());
            playerStates.add(playerState);
        }
        gameState.put("players", playerStates);

        List<Map<String, Object>> projectileStates = new ArrayList<>();
        for (Projectile projectile : gameEntities.getAllProjectiles()) {
            Vector2 pos = projectile.getPosition();
            Vector2 vel = projectile.getBody().getLinearVelocity();
            Map<String, Object> projState = new HashMap<>();
            projState.put("id", projectile.getId());
            projState.put("x", pos.x);
            projState.put("y", pos.y);
            projState.put("vx", vel.x);
            projState.put("vy", vel.y);
            projState.put("ownerId", projectile.getOwnerId());
            projState.put("ownerTeam", projectile.getOwnerTeam());
            projState.put("ordinance", projectile.getOrdinance().name());

            // Convert bullet effects to string list for JSON serialization
            List<String> effectNames = projectile.getBulletEffects().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            projState.put("bulletEffects", effectNames);

            projectileStates.add(projState);
        }
        gameState.put("projectiles", projectileStates);

        List<Map<String, Object>> locationStates = new ArrayList<>();
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            Vector2 pos = location.getPosition();
            Map<String, Object> locState = new HashMap<>();
            locState.put("id", location.getId());
            locState.put("name", location.getLocationName());
            locState.put("x", pos.x);
            locState.put("y", pos.y);
            locState.put("controllingPlayer", location.getControllingPlayerId());
            locState.put("captureProgress", location.getCaptureProgress());
            locState.put("capturingPlayer", location.getCapturingPlayerId());
            locationStates.add(locState);
        }
        gameState.put("locations", locationStates);

        List<Map<String, Object>> obstacleStates = new ArrayList<>();
        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            Vector2 pos = obstacle.getPosition();
            Map<String, Object> obsState = new HashMap<>();
            obsState.put("id", obstacle.getId());
            obsState.put("x", pos.x);
            obsState.put("y", pos.y);
            obsState.put("type", obstacle.getType().name());
            obsState.put("shapeCategory", obstacle.getShapeCategory().name());
            obsState.put("boundingRadius", obstacle.getBoundingRadius());
            obsState.put("rotation", obstacle.getBody().getTransform().getRotation().toRadians());

            // Add detailed shape data for client rendering
            obsState.putAll(obstacle.getShapeData());

            obstacleStates.add(obsState);
        }
        gameState.put("obstacles", obstacleStates);

        // Add field effects to game state
        List<Map<String, Object>> fieldEffectStates = new ArrayList<>();
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            Vector2 pos = effect.getPosition();
            Map<String, Object> effectState = new HashMap<>();
            effectState.put("id", effect.getId());
            effectState.put("type", effect.getType().name());
            effectState.put("x", pos.x);
            effectState.put("y", pos.y);
            effectState.put("radius", effect.getRadius());
            effectState.put("damage", effect.getDamage());
            effectState.put("duration", effect.getDuration());
            effectState.put("timeRemaining", effect.getTimeRemaining());
            effectState.put("progress", effect.getProgress());
            effectState.put("active", effect.isActive());
            effectState.put("ownerTeam", effect.getOwnerTeam());
            fieldEffectStates.add(effectState);
        }
        gameState.put("fieldEffects", fieldEffectStates);

        broadcast(gameState);
    }

    /**
     * Find a spawn point for a specific team.
     * Uses team-based spawn areas if team mode is enabled, otherwise FFA spawning.
     *
     * @param team Team number (0 for FFA)
     * @return Spawn point for the team
     */
    private Vector2 findSpawnPointForTeam(int team) {
        if (gameConfig.isFreeForAll() || team == 0) {
            return findFFASpawnPoint();
        }

        if (teamSpawnManager.isTeamSpawningEnabled()) {
            // Try to get a team spawn point that avoids obstacles
            Vector2 teamSpawnPoint = teamSpawnManager.getSafeTeamSpawnPoint(team, gameEntities.getAllPlayers(), 100.0);

            // Verify it's clear of terrain obstacles using TerrainGenerator
            if (terrainGenerator.isPositionClear(teamSpawnPoint, 50.0)) {
                return teamSpawnPoint;
            }

            // If team spawn point is blocked, try to find a safe position near the team area
            TeamSpawnArea teamArea = teamSpawnManager.getTeamArea(team);
            if (teamArea != null) {
                for (int attempts = 0; attempts < 10; attempts++) {
                    Vector2 candidate = teamArea.generateSpawnPoint();
                    if (terrainGenerator.isPositionClear(candidate, 50.0)) {
                        return candidate;
                    }
                }
            }
        }

        // Fallback to FFA spawning
        return findFFASpawnPoint();
    }

    /**
     * Find a safe spawn point for Free For All mode.
     *
     * @return FFA spawn point
     */
    private Vector2 findFFASpawnPoint() {
        if (teamSpawnManager != null) {
            Vector2 ffaSpawnPoint = teamSpawnManager.getSafeFFASpawnPoint(gameEntities.getAllPlayers(), 100.0);

            // Verify it's clear of terrain obstacles
            if (terrainGenerator.isPositionClear(ffaSpawnPoint, 50.0)) {
                return ffaSpawnPoint;
            }
        }

        // Use terrain generator's safe spawn position method
        Vector2 terrainSafeSpawn = terrainGenerator.getSafeSpawnPosition(50.0);

        // Double-check it's not too close to existing players
        for (Player player : gameEntities.getAllPlayers()) {
            if (player.isActive() && terrainSafeSpawn.distance(player.getPosition()) < 100.0) {
                // Try legacy spawn as final fallback
                return findLegacySpawnPoint();
            }
        }

        return terrainSafeSpawn;
    }

    /**
     * Legacy spawn point logic for backward compatibility.
     *
     * @return Legacy spawn point
     */
    private Vector2 findLegacySpawnPoint() {
        for (int attempts = 0; attempts < 10; attempts++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldWidth() - 100);
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * (gameConfig.getWorldHeight() - 100);
            Vector2 candidate = new Vector2(x, y);

            boolean tooClose = false;
            for (Player other : gameEntities.getAllPlayers()) {
                if (other.getPosition().distance(candidate) < 100) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                return candidate;
            }
        }

        return new Vector2(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * gameConfig.getWorldWidth() * 0.8,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * gameConfig.getWorldHeight() * 0.8);
    }

    private Map<String, Object> createInitialGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "initialState");
        state.put("worldWidth", gameConfig.getWorldWidth());
        state.put("worldHeight", gameConfig.getWorldHeight());
        state.put("teamCount", gameConfig.getTeamCount());
        state.put("teamMode", gameConfig.isTeamMode());

        // Add team spawn area information
        if (teamSpawnManager.isTeamSpawningEnabled()) {
            state.put("teamAreas", teamSpawnManager.getTeamAreaInfo());
        }

        // Add procedural terrain data
        state.put("terrain", terrainGenerator.getTerrainData());

        List<Map<String, Object>> locations = new ArrayList<>();
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            Vector2 pos = location.getPosition();
            Map<String, Object> locData = new HashMap<>();
            locData.put("id", location.getId());
            locData.put("name", location.getLocationName());
            locData.put("x", pos.x);
            locData.put("y", pos.y);
            locData.put("radius", gameConfig.getCaptureRadius());
            locations.add(locData);
        }
        state.put("locations", locations);

        List<Map<String, Object>> obstacles = new ArrayList<>();
        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            Vector2 pos = obstacle.getPosition();
            Map<String, Object> obsData = new HashMap<>();
            obsData.put("id", obstacle.getId());
            obsData.put("x", pos.x);
            obsData.put("y", pos.y);
            obsData.put("type", obstacle.getType().name());
            obsData.put("shapeCategory", obstacle.getShapeCategory().name());
            obsData.put("boundingRadius", obstacle.getBoundingRadius());
            obsData.put("rotation", obstacle.getBody().getTransform().getRotation().toRadians());

            // Add detailed shape data for client rendering
            obsData.putAll(obstacle.getShapeData());

            obstacles.add(obsData);
        }
        state.put("obstacles", obstacles);

        return state;
    }

    @Override
    public void onPlayerHitByProjectile(Player player, Projectile projectile) {
        player.takeDamage(projectile.getDamage());
        projectile.setActive(false);

        log.info("Player {} hit by projectile from player {} for {} damage", player.getId(), projectile.getOwnerId(), projectile.getDamage());

        if (!player.isActive()) {
            Player killer = gameEntities.getPlayer(projectile.getOwnerId());
            if (killer != null) {
                killer.addKill();
            }
            player.die();

            Map<String, Object> deathNotification = new HashMap<>();
            deathNotification.put("type", "playerKilled");
            deathNotification.put("victimId", player.getId());
            deathNotification.put("killerId", projectile.getOwnerId());
            broadcast(deathNotification);

            log.info("Player {} was killed by player {}", player.getId(), projectile.getOwnerId());
        }
    }

    @Override
    public void onPlayerEnterLocation(Player player, StrategicLocation location) {
        log.debug("Player {} entered strategic location {}", player.getId(), location.getLocationName());
    }

    @Override
    public void onPlayerStayInLocation(Player player, StrategicLocation location) {
    }

    @Override
    public void onPlayerExitLocation(Player player, StrategicLocation location) {
        log.debug("Player {} exited strategic location {}", player.getId(), location.getLocationName());
    }

    /**
     * Process field effects and apply damage to players in range
     */
    private void processFieldEffects(double deltaTime) {
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            if (!effect.isActive()) continue;

            // Apply damage to players in range
            for (Player player : gameEntities.getAllPlayers()) {
                if (!player.isActive()) continue;
                if (!effect.canAffect(player)) continue;

                double damage = effect.getDamageAtPosition(player.getPosition());
                if (damage > 0) {
                    // Apply damage based on effect type
                    if (effect.getType().isInstantaneous()) {
                        // Instant damage (explosions)
                        player.takeDamage(damage);
                        effect.markAsAffected(player);
                    } else {
                        // Damage over time (fire, electric, etc.)
                        player.takeDamage(damage * deltaTime);

                        // Apply special effects
                        switch (effect.getType()) {
                            case FREEZE:
                                // TODO: Apply slowing effect
                                break;
                            case FIRE:
                            case ELECTRIC:
                            case POISON:
                                // Damage already applied above
                                break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Apply homing behavior to projectiles with homing effect
     */
    private void processHomingProjectiles(double deltaTime) {
        CollisionProcessor collisionProcessor = getCollisionProcessor();
        if (collisionProcessor != null) {
            for (Projectile projectile : gameEntities.getAllProjectiles()) {
                if (projectile.isActive()) {
                    collisionProcessor.getBulletEffectProcessor().applyHomingBehavior(projectile, deltaTime);
                }
            }
        }
    }

    /**
     * Trigger effects when a projectile is dismissed due to low velocity.
     * This handles explosions, electric discharges, and other effects.
     */
    private void triggerProjectileDismissalEffects(Projectile projectile) {
        CollisionProcessor collisionProcessor = getCollisionProcessor();
        if (collisionProcessor == null) {
            return;
        }

        Vector2 position = projectile.getPosition();
        BulletEffectProcessor effectProcessor = collisionProcessor.getBulletEffectProcessor();

        // Mark projectile as exploded to prevent duplicate effects
        projectile.markAsExploded();

        // Trigger effects based on ordinance type and bullet effects
        switch (projectile.getOrdinance()) {
            case ROCKET:
            case GRENADE:
                // These always create explosions when dismissed by velocity
                effectProcessor.createExplosion(projectile, position);
                break;

            case PLASMA:
                if (projectile.hasBulletEffect(BulletEffect.ELECTRIC)) {
                    effectProcessor.createElectricEffect(projectile, position);
                }
                break;

            case FLAMETHROWER:
                if (projectile.hasBulletEffect(BulletEffect.INCENDIARY)) {
                    effectProcessor.createFireEffect(projectile, position);
                }
                break;

            default:
                // Handle special bullet effects for other projectile types
                if (projectile.hasBulletEffect(BulletEffect.EXPLODES_ON_IMPACT)) {
                    effectProcessor.createExplosion(projectile, position);
                } else if (projectile.hasBulletEffect(BulletEffect.ELECTRIC)) {
                    effectProcessor.createElectricEffect(projectile, position);
                } else if (projectile.hasBulletEffect(BulletEffect.INCENDIARY)) {
                    effectProcessor.createFireEffect(projectile, position);
                } else if (projectile.hasBulletEffect(BulletEffect.FREEZING)) {
                    effectProcessor.createFreezeEffect(projectile, position);
                }
                break;
        }
    }

    /**
     * Add pending field effects and projectiles from bullet effect processing
     */
    private void processPendingEffects() {
        CollisionProcessor collisionProcessor = getCollisionProcessor();
        if (collisionProcessor != null) {
            BulletEffectProcessor effectProcessor = collisionProcessor.getBulletEffectProcessor();

            // Add pending field effects
            for (FieldEffect effect : effectProcessor.getPendingFieldEffects()) {
                gameEntities.addFieldEffect(effect);
            }

            // Add pending projectiles (from fragmentation, etc.)
            for (Projectile projectile : effectProcessor.getPendingProjectiles()) {
                gameEntities.addProjectile(projectile);
                bodiesToAdd.add(projectile.getBody());
            }
        }
    }

    /**
     * Process physics body additions and removals
     */
    private void processPhysicsBodies() {
        // Add new bodies
        while (!bodiesToAdd.isEmpty()) {
            Body body = bodiesToAdd.poll();
            if (body != null) {
                world.addBody(body);
            }
        }

        // Remove old bodies
        while (!bodiesToRemove.isEmpty()) {
            Body body = bodiesToRemove.poll();
            if (body != null) {
                world.removeBody(body);
            }
        }
    }

    /**
     * Get the collision processor from the world's collision listeners
     */
    private CollisionProcessor getCollisionProcessor() {
        // This is a bit of a hack, but we need access to the collision processor
        // In a real implementation, we'd store a reference to it
        return world.getCollisionListeners().stream()
                .filter(listener -> listener instanceof CollisionProcessor)
                .map(listener -> (CollisionProcessor) listener)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void begin(TimeStep step, PhysicsWorld<Body, ?> world) {

    }

    @Override
    public void updatePerformed(TimeStep step, PhysicsWorld<Body, ?> world) {

    }

    @Override
    public void postSolve(TimeStep step, PhysicsWorld<Body, ?> world) {

    }

    @Override
    public void end(TimeStep step, PhysicsWorld<Body, ?> world) {
        double deltaTime = step.getDeltaTime();

        // Process field effects and apply damage
        processFieldEffects(deltaTime);

        // Apply homing behavior to projectiles
        processHomingProjectiles(deltaTime);

        // Add pending field effects and projectiles from bullet effects
        processPendingEffects();

        // Add/remove bodies from physics world
        processPhysicsBodies();

//        for (Projectile projectile : gameEntities.getAllProjectiles()) {
//            if (projectile.isActive()) {
//                Vector2 position = projectile.getPosition();
//                Vector2 velocity = projectile.getBody().getLinearVelocity();
//                log.info(String.format("Projectile %d - Position: (%.2f, %.2f), Velocity: (%.2f, %.2f), Speed: %.2f",
//                        projectile.getId(),
//                        position.x,
//                        position.y,
//                        velocity.x,
//                        velocity.y,
//                        velocity.getMagnitude()));
//            }
//        }
    }
}

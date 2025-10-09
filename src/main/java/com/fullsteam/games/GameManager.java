package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.ai.AIGameHelper;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AIPlayerManager;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.Rules;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnArea;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.TeleportPad;
import com.fullsteam.physics.Turret;
import io.micronaut.websocket.WebSocketSession;
import lombok.Getter;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.DetectFilter;
import org.dyn4j.world.World;
import org.dyn4j.world.result.RaycastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameManager {
    protected static final Logger log = LoggerFactory.getLogger(GameManager.class);

    @Getter
    protected final String gameId;
    @Getter
    protected final GameConfig gameConfig;
    @Getter
    protected final GameEntities gameEntities;
    @Getter
    protected final AIPlayerManager aiPlayerManager;
    @Getter
    protected final TeamSpawnManager teamSpawnManager;
    @Getter
    protected final TerrainGenerator terrainGenerator;
    @Getter
    protected final GameEventManager gameEventManager;
    @Getter
    protected final RuleSystem ruleSystem;
    @Getter
    protected final WeaponSystem weaponSystem;
    @Getter
    protected final UtilitySystem utilitySystem;

    protected final ObjectMapper objectMapper;

    @Getter
    protected long gameStartTime;
    protected boolean gameRunning = false;
    private final long aiCheckIntervalMs;
    private long lastAICheckTime = 0;
    private boolean aiAdjustmentEnabled = true;
    private final World<Body> world;
    private final ScheduledFuture<?> shutdownHook;
    private double lastUpdateTime = System.nanoTime() / 1e9;

    public GameManager(String gameId, GameConfig gameConfig, ObjectMapper objectMapper) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.objectMapper = objectMapper;
        this.gameStartTime = System.currentTimeMillis();
        this.aiPlayerManager = new AIPlayerManager(gameConfig);

        // Initialize AI management settings from config
        this.aiCheckIntervalMs = gameConfig.getAiCheckIntervalMs();
        this.teamSpawnManager = new TeamSpawnManager(gameConfig.getWorldWidth(), gameConfig.getWorldHeight(), gameConfig.getTeamCount());
        this.terrainGenerator = new TerrainGenerator(gameConfig.getWorldWidth(), gameConfig.getWorldHeight());

        this.world = new World<>();

        Settings settings = new Settings();
        settings.setMaximumTranslation(300.0);
        this.world.setSettings(settings);
        this.world.setGravity(new Vector2(0, 0));
        this.world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));

        this.gameEntities = new GameEntities(gameConfig, world);
        CollisionProcessor collisionProcessor = new CollisionProcessor(this, this.gameEntities);
        this.world.addCollisionListener(collisionProcessor);
        this.world.addContactListener(collisionProcessor);

        // Initialize game event manager
        this.gameEventManager = new GameEventManager(gameEntities, this::send);

        // Initialize rule system
        this.ruleSystem = new RuleSystem(
                gameId,
                gameConfig.getRules(),
                gameEntities,
                gameEventManager,
                this::broadcast,
                gameConfig.getTeamCount()
        );

        // Initialize weapon system
        this.weaponSystem = new WeaponSystem(gameEntities, world);
        // Set kill callback for beam weapons
        this.weaponSystem.setKillCallback(this::killPlayer);

        // Initialize utility system
        this.utilitySystem = new UtilitySystem(
                gameEntities,
                world,
                pos -> isPositionClearOfObstacles(pos, 15.0),
                (msg, category) -> broadcastGameEvent(msg, category, "#FF8800"),
                weaponSystem
        );

        createWorldBoundaries();
        createObstacles();
        createFlags();
        createKothZones();

        // Add initial AI players to make the game more interesting from the start (if enabled)
        if (gameConfig.isEnableAIFilling()) {
            int initialAICount = getMaxPlayers();
            int added = AIGameHelper.addMixedAIPlayers(this, initialAICount);
            if (added > 0) {
                log.info("Added {} initial AI players to game {} for better gameplay", added, gameId);
            }
        } else {
            log.info("AI filling disabled for game {} - no initial AI players added", gameId);
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
                gameEntities.getPlayerSessions().size(),
                getMaxPlayers(),
                gameStartTime,
                gameRunning ? "running" : "waiting"
        );
    }

    public int getPlayerCount() {
        return gameEntities.getPlayerSessions().size();
    }

    /**
     * Get the current round time remaining in seconds.
     * Returns -1 if rounds are not enabled.
     */
    public double getRoundTimeRemaining() {
        return ruleSystem.getRoundTimeRemaining();
    }

    public void shutdown() {
        shutdownHook.cancel(true);
    }

    /**
     * Add an AI player with a specific personality type.
     */
    public boolean addAIPlayer(String personalityType) {
        if (gameEntities.getAllPlayers().size() >= getMaxPlayers()) {
            return false;
        }

        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = findVariedSpawnPointForTeam(assignedTeam);
        AIPlayer aiPlayer = AIPlayerManager.createAIPlayerWithPersonality(Config.nextId(), spawnPoint.x, spawnPoint.y, personalityType, assignedTeam, gameConfig.getPlayerMaxHealth());
        aiPlayer.setHealth(gameConfig.getPlayerMaxHealth());

        // Initialize lives based on respawn mode (delegated to RuleSystem)
        ruleSystem.initializePlayerLives(aiPlayer);

        // Add to game entities
        gameEntities.addPlayer(aiPlayer);
        world.addBody(aiPlayer.getBody());

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
                world.removeBody(player.getBody());
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
                teamCounts[1],
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
        if (!aiAdjustmentEnabled || !gameConfig.isEnableAIFilling()) {
            return;
        }

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
     * Disable AI player adjustment (for testing).
     */
    public void disableAIAdjustment() {
        this.aiAdjustmentEnabled = false;
    }

    /**
     * Enable AI player adjustment (for testing).
     */
    public void enableAIAdjustment() {
        this.aiAdjustmentEnabled = true;
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

            // Skip updates if game is over
            if (ruleSystem.isGameOver()) {
                return;
            }

            // Update rule systems (rounds, victory conditions, respawns)
            ruleSystem.update(deltaTime);

            // Process wave respawns if timer expired
            if (ruleSystem.shouldProcessWaveRespawn()) {
                processWaveRespawns();
            }

            aiPlayerManager.update(gameEntities, deltaTime);
            aiPlayerManager.getAllPlayerInputs().forEach((playerId, input) -> {
                gameEntities.getPlayerInputs().put(playerId, input);
            });
            checkAndAdjustAIPlayers();

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);
            gameEntities.updateAll(deltaTime);
            updateCarriedFlags(); // Update flag positions for carried flags
            getCollisionProcessor().updateKothZones(deltaTime); // Update KOTH zone control and award points (using proper deltaTime)
            gameEntities.getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                if (!projectile.isActive()) {
                    if (projectile.shouldTriggerEffectsOnDismissal()) {
                        projectile.markAsExploded();
                        getCollisionProcessor().getBulletEffectProcessor().processEffectHit(projectile, projectile.getPosition());
                    }
                    world.removeBody(projectile.getBody());
                    return true;
                }
                return false;
            });

            updateUtilityEntities(deltaTime);
            world.updatev(deltaTime);

            gameEntities.runPostUpdateHooks();
            gameEntities.removeInactiveEntities();

            // Update weapon system (homing projectiles, weapon states)
            weaponSystem.updateHomingProjectiles();
            weaponSystem.updateWeaponStates(deltaTime);

            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    /**
     * Update utility entities and handle their special behaviors
     */
    private void updateUtilityEntities(double deltaTime) {
        // Update turrets and handle their AI
        for (Turret turret : gameEntities.getAllTurrets()) {
            if (!turret.isActive()) {
                continue;
            }

            // Turret AI: acquire targets and fire
            turret.acquireTarget(gameEntities.getAllPlayers().stream().toList());
            Projectile turretShot = turret.tryFire();
            if (turretShot != null) {
                gameEntities.addProjectile(turretShot);
                world.addBody(turretShot.getBody());
            }
        }

        // Handle teleport pad activations
        for (TeleportPad teleportPad : gameEntities.getAllTeleportPads()) {
            if (!teleportPad.isActive()) {
                continue;
            }

            // Check for player activations
            Vector2 padPos = teleportPad.getPosition();
            for (Player player : gameEntities.getAllPlayers()) {
                if (!player.isActive()) {
                    continue;
                }

                double distance = padPos.distance(player.getPosition());
                if (distance <= teleportPad.getActivationRadius()) {
                    teleportPad.teleportPlayer(player);
                }
            }
        }

        // Process beam damage for DOT beams
        for (Beam beam : gameEntities.getAllBeams()) {
            if (!beam.isActive()) {
                continue;
            }

            Player beamOwner = gameEntities.getPlayer(beam.getOwnerId());

            // Get players in beam path and apply damage based on beam type
            List<Player> playersInPath = getPlayersInBeamPath(beam);
            for (Player player : playersInPath) {
                if (beam.canAffectPlayer(player)) {
                    switch (beam.getDamageApplicationType()) {
                        case DAMAGE_OVER_TIME:
                            double dotDamage = beam.processContinuousDamage(player, deltaTime);
                            if (dotDamage > 0) {
                                // Apply damage and check if player died
                                if (player.takeDamage(dotDamage)) {
                                    killPlayer(player, beamOwner);
                                }
                            } else if (dotDamage < 0) {
                                // Apply healing (negative damage)
                                player.heal(-dotDamage);
                            }
                            break;
                        case INSTANT:
                            // Instant damage was already applied when beam was created
                            break;
                    }
                }
            }
        }
    }

    protected void onPlayerJoined(PlayerSession playerSession) {
        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = findVariedSpawnPointForTeam(assignedTeam);
        log.info("Player {} joining game {} at spawn point ({}, {}) on team {}",
                playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y, assignedTeam);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y, assignedTeam, gameConfig.getPlayerMaxHealth());
        player.setHealth(gameConfig.getPlayerMaxHealth());

        // Initialize lives based on respawn mode
        if (gameConfig.getRules().hasLimitedLives()) {
            player.initializeLives(gameConfig.getRules().getMaxLives());
            log.info("Player {} initialized with {} lives", player.getId(), gameConfig.getRules().getMaxLives());
        }

        gameEntities.addPlayer(player);
        world.addBody(player.getBody());

        send(playerSession.getSession(), createInitialGameState(player));
        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());

        // Broadcast player join event
        gameEventManager.broadcastSystemMessage(playerSession.getPlayerName() + " joined the game");

        // Adjust AI players when a human player joins
        adjustAIPlayers();
    }

    protected void onPlayerLeft(PlayerSession playerSession) {
        gameEntities.getPlayerInputs().remove(playerSession.getPlayerId());
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            world.removeBody(player.getBody());
            gameEntities.removePlayer(player.getId());

            // Remove from AI manager if it's an AI player
            if (aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
                aiPlayerManager.removeAIPlayer(playerSession.getPlayerId());
            }
        }
        log.info("Player {} left game {}", playerSession.getPlayerId(), gameId);

        // Broadcast player leave event (only for human players)
        if (!aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
            gameEventManager.broadcastSystemMessage(playerSession.getPlayerName() + " left the game");
        }

        // Adjust AI players when a human player leaves
        if (!aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
            adjustAIPlayers();
        }
    }

    protected void processPlayerInput(Integer playerId, PlayerInput input) {
        Player player = gameEntities.getPlayer(playerId);
        if (player != null && input != null) {
            player.processInput(input);

            // Handle primary weapon fire (delegated to WeaponSystem)
            weaponSystem.handlePrimaryFire(player, input);

            // Handle utility weapon fire (delegated to UtilitySystem)
            if (input.isAltFire()) {
                Player.UtilityActivation activation = player.useUtility();
                if (activation != null) {
                    utilitySystem.handleUtilityActivation(activation);
                }
            }
        }
    }

    /**
     * Get all players that intersect with a beam's path using dyn4j ray casting.
     * This method is used for continuous beam damage updates and handles different piercing behaviors.
     */
    private List<Player> getPlayersInBeamPath(Beam beam) {
        List<Player> playersInPath = new ArrayList<>();
        Vector2 beamStart = beam.getStartPoint();
        Vector2 effectiveBeamEnd = beam.getEffectiveEndPoint();

        // Create ray from beam start to effective end
        Vector2 direction = effectiveBeamEnd.copy();
        direction.subtract(beamStart);
        double maxDistance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(beamStart, direction);

        // Use dyn4j's ray casting to find all players in the beam path
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(ray, maxDistance, new DetectFilter<>(true, true, null));

        // Convert results to players and sort by distance
        for (RaycastResult<Body, BodyFixture> result : results) {
            Body body = result.getBody();
            if (body.getUserData() instanceof Player player) {
                if (player.isActive() && player.getHealth() > 0) {
                    playersInPath.add(player);
                }
            }
        }

        // Sort by distance from beam start for proper piercing order
        playersInPath.sort((p1, p2) -> {
            double dist1 = beamStart.distanceSquared(p1.getPosition());
            double dist2 = beamStart.distanceSquared(p2.getPosition());
            return Double.compare(dist1, dist2);
        });

        // For beams that don't pierce players, only return the first player
        if (!beam.canPiercePlayers() && !playersInPath.isEmpty()) {
            return List.of(playersInPath.get(0));
        }

        return playersInPath;
    }

    /**
     * Find beam obstacle intersection - delegates to WeaponSystem.
     */
    private Vector2 findBeamObstacleIntersection(Vector2 beamStart, Vector2 beamEnd) {
        return weaponSystem.findBeamObstacleIntersectionPublic(beamStart, beamEnd);
    }

    /**
     * Process beam initial hit - delegates to WeaponSystem.
     */
    private void processBeamInitialHit(Beam beam) {
        weaponSystem.processBeamInitialHitPublic(beam);
    }

    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            WeaponConfig primaryConfig = WeaponConfig.ASSAULT_RIFLE_PRESET;
            UtilityWeapon utilityConfig = UtilityWeapon.HEAL_ZONE;
            if (request.getWeaponConfig() != null) {
                primaryConfig = request.getWeaponConfig();
            }
            if (request.getUtilityWeapon() != null) {
                utilityConfig = UtilityWeapon.valueOf(request.getUtilityWeapon());
            }
            player.applyWeaponConfig(primaryConfig, utilityConfig);
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


    private void createObstacles() {
        // Use procedurally generated simple obstacles from terrain generator
        for (Obstacle obstacle : terrainGenerator.getGeneratedObstacles()) {
            gameEntities.addObstacle(obstacle);
            world.addBody(obstacle.getBody());
        }
    }

    /**
     * Create flags for capture-the-flag gameplay if configured.
     */
    private void createFlags() {
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
     * Create King of the Hill zones if enabled in rules.
     * Zones are placed strategically between team spawn areas for fair gameplay.
     */
    private void createKothZones() {
        Rules rules = gameConfig.getRules();
        if (!rules.hasKothZones() || !gameConfig.isTeamMode()) {
            return; // KOTH disabled
        }

        int zoneCount = rules.getKothZones();
        int teamCount = gameConfig.getTeamCount();

        log.info("Creating {} KOTH zones for game {}", zoneCount, gameId);

        int zoneId = Config.nextId();

        // Calculate zone positions based on number of zones and teams
        for (int i = 0; i < zoneCount; i++) {
            Vector2 zonePosition = calculateKothZonePosition(i, zoneCount, teamCount);

            // Ensure zone position is clear of obstacles
            if (!terrainGenerator.isPositionClear(zonePosition, 100.0)) {
                // Try to find a nearby clear position
                for (int attempt = 0; attempt < 10; attempt++) {
                    double offsetX = (Math.random() - 0.5) * 200;
                    double offsetY = (Math.random() - 0.5) * 200;
                    Vector2 candidate = new Vector2(zonePosition.x + offsetX, zonePosition.y + offsetY);

                    if (terrainGenerator.isPositionClear(candidate, 100.0)) {
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
        double standardSpread = 0.33;

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
     * Update positions of flags that are being carried by players.
     */
    private void updateCarriedFlags() {
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried()) {
                int carrierId = flag.getCarriedByPlayerId();
                Player carrier = gameEntities.getPlayer(carrierId);

                if (carrier != null && carrier.isActive()) {
                    // Move flag to player's position
                    Vector2 playerPos = carrier.getPosition();
                    flag.getBody().getTransform().setTranslation(playerPos.x, playerPos.y);
                } else {
                    // Carrier is no longer active, drop the flag
                    flag.drop();
                    log.info("Flag {} dropped at ({}, {}) - carrier {} inactive",
                            flag.getId(), flag.getPosition().x, flag.getPosition().y, carrierId);
                }
            }
        }
    }

    /**
     * Broadcast zone control change events to players.
     * Called by CollisionProcessor.
     */
    public void broadcastZoneControlChange(KothZone zone, int previousController, KothZone.ZoneState previousState) {
        int currentController = zone.getControllingTeam();
        KothZone.ZoneState currentState = zone.getState();
        String zoneName = "Zone " + (zone.getZoneNumber() + 1);

        // Zone captured
        if (currentState == KothZone.ZoneState.CONTROLLED && previousState != KothZone.ZoneState.CONTROLLED) {
            String teamName = getTeamName(currentController);
            broadcastGameEvent(
                    String.format("%s captured %s!", teamName, zoneName),
                    "ZONE_CAPTURE",
                    getTeamColorHex(currentController)
            );
            log.info("Zone {} captured by team {}", zone.getZoneNumber(), currentController);
        }
        // Zone contested
        else if (currentState == KothZone.ZoneState.CONTESTED && previousState != KothZone.ZoneState.CONTESTED) {
            broadcastGameEvent(
                    String.format("%s is contested!", zoneName),
                    "ZONE_CONTESTED",
                    "#FF8800"
            );
        }
        // Zone neutralized
        else if (currentState == KothZone.ZoneState.NEUTRAL && previousController >= 0) {
            broadcastGameEvent(
                    String.format("%s neutralized", zoneName),
                    "ZONE_NEUTRAL",
                    "#888888"
            );
        }
    }

    /**
     * Get team name for display.
     */
    private String getTeamName(int team) {
        if (gameConfig.isFreeForAll()) {
            return "Player";
        }
        return "Team " + (team + 1);
    }

    /**
     * Get team color hex code for events.
     */
    private String getTeamColorHex(int team) {
        return switch (team) {
            case 0 -> "#4CAF50";  // Green
            case 1 -> "#F44336";  // Red
            case 2 -> "#2196F3";  // Blue
            case 3 -> "#FF9800";  // Orange
            default -> "#FFFFFF"; // White
        };
    }

    private void sendGameState() {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("type", "gameState");
        gameState.put("timestamp", System.currentTimeMillis());

        // Include rule system state (rounds, victory, respawns)
        gameState.putAll(ruleSystem.getStateData());

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
            playerState.put("weapon", 0); // Always primary weapon now
            playerState.put("utilityWeapon", player.getUtilityWeapon().name());
            playerState.put("ammo", player.getCurrentWeapon().getCurrentAmmo());
            playerState.put("maxAmmo", player.getCurrentWeapon().getMagazineSize());
            playerState.put("reloading", player.isReloading());
            playerState.put("kills", player.getKills());
            playerState.put("deaths", player.getDeaths());
            playerState.put("captures", player.getCaptures());
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
            if (obstacle.getType() == Obstacle.ObstacleType.PLAYER_BARRIER) {
                obsState.put("health", obstacle.getHealth());
                obsState.put("maxHealth", obstacle.getMaxHealth());
                obsState.put("active", obstacle.isActive());
                obsState.put("ownerId", obstacle.getOwnerId());
                obsState.put("ownerTeam", obstacle.getOwnerTeam());
            }

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
            effectState.put("duration", effect.getDuration());
            effectState.put("timeRemaining", effect.getTimeRemaining());
            effectState.put("progress", effect.getProgress());
            effectState.put("active", effect.isActive());
            effectState.put("ownerTeam", effect.getOwnerTeam());
            fieldEffectStates.add(effectState);
        }
        gameState.put("fieldEffects", fieldEffectStates);

        // Add utility entities to game state
        List<Map<String, Object>> turretStates = new ArrayList<>();
        for (Turret turret : gameEntities.getAllTurrets()) {
            Vector2 pos = turret.getPosition();
            Map<String, Object> turretState = new HashMap<>();
            turretState.put("id", turret.getId());
            turretState.put("type", "TURRET");
            turretState.put("x", pos.x);
            turretState.put("y", pos.y);
            turretState.put("rotation", turret.getBody().getTransform().getRotation().toRadians());
            turretState.put("health", turret.getHealth());
            turretState.put("active", turret.isActive());
            turretState.put("ownerId", turret.getOwnerId());
            turretState.put("ownerTeam", turret.getOwnerTeam());
            turretState.put("lifespanPercent", turret.getLifespanPercent());
            turretStates.add(turretState);
        }
        gameState.put("turrets", turretStates);

        List<Map<String, Object>> netStates = new ArrayList<>();
        for (NetProjectile net : gameEntities.getAllNetProjectiles()) {
            Vector2 pos = net.getPosition();
            Vector2 vel = net.getVelocity();
            Map<String, Object> netState = new HashMap<>();
            netState.put("id", net.getId());
            netState.put("type", "NET");
            netState.put("x", pos.x);
            netState.put("y", pos.y);
            netState.put("vx", vel.x);
            netState.put("vy", vel.y);
            netState.put("rotation", net.getBody().getTransform().getRotation().toRadians());
            netState.put("active", net.isActive());
            netState.put("ownerId", net.getOwnerId());
            netState.put("ownerTeam", net.getOwnerTeam());
            netStates.add(netState);
        }
        gameState.put("nets", netStates);

        List<Map<String, Object>> mineStates = new ArrayList<>();
        for (FieldEffect fieldEffect : gameEntities.getAllFieldEffects()) {
            if (fieldEffect.getType() != FieldEffectType.PROXIMITY_MINE) {
                continue;
            }
            Vector2 pos = fieldEffect.getPosition();
            Map<String, Object> mineState = new HashMap<>();
            mineState.put("id", fieldEffect.getId());
            mineState.put("type", "MINE");
            mineState.put("x", pos.x);
            mineState.put("y", pos.y);
            mineState.put("active", fieldEffect.isActive());
            mineState.put("ownerId", fieldEffect.getOwnerId());
            mineState.put("ownerTeam", fieldEffect.getOwnerTeam());
            mineState.put("isArmed", fieldEffect.isArmed());
            mineStates.add(mineState);
        }
        gameState.put("mines", mineStates);

        List<Map<String, Object>> teleportPadStates = new ArrayList<>();
        for (TeleportPad teleportPad : gameEntities.getAllTeleportPads()) {
            Vector2 pos = teleportPad.getPosition();
            Map<String, Object> padState = new HashMap<>();
            padState.put("id", teleportPad.getId());
            padState.put("type", "TELEPORT_PAD");
            padState.put("x", pos.x);
            padState.put("y", pos.y);
            padState.put("active", teleportPad.isActive());
            padState.put("ownerId", teleportPad.getOwnerId());
            padState.put("ownerTeam", teleportPad.getOwnerTeam());
            padState.put("isLinked", teleportPad.isLinked());
            padState.put("isCharging", teleportPad.isCharging());
            padState.put("chargingProgress", teleportPad.getChargingProgress());
            padState.put("lifespanPercent", teleportPad.getLifespanPercent());
            padState.put("pulseValue", teleportPad.getPulseValue());
            if (teleportPad.getLinkedPad() != null) {
                padState.put("linkedPadId", teleportPad.getLinkedPad().getId());
            }
            teleportPadStates.add(padState);
        }
        gameState.put("teleportPads", teleportPadStates);

        // Beam states
        List<Map<String, Object>> beamStates = new ArrayList<>();
        for (Beam beam : gameEntities.getAllBeams()) {
            Vector2 startPos = beam.getStartPoint();
            Vector2 effectiveEndPos = beam.getEffectiveEndPoint(); // Use effective end point for rendering
            Map<String, Object> beamState = new HashMap<>();
            beamState.put("id", beam.getId());
            beamState.put("startX", startPos.x);
            beamState.put("startY", startPos.y);
            beamState.put("endX", effectiveEndPos.x);
            beamState.put("endY", effectiveEndPos.y);
            beamState.put("ownerId", beam.getOwnerId());
            beamState.put("ownerTeam", beam.getOwnerTeam());
            beamState.put("damage", beam.getDamage());
            beamState.put("damageType", beam.getDamageApplicationType().name());
            beamState.put("durationPercent", beam.getDurationPercent());
            beamState.put("isHealingBeam", beam.isHealingBeam());
            beamState.put("canPiercePlayers", beam.canPiercePlayers());
            beamState.put("canPierceObstacles", beam.canPierceObstacles());
            beamStates.add(beamState);
        }
        gameState.put("beams", beamStates);

        // Include KOTH zone states if enabled
        if (gameConfig.getRules().hasKothZones()) {
            List<Map<String, Object>> zoneStates = new ArrayList<>();
            for (KothZone zone : gameEntities.getAllKothZones()) {
                Vector2 pos = zone.getPosition();
                Map<String, Object> zoneState = new HashMap<>();
                zoneState.put("id", zone.getId());
                zoneState.put("zoneNumber", zone.getZoneNumber());
                zoneState.put("x", pos.x);
                zoneState.put("y", pos.y);
                zoneState.put("radius", 80.0); // ZONE_RADIUS from KothZone
                zoneState.put("controllingTeam", zone.getControllingTeam());
                zoneState.put("state", zone.getState().name());
                zoneState.put("captureProgress", zone.getCaptureProgress());
                zoneState.put("playerCount", zone.getTotalPlayerCount());
                zoneStates.add(zoneState);
            }
            gameState.put("kothZones", zoneStates);
            gameState.put("kothEnabled", true);
        } else {
            gameState.put("kothEnabled", false);
        }

        // Include flag states if flags are enabled
        if (gameConfig.getRules().hasFlags()) {
            List<Map<String, Object>> flagStates = new ArrayList<>();
            for (Flag flag : gameEntities.getAllFlags()) {
                Vector2 pos = flag.getPosition();
                Map<String, Object> flagState = new HashMap<>();
                flagState.put("id", flag.getId());
                flagState.put("x", pos.x);
                flagState.put("y", pos.y);
                flagState.put("ownerTeam", flag.getOwnerTeam());
                flagState.put("state", flag.getState().name());
                flagState.put("carriedBy", flag.getCarriedByPlayerId());
                flagState.put("homeX", flag.getHomePosition().x);
                flagState.put("homeY", flag.getHomePosition().y);
                flagState.put("captureCount", flag.getCaptureCount());
                flagStates.add(flagState);
            }
            gameState.put("flags", flagStates);
            gameState.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        broadcast(gameState);
    }

    /**
     * Check if a position is clear of obstacles for entity placement.
     * Used to prevent placing turrets, barriers, etc. inside obstacles.
     *
     * @param position Position to check
     * @param radius   Radius of the entity being placed
     * @return true if position is clear, false if blocked by obstacle
     */
    private boolean isPositionClearOfObstacles(Vector2 position, double radius) {
        // Add a small buffer to prevent entities from being placed too close to obstacles
        double checkRadius = radius + 5.0;

        // Check against all obstacles
        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            double distance = position.distance(obstacle.getPosition());
            double minDistance = checkRadius + obstacle.getBoundingRadius();

            if (distance < minDistance) {
                return false; // Position is blocked
            }
        }

        // Also check against world boundaries
        double halfWidth = gameConfig.getWorldWidth() / 2.0;
        double halfHeight = gameConfig.getWorldHeight() / 2.0;

        return !(Math.abs(position.x) + checkRadius > halfWidth)
               && !(Math.abs(position.y) + checkRadius > halfHeight);
    }

    /**
     * Find a varied spawn point for a specific team that avoids clustering.
     * Tries multiple locations to avoid spawning too close to other players.
     *
     * @param team Team number (0 for FFA)
     * @return Spawn point for the team with good spacing
     */
    private Vector2 findVariedSpawnPointForTeam(int team) {
        Vector2 bestSpawnPoint = null;
        double bestMinDistance = 0;

        // Try multiple spawn attempts to find the best one
        for (int attempt = 0; attempt < 15; attempt++) {
            Vector2 candidateSpawn = findSpawnPointForTeam(team);

            // Calculate minimum distance to any active player
            double minDistanceToPlayer = Double.MAX_VALUE;
            for (Player player : gameEntities.getAllPlayers()) {
                if (player.isActive()) {
                    double distance = candidateSpawn.distance(player.getPosition());
                    minDistanceToPlayer = Math.min(minDistanceToPlayer, distance);
                }
            }

            // Keep the spawn point with the best (largest) minimum distance
            if (minDistanceToPlayer > bestMinDistance) {
                bestMinDistance = minDistanceToPlayer;
                bestSpawnPoint = candidateSpawn;
            }

            // If we found a spawn point with good spacing, use it
            if (bestMinDistance > 150.0) { // Minimum desired spacing
                break;
            }
        }

        return bestSpawnPoint != null ? bestSpawnPoint : findSpawnPointForTeam(team);
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

    private Map<String, Object> createInitialGameState(Player player) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "initialState");
        state.put("playerId", player.getId());
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
            obsData.put("health", obstacle.getHealth());
            obsData.put("maxHealth", obstacle.getMaxHealth());
            obsData.put("active", obstacle.isActive());
            obsData.put("ownerId", obstacle.getOwnerId());
            obsData.put("ownerTeam", obstacle.getOwnerTeam());

            // Add detailed shape data for client rendering
            obsData.putAll(obstacle.getShapeData());

            obstacles.add(obsData);
        }
        state.put("obstacles", obstacles);

        // Add flag information if flags are enabled
        if (gameConfig.getRules().hasFlags()) {
            List<Map<String, Object>> flagsData = new ArrayList<>();
            for (Flag flag : gameEntities.getAllFlags()) {
                Map<String, Object> flagData = new HashMap<>();
                flagData.put("id", flag.getId());
                flagData.put("ownerTeam", flag.getOwnerTeam());
                flagData.put("homeX", flag.getHomePosition().x);
                flagData.put("homeY", flag.getHomePosition().y);
                flagsData.add(flagData);
            }
            state.put("flags", flagsData);
            state.put("flagsPerTeam", gameConfig.getRules().getFlagsPerTeam());
            state.put("scoreStyle", gameConfig.getRules().getScoreStyle().name());
        }

        return state;
    }

    /**
     * Process wave respawns - called when wave timer expires.
     */
    private void processWaveRespawns() {
        for (Player player : ruleSystem.getDeadPlayersWaitingForWave()) {
            if (!player.isEliminated()) {
                player.setActive(true);
                player.setHealth(gameConfig.getPlayerMaxHealth());
                player.setRespawnTime(0);

                // Move to spawn point
                Vector2 spawnPoint = findVariedSpawnPointForTeam(player.getTeam());
                player.getBody().getTransform().setTranslation(spawnPoint.x, spawnPoint.y);
                player.getBody().setLinearVelocity(0, 0);
                player.getBody().setAngularVelocity(0);

                log.debug("Wave respawned player {} at ({}, {})",
                        player.getId(), spawnPoint.x, spawnPoint.y);
            }
        }
    }

    public void killPlayer(Player victim, Player shooter) {
        if (shooter != null) {
            shooter.addKill();
        }
        victim.die();

        // Drop any flag the victim was carrying
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried() && flag.getCarriedByPlayerId() == victim.getId()) {
                flag.drop();
                log.info("Player {} died, dropped flag {}", victim.getId(), flag.getId());
            }
        }

        // Handle respawn based on respawn mode (delegated to RuleSystem)
        RuleSystem.RespawnAction action = ruleSystem.handlePlayerDeath(victim);

        switch (action) {
            case RESPAWN_AFTER_DELAY:
                // Standard respawn with delay
                victim.setRespawnTime(gameConfig.getRules().getRespawnDelay());
                Vector2 newSpawnPoint = findVariedSpawnPointForTeam(victim.getTeam());
                victim.setRespawnPoint(newSpawnPoint);
                break;

            case WAIT_FOR_WAVE:
                // Player added to wave queue by RuleSystem
                break;

            case WAIT_FOR_ROUND:
                // Player will respawn at round start
                break;

            case ELIMINATE:
                // Player is permanently eliminated
                break;
        }

        // Broadcast kill event with team colors
        String killerName = shooter != null ? shooter.getPlayerName() : "Unknown";
        String victimName = victim.getPlayerName();
        String weaponName = shooter != null ? shooter.getCurrentWeapon().getName() : "Unknown weapon";
        Integer killerTeam = shooter != null ? shooter.getTeam() : null;
        Integer victimTeam = victim.getTeam();

        gameEventManager.broadcastKill(killerName, victimName, weaponName, killerTeam, victimTeam);

        // Legacy death notification (keeping for compatibility)
        Map<String, Object> deathNotification = new HashMap<>();
        deathNotification.put("type", "playerKilled");
        deathNotification.put("victimId", victim.getId());
        deathNotification.put("killerId", shooter != null ? shooter.getId() : null);
        deathNotification.put("killerName", shooter != null ? shooter.getPlayerName() : null);
        broadcast(deathNotification);
    }

    // Game Event Broadcasting Convenience Methods

    /**
     * Broadcast a system message to all players
     */
    public void broadcastSystemMessage(String message) {
        gameEventManager.broadcastSystemMessage(message);
    }

    /**
     * Broadcast a custom message with color to all players
     */
    public void broadcastMessage(String message, String color) {
        gameEventManager.broadcastCustomMessage(message, color,
                GameEvent.EventTarget.builder().type(GameEvent.EventTarget.TargetType.ALL).build());
    }

    /**
     * Broadcast a team-specific message
     */
    public void broadcastTeamMessage(String message, int teamId, GameEvent.EventCategory category) {
        gameEventManager.broadcastTeamMessage(message, teamId, category);
    }

    /**
     * Broadcast to a specific player
     */
    public void broadcastToPlayer(String message, int playerId, GameEvent.EventCategory category) {
        gameEventManager.broadcastToPlayer(message, playerId, category);
    }

    /**
     * Broadcast an achievement
     */
    public void broadcastAchievement(String playerName, String achievement) {
        gameEventManager.broadcastAchievement(playerName, achievement);
    }

    /**
     * Broadcast a custom game event
     */
    public void broadcastGameEvent(GameEvent event) {
        gameEventManager.broadcastEvent(event);
    }

    /**
     * Broadcast a custom game event (convenience overload).
     */
    public void broadcastGameEvent(String message, String category, String color) {
        GameEvent.EventCategory eventCategory = GameEvent.EventCategory.INFO;
        try {
            eventCategory = GameEvent.EventCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            // Use default INFO category if invalid
        }

        GameEvent event = GameEvent.builder()
                .message(message)
                .category(eventCategory)
                .color(color)
                .target(GameEvent.EventTarget.builder()
                        .type(GameEvent.EventTarget.TargetType.ALL)
                        .build())
                .displayDuration(5000L)
                .build();
        gameEventManager.broadcastEvent(event);
    }

    /**
     * Award a capture to a player and their team.
     */
    public void awardCapture(Player player, int capturedFlagTeam) {
        player.addCapture();
        log.info("Player {} (team {}) awarded capture. Total captures: {}",
                player.getId(), player.getTeam(), player.getCaptures());
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
}

package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.util.IdGenerator;
import com.fullsteam.ai.AIGameHelper;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AIPlayerManager;
import com.fullsteam.model.*;
import com.fullsteam.util.GameConstants;
import com.fullsteam.util.WeaponFormatter;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnArea;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.TeleportPad;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.Workshop;
import com.fullsteam.physics.PowerUp;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    protected final CollisionProcessor collisionProcessor;
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
    @Getter
    protected final EntitySpawner entitySpawner;
    @Getter
    protected final SpawnPointManager spawnPointManager;
    @Getter
    protected final GameStateSerializer gameStateSerializer;

    protected final ObjectMapper objectMapper;

    @Getter
    protected long gameStartTime;
    protected boolean gameRunning = false;
    private final long aiCheckIntervalMs;
    private long lastAICheckTime = 0;
    private final World<Body> world;
    private final ScheduledFuture<?> shutdownHook;
    private double lastUpdateTime = System.nanoTime() / 1e9;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public GameManager(String gameId, GameConfig gameConfig, ObjectMapper objectMapper) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.objectMapper = objectMapper;
        this.gameStartTime = System.currentTimeMillis();
        this.aiPlayerManager = new AIPlayerManager(gameConfig);

        // Initialize AI management settings from config
        this.aiCheckIntervalMs = gameConfig.getAiCheckIntervalMs();
        this.teamSpawnManager = new TeamSpawnManager(gameConfig.getWorldWidth(), gameConfig.getWorldHeight(), gameConfig.getTeamCount());

        // Pass oddball info and obstacle density to terrain generator
        boolean hasOddball = gameConfig.getRules().hasOddball();
        EntityWorldDensity obstacleDensity = gameConfig.getRules().getObstacleDensity();
        this.terrainGenerator = new TerrainGenerator(
                gameConfig.getWorldWidth(),
                gameConfig.getWorldHeight(),
                hasOddball,
                obstacleDensity
        );

        this.world = new World<>();

        Settings settings = new Settings();
        settings.setMaximumTranslation(300.0);
        this.world.setSettings(settings);
        this.world.setGravity(new Vector2(0, 0));
        this.world.setBounds(new AxisAlignedBounds(gameConfig.getWorldWidth(), gameConfig.getWorldHeight()));

        this.gameEntities = new GameEntities(gameConfig, world);
        this.collisionProcessor = new CollisionProcessor(this, this.gameEntities);
        this.world.addCollisionListener(collisionProcessor);

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
                pos -> isPositionClearOfObstacles(pos, 15.0)
        );

        // Initialize entity spawner
        this.entitySpawner = new EntitySpawner(
                gameId,
                gameConfig,
                gameEntities,
                world,
                teamSpawnManager,
                terrainGenerator
        );

        // Initialize spawn point manager
        this.spawnPointManager = new SpawnPointManager(
                gameConfig,
                gameEntities,
                teamSpawnManager,
                terrainGenerator
        );

        // Initialize game state serializer
        this.gameStateSerializer = new GameStateSerializer(
                gameConfig,
                gameEntities,
                ruleSystem,
                teamSpawnManager,
                terrainGenerator
        );

        entitySpawner.createWorldBoundaries();
        entitySpawner.createObstacles();
        entitySpawner.createFlags();
        entitySpawner.createOddball();
        entitySpawner.createKothZones();
        entitySpawner.createWorkshops();
        entitySpawner.createHeadquarters();

        // Initialize event system if enabled (must be after terrain generation)
        ruleSystem.initializeEventSystem(
                terrainGenerator,
                this::addFieldEffectToWorld,
                this::addPowerUpToWorld,
                gameConfig.getWorldWidth(),
                gameConfig.getWorldHeight()
        );

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

        // Check if game is locked to new players
        if (isGameLocked()) {
            log.info("Player {} attempted to join locked game {}", playerSession.getPlayerId(), gameId);
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
        } catch (WebSocketSessionException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                log.error("Error sending message", e);
            } else {
                log.debug("interrupted sending message, likely game was shutdown", e);
            }
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
                gameRunning ? "running" : "waiting",
                gameConfig
        );
    }

    public int getPlayerCount() {
        return gameEntities.getPlayerSessions().size();
    }

    /**
     * Check if the game is locked to new players based on elapsed time.
     */
    public boolean isGameLocked() {
        if (!gameConfig.getRules().shouldLockGame()) {
            return false; // Game never locks
        }

        double elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000.0;
        return elapsedSeconds >= gameConfig.getRules().getLockGameAfterSeconds();
    }

    public void shutdown() {
        shutdown.set(true);
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
        Vector2 spawnPoint = spawnPointManager.findVariedSpawnPointForTeam(assignedTeam);
        AIPlayer aiPlayer = AIPlayerManager.createAIPlayerWithPersonality(IdGenerator.nextEntityId(), spawnPoint.x, spawnPoint.y, personalityType, assignedTeam, gameConfig.getPlayerMaxHealth());
        aiPlayer.setHealth(gameConfig.getPlayerMaxHealth());

        // Initialize lives based on respawn mode (delegated to RuleSystem)
        ruleSystem.initializePlayerLives(aiPlayer);

        // Apply spawn invincibility to give AI player time to get their bearings
        StatusEffectManager.applySpawnInvincibility(aiPlayer);

        // Add to game entities
        gameEntities.addPlayer(aiPlayer);
        world.addBody(aiPlayer.getBody());

        // Add to AI manager
        aiPlayerManager.addAIPlayer(aiPlayer);

        log.info("Added AI player {} ({}) with {} personality on team {} at spawn point ({}, {})",
                aiPlayer.getId(), aiPlayer.getPlayerName(), personalityType,
                assignedTeam, spawnPoint.x, spawnPoint.y);

        // Ensure VIP is assigned for this team if VIP mode is enabled
        if (gameConfig.getRules().hasVip()) {
            ruleSystem.ensureVipForTeam(assignedTeam);
        }

        return true;
    }

    /**
     * Remove an AI player from the game.
     */
    public void removeAIPlayer(int playerId) {
        if (aiPlayerManager.isAIPlayer(playerId)) {
            Player player = gameEntities.getPlayer(playerId);
            int playerTeam = 0;
            if (player != null) {
                playerTeam = player.getTeam();
                world.removeBody(player.getBody());
                gameEntities.removePlayer(playerId);
            }
            aiPlayerManager.removeAIPlayer(playerId);
            log.info("Removed AI player {}", playerId);
            
            // Ensure VIP is reassigned if this AI player was the VIP
            if (gameConfig.getRules().hasVip() && playerTeam > 0) {
                ruleSystem.ensureVipForTeam(playerTeam);
            }
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
                Arrays.toString(teamCounts));
        log.info("Assigning player to team {} (counts: T1={}, T2={}, T3={}, T4={})",
                bestTeam,
                teamCounts[1],
                teamCounts.length > 2 ? teamCounts[2] : 0,
                teamCounts.length > 3 ? teamCounts[3] : 0,
                teamCounts.length > 4 ? teamCounts[4] : 0);

        return bestTeam;
    }

    /**
     * Manually trigger AI player adjustment based on current settings.
     */
    public void adjustAIPlayers() {
        if (!gameConfig.isEnableAIFilling()) {
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
        if (shutdown.get()) {
            return;
        }
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

            // Process individual player respawns based on rules
            processPlayerRespawns();

            aiPlayerManager.update(gameEntities, deltaTime);
            gameEntities.getPlayerInputs().putAll(aiPlayerManager.getAllPlayerInputs());
            checkAndAdjustAIPlayers();

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);
            gameEntities.updateAll(deltaTime);
            updateCarriedFlags(); // Update flag positions for carried flags
            collisionProcessor.updateKothZones(deltaTime); // Update KOTH zone control and award points (using proper deltaTime)
            collisionProcessor.updateOddball(deltaTime); // Update oddball scoring (using proper deltaTime)
            collisionProcessor.updateWorkshops(deltaTime); // Update workshop crafting mechanics (using proper deltaTime)
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
                collisionProcessor.getBulletEffectProcessor().applyHomingBehavior(projectile);
                return false;
            });

            updateUtilityEntities(deltaTime);
            updateDefenseLaserBeamEndpoints();

            world.updatev(deltaTime);

            gameEntities.runPostUpdateHooks();
            gameEntities.removeInactiveEntities();
            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    /**
     * Update DefenseLaser beam effective endpoints based on obstacle collisions.
     * This ensures beams stop at obstacles instead of piercing through them.
     */
    private void updateDefenseLaserBeamEndpoints() {
        for (DefenseLaser defenseLaser : gameEntities.getAllDefenseLasers()) {
            if (!defenseLaser.isActive()) {
                continue;
            }

            // Calculate effective endpoints for all beams
            Vector2[] effectiveEndpoints = new Vector2[defenseLaser.getBeams().size()];
            for (int i = 0; i < defenseLaser.getBeams().size(); i++) {
                Beam beam = defenseLaser.getBeams().get(i);
                Vector2 effectiveEnd = weaponSystem.findBeamObstacleIntersection(
                        beam.getStartPoint(),
                        beam.getEndPoint()
                );
                effectiveEndpoints[i] = effectiveEnd;
            }

            // Update the DefenseLaser with the calculated effective endpoints
            defenseLaser.updateBeamEffectiveEndpoints(effectiveEndpoints);
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
        Vector2 spawnPoint = spawnPointManager.findVariedSpawnPointForTeam(assignedTeam);
        log.info("Player {} joining game {} at spawn point ({}, {}) on team {}",
                playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y, assignedTeam);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y, assignedTeam, gameConfig.getPlayerMaxHealth());
        player.setHealth(gameConfig.getPlayerMaxHealth());

        // Initialize lives based on respawn mode
        if (gameConfig.getRules().hasLimitedLives()) {
            player.initializeLives(gameConfig.getRules().getMaxLives());
            log.info("Player {} initialized with {} lives", player.getId(), gameConfig.getRules().getMaxLives());
        }

        // Apply spawn invincibility to give player time to get their bearings
        StatusEffectManager.applySpawnInvincibility(player);

        gameEntities.addPlayer(player);
        world.addBody(player.getBody());

        send(playerSession.getSession(), createInitialGameState(player));
        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());

        // Broadcast player join event with team color
        gameEventManager.broadcastPlayerJoin(playerSession.getPlayerName(), assignedTeam);

        // Ensure VIP is assigned for this team if VIP mode is enabled
        if (gameConfig.getRules().hasVip()) {
            ruleSystem.ensureVipForTeam(assignedTeam);
        }

        // Adjust AI players when a human player joins
        adjustAIPlayers();
    }

    protected void onPlayerLeft(PlayerSession playerSession) {
        gameEntities.getPlayerInputs().remove(playerSession.getPlayerId());
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            int playerTeam = player.getTeam();
            
            world.removeBody(player.getBody());
            gameEntities.removePlayer(player.getId());

            // Remove from AI manager if it's an AI player
            if (aiPlayerManager.isAIPlayer(playerSession.getPlayerId())) {
                aiPlayerManager.removeAIPlayer(playerSession.getPlayerId());
            }
            
            // Ensure VIP is reassigned if this player was the VIP
            if (gameConfig.getRules().hasVip() && playerTeam > 0) {
                ruleSystem.ensureVipForTeam(playerTeam);
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


    /**
     * Create flags for capture-the-flag gameplay if configured.
     */
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
    /**
     * Create King of the Hill zones if enabled in rules.
     * Zones are placed strategically between team spawn areas for fair gameplay.
     */
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
    /**
     * Create headquarters if enabled in rules.
     * Each team gets one headquarters placed in their spawn zone (defensive position).
     */
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

                    // Remove ball carrier status effect if this was the oddball
                    if (flag.isOddball() && carrier != null) {
                        StatusEffectManager.removeBallCarrier(carrier);
                    }

                    log.info("Flag {} dropped at ({}, {}) - carrier {} inactive",
                            flag.getId(), flag.getPosition().x, flag.getPosition().y, carrierId);
                }
            }
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
            case 1 -> "#4CAF50";  // Green
            case 2 -> "#F44336";  // Red
            case 3 -> "#2196F3";  // Blue
            case 4 -> "#FF9800";  // Orange
            default -> "#FFFFFF"; // White
        };
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

    private void sendGameState() {
        Map<String, Object> gameState = gameStateSerializer.createGameState();
        broadcast(gameState);
    }

    private Map<String, Object> createInitialGameState(Player player) {
        return gameStateSerializer.createInitialGameState(player);
    }

    private void processPlayerRespawns() {
        for (Player player : gameEntities.getAllPlayers()) {
            if (ruleSystem.shouldPlayerRespawn(player)) {
                respawnPlayer(player);
            }
        }
    }

    public void respawnPlayer(Player player) {
        player.setActive(true);
        player.setHealth(gameConfig.getPlayerMaxHealth());
        player.setRespawnTime(0);

        // Move to spawn point
        Vector2 spawnPoint = spawnPointManager.findVariedSpawnPointForTeam(player.getTeam());
        player.getBody().getTransform().setTranslation(spawnPoint.x, spawnPoint.y);
        player.getBody().setLinearVelocity(0, 0);
        player.getBody().setAngularVelocity(0);
        
        // Apply spawn invincibility to give player time to get their bearings
        StatusEffectManager.applySpawnInvincibility(player);
        
        // Ensure VIP is assigned for this team if VIP mode is enabled
        if (gameConfig.getRules().hasVip()) {
            ruleSystem.ensureVipForTeam(player.getTeam());
        }
    }

    public void killPlayer(Player victim, Player shooter) {
        // Check if this was a VIP kill BEFORE calling die() (which clears status effects)
        boolean wasVip = gameConfig.getRules().hasVip() && StatusEffectManager.isVip(victim);
        
        if (shooter != null) {
            shooter.addKill();
        }
        victim.die();

        if (gameConfig.getRules().getVictoryCondition() == VictoryCondition.ELIMINATION) {
            victim.setEliminated(true);
            victim.setEliminationTime(System.currentTimeMillis());
            
            // Calculate placement based on how many players are still alive
            int remainingPlayers = (int) gameEntities.getAllPlayers().stream()
                    .filter(p -> !p.isEliminated())
                    .count();
            victim.setPlacement(remainingPlayers + 1); // +1 because this player just got eliminated
        }

        // Award points if this was a VIP kill
        if (wasVip) {
            if (shooter != null && shooter.getTeam() != victim.getTeam()) {
                ruleSystem.awardVipKill(shooter.getTeam());
                
                // Broadcast VIP kill event
                gameEventManager.broadcastSystemMessage(
                        String.format("💀 %s eliminated the VIP %s! +1 OBJECTIVE", 
                                shooter.getPlayerName(), victim.getPlayerName()));
            }
            
            // VIP died, need to select a new VIP for their team after respawn
            // This will be handled in ensureVipForTeam during respawn
        }

        // Check if player lost their last life
        if (victim.loseLife()) {
            gameEventManager.broadcastElimination(
                    victim.getPlayerName(),
                    victim.getTeam(),
                    victim.getLivesRemaining()
            );
        }

        // Drop any flag the victim was carrying
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried() && flag.getCarriedByPlayerId() == victim.getId()) {
                flag.drop();

                // Remove ball carrier status effect if they were carrying the oddball
                if (flag.isOddball()) {
                    StatusEffectManager.removeBallCarrier(victim);
                }

                log.info("Player {} died, dropped flag {}", victim.getId(), flag.getId());
            }
        }

        ruleSystem.setRespawnTime(victim);

        // Broadcast kill event with team colors
        String killerName = shooter != null ? shooter.getPlayerName() : "Unknown";
        String victimName = victim.getPlayerName();
        String weaponName = shooter != null ? WeaponFormatter.getDisplayName(shooter.getCurrentWeapon()) : "Unknown weapon";
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

    /**
     * Handle headquarters damage and scoring.
     * Called by CollisionProcessor when HQ is hit.
     */
    public void handleHeadquartersDamage(Headquarters hq, Player attacker, double damage, boolean destroyed) {
        Rules rules = gameConfig.getRules();

        // Award points for damage dealt
        double pointsPerDamage = rules.getHeadquartersPointsPerDamage();
        int points = (int) (damage * pointsPerDamage);

        if (points > 0 && attacker != null) {
            ruleSystem.addTeamPoints(attacker.getTeam(), points);
            log.debug("Team {} scored {} points for damaging team {} headquarters (damage: {})",
                    attacker.getTeam(), points, hq.getTeamNumber(), damage);
        }

        // Handle destruction
        if (destroyed) {
            int destructionBonus = rules.getHeadquartersDestructionBonus();
            if (attacker != null) {
                ruleSystem.addTeamPoints(attacker.getTeam(), destructionBonus);
            }

            // Create destruction effect
            createHeadquartersDestructionEffect(hq);

            log.info("Team {} headquarters destroyed by team {}! Bonus: {} points",
                    hq.getTeamNumber(), attacker != null ? attacker.getTeam() : "?", destructionBonus);

            // Broadcast HQ destruction event
            if (attacker != null) {
                gameEventManager.broadcastHeadquartersDestroyed(hq.getTeamNumber(), attacker.getTeam());
            }

            // Check if this ends the game
            if (rules.isHeadquartersDestructionEndsGame()) {
                int winningTeam = attacker != null ? attacker.getTeam() : -1;
                if (winningTeam > 0) {
                    ruleSystem.declareVictory(winningTeam, -1, "Headquarters Destroyed");
                }
            }
        }
    }

    /**
     * Create explosion effect when headquarters is destroyed.
     */
    private void createHeadquartersDestructionEffect(Headquarters hq) {
        Vector2 pos = hq.getPosition();

        // Create large explosion effect at HQ location
        FieldEffect explosion = new FieldEffect(
                IdGenerator.nextEntityId(),
                -1, // No owner
                FieldEffectType.EXPLOSION,
                pos,
                100.0, // Large radius
                100.0, // Large damage
                2.0,   // 2 second duration
                0      // No team
        );
        gameEntities.addFieldEffect(explosion);
        world.addBody(explosion.getBody());
    }

    /**
     * Add a field effect to the game world (used by event system).
     */
    private void addFieldEffectToWorld(FieldEffect fieldEffect) {
        gameEntities.addFieldEffect(fieldEffect);
        world.addBody(fieldEffect.getBody());
    }

    /**
     * Add a power-up to the game world (used by event system).
     */
    private void addPowerUpToWorld(PowerUp powerUp) {
        gameEntities.addPowerUp(powerUp);
        world.addBody(powerUp.getBody());
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
    }

}

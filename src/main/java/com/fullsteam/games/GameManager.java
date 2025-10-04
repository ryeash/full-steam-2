package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.ai.AIGameHelper;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AIPlayerManager;
import com.fullsteam.model.DamageApplicationType;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.Beam;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.GameEntities;
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
import org.dyn4j.dynamics.TimeStep;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.DetectFilter;
import org.dyn4j.world.PhysicsWorld;
import org.dyn4j.world.World;
import org.dyn4j.world.listener.StepListener;
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

public class GameManager implements StepListener<Body> {
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

    protected final ObjectMapper objectMapper;

    @Getter
    protected long gameStartTime;
    protected boolean gameRunning = false;
    private final long aiCheckIntervalMs;
    private long lastAICheckTime = 0;
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
        this.world.addStepListener(this);

        // Initialize game event manager
        this.gameEventManager = new GameEventManager(gameEntities, this::send);

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
        AIPlayer aiPlayer = AIPlayerManager.createAIPlayerWithPersonality(Config.nextId(), spawnPoint.x, spawnPoint.y, personalityType, assignedTeam);
        aiPlayer.setHealth(gameConfig.getPlayerMaxHealth());

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
            aiPlayerManager.update(gameEntities, deltaTime);
            aiPlayerManager.getAllPlayerInputs().forEach((playerId, input) -> {
                gameEntities.getPlayerInputs().put(playerId, input);
            });
            checkAndAdjustAIPlayers();

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);
            gameEntities.updateAll(deltaTime);
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

            processHomingProjectiles();
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
                    if (teleportPad.teleportPlayer(player)) {
                        log.info("Player {} teleported via pad {}", player.getId(), teleportPad.getId());
                    }
                }
            }
        }

        // Process beam damage for DOT and burst beams
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
                        case BURST:
                            // Handle burst damage with proper timing
                            beam.updateTimeSinceLastDamage(deltaTime);
                            if (beam.shouldApplyBurstDamage()) {
                                double burstDamage = beam.processBurstDamage(player);
                                if (burstDamage > 0) {
                                    // Apply damage and check if player died
                                    if (player.takeDamage(burstDamage)) {
                                        killPlayer(player, beamOwner);
                                    }
                                }
                                beam.resetBurstTimer();
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

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y, assignedTeam);
        player.setHealth(gameConfig.getPlayerMaxHealth());
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

            // Handle primary weapon fire (left click)
            if (input.isLeft()) {
                // Check if weapon fires beams or projectiles
                if (player.getCurrentWeapon().getOrdinance().isBeamType()) {
                    // Handle beam weapons
                    Beam beam = player.shootBeam();
                    if (beam != null) {
                        // Update beam's effective end point based on obstacle collisions
                        Vector2 effectiveEnd = findBeamObstacleIntersection(beam.getStartPoint(), beam.getEndPoint());
                        beam.setEffectiveEndPoint(effectiveEnd);

                        gameEntities.addBeam(beam);
                        world.addBody(beam.getBody());

                        // Process initial hit for instant damage beams
                        if (beam.getDamageApplicationType() == DamageApplicationType.INSTANT) {
                            processBeamInitialHit(beam);
                        }
                    }
                } else {
                    // Handle projectile weapons
                    for (Projectile projectile : player.shoot()) {
                        if (projectile != null) {
                            gameEntities.addProjectile(projectile);
                            world.addBody(projectile.getBody());
                        }
                    }
                }
            }

            // Handle utility weapon fire (right click/altFire)
            if (input.isAltFire()) {
                Player.UtilityActivation activation = player.useUtility();
                if (activation != null) {
                    processUtilityActivation(activation);
                }
            }
        }
    }

    /**
     * Process utility weapon activation and create appropriate effects
     */
    protected void processUtilityActivation(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;

        if (utility.isFieldEffectBased()) {
            // Create FieldEffect for area-based utilities
            createUtilityFieldEffect(activation);
        } else if (utility.isEntityBased()) {
            // Create custom entity for complex utilities
            createUtilityEntity(activation);
        } else if (utility.isBeamBased()) {
            // Create beam for line-of-sight utilities
            createUtilityBeam(activation);
        }
    }

    /**
     * Create a FieldEffect for utility weapons that use the field effect system
     */
    private void createUtilityFieldEffect(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;
        FieldEffectType effectType = utility.getFieldEffectType();

        // Calculate target position based on utility range and aim direction
        Vector2 targetPos = activation.position.copy();
        if (utility.getRange() > 0) {
            Vector2 offset = activation.direction.copy();
            offset.multiply(utility.getRange());
            targetPos.add(offset);
        }

        // Create the field effect
        FieldEffect fieldEffect = new FieldEffect(
                Config.nextId(),
                activation.playerId,
                effectType,
                targetPos,
                utility.getRadius(), // Use explicit radius
                utility.getDamage(),
                effectType.getDefaultDuration(),
                activation.team
        );

        gameEntities.addFieldEffect(fieldEffect);
        world.addBody(fieldEffect.getBody());

        log.info("Created {} field effect at ({}, {}) with radius {} for player {}",
                effectType.name(), targetPos.x, targetPos.y, utility.getRadius(), activation.playerId);
    }

    /**
     * Create custom entities for utility weapons that need complex behavior
     */
    private void createUtilityEntity(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;

        switch (utility) {
            case TURRET_CONSTRUCTOR:
                createTurret(activation);
                break;
            case WALL_BUILDER:
                createBarrier(activation);
                break;
            case NET_LAUNCHER:
                createNetProjectile(activation);
                break;
            case MINE_LAYER:
                createProximityMine(activation);
                break;
            case TELEPORTER:
                createTeleportPad(activation);
                break;
            default:
                log.warn("Unknown entity-based utility weapon: {}", utility.getDisplayName());
                break;
        }
    }

    private void createTurret(Player.UtilityActivation activation) {
        // Calculate placement position slightly in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(50.0); // Place 50 units in front
        placement.add(offset);

        Turret turret = new Turret(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                15.0 // 15 second lifespan
        );

        gameEntities.addTurret(turret);
        world.addBody(turret.getBody());
        log.info("Player {} deployed turret at ({}, {})", activation.playerId, placement.x, placement.y);
    }

    private void createBarrier(Player.UtilityActivation activation) {
        // Calculate placement position in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(40.0); // Place 40 units in front
        placement.add(offset);

        Obstacle barrier = Obstacle.createPlayerBarrier(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                activation.direction,
                20.0 // 20 second lifespan
        );

        gameEntities.addObstacle(barrier);
        world.addBody(barrier.getBody());
    }

    private void createNetProjectile(Player.UtilityActivation activation) {
        // Fire net projectile in aim direction
        Vector2 velocity = activation.direction.copy();
        velocity.multiply(300.0); // Net projectile speed

        NetProjectile netProjectile = new NetProjectile(
                Config.nextId(),
                activation.playerId,
                activation.team,
                activation.position,
                velocity,
                5.0 // 5 second time to live
        );

        gameEntities.addNetProjectile(netProjectile);
        world.addBody(netProjectile.getBody());
    }

    private void createProximityMine(Player.UtilityActivation activation) {
        // Place mine at player's current position
        FieldEffect mine = new FieldEffect(
                Config.nextId(),
                activation.playerId,
                FieldEffectType.PROXIMITY_MINE,
                activation.position,
                45.0, // Small visual radius for mine
                1.0, // Damage (not used for mines, explosion handles damage)
                15.0, // 30 second lifespan
                activation.team
        );

        gameEntities.addFieldEffect(mine);
        world.addBody(mine.getBody());
        log.info("Player {} placed proximity mine at ({}, {})", activation.playerId, activation.position.x, activation.position.y);
    }

    private void createTeleportPad(Player.UtilityActivation activation) {
        // Calculate placement position slightly in front of player
        Vector2 placement = activation.position.copy();
        Vector2 offset = activation.direction.copy();
        offset.multiply(30.0); // Place 30 units in front
        placement.add(offset);

        TeleportPad teleportPad = new TeleportPad(
                Config.nextId(),
                activation.playerId,
                activation.team,
                placement,
                60.0 // 60 second lifespan
        );

        gameEntities.addTeleportPad(teleportPad);
        world.addBody(teleportPad.getBody());

        // Try to link with existing teleport pad from same player
        linkTeleportPads(teleportPad, activation.playerId);

        log.info("Player {} placed teleport pad at ({}, {})", activation.playerId, placement.x, placement.y);
    }

    /**
     * Link teleport pads from the same player
     */
    private void linkTeleportPads(TeleportPad newPad, int playerId) {
        // Find existing unlinked teleport pad from same player
        for (TeleportPad existingPad : gameEntities.getAllTeleportPads()) {
            if (existingPad.getId() != newPad.getId() &&
                existingPad.getOwnerId() == playerId &&
                !existingPad.isLinked() &&
                existingPad.isActive()) {

                // Link the pads
                newPad.linkTo(existingPad);
                log.info("Linked teleport pads {} and {} for player {}", newPad.getId(), existingPad.getId(), playerId);
                break; // Only link to one pad
            }
        }
    }

    /**
     * Create a beam for utility weapons that use the beam system
     */
    private void createUtilityBeam(Player.UtilityActivation activation) {
        UtilityWeapon utility = activation.utilityWeapon;
        Ordinance beamOrdinance = utility.getBeamOrdinance();

        // Create beam using the utility's beam ordinance
        Beam utilityBeam = createBeamFromUtility(
                Config.nextId(),
                activation.position,
                activation.direction,
                utility.getRange(),
                utility.getDamage(),
                activation.playerId,
                activation.team,
                beamOrdinance
        );

        // Update beam's effective end point based on obstacle collisions
        Vector2 effectiveEnd = findBeamObstacleIntersection(utilityBeam.getStartPoint(), utilityBeam.getEndPoint());
        utilityBeam.setEffectiveEndPoint(effectiveEnd);

        gameEntities.addBeam(utilityBeam);
        world.addBody(utilityBeam.getBody());

        // Process initial hit for instant damage beams
        if (utilityBeam.getDamageApplicationType() == DamageApplicationType.INSTANT) {
            processBeamInitialHit(utilityBeam);
        }
    }

    /**
     * Factory method to create utility beams (simplified single-class approach)
     */
    private Beam createBeamFromUtility(int beamId, Vector2 startPoint, Vector2 direction,
                                       double range, double damage, int ownerId, int ownerTeam,
                                       Ordinance ordinance) {
        // Single Beam class handles all beam types via ordinance
        return new Beam(beamId, startPoint, direction, range, damage, ownerId, ownerTeam, ordinance);
    }

    /**
     * Process initial hit for instant damage beams (like laser, railgun)
     */
    private void processBeamInitialHit(Beam beam) {
        List<Player> playersInPath = getPlayersInBeamPath(beam);
        Player beamOwner = gameEntities.getPlayer(beam.getOwnerId());

        for (Player player : playersInPath) {
            if (beam.canAffectPlayer(player)) {
                double damageAmount = beam.processInitialHit(player);

                if (damageAmount > 0) {
                    // Apply damage and check if player died
                    if (player.takeDamage(damageAmount)) {
                        killPlayer(player, beamOwner);
                    }
                }

                // For non-piercing beams, stop after first hit
                if (!beam.canPierceTargets()) {
                    // TODO: raycast to set the end point of the beam to the intersection point on the player
                    break;
                }
            }
        }
    }

    /**
     * Get all players that intersect with a beam's path using dyn4j ray casting
     * This method accounts for obstacles blocking the beam path and is much more accurate
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

        return playersInPath;
    }

    /**
     * Find the intersection point between a beam and obstacles using dyn4j ray casting
     * This is much more accurate and efficient than manual line-segment collision detection
     */
    private Vector2 findBeamObstacleIntersection(Vector2 beamStart, Vector2 beamEnd) {
        // Create ray from beam start to beam end
        Vector2 direction = beamEnd.copy();
        direction.subtract(beamStart);
        double maxDistance = direction.getMagnitude();
        direction.normalize();

        Ray ray = new Ray(beamStart, direction);

        // Use dyn4j's built-in ray casting to find the closest obstacle intersection
        List<RaycastResult<Body, BodyFixture>> results = world.raycast(ray, maxDistance, new DetectFilter<>(true, true, null));
        RaycastResult<Body, BodyFixture> result = null;
        if (!results.isEmpty()) {
            // Find the closest result
            result = results.get(0);
            for (RaycastResult<Body, BodyFixture> r : results) {
                if (r.getBody().getUserData() instanceof Obstacle && r.getRaycast().getDistance() < result.getRaycast().getDistance()) {
                    result = r;
                }
            }
        }

        if (result != null) {
            // Return the intersection point
            return result.getRaycast().getPoint();
        } else {
            // No obstacle intersection found, return original end point
            return beamEnd.copy();
        }
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
            playerState.put("weapon", 0); // Always primary weapon now
            playerState.put("utilityWeapon", player.getUtilityWeapon().name());
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
            beamState.put("canPierce", beam.canPierceTargets());
            beamStates.add(beamState);
        }
        gameState.put("beams", beamStates);

        broadcast(gameState);
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

            // Add detailed shape data for client rendering
            obsData.putAll(obstacle.getShapeData());

            obstacles.add(obsData);
        }
        state.put("obstacles", obstacles);

        return state;
    }

    public void killPlayer(Player victim, Player shooter) {
        if (shooter != null) {
            shooter.addKill();
        }
        victim.die();

        // Assign a new spawn point for the victim when they respawn
        Vector2 newSpawnPoint = findVariedSpawnPointForTeam(victim.getTeam());
        victim.setRespawnPoint(newSpawnPoint);
        log.debug("Player {} will respawn at new location ({}, {}) on team {}",
                victim.getId(), newSpawnPoint.x, newSpawnPoint.y, victim.getTeam());

        // Broadcast kill event
        String killerName = shooter != null ? shooter.getPlayerName() : "Unknown";
        String victimName = victim.getPlayerName();
        String weaponName = shooter != null ? shooter.getCurrentWeapon().getName() : "Unknown weapon";

        gameEventManager.broadcastKill(killerName, victimName, weaponName);

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
     * Apply homing behavior to projectiles with homing effect
     */
    private void processHomingProjectiles() {
        CollisionProcessor collisionProcessor = getCollisionProcessor();
        if (collisionProcessor != null) {
            for (Projectile projectile : gameEntities.getAllProjectiles()) {
                if (projectile.isActive()) {
                    collisionProcessor.getBulletEffectProcessor().applyHomingBehavior(projectile);
                }
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
        processHomingProjectiles();
    }
}

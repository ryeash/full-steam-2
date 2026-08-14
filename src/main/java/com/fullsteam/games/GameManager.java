package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.RandomNames;
import com.fullsteam.ai.AIPersonality;
import com.fullsteam.ai.AIPlayer;
import com.fullsteam.ai.AIPlayerManager;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectCircle;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.PlayerSessionState;
import com.fullsteam.model.Rules;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.Obstacle;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.TeamSpawnManager;
import com.fullsteam.physics.Turret;
import com.fullsteam.physics.UtilityActivation;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import lombok.Getter;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Getter
public class GameManager {
    protected static final Logger log = LoggerFactory.getLogger(GameManager.class);

    /**
     * How long (ms) a session may sit in {@link PlayerSessionState#LOBBY} before
     * being soft-downgraded to {@link PlayerSessionState#SPECTATOR}, freeing
     * the slot for AI fill.
     */
    public static final long LOBBY_TIMEOUT_MS = 180_000L;

    public static final long FRAME_RATE = 30L;
    public static final long FRAME_PERIOD = 1000L / FRAME_RATE; // ms/s / frame/s = ms/frame

    protected final String gameId;
    protected final GameConfig gameConfig;
    protected final GameEntities gameEntities;
    protected final CollisionProcessor collisionProcessor;
    protected final AIPlayerManager aiPlayerManager;
    protected final TeamSpawnManager teamSpawnManager;
    protected final TerrainGenerator terrainGenerator;
    protected final GameEventManager gameEventManager;
    protected final RuleSystem ruleSystem;
    protected final WeaponSystem weaponSystem;
    protected final UtilitySystem utilitySystem;
    protected final EntitySpawner entitySpawner;
    protected final SpawnPointManager spawnPointManager;
    protected final GameStateSerializer gameStateSerializer;
    protected final BinaryGameStateSerializer binaryGameStateSerializer;
    protected final ObjectMapper objectMapper;

    protected long gameStartTime;
    protected boolean gameRunning = false;
    private final long aiCheckIntervalMs;
    private long lastAICheckTime = 0;
    private final World<Body> world;
    private final ScheduledFuture<?> shutdownHook;
    private double lastUpdateTime = System.nanoTime() / 1e9;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile boolean hadHumanPlayers = false;
    private final GameLobby gameLobby;

    public GameManager(String gameId, GameConfig gameConfig, ObjectMapper objectMapper) {
        this(gameId, gameConfig, objectMapper, null);
    }

    public GameManager(String gameId, GameConfig gameConfig, ObjectMapper objectMapper, GameLobby gameLobby) {
        this.gameId = gameId;
        this.gameConfig = gameConfig;
        this.objectMapper = objectMapper;
        this.gameLobby = gameLobby;
        this.gameStartTime = System.currentTimeMillis();
        this.aiPlayerManager = new AIPlayerManager(gameConfig);

        // Initialize AI management settings from config
        this.aiCheckIntervalMs = gameConfig.getAiCheckIntervalMs();
        this.teamSpawnManager = new TeamSpawnManager(gameConfig.getWorldWidth(), gameConfig.getWorldHeight(), gameConfig.getTeamCount());
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

        this.weaponSystem = new WeaponSystem(gameEntities, world);
        this.terrainGenerator = new TerrainGenerator(world, gameConfig);
        this.utilitySystem = new UtilitySystem(gameEntities, world, this::isPositionClearOfObstacles);
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
                teamSpawnManager
        );

        this.binaryGameStateSerializer = new BinaryGameStateSerializer(
                gameConfig,
                gameEntities,
                ruleSystem
        );
        this.binaryGameStateSerializer.setGameManager(this);

        entitySpawner.createWorldBoundaries();
        entitySpawner.createObstacles();
        entitySpawner.createFlags();
        entitySpawner.createOddballNpcs();
        entitySpawner.createKothZones();
        entitySpawner.createHeadquarters();

        // Initialize event system if enabled (must be after terrain generation)
        ruleSystem.initializeEventSystem(
                terrainGenerator,
                gameConfig.getWorldWidth(),
                gameConfig.getWorldHeight()
        );

        // Add initial AI players to make the game more interesting from the start (if enabled)
        if (gameConfig.isEnableAIFilling()) {
            int initialAICount = getMaxPlayers();
            int added = addMixedAIPlayers(initialAICount);
            if (added > 0) {
                log.debug("Added {} initial AI players to game {} for better gameplay", added, gameId);
            }
        } else {
            log.debug("AI filling disabled for game {} - no initial AI players added", gameId);
        }

        this.shutdownHook = Config.EXECUTOR.scheduleAtFixedRate(this::update, FRAME_PERIOD, FRAME_PERIOD, TimeUnit.MILLISECONDS);
    }

    public boolean addPlayer(PlayerSession playerSession) {
        // Spectators don't consume a player slot, so they're only blocked by
        // game-over. Real players (LOBBY/PLAYING) are gated on cap + lock + game-over.
        boolean asSpectator = playerSession.getState() == PlayerSessionState.SPECTATOR;

        if (ruleSystem.isGameOver()) {
            log.debug("{} {} attempted to join finished game {}",
                    asSpectator ? "Spectator" : "Player", playerSession.getPlayerId(), gameId);
            return false;
        }

        if (!asSpectator) {
            hadHumanPlayers = true;
            if (getPlayingAndLobbyCount() >= getMaxPlayers()) {
                return false;
            }
            if (isGameLocked()) {
                log.debug("Player {} attempted to join locked game {}",
                        playerSession.getPlayerId(), gameId);
                return false;
            }
        }

        gameEntities.addPlayerSession(playerSession);
        onPlayerJoined(playerSession);
        return true;
    }

    /**
     * Add a balanced mix of AI players with different personalities.
     *
     * @param count Number of AI players to add
     * @return Number of AI players actually added
     */
    public int addMixedAIPlayers(int count) {
        int added = 0;
        for (int i = 0; i < count; i++) {
            if (addAIPlayer()) {
                added++;
            } else {
                log.warn("Could not add AI player {} of {} - game may be full", i + 1, count);
                break;
            }
        }
        log.debug("Added {} AI players to game {}", added, getGameId());
        return added;
    }

    /**
     * Reason a {@link #addPlayer} call failed; used by the connection layer to
     * produce a typed {@code joinRejected} message before closing the socket.
     */
    public JoinRejectReason determineJoinRejectReason(PlayerSession playerSession) {
        if (ruleSystem.isGameOver()) {
            return JoinRejectReason.GAME_ENDED;
        }
        if (playerSession.getState() == PlayerSessionState.SPECTATOR) {
            return JoinRejectReason.GAME_ENDED; // only game-over rejects spectators
        }
        if (isGameLocked()) {
            return JoinRejectReason.GAME_LOCKED;
        }
        return JoinRejectReason.GAME_FULL;
    }

    /**
     * Reason a join attempt was rejected. Mirrors the wire protocol's
     * {@code joinRejected.reason} string.
     */
    public enum JoinRejectReason {
        GAME_FULL,
        GAME_LOCKED,
        GAME_NOT_FOUND,
        GAME_ENDED
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

    /**
     * Handle a client's {@code readyToSpawn} request - the player has finished
     * customizing their loadout and wants to enter the game.
     *
     * <ul>
     *   <li>{@code LOBBY} → {@code PLAYING}: always succeeds (slot was already
     *       reserved at WebSocket open).</li>
     *   <li>{@code SPECTATOR} → {@code PLAYING}: only succeeds if there is a
     *       free player slot. AI may be evicted to make room. Sends a typed
     *       {@code joinRejected} reply on failure.</li>
     *   <li>{@code PLAYING}: no-op (idempotent).</li>
     * </ul>
     */
    public void handleReadyToSpawn(int playerId, PlayerConfigRequest request) {
        PlayerSession playerSession = gameEntities.getPlayerSession(playerId);
        if (playerSession == null) {
            return;
        }
        WeaponConfig weaponConfig = request != null ? request.getWeaponConfig() : null;
        UtilityWeapon utilityWeapon = null;
        if (request != null && request.getUtilityWeapon() != null) {
            try {
                utilityWeapon = UtilityWeapon.valueOf(request.getUtilityWeapon());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown utility weapon '{}' in readyToSpawn for player {}",
                        request.getUtilityWeapon(), playerId);
            }
        }

        // Apply chosen name only when it comes from the curated list.
        applyPlayerNameFromRequest(playerSession, request);

        switch (playerSession.getState()) {
            case PLAYING -> log.debug("Ignoring readyToSpawn from already-playing session {}", playerId);
            case LOBBY -> spawnPlayerFromSession(playerSession, weaponConfig, utilityWeapon);
            case SPECTATOR -> spawnFromSpectator(playerSession, weaponConfig, utilityWeapon);
        }
    }

    private void spawnFromSpectator(PlayerSession playerSession,
                                    WeaponConfig weaponConfig,
                                    UtilityWeapon utilityWeapon) {
        if (ruleSystem.isGameOver()) {
            sendJoinRejected(playerSession, "GAME_ENDED");
            return;
        }
        if (isGameLocked()) {
            sendJoinRejected(playerSession, "GAME_LOCKED");
            return;
        }
        // Free a slot by evicting an AI if we're at the player cap
        if (getPlayingAndLobbyCount() >= getMaxPlayers()) {
            if (gameConfig.isEnableAIFilling() && getAIPlayerCount() > 0) {
                removeExcessAIPlayers(1);
            } else {
                sendJoinRejected(playerSession, "GAME_FULL");
                return;
            }
        }
        spawnPlayerFromSession(playerSession, weaponConfig, utilityWeapon);
    }

    private void sendJoinRejected(PlayerSession playerSession, String reason) {
        send(playerSession.getSession(), Map.of(
                "type", "joinRejected",
                "reason", reason
        ));
    }

    public void send(WebSocketSession session, Object message) {
        try {
            if (session.isWritable() && session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendAsync(json);
            }
        } catch (WebSocketSessionException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                log.error("Error sending message", e);
            } else {
                log.debug("interrupted sending message, likely game was shutdown", e);
            }
        }
    }

    public void sendBinary(WebSocketSession session, byte[] bytes) {
        try {
            if (session.isWritable() && session.isOpen()) {
                session.sendAsync(bytes);
            }
        } catch (WebSocketSessionException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                log.error("Error sending binary message", e);
            } else {
                log.debug("interrupted sending binary message, likely game was shutdown", e);
            }
        }
    }

    public void broadcast(Object message) {
        gameEntities.getPlayerSessions()
                .values()
                .forEach(player -> {
                    if (player.getSession().isOpen()) {
                        send(player.getSession(), message);
                    }
                });
    }

    public void broadcastBinary(byte[] bytes) {
        gameEntities.getPlayerSessions()
                .values()
                .forEach(player -> {
                    if (player.getSession().isOpen()) {
                        sendBinary(player.getSession(), bytes);
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

    /**
     * Number of sessions that are reserving a player slot - both {@code LOBBY}
     * (yet-to-spawn) and {@code PLAYING}. Spectators are excluded. This is the
     * count that gates new joins against {@link #getMaxPlayers()}.
     */
    public int getPlayingAndLobbyCount() {
        return (int) gameEntities.getPlayerSessions()
                .values()
                .stream()
                .filter(player -> player.getSession().isOpen())
                .filter(s -> s.getState() != PlayerSessionState.SPECTATOR)
                .count();
    }

    /**
     * Get the number of spectators in the game.
     */
    public int getSpectatorCount() {
        return (int) gameEntities.getPlayerSessions().values().stream()
                .filter(PlayerSession::isSpectator)
                .count();
    }

    /**
     * Get list of all spectators.
     */
    public List<PlayerSession> getSpectators() {
        return gameEntities.getPlayerSessions().values().stream()
                .filter(PlayerSession::isSpectator)
                .collect(Collectors.toList());
    }

    /**
     * Check if the game is locked to new players based on elapsed time.
     */
    public boolean isGameLocked() {
        if (!gameConfig.getRules().shouldLockGame()) {
            return false; // Game never locks
        }
        if (ruleSystem.isCountdown()) {
            return false; // Never lock during pre-game countdown
        }

        double elapsedSeconds = (System.currentTimeMillis() - ruleSystem.getMatchStartTime()) / 1000.0;
        return elapsedSeconds >= gameConfig.getRules().getLockGameAfterSeconds();
    }

    public void shutdown() {
        shutdown.set(true);
        shutdownHook.cancel(true);
    }

    /**
     * Add an AI player with a specific personality type.
     */
    public boolean addAIPlayer() {
        if (gameEntities.getAllPlayers().size() >= getMaxPlayers()) {
            return false;
        }

        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = spawnPointManager.findVariedSpawnPointForTeam(assignedTeam);
        String name = RandomNames.randomName();
        AIPlayer aiPlayer = AIPlayerManager.createAIPlayerWithName(Config.nextEntityId(), name, AIPersonality.typeFromName(name), spawnPoint.x, spawnPoint.y, assignedTeam, gameConfig.getPlayerMaxHealth());
        aiPlayer.setHealth(gameConfig.getPlayerMaxHealth());

        // Initialize lives based on respawn mode (delegated to RuleSystem)
        ruleSystem.initializePlayerLives(aiPlayer);
        ruleSystem.assignPlayerRandomWeapons(aiPlayer);

        // Apply spawn invincibility to give AI player time to get their bearings
        StatusEffectManager.applySpawnInvincibility(aiPlayer);

        // Add to game entities
        gameEntities.add(aiPlayer);

        // Add to AI manager
        aiPlayerManager.addAIPlayer(aiPlayer);

        this.binaryGameStateSerializer.triggerLowFreqSync();

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
            log.debug("Removed AI player {}", playerId);

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
        return (int) gameEntities.getAllPlayers()
                .stream()
                .map(Player::getId)
                .filter(aiPlayerManager::isAIPlayer)
                .count();
    }

    /**
     * Check if a player is an AI player.
     */
    public boolean isAIPlayer(int playerId) {
        return aiPlayerManager.isAIPlayer(playerId);
    }

    public boolean hasHumanPlayers() {
        return getPlayingAndLobbyCount() > 0;
    }

    public boolean hadHumanPlayers() {
        return hadHumanPlayers;
    }

    /**
     * Assign a player to the team with the fewest members.
     * Ties are broken randomly so teams fill in a balanced, non-deterministic order.
     *
     * @return Team number (0 for FFA, 1+ for team modes)
     */
    private int assignPlayerToTeam() {
        if (gameConfig.isFreeForAll()) {
            return 0;
        }

        int teamCount = gameConfig.getTeamCount();
        // Index 0 unused; indices 1..teamCount hold per-team headcounts.
        int[] teamCounts = new int[teamCount + 1];
        for (Player player : gameEntities.getAllPlayers()) {
            int team = player.getTeam();
            if (team >= 1 && team <= teamCount) {
                teamCounts[team]++;
            }
        }

        // Find the minimum headcount across all teams.
        int minCount = Integer.MAX_VALUE;
        for (int t = 1; t <= teamCount; t++) {
            if (teamCounts[t] < minCount) {
                minCount = teamCounts[t];
            }
        }

        // Collect every team tied at the minimum so tie-breaking is uniformly random.
        List<Integer> candidates = new ArrayList<>();
        for (int t = 1; t <= teamCount; t++) {
            if (teamCounts[t] == minCount) {
                candidates.add(t);
            }
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Manually trigger AI player adjustment based on current settings.
     * <p>
     * This does three things in order:
     * <ol>
     *   <li>Adjust the <em>total</em> AI count so the lobby stays at {@code maxPlayers}.</li>
     *   <li>For team modes, rebalance AI across teams so no team is more than one
     *       player larger than another.</li>
     * </ol>
     */
    public void adjustAIPlayers() {
        if (!gameConfig.isEnableAIFilling()) {
            return;
        }

        int totalPlayers = gameEntities.getAllPlayers().size();
        int humanPlayers = totalPlayers - getAIPlayerCount();

        // Step 1 – adjust total count.
        if (totalPlayers < getMaxPlayers()) {
            int aiToAdd = getMaxPlayers() - totalPlayers;
            int added = addMixedAIPlayers(aiToAdd);
            if (added > 0) {
                log.debug("Auto-filled {} AI players (total: {})", added, totalPlayers + added);
            }
        } else if (humanPlayers > 0 && totalPlayers > getMaxPlayers()) {
            int aiToRemove = totalPlayers - getMaxPlayers();
            int removed = removeExcessAIPlayers(aiToRemove);
            if (removed > 0) {
                log.debug("Removed {} excess AI players (total remaining: {})", removed, totalPlayers - removed);
            }
        }

        // Step 2 – rebalance teams (no-op in FFA).
        if (!gameConfig.isFreeForAll()) {
            rebalanceAITeams();
        }
    }

    /**
     * Redistribute AI players between teams so that each team's headcount is
     * within one of every other team's headcount.
     * <p>
     * The algorithm iterates until stable: it finds the most- and least-populated
     * teams and, if the gap is ≥ 2, removes one AI from the over-full team and
     * immediately adds a new one (which {@link #assignPlayerToTeam()} will place
     * on the under-full team).
     */
    private void rebalanceAITeams() {
        int teamCount = gameConfig.getTeamCount();
        if (teamCount < 2) {
            return;
        }

        for (int iteration = 0; iteration < teamCount * 2; iteration++) {
            // Build a fresh per-team headcount each pass.
            int[] teamCounts = new int[teamCount + 1];
            Map<Integer, List<Integer>> aiByTeam = new HashMap<>();
            for (Player p : gameEntities.getAllPlayers()) {
                int t = p.getTeam();
                if (t < 1 || t > teamCount) {
                    continue;
                }
                teamCounts[t]++;
                if (isAIPlayer(p.getId())) {
                    aiByTeam.computeIfAbsent(t, k -> new ArrayList<>()).add(p.getId());
                }
            }

            // Find most- and least-populated teams.
            int maxTeam = 1, minTeam = 1;
            for (int t = 2; t <= teamCount; t++) {
                if (teamCounts[t] > teamCounts[maxTeam]) {
                    maxTeam = t;
                }
                if (teamCounts[t] < teamCounts[minTeam]) {
                    minTeam = t;
                }
            }

            // Already balanced (gap ≤ 1) — done.
            if (teamCounts[maxTeam] - teamCounts[minTeam] <= 1) {
                break;
            }

            // Can only fix the imbalance if the over-full team has a removable AI.
            List<Integer> aiOnMaxTeam = aiByTeam.getOrDefault(maxTeam, List.of());
            if (aiOnMaxTeam.isEmpty()) {
                break; // all excess players on that team are human — can't move
            }

            int aiToMove = aiOnMaxTeam.getFirst();
            removeAIPlayer(aiToMove);
            // assignPlayerToTeam() will now direct the replacement to minTeam.
            addMixedAIPlayers(1);
            log.debug("Rebalanced AI: moved one player from team {} ({}) to team {} ({})",
                    maxTeam, teamCounts[maxTeam], minTeam, teamCounts[minTeam]);
        }
    }

    /**
     * Remove {@code count} AI players, always pulling from the most-populated
     * team first so that removal keeps (or improves) team balance.
     */
    private int removeExcessAIPlayers(int count) {
        int removed = 0;
        for (int i = 0; i < count; i++) {
            // Re-scan each iteration because team counts change as we remove.
            int teamCount = gameConfig.getTeamCount();
            int[] teamCounts = new int[Math.max(teamCount + 1, 1)];
            // In FFA there are no teams; just pick any AI.
            for (Player p : gameEntities.getAllPlayers()) {
                int t = p.getTeam();
                if (t >= 1 && t < teamCounts.length) {
                    teamCounts[t]++;
                }
            }

            // Find the team with the most total players (AI or human).
            int targetTeam = 0; // 0 = FFA / don't filter by team
            if (!gameConfig.isFreeForAll()) {
                int maxCount = -1;
                for (int t = 1; t <= teamCount; t++) {
                    if (teamCounts[t] > maxCount) {
                        maxCount = teamCounts[t];
                        targetTeam = t;
                    }
                }
            }

            // Remove one AI from that team (or any AI in FFA).
            boolean found = false;
            for (Player p : gameEntities.getAllPlayers()) {
                if (!isAIPlayer(p.getId())) {
                    continue;
                }
                if (targetTeam != 0 && p.getTeam() != targetTeam) {
                    continue;
                }
                removeAIPlayer(p.getId());
                removed++;
                found = true;
                break;
            }
            if (!found) {
                break; // no more AI to remove
            }
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

    public void update() {
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

            purgeClosedSessions();

            // Update rule systems (rounds, victory conditions, respawns)
            ruleSystem.update(deltaTime);

            // Process individual player respawns based on rules
            processPlayerRespawns();

            // Soft-downgrade idle lobby sessions so freed slots can be back-filled
            processLobbyTimeouts();

            aiPlayerManager.update(gameEntities, deltaTime);
            gameEntities.getPlayerInputs().putAll(aiPlayerManager.getAllPlayerInputs());
            checkAndAdjustAIPlayers();

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);
            gameEntities.updateAll(deltaTime);
            if (!ruleSystem.isCountdown()) {
                updateCarriedFlags(); // Update flag positions for carried flags
                collisionProcessor.updateKothZones(deltaTime);
            }
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

            updateUtilityEntities();
            updateDefenseLaserBeamEndpoints();

            // Reset vision flags before physics collision pass re-evaluates them
            gameEntities.getAllPlayers().forEach(p -> p.setVisionObscured(false));

            world.updatev(deltaTime);

            gameEntities.runPostUpdateHooks();
            gameEntities.removeInactiveEntities();
            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    private void purgeClosedSessions() {
        List<PlayerSession> closedSessions = gameEntities.getPlayerSessions().values().stream()
                .filter(session -> session.getSession() != null && !session.getSession().isOpen())
                .toList();

        if (closedSessions.isEmpty()) {
            return;
        }

        for (PlayerSession closedSession : closedSessions) {
            log.info("Purging closed WebSocket session for player {} in game {}", closedSession.getPlayerId(), gameId);
            removePlayer(closedSession.getPlayerId());
            if (closedSession.isCountedInGlobalPlayerCount()) {
                if (gameLobby != null) {
                    gameLobby.decrementPlayerCount();
                }
                closedSession.setCountedInGlobalPlayerCount(false);
            }
        }

        if ((!hasHumanPlayers() || ruleSystem.isGameOver()) && gameLobby != null) {
            gameLobby.removeGame(gameId);
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
            for (int i = 0; i < defenseLaser.getBeams().size(); i++) {
                FieldEffectBeam beam = defenseLaser.getBeams().get(i);
                List<Vector2> path = weaponSystem.computeBeamPath(beam);
                beam.setEndPoint(path.get(1));
                beam.updateBodyTransform();
            }
        }
    }

    /**
     * Update utility entities and handle their special behaviors
     */
    private void updateUtilityEntities() {
        for (Turret turret : gameEntities.getAllTurrets()) {
            if (turret.isActive()) {
                turret.acquireTarget(gameEntities.getAllPlayers().stream().toList());
                weaponSystem.handleTurretFire(turret);
            }
        }

        if (!ruleSystem.isCountdown()) {
            for (Oddball oddball : gameEntities.getAllOddballNpcs()) {
                if (oddball.isActive()) {
                    oddball.tickAI(Collections.unmodifiableCollection(gameEntities.getAllPlayers()));
                    weaponSystem.handleOddballFire(oddball);
                }
            }
        }
    }

    protected void onPlayerJoined(PlayerSession playerSession) {
        switch (playerSession.getState()) {
            case SPECTATOR -> onSpectatorJoined(playerSession);
            case LOBBY -> onLobbyJoined(playerSession);
            case PLAYING ->
                    log.warn("Session {} entered onPlayerJoined already in PLAYING state; ignoring.", playerSession.getPlayerId());
        }
    }

    private void onSpectatorJoined(PlayerSession playerSession) {
        // Send spectator-specific initial game state
        send(playerSession.getSession(), gameStateSerializer.createSpectatorInitialState(getSpectatorCount()));

        log.debug("Spectator {} joined game {} successfully. Total spectators: {}",
                playerSession.getPlayerId(), gameId, getSpectatorCount());

        // Notify players that a spectator joined (subtle notification)
        gameEventManager.broadcastEvent(
                GameEvent.builder()
                        .message("👁️ A spectator joined")
                        .category(GameEvent.EventCategory.INFO)
                        .color(GameEvent.EventCategory.INFO.getDefaultColor())
                        .target(GameEvent.EventTarget.builder()
                                .type(GameEvent.EventTarget.TargetType.ALL)
                                .build())
                        .displayDuration(2000L)
                        .build()
        );
    }

    private void onLobbyJoined(PlayerSession playerSession) {
        // Holds a player slot but has no Player entity yet. The client renders
        // the customization modal until it sends readyToSpawn. No AI rebalance
        // here (the AI pre-fill stays put until the player actually spawns).
        playerSession.setLobbyEnteredAt(System.currentTimeMillis());
        send(playerSession.getSession(), gameStateSerializer.createLobbyInitialState(LOBBY_TIMEOUT_MS, playerSession.getPlayerName()));
        log.debug("Player {} joined game {} in LOBBY state (awaiting loadout). Total sessions: {}",
                playerSession.getPlayerId(), gameId, gameEntities.getPlayerSessions().size());
    }

    /**
     * Materialize a {@link Player} entity for a session that is in LOBBY (or
     * SPECTATOR) state. Caller is responsible for confirming the session is
     * eligible (e.g. that a slot is available when transitioning a SPECTATOR
     * to PLAYING).
     */
    protected void spawnPlayerFromSession(PlayerSession playerSession,
                                          WeaponConfig weaponConfig,
                                          UtilityWeapon utilityWeapon) {
        int assignedTeam = assignPlayerToTeam();
        Vector2 spawnPoint = spawnPointManager.findVariedSpawnPointForTeam(assignedTeam);
        log.debug("Player {} spawning in game {} at spawn point ({}, {}) on team {}",
                playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y, assignedTeam);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(),
                spawnPoint.x, spawnPoint.y, assignedTeam, gameConfig.getPlayerMaxHealth());
        player.setHealth(gameConfig.getPlayerMaxHealth());

        // Apply the loadout the client chose before we add the player to the world
        if (weaponConfig != null || utilityWeapon != null) {
            WeaponConfig primary = weaponConfig != null ? weaponConfig : WeaponConfig.ASSAULT_RIFLE_PRESET;
            UtilityWeapon utility = utilityWeapon != null ? utilityWeapon : UtilityWeapon.HEAL_ZONE;
            player.applyWeaponConfig(primary, utility);
        }

        // Apply spawn invincibility to give player time to get their bearings

        gameEntities.add(player);
        ruleSystem.initializePlayerLives(player);
        ruleSystem.assignPlayerRandomWeapons(player);
        ruleSystem.ensureVipForTeam(assignedTeam);

        playerSession.setState(PlayerSessionState.PLAYING);
        send(playerSession.getSession(), gameStateSerializer.createInitialGameState(player));
        log.debug("Player {} ({}) spawned in game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId,
                gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());

        // Broadcast player join event with team color
        gameEventManager.broadcastPlayerJoin(playerSession.getPlayerName(), assignedTeam);

        StatusEffectManager.applySpawnInvincibility(player);

        this.binaryGameStateSerializer.triggerLowFreqSync();

        // Adjust AI players when a human player spawns
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
        log.debug("Player {} left game {}", playerSession.getPlayerId(), gameId);

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
            if (ruleSystem.isCountdown()) {
                input.setMoveX(0.0);
                input.setMoveY(0.0);
                input.setLeft(false);
                input.setAltFire(false);
                input.setReload(false);
            }
            player.processInput(input);

            if (!ruleSystem.isCountdown()) {
                // Handle primary weapon fire (delegated to WeaponSystem)
                weaponSystem.handlePrimaryFire(player, input);

                // Handle utility weapon fire (delegated to UtilitySystem)
                if (input.isAltFire()) {
                    UtilityActivation activation = player.useUtility();
                    if (activation != null) {
                        utilitySystem.handleUtilityActivation(activation);
                    }
                }
            }
        }
    }

    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        applyPlayerNameFromRequest(playerSession, request);
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            WeaponConfig primaryConfig = WeaponConfig.ASSAULT_RIFLE_PRESET;
            UtilityWeapon utilityConfig = UtilityWeapon.HEAL_ZONE;
            if (request.getWeaponConfig() != null) {
                primaryConfig = request.getWeaponConfig();
            }
            if (request.getUtilityWeapon() != null) {
                try {
                    utilityConfig = UtilityWeapon.valueOf(request.getUtilityWeapon());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown utility weapon '{}' in config change for player {}, keeping default",
                            request.getUtilityWeapon(), playerSession.getPlayerId());
                }
            }
            player.applyWeaponConfig(primaryConfig, utilityConfig);
            player.setPlayerName(playerSession.getPlayerName());
        }
    }

    /**
     * If {@code request} contains a non-null name that exists in the curated
     * names list, apply it to the session. Silently ignores invalid or missing
     * names so that the server-assigned random name is kept as the fallback.
     */
    private void applyPlayerNameFromRequest(PlayerSession playerSession, PlayerConfigRequest request) {
        if (request == null || request.getPlayerName() == null) {
            return;
        }
        String name = request.getPlayerName().trim();
        if (RandomNames.getNames().contains(name)) {
            playerSession.setPlayerName(name);
        } else {
            log.warn("Player {} submitted non-curated name '{}', keeping assigned name",
                    playerSession.getPlayerId(), name);
        }
    }

    public int getMaxPlayers() {
        return gameConfig.getMaxPlayers();
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

                    log.debug("Flag {} dropped at ({}, {}) - carrier {} inactive",
                            flag.getId(), flag.getPosition().x, flag.getPosition().y, carrierId);
                }
            }
        }
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
            double minDistance = checkRadius + obstacle.getRadius();

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
        boolean anyBlinded = gameEntities.getAllPlayers()
                .stream()
                .anyMatch(Player::isVisionObscured);
        boolean anyLobby = gameEntities.getPlayerSessions().values().stream()
                .anyMatch(s -> s.getState() == PlayerSessionState.LOBBY);

        if (!anyBlinded && !anyLobby) {
            byte[] fullStateBinary = binaryGameStateSerializer.serializeGameState();
            broadcastBinary(fullStateBinary);
            return;
        }

        byte[] fullStateBinary = null;
        byte[] lobbyStateBinary = null;

        for (PlayerSession session : gameEntities.getPlayerSessions().values()) {
            if (!session.getSession().isOpen()) {
                continue;
            }
            if (session.getState() == PlayerSessionState.LOBBY) {
                if (lobbyStateBinary == null) {
                    lobbyStateBinary = binaryGameStateSerializer.serializeLobbyGameState();
                }
                sendBinary(session.getSession(), lobbyStateBinary);
                continue;
            }
            Player player = gameEntities.getPlayer(session.getPlayerId());
            if (player != null && player.isVisionObscured()) {
                byte[] blindedStateBinary = binaryGameStateSerializer.serializeBlindedGameState(player);
                sendBinary(session.getSession(), blindedStateBinary);
            } else {
                if (fullStateBinary == null) {
                    fullStateBinary = binaryGameStateSerializer.serializeGameState();
                }
                sendBinary(session.getSession(), fullStateBinary);
            }
        }
    }

    private void processPlayerRespawns() {
        for (Player player : gameEntities.getAllPlayers()) {
            if (ruleSystem.shouldPlayerRespawn(player)) {
                respawnPlayer(player);
            }
        }
    }

    /**
     * Walk every active {@code LOBBY} session and downgrade any that have been
     * idle longer than {@link #LOBBY_TIMEOUT_MS} to {@code SPECTATOR}. The
     * freed slot is then back-filled by AI (if AI filling is enabled).
     *
     * <p>This is invoked every tick from {@link #update()}; the work is O(n)
     * over sessions and trivially cheap for normal player counts.
     */
    private void processLobbyTimeouts() {
        long now = System.currentTimeMillis();
        boolean anyDowngraded = false;
        for (PlayerSession session : gameEntities.getPlayerSessions().values()) {
            if (session.getState() != PlayerSessionState.LOBBY) {
                continue;
            }
            if (now - session.getLobbyEnteredAt() <= LOBBY_TIMEOUT_MS) {
                continue;
            }
            downgradeLobbyToSpectator(session);
            anyDowngraded = true;
        }
        if (anyDowngraded) {
            adjustAIPlayers();
        }
    }

    /**
     * Move a just-eliminated human player into free-look spectator mode. Their
     * {@link PlayerSession} flips {@code PLAYING → SPECTATOR} and the client is
     * switched to the spectator view. The player's {@link Player} entity is left
     * in place (inactive) so it still contributes to final scores/placement.
     *
     * <p>No-op for AI (no session) and for sessions not currently PLAYING.
     * Deliberately does <em>not</em> trigger AI back-fill of the freed slot — the
     * match shouldn't gain a fresh bot the instant someone is knocked out.
     */
    private void convertEliminatedToSpectator(Player victim) {
        PlayerSession session = gameEntities.getPlayerSession(victim.getId());
        if (session == null || session.getState() != PlayerSessionState.PLAYING) {
            return;
        }
        session.setState(PlayerSessionState.SPECTATOR);
        send(session.getSession(), Map.of("type", "eliminated"));
        // Switch the client over to the spectator view (full game state).
        send(session.getSession(), gameStateSerializer.createSpectatorInitialState(getSpectatorCount()));
        log.debug("Player {} eliminated in last-man-standing game; switched to SPECTATOR.",
                victim.getId());
    }

    private void downgradeLobbyToSpectator(PlayerSession session) {
        session.setState(PlayerSessionState.SPECTATOR);
        send(session.getSession(), Map.of("type", "lobbyTimeout"));
        // Switch the client over to the spectator view (full game state)
        send(session.getSession(), gameStateSerializer.createSpectatorInitialState(getSpectatorCount()));
        log.debug("Lobby session {} timed out after {}ms; downgraded to SPECTATOR.",
                session.getPlayerId(), LOBBY_TIMEOUT_MS);
    }

    public void respawnPlayer(Player player) {
        player.setActive(true);
        player.setHealth(gameConfig.getPlayerMaxHealth());
        player.setRespawnTime(0);
        player.getWeapon().reload();

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

    /**
     * Resolve the entity that owns a piece of ordnance (projectile, beam, field
     * effect, DoT tick) into a killer, then attribute the death. Players are keyed
     * by their positive id; NPC-fired ordnance (e.g. an Oddball) uses a negative
     * sentinel owner id, so those are looked up via {@link GameEntities#getOddballNpc}.
     */
    public void killPlayer(Player victim, int ownerId) {
        Player shooter = gameEntities.getPlayer(ownerId);
        if (shooter != null) {
            killPlayer(victim, shooter, killerIdentityOf(shooter));
            return;
        }
        Oddball oddball = ownerId < 0 ? gameEntities.getOddballNpc(-ownerId) : null;
        killPlayer(victim, null, killerIdentityOf(oddball));
    }

    private void killPlayer(Player victim, Player shooter, KillerIdentity killer) {
        // Check if this was a VIP kill BEFORE calling die() (which clears status effects)
        boolean wasVip = gameConfig.getRules().hasVip() && StatusEffectManager.isVip(victim);

        if (shooter != null) {
            shooter.addKill();
        }
        victim.die();

        // Award points if this was a VIP kill
        if (wasVip) {
            if (shooter != null && shooter.getTeam() != victim.getTeam() && shooter.getTeam() > 0) {
                shooter.getScoring().addVipKill();
                // Broadcast VIP kill event
                gameEventManager.broadcastSystemMessage(String.format("💀 %s eliminated the VIP %s! +1 OBJECTIVE", shooter.getPlayerName(), victim.getPlayerName()));
            }
        }

        boolean wasEliminated = victim.loseLife() || victim.isEliminated();
        log.debug("Player {} died. Lives remaining: {}, Eliminated: {}",
                victim.getId(), victim.getLivesRemaining(), victim.isEliminated());

        if (wasEliminated) {
            // Record elimination time + placement for last-man-standing ordering.
            // Fires for any mode that can eliminate (e.g. LIMITED running out of
            // lives) — was previously gated to the now-removed ELIMINATION respawn
            // mode, which left LIMITED + elimination-victory games without placements.
            victim.setEliminated(true);
            victim.setEliminationTime(System.currentTimeMillis());
            int remainingPlayers = (int) gameEntities.getAllPlayers().stream()
                    .filter(p -> !p.isEliminated())
                    .count();
            victim.setPlacement(remainingPlayers + 1); // +1 because this player just got eliminated

            gameEventManager.broadcastElimination(
                    victim.getPlayerName(),
                    victim.getTeam(),
                    victim.getLivesRemaining()
            );

            // In last-man-standing games, a fully-eliminated player is out for good,
            // so move their client into free-look spectator mode rather than leaving
            // them stranded on their corpse. The Player entity is retained (inactive)
            // so their placement/score still appears on the final scoreboard.
            if (gameConfig.getRules().getVictoryCondition() == VictoryCondition.ELIMINATION) {
                convertEliminatedToSpectator(victim);
            }
        }

        // Drop any flag the victim was carrying
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried() && flag.getCarriedByPlayerId() == victim.getId()) {
                flag.drop();
                log.debug("Player {} died, dropped flag {}", victim.getId(), flag.getId());
            }
        }

        ruleSystem.setRespawnTime(victim);

        // Broadcast kill event with team colors
        String victimName = victim.getPlayerName();
        Integer victimTeam = victim.getTeam();

        gameEventManager.broadcastKill(killer.name(), victimName, killer.weaponName(), killer.team(), victimTeam);

        // Legacy death notification (keeping for compatibility)
        Map<String, Object> deathNotification = new HashMap<>();
        deathNotification.put("type", "playerKilled");
        deathNotification.put("victimId", victim.getId());
        deathNotification.put("killerId", killer.entityId());
        deathNotification.put("killerName", killer.entityId() != null ? killer.name() : null);
        broadcast(deathNotification);
    }

    /**
     * Display + team info used to attribute a kill in the feed. The killer may be a
     * {@link Player} or an NPC such as an {@link Oddball}; {@code entityId} is the
     * source entity's id, or {@code null} when the killer is unknown/environmental.
     */
    private record KillerIdentity(String name, Integer team, String weaponName, Integer entityId) {
        private static final KillerIdentity UNKNOWN =
                new KillerIdentity("Unknown", null, "Unknown weapon", null);
    }

    private static KillerIdentity killerIdentityOf(Player shooter) {
        if (shooter == null) {
            return KillerIdentity.UNKNOWN;
        }
        return new KillerIdentity(
                shooter.getPlayerName(),
                shooter.getTeam(),
                shooter.getCurrentWeapon().getDisplayName(),
                shooter.getId());
    }

    private static KillerIdentity killerIdentityOf(Oddball oddball) {
        if (oddball == null) {
            return KillerIdentity.UNKNOWN;
        }
        return new KillerIdentity(
                oddball.getDisplayName(),
                null,
                oddball.getWeapon().getDisplayName(),
                oddball.getId());
    }

    /**
     * Handle headquarters damage and scoring.
     * Called by CollisionProcessor when HQ is hit.
     */
    public void handleHeadquartersDamage(Headquarters hq, Player attacker, double damage, boolean destroyed) {
        Rules rules = gameConfig.getRules();

        // Credit the attacker for the raw damage dealt; the point value is derived
        // from this in Scoring.total() using the configured points-per-damage.
        if (attacker != null) {
            attacker.getScoring().addHeadquarterDamage(damage);
            log.debug("Player {} (team {}) dealt {} damage to team {} headquarters",
                    attacker.getId(), attacker.getTeam(), damage, hq.getOwnerTeam());
        }

        // Handle destruction
        if (destroyed) {
            if (attacker != null) {
                attacker.getScoring().addHeadquartersDestroyed();
            }

            // Create destruction effect
            createHeadquartersDestructionEffect(hq);
            // Broadcast HQ destruction event
            if (attacker != null) {
                gameEventManager.broadcastHeadquartersDestroyed(hq.getOwnerTeam(), attacker.getTeam());
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
        // Create large explosion effect at HQ location
        gameEntities.add(new FieldEffectCircle(
                -1,
                FieldEffectType.EXPLOSION,
                hq.getPosition(),
                hq.getRadius() * 1.5,
                hq.getRadius() * 1.5,
                100.0,
                2.0,
                0,
                0));
    }

    /**
     * Broadcast a custom game event (convenience overload).
     */
    public void broadcastGameEvent(String message, GameEvent.EventCategory category, String color) {
        gameEventManager.broadcastEvent(GameEvent.builder()
                .message(message)
                .category(category)
                .color(color)
                .target(GameEvent.EventTarget.builder()
                        .type(GameEvent.EventTarget.TargetType.ALL)
                        .build())
                .displayDuration(5000L)
                .build());
    }

    /**
     * Award a capture to a player and their team.
     */
    public void awardCapture(Player player) {
        player.addCapture();
    }
}

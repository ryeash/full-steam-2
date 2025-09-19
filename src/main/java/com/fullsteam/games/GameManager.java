package com.fullsteam.games;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.model.GameInfo;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.*;
import io.micronaut.websocket.WebSocketSession;
import lombok.Getter;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.dynamics.TimeStep;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.PhysicsWorld;
import org.dyn4j.world.World;
import org.dyn4j.world.listener.StepListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameManager implements CollisionProcessor.CollisionHandler, StepListener<Body> {
    protected static final Logger log = LoggerFactory.getLogger(GameManager.class);

    @Getter
    protected final String gameId;
    @Getter
    protected final String gameType;
    protected final GameEntities gameEntities = new GameEntities();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    protected long gameStartTime;
    protected boolean gameRunning = false;

    private final World<Body> world;
    private final Queue<Body> bodiesToAdd = new ConcurrentLinkedQueue<>();
    private final Queue<Body> bodiesToRemove = new ConcurrentLinkedQueue<>();
    private double lastUpdateTime = System.nanoTime() / 1e9;

    public GameManager(String gameId, String gameType) {
        this.gameId = gameId;
        this.gameType = gameType;
        this.gameStartTime = System.currentTimeMillis();

        this.world = new World<>();
        Settings settings = new Settings();
        settings.setMaximumTranslation(300.0);
        this.world.setSettings(settings);
        this.world.setGravity(new Vector2(0, 0));
        this.world.setBounds(new AxisAlignedBounds(Config.WORLD_WIDTH, Config.WORLD_HEIGHT));

        CollisionProcessor collisionProcessor = new CollisionProcessor(this.gameEntities, this);
        this.world.addCollisionListener(collisionProcessor);
        this.world.addStepListener(this);

        createWorldBoundaries();
        createStrategicLocations();
        createObstacles();

        scheduler.scheduleAtFixedRate(this::update, 0, 16, TimeUnit.MILLISECONDS);
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
        if(input != null) {
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
            String json = objectMapper.writeValueAsString(message);
            session.sendSync(json);
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
        scheduler.shutdown();
    }

    protected void update() {
        try {
            double currentTime = System.nanoTime() / 1e9;
            double deltaTime = currentTime - lastUpdateTime;
            lastUpdateTime = currentTime;

            gameEntities.getPlayerInputs().forEach(this::processPlayerInput);

            gameEntities.getAllPlayers().forEach(player -> player.update(deltaTime));

            gameEntities.getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                projectile.update(deltaTime);
                if (!projectile.isActive()) {
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

            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    protected void onPlayerJoined(PlayerSession playerSession) {
        Vector2 spawnPoint = findSpawnPoint();
        log.info("Player {} joining game {} at spawn point ({}, {})", playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y);
        gameEntities.addPlayer(player);
        bodiesToAdd.add(player.getBody());

        send(playerSession.getSession(), createInitialGameState());
        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());
    }

    protected void onPlayerLeft(PlayerSession playerSession) {
        gameEntities.getPlayerInputs().remove(playerSession.getPlayerId());
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            bodiesToRemove.add(player.getBody());
            gameEntities.removePlayer(player.getId());
        }
        log.info("Player {} left game {}", playerSession.getPlayerId(), gameId);
    }

    protected void processPlayerInput(Integer playerId, PlayerInput input) {
        Player player = gameEntities.getPlayer(playerId);
        if (player != null && input != null) {
            player.processInput(input);
            if (input.isLeft()) {
                Projectile projectile = player.shoot();
                if (projectile != null) {
                    gameEntities.addProjectile(projectile);
                    bodiesToAdd.add(projectile.getBody());
                }
            }
        }
    }

    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            if (request.getPlayerName() != null) {
                player.setPlayerName(request.getPlayerName());
            }
            player.applyWeaponConfig(request.getPrimaryWeapon(), request.getSecondaryWeapon());
        }
    }

    protected int getMaxPlayers() {
        return Config.MAX_PLAYERS_PER_GAME;
    }

    private void createWorldBoundaries() {
        double halfWidth = Config.WORLD_WIDTH / 2.0;
        double halfHeight = Config.WORLD_HEIGHT / 2.0;
        double wallThickness = 50.0;

        Body topWall = new Body();
        topWall.addFixture(new Rectangle(Config.WORLD_WIDTH + wallThickness * 2, wallThickness));
        topWall.setMass(MassType.INFINITE);
        topWall.getTransform().setTranslation(0, halfHeight + wallThickness / 2.0);
        bodiesToAdd.add(topWall);

        Body bottomWall = new Body();
        bottomWall.addFixture(new Rectangle(Config.WORLD_WIDTH + wallThickness * 2, wallThickness));
        bottomWall.setMass(MassType.INFINITE);
        bottomWall.getTransform().setTranslation(0, -halfHeight - wallThickness / 2.0);
        bodiesToAdd.add(bottomWall);

        Body leftWall = new Body();
        leftWall.addFixture(new Rectangle(wallThickness, Config.WORLD_HEIGHT));
        leftWall.setMass(MassType.INFINITE);
        leftWall.getTransform().setTranslation(-halfWidth - wallThickness / 2.0, 0);
        bodiesToAdd.add(leftWall);

        Body rightWall = new Body();
        rightWall.addFixture(new Rectangle(wallThickness, Config.WORLD_HEIGHT));
        rightWall.setMass(MassType.INFINITE);
        rightWall.getTransform().setTranslation(halfWidth + wallThickness / 2.0, 0);
        bodiesToAdd.add(rightWall);
    }

    private void createStrategicLocations() {
        String[] locationNames = {"Alpha", "Beta", "Gamma", "Delta", "Echo"};
        Random random = new Random();

        for (int i = 0; i < Config.STRATEGIC_LOCATIONS_COUNT; i++) {
            double x = (random.nextDouble() - 0.5) * (Config.WORLD_WIDTH - 200);
            double y = (random.nextDouble() - 0.5) * (Config.WORLD_HEIGHT - 200);

            StrategicLocation location = new StrategicLocation(locationNames[i], x, y);
            gameEntities.addStrategicLocation(location);
            bodiesToAdd.add(location.getBody());
        }
    }

    private void createObstacles() {
        Random random = new Random();
        int obstacleCount = 5 + random.nextInt(6); // 5 to 10 obstacles

        for (int i = 0; i < obstacleCount; i++) {
            double x = (random.nextDouble() - 0.5) * (Config.WORLD_WIDTH - 200);
            double y = (random.nextDouble() - 0.5) * (Config.WORLD_HEIGHT - 200);
            double radius = 10 + random.nextInt(21); // 10 to 30 radius

            Boulder boulder = new Boulder(x, y, radius);
            gameEntities.addObstacle(boulder);
            bodiesToAdd.add(boulder.getBody());
        }
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
            playerState.put("x", pos.x);
            playerState.put("y", pos.y);
            playerState.put("rotation", player.getRotation());
            playerState.put("health", player.getHealth());
            playerState.put("active", player.isActive());
            playerState.put("weapon", player.getCurrentWeaponIndex());
            playerState.put("ammo", player.getCurrentWeapon().getAmmo());
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
            Map<String, Object> projState = new HashMap<>();
            projState.put("id", projectile.getId());
            projState.put("x", pos.x);
            projState.put("y", pos.y);
            projState.put("ownerId", projectile.getOwnerId());
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
            obsState.put("type", obstacle.getType());
            // This is a bit of a hack, but it's the easiest way to get the radius for now
            obsState.put("radius", ((Circle) obstacle.getBody().getFixture(0).getShape()).getRadius());
            obstacleStates.add(obsState);
        }
        gameState.put("obstacles", obstacleStates);

        broadcast(gameState);
    }

    private Vector2 findSpawnPoint() {
        Random random = new Random();
        for (int attempts = 0; attempts < 10; attempts++) {
            double x = (random.nextDouble() - 0.5) * (Config.WORLD_WIDTH - 100);
            double y = (random.nextDouble() - 0.5) * (Config.WORLD_HEIGHT - 100);
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

        return new Vector2((random.nextDouble() - 0.5) * Config.WORLD_WIDTH * 0.8, (random.nextDouble() - 0.5) * Config.WORLD_HEIGHT * 0.8);
    }

    private Map<String, Object> createInitialGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "initialState");
        state.put("worldWidth", Config.WORLD_WIDTH);
        state.put("worldHeight", Config.WORLD_HEIGHT);

        List<Map<String, Object>> locations = new ArrayList<>();
        for (StrategicLocation location : gameEntities.getAllStrategicLocations()) {
            Vector2 pos = location.getPosition();
            Map<String, Object> locData = new HashMap<>();
            locData.put("id", location.getId());
            locData.put("name", location.getLocationName());
            locData.put("x", pos.x);
            locData.put("y", pos.y);
            locData.put("radius", Config.CAPTURE_RADIUS);
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
            obsData.put("type", obstacle.getType());
            obsData.put("radius", ((Circle) obstacle.getBody().getFixture(0).getShape()).getRadius());
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
        for (Projectile projectile : gameEntities.getAllProjectiles()) {
            if (projectile.isActive()) {
                Vector2 position = projectile.getPosition();
                Vector2 velocity = projectile.getBody().getLinearVelocity();
                log.info(String.format("Projectile %d - Position: (%.2f, %.2f), Velocity: (%.2f, %.2f), Speed: %.2f",
                        projectile.getId(),
                        position.x,
                        position.y,
                        velocity.x,
                        velocity.y,
                        velocity.getMagnitude()));
            }
        }
    }
}

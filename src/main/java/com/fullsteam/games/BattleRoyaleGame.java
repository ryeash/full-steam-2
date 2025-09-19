package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.StrategicLocation;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class BattleRoyaleGame extends AbstractGameStateManager implements CollisionProcessor.CollisionHandler, StepListener<Body> {
    private final World<Body> world;
    private final GameEntities gameEntities = new GameEntities();

    private double lastUpdateTime = System.nanoTime() / 1e9;

    public BattleRoyaleGame(String gameId, String gameType) {
        super(gameId, gameType);

        // Initialize physics world
        this.world = new World<>();
        Settings settings = new Settings();
        // Increase the maximum translation to allow for high-speed projectiles
        // Max speed: 17000, time step: 1/60s => 17000 * 0.0166 = 282.2
        settings.setMaximumTranslation(300.0); // Default is 2.0
        this.world.setSettings(settings);
        this.world.setGravity(new Vector2(0, 0)); // Top-down game, no gravity
        this.world.setBounds(new AxisAlignedBounds(2000, 2000));

        // Initialize collision processor and add it as collision listener
        CollisionProcessor collisionProcessor = new CollisionProcessor(gameEntities, this);
        this.world.addCollisionListener(collisionProcessor);
        this.world.addStepListener(this); // Add this class as a step listener

        createWorldBoundaries();
        createStrategicLocations();
    }

    private void createWorldBoundaries() {
        double halfWidth = Config.WORLD_WIDTH / 2;
        double halfHeight = Config.WORLD_HEIGHT / 2;
        double wallThickness = 50;

        // Top wall
        Body topWall = new Body();
        topWall.addFixture(new Rectangle(Config.WORLD_WIDTH + wallThickness * 2, wallThickness));
        topWall.setMass(MassType.INFINITE);
        topWall.getTransform().setTranslation(0, halfHeight + wallThickness / 2);
        world.addBody(topWall);

        // Bottom wall
        Body bottomWall = new Body();
        bottomWall.addFixture(new Rectangle(Config.WORLD_WIDTH + wallThickness * 2, wallThickness));
        bottomWall.setMass(MassType.INFINITE);
        bottomWall.getTransform().setTranslation(0, -halfHeight - wallThickness / 2);
        world.addBody(bottomWall);

        // Left wall
        Body leftWall = new Body();
        leftWall.addFixture(new Rectangle(wallThickness, Config.WORLD_HEIGHT));
        leftWall.setMass(MassType.INFINITE);
        leftWall.getTransform().setTranslation(-halfWidth - wallThickness / 2, 0);
        world.addBody(leftWall);

        // Right wall
        Body rightWall = new Body();
        rightWall.addFixture(new Rectangle(wallThickness, Config.WORLD_HEIGHT));
        rightWall.setMass(MassType.INFINITE);
        rightWall.getTransform().setTranslation(halfWidth + wallThickness / 2, 0);
        world.addBody(rightWall);
    }

    private void createStrategicLocations() {
        String[] locationNames = {"Alpha", "Beta", "Gamma", "Delta", "Echo"};
        Random random = new Random();

        for (int i = 0; i < Config.STRATEGIC_LOCATIONS_COUNT; i++) {
            double x = (random.nextDouble() - 0.5) * (Config.WORLD_WIDTH - 200);
            double y = (random.nextDouble() - 0.5) * (Config.WORLD_HEIGHT - 200);

            StrategicLocation location = new StrategicLocation(locationNames[i], x, y);
            gameEntities.addStrategicLocation(location);
            world.addBody(location.getBody());
        }
    }

    @Override
    protected void update() {
        try {
            double currentTime = System.nanoTime() / 1e9;
            double deltaTime = currentTime - lastUpdateTime;
            lastUpdateTime = currentTime;

            gameEntities.getAllPlayers().forEach(player -> {
                processPlayerInput(player, gameEntities.getPlayerInput().get(player.getId()));
                player.update(deltaTime);
            });

            // Update projectiles and queue inactive ones for removal
            gameEntities.getProjectiles().entrySet().removeIf(entry -> {
                Projectile projectile = entry.getValue();
                projectile.update(deltaTime);
                if (!projectile.isActive()) {
                    world.removeBody(projectile.getBody());
                    return true;
                }
                return false;
            });

            // Update strategic locations
            updateStrategicLocations(deltaTime);

            // Safely modify the physics world
            synchronized (world) {
                // Update the physics world
                world.updatev(deltaTime);
            }

            // Send game state to clients
            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
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

        broadcast(gameState);
    }

    @Override
    protected void onPlayerJoined(PlayerSession playerSession) {
        Vector2 spawnPoint = findSpawnPoint();
        log.info("Player {} joining game {} at spawn point ({}, {})", playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y);

        Player player = new Player(playerSession.getPlayerId(), playerSession.getPlayerName(), spawnPoint.x, spawnPoint.y);
        gameEntities.addPlayer(player);
        world.addBody(player.getBody());

        send(playerSession.getSession(), createInitialGameState());
        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gameEntities.getPlayers().size(), gameEntities.getPlayerSessions().size());
    }

    @Override
    protected void onPlayerLeft(PlayerSession playerSession) {
        gameEntities.getPlayerInput().remove(playerSession.getPlayerId());
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            world.removeBody(player.getBody());
            gameEntities.removePlayer(playerSession.getPlayerId());
        }
        log.info("Player {} left game {}", playerSession.getPlayerId(), gameId);
    }

    @Override
    protected void processPlayerInput(Player player, PlayerInput input) {
        if (player != null) {
            player.processInput(input);
            if (input.isLeft()) {
                Projectile projectile = player.shoot();
                if (projectile != null) {
                    gameEntities.addProjectile(projectile);
                    world.addBody(projectile.getBody());
                }
            }
        }
    }

    @Override
    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        Player player = gameEntities.getPlayer(playerSession.getPlayerId());
        if (player != null) {
            if (request.getPlayerName() != null) {
                player.setPlayerName(request.getPlayerName());
                playerSession.setPlayerName(request.getPlayerName());
            }
            player.applyWeaponConfig(request.getPrimaryWeapon(), request.getSecondaryWeapon());
        }
    }

    @Override
    protected int getMaxPlayers() {
        return Config.MAX_PLAYERS_PER_GAME;
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

    // ===== StepListener Implementation =====

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

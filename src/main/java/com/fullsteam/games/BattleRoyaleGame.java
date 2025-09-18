package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.physics.CollisionProcessor;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.StrategicLocation;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BattleRoyaleGame extends AbstractGameStateManager implements CollisionProcessor.CollisionHandler {
    private final World<Body> world;
    private final Map<Integer, Player> gamePlayer = new ConcurrentHashMap<>();
    private final Map<Integer, Projectile> projectiles = new ConcurrentHashMap<>();
    private final Map<Integer, StrategicLocation> strategicLocations = new ConcurrentHashMap<>();
    private final CollisionProcessor collisionProcessor;

    private double lastUpdateTime = System.nanoTime() / 1e9;
    private int updateCounter = 0;

    public BattleRoyaleGame(String gameId, String gameType) {
        super(gameId, gameType);

        // Initialize physics world
        this.world = new World<>();
        this.world.setSettings(new Settings());
        this.world.setGravity(new Vector2(0, 0)); // Top-down game, no gravity
        this.world.setBounds(new AxisAlignedBounds(2000, 2000));

        // Initialize collision processor and add it as step listener
        this.collisionProcessor = new CollisionProcessor(gamePlayer, projectiles, strategicLocations, this);
        this.world.addStepListener(collisionProcessor);

//        createWorldBoundaries();
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
            strategicLocations.put(location.getId(), location);
            world.addBody(location.getBody());
        }
    }

    @Override
    protected void update() {
        try {
            double currentTime = System.nanoTime() / 1e9;
            double deltaTime = currentTime - lastUpdateTime;
            lastUpdateTime = currentTime;

            // Update all game entities
            playerInput.forEach((id, input) -> processPlayerInput(players.get(id), input));
            gamePlayer.values().forEach(player -> player.update(deltaTime));

            synchronized (world) {
                world.updatev(deltaTime);
            }

            // Update projectiles and remove inactive ones (synchronized)
            synchronized (world) {
                projectiles.entrySet().removeIf(entry -> {
                    Projectile projectile = entry.getValue();
                    projectile.update(deltaTime);
                    if (!projectile.isActive()) {
                        world.removeBody(projectile.getBody());
                        return true;
                    }
                    return false;
                });
            }


            // Update strategic locations
            updateStrategicLocations(deltaTime);

            // Collision detection is handled by the CollisionProcessor step listener

            // Send game state to clients (reduce frequency for performance)
            sendGameState();
        } catch (Throwable t) {
            log.error("Error in update loop", t);
        }
    }

    private void updateStrategicLocations(double deltaTime) {
        for (StrategicLocation location : strategicLocations.values()) {
            location.update(deltaTime);

            // Check which players are in range
            Set<Integer> playersInRange = new HashSet<>();
            for (Player player : gamePlayer.values()) {
                if (player.isActive() && location.isPlayerInRange(player.getPosition())) {
                    playersInRange.add(player.getId());
                }
            }

            if (playersInRange.size() == 1) {
                // Single player capturing
                Integer playerId = playersInRange.iterator().next();
                if (!location.isControlledBy(playerId)) {
                    location.startCapture(playerId);
                    location.updateCapture(deltaTime);
                }
            } else if (playersInRange.size() > 1) {
                // Contested - stop capture
                location.stopCapture();
            } else {
                // No one in range
                location.stopCapture();
            }
        }
    }

    private void sendGameState() {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("type", "gameState");
        gameState.put("timestamp", System.currentTimeMillis());

        // Player states
        List<Map<String, Object>> playerStates = new ArrayList<>();
        for (Player player : gamePlayer.values()) {
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

        // Projectile states
        List<Map<String, Object>> projectileStates = new ArrayList<>();
        for (Projectile projectile : projectiles.values()) {
            Vector2 pos = projectile.getPosition();
            Map<String, Object> projState = new HashMap<>();
            projState.put("id", projectile.getId());
            projState.put("x", pos.x);
            projState.put("y", pos.y);
            projState.put("ownerId", projectile.getOwnerId());
            projectileStates.add(projState);
        }
        gameState.put("projectiles", projectileStates);

        // Strategic location states
        List<Map<String, Object>> locationStates = new ArrayList<>();
        for (StrategicLocation location : strategicLocations.values()) {
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
        // Find spawn point
        Vector2 spawnPoint = findSpawnPoint();

        log.info("Player {} joining game {} at spawn point ({}, {})",
                playerSession.getPlayerId(), gameId, spawnPoint.x, spawnPoint.y);

        Player player = new Player(
                playerSession.getPlayerId(),
                playerSession.getPlayerName(),
                spawnPoint.x,
                spawnPoint.y
        );

        gamePlayer.put(playerSession.getPlayerId(), player);
        synchronized (world) {
            world.addBody(player.getBody());
        }

        // Send initial game state to new player
        send(playerSession.getSession(), createInitialGameState());

        log.info("Player {} ({}) joined game {} successfully. Total players: {}, Total sessions: {}",
                playerSession.getPlayerId(), playerSession.getPlayerName(), gameId, gamePlayer.size(), players.size());
    }

    @Override
    protected void onPlayerLeft(PlayerSession playerSession) {
        playerInput.remove(playerSession.getPlayerId());
        Player player = gamePlayer.remove(playerSession.getPlayerId());
        if (player != null) {
            synchronized (world) {
                world.removeBody(player.getBody());
            }
        }
        log.info("Player {} left game {}", playerSession.getPlayerId(), gameId);
    }

    @Override
    protected void processPlayerInput(PlayerSession playerSession, PlayerInput input) {
        if (playerSession != null) {
            Player player = gamePlayer.get(playerSession.getPlayerId());
            if (player != null) {
                player.processInput(input);
                if (input.isLeft()) {
                    Projectile projectile = player.shoot();
                    if (projectile != null) {
                        projectiles.put(projectile.getId(), projectile);
                        synchronized (world) {
                            world.addBody(projectile.getBody());
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void processPlayerConfigChange(PlayerSession playerSession, PlayerConfigRequest request) {
        Player player = gamePlayer.get(playerSession.getPlayerId());
        if (player != null) {
            if (request.getPlayerName() != null) {
                player.setPlayerName(request.getPlayerName());
                playerSession.setPlayerName(request.getPlayerName()); // Also update session
            }
            player.applyWeaponConfig(request.getPrimaryWeapon(), request.getSecondaryWeapon());
        }
    }

    @Override
    protected int getMaxPlayers() {
        return Config.MAX_PLAYERS_PER_GAME;
    }

    private Vector2 findSpawnPoint() {
        // Try to find a spawn point away from other players
        Random random = new Random();
        for (int attempts = 0; attempts < 10; attempts++) {
            double x = (random.nextDouble() - 0.5) * (Config.WORLD_WIDTH - 100);
            double y = (random.nextDouble() - 0.5) * (Config.WORLD_HEIGHT - 100);
            Vector2 candidate = new Vector2(x, y);

            boolean tooClose = false;
            for (Player other : gamePlayer.values()) {
                if (other.getPosition().distance(candidate) < 100) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                return candidate;
            }
        }

        // Fallback to random position
        return new Vector2(
                (random.nextDouble() - 0.5) * Config.WORLD_WIDTH * 0.8,
                (random.nextDouble() - 0.5) * Config.WORLD_HEIGHT * 0.8
        );
    }

    private Map<String, Object> createInitialGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "initialState");
        state.put("worldWidth", Config.WORLD_WIDTH);
        state.put("worldHeight", Config.WORLD_HEIGHT);

        // Send strategic locations
        List<Map<String, Object>> locations = new ArrayList<>();
        for (StrategicLocation location : strategicLocations.values()) {
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

    // ===== CollisionProcessor.CollisionHandler Implementation =====

    @Override
    public void onPlayerHitByProjectile(Player player, Projectile projectile) {
        // Apply damage to the player
        player.takeDamage(projectile.getDamage());
        
        // Mark projectile as inactive so it gets removed
        projectile.setActive(false);
        
        log.info("Player {} hit by projectile from player {} for {} damage", 
                player.getId(), projectile.getOwnerId(), projectile.getDamage());
        
        // Check if player died
        if (!player.isActive()) {
            Player killer = gamePlayer.get(projectile.getOwnerId());
            if (killer != null) {
                killer.addKill();
            }
            player.die();
            
            // Notify clients of player death
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
        // The strategic location capture logic is handled in updateStrategicLocations()
        // This is just for logging/notification purposes
    }

    @Override
    public void onPlayerStayInLocation(Player player, StrategicLocation location) {
        // This is called every frame while the player is in the location
        // We don't need to do anything here as capture logic is handled elsewhere
    }

    @Override
    public void onPlayerExitLocation(Player player, StrategicLocation location) {
        log.debug("Player {} exited strategic location {}", player.getId(), location.getLocationName());
        // The strategic location capture logic is handled in updateStrategicLocations()
        // This is just for logging/notification purposes
    }

}

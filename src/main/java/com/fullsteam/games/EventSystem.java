package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.model.ActiveGameEvent;
import com.fullsteam.model.EnvironmentalEvent;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Rules;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.PowerUp;
import lombok.Getter;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages random events and environmental hazards during gameplay.
 * Creates dramatic moments with meteor showers, supply drops, volcanic eruptions, and more.
 */
public class EventSystem {
    private static final Logger log = LoggerFactory.getLogger(EventSystem.class);

    private final String gameId;
    private final Rules rules;
    private final GameEntities gameEntities;
    private final GameEventManager gameEventManager;
    private final TerrainGenerator terrainGenerator;
    private final double worldWidth;
    private final double worldHeight;

    private long nextEventTime = 0L;

    @Getter
    private ActiveGameEvent currentEvent = null;
    private boolean warningZonesSpawned = false;

    public EventSystem(String gameId, Rules rules, GameEntities gameEntities,
                       GameEventManager gameEventManager, TerrainGenerator terrainGenerator,
                       double worldWidth, double worldHeight) {
        this.gameId = gameId;
        this.rules = rules;
        this.gameEntities = gameEntities;
        this.gameEventManager = gameEventManager;
        this.terrainGenerator = terrainGenerator;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        scheduleNextEvent();
    }

    /**
     * Update the event system and trigger events when ready.
     */
    public void update(double deltaTime) {
        if (!rules.isEnableRandomEvents()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Check if we should start a new event
        if (currentEvent == null && now >= nextEventTime) {
            startRandomEvent();
        }

        // Update current event
        if (currentEvent != null) {
            currentEvent.update();

            // Spawn warning zones during warning phase
            if (currentEvent.isInWarningPhase() && !warningZonesSpawned) {
                spawnWarningZones(currentEvent);
                warningZonesSpawned = true;
            }

            // Trigger the actual event when warning phase ends
            if (currentEvent.isActive() && warningZonesSpawned) {
                triggerEvent(currentEvent);
                warningZonesSpawned = false; // Reset for next event
            }

            // Clean up completed events
            if (currentEvent.isCompleted()) {
                currentEvent = null;
                scheduleNextEvent();
            }
        }
    }

    /**
     * Schedule the next random event.
     */
    private void scheduleNextEvent() {
        double baseInterval = rules.getRandomEventInterval();
        double variance = rules.getRandomEventIntervalVariance();

        // Calculate interval with variance
        double varianceAmount = baseInterval * variance;
        double randomVariance = ThreadLocalRandom.current().nextDouble(-varianceAmount, varianceAmount);
        double actualInterval = Math.max(30.0, baseInterval + randomVariance); // Minimum 30 seconds

        nextEventTime = (long) (System.currentTimeMillis() + (actualInterval * 1000));
    }

    /**
     * Start a random event.
     */
    private void startRandomEvent() {
        EnvironmentalEvent eventType = selectRandomEvent();
        List<Vector2> targetLocations = generateEventLocations(eventType);

        currentEvent = new ActiveGameEvent(eventType, rules.getEventWarningDuration(), targetLocations);
        warningZonesSpawned = false;

        // Announce the event
        gameEventManager.broadcastSystemMessage(eventType.getAnnouncementMessage());
        log.info("Game {} - Event started: {} at {} locations", gameId, eventType.name(), targetLocations.size());
    }

    /**
     * Select a random event type from enabled events.
     */
    private EnvironmentalEvent selectRandomEvent() {
        List<EnvironmentalEvent> enabledEvents = rules.getEnabledEvents();

        // If no specific events are enabled, use all events
        if (enabledEvents == null || enabledEvents.isEmpty()) {
            enabledEvents = Arrays.asList(EnvironmentalEvent.values());
        }

        int index = ThreadLocalRandom.current().nextInt(enabledEvents.size());
        return enabledEvents.get(index);
    }

    /**
     * Generate random locations for event impacts based on event type.
     */
    private List<Vector2> generateEventLocations(EnvironmentalEvent eventType) {
        int count = getEventLocationCount(eventType);
        List<Vector2> locations = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Vector2 location = generateRandomLocation();
            locations.add(location);
        }

        return locations;
    }

    /**
     * Get the number of locations for an event type.
     */
    private int getEventLocationCount(EnvironmentalEvent eventType) {
        return switch (eventType) {
            case METEOR_SHOWER -> rules.getMeteorShowerCount();
            case SUPPLY_DROP -> rules.getSupplyDropCount();
            case VOLCANIC_ERUPTION -> rules.getVolcanicEruptionCount();
            case EARTHQUAKE -> 1; // Single large area
            case SOLAR_FLARE -> 3; // Multiple burn zones
            case ION_STORM -> rules.getIonStormZones();
            case BLIZZARD -> 4; // Multiple freeze zones
        };
    }

    /**
     * Generate a random location on the map.
     */
    private Vector2 generateRandomLocation() {
        // Try to find a clear position, but don't be too strict
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldWidth * 0.9;
            double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldHeight * 0.9;
            Vector2 candidate = new Vector2(x, y);

            // Events can happen anywhere, but prefer clear areas
            if (attempt < 5 && !terrainGenerator.isPositionClear(candidate, 30.0)) {
                continue;
            }

            return candidate;
        }

        // Fallback: just use a random position
        double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldWidth * 0.9;
        double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * worldHeight * 0.9;
        return new Vector2(x, y);
    }

    /**
     * Spawn warning zone indicators for upcoming event.
     */
    private void spawnWarningZones(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            double radius = getWarningRadius(event.getEventType());

            FieldEffect warningZone = new FieldEffect(
                    Config.nextId(),
                    -1, // No owner (system event)
                    FieldEffectType.WARNING_ZONE,
                    location,
                    radius,
                    0.0, // No damage
                    rules.getEventWarningDuration(),
                    0 // No team
            );

            gameEntities.addFieldEffect(warningZone);
            gameEntities.getWorld().addBody(warningZone.getBody());
            event.addWarningZoneId(warningZone.getId());
        }

        log.debug("Game {} - Spawned {} warning zones for {}", gameId,
                event.getTargetLocations().size(), event.getEventType().name());
    }

    /**
     * Get the warning radius for an event type.
     */
    private double getWarningRadius(EnvironmentalEvent eventType) {
        return switch (eventType) {
            case METEOR_SHOWER -> rules.getMeteorRadius();
            case SUPPLY_DROP -> 50.0;
            case VOLCANIC_ERUPTION -> rules.getEruptionRadius();
            case EARTHQUAKE -> worldWidth * 0.3; // Large area
            case SOLAR_FLARE -> 100.0;
            case ION_STORM -> 80.0;
            case BLIZZARD -> 90.0;
        };
    }

    /**
     * Trigger the actual event effects.
     */
    private void triggerEvent(ActiveGameEvent event) {
        switch (event.getEventType()) {
            case METEOR_SHOWER -> triggerMeteorShower(event);
            case SUPPLY_DROP -> triggerSupplyDrop(event);
            case VOLCANIC_ERUPTION -> triggerVolcanicEruption(event);
            case EARTHQUAKE -> triggerEarthquake(event);
            case SOLAR_FLARE -> triggerSolarFlare(event);
            case ION_STORM -> triggerIonStorm(event);
            case BLIZZARD -> triggerBlizzard(event);
        }
    }

    /**
     * Trigger a meteor shower - multiple explosions.
     */
    private void triggerMeteorShower(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect explosion = new FieldEffect(
                    Config.nextId(),
                    -1, // System event
                    FieldEffectType.EXPLOSION,
                    location,
                    rules.getMeteorRadius(),
                    rules.getMeteorDamage(),
                    FieldEffectType.EXPLOSION.getDefaultDuration(),
                    0 // No team
            );
            gameEntities.addFieldEffect(explosion);
            gameEntities.getWorld().addBody(explosion.getBody());
        }
    }

    /**
     * Trigger a supply drop - spawn power-ups with visual explosion.
     */
    private void triggerSupplyDrop(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            // Visual explosion (no damage)
            FieldEffect explosion = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.EXPLOSION,
                    location,
                    50.0,
                    0.0, // No damage
                    FieldEffectType.EXPLOSION.getDefaultDuration(),
                    0
            );
            gameEntities.addFieldEffect(explosion);
            gameEntities.getWorld().addBody(explosion.getBody());

            // Spawn random power-up
            PowerUp.PowerUpType powerUpType = getRandomPowerUpType();
            PowerUp powerUp = new PowerUp(
                    Config.nextId(),
                    location,
                    powerUpType,
                    -1, // Not from a workshop
                    30.0, // Duration
                    1.5 // Strength
            );
            gameEntities.addPowerUp(powerUp);
            gameEntities.getWorld().addBody(powerUp.getBody());
        }
    }

    /**
     * Trigger volcanic eruption - persistent damage zones.
     */
    private void triggerVolcanicEruption(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect eruption = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.FIRE,
                    location,
                    rules.getEruptionRadius(),
                    rules.getEruptionRadius() * 2.5,
                    rules.getEruptionDamage(),
                    event.getEventType().getBaseDuration(),
                    0,
                    0
            );
            gameEntities.addFieldEffect(eruption);
            gameEntities.getWorld().addBody(eruption.getBody());
        }
    }

    /**
     * Trigger earthquake - large area damage.
     */
    private void triggerEarthquake(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect earthquake = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.EARTHQUAKE,
                    location,
                    worldWidth * 0.3, // Large radius
                    rules.getEarthquakeDamage(),
                    event.getEventType().getBaseDuration(),
                    0
            );
            gameEntities.addFieldEffect(earthquake);
            gameEntities.getWorld().addBody(earthquake.getBody());
        }
    }

    /**
     * Trigger solar flare - fire damage zones.
     */
    private void triggerSolarFlare(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect fire = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.FIRE,
                    location,
                    100.0,
                    35.0, // High damage
                    event.getEventType().getBaseDuration(),
                    0
            );
            gameEntities.addFieldEffect(fire);
            gameEntities.getWorld().addBody(fire.getBody());
        }
    }

    /**
     * Trigger ion storm - electric fields.
     */
    private void triggerIonStorm(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect electric = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.ELECTRIC,
                    location,
                    80.0,
                    rules.getIonStormDamage(),
                    event.getEventType().getBaseDuration(),
                    0
            );
            gameEntities.addFieldEffect(electric);
            gameEntities.getWorld().addBody(electric.getBody());
        }
    }

    /**
     * Trigger blizzard - freeze zones.
     */
    private void triggerBlizzard(ActiveGameEvent event) {
        for (Vector2 location : event.getTargetLocations()) {
            FieldEffect freeze = new FieldEffect(
                    Config.nextId(),
                    -1,
                    FieldEffectType.FREEZE,
                    location,
                    90.0,
                    20.0, // Moderate damage
                    event.getEventType().getBaseDuration(),
                    0
            );
            gameEntities.addFieldEffect(freeze);
            gameEntities.getWorld().addBody(freeze.getBody());
        }
    }

    /**
     * Get a random power-up type for supply drops.
     */
    private PowerUp.PowerUpType getRandomPowerUpType() {
        PowerUp.PowerUpType[] types = PowerUp.PowerUpType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    /**
     * Check if an event is currently active.
     */
    public boolean isEventActive() {
        return currentEvent != null && currentEvent.isActive();
    }

    /**
     * Get event state data for client broadcasting.
     */
    public List<Object> getEventData() {
        List<Object> eventData = new ArrayList<>();

        if (currentEvent != null && !currentEvent.isCompleted()) {
            eventData.add(new EventStateData(
                    currentEvent.getEventType().name(),
                    currentEvent.isInWarningPhase(),
                    currentEvent.isActive(),
                    currentEvent.isInWarningPhase() ? currentEvent.getWarningTimeRemaining() : 0.0,
                    currentEvent.isActive() ? currentEvent.getEventTimeRemaining() : 0.0
            ));
        }

        return eventData;
    }

    /**
     * Data class for event state broadcasting.
     */
    public record EventStateData(
            String eventType,
            boolean inWarning,
            boolean active,
            double warningTimeRemaining,
            double eventTimeRemaining
    ) {
    }
}


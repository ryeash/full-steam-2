package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.EnvironmentalEvent;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.Rules;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.PowerUp;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventSystemTest extends BaseTestClass {

    private World<Body> world;

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
    }

    @Test
    void testEventSystemInitialization() {
        // Create rules with events enabled
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .randomEventInterval(10.0)
                .build();

        GameConfig config = GameConfig.builder()
                .worldWidth(2000)
                .worldHeight(2000)
                .rules(rules)
                .build();

        GameEntities gameEntities = new GameEntities(config, world);
        GameEventManager eventManager = new GameEventManager(gameEntities, (session, msg) -> {
        });
        TerrainGenerator terrainGenerator = new TerrainGenerator(2000, 2000);

        EventSystem eventSystem = new EventSystem(
                "test-game",
                rules,
                gameEntities,
                eventManager,
                terrainGenerator,
                2000,
                2000
        );

        assertNotNull(eventSystem);
        assertFalse(eventSystem.isEventActive());
        assertNull(eventSystem.getCurrentEvent());
    }

    @Test
    void testEventTypesHaveValidData() {
        for (EnvironmentalEvent event : EnvironmentalEvent.values()) {
            assertNotNull(event.getDisplayName());
            assertNotNull(event.getIcon());
            assertTrue(event.getBaseDuration() > 0);
            assertNotNull(event.getAnnouncementMessage());
            assertTrue(event.getAnnouncementMessage().contains(event.getIcon()));
        }
    }

    @Test
    void testMeteorShowerEventConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .meteorShowerCount(10)
                .meteorDamage(50.0)
                .meteorRadius(75.0)
                .build();

        assertEquals(10, rules.getMeteorShowerCount());
        assertEquals(50.0, rules.getMeteorDamage());
        assertEquals(75.0, rules.getMeteorRadius());
    }

    @Test
    void testSupplyDropEventConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .supplyDropCount(8)
                .build();

        assertEquals(8, rules.getSupplyDropCount());
    }

    @Test
    void testVolcanicEruptionEventConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .volcanicEruptionCount(5)
                .eruptionDamage(35.0)
                .eruptionRadius(80.0)
                .build();

        assertEquals(5, rules.getVolcanicEruptionCount());
        assertEquals(35.0, rules.getEruptionDamage());
        assertEquals(80.0, rules.getEruptionRadius());
    }

    @Test
    void testEventIntervalConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .randomEventInterval(120.0)
                .randomEventIntervalVariance(0.3)
                .eventWarningDuration(5.0)
                .build();

        assertEquals(120.0, rules.getRandomEventInterval());
        assertEquals(0.3, rules.getRandomEventIntervalVariance());
        assertEquals(5.0, rules.getEventWarningDuration());
    }

    @Test
    void testEnabledEventsFilter() {
        List<EnvironmentalEvent> enabledEvents = List.of(
                EnvironmentalEvent.METEOR_SHOWER,
                EnvironmentalEvent.SUPPLY_DROP
        );

        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .enabledEvents(enabledEvents)
                .build();

        assertEquals(2, rules.getEnabledEvents().size());
        assertTrue(rules.getEnabledEvents().contains(EnvironmentalEvent.METEOR_SHOWER));
        assertTrue(rules.getEnabledEvents().contains(EnvironmentalEvent.SUPPLY_DROP));
        assertFalse(rules.getEnabledEvents().contains(EnvironmentalEvent.VOLCANIC_ERUPTION));
    }

    @Test
    void testNewFieldEffectTypes() {
        // Test WARNING_ZONE
        FieldEffectType warningZone = FieldEffectType.WARNING_ZONE;
        assertEquals(3.0, warningZone.getDefaultDuration());
        assertFalse(warningZone.isInstantaneous());

        // Test EARTHQUAKE
        FieldEffectType earthquake = FieldEffectType.EARTHQUAKE;
        assertEquals(4.0, earthquake.getDefaultDuration());
        assertFalse(earthquake.isInstantaneous());
    }

    @Test
    void testEventSystemDisabledByDefault() {
        Rules rules = Rules.builder().build();

        assertFalse(rules.isEnableRandomEvents());
    }

    @Test
    void testIonStormConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .ionStormZones(8)
                .ionStormDamage(30.0)
                .build();

        assertEquals(8, rules.getIonStormZones());
        assertEquals(30.0, rules.getIonStormDamage());
    }

    @Test
    void testEarthquakeConfiguration() {
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .earthquakeDamage(20.0)
                .build();

        assertEquals(20.0, rules.getEarthquakeDamage());
    }

    @Test
    void testAllEventTypesCount() {
        // Ensure we have all expected event types
        EnvironmentalEvent[] events = EnvironmentalEvent.values();
        assertEquals(7, events.length, "Should have 7 environmental event types");
    }

    @Test
    void testEventDataFormatting() {
        // Test that event data can be created and retrieved
        Rules rules = Rules.builder()
                .enableRandomEvents(true)
                .build();

        GameConfig config = GameConfig.builder()
                .worldWidth(2000)
                .worldHeight(2000)
                .rules(rules)
                .build();

        GameEntities gameEntities = new GameEntities(config, world);
        GameEventManager eventManager = new GameEventManager(gameEntities, (session, msg) -> {
        });
        TerrainGenerator terrainGenerator = new TerrainGenerator(2000, 2000);

        EventSystem eventSystem = new EventSystem(
                "test-game",
                rules,
                gameEntities,
                eventManager,
                terrainGenerator,
                2000,
                2000
        );

        List<Object> eventData = eventSystem.getEventData();
        assertNotNull(eventData);
        assertEquals(0, eventData.size(), "Should be empty when no events active");
    }
}


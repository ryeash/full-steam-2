package com.fullsteam;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Factory
public class Config {
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(8);

    public static final double PLAYER_SPEED = 600.0; // pixels per second (max speed)
    public static final double PLAYER_RADIUS = 20.0;
    public static final double PLAYER_ACCELERATION = 3300.0; // Force applied to reach target velocity
    public static final double PLAYER_BRAKING_FORCE = 2100.0; // Force applied when stopping
    public static final double PLAYER_LINEAR_DAMPING = 3.6; // Physics damping for responsive movement
    public static final double PLAYER_ANGULAR_DAMPING = 1.0; // Rotation control damping
    public static final double NET_PUSHBACK_FORCE = 2_000_000.0; // Force applied when net hits player
    public static final double HOMING_DISTANCE = 300.0;
    public static final int MAX_GLOBAL_PLAYERS = Integer.parseInt(System.getProperty("max.global.players", "100"));
    public static final int MAX_GLOBAL_GAMES = Integer.parseInt(System.getProperty("max.global.game", "10"));
    public static final double WORLD_BOUNDARY_THICKNESS = Double.parseDouble(System.getProperty("world.boundary.thickness", "50.0"));
    public static final double SPAWN_INVINCIBILITY_DURATION = Double.parseDouble(System.getProperty("spawn.invincibilityDuration", "3.0"));
    public static final double RIOT_SHIELD_MAX_HEALTH = Double.parseDouble(System.getProperty("riot.shield.maxHealth", "150.0"));
    public static final double GRAVITY_WELL_CONSTANT = 1600000000.0;

    private static final AtomicInteger ENTITY_ID = new AtomicInteger(1);
    private static final AtomicLong GAME_ID = new AtomicLong(1);
    private static final AtomicInteger PLAYER_ID = new AtomicInteger(1);

    @Singleton
    @Named("json")
    @Replaces(JsonMapper.class)
    public JsonMapper jsonMapper(JsonMapper.Builder jsonMapperBuilder) {
        return jsonMapperBuilder
                .defaultMergeable(true)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.ALWAYS))
                .addModule(new SimpleModule()
                        .addSerializer(Double.class, new SerializerDouble())
                        .addSerializer(double.class, new SerializerDouble())
                        .addSerializer(BigDecimal.class, new SerializerBigDecimal()))
                .build();
    }

    public static final class SerializerBigDecimal extends ValueSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                gen.writeNumber(value.setScale(2, RoundingMode.FLOOR));
            } else {
                gen.writeNull();
            }
        }
    }

    public static final class SerializerDouble extends ValueSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                if (value.isInfinite()) {
                    gen.writeNumber(999_999_999); // a large number
                } else {
                    BigDecimal bd = BigDecimal.valueOf(value);
                    gen.writeNumber(bd.setScale(2, RoundingMode.FLOOR));
                }
            } else {
                gen.writeNull();
            }
        }
    }


    /**
     * Generate next entity ID (for game objects like projectiles, obstacles, etc.)
     * Skips 0 as it's used as a magic value in some places.
     *
     * @return Unique entity ID
     */
    public static int nextEntityId() {
        int id = ENTITY_ID.incrementAndGet();
        // Zero is magic, don't use it
        if (id == 0) {
            id = ENTITY_ID.incrementAndGet();
        }
        return id;
    }

    /**
     * Generate next game ID.
     *
     * @return Unique game ID string
     */
    public static String nextGameId() {
        return "" + GAME_ID.getAndIncrement();
    }

    /**
     * Generate next player ID.
     *
     * @return Unique player ID
     */
    public static int nextPlayerId() {
        return PLAYER_ID.getAndIncrement();
    }
}



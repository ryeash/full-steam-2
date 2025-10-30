package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
@Introspected
public class Rules {
    /**
     * Duration of each round in seconds. 0 = infinite/no rounds
     */
    @Min(0)
    @Max(7200)
    @Builder.Default
    private double roundDuration = 120.0;
    
    /**
     * Rest period between rounds in seconds
     */
    @Min(0)
    @Max(60)
    @Builder.Default
    private double restDuration = 10.0;
    
    /**
     * Number of flags each team has to protect/capture. 0 = no flags (traditional deathmatch)
     */
    @Min(0)
    @Max(3)
    @Builder.Default
    private int flagsPerTeam = 0;
    
    /**
     * How team/player scores are calculated
     */
    @NotNull
    @Builder.Default
    private ScoreStyle scoreStyle = ScoreStyle.TOTAL_KILLS;
    
    /**
     * How the game is won
     */
    @NotNull
    @Builder.Default
    private VictoryCondition victoryCondition = VictoryCondition.ENDLESS;
    
    /**
     * Score limit for SCORE_LIMIT victory condition.
     * First team/player to reach this score wins.
     */
    @Min(1)
    @Max(10000)
    @Builder.Default
    private int scoreLimit = 50;
    
    /**
     * Time limit for TIME_LIMIT victory condition in seconds.
     * Team/player with most points when time expires wins.
     */
    @Min(1)
    @Max(7200)
    @Builder.Default
    private double timeLimit = 600.0; // 10 minutes default
    
    /**
     * Enable sudden death mode when game ends in a tie.
     * Next score wins if scores are equal.
     */
    @NotNull
    @Builder.Default
    private boolean suddenDeath = false;
    
    /**
     * Lock the game to new players after this many seconds from game start.
     * 0 = never lock (players can join anytime).
     * Useful for Battle Royale modes where late joins would be unfair.
     */
    @Min(0)
    @Max(3600)
    @Builder.Default
    private double lockGameAfterSeconds = 0.0;
    
    // ===== Respawn Rules =====
    
    /**
     * How players respawn after death.
     */
    @NotNull
    @Builder.Default
    private RespawnMode respawnMode = RespawnMode.INSTANT;
    
    /**
     * Delay in seconds before player respawns (for INSTANT mode).
     */
    @Min(0)
    @Max(60)
    @Builder.Default
    private double respawnDelay = 5.0;
    
    /**
     * Maximum lives per player. -1 = unlimited lives.
     * Used in LIMITED respawn mode.
     */
    @Min(-1)
    @Max(100)
    @Builder.Default
    private int maxLives = -1;
    
    /**
     * Interval in seconds between wave respawns (for WAVE mode).
     * All dead players respawn together at this interval.
     */
    @Min(1)
    @Max(300)
    @Builder.Default
    private double waveRespawnInterval = 30.0;
    
    // ===== King of the Hill Rules =====
    
    /**
     * Number of King of the Hill zones. 0 = disabled, 1-4 = number of zones.
     * Zones are placed equidistant between team spawn areas for fairness.
     */
    @Min(0)
    @Max(4)
    @Builder.Default
    private int kothZones = 0;
    
    /**
     * Points awarded per second for controlling a KOTH zone.
     */
    @DecimalMin("0.1")
    @DecimalMax("100.0")
    @Builder.Default
    private double kothPointsPerSecond = 1.0;
    
    // ===== Workshop Rules =====
    
    /**
     * Whether to add workshops to the game. When enabled, each team gets one workshop in their spawn zone.
     * Workshops allow players to craft power-ups by standing near them.
     */
    @NotNull
    @JsonProperty("addWorkshops")
    @Builder.Default
    private boolean addWorkshops = false;
    
    /**
     * Time in seconds required to craft a power-up at a workshop.
     */
    @DecimalMin("1.0")
    @DecimalMax("120.0")
    @Builder.Default
    private double workshopCraftTime = 10.0;
    
    /**
     * Radius around workshop where players can craft power-ups.
     */
    @DecimalMin("10.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double workshopCraftRadius = 80.0;
    
    /**
     * Maximum number of power-ups that can exist around a workshop.
     */
    @Min(1)
    @Max(20)
    @Builder.Default
    private int maxPowerUpsPerWorkshop = 3;
    
    // ===== Oddball Rules =====
    
    /**
     * Whether to enable Oddball mode. When enabled, a single ball spawns at the world center.
     * Players score points by holding the ball, but cannot fire weapons while carrying it.
     */
    @NotNull
    @Builder.Default
    private boolean enableOddball = false;
    
    /**
     * Points awarded per second for holding the oddball.
     */
    @DecimalMin("0.1")
    @DecimalMax("100.0")
    @Builder.Default
    private double oddballPointsPerSecond = 1.0;
    
    // ===== VIP Rules =====
    
    /**
     * Whether to enable VIP mode. When enabled, one player per team is designated as the VIP.
     * Only kills of VIP players count towards the objective score.
     * VIP status passes to another player if the current VIP drops out.
     */
    @NotNull
    @Builder.Default
    private boolean enableVip = false;
    
    // ===== Random Weapons Rules =====
    
    /**
     * Whether to enable random weapon rotation. When enabled, all players are assigned
     * new random weapons (excluding healing weapons) at regular intervals.
     * Creates chaotic, unpredictable gameplay where players must adapt to different loadouts.
     */
    @NotNull
    @Builder.Default
    private boolean enableRandomWeapons = false;
    
    /**
     * Interval in seconds between random weapon rotations.
     * All players receive new weapons simultaneously.
     */
    @DecimalMin("5.0")
    @DecimalMax("300.0")
    @Builder.Default
    private double randomWeaponInterval = 30.0;
    
    // ===== Terrain Rules =====
    
    /**
     * Obstacle density for terrain generation.
     * SPARSE = fewer obstacles, more open space
     * DENSE = normal obstacle density
     * CHOKED = many obstacles, cramped battlefield
     * RANDOM = randomly select density each game
     */
    @NotNull
    @Builder.Default
    private ObstacleDensity obstacleDensity = ObstacleDensity.RANDOM;
    
    // ===== Headquarters Rules =====
    
    /**
     * Whether to add headquarters to the game. When enabled, each team gets one headquarters in their spawn zone.
     * Headquarters are destructible structures that can be shot to score points.
     */
    @NotNull
    @JsonProperty("addHeadquarters")
    @Builder.Default
    private boolean addHeadquarters = false;
    
    /**
    /**
     * Health of each headquarters structure.
     */
    @DecimalMin("100.0")
    @DecimalMax("100000.0")
    @Builder.Default
    private double headquartersMaxHealth = 1000.0;
    
    /**
     * Points awarded per damage dealt to enemy headquarters.
     * e.g., 1.0 = 1 point per 1 damage, 0.1 = 1 point per 10 damage
     */
    @DecimalMin("0.01")
    @DecimalMax("10.0")
    @Builder.Default
    private double headquartersPointsPerDamage = 0.1;
    
    /**
     * Bonus points awarded when a team destroys enemy headquarters.
     */
    @Min(0)
    @Max(10000)
    @Builder.Default
    private int headquartersDestructionBonus = 100;
    
    /**
     * Whether destroying headquarters ends the game.
     */
    @NotNull
    @Builder.Default
    private boolean headquartersDestructionEndsGame = true;
    
    // ===== Event System Rules =====
    
    /**
     * Whether to enable random events during gameplay.
     */
    @NotNull
    @Builder.Default
    private boolean enableRandomEvents = false;
    
    /**
     * Interval in seconds between random events (minimum time).
     * Actual time will vary based on randomEventIntervalVariance.
     */
    @DecimalMin("5.0")
    @DecimalMax("600.0")
    @Builder.Default
    private double randomEventInterval = 40.0; // 40 seconds default
    
    /**
     * Variance factor for event intervals (0.0 - 1.0).
     * 0.5 means events can occur 50% earlier or later than the base interval.
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private double randomEventIntervalVariance = 0.3;
    
    /**
     * Warning duration in seconds before an event actually triggers.
     * Displays visual indicators to give players time to react.
     */
    @DecimalMin("0.0")
    @DecimalMax("30.0")
    @Builder.Default
    private double eventWarningDuration = 3.0;
    
    /**
     * Which event types are enabled for this game.
     * Empty list means all events can occur.
     */
    @NotNull
    @Builder.Default
    private List<EnvironmentalEvent> enabledEvents = new ArrayList<>();
    
    // ===== Event Density Settings =====
    
    /**
     * Density of meteor shower impact zones.
     * Controls how many meteors spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity meteorShowerDensity = EventDensity.DENSE;
    
    /**
     * Density of supply drop locations.
     * Controls how many supply drops spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity supplyDropDensity = EventDensity.SPARSE;
    
    /**
     * Density of volcanic eruption zones.
     * Controls how many eruption zones spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity volcanicEruptionDensity = EventDensity.DENSE;
    
    /**
     * Density of ion storm zones.
     * Controls how many electric zones spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity ionStormDensity = EventDensity.DENSE;
    
    /**
     * Density of earthquake impact zones.
     * Controls how many earthquake zones spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity earthquakeDensity = EventDensity.SPARSE;

    /**
     * Density of blizzard freeze zones.
     * Controls how many blizzard zones spawn relative to map size.
     */
    @NotNull
    @Builder.Default
    private EventDensity blizzardDensity = EventDensity.DENSE;
    
    // ===== Event Intensity Settings =====
    
    /**
     * Damage per meteor impact.
     */
    @DecimalMin("1.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double meteorDamage = 40.0;
    
    /**
     * Radius of each meteor explosion.
     */
    @DecimalMin("10.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double meteorRadius = 60.0;
    
    /**
     * Damage per second from eruption zones.
     */
    @DecimalMin("1.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double eruptionDamage = 30.0;
    
    /**
     * Radius of each eruption zone.
     */
    @DecimalMin("10.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double eruptionRadius = 70.0;
    
    /**
     * Damage per second from earthquake events.
     */
    @DecimalMin("1.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double earthquakeDamage = 15.0;
    
    /**
     * Damage from ion storm electric fields.
     */
    @DecimalMin("1.0")
    @DecimalMax("500.0")
    @Builder.Default
    private double ionStormDamage = 25.0;
    
    /**
     * Check if this game mode uses flags.
     */
    public boolean hasFlags() {
        return flagsPerTeam > 0;
    }
    
    /**
     * Check if this game mode uses King of the Hill zones.
     */
    public boolean hasKothZones() {
        return kothZones > 0;
    }
    
    /**
     * Check if this game mode uses workshops.
     */
    public boolean hasWorkshops() {
        return addWorkshops;
    }
    
    /**
     * Check if this game mode uses headquarters.
     */
    public boolean hasHeadquarters() {
        return addHeadquarters;
    }
    
    /**
     * Check if players have limited lives.
     */
    public boolean hasLimitedLives() {
        return respawnMode == RespawnMode.LIMITED && maxLives > 0;
    }
    
    /**
     * Check if this mode uses wave respawns.
     */
    public boolean usesWaveRespawn() {
        return respawnMode == RespawnMode.WAVE;
    }
    
    /**
     * Check if players can respawn at all.
     */
    public boolean allowsRespawn() {
        return respawnMode != RespawnMode.ELIMINATION;
    }
    
    /**
     * Check if this game has a time limit.
     */
    public boolean hasTimeLimit() {
        return victoryCondition == VictoryCondition.TIME_LIMIT && timeLimit > 0;
    }
    
    /**
     * Check if this game has a score limit.
     */
    public boolean hasScoreLimit() {
        return victoryCondition == VictoryCondition.SCORE_LIMIT && scoreLimit > 0;
    }
    
    /**
     * Check if this game mode uses oddball.
     */
    public boolean hasOddball() {
        return enableOddball;
    }
    
    /**
     * Check if this game mode uses VIP.
     */
    public boolean hasVip() {
        return enableVip;
    }
    
    /**
     * Check if the game should lock after a certain time.
     */
    public boolean shouldLockGame() {
        return lockGameAfterSeconds > 0;
    }
    
    /**
     * Check if this game mode uses random weapon rotation.
     */
    public boolean hasRandomWeapons() {
        return enableRandomWeapons;
    }
}

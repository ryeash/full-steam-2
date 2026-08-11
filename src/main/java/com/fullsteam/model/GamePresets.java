package com.fullsteam.model;

import com.fullsteam.games.GameConfig;
import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Optional;

@Introspected
public class GamePresets {

    // --- Game Preset Container DTO ---
    @Introspected
    public record Preset(String id, String label, String description, GameConfig config) {
    }

    public static final Preset TEAM_BATTLE = new Preset("team-battle", "⚔️ Team Battle",
            "4-team tactical deathmatch with delayed respawns and a 5-minute time limit.",
            GameConfig.builder()
                    .maxPlayers(20)
                    .teamCount(4)
                    .worldWidth(2500)
                    .worldHeight(2500)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL_KILLS)
                            .victoryCondition(VictoryCondition.TIME_LIMIT)
                            .timeLimit(300.0)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(5.0)
                            .build())
                    .build());
    public static final Preset FREE_FOR_ALL = new Preset("ffa", "💥 Free For All",
            "Fast-paced free-for-all deathmatch where the first player to 25 kills wins.",
            GameConfig.builder()
                    .maxPlayers(10)
                    .teamCount(0)
                    .worldWidth(1500)
                    .worldHeight(1500)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL_KILLS)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(25)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(3.0)
                            .build())
                    .build());
    public static final Preset CAPTURE_THE_FLAG = new Preset("ctf", "🚩 Capture The Flag",
            "Capture enemy flags and defend your own in a 2-team objective battle.",
            GameConfig.builder()
                    .maxPlayers(12)
                    .teamCount(2)
                    .worldWidth(2500)
                    .worldHeight(2500)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .flagsPerTeam(1)
                            .scoreStyle(ScoreStyle.OBJECTIVE)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(3)
                            .gameStartCountdown(15)
                            .respawnMode(RespawnMode.WAVE)
                            .respawnDelay(5.0)
                            .waveRespawnInterval(20.0)
                            .build())
                    .build());
    public static final Preset DOMINATION = new Preset("domination", "👑 Domination",
            "Control 3 King-of-the-Hill zones across a 3-team battlefield to earn points.",
            GameConfig.builder()
                    .maxPlayers(15)
                    .teamCount(3)
                    .worldWidth(2200)
                    .worldHeight(2200)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.OBJECTIVE)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(200)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(4.0)
                            .kothZones(3)
                            .kothPointsPerSecond(2.0)
                            .build())
                    .build());
    public static final Preset ELIMINATION = new Preset("elimination", "🎯 Battle Royale",
            "50-player Battle Royale with 1 life, danger zones, and environmental hazards.",
            GameConfig.builder()
                    .maxPlayers(50)
                    .teamCount(0)
                    .worldWidth(4000)
                    .worldHeight(4000)
                    .playerMaxHealth(250)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL_KILLS)
                            .victoryCondition(VictoryCondition.ELIMINATION)
                            .gameStartCountdown(30.0)
                            .scoreLimit(50)
                            .lockGameAfterSeconds(15.0)
                            .respawnMode(RespawnMode.LIMITED)
                            .maxLives(1)
                            .enableRandomEvents(true)
                            .randomEventInterval(60.0)
                            .randomEventIntervalVariance(0.4)
                            .eventWarningDuration(4.0)
                            .build())
                    .build());
    public static final Preset STOCK_BATTLE = new Preset("stock-battle", "❤️ Stock Battle",
            "4-team elimination match where each player has 5 stock lives.",
            GameConfig.builder()
                    .maxPlayers(16)
                    .teamCount(4)
                    .worldWidth(1800)
                    .worldHeight(1800)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL_KILLS)
                            .victoryCondition(VictoryCondition.ELIMINATION)
                            .scoreLimit(50)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.LIMITED)
                            .respawnDelay(3.0)
                            .maxLives(5)
                            .build())
                    .build());
    public static final Preset HEADQUARTERS_ASSAULT = new Preset("headquarters-assault", "🏛️ Headquarters",
            "2-team base assault — breach and destroy the enemy HQ structure while defending your own.",
            GameConfig.builder()
                    .maxPlayers(16)
                    .teamCount(2)
                    .worldWidth(4000)
                    .worldHeight(1000)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(1000)
                            .respawnMode(RespawnMode.WAVE)
                            .waveRespawnInterval(25.0)
                            .addHeadquarters(true)
                            .headquartersMaxHealth(5000.0)
                            .headquartersPointsPerDamage(0.01)
                            .headquartersDestructionBonus(1000)
                            .headquartersDestructionEndsGame(true)
                            .build())
                    .build());
    public static final Preset CHAOS_MODE = new Preset("chaos-mode", "🌋 Chaos Mode",
            "3-team chaotic battle with multiple Hill zones and frequent random environmental hazard events.",
            GameConfig.builder()
                    .maxPlayers(15)
                    .teamCount(3)
                    .worldWidth(3000)
                    .worldHeight(2000)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL)
                            .victoryCondition(VictoryCondition.TIME_LIMIT)
                            .scoreLimit(100)
                            .timeLimit(600.0)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(3.0)
                            .kothZones(2)
                            .kothPointsPerSecond(1.5)
                            .enableRandomEvents(true)
                            .randomEventInterval(15.0)
                            .eventWarningDuration(5.0)
                            .meteorShowerDensity(EntityWorldDensity.CHOKED)
                            .volcanicEruptionDensity(EntityWorldDensity.DENSE)
                            .ionStormDensity(EntityWorldDensity.DENSE)
                            .earthquakeDensity(EntityWorldDensity.DENSE)
                            .blizzardDensity(EntityWorldDensity.DENSE)
                            .build())
                    .build());
    public static final Preset ODDBALL = new Preset("oddball", "⭐ Oddball",
            "Hunt down and attack high-DPS NPC Rampage & Seeker balls to score points.",
            GameConfig.builder()
                    .maxPlayers(12)
                    .teamCount(4)
                    .worldWidth(2500)
                    .worldHeight(2500)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.OBJECTIVE)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(1000)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(4.0)
                            .enableOddballNpcs(true)
                            .rampageBallCount(1)
                            .seekerBallCount(2)
                            .oddballNpcPointsPerDamage(0.1)
                            .build())
                    .build());
    public static final Preset VIP_ASSASSINATION = new Preset("vip-assassination", "🎖️ VIP",
            "Protect your team's designated VIP while hunting down and eliminating enemy VIPs.",
            GameConfig.builder()
                    .maxPlayers(15)
                    .teamCount(3)
                    .worldWidth(2200)
                    .worldHeight(2200)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.OBJECTIVE)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(10)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.WAVE)
                            .waveRespawnInterval(20.0)
                            .enableRandomEvents(true)
                            .randomEventInterval(50.0)
                            .eventWarningDuration(4.0)
                            .meteorShowerDensity(EntityWorldDensity.CHOKED)
                            .volcanicEruptionDensity(EntityWorldDensity.SPARSE)
                            .ionStormDensity(EntityWorldDensity.DENSE)
                            .earthquakeDensity(EntityWorldDensity.SPARSE)
                            .blizzardDensity(EntityWorldDensity.SPARSE)
                            .enableVip(true)
                            .obstacleDensity(EntityWorldDensity.DENSE)
                            .build())
                    .build());
    public static final Preset KINGPIN = new Preset("kingpin", "👑 Kingpin",
            "5-player FFA duel featuring single-zone control, environmental events, and rotating random weapons.",
            GameConfig.builder()
                    .maxPlayers(5)
                    .teamCount(0)
                    .worldWidth(1600)
                    .worldHeight(1600)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.OBJECTIVE)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(90)
                            .suddenDeath(true)
                            .respawnMode(RespawnMode.DELAYED)
                            .respawnDelay(3.0)
                            .kothZones(1)
                            .kothPointsPerSecond(1.0)
                            .enableRandomEvents(true)
                            .randomEventInterval(60.0)
                            .eventWarningDuration(4.0)
                            .meteorShowerDensity(EntityWorldDensity.SPARSE)
                            .volcanicEruptionDensity(EntityWorldDensity.SPARSE)
                            .ionStormDensity(EntityWorldDensity.SPARSE)
                            .earthquakeDensity(EntityWorldDensity.SPARSE)
                            .blizzardDensity(EntityWorldDensity.SPARSE)
                            .obstacleDensity(EntityWorldDensity.DENSE)
                            .pointsPerFlagCapture(1)
                            .enableRandomWeapons(true)
                            .build())
                    .build());
    public static final Preset LAST_STAND = new Preset("last-stand", "⚔️ Last Stand",
            "Free-for-all elimination round where dead players stay parked until a single survivor remains.",
            GameConfig.builder()
                    .maxPlayers(12)
                    .teamCount(0)
                    .worldWidth(1600)
                    .worldHeight(1600)
                    .playerMaxHealth(100)
                    .aiCheckIntervalMs(10000)
                    .enableAIFilling(true)
                    .rules(Rules.builder()
                            .scoreStyle(ScoreStyle.TOTAL_KILLS)
                            .victoryCondition(VictoryCondition.SCORE_LIMIT)
                            .scoreLimit(20)
                            .respawnMode(RespawnMode.LAST_STANDING)
                            .respawnDelay(0.0)
                            .waveRespawnInterval(30.0)
                            .build())
                    .build());

    public static final List<Preset> ALL_PRESETS = List.of(
            TEAM_BATTLE,
            FREE_FOR_ALL,
            CAPTURE_THE_FLAG,
            DOMINATION,
            ELIMINATION,
            STOCK_BATTLE,
            HEADQUARTERS_ASSAULT,
            CHAOS_MODE,
            ODDBALL,
            KINGPIN,
            VIP_ASSASSINATION,
            LAST_STAND);

    public static Optional<Preset> getPreset(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return ALL_PRESETS.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst();
    }
}

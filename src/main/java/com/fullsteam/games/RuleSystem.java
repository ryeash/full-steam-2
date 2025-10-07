package com.fullsteam.games;

import com.fullsteam.model.GameState;
import com.fullsteam.model.RespawnMode;
import com.fullsteam.model.RoundScore;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages all game rules including rounds, victory conditions, respawn modes, and scoring.
 * This class centralizes rule-related logic that was previously scattered in GameManager.
 */
public class RuleSystem {
    private static final Logger log = LoggerFactory.getLogger(RuleSystem.class);

    private final String gameId;
    private final Rules rules;
    private final GameEntities gameEntities;
    private final GameEventManager gameEventManager;
    private final Consumer<Map<String, Object>> broadcaster; // For broadcasting raw messages
    private final int teamCount;
    private final double playerMaxHealth;

    // Round state
    @Getter
    private GameState gameState = GameState.PLAYING;
    @Getter
    private int currentRound = 1;
    @Getter
    private double roundTimeRemaining = 0.0;
    @Getter
    private double restTimeRemaining = 0.0;
    private final Map<Integer, RoundScore> roundScores = new HashMap<>();

    // Victory state
    @Getter
    private double gameTimeElapsed = 0.0;
    @Getter
    private boolean gameOver = false;
    @Getter
    private String victoryMessage = null;
    @Getter
    private Integer winningTeam = null;
    @Getter
    private Integer winningPlayerId = null;

    // Respawn state
    @Getter
    private double waveRespawnTimer = 0.0;
    @Getter
    private final Set<Player> deadPlayersWaitingForWave = new HashSet<>();

    public RuleSystem(String gameId, Rules rules, GameEntities gameEntities,
                      GameEventManager gameEventManager, Consumer<Map<String, Object>> broadcaster,
                      int teamCount, double playerMaxHealth) {
        this.gameId = gameId;
        this.rules = rules;
        this.gameEntities = gameEntities;
        this.gameEventManager = gameEventManager;
        this.broadcaster = broadcaster;
        this.teamCount = teamCount;
        this.playerMaxHealth = playerMaxHealth;

        // Initialize round timer if rounds are enabled
        if (rules.getRoundDuration() > 0) {
            this.roundTimeRemaining = rules.getRoundDuration();
            log.info("Game {} initialized with round-based gameplay: {} second rounds, {} second rest",
                    gameId, rules.getRoundDuration(), rules.getRestDuration());
        }

        // Initialize wave respawn timer if using wave respawns
        if (rules.usesWaveRespawn()) {
            this.waveRespawnTimer = rules.getWaveRespawnInterval();
            log.info("Game {} initialized with wave respawn mode: {} second intervals",
                    gameId, rules.getWaveRespawnInterval());
        }
    }

    // ===== UPDATE METHODS =====

    /**
     * Update all rule systems with the given time delta.
     */
    public void update(double deltaTime) {
        if (gameOver) return;

        // Track total game time for time-limit victory condition
        gameTimeElapsed += deltaTime;

        // Update round state if rounds are enabled
        if (rules.getRoundDuration() > 0) {
            updateRoundState(deltaTime);
        }

        // Update wave respawn timer if using wave mode
        if (rules.usesWaveRespawn()) {
            updateWaveRespawn(deltaTime);
        }

        // Check victory conditions
        checkVictoryConditions();
    }

    // ===== ROUND MANAGEMENT =====

    private void updateRoundState(double deltaTime) {
        switch (gameState) {
            case PLAYING:
                updatePlayingState(deltaTime);
                break;
            case ROUND_END:
                updateRoundEndState(deltaTime);
                break;
            case REST_PERIOD:
                updateRestPeriodState(deltaTime);
                break;
        }
    }

    private void updatePlayingState(double deltaTime) {
        roundTimeRemaining -= deltaTime;

        if (roundTimeRemaining <= 0) {
            endRound();
        }
    }

    private void endRound() {
        gameState = GameState.ROUND_END;
        roundTimeRemaining = 0;

        // Capture current scores
        roundScores.clear();
        for (Player player : gameEntities.getAllPlayers()) {
            RoundScore score = RoundScore.builder()
                    .playerId(player.getId())
                    .playerName(player.getPlayerName())
                    .team(player.getTeam())
                    .kills(player.getKills())
                    .deaths(player.getDeaths())
                    .captures(player.getCaptures())
                    .build();
            roundScores.put(player.getId(), score);
        }

        log.info("Round {} ended in game {}. {} players scored.", currentRound, gameId, roundScores.size());

        // Broadcast round end event with scores
        Map<String, Object> roundEndEvent = new HashMap<>();
        roundEndEvent.put("type", "roundEnd");
        roundEndEvent.put("round", currentRound);
        roundEndEvent.put("scores", new ArrayList<>(roundScores.values()));
        roundEndEvent.put("restDuration", rules.getRestDuration());
        broadcaster.accept(roundEndEvent);

        // Start rest period
        restTimeRemaining = rules.getRestDuration();
    }

    private void updateRoundEndState(double deltaTime) {
        // Brief pause, then transition to rest period
        gameState = GameState.REST_PERIOD;
    }

    private void updateRestPeriodState(double deltaTime) {
        restTimeRemaining -= deltaTime;

        if (restTimeRemaining <= 0) {
            startNextRound();
        }
    }

    /**
     * Start the next round - to be called by GameManager for player reset logic.
     * Returns true if a new round was started.
     */
    public boolean startNextRound() {
        currentRound++;
        gameState = GameState.PLAYING;
        roundTimeRemaining = rules.getRoundDuration();
        restTimeRemaining = 0;

        log.info("Round {} started in game {}", currentRound, gameId);

        // Broadcast round start event
        Map<String, Object> roundStartEvent = new HashMap<>();
        roundStartEvent.put("type", "roundStart");
        roundStartEvent.put("round", currentRound);
        roundStartEvent.put("duration", rules.getRoundDuration());
        broadcaster.accept(roundStartEvent);

        return true;
    }

    // ===== RESPAWN MANAGEMENT =====

    private void updateWaveRespawn(double deltaTime) {
        waveRespawnTimer -= deltaTime;

        if (waveRespawnTimer <= 0 && !deadPlayersWaitingForWave.isEmpty()) {
            log.info("Wave respawn triggered! {} players ready", deadPlayersWaitingForWave.size());

            // Clear the waiting list and reset timer
            deadPlayersWaitingForWave.clear();
            waveRespawnTimer = rules.getWaveRespawnInterval();

            // Broadcast wave respawn event
            gameEventManager.broadcastSystemMessage("⚡ Wave Respawn!");
        }
    }

    /**
     * Handle player death according to respawn rules.
     * Returns the respawn mode action to take.
     */
    public RespawnAction handlePlayerDeath(Player victim) {
        RespawnMode respawnMode = rules.getRespawnMode();

        switch (respawnMode) {
            case INSTANT:
                return RespawnAction.RESPAWN_AFTER_DELAY;

            case WAVE:
                if (!deadPlayersWaitingForWave.contains(victim)) {
                    deadPlayersWaitingForWave.add(victim);
                    log.info("Player {} added to wave respawn queue. Queue size: {}",
                            victim.getId(), deadPlayersWaitingForWave.size());
                }
                return RespawnAction.WAIT_FOR_WAVE;

            case NEXT_ROUND:
                log.info("Player {} will respawn next round", victim.getId());
                return RespawnAction.WAIT_FOR_ROUND;

            case ELIMINATION:
                victim.eliminate();
                log.info("Player {} permanently eliminated", victim.getId());
                return RespawnAction.ELIMINATE;

            case LIMITED:
                if (victim.loseLife()) {
                    // Player ran out of lives
                    victim.eliminate();
                    log.info("Player {} eliminated (no lives remaining)", victim.getId());
                    gameEventManager.broadcastSystemMessage(
                            String.format("💀 %s has been eliminated!", victim.getPlayerName()));
                    return RespawnAction.ELIMINATE;
                } else {
                    // Player still has lives
                    log.info("Player {} died. Lives remaining: {}", victim.getId(), victim.getLivesRemaining());
                    return RespawnAction.RESPAWN_AFTER_DELAY;
                }

            default:
                return RespawnAction.RESPAWN_AFTER_DELAY;
        }
    }

    /**
     * Check if wave respawn should process now.
     */
    public boolean shouldProcessWaveRespawn() {
        return waveRespawnTimer <= 0 && !deadPlayersWaitingForWave.isEmpty();
    }

    // ===== VICTORY CONDITION MANAGEMENT =====

    private void checkVictoryConditions() {
        if (gameOver) return;

        VictoryCondition condition = rules.getVictoryCondition();
        if (condition == null || condition == VictoryCondition.ENDLESS) {
            return;
        }

        switch (condition) {
            case SCORE_LIMIT:
                checkScoreLimitVictory();
                break;
            case TIME_LIMIT:
                checkTimeLimitVictory();
                break;
            case ELIMINATION:
                checkEliminationVictory();
                break;
            case OBJECTIVE:
                checkObjectiveVictory();
                break;
        }
    }

    private void checkScoreLimitVictory() {
        int scoreLimit = rules.getScoreLimit();

        if (teamCount > 0) {
            // Team mode - check team scores
            Map<Integer, Integer> teamScores = calculateTeamScores();
            for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                if (entry.getValue() >= scoreLimit) {
                    declareTeamVictory(entry.getKey(),
                            String.format("Team %d wins with %d %s!",
                                    entry.getKey(), entry.getValue(), getScoreTypeName()));
                    return;
                }
            }
        } else {
            // FFA mode - check individual scores
            for (Player player : gameEntities.getAllPlayers()) {
                int score = getPlayerScore(player);
                if (score >= scoreLimit) {
                    declarePlayerVictory(player.getId(), player.getPlayerName(),
                            String.format("%s wins with %d %s!",
                                    player.getPlayerName(), score, getScoreTypeName()));
                    return;
                }
            }
        }
    }

    private void checkTimeLimitVictory() {
        if (gameTimeElapsed < rules.getTimeLimit()) {
            return;
        }

        if (teamCount > 0) {
            // Team mode
            Map<Integer, Integer> teamScores = calculateTeamScores();
            int winningTeamNum = -1;
            int highestScore = -1;
            int teamsWithHighScore = 0;

            for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                if (entry.getValue() > highestScore) {
                    highestScore = entry.getValue();
                    winningTeamNum = entry.getKey();
                    teamsWithHighScore = 1;
                } else if (entry.getValue() == highestScore) {
                    teamsWithHighScore++;
                }
            }

            if (teamsWithHighScore > 1 && rules.isSuddenDeath()) {
                enableSuddenDeath();
            } else if (winningTeamNum != -1) {
                declareTeamVictory(winningTeamNum,
                        String.format("Time's up! Team %d wins with %d %s!",
                                winningTeamNum, highestScore, getScoreTypeName()));
            }
        } else {
            // FFA mode
            Player winner = null;
            int highestScore = -1;
            int playersWithHighScore = 0;

            for (Player player : gameEntities.getAllPlayers()) {
                int score = getPlayerScore(player);
                if (score > highestScore) {
                    highestScore = score;
                    winner = player;
                    playersWithHighScore = 1;
                } else if (score == highestScore) {
                    playersWithHighScore++;
                }
            }

            if (playersWithHighScore > 1 && rules.isSuddenDeath()) {
                enableSuddenDeath();
            } else if (winner != null) {
                declarePlayerVictory(winner.getId(), winner.getPlayerName(),
                        String.format("Time's up! %s wins with %d %s!",
                                winner.getPlayerName(), highestScore, getScoreTypeName()));
            }
        }
    }

    private void checkEliminationVictory() {
        RespawnMode respawnMode = rules.getRespawnMode();

        if (respawnMode != RespawnMode.ELIMINATION && respawnMode != RespawnMode.LIMITED) {
            return;
        }

        // Count active (non-eliminated) players per team
        Map<Integer, Integer> activePlayersPerTeam = new HashMap<>();
        int totalActivePlayers = 0;

        for (Player player : gameEntities.getAllPlayers()) {
            if (!player.isEliminated() && player.hasLivesRemaining()) {
                activePlayersPerTeam.merge(player.getTeam(), 1, Integer::sum);
                totalActivePlayers++;
            }
        }

        if (totalActivePlayers == 0) {
            declareTeamVictory(-1, "All players eliminated! It's a draw!");
            return;
        }

        if (teamCount > 0) {
            if (activePlayersPerTeam.size() == 1) {
                int winningTeam = activePlayersPerTeam.keySet().iterator().next();
                int playerCount = activePlayersPerTeam.get(winningTeam);
                declareTeamVictory(winningTeam,
                        String.format("Team %d wins! Last team standing with %d player%s!",
                                winningTeam, playerCount, playerCount == 1 ? "" : "s"));
            }
        } else {
            if (totalActivePlayers == 1) {
                for (Player player : gameEntities.getAllPlayers()) {
                    if (!player.isEliminated() && player.hasLivesRemaining()) {
                        declarePlayerVictory(player.getId(), player.getPlayerName(),
                                String.format("%s wins! Last player standing!", player.getPlayerName()));
                        break;
                    }
                }
            }
        }
    }

    private void checkObjectiveVictory() {
        // Objective victory uses score limit (e.g., CTF captures, KOTH points)
        checkScoreLimitVictory();
    }

    private void enableSuddenDeath() {
        if (gameOver) return;

        log.info("Game {} entering sudden death mode - next score wins!", gameId);
        gameEventManager.broadcastSystemMessage("⚠️ SUDDEN DEATH! Next score wins!");

        int currentHighest = getCurrentHighestScore();
        rules.setScoreLimit(currentHighest + 1);
    }

    private void declareTeamVictory(int teamNumber, String message) {
        gameOver = true;
        winningTeam = teamNumber;
        victoryMessage = message;

        log.info("Game {} ended - Team {} wins!", gameId, teamNumber);

        Map<String, Object> victoryEvent = new HashMap<>();
        victoryEvent.put("type", "gameOver");
        victoryEvent.put("winningTeam", teamNumber);
        victoryEvent.put("victoryMessage", message);
        victoryEvent.put("victoryCondition", rules.getVictoryCondition());
        victoryEvent.put("finalScores", calculateFinalScores());
        broadcaster.accept(victoryEvent);

        gameEventManager.broadcastSystemMessage("🏆 " + message);
    }

    private void declarePlayerVictory(int playerId, String playerName, String message) {
        gameOver = true;
        winningPlayerId = playerId;
        victoryMessage = message;

        log.info("Game {} ended - Player {} ({}) wins!", gameId, playerId, playerName);

        Map<String, Object> victoryEvent = new HashMap<>();
        victoryEvent.put("type", "gameOver");
        victoryEvent.put("winningPlayerId", playerId);
        victoryEvent.put("winningPlayerName", playerName);
        victoryEvent.put("victoryMessage", message);
        victoryEvent.put("victoryCondition", rules.getVictoryCondition());
        victoryEvent.put("finalScores", calculateFinalScores());
        broadcaster.accept(victoryEvent);

        gameEventManager.broadcastSystemMessage("🏆 " + message);
    }

    // ===== SCORING HELPERS =====

    private Map<Integer, Integer> calculateTeamScores() {
        Map<Integer, Integer> teamScores = new HashMap<>();
        
        // Add player-based scores based on ScoreStyle
        if (rules.getScoreStyle() == ScoreStyle.TOTAL_KILLS || 
            rules.getScoreStyle() == ScoreStyle.CAPTURES || 
            rules.getScoreStyle() == ScoreStyle.TOTAL) {
            
            for (Player player : gameEntities.getAllPlayers()) {
                int score = getPlayerScore(player);
                teamScores.merge(player.getTeam(), score, Integer::sum);
            }
        }
        
        // Add KOTH zone scores based on ScoreStyle
        if (rules.getScoreStyle() == ScoreStyle.KOTH_ZONES || 
            rules.getScoreStyle() == ScoreStyle.TOTAL) {
            
            for (KothZone zone : gameEntities.getAllKothZones()) {
                Map<Integer, Double> zoneTeamScores = zone.getAllTeamScores();
                for (Map.Entry<Integer, Double> entry : zoneTeamScores.entrySet()) {
                    int team = entry.getKey();
                    double kothPoints = entry.getValue();
                    // Convert KOTH points to integer kills for now (can be refined later)
                    int kothScore = (int) Math.round(kothPoints);
                    teamScores.merge(team, kothScore, Integer::sum);
                }
            }
        }
        
        return teamScores;
    }

    private int getPlayerScore(Player player) {
        return switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> player.getKills();
            case CAPTURES -> player.getCaptures();
            case KOTH_ZONES -> 0; // KOTH scores are handled separately in calculateTeamScores()
            case TOTAL -> player.getKills() + player.getCaptures();
        };
    }

    private String getScoreTypeName() {
        return switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> "kills";
            case CAPTURES -> "captures";
            case KOTH_ZONES -> "zone control";
            case TOTAL -> "points";
        };
    }

    private int getCurrentHighestScore() {
        if (teamCount > 0) {
            return calculateTeamScores().values().stream()
                    .max(Integer::compare).orElse(0);
        } else {
            return gameEntities.getAllPlayers().stream()
                    .mapToInt(this::getPlayerScore)
                    .max().orElse(0);
        }
    }

    private List<Map<String, Object>> calculateFinalScores() {
        List<Map<String, Object>> scores = new ArrayList<>();

        if (teamCount > 0) {
            Map<Integer, Integer> teamScores = calculateTeamScores();
            Map<Integer, Integer> teamKills = new HashMap<>();
            Map<Integer, Integer> teamDeaths = new HashMap<>();
            Map<Integer, Integer> teamCaptures = new HashMap<>();

            for (Player player : gameEntities.getAllPlayers()) {
                int team = player.getTeam();
                teamKills.merge(team, player.getKills(), Integer::sum);
                teamDeaths.merge(team, player.getDeaths(), Integer::sum);
                teamCaptures.merge(team, player.getCaptures(), Integer::sum);
            }

            for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                Map<String, Object> teamScore = new HashMap<>();
                teamScore.put("team", entry.getKey());
                teamScore.put("score", entry.getValue());
                teamScore.put("kills", teamKills.getOrDefault(entry.getKey(), 0));
                teamScore.put("deaths", teamDeaths.getOrDefault(entry.getKey(), 0));
                teamScore.put("captures", teamCaptures.getOrDefault(entry.getKey(), 0));
                scores.add(teamScore);
            }
        } else {
            for (Player player : gameEntities.getAllPlayers()) {
                Map<String, Object> playerScore = new HashMap<>();
                playerScore.put("playerId", player.getId());
                playerScore.put("playerName", player.getPlayerName());
                playerScore.put("score", getPlayerScore(player));
                playerScore.put("kills", player.getKills());
                playerScore.put("deaths", player.getDeaths());
                playerScore.put("captures", player.getCaptures());
                scores.add(playerScore);
            }
        }

        return scores;
    }

    // ===== PUBLIC HELPER METHODS =====

    /**
     * Get game state data for broadcasting to clients.
     */
    public Map<String, Object> getStateData() {
        Map<String, Object> data = new HashMap<>();

        // Round data
        if (rules.getRoundDuration() > 0) {
            data.put("roundEnabled", true);
            data.put("currentRound", currentRound);
            data.put("gameState", gameState.name());
            data.put("roundTimeRemaining", roundTimeRemaining);
            data.put("restTimeRemaining", restTimeRemaining);
        } else {
            data.put("roundEnabled", false);
        }

        // Victory data
        data.put("gameOver", gameOver);
        if (gameOver) {
            data.put("victoryMessage", victoryMessage);
            data.put("winningTeam", winningTeam);
            data.put("winningPlayerId", winningPlayerId);
        }

        // Respawn data
        if (rules.usesWaveRespawn()) {
            data.put("waveRespawnTimer", waveRespawnTimer);
            data.put("playersWaitingForWave", deadPlayersWaitingForWave.size());
        }

        // Team scores
        data.put("teamScores", calculateTeamScores());

        return data;
    }

    /**
     * Initialize player lives if using limited respawn mode.
     */
    public void initializePlayerLives(Player player) {
        if (rules.hasLimitedLives()) {
            player.initializeLives(rules.getMaxLives());
            log.info("Player {} initialized with {} lives", player.getId(), rules.getMaxLives());
        }
    }

    /**
     * Respawn action enum for communicating what should happen after death.
     */
    public enum RespawnAction {
        RESPAWN_AFTER_DELAY,  // Instant mode - respawn after delay
        WAIT_FOR_WAVE,        // Wave mode - wait for next wave
        WAIT_FOR_ROUND,       // Next round mode - wait for round end
        ELIMINATE             // Elimination/limited - player is out
    }
}

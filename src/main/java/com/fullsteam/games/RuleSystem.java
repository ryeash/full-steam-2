package com.fullsteam.games;

import com.fullsteam.model.GameState;
import com.fullsteam.model.RespawnMode;
import com.fullsteam.model.RoundScore;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Round state
    @Getter
    private GameState gameState = GameState.PLAYING;
    @Getter
    private int currentRound = 1;
    @Getter
    private long roundEndTime = 0L;
    @Getter
    private long restTimeEnd = 0L;
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
    @Getter
    private long waveRespawnTime = 0;

    // Bonus points tracking (for HQ damage, objectives, etc.)
    private final Map<Integer, Integer> bonusTeamPoints = new HashMap<>();

    public RuleSystem(String gameId, Rules rules, GameEntities gameEntities,
                      GameEventManager gameEventManager, Consumer<Map<String, Object>> broadcaster,
                      int teamCount) {
        this.gameId = gameId;
        this.rules = rules;
        this.gameEntities = gameEntities;
        this.gameEventManager = gameEventManager;
        this.broadcaster = broadcaster;
        this.teamCount = teamCount;

        // Initialize round timer if rounds are enabled
        if (rules.getRoundDuration() > 0) {
            this.roundEndTime = (long) (System.currentTimeMillis() + (rules.getRoundDuration() * 1000));
        }
    }

    // ===== UPDATE METHODS =====

    /**
     * Update all rule systems with the given time delta.
     */
    public void update(double deltaTime) {
        if (gameOver) {
            return;
        }

        // Track total game time for time-limit victory condition
        gameTimeElapsed += deltaTime;

        // Update round state if rounds are enabled
        if (rules.getRoundDuration() > 0) {
            updateRoundState();
        }

        // Update wave respawn timer if using wave mode
        if (rules.usesWaveRespawn()) {
            updateWaveRespawn();
        }

        // Check victory conditions
        checkVictoryConditions();
    }

    private void updateRoundState() {
        switch (gameState) {
            case PLAYING:
                updatePlayingState();
                break;
            case ROUND_END:
                updateRoundEndState();
                break;
            case REST_PERIOD:
                updateRestPeriodState();
                break;
        }
    }

    private void updatePlayingState() {
        if (System.currentTimeMillis() > roundEndTime) {
            endRound();
        }
    }

    private void endRound() {
        gameState = GameState.ROUND_END;
        roundEndTime = 0;
        restTimeEnd = (long) (System.currentTimeMillis() + (rules.getRestDuration() * 1000));

        // Capture current scores
        roundScores.clear();
        for (Player player : gameEntities.getAllPlayers()) {
            // Get team bonus points for this player's team
            int teamBonus = bonusTeamPoints.getOrDefault(player.getTeam(), 0);

            RoundScore score = RoundScore.builder()
                    .playerId(player.getId())
                    .playerName(player.getPlayerName())
                    .team(player.getTeam())
                    .kills(player.getKills())
                    .deaths(player.getDeaths())
                    .captures(player.getCaptures())
                    .bonusPoints(teamBonus)
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
    }

    private void updateRoundEndState() {
        gameState = GameState.REST_PERIOD;
    }

    private void updateRestPeriodState() {
        if (System.currentTimeMillis() > restTimeEnd) {
            startNextRound();
        }
    }

    /**
     * Start the next round - to be called by GameManager for player reset logic.
     * Returns true if a new round was started.
     */
    public void startNextRound() {
        currentRound++;
        gameState = GameState.PLAYING;
        roundEndTime = (long) (System.currentTimeMillis() + (rules.getRoundDuration() * 1000));
        restTimeEnd = 0;

        // Reset player lives for stock mode (LIMITED respawn mode)
        resetPlayerLivesForNewRound();

        gameEntities.getPlayers().values().forEach(p -> {
            // force a respawn of all players
            p.setActive(false);
            p.setRespawnTime(1L);
            // reset scoring
            p.setKills(0);
            p.setDeaths(0);
        });

        bonusTeamPoints.clear();

        gameEntities.getFlags().values().forEach(Flag::returnToHome);
        gameEntities.getDefenseLasers().clear();
        gameEntities.getFieldEffects().clear();
        gameEntities.getBeams().clear();
        gameEntities.getProjectiles().clear();

        // Broadcast round start event
        Map<String, Object> roundStartEvent = new HashMap<>();
        roundStartEvent.put("type", "roundStart");
        roundStartEvent.put("round", currentRound);
        roundStartEvent.put("duration", rules.getRoundDuration());
        broadcaster.accept(roundStartEvent);
    }

    private void updateWaveRespawn() {
        if (System.currentTimeMillis() >= waveRespawnTime) {
            // Broadcast wave respawn event
            gameEventManager.broadcastSystemMessage("⚡ Wave Respawn!");
            waveRespawnTime = (long) (System.currentTimeMillis() + (rules.getWaveRespawnInterval() * 1000));
        }
    }

    /**
     * Check if a player should be allowed to respawn based on current rules.
     * This centralizes all respawn logic in the RuleSystem.
     */
    public boolean shouldPlayerRespawn(Player player) {
        if (player.isActive()) {
            return false; // Player is already active
        }
        return player.hasLivesRemaining()
                && player.getRespawnTime() > 0
                && System.currentTimeMillis() > player.getRespawnTime();
    }

    public void setRespawnTime(Player player) {
        // this player is not dead, reset respawn and carry on
        if (player.isActive()) {
            player.setRespawnTime(0);
            return;
        }
        switch (rules.getRespawnMode()) {
            case INSTANT:
                player.setRespawnTime((long) (System.currentTimeMillis() + (rules.getRespawnDelay() * 1000)));
                break;
            case WAVE:
                player.setRespawnTime(waveRespawnTime);
                break;
            case NEXT_ROUND:
            case ELIMINATION:
                player.setRespawnTime(roundEndTime);
                break;
            case LIMITED:
                if (player.isEliminated()) {
                    player.setRespawnTime(0);
                } else {
                    player.setRespawnTime((long) (System.currentTimeMillis() + (rules.getRespawnDelay() * 1000)));
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + rules.getRespawnMode());
        }
    }

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

    /**
     * Public method to manually declare victory (e.g., for HQ destruction).
     */
    public void declareVictory(int winningTeamNumber, int winningPlayerId, String reason) {
        if (winningTeamNumber > 0) {
            declareTeamVictory(winningTeamNumber,
                    String.format("Team %d wins - %s!", winningTeamNumber, reason));
        } else if (winningPlayerId >= 0) {
            Player winner = gameEntities.getPlayer(winningPlayerId);
            if (winner != null) {
                declarePlayerVictory(winner.getId(), winner.getPlayerName(),
                        String.format("%s wins - %s!", winner.getPlayerName(), reason));
            }
        }
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

        // Add bonus points (HQ damage, objectives, etc.) - always included
        for (Map.Entry<Integer, Integer> entry : bonusTeamPoints.entrySet()) {
            teamScores.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        return teamScores;
    }

    /**
     * Add bonus points to a team's score (for HQ damage, objectives, etc.).
     * These points are always added regardless of ScoreStyle.
     */
    public void addTeamPoints(int team, int points) {
        if (team > 0 && points > 0) {
            bonusTeamPoints.merge(team, points, Integer::sum);
            log.debug("Added {} bonus points to team {}. Total bonus: {}",
                    points, team, bonusTeamPoints.get(team));

            // Check victory conditions after adding points
            checkVictoryConditions();
        }
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
            data.put("roundTimeRemaining", Math.max(0, (roundEndTime - System.currentTimeMillis()) / 1000));
            data.put("restTimeRemaining", Math.max(0, (restTimeEnd - System.currentTimeMillis()) / 1000));
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

        // Team scores
        data.put("teamScores", calculateTeamScores());

        // Scoring style info
        data.put("scoreStyle", rules.getScoreStyle().name());

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
     * Reset all player lives for a new round (stock mode).
     * This ensures players get their lives back at the start of each round.
     */
    private void resetPlayerLivesForNewRound() {
        if (rules.hasLimitedLives()) {
            for (Player player : gameEntities.getAllPlayers()) {
                player.initializeLives(rules.getMaxLives());
                log.info("Player {} lives reset to {} for round {}",
                        player.getId(), rules.getMaxLives(), currentRound);
            }

            // Broadcast lives reset message
            gameEventManager.broadcastSystemMessage(
                    String.format("🔄 Round %d - All players have %d lives!",
                            currentRound, rules.getMaxLives()));
        }
    }
}

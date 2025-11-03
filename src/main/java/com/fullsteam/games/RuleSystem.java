package com.fullsteam.games;

import com.fullsteam.ai.AIWeaponSelector;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.RespawnMode;
import com.fullsteam.model.RoundScore;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.PowerUp;
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
    private final long start = System.currentTimeMillis();
    private final Rules rules;
    private final GameEntities gameEntities;
    private final GameEventManager gameEventManager;
    private final Consumer<Map<String, Object>> broadcaster; // For broadcasting raw messages
    private final int teamCount;

    // Event system (optional)
    private EventSystem eventSystem = null;

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

    // Oddball scoring tracking (playerId -> total points earned)
    private final Map<Integer, Double> oddballPlayerScores = new HashMap<>();

    // VIP kill tracking (team number -> VIP kills scored)
    private final Map<Integer, Integer> vipKillScores = new HashMap<>();

    // VIP validation timer (check every 2 seconds)
    private long lastVipCheckTime = 0;
    private static final long VIP_CHECK_INTERVAL_MS = 2000;

    // Random weapon rotation tracking
    private long nextWeaponRotationTime = 0L;

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

        // Initialize VIP mode if enabled
        if (rules.hasVip()) {
            initializeVipMode();
        }

        // Initialize random weapon rotation if enabled
        if (rules.hasRandomWeapons()) {
            scheduleNextWeaponRotation();
        }
    }

    // ===== UPDATE METHODS =====

    /**
     * Initialize the event system if enabled.
     * Must be called after construction with the required dependencies.
     */
    public void initializeEventSystem(TerrainGenerator terrainGenerator,
                                      Consumer<FieldEffect> fieldEffectSpawner,
                                      Consumer<PowerUp> powerUpSpawner,
                                      double worldWidth, double worldHeight) {
        if (rules.isEnableRandomEvents() && eventSystem == null) {
            this.eventSystem = new EventSystem(
                    gameId,
                    rules,
                    gameEntities,
                    gameEventManager,
                    terrainGenerator,
                    worldWidth,
                    worldHeight
            );
            log.debug("Event system initialized for game {}", gameId);
        }
    }

    /**
     * Initialize VIP mode - select one VIP per team.
     */
    private void initializeVipMode() {
        if (!rules.hasVip() || teamCount == 0) {
            return;
        }

        log.info("Initializing VIP mode for game {}", gameId);

        // Select one VIP per team
        for (int team = 1; team <= teamCount; team++) {
            selectVipForTeam(team);
        }
    }

    /**
     * Select a VIP for a specific team.
     * Chooses the first active player on the team.
     */
    private void selectVipForTeam(int teamNumber) {
        // Find all active players on this team
        List<Player> teamPlayers = gameEntities.getAllPlayers().stream()
                .filter(p -> p.getTeam() == teamNumber && p.isActive())
                .toList();

        if (teamPlayers.isEmpty()) {
            log.debug("No active players on team {} to select as VIP", teamNumber);
            return;
        }

        // Select first player as VIP (could be randomized or based on score)
        Player vip = teamPlayers.get(0);
        setPlayerAsVip(vip);

        log.info("Player {} ({}) selected as VIP for team {}",
                vip.getId(), vip.getPlayerName(), teamNumber);
    }

    /**
     * Set a player as the VIP for their team.
     * Removes VIP status from previous VIP if any.
     */
    private void setPlayerAsVip(Player player) {
        int teamNumber = player.getTeam();

        // Remove VIP status from previous VIP
        Integer previousVipId = gameEntities.getTeamVip(teamNumber);
        if (previousVipId != null) {
            Player previousVip = gameEntities.getPlayer(previousVipId);
            if (previousVip != null) {
                StatusEffectManager.removeVipStatus(previousVip);
            }
        }

        // Set new VIP
        gameEntities.setTeamVip(teamNumber, player.getId());
        StatusEffectManager.applyVipStatus(player);

        // Broadcast VIP selection event
        gameEventManager.broadcastSystemMessage(
                String.format("👑 %s is now the VIP for Team %d!",
                        player.getPlayerName(), teamNumber));
    }

    /**
     * Periodically validate that all teams have valid VIPs assigned.
     * This runs in the game loop to catch any edge cases where VIP status might be lost.
     */
    private void validateVipAssignments() {
        long currentTime = System.currentTimeMillis();

        // Only check every VIP_CHECK_INTERVAL_MS to avoid excessive checking
        if (currentTime - lastVipCheckTime < VIP_CHECK_INTERVAL_MS) {
            return;
        }

        lastVipCheckTime = currentTime;

        // Check each team
        for (int team = 1; team <= teamCount; team++) {
            ensureVipForTeam(team);
        }
    }

    /**
     * Check if VIP needs to be reassigned for a team (e.g., VIP left or died).
     * Called when a player leaves or when needed.
     */
    public void ensureVipForTeam(int teamNumber) {
        if (!rules.hasVip()) {
            return;
        }

        Integer currentVipId = gameEntities.getTeamVip(teamNumber);
        Player currentVip = currentVipId != null ? gameEntities.getPlayer(currentVipId) : null;

        // Check if current VIP is still valid
        if (currentVip != null && currentVip.isActive()) {
            return; // VIP is still valid
        }

        // Need to select a new VIP
        log.debug("VIP for team {} is no longer valid, selecting new VIP", teamNumber);
        selectVipForTeam(teamNumber);
    }

    /**
     * Award points for a VIP kill.
     * Only VIP kills count towards objective scoring in VIP mode.
     */
    public void awardVipKill(int killerTeam) {
        if (!rules.hasVip() || killerTeam <= 0) {
            return;
        }

        vipKillScores.merge(killerTeam, 1, Integer::sum);
        log.info("Team {} scored VIP kill. Total VIP kills: {} (Score Style: {})",
                killerTeam, vipKillScores.get(killerTeam), rules.getScoreStyle());

        // Check victory conditions after VIP kill
        checkVictoryConditions();
    }

    /**
     * Update all rule systems with the given time delta.
     */
    public void update(double deltaTime) {
        if (gameOver) {
            return;
        }

        // Update round state if rounds are enabled
        if (rules.getRoundDuration() > 0) {
            updateRoundState();
        }

        // Update wave respawn timer if using wave mode
        if (rules.usesWaveRespawn()) {
            updateWaveRespawn();
        }

        // Update event system if enabled
        if (eventSystem != null) {
            eventSystem.update(deltaTime);
        }

        // Periodically validate VIP assignments if VIP mode is enabled
        if (rules.hasVip()) {
            validateVipAssignments();
        }

        // Update random weapon rotation if enabled
        if (rules.hasRandomWeapons()) {
            updateWeaponRotation();
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
        vipKillScores.clear();

        gameEntities.getFlags().values().forEach(Flag::returnToHome);
        gameEntities.getDefenseLasers().clear();
        gameEntities.getFieldEffects().clear();
        gameEntities.getBeams().clear();
        gameEntities.getProjectiles().clear();

        // Reassign VIPs for new round
        if (rules.hasVip()) {
            for (int team = 1; team <= teamCount; team++) {
                selectVipForTeam(team);
            }
        }

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
        boolean hasLives = player.hasLivesRemaining();
        boolean hasRespawnTime = player.getRespawnTime() > 0;
        boolean timeElapsed = System.currentTimeMillis() > player.getRespawnTime();
        return hasLives && hasRespawnTime && timeElapsed;
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
                    log.info("Player {} eliminated, no respawn", player.getId());
                    player.setRespawnTime(0);
                } else {
                    long respawnTime = (long) (System.currentTimeMillis() + (rules.getRespawnDelay() * 1000));
                    player.setRespawnTime(respawnTime);
                    log.info("Player {} will respawn in {} seconds (lives: {})",
                            player.getId(), rules.getRespawnDelay(), player.getLivesRemaining());
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + rules.getRespawnMode());
        }
    }

    private void checkVictoryConditions() {
        if (gameOver) {
            return;
        }

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
        if (System.currentTimeMillis() < start + (rules.getTimeLimit() * 1000)) {
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
                        player.setPlacement(1); // Winner gets 1st place
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
        if (gameOver) {
            return;
        }
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
                rules.getScoreStyle() == ScoreStyle.TOTAL) {

            for (Player player : gameEntities.getAllPlayers()) {
                int score = getPlayerScore(player);
                teamScores.merge(player.getTeam(), score, Integer::sum);
            }
        }

        // Add objective scores (KOTH zones, captures, oddball, VIP kills) based on ScoreStyle
        if (rules.getScoreStyle() == ScoreStyle.OBJECTIVE ||
                rules.getScoreStyle() == ScoreStyle.TOTAL) {

            // Add player captures
            for (Player player : gameEntities.getAllPlayers()) {
                teamScores.merge(player.getTeam(), player.getCaptures(), Integer::sum);
            }

            // Add KOTH zone scores
            for (KothZone zone : gameEntities.getAllKothZones()) {
                Map<Integer, Double> zoneTeamScores = zone.getAllTeamScores();
                for (Map.Entry<Integer, Double> entry : zoneTeamScores.entrySet()) {
                    int team = entry.getKey();
                    double kothPoints = entry.getValue();
                    // Convert KOTH points to integer for scoring
                    int kothScore = (int) Math.round(kothPoints);
                    teamScores.merge(team, kothScore, Integer::sum);
                }
            }

            // Add oddball scores (per player, then summed to team)
            if (rules.hasOddball()) {
                for (Map.Entry<Integer, Double> entry : oddballPlayerScores.entrySet()) {
                    Player player = gameEntities.getPlayer(entry.getKey());
                    if (player != null) {
                        int oddballScore = (int) Math.round(entry.getValue());
                        teamScores.merge(player.getTeam(), oddballScore, Integer::sum);
                    }
                }
            }

            // Add VIP kill scores
            if (rules.hasVip()) {
                for (Map.Entry<Integer, Integer> entry : vipKillScores.entrySet()) {
                    teamScores.merge(entry.getKey(), entry.getValue(), Integer::sum);
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

    /**
     * Award oddball points to a player for holding the ball.
     * This method is called by CollisionProcessor during gameplay.
     *
     * @param playerId The player ID to award points to
     * @param points   The number of points to award
     */
    public void awardOddballPoints(int playerId, double points) {
        if (playerId >= 0 && points > 0) {
            oddballPlayerScores.merge(playerId, points, Double::sum);
            log.debug("Awarded {} oddball points to player {}. Total: {}",
                    points, playerId, oddballPlayerScores.get(playerId));

            // Check victory conditions after adding points
            checkVictoryConditions();
        }
    }

    /**
     * Get the oddball score for a specific player.
     *
     * @param playerId The player ID
     * @return The total oddball points earned by this player
     */
    public double getOddballScore(int playerId) {
        return oddballPlayerScores.getOrDefault(playerId, 0.0);
    }

    private int getPlayerScore(Player player) {
        int score = switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> player.getKills();
            case OBJECTIVE -> player.getCaptures(); // Only captures for individual scoring; KOTH is team-based
            case TOTAL -> player.getKills() + player.getCaptures();
        };

        // Add oddball scores for this player
        if (rules.hasOddball()) {
            double oddballScore = getOddballScore(player.getId());
            score += (int) Math.round(oddballScore);
        }

        return score;
    }

    private String getScoreTypeName() {
        return switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> "kills";
            case OBJECTIVE -> "objectives";
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
            // FFA mode
            List<Player> players = new ArrayList<>(gameEntities.getAllPlayers());

            // In ELIMINATION mode, sort by placement first, then by kills
            if (rules.getVictoryCondition() == VictoryCondition.ELIMINATION) {
                players.sort((p1, p2) -> {
                    // Sort by placement (lower is better: 1st place < 2nd place)
                    if (p1.getPlacement() != p2.getPlacement()) {
                        return Integer.compare(p1.getPlacement(), p2.getPlacement());
                    }
                    // If placement is the same (or 0), sort by kills (higher is better)
                    if (p2.getKills() != p1.getKills()) {
                        return Integer.compare(p2.getKills(), p1.getKills());
                    }
                    // If kills are the same, sort by elimination time (later is better - survived longer)
                    return Long.compare(p2.getEliminationTime(), p1.getEliminationTime());
                });
            } else {
                // Other modes: sort by score
                players.sort((p1, p2) -> Integer.compare(getPlayerScore(p2), getPlayerScore(p1)));
            }

            for (Player player : players) {
                Map<String, Object> playerScore = new HashMap<>();
                playerScore.put("playerId", player.getId());
                playerScore.put("playerName", player.getPlayerName());
                playerScore.put("score", getPlayerScore(player));
                playerScore.put("kills", player.getKills());
                playerScore.put("deaths", player.getDeaths());
                playerScore.put("captures", player.getCaptures());
                playerScore.put("placement", player.getPlacement());
                playerScore.put("eliminationTime", player.getEliminationTime());
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

        // Event data
        if (eventSystem != null) {
            data.put("activeEvents", eventSystem.getEventData());
        }

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

    // ===== RANDOM WEAPON ROTATION =====

    /**
     * Schedule the next weapon rotation.
     */
    private void scheduleNextWeaponRotation() {
        nextWeaponRotationTime = (long) (System.currentTimeMillis() +
                (rules.getRandomWeaponInterval() * 1000));
        log.debug("Next weapon rotation scheduled for game {} in {} seconds",
                gameId, rules.getRandomWeaponInterval());
    }

    /**
     * Update weapon rotation timer and rotate weapons when time is up.
     */
    private void updateWeaponRotation() {
        if (System.currentTimeMillis() >= nextWeaponRotationTime) {
            rotateAllPlayerWeapons();
            scheduleNextWeaponRotation();
        }
    }

    /**
     * Rotate all active players to new random weapons.
     * Excludes healing weapons to maintain combat focus.
     */
    private void rotateAllPlayerWeapons() {
        int rotatedCount = 0;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.isActive()) {
                WeaponConfig newWeapon = AIWeaponSelector.selectRandomNonHealingWeapon();
                UtilityWeapon newUtility = AIWeaponSelector.selectRandomUtilityWeapon();

                // Notify player of their new loadout
                String message = String.format("🔀 New Loadout: %s + %s", newWeapon.getType(), newUtility.getDisplayName());
                gameEventManager.broadcastToPlayer(message, player.getId(), GameEvent.EventCategory.INFO);

                player.applyWeaponConfig(newWeapon, newUtility);
                rotatedCount++;

                log.debug("Player {} ({}) assigned new weapons: {}, {}",
                        player.getId(), player.getPlayerName(),
                        newWeapon.getType(), newUtility.getDisplayName());
            }
        }

        if (rotatedCount > 0) {
            gameEventManager.broadcastSystemMessage("🔄 Weapon Rotation! New loadouts assigned!");
            log.info("Game {} - Rotated weapons for {} players", gameId, rotatedCount);
        }
    }
}

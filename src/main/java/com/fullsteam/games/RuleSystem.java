package com.fullsteam.games;

import com.fullsteam.ai.AIWeaponSelector;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.RespawnMode;
import com.fullsteam.model.Rules;
import com.fullsteam.model.Scoring;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.model.WeaponConfig;
import com.fullsteam.physics.GameEntities;
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
    private final long start = System.currentTimeMillis();
    @Getter
    private final Rules rules;
    private final GameEntities gameEntities;
    private final GameEventManager gameEventManager;
    private final Consumer<Map<String, Object>> broadcaster; // For broadcasting raw messages
    private final int teamCount;

    // Event system (optional)
    private EventSystem eventSystem = null;

    // Game state
    @Getter
    private GameState gameState;
    @Getter
    private double startCountdownRemaining = 0.0;
    @Getter
    private long matchStartTime;

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

    // All scoring (kills, captures, KOTH, oddball, HQ damage, VIP kills, etc.) is
    // tracked per player in Player.getScoring(); team/FFA totals are derived by
    // summing Scoring.total(rules). See com.fullsteam.model.Scoring.

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

        this.matchStartTime = System.currentTimeMillis();
        if (rules.hasGameStartCountdown()) {
            this.gameState = GameState.COUNTDOWN;
            this.startCountdownRemaining = rules.getGameStartCountdown();
        } else {
            this.gameState = GameState.PLAYING;
            this.startCountdownRemaining = 0.0;
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

    /**
     * Initialize the event system if enabled.
     * Must be called after construction with the required dependencies.
     */
    public void initializeEventSystem(TerrainGenerator terrainGenerator, double worldWidth, double worldHeight) {
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
        Player vip = teamPlayers.getFirst();
        setPlayerAsVip(vip);

        log.debug("Player {} ({}) selected as VIP for team {}",
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
                String.format("👑 %s is now the VIP for Team %d!", player.getPlayerName(), teamNumber));
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

    public boolean isCountdown() {
        return gameState == GameState.COUNTDOWN;
    }

    /**
     * Update all rule systems with the given time delta.
     */
    public void update(double deltaTime) {
        if (gameOver) {
            return;
        }

        if (gameState == GameState.COUNTDOWN) {
            startCountdownRemaining -= deltaTime;
            if (startCountdownRemaining <= 0) {
                gameState = GameState.PLAYING;
                startCountdownRemaining = 0.0;
                matchStartTime = System.currentTimeMillis();
                log.info("Game {} start countdown finished; gameplay is now PLAYING.", gameId);
                gameEventManager.broadcastSystemMessage("⚔️ Game started! Fight!");
            } else {
                return;
            }
        }

        // Update wave respawn timer if using wave mode
        if (rules.usesWaveRespawn()) {
            updateWaveRespawn();
        }

        // Release the waiting group if the arena has collapsed to one survivor
        if (rules.usesLastStanding()) {
            updateLastStanding();
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

    private void updateWaveRespawn() {
        if (System.currentTimeMillis() >= waveRespawnTime) {
            // Broadcast wave respawn event
            gameEventManager.broadcastSystemMessage("⚡ Wave Respawn!");
            waveRespawnTime = (long) (System.currentTimeMillis() + (rules.getWaveRespawnInterval() * 1000));
        }
    }

    /**
     * "Last one standing" respawn: dead players are parked (no timer) until the
     * arena resolves to a single survivor — one alive player in FFA, or one team
     * with anyone still alive in team mode — then the whole waiting group respawns
     * together. Requires at least one waiting player so it never fires at match
     * start or with a lone participant in the lobby.
     */
    private void updateLastStanding() {
        var all = gameEntities.getAllPlayers();

        // Players held out awaiting the next skirmish (unlimited lives, so never eliminated).
        List<Player> waiting = all.stream()
                .filter(p -> !p.isActive() && !p.isEliminated())
                .toList();
        if (waiting.isEmpty()) {
            return; // nothing to bring back yet — don't trigger at spawn
        }

        boolean collapsed;
        if (teamCount > 0) {
            // Last team standing == last man standing: only one team has anyone alive.
            long teamsAlive = all.stream()
                    .filter(Player::isActive)
                    .map(Player::getTeam)
                    .distinct()
                    .count();
            collapsed = teamsAlive <= 1;
        } else {
            collapsed = all.stream().filter(Player::isActive).count() <= 1;
        }

        if (collapsed) {
            gameEventManager.broadcastSystemMessage("⚔️ Last one standing — respawning!");
            waiting.forEach(p -> p.setRespawnTime(1L)); // release ASAP; GameManager respawns next tick
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
        if (player.isEliminated()) {
            log.debug("Player {} eliminated, no respawn", player.getId());
            player.setRespawnTime(0);
            return;
        }
        switch (rules.getRespawnMode()) {
            case DELAYED, LIMITED:
                player.setRespawnTime((long) (System.currentTimeMillis() + (rules.getRespawnDelay() * 1000)));
                break;
            case WAVE:
                player.setRespawnTime(waveRespawnTime);
                break;
            case LAST_STANDING:
                // Park indefinitely; updateLastStanding() releases the whole
                // waiting group at once when the arena collapses to one survivor.
                player.setRespawnTime(Long.MAX_VALUE);
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
        if (System.currentTimeMillis() < matchStartTime + (long) (rules.getTimeLimit() * 1000)) {
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

        if (respawnMode != RespawnMode.LIMITED) {
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
        victoryEvent.put("scoringConfig", buildScoringConfig());
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
        victoryEvent.put("scoringConfig", buildScoringConfig());
        broadcaster.accept(victoryEvent);

        gameEventManager.broadcastSystemMessage("🏆 " + message);
    }

    // ===== SCORING HELPERS =====

    /**
     * Scoreboard config shared by the live gameState, round-end, and game-over
     * payloads: the contributing score components (display order) and how to
     * order the board. Derived from {@link Rules} so it always matches scoring.
     */
    private Map<String, Object> buildScoringConfig() {
        Map<String, Object> scoringConfig = new HashMap<>();
        scoringConfig.put("components", rules.getActiveScoreComponents());
        scoringConfig.put("scoreStyle", rules.getScoreStyle().name());
        scoringConfig.put("sortBy",
                rules.getVictoryCondition() == VictoryCondition.ELIMINATION ? "placement" : "score");
        return scoringConfig;
    }

    /**
     * Per-component score breakdown for a player, matching the live gameState
     * player {@code score} map so round-end/game-over screens reuse the same
     * client rendering. KOTH/oddball/HQ-damage stay as doubles.
     */
    private Map<String, Object> buildScoreBreakdown(Player player) {
        Scoring s = player.getScoring();
        Map<String, Object> m = new HashMap<>();
        m.put("kills", s.getKills());
        m.put("deaths", s.getDeaths());
        m.put("captures", s.getFlagCaptures());
        m.put("koth", s.getKingOfTheHillPoints());
        m.put("oddball", s.getOddball());
        m.put("hqDamage", s.getHeadquarterDamage());
        m.put("hqDestroyed", s.getHeadquartersDestroyed());
        m.put("vipKills", s.getVipKills());
        m.put("bonus", s.bonusPoints(rules));
        m.put("total", s.total(rules));
        return m;
    }

    public Map<Integer, Integer> calculateTeamScores() {
        Map<Integer, Integer> teamScores = new HashMap<>();

        // Every scoring mechanism is now credited to the player who earned it, so
        // team (and FFA) totals are simply the sum of each player's Scoring.total().
        for (Player player : gameEntities.getAllPlayers()) {
            teamScores.merge(player.getTeam(), getPlayerScore(player), Integer::sum);
        }

        return teamScores;
    }

    private int getPlayerScore(Player player) {
        return player.getScoring().total(rules);
    }

    private String getScoreTypeName() {
        return switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> "kills";
            case OBJECTIVE -> "objective points";
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
            // Per-team aggregate of every score component, summing the same
            // breakdown shown per player so the team row totals reconcile.
            Map<Integer, Map<String, Object>> teamBreakdown = new HashMap<>();

            for (Player player : gameEntities.getAllPlayers()) {
                int team = player.getTeam();
                teamKills.merge(team, player.getKills(), Integer::sum);
                teamDeaths.merge(team, player.getDeaths(), Integer::sum);
                teamCaptures.merge(team, player.getCaptures(), Integer::sum);
                accumulateBreakdown(teamBreakdown.computeIfAbsent(team, k -> new HashMap<>()),
                        buildScoreBreakdown(player));
            }

            for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                Map<String, Object> teamScore = new HashMap<>();
                teamScore.put("team", entry.getKey());
                teamScore.put("score", entry.getValue());
                teamScore.put("kills", teamKills.getOrDefault(entry.getKey(), 0));
                teamScore.put("deaths", teamDeaths.getOrDefault(entry.getKey(), 0));
                teamScore.put("captures", teamCaptures.getOrDefault(entry.getKey(), 0));
                // Per-component breakdown (object) under a distinct key, since
                // "score" here is the numeric team total. The authoritative team
                // total overrides the summed-doubles total.
                Map<String, Object> breakdown = teamBreakdown.getOrDefault(entry.getKey(), new HashMap<>());
                breakdown.put("total", entry.getValue());
                teamScore.put("scoreBreakdown", breakdown);
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
                // Per-component breakdown (object) under a distinct key, since
                // "score" here is the numeric player total.
                playerScore.put("scoreBreakdown", buildScoreBreakdown(player));
                scores.add(playerScore);
            }
        }

        return scores;
    }

    /**
     * Add the numeric values of {@code src} into {@code dst} key-by-key (used to
     * sum per-player score breakdowns into a team aggregate).
     */
    private void accumulateBreakdown(Map<String, Object> dst, Map<String, Object> src) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (!(e.getValue() instanceof Number n)) {
                continue;
            }
            double prev = dst.get(e.getKey()) instanceof Number p ? p.doubleValue() : 0.0;
            dst.put(e.getKey(), prev + n.doubleValue());
        }
    }

    /**
     * Get game state data for broadcasting to clients.
     */
    public Map<String, Object> getStateData() {
        Map<String, Object> data = new HashMap<>();

        // Game timer data
        data.put("gameState", gameState.name());
        data.put("isCountdown", isCountdown());
        data.put("startCountdownRemaining", Math.max(0.0, startCountdownRemaining));
        if (rules.hasTimeLimit()) {
            data.put("gameTimed", true);
            long endTime = matchStartTime + (long) (rules.getTimeLimit() * 1000);
            data.put("gameTimeRemaining", Math.max(0, (endTime - System.currentTimeMillis()) / 1000));
        } else {
            data.put("gameTimed", false);
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

        // Scoreboard config: which per-player score components actually feed the
        // team total under these rules (in display order), plus how to order the
        // board. Lets the client render exactly the contributing columns.
        data.put("scoringConfig", buildScoringConfig());

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
            log.debug("Player {} initialized with {} lives", player.getId(), rules.getMaxLives());
        }
    }

    // ===== RANDOM WEAPON ROTATION =====

    private WeaponConfig newWeapon = AIWeaponSelector.selectRandomWeapon();
    private UtilityWeapon newUtility = AIWeaponSelector.selectRandomUtilityWeapon();

    /**
     * Schedule the next weapon rotation.
     */
    private void scheduleNextWeaponRotation() {
        nextWeaponRotationTime = (long) (System.currentTimeMillis() + (rules.getRandomWeaponInterval() * 1000));
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

    public void assignPlayerRandomWeapons(Player player) {
        if (rules.hasRandomWeapons()) {
            player.applyWeaponConfig(newWeapon, newUtility);
            player.getCurrentWeapon().setCurrentAmmo(0); // force everyone to reload
            player.setLastUtilityUseTime(System.currentTimeMillis());
        }
    }

    /**
     * Rotate all active players to new random weapons.
     * Excludes healing weapons to maintain combat focus.
     */
    private void rotateAllPlayerWeapons() {
        int rotatedCount = 0;

        for (Player player : gameEntities.getAllPlayers()) {
            if (!player.isActive()) continue;
            newWeapon = AIWeaponSelector.selectRandomWeapon();
            newUtility = AIWeaponSelector.selectRandomUtilityWeapon();
            // Notify player of their new loadout
            String message = String.format("🔀 New Loadout: %s + %s", newWeapon.getType(), newUtility.getDisplayName());
            gameEventManager.broadcastToPlayer(message, player.getId(), GameEvent.EventCategory.INFO);
            assignPlayerRandomWeapons(player);
            rotatedCount++;
        }

        if (rotatedCount > 0) {
            gameEventManager.broadcastSystemMessage("🔄 Weapon Rotation! New loadouts assigned!");
            log.debug("Game {} - Rotated weapons for {} players", gameId, rotatedCount);
        }
    }
}

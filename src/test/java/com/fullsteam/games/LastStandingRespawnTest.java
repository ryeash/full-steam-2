package com.fullsteam.games;

import com.fullsteam.BaseTestClass;
import com.fullsteam.model.RespawnMode;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.dynamics.Body;
import org.dyn4j.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the LAST_STANDING respawn mode: dead players are parked until
 * the arena collapses to a single survivor (FFA) or a single team with anyone
 * alive (team mode), at which point the whole waiting group is released.
 */
class LastStandingRespawnTest extends BaseTestClass {

    private World<Body> world;
    private GameEntities gameEntities;
    private GameEventManager gameEventManager;
    private final Consumer<Map<String, Object>> broadcaster = e -> {
    };

    @Override
    @BeforeEach
    protected void baseSetUp() {
        world = new World<>();
    }

    private RuleSystem newRuleSystem(int teamCount) {
        Rules rules = Rules.builder()
                .respawnMode(RespawnMode.LAST_STANDING)
                .victoryCondition(VictoryCondition.SCORE_LIMIT)
                .scoreLimit(20)
                .scoreStyle(ScoreStyle.TOTAL_KILLS)
                .maxLives(-1)
                .build();
        GameConfig config = GameConfig.builder()
                .rules(rules)
                .teamCount(teamCount)
                .playerMaxHealth(100.0)
                .enableAIFilling(false)
                .build();
        gameEntities = new GameEntities(config, world);
        gameEventManager = new GameEventManager(gameEntities, (session, message) -> {
        });
        return new RuleSystem("last-standing-test", rules, gameEntities, gameEventManager, broadcaster, teamCount);
    }

    private Player spawnPlayer(int id, int team) {
        Player player = new Player(id, "TestPlayer" + id, 0, 0, team, 100.0);
        player.setActive(true);
        gameEntities.add(player);
        return player;
    }

    /** Kill a player the way GameManager does: die() then park via the rule system. */
    private void kill(RuleSystem ruleSystem, Player player) {
        player.die();
        ruleSystem.setRespawnTime(player);
    }

    @Test
    @DisplayName("FFA: dead players are parked and released only when one survivor remains")
    void testFfaCollapseReleasesWaitingPlayers() {
        RuleSystem ruleSystem = newRuleSystem(0);
        Player p1 = spawnPlayer(1, 0);
        Player p2 = spawnPlayer(2, 0);
        Player p3 = spawnPlayer(3, 0);

        // Kill one of three — two still alive, so nobody should be released.
        kill(ruleSystem, p1);
        assertFalse(ruleSystem.shouldPlayerRespawn(p1), "Parked player should not respawn while 2 are alive");
        ruleSystem.update(0.016);
        assertFalse(ruleSystem.shouldPlayerRespawn(p1), "Still parked while more than one player is alive");

        // Kill a second — now only p3 stands, so the collapse should release the group.
        kill(ruleSystem, p2);
        ruleSystem.update(0.016);
        assertTrue(ruleSystem.shouldPlayerRespawn(p1), "Waiting player should be released once one survivor remains");
        assertTrue(ruleSystem.shouldPlayerRespawn(p2), "Waiting player should be released once one survivor remains");
    }

    @Test
    @DisplayName("Does not trigger at match start when nobody is waiting")
    void testNoReleaseWithoutWaitingPlayers() {
        RuleSystem ruleSystem = newRuleSystem(0);
        Player solo = spawnPlayer(1, 0);
        // No one is dead — update must be a no-op (no exception, no release).
        ruleSystem.update(0.016);
        assertFalse(ruleSystem.shouldPlayerRespawn(solo), "Active player never respawns");
    }

    @Test
    @DisplayName("Team mode: release only when a single team has anyone alive")
    void testTeamCollapseReleasesWaitingPlayers() {
        RuleSystem ruleSystem = newRuleSystem(2);
        Player t1a = spawnPlayer(1, 1);
        Player t1b = spawnPlayer(2, 1);
        Player t2a = spawnPlayer(3, 2);
        Player t2b = spawnPlayer(4, 2);

        // Kill one from each team — both teams still have a survivor, so no release.
        kill(ruleSystem, t1a);
        kill(ruleSystem, t2a);
        ruleSystem.update(0.016);
        assertFalse(ruleSystem.shouldPlayerRespawn(t1a), "No release while both teams have someone alive");
        assertFalse(ruleSystem.shouldPlayerRespawn(t2a), "No release while both teams have someone alive");

        // Wipe the rest of team 2 — only team 1 stands, so the group is released.
        kill(ruleSystem, t2b);
        ruleSystem.update(0.016);
        assertTrue(ruleSystem.shouldPlayerRespawn(t1a), "Waiting players released when one team stands");
        assertTrue(ruleSystem.shouldPlayerRespawn(t2a), "Waiting players released when one team stands");
        assertTrue(ruleSystem.shouldPlayerRespawn(t2b), "Waiting players released when one team stands");
        assertFalse(ruleSystem.shouldPlayerRespawn(t1b), "Surviving player is not a respawn candidate");
    }
}

package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AI behavior for VIP mode. Non-VIP players either protect their team's VIP
 * or hunt down enemy VIPs, depending on tactical context.
 * VIP players themselves prioritise survival.
 */
public class VipBehavior implements AIBehavior {

    private enum VipRole {
        VIP_SURVIVE,   // I am the VIP — stay alive
        PROTECTOR,     // Teammate is the VIP — guard them
        HUNTER         // Go kill the enemy VIP
    }

    private VipRole currentRole = VipRole.HUNTER;
    private double roleEvalTimer = 0;
    private static final double ROLE_EVAL_INTERVAL = 2.0;

    private int targetVipId = -1;

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        roleEvalTimer += deltaTime;
        if (roleEvalTimer >= ROLE_EVAL_INTERVAL) {
            evaluateRole(aiPlayer, gameEntities);
            roleEvalTimer = 0;
        }

        switch (currentRole) {
            case VIP_SURVIVE -> executeVipSurviveBehavior(aiPlayer, gameEntities, input, deltaTime);
            case PROTECTOR -> executeProtectorBehavior(aiPlayer, gameEntities, input, deltaTime);
            case HUNTER -> executeHunterBehavior(aiPlayer, gameEntities, input, deltaTime);
        }

        handleReload(aiPlayer, input);

        return input;
    }

    // ------------------------------------------------------------------
    // Role evaluation
    // ------------------------------------------------------------------

    private void evaluateRole(AIPlayer aiPlayer, GameEntities gameEntities) {
        int myTeam = aiPlayer.getTeam();

        // Am I the VIP?
        if (gameEntities.isPlayerVip(aiPlayer.getId())) {
            currentRole = VipRole.VIP_SURVIVE;
            return;
        }

        // Does our team have a living VIP that needs protection?
        Integer ourVipId = gameEntities.getTeamVip(myTeam);
        Player ourVip = ourVipId != null ? gameEntities.getPlayer(ourVipId) : null;
        boolean ourVipAlive = ourVip != null && ourVip.isActive();

        // Find the nearest enemy VIP
        Player nearestEnemyVip = findNearestEnemyVip(aiPlayer, gameEntities);

        if (!ourVipAlive) {
            // Our VIP is dead / missing — go hunt instead of guarding nothing
            currentRole = VipRole.HUNTER;
            targetVipId = nearestEnemyVip != null ? nearestEnemyVip.getId() : -1;
            return;
        }

        double distToOurVip = aiPlayer.getPosition().distance(ourVip.getPosition());
        double aggressiveness = aiPlayer.getPersonality().getAggressiveness();

        // Aggressive personalities or those far from VIP lean toward hunting
        if (aggressiveness > 0.7 && nearestEnemyVip != null) {
            double distToEnemyVip = aiPlayer.getPosition().distance(nearestEnemyVip.getPosition());
            if (distToEnemyVip < 500 || distToOurVip > 400) {
                currentRole = VipRole.HUNTER;
                targetVipId = nearestEnemyVip.getId();
                return;
            }
        }

        // Default: protect our VIP
        currentRole = VipRole.PROTECTOR;
        targetVipId = -1;
    }

    // ------------------------------------------------------------------
    // VIP_SURVIVE — the AI is the VIP and must stay alive
    // ------------------------------------------------------------------

    private void executeVipSurviveBehavior(AIPlayer aiPlayer, GameEntities gameEntities,
                                           PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);

        if (nearestEnemy != null) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            double dist = myPos.distance(enemyPos);

            // Evade: move away from the closest threat
            Vector2 away = myPos.copy().subtract(enemyPos);
            away.normalize();
            away = HazardAvoidance.calculateSafeMovement(myPos, away, gameEntities, 120.0);
            input.setMoveX(away.x);
            input.setMoveY(away.y);

            // Shoot back if within range
            input.setWorldX(enemyPos.x);
            input.setWorldY(enemyPos.y);
            if (dist < aiPlayer.getCurrentWeapon().getRange() * 0.85) {
                input.setLeft(true);
            }
        } else {
            // Safe — drift toward the map centre where teammates are more likely
            moveTowardCenter(aiPlayer, input, gameEntities);
        }
    }

    // ------------------------------------------------------------------
    // PROTECTOR — guard our team's VIP
    // ------------------------------------------------------------------

    private void executeProtectorBehavior(AIPlayer aiPlayer, GameEntities gameEntities,
                                          PlayerInput input, double deltaTime) {
        Integer ourVipId = gameEntities.getTeamVip(aiPlayer.getTeam());
        Player ourVip = ourVipId != null ? gameEntities.getPlayer(ourVipId) : null;
        if (ourVip == null || !ourVip.isActive()) {
            currentRole = VipRole.HUNTER;
            evaluateRole(aiPlayer, gameEntities);
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 vipPos = ourVip.getPosition();
        double distToVip = myPos.distance(vipPos);

        Player nearestEnemy = findNearestEnemy(aiPlayer, gameEntities);

        if (nearestEnemy != null && nearestEnemy.isActive()) {
            Vector2 enemyPos = nearestEnemy.getPosition();
            double distToEnemy = myPos.distance(enemyPos);

            // Position between VIP and the threat
            Vector2 guardPos = vipPos.copy().add(
                    enemyPos.copy().subtract(vipPos).getNormalized().multiply(70.0));
            Vector2 toGuard = guardPos.copy().subtract(myPos);
            if (toGuard.getMagnitude() > 10) {
                toGuard.normalize();
                toGuard = HazardAvoidance.calculateSafeMovement(myPos, toGuard, gameEntities, 100.0);
                input.setMoveX(toGuard.x);
                input.setMoveY(toGuard.y);
            }

            input.setWorldX(enemyPos.x);
            input.setWorldY(enemyPos.y);
            if (distToEnemy < aiPlayer.getCurrentWeapon().getRange() * 0.9) {
                input.setLeft(true);
            }
        } else {
            // No threats — escort the VIP at a comfortable distance
            if (distToVip > 150) {
                Vector2 toVip = vipPos.copy().subtract(myPos).getNormalized();
                toVip = HazardAvoidance.calculateSafeMovement(myPos, toVip, gameEntities, 100.0);
                input.setMoveX(toVip.x);
                input.setMoveY(toVip.y);
            } else if (distToVip < 50) {
                Vector2 awayFromVip = myPos.copy().subtract(vipPos).getNormalized();
                input.setMoveX(awayFromVip.x * 0.4);
                input.setMoveY(awayFromVip.y * 0.4);
            }
            input.setWorldX(vipPos.x);
            input.setWorldY(vipPos.y);
        }
    }

    // ------------------------------------------------------------------
    // HUNTER — seek and destroy enemy VIPs
    // ------------------------------------------------------------------

    private void executeHunterBehavior(AIPlayer aiPlayer, GameEntities gameEntities,
                                       PlayerInput input, double deltaTime) {
        // Re-resolve target each frame in case VIP changed
        Player targetVip = resolveTargetVip(aiPlayer, gameEntities);

        if (targetVip == null) {
            // No enemy VIP visible — fall back to wandering toward centre
            moveTowardCenter(aiPlayer, input, gameEntities);
            return;
        }

        Vector2 myPos = aiPlayer.getPosition();
        Vector2 targetPos = targetVip.getPosition();
        double dist = myPos.distance(targetPos);

        // Move toward the enemy VIP
        Vector2 toTarget = targetPos.copy().subtract(myPos);
        toTarget.normalize();
        toTarget = HazardAvoidance.calculateSafeMovement(myPos, toTarget, gameEntities, 100.0);
        input.setMoveX(toTarget.x);
        input.setMoveY(toTarget.y);

        // Aim and shoot
        input.setWorldX(targetPos.x);
        input.setWorldY(targetPos.y);
        if (dist < aiPlayer.getCurrentWeapon().getRange() * 0.9) {
            input.setLeft(true);
        }
    }

    // ------------------------------------------------------------------
    // Priority / lifecycle
    // ------------------------------------------------------------------

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        if (!gameEntities.getConfig().getRules().hasVip()) {
            return 0;
        }

        // If I'm the VIP, survival is extremely important
        if (gameEntities.isPlayerVip(aiPlayer.getId())) {
            return 95;
        }

        // If an enemy VIP is nearby, high priority to hunt
        Player enemyVip = findNearestEnemyVip(aiPlayer, gameEntities);
        if (enemyVip != null) {
            double dist = aiPlayer.getPosition().distance(enemyVip.getPosition());
            if (dist < 400) return 85;
            return 70;
        }

        // Protect our VIP
        Integer ourVipId = gameEntities.getTeamVip(aiPlayer.getTeam());
        Player ourVip = ourVipId != null ? gameEntities.getPlayer(ourVipId) : null;
        if (ourVip != null && ourVip.isActive()) {
            return 65;
        }

        return 0;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        return gameEntities.getConfig().getRules().hasVip();
    }

    @Override
    public String getName() {
        return "VIP";
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Player findNearestEnemyVip(AIPlayer aiPlayer, GameEntities gameEntities) {
        int myTeam = aiPlayer.getTeam();
        int teamCount = gameEntities.getConfig().getTeamCount();
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int t = 1; t <= teamCount; t++) {
            if (t == myTeam) continue;
            Integer vipId = gameEntities.getTeamVip(t);
            if (vipId == null) continue;
            Player vip = gameEntities.getPlayer(vipId);
            if (vip == null || !vip.isActive()) continue;

            double d = aiPlayer.getPosition().distance(vip.getPosition());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = vip;
            }
        }
        return nearest;
    }

    private Player resolveTargetVip(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Prefer previously tracked target if still valid
        if (targetVipId != -1) {
            Player p = gameEntities.getPlayer(targetVipId);
            if (p != null && p.isActive() && p.getTeam() != aiPlayer.getTeam()
                    && gameEntities.isPlayerVip(targetVipId)) {
                return p;
            }
        }
        // Find a new one
        Player vip = findNearestEnemyVip(aiPlayer, gameEntities);
        targetVipId = vip != null ? vip.getId() : -1;
        return vip;
    }

    private Player findNearestEnemy(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player p : gameEntities.getAllPlayers()) {
            if (p.getId() == aiPlayer.getId() || !p.isActive() || p.getTeam() == myTeam) continue;
            double d = myPos.distance(p.getPosition());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private void moveTowardCenter(AIPlayer aiPlayer, PlayerInput input, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 toCenter = new Vector2(-myPos.x, -myPos.y);
        double dist = toCenter.getMagnitude();
        if (dist > 50) {
            toCenter.normalize();
            toCenter = HazardAvoidance.calculateSafeMovement(myPos, toCenter, gameEntities, 100.0);
            input.setMoveX(toCenter.x * 0.5);
            input.setMoveY(toCenter.y * 0.5);
        } else {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            input.setMoveX(Math.cos(angle) * 0.3);
            input.setMoveY(Math.sin(angle) * 0.3);
        }
    }

    private void handleReload(AIPlayer aiPlayer, PlayerInput input) {
        int ammo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int mag = aiPlayer.getCurrentWeapon().getMagazineSize();
        if (ammo == 0 || (ammo < mag * 0.3 && currentRole != VipRole.HUNTER)) {
            input.setReload(true);
            input.setLeft(false);
        }
    }
}

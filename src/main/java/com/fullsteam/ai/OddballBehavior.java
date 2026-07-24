package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Oddball;
import org.dyn4j.geometry.Vector2;

/**
 * Behavior for NPC Oddball mode.
 *
 * <p>AI players score by dealing damage to the invincible OddballNPC entities that
 * bounce around the map. This behavior:
 * <ul>
 *   <li><b>SEEK</b>  – drift toward the map center when no NPC is in view.</li>
 *   <li><b>HUNT</b>  – close in on the target NPC, stay in the optimal firing band,
 *       and strafe slowly to avoid being a sitting duck.</li>
 *   <li><b>EVADE</b> – side-step perpendicular to a charging RAMPAGE NPC while
 *       continuing to aim and fire at it.</li>
 * </ul>
 *
 * <p>Target selection prefers the RAMPAGE ball (2× point multiplier) over distance,
 * so the AI will sometimes close extra ground to hit the bigger prize.
 */
public class OddballBehavior implements AIBehavior {

    private enum Role {HUNT, EVADE, SEEK}

    // Engagement envelope — keep the NPC in this distance window for reliable hits
    private static final double OPTIMAL_RANGE_MIN = 120.0;
    private static final double OPTIMAL_RANGE_MAX = 350.0;

    // Charge-detection: NPC velocity must be pointing > this fraction toward the AI
    private static final double EVADE_DOT_THRESHOLD = 0.85;
    // Only bother evading when the NPC is inside this radius
    private static final double EVADE_TRIGGER_DIST = 220.0;

    // Re-pick target NPC at this cadence (seconds)
    private static final double EVAL_INTERVAL = 1.5;

    private int targetNpcId = -1;
    private Role role = Role.SEEK;
    private double evalTimer = 0.0;

    // Stable strafe side so the AI doesn't flip-flop every tick
    private double strafeSign = 1.0;

    // ── AIBehavior ──────────────────────────────────────────────────────────────

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        evalTimer += deltaTime;
        if (evalTimer >= EVAL_INTERVAL || gameEntities.getOddballNpc(targetNpcId) == null) {
            pickTarget(aiPlayer, gameEntities);
            evalTimer = 0;
        }

        Oddball target = gameEntities.getOddballNpc(targetNpcId);
        if (target == null || !target.isActive()) {
            role = Role.SEEK;
            target = null;
        } else {
            updateRole(aiPlayer, target);
        }

        switch (role) {
            case HUNT -> executeHunt(aiPlayer, target, gameEntities, input);
            case EVADE -> executeEvade(aiPlayer, target, gameEntities, input);
            case SEEK -> executeSeek(aiPlayer, gameEntities, input);
        }

        // Reload when empty or below 30% in a safe moment (SEEK = no immediate threat)
        smartReload(aiPlayer, input, role == Role.SEEK);
        return input;
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        // High priority when NPC oddballs are present; zero otherwise so other behaviors
        // (combat, KOTH, etc.) take over in non-NPC-oddball game modes.
        return gameEntities.getAllOddballNpcs().stream().anyMatch(Oddball::isActive) ? 90 : 0;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        return gameEntities.getAllOddballNpcs().stream().anyMatch(Oddball::isActive);
    }

    @Override
    public String getName() {
        return "OddballNPC";
    }

    // ── Target selection ────────────────────────────────────────────────────────

    /**
     * Pick the highest-value reachable NPC. RAMPAGE gives 2× points so it scores
     * higher than a closer SEEKER until the SEEKER is roughly 2× nearer.
     * score = (pointsMultiplier × 1000) / (distance + 1)
     */
    private void pickTarget(AIPlayer aiPlayer, GameEntities gameEntities) {
        Oddball best = null;
        double bestScore = -1;
        Vector2 myPos = aiPlayer.getPosition();

        for (Oddball npc : gameEntities.getAllOddballNpcs()) {
            if (!npc.isActive()) {
                continue;
            }
            double dist = myPos.distance(npc.getPosition());
            double score = npc.getPointsMultiplier() * 1000.0 / (dist + 1.0);
            if (score > bestScore) {
                bestScore = score;
                best = npc;
            }
        }
        targetNpcId = best != null ? best.getId() : -1;
    }

    // ── Role transitions ────────────────────────────────────────────────────────

    /**
     * Switch to EVADE if the target NPC is close and its velocity vector is aimed
     * directly at the AI (dot product > threshold). Otherwise HUNT.
     * Strafe side is fixed at the moment we first enter EVADE so we dodge cleanly.
     */
    private void updateRole(AIPlayer aiPlayer, Oddball npc) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 npcPos = npc.getPosition();
        double dist = myPos.distance(npcPos);

        if (dist < EVADE_TRIGGER_DIST) {
            Vector2 npcVel = npc.getBody().getLinearVelocity();
            if (npcVel.getMagnitude() > 10.0) {
                Vector2 npcDir = npcVel.getNormalized();
                Vector2 toAI = myPos.copy().subtract(npcPos).getNormalized();
                double dot = npcDir.dot(toAI);
                if (dot > EVADE_DOT_THRESHOLD) {
                    if (role != Role.EVADE) {
                        // Lock in a strafe direction for this evasion
                        strafeSign = Math.random() < 0.5 ? 1.0 : -1.0;
                    }
                    role = Role.EVADE;
                    return;
                }
            }
        }
        role = Role.HUNT;
    }

    // ── Role execution ──────────────────────────────────────────────────────────

    /**
     * HUNT: maintain the optimal firing band around the NPC and shoot continuously.
     * Inside the band the AI slow-strafes sideways to avoid being a stationary target.
     */
    private void executeHunt(AIPlayer aiPlayer, Oddball npc,
                             GameEntities gameEntities, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 npcPos = npc.getPosition();
        double dist = myPos.distance(npcPos);
        Vector2 toNpc = npcPos.copy().subtract(myPos).getNormalized();

        if (dist < OPTIMAL_RANGE_MIN) {
            // Too close — back away
            input.setMoveX(-toNpc.x * 0.8);
            input.setMoveY(-toNpc.y * 0.8);
        } else if (dist > OPTIMAL_RANGE_MAX) {
            // Too far — advance with obstacle avoidance
            Vector2 move = HazardAvoidance.calculateSafeMovement(myPos, toNpc, gameEntities, 80.0);
            input.setMoveX(move.x);
            input.setMoveY(move.y);
        } else {
            // In band — slow sideways strafe to avoid the NPC's return fire
            Vector2 strafe = new Vector2(-toNpc.y * strafeSign, toNpc.x * strafeSign);
            input.setMoveX(strafe.x * 0.45);
            input.setMoveY(strafe.y * 0.45);
        }

        aimAndShoot(aiPlayer, npcPos, dist, input);
    }

    /**
     * EVADE: strafe perpendicular to the NPC's charge vector while still firing.
     * The AI never runs away — it sidesteps and keeps dealing damage.
     */
    private void executeEvade(AIPlayer aiPlayer, Oddball npc,
                              GameEntities gameEntities, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 npcPos = npc.getPosition();
        double dist = myPos.distance(npcPos);
        Vector2 toNpc = npcPos.copy().subtract(myPos).getNormalized();

        // Perpendicular direction (left or right of the NPC's approach axis)
        Vector2 strafe = new Vector2(-toNpc.y * strafeSign, toNpc.x * strafeSign);
        Vector2 move = HazardAvoidance.calculateSafeMovement(myPos, strafe, gameEntities, 80.0);
        input.setMoveX(move.x);
        input.setMoveY(move.y);

        aimAndShoot(aiPlayer, npcPos, dist, input);
    }

    /**
     * SEEK: no live NPC target — drift toward the map center at half speed.
     */
    private void executeSeek(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 toward = new Vector2(0, 0).subtract(myPos);
        if (toward.getMagnitude() > 50) {
            toward.normalize();
            Vector2 move = HazardAvoidance.calculateSafeMovement(myPos, toward, gameEntities, 80.0);
            input.setMoveX(move.x * 0.6);
            input.setMoveY(move.y * 0.6);
        }
        input.setWorldX(0);
        input.setWorldY(0);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void aimAndShoot(AIPlayer aiPlayer, Vector2 targetPos, double dist, PlayerInput input) {
        input.setWorldX(targetPos.x);
        input.setWorldY(targetPos.y);
        double weaponRange = aiPlayer.getCurrentWeapon().getRange();
        if (dist < weaponRange * 0.95) {
            input.setLeft(true);
        }
    }
}

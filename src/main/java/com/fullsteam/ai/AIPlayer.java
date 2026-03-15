package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Player;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.geometry.Vector2;

/**
 * AIPlayer extends Player with AI-specific properties and behavior management.
 * It maintains the same interface as Player but adds AI personality and behavior state.
 */
@Getter
@Setter
public class AIPlayer extends Player {
    private AIPersonality personality;
    private AIBehavior currentBehavior;
    private AIMemory memory;
    private double lastDecisionTime = 0;
    private double decisionCooldown = 0.05; // Make decisions every 50ms for truly continuous movement
    private int targetPlayerId = -1;
    private int targetLocationId = -1;
    private boolean isHuman = false; // Always false for AI players

    // Movement smoothing state
    private Vector2 lastMoveDirection = new Vector2(0, 0);
    private Vector2 targetMoveDirection = new Vector2(0, 0);
    private double movementSmoothingFactor = 0.7; // How much to blend between old and new movement

    // Stuck detection state
    private Vector2 lastRecordedPosition = null;
    private double stuckCheckTimer = 0;
    private double stuckDuration = 0;
    private static final double STUCK_CHECK_INTERVAL = 0.5; // Check every 500ms
    private static final double STUCK_MOVEMENT_THRESHOLD = 5.0; // Less than 5 units moved = stuck
    private static final double STUCK_DURATION_THRESHOLD = 1.5; // Considered stuck after 1.5s of no progress
    private boolean isStuck = false;

    public AIPlayer(int id, String playerName, double x, double y, AIPersonality personality, int team, double maxHealth) {
        super(id, playerName, x, y, team, maxHealth);
        this.personality = personality;
        this.memory = new AIMemory();
        this.currentBehavior = new IdleBehavior();
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);

        // Update AI decision making
        lastDecisionTime += deltaTime;
        if (lastDecisionTime >= decisionCooldown && isActive()) {
            // AI decision making will be handled by AIPlayerManager
            lastDecisionTime = 0;
        }

        // Update stuck detection
        updateStuckDetection(deltaTime);

        // Update memory with current game state
        memory.update(deltaTime);
    }

    /**
     * Detect when the AI is stuck against a wall or obstacle by tracking
     * whether its actual position changes despite movement inputs being generated.
     */
    private void updateStuckDetection(double deltaTime) {
        if (!isActive()) {
            resetStuckState();
            return;
        }

        stuckCheckTimer += deltaTime;
        if (stuckCheckTimer < STUCK_CHECK_INTERVAL) {
            return;
        }
        stuckCheckTimer = 0;

        Vector2 currentPos = getPosition();

        if (lastRecordedPosition == null) {
            lastRecordedPosition = currentPos.copy();
            return;
        }

        double distanceMoved = currentPos.distance(lastRecordedPosition);
        boolean hasMovementInput = lastMoveDirection.getMagnitude() > 0.1;

        if (distanceMoved < STUCK_MOVEMENT_THRESHOLD && hasMovementInput) {
            stuckDuration += STUCK_CHECK_INTERVAL;
            if (stuckDuration >= STUCK_DURATION_THRESHOLD) {
                isStuck = true;
            }
        } else {
            stuckDuration = 0;
            isStuck = false;
        }

        lastRecordedPosition = currentPos.copy();
    }

    /**
     * Reset stuck detection state (e.g. after respawn or unstick maneuver).
     */
    public void resetStuckState() {
        stuckDuration = 0;
        isStuck = false;
        lastRecordedPosition = null;
        lastMoveDirection.set(0, 0);
    }

    public void setCurrentBehavior(AIBehavior behavior) {
        if (this.currentBehavior != null) {
            this.currentBehavior.onExit(this);
        }
        this.currentBehavior = behavior;
        if (behavior != null) {
            behavior.onEnter(this);
        }
    }

    public boolean needsNewDecision() {
        return lastDecisionTime >= decisionCooldown && isActive();
    }

    public void resetDecisionTimer() {
        lastDecisionTime = 0;
    }

    /**
     * Apply movement smoothing to reduce jerky AI movement.
     * When stuck, bypasses smoothing to allow immediate direction changes.
     */
    public void smoothMovement(PlayerInput input) {
        // Get the new target movement direction
        targetMoveDirection.set(input.getMoveX(), input.getMoveY());

        if (isStuck) {
            // When stuck, skip smoothing entirely so the unstick direction
            // takes effect immediately instead of being blended with the
            // wall-directed lastMoveDirection that got us stuck.
            lastMoveDirection.set(input.getMoveX(), input.getMoveY());
            return;
        }

        // Smooth transition between last and target movement
        double smoothing = movementSmoothingFactor;
        double newX = lastMoveDirection.x * (1.0 - smoothing) + targetMoveDirection.x * smoothing;
        double newY = lastMoveDirection.y * (1.0 - smoothing) + targetMoveDirection.y * smoothing;

        // Update the input with smoothed movement
        input.setMoveX(newX);
        input.setMoveY(newY);

        // Store current movement for next frame
        lastMoveDirection.set(newX, newY);
    }

    /**
     * Get current movement direction for continuous motion.
     */
    public Vector2 getCurrentMovementDirection() {
        return lastMoveDirection.copy();
    }

    /**
     * Set movement smoothing factor (0.0 = no smoothing, 1.0 = maximum smoothing).
     */
    public void setMovementSmoothingFactor(double factor) {
        this.movementSmoothingFactor = Math.max(0.0, Math.min(1.0, factor));
    }

    public double getTimeSinceLastDecision() {
        return lastDecisionTime;
    }

    /**
     * Evaluate if AI should switch weapons based on tactical situation.
     * Returns true if weapon switch would be beneficial.
     * 
     * @param targetDistance Distance to current target
     * @return true if should switch weapons
     */
    public boolean shouldSwitchWeapon(double targetDistance) {
        // Check if out of ammo
        if (getCurrentWeapon().getCurrentAmmo() == 0 && isReloading()) {
            return true; // Switch to avoid reload time
        }

        // Check if weapon is ineffective at current range
        double weaponRange = getCurrentWeapon().getRange();
        
        // Too far for current weapon
        if (targetDistance > weaponRange * 0.9) {
            return true;
        }

        // Very low ammo and enemy is close
        int currentAmmo = getCurrentWeapon().getCurrentAmmo();
        int magazineSize = getCurrentWeapon().getMagazineSize();
        if (currentAmmo < magazineSize * 0.15 && targetDistance < 150) {
            return true; // Switch instead of reload in close combat
        }

        return false;
    }

    /**
     * Get preferred weapon range based on personality.
     * Used to select appropriate weapon for situation.
     */
    public double getPreferredCombatRange() {
        return personality.getPreferredCombatRange();
    }
}

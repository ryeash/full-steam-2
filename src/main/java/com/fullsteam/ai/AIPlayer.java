package com.fullsteam.ai;

import com.fullsteam.physics.Player;
import lombok.Getter;
import lombok.Setter;

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
    private double decisionCooldown = 0.3; // Make decisions every 300ms for smoother behavior
    private int targetPlayerId = -1;
    private int targetLocationId = -1;
    private boolean isHuman = false; // Always false for AI players

    public AIPlayer(int id, String playerName, double x, double y, AIPersonality personality) {
        this(id, playerName, x, y, personality, 0); // Default to FFA team
    }
    
    public AIPlayer(int id, String playerName, double x, double y, AIPersonality personality, int team) {
        super(id, playerName, x, y, team);
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
        
        // Update memory with current game state
        memory.update(deltaTime);
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

    public double getTimeSinceLastDecision() {
        return lastDecisionTime;
    }
}

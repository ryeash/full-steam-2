package com.fullsteam.model;

import lombok.Getter;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an active environmental event instance with its state and timing.
 */
@Getter
public class ActiveGameEvent {
    private final EnvironmentalEvent eventType;
    private final long warningStartTime;
    private final long eventStartTime;
    private final long eventEndTime;
    private final List<Vector2> targetLocations;
    private final List<Integer> warningZoneIds; // Track warning zone field effects
    private boolean warningPhase;
    private boolean active;
    private boolean completed;

    public ActiveGameEvent(EnvironmentalEvent eventType, double warningDuration, List<Vector2> targetLocations) {
        this.eventType = eventType;
        this.targetLocations = new ArrayList<>(targetLocations);
        this.warningZoneIds = new ArrayList<>();
        
        long now = System.currentTimeMillis();
        this.warningStartTime = now;
        this.eventStartTime = (long) (now + (warningDuration * 1000));
        this.eventEndTime = (long) (eventStartTime + (eventType.getBaseDuration() * 1000));
        
        this.warningPhase = true;
        this.active = false;
        this.completed = false;
    }

    public void update() {
        long now = System.currentTimeMillis();
        
        if (completed) {
            return;
        }
        
        if (warningPhase && now >= eventStartTime) {
            warningPhase = false;
            active = true;
        }
        
        if (active && now >= eventEndTime) {
            active = false;
            completed = true;
        }
    }

    public boolean isInWarningPhase() {
        return warningPhase && !completed;
    }

    public boolean isActive() {
        return active && !completed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public double getWarningTimeRemaining() {
        if (!warningPhase) {
            return 0.0;
        }
        return Math.max(0.0, (eventStartTime - System.currentTimeMillis()) / 1000.0);
    }

    public double getEventTimeRemaining() {
        if (!active) {
            return 0.0;
        }
        return Math.max(0.0, (eventEndTime - System.currentTimeMillis()) / 1000.0);
    }

    public void addWarningZoneId(int fieldEffectId) {
        warningZoneIds.add(fieldEffectId);
    }
}


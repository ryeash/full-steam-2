package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for beam weapons that provide instant hit-scan behavior
 */
@Getter
@Setter
public abstract class Beam extends GameEntity {
    protected final Vector2 startPoint;
    protected final Vector2 endPoint;
    protected Vector2 effectiveEndPoint; // Actual end point after obstacle collision
    protected final Vector2 direction;
    protected final double range;
    protected final double damage;
    protected final int ownerId;
    protected final int ownerTeam;
    protected final DamageApplicationType damageApplicationType;
    protected final double damageInterval;
    protected final double beamDuration;
    
    // Track affected players for DOT and burst beams
    protected final Set<Integer> affectedPlayers = new HashSet<>();
    protected final Map<Integer, Long> lastDamageTime = new HashMap<>();
    
    private double timeRemaining;
    private double timeSinceLastDamage = 0.0;

    public Beam(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                int ownerId, int ownerTeam, DamageApplicationType damageApplicationType, 
                double damageInterval, double beamDuration) {
        super(id, createBeamBody(startPoint, direction, range), Double.POSITIVE_INFINITY); // Beams don't have health
        this.startPoint = startPoint.copy();
        this.direction = direction.copy();
        this.direction.normalize();
        this.range = range;
        this.damage = damage;
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.damageApplicationType = damageApplicationType;
        this.damageInterval = damageInterval;
        this.beamDuration = beamDuration;
        this.timeRemaining = beamDuration;
        
        // Calculate end point
        Vector2 offset = this.direction.copy();
        offset = offset.multiply(range);
        this.endPoint = startPoint.copy();
        this.endPoint.add(offset);
        
        // Initially, effective end point is the same as end point
        // This will be updated by GameManager after obstacle collision detection
        this.effectiveEndPoint = this.endPoint.copy();
    }

    private static Body createBeamBody(Vector2 startPoint, Vector2 direction, double range) {
        Body body = new Body();
        // Create a thin rectangle representing the beam line for collision detection
        Rectangle rectangle = new Rectangle(range, 2.0); // Very thin beam
        BodyFixture bodyFixture = body.addFixture(rectangle);
        bodyFixture.setSensor(true);
        body.setMass(MassType.INFINITE); // Stationary
        
        // Position and orient the beam
        Vector2 center = startPoint.copy();
        Vector2 offset = direction.copy();
        offset = offset.multiply(range / 2.0);
        center.add(offset);
        
        body.getTransform().setTranslation(center.x, center.y);
        body.getTransform().setRotation(Math.atan2(direction.y, direction.x));
        
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Update beam duration
        timeRemaining -= deltaTime;
        if (timeRemaining <= 0) {
            active = false;
            return;
        }

        // Handle damage application based on type
        switch (damageApplicationType) {
            case INSTANT:
                // Instant damage is applied once during creation
                break;
            case DAMAGE_OVER_TIME:
                applyDamageOverTime(deltaTime);
                break;
            case BURST:
                applyBurstDamage(deltaTime);
                break;
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Apply damage over time to all players in beam path
     */
    private void applyDamageOverTime(double deltaTime) {
        List<Player> playersInPath = getPlayersInBeamPath();
        
        for (Player player : playersInPath) {
            if (canAffectPlayer(player)) {
                processContinuousDamage(player, deltaTime);
            }
        }
    }

    /**
     * Apply burst damage at intervals
     */
    private void applyBurstDamage(double deltaTime) {
        timeSinceLastDamage += deltaTime;
        
        if (timeSinceLastDamage >= damageInterval) {
            List<Player> playersInPath = getPlayersInBeamPath();
            
            for (Player player : playersInPath) {
                if (canAffectPlayer(player)) {
                    processBurstDamage(player);
                }
            }
            
            timeSinceLastDamage = 0.0;
        }
    }

    /**
     * Get all players that intersect with the beam path using line-based collision detection
     * This is populated by the GameManager during beam processing
     */
    public List<Player> getPlayersInBeamPath() {
        // This method is called by GameManager with collision detection results
        // The actual collision detection is done in GameManager.getPlayersInBeamPath()
        return new ArrayList<>(); // Placeholder - actual implementation in GameManager
    }

    /**
     * Check if this beam can affect a specific player
     */
    public boolean canAffectPlayer(Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }

        // Can't affect the owner (unless it's a heal beam)
        if (player.getId() == ownerId && !isHealingBeam()) {
            return false;
        }

        // Team-based logic
        if (isHealingBeam()) {
            // Healing beams only affect allies (or self in FFA)
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return player.getId() == ownerId; // Only heal self in FFA
            }
            return ownerTeam == player.getTeam(); // Heal teammates
        } else {
            // Damage beams affect enemies
            if (ownerTeam == 0 || player.getTeam() == 0) {
                return player.getId() != ownerId; // Damage anyone except self in FFA
            }
            return ownerTeam != player.getTeam(); // Damage enemies
        }
    }

    /**
     * Process initial hit when beam is first created (for instant damage)
     */
    public abstract void processInitialHit(Player player);

    /**
     * Process continuous damage over time
     */
    public abstract void processContinuousDamage(Player player, double deltaTime);

    /**
     * Process burst damage at intervals
     */
    public abstract void processBurstDamage(Player player);

    /**
     * Check if this is a healing beam
     */
    public abstract boolean isHealingBeam();

    /**
     * Check if the beam has expired
     */
    public boolean isExpired() {
        return !active || timeRemaining <= 0;
    }

    /**
     * Get the remaining duration as a percentage
     */
    public double getDurationPercent() {
        return beamDuration > 0 ? Math.max(0, timeRemaining / beamDuration) : 0;
    }

    /**
     * Check if this beam can pierce through targets
     */
    public abstract boolean canPierceTargets();
}

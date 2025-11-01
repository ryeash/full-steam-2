package com.fullsteam.ai;

import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.physics.GameEntities;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for AI hazard detection and avoidance.
 * Helps AI players detect and avoid dangerous field effects and environmental hazards.
 */
public class HazardAvoidance {
    
    /**
     * Find all dangerous field effects near a position.
     * 
     * @param position Center position to check from
     * @param checkRadius How far to look for hazards
     * @param gameEntities Current game state
     * @return List of dangerous field effects within range
     */
    public static List<FieldEffect> findNearbyHazards(Vector2 position, double checkRadius, GameEntities gameEntities) {
        List<FieldEffect> hazards = new ArrayList<>();
        
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            if (!effect.isActive()) {
                continue;
            }
            
            // Check if this is a dangerous effect type
            if (!isDangerousEffect(effect.getType())) {
                continue;
            }
            
            // Check if effect is within detection range (effect radius + check radius)
            double distance = position.distance(effect.getPosition());
            double effectiveRange = effect.getRadius() + checkRadius;
            
            if (distance < effectiveRange) {
                hazards.add(effect);
            }
        }
        
        return hazards;
    }
    
    /**
     * Check if a field effect type is dangerous and should be avoided.
     */
    public static boolean isDangerousEffect(FieldEffectType type) {
        return switch (type) {
            // Damaging effects - avoid these
            case EXPLOSION, FIRE, ELECTRIC, POISON, EARTHQUAKE -> true;
            // Warning zones - avoid these (hazard incoming)
            case WARNING_ZONE -> true;
            // Slow fields and freeze - avoid when in combat or fleeing
            case FREEZE, SLOW_FIELD, GRAVITY_WELL -> true;
            // Proximity mines - definitely avoid
            case PROXIMITY_MINE -> true;
            // Safe or beneficial effects
            case HEAL_ZONE, SPEED_BOOST, SHIELD_BARRIER, FRAGMENTATION -> false;
        };
    }
    
    /**
     * Get the threat level of a field effect (0.0 = safe, 1.0 = extremely dangerous).
     */
    public static double getThreatLevel(FieldEffect effect) {
        return switch (effect.getType()) {
            case EXPLOSION -> 1.0; // Instant high damage
            case PROXIMITY_MINE -> 0.95; // Very dangerous if triggered
            case FIRE, POISON -> 0.8; // High damage over time
            case EARTHQUAKE -> 0.7; // Moderate continuous damage
            case ELECTRIC -> 0.75; // Can chain to nearby allies
            case WARNING_ZONE -> 0.6; // Incoming hazard
            case FREEZE, SLOW_FIELD -> 0.4; // Impairs movement
            case GRAVITY_WELL -> 0.3; // Pulls you around
            default -> 0.0;
        };
    }
    
    /**
     * Calculate a safe movement direction that avoids nearby hazards.
     * 
     * @param currentPos Current position
     * @param desiredDirection The direction the AI wants to move
     * @param gameEntities Current game state
     * @param detectionRadius How far to detect hazards
     * @return Modified movement direction that avoids hazards, or original if no hazards
     */
    public static Vector2 calculateSafeMovement(Vector2 currentPos, Vector2 desiredDirection, 
                                                 GameEntities gameEntities, double detectionRadius) {
        List<FieldEffect> nearbyHazards = findNearbyHazards(currentPos, detectionRadius, gameEntities);
        
        if (nearbyHazards.isEmpty()) {
            return desiredDirection.copy(); // No hazards, move as desired
        }
        
        // Calculate repulsion vectors from all hazards
        Vector2 avoidanceVector = new Vector2(0, 0);
        
        for (FieldEffect hazard : nearbyHazards) {
            Vector2 toHazard = hazard.getPosition().copy().subtract(currentPos);
            double distance = toHazard.getMagnitude();
            
            if (distance < 0.1) {
                // We're basically on top of the hazard - flee in any direction
                distance = 0.1;
            }
            
            // Calculate repulsion strength based on distance and threat level
            double threatLevel = getThreatLevel(hazard);
            double hazardRadius = hazard.getRadius();
            
            // Stronger repulsion when closer to hazard
            double repulsionStrength;
            if (distance < hazardRadius) {
                // Inside the hazard - strong repulsion
                repulsionStrength = threatLevel * 2.0;
            } else {
                // Outside but nearby - repulsion decreases with distance
                double distanceFromEdge = distance - hazardRadius;
                repulsionStrength = threatLevel * (detectionRadius / (distanceFromEdge + 1.0));
            }
            
            // Add repulsion vector (away from hazard)
            toHazard.normalize();
            toHazard.multiply(-repulsionStrength); // Negative to push away
            avoidanceVector.add(toHazard);
        }
        
        // Combine desired direction with avoidance
        // Weight avoidance more heavily when hazards are very close
        double avoidanceWeight = Math.min(1.0, avoidanceVector.getMagnitude() / 2.0);
        double desiredWeight = 1.0 - (avoidanceWeight * 0.7); // Don't completely ignore desired direction
        
        Vector2 finalDirection = desiredDirection.copy().multiply(desiredWeight);
        finalDirection.add(avoidanceVector.multiply(avoidanceWeight));
        
        // Normalize to get direction
        if (finalDirection.getMagnitude() > 0.01) {
            finalDirection.normalize();
        } else {
            // If we're stuck, just move away from the nearest hazard
            if (!nearbyHazards.isEmpty()) {
                FieldEffect nearest = nearbyHazards.get(0);
                finalDirection = currentPos.copy().subtract(nearest.getPosition());
                finalDirection.normalize();
            } else {
                finalDirection = desiredDirection.copy();
            }
        }
        
        return finalDirection;
    }
    
    /**
     * Check if a position is currently safe (no active hazards).
     * 
     * @param position Position to check
     * @param safetyMargin Extra distance to consider (buffer zone)
     * @param gameEntities Current game state
     * @return true if position is safe, false if in or near hazards
     */
    public static boolean isPositionSafe(Vector2 position, double safetyMargin, GameEntities gameEntities) {
        for (FieldEffect effect : gameEntities.getAllFieldEffects()) {
            if (!effect.isActive() || !isDangerousEffect(effect.getType())) {
                continue;
            }
            
            double distance = position.distance(effect.getPosition());
            double dangerZone = effect.getRadius() + safetyMargin;
            
            if (distance < dangerZone) {
                return false; // Too close to hazard
            }
        }
        
        return true; // No hazards nearby
    }
    
    /**
     * Find the nearest safe position from a given location.
     * Useful for finding where to flee when surrounded by hazards.
     * 
     * @param currentPos Current position
     * @param searchRadius How far to search for safety
     * @param gameEntities Current game state
     * @return Nearest safe position, or null if none found
     */
    public static Vector2 findNearestSafePosition(Vector2 currentPos, double searchRadius, GameEntities gameEntities) {
        // Sample positions in a circle around current position
        int samples = 16; // Check 16 directions
        double bestDistance = Double.MAX_VALUE;
        Vector2 bestPosition = null;
        
        for (int i = 0; i < samples; i++) {
            double angle = (Math.PI * 2.0 * i) / samples;
            
            // Check at multiple distances
            for (double dist = searchRadius * 0.5; dist <= searchRadius; dist += searchRadius * 0.25) {
                Vector2 testPos = new Vector2(
                    currentPos.x + Math.cos(angle) * dist,
                    currentPos.y + Math.sin(angle) * dist
                );
                
                if (isPositionSafe(testPos, 20.0, gameEntities)) {
                    double distanceToSafety = currentPos.distance(testPos);
                    if (distanceToSafety < bestDistance) {
                        bestDistance = distanceToSafety;
                        bestPosition = testPos;
                    }
                }
            }
        }
        
        return bestPosition;
    }
    
    /**
     * Check if moving from one position to another would cross through hazards.
     * 
     * @param from Starting position
     * @param to Destination position
     * @param gameEntities Current game state
     * @return true if path crosses hazards, false if clear
     */
    public static boolean pathCrossesHazards(Vector2 from, Vector2 to, GameEntities gameEntities) {
        Vector2 direction = to.copy().subtract(from);
        double distance = direction.getMagnitude();
        
        if (distance < 1.0) {
            return false; // Too short to matter
        }
        
        direction.normalize();
        
        // Check points along the path
        int checkPoints = (int) Math.min(10, distance / 20.0); // Check every ~20 units
        for (int i = 1; i <= checkPoints; i++) {
            double t = (double) i / checkPoints;
            Vector2 checkPos = from.copy().add(direction.copy().multiply(distance * t));
            
            if (!isPositionSafe(checkPos, 10.0, gameEntities)) {
                return true; // Path crosses hazard
            }
        }
        
        return false; // Path is clear
    }
    
    /**
     * Get a danger rating for the current area (0.0 = safe, 1.0 = extremely dangerous).
     * Useful for deciding whether to retreat or engage.
     */
    public static double getAreaDangerRating(Vector2 position, double radius, GameEntities gameEntities) {
        List<FieldEffect> nearbyHazards = findNearbyHazards(position, radius, gameEntities);
        
        if (nearbyHazards.isEmpty()) {
            return 0.0;
        }
        
        double totalThreat = 0.0;
        double maxThreat = 0.0;
        
        for (FieldEffect hazard : nearbyHazards) {
            double threat = getThreatLevel(hazard);
            double distance = position.distance(hazard.getPosition());
            double hazardRadius = hazard.getRadius();
            
            // Threat increases as we get closer
            double proximityFactor;
            if (distance < hazardRadius) {
                proximityFactor = 1.0; // Inside hazard
            } else {
                proximityFactor = Math.max(0.0, 1.0 - ((distance - hazardRadius) / radius));
            }
            
            double effectiveThreat = threat * proximityFactor;
            totalThreat += effectiveThreat;
            maxThreat = Math.max(maxThreat, effectiveThreat);
        }
        
        // Return combination of max threat and average threat
        return Math.min(1.0, (maxThreat * 0.7) + (totalThreat / nearbyHazards.size() * 0.3));
    }
}


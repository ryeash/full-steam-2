package com.fullsteam.physics;

import org.dyn4j.dynamics.Body;
import org.dyn4j.world.listener.StepListener;
import org.dyn4j.world.PhysicsWorld;
import org.dyn4j.dynamics.TimeStep;

import java.util.Map;

/**
 * CollisionProcessor handles collision detection and responses in the physics world.
 * It implements StepListener to receive world step events and manually check for collisions.
 */
public class CollisionProcessor implements StepListener<Body> {
    
    private final Map<Integer, Player> players;
    private final Map<Integer, Projectile> projectiles;
    private final Map<Integer, StrategicLocation> strategicLocations;
    private final CollisionHandler collisionHandler;
    
    public CollisionProcessor(Map<Integer, Player> players, 
                            Map<Integer, Projectile> projectiles,
                            Map<Integer, StrategicLocation> strategicLocations,
                            CollisionHandler collisionHandler) {
        this.players = players;
        this.projectiles = projectiles;
        this.strategicLocations = strategicLocations;
        this.collisionHandler = collisionHandler;
    }
    
    @Override
    public void begin(TimeStep step, PhysicsWorld<Body, ?> world) {
        // Called at the beginning of each world step
        // Can be used for pre-step processing
    }
    
    @Override
    public void updatePerformed(TimeStep step, PhysicsWorld<Body, ?> world) {
        // Called after velocity integration but before constraint resolution
        // We can manually check for collisions here
        processCollisions();
    }
    
    @Override
    public void postSolve(TimeStep step, PhysicsWorld<Body, ?> world) {
        // Called after constraint resolution
        // Can be used for post-constraint processing
    }
    
    @Override
    public void end(TimeStep step, PhysicsWorld<Body, ?> world) {
        // Called at the end of each world step
        // Can be used for post-step processing
    }
    
    /**
     * Manually process collisions between game entities
     */
    private void processCollisions() {
        // Check projectile vs player collisions
        for (Projectile projectile : projectiles.values()) {
            if (!projectile.isActive()) continue;
            
            for (Player player : players.values()) {
                if (!player.isActive()) continue;
                
                // Skip friendly fire
                if (projectile.getOwnerId() == player.getId()) continue;
                
                // Check if projectile and player are colliding
                if (areColliding(projectile.getBody(), player.getBody())) {
                    handlePlayerProjectileCollision(player, projectile);
                }
            }
        }
        
        // Check player vs strategic location interactions
        for (Player player : players.values()) {
            if (!player.isActive()) continue;
            
            for (StrategicLocation location : strategicLocations.values()) {
                if (areOverlapping(player.getBody(), location.getBody())) {
                    handlePlayerLocationInteraction(player, location);
                }
            }
        }
    }
    
    /**
     * Check if two bodies are colliding (for solid bodies)
     */
    private boolean areColliding(Body body1, Body body2) {
        // Use the physics world's collision detection
        // This is a simplified collision check
        double distance = body1.getTransform().getTranslation()
                .distance(body2.getTransform().getTranslation());
        
        // Rough collision detection based on radius approximation
        // In a real implementation, you'd use proper AABB or shape intersection
        double combinedRadius = getApproximateRadius(body1) + getApproximateRadius(body2);
        
        return distance <= combinedRadius;
    }
    
    /**
     * Check if two bodies are overlapping (for sensor interactions)
     */
    private boolean areOverlapping(Body body1, Body body2) {
        // Similar to areColliding but for sensor-based interactions
        return areColliding(body1, body2);
    }
    
    /**
     * Get approximate radius of a body for simple collision detection
     */
    private double getApproximateRadius(Body body) {
        // This is a simple approximation - in a real implementation
        // you'd use the actual shape bounds
        if (body.getFixtureCount() > 0) {
            return body.getFixture(0).getShape().getRadius();
        }
        return 10.0; // Default radius
    }
    
    /**
     * Handle player hit by projectile
     */
    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
        if (collisionHandler != null) {
            collisionHandler.onPlayerHitByProjectile(player, projectile);
        }
    }
    
    /**
     * Handle player interacting with strategic location
     */
    private void handlePlayerLocationInteraction(Player player, StrategicLocation location) {
        if (collisionHandler != null) {
            collisionHandler.onPlayerStayInLocation(player, location);
        }
    }
    
    /**
     * Interface for handling collision events in game logic
     */
    public interface CollisionHandler {
        void onPlayerHitByProjectile(Player player, Projectile projectile);
        void onPlayerEnterLocation(Player player, StrategicLocation location);
        void onPlayerStayInLocation(Player player, StrategicLocation location);
        void onPlayerExitLocation(Player player, StrategicLocation location);
    }
}
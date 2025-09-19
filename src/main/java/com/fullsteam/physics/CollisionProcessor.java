package com.fullsteam.physics;

import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;


/**
 * CollisionProcessor handles collision detection and responses in the physics world.
 * It implements CollisionListener to receive collision events directly from the physics engine.
 */
public class CollisionProcessor implements CollisionListener<Body, BodyFixture> {
    
    private final GameEntities gameEntities;
    private final CollisionHandler collisionHandler;
    
    public CollisionProcessor(GameEntities gameEntities, CollisionHandler collisionHandler) {
        this.gameEntities = gameEntities;
        this.collisionHandler = collisionHandler;
    }

    @Override
    public boolean collision(BroadphaseCollisionData<Body, BodyFixture> collision) {
        // Broadphase collision - quick rejection test
        // Return true to continue to narrowphase
        return true;
    }

    @Override
    public boolean collision(NarrowphaseCollisionData<Body, BodyFixture> collision) {
        // Narrowphase collision - more detailed collision detection
        // Return true to continue to manifold generation
        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData<Body, BodyFixture> collision) {
        // Manifold collision - actual contact points generated
        // This is where we handle the actual collision response
        boolean shouldPreventPhysicsResolution = handleCollision(collision);
        
        // Return false only for collisions we handle manually (like projectile hits)
        // Return true to allow physics engine to resolve other collisions normally
        return !shouldPreventPhysicsResolution;
    }

    /**
     * Handle collision between two bodies using ManifoldCollisionData
     * @return true if physics resolution should be prevented, false if physics should resolve normally
     */
    private boolean handleCollision(ManifoldCollisionData<Body, BodyFixture> collision) {
        Body body1 = collision.getBody1();
        Body body2 = collision.getBody2();
        
        // Get the game entities from the bodies
        GameEntity entity1 = (GameEntity) body1.getUserData();
        GameEntity entity2 = (GameEntity) body2.getUserData();
        
        if (entity1 == null || entity2 == null) {
            return false; // One of the bodies is not a game entity, let physics handle it
        }
        
        // Handle different collision types and return whether to prevent physics resolution
        return handleEntityCollision(entity1, entity2);
    }
    
    /**
     * Handle collision between two game entities based on their types
     * @return true if physics resolution should be prevented, false if physics should resolve normally
     */
    private boolean handleEntityCollision(GameEntity entity1, GameEntity entity2) {
        // Determine collision types and handle appropriately
        if (entity1 instanceof Player && entity2 instanceof Projectile) {
            handlePlayerProjectileCollision((Player) entity1, (Projectile) entity2);
            return true; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Projectile && entity2 instanceof Player) {
            handlePlayerProjectileCollision((Player) entity2, (Projectile) entity1);
            return true; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Player && entity2 instanceof StrategicLocation) {
            handlePlayerLocationInteraction((Player) entity1, (StrategicLocation) entity2);
            return false; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof StrategicLocation && entity2 instanceof Player) {
            handlePlayerLocationInteraction((Player) entity2, (StrategicLocation) entity1);
            return false; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof Projectile && entity2 instanceof Obstacle) {
            return handleProjectileObstacleCollision((Projectile) entity1, (Obstacle) entity2);
        } else if (entity1 instanceof Obstacle && entity2 instanceof Projectile) {
            return handleProjectileObstacleCollision((Projectile) entity2, (Obstacle) entity1);
        }
        
        // For any other collision types, let physics handle them normally
        return false;
    }
    
    
    /**
     * Handle player hit by projectile
     */
    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
        // Skip friendly fire - player can't hit themselves
        if (player.getId() == projectile.getOwnerId()) {
            return;
        }
        
        // Skip if entities are not active
        if (!player.isActive() || !projectile.isActive()) {
            return;
        }
        
        if (collisionHandler != null) {
            collisionHandler.onPlayerHitByProjectile(player, projectile);
        }
    }

    /**
     * Handle projectile hitting an obstacle
     * @return true if physics resolution should be prevented, false if physics should resolve normally
     */
    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
        if (!projectile.isActive()) {
            return false;
        }

        if (projectile.isBounces()) {
            // Let the physics engine handle the bounce
            return false;
        } else {
            // Deactivate the projectile
            projectile.setActive(false);
            // Prevent the physics engine from resolving the collision (e.g., bouncing)
            return true;
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
package com.fullsteam.physics;

import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;


public class CollisionProcessor implements CollisionListener<Body, BodyFixture> {

    private final GameEntities gameEntities;
    private final CollisionHandler collisionHandler;

    public CollisionProcessor(GameEntities gameEntities, CollisionHandler collisionHandler) {
        this.gameEntities = gameEntities;
        this.collisionHandler = collisionHandler;
    }

    @Override
    public boolean collision(BroadphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(NarrowphaseCollisionData<Body, BodyFixture> collision) {
        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData<Body, BodyFixture> collision) {
        return handleCollision(collision);
    }

    private boolean handleCollision(ManifoldCollisionData<Body, BodyFixture> collision) {
        Body body1 = collision.getBody1();
        Body body2 = collision.getBody2();

        Object userData1 = body1.getUserData();
        Object userData2 = body2.getUserData();

        // Check for projectile hitting a world boundary
        if (userData1 instanceof Projectile && "boundary".equals(userData2)) {
            ((Projectile) userData1).setActive(false);
            return false; // Prevent bounce
        }
        if (userData2 instanceof Projectile && "boundary".equals(userData1)) {
            ((Projectile) userData2).setActive(false);
            return false; // Prevent bounce
        }

        // Check for entity-entity collisions
        if (userData1 instanceof GameEntity && userData2 instanceof GameEntity) {
            return handleEntityCollision((GameEntity) userData1, (GameEntity) userData2);
        }

        // For any other collision types, let physics handle them normally
        return true;
    }

    private boolean handleEntityCollision(GameEntity entity1, GameEntity entity2) {
        if (entity1 instanceof Player && entity2 instanceof Projectile) {
            handlePlayerProjectileCollision((Player) entity1, (Projectile) entity2);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Projectile && entity2 instanceof Player) {
            handlePlayerProjectileCollision((Player) entity2, (Projectile) entity1);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Player && entity2 instanceof StrategicLocation) {
            handlePlayerLocationInteraction((Player) entity1, (StrategicLocation) entity2);
            return true; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof StrategicLocation && entity2 instanceof Player) {
            handlePlayerLocationInteraction((Player) entity2, (StrategicLocation) entity1);
            return true; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof Projectile && entity2 instanceof Obstacle) {
            return handleProjectileObstacleCollision((Projectile) entity1, (Obstacle) entity2);
        } else if (entity1 instanceof Obstacle && entity2 instanceof Projectile) {
            return handleProjectileObstacleCollision((Projectile) entity2, (Obstacle) entity1);
        }

        return true;
    }


    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
        if (player.getId() == projectile.getOwnerId() || !player.isActive() || !projectile.isActive()) {
            return;
        }
        if (collisionHandler != null) {
            collisionHandler.onPlayerHitByProjectile(player, projectile);
        }
    }

    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
        if (!projectile.isActive()) {
            return true;
        }

        if (projectile.isBounces()) {
            return true;
        } else {
            projectile.setActive(false);
            return false;
        }
    }

    private void handlePlayerLocationInteraction(Player player, StrategicLocation location) {
        if (collisionHandler != null) {
            collisionHandler.onPlayerStayInLocation(player, location);
        }
    }

    public interface CollisionHandler {
        void onPlayerHitByProjectile(Player player, Projectile projectile);
        void onPlayerEnterLocation(Player player, StrategicLocation location);
        void onPlayerStayInLocation(Player player, StrategicLocation location);
        void onPlayerExitLocation(Player player, StrategicLocation location);
    }
}

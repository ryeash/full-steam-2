package com.fullsteam.physics;

import com.fullsteam.games.GameManager;
import com.fullsteam.model.FieldEffect;
import lombok.Getter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.contact.Contact;
import org.dyn4j.dynamics.contact.SolvedContact;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ContactCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;
import org.dyn4j.world.listener.ContactListener;


public class CollisionProcessor implements CollisionListener<Body, BodyFixture>, ContactListener<Body> {

    private final GameManager gameManager;
    private final GameEntities gameEntities;
    /**
     * -- GETTER --
     * Get the bullet effect processor for accessing pending effects and projectiles
     */
    @Getter
    private final BulletEffectProcessor bulletEffectProcessor;

    public CollisionProcessor(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
        this.bulletEffectProcessor = new BulletEffectProcessor(gameEntities);
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
        // Prevent projectile-to-projectile collisions - let them pass through each other
        if (entity1 instanceof Projectile && entity2 instanceof Projectile) {
            return false; // Disable collision resolution between projectiles
        }

        if (entity1 instanceof Player player && entity2 instanceof Projectile projectile) {
            handlePlayerProjectileCollision(player, projectile);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Player player) {
            handlePlayerProjectileCollision(player, projectile);
            return false; // Prevent physics resolution for projectile hits
        } else if (entity1 instanceof Player player && entity2 instanceof StrategicLocation strategicLocation) {
            handlePlayerLocationInteraction(player, strategicLocation);
            return true; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof StrategicLocation strategicLocation && entity2 instanceof Player player) {
            handlePlayerLocationInteraction(player, strategicLocation);
            return true; // Allow physics to handle player-location overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof Projectile projectile && entity2 instanceof Obstacle obstacle) {
            return handleProjectileObstacleCollision(projectile, obstacle);
        } else if (entity1 instanceof Obstacle obstacle && entity2 instanceof Projectile projectile) {
            return handleProjectileObstacleCollision(projectile, obstacle);
        } else if (entity1 instanceof Player player && entity2 instanceof FieldEffect fieldEffect) {
            handlePlayerFieldEffectCollision(player, fieldEffect);
            return true; // Allow physics to handle overlaps (sensors should not resolve anyway)
        } else if (entity1 instanceof FieldEffect fieldEffect && entity2 instanceof Player player) {
            handlePlayerFieldEffectCollision(player, fieldEffect);
            return true; // Allow physics to handle overlaps (sensors should not resolve anyway)
        }
        return true;
    }


    private void handlePlayerProjectileCollision(Player player, Projectile projectile) {
        if (!player.isActive() || !projectile.isActive()) {
            return;
        }

        // if the player has already been hit with this projectile, skip them
        // particularly important for piercing projectiles
        if (!projectile.getAffectedPlayers().add(player.getId())) {
            return;
        }

        // Use the projectile's team-aware damage logic
        if (!projectile.canDamage(player)) {
            return; // Can't damage self or teammates
        }

        // Process bullet effects before handling the hit
        bulletEffectProcessor.processEffectsOnPlayerHit(projectile, player);

        if (player.takeDamage(projectile.getDamage())) {
            Player killer = gameEntities.getPlayer(projectile.getOwnerId());
            gameManager.killPlayer(player, killer);
        }

        // Check if projectile should pierce through the target
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, player);
        // Deactivate projectile unless it pierces
        if (!shouldPierce) {
            projectile.setActive(false);
        }
    }

    private boolean handleProjectileObstacleCollision(Projectile projectile, Obstacle obstacle) {
        if (!projectile.isActive()) {
            return true;
        }

        // Get hit position for effects
        org.dyn4j.geometry.Vector2 hitPos = projectile.getBody().getTransform().getTranslation();
        Vector2 hitPosition = new Vector2(hitPos.x, hitPos.y);

        // Process bullet effects on obstacle hit
        bulletEffectProcessor.processEffectsOnObstacleHit(projectile, hitPosition);

        // Check if projectile should bounce
        boolean shouldBounce = bulletEffectProcessor.shouldBounceOffObstacle(projectile, obstacle);

        // Check if projectile should pierce through obstacles
        boolean shouldPierce = bulletEffectProcessor.shouldPierceTarget(projectile, obstacle);

        if (shouldPierce) {
            return false; // Don't resolve collision, let projectile pass through
        } else if (shouldBounce) {
            return true; // Let physics handle the bounce
        } else {
            projectile.setActive(false);
            return false; // Stop the projectile
        }
    }

    private void handlePlayerLocationInteraction(Player player, StrategicLocation location) {
        // TODO
    }

    private void handlePlayerFieldEffectCollision(Player player, FieldEffect fieldEffect) {
        if (!player.isActive() || !fieldEffect.isActive()) {
            return;
        }

        // Check if the field effect can affect this player (team rules, etc.)
        if (!fieldEffect.canAffect(player)) {
            return;
        }

        // Delegate to collision handler if available
        if (!player.isActive() || !fieldEffect.isActive()) {
            return;
        }

        // Only handle instantaneous effects here (explosions, fragmentation)
        // Damage-over-time effects are handled in updateFieldEffects()
        if (fieldEffect.getType().isInstantaneous()) {
            double damage = fieldEffect.getDamageAtPosition(player.getPosition());
            if (damage > 0) {
                // Instant damage - only apply once per effect
                if (!fieldEffect.getAffectedEntities().contains(player.getId())) {
                    boolean playerKilled = player.takeDamage(damage);
                    fieldEffect.markAsAffected(player);

                    if (playerKilled) {
                        gameManager.killPlayer(player, gameEntities.getPlayer(fieldEffect.getOwnerId()));
                    }
                }
            }
        }
        // Note: Damage-over-time effects (FIRE, POISON, ELECTRIC, FREEZE) are now handled
        // continuously in updateFieldEffects() method for proper timing and damage application
    }

    // ContactListener methods

    @Override
    public void begin(ContactCollisionData<Body> collision, Contact contact) {
    }

    @Override
    public void persist(ContactCollisionData<Body> collision, Contact oldContact, Contact newContact) {

    }

    @Override
    public void end(ContactCollisionData<Body> collision, Contact contact) {

    }

    @Override
    public void destroyed(ContactCollisionData<Body> collision, Contact contact) {

    }

    @Override
    public void collision(ContactCollisionData<Body> collision) {

    }

    @Override
    public void preSolve(ContactCollisionData<Body> collision, Contact contact) {

    }

    @Override
    public void postSolve(ContactCollisionData<Body> collision, SolvedContact contact) {

    }
}

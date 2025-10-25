package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.StatusEffects;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Special projectile that immobilizes targets when it hits
 */
@Getter
@Setter
public class NetProjectile extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double damage;
    private final double slowEffect; // Linear damping multiplier for slowing effect (higher = more slowed)
    private final double slowDuration; // How long the slow effect lasts
    private final double pushbackForce; // Force applied to push player backward
    private Vector2 velocity;
    private boolean hasHit = false;

    public NetProjectile(int id, int ownerId, int ownerTeam, Vector2 position, Vector2 velocity, double timeToLive) {
        super(id, createNetProjectileBody(position), 1);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.velocity = velocity.copy();
        this.damage = 0;
        this.slowEffect = 10.0;
        this.slowDuration = 2.0;
        this.pushbackForce = Config.NET_PUSHBACK_FORCE;
        this.expires = (long) (System.currentTimeMillis() + (timeToLive * 1000));
    }

    private static Body createNetProjectileBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(8.0); // Slightly larger than normal projectiles
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(position.x, position.y);
        body.setLinearDamping(0.01); // Very little damping - nets fly far
        body.setAngularVelocity(70); // Nets spin around
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }
        if (!hasHit) {
            body.setLinearVelocity(velocity.x, velocity.y);
        }
        super.update(deltaTime);
    }

    /**
     * Handle collision with a player
     */
    public void hitPlayer(Player player) {
        if (hasHit || !active) {
            return;
        }
        hasHit = true;
        active = false; // Net is consumed on hit

        // Apply immobilization effect after pushback
        String ownerName = "Net"; // Default name if owner not found
        StatusEffects.applySlowEffect(player, slowEffect, slowDuration, ownerName);
        // Apply pushback impulse - push player in opposite direction of net's velocity
        player.getBody().applyImpulse(velocity.getNormalized().multiply(pushbackForce));
    }

    /**
     * Check if this net can affect a player
     */
    public boolean canAffectPlayer(Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }

        // Can't affect the owner
        if (player.getId() == ownerId) {
            return false;
        }

        // In FFA mode (team 0), can affect anyone except owner
        if (ownerTeam == 0 || player.getTeam() == 0) {
            return true;
        }

        // In team mode, can only affect players on different teams
        return ownerTeam != player.getTeam();
    }

    /**
     * Get current velocity
     */
    public Vector2 getVelocity() {
        return velocity.copy();
    }

    /**
     * Set velocity (for physics updates)
     */
    public void setVelocity(Vector2 newVelocity) {
        this.velocity = newVelocity.copy();
        if (body != null) {
            body.setLinearVelocity(newVelocity.x, newVelocity.y);
        }
    }
}

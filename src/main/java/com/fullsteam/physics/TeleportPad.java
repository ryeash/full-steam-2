package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Teleporter pad that creates linked portals for quick movement
 */
@Getter
@Setter
public class TeleportPad extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double activationRadius;
    private final double cooldownTime;
    private final double maxLifespan;

    private double lastActivationTime = 0;
    private boolean isLinked = false;
    private TeleportPad linkedPad = null;
    private List<Integer> recentlyTeleportedPlayers = new ArrayList<>();

    // Visual effects
    private double pulseTime = 0;
    private boolean isCharging = true;
    private double chargingTime = 2.0; // 2 seconds to fully activate

    public TeleportPad(int id, int ownerId, int ownerTeam, Vector2 position, double maxLifespan) {
        super(id, createTeleportPadBody(position), Double.POSITIVE_INFINITY); // Teleport pads don't have health
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.activationRadius = 25.0; // Players must be within this distance
        this.cooldownTime = 1.0; // 1 second cooldown between teleports
        this.maxLifespan = maxLifespan;
        this.expires = (long) (System.currentTimeMillis() + (maxLifespan * 1000));
    }

    private static Body createTeleportPadBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(20.0); // Large activation area
        BodyFixture bodyFixture = body.addFixture(circle);
        bodyFixture.setSensor(true);
        body.setMass(MassType.INFINITE); // Stationary
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Update charging state
        if (isCharging) {
            chargingTime -= deltaTime;
            if (chargingTime <= 0) {
                isCharging = false;
            }
        }

        // Update pulse animation
        pulseTime += deltaTime * 3.0; // Fast pulsing

        // Clear recently teleported players after cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActivationTime > cooldownTime * 1000) {
            recentlyTeleportedPlayers.clear();
        }

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Link this teleport pad to another pad
     */
    public void linkTo(TeleportPad otherPad) {
        // Unlink from current partner if we have one
        if (this.linkedPad != null && this.linkedPad != otherPad) {
            TeleportPad oldPartner = this.linkedPad;
            this.linkedPad = null;
            this.isLinked = false;
            // Unlink the old partner from us (without recursion)
            if (oldPartner.linkedPad == this) {
                oldPartner.linkedPad = null;
                oldPartner.isLinked = false;
            }
        }
        
        this.linkedPad = otherPad;
        this.isLinked = true;

        // Create bidirectional link
        if (otherPad != null && otherPad.linkedPad != this) {
            otherPad.linkTo(this);
        }
    }

    /**
     * Attempt to teleport a player
     */
    public boolean teleportPlayer(Player player) {
        if (!canTeleportPlayer(player)) {
            return false;
        }

        if (!isLinked || linkedPad == null || !linkedPad.isActive()) {
            return false;
        }

        // Perform teleportation
        Vector2 destination = linkedPad.getPosition();
        player.setPosition(destination.x, destination.y);

        // Add player to recently teleported list to prevent immediate return
        recentlyTeleportedPlayers.add(player.getId());
        linkedPad.recentlyTeleportedPlayers.add(player.getId());

        lastActivationTime = System.currentTimeMillis();
        linkedPad.lastActivationTime = System.currentTimeMillis();

        return true;
    }

    /**
     * Check if a player can be teleported
     */
    private boolean canTeleportPlayer(Player player) {
        if (!player.isActive() || player.getHealth() <= 0) {
            return false;
        }

        if (isCharging) {
            return false; // Still charging up
        }

        // Check if player is within activation radius
        Vector2 playerPos = player.getPosition();
        Vector2 padPos = getPosition();
        double distance = playerPos.distance(padPos);
        if (distance > activationRadius) {
            return false;
        }

        // Check cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActivationTime < cooldownTime * 1000) {
            return false;
        }

        // Check if player was recently teleported (prevent ping-ponging)
        if (recentlyTeleportedPlayers.contains(player.getId())) {
            return false;
        }

        // Team restrictions - only owner's team can use (or everyone in FFA)
        if (ownerTeam != 0 && player.getTeam() != 0 && player.getTeam() != ownerTeam) {
            return false;
        }

        return true;
    }

    /**
     * Get the charging progress (0.0 to 1.0)
     */
    public double getChargingProgress() {
        if (!isCharging) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (chargingTime / 2.0));
    }

    /**
     * Get pulse animation value for visual effects
     */
    public double getPulseValue() {
        return 0.5 + 0.5 * Math.sin(pulseTime);
    }

    /**
     * Unlink this pad from its partner
     */
    public void unlink() {
        if (linkedPad != null) {
            linkedPad.linkedPad = null;
            linkedPad.isLinked = false;
        }
        this.linkedPad = null;
        this.isLinked = false;
    }

    /**
     * Destroy this teleport pad and unlink it
     */
    public void destroy() {
        unlink();
        active = false;
    }
}

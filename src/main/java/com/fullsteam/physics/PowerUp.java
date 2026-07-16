package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Power-up entity that can be collected by players to gain temporary abilities.
 */
@Getter
@Setter
public class PowerUp extends GameEntity {

    private final PowerUpType type;
    private final double duration;
    private final double effectStrength;

    public PowerUp(int id, Vector2 position, PowerUpType type, double duration, double effectStrength) {
        super(id, createPowerUpBody(position), 1.0);
        this.type = type;
        this.duration = duration;
        this.effectStrength = effectStrength;
    }

    private static Body createPowerUpBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(15.0); // Small pickup size
        body.addFixture(circle).setSensor(true);
        body.setMass(MassType.INFINITE); // Make power-ups stationary
        body.getTransform().setTranslation(position.x, position.y);
        body.setUserData("powerup");
        return body;
    }

    /**
     * Check if a player can collect this power-up.
     */
    public boolean canBeCollectedBy(Player player) {
        return active && player.isActive() && player.getHealth() > 0;
    }

    /**
     * Get the effect parameters for this power-up.
     */
    public PowerUpEffect getEffect() {
        return new PowerUpEffect(type, duration, effectStrength);
    }

}

package com.fullsteam.physics;

import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

/**
 * Power-up entity that can be collected by players to gain temporary abilities.
 * Power-ups spawn around workshops and can be picked up by walking over them.
 */
@Getter
@Setter
public class PowerUp extends GameEntity {
    public enum PowerUpType {
        SPEED_BOOST("Speed Boost", "⚡"),
        HEALTH_REGENERATION("Health Regen", "❤️"),
        DAMAGE_BOOST("Damage Boost", "⚔️"),
        DAMAGE_RESISTANCE("Damage Resist", "🛡️"),
        BERSERKER_MODE("Berserker", "🔥");

        private final String displayName;
        private final String renderHint;
        
        PowerUpType(String displayName, String renderHint) {
            this.displayName = displayName;
            this.renderHint = renderHint;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getRenderHint() {
            return renderHint;
        }
    }
    
    private final PowerUpType type;
    private final int workshopId; // Which workshop spawned this power-up
    private final double duration; // How long the effect lasts
    private final double effectStrength; // Strength of the effect
    
    public PowerUp(int id, Vector2 position, PowerUpType type, int workshopId, double duration, double effectStrength) {
        super(id, createPowerUpBody(position), 1.0);
        this.type = type;
        this.workshopId = workshopId;
        this.duration = duration;
        this.effectStrength = effectStrength;
    }
    
    private static Body createPowerUpBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(8.0); // Small pickup size
        body.addFixture(circle);
        body.getFixture(0).setSensor(true); // Make it a sensor so players can walk through it
        body.setMass(MassType.INFINITE); // Make power-ups stationary
        body.getTransform().setTranslation(position.x, position.y);
        body.setUserData("powerup");
        return body;
    }
    
    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }
        
        // Power-ups are stationary - no floating animation needed
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Check if a player can collect this power-up.
     */
    public boolean canBeCollectedBy(Player player) {
        if (!active || !player.isActive() || player.getHealth() <= 0) {
            return false;
        }
        
        // Check if player is close enough
        double distance = getPosition().distance(player.getPosition());
        return distance <= 15.0; // Collection radius
    }
    
    /**
     * Get the effect parameters for this power-up.
     */
    public PowerUpEffect getEffect() {
        return new PowerUpEffect(type, duration, effectStrength);
    }
    
    /**
     * Data class containing power-up effect information.
     */
    public static class PowerUpEffect {
        private final PowerUpType type;
        private final double duration;
        private final double strength;
        
        public PowerUpEffect(PowerUpType type, double duration, double strength) {
            this.type = type;
            this.duration = duration;
            this.strength = strength;
        }
        
        public PowerUpType getType() {
            return type;
        }
        
        public double getDuration() {
            return duration;
        }
        
        public double getStrength() {
            return strength;
        }
    }
}

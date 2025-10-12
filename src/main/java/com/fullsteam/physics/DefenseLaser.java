package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.Ordinance;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Defense Laser utility weapon that creates three rotating plasma beams
 * around itself for area denial and damage over time
 */
@Getter
@Setter
public class DefenseLaser extends GameEntity {
    private final int ownerId;
    private final int ownerTeam;
    private final double detectionRange;
    private final double beamLength;
    private final double rotationSpeed; // radians per second
    private final double damage;
    private final long expires;
    
    // Three rotating beams
    private final List<Beam> beams = new ArrayList<>();
    private double currentRotation = 0.0;
    private final World<Body> world;

    public DefenseLaser(int id, int ownerId, int ownerTeam, Vector2 position, double lifespan, World<Body> world) {
        super(id, createDefenseLaserBody(position), 75.0); // 75 HP
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
        this.detectionRange = 300.0;
        this.beamLength = 200.0;
        this.rotationSpeed = Math.PI / 2.0; // 90 degrees per second
        this.damage = 40.0; // Moderate DOT damage
        this.expires = (long) (System.currentTimeMillis() + (lifespan * 1000));
        this.world = world;
        
        // Create initial beams at 120-degree intervals
        createRotatingBeams();
    }

    private static Body createDefenseLaserBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS * 0.8); // Slightly smaller than player
        body.addFixture(circle);
        body.setMass(MassType.INFINITE); // Stationary
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    /**
     * Create the three rotating beams at 120-degree intervals
     */
    private void createRotatingBeams() {
        beams.clear();
        Vector2 center = getPosition();
        
        for (int i = 0; i < 3; i++) {
            double angle = currentRotation + (i * 2 * Math.PI / 3);
            Vector2 direction = new Vector2(Math.cos(angle), Math.sin(angle));
            
            Beam beam = new Beam(
                Config.nextId(),
                center,
                direction,
                beamLength,
                damage,
                ownerId,
                ownerTeam,
                Ordinance.PLASMA_BEAM, // Reuse existing plasma beam
                Set.of() // No special effects needed
            );
            beam.setExpires(this.getExpires());
            beams.add(beam);
        }
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        // Check expiration
        if (System.currentTimeMillis() > expires) {
            active = false;
            return;
        }

        // Rotate beams
        currentRotation += rotationSpeed * deltaTime;
        updateBeamPositions();
        
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Update the positions and directions of all three beams
     * Now that Beam endPoint is mutable, we can update existing beams efficiently
     */
    private void updateBeamPositions() {
        Vector2 center = getPosition();
        
        for (int i = 0; i < beams.size(); i++) {
            Beam beam = beams.get(i);
            double angle = currentRotation + (i * 2 * Math.PI / 3);
            Vector2 direction = new Vector2(Math.cos(angle), Math.sin(angle));
            
            // Update beam start point (if needed)
            beam.getStartPoint().set(center);
            
            // Update beam direction
            beam.getDirection().set(direction);
            beam.getDirection().normalize();
            
            // Update beam end point
            Vector2 offset = direction.copy();
            offset.multiply(beamLength);
            beam.getEndPoint().set(center);
            beam.getEndPoint().add(offset);
            
            // Note: Effective endpoint will be updated by WeaponSystem via updateBeamEffectiveEndpoints()
        }
    }
    
    /**
     * Update the effective endpoints of all beams based on obstacle collisions.
     * This method should be called by WeaponSystem after updateBeamPositions().
     */
    public void updateBeamEffectiveEndpoints(Vector2[] effectiveEndpoints) {
        if (effectiveEndpoints.length != beams.size()) {
            throw new IllegalArgumentException("Effective endpoints array size must match beam count");
        }
        
        for (int i = 0; i < beams.size(); i++) {
            beams.get(i).setEffectiveEndPoint(effectiveEndpoints[i]);
        }
    }
}

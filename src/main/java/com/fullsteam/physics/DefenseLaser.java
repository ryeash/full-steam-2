package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.model.FieldEffectBeam;
import com.fullsteam.model.FieldEffectType;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defense Laser utility weapon that creates three rotating plasma beams
 * around itself for area denial and damage over time.
 *
 * <p>The three arm beams are {@link FieldEffectBeam} objects held directly by this
 * entity (not added to the global {@code fieldEffects} map). Their geometry is
 * updated in-place every tick via {@link #updateBeamPositions()}; the
 * {@link com.fullsteam.games.GameManager} reads their current start/end points to
 * compute obstacle-clipped effective endpoints each frame.
 */
@Getter
@Setter
public class DefenseLaser extends OwnedGameEntity {
    private final double detectionRange;
    private final double beamLength;
    private final double rotationSpeed; // radians per second
    private final double damage;
    private final long expires;

    // Three rotating arm beams (not added to the global fieldEffects map)
    private final List<FieldEffectBeam> beams = new ArrayList<>();
    private double currentRotation = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
    private final World<Body> world;

    public DefenseLaser(int ownerId, int ownerTeam, Vector2 position, double lifespan, World<Body> world) {
        super(Config.nextEntityId(), createDefenseLaserBody(position), 75.0, ownerId, ownerTeam); // 75 HP
        this.detectionRange = 300.0;
        this.beamLength = 200.0;
        this.rotationSpeed = Math.PI / 2.0; // 90 degrees per second
        this.damage = 80.0; // Moderate DOT damage
        this.expires = (long) (System.currentTimeMillis() + (lifespan * 1000));
        this.world = world;
        createRotatingBeams();
    }

    private static Body createDefenseLaserBody(Vector2 position) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS * 0.8);
        body.addFixture(circle);
        body.setMass(MassType.INFINITE);
        body.getTransform().setTranslation(position.x, position.y);
        return body;
    }

    /**
     * Create the three arm beams at 120-degree intervals.
     */
    private void createRotatingBeams() {
        beams.clear();
        Vector2 center = getPosition();

        for (int i = 0; i < 3; i++) {
            double angle = currentRotation + (i * 2 * Math.PI / 3);
            Vector2 direction = new Vector2(Math.cos(angle), Math.sin(angle));

            FieldEffectBeam beam = new FieldEffectBeam(
                    center,
                    direction,
                    beamLength,
                    damage,
                    ownerId,
                    ownerTeam,
                    FieldEffectType.PLASMA,
                    Set.of(),
                    1.0
            );
            // Arm beams live as long as the DefenseLaser itself.
            beam.setExpires(this.getExpires());
            beams.add(beam);
        }
    }

    @Override
    public void update(double deltaTime) {
        if (!active) {
            return;
        }

        if (System.currentTimeMillis() > expires) {
            active = false;
            return;
        }

        currentRotation += rotationSpeed * deltaTime;
        updateBeamPositions();

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Update the positions and directions of all three arm beams in-place.
     */
    private void updateBeamPositions() {
        Vector2 center = getPosition();

        for (int i = 0; i < beams.size(); i++) {
            FieldEffectBeam beam = beams.get(i);
            double angle = currentRotation + (i * 2 * Math.PI / 3);
            Vector2 direction = new Vector2(Math.cos(angle), Math.sin(angle));

            beam.getStartPoint().set(center);
            beam.getDirection().set(direction);
            beam.getDirection().normalize();

            Vector2 offset = direction.copy().multiply(beamLength);
            beam.getEndPoint().set(center);
            beam.getEndPoint().add(offset);
            beam.updateBodyTransform();
        }
    }

    /**
     * Update the effective (obstacle-clipped) endpoints of all arm beams.
     * Called by {@code GameManager.updateDefenseLaserBeamEndpoints()} after
     * obstacle raycasting.
     */
    public void updateBeamEffectiveEndpoints(Vector2[] effectiveEndpoints) {
        if (effectiveEndpoints.length != beams.size()) {
            throw new IllegalArgumentException("Effective endpoints array size must match beam count");
        }
        for (int i = 0; i < beams.size(); i++) {
            beams.get(i).setEndPoint(effectiveEndpoints[i]);
            beams.get(i).updateBodyTransform();
        }
    }
}

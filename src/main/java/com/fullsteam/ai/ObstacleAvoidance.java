package com.fullsteam.ai;

import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Obstacle;
import org.dyn4j.geometry.Vector2;

/**
 * Steering helper that keeps AI players from running into physical map obstacles
 * (boulders, houses, walls, etc.).
 *
 * <p>Unlike {@link HazardAvoidance}, which repels AI from transient field effects,
 * this class performs look-ahead steering around solid bodies: obstacles directly
 * in the path produce tangential ("go around") steering plus a radial push when
 * very close, while obstacles to the side or behind the direction of travel are
 * ignored so the AI isn't pushed off course by things it isn't heading toward.
 */
public final class ObstacleAvoidance {

    private static final double EPSILON = 1e-4;

    private ObstacleAvoidance() {
    }

    /**
     * Adjust a desired movement direction to steer around nearby obstacles.
     *
     * @param currentPos       the AI's current position
     * @param desiredDirection the direction the AI wants to travel (need not be normalized)
     * @param gameEntities     current world state
     * @param lookAhead        how far beyond an obstacle's surface to start reacting
     * @param agentRadius      the moving agent's collision radius
     * @return a normalized steering direction (falls back to the desired direction when clear)
     */
    public static Vector2 steer(Vector2 currentPos, Vector2 desiredDirection,
                                GameEntities gameEntities, double lookAhead, double agentRadius) {
        if (desiredDirection.getMagnitude() < EPSILON) {
            return desiredDirection.copy();
        }

        Vector2 desired = desiredDirection.getNormalized();
        Vector2 avoidance = new Vector2(0, 0);

        for (Obstacle obstacle : gameEntities.getAllObstacles()) {
            if (!obstacle.isActive()) {
                continue;
            }

            Vector2 toObstacle = obstacle.getPosition().subtract(currentPos);
            double dist = toObstacle.getMagnitude();
            if (dist < EPSILON) {
                continue;
            }

            double clearance = obstacle.getRadius() + agentRadius;
            double surfaceDist = dist - clearance;
            if (surfaceDist > lookAhead) {
                continue; // too far away to matter
            }

            Vector2 dirToObstacle = toObstacle.multiply(1.0 / dist); // now normalized
            double forwardness = desired.dot(dirToObstacle);
            if (forwardness <= 0.0) {
                continue; // obstacle is beside or behind our heading
            }

            // 1.0 at (or inside) the surface, fading to 0.0 at the look-ahead edge.
            double proximity = surfaceDist <= 0.0
                    ? 1.0
                    : Math.max(0.0, 1.0 - surfaceDist / lookAhead);
            double strength = proximity * forwardness;

            // Tangential steering: turn toward whichever side is the smaller course change.
            Vector2 left = new Vector2(-dirToObstacle.y, dirToObstacle.x);
            Vector2 right = new Vector2(dirToObstacle.y, -dirToObstacle.x);
            Vector2 tangent = desired.dot(left) >= desired.dot(right) ? left : right;

            avoidance.add(tangent.multiply(strength));
            // Radial push (away from the obstacle) ramps up near the surface to avoid clipping.
            avoidance.add(dirToObstacle.multiply(-strength * proximity));
        }

        if (avoidance.getMagnitude() < EPSILON) {
            return desired;
        }

        // The stronger the accumulated avoidance, the more it overrides the desired heading.
        double avoidWeight = Math.min(1.5, avoidance.getMagnitude());
        Vector2 result = desired.copy().add(avoidance.getNormalized().multiply(avoidWeight));

        if (result.getMagnitude() < EPSILON) {
            // Desired and avoidance cancelled out (obstacle dead ahead) — go fully tangential.
            return avoidance.getNormalized();
        }
        return result.getNormalized();
    }
}

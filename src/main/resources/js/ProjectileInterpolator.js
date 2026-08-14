/**
 * Projectile Interpolator for smooth movement between server updates.
 * Extends EntityInterpolator for universal motion compatibility.
 */
class ProjectileInterpolator extends EntityInterpolator {
    constructor(container, initialVelocity = { x: 0, y: 0 }) {
        super(container, {
            vx: initialVelocity.x,
            vy: initialVelocity.y,
            snapThreshold: 100,
            lerpRate: 24.0
        });
    }
}

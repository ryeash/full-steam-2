/**
 * Universal Entity Interpolator for smooth movement and rotation between server updates.
 *
 * Supports dead reckoning (velocity prediction), frame-rate independent lerping,
 * angle wrapping for rotation, snap thresholds for large position deltas (teleports/respawns),
 * and callbacks for updating attached child components (e.g. health bars, labels).
 */
class EntityInterpolator {
    /**
     * @param {Object} target - Display object or container with x, y (and optional rotation)
     * @param {Object} options - Configuration options
     * @param {number} [options.vx=0] - Initial X velocity (units/sec)
     * @param {number} [options.vy=0] - Initial Y velocity (units/sec)
     * @param {number} [options.snapThreshold=150] - Distance threshold (units) to snap immediately
     * @param {number} [options.lerpRate=18.0] - Smoothing rate
     * @param {boolean} [options.hasRotation=false] - Whether to interpolate rotation
     * @param {Function} [options.onUpdate=null] - Optional callback run after updating target position
     */
    constructor(target, options = {}) {
        this.target = target;
        this.vx = options.vx || 0;
        this.vy = options.vy || 0;

        const startX = target && typeof target.x === 'number' ? target.x : 0;
        const startY = target && typeof target.y === 'number' ? target.y : 0;
        const startRot = target && typeof target.rotation === 'number' ? target.rotation : 0;

        this.targetX = startX;
        this.targetY = startY;
        this.targetRotation = startRot;

        this.snapThreshold = options.snapThreshold || 150;
        this.lerpRate = options.lerpRate || 18.0;
        this.hasRotation = !!options.hasRotation;
        this.onUpdate = options.onUpdate || null;
    }

    /**
     * Update target position, velocity, and rotation from server message.
     * @param {number} x
     * @param {number} y
     * @param {number} [vx=0]
     * @param {number} [vy=0]
     * @param {number|null} [rotation=null]
     */
    updateFromServer(x, y, vx = 0, vy = 0, rotation = null) {
        if (!this.target || typeof x !== 'number' || typeof y !== 'number') {
            return;
        }

        // Check if position delta exceeds snap threshold (teleport, respawn, spawn)
        const dx = this.target.x - x;
        const dy = this.target.y - y;
        if (dx * dx + dy * dy > this.snapThreshold * this.snapThreshold) {
            this.target.x = x;
            this.target.y = y;
            if (this.hasRotation && typeof rotation === 'number') {
                this.target.rotation = rotation;
            }
        }

        this.targetX = x;
        this.targetY = y;
        this.vx = vx || 0;
        this.vy = vy || 0;

        if (this.hasRotation && typeof rotation === 'number') {
            this.targetRotation = rotation;
        }
    }

    /**
     * Advance interpolation by deltaTime (frame step).
     * @param {number} deltaTime - PIXI deltaTime (at 60 FPS = 1.0)
     */
    update(deltaTime) {
        if (!this.target) {
            return;
        }

        // Convert PIXI deltaTime to seconds (60 FPS baseline)
        const dt = (deltaTime || 1) / 60.0;

        // Velocity prediction
        const predX = this.targetX + this.vx * dt;
        const predY = this.targetY + this.vy * dt;

        // Smooth lerp towards predicted target
        const lerpFactor = Math.min(1.0, this.lerpRate * dt);
        this.target.x += (predX - this.target.x) * lerpFactor;
        this.target.y += (predY - this.target.y) * lerpFactor;

        // Angle lerp with shortest-arc wrapping
        if (this.hasRotation && typeof this.targetRotation === 'number') {
            let diff = this.targetRotation - this.target.rotation;
            while (diff < -Math.PI) diff += Math.PI * 2;
            while (diff > Math.PI) diff -= Math.PI * 2;
            this.target.rotation += diff * lerpFactor;
        }

        // Trigger child component update callback if attached
        if (this.onUpdate) {
            this.onUpdate(this.target, dt);
        }
    }

    /**
     * Clean up references when the entity or interpolator is destroyed.
     */
    destroy() {
        this.target = null;
        this.onUpdate = null;
    }
}

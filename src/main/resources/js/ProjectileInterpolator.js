/**
 * Projectile Interpolator for smooth movement between server updates
 * Optimized for performance and accuracy
 */
class ProjectileInterpolator {
    constructor(container, initialVelocity = { x: 0, y: 0 }) {
        this.container = container; // Now works with container instead of sprite
        this.velocity = { ...initialVelocity };
        this.serverPos = { x: container.x, y: container.y };
        this.lastServerUpdate = performance.now(); // Use high-resolution timer
        this.correctionThreshold = 15; // Reduced from 30 pixels for smoother movement
        this.maxCorrectionSpeed = 500; // pixels/second - limit correction speed to avoid teleporting
        
        // Performance optimization: cache frequently used values
        this.tempDistance = 0;
        this.tempDx = 0;
        this.tempDy = 0;
        
        // Trail tracking
        this.lastTrailPosition = { x: container.x, y: container.y };
        this.trailUpdateDistance = 5; // Add trail point every 5 pixels of movement
    }
    
    updateFromServer(x, y, vx = 0, vy = 0) {
        const now = performance.now();
        const timeSinceLastUpdate = (now - this.lastServerUpdate) / 1000; // Convert to seconds
        
        // Calculate prediction error (difference between predicted and actual server position)
        this.tempDx = this.container.x - x;
        this.tempDy = this.container.y - y;
        this.tempDistance = Math.sqrt(this.tempDx * this.tempDx + this.tempDy * this.tempDy);
        
        // Apply correction based on error magnitude
        if (this.tempDistance > this.correctionThreshold) {
            // For large errors, apply gradual correction to avoid jarring snaps
            if (this.tempDistance > 100) {
                // Very large error - likely a teleport or major desync, snap immediately
                this.container.x = x;
                this.container.y = y;
            } else {
                // Moderate error - apply smooth correction over time
                const correctionFactor = Math.min(1.0, (this.maxCorrectionSpeed * timeSinceLastUpdate) / this.tempDistance);
                this.container.x += this.tempDx * -correctionFactor;
                this.container.y += this.tempDy * -correctionFactor;
            }
        }
        
        // Always update server reference and velocity
        this.serverPos.x = x;
        this.serverPos.y = y;
        this.velocity.x = vx;
        this.velocity.y = vy;
        this.lastServerUpdate = now;
    }
    
    update(deltaTime) {
        // PIXI deltaTime is frame-based, but we need consistent time-based movement
        // Use a fixed timestep approach for more predictable interpolation
        const targetFPS = 60;
        const dt = deltaTime / targetFPS; // Convert PIXI deltaTime to seconds
        
        // Store old position for trail tracking
        const oldX = this.container.x;
        const oldY = this.container.y;
        
        // Apply velocity-based prediction
        // This predicts where the projectile should be based on last known velocity
        this.container.x += this.velocity.x * dt;
        this.container.y += this.velocity.y * dt;
        
        // Update trail if projectile has one
        this.updateTrail(oldX, oldY);
    }
    
    updateTrail(oldX, oldY) {
        if (!this.container.trail || !this.container.trailPoints) return;
        
        // Check if projectile has moved enough to add a new trail point
        const dx = this.container.x - this.lastTrailPosition.x;
        const dy = this.container.y - this.lastTrailPosition.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance >= this.trailUpdateDistance) {
            // Add new trail point (relative to container position)
            const relativeX = this.lastTrailPosition.x - this.container.x;
            const relativeY = this.lastTrailPosition.y - this.container.y;
            
            this.container.trailPoints.push({
                x: relativeX,
                y: relativeY
            });
            
            // Update last trail position
            this.lastTrailPosition.x = this.container.x;
            this.lastTrailPosition.y = this.container.y;
            
            // Keep trail points within maximum length
            while (this.container.trailPoints.length > this.container.maxTrailLength) {
                this.container.trailPoints.shift();
            }
            
            // Update trail graphics through game engine
            if (window.gameEngine) {
                window.gameEngine.updateProjectileTrail(this.container);
            }
        }
    }
    
    /**
     * Clean up resources when interpolator is no longer needed
     */
    destroy() {
        // Clear all object references to prevent memory leaks
        this.container = null;
        this.velocity = null;
        this.serverPos = null;
        this.lastTrailPosition = null;
        
        // Clear any cached values
        this.tempDistance = 0;
        this.tempDx = 0;
        this.tempDy = 0;
    }
}


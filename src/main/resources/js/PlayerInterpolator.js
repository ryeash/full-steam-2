/**
 * Player Interpolator for smooth movement with client-side prediction
 * Handles both local player (with prediction) and remote players (with smoothing)
 */
class PlayerInterpolator {
    constructor(sprite, playerId, isLocalPlayer = false) {
        this.sprite = sprite;
        this.playerId = playerId;
        this.isLocalPlayer = isLocalPlayer;
        
        // Server state
        this.serverPos = { x: sprite.x, y: sprite.y };
        this.serverRotation = 0;
        this.lastServerUpdate = performance.now();
        this.isFirstUpdate = true; // Flag to handle initial positioning
        
        // Interpolation settings
        this.smoothingFactor = 0.15;
        this.maxInterpolationDistance = 200; // pixels - beyond this, assume teleport/respawn
        
        // Performance optimization
        this.tempDx = 0;
        this.tempDy = 0;
        this.tempDistance = 0;
    }
    
    updateFromServer(x, y, rotation = 0) {
        const now = performance.now();
        const timeSinceLastUpdate = (now - this.lastServerUpdate) / 1000;
        
        // Calculate distance from current position to server position
        this.tempDx = this.sprite.x - x;
        this.tempDy = this.sprite.y - y;
        this.tempDistance = Math.sqrt(this.tempDx * this.tempDx + this.tempDy * this.tempDy);
        
        // Check if this is a teleport/respawn (distance too large for normal movement)
        // Always snap on first update to avoid initial interpolation from (0,0)
        if (this.isFirstUpdate || this.tempDistance > this.maxInterpolationDistance) {
            // Large distance change - assume teleport/respawn, snap immediately
            this.sprite.x = x;
            this.sprite.y = y;
            this.sprite.rotation = rotation;
            
            // Player teleported/respawned - no logging needed for performance
            this.isFirstUpdate = false;
        } else {
            // Normal movement - use interpolation
            // Treat local player the same as remote players for smooth interpolation
            this.interpolateToServer(x, y, rotation, timeSinceLastUpdate);
            this.isFirstUpdate = false;
        }
        
        // Update server reference
        this.serverPos.x = x;
        this.serverPos.y = y;
        this.serverRotation = rotation;
        this.lastServerUpdate = now;
    }
    
    interpolateToServer(serverX, serverY, serverRotation, deltaTime) {
        // Smooth interpolation to server position for all players
        const lerpFactor = Math.min(1.0, this.smoothingFactor * (deltaTime * 60)); // Adjust for frame rate
        
        this.sprite.x += (serverX - this.sprite.x) * lerpFactor;
        this.sprite.y += (serverY - this.sprite.y) * lerpFactor;
        this.sprite.rotation = this.lerpAngle(this.sprite.rotation, serverRotation, lerpFactor);
    }
    
    lerpAngle(from, to, factor) {
        // Handle angle wrapping for smooth rotation
        let diff = to - from;
        if (diff > Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        return from + diff * factor;
    }
    
    destroy() {
        // Clear all object references to prevent memory leaks
        this.sprite = null;
        this.serverPos = null;
        this.predictedPos = null;
        this.velocity = null;
        this.isFirstUpdate = null;
        
        // Clear cached values
        this.tempDx = 0;
        this.tempDy = 0;
        this.tempDistance = 0;
    }
}


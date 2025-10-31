/**
 * SpectatorCamera - Manages camera movement for spectator mode
 * Supports three modes: free camera, follow player, and auto-follow action
 */
class SpectatorCamera {
    constructor(engine) {
        this.engine = engine;
        this.mode = 'free'; // 'free', 'follow', 'auto'
        this.followedPlayerId = null;
        this.position = { x: 0, y: 0 };
        this.velocity = { x: 0, y: 0 };
        this.moveSpeed = 500; // pixels per second
        this.smoothing = 0.1; // Camera smoothing factor for follow mode
    }

    /**
     * Initialize camera at world center
     */
    init(worldBounds) {
        // World coordinates are centered at (0, 0)
        this.position.x = 0;
        this.position.y = 0;
        this.updateEngineCamera();
    }

    /**
     * Update camera based on current mode
     */
    update(deltaTime) {
        switch (this.mode) {
            case 'free':
                this.updateFreeCamera(deltaTime);
                break;
            case 'follow':
                this.updateFollowCamera(deltaTime);
                break;
            case 'auto':
                this.updateAutoCamera(deltaTime);
                break;
        }
        
        this.updateEngineCamera();
    }

    /**
     * Free camera - WASD movement
     */
    updateFreeCamera(deltaTime) {
        const movement = this.engine.inputManager.movement;
        
        // Apply movement input
        this.velocity.x = movement.moveX * this.moveSpeed;
        this.velocity.y = movement.moveY * this.moveSpeed;
        
        // Update position
        this.position.x += this.velocity.x * deltaTime;
        this.position.y += this.velocity.y * deltaTime;
        
        // Clamp to world bounds
        this.clampToWorldBounds();
    }

    /**
     * Follow camera - Lock to specific player
     */
    updateFollowCamera(deltaTime) {
        if (!this.followedPlayerId) {
            // Auto-select first player if none selected
            const firstPlayerSprite = this.engine.players.values().next().value;
            if (firstPlayerSprite && firstPlayerSprite.playerData) {
                this.followedPlayerId = firstPlayerSprite.playerData.id;
            } else {
                // No players available, switch to free cam
                this.mode = 'free';
                return;
            }
        }
        
        const playerSprite = this.engine.players.get(this.followedPlayerId);
        if (playerSprite) {
            // Smooth camera follow (playerSprite IS the sprite, with position x/y)
            const targetX = playerSprite.x;
            const targetY = playerSprite.y;
            
            this.position.x += (targetX - this.position.x) * this.smoothing;
            this.position.y += (targetY - this.position.y) * this.smoothing;
        } else {
            // Player no longer exists (died/disconnected), cycle to next
            this.cycleToNextPlayer();
        }
    }

    /**
     * Auto camera - Follow the action (most recent activity)
     */
    updateAutoCamera(deltaTime) {
        // For now, just use follow camera behavior
        // TODO: Implement action detection (recent kills, objective captures, etc.)
        this.updateFollowCamera(deltaTime);
    }

    /**
     * Clamp camera position to world bounds
     */
    clampToWorldBounds() {
        const bounds = this.engine.worldBounds;
        // World coordinates are centered at (0, 0), ranging from -width/2 to +width/2
        const halfWidth = bounds.width / 2;
        const halfHeight = bounds.height / 2;
        this.position.x = Math.max(-halfWidth, Math.min(halfWidth, this.position.x));
        this.position.y = Math.max(-halfHeight, Math.min(halfHeight, this.position.y));
    }

    /**
     * Update the engine's camera object
     */
    updateEngineCamera() {
        if (this.engine.camera) {
            this.engine.camera.x = this.position.x;
            this.engine.camera.y = this.position.y;
        }
    }

    /**
     * Cycle to next available player
     */
    cycleToNextPlayer() {
        const playerSprites = Array.from(this.engine.players.values());
        if (playerSprites.length === 0) {
            this.mode = 'free';
            return;
        }
        
        // Get player IDs from playerData
        const playerIds = playerSprites
            .filter(sprite => sprite.playerData)
            .map(sprite => sprite.playerData.id);
        
        if (playerIds.length === 0) {
            this.mode = 'free';
            return;
        }
        
        const currentIndex = playerIds.indexOf(this.followedPlayerId);
        const nextIndex = (currentIndex + 1) % playerIds.length;
        this.followedPlayerId = playerIds[nextIndex];
    }

    /**
     * Set camera mode
     */
    setMode(mode) {
        this.mode = mode;
        
        if (mode === 'follow' && !this.followedPlayerId) {
            // Auto-select first player
            const firstPlayerSprite = this.engine.players.values().next().value;
            if (firstPlayerSprite && firstPlayerSprite.playerData) {
                this.followedPlayerId = firstPlayerSprite.playerData.id;
            }
        }
    }

    /**
     * Follow specific player
     */
    followPlayer(playerId) {
        this.followedPlayerId = playerId;
        this.mode = 'follow';
    }

    /**
     * Get current camera mode
     */
    getMode() {
        return this.mode;
    }

    /**
     * Get followed player ID
     */
    getFollowedPlayerId() {
        return this.followedPlayerId;
    }

    /**
     * Get followed player name
     */
    getFollowedPlayerName() {
        if (!this.followedPlayerId) return null;
        
        const playerSprite = this.engine.players.get(this.followedPlayerId);
        if (playerSprite && playerSprite.playerData) {
            // Server sends "name" not "playerName"
            return playerSprite.playerData.name || playerSprite.playerData.playerName || `Player ${this.followedPlayerId}`;
        }
        return null;
    }
}


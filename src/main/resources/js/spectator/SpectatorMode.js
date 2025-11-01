/**
 * SpectatorMode - Main orchestrator for spectator functionality
 * Coordinates camera, UI, and input for spectator experience
 */
class SpectatorMode {
    constructor(engine) {
        this.engine = engine;
        this.camera = new SpectatorCamera(engine);
        this.ui = new SpectatorUI(engine);
        this.input = new SpectatorInput(this);
        this.spectatorCount = 0;
    }

    /**
     * Initialize spectator mode
     */
    async init(initData) {
        console.log('Initializing spectator mode', initData);
        
        // Initialize camera at world center (use engine's worldBounds which was set from server data)
        this.camera.init(this.engine.worldBounds);
        
        // Show spectator UI
        this.ui.show();
        
        // Setup input controls
        this.input.setupControls();
        
        // Update spectator count
        if (initData.spectatorData && initData.spectatorData.spectatorCount !== undefined) {
            this.spectatorCount = initData.spectatorData.spectatorCount;
            this.ui.updateSpectatorCount(this.spectatorCount);
        }
        
        // Show welcome notification
        this.ui.showNotification('👁️ Spectator Mode Active - Press C to change camera', 4000);
    }

    /**
     * Update spectator mode (called every frame)
     */
    update(deltaTime) {
        // Update camera
        this.camera.update(deltaTime);
        
        // Update UI with followed player name
        if (this.camera.getMode() === 'follow') {
            const playerName = this.camera.getFollowedPlayerName();
            if (playerName) {
                this.ui.updateFollowedPlayer(playerName);
            }
        }
    }

    /**
     * Handle server messages specific to spectators
     */
    handleServerMessage(data) {
        switch (data.type) {
            case 'spectatorInit':
                // Already handled in init, but update if needed
                if (data.spectatorData) {
                    this.spectatorCount = data.spectatorData.spectatorCount || 0;
                    this.ui.updateSpectatorCount(this.spectatorCount);
                }
                break;
                
            case 'spectatorCount':
                // Update spectator count if server sends updates
                this.spectatorCount = data.count || 0;
                this.ui.updateSpectatorCount(this.spectatorCount);
                break;
        }
    }

    /**
     * Cycle through camera modes
     */
    cycleCameraMode() {
        const modes = ['free', 'follow'];
        const currentMode = this.camera.getMode();
        const currentIndex = modes.indexOf(currentMode);
        const nextMode = modes[(currentIndex + 1) % modes.length];
        
        this.camera.setMode(nextMode);
        this.ui.updateCameraMode(nextMode);
        
        // Show notification
        const modeNames = {
            'free': 'Free Camera (WASD to move)',
            'follow': 'Follow Player (E to cycle)'
        };
        this.ui.showNotification(`Camera: ${modeNames[nextMode]}`, 2000);
    }

    /**
     * Cycle to next player in follow mode
     */
    cycleFollowedPlayer() {
        this.camera.cycleToNextPlayer();
        
        const playerName = this.camera.getFollowedPlayerName();
        if (playerName) {
            this.ui.updateFollowedPlayer(playerName);
            this.ui.showNotification(`Following: ${playerName}`, 2000);
        }
    }

    /**
     * Follow specific player
     */
    followPlayer(playerId) {
        this.camera.followPlayer(playerId);
        this.ui.updateCameraMode('follow');
        
        const playerName = this.camera.getFollowedPlayerName();
        if (playerName) {
            this.ui.updateFollowedPlayer(playerName);
            this.ui.showNotification(`Following: ${playerName}`, 2000);
        }
    }

    /**
     * Cleanup spectator mode
     */
    destroy() {
        this.input.destroy();
        this.ui.destroy();
    }
}


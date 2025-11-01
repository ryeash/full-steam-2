/**
 * SpectatorInput - Handles keyboard and mouse input for spectator mode
 * Manages camera controls, player following, and UI interactions
 */
class SpectatorInput {
    constructor(spectatorMode) {
        this.spectatorMode = spectatorMode;
        this.engine = spectatorMode.engine;
        this.eventHandlers = [];
    }

    /**
     * Setup all input controls
     */
    setupControls() {
        // Setup keyboard controls
        this.setupKeyboardControls();
        
        // Setup button click handlers
        this.setupButtonControls();
    }

    /**
     * Setup keyboard shortcuts
     */
    setupKeyboardControls() {
        const keydownHandler = (e) => {
            // C - Cycle camera mode
            if (e.key === 'c' || e.key === 'C') {
                this.spectatorMode.cycleCameraMode();
                e.preventDefault();
            }
            
            // E - Cycle to next player (in follow mode)
            if (e.key === 'e' || e.key === 'E') {
                if (this.spectatorMode.camera.getMode() === 'follow') {
                    this.spectatorMode.cycleFollowedPlayer();
                }
                e.preventDefault();
            }
            
            // Number keys 1-9 - Jump to specific player
            if (e.key >= '1' && e.key <= '9') {
                const playerIndex = parseInt(e.key) - 1;
                const playerIds = Array.from(this.engine.players.keys());
                if (playerIndex < playerIds.length) {
                    this.spectatorMode.followPlayer(playerIds[playerIndex]);
                }
                e.preventDefault();
            }
            
            // Space - Toggle between free and follow mode
            if (e.key === ' ') {
                const currentMode = this.spectatorMode.camera.getMode();
                if (currentMode === 'free') {
                    this.spectatorMode.camera.setMode('follow');
                    this.spectatorMode.ui.updateCameraMode('follow');
                } else {
                    this.spectatorMode.camera.setMode('free');
                    this.spectatorMode.ui.updateCameraMode('free');
                }
                e.preventDefault();
            }
        };
        
        document.addEventListener('keydown', keydownHandler);
        this.eventHandlers.push({ element: document, type: 'keydown', handler: keydownHandler });
    }

    /**
     * Setup button click handlers
     */
    setupButtonControls() {
        // Change camera button
        const changeCameraBtn = document.getElementById('change-camera-btn');
        if (changeCameraBtn) {
            const handler = () => this.spectatorMode.cycleCameraMode();
            changeCameraBtn.addEventListener('click', handler);
            this.eventHandlers.push({ element: changeCameraBtn, type: 'click', handler });
        }
        
        // Cycle player button
        const cyclePlayerBtn = document.getElementById('cycle-player-btn');
        if (cyclePlayerBtn) {
            const handler = () => this.spectatorMode.cycleFollowedPlayer();
            cyclePlayerBtn.addEventListener('click', handler);
            this.eventHandlers.push({ element: cyclePlayerBtn, type: 'click', handler });
        }
    }

    /**
     * Cleanup event handlers
     */
    destroy() {
        for (const { element, type, handler } of this.eventHandlers) {
            element.removeEventListener(type, handler);
        }
        this.eventHandlers = [];
    }
}


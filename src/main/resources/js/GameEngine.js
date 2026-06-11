class GameEngine {
    constructor() {
        this.app = null;
        this.gameContainer = null;
        this.uiContainer = null;
        this.players = new Map();
        this.projectiles = new Map();
        this.projectileInterpolators = new Map();
        this.obstacles = new Map();
        this.fieldEffects = new Map();
        this.beams = new Map();
        this.utilityEntities = new Map(); // For turrets, nets, mines, defense lasers, workshops, headquarters, power-ups
        this.flags = new Map(); // CTF flags
        this.kothZones = new Map(); // King of the Hill zones
        this.myPlayerId = null;
        this.gameState = null;
        this.websocket = null;
        this.inputManager = null;
        this.camera = null;
        this.worldBounds = { width: 2000, height: 2000 };
        
        // Game settings
        this.zoomLevel = 1.0;
        this.targetZoomLevel = 1.0;
        this.minZoom = 0.5;
        this.maxZoom = 2.0;
        this.zoomSmoothingFactor = 0.05; // Smooth zoom transitions
        
        // Spectator mode
        this.isSpectator = false;
        this.spectatorMode = null; // Will be SpectatorMode instance if spectating
        
        // Store references to event handlers and timeouts for cleanup
        this.eventHandlers = {
            webglContextLost: null,
            webglContextRestored: null,
            resize: null,
            keydown: null,
            keyup: null,
            closeScoreboard: null
        };
        this.pendingTimeouts = [];
        this.tickerCallbacks = new Set();
        this.memoryCleanupInterval = null;
        
        this.init();
    }
    
    async init() {
        try {
            this.updateLoadingProgress(10, "Creating PixiJS application...");
            await this.initPixiApp();
            
            this.updateLoadingProgress(20, "Setting up camera system...");
            this.setupCamera();
            
            this.updateLoadingProgress(30, "Initializing input system...");
            this.setupInput();

            this.updateLoadingProgress(40, "Setting up UI...");
            this.setupUI();
            this.createConsolidatedHUD();
            this.createRoundTimer();
            
            this.updateLoadingProgress(60, "Loading assets...");
            await this.loadAssets();
            
            this.updateLoadingProgress(80, "Connecting to server...");
            await this.connectToServer();
            
            this.updateLoadingProgress(100, "Ready!");
            this.hideLoadingScreen();
            
            // Set up periodic memory cleanup (every 60 seconds)
            this.memoryCleanupInterval = setInterval(() => {
                this.performMemoryCleanup();
            }, 60000);
            
        } catch (error) {
            console.error('Game initialization failed:', error);
            this.updateLoadingProgress(0, `Error: ${error.message}`);
        }
    }
    
    async initPixiApp() {
        this.app = new PIXI.Application();
        
        await this.app.init({
            width: window.innerWidth,
            height: window.innerHeight,
            backgroundColor: 0x1a1a1a, // Dark grey for better ordinance visibility
            antialias: true,
            resolution: window.devicePixelRatio || 1,
            autoDensity: true
        });

        document.getElementById('pixi-container').appendChild(this.app.canvas);

        // Handle WebGL context loss - store handlers for cleanup
        this.eventHandlers.webglContextLost = (event) => {
            console.warn('WebGL context lost');
            event.preventDefault();
            this.handleWebGLContextLost();
        };
        // Access canvas directly - v8 compatible with both WebGL and WebGPU
        this.app.canvas.addEventListener('webglcontextlost', this.eventHandlers.webglContextLost);

        this.eventHandlers.webglContextRestored = () => {
            console.log('WebGL context restored');
            this.handleWebGLContextRestored();
        };
        this.app.canvas.addEventListener('webglcontextrestored', this.eventHandlers.webglContextRestored);

        // Set up interpolation ticker for smooth movement - store reference for cleanup
        const interpolationCallback = (ticker) => {
            const deltaTime = ticker.deltaTime;
            const dt = deltaTime / 60.0; // Convert to seconds
            
            // Update all projectile interpolators every frame
            this.projectileInterpolators.forEach(interpolator => {
                interpolator.update(deltaTime);
            });
            
            // Animate plasma effects and extend projectile trails. Both ride the
            // interpolated container position, so they must run per render frame
            // (after the interpolators above have moved the containers).
            this.projectiles.forEach(projectileContainer => {
                if (projectileContainer.isPlasma) {
                    this.animatePlasmaEffects(projectileContainer, deltaTime);
                }
                if (projectileContainer.trail) {
                    this.updateProjectileTrail(projectileContainer);
                }
            });
            
            // Smoothly update workshop progress bars
            this.utilityEntities.forEach((container, entityId) => {
                if (container.progressBars) {
                    container.progressBars.forEach((progressBar) => {
                        this.updateProgressBarAnimation(progressBar, deltaTime);
                    });
                }
            });
        };
        this.addTickerCallback(interpolationCallback);

        // Create main containers
        this.backgroundContainer = new PIXI.Container();
        this.gameContainer = new PIXI.Container();
        this.nameContainer = new PIXI.Container(); // Separate container for name labels
        this.uiContainer = new PIXI.Container();

        // Flip Y-axis to match dyn4j physics coordinate system (Y-up)
        // This eliminates the need for coordinate conversions between physics and rendering
        this.gameContainer.scale.y = -1;
        this.nameContainer.scale.y = -1; // Also flip nameContainer to match

        // Set up proper z-ordering
        this.backgroundContainer.zIndex = 0;
        this.gameContainer.zIndex = 1;
        this.gameContainer.sortableChildren = true
        this.nameContainer.zIndex = 50; // Above game objects but below UI
        this.uiContainer.zIndex = 100;

        this.app.stage.addChild(this.backgroundContainer);
        this.app.stage.addChild(this.gameContainer);
        this.app.stage.addChild(this.nameContainer);
        this.app.stage.addChild(this.uiContainer);

        // Enable sorting for proper z-index handling
        this.app.stage.sortableChildren = true;

        // All input is handled via DOM listeners (keyboard/gamepad/HTML buttons);
        // nothing uses Pixi pointer events. Disabling the event system stops the
        // renderer from walking the whole scene graph for hit-testing on every
        // pointer move. (PixiJS perf guide: "Event Handling".)
        this.app.stage.eventMode = 'none';
        this.app.stage.interactiveChildren = false;

        // Handle window resize - store handler for cleanup
        this.eventHandlers.resize = () => {
            this.handleResize();
            this.updateRoundTimerPosition();
        };
        window.addEventListener('resize', this.eventHandlers.resize);
    }
    
    /**
     * Add a ticker callback with proper tracking for cleanup
     */
    addTickerCallback(callback) {
        this.tickerCallbacks.add(callback);
        this.app.ticker.add(callback);
    }
    
    /**
     * Remove a ticker callback and stop tracking it
     */
    removeTickerCallback(callback) {
        this.tickerCallbacks.delete(callback);
        this.app.ticker.remove(callback);
    }
    
    /**
     * Create consolidated HUD in top-left corner.
     */
    createConsolidatedHUD() {
        // Create main HUD container
        this.hudContainer = new PIXI.Container();
        this.hudContainer.zIndex = 200; // Above everything else
        
        // HUD background (smaller without health section)
        const hudBg = new PIXI.Graphics();
        hudBg.roundRect(10, 10, 280, 170, 8).fill({ color: 0x000000, alpha: 0.7 });
        hudBg.roundRect(10, 10, 280, 170, 8).stroke({ width: 2, color: 0x444444, alpha: 0.8 });
        this.hudContainer.addChild(hudBg);
        
        // Player info (bottom portion) - minimap will be created after world bounds are received
        this.createHUDPlayerInfo();
        
        this.uiContainer.addChild(this.hudContainer);
    }
    
    /**
     * Create round timer display in top center of screen.
     */
    createRoundTimer() {
        this.roundTimerContainer = new PIXI.Container();
        this.roundTimerContainer.zIndex = 200;
        this.roundTimerContainer.visible = false; // Hidden by default, shown when rounds are enabled or team mode is active
        
        // Wider background to accommodate team scores
        const bg = new PIXI.Graphics();
        bg.roundRect(0, 0, 400, 60, 8).fill({ color: 0x000000, alpha: 0.8 });
        bg.roundRect(0, 0, 400, 60, 8).stroke({ width: 2, color: 0xffaa00, alpha: 0.9 });
        this.roundTimerContainer.addChild(bg);
        this.roundTimerBackground = bg;
        
        // Round number text (center)
        this.roundNumberText = new PIXI.Text('ROUND 1', {
            fontSize: 14,
            fill: 0xffaa00,
            fontWeight: 'bold',
            align: 'center'
        });
        this.roundNumberText.anchor.set(0.5, 0);
        this.roundNumberText.position.set(200, 8);
        this.roundTimerContainer.addChild(this.roundNumberText);
        
        // Timer text (MM:SS) (center)
        this.roundTimerText = new PIXI.Text('05:00', {
            fontSize: 24,
            fill: 0xffffff,
            fontWeight: 'bold',
            align: 'center'
        });
        this.roundTimerText.anchor.set(0.5, 0);
        this.roundTimerText.position.set(200, 28);
        this.roundTimerContainer.addChild(this.roundTimerText);
        
        // Create team score containers (will be populated dynamically)
        this.teamScoreContainers = new Map();
        
        this.uiContainer.addChild(this.roundTimerContainer);
        
        // Position at top center of screen
        this.updateRoundTimerPosition();
    }
    
    /**
     * Update round timer position based on screen size.
     */
    updateRoundTimerPosition() {
        if (!this.roundTimerContainer) return;
        this.roundTimerContainer.position.set(
            (this.app.screen.width / 2) - 200, // Center horizontally (wider now)
            10 // Top of screen with padding
        );
    }
    
    /**
     * Update round timer display with current round state and team scores.
     */
    updateRoundTimer(roundData) {
        if (!this.roundTimerContainer) return;
        
        const hasTeams = this.teamCount > 0;
        const hasTeamScores = roundData.teamScores && Object.keys(roundData.teamScores).length > 0;
        
        // Show if rounds are enabled OR if we have team scores to display
        const shouldShow = roundData.roundEnabled || (hasTeams && hasTeamScores);
        
        if (!shouldShow) {
            this.roundTimerContainer.visible = false;
            return;
        }
        
        this.roundTimerContainer.visible = true;
        
        // Update round number and timer (if rounds are enabled)
        if (roundData.roundEnabled) {
            this.roundNumberText.visible = true;
            this.roundTimerText.visible = true;
            
            // Update round number
            this.roundNumberText.text = `ROUND ${roundData.currentRound}`;
            
            // Update timer based on game state
            let timeRemaining;
            let timerColor;
            
            if (roundData.gameState === 'PLAYING') {
                timeRemaining = roundData.roundTimeRemaining;
                timerColor = timeRemaining <= 30 ? 0xff4444 : 0xffffff; // Red when under 30 seconds
            } else if (roundData.gameState === 'REST_PERIOD') {
                timeRemaining = roundData.restTimeRemaining;
                timerColor = 0xffaa00; // Orange during rest
                this.roundNumberText.text = 'REST PERIOD';
            } else {
                timeRemaining = 0;
                timerColor = 0xffffff;
            }
            
            // Format as MM:SS
            const minutes = Math.floor(Math.max(0, timeRemaining) / 60);
            const seconds = Math.floor(Math.max(0, timeRemaining) % 60);
            this.roundTimerText.text = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
            this.setTextFill(this.roundTimerText, timerColor);
        } else {
            // Hide round/timer info if rounds are disabled
            this.roundNumberText.visible = false;
            this.roundTimerText.visible = false;
        }
        
        // Update team scores
        this.updateTeamScores(roundData);
    }
    
    /**
     * Update team score displays in the round timer container.
     */
    updateTeamScores(roundData) {
        if (!this.roundTimerContainer || !roundData.teamScores) return;
        
        const teamScores = roundData.teamScores;
        const teams = Object.keys(teamScores).map(t => parseInt(t)).sort((a, b) => a - b);
        
        // Clear old team score containers
        for (let [teamId, container] of this.teamScoreContainers) {
            if (!teams.includes(teamId)) {
                // Properly destroy the container and its children to prevent memory leak
                // Note: colorBar and scoreText are children of container, so they will be
                // destroyed automatically with { children: true }. No need to destroy them separately.
                if (container.colorBar && container.colorBar.clear) {
                    container.colorBar.clear();
                }
                this.roundTimerContainer.removeChild(container);
                // children:true + default texture frees the scoreText Text's texture.
                container.destroy({ children: true, context: true });
                this.teamScoreContainers.delete(teamId);
            }
        }
        
        // Fixed slot positions: 2 on left, 2 on right
        // Each team score display is ~56px wide (6px bar + 50px content)
        const slotPositions = [
            { x: 15, y: 5 },       // Left slot 1
            { x: 75, y: 5 },       // Left slot 2 (adjacent, 60px spacing)
            { x: 330, y: 5 },      // Right slot 1
            { x: 270, y: 5 }       // Right slot 2 (left of slot 1, for 4 teams)
        ];
        
        // Create or update team score displays
        teams.forEach((teamId, index) => {
            // Only display up to 4 teams (2 left, 2 right)
            if (index >= 4) return;
            
            let container = this.teamScoreContainers.get(teamId);
            
            if (!container) {
                // Create new team score container
                container = new PIXI.Container();
                
                // Team color indicator
                const colorBar = new PIXI.Graphics();
                colorBar.roundRect(0, 0, 6, 48, 3).fill(this.getTeamColor(teamId));
                colorBar.position.set(0, 2);
                container.addChild(colorBar);
                container.colorBar = colorBar;
                
                // Team label
                const teamLabel = new PIXI.Text(`T${teamId}`, {
                    fontSize: 10,
                    fill: this.getTeamColor(teamId),
                    fontWeight: 'bold',
                    align: 'center'
                });
                teamLabel.anchor.set(0.5, 0);
                teamLabel.position.set(28, 8);
                container.addChild(teamLabel);
                
                // Score text
                const scoreText = new PIXI.Text('0', {
                    fontSize: 20,
                    fill: 0xffffff,
                    fontWeight: 'bold',
                    align: 'center'
                });
                scoreText.anchor.set(0.5, 0);
                scoreText.position.set(28, 26);
                container.addChild(scoreText);
                container.scoreText = scoreText;
                
                this.roundTimerContainer.addChild(container);
                this.teamScoreContainers.set(teamId, container);
            }
            
            // Update score
            const score = teamScores[teamId] || 0;
            container.scoreText.text = score.toString();
            
            // Position teams in fixed slots
            // Slot assignment based on team count and index:
            // 2 teams: team 0 -> left, team 1 -> right (slots 0, 2)
            // 3 teams: team 0 -> left, team 1 -> left adjacent, team 2 -> right (slots 0, 1, 2)
            // 4 teams: team 0,1 -> left, team 2,3 -> right (slots 0, 1, 2, 3)
            
            let slotIndex;
            if (teams.length === 2) {
                slotIndex = index === 0 ? 0 : 2; // First team left, second team right
            } else if (teams.length === 3) {
                slotIndex = index; // 0,1 on left side, 2 on right
            } else {
                slotIndex = index; // All 4 slots used
            }
            
            container.position.set(slotPositions[slotIndex].x, slotPositions[slotIndex].y);
        });
    }
    
    /**
     * Create minimap section of HUD with correct aspect ratio.
     */
    createHUDMinimap() {
        // Calculate minimap dimensions based on world aspect ratio
        const worldAspectRatio = this.worldBounds.width / this.worldBounds.height;
        const maxMinimapSize = 120; // Maximum size for either dimension
        
        let minimapWidth, minimapHeight;
        
        if (worldAspectRatio > 1) {
            // World is wider than tall
            minimapWidth = maxMinimapSize;
            minimapHeight = maxMinimapSize / worldAspectRatio;
        } else {
            // World is taller than wide (or square)
            minimapHeight = maxMinimapSize;
            minimapWidth = maxMinimapSize * worldAspectRatio;
        }
        
        // Ensure minimum size for usability
        minimapWidth = Math.max(minimapWidth, 60);
        minimapHeight = Math.max(minimapHeight, 60);
        
        // Minimap container
        const minimapContainer = new PIXI.Container();
        minimapContainer.position.set(20, 20);
        
        // Minimap background
        const minimapBg = new PIXI.Graphics();
        minimapBg.roundRect(0, 0, minimapWidth, minimapHeight, 4).fill({ color: 0x1a3d1f, alpha: 0.8 });
        minimapBg.roundRect(0, 0, minimapWidth, minimapHeight, 4).stroke({ width: 1, color: 0x2ecc71, alpha: 0.6 });
        minimapContainer.addChild(minimapBg);
        
        // Minimap title
        const minimapTitle = new PIXI.Text('MAP', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        minimapTitle.position.set(2, 2);
        minimapContainer.addChild(minimapTitle);
        
        // Store references for updates
        this.hudMinimap = minimapContainer;
        this.hudMinimapBg = minimapBg;
        this.minimapWidth = minimapWidth;
        this.minimapHeight = minimapHeight;
        
        this.hudContainer.addChild(minimapContainer);
    }
    
    /**
     * Update HUD layout after minimap is created with proper dimensions.
     */
    updateHUDLayout() {
        if (!this.hudMinimap || !this.minimapHeight) return;
        
        // Adjust HUD background size to accommodate the minimap
        const hudBg = this.hudContainer.children[0]; // First child is the background
        if (hudBg) {
            const totalWidth = Math.max(280, this.minimapWidth + 40); // 20px margins on each side
            const totalHeight = this.minimapHeight + 90; // Minimap + title + player info section
            
            hudBg.clear();
            hudBg.roundRect(10, 10, totalWidth, totalHeight, 8).fill({ color: 0x000000, alpha: 0.7 });
            hudBg.roundRect(10, 10, totalWidth, totalHeight, 8).stroke({ width: 2, color: 0x444444, alpha: 0.8 });
        }
        
        // Adjust player info position to be below the minimap
        const playerInfoContainer = this.hudContainer.children.find(child => 
            child.children && child.children.some(grandchild => 
                grandchild.text && grandchild.text === 'WEAPON'
            )
        );
        
        if (playerInfoContainer) {
            playerInfoContainer.position.set(20, 30 + this.minimapHeight + 10); // Below minimap with padding
        }
    }
    
    /**
     * Create player info section of HUD.
     */
    createHUDPlayerInfo() {
        const infoContainer = new PIXI.Container();
        infoContainer.position.set(20, 150);
        
        // Weapon section
        const weaponLabel = new PIXI.Text('WEAPON', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        weaponLabel.position.set(0, 0);
        infoContainer.addChild(weaponLabel);
        
        const weaponText = new PIXI.Text('Primary', {
            fontSize: 9,
            fill: 0xffffff
        });
        weaponText.position.set(50, 0);
        infoContainer.addChild(weaponText);
        
        // Ammo section
        const ammoLabel = new PIXI.Text('AMMO', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        ammoLabel.position.set(0, 15);
        infoContainer.addChild(ammoLabel);
        
        const ammoText = new PIXI.Text('30/30', {
            fontSize: 9,
            fill: 0xffffff
        });
        ammoText.position.set(40, 15);
        infoContainer.addChild(ammoText);
        
        // Reload indicator
        const reloadText = new PIXI.Text('RELOADING...', {
            fontSize: 9,
            fill: 0xff4444,
            fontWeight: 'bold'
        });
        reloadText.position.set(90, 15);
        reloadText.visible = false;
        infoContainer.addChild(reloadText);
        
        // Team indicator
        const teamLabel = new PIXI.Text('TEAM', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        teamLabel.position.set(150, 0);
        infoContainer.addChild(teamLabel);
        
        const teamText = new PIXI.Text('1', {
            fontSize: 12,
            fill: 0xff4444,
            fontWeight: 'bold'
        });
        teamText.position.set(185, 0);
        infoContainer.addChild(teamText);
        
        // Input source indicator
        const inputLabel = new PIXI.Text('INPUT', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        inputLabel.position.set(150, 15);
        infoContainer.addChild(inputLabel);
        
        const inputText = new PIXI.Text('Keyboard', {
            fontSize: 9,
            fill: 0xffffff
        });
        inputText.position.set(195, 15);
        infoContainer.addChild(inputText);
        
        // Lives indicator (for stock mode)
        const livesLabel = new PIXI.Text('LIVES', {
            fontSize: 10,
            fill: 0xffffff,
            fontWeight: 'bold'
        });
        livesLabel.position.set(0, 30);
        infoContainer.addChild(livesLabel);
        
        const livesText = new PIXI.Text('∞', {
            fontSize: 12,
            fill: 0x44ff44,
            fontWeight: 'bold'
        });
        livesText.position.set(40, 30);
        infoContainer.addChild(livesText);
        
        // Store references for updates (removed health references)
        this.hudWeaponText = weaponText;
        this.hudAmmoText = ammoText;
        this.hudReloadText = reloadText;
        this.hudTeamText = teamText;
        this.hudInputText = inputText;
        this.hudLivesLabel = livesLabel;
        this.hudLivesText = livesText;
        
        this.hudContainer.addChild(infoContainer);
    }
    
    /**
     * Get the color for a player based on their team.
     * Uses easily distinguishable colors for team-based gameplay.
     */
    /**
     * Color utility methods for consistent color handling
     */
    
    /**
     * Get the base color for a team.
     */
    getTeamColor(teamNumber) {
        switch (teamNumber) {
            case 0: return 0x808080; // Gray for FFA/no team
            case 1: return 0x4CAF50; // Green
            case 2: return 0xF44336; // Red
            case 3: return 0x2196F3; // Blue
            case 4: return 0xFF9800; // Orange
            default: return 0x808080; // Default gray
        }
    }
    
    /**
     * Get team color as CSS color string for HTML elements.
     */
    getTeamColorCSS(teamNumber) {
        const color = this.getTeamColor(teamNumber);
        const r = (color >> 16) & 0xFF;
        const g = (color >> 8) & 0xFF;
        const b = color & 0xFF;
        return `rgb(${r}, ${g}, ${b})`;
    }
    
    /**
     * Get player color with special highlighting for current player.
     */
    getPlayerColor(playerData) {
        const baseColor = this.getTeamColor(playerData.team || 0);
        
        // Special highlight for the current player
        if (playerData.id === this.myPlayerId) {
            return this.brightenColor(baseColor);
        }
        
        return baseColor;
    }
    
    /**
     * Brighten a color by adding 60 to each RGB component.
     */
    brightenColor(color) {
        const r = Math.min(255, ((color >> 16) & 0xFF) + 60);
        const g = Math.min(255, ((color >> 8) & 0xFF) + 60);
        const b = Math.min(255, (color & 0xFF) + 60);
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Darken a color by subtracting 40 from each RGB component.
     */
    darkenColor(color) {
        const r = Math.max(0, ((color >> 16) & 0xFF) - 40);
        const g = Math.max(0, ((color >> 8) & 0xFF) - 40);
        const b = Math.max(0, (color & 0xFF) - 40);
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Blend two colors together using a factor (0.0 = color1, 1.0 = color2).
     */
    blendColors(color1, color2, factor) {
        const r1 = (color1 >> 16) & 0xFF;
        const g1 = (color1 >> 8) & 0xFF;
        const b1 = color1 & 0xFF;
        
        const r2 = (color2 >> 16) & 0xFF;
        const g2 = (color2 >> 8) & 0xFF;
        const b2 = color2 & 0xFF;
        
        const r = Math.round(r1 * (1 - factor) + r2 * factor);
        const g = Math.round(g1 * (1 - factor) + g2 * factor);
        const b = Math.round(b1 * (1 - factor) + b2 * factor);
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Get the current player's data for gamepad aiming
     */
    getMyPlayer() {
        if (!this.myPlayerId) return null;
        const sprite = this.players.get(this.myPlayerId);
        return sprite ? sprite.playerData : null;
    }
    
    setupCamera() {
        this.camera = {
            x: 0,
            y: 0,
            targetX: 0,
            targetY: 0,
            smoothing: 0.1
        };
        
        // Start game loop - store reference for cleanup
        const gameLoopCallback = () => this.gameLoop();
        this.addTickerCallback(gameLoopCallback);
    }
    
    setupInput() {
        // Make gameEngine globally accessible for InputManager
        window.gameEngine = this;
        
        this.inputManager = new InputManager();
        
        // Spectators don't send input to server
        if (!this.isSpectator) {
            this.inputManager.onInputChange = (input) => {
                this.sendPlayerInput(input);
            };
        }
        
        // Store reference for HUD updates
        this.inputManager.gameEngine = this;
    }
    
    setupUI() {
        // Setup event listeners - store handlers for cleanup
        this.eventHandlers.closeScoreboard = () => {
            document.getElementById('scoreboard').style.display = 'none';
            this.scoreboardVisible = false; // Update state for gamepad users
        };
        document.getElementById('close-scoreboard')?.addEventListener('click', this.eventHandlers.closeScoreboard);
        
        // Tab to show scoreboard
        this.eventHandlers.keydown = (e) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                document.getElementById('scoreboard').style.display = 'block';
                this.scoreboardVisible = true; // Update state for gamepad sync
            }
        };
        document.addEventListener('keydown', this.eventHandlers.keydown);
        
        this.eventHandlers.keyup = (e) => {
            if (e.key === 'Tab') {
                document.getElementById('scoreboard').style.display = 'none';
                this.scoreboardVisible = false; // Update state for gamepad sync
            }
        };
        document.addEventListener('keyup', this.eventHandlers.keyup);
        
        // Store reference for gamepad scoreboard control
        this.scoreboardVisible = false;
    }

    async loadAssets() {
        // Create clean 2D top-down assets
        
        // Player sprite - clean circular design
        const playerGraphics = new PIXI.Graphics();
        playerGraphics.circle(0, 0, 20).fill(0x8c8c8c);
        
        // Direction indicator (weapon/facing)
        playerGraphics.poly([15, 0, 25, -5, 25, 5]).fill(0xffffff);
        
        // Generate texture - PIXI will auto-calculate bounds
        // The texture will include the full visual (circle + triangle)
        this.playerTexture = this.app.renderer.generateTexture(playerGraphics);
        playerGraphics.destroy({ context: true }); // Clean up after generating texture
        
        // Projectile - simple bullet
        const projectileGraphics = new PIXI.Graphics();
        projectileGraphics.circle(0, 0, 3).fill(0xf39c12);
        this.projectileTexture = this.app.renderer.generateTexture(projectileGraphics);
        projectileGraphics.destroy({ context: true }); // Clean up graphics after generating texture

        // Shared soft-glow circle. This is the single reusable base texture for
        // ALL field effects, power-up auras, and particle-style visuals. Instead
        // of rebuilding per-instance Graphics geometry every frame, we render a
        // tinted/scaled Sprite of this texture, which lets PIXI batch them into a
        // handful of draw calls with zero per-frame geometry churn.
        this.glowTextureRadius = 64;
        const glowGraphics = new PIXI.Graphics();
        const glowSteps = 2;
        for (let i = glowSteps; i >= 1; i--) {
            const r = this.glowTextureRadius * (i / glowSteps);
            const a = Math.pow(1 - (i - 1) / glowSteps, 2) * 0.18;
            glowGraphics.circle(0, 0, r).fill({ color: 0xffffff, alpha: a });
        }
        glowGraphics.circle(0, 0, this.glowTextureRadius * 0.35).fill({ color: 0xffffff, alpha: 0.85 });
        this.glowTexture = this.app.renderer.generateTexture(glowGraphics);
        glowGraphics.destroy({ context: true });

        // Field-effect disc. Unlike the glow texture (small bright core + wide
        // faint halo), this fills its full extent: dense toward the centre,
        // fading only at the very rim. Field effects scale this so the rim lands
        // exactly on the gameplay radius — the render never exceeds the physics
        // radius, keeping the visual boundary honest for players.
        this.fieldTextureRadius = 64;
        const fieldGraphics = new PIXI.Graphics();
        const fieldSteps = 16;
        for (let i = fieldSteps; i >= 1; i--) {
            const r = this.fieldTextureRadius * (i / fieldSteps);
            // Painter's stacking concentrates opacity toward the centre; soften
            // only the outermost ring so the disc reads as filled with a soft edge.
            const rimFade = i >= fieldSteps ? 0.4 : 1.0;
            fieldGraphics.circle(0, 0, r).fill({ color: 0xffffff, alpha: 0.11 * rimFade });
        }
        this.fieldTexture = this.app.renderer.generateTexture(fieldGraphics);
        fieldGraphics.destroy({ context: true });
        
        // Death marker - tombstone/X
        const deathGraphics = new PIXI.Graphics();
        // Add a circle background first
        deathGraphics.circle(0, 0, 18).fill({ color: 0x000000, alpha: 0.3 });
        deathGraphics.circle(0, 0, 18).stroke({ width: 2, color: 0x444444, alpha: 0.8 });
        // Draw a red X
        deathGraphics.moveTo(-15, -15);
        deathGraphics.lineTo(15, 15);
        deathGraphics.moveTo(15, -15);
        deathGraphics.lineTo(-15, 15);
        deathGraphics.stroke({ width: 4, color: 0xff4444 });
        this.deathTexture = this.app.renderer.generateTexture(deathGraphics);
        deathGraphics.destroy({ context: true }); // Clean up graphics after generating texture
    }
    
    async connectToServer() {
        const params = new URLSearchParams(window.location.search);
        const gameId = params.get('gameId') || 'default';
        const spectate = params.get('spectate') === 'true';

        // Set spectator flag (preserved for SpectatorMode)
        this.isSpectator = spectate;
        // Indicates the player has not yet spawned. Becomes false on initialState.
        this.isAwaitingSpawn = !spectate;
        // Was set when the server told us our lobby slot was freed (timeout).
        // After this, "Ready" actually retries as SPECTATOR->PLAYING.
        this.wasLobbyDowngraded = false;
        // Suppresses the generic connection-lost overlay when the server is
        // closing the socket on a typed joinRejected.
        this.expectingSocketClose = false;

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/game/${gameId}?spectate=${spectate}`;

        return new Promise((resolve, reject) => {
            this.websocket = new WebSocket(wsUrl);

            this.websocket.onopen = () => {
                resolve();
            };

            this.websocket.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    this.handleServerMessage(data);
                } catch (error) {
                    console.error('Error parsing server message:', error);
                }
            };

            this.websocket.onclose = () => {
                if (!this.expectingSocketClose) {
                    this.showConnectionError();
                }
            };

            this.websocket.onerror = (error) => {
                reject(error);
            };

            this.safeSetTimeout(() => {
                if (this.websocket.readyState !== WebSocket.OPEN) {
                    reject(new Error('Connection timeout'));
                }
            }, 10000);
        });
    }
    
    gameLoop() {
        const deltaTime = this.app.ticker.deltaMS / 1000;
        
        // Update spectator mode if active (spectator handles its own camera)
        if (this.spectatorMode) {
            this.spectatorMode.update(deltaTime);
        } else {
            // Normal player camera logic
            if (this.myPlayerId && this.players.has(this.myPlayerId)) {
                const myPlayer = this.players.get(this.myPlayerId);
                if (myPlayer && myPlayer.playerData) {
                    this.camera.targetX = myPlayer.playerData.x;
                    this.camera.targetY = myPlayer.playerData.y; // No inversion needed - coordinates match now!
                    
                    // Update target zoom based on weapon range
                    this.updateZoomForWeaponRange(myPlayer.playerData);
                }
            } else {
                // Default camera position if no player yet
                this.camera.targetX = 0;
                this.camera.targetY = 0;
            }
            
            this.camera.x += (this.camera.targetX - this.camera.x) * this.camera.smoothing;
            this.camera.y += (this.camera.targetY - this.camera.y) * this.camera.smoothing;
        }
        
        // Smoothly interpolate zoom level
        this.zoomLevel += (this.targetZoomLevel - this.zoomLevel) * this.zoomSmoothingFactor;
        this.updateCameraTransform();
        this.updateMinimap();
    }
    
    updateZoomForWeaponRange(playerData) {
        // Only adjust zoom if weaponRange is available
        if (!playerData.weaponRange) {
            this.targetZoomLevel = 1.0;
            return;
        }
        
        // Weapon range values from WeaponAttribute.java:
        // Min range: 150 + (-3 * 35) = 45 units
        // Max range: 150 + (35 * 35) = 1375 units
        // Default/mid range: ~400-600 units
        
        const weaponRange = playerData.weaponRange;
        
        // Map weapon range to zoom level
        // Short range weapons (< 300): zoom in more (1.2 - 1.5x)
        // Medium range weapons (300-700): normal zoom (0.9 - 1.2x)
        // Long range weapons (> 700): zoom out more (0.6 - 0.9x)
        
        let calculatedZoom;
        if (weaponRange < 300) {
            // Short range: zoom in (1.5 at range 45, 1.2 at range 300)
            calculatedZoom = 1.5 - ((weaponRange - 45) / 255) * 0.3;
        } else if (weaponRange < 700) {
            // Medium range: normal zoom (1.2 at range 300, 0.9 at range 700)
            calculatedZoom = 1.2 - ((weaponRange - 300) / 400) * 0.3;
        } else {
            // Long range: zoom out (0.9 at range 700, 0.6 at range 1375)
            calculatedZoom = 0.9 - ((weaponRange - 700) / 675) * 0.3;
        }
        
        // Clamp to min/max zoom bounds
        this.targetZoomLevel = Math.max(this.minZoom, Math.min(this.maxZoom, calculatedZoom));
    }
    
    updateCameraTransform() {
        const centerX = this.app.screen.width / 2;
        const centerY = this.app.screen.height / 2;
        
        // Apply camera transform to game and name containers (both Y-flipped)
        [this.gameContainer, this.nameContainer].forEach(container => {
            container.position.set(centerX, centerY);
            container.scale.set(this.zoomLevel, -this.zoomLevel); // Preserve Y-flip
            container.pivot.x = this.camera.x;
            container.pivot.y = this.camera.y;
        });
        
        // Background container needs same transform but also Y-flipped to match world coordinates
        this.backgroundContainer.position.set(centerX, centerY);
        this.backgroundContainer.scale.set(this.zoomLevel, -this.zoomLevel); // Also Y-flip background
        this.backgroundContainer.pivot.x = this.camera.x;
        this.backgroundContainer.pivot.y = this.camera.y;
    }
    
    // Coordinate conversion functions removed - no longer needed!
    // The gameContainer now uses Y-up coordinates matching dyn4j physics
    
    /**
     * Wrapper for setTimeout that tracks timeouts for cleanup
     */
    safeSetTimeout(callback, delay) {
        const timeoutId = setTimeout(() => {
            // Remove from tracking when it fires
            const index = this.pendingTimeouts.indexOf(timeoutId);
            if (index > -1) {
                this.pendingTimeouts.splice(index, 1);
            }
            callback();
        }, delay);
        this.pendingTimeouts.push(timeoutId);
        return timeoutId;
    }
    
    handleServerMessage(data) {
        // Handle spectator-specific messages
        if (data.type === 'spectatorInit') {
            this.handleSpectatorInit(data);
            return;
        }

        // Delegate to spectator mode if active
        if (this.spectatorMode) {
            this.spectatorMode.handleServerMessage(data);
        }

        switch (data.type) {
            case 'lobbyInit':
                this.handleLobbyInit(data);
                break;
            case 'lobbyTimeout':
                this.handleLobbyTimeout(data);
                break;
            case 'joinRejected':
                this.handleJoinRejected(data);
                break;
            case 'initialState':
                this.handleInitialState(data);
                break;
            case 'gameState':
                this.handleGameState(data);
                break;
            case 'playerKilled':
                this.handlePlayerKilled(data);
                break;
            case 'gameEvent':
                this.handleGameEvent(data);
                break;
            case 'roundEnd':
                this.handleRoundEnd(data);
                break;
            case 'roundStart':
                this.handleRoundStart(data);
                break;
            case 'gameOver':
                this.showGameOverScreen(data);
                break;
        }
    }
    
    handleSpectatorInit(data) {
        // Use the shared world setup so a LOBBY -> SPECTATOR downgrade doesn't
        // duplicate obstacles/terrain/grid that we already drew during lobbyInit.
        // Skip the minimap because the spectator HUD has a full-map overlay.
        this.setupWorldFromInitData(data, /* includeMinimap */ false);

        // If the user joined directly as a spectator (URL flag), close the
        // loadout modal. After a lobby-timeout downgrade, keep it open so the
        // user can still pick a loadout and click Ready to claim a free slot.
        if (!this.wasLobbyDowngraded) {
            this.hideLoadoutModal();
        }

        // Create the spectator mode instance if one isn't already running.
        if (!this.spectatorMode && typeof SpectatorMode !== 'undefined') {
            this.spectatorMode = new SpectatorMode(this);
            this.spectatorMode.init(data);
        }

        // Show game UI
        const gameUi = document.getElementById('game-ui');
        if (gameUi) gameUi.style.display = 'block';
    }
    
    handleInitialState(data) {
        this.myPlayerId = data.playerId;
        // World may already be set up from a prior lobbyInit; setup is idempotent.
        this.setupWorldFromInitData(data);

        this.isAwaitingSpawn = false;
        this.hideLoadoutModal();
    }

    /**
     * Idempotently configure world bounds, terrain, obstacles, grid, team
     * areas, and (optionally) the minimap from an initial-state-style
     * payload. Shared between {@code handleInitialState},
     * {@code handleLobbyInit}, and {@code handleSpectatorInit}.
     *
     * @param data           initial-state payload from the server.
     * @param includeMinimap when false (spectators), the minimap is skipped
     *                       since the spectator HUD has its own full-map view.
     */
    setupWorldFromInitData(data, includeMinimap = true) {
        if (!this._worldSetupDone) {
            this.worldBounds.width = data.worldWidth || 2000;
            this.worldBounds.height = data.worldHeight || 2000;
            this.teamMode = data.teamMode || false;
            this.teamCount = data.teamCount || 0;
            this.teamAreas = data.teamAreas || null;

            if (data.obstacles) {
                data.obstacles.forEach(obstacle => this.createObstacle(obstacle));
            }
            if (this.teamMode && this.teamAreas) {
                this.createTeamSpawnAreas();
            }
            this.createCrosshatchGrid();
            if (includeMinimap) {
                this.createHUDMinimap();
                this.updateHUDLayout();
            }

            this._worldSetupDone = true;
        }
    }

    handleLobbyInit(data) {
        this.setupWorldFromInitData(data);
        // Show the loading screen briefly during initial layout so the canvas
        // doesn't pop up under the modal; the modal itself takes over right after.
        this.showLoadoutModal(data);
    }

    handleLobbyTimeout(data) {
        // Server has soft-downgraded us to spectator. The modal stays visible
        // so the user can still pick a loadout and click Ready when a slot opens.
        this.wasLobbyDowngraded = true;
        this.isAwaitingSpawn = false; // We're effectively a spectator now.
        this.showLoadoutBanner(
            'Your slot was freed for being idle. Press Ready when you want to '
            + 'try to spawn into an available slot.'
        );
        const countdownEl = document.getElementById('lobby-countdown');
        if (countdownEl) countdownEl.textContent = '';
        if (this._lobbyCountdownInterval) {
            clearInterval(this._lobbyCountdownInterval);
            this._lobbyCountdownInterval = null;
        }
    }

    handleJoinRejected(data) {
        // Server explicitly rejected our spawn/join. Show a friendly overlay
        // and stop the generic disconnect screen from masking the reason.
        this.expectingSocketClose = true;
        this.hideLoadoutModal();

        const reason = data && data.reason ? data.reason : 'UNKNOWN';
        const messages = {
            GAME_FULL: 'This game is full. Try a different game or wait for a slot to open.',
            GAME_LOCKED: 'This game has locked late joiners out and cannot accept new players.',
            GAME_ENDED: 'This game has already finished.',
            GAME_NOT_FOUND: "That game doesn't exist anymore. It may have ended."
        };
        const titleEl = document.getElementById('join-rejected-title');
        const msgEl = document.getElementById('join-rejected-message');
        const returnBtn = document.getElementById('join-rejected-return');
        const overlay = document.getElementById('join-rejected');
        if (titleEl) {
            titleEl.textContent = reason === 'GAME_NOT_FOUND' ? 'Game not found' : 'Unable to join';
        }
        if (msgEl) {
            msgEl.textContent = messages[reason] || `Reason: ${reason}`;
        }
        if (returnBtn && !returnBtn._handlerBound) {
            returnBtn._handlerBound = true;
            returnBtn.addEventListener('click', () => {
                window.location.href = '/lobby.html';
            });
        }
        if (overlay) overlay.classList.add('visible');
    }

    showLoadoutModal(data) {
        const modal = document.getElementById('loadout-modal');
        if (!modal) {
            console.warn('Loadout modal markup missing from page');
            return;
        }

        const root = document.getElementById('loadout-customizer-root');
        const readyBtn = document.getElementById('ready-up');

        if (!this.weaponCustomizer && root && typeof WeaponCustomizer !== 'undefined') {
            this.weaponCustomizer = new WeaponCustomizer(root, {
                onValidityChange: (isValid) => {
                    if (readyBtn) readyBtn.disabled = !isValid;
                }
            });
            this.weaponCustomizer.init();
        }

        if (readyBtn && !readyBtn._handlerBound) {
            readyBtn._handlerBound = true;
            readyBtn.addEventListener('click', () => this.submitReadyToSpawn());
        }

        // Populate the name dropdown with the curated list; pre-select the
        // server-assigned random name so the player can just hit Ready.
        this.populateNamePicker(data && data.assignedName);

        // Show the modal and hide the loading screen behind it
        modal.classList.add('visible');
        const loading = document.getElementById('loading-screen');
        if (loading) loading.style.display = 'none';
        const gameUi = document.getElementById('game-ui');
        if (gameUi) gameUi.style.display = 'block';

        // Kick off the lobby countdown if the server told us a timeout
        const timeoutMs = data && typeof data.lobbyTimeoutMs === 'number' ? data.lobbyTimeoutMs : null;
        if (timeoutMs) this.startLobbyCountdown(timeoutMs);
    }

    /**
     * Fetch the curated name list from /api/names, populate #name-select, and
     * pre-select the server-assigned name. Attaches the randomise button handler.
     * Safe to call multiple times — skips re-population when the list is already loaded.
     */
    async populateNamePicker(assignedName) {
        const select = document.getElementById('name-select');
        const randomBtn = document.getElementById('name-randomize');
        if (!select) return;

        // Only fetch once; on subsequent calls just update the selection.
        if (!this._namePicker_loaded) {
            try {
                const resp = await fetch('/api/names');
                const names = await resp.json();
                select.innerHTML = '';
                names.forEach(name => {
                    const opt = document.createElement('option');
                    opt.value = name;
                    opt.textContent = name;
                    select.appendChild(opt);
                });
                this._namePicker_loaded = true;
                this._namePicker_names = names;
            } catch (err) {
                console.error('Failed to load name list', err);
                select.innerHTML = '<option value="">Could not load names</option>';
                return;
            }
        }

        // Priority: previously saved name → server-assigned random name → first in list.
        const saved = localStorage.getItem('fullsteam_playerName');
        if (saved && this._namePicker_names && this._namePicker_names.includes(saved)) {
            select.value = saved;
        } else if (assignedName) {
            select.value = assignedName;
        }

        // Persist any manual selection immediately so it survives page reloads.
        if (!select._changeHandlerBound) {
            select._changeHandlerBound = true;
            select.addEventListener('change', () => {
                if (select.value) localStorage.setItem('fullsteam_playerName', select.value);
            });
        }

        // Randomise button — picks a new entry from the already-loaded list.
        if (randomBtn && !randomBtn._handlerBound) {
            randomBtn._handlerBound = true;
            randomBtn.addEventListener('click', () => {
                const names = this._namePicker_names;
                if (!names || !names.length) return;
                select.value = names[Math.floor(Math.random() * names.length)];
                if (select.value) localStorage.setItem('fullsteam_playerName', select.value);
            });
        }
    }

    hideLoadoutModal() {
        const modal = document.getElementById('loadout-modal');
        if (modal) modal.classList.remove('visible');
        if (this._lobbyCountdownInterval) {
            clearInterval(this._lobbyCountdownInterval);
            this._lobbyCountdownInterval = null;
        }
        const countdownEl = document.getElementById('lobby-countdown');
        if (countdownEl) countdownEl.textContent = '';
    }

    showLoadoutBanner(message) {
        const banner = document.getElementById('loadout-banner');
        if (banner) {
            banner.textContent = message;
            banner.classList.add('visible');
        }
    }

    startLobbyCountdown(timeoutMs) {
        const deadline = Date.now() + timeoutMs;
        const el = document.getElementById('lobby-countdown');
        if (this._lobbyCountdownInterval) {
            clearInterval(this._lobbyCountdownInterval);
        }
        const tick = () => {
            const remaining = Math.max(0, deadline - Date.now());
            if (el) {
                const seconds = Math.ceil(remaining / 1000);
                el.textContent = remaining > 0
                    ? `Slot held for ${seconds}s while customizing`
                    : '';
            }
            if (remaining <= 0 && this._lobbyCountdownInterval) {
                clearInterval(this._lobbyCountdownInterval);
                this._lobbyCountdownInterval = null;
            }
        };
        tick();
        this._lobbyCountdownInterval = setInterval(tick, 1000);
    }

    submitReadyToSpawn() {
        if (!this.weaponCustomizer || !this.websocket
            || this.websocket.readyState !== WebSocket.OPEN) {
            return;
        }
        const config = this.weaponCustomizer.getPlayerConfig();
        const nameSelect = document.getElementById('name-select');
        const message = {
            type: 'readyToSpawn',
            weaponConfig: config.weaponConfig,
            utilityWeapon: config.utilityWeapon,
            playerName: nameSelect ? nameSelect.value : undefined
        };
        this.websocket.send(JSON.stringify(message));

        // Visually indicate the action is in flight; server will reply with
        // `initialState` (success) or `joinRejected` (failure).
        const readyBtn = document.getElementById('ready-up');
        if (readyBtn) {
            readyBtn.disabled = true;
            readyBtn.textContent = 'Spawning...';
            // Restore label if server takes too long (defensive only)
            this.safeSetTimeout(() => {
                if (readyBtn && readyBtn.textContent === 'Spawning...') {
                    readyBtn.textContent = 'Ready';
                    readyBtn.disabled = !(this.weaponCustomizer && this.weaponCustomizer.isValid());
                }
            }, 5000);
        }
    }
    
    handleGameState(data) {
        this.gameState = data;
        
        // Update round timer if rounds are enabled
        if (data.roundEnabled !== undefined) {
            this.updateRoundTimer(data);
        }
        
        if (data.players) {
            const currentPlayerIds = new Set();
            
            data.players.forEach(playerData => {
                currentPlayerIds.add(playerData.id);
                if (this.players.has(playerData.id)) {
                    this.updatePlayer(playerData);
                } else {
                    this.createPlayer(playerData);
                }
            });
            
            for (let [playerId, player] of this.players) {
                if (!currentPlayerIds.has(playerId)) {
                    this.removePlayer(playerId);
                }
            }
            
            // Update scoreboard for all clients (including spectators)
            this.updateScoreboard(data.players);
        }
        
        // Projectiles: cleanup runs unconditionally so an absent field (server
        // omits the key when the list is empty) clears any stale sprites.
        {
            const currentProjectileIds = new Set();
            if (data.projectiles) {
                data.projectiles.forEach(projectileData => {
                    currentProjectileIds.add(projectileData.id);
                    if (this.projectiles.has(projectileData.id)) {
                        this.updateProjectile(projectileData);
                    } else {
                        this.createProjectile(projectileData);
                    }
                });
            }
            for (let [projectileId] of this.projectiles) {
                if (!currentProjectileIds.has(projectileId)) {
                    this.removeProjectile(projectileId);
                }
            }
        }

        // Obstacles are static — delivered once in the init payload and never
        // re-sent in recurring gameState messages.  We still handle the field
        // if it appears (e.g. future reconnect flows) but never rely on its
        // absence to remove obstacles that were set up during initialisation.
        if (data.obstacles) {
            const currentObstacleIds = new Set();
            data.obstacles.forEach(obstacleData => {
                currentObstacleIds.add(obstacleData.id);
                if (this.obstacles.has(obstacleData.id)) {
                    this.updateObstacle(obstacleData);
                } else {
                    this.createObstacle(obstacleData);
                }
            });
            for (let [obstacleId] of this.obstacles) {
                if (!currentObstacleIds.has(obstacleId)) {
                    this.removeObstacle(obstacleId);
                }
            }
        }

        // Field effects: same absent-means-empty pattern as projectiles
        {
            const currentFieldEffectIds = new Set();
            if (data.fieldEffects) {
                data.fieldEffects.forEach(effectData => {
                    currentFieldEffectIds.add(effectData.id);
                    if (this.fieldEffects.has(effectData.id)) {
                        this.updateFieldEffect(effectData);
                    } else {
                        this.createFieldEffect(effectData);
                    }
                });
            }
            for (let [effectId] of this.fieldEffects) {
                if (!currentFieldEffectIds.has(effectId)) {
                    this.removeFieldEffect(effectId);
                }
            }
        }

        // Beams: same pattern
        {
            const currentBeamIds = new Set();
            if (data.beams) {
                data.beams.forEach(beamData => {
                    currentBeamIds.add(beamData.id);
                    if (this.beams.has(beamData.id)) {
                        this.updateBeam(beamData);
                    } else {
                        this.createBeam(beamData);
                    }
                });
            }

            for (let [beamId, beam] of this.beams) {
                if (!currentBeamIds.has(beamId)) {
                    this.removeBeam(beamId);
                }
            }
        }
        
        // Handle flags (CTF mode)
        if (data.flags) {
            const currentFlagIds = new Set();
            data.flags.forEach(flagData => {
                currentFlagIds.add(flagData.id);
                if (this.flags.has(flagData.id)) {
                    this.updateFlag(flagData);
                } else {
                    this.createFlag(flagData);
                }
            });
            
            for (let [flagId, flag] of this.flags) {
                if (!currentFlagIds.has(flagId)) {
                    this.removeFlag(flagId);
                }
            }
        }
        
        // Handle KOTH zones
        if (data.kothZones) {
            const currentZoneIds = new Set();
            data.kothZones.forEach(zoneData => {
                currentZoneIds.add(zoneData.id);
                if (this.kothZones.has(zoneData.id)) {
                    this.updateKothZone(zoneData);
                } else {
                    this.createKothZone(zoneData);
                }
            });
            
            for (let [zoneId, zone] of this.kothZones) {
                if (!currentZoneIds.has(zoneId)) {
                    this.removeKothZone(zoneId);
                }
            }
        }
        
        // Handle utility entities (turrets, nets, mines, defense lasers, workshops, headquarters, power-ups)
        this.handleUtilityEntities(data);
        
        this.updateUI(data);
    }
    
    /**
     * Handle all utility entities from server data
     */
    handleUtilityEntities(data) {
        const currentEntityIds = new Set();
        
        // Handle turrets
        if (data.turrets) {
            data.turrets.forEach(turretData => {
                currentEntityIds.add(turretData.id);
                if (this.utilityEntities.has(turretData.id)) {
                    this.updateUtilityEntity(turretData);
                } else {
                    this.createUtilityEntity(turretData);
                }
            });
        }
        
        // Handle nets
        if (data.nets) {
            data.nets.forEach(netData => {
                currentEntityIds.add(netData.id);
                if (this.utilityEntities.has(netData.id)) {
                    this.updateUtilityEntity(netData);
                } else {
                    this.createUtilityEntity(netData);
                }
            });
        }
        
        // Handle mines
        if (data.mines) {
            data.mines.forEach(mineData => {
                currentEntityIds.add(mineData.id);
                if (this.utilityEntities.has(mineData.id)) {
                    this.updateUtilityEntity(mineData);
                } else {
                    this.createUtilityEntity(mineData);
                }
            });
        }
        
        // Handle defense lasers
        if (data.defenseLasers) {
            data.defenseLasers.forEach(laserData => {
                currentEntityIds.add(laserData.id);
                if (this.utilityEntities.has(laserData.id)) {
                    this.updateUtilityEntity(laserData);
                } else {
                    this.createUtilityEntity(laserData);
                }
            });
        }
        
        // Handle workshops
        if (data.workshops) {
            data.workshops.forEach(workshopData => {
                currentEntityIds.add(workshopData.id);
                if (this.utilityEntities.has(workshopData.id)) {
                    this.updateUtilityEntity(workshopData);
                } else {
                    this.createUtilityEntity(workshopData);
                }
            });
        }
        
        // Handle power-ups
        if (data.powerUps) {
            data.powerUps.forEach(powerUpData => {
                currentEntityIds.add(powerUpData.id);
                if (this.utilityEntities.has(powerUpData.id)) {
                    this.updateUtilityEntity(powerUpData);
                } else {
                    this.createUtilityEntity(powerUpData);
                }
            });
        }
        
        // Handle headquarters
        if (data.headquarters) {
            data.headquarters.forEach(hqData => {
                currentEntityIds.add(hqData.id);
                if (this.utilityEntities.has(hqData.id)) {
                    this.updateUtilityEntity(hqData);
                } else {
                    this.createUtilityEntity(hqData);
                }
            });
        }
        
        // Remove entities that no longer exist
        for (let [entityId, entity] of this.utilityEntities) {
            if (!currentEntityIds.has(entityId)) {
                this.removeUtilityEntity(entityId);
            }
        }

        // Handle vision-obscured overlay
        this.updateVisionObscuredOverlay(data.visionObscured === true);
    }
    
    /**
     * Show or hide a full-screen smoke overlay when the player's vision is obscured.
     */
    updateVisionObscuredOverlay(isObscured) {
        if (isObscured && !this.smokeOverlay) {
            this.smokeOverlay = new PIXI.Graphics();
            this.smokeOverlay.rect(
                -this.app.screen.width,
                -this.app.screen.height,
                this.app.screen.width * 3,
                this.app.screen.height * 3
            ).fill({ color: 0x888888, alpha: 0.75 });
            this.smokeOverlay.zIndex = 45; // Above game objects, below HUD
            this.gameContainer.addChild(this.smokeOverlay);
        } else if (isObscured && this.smokeOverlay) {
            // Keep it visible, follow camera
            this.smokeOverlay.visible = true;
        } else if (!isObscured && this.smokeOverlay) {
            this.gameContainer.removeChild(this.smokeOverlay);
            this.smokeOverlay.destroy({ children: true, context: true });
            this.smokeOverlay = null;
        }
    }

    /**
     * Create a utility entity
     */
    createUtilityEntity(entityData) {
        const entityContainer = new PIXI.Container();
        entityContainer.position.set(entityData.x, entityData.y);
        
        // Create graphics based on entity type
        const entityGraphics = this.createUtilityEntityGraphics(entityData);
        entityContainer.addChild(entityGraphics);
        
        // Create health bar for turrets
        if (entityData.type === 'TURRET') {
            const healthBarContainer = this.createTurretHealthBar(entityData);
            entityContainer.healthBar = healthBarContainer;
            this.nameContainer.addChild(healthBarContainer);
        }
        
        // Set z-index based on type
        entityContainer.zIndex = this.getUtilityEntityZIndex(entityData.type);
        
        // Store entity data
        entityContainer.entityData = entityData;
        entityContainer.entityGraphics = entityGraphics;
        this.utilityEntities.set(entityData.id, entityContainer);
        this.gameContainer.addChild(entityContainer);
    }
    
    /**
     * Update a utility entity
     */
    updateUtilityEntity(entityData) {
        const entityContainer = this.utilityEntities.get(entityData.id);
        if (!entityContainer) return;
        
        // Update position
        entityContainer.position.set(entityData.x, entityData.y);
        
        // Update visual state based on entity data
        this.updateUtilityEntityVisual(entityContainer, entityData);
        
        entityContainer.entityData = entityData;
    }
    
    /**
     * Remove a utility entity
     */
    removeUtilityEntity(entityId) {
        const entityContainer = this.utilityEntities.get(entityId);
        if (entityContainer) {
            this.cleanupUtilityEntityContainer(entityContainer);
            this.gameContainer.removeChild(entityContainer);
            this.utilityEntities.delete(entityId);
        }
    }
    
    handlePlayerKilled(data) {
        if (data.victimId === this.myPlayerId) {
            this.showDeathScreen(data);
        }
    }
    
    handleGameEvent(data) {
        this.displayGameEvent(data);
    }

    displayGameEvent(event) {
        if (!this.eventContainer) {
            this.createEventDisplay();
        }

        const eventElement = document.createElement('div');
        eventElement.className = 'game-event';

        // Overall colour hint (may be overridden by colored-text spans inside)
        if (event.color) eventElement.style.color = event.color;

        if (event.category) {
            eventElement.classList.add('event-' + event.category.toLowerCase());
        }

        const coloredMessage = this.parseColoredMessage(event.message);
        if (coloredMessage) {
            eventElement.innerHTML = coloredMessage;
        } else {
            eventElement.textContent = event.message;
        }

        this.eventContainer.prepend(eventElement);

        // Auto-remove after display duration
        const displayDuration = event.displayDuration || 3000;
        this.safeSetTimeout(() => this.removeGameEvent(eventElement), displayDuration);

        // Cap visible events. Evict overflow SYNCHRONOUSLY: removeGameEvent() only
        // schedules an animated removal 300ms later, so using it here would never
        // change children.length and this while-loop would spin forever (tab
        // freeze) — which is exactly what high event-rate modes (CTF/oddball)
        // triggered. The break guard ensures we can never loop without progress.
        const MAX_EVENTS = 10;
        while (this.eventContainer.children.length > MAX_EVENTS) {
            const oldest = this.eventContainer.lastElementChild;
            if (!oldest) break;
            this.eventContainer.removeChild(oldest);
        }
    }
    
    /**
     * Parse message with color tags and convert to HTML spans
     * Format: <color:#RRGGBB>Text</color> -> <span style="color:#RRGGBB">Text</span>
     */
    parseColoredMessage(message) {
        if (!message || !message.includes('<color:')) {
            return null;
        }
        
        // Replace color tags with styled spans
        const colorPattern = /<color:(#[0-9A-Fa-f]{6})>(.*?)<\/color>/g;
        const htmlMessage = message.replace(colorPattern, (match, color, text) => {
            // Escape HTML in the text content to prevent XSS
            const escapedText = text.replace(/&/g, '&amp;')
                                   .replace(/</g, '&lt;')
                                   .replace(/>/g, '&gt;')
                                   .replace(/"/g, '&quot;')
                                   .replace(/'/g, '&#039;');
            return `<span style="color:${color}; font-weight:bold;">${escapedText}</span>`;
        });
        
        return htmlMessage;
    }
    
    createEventDisplay() {
        // All layout and styling lives in unified.css (#game-events, .game-event, etc.)
        this.eventContainer = document.createElement('div');
        this.eventContainer.id = 'game-events';
        document.body.appendChild(this.eventContainer);
    }
    
    removeGameEvent(eventElement) {
        if (!eventElement?.parentNode) return;
        // CSS animation (.removing keyframe) plays the exit; remove from DOM after it finishes
        eventElement.classList.add('removing');
        this.safeSetTimeout(() => eventElement.parentNode?.removeChild(eventElement), 300);
    }
    
    /**
     * Handle round end event - display scores
     */
    handleRoundEnd(data) {
        this.showRoundEndScreen(data);
    }
    
    /**
     * Handle round start event - clear round end screen
     */
    handleRoundStart(data) {
        this.hideRoundEndScreen();
    }
    
    /**
     * Show round end screen with scores
     */
    showRoundEndScreen(data) {
        let overlay = document.getElementById('round-end-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'round-end-overlay';
            document.body.appendChild(overlay);
        }

        const content = document.createElement('div');
        content.className = 'round-end-content';

        const title = document.createElement('h1');
        title.className = 'round-end-title';
        title.textContent = `ROUND ${data.round} COMPLETE`;
        content.appendChild(title);

        if (data.scores?.length > 0) {
            const scoresContainer = document.createElement('div');
            scoresContainer.className = 'scores-container';

            const hasTeams = data.scores.some(s => s.team > 0);

            if (hasTeams) {
                const teams = {};
                const teamTotals = {};
                data.scores.forEach(score => {
                    const t = score.team || 0;
                    if (!teams[t]) {
                        teams[t] = [];
                        teamTotals[t] = { kills: 0, deaths: 0, captures: 0 };
                    }
                    teams[t].push(score);
                    teamTotals[t].kills   += score.kills   || 0;
                    teamTotals[t].deaths  += score.deaths  || 0;
                    teamTotals[t].captures += score.captures || 0;
                });

                const sortedTeams = Object.entries(teams).sort(([a], [b]) => {
                    const ta = parseInt(a), tb = parseInt(b);
                    if (ta === 0) return 1;
                    if (tb === 0) return -1;
                    return teamTotals[b].kills - teamTotals[a].kills;
                });

                sortedTeams.forEach(([teamNum, players]) => {
                    const teamNumInt = parseInt(teamNum);
                    const totals = teamTotals[teamNum];
                    const teamColor = this.getTeamColorCSS(teamNumInt);

                    const teamHeader = document.createElement('div');
                    teamHeader.className = 'team-header';
                    teamHeader.style.borderLeftColor = teamColor;

                    const teamName = document.createElement('h3');
                    teamName.className = 'team-header-name';
                    teamName.style.color = teamColor;
                    teamName.textContent = teamNum == 0 ? 'No Team' : `Team ${teamNum}`;

                    const teamStats = document.createElement('div');
                    teamStats.className = 'team-header-stats';

                    const kEl = document.createElement('span');
                    kEl.className = 'stat-kills';
                    kEl.textContent = `${totals.kills} K`;

                    const dEl = document.createElement('span');
                    dEl.className = 'stat-deaths';
                    dEl.textContent = `${totals.deaths} D`;

                    const kdEl = document.createElement('span');
                    kdEl.className = 'stat-kd';
                    kdEl.textContent = `${(totals.kills / Math.max(1, totals.deaths)).toFixed(2)} K/D`;

                    teamStats.append(kEl, dEl);
                    if (totals.captures > 0) {
                        const cEl = document.createElement('span');
                        cEl.className = 'stat-captures';
                        cEl.textContent = `${totals.captures} 🚩`;
                        teamStats.appendChild(cEl);
                    }
                    teamStats.appendChild(kdEl);

                    teamHeader.append(teamName, teamStats);
                    scoresContainer.appendChild(teamHeader);

                    [...players]
                        .sort((a, b) => (b.kills || 0) - (a.kills || 0))
                        .forEach(score => scoresContainer.appendChild(this.createScoreRow(score)));
                });
            } else {
                [...data.scores]
                    .sort((a, b) => (b.kills || 0) - (a.kills || 0))
                    .forEach((score, i) => scoresContainer.appendChild(this.createScoreRow(score, i + 1)));
            }

            content.appendChild(scoresContainer);
        }

        const nextRoundText = document.createElement('p');
        nextRoundText.className = 'next-round-text';
        nextRoundText.textContent = `Next round starts in ${Math.ceil(data.restDuration)} seconds…`;
        content.appendChild(nextRoundText);

        overlay.innerHTML = '';
        overlay.appendChild(content);
        overlay.classList.add('visible');
    }
    
    /**
     * Create a score row for a player (round-end scoreboard)
     */
    createScoreRow(score, rank = null) {
        const isLocalPlayer = score.playerId === this.myPlayerId;

        const row = document.createElement('div');
        row.className = 'score-row' + (isLocalPlayer ? ' local-player' : '');
        row.style.borderLeftColor = this.getTeamColorCSS(score.team);

        const nameSection = document.createElement('div');
        nameSection.className = 'score-row-name';
        nameSection.style.color = isLocalPlayer ? '#FFD700' : '#ffffff';
        nameSection.textContent = (rank ? `#${rank} ` : '') + score.playerName;

        const stats = document.createElement('div');
        stats.className = 'score-row-stats';

        const kills = document.createElement('span');
        kills.className = 'stat-kills';
        kills.textContent = `${score.kills || 0} K`;

        const deaths = document.createElement('span');
        deaths.className = 'stat-deaths';
        deaths.textContent = `${score.deaths || 0} D`;

        if (score.captures > 0) {
            const captures = document.createElement('span');
            captures.className = 'stat-captures';
            captures.textContent = `${score.captures} 🚩`;
            stats.appendChild(captures);
        }

        const kd = document.createElement('span');
        kd.className = 'stat-kd';
        kd.textContent = `${((score.kills || 0) / Math.max(1, score.deaths || 0)).toFixed(2)} K/D`;

        stats.append(kills, deaths, kd);
        row.append(nameSection, stats);
        return row;
    }
    
    /**
     * Hide round end screen
     */
    hideRoundEndScreen() {
        document.getElementById('round-end-overlay')?.classList.remove('visible');
    }
    
    /**
     * Show game over screen with final results
     */
    showGameOverScreen(data) {
        let overlay = document.getElementById('game-over-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'game-over-overlay';
            document.body.appendChild(overlay);
        }

        const content = document.createElement('div');
        content.className = 'game-over-content';

        const title = document.createElement('h1');
        title.className = 'game-over-title';
        title.textContent = '🏆 GAME OVER';
        content.appendChild(title);

        const message = document.createElement('p');
        message.className = 'game-over-message';
        message.textContent = data.message || 'The battle has ended!';
        content.appendChild(message);

        const vcInfo = document.createElement('p');
        vcInfo.className = 'game-over-vc-info';
        vcInfo.textContent = `Victory Condition: ${this.getVictoryConditionName(data.victoryCondition)}`;
        content.appendChild(vcInfo);

        if (data.finalScores?.length > 0) {
            const scoresTitle = document.createElement('h2');
            scoresTitle.className = 'final-scores-title';
            scoresTitle.textContent = 'Final Scores';
            content.appendChild(scoresTitle);

            const scoresContainer = document.createElement('div');
            scoresContainer.className = 'final-scores-container';

            const isEliminationMode = data.victoryCondition === 'ELIMINATION';
            const sortedScores = [...data.finalScores].sort((a, b) => {
                if (isEliminationMode && a.placement && b.placement) {
                    if (a.placement !== b.placement) return a.placement - b.placement;
                    if (b.kills !== a.kills) return b.kills - a.kills;
                    return b.eliminationTime - a.eliminationTime;
                }
                return b.score - a.score;
            });

            sortedScores.forEach((score, index) => {
                scoresContainer.appendChild(this.createFinalScoreRow(score, index + 1, data, isEliminationMode));
            });

            content.appendChild(scoresContainer);
        }

        const lobbyButton = document.createElement('button');
        lobbyButton.className = 'lobby-button';
        lobbyButton.textContent = '← Return to Lobby';
        lobbyButton.onclick = () => { window.location.href = '/lobby.html'; };
        content.appendChild(lobbyButton);

        overlay.innerHTML = '';
        overlay.appendChild(content);
        overlay.classList.add('visible');
    }
    
    /**
     * Create a score row for the game over screen
     */
    createFinalScoreRow(score, rank, gameOverData, isEliminationMode = false) {
        const isLocalPlayer = score.playerId === this.myPlayerId;
        const displayRank = isEliminationMode && score.placement ? score.placement : rank;

        const row = document.createElement('div');
        const classes = ['final-score-row'];
        if (isLocalPlayer) classes.push('local-player');
        else if (rank === 1) classes.push('rank-1');
        row.className = classes.join(' ');
        row.style.borderLeftColor = this.getRankColor(displayRank);

        const nameSection = document.createElement('div');
        nameSection.className = 'final-score-name-section';

        const rankBadge = document.createElement('span');
        rankBadge.className = 'rank-badge';
        rankBadge.textContent = this.getRankBadge(displayRank);
        nameSection.appendChild(rankBadge);

        const nameText = document.createElement('span');
        nameText.className = 'final-score-name';
        if (score.team !== undefined) {
            nameText.textContent = `Team ${score.team}`;
            nameText.style.color = this.getTeamColorCSS(score.team);
        } else {
            nameText.textContent = score.playerName || `Player ${score.playerId}`;
            nameText.style.color = isLocalPlayer ? '#FFD700' : '#ffffff';
        }
        nameSection.appendChild(nameText);
        row.appendChild(nameSection);

        const stats = document.createElement('div');
        stats.className = 'final-score-stats';

        if (isEliminationMode && score.placement) {
            const placementSpan = document.createElement('span');
            placementSpan.className = displayRank <= 3 ? 'stat-placement' : '';
            placementSpan.textContent = `#${score.placement}`;
            stats.appendChild(placementSpan);
        } else {
            const scoreSpan = document.createElement('span');
            scoreSpan.className = 'stat-score';
            scoreSpan.textContent = `${score.score} pts`;
            stats.appendChild(scoreSpan);
        }

        const killsSpan = document.createElement('span');
        killsSpan.className = 'stat-kills';
        killsSpan.textContent = `${score.kills} K`;

        const deathsSpan = document.createElement('span');
        deathsSpan.className = 'stat-deaths';
        deathsSpan.textContent = `${score.deaths} D`;

        stats.append(killsSpan, deathsSpan);

        if (score.captures > 0) {
            const capturesSpan = document.createElement('span');
            capturesSpan.className = 'stat-captures';
            capturesSpan.textContent = `${score.captures} 🚩`;
            stats.appendChild(capturesSpan);
        }

        row.appendChild(stats);
        return row;
    }
    
    /**
     * Get victory condition display name
     */
    getVictoryConditionName(condition) {
        const names = {
            'SCORE_LIMIT': 'Score Limit',
            'TIME_LIMIT': 'Time Limit',
            'OBJECTIVE': 'Objective',
            'ELIMINATION': 'Elimination',
            'ENDLESS': 'Endless'
        };
        return names[condition] || condition;
    }
    
    /**
     * Get color for rank position
     */
    getRankColor(rank) {
        if (rank === 1) return '#FFD700'; // Gold
        if (rank === 2) return '#C0C0C0'; // Silver
        if (rank === 3) return '#CD7F32'; // Bronze
        return '#666666'; // Gray
    }
    
    /**
     * Get badge emoji for rank
     */
    getRankBadge(rank) {
        if (rank === 1) return '🥇';
        if (rank === 2) return '🥈';
        if (rank === 3) return '🥉';
        return `#${rank}`;
    }
    
    createPlayer(playerData) {
        
        const sprite = new PIXI.Sprite(this.playerTexture);
        // The texture bounds are from (-20, -20) to (25, 20) - width 45, height 40
        // Circle center is at (0, 0) which is (20, 20) in texture pixel coordinates
        // Set anchor to rotate around the circle center, not the texture center
        sprite.anchor.set(20/45, 0.5); // x: 20/45 ≈ 0.444, y: 0.5
        
        sprite.position.set(playerData.x, playerData.y);
        sprite.rotation = playerData.rotation || 0;
        
        // Color players based on their team
        const playerColor = this.getPlayerColor(playerData);
        sprite.tint = playerColor;
        
        // Flip Y-axis back so sprite appears right-side up (gameContainer is Y-flipped)
        sprite.scale.set(1.0, -1.0);
        sprite.alpha = 1.0; // Ensure full opacity
        sprite.visible = true; // Explicitly set visible
        
        // Create health bar above player
        const healthBarContainer = this.createPlayerHealthBar(playerData);
        sprite.healthBar = healthBarContainer;
        
        // Create reload indicator (initially hidden)
        const reloadIndicator = this.createReloadIndicator(playerData);
        sprite.reloadIndicator = reloadIndicator;
    
        // Create death marker (initially hidden)
        if (this.deathTexture) {
            const deathMarker = new PIXI.Sprite(this.deathTexture);
            deathMarker.anchor.set(0.5);
            deathMarker.position.set(playerData.x, playerData.y);
            deathMarker.scale.y = -1; // Flip Y-axis back
            deathMarker.zIndex = 12; // Above players and projectiles
            deathMarker.visible = false;
            sprite.deathMarker = deathMarker;
            this.gameContainer.addChild(deathMarker);
        }
        // Note: Death texture should be available after loadAssets()
        
        // Create name label in separate container so it doesn't rotate
        const nameLabel = new PIXI.Text(playerData.name || `Player ${playerData.id}`, {
            fontSize: 14,
            fill: 0xffffff,
            stroke: 0x000000,
            strokeThickness: 3
        });
        nameLabel.anchor.set(0.5);
        nameLabel.scale.y = -1; // Flip Y-axis back so text is readable
        
        // Position name label below health bar
        nameLabel.position.set(playerData.x, playerData.y - 25);
        
        // Store reference to name label on sprite for easy access
        sprite.nameLabel = nameLabel;
        
        // Add name label to separate container
        this.nameContainer.addChild(nameLabel);
        
        // Set player z-index to ensure it's on top
        sprite.zIndex = 10;
        
        sprite.playerData = playerData;
        this.players.set(playerData.id, sprite);
        this.gameContainer.addChild(sprite);
    }
    
    updatePlayer(playerData) {
        const sprite = this.players.get(playerData.id);
        if (!sprite) return;

        // Direct position update - no interpolation
        sprite.position.set(playerData.x, playerData.y);
        sprite.rotation = playerData.rotation || 0;
        
        // Handle death marker logic
        const isDead = !playerData.active && playerData.respawnTime > 0;
        
        // Show/hide player sprite and death marker
        sprite.visible = playerData.active;
        if (sprite.deathMarker) {
            sprite.deathMarker.visible = isDead;
            if (isDead) {
                sprite.deathMarker.position.set(sprite.x, sprite.y);
            }
        }

        // Update name label position (doesn't rotate with player)
        if (sprite.nameLabel) {
            // Use current sprite position (which may be interpolated)
            sprite.nameLabel.position.set(sprite.x, sprite.y - 25);
            // Show name label for active players or dead players with respawn timer
            sprite.nameLabel.visible = playerData.active || isDead;
        }

        // Update health bar (hide for dead players)
        if (sprite.healthBar) {
            this.updateHealthBar(sprite.healthBar, playerData, sprite, sprite.healthBar.config);
            sprite.healthBar.visible = playerData.active && playerData.health > 0;
        }
        
        // Update reload indicator
        if (sprite.reloadIndicator) {
            this.updateReloadIndicator(sprite.reloadIndicator, playerData);
        }
        
        // Update power-up visual indicators
        this.updatePowerUpIndicators(sprite, playerData);

        sprite.playerData = playerData;
    }
    
    removePlayer(playerId) {
        const sprite = this.players.get(playerId);
        if (sprite) {
            // Clean up all player-related objects
            this.cleanupPlayerSprite(sprite);
            
            // Remove sprite from game container
            this.gameContainer.removeChild(sprite);
            this.players.delete(playerId);
        }
    }
    
    /**
     * Thoroughly clean up a player sprite to prevent memory leaks
     */
    cleanupPlayerSprite(sprite) {
        // Remove and destroy name label
        if (sprite.nameLabel) {
            if (sprite.nameLabel.parent) {
                sprite.nameLabel.parent.removeChild(sprite.nameLabel);
            }
            // Text owns an auto-generated GPU texture; let destroy() free it.
            sprite.nameLabel.destroy();
            sprite.nameLabel = null;
        }
        
        // Remove and destroy health bar
        if (sprite.healthBar) {
            if (sprite.healthBar.parent) {
                sprite.healthBar.parent.removeChild(sprite.healthBar);
            }
            // Clean up health bar components
            if (sprite.healthBar.healthBg) {
                sprite.healthBar.healthBg.destroy({ context: true });
            }
            if (sprite.healthBar.healthFill) {
                sprite.healthBar.healthFill.destroy({ context: true });
            }
            sprite.healthBar.destroy({ children: true, context: true });
            sprite.healthBar = null;
        }
        
        // Remove and destroy reload indicator
        if (sprite.reloadIndicator) {
            if (sprite.reloadIndicator.parent) {
                sprite.reloadIndicator.parent.removeChild(sprite.reloadIndicator);
            }
            // Clean up reload indicator components
            if (sprite.reloadIndicator.background) {
                sprite.reloadIndicator.background.destroy({ context: true });
            }
            if (sprite.reloadIndicator.reloadText) {
                sprite.reloadIndicator.reloadText.destroy(); // free Text's GPU texture
            }
            sprite.reloadIndicator.destroy({ children: true, context: true });
            sprite.reloadIndicator = null;
        }
        
        // Remove and destroy death marker
        if (sprite.deathMarker) {
            if (sprite.deathMarker.parent) {
                sprite.deathMarker.parent.removeChild(sprite.deathMarker);
            }
            sprite.deathMarker.destroy({ context: true });
            sprite.deathMarker = null;
        }
        
        // Remove and destroy power-up container
        if (sprite.powerUpContainer) {
            if (sprite.powerUpContainer.parent) {
                sprite.powerUpContainer.parent.removeChild(sprite.powerUpContainer);
            }
            sprite.powerUpContainer.destroy({ children: true, context: true });
            sprite.powerUpContainer = null;
        }
        
        // Clear player data reference
        sprite.playerData = null;
        
        // Destroy the main sprite (texture is shared, so keep it; free child geometry)
        sprite.destroy({ children: true, texture: false, context: true });
    }
    
    /**
     * Create health bar for a player.
     */
    /**
     * Create a health bar for any entity (player, obstacle, turret, etc.)
     * @param {Object} entityData - The entity data
     * @param {Object} config - Health bar configuration
     * @param {number} config.width - Width of the health bar
     * @param {number} config.height - Height of the health bar
     * @param {number} config.yOffset - Y offset from entity position
     * @param {number} config.bgColor - Background color
     * @param {number} config.fillColor - Fill color
     * @param {number} config.cornerRadius - Corner radius for rounded rectangle
     * @param {boolean} config.showWhenFull - Whether to show when at full health
     * @returns {PIXI.Container} The health bar container
     */
    createHealthBar(entityData, config) {
        const healthBarContainer = new PIXI.Container();
        
        // Health bar background
        const healthBg = new PIXI.Graphics();
        healthBg.roundRect(-config.width/2, 0, config.width, config.height, config.cornerRadius).fill(config.bgColor);
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.roundRect(-config.width/2, 0, config.width, config.height, config.cornerRadius).fill(config.fillColor);
        healthBarContainer.addChild(healthFill);
        
        // Store references for updates
        healthBarContainer.healthBg = healthBg;
        healthBarContainer.healthFill = healthFill;
        healthBarContainer.config = config;
        
        // Position above entity
        healthBarContainer.position.set(entityData.x, entityData.y - (config.yOffset || 0));
        
        // Add to name container so it doesn't rotate with entity
        this.nameContainer.addChild(healthBarContainer);
        
        return healthBarContainer;
    }
    
    /**
     * Update a health bar for any entity
     * @param {PIXI.Container} healthBarContainer - The health bar container
     * @param {Object} entityData - The entity data
     * @param {Object} sprite - The entity sprite for positioning
     * @param {Object} config - Health bar configuration
     */
    updateHealthBar(healthBarContainer, entityData, sprite, config) {
        if (!healthBarContainer || !healthBarContainer.healthFill) return;
        
        // Update position above entity
        if (sprite) {
            const yOffset = (config && config.yOffset) || 0;
            healthBarContainer.position.set(sprite.x, sprite.y - yOffset);
        }
        
        // Calculate health percentage
        const healthPercent = entityData.health;
        
        // Determine visibility
        const isDamaged = healthPercent < 1.0;
        healthBarContainer.visible = entityData.active && (config.showWhenFull || isDamaged);
        
        if (!healthBarContainer.visible) {
            return;
        }
        
        // Color based on health level (for players)
        let healthColor = config.fillColor;
        if (config.dynamicColor && healthPercent < 0.3) {
            healthColor = 0xe74c3c; // Red
        } else if (config.dynamicColor && healthPercent < 0.6) {
            healthColor = 0xf39c12; // Orange
        }

        // Only rebuild the fill geometry when it actually changes. Rebuilding a
        // Graphics every frame (clear + redraw) for every entity is the PixiJS
        // anti-pattern that churns GPU geometry; health rarely changes, so this
        // skips the vast majority of redraws.
        const fill = healthBarContainer.healthFill;
        if (fill._lastHealthPercent === healthPercent && fill._lastHealthColor === healthColor) {
            return;
        }
        fill._lastHealthPercent = healthPercent;
        fill._lastHealthColor = healthColor;

        fill.clear();
        fill.roundRect(
            -config.width/2, 0, 
            config.width * healthPercent, 
            config.height, 
            config.cornerRadius
        ).fill(healthColor);
    }
    
    /**
     * Create health bar for a player
     */
    createPlayerHealthBar(playerData) {
        return this.createHealthBar(playerData, {
            width: 50,
            height: 6,
            yOffset: 35,
            bgColor: 0x333333,
            fillColor: 0x2ecc71,
            cornerRadius: 2,
            showWhenFull: true,
            dynamicColor: true
        });
    }
    
    /**
     * Create health bar for a turret
     */
    createTurretHealthBar(entityData) {
        return this.createHealthBar(entityData, {
            width: 40,
            height: 5,
            yOffset: 30,
            bgColor: 0x333333,
            fillColor: 0x2ecc71,
            cornerRadius: 2,
            showWhenFull: true,
            dynamicColor: false
        });
    }
    
    /**
     * Create reload indicator for a player.
     */
    createReloadIndicator(playerData) {
        const reloadContainer = new PIXI.Container();
        
        // Create background circle for the "R"
        const background = new PIXI.Graphics();
        background.circle(0, 0, 12).fill({ color: 0x000000, alpha: 0.7 }); // Semi-transparent black background
        background.circle(0, 0, 12).stroke({ width: 2, color: 0xff4444 }); // Red border
        reloadContainer.addChild(background);
        
        // Create the "R" text
        const reloadText = new PIXI.Text('R', {
            fontSize: 14,
            fill: 0xff4444, // Red color
            fontWeight: 'bold',
            fontFamily: 'Arial'
        });
        reloadText.anchor.set(0.5);
        reloadText.scale.y = -1; // Flip Y-axis back so text is readable
        reloadText.position.set(0, 0);
        reloadContainer.addChild(reloadText);
        
        // Position above player (will be updated in updatePlayer)
        reloadContainer.position.set(playerData.x, playerData.y - 50); // Above health bar
        
        // Initially hidden
        reloadContainer.visible = false;
        
        // Store references for updates
        reloadContainer.background = background;
        reloadContainer.reloadText = reloadText;
        
        // Add to name container so it doesn't rotate with player
        this.nameContainer.addChild(reloadContainer);
        
        return reloadContainer;
    }
    
    /**
     * Update reload indicator appearance and position.
     */
    updateReloadIndicator(reloadContainer, playerData) {
        if (!reloadContainer) return;
        
        // Update position above player using current sprite position (may be interpolated)
        const sprite = this.players.get(playerData.id);
        if (sprite) {
            reloadContainer.position.set(sprite.x, sprite.y - 50); // Above health bar
        }
        
        // Show/hide based on reloading status and if player is active
        const isReloading = playerData.reloading || false;
        const isActive = playerData.active || false;
        reloadContainer.visible = isActive && isReloading;
        
        // Optional: Add pulsing animation when reloading
        if (isReloading && reloadContainer.reloadText) {
            const time = Date.now() * 0.005; // Slow pulsing
            const pulse = 0.8 + Math.sin(time) * 0.2; // Pulse between 0.6 and 1.0
            reloadContainer.reloadText.alpha = pulse;
        } else if (reloadContainer.reloadText) {
            reloadContainer.reloadText.alpha = 1.0; // Full opacity when not reloading
        }
    }
    
    /**
     * Update power-up visual indicators around player.
     */
    updatePowerUpIndicators(sprite, playerData) {
        const activePowerUps = playerData.activePowerUps || [];
        
        // Create power-up container if it doesn't exist
        if (!sprite.powerUpContainer) {
            sprite.powerUpContainer = new PIXI.Container();
            sprite.powerUpContainer._powerUpFingerprint = '';
            this.gameContainer.addChild(sprite.powerUpContainer);
        }
        
        // Update position to match player
        sprite.powerUpContainer.position.set(sprite.x, sprite.y);
        sprite.powerUpContainer.visible = playerData.active;
        
        // Build a fingerprint so we only rebuild graphics when the set of effects changes
        const fingerprint = activePowerUps.join('|');
        const changed = fingerprint !== sprite.powerUpContainer._powerUpFingerprint;
        sprite.powerUpContainer._powerUpFingerprint = fingerprint;
        
        if (activePowerUps.length > 0) {
            this.updatePowerUpVisuals(sprite.powerUpContainer, activePowerUps, sprite, changed);
        } else if (changed) {
            // Effects just cleared — destroy all children so nothing renders
            const toDestroy = [...sprite.powerUpContainer.children];
            toDestroy.forEach(child => {
                sprite.powerUpContainer.removeChild(child);
                child.destroy({ children: true, texture: false, baseTexture: false, context: true });
            });
            // Reset tracking arrays so the next activation starts fresh
            sprite.powerUpContainer._auraSprites = [];
            sprite.powerUpContainer._badgeContainer = null;
        }
    }
    
    /**
     * Create/update power-up visual effects based on render hints.
     * 
     * RenderHint Format: "effect_name:#COLOR:animation_type:show_icon:Display Name:params"
     * 
     * Animation Types:
     * - pulse/sparkle: Pulsing ring with rotating particles
     *   Params: {particles, radius, particleDistance, particleSize}
     * - shield: Polygonal shield pattern
     *   Params: {sides, size}
     * - slow: Dripping effect for debuffs
     *   Params: {drops, radius, dropSize, dripAmount}
     * - cloud: Billowing cloud effect (poison)
     *   Params: {radius, puffs, wisps}
     * - flame: Flickering fire particles (burning)
     *   Params: {count, radius, height}
     * - star: Orbiting stars (special status)
     *   Params: {count, radius, size}
     * - crown: VIP crown with sparkles
     * 
     * Examples:
     * "poison:#8BC34A:cloud:true:Poison"
     * "fire:#FF4500:flame:true:Burning:{\"count\":12,\"radius\":22,\"height\":10}"
     */
    updatePowerUpVisuals(container, activePowerUps, sprite, changed) {
        // Parse render hints: "effect_name:#COLOR:animation_type:show_icon:Display Name:params"
        const effects = activePowerUps.map(hint => {
            const parts = hint.split(':');
            let params = {};
            if (parts.length > 5) {
                try { params = JSON.parse(parts.slice(5).join(':')); } catch (e) { /* legacy */ }
            }
            return {
                name: parts[0] || 'unknown',
                color: parseInt(parts[1]?.replace('#', '') || 'FFFFFF', 16),
                animation: parts[2] || 'pulse',
                showIcon: parts[3] === 'true',
                displayName: parts[4] || '',
                params
            };
        });

        // --- Aura layer: one tinted glow Sprite per active effect ---
        // Sprites share the single glow texture, so they batch into ~1 draw call
        // and allocate no geometry. We only rebuild the sprite set when the effect
        // set changes; per-frame work is cheap transform updates (no clear/redraw).
        if (!container._auraSprites) container._auraSprites = [];

        if (changed) {
            container._auraSprites.forEach(s => {
                container.removeChild(s);
                s.destroy({ children: true, texture: false, baseTexture: false });
            });
            container._auraSprites = effects.map(effect => {
                const s = new PIXI.Sprite(this.glowTexture);
                s.anchor.set(0.5);
                s.tint = effect.color;
                const p = effect.params || {};
                const baseRadius = p.radius || p.size || 22;
                s._baseScale = (baseRadius * 1.6) / (this.glowTextureRadius || 64);
                s._animation = effect.animation;
                container.addChildAt(s, 0); // keep auras beneath badge overlays
                return s;
            });
        }

        // Animate each aura sprite via transform only (rotation/scale/alpha).
        const now = Date.now();
        container._auraSprites.forEach((s, index) => {
            const time = now * 0.003 + index;
            const pulse = 0.9 + Math.sin(time) * 0.12;
            s.scale.set(s._baseScale * pulse);
            switch (s._animation) {
                case 'flame':
                case 'speed':
                    s.alpha = 0.55 + Math.sin(time * 3) * 0.2;
                    s.rotation = now * 0.002;
                    break;
                case 'star':
                case 'crown':
                    s.alpha = 0.7;
                    s.rotation = now * 0.0015;
                    break;
                case 'cloud':
                    s.alpha = 0.45 + Math.sin(time * 1.2) * 0.1;
                    s.rotation = now * 0.0003;
                    break;
                default:
                    s.alpha = 0.5 + Math.sin(time * 2) * 0.12;
                    s.rotation = now * 0.0008;
            }
        });

        // Badge overlays — kept in a dedicated sub-container, never mixed with aura Graphics
        if (changed) {
            if (!container._badgeContainer) {
                container._badgeContainer = new PIXI.Container();
                container.addChild(container._badgeContainer);
            }
            const bc = container._badgeContainer;
            [...bc.children].forEach(child => {
                bc.removeChild(child);
                // Badges contain Text (auto-generated texture); free it on destroy.
                child.destroy({ children: true, context: true });
            });
            if (sprite.playerData.id === this.myPlayerId) {
                effects.forEach((effect, index) => {
                    if (effect.showIcon) {
                        bc.addChild(this.createPowerUpBadge(effect, index));
                    }
                });
            }
        }
    }
    
    /**
     * Create a small badge/icon for power-up status (shown only for local player).
     */
    createPowerUpBadge(effect, index) {
        const badge = new PIXI.Container();
        
        // Position badges in a row above player
        const offsetX = (index - 0.5) * 30;
        badge.position.set(offsetX, -45);
        
        // Background circle
        const bg = new PIXI.Graphics();
        bg.circle(0, 0, 10).fill({ color: 0x000000, alpha: 0.7 });
        bg.circle(0, 0, 10).stroke({ width: 2, color: effect.color });
        badge.addChild(bg);
        
        // Icon letter (first letter of effect name)
        const letter = effect.displayName.charAt(0) || '?';
        const text = new PIXI.Text(letter, {
            fontSize: 12,
            fill: effect.color,
            fontWeight: 'bold'
        });
        text.anchor.set(0.5);
        text.scale.y = -1; // Flip Y-axis back so text is readable
        badge.addChild(text);
        
        return badge;
    }
    
    createProjectile(projectileData) {
        // Create main projectile container
        const projectileContainer = new PIXI.Container();
        
        projectileContainer.position.set(projectileData.x, projectileData.y);
        
        // Create the main projectile sprite
        const sprite = new PIXI.Sprite(this.projectileTexture);
        sprite.anchor.set(0.5);
        
        // Flip Y-axis back so sprite appears right-side up (gameContainer is Y-flipped)
        sprite.scale.y = -1;
        
        // Customize projectile appearance based on ordinance type
        this.customizeProjectileAppearance(sprite, projectileData);
        
        // Add sprite to container
        projectileContainer.addChild(sprite);
        
        // Add special effects for plasma projectiles
        const ordinance = projectileData.ordinance || 'BULLET';
        if (ordinance === 'PLASMA') {
            this.createPlasmaEffects(projectileContainer, sprite);
        }
        
        // Check if this projectile should have a trail
        const shouldHaveTrail = this.shouldProjectileHaveTrail(ordinance);
        
        if (shouldHaveTrail) {
            // Create trail graphics
            const trail = this.createProjectileTrail(ordinance);
            trail.zIndex = -1; // Behind the main projectile
            projectileContainer.addChildAt(trail, 0); // Add at index 0 to be behind sprite
            projectileContainer.trail = trail;
            projectileContainer.trailPoints = []; // Store recent positions for trail
            projectileContainer.maxTrailLength = this.getTrailLength(ordinance);
        }
        
        // Set projectile z-index below players but above obstacles
        projectileContainer.zIndex = 8;
        
        // Store references
        projectileContainer.projectileData = projectileData;
        projectileContainer.sprite = sprite;
        this.projectiles.set(projectileData.id, projectileContainer);
        this.gameContainer.addChild(projectileContainer);
        
        // Create interpolator for smooth movement
        const velocity = { x: projectileData.vx || 0, y: projectileData.vy || 0 };
        
        // Initialize interpolator with velocity
        const interpolator = new ProjectileInterpolator(projectileContainer, velocity);
        this.projectileInterpolators.set(projectileData.id, interpolator);
    }
    
    /**
     * Check if projectile should have a trail based on ordinance type
     */
    shouldProjectileHaveTrail(ordinance) {
        // Based on Ordinance.java hasTrail() property
        switch (ordinance) {
            case 'ROCKET':
            case 'GRENADE':
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Create trail graphics for projectiles
     */
    createProjectileTrail(ordinance) {
        const trail = new PIXI.Graphics();
        
        switch (ordinance) {
            case 'ROCKET':
                // Rocket exhaust trail - bright orange/yellow with flames
                trail.trailColor = 0xff6600; // Orange
                trail.trailSecondaryColor = 0xffaa00; // Yellow
                trail.trailWidth = 8;
                trail.trailAlpha = 0.8;
                break;
            case 'GRENADE':
                // Grenade trail - dark smoke
                trail.trailColor = 0x666666; // Dark gray
                trail.trailSecondaryColor = 0x999999; // Light gray
                trail.trailWidth = 6;
                trail.trailAlpha = 0.6;
                break;
            default:
                trail.trailColor = 0xffffff;
                trail.trailSecondaryColor = 0xcccccc;
                trail.trailWidth = 4;
                trail.trailAlpha = 0.5;
                break;
        }
        
        return trail;
    }
    
    /**
     * Get trail length based on ordinance type
     */
    getTrailLength(ordinance) {
        switch (ordinance) {
            case 'ROCKET':
                return 15; // Long rocket exhaust
            case 'GRENADE':
                return 10; // Medium smoke trail
            default:
                return 8;
        }
    }
    
    /**
     * Create plasma effects for super-heated buzzing/glowing appearance
     */
    createPlasmaEffects(projectileContainer, sprite) {
        // Single reusable glow sprite instead of 3 layered Graphics + per-frame
        // electric-arc geometry rebuilds. Pulsed via transform only.
        const glow = new PIXI.Sprite(this.glowTexture);
        glow.anchor.set(0.5);
        glow.tint = 0x6688ff;
        glow._baseScale = 14 / (this.glowTextureRadius || 64);
        glow.scale.set(glow._baseScale);
        glow.zIndex = -1;
        projectileContainer.addChildAt(glow, 0);

        projectileContainer.plasmaGlow = glow;
        projectileContainer.plasmaTime = 0;
        projectileContainer.isPlasma = true;
    }
    
    /**
     * Animate plasma glow (transform-only pulse, no geometry).
     */
    animatePlasmaEffects(projectileContainer, deltaTime) {
        const glow = projectileContainer.plasmaGlow;
        if (!glow) return;

        projectileContainer.plasmaTime += deltaTime * 0.05;
        const t = projectileContainer.plasmaTime;

        const pulse = 1.0 + Math.sin(t * 10) * 0.25;
        glow.scale.set(glow._baseScale * pulse);
        glow.alpha = 0.55 + Math.sin(t * 8) * 0.2;
    }
    
    /**
     * Update projectile trail graphics
     */
    updateProjectileTrail(projectileContainer) {
        const trail = projectileContainer.trail;
        const points = projectileContainer.trailPoints;
        if (!trail || !points) {
            return;
        }

        // Record the projectile's current world position (its container lives in
        // gameContainer space, driven by the interpolator). We keep a short
        // rolling history and drop the oldest sample once we exceed the cap.
        const cx = projectileContainer.position.x;
        const cy = projectileContainer.position.y;
        points.push({ x: cx, y: cy });
        const maxLen = projectileContainer.maxTrailLength || 8;
        if (points.length > maxLen) {
            points.splice(0, points.length - maxLen);
        }

        trail.clear();
        if (points.length < 2) return;

        // The trail Graphics is a CHILD of the moving container, so draw each
        // recorded world point relative to the container's current position —
        // that anchors the trail in world space behind the projectile. Taper
        // width + alpha from oldest (thin/faint) to newest (full) so it fades
        // out into the distance.
        const isRocket = projectileContainer.projectileData
            && projectileContainer.projectileData.ordinance === 'ROCKET';
        for (let i = 1; i < points.length; i++) {
            const progress = i / (points.length - 1); // 0 = oldest segment, 1 = newest
            const ax = points[i - 1].x - cx, ay = points[i - 1].y - cy;
            const bx = points[i].x - cx, by = points[i].y - cy;
            const width = trail.trailWidth * (0.2 + 0.8 * progress);
            const alpha = trail.trailAlpha * progress;

            trail.moveTo(ax, ay);
            trail.lineTo(bx, by);
            trail.stroke({ width, color: trail.trailColor, alpha });

            // Bright inner core near the head of a rocket exhaust.
            if (isRocket && progress > 0.7) {
                trail.moveTo(ax, ay);
                trail.lineTo(bx, by);
                trail.stroke({ width: width * 0.4, color: trail.trailSecondaryColor, alpha: alpha * 0.8 });
            }
        }
    }
    
    /**
     * Customize projectile appearance based on ordinance type and effects
     */
    customizeProjectileAppearance(sprite, projectileData) {
        const ordinance = projectileData.ordinance || 'BULLET';
        const effects = projectileData.bulletEffects || [];
        
        // Set size based on ordinance
        switch (ordinance) {
            case 'ROCKET':
                sprite.scale.set(2.0);
                sprite.tint = 0xff4444; // Red for rockets
                break;
            case 'GRENADE':
                sprite.scale.set(1.5);
                sprite.tint = 0x44aa44; // Green for grenades
                break;
            case 'PLASMA':
                sprite.scale.set(1.2);
                sprite.tint = 0x8888ff; // Bright blue-white for plasma core
                sprite.alpha = 0.9; // Slightly transparent for energy effect
                break;
            case 'LASER':
                sprite.scale.set(0.8);
                sprite.tint = 0xff44ff; // Magenta for laser
                break;
            case 'DART':
                sprite.scale.set(0.5);
                sprite.tint = 0xffaa44; // Orange for darts
                break;
            case 'BULLET':
            default:
                sprite.scale.set(1.0);
                sprite.tint = 0xf39c12; // Default bullet color
                break;
        }
        
        // Add visual effects for special bullet effects
        if (effects.includes('HOMING')) {
            // Add a subtle glow for homing projectiles
            const glow = new PIXI.Graphics();
            glow.circle(0, 0, 8).fill({ color: 0xffffff, alpha: 0.3 });
            sprite.addChild(glow);
        }
        
        if (effects.includes('ELECTRIC')) {
            // Add electric sparks
            sprite.tint = this.blendColors(sprite.tint, 0x88aaff, 0.5);
        }
        
        if (effects.includes('INCENDIARY')) {
            // Add fire tint
            sprite.tint = this.blendColors(sprite.tint, 0xff4444, 0.3);
        }
        
        if (effects.includes('FREEZING')) {
            // Add ice tint
            sprite.tint = this.blendColors(sprite.tint, 0x88ccff, 0.3);
        }
        
        if (effects.includes('PIERCING')) {
            // Make piercing projectiles slightly transparent
            sprite.alpha = 0.8;
        }
    }
    
    updateProjectile(projectileData) {
        const projectileContainer = this.projectiles.get(projectileData.id);
        if (!projectileContainer) return;
        
        // Update projectile data
        projectileContainer.projectileData = projectileData;
        
        // Use interpolator for smooth movement
        const interpolator = this.projectileInterpolators.get(projectileData.id);
        if (interpolator) {
            const velocity = { x: projectileData.vx || 0, y: projectileData.vy || 0 };
            
            // Update interpolator with server data
            interpolator.updateFromServer(projectileData.x, projectileData.y, velocity.x, velocity.y);
        } else {
            // Fallback to direct position update if no interpolator
            // This should rarely happen, but provides safety
            projectileContainer.position.set(projectileData.x, projectileData.y);
        }
    }
    
    removeProjectile(projectileId) {
        const projectileContainer = this.projectiles.get(projectileId);
        if (projectileContainer) {
            // Clean up all child objects and references
            this.cleanupProjectileContainer(projectileContainer);
            
            // Remove from parent container
            this.gameContainer.removeChild(projectileContainer);
            this.projectiles.delete(projectileId);
        }
        
        // Clean up interpolator properly to prevent memory leaks
        const interpolator = this.projectileInterpolators.get(projectileId);
        if (interpolator) {
            interpolator.destroy();
            this.projectileInterpolators.delete(projectileId);
        }
    }
    
    /**
     * Thoroughly clean up a projectile container to prevent memory leaks
     */
    cleanupProjectileContainer(projectileContainer) {
        // Clean up plasma glow sprite if it exists (shared texture preserved)
        if (projectileContainer.plasmaGlow) {
            if (projectileContainer.plasmaGlow.parent) {
                projectileContainer.plasmaGlow.parent.removeChild(projectileContainer.plasmaGlow);
            }
            projectileContainer.plasmaGlow.destroy({ texture: false, baseTexture: false });
            projectileContainer.plasmaGlow = null;
        }
        
        // Clean up trail if it exists
        if (projectileContainer.trail) {
            if (projectileContainer.trail.parent) {
                projectileContainer.trail.parent.removeChild(projectileContainer.trail);
            }
            projectileContainer.trail.destroy({ context: true });
            projectileContainer.trail = null;
        }
        
        // Clear trail points array
        if (projectileContainer.trailPoints) {
            projectileContainer.trailPoints.length = 0;
            projectileContainer.trailPoints = null;
        }
        
        // Clean up main sprite
        if (projectileContainer.sprite) {
            if (projectileContainer.sprite.parent) {
                projectileContainer.sprite.parent.removeChild(projectileContainer.sprite);
            }
            // Destroy sprite but preserve the shared texture
            projectileContainer.sprite.destroy({ children: false, texture: false, baseTexture: false });
            projectileContainer.sprite = null;
        }
        
        // Clear all references
        projectileContainer.projectileData = null;
        projectileContainer.isPlasma = null;
        projectileContainer.maxTrailLength = null;
        
        // Destroy the container itself (children already manually destroyed above)
        projectileContainer.destroy({ children: true, texture: false, baseTexture: false, context: true });
    }

    createObstacle(obstacleData) {
        const graphics = this.createObstacleGraphics(obstacleData);
        graphics.position.set(obstacleData.x, obstacleData.y);
        graphics.rotation = obstacleData.rotation || 0;
        graphics.zIndex = 5;
        this.obstacles.set(obstacleData.id, graphics);
        this.gameContainer.addChild(graphics);
    }

    /**
     * Parse the compact shapes shorthand string produced by the server into an
     * array of drawable fixture descriptors.
     *
     * Format:  "fixture1;fixture2;..."
     *   Polygon fixture:  "(x1,y1)/(x2,y2)/..."
     *   Circle fixture:   "(cx,cy,r)"   (distinguished by having 3 comma-separated numbers)
     *
     * Returns an array of objects:
     *   { type: 'circle',  cx, cy, r }
     *   { type: 'polygon', points: [[x,y], ...] }
     */
    parseObstacleShapes(shapesStr) {
        if (!shapesStr) return [];
        return shapesStr.split(';').filter(s => s.length > 0).map(fixtureStr => {
            const parts = fixtureStr.split('/').map(v => {
                return v.replace(/[()]/g, '').split(',').map(Number);
            });
            if (parts[0].length === 3) {
                const [cx, cy, r] = parts[0];
                return { type: 'circle', cx, cy, r };
            }
            return { type: 'polygon', points: parts };
        });
    }

    /**
     * Create graphics for an obstacle using the compact shapes shorthand.
     * Handles circles, convex polygons, and multi-fixture compound shapes
     * (e.g. L-walls, cross-barriers) — all drawn from the same data.
     */
    createObstacleGraphics(obstacleData) {
        const graphics = new PIXI.Graphics();
        const obstacleType = obstacleData.type || 'BOULDER';
        const color = this.getObstacleColor(obstacleType);
        const outlineColor = this.darkenColor(color);

        const shapes = this.parseObstacleShapes(obstacleData.shapes);
        if (shapes.length > 0) {
            for (const shape of shapes) {
                if (shape.type === 'circle') {
                    graphics.circle(shape.cx, shape.cy, shape.r);
                } else {
                    graphics.poly(shape.points.flatMap(([x, y]) => [x, y]));
                }
            }
        } else {
            // Fallback for missing shape data
            graphics.circle(0, 0, obstacleData.boundingRadius || 20);
        }

        graphics.fill({ color, alpha: 0.8 });
        graphics.stroke({ width: 2, color: outlineColor });
        return graphics;
    }

    /**
     * Get color for obstacle based on type.
     */
    getObstacleColor(obstacleType) {
        switch (obstacleType) {
            case 'BOULDER': return 0x808080;
            case 'HOUSE': return 0x8B4513;
            case 'WALL_SEGMENT': return 0x696969;
            case 'TRIANGLE_ROCK': return 0x708090;
            case 'POLYGON_DEBRIS': return 0x654321;
            case 'HEXAGON_CRYSTAL': return 0x4169E1;
            case 'DIAMOND_STONE': return 0x9370DB;
            case 'L_SHAPED_WALL': return 0x2F4F4F;
            case 'CROSS_BARRIER': return 0x8B7D6B;
            default: return 0x808080;
        }
    }

    updateObstacle(obstacleData) {
        const graphics = this.obstacles.get(obstacleData.id);
        if (!graphics) return;
        graphics.position.set(obstacleData.x, obstacleData.y);
        graphics.rotation = obstacleData.rotation || 0;
    }

    removeObstacle(obstacleId) {
        const sprite = this.obstacles.get(obstacleId);
        if (sprite) {
            // Clean up graphics content
            if (sprite.clear && typeof sprite.clear === 'function') {
                sprite.clear();
            }
            
            // Remove from game container
            this.gameContainer.removeChild(sprite);
            this.obstacles.delete(obstacleId);
            
            // Clear references
            sprite.obstacleData = null;
            sprite.destroy({ children: true, context: true });
        }
    }
    
    /**
     * Create a field effect (explosion, fire, electric, etc.)
     */
    createFieldEffect(effectData) {
        const effectContainer = new PIXI.Container();
        effectContainer.position.set(effectData.x, effectData.y);
        
        // Set z-index based on effect type
        // Ground effects (heal zones, speed boosts) should render beneath players
        // Dangerous effects (fire, explosions) should render above players
        effectContainer.zIndex = this.getFieldEffectZIndex(effectData.type);
        
        // Create the main effect visual based on type
        const effectGraphics = this.createEffectGraphics(effectData);
        effectContainer.addChild(effectGraphics);

        // Shield barriers get a solid shell ring on top of the fill so they read
        // as a hard barrier rather than just another tinted field (e.g. ice).
        if (effectData.type === 'SHIELD_BARRIER') {
            const ring = this.createShieldBarrierRing(effectData.radius || 50);
            effectContainer.addChild(ring);
            effectContainer.barrierRing = ring;
        }

        // Add animated elements for certain effects
        this.addEffectAnimation(effectContainer, effectData);
        
        // Store effect data and add to containers
        effectContainer.effectData = effectData;
        effectContainer.effectGraphics = effectGraphics;
        this.fieldEffects.set(effectData.id, effectContainer);
        this.gameContainer.addChild(effectContainer);
    }
    
    /**
     * Update a field effect (mainly for animation and fade-out)
     */
    updateFieldEffect(effectData) {
        const effectContainer = this.fieldEffects.get(effectData.id);
        if (!effectContainer) {
            return;
        }
        
        // Update position (in case effect moves)
        effectContainer.position.set(effectData.x, effectData.y);
        
        // Growing effects (e.g. FIRE/ERUPTION): rescale the shared glow sprite to
        // match the server radius. The animate*() container pulse multiplies on top.
        if (effectData.type === 'FIRE' && effectContainer.effectGraphics) {
            effectContainer.effectGraphics.scale.set(effectData.radius / (this.fieldTextureRadius || 64));
        }
        
        // Update visual based on effect progress/intensity
        this.updateEffectVisual(effectContainer, effectData);
        
        effectContainer.effectData = effectData;
    }

    /**
     * Remove a field effect
     */
    removeFieldEffect(effectId) {
        const effectContainer = this.fieldEffects.get(effectId);
        if (effectContainer) {
            this.fieldEffects.delete(effectId);

            // Guard against tearing down the same container twice.
            if (effectContainer._removing) {
                return;
            }
            effectContainer._removing = true;

            // Clean up animation ticker first
            if (effectContainer.animationFunction) {
                this.removeTickerCallback(effectContainer.animationFunction);
                effectContainer.animationFunction = null;
            }
            
            // Add fade-out animation before final teardown (frees GPU geometry).
            this.fadeOutEffect(effectContainer, () => {
                this.cleanupFieldEffectContainer(effectContainer);
                if (effectContainer.parent) {
                    effectContainer.parent.removeChild(effectContainer);
                }
            });
        }
    }
    
    /**
     * Create a beam weapon effect
     */
    createBeam(beamData) {
        const beamContainer = new PIXI.Container();
        
        beamContainer.position.set(beamData.startX, beamData.startY);
        
        // Calculate beam length and angle
        const dx = beamData.endX - beamData.startX;
        const dy = beamData.endY - beamData.startY;
        const length = Math.sqrt(dx * dx + dy * dy);
        const angle = Math.atan2(dy, dx);
        
        // Create the main beam graphics based on type
        const beamGraphics = this.createBeamGraphics(beamData, length);
        beamGraphics.rotation = angle;
        beamContainer.addChild(beamGraphics);
        
        // Add beam effects based on damage type
        if (beamData.damageType === 'DAMAGE_OVER_TIME') {
            this.addBeamEffects(beamContainer, beamData, length, angle);
        }
        
        // Set z-index above projectiles but below players
        beamContainer.zIndex = 9;
        
        // Store beam data and add to containers
        beamContainer.beamData = beamData;
        beamContainer.beamGraphics = beamGraphics;
        beamContainer.beamLength = length;
        beamContainer.beamAngle = angle;
        this.beams.set(beamData.id, beamContainer);
        this.gameContainer.addChild(beamContainer);
    }
    
    /**
     * Update a beam weapon effect
     */
    updateBeam(beamData) {
        const beamContainer = this.beams.get(beamData.id);
        if (!beamContainer) return;

        beamContainer.position.set(beamData.startX, beamData.startY);

        const dx = beamData.endX - beamData.startX;
        const dy = beamData.endY - beamData.startY;
        const length = Math.sqrt(dx * dx + dy * dy);
        const angle = Math.atan2(dy, dx);

        // Only re-rasterise when the beam length changes — that's the expensive
        // path (clear + redraw path geometry). Rotation is just a cheap matrix
        // property and must be synced every frame so rotating beams (e.g. the
        // defense laser) don't leave stale ghost graphics at the original angle.
        if (Math.abs(length - beamContainer.beamLength) > 5) {
            if (beamContainer.beamGraphics) {
                beamContainer.beamGraphics.clear();
                this.drawBeamGraphics(beamContainer.beamGraphics, beamData, length);
            }
            if (beamContainer.energyEffect) {
                beamContainer.energyEffect.clear();
                beamContainer.energyEffect.moveTo(0, 0);
                beamContainer.energyEffect.lineTo(length, 0);
                beamContainer.energyEffect.stroke({ width: 8, color: 0x4488ff, alpha: 0.1 });
            }
            beamContainer.beamLength = length;
        }

        // Always sync rotation on both children — this is free and ensures
        // energyEffect stays aligned with beamGraphics as the beam rotates.
        if (beamContainer.beamGraphics) {
            beamContainer.beamGraphics.rotation = angle;
        }
        if (beamContainer.energyEffect) {
            beamContainer.energyEffect.rotation = angle;
        }
        beamContainer.beamAngle = angle;

        // Fade the whole container as the beam nears expiry
        const intensity = beamData.durationPercent || 1.0;
        beamContainer.alpha = Math.max(0.3, intensity);

        beamContainer.beamData = beamData;
    }
    
    removeBeam(beamId) {
        const beamContainer = this.beams.get(beamId);
        if (beamContainer) {
            this.cleanupBeamContainer(beamContainer);
            this.gameContainer.removeChild(beamContainer);
            this.beams.delete(beamId);
        }
    }
    
    // ===== Flag Management (CTF Mode) =====

    createFlag(flagData) {
        const flagContainer = new PIXI.Container();
        flagContainer.position.set(flagData.x, flagData.y);
        const oddball = flagData.oddball;
        if (oddball) {
            this.createOddballGraphics(flagContainer, flagData);
        } else {
            this.createCTFFlagGraphics(flagContainer, flagData);
        }
        flagContainer.zIndex = 11;
        flagContainer.flagData = flagData;
        flagContainer.oddball = oddball;
        this.flags.set(flagData.id, flagContainer);
        this.gameContainer.addChild(flagContainer);
    }

    createOddballGraphics(flagContainer, flagData) {
        const ball = new PIXI.Graphics();
        const ballColor = 0xFFFF00;
        ball.circle(0, 0, 20).fill(ballColor);
        ball.circle(0, 0, 20).stroke({ width: 2, color: 0xFFAA00 });
        const starPoints = 3;
        const outerRadius = 12;
        const innerRadius = 5;
        for (let i = 0; i < starPoints * 2; i++) {
            const radius = i % 2 === 0 ? outerRadius : innerRadius;
            const angle = (i * Math.PI) / starPoints - Math.PI / 2;
            const x = Math.cos(angle) * radius;
            const y = Math.sin(angle) * radius;
            if (i === 0) {
                ball.moveTo(x, y);
            } else {
                ball.lineTo(x, y);
            }
        }
        ball.closePath();
        ball.fill({ color: 0xFFFFFF, alpha: 0.9 });
        for (let i = 0; i < starPoints * 2; i++) {
            const radius = i % 2 === 0 ? outerRadius : innerRadius;
            const angle = (i * Math.PI) / starPoints - Math.PI / 2;
            const x = Math.cos(angle) * radius;
            const y = Math.sin(angle) * radius;
            
            if (i === 0) {
                ball.moveTo(x, y);
            } else {
                ball.lineTo(x, y);
            }
        }
        ball.closePath();
        ball.stroke({ width: 1.5, color: 0xFFAA00 });
        
        flagContainer.addChild(ball);
        flagContainer.ballSprite = ball;
    }
    
    createCTFFlagGraphics(flagContainer, flagData) {
        // Create flag pole (extends upward from base)
        const pole = new PIXI.Graphics();
        pole.rect(-2, 0, 4, 30).fill(0xEEEEEE); // Very bright silver/chrome
        pole.rect(-2, 0, 4, 30).stroke({ width: 1, color: 0xFFFFFF, alpha: 0.8 }); // White outline for extra visibility
        flagContainer.addChild(pole);
        
        // Create flag sprite (triangle) - flag at top of pole
        const flag = new PIXI.Graphics();
        const teamColor = this.getTeamColor(flagData.ownerTeam);
        flag.moveTo(0, 30);
        flag.lineTo(20, 20);
        flag.lineTo(0, 10);
        flag.lineTo(0, 30);
        flag.fill(teamColor);
        
        // Add black outline
        flag.moveTo(0, 30);
        flag.lineTo(20, 20);
        flag.lineTo(0, 10);
        flag.closePath();
        flag.stroke({ width: 1, color: 0x000000 });
        
        flagContainer.addChild(flag);
        flagContainer.flagSprite = flag;
    }
    
    updateFlag(flagData) {
        const flagContainer = this.flags.get(flagData.id);
        if (!flagContainer) {
            return;
        }
        
        // Update position (important for carried flags)
        flagContainer.position.set(flagData.x, flagData.y);
        
        // Update visual state based on flag state
        const state = flagData.state;
        const oddball = flagContainer.oddball;

        if (state === 'CARRIED') {
            flagContainer.alpha = 0.9;
            flagContainer.scale.set(0.8);
        } else if (state === 'DROPPED') {
            flagContainer.alpha = 0.8 + Math.sin(Date.now() / 500) * 0.2;
            flagContainer.scale.set(1.0);
        } else {
            // Flag/ball is at home - full opacity
            flagContainer.alpha = 1.0;
            flagContainer.scale.set(1.0);
        }
        flagContainer.flagData = flagData;
    }
    
    removeFlag(flagId) {
        const flagContainer = this.flags.get(flagId);
        if (flagContainer) {
            flagContainer.destroy({ children: true, context: true });
            this.gameContainer.removeChild(flagContainer);
            this.flags.delete(flagId);
        }
    }
    
    createKothZone(zoneData) {
        const zoneContainer = new PIXI.Container();
        
        zoneContainer.position.set(zoneData.x, zoneData.y);
        
        // Create zone circle (base layer)
        const baseCircle = new PIXI.Graphics();
        zoneContainer.baseCircle = baseCircle;
        zoneContainer.addChild(baseCircle);
        
        // Create capture progress ring
        const progressRing = new PIXI.Graphics();
        zoneContainer.progressRing = progressRing;
        zoneContainer.addChild(progressRing);
        
        // Create inner glow effect
        const glow = new PIXI.Graphics();
        zoneContainer.glow = glow;
        zoneContainer.addChild(glow);
        
        // Create zone number text
        const zoneText = new PIXI.Text(`${zoneData.zoneNumber + 1}`, {
            fontSize: 24,
            fill: 0xffffff,
            fontWeight: 'bold',
            stroke: 0x000000,
            strokeThickness: 3
        });
        zoneText.anchor.set(0.5);
        zoneText.scale.y = -1; // Flip Y-axis back so text is readable
        zoneContainer.addChild(zoneText);
        zoneContainer.zoneText = zoneText;
        
        // Create status text (below number)
        const statusText = new PIXI.Text('NEUTRAL', {
            fontSize: 12,
            fill: 0xaaaaaa,
            fontWeight: 'bold',
            stroke: 0x000000,
            strokeThickness: 2
        });
        statusText.anchor.set(0.5);
        statusText.scale.y = -1; // Flip Y-axis back so text is readable
        statusText.position.set(0, 20);
        zoneContainer.addChild(statusText);
        zoneContainer.statusText = statusText;
        
        // Create player count text
        const playerCountText = new PIXI.Text('', {
            fontSize: 10,
            fill: 0xffffff,
            stroke: 0x000000,
            strokeThickness: 2
        });
        playerCountText.anchor.set(0.5);
        playerCountText.scale.y = -1; // Flip Y-axis back so text is readable
        playerCountText.position.set(0, 35);
        zoneContainer.addChild(playerCountText);
        zoneContainer.playerCountText = playerCountText;
        
        // Set z-index (below players, above ground)
        zoneContainer.zIndex = 1;
        
        // Store zone data
        zoneContainer.zoneData = zoneData;
        zoneContainer.animationPhase = 0;
        
        // Add to game
        this.kothZones.set(zoneData.id, zoneContainer);
        this.gameContainer.addChild(zoneContainer);
        
        // Initial render
        this.updateKothZone(zoneData);
    }
    
    /**
     * Update a KOTH zone's visual state
     */
    updateKothZone(zoneData) {
        const zoneContainer = this.kothZones.get(zoneData.id);
        if (!zoneContainer) return;
        
        // Update position
        zoneContainer.position.set(zoneData.x, zoneData.y);
        
        // Store updated data
        zoneContainer.zoneData = zoneData;
        
        // Get colors based on state
        const colors = this.getKothZoneColors(zoneData);
        const radius = zoneData.radius || 80;

        // Only rebuild the circle/glow geometry when the visual state actually
        // changes (state, controlling team, or radius). Redrawing every tick is
        // the GPU-geometry-churn anti-pattern; zone state changes rarely.
        const renderKey = `${zoneData.state}|${zoneData.controllingTeam}|${radius}`;
        if (zoneContainer._renderKey !== renderKey) {
            zoneContainer._renderKey = renderKey;

            const baseCircle = zoneContainer.baseCircle;
            baseCircle.clear();
            baseCircle.circle(0, 0, radius).fill({ color: colors.fill, alpha: 0.2 });
            baseCircle.circle(0, 0, radius).stroke({ width: 3, color: colors.border });

            const glow = zoneContainer.glow;
            glow.clear();
            glow.circle(0, 0, radius * 0.7).fill({ color: colors.glow, alpha: 0.3 });

            zoneContainer.zoneText.style.fill = colors.text;

            const statusText = zoneContainer.statusText;
            statusText.text = this.getKothZoneStatusText(zoneData);
            statusText.style.fill = colors.statusText;
        }
        
        // Update player count
        const playerCountText = zoneContainer.playerCountText;
        if (zoneData.playerCount > 0) {
            playerCountText.text = `👥 ${zoneData.playerCount}`;
            playerCountText.visible = true;
        } else {
            playerCountText.visible = false;
        }
    }
    
    /**
     * Get colors for KOTH zone based on state
     */
    getKothZoneColors(zoneData) {
        switch (zoneData.state) {
            case 'CONTROLLED':
                const teamColor = this.getTeamColor(zoneData.controllingTeam);
                return {
                    fill: teamColor,
                    border: teamColor,
                    progress: teamColor,
                    glow: teamColor,
                    text: teamColor,
                    statusText: teamColor
                };
            
            case 'CONTESTED':
                return {
                    fill: 0xFF4444,
                    border: 0xFF4444,
                    progress: 0xFF4444,
                    glow: 0xFF4444,
                    text: 0xFFFFFF,
                    statusText: 0xFF4444
                };
            
            case 'NEUTRAL':
            default:
                return {
                    fill: 0x888888,
                    border: 0x888888,
                    progress: 0x888888,
                    glow: 0x444444,
                    text: 0xCCCCCC,
                    statusText: 0x888888
                };
        }
    }
    
    /**
     * Get status text for KOTH zone
     */
    getKothZoneStatusText(zoneData) {
        switch (zoneData.state) {
            case 'CONTROLLED':
                return `TEAM ${zoneData.controllingTeam + 1}`;
            case 'CONTESTED':
                return 'CONTESTED!';
            case 'NEUTRAL':
            default:
                return 'NEUTRAL';
        }
    }
    
    /**
     * Remove a KOTH zone
     */
    removeKothZone(zoneId) {
        const zoneContainer = this.kothZones.get(zoneId);
        if (zoneContainer) {
            zoneContainer.destroy({ children: true, context: true });
            this.gameContainer.removeChild(zoneContainer);
            this.kothZones.delete(zoneId);
        }
    }
    
    /**
     * Create beam graphics based on beam type and properties
     */
    createBeamGraphics(beamData, length) {
        const graphics = new PIXI.Graphics();
        this.drawBeamGraphics(graphics, beamData, length);
        return graphics;
    }
    
    drawBeamGraphics(graphics, beamData, length) {
        let beamType = beamData.ordinance || 'LASER';
        switch (beamType) {
            case 'LASER':
                this.createLaserGraphics(graphics, length, beamData); break;
            case 'PLASMA_BEAM':
                this.createPlasmaBeamGraphics(graphics, length, beamData); break;
        }
    }
    
    /**
     * Create laser beam graphics
     */
    createLaserGraphics(graphics, length, beamData) {
        const color = this.getTeamColor(beamData.ownerTeam)

        // Soft outer glow + bright white core. Two strokes instead of three.
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        graphics.stroke({ width: beamData.size * 1.5, color: color, alpha: 0.35 });

        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        graphics.stroke({ width: beamData.size / 2, color: 0xffffff });

        return graphics;
    }
    
    /**
     * Create plasma beam graphics
     */
    createPlasmaBeamGraphics(graphics, length, beamData) {
        const color = this.getTeamColor(beamData.ownerTeam)

        // Colored body + bright core. Dropped the per-segment instability loop
        // (lots of tiny strokes) in favor of two clean strokes.
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        graphics.stroke({ width: beamData.size, color: color, alpha: 0.8 });

        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        graphics.stroke({ width: beamData.size / 2, color: 0xaaffff });

        return graphics;
    }
    
    /**
     * Add special effects to beams (for DOT types)
     */
    addBeamEffects(beamContainer, beamData, length, angle) {
        // Add pulsing or crackling effects for continuous beams
        if (beamData.damageType === 'DAMAGE_OVER_TIME') {
            // Add continuous energy effect
            const energyEffect = new PIXI.Graphics();
            energyEffect.moveTo(0, 0);
            energyEffect.lineTo(length, 0);
            energyEffect.stroke({ width: 8, color: 0x4488ff, alpha: 0.1 });
            energyEffect.rotation = angle;
            beamContainer.addChild(energyEffect);
            
            // Store for animation
            beamContainer.energyEffect = energyEffect;
        }
    }
    
    /**
     * Create graphics for utility entities based on type
     */
    createUtilityEntityGraphics(entityData) {
        const graphics = new PIXI.Graphics();
        
        switch (entityData.type) {
            case 'TURRET':
                return this.createTurretGraphics(graphics, entityData);
            case 'NET':
                return this.createNetGraphics(graphics, entityData);
            case 'MINE':
                return this.createMineGraphics(graphics, entityData);
            case 'DEFENSE_LASER':
                return this.createDefenseLaserGraphics(graphics, entityData);
            case 'WORKSHOP':
                return this.createWorkshopGraphics(graphics, entityData);
            case 'HEADQUARTERS':
                return this.createHeadquartersGraphics(graphics, entityData);
            case 'POWERUP':
                return this.createPowerUpGraphics(graphics, entityData);
            default:
                return this.createGenericUtilityGraphics(graphics, entityData);
        }
    }
    
    /**
     * Create turret graphics
     */
    createTurretGraphics(graphics, entityData) {
        // Turret base - dark gray circle
        graphics.circle(0, 0, 18).fill({ color: 0x444444, alpha: 0.9 });
        
        // Turret base outline
        graphics.circle(0, 0, 18).stroke({ width: 2, color: 0x666666 });
        
        // Turret barrel - pointing in direction
        graphics.moveTo(0, 0);
        graphics.lineTo(25, 0); // Barrel length
        graphics.stroke({ width: 4, color: 0x333333 });
        
        // Turret barrel tip
        graphics.circle(25, 0, 3).fill(0x222222);
        
        // Team color indicator
        const teamColor = this.getTeamColor(entityData.ownerTeam || 0);
        graphics.circle(0, 0, 8).fill({ color: teamColor, alpha: 0.8 });
        
        // Health indicator (if available)
        if (entityData.health !== undefined) {
            const healthPercent = Math.max(0, entityData.health / 100);
            graphics.circle(0, 0, 20).stroke({ width: 2, color: 0x2ecc71, alpha: healthPercent });
        }
        
        return graphics;
    }
    
    /**
     * Create net projectile graphics
     */
    createNetGraphics(graphics, entityData) {
        // Choose net design - rectangle or pentagon
        const netDesign = 'pentagon'; // Change to 'rectangle' for rectangular net
        
        if (netDesign === 'pentagon') {
            return this.createPentagonNetGraphics(graphics, entityData);
        } else {
            return this.createRectangleNetGraphics(graphics, entityData);
        }
    }
    
    /**
     * Create rectangular mesh net graphics
     */
    createRectangleNetGraphics(graphics, entityData) {
        // Mesh-like rectangular net design
        const netWidth = 20;
        const netHeight = 16;
        const meshSize = 3; // Size of each mesh cell
        
        // Draw the main net frame (rectangle outline)
        graphics.rect(-netWidth/2, -netHeight/2, netWidth, netHeight).stroke({ width: 2, color: 0x8B4513, alpha: 0.9 }); // Brown rope color
        
        // Draw horizontal mesh lines
        const horizontalLines = Math.floor(netHeight / meshSize);
        for (let i = 1; i < horizontalLines; i++) {
            const y = -netHeight/2 + (i * meshSize);
            graphics.moveTo(-netWidth/2, y);
            graphics.lineTo(netWidth/2, y);
        }
        
        // Draw vertical mesh lines
        const verticalLines = Math.floor(netWidth / meshSize);
        for (let i = 1; i < verticalLines; i++) {
            const x = -netWidth/2 + (i * meshSize);
            graphics.moveTo(x, -netHeight/2);
            graphics.lineTo(x, netHeight/2);
        }
        graphics.stroke({ width: 1, color: 0x654321, alpha: 0.8 }); // Slightly darker brown
        
        // Add corner weights for realistic net behavior
        const cornerRadius = 3;
        const corners = [
            { x: -netWidth/2, y: -netHeight/2 }, // Top-left
            { x: netWidth/2, y: -netHeight/2 },  // Top-right
            { x: -netWidth/2, y: netHeight/2 },  // Bottom-left
            { x: netWidth/2, y: netHeight/2 }    // Bottom-right
        ];
        
        corners.forEach(corner => {
            // Corner weight
            graphics.circle(corner.x, corner.y, cornerRadius).fill({ color: 0x4A4A4A, alpha: 0.9 }); // Dark gray metal
            graphics.circle(corner.x, corner.y, cornerRadius).stroke({ width: 1, color: 0x2A2A2A }); // Darker outline
            
            // Add metallic shine
            graphics.circle(corner.x - 1, corner.y - 1, cornerRadius * 0.4).fill({ color: 0x6A6A6A, alpha: 0.6 });
        });
        
        // Add subtle net texture with small cross-hatches
        for (let x = -netWidth/2 + meshSize/2; x < netWidth/2; x += meshSize) {
            for (let y = -netHeight/2 + meshSize/2; y < netHeight/2; y += meshSize) {
                // Small cross pattern in each mesh cell
                graphics.moveTo(x - 0.5, y - 0.5);
                graphics.lineTo(x + 0.5, y + 0.5);
                graphics.moveTo(x + 0.5, y - 0.5);
                graphics.lineTo(x - 0.5, y + 0.5);
            }
        }
        graphics.stroke({ width: 0.5, color: 0x654321, alpha: 0.4 });
        
        return graphics;
    }
    
    /**
     * Create pentagonal mesh net graphics
     */
    createPentagonNetGraphics(graphics, entityData) {
        const radius = 12; // Radius of the pentagon
        const meshSize = 2.5; // Size of each mesh cell
        
        // Calculate pentagon vertices
        const vertices = [];
        for (let i = 0; i < 5; i++) {
            const angle = (i * 2 * Math.PI) / 5 - Math.PI / 2; // Start from top
            const x = radius * Math.cos(angle);
            const y = radius * Math.sin(angle);
            vertices.push({ x, y });
        }
        
        // Draw pentagon outline
        graphics.moveTo(vertices[0].x, vertices[0].y);
        for (let i = 1; i < vertices.length; i++) {
            graphics.lineTo(vertices[i].x, vertices[i].y);
        }
        graphics.lineTo(vertices[0].x, vertices[0].y); // Close the pentagon
        graphics.stroke({ width: 2, color: 0x8B4513, alpha: 0.9 }); // Brown rope color
        
        // Draw mesh lines from center to each vertex
        vertices.forEach(vertex => {
            graphics.moveTo(0, 0); // Center
            graphics.lineTo(vertex.x, vertex.y);
        });
        
        // Draw concentric pentagon mesh lines
        const meshLevels = Math.floor(radius / meshSize);
        for (let level = 1; level < meshLevels; level++) {
            const levelRadius = (level * radius) / meshLevels;
            const levelVertices = [];
            
            for (let i = 0; i < 5; i++) {
                const angle = (i * 2 * Math.PI) / 5 - Math.PI / 2;
                const x = levelRadius * Math.cos(angle);
                const y = levelRadius * Math.sin(angle);
                levelVertices.push({ x, y });
            }
            
            // Draw the concentric pentagon
            graphics.moveTo(levelVertices[0].x, levelVertices[0].y);
            for (let i = 1; i < levelVertices.length; i++) {
                graphics.lineTo(levelVertices[i].x, levelVertices[i].y);
            }
            graphics.lineTo(levelVertices[0].x, levelVertices[0].y);
        }
        graphics.stroke({ width: 1, color: 0x654321, alpha: 0.8 }); // Slightly darker brown
        
        // Add corner weights at each vertex
        const cornerRadius = 2.5;
        vertices.forEach(vertex => {
            // Corner weight
            graphics.circle(vertex.x, vertex.y, cornerRadius).fill({ color: 0x4A4A4A, alpha: 0.9 }); // Dark gray metal
            graphics.circle(vertex.x, vertex.y, cornerRadius).stroke({ width: 1, color: 0x2A2A2A }); // Darker outline
            
            // Add metallic shine
            graphics.circle(vertex.x - 0.8, vertex.y - 0.8, cornerRadius * 0.4).fill({ color: 0x6A6A6A, alpha: 0.6 });
        });
        
        // Add subtle net texture with small cross-hatches in mesh cells
        for (let level = 1; level < meshLevels; level++) {
            const levelRadius = (level * radius) / meshLevels;
            const nextLevelRadius = ((level + 1) * radius) / meshLevels;
            
            for (let i = 0; i < 5; i++) {
                const angle1 = (i * 2 * Math.PI) / 5 - Math.PI / 2;
                const angle2 = ((i + 1) * 2 * Math.PI) / 5 - Math.PI / 2;
                
                // Add cross-hatch in the mesh cell
                const centerX = (levelRadius + nextLevelRadius) / 2 * Math.cos((angle1 + angle2) / 2);
                const centerY = (levelRadius + nextLevelRadius) / 2 * Math.sin((angle1 + angle2) / 2);
                
                graphics.moveTo(centerX - 0.5, centerY - 0.5);
                graphics.lineTo(centerX + 0.5, centerY + 0.5);
                graphics.moveTo(centerX + 0.5, centerY - 0.5);
                graphics.lineTo(centerX - 0.5, centerY + 0.5);
            }
        }
        graphics.stroke({ width: 0.5, color: 0x654321, alpha: 0.3 });
        
        return graphics;
    }
    
    /**
     * Create proximity mine graphics
     */
    createMineGraphics(graphics, entityData) {
        const isArmed = entityData.isArmed || false;
        const ownerTeam = entityData.ownerTeam || 0;
        
        // Outer trigger zone - very subtle danger area
        graphics.circle(0, 0, 18).fill({ color: 0xff4444, alpha: 0.06 });
        
        // Trigger zone outline - dashed circle (more subtle)
        const dashCount = 16;
        for (let i = 0; i < dashCount; i++) {
            const startAngle = (i / dashCount) * Math.PI * 2;
            const endAngle = ((i + 0.5) / dashCount) * Math.PI * 2;
            graphics.arc(0, 0, 18, startAngle, endAngle);
        }
        graphics.stroke({ width: 1, color: 0xff6666, alpha: 0.25 });
        
        // Mine center body - darker, more blended
        graphics.circle(0, 0, 8).fill({ color: 0x252f3a, alpha: 0.85 });

        // Center body outline - much more subtle
        graphics.circle(0, 0, 8).stroke({ width: 1, color: 0x1e2329, alpha: 0.7 });
 
        // Core highlight - metallic shine (more prominent without inner ring)
        graphics.circle(-1, -1, 2).fill({ color: 0x3a4a5a, alpha: 0.4 });
        
        // Sensor spikes - 6 directional sensors (more subtle)
        for (let i = 0; i < 6; i++) {
            const angle = (i / 6) * Math.PI * 2;
            const innerRadius = 6;
            const outerRadius = 12;
            const spikeWidth = 1.2;
            
            // Sensor spike body - darker and more transparent
            graphics.moveTo(
                Math.cos(angle) * innerRadius,
                Math.sin(angle) * innerRadius
            );
            graphics.lineTo(
                Math.cos(angle) * outerRadius,
                Math.sin(angle) * outerRadius
            );
            graphics.stroke({ width: spikeWidth, color: 0x2a3441, alpha: 0.8 });
            
            // Sensor tip - small detection node (more subtle)
            graphics.circle(
                Math.cos(angle) * outerRadius,
                Math.sin(angle) * outerRadius,
                1.2
            ).fill({ color: 0x3a4a5a, alpha: 0.7 });
        }
        
        // Status indicator
        if (isArmed) {
            // Armed - team color pulsing ring around center
            const pulse = 0.6 + 0.4 * Math.sin(Date.now() * 0.01);
            const teamColor = this.getTeamColor(ownerTeam);
            graphics.circle(0, 0, 10).stroke({ width: 2, color: teamColor, alpha: pulse });
            
            // Armed indicator - small pulsing center light
            const centerPulse = 0.3 + 0.7 * Math.sin(Date.now() * 0.015);
            graphics.circle(0, 0, 1.5).fill({ color: teamColor, alpha: centerPulse });
        }
        
        return graphics;
    }
    
    /**
     * Create defense laser graphics
     */
    createDefenseLaserGraphics(graphics, entityData) {
        // Base structure - blue-gray circle
        graphics.circle(0, 0, 15).fill({ color: 0x4444AA, alpha: 0.9 });
        
        // Central core - brighter blue
        graphics.circle(0, 0, 8).fill({ color: 0x6666CC, alpha: 0.8 });
        
        // Rotating indicator - shows current beam direction
        graphics.rect(-2, -12, 4, 6).fill({ color: 0x8888FF, alpha: 0.9 });
        
        // Outer ring to show it's active
        graphics.circle(0, 0, 18).stroke({ width: 2, color: 0xAAAAFF, alpha: 0.8 });
        
        return graphics;
    }
    
    /**
     * Create workshop graphics
     */
    createWorkshopGraphics(graphics, entityData) {
        if (entityData.type !== 'WORKSHOP') {
            return;
        }

        // Derive bounding dimensions from the compact shapes string so we don't
        // rely on separate width/height fields from the server.
        const shapes = this.parseObstacleShapes(entityData.shapes);
        let halfWidth = entityData.craftRadius * 0.5 || 40;
        let halfHeight = entityData.craftRadius * 0.4 || 30;

        if (shapes.length > 0 && shapes[0].type === 'polygon') {
            const xs = shapes[0].points.map(([x]) => x);
            const ys = shapes[0].points.map(([, y]) => y);
            halfWidth  = (Math.max(...xs) - Math.min(...xs)) / 2;
            halfHeight = (Math.max(...ys) - Math.min(...ys)) / 2;
        }

        // Workshop base — drawn from shapes for consistency with obstacle rendering
        if (shapes.length > 0) {
            for (const shape of shapes) {
                if (shape.type === 'circle') {
                    graphics.circle(shape.cx, shape.cy, shape.r);
                } else {
                    graphics.poly(shape.points.flatMap(([x, y]) => [x, y]));
                }
            }
        } else {
            graphics.rect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2);
        }
        graphics.fill({ color: 0x555555, alpha: 0.9 });
        graphics.stroke({ width: 3, color: 0x777777 });
        
        // Crafting radius indicator (subtle)
        graphics.circle(0, 0, entityData.craftRadius || 80).stroke({ width: 1, color: 0x888888, alpha: 0.3 });
        
        // Workshop center - gear-like design
        graphics.moveTo(-8, -8);
        graphics.lineTo(8, 8);
        graphics.moveTo(8, -8);
        graphics.lineTo(-8, 8);
        graphics.circle(0, 0, 6);
        graphics.stroke({ width: 2, color: 0x999999 });
        
        // Add some workshop details to make it look more industrial
        // Horizontal lines for workshop floor (scaled to actual dimensions)
        const floorY1 = -halfHeight * 0.3;
        const floorY2 = halfHeight * 0.3;
        graphics.moveTo(-halfWidth * 0.8, floorY1);
        graphics.lineTo(halfWidth * 0.8, floorY1);
        graphics.moveTo(-halfWidth * 0.8, floorY2);
        graphics.lineTo(halfWidth * 0.8, floorY2);
        // Vertical lines for workshop walls (scaled to actual dimensions)
        const wallX1 = -halfWidth * 0.6;
        const wallX2 = halfWidth * 0.6;
        graphics.moveTo(wallX1, -halfHeight * 0.8);
        graphics.lineTo(wallX1, halfHeight * 0.8);
        graphics.moveTo(wallX2, -halfHeight * 0.8);
        graphics.lineTo(wallX2, halfHeight * 0.8);
        graphics.stroke({ width: 1, color: 0x666666, alpha: 0.8 });
        
        // Add crafting progress indicators for active players
        if (entityData.craftingProgress) {
            const progressEntries = Object.entries(entityData.craftingProgress);
            progressEntries.forEach(([playerId, progress], index) => {
                if (progress > 0) {
                    const angle = (index / progressEntries.length) * Math.PI * 2;
                    const radius = Math.max(halfWidth, halfHeight) + 20; // Position further outside the workshop
                    const x = Math.cos(angle) * radius;
                    const y = Math.sin(angle) * radius;
                    
                    // Progress indicator dot (larger and more visible)
                    graphics.circle(x, y, 8).fill({ color: 0x00AAFF });
                    
                    // Progress ring (outer) - thicker and more visible
                    graphics.circle(x, y, 12).stroke({ width: 4, color: 0x00AAFF, alpha: progress });
                    
                    // Inner progress circle
                    graphics.circle(x, y, 6).stroke({ width: 2, color: 0xFFFFFF, alpha: 0.8 });
                    
                    // Progress percentage indicator (pulsing dot)
                    const pulseSize = 4 + (progress * 4);
                    graphics.circle(x, y, pulseSize).fill({ color: 0xFFFFFF, alpha: 0.9 });
                }
            });
        }
        
        // Add workshop activity indicator (pulsing center when active)
        if (entityData.activeCrafters > 0) {
            // Pulsing center circle to show workshop is active
            graphics.circle(0, 0, 10).stroke({ width: 3, color: 0x00FF00 }); // Green for active - thicker and brighter
        }
        
        return graphics;
    }
    
    /**
     * Create headquarters graphics
     */
    createHeadquartersGraphics(graphics, entityData) {
        // Derive dimensions from the compact shapes string; fall back to
        // sensible defaults so the renderer never breaks on missing data.
        const shapes = this.parseObstacleShapes(entityData.shapes);

        // The physics body is composed of wall polygon(s) plus one circle fixture
        // per corner turret. Honor that data directly rather than synthesizing
        // decorations at guessed positions.
        const wallShapes = shapes.filter(s => s.type === 'polygon');
        const turretShapes = shapes.filter(s => s.type === 'circle');

        // The wall bounding box drives the HQ proportions (outline, command
        // center, health bar). Turret circles extend beyond it.
        let halfWidth = 40;
        let halfHeight = 30;
        const wallPoints = wallShapes.flatMap(s => s.points);
        if (wallPoints.length > 0) {
            const xs = wallPoints.map(([x]) => x);
            const ys = wallPoints.map(([, y]) => y);
            halfWidth  = (Math.max(...xs) - Math.min(...xs)) / 2;
            halfHeight = (Math.max(...ys) - Math.min(...ys)) / 2;
        }
        const width  = halfWidth  * 2;
        const height = halfHeight * 2;
        const team = entityData.team || 0;

        // Get team color
        const teamColor = this.getTeamColor(team);
        const darkerTeamColor = this.darkenColor(teamColor);

        // Health is already sent as a percentage (0.0 - 1.0) from backend
        const healthPct = entityData.health || 1.0;
        const damageAlpha = healthPct < 1.0 ? (1.0 - healthPct) * 0.6 : 0;

        // Re-issue the wall path(s) so they can be filled, damage-overlaid, and stroked.
        const traceWalls = () => {
            if (wallShapes.length > 0) {
                for (const wall of wallShapes) {
                    graphics.poly(wall.points.flatMap(([x, y]) => [x, y]));
                }
            } else {
                graphics.rect(-halfWidth, -halfHeight, width, height);
            }
        };

        // Walls: team-colored fill, damage overlay, fortified white outline.
        traceWalls();
        graphics.fill({ color: teamColor, alpha: 0.9 });
        if (damageAlpha > 0) {
            traceWalls();
            graphics.fill({ color: 0x000000, alpha: damageAlpha });
        }
        traceWalls();
        graphics.stroke({ width: 4, color: 0xFFFFFF, alpha: 0.9 });

        const turrets = turretShapes.map(c => ({ x: c.cx, y: c.cy, r: c.r }))
        turrets.forEach(turret => {
            // Turret base (darker shade of team color)
            graphics.circle(turret.x, turret.y, turret.r).fill({ color: darkerTeamColor, alpha: 0.95 });

            // Damage overlay on turrets
            if (damageAlpha > 0) {
                graphics.circle(turret.x, turret.y, turret.r).fill({ color: 0x000000, alpha: damageAlpha });
            }

            // Turret outline
            graphics.circle(turret.x, turret.y, turret.r).stroke({ width: 3, color: 0xFFFFFF, alpha: 0.95 });

            // Inner turret detail (smaller circle)
            graphics.circle(turret.x, turret.y, turret.r * 0.6).stroke({ width: 2, color: 0xFFFFFF, alpha: 0.7 });

            // Turret top accent
            graphics.circle(turret.x, turret.y, turret.r * 0.3).fill({ color: 0xFFFFFF, alpha: 0.4 });
        });

        // Central command center design
        const centerSize = Math.min(halfWidth, halfHeight) * 0.5;
        graphics.circle(0, 0, centerSize).fill({ color: teamColor, alpha: 0.5 });
        graphics.circle(0, 0, centerSize).stroke({ width: 2, color: 0xFFFFFF, alpha: 0.8 });
        
        // Team indicator - large team number in center
        const teamText = new PIXI.Text(`HQ\n${team}`, {
            fontSize: 18,
            fill: 0xFFFFFF,
            fontWeight: 'bold',
            align: 'center',
            stroke: 0x000000,
            strokeThickness: 3
        });
        teamText.anchor.set(0.5);
        teamText.scale.y = -1; // Flip Y-axis back so text is readable
        graphics.addChild(teamText);
        
        // Health bar above HQ
        const barWidth = width * 0.8;
        const barHeight = 6;
        const barY = -halfHeight - 15;
        
        // Health bar background
        graphics.rect(-barWidth/2, barY, barWidth, barHeight).fill({ color: 0x333333, alpha: 0.8 });
        graphics.rect(-barWidth/2, barY, barWidth, barHeight).stroke({ width: 2, color: 0x000000, alpha: 0.8 });
        
        // Health bar fill (color changes based on health)
        let healthBarColor = 0x00FF00; // Green
        if (healthPct < 0.3) {
            healthBarColor = 0xFF0000; // Red
        } else if (healthPct < 0.6) {
            healthBarColor = 0xFFAA00; // Orange
        }
        
        graphics.rect(-barWidth/2, barY, barWidth * healthPct, barHeight).fill({ color: healthBarColor, alpha: 0.9 });
        
        // Health text (show as percentage)
        const healthPercentage = (healthPct * 100).toFixed(0);
        const healthText = new PIXI.Text(`${healthPercentage}%`, {
            fontSize: 10,
            fill: 0xFFFFFF,
            fontWeight: 'bold',
            stroke: 0x000000,
            strokeThickness: 2
        });
        healthText.anchor.set(0.5);
        healthText.scale.y = -1; // Flip Y-axis back so text is readable
        healthText.position.set(0, barY - 12);
        graphics.addChild(healthText);
        
        // Warning pulse effect when heavily damaged
        if (healthPct < 0.3) {
            const pulse = Math.sin(Date.now() * 0.005) * 0.5 + 0.5;
            graphics.rect(-halfWidth - 5, -halfHeight - 5, width + 10, height + 10).stroke({ width: 3, color: 0xFF0000, alpha: pulse });
        }
        
        return graphics;
    }
    
    /**
     * Update headquarters visual to show health changes
     */
    updateHeadquartersVisual(container, entityData) {
        const graphics = container.getChildAt(0);
        if (!graphics) return;

        // Only rebuild when health or team changes. The HQ is otherwise static,
        // and a rebuild allocates two PIXI.Text objects (each owns a GPU texture),
        // so redrawing every tick churned both geometry and textures.
        const renderKey = `${entityData.health}|${entityData.team}`;
        if (container._hqRenderKey === renderKey) return;
        container._hqRenderKey = renderKey;

        graphics.clear();
        // Fully destroy old children. Text owns an auto-generated GPU texture, so
        // we must let destroy() free it — passing texture:false here leaks it.
        while (graphics.children.length > 0) {
            const child = graphics.children[0];
            graphics.removeChild(child);
            child.destroy({ children: true });
        }
        this.createHeadquartersGraphics(graphics, entityData);
    }
    
    /**
     * Create power-up graphics
     */
    createPowerUpGraphics(graphics, entityData) {
        const powerUpType = entityData.powerUpType || entityData.type || 'SPEED_BOOST';
        
        // Power-up base - larger circle for better visibility
        graphics.circle(0, 0, 14).fill({ color: 0xFFFFFF, alpha: 0.9 });
        
        // Power-up outline (thicker for better visibility)
        graphics.circle(0, 0, 14).stroke({ width: 3, color: 0xCCCCCC });
        
        // Type-specific visual indicators (larger inner circle)
        switch (powerUpType) {
            case 'SPEED_BOOST':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0x00FFFF, alpha: 0.8 });
                break;
            case 'HEALTH_REGENERATION':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0x00FF00, alpha: 0.8 });
                break;
            case 'DAMAGE_BOOST':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0xFF0000, alpha: 0.8 });
                break;
            case 'DAMAGE_RESISTANCE':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0xFFD700, alpha: 0.8 });
                break;
            case 'BERSERKER_MODE':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0xFF4500, alpha: 0.8 });
                break;
            case 'INFINITE_AMMO':
                // Orange background circle
                graphics.circle(0, 0, entityData.radius).fill({ color: 0xFFA500, alpha: 0.8 });
                // Draw infinity symbol (∞) - two connected loops
                graphics.circle(-3, 0, 3.5).stroke({ width: 1.5, color: 0xFFFFFF, alpha: 0.9 });
                graphics.circle(3, 0, 3.5).stroke({ width: 1.5, color: 0xFFFFFF, alpha: 0.9 });
                graphics.rect(-1, -1.5, 2, 3).fill({ color: 0xFFA500, alpha: 0.8 });
                break;
            case 'SLOW_EFFECT':
                graphics.circle(0, 0, entityData.radius).fill({ color: 0x0066CC, alpha: 0.8 });
                break;
        }
        
        // Add larger sparkle effect for better visibility
        graphics.circle(-5, -5, 3).fill({ color: 0xFFFFFF, alpha: 0.7 });
        graphics.circle(5, 5, 3).fill({ color: 0xFFFFFF, alpha: 0.7 });
        
        return graphics;
    }
    
    /**
     * Create generic utility graphics
     */
    createGenericUtilityGraphics(graphics, entityData) {
        graphics.circle(0, 0, 15).fill({ color: 0x888888, alpha: 0.7 });
        
        graphics.circle(0, 0, 15).stroke({ width: 2, color: 0xcccccc });
        
        return graphics;
    }
    
    /**
     * Get z-index for utility entity types
     */
    getUtilityEntityZIndex(entityType) {
        switch (entityType) {
            case 'TURRET':
                return 12; // Above players
            case 'DEFENSE_LASER':
                return 12; // Above players, same as turret
            case 'NET':
                return 9;  // Same as projectiles
            case 'MINE':
                return 7;  // Above obstacles, below players
            case 'WORKSHOP':
                return 6;  // Above obstacles, below players
            case 'HEADQUARTERS':
                return 5;  // Same as obstacles (HQ is a structure)
            case 'POWERUP':
                return 10; // Above players, below projectiles
            default:
                return 8;
        }
    }
    
    /**
     * Get z-index for field effects based on type
     * Ground/support effects render below players, dangerous effects above
     */
    getFieldEffectZIndex(effectType) {
        switch (effectType) {
            // Ground/support effects - render beneath players
            case 'HEAL_ZONE':
            case 'SPEED_BOOST':
                return 6;  // Above obstacles, below players
            
            // Dangerous/active effects - render above players for visibility
            case 'EXPLOSION':
            case 'FRAGMENTATION':
            case 'FIRE':
            case 'ELECTRIC':
            case 'FREEZE':
            case 'POISON':
            case 'ERUPTION':
                return 20; // Above players
            
            // Crowd control effects - render above players
            case 'SLOW_FIELD':
            case 'GRAVITY_WELL':
                return 20; // Above players
            
            // Defensive effects - render above players
            case 'SHIELD_BARRIER':
                return 15; // Above players but below dangerous effects
            
            // Smoke - render above players for visibility
            case 'SMOKE':
                return 25; // Above everything for visual coverage

            // Environmental effects
            case 'WARNING_ZONE':
            case 'EARTHQUAKE':
                return 20; // Above players
            
            default:
                return 20; // Default above players
        }
    }
    
    /**
     * Update utility entity visual state
     */
    updateUtilityEntityVisual(container, entityData) {
        // Update alpha based on activity
        if (!entityData.active) {
            container.alpha = 0.5;
        } else {
            container.alpha = 1.0;
        }
        
        // Type-specific updates
        switch (entityData.type) {
            case 'MINE':
                this.updateMineVisual(container, entityData);
                break;
            case 'TURRET':
                this.updateTurretVisual(container, entityData);
                break;
            case 'NET':
                this.updateNetVisual(container, entityData);
                break;
            case 'DEFENSE_LASER':
                this.updateDefenseLaserVisual(container, entityData);
                break;
            case 'WORKSHOP':
                this.updateWorkshopVisual(container, entityData);
                break;
            case 'HEADQUARTERS':
                this.updateHeadquartersVisual(container, entityData);
                break;
        }
    }
    
    /**
     * Update mine visual effects
     */
    updateMineVisual(container, entityData) {
        // Recreate graphics if arming status changed
        if (container.lastArmedState !== entityData.isArmed) {
            container.removeChild(container.entityGraphics);
            container.entityGraphics.destroy({ context: true });
            
            const newGraphics = this.createMineGraphics(new PIXI.Graphics(), entityData);
            container.addChild(newGraphics);
            container.entityGraphics = newGraphics;
            container.lastArmedState = entityData.isArmed;
        }
    }
    
    /**
     * Update turret visual effects
     */
    updateTurretVisual(container, entityData) {
        container.rotation = entityData.rotation || 0;
        
        // Update health bar if it exists
        if (container.healthBar) {
            this.updateHealthBar(container.healthBar, entityData, container, container.healthBar.config);
        }
    }

    updateNetVisual(container, entityData) {
        container.rotation = entityData.rotation || 0;
    }
    
    /**
     * Update defense laser visual effects
     */
    updateDefenseLaserVisual(container, entityData) {
        // Update rotation of the visual indicator to show beam direction
        container.rotation = entityData.rotation || 0;
        
        // Add pulsing effect to show it's active
        const time = Date.now() * 0.003; // Slow pulse
        const pulseValue = 0.8 + 0.2 * Math.sin(time);
        container.alpha = pulseValue;
    }
    
    /**
     * Update workshop visual with crafting progress
     */
    updateWorkshopVisual(container, entityData) {
        // Always update the progress data for smooth interpolation
        container.craftingProgress = entityData.craftingProgress || {};
        container.activeCrafters = entityData.activeCrafters || 0;
        
        // Create progress bars if they don't exist
        if (!container.progressBars) {
            container.progressBars = new Map();
        }
        
        // Get current crafters
        const currentCrafters = new Set(Object.keys(container.craftingProgress));
        const existingCrafters = new Set(container.progressBars.keys());
        
        // Remove progress bars for players who stopped crafting
        for (const playerId of existingCrafters) {
            if (!currentCrafters.has(playerId)) {
                const progressBar = container.progressBars.get(playerId);
                if (progressBar && progressBar.parent) {
                    container.removeChild(progressBar);
                    progressBar.destroy({ context: true });
                }
                container.progressBars.delete(playerId);
            }
        }
        
        // Create or update progress bars for active crafters
        let barIndex = 0;
        for (const [playerId, progress] of Object.entries(container.craftingProgress)) {
            if (progress > 0) {
                let progressBar = container.progressBars.get(playerId);
                
                if (!progressBar) {
                    // Create new progress bar
                    progressBar = this.createWorkshopProgressBar();
                    container.addChild(progressBar);
                    container.progressBars.set(playerId, progressBar);
                }
                
                // Position progress bar above workshop
                const yOffset = -40 - (barIndex * 12); // Stack multiple bars
                progressBar.position.set(0, yOffset);
                
                // Update progress bar fill (smooth interpolation happens in animation)
                progressBar.targetProgress = progress;
                
                barIndex++;
            }
        }
    }
    
    /**
     * Create a simple horizontal progress bar for workshop crafting
     */
    createWorkshopProgressBar() {
        const barContainer = new PIXI.Container();
        
        // Progress bar dimensions
        const barWidth = 60;
        const barHeight = 8;
        
        // Background (dark gray)
        const background = new PIXI.Graphics();
        background.roundRect(-barWidth/2, 0, barWidth, barHeight, 3).fill({ color: 0x222222, alpha: 0.8 });
        
        // Border
        background.roundRect(-barWidth/2, 0, barWidth, barHeight, 3).stroke({ width: 1, color: 0x444444, alpha: 0.8 });
        barContainer.addChild(background);
        
        // Progress fill (starts empty)
        const progressFill = new PIXI.Graphics();
        barContainer.addChild(progressFill);
        
        // Store references and state
        barContainer.background = background;
        barContainer.progressFill = progressFill;
        barContainer.barWidth = barWidth;
        barContainer.barHeight = barHeight;
        barContainer.currentProgress = 0;
        barContainer.targetProgress = 0;
        
        return barContainer;
    }
    
    /**
     * Smoothly animate progress bar from current to target progress
     */
    updateProgressBarAnimation(progressBar, deltaTime) {
        const target = progressBar.targetProgress || 0;
        const current = progressBar.currentProgress || 0;
        
        // Smooth interpolation (lerp)
        const lerpSpeed = 0.15; // Adjust for smoothness (0.1 = slow, 0.5 = fast)
        const newProgress = current + (target - current) * lerpSpeed;
        
        // Only redraw if progress changed enough (avoid tiny updates)
        if (Math.abs(newProgress - current) > 0.001) {
            progressBar.currentProgress = newProgress;
            
            // Redraw the progress fill
            const fill = progressBar.progressFill;
            fill.clear();
            
            if (newProgress > 0) {
                // Calculate fill width
                const fillWidth = (progressBar.barWidth - 4) * newProgress; // -4 for padding
                
                // Color based on progress (blue -> green as it fills)
                let fillColor;
                if (newProgress < 0.33) {
                    fillColor = 0x3498db; // Blue
                } else if (newProgress < 0.66) {
                    fillColor = 0x2ecc71; // Green
                } else {
                    fillColor = 0xf39c12; // Orange/Gold (almost complete)
                }
                
                // Draw the fill
                fill.roundRect(-progressBar.barWidth/2 + 2, 2, fillWidth, progressBar.barHeight - 4, 2).fill({ color: fillColor, alpha: 0.9 });
                
                // Add a subtle shine effect
                fill.roundRect(-progressBar.barWidth/2 + 2, 2, fillWidth, 2, 2).fill({ color: 0xffffff, alpha: 0.3 });
            }
        }
    }
    
    /**
     * Create health bar for a turret
     */
    createTurretHealthBar(entityData) {
        const healthBarContainer = new PIXI.Container();
        
        // Health bar background
        const healthBg = new PIXI.Graphics();
        healthBg.roundRect(-20, 0, 40, 5, 2).fill(0x333333);
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.roundRect(-20, 0, 40, 5, 2).fill(0x2ecc71);
        healthBarContainer.addChild(healthFill);
        
        // Store references for updates
        healthBarContainer.healthBg = healthBg;
        healthBarContainer.healthFill = healthFill;
        healthBarContainer.config = {
            width: 40,
            height: 5,
            yOffset: 30,
            bgColor: 0x333333,
            fillColor: 0x2ecc71,
            cornerRadius: 2,
            showWhenFull: true,
            dynamicColor: true
        };
        
        // Position above turret
        healthBarContainer.position.set(entityData.x, entityData.y - 30);
        
        return healthBarContainer;
    }
    
    /**
     * Update turret health bar
     */
    /**
     * Clean up utility entity container
     */
    cleanupUtilityEntityContainer(container) {
        // Clean up graphics
        if (container.entityGraphics) {
            container.entityGraphics.destroy({ context: true });
            container.entityGraphics = null;
        }
        
        // Clean up health bar
        if (container.healthBar) {
            container.healthBar.destroy({ children: true, context: true });
        }
        
        // Clear references
        container.entityData = null;
        container.lastArmedState = null;
        
        // Destroy container
        container.destroy({ children: true, texture: false, baseTexture: false, context: true });
    }
    
    /**
     * Clean up beam container to prevent memory leaks
     */
    cleanupBeamContainer(beamContainer) {
        // Clean up beam graphics
        if (beamContainer.beamGraphics) {
            beamContainer.beamGraphics.destroy({ context: true });
            beamContainer.beamGraphics = null;
        }
        
        // Clean up energy effects
        if (beamContainer.energyEffect) {
            beamContainer.energyEffect.destroy({ context: true });
            beamContainer.energyEffect = null;
        }
        
        // Clear all references
        beamContainer.beamData = null;
        beamContainer.beamLength = null;
        beamContainer.beamAngle = null;
        
        // Destroy the container
        beamContainer.destroy({ children: true, texture: false, baseTexture: false, context: true });
    }
    
    /**
     * Thoroughly clean up a field effect container to prevent memory leaks
     */
    cleanupFieldEffectContainer(effectContainer) {
        // Remove animation ticker first (if not already removed)
        if (effectContainer.animationFunction) {
            this.removeTickerCallback(effectContainer.animationFunction);
            effectContainer.animationFunction = null;
        }
        
        // Remove fade-out ticker if it exists
        if (effectContainer.fadeOutFunction) {
            this.removeTickerCallback(effectContainer.fadeOutFunction);
            effectContainer.fadeOutFunction = null;
        }
        
        // Clean up graphics objects explicitly
        if (effectContainer.effectGraphics) {
            if (effectContainer.effectGraphics.parent) {
                effectContainer.effectGraphics.parent.removeChild(effectContainer.effectGraphics);
            }
            effectContainer.effectGraphics.destroy({ context: true });
            effectContainer.effectGraphics = null;
        }
        
        // Clean up all child graphics objects
        const childrenToDestroy = [...effectContainer.children];
        childrenToDestroy.forEach(child => {
            if (child.parent) {
                child.parent.removeChild(child);
            }
            child.destroy({ children: true, context: true });
        });
        
        // Clear all custom properties
        effectContainer.effectData = null;
        effectContainer.animationTime = null;
        effectContainer.animationSpeed = null;
        effectContainer.originalScale = null;
        effectContainer.originalX = null;
        effectContainer.originalY = null;
        effectContainer.barrierRing = null; // destroyed via the child loop above
        
        // Destroy the container itself
        effectContainer.destroy({ children: true, texture: false, baseTexture: false, context: true });
    }
    
    /**
     * Create the main graphics for a field effect based on its type
     */
    createEffectGraphics(effectData) {
        // Every field effect is now a single tinted Sprite of the shared glow
        // texture. The existing animate*() methods drive scale/alpha/rotation on
        // the parent container, so we keep all motion with zero geometry churn.
        const radius = effectData.radius || 50;
        const style = this.getFieldEffectStyle(effectData.type);

        const sprite = new PIXI.Sprite(this.fieldTexture);
        sprite.anchor.set(0.5);
        sprite.tint = style.color;
        sprite.alpha = style.alpha;
        // Map the texture's full extent onto the physics radius so the disc fills
        // its area but never renders larger than the real radius.
        sprite.scale.set(radius / (this.fieldTextureRadius || 64));
        return sprite;
    }

    /**
     * Build the hard "shell" ring that distinguishes a shield barrier from a
     * plain tinted field. Strokes are inset by half their width so the outer
     * edge lands exactly on the physics radius — never beyond it. Stays in the
     * blue energy-shield palette.
     */
    createShieldBarrierRing(radius) {
        const ring = new PIXI.Graphics();

        // Solid outer shell — the dominant "this is a barrier" cue.
        const shellWidth = Math.max(3, radius * 0.06);
        ring.circle(0, 0, radius - shellWidth / 2)
            .stroke({ width: shellWidth, color: 0x33aaff, alpha: 0.95 });

        // Bright inner highlight ring for a layered, glassy shell look.
        const highlightWidth = Math.max(1.5, radius * 0.025);
        ring.circle(0, 0, radius - shellWidth - highlightWidth)
            .stroke({ width: highlightWidth, color: 0xcceeff, alpha: 0.75 });

        return ring;
    }

    /**
     * Tint + base opacity for each field effect type. Color is the only
     * per-type differentiator now that all effects share one glow sprite.
     */
    getFieldEffectStyle(type) {
        switch (type) {
            case 'EXPLOSION':      return { color: 0xffaa44, alpha: 0.9 };
            case 'FIRE':           return { color: 0xff5522, alpha: 0.7 };
            case 'ELECTRIC':       return { color: 0x88aaff, alpha: 0.85 };
            case 'FREEZE':         return { color: 0x88ccff, alpha: 0.7 };
            case 'FRAGMENTATION':  return { color: 0xffcc66, alpha: 0.9 };
            case 'POISON':         return { color: 0x88cc44, alpha: 0.6 };
            case 'HEAL_ZONE':      return { color: 0x44ff88, alpha: 0.5 };
            case 'SLOW_FIELD':     return { color: 0x66aaff, alpha: 0.5 };
            case 'SHIELD_BARRIER': return { color: 0x66ccff, alpha: 0.5 };
            case 'GRAVITY_WELL':   return { color: 0x9966ff, alpha: 0.7 };
            case 'SPEED_BOOST':    return { color: 0xffee66, alpha: 0.6 };
            case 'SMOKE':          return { color: 0x888888, alpha: 0.6 };
            case 'WARNING_ZONE':   return { color: 0xff4444, alpha: 0.5 };
            case 'EARTHQUAKE':     return { color: 0xaa7744, alpha: 0.6 };
            default:               return { color: 0xffffff, alpha: 0.6 };
        }
    }
    
    
    /**
     * Add animation to field effects
     */
    addEffectAnimation(container, effectData) {
        const animationSpeed = this.getEffectAnimationSpeed(effectData.type);
        
        // Store animation properties
        container.animationTime = 0;
        container.animationSpeed = animationSpeed;
        container.originalScale = container.scale.x;
        
        // Create a bound function reference so we can remove it later
        container.animationFunction = () => this.animateEffect(container);
        
        // Add to ticker for animation updates
        this.addTickerCallback(container.animationFunction);
    }
    
    /**
     * Animate field effects
     */
    animateEffect(container) {
        if (!container.effectData || !container.parent || !container.effectGraphics) {
            // Effect has been removed, stop animating
            if (container.animationFunction) {
                this.removeTickerCallback(container.animationFunction);
                container.animationFunction = null;
            }
            return;
        }
        
        container.animationTime += container.animationSpeed;
        const effectData = container.effectData;
        
        switch (effectData.type) {
            case 'EXPLOSION':
                this.animateExplosion(container);
                break;
            case 'FIRE':
                this.animateFire(container);
                break;
            case 'ELECTRIC':
                this.animateElectric(container);
                break;
            case 'FREEZE':
                this.animateFreeze(container);
                break;
            case 'FRAGMENTATION':
                this.animateFragmentation(container);
                break;
            case 'POISON':
                this.animatePoison(container);
                break;
            // Utility effect animations
            case 'HEAL_ZONE':
                this.animateHealZone(container);
                break;
            case 'SLOW_FIELD':
                this.animateSlowField(container);
                break;
            case 'SHIELD_BARRIER':
                this.animateShieldBarrier(container);
                break;
            case 'GRAVITY_WELL':
                this.animateGravityWell(container);
                break;
            case 'SPEED_BOOST':
                this.animateSpeedBoost(container);
                break;
            case 'SMOKE':
                this.animateSmoke(container);
                break;
            // Environmental event animations
            case 'WARNING_ZONE':
                this.animateWarningZone(container);
                break;
            case 'EARTHQUAKE':
                this.animateEarthquake(container);
                break;
        }
    }
    
    /**
     * Animate explosion effects
     */
    animateExplosion(container) {
        const time = container.animationTime;
        const progress = container.effectData.progress || 0;
        
        // Rapid expansion in first 0.1 seconds, then shrink slightly
        let scale;
        if (time < 0.1) {
            scale = 0.3 + (time / 0.1) * 0.7; // Expand from 0.3 up to the physics radius (1.0)
        } else {
            scale = 1.0 - progress * 0.3; // Shrink based on server progress
        }
        container.scale.set(Math.max(0.1, scale));
        
        // Quick fade out after initial flash
        if (time < 0.05) {
            container.alpha = 1.0; // Full brightness for first 0.05 seconds
        } else {
            container.alpha = Math.max(0, 1.0 - progress); // Fade based on server progress
        }
        
        // Slight rotation for dynamic feel
        container.rotation = time * 2.0;
    }
    
    /**
     * Animate fire effects
     */
    animateFire(container) {
        const time = container.animationTime;
        
        // Gentle flickering effect (slowed down from 15 to 6)
        const flicker = 0.8 + Math.sin(time * 6) * 0.2;
        container.alpha = flicker;
        
        // Gentle scale variation (slowed down from 8 to 3)
        const scale = 0.9 + Math.sin(time * 3) * 0.1;
        container.scale.set(scale);
        
        // Very subtle rotation (slowed down from 3 to 1)
        container.rotation = Math.sin(time * 1) * 0.1;
    }
    
    /**
     * Animate electric effects
     */
    animateElectric(container) {
        const time = container.animationTime;
        
        // Rapid flickering
        const flicker = Math.random() > 0.3 ? 1.0 : 0.6;
        container.alpha = flicker;
        
        // Electrical pulsing
        const pulse = 0.9 + Math.sin(time * 20) * 0.1;
        container.scale.set(pulse);
        
        // Random rotation for chaotic effect
        if (Math.random() > 0.9) {
            container.rotation = Math.random() * Math.PI * 2;
        }
    }
    
    /**
     * Animate freeze effects
     */
    animateFreeze(container) {
        const time = container.animationTime;
        
        // Slow, steady pulse
        const pulse = 0.95 + Math.sin(time * 5) * 0.05;
        container.scale.set(pulse);
        
        // Gradual rotation
        container.rotation = time * 0.2;
        
        // Stable alpha
        container.alpha = 0.8;
    }
    
    /**
     * Animate fragmentation effects
     */
    animateFragmentation(container) {
        const time = container.animationTime;
        const progress = container.effectData.progress || 0;
        
        // Rapid expansion up to the physics radius, never beyond
        const scale = 0.3 + progress * 0.7;
        container.scale.set(scale);
        
        // Quick fade after brief visibility
        if (time < 0.05) {
            container.alpha = 1.0; // Full brightness briefly
        } else {
            container.alpha = Math.max(0, 1.0 - progress); // Fade based on server progress
        }
        
        // Fast spinning fragments
        container.rotation = time * 8;
    }
    
    /**
     * Animate poison effects
     */
    animatePoison(container) {
        const time = container.animationTime;
        
        // Slow billowing cloud effect - gentle expansion and contraction
        const billow1 = Math.sin(time * 1.2) * 0.04;
        const billow2 = Math.sin(time * 1.8) * 0.03;
        const billow3 = Math.sin(time * 2.3) * 0.02;
        // Bias below 1.0 so the billow peaks at the physics radius, never over it
        const totalBillow = 0.91 + billow1 + billow2 + billow3;
        container.scale.set(totalBillow);
        
        // Very slow rotation to simulate cloud swirling
        container.rotation = time * 0.15;
        
        // Pulsing alpha to simulate cloud density changes - more subtle for pallor effect
        const pulse1 = Math.sin(time * 1.5) * 0.06;
        const pulse2 = Math.sin(time * 2.2) * 0.04;
        container.alpha = 0.7 + pulse1 + pulse2;
        
        // Add subtle position drift to simulate gas spreading and movement
        if (!container.originalX) {
            container.originalX = container.x;
            container.originalY = container.y;
        }
        
        const drift = time * 0.2;
        container.x = container.originalX + Math.sin(drift) * 3;
        container.y = container.originalY + Math.cos(drift * 0.8) * 2.5;
    }
    
    /**
     * Animate heal zone effects
     */
    animateHealZone(container) {
        const time = container.animationTime;
        
        // Gentle pulsing scale for the entire zone (like a heartbeat)
        const pulse = 0.95 + Math.sin(time * 3) * 0.05;
        container.scale.set(pulse);
        
        // Pulsing alpha to make the red cross appear to "breathe" or pulse
        // This creates the active healing indicator effect
        const breathe = 0.85 + Math.sin(time * 4) * 0.15;
        container.alpha = breathe;
        
        // No rotation - keep the cross upright and recognizable
    }
    
    
    /**
     * Animate slow field effects
     */
    animateSlowField(container) {
        const time = container.animationTime;
        
        // Slow ripple effect
        const ripple = 0.95 + Math.sin(time * 3) * 0.05;
        container.scale.set(ripple);
        
        // Pulsing alpha to show field strength
        const pulse = 0.7 + Math.sin(time * 2.5) * 0.2;
        container.alpha = pulse;
        
        // Slow counter-rotation
        container.rotation = -time * 0.3;
    }
    
    /**
     * Animate shield barrier effects
     */
    animateShieldBarrier(container) {
        // Shield barriers render as a steady, solid shell — no scale/alpha
        // oscillation or rotation.
        container.scale.set(1.0);
        container.alpha = 1.0;
        container.rotation = 0;
    }
    
    /**
     * Animate gravity well effects
     */
    animateGravityWell(container) {
        const time = container.animationTime;
        
        // Gravitational distortion - slight scale variation
        const distortion = 0.98 + Math.sin(time * 5) * 0.02;
        container.scale.set(distortion);
        
        // Stable but ominous presence
        container.alpha = 0.9;
        
        // Slow rotation suggesting gravitational forces
        container.rotation = time * 0.8;
    }
    
    /**
     * Animate speed boost effects
     */
    animateSpeedBoost(container) {
        const time = container.animationTime;
        
        // Energetic pulsing
        const energy = 0.9 + Math.sin(time * 10) * 0.1;
        container.scale.set(energy);
        
        // Bright, active alpha
        const active = 0.8 + Math.sin(time * 7) * 0.15;
        container.alpha = active;
        
        // Fast rotation for dynamic feel
        container.rotation = time * 2.0;
    }
    
    /**
     * Animate warning zone effects (pulsing alert)
     */
    animateWarningZone(container) {
        const time = container.animationTime;
        
        // Rapid pulsing to draw attention
        const pulse = 0.85 + Math.sin(time * 15) * 0.15;
        container.scale.set(pulse);
        
        // Flashing alpha for urgency
        const flash = 0.6 + Math.sin(time * 12) * 0.3;
        container.alpha = flash;
        
        // Slow rotation
        container.rotation = time * 0.5;
    }
    
    /**
     * Animate earthquake effects (shaking ground)
     */
    animateEarthquake(container) {
        const time = container.animationTime;
        
        // Violent shaking effect
        const shakeX = Math.sin(time * 20) * 3 + Math.sin(time * 35) * 1.5;
        const shakeY = Math.cos(time * 23) * 3 + Math.cos(time * 38) * 1.5;
        
        if (!container.originalX) {
            container.originalX = container.x;
            container.originalY = container.y;
        }
        
        container.x = container.originalX + shakeX;
        container.y = container.originalY + shakeY;
        
        // Pulsing to show intensity
        const intensity = 0.95 + Math.sin(time * 8) * 0.05;
        container.scale.set(intensity);
        
        // Slight alpha variation
        const rumble = 0.7 + Math.sin(time * 6) * 0.2;
        container.alpha = rumble;
    }
    
    /**
     * Animate smoke cloud (slow swirling drift)
     */
    animateSmoke(container) {
        const time = container.animationTime;

        // Slow rotation for swirling effect
        container.rotation = Math.sin(time * 0.7) * 0.15;

        // Gentle pulsing scale (biased below 1.0 so it never exceeds the radius)
        const pulse = 0.96 + Math.sin(time * 1.2) * 0.04;
        container.scale.set(pulse);

        // Alpha fluctuation to simulate drifting density
        container.alpha = 0.65 + Math.sin(time * 0.9) * 0.1 + Math.sin(time * 2.1) * 0.05;
    }

    /**
     * Get animation speed for different effect types
     */
    getEffectAnimationSpeed(effectType) {
        switch (effectType) {
            case 'EXPLOSION':
            case 'FRAGMENTATION':
                return 0.3; // Fast animation
            case 'ELECTRIC':
                return 0.2; // Very fast animation
            case 'FIRE':
            case 'POISON':
                return 0.1; // Medium animation
            case 'FREEZE':
                return 0.05; // Slow animation
            // Utility effect speeds
            case 'HEAL_ZONE':
            case 'SLOW_FIELD':
                return 0.08; // Slow, gentle animation
            case 'SHIELD_BARRIER':
            case 'SPEED_BOOST':
                return 0.12; // Medium-fast, energetic
            case 'GRAVITY_WELL':
                return 0.06; // Slow, ominous
            case 'SMOKE':
                return 0.04; // Slow, drifting animation
            // Environmental event speeds
            case 'WARNING_ZONE':
                return 0.2; // Fast, urgent pulsing
            case 'EARTHQUAKE':
                return 0.25; // Very fast, violent shaking
            default:
                return 0.1;
        }
    }
    
    /**
     * Update effect visual based on current state
     */
    updateEffectVisual(container, effectData) {
        const progress = effectData.progress || 0;
        const intensity = effectData.intensity || 1.0;
        
        // All effects now use server progress since server handles proper timing
        // The animation methods will override alpha as needed for visual polish
        if (effectData.type === 'EXPLOSION' || effectData.type === 'FRAGMENTATION') {
            // Let animation handle these for visual polish, but server controls lifetime
        } else {
            // Duration effects use server progress
            container.alpha = Math.max(0.3, intensity * (1.0 - progress * 0.5));
        }
    }
    
    /**
     * Fade out effect before removal. Guarantees `callback` is invoked at most once,
     * even if both the per-frame ticker and the safety timeout race.
     */
    fadeOutEffect(container, callback) {
        const fadeSpeed = 0.05;
        let finished = false;

        const finalize = () => {
            if (finished) return;
            finished = true;
            if (container && container.fadeOutFunction) {
                this.removeTickerCallback(container.fadeOutFunction);
                container.fadeOutFunction = null;
            }
            if (callback) callback();
        };

        const fadeOut = () => {
            if (finished) return;
            if (!container || container.destroyed || container.alpha === undefined) {
                // Container was destroyed externally; ensure ticker is removed
                finalize();
                return;
            }

            container.alpha -= fadeSpeed;
            if (container.alpha <= 0) {
                finalize();
            }
        };

        container.fadeOutFunction = fadeOut;
        this.addTickerCallback(fadeOut);

        // Safety timeout to guarantee completion even if the ticker stalls
        this.safeSetTimeout(finalize, 5000);
    }
    
    // Health bar methods removed - health now shown in consolidated HUD
    
    updateUI(gameState) {
        const myPlayer = gameState.players?.find(p => p.id === this.myPlayerId);
        if (!myPlayer) return;
        
        // Update consolidated HUD
        this.updateConsolidatedHUD(myPlayer);
        
        if (!myPlayer.active && myPlayer.respawnTime > 0) {
            this.showRespawnTimer(myPlayer.respawnTime, myPlayer);
        } else {
            this.hideDeathScreen();
        }
        
        this.updateScoreboard(gameState.players);
    }
    
    /**
     * Assign a Text's fill colour only when it actually changes. Reassigning a
     * TextStyle property forces the text to re-rasterise on the next render even
     * if the value is identical, so guarding it avoids needless per-frame glyph
     * rebuilds in the HUD. (PixiJS perf guide: "Text — avoid changing every frame".)
     */
    setTextFill(textObj, color) {
        if (!textObj || textObj._lastFill === color) return;
        textObj._lastFill = color;
        textObj.style.fill = color;
    }

    /**
     * Update the consolidated HUD with player information.
     */
    updateConsolidatedHUD(myPlayer) {
        if (!this.hudContainer) return;
        
        // Update weapon info
        if (this.hudWeaponText) {
            this.hudWeaponText.text = myPlayer.weapon === 0 ? 'Primary' : 'Secondary';
        }
        
        // Update ammo info
        if (this.hudAmmoText) {
            const currentAmmo = myPlayer.ammo || 0;
            const maxAmmo = myPlayer.maxAmmo || 0;
            this.hudAmmoText.text = `${currentAmmo}/${maxAmmo}`;
        }
        
        // Update reload indicator
        if (this.hudReloadText) {
            this.hudReloadText.visible = myPlayer.reloading || false;
        }
        
        // Update team info
        if (this.hudTeamText) {
            const teamNumber = myPlayer.team || 0;
            if (teamNumber === 0) {
                this.hudTeamText.text = 'FFA';
                this.setTextFill(this.hudTeamText, 0x808080); // Gray for FFA
            } else {
                this.hudTeamText.text = teamNumber.toString();
                this.setTextFill(this.hudTeamText, this.getTeamColor(teamNumber)); // Team color
            }
        }

        // Update input source info
        if (this.hudInputText && this.inputManager) {
            if (this.inputManager.gamepad.connected && this.inputManager.inputSource === 'gamepad') {
                this.hudInputText.text = 'Gamepad';
                this.setTextFill(this.hudInputText, 0x44ff44); // Green for gamepad
            } else {
                this.hudInputText.text = 'Keyboard';
                this.setTextFill(this.hudInputText, 0xffffff); // White for keyboard
            }
        }
        
        // Update lives info
        if (this.hudLivesText) {
            const livesRemaining = myPlayer.livesRemaining !== undefined ? myPlayer.livesRemaining : -1;
            if (livesRemaining === -1) {
                // Unlimited lives (default mode) - hide the lives display
                this.hudLivesLabel.visible = false;
                this.hudLivesText.visible = false;
            } else {
                // Show lives display for limited lives modes (including elimination)
                this.hudLivesLabel.visible = true;
                this.hudLivesText.visible = true;
                
                if (livesRemaining === 0) {
                    // Eliminated
                    this.hudLivesText.text = 'ELIM';
                    this.setTextFill(this.hudLivesText, 0xff4444); // Red for eliminated
                } else {
                    // Limited lives (stock mode)
                    this.hudLivesText.text = livesRemaining.toString();
                    this.setTextFill(this.hudLivesText, 0xffaa00); // Orange for limited lives
                }
            }
        }
    }
    
    updateScoreboard(players) {
        const content = document.getElementById('scoreboard-content');
        if (!content || !players) return;
        
        // Throttle DOM updates to ~4 times per second
        const now = performance.now();
        if (this._lastScoreboardUpdate && now - this._lastScoreboardUpdate < 250) return;
        this._lastScoreboardUpdate = now;
        
        // Check if we're in team mode
        const hasTeams = players.some(p => p.team && p.team > 0);
        
        if (hasTeams) {
            this.updateTeamScoreboard(content, players);
        } else {
            this.updateFFAScoreboard(content, players);
        }
    }
    
    updateFFAScoreboard(content, players) {
        // The server-computed total (kills/captures/KOTH/oddball/HQ by score style)
        // is the authoritative ranking; fall back to kills for older payloads.
        const totalOf = p => (p.score && typeof p.score.total === 'number') ? p.score.total : (p.kills || 0);
        const sortedPlayers = [...players].sort((a, b) => totalOf(b) - totalOf(a));
        
        // Check if any player has captures (CTF mode)
        const hasCaptures = players.some(p => (p.captures || 0) > 0);
        // Only show the Score column when objective scoring makes it differ from kills.
        const hasObjectiveScore = players.some(p => totalOf(p) !== (p.kills || 0));
        
        content.innerHTML = `
            <table style="width: 100%; color: white;">
                <thead>
                    <tr>
                        <th>Player</th>
                        ${hasObjectiveScore ? '<th>Score</th>' : ''}
                        <th>Kills</th>
                        <th>Deaths</th>
                        ${hasCaptures ? '<th>Captures</th>' : ''}
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${sortedPlayers.map(player => {
                        const vipIndicator = player.isVip ? ' 👑' : '';
                        return `
                        <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                            <td><span style="color: ${this.getTeamColorCSS(player.team || 0)}">●</span> ${player.name || `Player ${player.id}`}${vipIndicator}</td>
                            ${hasObjectiveScore ? `<td style="font-weight: bold;">${totalOf(player)}</td>` : ''}
                            <td>${player.kills || 0}</td>
                            <td>${player.deaths || 0}</td>
                            ${hasCaptures ? `<td style="color: #FFD700;">${player.captures || 0} 🚩</td>` : ''}
                            <td>${player.active ? 'Alive' : 'Dead'}</td>
                        </tr>
                    `}).join('')}
                </tbody>
            </table>
        `;
    }
    
    updateTeamScoreboard(content, players) {
        // Group players by team
        const teams = {};
        players.forEach(player => {
            const teamNum = player.team || 0;
            if (!teams[teamNum]) {
                teams[teamNum] = [];
            }
            teams[teamNum].push(player);
        });
        
        // Check if any player has captures (CTF mode)
        const hasCaptures = players.some(p => (p.captures || 0) > 0);
        
        // Get effective team scores from game state (includes all scoring mechanisms)
        const effectiveTeamScores = this.gameState?.teamScores || {};
        
        // Sort teams by effective team score (from server rules)
        const sortedTeams = Object.entries(teams).sort((a, b) => {
            const teamA = parseInt(a[0]);
            const teamB = parseInt(b[0]);
            const scoreA = effectiveTeamScores[teamA] || 0;
            const scoreB = effectiveTeamScores[teamB] || 0;
            return scoreB - scoreA;
        });
        
        // Get scoring style info for display
        const scoreStyle = this.gameState?.scoreStyle || 'TOTAL_KILLS';
        const scoreTypeName = this.getScoreTypeName(scoreStyle);
        
        let html = `<div style="color: white;">
            <div style="text-align: center; margin-bottom: 10px; font-size: 12px; color: #aaa;">
                Scoring: ${scoreTypeName}
            </div>`;
        
        sortedTeams.forEach(([teamNum, teamPlayers]) => {
            const teamKills = teamPlayers.reduce((sum, p) => sum + (p.kills || 0), 0);
            const teamDeaths = teamPlayers.reduce((sum, p) => sum + (p.deaths || 0), 0);
            const teamCaptures = teamPlayers.reduce((sum, p) => sum + (p.captures || 0), 0);
            const teamName = teamNum == 0 ? 'Free For All' : `Team ${teamNum}`;
            const teamColor = this.getTeamColorCSS(parseInt(teamNum));
            
            // Get effective team score from server
            const effectiveScore = effectiveTeamScores[parseInt(teamNum)] || 0;
            
            // Build team header with effective score prominently displayed
            const teamStats = hasCaptures 
                ? `Score: ${effectiveScore} | K: ${teamKills} | D: ${teamDeaths} | 🚩: ${teamCaptures}`
                : `Score: ${effectiveScore} | K: ${teamKills} | D: ${teamDeaths}`;
            
            html += `
                <div style="margin-bottom: 15px; border: 1px solid ${teamColor}; border-radius: 5px; padding: 8px;">
                    <h4 style="margin: 0 0 8px 0; color: ${teamColor};">${teamName} (${teamStats})</h4>
                    <table style="width: 100%; font-size: 12px;">
                        ${teamPlayers
                            .sort((a, b) => (b.kills || 0) - (a.kills || 0))
                            .map(player => {
                                const vipIndicator = player.isVip ? ' 👑' : '';
                                return `
                                <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                                    <td style="padding: 2px;">${player.name || `Player ${player.id}`}${vipIndicator}</td>
                                    <td style="padding: 2px; text-align: center;">${player.kills || 0}K</td>
                                    <td style="padding: 2px; text-align: center;">${player.deaths || 0}D</td>
                                    ${hasCaptures ? `<td style="padding: 2px; text-align: center; color: #FFD700;">${player.captures || 0}🚩</td>` : ''}
                                    <td style="padding: 2px; text-align: center;">${player.active ? '✓' : '✗'}</td>
                                </tr>
                            `}).join('')}
                    </table>
                </div>
            `;
        });
        
        html += '</div>';
        content.innerHTML = html;
    }
    
    getScoreTypeName(scoreStyle) {
        switch (scoreStyle) {
            case 'TOTAL_KILLS': return 'Kills Only';
            case 'CAPTURES': return 'Flag Captures Only';
            case 'KOTH_ZONES': return 'Zone Control Only';
            case 'TOTAL': return 'All Scoring Methods';
            default: return 'Kills Only';
        }
    }
    
    updateMinimap() {
        if (!this.hudMinimap || !this.minimapWidth || !this.minimapHeight) return;

        // Throttle minimap updates to ~10Hz instead of 60Hz to reduce CPU/GC pressure
        const now = performance.now();
        if (this._lastMinimapUpdate && now - this._lastMinimapUpdate < 100) return;
        this._lastMinimapUpdate = now;

        // Reuse a single Graphics object for all player dots; clear-and-redraw avoids
        // allocating/destroying PIXI.Graphics every frame, which was a major leak source.
        if (!this.minimapContent) {
            this.minimapContent = new PIXI.Container();
            this.minimapContent.position.set(2, 16); // Below title, within border
            this.hudMinimap.addChild(this.minimapContent);
        }
        if (!this.minimapPlayerDots) {
            this.minimapPlayerDots = new PIXI.Graphics();
            this.minimapContent.addChild(this.minimapPlayerDots);
        }

        const dots = this.minimapPlayerDots;
        dots.clear();

        // Use actual minimap dimensions minus borders and title space
        const mapWidth = this.minimapWidth - 4; // Account for 2px border on each side
        const mapHeight = this.minimapHeight - 18; // Account for borders and title space

        // Use uniform scaling to maintain aspect ratio
        const scale = Math.min(mapWidth / this.worldBounds.width, mapHeight / this.worldBounds.height);

        // Calculate actual scaled world dimensions
        const scaledWorldWidth = this.worldBounds.width * scale;
        const scaledWorldHeight = this.worldBounds.height * scale;

        // Calculate offsets to center the scaled world within the available minimap space
        const offsetX = (mapWidth - scaledWorldWidth) / 2;
        const offsetY = (mapHeight - scaledWorldHeight) / 2;

        // Draw players into the shared Graphics object
        this.players.forEach(player => {
            const data = player.playerData;
            if (!data || !data.active) return;

            const x = (data.x + this.worldBounds.width / 2) * scale + offsetX;
            const y = (-data.y + this.worldBounds.height / 2) * scale + offsetY; // Flip y-axis to match physics world

            const teamColor = this.getTeamColor(data.team || 0);
            const isMe = data.id === this.myPlayerId;
            const radius = isMe ? 2.5 : 1.5;

            dots.circle(x, y, radius).fill(teamColor);

            if (isMe) {
                dots.circle(x, y, radius).stroke({ width: 1, color: 0xffffff });
            }

            if (data.isVip) {
                dots.circle(x, y, radius + 1.5).stroke({ width: 1, color: 0xFFD700 });
            }
        });
    }
    
    showDeathScreen(data) {
        const deathScreen = document.getElementById('death-screen');
        const deathInfo = document.getElementById('death-info');
        
        if (deathScreen && deathInfo) {
            deathScreen.style.display = 'flex';
            deathInfo.textContent = data.killerName ?
                `Eliminated by ${data.killerName}` :
                'You were eliminated';
        }
    }
    
    showRespawnTimer(timeRemaining, playerData) {
        const deathScreen = document.getElementById('death-screen');
        const countdown = document.getElementById('respawn-countdown');
        const deathInfo = document.getElementById('death-info');
        
        if (deathScreen && countdown) {
            deathScreen.style.display = 'flex';
            
            // Check if player is eliminated or out of lives
            const livesRemaining = playerData?.livesRemaining || -1;
            const isEliminated = playerData?.eliminated || false;
            
            if (isEliminated || livesRemaining === 0) {
                // Player is eliminated - show elimination message
                countdown.textContent = 'ELIMINATED';
                countdown.style.color = '#ff4444';
                countdown.style.fontWeight = 'bold';
                
                if (deathInfo) {
                    deathInfo.innerHTML = `
                        <p style="color: #ff6666; margin: 10px 0;">
                            You have been eliminated from this round.
                        </p>
                        <p style="color: #cccccc; font-size: 14px;">
                            Wait for the next round to respawn.
                        </p>
                    `;
                }
            } else {
                // Normal respawn countdown
                countdown.textContent = Math.ceil(timeRemaining);
                countdown.style.color = '#f39c12';
                countdown.style.fontWeight = 'normal';
                
                if (deathInfo && livesRemaining != -1) {
                    const livesText = livesRemaining;
                    deathInfo.innerHTML = `
                        <p style="color: #cccccc; margin: 10px 0;">
                            Lives remaining: <span style="color: #ffaa00; font-weight: bold;">${livesText}</span>
                        </p>
                    `;
                }
            }
        }
    }
    
    hideDeathScreen() {
        const deathScreen = document.getElementById('death-screen');
        if (deathScreen) {
            deathScreen.style.display = 'none';
        }
    }
    
    showConnectionError() {
        this.updateLoadingProgress(0, "Connection lost. Please refresh the page.");
        document.getElementById('loading-screen').style.display = 'flex';
    }
    
    sendPlayerInput(input) {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(JSON.stringify(input));
        }
    }
    
    updateLoadingProgress(percent, status) {
        const progressBar = document.getElementById('loading-progress');
        const statusText = document.getElementById('loading-status');
        
        if (progressBar) progressBar.style.width = `${percent}%`;
        if (statusText) statusText.textContent = status;
    }
    
    hideLoadingScreen() {
        this.safeSetTimeout(() => {
            document.getElementById('loading-screen').style.display = 'none';
            document.getElementById('game-ui').style.display = 'block';
        }, 500);
    }
    
    createCrosshatchGrid() {
        const gridSize = 100; // Grid cell size in pixels
        const worldWidth = this.worldBounds.width;
        const worldHeight = this.worldBounds.height;
        
        // Create a graphics object for the grid
        const grid = new PIXI.Graphics();
        
        // Draw vertical lines
        for (let x = -worldWidth/2; x <= worldWidth/2; x += gridSize) {
            grid.moveTo(x, -worldHeight/2);
            grid.lineTo(x, worldHeight/2);
        }
        
        // Draw horizontal lines
        for (let y = -worldHeight/2; y <= worldHeight/2; y += gridSize) {
            grid.moveTo(-worldWidth/2, y);
            grid.lineTo(worldWidth/2, y);
        }
        
        // Apply stroke for grid lines
        grid.stroke({ width: 1, color: 0x3a5f3f, alpha: 0.4 }); // Semi-transparent green lines
        
        // Add crosshatch pattern (diagonal lines every 4th grid line)
        for (let x = -worldWidth/2; x <= worldWidth/2; x += gridSize * 4) {
            for (let y = -worldHeight/2; y <= worldHeight/2; y += gridSize * 4) {
                // Draw small diagonal crosses
                const crossSize = gridSize * 0.3;
                
                // Top-left to bottom-right diagonal
                grid.moveTo(x - crossSize, y - crossSize);
                grid.lineTo(x + crossSize, y + crossSize);
                
                // Top-right to bottom-left diagonal
                grid.moveTo(x + crossSize, y - crossSize);
                grid.lineTo(x - crossSize, y + crossSize);
            }
        }
        
        // Apply stroke for crosshatch lines
        grid.stroke({ width: 1, color: 0x4a6f4f, alpha: 0.2 }); // Even more subtle diagonal lines
        
        // Add world boundary
        grid.rect(-worldWidth/2, -worldHeight/2, worldWidth, worldHeight).stroke({ width: 3, color: 0x5a8f5f, alpha: 0.8 }); // Thicker, more visible boundary
        
        this.backgroundContainer.addChild(grid);
        
    }
    
    /**
     * Create visual indicators for team spawn areas.
     */
    createTeamSpawnAreas() {
        if (!this.teamAreas || !this.teamAreas.teamAreas) return;
        
        Object.entries(this.teamAreas.teamAreas).forEach(([teamNum, areaData]) => {
            const graphics = new PIXI.Graphics();
            const teamColor = this.getTeamColor(parseInt(teamNum));
            
            // Draw semi-transparent team area
            graphics.rect(
                areaData.minX, 
                areaData.minY, // No inversion needed - coordinates match now!
                areaData.maxX - areaData.minX,
                areaData.maxY - areaData.minY
            ).fill({ color: teamColor, alpha: 0.1 });
            
            graphics.rect(
                areaData.minX, 
                areaData.minY,
                areaData.maxX - areaData.minX,
                areaData.maxY - areaData.minY
            ).stroke({ width: 2, color: teamColor, alpha: 0.5 });
            
            graphics.zIndex = -1; // Behind everything else
            
            this.backgroundContainer.addChild(graphics);
        });
    }
    
    /**
     * Blend two colors together.
     */
    handleResize() {
        this.app.renderer.resize(window.innerWidth, window.innerHeight);
    }
    
    /**
     * Handle WebGL context loss
     */
    handleWebGLContextLost() {
        console.warn('WebGL context lost - stopping game engine');
        // Clear the memory cleanup interval to prevent errors
        if (this.memoryCleanupInterval) {
            clearInterval(this.memoryCleanupInterval);
            this.memoryCleanupInterval = null;
        }
        
        // Stop the ticker to prevent further updates
        if (this.app && this.app.ticker) {
            this.app.ticker.stop();
        }
        
        // Show user message
        this.updateLoadingProgress(0, "Graphics context lost. Please refresh the page.");
    }

    /**
     * Handle WebGL context restoration
     */
    handleWebGLContextRestored() {
        // Restart the ticker
        if (this.app && this.app.ticker) {
            this.app.ticker.start();
        }
        
        // Restart memory cleanup
        if (!this.memoryCleanupInterval) {
            this.memoryCleanupInterval = setInterval(() => this.performMemoryCleanup(), 30000);
        }
        
        // Clear the loading message
        this.updateLoadingProgress(100, "Graphics context restored.");
    }

    /**
     * Performance monitoring for entity management optimization
     */
    logEntityManagementStats() {
        const stats = {
            players: this.players.size,
            projectiles: this.projectiles.size,
            obstacles: this.obstacles.size,
            fieldEffects: this.fieldEffects.size,
            beams: this.beams.size,
            utilityEntities: this.utilityEntities.size,
            flags: this.flags.size,
            kothZones: this.kothZones.size,
            totalEntities: this.players.size + this.projectiles.size + this.obstacles.size + 
                          this.fieldEffects.size + this.beams.size + this.utilityEntities.size + 
                          this.flags.size + this.kothZones.size
        };
        return stats;
    }

    /**
     * Perform periodic memory cleanup to prevent leaks
     */
    performMemoryCleanup() {
        // Safety check - don't perform cleanup if the app is destroyed
        if (!this.app || !this.app.ticker) {
            console.warn('Cannot perform memory cleanup - app is destroyed');
            return;
        }
        
        console.log('Performing memory cleanup...');
        
        // Log entity management stats for monitoring
        this.logEntityManagementStats();
        
        // Clean up any orphaned interpolators
        this.projectileInterpolators.forEach((interpolator, id) => {
            if (!this.projectiles.has(id)) {
                interpolator.destroy();
                this.projectileInterpolators.delete(id);
            }
        });

        // Clean up any field effects with orphaned animation functions
        this.fieldEffects.forEach((effect, id) => {
            if (effect.animationFunction && (!effect.parent || !effect.effectData)) {
                this.removeTickerCallback(effect.animationFunction);
                effect.animationFunction = null;
            }
            if (effect.fadeOutFunction && (!effect.parent || effect.alpha <= 0)) {
                this.removeTickerCallback(effect.fadeOutFunction);
                effect.fadeOutFunction = null;
            }
        });
        
        // Force garbage collection if available (Chrome DevTools)
        if (window.gc) {
            window.gc();
        }
        
        // Log memory usage if available
        if (performance.memory) {
            const memInfo = performance.memory;
            console.log(`Memory usage: ${(memInfo.usedJSHeapSize / 1024 / 1024).toFixed(2)}MB / ${(memInfo.totalJSHeapSize / 1024 / 1024).toFixed(2)}MB`);
        }
    }

    /**
     * Clean up all resources when the game engine is destroyed
     */
    destroy() {
        // Clear the memory cleanup interval
        if (this.memoryCleanupInterval) {
            clearInterval(this.memoryCleanupInterval);
            this.memoryCleanupInterval = null;
        }

        // Clear the lobby countdown interval, if any
        if (this._lobbyCountdownInterval) {
            clearInterval(this._lobbyCountdownInterval);
            this._lobbyCountdownInterval = null;
        }

        // Clear all pending timeouts
        this.pendingTimeouts.forEach(timeoutId => clearTimeout(timeoutId));
        this.pendingTimeouts = [];
        
        // Remove all ticker callbacks
        if (this.app && this.app.ticker) {
            [...this.tickerCallbacks].forEach(callback => {
                this.removeTickerCallback(callback);
            });
            
            // Stop ticker
            this.app.ticker.stop();
        }
        
        // Remove all event listeners
        // In PixiJS v8, access canvas directly from app.canvas instead of renderer.gl.canvas
        if (this.app && this.app.canvas) {
            if (this.eventHandlers.webglContextLost) {
                this.app.canvas.removeEventListener('webglcontextlost', this.eventHandlers.webglContextLost);
            }
            if (this.eventHandlers.webglContextRestored) {
                this.app.canvas.removeEventListener('webglcontextrestored', this.eventHandlers.webglContextRestored);
            }
        }
        
        if (this.eventHandlers.resize) {
            window.removeEventListener('resize', this.eventHandlers.resize);
        }
        
        if (this.eventHandlers.keydown) {
            document.removeEventListener('keydown', this.eventHandlers.keydown);
        }
        
        if (this.eventHandlers.keyup) {
            document.removeEventListener('keyup', this.eventHandlers.keyup);
        }
        
        const closeScoreboardBtn = document.getElementById('close-scoreboard');
        if (closeScoreboardBtn && this.eventHandlers.closeScoreboard) {
            closeScoreboardBtn.removeEventListener('click', this.eventHandlers.closeScoreboard);
        }
        
        // Clear event handlers
        this.eventHandlers = {};
        
        // Clean up DOM elements
        if (this.eventContainer && this.eventContainer.parentNode) {
            this.eventContainer.parentNode.removeChild(this.eventContainer);
            this.eventContainer = null;
        }
        
        // Clean up all interpolators
        this.projectileInterpolators.forEach(interpolator => interpolator.destroy());
        this.projectileInterpolators.clear();
        
        // Clean up all game objects
        this.projectiles.forEach(projectile => this.cleanupProjectileContainer(projectile));
        this.projectiles.clear();
        
        this.players.forEach(player => this.cleanupPlayerSprite(player));
        this.players.clear();
        
        this.fieldEffects.forEach(effect => this.cleanupFieldEffectContainer(effect));
        this.fieldEffects.clear();
        
        this.beams.forEach(beam => this.cleanupBeamContainer(beam));
        this.beams.clear();
        
        this.utilityEntities.forEach(entity => this.cleanupUtilityEntityContainer(entity));
        this.utilityEntities.clear();

        // Clean up obstacles
        this.obstacles.forEach(obstacle => {
            obstacle.obstacleData = null;
            obstacle.destroy({ children: true, context: true });
        });
        this.obstacles.clear();
        
        // Clean up flags
        this.flags.forEach(flag => {
            flag.destroy({ children: true, context: true });
        });
        this.flags.clear();
        
        // Clean up KOTH zones
        this.kothZones.forEach(zone => {
            zone.destroy({ children: true, context: true });
        });
        this.kothZones.clear();
        
        // Close WebSocket connection
        if (this.websocket) {
            this.websocket.close();
            this.websocket = null;
        }
        
        // Clean up InputManager
        if (this.inputManager && typeof this.inputManager.destroy === 'function') {
            this.inputManager.destroy();
        }
        this.inputManager = null;
        
        // Clean up global reference
        if (window.gameEngine === this) {
            window.gameEngine = null;
        }
        
        // Clean up PIXI app
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true, baseTexture: true });
            this.app = null;
        }
        
        // Clear all references
        this.gameContainer = null;
        this.uiContainer = null;
        this.backgroundContainer = null;
        this.nameContainer = null;
        this.camera = null;
        this.gameState = null;
    }
}

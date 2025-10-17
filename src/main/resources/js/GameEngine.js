class GameEngine {
    constructor() {
        this.app = null;
        this.gameContainer = null;
        this.uiContainer = null;
        this.players = new Map();
        this.playerInterpolators = new Map();
        this.projectiles = new Map();
        this.projectileInterpolators = new Map();
        this.obstacles = new Map();
        this.fieldEffects = new Map();
        this.beams = new Map();
        this.utilityEntities = new Map(); // For turrets, barriers, nets, mines, teleport pads
        this.flags = new Map(); // CTF flags
        this.kothZones = new Map(); // King of the Hill zones
        this.teleportConnections = new Map(); // Track teleport pad connections
        this.myPlayerId = null;
        this.gameState = null;
        this.websocket = null;
        this.inputManager = null;
        this.camera = null;
        this.worldBounds = { width: 2000, height: 2000 };
        
        // Game settings
        this.zoomLevel = 1.0;
        this.minZoom = 0.5;
        this.maxZoom = 2.0;
        
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
        this.tickerCallbacks = [];
        
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
            
        } catch (error) {
            console.error('Game initialization failed:', error);
            this.updateLoadingProgress(0, `Error: ${error.message}`);
        }
    }
    
    async initPixiApp() {
        this.app = new PIXI.Application({
            width: window.innerWidth,
            height: window.innerHeight,
            backgroundColor: 0x1a1a1a, // Dark grey for better ordinance visibility
            antialias: true,
            resolution: window.devicePixelRatio || 1,
            autoDensity: true
        });

        document.getElementById('pixi-container').appendChild(this.app.view);

        // Handle WebGL context loss - store handlers for cleanup
        this.eventHandlers.webglContextLost = (event) => {
            console.warn('WebGL context lost');
            event.preventDefault();
            this.handleWebGLContextLost();
        };
        this.app.renderer.gl.canvas.addEventListener('webglcontextlost', this.eventHandlers.webglContextLost);

        this.eventHandlers.webglContextRestored = () => {
            console.log('WebGL context restored');
            this.handleWebGLContextRestored();
        };
        this.app.renderer.gl.canvas.addEventListener('webglcontextrestored', this.eventHandlers.webglContextRestored);

        // Set up interpolation ticker for smooth movement - store reference for cleanup
        const interpolationCallback = (deltaTime) => {
            const dt = deltaTime / 60.0; // Convert to seconds
            
            // Update all projectile interpolators every frame
            this.projectileInterpolators.forEach(interpolator => {
                interpolator.update(deltaTime);
            });
            
            // Animate plasma effects
            this.projectiles.forEach(projectileContainer => {
                if (projectileContainer.isPlasma) {
                    this.animatePlasmaEffects(projectileContainer, deltaTime);
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
        this.tickerCallbacks.push(interpolationCallback);
        this.app.ticker.add(interpolationCallback);

        // Create main containers
        this.backgroundContainer = new PIXI.Container();
        this.gameContainer = new PIXI.Container();
        this.nameContainer = new PIXI.Container(); // Separate container for name labels
        this.uiContainer = new PIXI.Container();

        // Set up proper z-ordering
        this.backgroundContainer.zIndex = 0;
        this.gameContainer.zIndex = 1;
        this.nameContainer.zIndex = 50; // Above game objects but below UI
        this.uiContainer.zIndex = 100;

        this.app.stage.addChild(this.backgroundContainer);
        this.app.stage.addChild(this.gameContainer);
        this.app.stage.addChild(this.nameContainer);
        this.app.stage.addChild(this.uiContainer);

        // Enable sorting for proper z-index handling
        this.app.stage.sortableChildren = true;

        // Handle window resize - store handler for cleanup
        this.eventHandlers.resize = () => {
            this.handleResize();
            this.updateRoundTimerPosition();
        };
        window.addEventListener('resize', this.eventHandlers.resize);
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
        hudBg.beginFill(0x000000, 0.7);
        hudBg.drawRoundedRect(10, 10, 280, 170, 8);
        hudBg.endFill();
        hudBg.lineStyle(2, 0x444444, 0.8);
        hudBg.drawRoundedRect(10, 10, 280, 170, 8);
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
        this.roundTimerContainer.visible = false; // Hidden by default, shown when rounds are enabled
        
        // Background
        const bg = new PIXI.Graphics();
        bg.beginFill(0x000000, 0.8);
        bg.drawRoundedRect(0, 0, 200, 60, 8);
        bg.endFill();
        bg.lineStyle(2, 0xffaa00, 0.9);
        bg.drawRoundedRect(0, 0, 200, 60, 8);
        this.roundTimerContainer.addChild(bg);
        
        // Round number text
        this.roundNumberText = new PIXI.Text('ROUND 1', {
            fontSize: 14,
            fill: 0xffaa00,
            fontWeight: 'bold',
            align: 'center'
        });
        this.roundNumberText.anchor.set(0.5, 0);
        this.roundNumberText.position.set(100, 8);
        this.roundTimerContainer.addChild(this.roundNumberText);
        
        // Timer text (MM:SS)
        this.roundTimerText = new PIXI.Text('05:00', {
            fontSize: 24,
            fill: 0xffffff,
            fontWeight: 'bold',
            align: 'center'
        });
        this.roundTimerText.anchor.set(0.5, 0);
        this.roundTimerText.position.set(100, 28);
        this.roundTimerContainer.addChild(this.roundTimerText);
        
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
            (this.app.screen.width / 2) - 100, // Center horizontally
            10 // Top of screen with padding
        );
    }
    
    /**
     * Update round timer display with current round state.
     */
    updateRoundTimer(roundData) {
        if (!this.roundTimerContainer || !roundData.roundEnabled) {
            if (this.roundTimerContainer) {
                this.roundTimerContainer.visible = false;
            }
            return;
        }
        
        this.roundTimerContainer.visible = true;
        
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
        this.roundTimerText.style.fill = timerColor;
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
        minimapBg.beginFill(0x1a3d1f, 0.8);
        minimapBg.drawRoundedRect(0, 0, minimapWidth, minimapHeight, 4);
        minimapBg.endFill();
        minimapBg.lineStyle(1, 0x2ecc71, 0.6);
        minimapBg.drawRoundedRect(0, 0, minimapWidth, minimapHeight, 4);
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
            hudBg.beginFill(0x000000, 0.7);
            hudBg.drawRoundedRect(10, 10, totalWidth, totalHeight, 8);
            hudBg.endFill();
            hudBg.lineStyle(2, 0x444444, 0.8);
            hudBg.drawRoundedRect(10, 10, totalWidth, totalHeight, 8);
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
        this.tickerCallbacks.push(gameLoopCallback);
        this.app.ticker.add(gameLoopCallback);
    }
    
    setupInput() {
        // Make gameEngine globally accessible for InputManager
        window.gameEngine = this;
        
        this.inputManager = new InputManager();
        this.inputManager.onInputChange = (input) => {
            this.sendPlayerInput(input);
        };
        
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
        playerGraphics.beginFill(0x8c8c8c);
        playerGraphics.drawCircle(0, 0, 20);
        playerGraphics.endFill();
        
        // Direction indicator (weapon/facing)
        playerGraphics.beginFill(0xffffff);
        playerGraphics.drawPolygon([15, 0, 25, -5, 25, 5]);
        playerGraphics.endFill();
        
        this.playerTexture = this.app.renderer.generateTexture(playerGraphics);
        
        // Projectile - simple bullet
        const projectileGraphics = new PIXI.Graphics();
        projectileGraphics.beginFill(0xf39c12);
        projectileGraphics.drawCircle(0, 0, 3);
        projectileGraphics.endFill();
        this.projectileTexture = this.app.renderer.generateTexture(projectileGraphics);

        // Obstacle - boulder
        const boulderGraphics = new PIXI.Graphics();
        boulderGraphics.beginFill(0x808080);
        boulderGraphics.drawCircle(0, 0, 20);
        boulderGraphics.endFill();
        this.boulderTexture = this.app.renderer.generateTexture(boulderGraphics);
        
        // Death marker - tombstone/X
        const deathGraphics = new PIXI.Graphics();
        // Draw a red X
        deathGraphics.lineStyle(4, 0xff4444, 1);
        deathGraphics.moveTo(-15, -15);
        deathGraphics.lineTo(15, 15);
        deathGraphics.moveTo(15, -15);
        deathGraphics.lineTo(-15, 15);
        // Add a circle background
        deathGraphics.lineStyle(2, 0x444444, 0.8);
        deathGraphics.beginFill(0x000000, 0.3);
        deathGraphics.drawCircle(0, 0, 18);
        deathGraphics.endFill();
        this.deathTexture = this.app.renderer.generateTexture(deathGraphics);
    }
    
    
    async connectToServer() {
        const params = new URLSearchParams(window.location.search);
        const gameId = params.get('gameId') || 'default';
        
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/game/${gameId}`;
        
        return new Promise((resolve, reject) => {
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                this.sendPlayerConfiguration();
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
                this.showConnectionError();
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
        this.gameLoopCounter = (this.gameLoopCounter || 0) + 1;
        
        if (this.myPlayerId && this.players.has(this.myPlayerId)) {
            const myPlayer = this.players.get(this.myPlayerId);
            if (myPlayer && myPlayer.playerData) {
                this.camera.targetX = myPlayer.playerData.x;
                this.camera.targetY = -myPlayer.playerData.y; // Invert Y for camera following
            }
            } else {
            // Default camera position if no player yet
            this.camera.targetX = 0;
            this.camera.targetY = 0;
        }
        
        this.camera.x += (this.camera.targetX - this.camera.x) * this.camera.smoothing;
        this.camera.y += (this.camera.targetY - this.camera.y) * this.camera.smoothing;
        
        this.updateCameraTransform();
        this.updateMinimap();
    }
    
    updateCameraTransform() {
        const centerX = this.app.screen.width / 2;
        const centerY = this.app.screen.height / 2;
        
        // Apply camera transform to background, game, and name containers
        [this.backgroundContainer, this.gameContainer, this.nameContainer].forEach(container => {
            container.position.set(centerX, centerY);
            container.scale.set(this.zoomLevel);
            container.pivot.x = this.camera.x;
            container.pivot.y = this.camera.y;
        });
    }
    
    worldToIsometric(worldX, worldY) {
        return { x: worldX, y: -worldY };
    }
    
    worldVelocityToIsometric(vx, vy) {
        return { x: vx, y: -vy };
    }
    
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
        switch (data.type) {
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
                this.handleGameOver(data);
                break;
        }
    }
    
    handleInitialState(data) {
        this.myPlayerId = data.playerId;
        this.worldBounds.width = data.worldWidth || 2000;
        this.worldBounds.height = data.worldHeight || 2000;
        
        // Store team information
        this.teamMode = data.teamMode || false;
        this.teamCount = data.teamCount || 0;
        this.teamAreas = data.teamAreas || null;
        
        // Store terrain information
        this.terrainData = data.terrain || null;

        if (data.obstacles) {
            data.obstacles.forEach(obstacle => {
                this.createObstacle(obstacle);
            });
        }
        
        // Create procedural terrain background
        if (this.terrainData) {
            this.createProceduralTerrain();
        }
        
        // Draw team spawn areas if in team mode
        if (this.teamMode && this.teamAreas) {
            this.createTeamSpawnAreas();
        }
        
        // Create crosshatch grid background with correct world dimensions
        this.createCrosshatchGrid();
        
        // Create minimap with correct aspect ratio
        this.createHUDMinimap();
        this.updateHUDLayout();
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
        }
        
        if (data.projectiles) {
            const currentProjectileIds = new Set();
            
            data.projectiles.forEach(projectileData => {
                currentProjectileIds.add(projectileData.id);
                
                if (this.projectiles.has(projectileData.id)) {
                    this.updateProjectile(projectileData);
                } else {
                    this.createProjectile(projectileData);
                }
            });
            
            for (let [projectileId, projectile] of this.projectiles) {
                if (!currentProjectileIds.has(projectileId)) {
                    this.removeProjectile(projectileId);
                }
            }
        }

        if (data.obstacles) {
            const currentObstacleIds = new Set();
            data.obstacles.forEach(obstacleData => {
                currentObstacleIds.add(obstacleData.id);
                if (this.obstacles.has(obstacleData.id)) {
                    this.updateObstacle(obstacleData);
                } else {
                    this.createObstacle(obstacleData);
                }
                
                // Create health bar for player barriers if it doesn't exist
                if (obstacleData.type === 'PLAYER_BARRIER' && obstacleData.ownerId > 0) {
                    this.obstacleHealthBars = this.obstacleHealthBars || new Map();
                    if (!this.obstacleHealthBars.has(obstacleData.id)) {
                        const healthBarContainer = this.createObstacleHealthBar(obstacleData);
                        this.obstacleHealthBars.set(obstacleData.id, healthBarContainer);
                    }
                }
            });

            for (let [obstacleId, obstacle] of this.obstacles) {
                if (!currentObstacleIds.has(obstacleId)) {
                    this.removeObstacle(obstacleId);
                }
            }
        }
        
        // Handle field effects
        if (data.fieldEffects) {
            const currentFieldEffectIds = new Set();
            data.fieldEffects.forEach(effectData => {
                currentFieldEffectIds.add(effectData.id);
                if (this.fieldEffects.has(effectData.id)) {
                    this.updateFieldEffect(effectData);
                } else {
                    this.createFieldEffect(effectData);
                }
            });

            for (let [effectId, effect] of this.fieldEffects) {
                if (!currentFieldEffectIds.has(effectId)) {
                    this.removeFieldEffect(effectId);
                }
            }
        }
        
        // Handle beams
        if (data.beams) {
            const currentBeamIds = new Set();
            data.beams.forEach(beamData => {
                currentBeamIds.add(beamData.id);
                if (this.beams.has(beamData.id)) {
                    this.updateBeam(beamData);
                } else {
                    this.createBeam(beamData);
                }
            });

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
        
        // Handle utility entities (turrets, barriers, nets, mines, teleport pads)
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
        
        // Handle teleport pads
        if (data.teleportPads) {
            data.teleportPads.forEach(padData => {
                currentEntityIds.add(padData.id);
                if (this.utilityEntities.has(padData.id)) {
                    this.updateUtilityEntity(padData);
                } else {
                    this.createUtilityEntity(padData);
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
    }
    
    /**
     * Create a utility entity
     */
    createUtilityEntity(entityData) {
        const entityContainer = new PIXI.Container();
        const isoPos = this.worldToIsometric(entityData.x, entityData.y);
        entityContainer.position.set(isoPos.x, isoPos.y);
        
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
        
        // Handle teleport pad connections
        if (entityData.type === 'TELEPORT_PAD') {
            this.updateTeleportPadConnections(entityData);
        }
        
        // Enable sorting
        this.gameContainer.sortableChildren = true;
    }
    
    /**
     * Update a utility entity
     */
    updateUtilityEntity(entityData) {
        const entityContainer = this.utilityEntities.get(entityData.id);
        if (!entityContainer) return;
        
        // Update position
        const isoPos = this.worldToIsometric(entityData.x, entityData.y);
        entityContainer.position.set(isoPos.x, isoPos.y);
        
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
        // Create the event display container if it doesn't exist
        if (!this.eventContainer) {
            this.createEventDisplay();
        }
        
        // Create the event element
        const eventElement = document.createElement('div');
        eventElement.className = 'game-event';
        eventElement.style.color = event.color || '#FFFFFF';
        
        // Parse and render colored text
        // Format: <color:#RRGGBB>Text</color>
        const coloredMessage = this.parseColoredMessage(event.message);
        if (coloredMessage) {
            eventElement.innerHTML = coloredMessage;
        } else {
            eventElement.textContent = event.message;
        }
        
        // Add category-specific styling
        if (event.category) {
            eventElement.classList.add('event-' + event.category.toLowerCase());
        }
        
        // Add to container (prepend so newest events appear at top)
        this.eventContainer.prepend(eventElement);
        
        // Animate in from the right
        eventElement.style.opacity = '0';
        eventElement.style.transform = 'translateX(20px)';
        requestAnimationFrame(() => {
            eventElement.style.transition = 'all 0.3s ease-out';
            eventElement.style.opacity = '1';
            eventElement.style.transform = 'translateX(0)';
        });
        
        // Auto-remove after display duration (or default 3 seconds)
        const displayDuration = event.displayDuration || 3000;
        this.safeSetTimeout(() => {
            this.removeGameEvent(eventElement);
        }, displayDuration);
        
        // Limit the number of visible events
        const maxEvents = 6;
        const events = this.eventContainer.children;
        if (events.length > maxEvents) {
            for (let i = maxEvents; i < events.length; i++) {
                this.removeGameEvent(events[i]);
            }
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
        // Create the event container
        this.eventContainer = document.createElement('div');
        this.eventContainer.id = 'game-events';
        this.eventContainer.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            width: 380px;
            max-width: 35vw;
            z-index: 1000;
            pointer-events: none;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
        `;
        
        document.body.appendChild(this.eventContainer);
        
        // Add CSS styles for events
        const style = document.createElement('style');
        style.textContent = `
            .game-event {
                background: rgba(0, 0, 0, 0.85);
                border: 1px solid rgba(255, 255, 255, 0.3);
                border-radius: 4px;
                padding: 6px 10px;
                margin-bottom: 4px;
                font-weight: 500;
                text-align: left;
                font-size: 12px;
                line-height: 1.3;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
                backdrop-filter: blur(3px);
                -webkit-backdrop-filter: blur(3px);
                max-width: 100%;
                word-wrap: break-word;
            }
            
            .game-event.event-kill {
                border-left: 3px solid #FF4444;
                border-color: rgba(255, 68, 68, 0.6);
            }
            
            .game-event.event-capture {
                border-left: 3px solid #00FF88;
                border-color: rgba(0, 255, 136, 0.6);
            }
            
            .game-event.event-system {
                border-left: 3px solid #FFAA00;
                border-color: rgba(255, 170, 0, 0.6);
            }
            
            .game-event.event-achievement {
                border-left: 3px solid #FFD700;
                border-color: rgba(255, 215, 0, 0.6);
                background: linear-gradient(135deg, rgba(255, 215, 0, 0.05), rgba(0, 0, 0, 0.85));
            }
            
            .game-event.event-warning {
                border-left: 3px solid #FF8800;
                border-color: rgba(255, 136, 0, 0.6);
            }
            
            .game-event.event-info {
                border-left: 3px solid #00AAFF;
                border-color: rgba(0, 170, 255, 0.6);
            }
        `;
        document.head.appendChild(style);
    }
    
    removeGameEvent(eventElement) {
        if (eventElement && eventElement.parentNode) {
            eventElement.style.transition = 'all 0.3s ease-in';
            eventElement.style.opacity = '0';
            eventElement.style.transform = 'translateX(30px) scale(0.95)';
            this.safeSetTimeout(() => {
                if (eventElement.parentNode) {
                    eventElement.parentNode.removeChild(eventElement);
                }
            }, 300);
        }
    }
    
    /**
     * Handle round end event - display scores
     */
    handleRoundEnd(data) {
        console.log('Round ended:', data);
        this.showRoundEndScreen(data);
    }
    
    /**
     * Handle round start event - clear round end screen
     */
    handleRoundStart(data) {
        console.log('Round started:', data);
        this.hideRoundEndScreen();
    }
    
    /**
     * Show round end screen with scores
     */
    showRoundEndScreen(data) {
        // Create or get round end overlay
        let overlay = document.getElementById('round-end-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'round-end-overlay';
            overlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.85);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
                backdrop-filter: blur(8px);
                -webkit-backdrop-filter: blur(8px);
            `;
            document.body.appendChild(overlay);
        }
        
        // Create content container
        const content = document.createElement('div');
        content.style.cssText = `
            background: linear-gradient(135deg, rgba(20, 20, 30, 0.95), rgba(40, 40, 60, 0.95));
            border: 2px solid rgba(255, 170, 0, 0.6);
            border-radius: 12px;
            padding: 40px;
            max-width: 800px;
            width: 90%;
            max-height: 80vh;
            overflow-y: auto;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
        `;
        
        // Title
        const title = document.createElement('h1');
        title.textContent = `ROUND ${data.round} COMPLETE`;
        title.style.cssText = `
            color: #ffaa00;
            text-align: center;
            margin: 0 0 30px 0;
            font-size: 36px;
            text-shadow: 0 2px 10px rgba(255, 170, 0, 0.5);
        `;
        content.appendChild(title);
        
        // Scores
        if (data.scores && data.scores.length > 0) {
            const scoresContainer = document.createElement('div');
            scoresContainer.style.cssText = `
                background: rgba(0, 0, 0, 0.3);
                border-radius: 8px;
                padding: 20px;
                margin-bottom: 20px;
            `;
            
            // Sort scores by kills (descending)
            const sortedScores = [...data.scores].sort((a, b) => b.kills - a.kills);
            
            // Group by team if team mode
            const hasTeams = sortedScores.some(score => score.team > 0);
            
            if (hasTeams) {
                // Team-based display
                const teams = {};
                sortedScores.forEach(score => {
                    const teamNum = score.team || 0;
                    if (!teams[teamNum]) {
                        teams[teamNum] = [];
                    }
                    teams[teamNum].push(score);
                });
                
                Object.entries(teams).forEach(([teamNum, players]) => {
                    const teamHeader = document.createElement('h3');
                    teamHeader.textContent = teamNum == 0 ? 'No Team' : `Team ${teamNum}`;
                    teamHeader.style.cssText = `
                        color: ${this.getTeamColorCSS(parseInt(teamNum))};
                        margin: 15px 0 10px 0;
                        font-size: 20px;
                    `;
                    scoresContainer.appendChild(teamHeader);
                    
                    players.forEach(score => {
                        scoresContainer.appendChild(this.createScoreRow(score));
                    });
                });
            } else {
                // FFA display
                sortedScores.forEach((score, index) => {
                    scoresContainer.appendChild(this.createScoreRow(score, index + 1));
                });
            }
            
            content.appendChild(scoresContainer);
        }
        
        // Next round info
        const nextRoundText = document.createElement('p');
        nextRoundText.textContent = `Next round starts in ${Math.ceil(data.restDuration)} seconds...`;
        nextRoundText.style.cssText = `
            color: #ffaa00;
            text-align: center;
            font-size: 18px;
            margin: 20px 0 0 0;
            animation: pulse 2s ease-in-out infinite;
        `;
        content.appendChild(nextRoundText);
        
        // Add pulse animation
        if (!document.getElementById('round-end-styles')) {
            const style = document.createElement('style');
            style.id = 'round-end-styles';
            style.textContent = `
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.6; }
                }
            `;
            document.head.appendChild(style);
        }
        
        overlay.innerHTML = '';
        overlay.appendChild(content);
        overlay.style.display = 'flex';
    }
    
    /**
     * Create a score row for a player
     */
    createScoreRow(score, rank = null) {
        const row = document.createElement('div');
        row.style.cssText = `
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 15px;
            margin: 5px 0;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 6px;
            border-left: 3px solid ${this.getTeamColorCSS(score.team)};
        `;
        
        const nameSection = document.createElement('div');
        nameSection.style.cssText = `
            flex: 1;
            color: #ffffff;
            font-size: 16px;
            font-weight: bold;
        `;
        nameSection.textContent = (rank ? `#${rank} ` : '') + score.playerName;
        
        const stats = document.createElement('div');
        stats.style.cssText = `
            display: flex;
            gap: 20px;
            color: #cccccc;
            font-size: 14px;
        `;
        
        const kills = document.createElement('span');
        kills.style.color = '#4ade80';
        kills.textContent = `${score.kills || 0} K`;
        
        const deaths = document.createElement('span');
        deaths.style.color = '#f87171';
        deaths.textContent = `${score.deaths || 0} D`;
        
        // Add captures if player has any
        if (score.captures && score.captures > 0) {
            const captures = document.createElement('span');
            captures.style.color = '#FFD700';
            captures.textContent = `${score.captures || 0} 🚩`;
            stats.appendChild(captures);
        }
        
        const kd = document.createElement('span');
        kd.style.color = '#fbbf24';
        kd.textContent = `${((score.kills || 0) / Math.max(1, (score.deaths || 0))).toFixed(2)} K/D`;
        
        stats.appendChild(kills);
        stats.appendChild(deaths);
        stats.appendChild(kd);
        
        row.appendChild(nameSection);
        row.appendChild(stats);
        
        return row;
    }
    
    /**
     * Hide round end screen
     */
    hideRoundEndScreen() {
        const overlay = document.getElementById('round-end-overlay');
        if (overlay) {
            overlay.style.display = 'none';
        }
    }
    
    /**
     * Handle game over event - show victory screen
     */
    handleGameOver(data) {
        console.log('Game Over:', data);
        this.showGameOverScreen(data);
    }
    
    /**
     * Show game over screen with final results
     */
    showGameOverScreen(data) {
        // Create or get game over overlay
        let overlay = document.getElementById('game-over-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'game-over-overlay';
            overlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.9);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 20000;
                backdrop-filter: blur(10px);
                -webkit-backdrop-filter: blur(10px);
            `;
            document.body.appendChild(overlay);
        }
        
        // Create content container
        const content = document.createElement('div');
        content.style.cssText = `
            background: linear-gradient(135deg, rgba(30, 30, 40, 0.98), rgba(50, 50, 70, 0.98));
            border: 3px solid #FFD700;
            border-radius: 15px;
            padding: 50px;
            max-width: 900px;
            width: 90%;
            max-height: 90vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(255, 215, 0, 0.3);
            text-align: center;
        `;
        
        // Victory title
        const title = document.createElement('h1');
        title.textContent = '🏆 GAME OVER';
        title.style.cssText = `
            color: #FFD700;
            font-size: 48px;
            margin: 0 0 20px 0;
            text-shadow: 0 0 20px rgba(255, 215, 0, 0.8);
            animation: pulse 2s ease-in-out infinite;
        `;
        content.appendChild(title);
        
        // Victory message
        const message = document.createElement('p');
        message.textContent = data.message || 'The battle has ended!';
        message.style.cssText = `
            color: #ffffff;
            font-size: 24px;
            margin: 20px 0 40px 0;
            font-weight: bold;
        `;
        content.appendChild(message);
        
        // Victory condition info
        const vcInfo = document.createElement('p');
        const vcName = this.getVictoryConditionName(data.victoryCondition);
        vcInfo.textContent = `Victory Condition: ${vcName}`;
        vcInfo.style.cssText = `
            color: #aaa;
            font-size: 16px;
            margin: 0 0 30px 0;
        `;
        content.appendChild(vcInfo);
        
        // Final scores
        if (data.finalScores && data.finalScores.length > 0) {
            const scoresTitle = document.createElement('h2');
            scoresTitle.textContent = 'Final Scores';
            scoresTitle.style.cssText = `
                color: #FFD700;
                font-size: 28px;
                margin: 30px 0 20px 0;
            `;
            content.appendChild(scoresTitle);
            
            const scoresContainer = document.createElement('div');
            scoresContainer.style.cssText = `
                background: rgba(0, 0, 0, 0.4);
                border-radius: 10px;
                padding: 20px;
                margin: 20px 0;
            `;
            
            // Sort scores by score value
            const sortedScores = [...data.finalScores].sort((a, b) => b.score - a.score);
            
            sortedScores.forEach((score, index) => {
                const scoreRow = this.createFinalScoreRow(score, index + 1, data);
                scoresContainer.appendChild(scoreRow);
            });
            
            content.appendChild(scoresContainer);
        }
        
        // Return to lobby button
        const lobbyButton = document.createElement('button');
        lobbyButton.textContent = '← Return to Lobby';
        lobbyButton.style.cssText = `
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            border: none;
            padding: 15px 40px;
            font-size: 18px;
            border-radius: 8px;
            cursor: pointer;
            margin-top: 30px;
            transition: transform 0.2s, box-shadow 0.2s;
        `;
        lobbyButton.onmouseover = () => {
            lobbyButton.style.transform = 'scale(1.05)';
            lobbyButton.style.boxShadow = '0 5px 20px rgba(102, 126, 234, 0.4)';
        };
        lobbyButton.onmouseout = () => {
            lobbyButton.style.transform = 'scale(1)';
            lobbyButton.style.boxShadow = 'none';
        };
        lobbyButton.onclick = () => {
            window.location.href = '/lobby.html';
        };
        content.appendChild(lobbyButton);
        
        overlay.innerHTML = '';
        overlay.appendChild(content);
        overlay.style.display = 'flex';
    }
    
    /**
     * Create a score row for the game over screen
     */
    createFinalScoreRow(score, rank, gameOverData) {
        const row = document.createElement('div');
        row.style.cssText = `
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 15px 20px;
            margin: 8px 0;
            background: ${rank === 1 ? 'rgba(255, 215, 0, 0.15)' : 'rgba(255, 255, 255, 0.05)'};
            border-radius: 8px;
            border-left: 4px solid ${this.getRankColor(rank)};
        `;
        
        // Rank and name
        const nameSection = document.createElement('div');
        nameSection.style.cssText = `
            display: flex;
            align-items: center;
            gap: 15px;
            flex: 1;
        `;
        
        const rankBadge = document.createElement('span');
        rankBadge.textContent = this.getRankBadge(rank);
        rankBadge.style.cssText = `
            font-size: 24px;
            min-width: 40px;
        `;
        nameSection.appendChild(rankBadge);
        
        const nameText = document.createElement('span');
        if (score.team !== undefined) {
            nameText.textContent = `Team ${score.team}`;
            nameText.style.color = this.getTeamColorCSS(score.team);
        } else {
            nameText.textContent = score.playerName || `Player ${score.playerId}`;
            nameText.style.color = '#ffffff';
        }
        nameText.style.cssText += `
            font-size: 20px;
            font-weight: bold;
        `;
        nameSection.appendChild(nameText);
        
        row.appendChild(nameSection);
        
        // Stats
        const stats = document.createElement('div');
        stats.style.cssText = `
            display: flex;
            gap: 25px;
            color: #cccccc;
            font-size: 16px;
        `;
        
        const scoreSpan = document.createElement('span');
        scoreSpan.style.color = '#FFD700';
        scoreSpan.style.fontWeight = 'bold';
        scoreSpan.style.fontSize = '20px';
        scoreSpan.textContent = `${score.score} pts`;
        stats.appendChild(scoreSpan);
        
        const killsSpan = document.createElement('span');
        killsSpan.style.color = '#4ade80';
        killsSpan.textContent = `${score.kills} K`;
        stats.appendChild(killsSpan);
        
        const deathsSpan = document.createElement('span');
        deathsSpan.style.color = '#f87171';
        deathsSpan.textContent = `${score.deaths} D`;
        stats.appendChild(deathsSpan);
        
        if (score.captures > 0) {
            const capturesSpan = document.createElement('span');
            capturesSpan.style.color = '#FFD700';
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
        sprite.anchor.set(0.5);
        
        const isoPos = this.worldToIsometric(playerData.x, playerData.y);
        sprite.position.set(isoPos.x, isoPos.y);
        sprite.rotation = playerData.rotation || 0;
        
        // Color players based on their team
        const playerColor = this.getPlayerColor(playerData);
        sprite.tint = playerColor;
        
        // Make sprite visible but normal size
        sprite.scale.set(1.0); // Normal size
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
            deathMarker.position.set(isoPos.x, isoPos.y);
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
        
        // Position name label below health bar
        nameLabel.position.set(isoPos.x, isoPos.y - 25);
        
        // Store reference to name label on sprite for easy access
        sprite.nameLabel = nameLabel;
        
        // Add name label to separate container
        this.nameContainer.addChild(nameLabel);
        
        // Set player z-index to ensure it's on top
        sprite.zIndex = 10;
        
        sprite.playerData = playerData;
        this.players.set(playerData.id, sprite);
        this.gameContainer.addChild(sprite);
        
        // Create player interpolator
        const isLocalPlayer = playerData.id === this.myPlayerId;
        const interpolator = new PlayerInterpolator(sprite, playerData.id, isLocalPlayer);
        this.playerInterpolators.set(playerData.id, interpolator);
        
        // Enable sorting for this container
        this.gameContainer.sortableChildren = true;
    }
    
    updatePlayer(playerData) {
        const sprite = this.players.get(playerData.id);
        if (!sprite) return;

        // Enable interpolation for smoothness, but keep prediction disabled
        const USE_INTERPOLATION = false;
        
        if (USE_INTERPOLATION) {
            // Use interpolator for smooth movement
            const interpolator = this.playerInterpolators.get(playerData.id);
            if (interpolator) {
                // Convert server position from world to screen coordinates
                const isoPos = this.worldToIsometric(playerData.x, playerData.y);
                interpolator.updateFromServer(isoPos.x, isoPos.y, playerData.rotation || 0);
            } else {
                // Fallback to direct position update if no interpolator
                const isoPos = this.worldToIsometric(playerData.x, playerData.y);
                sprite.position.set(isoPos.x, isoPos.y);
                sprite.rotation = -(playerData.rotation || 0);
            }
        } else {
            // Direct position update - no interpolation
            const isoPos = this.worldToIsometric(playerData.x, playerData.y);
            sprite.position.set(isoPos.x, isoPos.y);
            sprite.rotation = -(playerData.rotation || 0);
        }
        
        // Handle death marker logic
        const isDead = !playerData.active && playerData.respawnTime > 0;
        
        // Show/hide player sprite and death marker
        sprite.visible = playerData.active;
        if (sprite.deathMarker) {
            sprite.deathMarker.visible = isDead;
            if (isDead) {
                // Position death marker at death location
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
        
        // Clean up player interpolator
        const interpolator = this.playerInterpolators.get(playerId);
        if (interpolator) {
            interpolator.destroy();
            this.playerInterpolators.delete(playerId);
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
                sprite.healthBar.healthBg.destroy();
            }
            if (sprite.healthBar.healthFill) {
                sprite.healthBar.healthFill.destroy();
            }
            sprite.healthBar.destroy({ children: true });
            sprite.healthBar = null;
        }
        
        // Remove and destroy reload indicator
        if (sprite.reloadIndicator) {
            if (sprite.reloadIndicator.parent) {
                sprite.reloadIndicator.parent.removeChild(sprite.reloadIndicator);
            }
            // Clean up reload indicator components
            if (sprite.reloadIndicator.background) {
                sprite.reloadIndicator.background.destroy();
            }
            if (sprite.reloadIndicator.reloadText) {
                sprite.reloadIndicator.reloadText.destroy();
            }
            sprite.reloadIndicator.destroy({ children: true });
            sprite.reloadIndicator = null;
        }
        
        // Remove and destroy death marker
        if (sprite.deathMarker) {
            if (sprite.deathMarker.parent) {
                sprite.deathMarker.parent.removeChild(sprite.deathMarker);
            }
            sprite.deathMarker.destroy();
            sprite.deathMarker = null;
        }
        
        // Remove and destroy power-up container
        if (sprite.powerUpContainer) {
            if (sprite.powerUpContainer.parent) {
                sprite.powerUpContainer.parent.removeChild(sprite.powerUpContainer);
            }
            sprite.powerUpContainer.destroy({ children: true });
            sprite.powerUpContainer = null;
        }
        
        // Clear player data reference
        sprite.playerData = null;
        
        // Destroy the main sprite
        sprite.destroy();
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
        healthBg.beginFill(config.bgColor);
        healthBg.drawRoundedRect(-config.width/2, 0, config.width, config.height, config.cornerRadius);
        healthBg.endFill();
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.beginFill(config.fillColor);
        healthFill.drawRoundedRect(-config.width/2, 0, config.width, config.height, config.cornerRadius);
        healthFill.endFill();
        healthBarContainer.addChild(healthFill);
        
        // Store references for updates
        healthBarContainer.healthBg = healthBg;
        healthBarContainer.healthFill = healthFill;
        healthBarContainer.config = config;
        
        // Position above entity
        const isoPos = this.worldToIsometric(entityData.x, entityData.y);
        healthBarContainer.position.set(isoPos.x, isoPos.y - (config.yOffset || 0));
        
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
        
        if (!healthBarContainer.visible) return;
        
        // Update health bar fill
        healthBarContainer.healthFill.clear();
        
        // Color based on health level (for players)
        let healthColor = config.fillColor;
        if (config.dynamicColor && healthPercent < 0.3) {
            healthColor = 0xe74c3c; // Red
        } else if (config.dynamicColor && healthPercent < 0.6) {
            healthColor = 0xf39c12; // Orange
        }
        
        healthBarContainer.healthFill.beginFill(healthColor);
        healthBarContainer.healthFill.drawRoundedRect(
            -config.width/2, 0, 
            config.width * healthPercent, 
            config.height, 
            config.cornerRadius
        );
        healthBarContainer.healthFill.endFill();
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
     * Create health bar for an obstacle
     */
    createObstacleHealthBar(obstacleData) {
        return this.createHealthBar(obstacleData, {
            width: 30,
            height: 4,
            yOffset: 25,
            bgColor: 0x222222,
            fillColor: 0x4a90e2,
            cornerRadius: 1,
            showWhenFull: false,
            dynamicColor: false
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
        background.beginFill(0x000000, 0.7); // Semi-transparent black background
        background.drawCircle(0, 0, 12); // 12 pixel radius
        background.endFill();
        background.lineStyle(2, 0xff4444, 1.0); // Red border
        background.drawCircle(0, 0, 12);
        reloadContainer.addChild(background);
        
        // Create the "R" text
        const reloadText = new PIXI.Text('R', {
            fontSize: 14,
            fill: 0xff4444, // Red color
            fontWeight: 'bold',
            fontFamily: 'Arial'
        });
        reloadText.anchor.set(0.5);
        reloadText.position.set(0, 0);
        reloadContainer.addChild(reloadText);
        
        // Position above player (will be updated in updatePlayer)
        const isoPos = this.worldToIsometric(playerData.x, playerData.y);
        reloadContainer.position.set(isoPos.x, isoPos.y - 50); // Above health bar
        
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
            this.gameContainer.addChild(sprite.powerUpContainer);
        }
        
        // Update position to match player
        sprite.powerUpContainer.position.set(sprite.x, sprite.y);
        sprite.powerUpContainer.visible = playerData.active;
        
        // Parse active power-ups and create/update visuals
        if (activePowerUps.length > 0) {
            this.updatePowerUpVisuals(sprite.powerUpContainer, activePowerUps, sprite);
        } else {
            // Clear all power-up effects if no active power-ups
            sprite.powerUpContainer.removeChildren();
        }
    }
    
    /**
     * Create/update power-up visual effects based on render hints.
     */
    updatePowerUpVisuals(container, activePowerUps, sprite) {
        // Parse render hints: "effect_name:#COLOR:animation_type:show_icon:Display Name"
        const effects = activePowerUps.map(hint => {
            const parts = hint.split(':');
            return {
                name: parts[0] || 'unknown',
                color: parseInt(parts[1]?.replace('#', '') || 'FFFFFF', 16),
                animation: parts[2] || 'pulse',
                showIcon: parts[3] === 'true',
                displayName: parts[4] || ''
            };
        });
        
        // Clear existing visuals
        container.removeChildren();
        
        // Create visual effect for each active power-up
        effects.forEach((effect, index) => {
            // Create aura/glow around player
            const aura = new PIXI.Graphics();
            
            // Draw aura based on effect type
            if (effect.animation === 'sparkle' || effect.animation === 'pulse') {
                // Pulsing glow ring
                const time = Date.now() * 0.003;
                const pulseSize = 20 + Math.sin(time + index) * 5;
                
                aura.lineStyle(3, effect.color, 0.6);
                aura.drawCircle(0, 0, pulseSize);
                
                // Add inner particles/sparkles
                for (let i = 0; i < 8; i++) {
                    const angle = (i / 8) * Math.PI * 2 + time;
                    const distance = 25;
                    const x = Math.cos(angle) * distance;
                    const y = Math.sin(angle) * distance;
                    
                    aura.beginFill(effect.color, 0.8);
                    aura.drawCircle(x, y, 2);
                    aura.endFill();
                }
            } else if (effect.animation === 'shield') {
                // Hexagonal shield pattern
                const time = Date.now() * 0.002;
                const size = 22 + Math.sin(time) * 2;
                
                aura.lineStyle(2, effect.color, 0.7);
                for (let i = 0; i < 6; i++) {
                    const angle = (i / 6) * Math.PI * 2;
                    const x = Math.cos(angle) * size;
                    const y = Math.sin(angle) * size;
                    if (i === 0) {
                        aura.moveTo(x, y);
                    } else {
                        aura.lineTo(x, y);
                    }
                }
                aura.closePath();
            } else if (effect.animation === 'slow') {
                // Slow debuff - dripping effect
                const time = Date.now() * 0.002;
                aura.beginFill(effect.color, 0.5);
                for (let i = 0; i < 6; i++) {
                    const angle = (i / 6) * Math.PI * 2 + time;
                    const x = Math.cos(angle) * 18;
                    const y = Math.sin(angle) * 18 + Math.sin(time * 2 + i) * 3;
                    aura.drawCircle(x, y, 3);
                }
                aura.endFill();
            }
            
            // Store animation type for update loop
            aura.animationType = effect.animation;
            aura.effectColor = effect.color;
            aura.effectIndex = index;
            
            container.addChild(aura);
            
            // Add icon badge if requested (for player's own view)
            if (effect.showIcon && sprite.playerData.id === this.myPlayerId) {
                const badge = this.createPowerUpBadge(effect, index);
                container.addChild(badge);
            }
        });
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
        bg.beginFill(0x000000, 0.7);
        bg.drawCircle(0, 0, 10);
        bg.endFill();
        bg.lineStyle(2, effect.color, 1.0);
        bg.drawCircle(0, 0, 10);
        badge.addChild(bg);
        
        // Icon letter (first letter of effect name)
        const letter = effect.displayName.charAt(0) || '?';
        const text = new PIXI.Text(letter, {
            fontSize: 12,
            fill: effect.color,
            fontWeight: 'bold'
        });
        text.anchor.set(0.5);
        badge.addChild(text);
        
        return badge;
    }
    
    createProjectile(projectileData) {
        // Create main projectile container
        const projectileContainer = new PIXI.Container();
        
        // Convert world coordinates to screen coordinates once
        const isoPos = this.worldToIsometric(projectileData.x, projectileData.y);
        projectileContainer.position.set(isoPos.x, isoPos.y);
        
        // Create the main projectile sprite
        const sprite = new PIXI.Sprite(this.projectileTexture);
        sprite.anchor.set(0.5);
        
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
        // Convert server velocity from world coordinates to isometric screen coordinates
        const worldVel = { x: projectileData.vx || 0, y: projectileData.vy || 0 };
        const isoVel = this.worldVelocityToIsometric(worldVel.x, worldVel.y);
        
        // Initialize interpolator with screen coordinates (already converted)
        const interpolator = new ProjectileInterpolator(projectileContainer, isoVel);
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
        // Create multiple layers for plasma effect
        
        // Outer electric field
        const outerGlow = new PIXI.Graphics();
        outerGlow.beginFill(0x4444ff, 0.3);
        outerGlow.drawCircle(0, 0, 8);
        outerGlow.endFill();
        outerGlow.zIndex = -2;
        projectileContainer.addChild(outerGlow);
        
        // Middle energy field with pulsing
        const middleGlow = new PIXI.Graphics();
        middleGlow.beginFill(0x6666ff, 0.5);
        middleGlow.drawCircle(0, 0, 5);
        middleGlow.endFill();
        middleGlow.zIndex = -1;
        projectileContainer.addChild(middleGlow);
        
        // Inner core glow
        const innerGlow = new PIXI.Graphics();
        innerGlow.beginFill(0xaaaaff, 0.7);
        innerGlow.drawCircle(0, 0, 3);
        innerGlow.endFill();
        innerGlow.zIndex = 0;
        projectileContainer.addChild(innerGlow);
        
        // Electric arcs around the plasma
        const electricArcs = new PIXI.Graphics();
        electricArcs.zIndex = 1;
        projectileContainer.addChild(electricArcs);
        
        // Store references for animation
        projectileContainer.plasmaEffects = {
            outerGlow: outerGlow,
            middleGlow: middleGlow,
            innerGlow: innerGlow,
            electricArcs: electricArcs,
            animationTime: 0,
            arcUpdateTimer: 0
        };
        
        // Mark for plasma animation
        projectileContainer.isPlasma = true;
    }
    
    /**
     * Animate plasma effects for buzzing/glowing appearance
     */
    animatePlasmaEffects(projectileContainer, deltaTime) {
        if (!projectileContainer.plasmaEffects) return;
        
        const effects = projectileContainer.plasmaEffects;
        effects.animationTime += deltaTime * 0.05; // Slow down animation speed
        effects.arcUpdateTimer += deltaTime;
        
        const time = effects.animationTime;
        
        // Pulsing glow effects
        const pulseOuter = 0.8 + Math.sin(time * 8) * 0.3; // Fast pulse
        const pulseMiddle = 0.9 + Math.sin(time * 12) * 0.2; // Faster pulse
        const pulseInner = 0.95 + Math.sin(time * 15) * 0.1; // Very fast pulse
        
        effects.outerGlow.alpha = pulseOuter * 0.3;
        effects.middleGlow.alpha = pulseMiddle * 0.5;
        effects.innerGlow.alpha = pulseInner * 0.7;
        
        // Scale pulsing for energy field effect
        const scaleOuter = 1.0 + Math.sin(time * 6) * 0.2;
        const scaleMiddle = 1.0 + Math.sin(time * 10) * 0.15;
        const scaleInner = 1.0 + Math.sin(time * 14) * 0.1;
        
        effects.outerGlow.scale.set(scaleOuter);
        effects.middleGlow.scale.set(scaleMiddle);
        effects.innerGlow.scale.set(scaleInner);
        
        // Update electric arcs every few frames for buzzing effect
        if (effects.arcUpdateTimer > 0.1) { // Update every 100ms for buzzing
            this.updatePlasmaArcs(effects.electricArcs);
            effects.arcUpdateTimer = 0;
        }
        
        // Slight rotation for dynamic feel
        effects.outerGlow.rotation = time * 2;
        effects.middleGlow.rotation = -time * 3;
        effects.innerGlow.rotation = time * 4;
    }
    
    /**
     * Update electric arcs around plasma for buzzing effect
     */
    updatePlasmaArcs(electricArcs) {
        electricArcs.clear();
        electricArcs.lineStyle(1, 0xaaaaff, 0.8);
        
        // Draw 3-5 random electric arcs
        const numArcs = 3 + Math.floor(Math.random() * 3);
        
        for (let i = 0; i < numArcs; i++) {
            const startAngle = Math.random() * Math.PI * 2;
            const arcLength = Math.PI * 0.3 + Math.random() * Math.PI * 0.4; // 54-126 degrees
            const radius = 6 + Math.random() * 4; // 6-10 pixel radius
            
            // Create zigzag electric arc
            const steps = 5 + Math.floor(Math.random() * 3); // 5-7 steps
            let currentAngle = startAngle;
            const angleStep = arcLength / steps;
            
            let lastX = Math.cos(currentAngle) * radius;
            let lastY = Math.sin(currentAngle) * radius;
            
            for (let j = 1; j <= steps; j++) {
                currentAngle += angleStep;
                
                // Add random jitter for electric effect
                const jitterRadius = radius + (Math.random() - 0.5) * 3;
                const jitterAngle = currentAngle + (Math.random() - 0.5) * 0.3;
                
                const x = Math.cos(jitterAngle) * jitterRadius;
                const y = Math.sin(jitterAngle) * jitterRadius;
                
                electricArcs.moveTo(lastX, lastY);
                electricArcs.lineTo(x, y);
                
                lastX = x;
                lastY = y;
            }
        }
    }
    
    /**
     * Update projectile trail graphics
     */
    updateProjectileTrail(projectileContainer) {
        if (!projectileContainer.trail || !projectileContainer.trailPoints) return;
        
        const trail = projectileContainer.trail;
        const points = projectileContainer.trailPoints;
        
        // Clear previous trail
        trail.clear();
        
        if (points.length < 2) return;
        
        // Draw trail as a series of connected lines with decreasing width and alpha
        for (let i = 1; i < points.length; i++) {
            const progress = i / points.length; // 0 = oldest, 1 = newest
            const prevPoint = points[i - 1];
            const currentPoint = points[i];
            
            // Calculate trail properties based on progress
            const width = trail.trailWidth * (0.2 + 0.8 * progress); // Wider at front
            const alpha = trail.trailAlpha * progress; // More opaque at front
            
            // Use gradient effect by drawing multiple lines
            trail.lineStyle(width, trail.trailColor, alpha);
            trail.moveTo(prevPoint.x, prevPoint.y);
            trail.lineTo(currentPoint.x, currentPoint.y);
            
            // Add inner bright core for rocket trails
            if (projectileContainer.projectileData.ordinance === 'ROCKET' && progress > 0.7) {
                trail.lineStyle(width * 0.4, trail.trailSecondaryColor, alpha * 0.8);
                trail.moveTo(prevPoint.x, prevPoint.y);
                trail.lineTo(currentPoint.x, currentPoint.y);
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
            case 'FLAMETHROWER':
                sprite.scale.set(1.8);
                sprite.tint = 0xff8844; // Fire orange
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
            glow.beginFill(0xffffff, 0.3);
            glow.drawCircle(0, 0, 8);
            glow.endFill();
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
            // Convert server position and velocity from world to isometric coordinates
            // Batch coordinate transformations for better performance
            const isoPos = this.worldToIsometric(projectileData.x, projectileData.y);
            const worldVel = { x: projectileData.vx || 0, y: projectileData.vy || 0 };
            const isoVel = this.worldVelocityToIsometric(worldVel.x, worldVel.y);
            
            // Update interpolator with converted coordinates
            interpolator.updateFromServer(isoPos.x, isoPos.y, isoVel.x, isoVel.y);
        } else {
            // Fallback to direct position update if no interpolator
            // This should rarely happen, but provides safety
            const isoPos = this.worldToIsometric(projectileData.x, projectileData.y);
            projectileContainer.position.set(isoPos.x, isoPos.y);
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
        // Clean up plasma effects if they exist
        if (projectileContainer.plasmaEffects) {
            const effects = projectileContainer.plasmaEffects;
            
            // Remove and destroy all plasma effect children
            if (effects.outerGlow) {
                if (effects.outerGlow.parent) {
                    effects.outerGlow.parent.removeChild(effects.outerGlow);
                }
                if (effects.outerGlow.clear) effects.outerGlow.clear();
                effects.outerGlow.destroy();
            }
            if (effects.middleGlow) {
                if (effects.middleGlow.parent) {
                    effects.middleGlow.parent.removeChild(effects.middleGlow);
                }
                if (effects.middleGlow.clear) effects.middleGlow.clear();
                effects.middleGlow.destroy();
            }
            if (effects.innerGlow) {
                if (effects.innerGlow.parent) {
                    effects.innerGlow.parent.removeChild(effects.innerGlow);
                }
                if (effects.innerGlow.clear) effects.innerGlow.clear();
                effects.innerGlow.destroy();
            }
            if (effects.electricArcs) {
                if (effects.electricArcs.parent) {
                    effects.electricArcs.parent.removeChild(effects.electricArcs);
                }
                if (effects.electricArcs.clear) effects.electricArcs.clear();
                effects.electricArcs.destroy();
            }
            
            // Clear references
            projectileContainer.plasmaEffects = null;
        }
        
        // Clean up trail if it exists
        if (projectileContainer.trail) {
            if (projectileContainer.trail.parent) {
                projectileContainer.trail.parent.removeChild(projectileContainer.trail);
            }
            // Clear graphics content before destroying
            if (projectileContainer.trail.clear) {
                projectileContainer.trail.clear();
            }
            projectileContainer.trail.destroy();
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
        projectileContainer.destroy({ children: true, texture: false, baseTexture: false });
    }

    createObstacle(obstacleData) {
        const graphics = this.createObstacleGraphics(obstacleData);
        const isoPos = this.worldToIsometric(obstacleData.x, obstacleData.y);
        graphics.position.set(isoPos.x, isoPos.y);
        // Since we're inverting Y coordinates in vertices, we need to also invert rotation
        // to maintain the correct orientation
        graphics.rotation = -(obstacleData.rotation || 0);
        graphics.zIndex = 5;
        this.obstacles.set(obstacleData.id, graphics);
        this.gameContainer.addChild(graphics);
        
        // Create health bar for player-created obstacles (barriers)
        if (obstacleData.type === 'PLAYER_BARRIER' && obstacleData.ownerId > 0) {
            const healthBarContainer = this.createObstacleHealthBar(obstacleData);
            this.obstacleHealthBars = this.obstacleHealthBars || new Map();
            this.obstacleHealthBars.set(obstacleData.id, healthBarContainer);
        }
    }

    /**
     * Create graphics for an obstacle based on its shape data.
     */
    createObstacleGraphics(obstacleData) {
        const graphics = new PIXI.Graphics();
        const shapeCategory = obstacleData.shapeCategory || 'CIRCULAR';
        const obstacleType = obstacleData.type || 'BOULDER';
        
        // Get color based on obstacle type
        const color = this.getObstacleColor(obstacleType);
        const outlineColor = this.darkenColor(color);
        
        graphics.beginFill(color, 0.8);
        graphics.lineStyle(2, outlineColor, 1);
        
        switch (shapeCategory) {
            case 'CIRCULAR':
                this.drawCircularObstacle(graphics, obstacleData);
                break;
            case 'RECTANGULAR':
                this.drawRectangularObstacle(graphics, obstacleData);
                break;
            case 'TRIANGULAR':
                this.drawTriangularObstacle(graphics, obstacleData);
                break;
            case 'POLYGONAL':
                this.drawPolygonalObstacle(graphics, obstacleData);
                break;
            case 'COMPOUND':
                this.drawCompoundObstacle(graphics, obstacleData);
                break;
            default:
                // Fallback to circle
                graphics.drawCircle(0, 0, obstacleData.boundingRadius || 20);
                break;
        }
        
        graphics.endFill();
        return graphics;
    }
    
    /**
     * Get color for obstacle based on type.
     */
    getObstacleColor(obstacleType) {
        switch (obstacleType) {
            case 'BOULDER': return 0x808080; // Gray
            case 'HOUSE': return 0x8B4513; // Brown
            case 'WALL_SEGMENT': return 0x696969; // Dark gray
            case 'TRIANGLE_ROCK': return 0x708090; // Slate gray
            case 'POLYGON_DEBRIS': return 0x654321; // Dark brown
            case 'HEXAGON_CRYSTAL': return 0x4169E1; // Royal blue
            case 'DIAMOND_STONE': return 0x9370DB; // Medium purple
            case 'L_SHAPED_WALL': return 0x2F4F4F; // Dark slate gray
            case 'CROSS_BARRIER': return 0x8B7D6B; // Light gray
            case 'PLAYER_BARRIER': return 0x8B4513; // Light gray
            default: return 0x808080; // Default gray
        }
    }
    
    drawCircularObstacle(graphics, obstacleData) {
        const radius = obstacleData.radius || obstacleData.boundingRadius || 20;
        graphics.drawCircle(0, 0, radius);
    }
    
    drawRectangularObstacle(graphics, obstacleData) {
        const width = obstacleData.width || obstacleData.boundingRadius * 1.5 || 30;
        const height = obstacleData.height || obstacleData.boundingRadius * 1.2 || 25;
        graphics.drawRect(-width/2, -height/2, width, height);
    }
    
    drawTriangularObstacle(graphics, obstacleData) {
        if (obstacleData.vertices && obstacleData.vertices.length >= 3) {
            this.drawPolygonFromVertices(graphics, obstacleData.vertices);
        } else {
            // Fallback equilateral triangle
            const size = obstacleData.boundingRadius || 25;
            graphics.drawPolygon([
                0, -size * 0.577,          // Top (inverted Y)
                -size * 0.5, size * 0.289, // Bottom left (inverted Y)
                size * 0.5, size * 0.289   // Bottom right (inverted Y)
            ]);
        }
    }
    
    drawPolygonalObstacle(graphics, obstacleData) {
        if (obstacleData.vertices && obstacleData.vertices.length >= 3) {
            this.drawPolygonFromVertices(graphics, obstacleData.vertices);
        } else {
            // Fallback to hexagon
            const radius = obstacleData.boundingRadius || 25;
            const sides = 6;
            const points = [];
            for (let i = 0; i < sides; i++) {
                const angle = (2 * Math.PI * i) / sides;
                points.push(Math.cos(angle) * radius);
                points.push(-Math.sin(angle) * radius);  // Invert Y for PIXI coordinate system
            }
            graphics.drawPolygon(points);
        }
    }
    
    drawCompoundObstacle(graphics, obstacleData) {
        // For now, draw as rectangle - compound shapes would need special handling
        this.drawRectangularObstacle(graphics, obstacleData);
    }
    
    drawPolygonFromVertices(graphics, vertices) {
        if (vertices.length < 3) return;
        
        const points = [];
        vertices.forEach(vertex => {
            // Transform from physics coordinates (Y-up) to PIXI coordinates (Y-down)
            // The obstacle's rotation is handled by the graphics.rotation property,
            // so we just need to invert the Y coordinate here
            points.push(vertex.x);
            points.push(-vertex.y);  // Invert Y for PIXI coordinate system
        });
        graphics.drawPolygon(points);
    }

    updateObstacle(obstacleData) {
        const graphics = this.obstacles.get(obstacleData.id);
        if (!graphics) return;
        const isoPos = this.worldToIsometric(obstacleData.x, obstacleData.y);
        graphics.position.set(isoPos.x, isoPos.y);
        // Consistent with createObstacle - invert rotation for PIXI coordinate system
        graphics.rotation = -(obstacleData.rotation || 0);
        
        // Update health bar if it exists
        if (this.obstacleHealthBars && this.obstacleHealthBars.has(obstacleData.id)) {
            const healthBar = this.obstacleHealthBars.get(obstacleData.id);
            this.updateHealthBar(healthBar, obstacleData, graphics, healthBar.config);
        }
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
            sprite.destroy();
        }
        
        // Remove health bar if it exists
        if (this.obstacleHealthBars && this.obstacleHealthBars.has(obstacleId)) {
            const healthBar = this.obstacleHealthBars.get(obstacleId);
            if (healthBar.parent) {
                healthBar.parent.removeChild(healthBar);
            }
            healthBar.destroy();
            this.obstacleHealthBars.delete(obstacleId);
        }
    }
    
    /**
     * Create health bar for player-created obstacles (barriers).
     * Uses a smaller, more subtle design to distinguish from player health bars.
     */
    createObstacleHealthBar(obstacleData) {
        const healthBarContainer = new PIXI.Container();
        
        // Smaller health bar background (half the size of player health bars)
        const healthBg = new PIXI.Graphics();
        healthBg.beginFill(0x222222, 0.8); // Darker, more subtle background
        healthBg.drawRoundedRect(-15, 0, 30, 4, 1); // Smaller dimensions
        healthBg.endFill();
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.beginFill(0x4a90e2); // Blue color to distinguish from player health
        healthFill.drawRoundedRect(-15, 0, 30, 4, 1);
        healthFill.endFill();
        healthBarContainer.addChild(healthFill);
        
        // Store references for updates
        healthBarContainer.healthBg = healthBg;
        healthBarContainer.healthFill = healthFill;
        healthBarContainer.config = {
            width: 30,
            height: 4,
            yOffset: 25,
            bgColor: 0x222222,
            fillColor: 0x4a90e2,
            cornerRadius: 1,
            showWhenFull: false,
            dynamicColor: false
        };
        
        // Position above obstacle (will be updated in updateObstacleHealthBar)
        const isoPos = this.worldToIsometric(obstacleData.x, obstacleData.y);
        healthBarContainer.position.set(isoPos.x, isoPos.y - 25); // Closer to obstacle than player health bars
        
        // Add to name container so it doesn't rotate with obstacle
        this.nameContainer.addChild(healthBarContainer);
        
        return healthBarContainer;
    }
    
    /**
     * Update obstacle health bar appearance and position.
     */
    /**
     * Create a field effect (explosion, fire, electric, etc.)
     */
    createFieldEffect(effectData) {
        const effectContainer = new PIXI.Container();
        const isoPos = this.worldToIsometric(effectData.x, effectData.y);
        effectContainer.position.set(isoPos.x, isoPos.y);
        
        // Set z-index above players but below UI
        effectContainer.zIndex = 20;
        
        // Create the main effect visual based on type
        const effectGraphics = this.createEffectGraphics(effectData);
        effectContainer.addChild(effectGraphics);
        
        // Add animated elements for certain effects
        this.addEffectAnimation(effectContainer, effectData);
        
        // Store effect data and add to containers
        effectContainer.effectData = effectData;
        effectContainer.effectGraphics = effectGraphics;
        this.fieldEffects.set(effectData.id, effectContainer);
        this.gameContainer.addChild(effectContainer);
        
        // Enable sorting for proper z-index handling
        this.gameContainer.sortableChildren = true;
    }
    
    /**
     * Update a field effect (mainly for animation and fade-out)
     */
    updateFieldEffect(effectData) {
        const effectContainer = this.fieldEffects.get(effectData.id);
        if (!effectContainer) return;
        
        // Update position (in case effect moves)
        const isoPos = this.worldToIsometric(effectData.x, effectData.y);
        effectContainer.position.set(isoPos.x, isoPos.y);
        
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
            // Clean up animation ticker first
            if (effectContainer.animationFunction) {
                this.app.ticker.remove(effectContainer.animationFunction);
                effectContainer.animationFunction = null;
            }
            
            // Add fade-out animation before removal
            this.fadeOutEffect(effectContainer, () => {
                // Final cleanup
                this.cleanupFieldEffectContainer(effectContainer);
                this.gameContainer.removeChild(effectContainer);
                this.fieldEffects.delete(effectId);
            });
        }
    }
    
    /**
     * Create a beam weapon effect
     */
    createBeam(beamData) {
        const beamContainer = new PIXI.Container();
        
        // Convert world coordinates to screen coordinates
        const startPos = this.worldToIsometric(beamData.startX, beamData.startY);
        const endPos = this.worldToIsometric(beamData.endX, beamData.endY);
        
        beamContainer.position.set(startPos.x, startPos.y);
        
        // Calculate beam length and angle
        const dx = endPos.x - startPos.x;
        const dy = endPos.y - startPos.y;
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
        
        // Enable sorting for proper z-index handling
        this.gameContainer.sortableChildren = true;
    }
    
    /**
     * Update a beam weapon effect
     */
    updateBeam(beamData) {
        const beamContainer = this.beams.get(beamData.id);
        if (!beamContainer) return;
        
        // Update position and angle
        const startPos = this.worldToIsometric(beamData.startX, beamData.startY);
        const endPos = this.worldToIsometric(beamData.endX, beamData.endY);
        
        beamContainer.position.set(startPos.x, startPos.y);
        
        // Recalculate beam properties
        const dx = endPos.x - startPos.x;
        const dy = endPos.y - startPos.y;
        const length = Math.sqrt(dx * dx + dy * dy);
        const angle = Math.atan2(dy, dx);
        
        // Update beam graphics if length or angle changed significantly
        if (Math.abs(length - beamContainer.beamLength) > 5 ||
            Math.abs(angle - beamContainer.beamAngle) > 0.1) {
            
            // Remove old graphics
            if (beamContainer.beamGraphics) {
                beamContainer.removeChild(beamContainer.beamGraphics);
                beamContainer.beamGraphics.destroy();
            }
            
            // Create new graphics with updated dimensions
            const beamGraphics = this.createBeamGraphics(beamData, length);
            beamGraphics.rotation = angle;
            beamContainer.addChild(beamGraphics);
            
            beamContainer.beamGraphics = beamGraphics;
            beamContainer.beamLength = length;
            beamContainer.beamAngle = angle;
        }
        
        // Update beam intensity based on duration
        const intensity = beamData.durationPercent || 1.0;
        beamContainer.alpha = Math.max(0.3, intensity);
        
        beamContainer.beamData = beamData;
    }
    
    /**
     * Remove a beam weapon effect
     */
    removeBeam(beamId) {
        const beamContainer = this.beams.get(beamId);
        if (beamContainer) {
            this.cleanupBeamContainer(beamContainer);
            this.gameContainer.removeChild(beamContainer);
            this.beams.delete(beamId);
        }
    }
    
    // ===== Flag Management (CTF Mode) =====
    
    /**
     * Create a flag for capture-the-flag mode
     */
    createFlag(flagData) {
        const flagContainer = new PIXI.Container();
        
        // Convert world coordinates to screen coordinates
        const pos = this.worldToIsometric(flagData.x, flagData.y);
        flagContainer.position.set(pos.x, pos.y);
        
        // Create flag pole
        const pole = new PIXI.Graphics();
        pole.beginFill(0x333333);
        pole.drawRect(-2, -30, 4, 30);
        pole.endFill();
        flagContainer.addChild(pole);
        
        // Create flag sprite (triangle)
        const flag = new PIXI.Graphics();
        const teamColor = this.getTeamColor(flagData.ownerTeam);
        flag.beginFill(teamColor);
        flag.moveTo(0, -30);
        flag.lineTo(20, -20);
        flag.lineTo(0, -10);
        flag.lineTo(0, -30);
        flag.endFill();
        
        // Add black outline
        flag.lineStyle(1, 0x000000, 1);
        flag.moveTo(0, -30);
        flag.lineTo(20, -20);
        flag.lineTo(0, -10);
        
        flagContainer.addChild(flag);
        flagContainer.flagSprite = flag;
        
        // Add team number text on flag
        const teamText = new PIXI.Text(`${flagData.ownerTeam}`, {
            fontSize: 12,
            fill: 0xffffff,
            fontWeight: 'bold',
            stroke: 0x000000,
            strokeThickness: 2
        });
        teamText.anchor.set(0.5);
        teamText.position.set(10, -20);
        flagContainer.addChild(teamText);
        
        // Add glow effect for visibility
        const glow = new PIXI.Graphics();
        glow.beginFill(teamColor, 0.3);
        glow.drawCircle(0, -15, 25);
        glow.endFill();
        flagContainer.addChildAt(glow, 0); // Behind everything else
        flagContainer.glow = glow;
        
        // Animate glow
        flagContainer.glowPhase = 0;
        
        // Set z-index (above ground, below players)
        flagContainer.zIndex = 8;
        
        // Store flag data
        flagContainer.flagData = flagData;
        this.flags.set(flagData.id, flagContainer);
        this.gameContainer.addChild(flagContainer);
        
        // Enable sorting for proper z-index handling
        this.gameContainer.sortableChildren = true;
    }
    
    /**
     * Update a flag's position and state
     */
    updateFlag(flagData) {
        const flagContainer = this.flags.get(flagData.id);
        if (!flagContainer) return;
        
        // Update position (important for carried flags)
        const pos = this.worldToIsometric(flagData.x, flagData.y);
        flagContainer.position.set(pos.x, pos.y);
        
        // Update visual state based on flag state
        const state = flagData.state;
        
        if (state === 'CARRIED') {
            // Flag is being carried - make it bob and pulse
            flagContainer.alpha = 0.9;
            flagContainer.scale.set(0.8);
        } else if (state === 'DROPPED') {
            // Flag is dropped - pulse slowly
            flagContainer.alpha = 0.8 + Math.sin(Date.now() / 500) * 0.2;
            flagContainer.scale.set(1.0);
        } else {
            // Flag is at home - full opacity
            flagContainer.alpha = 1.0;
            flagContainer.scale.set(1.0);
        }
        
        // Animate glow
        flagContainer.glowPhase += 0.05;
        if (flagContainer.glow) {
            flagContainer.glow.alpha = 0.2 + Math.sin(flagContainer.glowPhase) * 0.1;
        }
        
        flagContainer.flagData = flagData;
    }
    
    /**
     * Remove a flag
     */
    removeFlag(flagId) {
        const flagContainer = this.flags.get(flagId);
        if (flagContainer) {
            flagContainer.destroy({ children: true });
            this.gameContainer.removeChild(flagContainer);
            this.flags.delete(flagId);
        }
    }
    
    // ===== KOTH Zone Management =====
    
    /**
     * Create a KOTH zone
     */
    createKothZone(zoneData) {
        const zoneContainer = new PIXI.Container();
        
        // Convert world coordinates to screen coordinates
        const pos = this.worldToIsometric(zoneData.x, zoneData.y);
        zoneContainer.position.set(pos.x, pos.y);
        
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
        const pos = this.worldToIsometric(zoneData.x, zoneData.y);
        zoneContainer.position.set(pos.x, pos.y);
        
        // Store updated data
        zoneContainer.zoneData = zoneData;
        
        // Get colors based on state
        const colors = this.getKothZoneColors(zoneData);
        const radius = zoneData.radius || 80;
        
        // Redraw base circle
        const baseCircle = zoneContainer.baseCircle;
        baseCircle.clear();
        baseCircle.lineStyle(3, colors.border, 1);
        baseCircle.beginFill(colors.fill, 0.2);
        baseCircle.drawCircle(0, 0, radius);
        baseCircle.endFill();
        
        // Draw capture progress ring (no longer needed - removed capture time)
        const progressRing = zoneContainer.progressRing;
        progressRing.clear();
        // No progress ring since zones are controlled immediately
        
        // Draw inner glow
        const glow = zoneContainer.glow;
        glow.clear();
        glow.beginFill(colors.glow, 0.3);
        glow.drawCircle(0, 0, radius * 0.7);
        glow.endFill();
        
        // Update zone number color
        zoneContainer.zoneText.style.fill = colors.text;
        
        // Update status text
        const statusText = zoneContainer.statusText;
        statusText.text = this.getKothZoneStatusText(zoneData);
        statusText.style.fill = colors.statusText;
        
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
            zoneContainer.destroy({ children: true });
            this.gameContainer.removeChild(zoneContainer);
            this.kothZones.delete(zoneId);
        }
    }
    
    /**
     * Create beam graphics based on beam type and properties
     */
    createBeamGraphics(beamData, length) {
        const graphics = new PIXI.Graphics();
        
        // Determine beam type from damage properties
        let beamType = 'LASER'; // default
        if (beamData.isHealingBeam) {
            beamType = 'HEAL_BEAM';
        } else if (beamData.damageType === 'DAMAGE_OVER_TIME') {
            beamType = 'PLASMA_BEAM';
        } else if (beamData.canPierceObstacles) {
            beamType = 'RAILGUN';
        } else if (beamData.canPiercePlayers) {
            beamType = 'LASER';
        }
        
        switch (beamType) {
            case 'LASER':
                return this.createLaserGraphics(graphics, length, beamData);
            case 'PLASMA_BEAM':
                return this.createPlasmaBeamGraphics(graphics, length, beamData);
            case 'HEAL_BEAM':
                return this.createHealBeamGraphics(graphics, length, beamData);
            case 'RAILGUN':
                return this.createRailgunGraphics(graphics, length, beamData);
            default:
                return this.createGenericBeamGraphics(graphics, length, beamData);
        }
    }
    
    /**
     * Create laser beam graphics
     */
    createLaserGraphics(graphics, length, beamData) {
        // Main laser beam - bright magenta/red
        graphics.lineStyle(4, 0xff44ff, 0.9);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Inner core - white hot
        graphics.lineStyle(2, 0xffffff, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Outer glow effect
        graphics.lineStyle(8, 0xff44ff, 0.3);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        return graphics;
    }
    
    /**
     * Create plasma beam graphics
     */
    createPlasmaBeamGraphics(graphics, length, beamData) {
        // Main plasma beam - electric blue
        graphics.lineStyle(6, 0x4488ff, 0.8);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Plasma core - bright white
        graphics.lineStyle(3, 0xaaffff, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Crackling energy effect
        graphics.lineStyle(10, 0x4488ff, 0.2);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Add plasma instability (random segments)
        for (let i = 0; i < length; i += 20) {
            const segmentEnd = Math.min(i + 15 + Math.random() * 10, length);
            const offset = (Math.random() - 0.5) * 4;
            
            graphics.lineStyle(2, 0x88aaff, 0.6);
            graphics.moveTo(i, 0);
            graphics.lineTo(segmentEnd, offset);
        }
        
        return graphics;
    }
    
    /**
     * Create heal beam graphics
     */
    createHealBeamGraphics(graphics, length, beamData) {
        // Main healing beam - soft green
        graphics.lineStyle(5, 0x2ecc71, 0.7);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Healing core - bright green
        graphics.lineStyle(2, 0x58d68d, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Healing aura
        graphics.lineStyle(12, 0x2ecc71, 0.2);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Add healing particles along the beam
        for (let i = 10; i < length; i += 15) {
            const offset = (Math.random() - 0.5) * 6;
            graphics.beginFill(0x58d68d, 0.8);
            graphics.drawCircle(i, offset, 1.5);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create railgun beam graphics
     */
    createRailgunGraphics(graphics, length, beamData) {
        // Main railgun beam - bright white/blue
        graphics.lineStyle(3, 0xaaffff, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Railgun core - pure white
        graphics.lineStyle(1, 0xffffff, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Electromagnetic field
        graphics.lineStyle(8, 0x88ccff, 0.4);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Add electromagnetic distortion lines
        for (let i = 0; i < length; i += 25) {
            const distortionLength = 8 + Math.random() * 6;
            graphics.lineStyle(1, 0xaaffff, 0.5);
            graphics.moveTo(i, -distortionLength/2);
            graphics.lineTo(i, distortionLength/2);
        }
        
        return graphics;
    }
    
    /**
     * Create generic beam graphics
     */
    createGenericBeamGraphics(graphics, length, beamData) {
        // Generic beam - white
        graphics.lineStyle(3, 0xffffff, 0.8);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
        // Core
        graphics.lineStyle(1, 0xffffff, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(length, 0);
        
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
            energyEffect.lineStyle(8, 0x4488ff, 0.1);
            energyEffect.moveTo(0, 0);
            energyEffect.lineTo(length, 0);
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
        console.log('DEBUG: createUtilityEntityGraphics called with type:', entityData.type);
        const graphics = new PIXI.Graphics();
        
        switch (entityData.type) {
            case 'TURRET':
                return this.createTurretGraphics(graphics, entityData);
            case 'NET':
                return this.createNetGraphics(graphics, entityData);
            case 'MINE':
                return this.createMineGraphics(graphics, entityData);
            case 'TELEPORT_PAD':
                return this.createTeleportPadGraphics(graphics, entityData);
            case 'DEFENSE_LASER':
                return this.createDefenseLaserGraphics(graphics, entityData);
            case 'WORKSHOP':
                return this.createWorkshopGraphics(graphics, entityData);
            case 'HEADQUARTERS':
                return this.createHeadquartersGraphics(graphics, entityData);
            case 'POWERUP':
                return this.createPowerUpGraphics(graphics, entityData);
            default:
                console.log('DEBUG: Using default graphics for type:', entityData.type);
                return this.createGenericUtilityGraphics(graphics, entityData);
        }
    }
    
    /**
     * Create turret graphics
     */
    createTurretGraphics(graphics, entityData) {
        // Turret base - dark gray circle
        graphics.beginFill(0x444444, 0.9);
        graphics.drawCircle(0, 0, 18);
        graphics.endFill();
        
        // Turret base outline
        graphics.lineStyle(2, 0x666666, 1.0);
        graphics.drawCircle(0, 0, 18);
        
        // Turret barrel - pointing in direction
        graphics.lineStyle(4, 0x333333, 1.0);
        graphics.moveTo(0, 0);
        graphics.lineTo(25, 0); // Barrel length
        
        // Turret barrel tip
        graphics.beginFill(0x222222, 1.0);
        graphics.drawCircle(25, 0, 3);
        graphics.endFill();
        
        // Team color indicator
        const teamColor = this.getTeamColor(entityData.ownerTeam || 0);
        graphics.beginFill(teamColor, 0.8);
        graphics.drawCircle(0, 0, 8);
        graphics.endFill();
        
        // Health indicator (if available)
        if (entityData.health !== undefined) {
            const healthPercent = Math.max(0, entityData.health / 100);
            graphics.lineStyle(2, 0x2ecc71, healthPercent);
            graphics.drawCircle(0, 0, 20);
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
        graphics.lineStyle(2, 0x8B4513, 0.9); // Brown rope color
        graphics.drawRect(-netWidth/2, -netHeight/2, netWidth, netHeight);
        
        // Draw horizontal mesh lines
        graphics.lineStyle(1, 0x654321, 0.8); // Slightly darker brown
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
            graphics.beginFill(0x4A4A4A, 0.9); // Dark gray metal
            graphics.lineStyle(1, 0x2A2A2A, 1.0); // Darker outline
            graphics.drawCircle(corner.x, corner.y, cornerRadius);
            graphics.endFill();
            
            // Add metallic shine
            graphics.beginFill(0x6A6A6A, 0.6);
            graphics.drawCircle(corner.x - 1, corner.y - 1, cornerRadius * 0.4);
            graphics.endFill();
        });
        
        // Add subtle net texture with small cross-hatches
        graphics.lineStyle(0.5, 0x654321, 0.4);
        for (let x = -netWidth/2 + meshSize/2; x < netWidth/2; x += meshSize) {
            for (let y = -netHeight/2 + meshSize/2; y < netHeight/2; y += meshSize) {
                // Small cross pattern in each mesh cell
                graphics.moveTo(x - 0.5, y - 0.5);
                graphics.lineTo(x + 0.5, y + 0.5);
                graphics.moveTo(x + 0.5, y - 0.5);
                graphics.lineTo(x - 0.5, y + 0.5);
            }
        }
        
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
        graphics.lineStyle(2, 0x8B4513, 0.9); // Brown rope color
        graphics.moveTo(vertices[0].x, vertices[0].y);
        for (let i = 1; i < vertices.length; i++) {
            graphics.lineTo(vertices[i].x, vertices[i].y);
        }
        graphics.lineTo(vertices[0].x, vertices[0].y); // Close the pentagon
        
        // Draw mesh lines from center to each vertex
        graphics.lineStyle(1, 0x654321, 0.8); // Slightly darker brown
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
        
        // Add corner weights at each vertex
        const cornerRadius = 2.5;
        vertices.forEach(vertex => {
            // Corner weight
            graphics.beginFill(0x4A4A4A, 0.9); // Dark gray metal
            graphics.lineStyle(1, 0x2A2A2A, 1.0); // Darker outline
            graphics.drawCircle(vertex.x, vertex.y, cornerRadius);
            graphics.endFill();
            
            // Add metallic shine
            graphics.beginFill(0x6A6A6A, 0.6);
            graphics.drawCircle(vertex.x - 0.8, vertex.y - 0.8, cornerRadius * 0.4);
            graphics.endFill();
        });
        
        // Add subtle net texture with small cross-hatches in mesh cells
        graphics.lineStyle(0.5, 0x654321, 0.3);
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
        
        return graphics;
    }
    
    /**
     * Create proximity mine graphics
     */
    createMineGraphics(graphics, entityData) {
        const isArmed = entityData.isArmed || false;
        const ownerTeam = entityData.ownerTeam || 0;
        
        // Outer trigger zone - very subtle danger area
        graphics.beginFill(0xff4444, 0.06);
        graphics.drawCircle(0, 0, 18);
        graphics.endFill();
        
        // Trigger zone outline - dashed circle (more subtle)
        graphics.lineStyle(1, 0xff6666, 0.25);
        const dashCount = 16;
        for (let i = 0; i < dashCount; i++) {
            const startAngle = (i / dashCount) * Math.PI * 2;
            const endAngle = ((i + 0.5) / dashCount) * Math.PI * 2;
            graphics.arc(0, 0, 18, startAngle, endAngle);
        }
        
        // Mine center body - darker, more blended
        graphics.beginFill(0x252f3a, 0.85);
        graphics.drawCircle(0, 0, 8);
        graphics.endFill();

        // Center body outline - much more subtle
        graphics.lineStyle(1, 0x1e2329, 0.7);
        graphics.drawCircle(0, 0, 8);
 
        // Core highlight - metallic shine (more prominent without inner ring)
        graphics.beginFill(0x3a4a5a, 0.4);
        graphics.drawCircle(-1, -1, 2);
        graphics.endFill();
        
        // Sensor spikes - 6 directional sensors (more subtle)
        for (let i = 0; i < 6; i++) {
            const angle = (i / 6) * Math.PI * 2;
            const innerRadius = 6;
            const outerRadius = 12;
            const spikeWidth = 1.2;
            
            // Sensor spike body - darker and more transparent
            graphics.lineStyle(spikeWidth, 0x2a3441, 0.8);
            graphics.moveTo(
                Math.cos(angle) * innerRadius,
                Math.sin(angle) * innerRadius
            );
            graphics.lineTo(
                Math.cos(angle) * outerRadius,
                Math.sin(angle) * outerRadius
            );
            
            // Sensor tip - small detection node (more subtle)
            graphics.beginFill(0x3a4a5a, 0.7);
            graphics.drawCircle(
                Math.cos(angle) * outerRadius,
                Math.sin(angle) * outerRadius,
                1.2
            );
            graphics.endFill();
        }
        
        // Status indicator
        if (isArmed) {
            // Armed - team color pulsing ring around center
            const pulse = 0.6 + 0.4 * Math.sin(Date.now() * 0.01);
            const teamColor = this.getTeamColor(ownerTeam);
            graphics.lineStyle(2, teamColor, pulse);
            graphics.drawCircle(0, 0, 10);
            
            // Armed indicator - small pulsing center light
            const centerPulse = 0.3 + 0.7 * Math.sin(Date.now() * 0.015);
            graphics.beginFill(teamColor, centerPulse);
            graphics.drawCircle(0, 0, 1.5);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create teleport pad graphics
     */
    createTeleportPadGraphics(graphics, entityData) {
        const isLinked = entityData.isLinked || false;
        const isCharging = entityData.isCharging || false;
        const chargingProgress = entityData.chargingProgress || 0;
        const pulseValue = entityData.pulseValue || 0.5;
        
        // Teleport pad base - large circle
        const baseColor = isLinked ? 0x9b59b6 : 0x6c5ce7; // Purple when linked, blue when not
        graphics.beginFill(baseColor, 0.3);
        graphics.drawCircle(0, 0, 20);
        graphics.endFill();
        
        // Outer ring
        graphics.lineStyle(3, baseColor, 0.8);
        graphics.drawCircle(0, 0, 20);
        
        // Inner energy core
        const coreAlpha = isCharging ? chargingProgress : 1.0;
        graphics.beginFill(0xffffff, coreAlpha * 0.6);
        graphics.drawCircle(0, 0, 8);
        graphics.endFill();
        
        // Pulsing energy rings
        if (!isCharging || chargingProgress > 0.5) {
            const pulseAlpha = pulseValue * 0.5;
            graphics.lineStyle(2, 0xffffff, pulseAlpha);
            graphics.drawCircle(0, 0, 12);
            graphics.drawCircle(0, 0, 16);
        }
        
        // Charging progress indicator
        if (isCharging) {
            graphics.lineStyle(4, 0xf39c12, 0.8);
            graphics.arc(0, 0, 24, 0, Math.PI * 2 * chargingProgress);
        }
        
        // Link indicator
        if (isLinked) {
            // Draw connection symbols - more prominent
            for (let i = 0; i < 4; i++) {
                const angle = (i / 4) * Math.PI * 2;
                const x = Math.cos(angle) * 16;
                const y = Math.sin(angle) * 16;
                
                // Outer glow effect
                graphics.beginFill(0x9b59b6, 0.3);
                graphics.drawCircle(x, y, 6);
                graphics.endFill();
                
                // Arrow pointing outward
                graphics.beginFill(0x9b59b6, 0.9);
                graphics.drawPolygon([
                    x - 2, y - 2,
                    x + 2, y - 2,
                    x + 4, y,
                    x + 2, y + 2,
                    x - 2, y + 2,
                    x - 4, y
                ]);
                graphics.endFill();
            }
            
            // Add pulsing connection ring
            const connectionRingAlpha = pulseValue * 0.6;
            graphics.lineStyle(2, 0x9b59b6, connectionRingAlpha);
            graphics.drawCircle(0, 0, 25);
        }
        
        return graphics;
    }
    
    /**
     * Create defense laser graphics
     */
    createDefenseLaserGraphics(graphics, entityData) {
        // Base structure - blue-gray circle
        graphics.beginFill(0x4444AA, 0.9);
        graphics.drawCircle(0, 0, 15);
        graphics.endFill();
        
        // Central core - brighter blue
        graphics.beginFill(0x6666CC, 0.8);
        graphics.drawCircle(0, 0, 8);
        graphics.endFill();
        
        // Rotating indicator - shows current beam direction
        graphics.beginFill(0x8888FF, 0.9);
        graphics.drawRect(-2, -12, 4, 6);
        graphics.endFill();
        
        // Outer ring to show it's active
        graphics.lineStyle(2, 0xAAAAFF, 0.8);
        graphics.drawCircle(0, 0, 18);
        
        return graphics;
    }
    
    /**
     * Create workshop graphics
     */
    createWorkshopGraphics(graphics, entityData) {
        if (entityData.type !== 'WORKSHOP') {
            return;
        }
        
        // Use the same approach as rectangular obstacles
        const width = entityData.width;
        const height = entityData.height;
        
        const halfWidth = width / 2;
        const halfHeight = height / 2;
        
        // Workshop base - industrial gray rectangle
        graphics.beginFill(0x555555, 0.9);
        graphics.drawRect(-halfWidth, -halfHeight, width, height);
        graphics.endFill();
        
        // Workshop outline
        graphics.lineStyle(3, 0x777777, 1.0);
        graphics.drawRect(-halfWidth, -halfHeight, width, height);
        
        // Crafting radius indicator (subtle)
        graphics.lineStyle(1, 0x888888, 0.3);
        graphics.drawCircle(0, 0, entityData.craftRadius || 80);
        
        // Workshop center - gear-like design
        graphics.lineStyle(2, 0x999999, 1.0);
        graphics.moveTo(-8, -8);
        graphics.lineTo(8, 8);
        graphics.moveTo(8, -8);
        graphics.lineTo(-8, 8);
        graphics.drawCircle(0, 0, 6);
        
        // Add some workshop details to make it look more industrial
        graphics.lineStyle(1, 0x666666, 0.8);
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
                    graphics.beginFill(0x00AAFF, 1.0);
                    graphics.drawCircle(x, y, 8);
                    graphics.endFill();
                    
                    // Progress ring (outer) - thicker and more visible
                    graphics.lineStyle(4, 0x00AAFF, progress);
                    graphics.drawCircle(x, y, 12);
                    
                    // Inner progress circle
                    graphics.lineStyle(2, 0xFFFFFF, 0.8);
                    graphics.drawCircle(x, y, 6);
                    
                    // Progress percentage indicator (pulsing dot)
                    const pulseSize = 4 + (progress * 4);
                    graphics.beginFill(0xFFFFFF, 0.9);
                    graphics.drawCircle(x, y, pulseSize);
                    graphics.endFill();
                }
            });
        }
        
        // Add workshop activity indicator (pulsing center when active)
        if (entityData.activeCrafters > 0) {
            // Pulsing center circle to show workshop is active
            graphics.lineStyle(3, 0x00FF00, 1.0); // Green for active - thicker and brighter
            graphics.drawCircle(0, 0, 10);
        }
        
        return graphics;
    }
    
    /**
     * Update workshop visual to show crafting progress changes
     */
    updateWorkshopVisual(container, entityData) {
        // Clear and redraw with updated progress
        const graphics = container.getChildAt(0);
        if (graphics) {
            graphics.clear();
            this.createWorkshopGraphics(graphics, entityData);
        }
    }
    
    /**
     * Create headquarters graphics
     */
    createHeadquartersGraphics(graphics, entityData) {
        const width = entityData.width || 80;
        const height = entityData.height || 60;
        const halfWidth = width / 2;
        const halfHeight = height / 2;
        const team = entityData.team || 0;
        
        // Get team color
        const teamColor = this.getTeamColor(team);
        
        // Calculate health percentage for damage visualization
        const healthPct = entityData.maxHealth > 0 ? entityData.health / entityData.maxHealth : 1.0;
        
        // HQ base - large fortified rectangle with team color
        const baseAlpha = 0.9;
        graphics.beginFill(teamColor, baseAlpha);
        graphics.drawRect(-halfWidth, -halfHeight, width, height);
        graphics.endFill();
        
        // Damage overlay (darker as health decreases)
        if (healthPct < 1.0) {
            const damageAlpha = (1.0 - healthPct) * 0.6;
            graphics.beginFill(0x000000, damageAlpha);
            graphics.drawRect(-halfWidth, -halfHeight, width, height);
            graphics.endFill();
        }
        
        // HQ fortified outline (thicker than normal obstacles)
        graphics.lineStyle(4, 0xFFFFFF, 0.9);
        graphics.drawRect(-halfWidth, -halfHeight, width, height);
        
        // Castle turrets at each corner
        const turretRadius = 12;
        const turretPositions = [
            { x: -halfWidth, y: -halfHeight },  // Top-left
            { x: halfWidth, y: -halfHeight },   // Top-right
            { x: halfWidth, y: halfHeight },    // Bottom-right
            { x: -halfWidth, y: halfHeight }    // Bottom-left
        ];
        
        turretPositions.forEach(pos => {
            // Turret base (darker shade of team color)
            const darkerTeamColor = this.darkenColor(teamColor);
            graphics.beginFill(darkerTeamColor, 0.95);
            graphics.drawCircle(pos.x, pos.y, turretRadius);
            graphics.endFill();
            
            // Damage overlay on turrets
            if (healthPct < 1.0) {
                const damageAlpha = (1.0 - healthPct) * 0.6;
                graphics.beginFill(0x000000, damageAlpha);
                graphics.drawCircle(pos.x, pos.y, turretRadius);
                graphics.endFill();
            }
            
            // Turret outline
            graphics.lineStyle(3, 0xFFFFFF, 0.95);
            graphics.drawCircle(pos.x, pos.y, turretRadius);
            
            // Inner turret detail (smaller circle)
            graphics.lineStyle(2, 0xFFFFFF, 0.7);
            graphics.drawCircle(pos.x, pos.y, turretRadius * 0.6);
            
            // Turret top accent
            graphics.beginFill(0xFFFFFF, 0.4);
            graphics.drawCircle(pos.x, pos.y, turretRadius * 0.3);
            graphics.endFill();
        });
        
        // Central command center design
        const centerSize = Math.min(halfWidth, halfHeight) * 0.5;
        graphics.lineStyle(2, 0xFFFFFF, 0.8);
        graphics.beginFill(teamColor, 0.5);
        graphics.drawCircle(0, 0, centerSize);
        graphics.endFill();
        
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
        graphics.addChild(teamText);
        
        // Health bar above HQ
        const barWidth = width * 0.8;
        const barHeight = 6;
        const barY = -halfHeight - 15;
        
        // Health bar background
        graphics.lineStyle(2, 0x000000, 0.8);
        graphics.beginFill(0x333333, 0.8);
        graphics.drawRect(-barWidth/2, barY, barWidth, barHeight);
        graphics.endFill();
        
        // Health bar fill (color changes based on health)
        let healthBarColor = 0x00FF00; // Green
        if (healthPct < 0.3) {
            healthBarColor = 0xFF0000; // Red
        } else if (healthPct < 0.6) {
            healthBarColor = 0xFFAA00; // Orange
        }
        
        graphics.beginFill(healthBarColor, 0.9);
        graphics.drawRect(-barWidth/2, barY, barWidth * healthPct, barHeight);
        graphics.endFill();
        
        // Health text
        const healthText = new PIXI.Text(`${Math.round(entityData.health)}/${Math.round(entityData.maxHealth)}`, {
            fontSize: 10,
            fill: 0xFFFFFF,
            fontWeight: 'bold',
            stroke: 0x000000,
            strokeThickness: 2
        });
        healthText.anchor.set(0.5);
        healthText.position.set(0, barY - 12);
        graphics.addChild(healthText);
        
        // Warning pulse effect when heavily damaged
        if (healthPct < 0.3) {
            const pulse = Math.sin(Date.now() * 0.005) * 0.5 + 0.5;
            graphics.lineStyle(3, 0xFF0000, pulse);
            graphics.drawRect(-halfWidth - 5, -halfHeight - 5, width + 10, height + 10);
        }
        
        return graphics;
    }
    
    /**
     * Update headquarters visual to show health changes
     */
    updateHeadquartersVisual(container, entityData) {
        // Clear and redraw with updated health
        const graphics = container.getChildAt(0);
        if (graphics) {
            graphics.clear();
            // Remove old text children
            while (graphics.children.length > 0) {
                graphics.removeChildAt(0);
            }
            this.createHeadquartersGraphics(graphics, entityData);
        }
    }
    
    /**
     * Create power-up graphics
     */
    createPowerUpGraphics(graphics, entityData) {
        const powerUpType = entityData.powerUpType || entityData.type || 'SPEED_BOOST';
        
        // Power-up base - larger circle for better visibility
        graphics.beginFill(0xFFFFFF, 0.9);
        graphics.drawCircle(0, 0, 14);
        graphics.endFill();
        
        // Power-up outline (thicker for better visibility)
        graphics.lineStyle(3, 0xCCCCCC, 1.0);
        graphics.drawCircle(0, 0, 14);
        
        // Type-specific visual indicators (larger inner circle)
        switch (powerUpType) {
            case 'SPEED_BOOST':
                graphics.beginFill(0x00FFFF, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
            case 'HEALTH_REGENERATION':
                graphics.beginFill(0x00FF00, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
            case 'DAMAGE_BOOST':
                graphics.beginFill(0xFF0000, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
            case 'DAMAGE_RESISTANCE':
                graphics.beginFill(0xFFD700, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
            case 'BERSERKER_MODE':
                graphics.beginFill(0xFF4500, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
            case 'SLOW_EFFECT':
                graphics.beginFill(0x0066CC, 0.8);
                graphics.drawCircle(0, 0, 9);
                graphics.endFill();
                break;
        }
        
        // Add larger sparkle effect for better visibility
        graphics.beginFill(0xFFFFFF, 0.7);
        graphics.drawCircle(-5, -5, 3);
        graphics.drawCircle(5, 5, 3);
        graphics.endFill();
        
        return graphics;
    }
    
    /**
     * Create generic utility graphics
     */
    createGenericUtilityGraphics(graphics, entityData) {
        graphics.beginFill(0x888888, 0.7);
        graphics.drawCircle(0, 0, 15);
        graphics.endFill();
        
        graphics.lineStyle(2, 0xcccccc, 1.0);
        graphics.drawCircle(0, 0, 15);
        
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
            case 'BARRIER':
                return 6;  // Above obstacles, below players
            case 'NET':
                return 9;  // Same as projectiles
            case 'MINE':
                return 7;  // Above obstacles, below players
            case 'TELEPORT_PAD':
                return 5;  // Below most things
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
            case 'TELEPORT_PAD':
                this.updateTeleportPadVisual(container, entityData);
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
            container.entityGraphics.destroy();
            
            const newGraphics = this.createMineGraphics(new PIXI.Graphics(), entityData);
            container.addChild(newGraphics);
            container.entityGraphics = newGraphics;
            container.lastArmedState = entityData.isArmed;
        }
    }
    
    /**
     * Update teleport pad visual effects
     */
    updateTeleportPadVisual(container, entityData) {
        // Recreate graphics for dynamic effects
        container.removeChild(container.entityGraphics);
        container.entityGraphics.destroy();
        
        const newGraphics = this.createTeleportPadGraphics(new PIXI.Graphics(), entityData);
        container.addChild(newGraphics);
        container.entityGraphics = newGraphics;
        
        // Update connection lines if this pad is linked
        this.updateTeleportPadConnections(entityData);
    }
    
    /**
     * Update teleport pad connection lines
     */
    updateTeleportPadConnections(entityData) {
        const padId = entityData.id;
        const linkedPadId = entityData.linkedPadId;
        
        // Remove existing connection for this pad
        if (this.teleportConnections.has(padId)) {
            const connection = this.teleportConnections.get(padId);
            if (connection.parent) {
                connection.parent.removeChild(connection);
            }
            connection.destroy();
            this.teleportConnections.delete(padId);
        }
        
        // Create new connection if this pad is linked
        if (linkedPadId && this.utilityEntities.has(linkedPadId)) {
            const linkedPad = this.utilityEntities.get(linkedPadId);
            if (linkedPad && linkedPad.entityData && linkedPad.entityData.type === 'TELEPORT_PAD') {
                this.createTeleportConnection(padId, linkedPadId, entityData, linkedPad.entityData);
            }
        }
    }
    
    /**
     * Create a visual connection line between two teleport pads
     */
    createTeleportConnection(padId1, padId2, padData1, padData2) {
        const connectionGraphics = new PIXI.Graphics();
        
        // Convert world positions to screen coordinates
        const pos1 = this.worldToIsometric(padData1.x, padData1.y);
        const pos2 = this.worldToIsometric(padData2.x, padData2.y);
        
        // Create animated connection line
        this.drawAnimatedConnectionLine(connectionGraphics, pos1, pos2);
        
        // Add to game container (behind other entities)
        connectionGraphics.zIndex = 1;
        this.gameContainer.addChild(connectionGraphics);
        
        // Store connection for cleanup
        this.teleportConnections.set(padId1, connectionGraphics);
        
        // Add animation ticker for the connection line
        const animateConnection = () => {
            if (!connectionGraphics.parent) {
                // Connection was removed - cleanup ticker
                if (connectionGraphics.animationFunction) {
                    this.app.ticker.remove(connectionGraphics.animationFunction);
                    connectionGraphics.animationFunction = null;
                }
                return;
            }
            
            connectionGraphics.clear();
            this.drawAnimatedConnectionLine(connectionGraphics, pos1, pos2);
        };
        
        connectionGraphics.animationFunction = animateConnection;
        this.app.ticker.add(animateConnection);
    }
    
    /**
     * Draw an animated connection line between two points
     */
    drawAnimatedConnectionLine(graphics, pos1, pos2) {
        const time = performance.now() * 0.003; // Slow animation
        const distance = Math.sqrt((pos2.x - pos1.x) ** 2 + (pos2.y - pos1.y) ** 2);
        
        // Create flowing energy effect along the line
        const segments = Math.max(8, Math.floor(distance / 20));
        const segmentLength = distance / segments;
        
        // Calculate direction vector
        const dx = (pos2.x - pos1.x) / distance;
        const dy = (pos2.y - pos1.y) / distance;
        
        // Draw animated segments
        for (let i = 0; i < segments; i++) {
            const progress = i / segments;
            const segmentStart = progress * distance;
            const segmentEnd = (progress + 1 / segments) * distance;
            
            // Animate the segment opacity
            const waveOffset = (time + progress * 3) % (Math.PI * 2);
            const alpha = 0.3 + 0.4 * Math.sin(waveOffset);
            
            // Calculate segment positions
            const startX = pos1.x + dx * segmentStart;
            const startY = pos1.y + dy * segmentStart;
            const endX = pos1.x + dx * segmentEnd;
            const endY = pos1.y + dy * segmentEnd;
            
            // Draw segment with gradient effect
            graphics.lineStyle(3, 0x9b59b6, alpha);
            graphics.moveTo(startX, startY);
            graphics.lineTo(endX, endY);
            
            // Add energy particles along the line
            if (i % 2 === 0) {
                const particleProgress = (progress + 0.5 / segments) % 1;
                const particleX = pos1.x + dx * particleProgress * distance;
                const particleY = pos1.y + dy * particleProgress * distance;
                
                graphics.beginFill(0xffffff, alpha * 0.8);
                graphics.drawCircle(particleX, particleY, 2);
                graphics.endFill();
            }
        }
        
        // Add connection indicators at both ends
        graphics.beginFill(0x9b59b6, 0.6);
        graphics.drawCircle(pos1.x, pos1.y, 3);
        graphics.drawCircle(pos2.x, pos2.y, 3);
        graphics.endFill();
        
        // Add directional arrows
        const midX = (pos1.x + pos2.x) / 2;
        const midY = (pos1.y + pos2.y) / 2;
        
        // Arrow pointing from pad1 to pad2
        const arrowSize = 4;
        const perpX = -dy * arrowSize;
        const perpY = dx * arrowSize;
        
        graphics.beginFill(0x9b59b6, 0.8);
        graphics.drawPolygon([
            midX + dx * arrowSize, midY + dy * arrowSize,
            midX - dx * arrowSize + perpX, midY - dy * arrowSize + perpY,
            midX - dx * arrowSize - perpX, midY - dy * arrowSize - perpY
        ]);
        graphics.endFill();
    }
    
    /**
     * Update turret visual effects
     */
    updateTurretVisual(container, entityData) {
        container.rotation = -(entityData.rotation || 0);
        
        // Update health bar if it exists
        if (container.healthBar) {
            this.updateHealthBar(container.healthBar, entityData, container, container.healthBar.config);
        }
    }

    updateNetVisual(container, entityData) {
        container.rotation = -(entityData.rotation || 0);
    }
    
    /**
     * Update defense laser visual effects
     */
    updateDefenseLaserVisual(container, entityData) {
        // Update rotation of the visual indicator to show beam direction
        container.rotation = -(entityData.rotation || 0);
        
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
                    progressBar.destroy();
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
        background.beginFill(0x222222, 0.8);
        background.drawRoundedRect(-barWidth/2, 0, barWidth, barHeight, 3);
        background.endFill();
        
        // Border
        background.lineStyle(1, 0x444444, 0.8);
        background.drawRoundedRect(-barWidth/2, 0, barWidth, barHeight, 3);
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
                fill.beginFill(fillColor, 0.9);
                fill.drawRoundedRect(-progressBar.barWidth/2 + 2, 2, fillWidth, progressBar.barHeight - 4, 2);
                fill.endFill();
                
                // Add a subtle shine effect
                fill.beginFill(0xffffff, 0.3);
                fill.drawRoundedRect(-progressBar.barWidth/2 + 2, 2, fillWidth, 2, 2);
                fill.endFill();
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
        healthBg.beginFill(0x333333);
        healthBg.drawRoundedRect(-20, 0, 40, 5, 2);
        healthBg.endFill();
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.beginFill(0x2ecc71);
        healthFill.drawRoundedRect(-20, 0, 40, 5, 2);
        healthFill.endFill();
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
        const isoPos = this.worldToIsometric(entityData.x, entityData.y);
        healthBarContainer.position.set(isoPos.x, isoPos.y - 30);
        
        return healthBarContainer;
    }
    
    /**
     * Update turret health bar
     */
    /**
     * Clean up utility entity container
     */
    cleanupUtilityEntityContainer(container) {
        // Clean up teleport pad connections if this is a teleport pad
        if (container.entityData && container.entityData.type === 'TELEPORT_PAD') {
            const padId = container.entityData.id;
            
            // Remove connection from this pad
            if (this.teleportConnections.has(padId)) {
                const connection = this.teleportConnections.get(padId);
                if (connection.animationFunction) {
                    this.app.ticker.remove(connection.animationFunction);
                }
                if (connection.parent) {
                    connection.parent.removeChild(connection);
                }
                connection.destroy();
                this.teleportConnections.delete(padId);
            }
            
            // Remove any connections TO this pad
            for (let [otherPadId, connection] of this.teleportConnections) {
                if (connection.entityData && connection.entityData.linkedPadId === padId) {
                    if (connection.animationFunction) {
                        this.app.ticker.remove(connection.animationFunction);
                    }
                    if (connection.parent) {
                        connection.parent.removeChild(connection);
                    }
                    connection.destroy();
                    this.teleportConnections.delete(otherPadId);
                }
            }
        }
        
        // Clean up graphics
        if (container.entityGraphics) {
            container.entityGraphics.clear();
            container.entityGraphics.destroy();
            container.entityGraphics = null;
        }
        
        // Clean up health bar
        if (container.healthBar) {
            container.healthBar.destroy();
        }
        
        // Clear references
        container.entityData = null;
        container.lastArmedState = null;
        
        // Destroy container
        container.destroy({ children: true, texture: false, baseTexture: false });
    }
    
    /**
     * Clean up beam container to prevent memory leaks
     */
    cleanupBeamContainer(beamContainer) {
        // Clean up beam graphics
        if (beamContainer.beamGraphics) {
            beamContainer.beamGraphics.clear();
            beamContainer.beamGraphics.destroy();
            beamContainer.beamGraphics = null;
        }
        
        // Clean up energy effects
        if (beamContainer.energyEffect) {
            beamContainer.energyEffect.clear();
            beamContainer.energyEffect.destroy();
            beamContainer.energyEffect = null;
        }
        
        // Clear all references
        beamContainer.beamData = null;
        beamContainer.beamLength = null;
        beamContainer.beamAngle = null;
        
        // Destroy the container
        beamContainer.destroy({ children: true, texture: false, baseTexture: false });
    }
    
    /**
     * Thoroughly clean up a field effect container to prevent memory leaks
     */
    cleanupFieldEffectContainer(effectContainer) {
        // Remove animation ticker first (if not already removed)
        if (effectContainer.animationFunction) {
            this.app.ticker.remove(effectContainer.animationFunction);
            effectContainer.animationFunction = null;
        }
        
        // Remove fade-out ticker if it exists
        if (effectContainer.fadeOutFunction) {
            this.app.ticker.remove(effectContainer.fadeOutFunction);
            effectContainer.fadeOutFunction = null;
        }
        
        // Clean up graphics objects explicitly
        if (effectContainer.effectGraphics) {
            effectContainer.effectGraphics.clear();
            if (effectContainer.effectGraphics.parent) {
                effectContainer.effectGraphics.parent.removeChild(effectContainer.effectGraphics);
            }
            effectContainer.effectGraphics.destroy();
            effectContainer.effectGraphics = null;
        }
        
        // Clean up all child graphics objects
        const childrenToDestroy = [...effectContainer.children];
        childrenToDestroy.forEach(child => {
            if (child.clear && typeof child.clear === 'function') {
                child.clear(); // Clear graphics content
            }
            if (child.parent) {
                child.parent.removeChild(child);
            }
            child.destroy();
        });
        
        // Clear all custom properties
        effectContainer.effectData = null;
        effectContainer.animationTime = null;
        effectContainer.animationSpeed = null;
        effectContainer.originalScale = null;
        effectContainer.originalX = null;
        effectContainer.originalY = null;
        
        // Destroy the container itself
        effectContainer.destroy({ children: true, texture: false, baseTexture: false });
    }
    
    /**
     * Create the main graphics for a field effect based on its type
     */
    createEffectGraphics(effectData) {
        const graphics = new PIXI.Graphics();
        const radius = effectData.radius || 50;
        
        switch (effectData.type) {
            case 'EXPLOSION':
                return this.createExplosionGraphics(graphics, radius, effectData);
            case 'FIRE':
                return this.createFireGraphics(graphics, radius, effectData);
            case 'ELECTRIC':
                return this.createElectricGraphics(graphics, radius, effectData);
            case 'FREEZE':
                return this.createFreezeGraphics(graphics, radius, effectData);
            case 'FRAGMENTATION':
                return this.createFragmentationGraphics(graphics, radius, effectData);
            case 'POISON':
                return this.createPoisonGraphics(graphics, radius, effectData);
            // Utility effect types
            case 'HEAL_ZONE':
                return this.createHealZoneGraphics(graphics, radius, effectData);
            case 'SLOW_FIELD':
                return this.createSlowFieldGraphics(graphics, radius, effectData);
            case 'SHIELD_BARRIER':
                return this.createShieldBarrierGraphics(graphics, radius, effectData);
            case 'GRAVITY_WELL':
                return this.createGravityWellGraphics(graphics, radius, effectData);
            case 'VISION_REVEAL':
                return this.createVisionRevealGraphics(graphics, radius, effectData);
            case 'SPEED_BOOST':
                return this.createSpeedBoostGraphics(graphics, radius, effectData);
            default:
                return this.createGenericEffectGraphics(graphics, radius, effectData);
        }
    }
    
    /**
     * Create explosion effect graphics
     */
    createExplosionGraphics(graphics, radius, effectData) {
        // Outer blast ring
        graphics.beginFill(0xff4444, 0.6);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Inner core
        graphics.beginFill(0xffaa44, 0.8);
        graphics.drawCircle(0, 0, radius * 0.6);
        graphics.endFill();
        
        // Bright center
        graphics.beginFill(0xffffff, 0.9);
        graphics.drawCircle(0, 0, radius * 0.3);
        graphics.endFill();
        
        return graphics;
    }
    
    /**
     * Create fire effect graphics
     */
    createFireGraphics(graphics, radius, effectData) {
        // Base fire area
        graphics.beginFill(0xff4444, 0.4);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Inner flames
        graphics.beginFill(0xff8844, 0.6);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Hot center
        graphics.beginFill(0xffaa44, 0.8);
        graphics.drawCircle(0, 0, radius * 0.4);
        graphics.endFill();
        
        // Add flame particles
        for (let i = 0; i < 8; i++) {
            const angle = (i / 8) * Math.PI * 2;
            const distance = radius * (0.6 + Math.random() * 0.4);
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 3 + Math.random() * 5;
            
            graphics.beginFill(0xff6644, 0.7);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create electric effect graphics
     */
    createElectricGraphics(graphics, radius, effectData) {
        // Electric field base
        graphics.beginFill(0x4444ff, 0.3);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Electric arcs
        graphics.lineStyle(2, 0x88aaff, 0.8);
        
        for (let i = 0; i < 6; i++) {
            const startAngle = (i / 6) * Math.PI * 2;
            const endAngle = startAngle + (Math.random() - 0.5) * Math.PI;
            
            const startX = Math.cos(startAngle) * radius * 0.2;
            const startY = Math.sin(startAngle) * radius * 0.2;
            const endX = Math.cos(endAngle) * radius * 0.9;
            const endY = Math.sin(endAngle) * radius * 0.9;
            
            // Draw zigzag lightning
            graphics.moveTo(startX, startY);
            
            const steps = 5;
            for (let j = 1; j <= steps; j++) {
                const t = j / steps;
                const x = startX + (endX - startX) * t + (Math.random() - 0.5) * 10;
                const y = startY + (endY - startY) * t + (Math.random() - 0.5) * 10;
                graphics.lineTo(x, y);
            }
        }
        
        // Bright electric center
        graphics.beginFill(0xaaffff, 0.9);
        graphics.drawCircle(0, 0, radius * 0.2);
        graphics.endFill();
        
        return graphics;
    }
    
    /**
     * Create freeze effect graphics
     */
    createFreezeGraphics(graphics, radius, effectData) {
        // Freeze field base
        graphics.beginFill(0x88ccff, 0.4);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Ice crystals
        graphics.lineStyle(2, 0xaaffff, 0.8);
        
        for (let i = 0; i < 8; i++) {
            const angle = (i / 8) * Math.PI * 2;
            const length = radius * (0.6 + Math.random() * 0.3);
            
            // Main crystal line
            graphics.moveTo(0, 0);
            graphics.lineTo(Math.cos(angle) * length, Math.sin(angle) * length);
            
            // Crystal branches
            const branchLength = length * 0.3;
            const branchX = Math.cos(angle) * length * 0.7;
            const branchY = Math.sin(angle) * length * 0.7;
            
            graphics.moveTo(branchX, branchY);
            graphics.lineTo(
                branchX + Math.cos(angle + Math.PI/4) * branchLength,
                branchY + Math.sin(angle + Math.PI/4) * branchLength
            );
            
            graphics.moveTo(branchX, branchY);
            graphics.lineTo(
                branchX + Math.cos(angle - Math.PI/4) * branchLength,
                branchY + Math.sin(angle - Math.PI/4) * branchLength
            );
        }
        
        // Frozen center
        graphics.beginFill(0xffffff, 0.7);
        graphics.drawCircle(0, 0, radius * 0.2);
        graphics.endFill();
        
        return graphics;
    }
    
    /**
     * Create fragmentation effect graphics
     */
    createFragmentationGraphics(graphics, radius, effectData) {
        // Fragmentation burst
        graphics.beginFill(0xffaa44, 0.5);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Fragment trails
        graphics.lineStyle(2, 0xff8844, 0.7);
        
        for (let i = 0; i < 12; i++) {
            const angle = (i / 12) * Math.PI * 2 + Math.random() * 0.2;
            const length = radius * (0.8 + Math.random() * 0.4);
            
            graphics.moveTo(0, 0);
            graphics.lineTo(Math.cos(angle) * length, Math.sin(angle) * length);
            
            // Fragment at end of trail
            graphics.beginFill(0xffcc44, 0.8);
            graphics.drawCircle(Math.cos(angle) * length, Math.sin(angle) * length, 2);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create poison effect graphics
     */
    createPoisonGraphics(graphics, radius, effectData) {
        // Outer poison cloud - darker green
        graphics.beginFill(0x2e7d32, 0.3);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Middle poison cloud - medium green
        graphics.beginFill(0x388e3c, 0.5);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Inner poison cloud - brighter green
        graphics.beginFill(0x4caf50, 0.6);
        graphics.drawCircle(0, 0, radius * 0.4);
        graphics.endFill();
        
        // Toxic bubbles scattered throughout
        for (let i = 0; i < 15; i++) {
            const angle = Math.random() * Math.PI * 2;
            const distance = Math.random() * radius * 0.9;
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 1.5 + Math.random() * 3;
            
            // Vary bubble colors for more realistic poison effect
            const bubbleColors = [0x66bb6a, 0x81c784, 0x9ccc65, 0x8bc34a];
            const bubbleColor = bubbleColors[Math.floor(Math.random() * bubbleColors.length)];
            
            graphics.beginFill(bubbleColor, 0.7);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        // Poison center - most concentrated
        graphics.beginFill(0x76ff03, 0.8);
        graphics.drawCircle(0, 0, radius * 0.2);
        graphics.endFill();
        
        // Add some swirling lines for gas effect
        graphics.lineStyle(1, 0x689f38, 0.4);
        for (let i = 0; i < 5; i++) {
            const startAngle = (i / 5) * Math.PI * 2;
            const spiralRadius = radius * 0.6;
            
            graphics.moveTo(
                Math.cos(startAngle) * spiralRadius * 0.3,
                Math.sin(startAngle) * spiralRadius * 0.3
            );
            
            // Create spiral effect
            for (let j = 1; j <= 8; j++) {
                const angle = startAngle + (j / 8) * Math.PI * 0.5;
                const currentRadius = spiralRadius * (0.3 + (j / 8) * 0.7);
                graphics.lineTo(
                    Math.cos(angle) * currentRadius,
                    Math.sin(angle) * currentRadius
                );
            }
        }
        
        return graphics;
    }
    
    /**
     * Create heal zone effect graphics
     */
    createHealZoneGraphics(graphics, radius, effectData) {
        // Outer healing aura - soft green
        graphics.beginFill(0x2ecc71, 0.2);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Middle healing field - brighter green
        graphics.beginFill(0x27ae60, 0.4);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Inner core - bright healing light
        graphics.beginFill(0x2ecc71, 0.6);
        graphics.drawCircle(0, 0, radius * 0.3);
        graphics.endFill();
        
        // Add healing cross symbol
        graphics.lineStyle(3, 0x2ecc71, 0.8);
        const crossSize = radius * 0.4;
        // Vertical line
        graphics.moveTo(0, -crossSize);
        graphics.lineTo(0, crossSize);
        // Horizontal line
        graphics.moveTo(-crossSize, 0);
        graphics.lineTo(crossSize, 0);
        
        // Add healing particles
        for (let i = 0; i < 8; i++) {
            const angle = (i / 8) * Math.PI * 2;
            const distance = radius * (0.5 + Math.random() * 0.3);
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 2 + Math.random() * 3;
            
            graphics.beginFill(0x58d68d, 0.7);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    
    /**
     * Create slow field effect graphics
     */
    createSlowFieldGraphics(graphics, radius, effectData) {
        // Outer slow field - purple/blue
        graphics.beginFill(0x8e44ad, 0.3);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Middle field - darker purple
        graphics.beginFill(0x663399, 0.4);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Inner core - deep purple
        graphics.beginFill(0x4a235a, 0.5);
        graphics.drawCircle(0, 0, radius * 0.4);
        graphics.endFill();
        
        // Add slow effect ripples
        graphics.lineStyle(2, 0x9b59b6, 0.6);
        for (let i = 1; i <= 4; i++) {
            const rippleRadius = radius * (i / 5);
            graphics.drawCircle(0, 0, rippleRadius);
        }
        
        // Add slow particles (moving inward)
        for (let i = 0; i < 12; i++) {
            const angle = (i / 12) * Math.PI * 2;
            const distance = radius * (0.6 + Math.random() * 0.3);
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 1.5 + Math.random() * 2;
            
            graphics.beginFill(0xbb8fce, 0.8);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
            
            // Add inward-pointing arrows
            const arrowSize = 4;
            graphics.lineStyle(1, 0xbb8fce, 0.7);
            graphics.moveTo(x + Math.cos(angle) * arrowSize, y + Math.sin(angle) * arrowSize);
            graphics.lineTo(x - Math.cos(angle) * arrowSize, y - Math.sin(angle) * arrowSize);
        }
        
        return graphics;
    }
    
    /**
     * Create shield barrier effect graphics
     */
    createShieldBarrierGraphics(graphics, radius, effectData) {
        // Outer shield energy field - cyan/blue
        graphics.beginFill(0x3498db, 0.2);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Shield barrier ring
        graphics.lineStyle(4, 0x2980b9, 0.8);
        graphics.drawCircle(0, 0, radius * 0.8);
        
        // Inner shield core
        graphics.beginFill(0x5dade2, 0.4);
        graphics.drawCircle(0, 0, radius * 0.3);
        graphics.endFill();
        
        // Add hexagonal shield pattern
        graphics.lineStyle(2, 0x85c1e9, 0.6);
        const hexRadius = radius * 0.6;
        const hexPoints = [];
        for (let i = 0; i < 6; i++) {
            const angle = (i / 6) * Math.PI * 2;
            hexPoints.push(Math.cos(angle) * hexRadius);
            hexPoints.push(Math.sin(angle) * hexRadius);
        }
        graphics.drawPolygon(hexPoints);
        
        // Add shield energy sparks
        for (let i = 0; i < 16; i++) {
            const angle = (i / 16) * Math.PI * 2;
            const distance = radius * 0.8;
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const sparkSize = 1 + Math.random() * 2;
            
            graphics.beginFill(0xaed6f1, 0.9);
            graphics.drawCircle(x, y, sparkSize);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create gravity well effect graphics
     */
    createGravityWellGraphics(graphics, radius, effectData) {
        // Outer gravity field - dark purple/black
        graphics.beginFill(0x1a1a2e, 0.4);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Gravity distortion rings
        graphics.lineStyle(2, 0x6c5ce7, 0.5);
        for (let i = 1; i <= 6; i++) {
            const ringRadius = radius * (i / 7);
            graphics.drawCircle(0, 0, ringRadius);
        }
        
        // Central singularity
        graphics.beginFill(0x0f0f23, 0.9);
        graphics.drawCircle(0, 0, radius * 0.15);
        graphics.endFill();
        
        // Event horizon glow
        graphics.beginFill(0x6c5ce7, 0.6);
        graphics.drawCircle(0, 0, radius * 0.25);
        graphics.endFill();
        
        // Add gravitational particles (spiraling inward)
        for (let i = 0; i < 20; i++) {
            const angle = (i / 20) * Math.PI * 2;
            const spiralOffset = (i / 20) * Math.PI * 4; // Multiple spirals
            const distance = radius * (0.4 + Math.random() * 0.5);
            const x = Math.cos(angle + spiralOffset) * distance;
            const y = Math.sin(angle + spiralOffset) * distance;
            const size = 1 + Math.random() * 1.5;
            
            graphics.beginFill(0xa29bfe, 0.7);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create vision reveal effect graphics
     */
    createVisionRevealGraphics(graphics, radius, effectData) {
        // Outer reveal field - bright yellow/gold
        graphics.beginFill(0xf1c40f, 0.2);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Middle scanning ring
        graphics.beginFill(0xe67e22, 0.3);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Inner radar core
        graphics.beginFill(0xf39c12, 0.5);
        graphics.drawCircle(0, 0, radius * 0.2);
        graphics.endFill();
        
        // Add radar sweep lines
        graphics.lineStyle(2, 0xf1c40f, 0.7);
        for (let i = 0; i < 8; i++) {
            const angle = (i / 8) * Math.PI * 2;
            graphics.moveTo(0, 0);
            graphics.lineTo(Math.cos(angle) * radius * 0.9, Math.sin(angle) * radius * 0.9);
        }
        
        // Add scanning pulses
        graphics.lineStyle(3, 0xf39c12, 0.8);
        for (let i = 1; i <= 3; i++) {
            const pulseRadius = radius * (i / 4);
            graphics.drawCircle(0, 0, pulseRadius);
        }
        
        // Add vision enhancement particles
        for (let i = 0; i < 12; i++) {
            const angle = (i / 12) * Math.PI * 2;
            const distance = radius * (0.5 + Math.random() * 0.4);
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 1.5 + Math.random() * 2;
            
            graphics.beginFill(0xffd700, 0.8);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create speed boost effect graphics
     */
    createSpeedBoostGraphics(graphics, radius, effectData) {
        // Outer speed field - bright green/lime
        graphics.beginFill(0x2ecc71, 0.2);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        // Middle boost ring
        graphics.beginFill(0x27ae60, 0.3);
        graphics.drawCircle(0, 0, radius * 0.7);
        graphics.endFill();
        
        // Inner speed core
        graphics.beginFill(0x00ff88, 0.5);
        graphics.drawCircle(0, 0, radius * 0.3);
        graphics.endFill();
        
        // Add speed boost arrows pointing outward
        graphics.lineStyle(3, 0x00ff88, 0.8);
        for (let i = 0; i < 8; i++) {
            const angle = (i / 8) * Math.PI * 2;
            const innerRadius = radius * 0.4;
            const outerRadius = radius * 0.8;
            const arrowSize = radius * 0.1;
            
            // Main arrow line
            const startX = Math.cos(angle) * innerRadius;
            const startY = Math.sin(angle) * innerRadius;
            const endX = Math.cos(angle) * outerRadius;
            const endY = Math.sin(angle) * outerRadius;
            
            graphics.moveTo(startX, startY);
            graphics.lineTo(endX, endY);
            
            // Arrow head
            const headAngle1 = angle + Math.PI * 0.8;
            const headAngle2 = angle - Math.PI * 0.8;
            
            graphics.moveTo(endX, endY);
            graphics.lineTo(
                endX + Math.cos(headAngle1) * arrowSize,
                endY + Math.sin(headAngle1) * arrowSize
            );
            
            graphics.moveTo(endX, endY);
            graphics.lineTo(
                endX + Math.cos(headAngle2) * arrowSize,
                endY + Math.sin(headAngle2) * arrowSize
            );
        }
        
        // Add speed particles (moving outward)
        for (let i = 0; i < 16; i++) {
            const angle = (i / 16) * Math.PI * 2;
            const distance = radius * (0.3 + Math.random() * 0.4);
            const x = Math.cos(angle) * distance;
            const y = Math.sin(angle) * distance;
            const size = 1 + Math.random() * 2;
            
            graphics.beginFill(0x58d68d, 0.9);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
        
        return graphics;
    }
    
    /**
     * Create generic effect graphics
     */
    createGenericEffectGraphics(graphics, radius, effectData) {
        graphics.beginFill(0x888888, 0.5);
        graphics.drawCircle(0, 0, radius);
        graphics.endFill();
        
        graphics.beginFill(0xcccccc, 0.7);
        graphics.drawCircle(0, 0, radius * 0.5);
        graphics.endFill();
        
        return graphics;
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
        this.app.ticker.add(container.animationFunction);
    }
    
    /**
     * Animate field effects
     */
    animateEffect(container) {
        if (!container.effectData || !container.parent || !container.effectGraphics) {
            // Effect has been removed, stop animating
            if (container.animationFunction) {
                this.app.ticker.remove(container.animationFunction);
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
            case 'VISION_REVEAL':
                this.animateVisionReveal(container);
                break;
            case 'SPEED_BOOST':
                this.animateSpeedBoost(container);
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
            scale = 0.3 + (time / 0.1) * 1.2; // Expand from 0.3 to 1.5
        } else {
            scale = 1.5 - progress * 0.3; // Shrink based on server progress
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
        
        // Flickering effect
        const flicker = 0.8 + Math.sin(time * 15) * 0.2;
        container.alpha = flicker;
        
        // Slight scale variation
        const scale = 0.9 + Math.sin(time * 8) * 0.1;
        container.scale.set(scale);
        
        // Subtle rotation
        container.rotation = Math.sin(time * 3) * 0.1;
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
        
        // Rapid expansion throughout the effect
        const scale = 0.3 + progress * 1.2;
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
        
        // Slow bubbling effect with multiple frequencies for organic feel
        const bubble1 = Math.sin(time * 4) * 0.05;
        const bubble2 = Math.sin(time * 6.5) * 0.03;
        const bubble3 = Math.sin(time * 8.2) * 0.02;
        const totalBubble = 0.95 + bubble1 + bubble2 + bubble3;
        container.scale.set(totalBubble);
        
        // Gentle swaying with multiple wave components for more natural movement
        const sway1 = Math.sin(time * 1.8) * 0.03;
        const sway2 = Math.sin(time * 2.7) * 0.02;
        container.rotation = sway1 + sway2;
        
        // Pulsing alpha to simulate gas density changes
        const pulse1 = Math.sin(time * 3) * 0.08;
        const pulse2 = Math.sin(time * 5.3) * 0.05;
        container.alpha = 0.75 + pulse1 + pulse2;
        
        // Add subtle position drift to simulate gas movement
        if (!container.originalX) {
            container.originalX = container.x;
            container.originalY = container.y;
        }
        
        const drift = time * 0.3;
        container.x = container.originalX + Math.sin(drift) * 2;
        container.y = container.originalY + Math.cos(drift * 1.3) * 1.5;
    }
    
    /**
     * Animate heal zone effects
     */
    animateHealZone(container) {
        const time = container.animationTime;
        
        // Gentle pulsing for healing effect
        const pulse = 0.9 + Math.sin(time * 4) * 0.1;
        container.scale.set(pulse);
        
        // Soft breathing alpha
        const breathe = 0.8 + Math.sin(time * 3) * 0.2;
        container.alpha = breathe;
        
        // Slow rotation for mystical feel
        container.rotation = time * 0.5;
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
        const time = container.animationTime;
        
        // Shield energy fluctuation (slowed down)
        const energy = 0.95 + Math.sin(time * 2) * 0.05;
        container.scale.set(energy);
        
        // Shield shimmer effect (slowed down)
        const shimmer = 0.8 + Math.sin(time * 3) * 0.15;
        container.alpha = shimmer;
        
        // Steady rotation for energy field (slowed down)
        container.rotation = time * 0.015;
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
     * Animate vision reveal effects
     */
    animateVisionReveal(container) {
        const time = container.animationTime;
        
        // Radar sweep effect
        const sweep = 0.9 + Math.sin(time * 8) * 0.1;
        container.scale.set(sweep);
        
        // Scanning pulse alpha
        const scan = 0.7 + Math.sin(time * 12) * 0.2;
        container.alpha = scan;
        
        // Fast rotation for radar sweep
        container.rotation = time * 3.0;
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
            case 'VISION_REVEAL':
                return 0.15; // Fast, active scanning
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
     * Fade out effect before removal
     */
    fadeOutEffect(container, callback) {
        const fadeSpeed = 0.05;
        
        // Store fade function reference for proper cleanup
        const fadeOut = () => {
            if (!container || container.alpha === undefined) {
                // Container was already destroyed, clean up ticker
                this.app.ticker.remove(fadeOut);
                if (callback) callback();
                return;
            }
            
            container.alpha -= fadeSpeed;
            if (container.alpha <= 0) {
                this.app.ticker.remove(fadeOut);
                if (callback) callback();
            }
        };
        
        // Store reference on container for emergency cleanup
        container.fadeOutFunction = fadeOut;
        this.app.ticker.add(fadeOut);
        
        // Safety timeout to prevent infinite fade
        this.safeSetTimeout(() => {
            if (container && container.fadeOutFunction) {
                this.app.ticker.remove(container.fadeOutFunction);
                container.fadeOutFunction = null;
                if (callback) callback();
            }
        }, 5000); // 5 second timeout
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
                this.hudTeamText.style.fill = 0x808080; // Gray for FFA
            } else {
                this.hudTeamText.text = teamNumber.toString();
                this.hudTeamText.style.fill = this.getTeamColor(teamNumber); // Team color
            }
        }
        
        // Update input source info
        if (this.hudInputText && this.inputManager) {
            if (this.inputManager.gamepad.connected && this.inputManager.inputSource === 'gamepad') {
                this.hudInputText.text = 'Gamepad';
                this.hudInputText.style.fill = 0x44ff44; // Green for gamepad
            } else {
                this.hudInputText.text = 'Keyboard';
                this.hudInputText.style.fill = 0xffffff; // White for keyboard
            }
        }
        
        // Update lives info
        if (this.hudLivesText) {
            const livesRemaining = myPlayer.livesRemaining || -1;
            if (livesRemaining === -1) {
                // Unlimited lives (default mode)
                this.hudLivesText.text = '∞';
                this.hudLivesText.style.fill = 0x44ff44; // Green for unlimited
            } else if (livesRemaining === 0) {
                // Eliminated
                this.hudLivesText.text = 'ELIM';
                this.hudLivesText.style.fill = 0xff4444; // Red for eliminated
            } else {
                // Limited lives (stock mode)
                this.hudLivesText.text = livesRemaining.toString();
                this.hudLivesText.style.fill = 0xffaa00; // Orange for limited lives
            }
        }
    }
    
    updateScoreboard(players) {
        const content = document.getElementById('scoreboard-content');
        if (!content || !players) return;
        
        // Check if we're in team mode
        const hasTeams = players.some(p => p.team && p.team > 0);
        
        if (hasTeams) {
            this.updateTeamScoreboard(content, players);
        } else {
            this.updateFFAScoreboard(content, players);
        }
    }
    
    updateFFAScoreboard(content, players) {
        const sortedPlayers = [...players].sort((a, b) => (b.kills || 0) - (a.kills || 0));
        
        // Check if any player has captures (CTF mode)
        const hasCaptures = players.some(p => (p.captures || 0) > 0);
        
        content.innerHTML = `
            <table style="width: 100%; color: white;">
                <thead>
                    <tr>
                        <th>Player</th>
                        <th>Kills</th>
                        <th>Deaths</th>
                        ${hasCaptures ? '<th>Captures</th>' : ''}
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${sortedPlayers.map(player => `
                        <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                            <td><span style="color: ${this.getTeamColorCSS(player.team || 0)}">●</span> ${player.name || `Player ${player.id}`}</td>
                            <td>${player.kills || 0}</td>
                            <td>${player.deaths || 0}</td>
                            ${hasCaptures ? `<td style="color: #FFD700;">${player.captures || 0} 🚩</td>` : ''}
                            <td>${player.active ? 'Alive' : 'Dead'}</td>
                        </tr>
                    `).join('')}
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
                            .map(player => `
                                <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                                    <td style="padding: 2px;">${player.name || `Player ${player.id}`}</td>
                                    <td style="padding: 2px; text-align: center;">${player.kills || 0}K</td>
                                    <td style="padding: 2px; text-align: center;">${player.deaths || 0}D</td>
                                    ${hasCaptures ? `<td style="padding: 2px; text-align: center; color: #FFD700;">${player.captures || 0}🚩</td>` : ''}
                                    <td style="padding: 2px; text-align: center;">${player.active ? '✓' : '✗'}</td>
                                </tr>
                            `).join('')}
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
        
        // Clear previous minimap content (keep background and title)
        if (this.minimapContent) {
            this.hudMinimap.removeChild(this.minimapContent);
        }
        
        // Create new minimap content container
        this.minimapContent = new PIXI.Container();
        this.minimapContent.position.set(2, 16); // Below title, within border
        
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

        // Draw players
        this.players.forEach(player => {
            const data = player.playerData;
            if (!data.active) return;
            
            const x = (data.x + this.worldBounds.width / 2) * scale + offsetX;
            const y = ((-data.y) + this.worldBounds.height / 2) * scale + offsetY; // Invert Y for minimap
            
            const playerDot = new PIXI.Graphics();
            
            // Use team colors
            const teamColor = this.getTeamColor(data.team || 0);
            playerDot.beginFill(teamColor);
            
            // Make current player slightly larger
            const radius = data.id === this.myPlayerId ? 2.5 : 1.5;
            playerDot.drawCircle(x, y, radius);
            playerDot.endFill();
            
            // Add white border for current player
            if (data.id === this.myPlayerId) {
                playerDot.lineStyle(1, 0xffffff);
                playerDot.drawCircle(x, y, radius);
            }
            
            this.minimapContent.addChild(playerDot);
        });
        
        this.hudMinimap.addChild(this.minimapContent);
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
    
    sendPlayerConfiguration() {
        const params = new URLSearchParams(window.location.search);
        
        // Try to decode Base64 encoded config first
        let playerConfig = null;
        const encodedConfig = params.get('config');
        
        if (encodedConfig) {
            try {
                const decodedString = decodeURIComponent(escape(atob(encodedConfig)));
                playerConfig = JSON.parse(decodedString);
            } catch (error) {
                console.error('Failed to decode player config:', error);
                playerConfig = null;
            }
        }
        
        // Use decoded config or fall back to legacy URL params
        let weaponConfig, utilityWeapon;
        
        if (playerConfig && playerConfig.weaponConfig) {
            weaponConfig = playerConfig.weaponConfig;
            utilityWeapon = playerConfig.utilityWeapon;
        } else {
            throw Error("missing configuration")
        }

        const message = {
            type: 'configChange',
            weaponConfig: weaponConfig,
            utilityWeapon: utilityWeapon
        };

        console.log('Sending player configuration:', message);
        this.websocket.send(JSON.stringify(message));
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
        
        // Set line style for the grid
        grid.lineStyle(1, 0x3a5f3f, 0.4); // Semi-transparent green lines
        
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
        
        // Add crosshatch pattern (diagonal lines every 4th grid line)
        grid.lineStyle(1, 0x4a6f4f, 0.2); // Even more subtle diagonal lines
        
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
        
        // Add world boundary
        grid.lineStyle(3, 0x5a8f5f, 0.8); // Thicker, more visible boundary
        grid.drawRect(-worldWidth/2, -worldHeight/2, worldWidth, worldHeight);
        
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
            graphics.beginFill(teamColor, 0.1);
            graphics.lineStyle(2, teamColor, 0.5);
            graphics.drawRect(
                areaData.minX, 
                -areaData.maxY, // Invert Y for PIXI
                areaData.maxX - areaData.minX,
                areaData.maxY - areaData.minY
            );
            graphics.endFill();
            graphics.zIndex = -1; // Behind everything else
            
            this.backgroundContainer.addChild(graphics);
        });
    }
    
    /**
     * Create simple dark background (terrain features disabled for better visibility).
     */
    createProceduralTerrain() {
        if (!this.terrainData || !this.terrainData.metadata) return;
        const metadata = this.terrainData.metadata;
        this.updateBackgroundForTerrain(metadata);
    }
    
    /**
     * Update background to simple dark color for better visibility.
     */
    updateBackgroundForTerrain(metadata) {
        const darkBackground = 0x1a1a1a; // Dark grey
        this.app.renderer.backgroundColor = darkBackground;
        this.createSimpleDarkBackground();
    }
    
    /**
     * Create simple dark background for better visibility.
     */
    createSimpleDarkBackground() {
        this.backgroundContainer.removeChildren();
        const graphics = new PIXI.Graphics();
        graphics.beginFill(0x1a1a1a); // Dark grey
        graphics.drawRect(
            -this.worldBounds.width / 2, 
            -this.worldBounds.height / 2, 
            this.worldBounds.width, 
            this.worldBounds.height
        );
        graphics.endFill();
        graphics.zIndex = -10; // Far background
        this.backgroundContainer.addChild(graphics);
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
        
        console.log('Entity Management Stats:', stats);
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
                console.warn(`Found orphaned projectile interpolator for ID ${id}, cleaning up`);
                interpolator.destroy();
                this.projectileInterpolators.delete(id);
            }
        });
        
        this.playerInterpolators.forEach((interpolator, id) => {
            if (!this.players.has(id)) {
                console.warn(`Found orphaned player interpolator for ID ${id}, cleaning up`);
                interpolator.destroy();
                this.playerInterpolators.delete(id);
            }
        });
        
        // Clean up any field effects with orphaned animation functions
        this.fieldEffects.forEach((effect, id) => {
            if (effect.animationFunction && (!effect.parent || !effect.effectData)) {
                console.warn(`Found orphaned field effect animation for ID ${id}, cleaning up`);
                this.app.ticker.remove(effect.animationFunction);
                effect.animationFunction = null;
            }
            if (effect.fadeOutFunction && (!effect.parent || effect.alpha <= 0)) {
                console.warn(`Found orphaned field effect fade for ID ${id}, cleaning up`);
                this.app.ticker.remove(effect.fadeOutFunction);
                effect.fadeOutFunction = null;
            }
        });
        
        // Clean up orphaned obstacle health bars
        if (this.obstacleHealthBars) {
            this.obstacleHealthBars.forEach((healthBar, obstacleId) => {
                if (!this.obstacles.has(obstacleId)) {
                    console.warn(`Found orphaned obstacle health bar for ID ${obstacleId}, cleaning up`);
                    if (healthBar.parent) {
                        healthBar.parent.removeChild(healthBar);
                    }
                    healthBar.destroy();
                    this.obstacleHealthBars.delete(obstacleId);
                }
            });
        }

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
        console.log('Destroying game engine...');
        
        // Clear the memory cleanup interval
        if (this.memoryCleanupInterval) {
            clearInterval(this.memoryCleanupInterval);
            this.memoryCleanupInterval = null;
        }
        
        // Clear all pending timeouts
        this.pendingTimeouts.forEach(timeoutId => clearTimeout(timeoutId));
        this.pendingTimeouts = [];
        
        // Remove all ticker callbacks
        if (this.app && this.app.ticker) {
            this.tickerCallbacks.forEach(callback => {
                this.app.ticker.remove(callback);
            });
            this.tickerCallbacks = [];
            
            // Stop ticker
            this.app.ticker.stop();
        }
        
        // Remove all event listeners
        if (this.app && this.app.renderer && this.app.renderer.gl && this.app.renderer.gl.canvas) {
            if (this.eventHandlers.webglContextLost) {
                this.app.renderer.gl.canvas.removeEventListener('webglcontextlost', this.eventHandlers.webglContextLost);
            }
            if (this.eventHandlers.webglContextRestored) {
                this.app.renderer.gl.canvas.removeEventListener('webglcontextrestored', this.eventHandlers.webglContextRestored);
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
        
        this.playerInterpolators.forEach(interpolator => interpolator.destroy());
        this.playerInterpolators.clear();
        
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
            if (obstacle.clear && typeof obstacle.clear === 'function') {
                obstacle.clear();
            }
            obstacle.obstacleData = null;
            obstacle.destroy();
        });
        this.obstacles.clear();
        
        // Clean up obstacle health bars
        if (this.obstacleHealthBars) {
            this.obstacleHealthBars.forEach(healthBar => {
                if (healthBar.parent) {
                    healthBar.parent.removeChild(healthBar);
                }
                healthBar.destroy();
            });
            this.obstacleHealthBars.clear();
        }
        
        // Clean up flags
        this.flags.forEach(flag => {
            flag.destroy({ children: true });
        });
        this.flags.clear();
        
        // Clean up KOTH zones
        this.kothZones.forEach(zone => {
            zone.destroy({ children: true });
        });
        this.kothZones.clear();
        
        // Clean up teleport pad connections
        this.teleportConnections.forEach(connection => {
            if (connection.animationFunction) {
                this.app.ticker.remove(connection.animationFunction);
            }
            if (connection.parent) {
                connection.parent.removeChild(connection);
            }
            connection.destroy();
        });
        this.teleportConnections.clear();
        
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
        
        console.log('GameEngine destroyed and cleaned up');
    }
}

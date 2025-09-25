/**
 * Full Steam - Isometric Battle Arena Game Engine
 * Built with PixiJS for 2.5D isometric rendering
 */

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
        
        // Prediction state (for local player)
        this.predictedPos = { x: sprite.x, y: sprite.y };
        this.predictedRotation = 0;
        this.velocity = { x: 0, y: 0 };
        
        // Interpolation settings
        this.smoothingFactor = isLocalPlayer ? 0.3 : 0.15; // Local player needs faster correction
        this.correctionThreshold = isLocalPlayer ? 25 : 15; // Local player allows more deviation
        this.maxCorrectionSpeed = 400; // pixels/second
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
            this.sprite.rotation = -rotation; // Invert for PIXI
            
            // Reset prediction state for local player
            if (this.isLocalPlayer) {
                this.predictedPos.x = x;
                this.predictedPos.y = y;
                this.predictedRotation = rotation;
                this.velocity.x = 0;
                this.velocity.y = 0;
            }
            
            // Player teleported/respawned - no logging needed for performance
            this.isFirstUpdate = false;
        } else {
            // Normal movement - use interpolation
            if (this.isLocalPlayer) {
                // Local player: Apply server reconciliation
                this.reconcileWithServer(x, y, rotation, timeSinceLastUpdate);
            } else {
                // Remote player: Apply smooth interpolation
                this.interpolateToServer(x, y, rotation, timeSinceLastUpdate);
            }
            this.isFirstUpdate = false;
        }
        
        // Update server reference
        this.serverPos.x = x;
        this.serverPos.y = y;
        this.serverRotation = rotation;
        this.lastServerUpdate = now;
    }
    
    reconcileWithServer(serverX, serverY, serverRotation, deltaTime) {
        // For local player: check if server position differs significantly from prediction
        if (this.tempDistance > this.correctionThreshold) {
            if (this.tempDistance > 100) {
                // Major desync - snap to server position
                this.sprite.x = serverX;
                this.sprite.y = serverY;
                this.predictedPos.x = serverX;
                this.predictedPos.y = serverY;
            } else {
                // Minor desync - gradually correct prediction
                const correctionFactor = Math.min(1.0, (this.maxCorrectionSpeed * deltaTime) / this.tempDistance);
                const correctionX = this.tempDx * -correctionFactor;
                const correctionY = this.tempDy * -correctionFactor;
                
                this.sprite.x += correctionX;
                this.sprite.y += correctionY;
                this.predictedPos.x += correctionX;
                this.predictedPos.y += correctionY;
            }
        }
        
        // Always update rotation smoothly
        this.sprite.rotation = this.lerpAngle(this.sprite.rotation, -serverRotation, this.smoothingFactor);
        this.predictedRotation = -serverRotation;
    }
    
    interpolateToServer(serverX, serverY, serverRotation, deltaTime) {
        // For remote players: smooth interpolation to server position
        const lerpFactor = Math.min(1.0, this.smoothingFactor * (deltaTime * 60)); // Adjust for frame rate
        
        this.sprite.x += (serverX - this.sprite.x) * lerpFactor;
        this.sprite.y += (serverY - this.sprite.y) * lerpFactor;
        this.sprite.rotation = this.lerpAngle(this.sprite.rotation, -serverRotation, lerpFactor);
    }
    
    predictMovement(input, deltaTime) {
        if (!this.isLocalPlayer || !input) return;
        
        // Client-side prediction for local player
        const moveSpeed = 200; // Should match server movement speed
        const sprintMultiplier = input.shift ? 1.5 : 1.0;
        
        // Calculate predicted velocity
        this.velocity.x = input.moveX * moveSpeed * sprintMultiplier;
        this.velocity.y = input.moveY * moveSpeed * sprintMultiplier;
        
        // Apply predicted movement
        this.predictedPos.x += this.velocity.x * deltaTime;
        this.predictedPos.y += this.velocity.y * deltaTime;
        
        // Update sprite position with prediction
        this.sprite.x = this.predictedPos.x;
        this.sprite.y = this.predictedPos.y;
        
        // Update rotation based on mouse input
        if (input.worldX !== undefined && input.worldY !== undefined) {
            const dx = input.worldX - this.predictedPos.x;
            const dy = input.worldY - this.predictedPos.y;
            this.predictedRotation = Math.atan2(dy, dx);
            this.sprite.rotation = -this.predictedRotation; // Invert for PIXI
        }
    }
    
    lerpAngle(from, to, factor) {
        // Handle angle wrapping for smooth rotation
        let diff = to - from;
        if (diff > Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        return from + diff * factor;
    }
    
    destroy() {
        this.sprite = null;
        this.serverPos = null;
        this.predictedPos = null;
        this.velocity = null;
        this.isFirstUpdate = null;
    }
}

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
        this.container = null;
        this.velocity = null;
        this.serverPos = null;
        this.lastTrailPosition = null;
    }
}

class GameEngine {
    constructor() {
        this.app = null;
        this.gameContainer = null;
        this.uiContainer = null;
        this.players = new Map();
        this.playerInterpolators = new Map();
        this.projectiles = new Map();
        this.projectileInterpolators = new Map();
        this.strategicLocations = new Map();
        this.obstacles = new Map();
        this.fieldEffects = new Map();
        this.myPlayerId = null;
        this.lastPlayerInput = null; // Store last input for prediction
        this.gameState = null;
        this.websocket = null;
        this.inputManager = null;
        this.camera = null;
        this.worldBounds = { width: 2000, height: 2000 };
        
        // Game settings
        this.zoomLevel = 1.0;
        this.minZoom = 0.5;
        this.maxZoom = 2.0;
        
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
            backgroundColor: 0x1a3d1f, // Darker green for better contrast with grid
            antialias: true,
            resolution: window.devicePixelRatio || 1,
            autoDensity: true
        });

        document.getElementById('pixi-container').appendChild(this.app.view);

        // Set up interpolation ticker for smooth movement
        this.app.ticker.add((deltaTime) => {
            const dt = deltaTime / 60.0; // Convert to seconds
            
            // Update all projectile interpolators every frame
            this.projectileInterpolators.forEach(interpolator => {
                interpolator.update(deltaTime);
            });
            
            // Update player predictions (only for local player)
            if (this.myPlayerId && this.lastPlayerInput) {
                const localInterpolator = this.playerInterpolators.get(this.myPlayerId);
                if (localInterpolator) {
                    localInterpolator.predictMovement(this.lastPlayerInput, dt);
                }
            }
            
            // Animate plasma effects
            this.projectiles.forEach(projectileContainer => {
                if (projectileContainer.isPlasma) {
                    this.animatePlasmaEffects(projectileContainer, deltaTime);
                }
            });
        });

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

        // Note: Grid creation moved to handleInitialState() to use correct world dimensions

        // Handle window resize
        window.addEventListener('resize', () => this.handleResize());
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
        
        // Store references for updates (removed health references)
        this.hudWeaponText = weaponText;
        this.hudAmmoText = ammoText;
        this.hudReloadText = reloadText;
        this.hudTeamText = teamText;
        this.hudInputText = inputText;
        
        this.hudContainer.addChild(infoContainer);
    }
    
    /**
     * Get the color for a player based on their team.
     * Uses easily distinguishable colors for team-based gameplay.
     */
    getPlayerColor(playerData) {
        // Special highlight for the current player
        if (playerData.id === this.myPlayerId) {
            // Make the current player brighter but still show their team color
            const baseColor = this.getTeamColor(playerData.team || 0);
            // Brighten the color for the current player
            return this.brightenColor(baseColor);
        }
        
        return this.getTeamColor(playerData.team || 0);
    }
    
    /**
     * Get the current player's data for gamepad aiming
     */
    getMyPlayer() {
        if (!this.myPlayerId) return null;
        const sprite = this.players.get(this.myPlayerId);
        return sprite ? sprite.playerData : null;
    }
    
    /**
     * Get the base color for a team.
     */
    getTeamColor(teamNumber) {
        switch (teamNumber) {
            case 0: return 0x808080; // Gray for FFA/no team
            case 1: return 0xff4444; // Red
            case 2: return 0x4444ff; // Blue  
            case 3: return 0x44ff44; // Green
            case 4: return 0xffff44; // Yellow
            default: 
                console.warn(`Unexpected team number: ${teamNumber}, using magenta`);
                return 0xff44ff; // Magenta for unexpected teams
        }
    }
    
    /**
     * Brighten a color for the current player highlight.
     */
    brightenColor(color) {
        const r = Math.min(255, ((color >> 16) & 0xFF) + 60);
        const g = Math.min(255, ((color >> 8) & 0xFF) + 60);
        const b = Math.min(255, (color & 0xFF) + 60);
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Get team color as CSS color string for minimap.
     */
    getTeamColorCSS(teamNumber) {
        const color = this.getTeamColor(teamNumber);
        const r = (color >> 16) & 0xFF;
        const g = (color >> 8) & 0xFF;
        const b = color & 0xFF;
        return `rgb(${r}, ${g}, ${b})`;
    }
    
    setupCamera() {
        this.camera = {
            x: 0,
            y: 0,
            targetX: 0,
            targetY: 0,
            smoothing: 0.1
        };
        
        // Start game loop
        this.app.ticker.add(() => this.gameLoop());
    }
    
    setupInput() {
        // Make gameEngine globally accessible for InputManager
        window.gameEngine = this;
        
        this.inputManager = new InputManager();
        this.inputManager.onInputChange = (input) => {
            // Store input for client-side prediction
            this.lastPlayerInput = input;
            this.sendPlayerInput(input);
        };
        
        // Store reference for HUD updates
        this.inputManager.gameEngine = this;
    }
    
    setupUI() {
        // Setup event listeners
        document.getElementById('close-scoreboard')?.addEventListener('click', () => {
            document.getElementById('scoreboard').style.display = 'none';
        });
        
        // Tab to show scoreboard
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                document.getElementById('scoreboard').style.display = 'block';
            }
        });
        
        document.addEventListener('keyup', (e) => {
            if (e.key === 'Tab') {
                document.getElementById('scoreboard').style.display = 'none';
            }
        });
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
        
        // Strategic location - clean circular area
        const locationGraphics = new PIXI.Graphics();
        locationGraphics.beginFill(0x8e44ad, 0.3);
        locationGraphics.drawCircle(0, 0, 50);
        locationGraphics.endFill();
        locationGraphics.lineStyle(3, 0x9b59b6);
        locationGraphics.drawCircle(0, 0, 50);
        this.locationTexture = this.app.renderer.generateTexture(locationGraphics);

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
        const gameType = params.get('gameType') || 'Battle Royale';
        
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/game/${gameId}/${gameType}`;
        
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
            
            setTimeout(() => {
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
        // Simple 2D top-down view with inverted Y axis to match screen coordinates
        // In physics: +Y = up, in screen: +Y = down
        return { x: worldX, y: -worldY };
    }
    
    worldVelocityToIsometric(vx, vy) {
        // Convert velocity from world coordinates to isometric screen coordinates
        // Same transformation as position: invert Y axis
        return { x: vx, y: -vy };
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
        }
    }
    
    handleInitialState(data) {
        this.worldBounds.width = data.worldWidth || 2000;
        this.worldBounds.height = data.worldHeight || 2000;
        
        // Store team information
        this.teamMode = data.teamMode || false;
        this.teamCount = data.teamCount || 0;
        this.teamAreas = data.teamAreas || null;
        
        // Store terrain information
        this.terrainData = data.terrain || null;
        
        if (data.locations) {
            data.locations.forEach(location => {
                this.createStrategicLocation(location);
            });
        }

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
        
        if (data.players) {
            const currentPlayerIds = new Set();
            
            data.players.forEach(playerData => {
                currentPlayerIds.add(playerData.id);
                
                if (this.players.has(playerData.id)) {
                    this.updatePlayer(playerData);
                } else {
                    this.createPlayer(playerData);
                }
                
                // Set our player ID if we don't have one yet
                if (!this.myPlayerId) {
                    this.myPlayerId = playerData.id;
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
        
        if (data.locations) {
            // Check if any location is being captured
            let anyLocationBeingCaptured = false;
            
            data.locations.forEach(locationData => {
                this.updateStrategicLocation(locationData);
                
                if (locationData.capturingPlayer && locationData.captureProgress > 0) {
                    anyLocationBeingCaptured = true;
                    this.showCaptureProgress(locationData);
                }
            });
            
            // Hide capture progress if no location is being captured
            if (!anyLocationBeingCaptured) {
                this.hideCaptureProgress();
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
        
        this.updateUI(data);
    }
    
    handlePlayerKilled(data) {
        if (data.victimId === this.myPlayerId) {
            this.showDeathScreen(data);
        }
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
        const healthBarContainer = this.createHealthBar(playerData);
        sprite.healthBar = healthBarContainer;
    
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

        // Use interpolator for smooth movement
        const interpolator = this.playerInterpolators.get(playerData.id);
        if (interpolator) {
            // Convert server position from world to screen coordinates
            const isoPos = this.worldToIsometric(playerData.x, playerData.y);
            
            // Update interpolator with server data
            interpolator.updateFromServer(isoPos.x, isoPos.y, playerData.rotation || 0);
        } else {
            // Fallback to direct position update if no interpolator
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
            this.updateHealthBar(sprite.healthBar, playerData);
            sprite.healthBar.visible = playerData.active && playerData.health > 0;
        }

        sprite.playerData = playerData;
    }
    
    removePlayer(playerId) {
        const sprite = this.players.get(playerId);
        if (sprite) {
            // Remove sprite from game container
            this.gameContainer.removeChild(sprite);
            
            // Remove name label from name container
            if (sprite.nameLabel) {
                this.nameContainer.removeChild(sprite.nameLabel);
            }
            
            // Remove health bar from name container
            if (sprite.healthBar) {
                this.nameContainer.removeChild(sprite.healthBar);
            }
            
            // Remove death marker from game container
            if (sprite.deathMarker) {
                this.gameContainer.removeChild(sprite.deathMarker);
            }
            
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
     * Create health bar for a player.
     */
    createHealthBar(playerData) {
        const healthBarContainer = new PIXI.Container();
        
        // Health bar background
        const healthBg = new PIXI.Graphics();
        healthBg.beginFill(0x333333);
        healthBg.drawRoundedRect(-25, 0, 50, 6, 2);
        healthBg.endFill();
        healthBarContainer.addChild(healthBg);
        
        // Health bar fill
        const healthFill = new PIXI.Graphics();
        healthFill.beginFill(0x2ecc71);
        healthFill.drawRoundedRect(-25, 0, 50, 6, 2);
        healthFill.endFill();
        healthBarContainer.addChild(healthFill);
        
        // Store references for updates
        healthBarContainer.healthBg = healthBg;
        healthBarContainer.healthFill = healthFill;
        
        // Position above player (will be updated in updatePlayer)
        const isoPos = this.worldToIsometric(playerData.x, playerData.y);
        healthBarContainer.position.set(isoPos.x, isoPos.y - 35);
        
        // Add to name container so it doesn't rotate with player
        this.nameContainer.addChild(healthBarContainer);
        
        return healthBarContainer;
    }
    
    /**
     * Update health bar appearance and position.
     */
    updateHealthBar(healthBarContainer, playerData) {
        if (!healthBarContainer || !healthBarContainer.healthFill) return;
        
        // Update position above player using current sprite position (may be interpolated)
        const sprite = this.players.get(playerData.id);
        if (sprite) {
            healthBarContainer.position.set(sprite.x, sprite.y - 35);
        }
        healthBarContainer.visible = playerData.active;
        
        // Update health bar fill
        const healthPercent = Math.max(0, Math.min(100, playerData.health)) / 100;
        
        healthBarContainer.healthFill.clear();
        
        // Color based on health level
        let healthColor = 0x2ecc71; // Green
        if (healthPercent < 0.3) {
            healthColor = 0xe74c3c; // Red
        } else if (healthPercent < 0.6) {
            healthColor = 0xf39c12; // Orange
        }
        
        healthBarContainer.healthFill.beginFill(healthColor);
        healthBarContainer.healthFill.drawRoundedRect(-25, 0, 50 * healthPercent, 6, 2);
        healthBarContainer.healthFill.endFill();
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
            case 'CANNONBALL':
                sprite.scale.set(3.0);
                sprite.tint = 0x444444; // Dark gray for cannonball
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
    
    createStrategicLocation(locationData) {
        const sprite = new PIXI.Sprite(this.locationTexture);
        sprite.anchor.set(0.5);
        
        const isoPos = this.worldToIsometric(locationData.x, locationData.y);
        sprite.position.set(isoPos.x, isoPos.y);
        
        const nameLabel = new PIXI.Text(locationData.name, {
            fontSize: 16,
            fill: 0xffffff,
            stroke: 0x000000,
            strokeThickness: 3,
            fontWeight: 'bold'
        });
        nameLabel.anchor.set(0.5);
        nameLabel.position.set(0, 0);
        sprite.addChild(nameLabel);
        
        // Set strategic location z-index below players
        sprite.zIndex = 1;
        
        sprite.locationData = locationData;
        this.strategicLocations.set(locationData.id, sprite);
        this.gameContainer.addChild(sprite);
    }
    
    updateStrategicLocation(locationData) {
        const sprite = this.strategicLocations.get(locationData.id);
        if (!sprite) return;
        
        if (locationData.controllingPlayer) {
            sprite.tint = locationData.controllingPlayer === this.myPlayerId ? 0x2ecc71 : 0xe74c3c;
        } else {
            sprite.tint = 0x8e44ad;
        }
        
        sprite.locationData = locationData;
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
    }

    removeObstacle(obstacleId) {
        const sprite = this.obstacles.get(obstacleId);
        if (sprite) {
            this.gameContainer.removeChild(sprite);
            this.obstacles.delete(obstacleId);
        }
    }
    
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
            // Add fade-out animation before removal
            this.fadeOutEffect(effectContainer, () => {
                this.gameContainer.removeChild(effectContainer);
                this.fieldEffects.delete(effectId);
            });
        }
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
        
        // Add to ticker for animation updates
        this.app.ticker.add(() => this.animateEffect(container));
    }
    
    /**
     * Animate field effects
     */
    animateEffect(container) {
        if (!container.effectData || !container.parent) {
            // Effect has been removed, stop animating
            this.app.ticker.remove(() => this.animateEffect(container));
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
        const fadeOut = () => {
            container.alpha -= fadeSpeed;
            if (container.alpha <= 0) {
                this.app.ticker.remove(fadeOut);
                callback();
            }
        };
        this.app.ticker.add(fadeOut);
    }
    
    // Health bar methods removed - health now shown in consolidated HUD
    
    updateUI(gameState) {
        const myPlayer = gameState.players?.find(p => p.id === this.myPlayerId);
        if (!myPlayer) return;
        
        // Update consolidated HUD
        this.updateConsolidatedHUD(myPlayer);
        
        if (!myPlayer.active && myPlayer.respawnTime > 0) {
            this.showRespawnTimer(myPlayer.respawnTime);
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
        
        content.innerHTML = `
            <table style="width: 100%; color: white;">
                <thead>
                    <tr>
                        <th>Player</th>
                        <th>Kills</th>
                        <th>Deaths</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${sortedPlayers.map(player => `
                        <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                            <td><span style="color: ${this.getTeamColorCSS(player.team || 0)}">●</span> ${player.name || `Player ${player.id}`}</td>
                            <td>${player.kills || 0}</td>
                            <td>${player.deaths || 0}</td>
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
        
        // Sort teams by total kills
        const sortedTeams = Object.entries(teams).sort((a, b) => {
            const aKills = a[1].reduce((sum, p) => sum + (p.kills || 0), 0);
            const bKills = b[1].reduce((sum, p) => sum + (p.kills || 0), 0);
            return bKills - aKills;
        });
        
        let html = '<div style="color: white;">';
        
        sortedTeams.forEach(([teamNum, teamPlayers]) => {
            const teamKills = teamPlayers.reduce((sum, p) => sum + (p.kills || 0), 0);
            const teamDeaths = teamPlayers.reduce((sum, p) => sum + (p.deaths || 0), 0);
            const teamName = teamNum == 0 ? 'Free For All' : `Team ${teamNum}`;
            const teamColor = this.getTeamColorCSS(parseInt(teamNum));
            
            html += `
                <div style="margin-bottom: 15px; border: 1px solid ${teamColor}; border-radius: 5px; padding: 8px;">
                    <h4 style="margin: 0 0 8px 0; color: ${teamColor};">${teamName} (${teamKills}/${teamDeaths})</h4>
                    <table style="width: 100%; font-size: 12px;">
                        ${teamPlayers
                            .sort((a, b) => (b.kills || 0) - (a.kills || 0))
                            .map(player => `
                                <tr style="${player.id === this.myPlayerId ? 'background: rgba(46, 204, 113, 0.2);' : ''}">
                                    <td style="padding: 2px;">${player.name || `Player ${player.id}`}</td>
                                    <td style="padding: 2px; text-align: center;">${player.kills || 0}</td>
                                    <td style="padding: 2px; text-align: center;">${player.deaths || 0}</td>
                                    <td style="padding: 2px; text-align: center;">${player.active ? 'Alive' : 'Dead'}</td>
                                </tr>
                            `).join('')}
                    </table>
                </div>
            `;
        });
        
        html += '</div>';
        content.innerHTML = html;
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
        
        // Draw strategic locations
        this.strategicLocations.forEach(location => {
            const data = location.locationData;
            const x = (data.x + this.worldBounds.width / 2) * scale + offsetX;
            const y = ((-data.y) + this.worldBounds.height / 2) * scale + offsetY; // Invert Y for minimap
            
            const locationDot = new PIXI.Graphics();
            locationDot.beginFill(data.controllingPlayer === this.myPlayerId ? 0x2ecc71 : 
                                 data.controllingPlayer ? 0xe74c3c : 0x8e44ad);
            locationDot.drawCircle(x, y, 3);
            locationDot.endFill();
            
            this.minimapContent.addChild(locationDot);
        });
        
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
    
    showCaptureProgress(locationData) {
        const progressEl = document.getElementById('capture-progress');
        const fillEl = document.getElementById('capture-fill');
        const textEl = document.getElementById('capture-text');
        
        if (progressEl && fillEl && textEl) {
            progressEl.style.display = 'block';
            fillEl.style.width = `${locationData.captureProgress * 100}%`;
            textEl.textContent = `Capturing ${locationData.name}...`;
        }
    }
    
    hideCaptureProgress() {
        const progressEl = document.getElementById('capture-progress');
        if (progressEl) {
            progressEl.style.display = 'none';
        }
    }
    
    showDeathScreen(data) {
        const deathScreen = document.getElementById('death-screen');
        const deathInfo = document.getElementById('death-info');
        
        if (deathScreen && deathInfo) {
            deathScreen.style.display = 'flex';
            deathInfo.textContent = data.killerName ?
                `Eliminated by Player ${data.killerName}` :
                'You were eliminated';
        }
    }
    
    showRespawnTimer(timeRemaining) {
        const deathScreen = document.getElementById('death-screen');
        const countdown = document.getElementById('respawn-countdown');
        
        if (deathScreen && countdown) {
            deathScreen.style.display = 'flex';
            countdown.textContent = Math.ceil(timeRemaining);
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
        let playerName, weaponConfig;
        
        if (playerConfig && playerConfig.playerName && playerConfig.weaponConfig) {
            playerName = playerConfig.playerName;
            weaponConfig = playerConfig.weaponConfig;
        } else {
            // Legacy fallback for old URL format
            playerName = params.get('playerName') || `Player${Math.floor(Math.random() * 1000)}`;
            const primaryWeaponType = params.get('primaryWeapon') || 'assault';
            const secondaryWeaponType = params.get('secondaryWeapon') || 'pistol';

            const weaponPresets = {
                assault: { type: 'Assault Rifle', damage: 25, fireRate: 8, range: 300, accuracy: 0.9, magazineSize: 30, reloadTime: 2.0, projectileSpeed: 800 },
                sniper: { type: 'Sniper Rifle', damage: 80, fireRate: 1.5, range: 600, accuracy: 0.99, magazineSize: 5, reloadTime: 3.0, projectileSpeed: 1200 },
                shotgun: { type: 'Shotgun', damage: 60, fireRate: 2, range: 150, accuracy: 0.7, magazineSize: 8, reloadTime: 2.5, projectileSpeed: 400 },
                smg: { type: 'SMG', damage: 18, fireRate: 12, range: 200, accuracy: 0.8, magazineSize: 40, reloadTime: 1.5, projectileSpeed: 600 },
                pistol: { type: 'Pistol', damage: 35, fireRate: 4, range: 200, accuracy: 0.95, magazineSize: 12, reloadTime: 1.5, projectileSpeed: 600 },
                magnum: { type: 'Magnum', damage: 65, fireRate: 2, range: 250, accuracy: 0.98, magazineSize: 6, reloadTime: 2.0, projectileSpeed: 800 },
                'auto-pistol': { type: 'Auto Pistol', damage: 22, fireRate: 8, range: 180, accuracy: 0.85, magazineSize: 20, reloadTime: 1.8, projectileSpeed: 650 }
            };
            
            weaponConfig = weaponPresets[primaryWeaponType];
        }

        const message = {
            type: 'configChange',
            playerName: playerName,
            weaponConfig: weaponConfig
        };

        this.websocket.send(JSON.stringify(message));
    }
    
    updateLoadingProgress(percent, status) {
        const progressBar = document.getElementById('loading-progress');
        const statusText = document.getElementById('loading-status');
        
        if (progressBar) progressBar.style.width = `${percent}%`;
        if (statusText) statusText.textContent = status;
    }
    
    hideLoadingScreen() {
        setTimeout(() => {
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
            
            // Add team label
            const teamLabel = new PIXI.Text(`Team ${teamNum} Base`, {
                fontSize: 18,
                fill: teamColor,
                stroke: 0x000000,
                strokeThickness: 2,
                fontWeight: 'bold'
            });
            teamLabel.anchor.set(0.5);
            teamLabel.position.set(areaData.centerX, -areaData.centerY); // Invert Y for PIXI
            
            graphics.addChild(teamLabel);
            graphics.zIndex = -1; // Behind everything else
            
            this.backgroundContainer.addChild(graphics);
        });
    }
    
    /**
     * Create procedural terrain background and features.
     */
    createProceduralTerrain() {
        if (!this.terrainData || !this.terrainData.metadata) return;
        
        const metadata = this.terrainData.metadata;
        const features = this.terrainData.features || [];
        const compoundObstacles = this.terrainData.compoundObstacles || [];
        
        // Update background color based on terrain type
        this.updateBackgroundForTerrain(metadata);
        
        // Create terrain features
        features.forEach(feature => {
            this.createTerrainFeature(feature);
        });
        
        // Create compound obstacles (complex structures)
        compoundObstacles.forEach(compound => {
            this.createCompoundObstacle(compound);
        });
        
        // Add atmospheric effects
        this.createAtmosphericEffects(metadata);
        
        // Terrain generation complete
    }
    
    /**
     * Update background appearance based on terrain type.
     */
    updateBackgroundForTerrain(metadata) {
        // Parse color strings to hex values
        const primaryColor = parseInt(metadata.primaryColor.substring(1), 16);
        const secondaryColor = parseInt(metadata.secondaryColor.substring(1), 16);
        
        // Update app background
        this.app.renderer.backgroundColor = secondaryColor;
        
        // Create terrain-specific background pattern
        this.createTerrainBackground(primaryColor, secondaryColor, metadata.terrainType);
    }
    
    /**
     * Create terrain-specific background patterns.
     */
    createTerrainBackground(primaryColor, secondaryColor, terrainType) {
        const graphics = new PIXI.Graphics();
        
        switch (terrainType) {
            case 'FOREST':
                this.createForestBackground(graphics, primaryColor, secondaryColor);
                break;
            case 'DESERT':
                this.createDesertBackground(graphics, primaryColor, secondaryColor);
                break;
            case 'URBAN':
                this.createUrbanBackground(graphics, primaryColor, secondaryColor);
                break;
            case 'TUNDRA':
                this.createTundraBackground(graphics, primaryColor, secondaryColor);
                break;
            case 'VOLCANIC':
                this.createVolcanicBackground(graphics, primaryColor, secondaryColor);
                break;
            case 'GRASSLAND':
                this.createGrasslandBackground(graphics, primaryColor, secondaryColor);
                break;
            default:
                this.createGenericBackground(graphics, primaryColor, secondaryColor);
                break;
        }
        
        graphics.zIndex = -10; // Far background
        this.backgroundContainer.addChild(graphics);
    }
    
    createForestBackground(graphics, primary, secondary) {
        // Create scattered patches of darker forest areas
        for (let i = 0; i < 20; i++) {
            const x = (Math.random() - 0.5) * this.worldBounds.width;
            const y = (Math.random() - 0.5) * this.worldBounds.height;
            const size = 50 + Math.random() * 100;
            
            graphics.beginFill(primary, 0.3);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
    }
    
    createDesertBackground(graphics, primary, secondary) {
        // Create sand dune patterns
        for (let i = 0; i < 15; i++) {
            const x = (Math.random() - 0.5) * this.worldBounds.width;
            const y = (Math.random() - 0.5) * this.worldBounds.height;
            const width = 80 + Math.random() * 120;
            const height = 30 + Math.random() * 40;
            
            graphics.beginFill(primary, 0.2);
            graphics.drawEllipse(x, y, width, height);
            graphics.endFill();
        }
    }
    
    createUrbanBackground(graphics, primary, secondary) {
        // Create grid pattern for urban areas
        graphics.lineStyle(1, primary, 0.2);
        
        for (let x = -this.worldBounds.width/2; x <= this.worldBounds.width/2; x += 150) {
            graphics.moveTo(x, -this.worldBounds.height/2);
            graphics.lineTo(x, this.worldBounds.height/2);
        }
        
        for (let y = -this.worldBounds.height/2; y <= this.worldBounds.height/2; y += 150) {
            graphics.moveTo(-this.worldBounds.width/2, y);
            graphics.lineTo(this.worldBounds.width/2, y);
        }
    }
    
    createTundraBackground(graphics, primary, secondary) {
        // Create ice formations
        for (let i = 0; i < 12; i++) {
            const x = (Math.random() - 0.5) * this.worldBounds.width;
            const y = (Math.random() - 0.5) * this.worldBounds.height;
            const size = 60 + Math.random() * 80;
            
            graphics.beginFill(0xffffff, 0.3);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
    }
    
    createVolcanicBackground(graphics, primary, secondary) {
        // Create lava flow patterns
        graphics.lineStyle(3, 0xff4444, 0.4);
        
        for (let i = 0; i < 8; i++) {
            const startX = (Math.random() - 0.5) * this.worldBounds.width;
            const startY = (Math.random() - 0.5) * this.worldBounds.height;
            
            graphics.moveTo(startX, startY);
            
            let x = startX;
            let y = startY;
            
            for (let j = 0; j < 10; j++) {
                x += (Math.random() - 0.5) * 100;
                y += (Math.random() - 0.5) * 100;
                graphics.lineTo(x, y);
            }
        }
    }
    
    createGrasslandBackground(graphics, primary, secondary) {
        // Create subtle grass patterns
        for (let i = 0; i < 25; i++) {
            const x = (Math.random() - 0.5) * this.worldBounds.width;
            const y = (Math.random() - 0.5) * this.worldBounds.height;
            const size = 20 + Math.random() * 30;
            
            graphics.beginFill(primary, 0.2);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
    }
    
    createGenericBackground(graphics, primary, secondary) {
        // Simple scattered elements
        for (let i = 0; i < 15; i++) {
            const x = (Math.random() - 0.5) * this.worldBounds.width;
            const y = (Math.random() - 0.5) * this.worldBounds.height;
            const size = 30 + Math.random() * 50;
            
            graphics.beginFill(primary, 0.2);
            graphics.drawCircle(x, y, size);
            graphics.endFill();
        }
    }
    
    /**
     * Create individual terrain features.
     */
    createTerrainFeature(feature) {
        const graphics = new PIXI.Graphics();
        const color = parseInt(feature.color.substring(1), 16);
        const opacity = this.getFeatureOpacity(feature.type);
        
        graphics.beginFill(color, opacity);
        
        // Create different shapes based on feature type
        switch (feature.type.toLowerCase()) {
            case 'treecluster':
                this.drawTreeCluster(graphics, feature);
                break;
            case 'building':
                this.drawBuilding(graphics, feature);
                break;
            case 'rockmesa':
            case 'lavarock':
                this.drawRockFormation(graphics, feature);
                break;
            case 'sanddune':
                this.drawSandDune(graphics, feature);
                break;
            default:
                this.drawGenericFeature(graphics, feature);
                break;
        }
        
        graphics.endFill();
        graphics.position.set(feature.x, -feature.y); // Invert Y for PIXI
        graphics.zIndex = this.getFeatureZIndex(feature.type);
        
        this.backgroundContainer.addChild(graphics);
    }
    
    drawTreeCluster(graphics, feature) {
        // Draw multiple overlapping circles for tree cluster
        for (let i = 0; i < 5; i++) {
            const offsetX = (Math.random() - 0.5) * feature.size * 0.6;
            const offsetY = (Math.random() - 0.5) * feature.size * 0.6;
            const treeSize = feature.size * (0.3 + Math.random() * 0.4);
            graphics.drawCircle(offsetX, offsetY, treeSize);
        }
    }
    
    drawBuilding(graphics, feature) {
        // Draw rectangular building
        const width = feature.size * 0.8;
        const height = feature.size * 1.2;
        graphics.drawRect(-width/2, -height/2, width, height);
    }
    
    drawRockFormation(graphics, feature) {
        // Draw irregular rock shape
        const points = [];
        const numPoints = 6 + Math.floor(Math.random() * 4);
        
        for (let i = 0; i < numPoints; i++) {
            const angle = (i / numPoints) * Math.PI * 2;
            const radius = feature.size * (0.7 + Math.random() * 0.3);
            points.push(Math.cos(angle) * radius);
            points.push(Math.sin(angle) * radius);
        }
        
        graphics.drawPolygon(points);
    }
    
    drawSandDune(graphics, feature) {
        // Draw elliptical sand dune
        graphics.drawEllipse(0, 0, feature.size, feature.size * 0.6);
    }
    
    drawGenericFeature(graphics, feature) {
        // Default circular feature
        graphics.drawCircle(0, 0, feature.size);
    }
    
    getFeatureOpacity(featureType) {
        switch (featureType.toLowerCase()) {
            case 'clearing':
            case 'flowerpatch':
                return 0.3;
            case 'thickbrush':
            case 'tallgrass':
                return 0.6;
            default:
                return 0.8;
        }
    }
    
    getFeatureZIndex(featureType) {
        switch (featureType.toLowerCase()) {
            case 'building':
                return -3;
            case 'treecluster':
            case 'rockmesa':
                return -5;
            case 'thickbrush':
            case 'tallgrass':
                return -7;
            default:
                return -8;
        }
    }
    
    /**
     * Create a compound obstacle (complex multi-body structure).
     */
    createCompoundObstacle(compound) {
        const obstacleContainer = new PIXI.Container();
        obstacleContainer.x = compound.x;
        obstacleContainer.y = compound.y;
        obstacleContainer.zIndex = 1;
        
        // Get color scheme based on type and biome
        const colorScheme = this.getCompoundObstacleColors(compound.type, compound.biome);
        
        // Render each body in the compound obstacle
        compound.bodies.forEach((body, index) => {
            const bodyGraphics = new PIXI.Graphics();
            
            // Choose color variation for this body
            const colorIndex = index % colorScheme.length;
            const color = colorScheme[colorIndex];
            
            bodyGraphics.beginFill(color, 0.8);
            bodyGraphics.lineStyle(2, this.darkenColor(color), 1);
            
            if (body.shape === 'circle') {
                bodyGraphics.drawCircle(body.x - compound.x, body.y - compound.y, body.width);
            } else if (body.shape === 'rectangle') {
                const x = body.x - compound.x - body.width / 2;
                const y = body.y - compound.y - body.height / 2;
                bodyGraphics.drawRect(x, y, body.width, body.height);
            }
            
            bodyGraphics.endFill();
            obstacleContainer.addChild(bodyGraphics);
        });
        
        // Add a subtle shadow/depth effect
        const shadow = new PIXI.Graphics();
        shadow.beginFill(0x000000, 0.2);
        shadow.drawRect(-compound.width/2 + 3, -compound.height/2 + 3, compound.width, compound.height);
        shadow.endFill();
        shadow.zIndex = -1;
        obstacleContainer.addChild(shadow);
        
        // Add type-specific details
        this.addCompoundObstacleDetails(obstacleContainer, compound);
        
        this.gameContainer.addChild(obstacleContainer);
    }
    
    /**
     * Get color scheme for compound obstacles based on type and biome.
     */
    getCompoundObstacleColors(type, biome) {
        const baseColors = {
            'building_complex': [0x8B4513, 0xA0522D, 0x8B7355, 0x696969],
            'fortification': [0x708090, 0x778899, 0x2F4F4F, 0x696969],
            'vehicle_wreck': [0x4A4A4A, 0x800000, 0x8B4513, 0x2F4F4F],
            'rock_formation': [0x696969, 0x708090, 0x2F4F4F, 0x8B7D6B],
            'industrial_complex': [0x4A4A4A, 0x8B4513, 0x800000, 0x2F4F4F],
            'ruins': [0x8B7D6B, 0x696969, 0xA0522D, 0x2F4F4F]
        };
        
        // Modify base colors based on biome
        let colors = baseColors[type] || baseColors['rock_formation'];
        
        switch (biome) {
            case 'DESERT':
                colors = colors.map(c => this.blendColors(c, 0xD2B48C, 0.3));
                break;
            case 'FOREST':
                colors = colors.map(c => this.blendColors(c, 0x228B22, 0.2));
                break;
            case 'TUNDRA':
                colors = colors.map(c => this.blendColors(c, 0xF0F8FF, 0.3));
                break;
            case 'VOLCANIC':
                colors = colors.map(c => this.blendColors(c, 0x8B0000, 0.2));
                break;
            case 'URBAN':
                colors = colors.map(c => this.blendColors(c, 0x696969, 0.2));
                break;
        }
        
        return colors;
    }
    
    /**
     * Add type-specific visual details to compound obstacles.
     */
    addCompoundObstacleDetails(container, compound) {
        const detailGraphics = new PIXI.Graphics();
        
        switch (compound.type) {
            case 'building_complex':
                // Add windows
                detailGraphics.beginFill(0x87CEEB, 0.6);
                for (let i = 0; i < 3; i++) {
                    for (let j = 0; j < 2; j++) {
                        detailGraphics.drawRect(-40 + i * 25, -20 + j * 20, 8, 12);
                    }
                }
                detailGraphics.endFill();
                break;
                
            case 'fortification':
                // Add battlements
                detailGraphics.lineStyle(2, 0x696969, 1);
                detailGraphics.moveTo(-60, -60);
                for (let i = 0; i < 6; i++) {
                    detailGraphics.lineTo(-60 + i * 20, i % 2 === 0 ? -60 : -55);
                }
                break;
                
            case 'vehicle_wreck':
                // Add scorch marks
                detailGraphics.beginFill(0x2F2F2F, 0.5);
                detailGraphics.drawCircle(10, 5, 15);
                detailGraphics.drawCircle(-15, -10, 12);
                detailGraphics.endFill();
                break;
                
            case 'industrial_complex':
                // Add pipes and vents
                detailGraphics.lineStyle(4, 0x4A4A4A, 1);
                detailGraphics.moveTo(-30, -25);
                detailGraphics.lineTo(-30, 25);
                detailGraphics.moveTo(30, -25);
                detailGraphics.lineTo(30, 25);
                break;
                
            case 'ruins':
                // Add vegetation growing through cracks
                detailGraphics.beginFill(0x228B22, 0.4);
                for (let i = 0; i < 5; i++) {
                    const x = (Math.random() - 0.5) * compound.width * 0.8;
                    const y = (Math.random() - 0.5) * compound.height * 0.8;
                    detailGraphics.drawCircle(x, y, 3 + Math.random() * 5);
                }
                detailGraphics.endFill();
                break;
        }
        
        container.addChild(detailGraphics);
    }
    
    /**
     * Blend two colors together.
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
     * Darken a color for outlines.
     */
    darkenColor(color) {
        const r = Math.max(0, ((color >> 16) & 0xFF) - 40);
        const g = Math.max(0, ((color >> 8) & 0xFF) - 40);
        const b = Math.max(0, (color & 0xFF) - 40);
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Create atmospheric effects based on terrain.
     */
    createAtmosphericEffects(metadata) {
        const particles = metadata.particles || {};
        
        // Atmospheric effects data available for future particle systems
        // particles.type, metadata.fogDensity, metadata.temperature
    }

    handleResize() {
        this.app.renderer.resize(window.innerWidth, window.innerHeight);
    }
}

/**
 * Input Manager for handling keyboard, mouse, and gamepad input
 */
class InputManager {
    constructor() {
        this.keys = {
            w: false, a: false, s: false, d: false,
            shift: false, space: false
        };
        this.movement = {
            moveX: 0.0, // -1.0 = left, +1.0 = right
            moveY: 0.0  // -1.0 = down, +1.0 = up
        };
        this.mouse = {
            x: 0, y: 0, worldX: 0, worldY: 0,
            left: false, right: false
        };
        
        // Gamepad support
        this.gamepad = {
            connected: false,
            index: -1,
            leftStick: { x: 0, y: 0 },
            rightStick: { x: 0, y: 0 },
            buttons: {
                a: false,        // Fire/Action (Button 0)
                b: false,        // Secondary action (Button 1)
                x: false,        // Reload (Button 2)
                y: false,        // Weapon switch (Button 3)
                lb: false,       // Left bumper (Button 4)
                rb: false,       // Right bumper (Button 5)
                lt: false,       // Left trigger (Button 6)
                rt: false,       // Right trigger (Button 7)
                back: false,     // Back/Select (Button 8)
                start: false,    // Start/Menu (Button 9)
                ls: false,       // Left stick click (Button 10)
                rs: false,       // Right stick click (Button 11)
                up: false,       // D-pad up (Button 12)
                down: false,     // D-pad down (Button 13)
                left: false,     // D-pad left (Button 14)
                right: false     // D-pad right (Button 15)
            },
            deadzone: 0.15,      // Deadzone for analog sticks
            triggerThreshold: 0.1 // Threshold for trigger buttons
        };
        
        this.onInputChange = null;
        this.inputSource = 'keyboard'; // 'keyboard' or 'gamepad'
        
        this.setupEventListeners();
        this.inputInterval = 20; // 50 FPS (20ms intervals)
        
        // Start gamepad polling
        this.pollGamepads();
    }
    
    setupEventListeners() {
        document.addEventListener('keydown', (e) => this.handleKeyDown(e));
        document.addEventListener('keyup', (e) => this.handleKeyUp(e));
        document.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        document.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        document.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        document.addEventListener('contextmenu', (e) => e.preventDefault());
        
        // Gamepad connection events
        window.addEventListener('gamepadconnected', (e) => this.handleGamepadConnected(e));
        window.addEventListener('gamepaddisconnected', (e) => this.handleGamepadDisconnected(e));
        
        // Send input at fixed 20ms intervals (50 FPS)
        setInterval(() => this.sendInput(), this.inputInterval);
    }
    
    updateMovementAxes() {
        // Convert WASD keys to axis values for gamepad compatibility
        this.movement.moveX = 0;
        this.movement.moveY = 0;
        
        if (this.keys.a) this.movement.moveX -= 1.0; // Left
        if (this.keys.d) this.movement.moveX += 1.0; // Right
        if (this.keys.s) this.movement.moveY -= 1.0; // Down
        if (this.keys.w) this.movement.moveY += 1.0; // Up
    }
    
    handleKeyDown(e) {
        // Prevent default for game keys to avoid browser actions
        if (['w', 'a', 's', 'd', ' ', '1', '2', 'r'].includes(e.key.toLowerCase())) {
            e.preventDefault();
        }
        
        switch(e.key.toLowerCase()) {
            case 'w': this.keys.w = true; this.updateMovementAxes(); break;
            case 'a': this.keys.a = true; this.updateMovementAxes(); break;
            case 's': this.keys.s = true; this.updateMovementAxes(); break;
            case 'd': this.keys.d = true; this.updateMovementAxes(); break;
            case 'shift': this.keys.shift = true; break;
            case ' ': this.keys.space = true; break;
            case '1': this.sendWeaponSwitch(0); break;
            case '2': this.sendWeaponSwitch(1); break;
            case 'r': this.sendReload(); break;
        }
    }
    
    handleKeyUp(e) {
        switch(e.key.toLowerCase()) {
            case 'w': this.keys.w = false; this.updateMovementAxes(); break;
            case 'a': this.keys.a = false; this.updateMovementAxes(); break;
            case 's': this.keys.s = false; this.updateMovementAxes(); break;
            case 'd': this.keys.d = false; this.updateMovementAxes(); break;
            case 'shift': this.keys.shift = false; break;
            case ' ': this.keys.space = false; break;
        }
    }
    
    handleMouseMove(e) {
        this.mouse.x = e.clientX;
        this.mouse.y = e.clientY;

        const gameEngine = window.gameEngine;
        if (gameEngine && gameEngine.gameContainer) {
            // Use PIXI's built-in transformation to get coordinates relative to the game world
            const screenPos = new PIXI.Point(this.mouse.x, this.mouse.y);
            const worldPos = gameEngine.gameContainer.toLocal(screenPos);

            // The world uses a Y-up coordinate system for physics, but PIXI uses Y-down.
            // We need to send the physics-correct coordinates to the server.
            this.mouse.worldX = worldPos.x;
            this.mouse.worldY = -worldPos.y; // Invert Y for the physics engine
        }
    }
    
    handleMouseDown(e) {
        // Only handle mouse events on the game canvas, not UI elements
        const isGameCanvas = e.target.tagName === 'CANVAS' || e.target.closest('#pixi-container');
        const isUIElement = e.target.closest('button, input, select, textarea, .ui-element');
        
        if (!isGameCanvas || isUIElement) {
            return;
        }

        if (e.button === 0) {
            this.mouse.left = true;
        }
        if (e.button === 2) this.mouse.right = true;
        e.preventDefault();
    }
    
    handleMouseUp(e) {
        if (e.button === 0) {
            this.mouse.left = false;
        }
        if (e.button === 2) this.mouse.right = false;
    }
    
    /**
     * Handle gamepad connection
     */
    handleGamepadConnected(e) {
        console.log('Gamepad connected:', e.gamepad.id);
        this.gamepad.connected = true;
        this.gamepad.index = e.gamepad.index;
        this.inputSource = 'gamepad';
        
        // Initialize mouse position to center of screen for gamepad aiming
        this.mouse.x = window.innerWidth / 2;
        this.mouse.y = window.innerHeight / 2;
        
        // Show gamepad connected notification
        this.showInputSourceNotification('Gamepad Connected: ' + e.gamepad.id);
        
        // Update HUD immediately if game engine is available
        if (this.gameEngine && this.gameEngine.hudInputText) {
            this.gameEngine.hudInputText.text = 'Gamepad';
            this.gameEngine.hudInputText.style.fill = 0x44ff44;
        }
    }
    
    /**
     * Handle gamepad disconnection
     */
    handleGamepadDisconnected(e) {
        console.log('Gamepad disconnected:', e.gamepad.id);
        this.gamepad.connected = false;
        this.gamepad.index = -1;
        this.inputSource = 'keyboard';
        
        // Reset gamepad state
        this.resetGamepadState();
        
        // Show gamepad disconnected notification
        this.showInputSourceNotification('Gamepad Disconnected - Switched to Keyboard');
        
        // Update HUD immediately if game engine is available
        if (this.gameEngine && this.gameEngine.hudInputText) {
            this.gameEngine.hudInputText.text = 'Keyboard';
            this.gameEngine.hudInputText.style.fill = 0xffffff;
        }
    }
    
    /**
     * Poll gamepad state (required for consistent gamepad input)
     */
    pollGamepads() {
        if (!this.gamepad.connected) {
            // Check for newly connected gamepads
            const gamepads = navigator.getGamepads();
            for (let i = 0; i < gamepads.length; i++) {
                if (gamepads[i]) {
                    this.handleGamepadConnected({ gamepad: gamepads[i] });
                    break;
                }
            }
        } else {
            // Update gamepad state
            this.updateGamepadState();
        }
        
        // Continue polling
        requestAnimationFrame(() => this.pollGamepads());
    }
    
    /**
     * Update gamepad input state
     */
    updateGamepadState() {
        const gamepads = navigator.getGamepads();
        const gamepad = gamepads[this.gamepad.index];
        
        if (!gamepad) {
            this.handleGamepadDisconnected({ gamepad: { id: 'Unknown' } });
            return;
        }
        
        // Update analog sticks with deadzone
        this.gamepad.leftStick.x = this.applyDeadzone(gamepad.axes[0] || 0);
        this.gamepad.leftStick.y = this.applyDeadzone(-(gamepad.axes[1] || 0)); // Invert Y for game coordinates
        this.gamepad.rightStick.x = this.applyDeadzone(gamepad.axes[2] || 0);
        this.gamepad.rightStick.y = this.applyDeadzone(-(gamepad.axes[3] || 0)); // Invert Y for game coordinates
        
        // Update button states
        const buttons = this.gamepad.buttons;
        const gamepadButtons = gamepad.buttons;
        
        // Face buttons (Xbox layout)
        buttons.a = this.isButtonPressed(gamepadButtons[0]);      // A - Fire
        buttons.b = this.isButtonPressed(gamepadButtons[1]);      // B - Secondary
        buttons.x = this.isButtonPressed(gamepadButtons[2]);      // X - Reload
        buttons.y = this.isButtonPressed(gamepadButtons[3]);      // Y - Weapon switch
        
        // Shoulder buttons
        buttons.lb = this.isButtonPressed(gamepadButtons[4]);     // Left bumper
        buttons.rb = this.isButtonPressed(gamepadButtons[5]);     // Right bumper
        buttons.lt = this.isTriggerPressed(gamepadButtons[6]);    // Left trigger
        buttons.rt = this.isTriggerPressed(gamepadButtons[7]);    // Right trigger
        
        // System buttons
        buttons.back = this.isButtonPressed(gamepadButtons[8]);   // Back/Select
        buttons.start = this.isButtonPressed(gamepadButtons[9]);  // Start/Menu
        
        // Stick clicks
        buttons.ls = this.isButtonPressed(gamepadButtons[10]);    // Left stick click
        buttons.rs = this.isButtonPressed(gamepadButtons[11]);    // Right stick click
        
        // D-pad
        buttons.up = this.isButtonPressed(gamepadButtons[12]);    // D-pad up
        buttons.down = this.isButtonPressed(gamepadButtons[13]);  // D-pad down
        buttons.left = this.isButtonPressed(gamepadButtons[14]);  // D-pad left
        buttons.right = this.isButtonPressed(gamepadButtons[15]); // D-pad right
        
        // Update movement from gamepad
        this.updateGamepadMovement();
        
        // Handle gamepad-specific actions
        this.handleGamepadActions();
    }
    
    /**
     * Apply deadzone to analog stick values
     */
    applyDeadzone(value) {
        if (Math.abs(value) < this.gamepad.deadzone) {
            return 0;
        }
        // Scale the value to account for deadzone
        const sign = Math.sign(value);
        const scaledValue = (Math.abs(value) - this.gamepad.deadzone) / (1 - this.gamepad.deadzone);
        return sign * scaledValue;
    }
    
    /**
     * Check if a button is pressed (handles both digital and analog buttons)
     */
    isButtonPressed(button) {
        if (typeof button === 'object') {
            return button.pressed || button.value > 0.5;
        }
        return button > 0.5;
    }
    
    /**
     * Check if a trigger is pressed (with threshold)
     */
    isTriggerPressed(button) {
        if (typeof button === 'object') {
            return button.value > this.gamepad.triggerThreshold;
        }
        return button > this.gamepad.triggerThreshold;
    }
    
    /**
     * Update movement from gamepad input
     */
    updateGamepadMovement() {
        if (!this.gamepad.connected) return;
        
        // Use left stick for movement
        this.movement.moveX = this.gamepad.leftStick.x;
        this.movement.moveY = this.gamepad.leftStick.y;
        
        // Alternative: D-pad movement (digital)
        if (Math.abs(this.movement.moveX) < 0.1 && Math.abs(this.movement.moveY) < 0.1) {
            this.movement.moveX = 0;
            this.movement.moveY = 0;
            
            if (this.gamepad.buttons.left) this.movement.moveX -= 1.0;
            if (this.gamepad.buttons.right) this.movement.moveX += 1.0;
            if (this.gamepad.buttons.down) this.movement.moveY -= 1.0;
            if (this.gamepad.buttons.up) this.movement.moveY += 1.0;
        }
    }
    
    /**
     * Handle gamepad-specific actions
     */
    handleGamepadActions() {
        if (!this.gamepad.connected) return;
        
        // Handle weapon switching (Y button)
        if (this.gamepad.buttons.y && !this.gamepad.buttons.y_prev) {
            this.sendWeaponSwitch(1); // Switch to secondary weapon
        }
        
        // Handle reload (X button)
        if (this.gamepad.buttons.x && !this.gamepad.buttons.x_prev) {
            this.sendReload();
        }
        
        // Store previous button states for edge detection
        this.gamepad.buttons.y_prev = this.gamepad.buttons.y;
        this.gamepad.buttons.x_prev = this.gamepad.buttons.x;
    }
    
    /**
     * Update aiming from gamepad right stick
     */
    updateGamepadAiming() {
        if (!this.gamepad.connected || !window.gameEngine) return;
        
        const gameEngine = window.gameEngine;
        
        // For gamepad, use right stick to directly control aiming direction
        if (Math.abs(this.gamepad.rightStick.x) > 0.1 || Math.abs(this.gamepad.rightStick.y) > 0.1) {
            // Get player position in world coordinates
            const myPlayer = gameEngine.getMyPlayer();
            if (myPlayer) {
                // Calculate aim direction relative to player position
                const aimRange = 200; // Distance to aim point from player
                const aimX = myPlayer.x + (this.gamepad.rightStick.x * aimRange);
                const aimY = myPlayer.y + (this.gamepad.rightStick.y * aimRange);
                
                // Convert to screen coordinates for mouse position
                const worldPos = new PIXI.Point(aimX, aimY);
                const screenPos = gameEngine.gameContainer.toGlobal(worldPos);
                
                // Update mouse position for aiming
                this.mouse.x = screenPos.x;
                this.mouse.y = screenPos.y;
                this.mouse.worldX = aimX;
                this.mouse.worldY = aimY;
            }
        } else {
            // When right stick is neutral, aim forward relative to player
            const myPlayer = gameEngine.getMyPlayer();
            if (myPlayer) {
                // Default aim direction (forward/up in world coordinates)
                const aimRange = 100;
                const aimX = myPlayer.x;
                const aimY = myPlayer.y + aimRange; // Aim upward by default
                
                const worldPos = new PIXI.Point(aimX, aimY);
                const screenPos = gameEngine.gameContainer.toGlobal(worldPos);
                
                this.mouse.x = screenPos.x;
                this.mouse.y = screenPos.y;
                this.mouse.worldX = aimX;
                this.mouse.worldY = aimY;
            }
        }
    }
    
    /**
     * Reset gamepad state
     */
    resetGamepadState() {
        this.gamepad.leftStick.x = 0;
        this.gamepad.leftStick.y = 0;
        this.gamepad.rightStick.x = 0;
        this.gamepad.rightStick.y = 0;
        
        Object.keys(this.gamepad.buttons).forEach(key => {
            if (!key.endsWith('_prev')) {
                this.gamepad.buttons[key] = false;
            }
        });
    }
    
    /**
     * Show input source notification
     */
    showInputSourceNotification(message) {
        // Create or update notification element
        let notification = document.getElementById('input-notification');
        if (!notification) {
            notification = document.createElement('div');
            notification.id = 'input-notification';
            notification.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                background: rgba(0, 0, 0, 0.8);
                color: white;
                padding: 10px 15px;
                border-radius: 5px;
                font-family: Arial, sans-serif;
                font-size: 14px;
                z-index: 1000;
                transition: opacity 0.3s ease;
            `;
            document.body.appendChild(notification);
        }
        
        notification.textContent = message;
        notification.style.opacity = '1';
        
        // Auto-hide after 3 seconds
        setTimeout(() => {
            if (notification) {
                notification.style.opacity = '0';
                setTimeout(() => {
                    if (notification && notification.parentNode) {
                        notification.parentNode.removeChild(notification);
                    }
                }, 300);
            }
        }, 3000);
    }
    
    sendInput() {
        if (this.onInputChange) {
            // Update gamepad aiming if connected
            if (this.gamepad.connected) {
                this.updateGamepadAiming();
            }
            
            // Determine input values based on active input source
            let moveX, moveY, fire, secondary, sprint, jump;
            
            if (this.gamepad.connected && this.inputSource === 'gamepad') {
                // Use gamepad input
                moveX = this.movement.moveX; // Already updated by updateGamepadMovement
                moveY = this.movement.moveY;
                fire = this.gamepad.buttons.rt || this.gamepad.buttons.a; // Right trigger or A button
                secondary = this.gamepad.buttons.lt || this.gamepad.buttons.b; // Left trigger or B button
                sprint = this.gamepad.buttons.ls || this.gamepad.buttons.rb; // Left stick click or right bumper
                jump = this.gamepad.buttons.lb; // Left bumper for jump/space
            } else {
                // Use keyboard/mouse input
                moveX = this.movement.moveX;
                moveY = this.movement.moveY;
                fire = this.mouse.left;
                secondary = this.mouse.right;
                sprint = this.keys.shift;
                jump = this.keys.space;
            }
            
            const input = {
                type: 'playerInput',
                moveX: moveX,
                moveY: moveY,
                shift: !!sprint,
                space: !!jump,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: !!fire,
                right: !!secondary,
                weaponSwitch: null,
                reload: null,
                inputSource: this.inputSource // Let server know input source
            };
            
            this.onInputChange(input);
        }
    }
    
    sendWeaponSwitch(weaponIndex) {
        if (this.onInputChange) {
            // Determine sprint/jump state based on input source
            let sprint, jump;
            if (this.gamepad.connected && this.inputSource === 'gamepad') {
                sprint = this.gamepad.buttons.ls || this.gamepad.buttons.rb;
                jump = this.gamepad.buttons.lb;
            } else {
                sprint = this.keys.shift;
                jump = this.keys.space;
            }
            
            const input = {
                type: 'playerInput',
                moveX: this.movement.moveX,
                moveY: this.movement.moveY,
                shift: !!sprint,
                space: !!jump,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: false, // Don't accidentally shoot when switching weapons
                right: false,
                weaponSwitch: weaponIndex,
                reload: null,
                inputSource: this.inputSource
            };
            
            this.onInputChange(input);
        }
    }
    
    sendReload() {
        if (this.onInputChange) {
            // Determine sprint/jump state based on input source
            let sprint, jump;
            if (this.gamepad.connected && this.inputSource === 'gamepad') {
                sprint = this.gamepad.buttons.ls || this.gamepad.buttons.rb;
                jump = this.gamepad.buttons.lb;
            } else {
                sprint = this.keys.shift;
                jump = this.keys.space;
            }
            
            const input = {
                type: 'playerInput',
                moveX: this.movement.moveX,
                moveY: this.movement.moveY,
                shift: !!sprint,
                space: !!jump,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: false, // Don't accidentally shoot when reloading
                right: false,
                weaponSwitch: null,
                reload: true,
                inputSource: this.inputSource
            };
            
            this.onInputChange(input);
        }
    }
}

/**
 * Full Steam - Isometric Battle Arena Game Engine
 * Built with PixiJS for 2.5D isometric rendering
 */

class GameEngine {
    constructor() {
        this.app = null;
        this.gameContainer = null;
        this.uiContainer = null;
        this.players = new Map();
        this.projectiles = new Map();
        this.strategicLocations = new Map();
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

        // Create crosshatch grid background
        this.createCrosshatchGrid();

        // Handle window resize
        window.addEventListener('resize', () => this.handleResize());
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
            this.sendPlayerInput(input);
        };
    }
    
    setupUI() {
        // Setup weapon customization
        this.setupWeaponCustomization();
        
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
    
    setupWeaponCustomization() {
        const weaponPresets = {
            assault: { damage: 25, fireRate: 8, range: 300, accuracy: 0.9, magazineSize: 30, reloadTime: 2.0, projectileSpeed: 800 },
            sniper: { damage: 80, fireRate: 1.5, range: 600, accuracy: 0.99, magazineSize: 5, reloadTime: 3.0, projectileSpeed: 1200 },
            shotgun: { damage: 60, fireRate: 2, range: 150, accuracy: 0.7, magazineSize: 8, reloadTime: 2.5, projectileSpeed: 400 },
            smg: { damage: 18, fireRate: 12, range: 200, accuracy: 0.8, magazineSize: 40, reloadTime: 1.5, projectileSpeed: 600 },
            pistol: { damage: 35, fireRate: 4, range: 200, accuracy: 0.95, magazineSize: 12, reloadTime: 1.5, projectileSpeed: 600 },
            magnum: { damage: 65, fireRate: 2, range: 250, accuracy: 0.98, magazineSize: 6, reloadTime: 2.0, projectileSpeed: 800 },
            'auto-pistol': { damage: 22, fireRate: 8, range: 180, accuracy: 0.85, magazineSize: 20, reloadTime: 1.8, projectileSpeed: 650 }
        };
        
        const updateWeaponStats = (weaponType, statsElementId) => {
            const stats = weaponPresets[weaponType];
            const statsElement = document.getElementById(statsElementId);
            if (stats && statsElement) {
                statsElement.innerHTML = `
                    <div class="stat-item"><span>Damage:</span><span>${stats.damage}</span></div>
                    <div class="stat-item"><span>Fire Rate:</span><span>${stats.fireRate}/s</span></div>
                    <div class="stat-item"><span>Range:</span><span>${stats.range}m</span></div>
                    <div class="stat-item"><span>Accuracy:</span><span>${Math.round(stats.accuracy * 100)}%</span></div>
                    <div class="stat-item"><span>Magazine:</span><span>${stats.magazineSize}</span></div>
                    <div class="stat-item"><span>Reload:</span><span>${stats.reloadTime}s</span></div>
                `;
            }
        };
        
        document.getElementById('primary-weapon-type')?.addEventListener('change', (e) => {
            updateWeaponStats(e.target.value, 'primary-stats');
        });
        
        document.getElementById('secondary-weapon-type')?.addEventListener('change', (e) => {
            updateWeaponStats(e.target.value, 'secondary-stats');
        });
        
        // Initialize with default values
        updateWeaponStats('assault', 'primary-stats');
        updateWeaponStats('pistol', 'secondary-stats');
        
        document.getElementById('apply-loadout')?.addEventListener('click', () => {
            this.sendWeaponConfig();
        });
        
        // Show weapon customization with C key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'c' || e.key === 'C') {
                const panel = document.getElementById('weapon-customization');
                if (panel) {
                    panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
                }
                }
            });
        }

    async loadAssets() {
        // Create clean 2D top-down assets
        
        // Player sprite - clean circular design
        const playerGraphics = new PIXI.Graphics();
        playerGraphics.beginFill(0x3498db);
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
                this.sendPlayerName();
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
        
        if (data.locations) {
            data.locations.forEach(location => {
                this.createStrategicLocation(location);
            });
        }
        
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
            data.locations.forEach(locationData => {
                this.updateStrategicLocation(locationData);
            });
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
        
        // Make sure players are visible with distinct colors
        sprite.tint = playerData.id === this.myPlayerId ? 0x00ff00 : 0xff0000; // Bright green vs bright red
        
        // Make sprite visible but normal size
        sprite.scale.set(1.0); // Normal size
        sprite.alpha = 1.0; // Ensure full opacity
        sprite.visible = true; // Explicitly set visible
        
        // Create health bar in separate container so it doesn't rotate
        const healthBar = this.createHealthBar();
        
        // Position health bar above player
        healthBar.position.set(isoPos.x, isoPos.y - 35);
        
        // Store reference to health bar on sprite for easy access
        sprite.healthBar = healthBar;
        
        // Add health bar to separate container
        this.nameContainer.addChild(healthBar);
        
        // Create name label in separate container so it doesn't rotate
        const nameLabel = new PIXI.Text(playerData.name || `Player ${playerData.id}`, {
            fontSize: 14,
            fill: 0xffffff,
            stroke: 0x000000,
            strokeThickness: 3
        });
        nameLabel.anchor.set(0.5);
        
        // Position name label above health bar
        nameLabel.position.set(isoPos.x, isoPos.y - 50);
        
        // Store reference to name label on sprite for easy access
        sprite.nameLabel = nameLabel;
        
        // Add name label to separate container
        this.nameContainer.addChild(nameLabel);
        
        // Set player z-index to ensure it's on top
        sprite.zIndex = 10;
        
        sprite.playerData = playerData;
        this.players.set(playerData.id, sprite);
        this.gameContainer.addChild(sprite);
        
        // Enable sorting for this container
        this.gameContainer.sortableChildren = true;
    }
    
    updatePlayer(playerData) {
        const sprite = this.players.get(playerData.id);
        if (!sprite) return;

        const isoPos = this.worldToIsometric(playerData.x, playerData.y);
        sprite.position.set(isoPos.x, isoPos.y);
        // The server calculates rotation in a Y-up world. PIXI operates in a Y-down world.
        // We must negate the rotation to make it display correctly.
        sprite.rotation = -(playerData.rotation || 0);
        sprite.visible = playerData.active;

        // Update name label position (doesn't rotate with player)
        if (sprite.nameLabel) {
            sprite.nameLabel.position.set(isoPos.x, isoPos.y - 50);
            sprite.nameLabel.visible = playerData.active;
        }

        // Update health bar position (doesn't rotate with player)
        if (sprite.healthBar) {
            sprite.healthBar.position.set(isoPos.x, isoPos.y - 35);
            sprite.healthBar.visible = playerData.active;
            this.updateHealthBar(sprite.healthBar, playerData.health);
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
            
            this.players.delete(playerId);
        }
    }
    
    createProjectile(projectileData) {
        const sprite = new PIXI.Sprite(this.projectileTexture);
        sprite.anchor.set(0.5);
        
        const isoPos = this.worldToIsometric(projectileData.x, projectileData.y);
        sprite.position.set(isoPos.x, isoPos.y);
        
        // Set projectile z-index above players
        sprite.zIndex = 15;
        
        sprite.projectileData = projectileData;
        this.projectiles.set(projectileData.id, sprite);
        this.gameContainer.addChild(sprite);
    }
    
    updateProjectile(projectileData) {
        const sprite = this.projectiles.get(projectileData.id);
        if (!sprite) return;
        
        const isoPos = this.worldToIsometric(projectileData.x, projectileData.y);
        sprite.position.set(isoPos.x, isoPos.y);
        sprite.projectileData = projectileData;
    }
    
    removeProjectile(projectileId) {
        const sprite = this.projectiles.get(projectileId);
        if (sprite) {
            this.gameContainer.removeChild(sprite);
            this.projectiles.delete(projectileId);
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
        
        if (locationData.capturingPlayer && locationData.captureProgress > 0) {
            this.showCaptureProgress(locationData);
        }
    }
    
    createHealthBar() {
        const container = new PIXI.Container();
        container.isHealthBar = true;
        
        const bg = new PIXI.Graphics();
        bg.beginFill(0x000000, 0.5);
        bg.drawRect(-20, -35, 40, 6);
        bg.endFill();
        
        const fill = new PIXI.Graphics();
        fill.beginFill(0x2ecc71);
        fill.drawRect(-20, -35, 40, 6);
        fill.endFill();
        
        container.addChild(bg);
        container.addChild(fill);
        container.healthFill = fill;
        
        return container;
    }
    
    updateHealthBar(healthBar, health) {
        const healthPercent = Math.max(0, Math.min(1, health / 100));
        const healthFill = healthBar.healthFill;
        
        healthFill.clear();
        
        let color = 0x2ecc71;
        if (healthPercent < 0.3) color = 0xe74c3c;
        else if (healthPercent < 0.6) color = 0xf39c12;
        
        healthFill.beginFill(color);
        healthFill.drawRect(-20, -35, 40 * healthPercent, 6);
        healthFill.endFill();
    }
    
    updateUI(gameState) {
        const myPlayer = gameState.players?.find(p => p.id === this.myPlayerId);
        if (!myPlayer) return;
        
        const healthFill = document.getElementById('health-fill');
        const healthText = document.getElementById('health-text');
        if (healthFill && healthText) {
            const healthPercent = Math.max(0, myPlayer.health);
            healthFill.style.width = `${healthPercent}%`;
            healthText.textContent = Math.round(healthPercent);
        }
        
        const weaponName = document.getElementById('weapon-name');
        const ammoCurrent = document.getElementById('ammo-current');
        const ammoMax = document.getElementById('ammo-max');
        const reloadIndicator = document.getElementById('reload-indicator');
        
        if (weaponName) {
            weaponName.textContent = myPlayer.weapon === 0 ? 'Primary' : 'Secondary';
        }
        
        if (ammoCurrent && ammoMax) {
            ammoCurrent.textContent = myPlayer.ammo || 0;
            ammoMax.textContent = 30;
        }
        
        if (reloadIndicator) {
            reloadIndicator.style.display = myPlayer.reloading ? 'block' : 'none';
        }
        
        if (!myPlayer.active && myPlayer.respawnTime > 0) {
            this.showRespawnTimer(myPlayer.respawnTime);
        } else {
            this.hideDeathScreen();
        }
        
        this.updateScoreboard(gameState.players);
    }
    
    updateScoreboard(players) {
        const content = document.getElementById('scoreboard-content');
        if (!content || !players) return;
        
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
                            <td>${player.name || `Player ${player.id}`}</td>
                            <td>${player.kills || 0}</td>
                            <td>${player.deaths || 0}</td>
                            <td>${player.active ? 'Alive' : 'Dead'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }
    
    updateMinimap() {
        const canvas = document.getElementById('minimap-canvas');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        ctx.strokeStyle = '#555';
        ctx.strokeRect(0, 0, canvas.width, canvas.height);
        
        const scaleX = canvas.width / this.worldBounds.width;
        const scaleY = canvas.height / this.worldBounds.height;
        
        this.strategicLocations.forEach(location => {
            const data = location.locationData;
            const x = (data.x + this.worldBounds.width / 2) * scaleX;
            const y = ((-data.y) + this.worldBounds.height / 2) * scaleY; // Invert Y for minimap
            
            ctx.fillStyle = data.controllingPlayer === this.myPlayerId ? '#2ecc71' : 
                           data.controllingPlayer ? '#e74c3c' : '#8e44ad';
            ctx.beginPath();
            ctx.arc(x, y, 8, 0, Math.PI * 2);
            ctx.fill();
        });
        
        this.players.forEach(player => {
            const data = player.playerData;
            if (!data.active) return;
            
            const x = (data.x + this.worldBounds.width / 2) * scaleX;
            const y = ((-data.y) + this.worldBounds.height / 2) * scaleY; // Invert Y for minimap
            
            ctx.fillStyle = data.id === this.myPlayerId ? '#2ecc71' : '#e74c3c';
            ctx.beginPath();
            ctx.arc(x, y, 3, 0, Math.PI * 2);
            ctx.fill();
        });
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
    
    showDeathScreen(data) {
        const deathScreen = document.getElementById('death-screen');
        const deathInfo = document.getElementById('death-info');
        
        if (deathScreen && deathInfo) {
            deathScreen.style.display = 'flex';
            deathInfo.textContent = data.killerId ? 
                `Eliminated by Player ${data.killerId}` : 
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
    
    sendPlayerName() {
        const name = prompt("Enter your player name:") || `Player${Math.floor(Math.random() * 1000)}`;
        
        const message = {
            type: 'configChange',
            playerName: name
        };
        
        this.websocket.send(JSON.stringify(message));
    }
    
    sendWeaponConfig() {
        const primaryType = document.getElementById('primary-weapon-type')?.value || 'assault';
        const secondaryType = document.getElementById('secondary-weapon-type')?.value || 'pistol';
        
        const weaponPresets = {
            assault: { type: 'Assault Rifle', damage: 25, fireRate: 8, range: 300, accuracy: 0.9, magazineSize: 30, reloadTime: 2.0, projectileSpeed: 800 },
            sniper: { type: 'Sniper Rifle', damage: 80, fireRate: 1.5, range: 600, accuracy: 0.99, magazineSize: 5, reloadTime: 3.0, projectileSpeed: 1200 },
            shotgun: { type: 'Shotgun', damage: 60, fireRate: 2, range: 150, accuracy: 0.7, magazineSize: 8, reloadTime: 2.5, projectileSpeed: 400 },
            smg: { type: 'SMG', damage: 18, fireRate: 12, range: 200, accuracy: 0.8, magazineSize: 40, reloadTime: 1.5, projectileSpeed: 600 },
            pistol: { type: 'Pistol', damage: 35, fireRate: 4, range: 200, accuracy: 0.95, magazineSize: 12, reloadTime: 1.5, projectileSpeed: 600 },
            magnum: { type: 'Magnum', damage: 65, fireRate: 2, range: 250, accuracy: 0.98, magazineSize: 6, reloadTime: 2.0, projectileSpeed: 800 },
            'auto-pistol': { type: 'Auto Pistol', damage: 22, fireRate: 8, range: 180, accuracy: 0.85, magazineSize: 20, reloadTime: 1.8, projectileSpeed: 650 }
        };
        
        this.websocket.send(JSON.stringify({
            type: 'configChange',
            primaryWeapon: weaponPresets[primaryType],
            secondaryWeapon: weaponPresets[secondaryType]
        }));
        
        document.getElementById('weapon-customization').style.display = 'none';
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

    handleResize() {
        this.app.renderer.resize(window.innerWidth, window.innerHeight);
    }
}

/**
 * Input Manager for handling keyboard and mouse input
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
        this.onInputChange = null;
        
        this.setupEventListeners();
        this.inputInterval = 20; // 50 FPS (20ms intervals)
    }
    
    setupEventListeners() {
        document.addEventListener('keydown', (e) => this.handleKeyDown(e));
        document.addEventListener('keyup', (e) => this.handleKeyUp(e));
        document.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        document.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        document.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        document.addEventListener('contextmenu', (e) => e.preventDefault());
        
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
    
    sendInput() {
        if (this.onInputChange) {
            // Always send input at fixed intervals - no change detection needed
            const input = {
                type: 'playerInput',
                moveX: this.movement.moveX,
                moveY: this.movement.moveY,
                shift: !!this.keys.shift,
                space: !!this.keys.space,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: !!this.mouse.left,
                right: !!this.mouse.right,
                weaponSwitch: null,
                reload: null
            };
            
            this.onInputChange(input);
        }
    }
    
    sendWeaponSwitch(weaponIndex) {
        if (this.onInputChange) {
            const input = {
                type: 'playerInput',
                moveX: this.movement.moveX,
                moveY: this.movement.moveY,
                shift: !!this.keys.shift,
                space: !!this.keys.space,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: false, // Don't accidentally shoot when switching weapons
                right: false,
                weaponSwitch: weaponIndex,
                reload: null
            };
            
            this.onInputChange(input);
        }
    }
    
    sendReload() {
        if (this.onInputChange) {
            const input = {
                type: 'playerInput',
                moveX: this.movement.moveX,
                moveY: this.movement.moveY,
                shift: !!this.keys.shift,
                space: !!this.keys.space,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: false, // Don't accidentally shoot when reloading
                right: false,
                weaponSwitch: null,
                reload: true
            };
            
            this.onInputChange(input);
        }
    }
}

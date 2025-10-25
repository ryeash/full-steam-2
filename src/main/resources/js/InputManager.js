/**
 * Input Manager for handling keyboard, mouse, and gamepad input
 * Supports multiple input devices with automatic switching
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
        
        // Window events
        window.addEventListener('beforeunload', () => this.handleBeforeUnload());
        window.addEventListener('unload', () => this.destroy());
        
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
            case 'r': this.keys.r = true; break;
            case 'shift': this.keys.shift = true; break;
            case ' ': this.keys.space = true; break;
        }
    }

    handleKeyUp(e) {
        switch(e.key.toLowerCase()) {
            case 'w': this.keys.w = false; this.updateMovementAxes(); break;
            case 'a': this.keys.a = false; this.updateMovementAxes(); break;
            case 's': this.keys.s = false; this.updateMovementAxes(); break;
            case 'd': this.keys.d = false; this.updateMovementAxes(); break;
            case 'r': this.keys.r = false; break;
            case 'shift': this.keys.shift = false; break;
            case ' ': this.keys.space = false; break;
        }
    }
    
    handleMouseMove(e) {
        this.mouse.x = e.clientX;
        this.mouse.y = e.clientY;
        this.updateMouseWorldCoordinates();
    }
    
    /**
     * Update mouse world coordinates based on current screen position
     */
    updateMouseWorldCoordinates() {
        const gameEngine = window.gameEngine;
        if (gameEngine && gameEngine.gameContainer) {
            // Use PIXI's built-in transformation to get coordinates relative to the game world
            const screenPos = new PIXI.Point(this.mouse.x, this.mouse.y);
            const worldPos = gameEngine.gameContainer.toLocal(screenPos);

            // gameContainer now uses Y-up coordinates matching physics - no conversion needed!
            this.mouse.worldX = worldPos.x;
            this.mouse.worldY = worldPos.y;
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
        if (e.button === 2) {
            this.mouse.right = true;
        }
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
        if (!this.gamepad.connected) {
            return;
        }
        
        // Handle scoreboard toggle with Back/Select button (button 8)
        const backPressed = this.gamepad.buttons.back;
        const backPrevious = this.gamepad.buttons.back_prev || false;
        
        // Toggle scoreboard on button press (edge detection)
        if (backPressed && !backPrevious) {
            this.toggleScoreboard();
        }
        
        // Store previous button states for edge detection
        this.gamepad.buttons.back_prev = this.gamepad.buttons.back;
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
     * Toggle scoreboard visibility (for gamepad users)
     */
    toggleScoreboard() {
        const scoreboard = document.getElementById('scoreboard');
        if (!scoreboard) return;
        
        if (window.gameEngine) {
            window.gameEngine.scoreboardVisible = !window.gameEngine.scoreboardVisible;
            scoreboard.style.display = window.gameEngine.scoreboardVisible ? 'block' : 'none';
            
            // Show notification for gamepad users
            const action = window.gameEngine.scoreboardVisible ? 'opened' : 'closed';
            this.showInputSourceNotification(`Scoreboard ${action} (Back/Select button)`);
        }
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
    
    handleBeforeUnload() {
        // Optional: any cleanup before page unload
    }
    
    sendInput() {
        if (this.onInputChange) {
            // Update mouse world coordinates every time we send input
            this.updateMouseWorldCoordinates();
            
            // Update gamepad aiming if connected
            if (this.gamepad.connected) {
                this.updateGamepadAiming();
            }
            
            // Determine input values based on active input source
            let moveX, moveY, fire, altFire, reload;
            
            if (this.gamepad.connected && this.inputSource === 'gamepad') {
                // Use gamepad input
                moveX = this.movement.moveX; // Already updated by updateGamepadMovement
                moveY = this.movement.moveY;
                fire = this.gamepad.buttons.rt || this.gamepad.buttons.a; // Right trigger or A button
                altFire = this.gamepad.buttons.rb || this.gamepad.buttons.b; // Right bumper or B button for utility
                reload = this.gamepad.buttons.x;
            } else {
                // Use keyboard/mouse input
                moveX = this.movement.moveX;
                moveY = this.movement.moveY;
                fire = this.mouse.left;
                altFire = this.mouse.right || this.keys.space; // Right click OR space bar for utility
                reload = this.keys.r;
            }
            
            const input = {
                type: 'playerInput',
                moveX: moveX,
                moveY: moveY,
                mouseX: this.mouse.x || 0,
                mouseY: this.mouse.y || 0,
                worldX: this.mouse.worldX || 0,
                worldY: this.mouse.worldY || 0,
                left: !!fire,
                right: !!altFire, // Legacy field - now maps to altFire
                altFire: !!altFire, // New field for utility weapons
                reload: !!reload,
                inputSource: this.inputSource // Let server know input source
            };
            
            this.onInputChange(input);
        }
    }
    
    /**
     * Clean up InputManager resources
     */
    destroy() {
        // Clear the memory cleanup interval
        if (this.memoryCleanupInterval) {
            clearInterval(this.memoryCleanupInterval);
            this.memoryCleanupInterval = null;
        }
        
        // Remove event listeners
        document.removeEventListener('keydown', this.handleKeyDown);
        document.removeEventListener('keyup', this.handleKeyUp);
        document.removeEventListener('mousemove', this.handleMouseMove);
        document.removeEventListener('mousedown', this.handleMouseDown);
        document.removeEventListener('mouseup', this.handleMouseUp);
        document.removeEventListener('contextmenu', (e) => e.preventDefault());
        
        window.removeEventListener('gamepadconnected', this.handleGamepadConnected);
        window.removeEventListener('gamepaddisconnected', this.handleGamepadDisconnected);
        
        // Clear all references
        this.keys = null;
        this.movement = null;
        this.mouse = null;
        this.gamepad = null;
        this.onInputChange = null;
        this.gameEngine = null;
        
        console.log('InputManager destroyed and cleaned up');
    }
}


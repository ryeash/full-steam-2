/**
 * SpectatorUI - Manages spectator-specific UI elements
 * Handles player list, controls, banner, and spectator count
 */
class SpectatorUI {
    constructor(engine) {
        this.engine = engine;
        this.elements = {
            hud: null,
            banner: null,
            spectatorCount: null,
            cameraModeLabel: null,
            followingPlayer: null,
            followedPlayerName: null,
            playerList: null
        };
        this.updateInterval = null;
    }

    /**
     * Initialize and show spectator UI
     */
    show() {
        // Get DOM elements
        this.elements.hud = document.getElementById('spectator-hud');
        this.elements.spectatorCount = document.getElementById('spectator-count');
        this.elements.cameraModeLabel = document.getElementById('camera-mode-label');
        this.elements.followingPlayer = document.getElementById('following-player');
        this.elements.followedPlayerName = document.getElementById('followed-player-name');
        this.elements.playerList = document.getElementById('spectator-player-list');
        
        // Show spectator HUD
        if (this.elements.hud) {
            this.elements.hud.style.display = 'block';
        }
        
        // Hide player HUD (consolidated HUD at bottom)
        const consolidatedHud = document.getElementById('consolidated-hud');
        if (consolidatedHud) {
            consolidatedHud.style.display = 'none';
        }
        
        // Hide minimap/player info HUD (top-left corner) for spectators
        if (this.engine.hudContainer) {
            this.engine.hudContainer.visible = false;
        }
        
        // Start periodic updates
        this.startPeriodicUpdates();
    }

    /**
     * Hide spectator UI
     */
    hide() {
        if (this.elements.hud) {
            this.elements.hud.style.display = 'none';
        }
        
        this.stopPeriodicUpdates();
    }

    /**
     * Start periodic UI updates
     */
    startPeriodicUpdates() {
        // Update player list every 500ms
        this.updateInterval = setInterval(() => {
            this.updatePlayerList();
        }, 500);
    }

    /**
     * Stop periodic updates
     */
    stopPeriodicUpdates() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
    }

    /**
     * Update spectator count display
     */
    updateSpectatorCount(count) {
        if (this.elements.spectatorCount) {
            const plural = count === 1 ? 'spectator' : 'spectators';
            this.elements.spectatorCount.textContent = `${count} ${plural}`;
        }
    }

    /**
     * Update camera mode label
     */
    updateCameraMode(mode) {
        if (this.elements.cameraModeLabel) {
            const modeText = mode.charAt(0).toUpperCase() + mode.slice(1);
            this.elements.cameraModeLabel.textContent = modeText;
        }
        
        // Show/hide following player info
        if (this.elements.followingPlayer) {
            this.elements.followingPlayer.style.display = 
                mode === 'follow' ? 'flex' : 'none';
        }
    }

    /**
     * Update followed player name
     */
    updateFollowedPlayer(playerName) {
        if (this.elements.followedPlayerName && playerName) {
            this.elements.followedPlayerName.textContent = playerName;
        }
    }

    /**
     * Update player list with current players
     */
    updatePlayerList() {
        if (!this.elements.playerList) return;
        
        const players = Array.from(this.engine.players.values());
        if (players.length === 0) {
            this.elements.playerList.innerHTML = '<h3>Players</h3><p style="color: #888; text-align: center;">No players in game</p>';
            return;
        }
        
        // Group players by team (accessing playerData)
        const teams = new Map();
        for (const playerSprite of players) {
            const playerData = playerSprite.playerData;
            if (!playerData) continue; // Skip if no data
            
            const teamId = playerData.team || 0;
            if (!teams.has(teamId)) {
                teams.set(teamId, []);
            }
            teams.get(teamId).push(playerSprite);
        }
        
        // Build HTML
        let html = '<h3>Players</h3>';
        
        // Sort teams
        const sortedTeams = Array.from(teams.entries()).sort((a, b) => a[0] - b[0]);
        
        for (const [teamId, teamPlayers] of sortedTeams) {
            html += '<div class="spectator-team">';
            html += `<h4>${this.getTeamName(teamId)}</h4>`;
            
            // Sort players by score/kills
            teamPlayers.sort((a, b) => {
                const scoreA = a.playerData?.score || 0;
                const scoreB = b.playerData?.score || 0;
                return scoreB - scoreA;
            });
            
            for (const playerSprite of teamPlayers) {
                const playerData = playerSprite.playerData;
                if (!playerData) continue;
                
                const isFollowing = this.engine.spectatorMode && 
                    this.engine.spectatorMode.camera.getFollowedPlayerId() === playerData.id;
                
                // Health is already a percentage (0-100) from server
                let healthPercent = playerData.health || 0;
                // If health is between 0-1, convert to percentage
                if (healthPercent > 0 && healthPercent <= 1) {
                    healthPercent = healthPercent * 100;
                }
                healthPercent = Math.max(0, Math.min(100, healthPercent));
                
                const teamColor = this.engine.getTeamColorCSS(playerData.team || 0);
                // Server sends "name" not "playerName"
                const playerName = this.escapeHtml(playerData.name || playerData.playerName || `Player ${playerData.id}`);
                
                html += `<div class="spectator-player-item ${isFollowing ? 'following' : ''}" 
                         onclick="window.gameEngine.spectatorMode.followPlayer(${playerData.id})">`;
                html += `<div class="player-name" style="color: ${teamColor}; font-weight: bold;">${playerName}</div>`;
                html += `<div class="player-health-bar">`;
                html += `<div class="health-fill" style="width: ${healthPercent}%"></div>`;
                html += `</div>`;
                html += `</div>`;
            }
            
            html += '</div>';
        }
        
        this.elements.playerList.innerHTML = html;
    }

    /**
     * Get team name for display
     */
    getTeamName(teamId) {
        if (teamId === 0) return 'Free For All';
        return `Team ${teamId}`;
    }

    /**
     * Get weapon display name
     */
    getWeaponDisplayName(weaponType) {
        if (!weaponType) return 'Unknown';
        
        // Convert ENUM_CASE to Title Case
        return weaponType
            .split('_')
            .map(word => word.charAt(0) + word.slice(1).toLowerCase())
            .join(' ');
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Show notification message
     */
    showNotification(message, duration = 3000) {
        // Create notification element
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0, 170, 255, 0.9);
            color: white;
            padding: 10px 20px;
            border-radius: 5px;
            z-index: 10000;
            font-size: 14px;
            pointer-events: none;
        `;
        notification.textContent = message;
        document.body.appendChild(notification);
        
        // Fade out and remove
        setTimeout(() => {
            notification.style.transition = 'opacity 0.5s';
            notification.style.opacity = '0';
            setTimeout(() => notification.remove(), 500);
        }, duration);
    }

    /**
     * Cleanup
     */
    destroy() {
        this.stopPeriodicUpdates();
        this.hide();
    }
}


// Game mode information database
const GAME_MODE_INFO = {
    'Team Deathmatch': {
        subtitle: 'Classic team-based combat',
        objective: 'Eliminate enemy players to score points for your team. The team with the most eliminations when time runs out wins.',
        teamBased: true,
        team1Objective: 'Eliminate Team 2 players',
        team2Objective: 'Eliminate Team 1 players'
    },
    'King of the Hill': {
        subtitle: 'Control the central point',
        objective: 'Control the central hill to accumulate points for your team. Contest the hill to prevent the enemy from scoring.',
        teamBased: true,
        team1Objective: 'Control and hold the hill',
        team2Objective: 'Control and hold the hill'
    },
    'Capture the Flag': {
        subtitle: 'Capture and return enemy flags',
        objective: 'Infiltrate the enemy base, steal their flag, and return it to your base to score. Defend your own flag from enemy flag carriers. Your flag must be present at your base to score.',
        teamBased: true,
        team1Objective: 'Capture Team 2\'s flag and return it to your base',
        team2Objective: 'Capture Team 1\'s flag and return it to your base'
    },
    'Free For All': {
        subtitle: 'Every player for themselves',
        objective: 'Eliminate all other players to accumulate the most kills. There are no teams - everyone is your enemy.',
        teamBased: false
    },
    'Elimination': {
        subtitle: 'Last team standing wins',
        objective: 'Eliminate all members of the opposing team to win the round. Players do not respawn until the next round begins.',
        teamBased: true,
        team1Objective: 'Eliminate all Team 2 players',
        team2Objective: 'Eliminate all Team 1 players'
    },
    'Stock Battle': {
        subtitle: 'Limited lives tactical combat',
        objective: 'Each team has limited revives (stock). Eliminate enemies to deplete their stock, then eliminate remaining players to win.',
        teamBased: true,
        team1Objective: 'Deplete Team 2\'s stock and eliminate survivors',
        team2Objective: 'Deplete Team 1\'s stock and eliminate survivors'
    },
    'Infection': {
        subtitle: 'Survive the outbreak',
        objective: 'Survivors must outlast the infected until time runs out. Infected must spread the virus to all survivors by eliminating them.',
        teamBased: true,
        team1Objective: 'Survive until time runs out',
        team2Objective: 'Infect all survivors'
    },
    'Zombie Defense': {
        subtitle: 'Survive the undead horde',
        objective: 'Work together as survivors to defend against increasingly difficult waves of zombies until the timer runs out.',
        teamBased: true,
        team1Objective: 'Survive all zombie waves',
        team2Objective: 'Eliminate all survivors (AI Zombies)'
    },
    'Oddball': {
        subtitle: 'Hold the oddball to score',
        objective: 'Control the Oddball to accumulate points for your team. Eliminate the carrier to make them drop it. The carrier is not able to shoot.',
        teamBased: true,
        team1Objective: 'Hold the Oddball and defend the carrier',
        team2Objective: 'Hold the Oddball and defend the carrier'
    },
    'Escort': {
        subtitle: 'Move the payload',
        objective: 'Teams compete to move the payload to the opposite side of the map. Stand near the payload to move it towards your goal.',
        teamBased: true,
        team1Objective: 'Push the payload to the right side',
        team2Objective: 'Push the payload to the left side'
    },
    'Juggernaut': {
        subtitle: 'Target the chosen ones',
        objective: 'Each team has one Juggernaut with enhanced health. Score by eliminating the enemy Juggernaut.',
        teamBased: true,
        team1Objective: 'Eliminate Team 2\'s Juggernaut',
        team2Objective: 'Eliminate Team 1\'s Juggernaut'
    },
    'Gun Master': {
        subtitle: 'Rotating weapon mayhem',
        objective: 'All players use the same weapon, which changes every 20 seconds. Adapt quickly to each new weapon.',
        teamBased: false
    },
    'Builder': {
        subtitle: 'Create your battlefield',
        objective: 'Use the action key (E) to place destructible crates. Build defensive positions and destroy enemy structures.',
        teamBased: false
    },
    'Lone Wolf': {
        subtitle: 'One vs. many survival',
        objective: 'One enhanced player (the Lone Wolf) fights against multiple hunters. Survive as long as possible or hunt down the wolf.',
        teamBased: true,
        team1Objective: 'Survive as the Lone Wolf',
        team2Objective: 'Hunt down the Lone Wolf'
    },
    'Portal': {
        subtitle: 'Tactical portal warfare',
        objective: 'Use your alt-fire to launch portals and redirect bullet trajectories. Eliminate enemy players to score points for your team.',
        teamBased: true,
        team1Objective: 'Eliminate Team 2 players using portal tactics',
        team2Objective: 'Eliminate Team 1 players using portal tactics'
    },
    'Base Destruction': {
        subtitle: 'Symmetric base warfare with vehicles',
        objective: 'Both teams have a base to defend and must destroy the enemy base while protecting their own. Capture motor pools to spawn vehicles for attack and defense.',
        teamBased: true,
        team1Objective: 'Destroy Team 2\'s base while defending your own',
        team2Objective: 'Destroy Team 1\'s base while defending your own'
    },
    'Progression': {
        subtitle: 'Weapon tier advancement',
        objective: 'Start with a weak weapon and upgrade by collecting drops from defeated enemies. Players cannot manually change weapons.',
        teamBased: false
    },
    'Blitz': {
        subtitle: 'Tactical capture warfare',
        objective: 'Capture opponent capture points while defending your own. Teams must balance offense and defense.',
        teamBased: true,
        team1Objective: 'Capture Team 2\'s points while defending your own',
        team2Objective: 'Capture Team 1\'s points while defending your own'
    }
};

// Game info overlay management
class GameInfoOverlay {
    constructor() {
        this.overlay = document.getElementById('game-info-overlay');
        this.isVisible = false;
        this.currentGameMode = null;
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Keyboard event for Tab key
        document.addEventListener('keydown', (e) => {
            if (e.code === 'Tab') {
                e.preventDefault();
                if (!this.isVisible) {
                    this.show();
                }
            }
        });

        document.addEventListener('keyup', (e) => {
            if (e.code === 'Tab') {
                e.preventDefault();
                if (this.isVisible) {
                    this.hide();
                }
            }
        });

        // Close overlay when clicking outside the content
        this.overlay.addEventListener('click', (e) => {
            if (e.target === this.overlay) {
                this.hide();
            }
        });
    }

    updateGamepadEvents(gamepad) {
        if (!gamepad) return;

        // Check for menu/options button (button 9 on most gamepads)
        const menuButton = gamepad.buttons[9]; // Start/Options/Menu button
        const selectButton = gamepad.buttons[8]; // Back/Select/View button

        if ((menuButton && menuButton.pressed) || (selectButton && selectButton.pressed)) {
            if (!this.gamepadButtonPressed) {
                this.gamepadButtonPressed = true;
                if (this.isVisible) {
                    this.hide();
                } else {
                    this.show();
                }
            }
        } else {
            this.gamepadButtonPressed = false;
        }
    }

    show() {
        this.isVisible = true;
        this.overlay.style.display = 'block';
        this.updateContent();
    }

    hide() {
        this.isVisible = false;
        this.overlay.style.display = 'none';
    }

    updateGameMode(gameMode) {
        this.currentGameMode = gameMode;
        if (this.isVisible) {
            this.updateContent();
        }
    }

    updateContent() {
        if (!this.currentGameMode) return;

        const modeInfo = GAME_MODE_INFO[this.currentGameMode];
        if (!modeInfo) {
            // Fallback for unknown game modes
            document.getElementById('overlay-game-mode').textContent = this.currentGameMode;
            document.getElementById('overlay-game-subtitle').textContent = 'Custom game mode';
            document.getElementById('objective-text').textContent = 'Game mode information not available.';
            document.getElementById('team-objectives').style.display = 'none';
            return;
        }

        // Update header
        document.getElementById('overlay-game-mode').textContent = this.currentGameMode;
        document.getElementById('overlay-game-subtitle').textContent = modeInfo.subtitle;

        // Update objective
        document.getElementById('objective-text').textContent = modeInfo.objective;

        // Update team objectives if applicable
        if (modeInfo.teamBased && modeInfo.team1Objective && modeInfo.team2Objective) {
            document.getElementById('team1-objective-text').textContent = modeInfo.team1Objective;
            document.getElementById('team2-objective-text').textContent = modeInfo.team2Objective;
            document.getElementById('team-objectives').style.display = 'block';
        } else {
            document.getElementById('team-objectives').style.display = 'none';
        }
    }
}

window.unzip = async function(compressedBuffer) {
    const ds = new DecompressionStream("gzip");
    const writer = ds.writable.getWriter();
    writer.write(compressedBuffer);
    writer.close();
    const reader = ds.readable.getReader();
    const chunks = [];
    let totalSize = 0;
    while (true) {
        const { value, done } = await reader.read();
        if (done) {
            break;
        }
        chunks.push(value);
        totalSize += value.byteLength;
    }
    const decompressedUint8Array = new Uint8Array(totalSize);
    let offset = 0;
    for (const chunk of chunks) {
        decompressedUint8Array.set(chunk, offset);
        offset += chunk.byteLength;
    }
    return JSON.parse(new TextDecoder().decode(decompressedUint8Array))
}

class GameUIManager {
    constructor(gameInstance) {
        this.game = gameInstance;
        this.doc = document;
        this.lastScoreboardUpdateTime = 0;
        this.SCOREBOARD_UPDATE_INTERVAL = 1000;

        this.elements = {
            playerCount: this.doc.getElementById('playerCount'),
            gameModeDisplay: this.doc.getElementById('gameModeDisplay'),
            roundTimer: this.doc.getElementById('roundTimer'),
            team1Score: this.doc.getElementById('team1Score'),
            team2Score: this.doc.getElementById('team2Score'),
            yourTeam: this.doc.getElementById('yourTeam').parentElement,
            switchTeamBtn: this.doc.getElementById('switchTeamBtn'),
            team1Scoreboard: this.doc.getElementById('team1-scoreboard'),
            team2Scoreboard: this.doc.getElementById('team2-scoreboard'),
            ffaScoreboard: this.doc.getElementById('ffa-scoreboard'),
            team1List: this.doc.getElementById('team1-players'),
            team2List: this.doc.getElementById('team2-players'),
            ffaList: this.doc.getElementById('ffa-players'),
            gameIdIndicator: this.doc.getElementById('gameIdIndicator'),
            weaponSelector: this.doc.getElementById('weaponSelector')
        };

        this.infoBarUpdaters = {
            'Free For All': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.team1Score.style.display = 'none';
                this.elements.team2Score.style.display = 'none';
                this.elements.yourTeam.style.display = 'none';
                this.elements.switchTeamBtn.style.display = 'none';
            },
            'Builder': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.team1Score.style.display = 'none';
                this.elements.team2Score.style.display = 'none';
                this.elements.yourTeam.style.display = 'none';
                this.elements.switchTeamBtn.style.display = 'none';
            },
            'Gun Master': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.team1Score.style.display = 'none';
                this.elements.team2Score.style.display = 'none';
                this.elements.yourTeam.style.display = 'none';
                this.elements.switchTeamBtn.style.display = 'none';
                this.doc.getElementById('weaponSelector').parentElement.style.display = 'none';
            },
            'Progression': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.team1Score.style.display = 'none';
                this.elements.team2Score.style.display = 'none';
                this.elements.yourTeam.style.display = 'none';
                this.elements.switchTeamBtn.style.display = 'none';
                this.doc.getElementById('weaponSelector').parentElement.style.display = 'none';
            },
            'Zombie Defense': (info) => {
                this.game.shouldDrawRespawnOverlay = false;
                this.elements.yourTeam.style.display = 'none';
                this.elements.switchTeamBtn.style.display = 'none';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';
                this.elements.team1Score.innerHTML = `Wave: <span style="color: ${GameColors.accent.gold};">${info.waveNumber || 0}</span>`;
                this.elements.team2Score.innerHTML = `Zombies: <span style="color: ${GameColors.accent.gold};">${info.zombiesAlive || 0}</span>`;
            },
            'Elimination': (info) => {
                this.game.shouldDrawRespawnOverlay = false;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'block';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';
                this.elements.team1Score.innerHTML = `${info.team1Score || 0} <span style="font-size: 0.7em; color: ${GameColors.text.secondary};">(${info.team1PlayersAlive || 0} alive)</span>`;
                this.elements.team2Score.innerHTML = `${info.team2Score || 0} <span style="font-size: 0.7em; color: ${GameColors.text.secondary};">(${info.team2PlayersAlive || 0} alive)</span>`;
            },
            'Lone Wolf': (info) => {
                this.game.shouldDrawRespawnOverlay = false;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'block';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'none';
                const lives = info.loneWolfLives || 0;
                this.elements.team1Score.innerHTML = lives + "🐺";
            },
            'Base Destruction': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'block';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';

                // Show base health percentages and team scores
                const team1BaseHealthPercent = info.team1Base ? Math.round(((info.team1Base.hp || 0) / info.team1Base.maxHp) * 100) : 0;
                const team2BaseHealthPercent = info.team2Base ? Math.round(((info.team2Base.hp || 0) / info.team2Base.maxHp) * 100) : 0;
                
                const team1Color = team1BaseHealthPercent > 50 ? GameColors.health.high : team1BaseHealthPercent > 25 ? GameColors.health.medium : GameColors.health.low;
                const team2Color = team2BaseHealthPercent > 50 ? GameColors.health.high : team2BaseHealthPercent > 25 ? GameColors.health.medium : GameColors.health.low;
                
                this.elements.team1Score.innerHTML = `${Math.floor(info.team1Score || 0)} <span style="font-size: 0.8em; color: ${team1Color};">Base: ${team1BaseHealthPercent}%</span>`;
                this.elements.team2Score.innerHTML = `${Math.floor(info.team2Score || 0)} <span style="font-size: 0.8em; color: ${team2Color};">Base: ${team2BaseHealthPercent}%</span>`;
            },
            'Stock Battle': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'block';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';
                
                // Show kills and stock for each team
                const team1StockColor = GameColors.teams.team1.primary
                const team2StockColor = GameColors.teams.team2.primary
                
                this.elements.team1Score.innerHTML = `<span style="font-size: 0.8em; color: ${team1StockColor};">Stock: ${info.team1Stock || 0}</span>`;
                this.elements.team2Score.innerHTML = `<span style="font-size: 0.8em; color: ${team2StockColor};">Stock: ${info.team2Stock || 0}</span>`;
            },
            'Infection': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'none'; // No team switching in infection
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';
                
                // Show survivor and infected counts with color coding
                const survivorColor = info.survivorCount > 0 ? GameColors.teams.team1.primary : GameColors.health.low;
                const infectedColor = info.infectedCount > 0 ? GameColors.teams.team2.primary : GameColors.health.low;
                
                this.elements.team1Score.innerHTML = `<span style="color: ${survivorColor};">Survivors: ${info.survivorCount || 0}</span>`;
                this.elements.team2Score.innerHTML = `<span style="color: ${infectedColor};">Infected: ${info.infectedCount || 0}</span>`;
                
                // Show infection countdown during preparation phase
                if (!info.gameStarted && info.infectionStartTime) {
                    const currentTime = Date.now();
                    const timeUntilInfection = Math.max(0, Math.ceil((info.infectionStartTime - currentTime) / 1000));
                    if (timeUntilInfection > 0) {
                        this.elements.roundTimer.textContent = `Infection in: ${timeUntilInfection}s`;
                    }
                }
            },
            'default': (info) => {
                this.game.shouldDrawRespawnOverlay = true;
                this.elements.yourTeam.style.display = 'inline';
                this.elements.switchTeamBtn.style.display = 'block';
                this.elements.team1Score.style.display = 'inline';
                this.elements.team2Score.style.display = 'inline';
                this.elements.team1Score.textContent = `${Math.floor(info.team1Score || 0)}`;
                this.elements.team2Score.textContent = `${Math.floor(info.team2Score || 0)}`;
            }
        };

        this.scoreboardUpdaters = {
            'Free For All': (scores, playerId) => {
                this.elements.team1Scoreboard.style.display = 'none';
                this.elements.team2Scoreboard.style.display = 'none';
                this.elements.ffaScoreboard.style.display = 'block';

                this.elements.ffaList.innerHTML = '';
                scores.forEach(p => this.elements.ffaList.appendChild(this.createPlayerEntry(p, playerId)));
            },
            'Builder': (scores, playerId) => {
                this.elements.team1Scoreboard.style.display = 'none';
                this.elements.team2Scoreboard.style.display = 'none';
                this.elements.ffaScoreboard.style.display = 'block';
                this.elements.ffaList.innerHTML = '';
                scores.forEach(p => this.elements.ffaList.appendChild(this.createPlayerEntry(p, playerId)));
            },
            'Gun Master': (scores, playerId) => {
                this.elements.team1Scoreboard.style.display = 'none';
                this.elements.team2Scoreboard.style.display = 'none';
                this.elements.ffaScoreboard.style.display = 'block';

                this.elements.ffaList.innerHTML = '';
                scores.forEach(p => this.elements.ffaList.appendChild(this.createPlayerEntry(p, playerId)));
            },
            'Progression': (scores, playerId) => {
                this.elements.team1Scoreboard.style.display = 'none';
                this.elements.team2Scoreboard.style.display = 'none';
                this.elements.ffaScoreboard.style.display = 'block';

                this.elements.ffaList.innerHTML = '';
                scores.forEach(p => this.elements.ffaList.appendChild(this.createPlayerEntry(p, playerId)));
            },
            'default': (scores, playerId) => {
                this.elements.team1Scoreboard.style.display = 'block';
                this.elements.team2Scoreboard.style.display = 'block';
                this.elements.ffaScoreboard.style.display = 'none';

                this.elements.team1List.innerHTML = '';
                this.elements.team2List.innerHTML = '';

                const team1Players = scores.filter(p => p.team === 1);
                const team2Players = scores.filter(p => p.team === 2);

                team1Players.forEach(p => this.elements.team1List.appendChild(this.createPlayerEntry(p, playerId)));
                team2Players.forEach(p => this.elements.team2List.appendChild(this.createPlayerEntry(p, playerId)));
            }
        };
    }

    update(gameState, scores) {
        if (!gameState || !gameState.info) return;

        this.elements.playerCount.textContent = gameState.players ? gameState.players.length : 0;
        const gameModeName = gameState.info.type.replace('Manager', '');
        this.elements.gameModeDisplay.textContent = gameModeName;

        // Update the game info overlay with the current game mode
        if (window.gameInfoOverlay) {
            window.gameInfoOverlay.updateGameMode(gameModeName);
        }

        if (gameState.info.timeLeft !== undefined) {
            const totalSeconds = gameState.info.timeLeft || 0;
            const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
            const seconds = (totalSeconds % 60).toString().padStart(2, '0');
            this.elements.roundTimer.textContent = `${minutes}:${seconds}`;
        }

        const infoUpdater = this.infoBarUpdaters[gameState.info.type] || this.infoBarUpdaters.default;
        infoUpdater(gameState.info);

        const now = Date.now();
        if (now - this.lastScoreboardUpdateTime > this.SCOREBOARD_UPDATE_INTERVAL) {
            const scoreboardUpdater = this.scoreboardUpdaters[gameState.info.type] || this.scoreboardUpdaters.default;
            scoreboardUpdater(scores, this.game.playerId);
            this.lastScoreboardUpdateTime = now;
        }

        if (this.game.playerId && this.game.localPlayer && this.game.localPlayer.weapon) {
            if (this.elements.weaponSelector.value !== this.game.localPlayer.weapon.name) {
                this.elements.weaponSelector.value = this.game.localPlayer.weapon.name;
                localStorage.setItem('weaponName', this.game.localPlayer.weapon.name);
            }
        }
    }

    updateOnWelcome(data) {
        const teamElement = this.doc.getElementById('yourTeam');
        teamElement.textContent = data.team;
        teamElement.className = `team${data.team}`;
        if (data.gameId) {
            this.elements.gameIdIndicator.textContent = `(gameId: ${data.gameId})`;
        }
    }

    createPlayerEntry(player, playerId) {
        const entry = this.doc.createElement('li');
        entry.className = 'scoreboard-player';
        if (player.id === playerId) {
            entry.classList.add('local-player-highlight');
        }

        const nameSpan = this.doc.createElement('span');
        nameSpan.className = 'player-name';
        nameSpan.textContent = player.name;
        nameSpan.title = player.name || player.id;

        const scoreSpan = this.doc.createElement('span');
        scoreSpan.className = 'player-score';
        scoreSpan.textContent = `${player.kills || 0} / ${player.deaths || 0}`;

        entry.appendChild(nameSpan);
        entry.appendChild(scoreSpan);
        return entry;
    }
}

class Game {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = this.canvas.getContext('2d');
        this.ws = null;
        this.gameId = null;
        this.keys = {};
        this.mouse = { x: 0, y: 0, down: false };
        this.playerId = null;
        this.playerTeam = null;
        this.isLocalPlayerDead = false;
        this.shouldDrawRespawnOverlay = true;
        this.isSpectator = false;
        this.gameEvents = [];
        this.scores = [];
        this.gamepad = null;
        this.isGamepadActive = false;
        this.gamepadAim = { x: 1, y: 0 };
        this.gamepadMoveDir = { x: 0, y: 0 };
        this.weaponNames = [];
        this.gamepadWeaponSwapPressed = false;

        this.latency = 0;
        this.pingStartTime = 0;

        this.backgroundCanvas = document.createElement('canvas');
        this.backgroundCtx = this.backgroundCanvas.getContext('2d');
        this.currentGameType = '';
        this.backgroundNeedsRedraw = true;
        this.localPlayer = null;

        // Interpolation system
        this.previousGameState = null;
        this.lastUpdateTime = 0;
        this.interpolationTime = 100; // ms to interpolate over
        this.interpolatedPlayers = new Map();
        this.interpolatedBullets = new Map();

        this.isRenderLoopRunning = false;
        this.lastFrameTime = 0;
        this.frameCount = 0;
        this.fps = 0;
        this.lastFpsTime = 0;
        this.resetGameState();
        this.uiManager = new GameUIManager(this);
        this.loadName();
        this.setupEventListeners();
        this.connect();
        this.startGameLoop();
    }

    resetGameState() {
        this.gameState = { players: [], bullets: [], laserBlasts: [], fieldEffects: [], obstacles: [], powerUps: [], vehicles: [], info: null, serverTime: 0 };
        this.gameEvents = [];
        this.previousGameState = null;
        this.lastUpdateTime = 0;
        this.interpolatedPlayers.clear();
        this.interpolatedBullets.clear();
    }

    // Linear interpolation function
    lerp(start, end, t) {
        return start + (end - start) * t;
    }

    // Update interpolation data when new game state arrives
    updateInterpolation(newGameState) {
        const currentTime = performance.now();

        // Store previous state for interpolation
        if (this.gameState) {
            this.previousGameState = structuredClone(this.gameState);
            this.lastUpdateTime = currentTime;
        }

        // Update current state
        this.gameState = newGameState;

        // Clean up interpolation data for disconnected players
        if (this.gameState.players) {
            const currentPlayerIds = new Set(this.gameState.players.map(p => p.id));
            for (const playerId of this.interpolatedPlayers.keys()) {
                if (!currentPlayerIds.has(playerId)) {
                    this.interpolatedPlayers.delete(playerId);
                }
            }

            // Initialize interpolated positions for new players
            this.gameState.players.forEach(player => {
                if (!this.interpolatedPlayers.has(player.id)) {
                    this.interpolatedPlayers.set(player.id, {
                        x: player.x,
                        y: player.y,
                        mouseX: player.mouseX,
                        mouseY: player.mouseY
                    });
                }
            });
        }

        // Clean up interpolation data for expired bullets
        if (this.gameState.bullets) {
            const currentBulletIds = new Set(this.gameState.bullets.map(b => b.id));
            for (const bulletId of this.interpolatedBullets.keys()) {
                if (!currentBulletIds.has(bulletId)) {
                    this.interpolatedBullets.delete(bulletId);
                }
            }

            // Initialize interpolated positions for new bullets
            this.gameState.bullets.forEach(bullet => {
                if (!this.interpolatedBullets.has(bullet.id)) {
                    this.interpolatedBullets.set(bullet.id, {
                        x: bullet.x || 0,
                        y: bullet.y || 0
                    });
                }
            });
        }
    }

    // Get interpolated positions for rendering
    getInterpolatedPositions(renderTime) {
        if (!this.previousGameState || !this.gameState) {
            return {
                players: this.gameState?.players || [],
                bullets: this.gameState?.bullets || []
            };
        }

        // Use render time for more accurate interpolation
        const currentTime = renderTime || performance.now();
        const timeSinceUpdate = currentTime - this.lastUpdateTime;
        const t = Math.min(Math.max(timeSinceUpdate / this.interpolationTime, 0.0), 1.0);

        if (t > 1.0 && timeSinceUpdate > this.interpolationTime * 2) {
            // If we haven't received an update in a long time, don't interpolate
            return {
                players: this.gameState?.players || [],
                bullets: this.gameState?.bullets || []
            };
        }

        // Interpolate player positions
        const interpolatedPlayers = [];
        if (this.gameState.players) {
            this.gameState.players.forEach(currentPlayer => {
                const previousPlayer = this.previousGameState?.players?.find(p => p.id === currentPlayer.id);

                if (previousPlayer && !currentPlayer.dead && !previousPlayer.dead) {
                    // Check if player teleported/respawned (distance too large for normal movement)
                    const distance = Math.sqrt(
                        Math.pow(currentPlayer.x - previousPlayer.x, 2) +
                        Math.pow(currentPlayer.y - previousPlayer.y, 2)
                    );
                    const maxReasonableDistance = 100; // Adjust based on game mechanics

                    if (distance > maxReasonableDistance) {
                        // Player likely teleported, don't interpolate
                        interpolatedPlayers.push(currentPlayer);
                        this.interpolatedPlayers.set(currentPlayer.id, {
                            x: currentPlayer.x,
                            y: currentPlayer.y,
                            mouseX: currentPlayer.mouseX,
                            mouseY: currentPlayer.mouseY
                        });
                    } else {
                        // Normal movement, interpolate
                        const interpolatedPlayer = { ...currentPlayer };
                        interpolatedPlayer.x = this.lerp(previousPlayer.x, currentPlayer.x, t);
                        interpolatedPlayer.y = this.lerp(previousPlayer.y, currentPlayer.y, t);

                        // Don't interpolate mouse position for local player (use live position)
                        if (currentPlayer.id !== this.playerId) {
                            interpolatedPlayer.mouseX = this.lerp(previousPlayer.mouseX, currentPlayer.mouseX, t);
                            interpolatedPlayer.mouseY = this.lerp(previousPlayer.mouseY, currentPlayer.mouseY, t);
                        }

                        interpolatedPlayers.push(interpolatedPlayer);

                        // Update stored interpolated position
                        this.interpolatedPlayers.set(currentPlayer.id, {
                            x: interpolatedPlayer.x,
                            y: interpolatedPlayer.y,
                            mouseX: interpolatedPlayer.mouseX,
                            mouseY: interpolatedPlayer.mouseY
                        });
                    }
                } else {
                    // No previous position, player just respawned, or player is dead - use current position
                    interpolatedPlayers.push(currentPlayer);
                    this.interpolatedPlayers.set(currentPlayer.id, {
                        x: currentPlayer.x,
                        y: currentPlayer.y,
                        mouseX: currentPlayer.mouseX,
                        mouseY: currentPlayer.mouseY
                    });
                }
            });
        }

        // Interpolate bullet positions using ID-based tracking
        const interpolatedBullets = [];
        if (this.gameState.bullets) {
            this.gameState.bullets.forEach(currentBullet => {
                const previousBullet = this.previousGameState?.bullets?.find(b => b.id === currentBullet.id);

                if (previousBullet) {
                    // Create interpolated bullet
                    const interpolatedBullet = { ...currentBullet };
                    interpolatedBullet.x = this.lerp(previousBullet.x, currentBullet.x, t);
                    interpolatedBullet.y = this.lerp(previousBullet.y, currentBullet.y, t);
                    interpolatedBullets.push(interpolatedBullet);

                    // Update stored interpolated position
                    this.interpolatedBullets.set(currentBullet.id, {
                        x: interpolatedBullet.x,
                        y: interpolatedBullet.y
                    });
                } else {
                    // New bullet, use current position
                    interpolatedBullets.push(currentBullet);
                    this.interpolatedBullets.set(currentBullet.id, {
                        x: currentBullet.x,
                        y: currentBullet.y
                    });
                }
            });
        }

        return {
            players: interpolatedPlayers,
            bullets: interpolatedBullets
        };
    }

    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const urlParams = new URLSearchParams(window.location.search);
        const isSpectator = urlParams.get('spectate') === 'true';
        const gameId = urlParams.get('gameId') || 'null';
        const gameType = urlParams.get('gameType') || 'null';
        let wsUrl;

        if (isSpectator) {
            wsUrl = `${protocol}//${location.hostname}:${location.port}/game/${gameId}/spectate`;
            this.enterSpectatorMode();
        } else {
            wsUrl = `${protocol}//${location.hostname}:${location.port}/game/${gameId}/${gameType}`;
        }

        this.ws = new WebSocket(wsUrl);
        this.ws.binaryType = "arraybuffer";

        this.backgroundCanvas.width = this.canvas.width;
        this.backgroundCanvas.height = this.canvas.height;

        this.ws.onopen = () => {
            if (this.isSpectator) {
                document.getElementById('status').textContent = 'Spectating';
                document.getElementById('status').style.color = GameColors.accent.blue;
            } else {
                document.getElementById('status').textContent = 'Connected';
                document.getElementById('status').style.color = GameColors.teams.team1.primary;
            }
            setInterval(() => {
                this.pingStartTime = Date.now();
                this.ws.send(JSON.stringify({ type: 'ping' }));
            }, 5000);
        };

        this.ws.onmessage = (event) => {
            unzip(event.data)
                .then((data) => {
                    if (data.type === 'pong') {
                        this.latency = Date.now() - this.pingStartTime;
                        const latencyEl = document.getElementById('latency');
                        latencyEl.textContent = this.latency;
                    } else if (data.type === 'welcome') {
                        this.gameId = data.gameId;
                        this.obstacles = data.obstacles;
                        this.playerId = data.playerId;
                        this.playerTeam = data.team;
                        this.backgroundNeedsRedraw = true;
                        this.resetGameState();
                        this.uiManager.updateOnWelcome(data);
                        if (data.weaponOptions) {
                            this.populateWeaponSelector(data.weaponOptions);
                        }
                    } else if (data.type === 'gameEvent') {
                        const event = data.event;
                        this.gameEvents.push(event);
                    } else if (data.type == 'scores') {
                        this.scores = data.scores;
                    } else {
                        // Use interpolation system for game state updates
                        this.updateInterpolation(data);
                        this.localPlayer = data.players.find(p => p.id === this.playerId);
                        this.isLocalPlayerDead = this.localPlayer && this.localPlayer.dead;
                        this.uiManager.update(this.gameState, this.scores);
                    }
                })
        };

        this.ws.onclose = () => {
            document.getElementById('status').textContent = 'Disconnected';
            document.getElementById('status').style.color = GameColors.teams.team2.primary;
            this.resetGameState();
            // Keep render loop running to show disconnected state
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    setupEventListeners() {
        document.addEventListener('keydown', (e) => {
            // Don't register game key-presses if the user is typing in an input field.
            const activeEl = document.activeElement;
            if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'SELECT')) {
                return;
            }
            this.keys[e.code] = true;
        });
        document.addEventListener('keyup', (e) => {
            const activeEl = document.activeElement;
            if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'SELECT')) {
                return;
            }
            this.keys[e.code] = false;
        });

        this.canvas.addEventListener('mousemove', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            this.mouse.x = e.clientX - rect.left;
            this.mouse.y = e.clientY - rect.top;
        });
        this.canvas.addEventListener('mousedown', (e) => { if (e.button === 0) this.mouse.down = true; });
        this.canvas.addEventListener('mouseup', (e) => { if (e.button === 0) this.mouse.down = false; });
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());

        // When the window loses focus, clear all input state to prevent the player
        // from getting stuck moving in one direction.
        window.addEventListener('blur', () => {
            this.keys = {};
            this.mouse.down = false;
        });

        this.canvas.addEventListener('dragstart', (e) => e.preventDefault());

        document.getElementById('nameInput').addEventListener('change', () => {
            this.sendConfigChange();
            this.canvas.focus();
        });
        document.getElementById('weaponSelector').addEventListener('change', () => {
            this.sendConfigChange();
            this.canvas.focus();
        });
        document.getElementById('leaveGameBtn').addEventListener('click', () => {
            if (this.ws) {
                this.ws.close();
            }
            window.location.href = '/';
        });
        document.getElementById('switchTeamBtn').addEventListener('click', () => {
            this.sendTeamChangeRequest();
            this.canvas.focus();
        });
        document.getElementById('spectatorBtn').addEventListener('click', () => {
            this.switchToSpectator();
        });
    }

    enterSpectatorMode() {
        this.isSpectator = true;
        const statusEl = document.getElementById('status');
        statusEl.textContent = 'Spectating';
        statusEl.style.color = GameColors.accent.blue; // A nice blue for spectator mode
        document.getElementById('player-config').style.display = 'none';
        document.getElementById('yourTeam').parentElement.style.display = 'none';
        document.getElementById('spectatorBtn').style.display = 'none'; // Hide spectator button when already spectating
        this.canvas.style.cursor = 'default';
    }

    pollGamepad() {
        const gamepads = navigator.getGamepads();
        const firstGamepad = gamepads[0] || gamepads[1] || gamepads[2] || gamepads[3];

        if (firstGamepad) {
            this.gamepad = firstGamepad;

            // Update overlay with gamepad events
            if (window.gameInfoOverlay) {
                window.gameInfoOverlay.updateGamepadEvents(this.gamepad);
            }

            const stickMoved = Math.abs(this.gamepad.axes[0]) > 0.1 ||
                               Math.abs(this.gamepad.axes[1]) > 0.1 ||
                               Math.abs(this.gamepad.axes[2]) > 0.1 ||
                               Math.abs(this.gamepad.axes[3]) > 0.1;
            const buttonPressed = this.gamepad.buttons.some(b => b.pressed);
            this.isGamepadActive = stickMoved || buttonPressed;

            const leftStickX = this.gamepad.axes[0];
            const leftStickY = this.gamepad.axes[1];
            const deadzone = 0.25;
            const moveMagnitude = Math.sqrt(leftStickX * leftStickX + leftStickY * leftStickY);

            if (moveMagnitude > deadzone) {
                this.gamepadMoveDir.x = leftStickX / moveMagnitude;
                this.gamepadMoveDir.y = leftStickY / moveMagnitude;
            } else {
                this.gamepadMoveDir.x = 0;
                this.gamepadMoveDir.y = 0;
            }
        } else {
            this.gamepad = null;
            this.isGamepadActive = false;
            this.gamepadMoveDir.x = 0;
            this.gamepadMoveDir.y = 0;
        }
    }

    sendInput() {
        if (this.isSpectator || this.isLocalPlayerDead || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }

        const input = {
            moveX: 0,
            moveY: 0,
            fire: false,
            altFire: false,
            mouseX: 0,
            mouseY: 0,
            reload: false,
            action1: false,
            action2: false,
        };
        const deadzone = 0.25;

        if (this.isGamepadActive && this.gamepad) {
            const leftStickX = this.gamepad.axes[0];
            const leftStickY = this.gamepad.axes[1];
            const rightStickX = this.gamepad.axes[2];
            const rightStickY = this.gamepad.axes[3];

            if (Math.abs(leftStickX) > deadzone) input.moveX = leftStickX;
            if (Math.abs(leftStickY) > deadzone) input.moveY = leftStickY;

            const moveMagnitude = Math.sqrt(input.moveX * input.moveX + input.moveY * input.moveY);
            if (moveMagnitude > 1.0) {
                input.moveX /= moveMagnitude;
                input.moveY /= moveMagnitude;
            }

            input.fire = this.gamepad.buttons[7] && this.gamepad.buttons[7].value > 0.5;
            input.altFire = this.gamepad.buttons[6] && this.gamepad.buttons[6].value > 0.5; // L2/LT button
            input.reload = this.gamepad.buttons[2] && this.gamepad.buttons[2].pressed;
            input.action1 = this.gamepad.buttons[5] && this.gamepad.buttons[5].pressed; // R1/RB button
            input.action2 = this.gamepad.buttons[0] && this.gamepad.buttons[0].pressed; // A/X button

            const swapButtonPressed = this.gamepad.buttons[3] && this.gamepad.buttons[3].pressed; // Y or Triangle
            if (swapButtonPressed && !this.gamepadWeaponSwapPressed) {
                if (this.localPlayer && this.localPlayer.weapon && this.weaponNames.length > 0) {
                    const currentWeaponName = this.localPlayer.weapon.name;
                    const currentIndex = this.weaponNames.indexOf(currentWeaponName);
                    if (currentIndex !== -1) {
                        const nextIndex = (currentIndex + 1) % this.weaponNames.length;
                        this.sendConfigChange(this.weaponNames[nextIndex]);
                    }
                }
            }
            this.gamepadWeaponSwapPressed = swapButtonPressed;

            const aimMagnitude = Math.sqrt(rightStickX * rightStickX + rightStickY * rightStickY);
            if (aimMagnitude > deadzone) {
                this.gamepadAim.x = rightStickX / aimMagnitude;
                this.gamepadAim.y = rightStickY / aimMagnitude;
            }

            if (this.localPlayer) {
                input.mouseX = this.localPlayer.x + this.gamepadAim.x * 100;
                input.mouseY = this.localPlayer.y + this.gamepadAim.y * 100;
            }
        } else {
            let moveX = 0;
            let moveY = 0;
            if (this.keys['KeyW'] || this.keys['ArrowUp']) moveY = -1;
            if (this.keys['KeyS'] || this.keys['ArrowDown']) moveY = 1;
            if (this.keys['KeyA'] || this.keys['ArrowLeft']) moveX = -1;
            if (this.keys['KeyD'] || this.keys['ArrowRight']) moveX = 1;

            const magnitude = Math.sqrt(moveX * moveX + moveY * moveY);
            if (magnitude > 0) {
                input.moveX = moveX / magnitude;
                input.moveY = moveY / magnitude;
            }

            input.fire = this.mouse.down;
            input.altFire = !!this.keys['KeyQ'];
            input.mouseX = this.mouse.x;
            input.mouseY = this.mouse.y;
            input.reload = !!this.keys['KeyR'];
            input.action1 = !!this.keys['KeyE'];
            input.action2 = !!this.keys['KeyF'];
        }
        this.ws.send(JSON.stringify(input));
    }

    sendConfigChange(weaponNameToSet) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }
        const name = document.getElementById('nameInput').value;
        const weaponName = weaponNameToSet || document.getElementById('weaponSelector').value;

        localStorage.setItem('name', name);
        localStorage.setItem('weaponName', weaponName);
        const message = {
            type: 'configChange',
            name: name,
            weaponName: weaponName
        };
        this.ws.send(JSON.stringify(message));
        console.log(`Sent player config: Name=${name}, Weapon=${weaponName}`);
    }

    sendTeamChangeRequest() {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }
        const message = {
            type: 'configChange',
            requestTeamChange: true
        };
        this.ws.send(JSON.stringify(message));
        console.log('Sent team change request.');
    }

    switchToSpectator() {
        if (this.gameId) {
            // Redirect to spectator mode for the same game
            window.location.href = `/game.html?spectate=true&gameId=${this.gameId}`;
        } else {
            // If no gameId, just go back to lobby
            window.location.href = '/';
        }
    }

    loadName() {
        const savedName = localStorage.getItem('name');
        if (savedName) {
            document.getElementById('nameInput').value = savedName;
        }
    }

    populateWeaponSelector(weaponNames) {
        const selector = document.getElementById('weaponSelector');
        if (!selector) return;

        this.weaponNames = weaponNames;
        selector.innerHTML = '';

        weaponNames.forEach(name => {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name;
            selector.appendChild(option);
        });

        const savedWeapon = localStorage.getItem('weaponName');
        if (savedWeapon && weaponNames.includes(savedWeapon)) {
            selector.value = savedWeapon;
        }

        if (weaponNames.length > 0) {
            this.sendConfigChange();
        }
    }

    preRenderBackground() {
        const bCtx = this.backgroundCtx;
        bCtx.fillStyle = GameColors.background.secondary;
        bCtx.clearRect(0, 0, this.backgroundCanvas.width, this.backgroundCanvas.height);

        bCtx.save();
        bCtx.strokeStyle = GameColors.overlays.gridLines;
        bCtx.lineWidth = 2;
        bCtx.setLineDash([15, 15]);
        bCtx.beginPath();
        bCtx.moveTo(this.backgroundCanvas.width / 2, 0);
        bCtx.lineTo(this.backgroundCanvas.width / 2, this.backgroundCanvas.height);
        bCtx.stroke();
        bCtx.restore();

        if (this.obstacles) {
            this.obstacles.forEach(obstacle => {
                if (!obstacle.vertices || obstacle.vertices.length < 3 || !obstacle.rendered) {
                   return;
                }
                bCtx.save();
                const cornerRadius = 4;
                bCtx.lineJoin = 'round';
                bCtx.lineWidth = cornerRadius * 2;
                bCtx.fillStyle = GameColors.entities.gameObjects.obstacles;
                bCtx.strokeStyle = GameColors.entities.gameObjects.obstacles;

                bCtx.beginPath();
                bCtx.moveTo(obstacle.vertices[0].x || 0, obstacle.vertices[0].y || 0);
                for (let i = 1; i < obstacle.vertices.length; i++) {
                    bCtx.lineTo(obstacle.vertices[i].x || 0, obstacle.vertices[i].y || 0);
                }
                bCtx.closePath();
                bCtx.stroke();
                bCtx.fill();
                bCtx.restore();
            });
        }
    }

    render(currentTime) {
        if (this.backgroundNeedsRedraw && this.gameState.info) {
            this.preRenderBackground();
            this.backgroundNeedsRedraw = false;
        }

        this.ctx.setTransform(1, 0, 0, 1, 0, 0)
        this.ctx.clearRect(0, 0, this.backgroundCanvas.width, this.backgroundCanvas.height);
        this.ctx.drawImage(this.backgroundCanvas, 0, 0);

        this.drawCrates();
        this.drawCratePreview();
        this.drawBase();
        this.drawFieldEffects();

        // Get interpolated positions for smooth rendering
        const interpolated = this.getInterpolatedPositions(currentTime);
        // Store interpolation factor for debug display
        this.lastInterpolationFactor = this.lastUpdateTime
            ? Math.min(Math.max((currentTime - this.lastUpdateTime) / this.interpolationTime, 0.0), 1.0)
            : 0;

        this.drawBullets(interpolated.bullets);
        this.drawLaserBlasts();
        interpolated.players.forEach(player => this.drawPlayer(player));
        this.drawPowerUps();
        this.drawWeaponUpgrades();
        this.drawVehicles();
        this.drawHill();
        this.drawMotorPools();
        this.drawFlags();
        this.drawOddball();
        this.drawPayload();
        this.drawGameEvents();
        this.drawDebugInfo(currentTime);
        this.drawRespawnOverlay();
    }

    findPlayer(playerId) {
        return this.gameState.players.find(player => player.id === playerId);
    }

    drawPowerUps() {
        if (!this.gameState.powerUps) {
            return;
        }

        this.ctx.save();
        this.ctx.font = '20px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';

        this.gameState.powerUps.forEach(powerUp => {
            const radius = 15;
            const centerX = powerUp.position.x;
            const centerY = powerUp.position.y;

            // Draw pickup radius circle
            this.ctx.strokeStyle = GameColors.entities.powerUps.clear;
            this.ctx.setLineDash([5, 5]);
            this.ctx.lineWidth = 1;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, radius, 0, 2 * Math.PI);
            this.ctx.stroke();

            // Draw power-up icon
            this.ctx.setLineDash([]);
            this.ctx.fillStyle = GameColors.entities.powerUps.clear;
            this.ctx.fillText(powerUp.icon, centerX, centerY + 4);
        });

        this.ctx.restore();
    }

    drawWeaponUpgrades() {
        const gameInfo = this.gameState.info;
        if (!gameInfo || gameInfo.type !== 'Progression' || !gameInfo.weaponUpgrades) {
            return;
        }

        this.ctx.save();
        this.ctx.font = '14px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';

        gameInfo.weaponUpgrades.forEach(weaponUpgrade => {
            if (!weaponUpgrade.position) {
                return;
            }

            const centerX = weaponUpgrade.position.x;
            const centerY = weaponUpgrade.position.y;
            const radius = weaponUpgrade.radius;
            const currentTime = this.gameState.serverTime || Date.now();

            // Create pulsing effect
            const pulseRate = 1500; // 1.5 seconds per pulse
            const pulsePhase = (currentTime % pulseRate) / pulseRate;
            const pulseBrightness = 0.6 + Math.sin(pulsePhase * Math.PI * 2) * 0.3;
            const pulseRadius = radius + Math.sin(pulsePhase * Math.PI * 2) * 3;

            // Draw pickup radius circle with pulsing effect
            this.ctx.strokeStyle = `rgba(255, 215, 0, ${pulseBrightness})`;
            this.ctx.setLineDash([5, 5]);
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, pulseRadius, 0, 2 * Math.PI);
            this.ctx.stroke();

            // Draw weapon icon background
            this.ctx.setLineDash([]);
            this.ctx.fillStyle = 'rgba(50, 50, 50, 0.8)';
            this.ctx.strokeStyle = 'rgba(255, 215, 0, 0.9)';
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, radius * 0.7, 0, 2 * Math.PI);
            this.ctx.fill();
            this.ctx.stroke();

            // Draw weapon upgrade icon - double chevron pointing up
            this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
            this.ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
            this.ctx.lineWidth = 2;
            this.ctx.lineJoin = 'round';
            this.ctx.lineCap = 'round';
            
            // Draw first (top) chevron
            this.ctx.beginPath();
            this.ctx.moveTo(centerX - 6, centerY - 2);
            this.ctx.lineTo(centerX, centerY - 8);
            this.ctx.lineTo(centerX + 6, centerY - 2);
            this.ctx.stroke();
            
            // Draw second (bottom) chevron
            this.ctx.beginPath();
            this.ctx.moveTo(centerX - 6, centerY + 4);
            this.ctx.lineTo(centerX, centerY - 2);
            this.ctx.lineTo(centerX + 6, centerY + 4);
            this.ctx.stroke();

            // Draw time remaining indicator
            if (weaponUpgrade.timeRemaining !== undefined) {
                const timeLeft = weaponUpgrade.timeRemaining;
                if (timeLeft <= 10) { // Show countdown for last 10 seconds
                    this.ctx.fillStyle = timeLeft <= 5 ? 'rgba(255, 100, 100, 0.9)' : 'rgba(255, 200, 100, 0.9)';
                    this.ctx.font = 'bold 12px Arial';
                    this.ctx.fillText(`${timeLeft}s`, centerX, centerY - radius - 10);
                }
            }
        });

        this.ctx.restore();
    }

    drawCrates() {
        if (!this.gameState.info || !this.gameState.info.crates) {
            return;
        }

        this.ctx.save();
        this.gameState.info.crates.forEach(crate => {
            // Draw crate body
            this.ctx.fillStyle = GameColors.entities.gameObjects.crates.body;
            this.ctx.strokeStyle = GameColors.entities.gameObjects.crates.border;
            this.ctx.lineWidth = 2;
            this.ctx.fillRect(crate.x || 0, crate.y || 0, crate.size, crate.size);
            this.ctx.strokeRect(crate.x || 0, crate.y || 0, crate.size, crate.size);

            // Draw health bar if damaged
            if (crate.hp < crate.maxHp) {
                const healthPercentage = Math.max(0, crate.hp / crate.maxHp);
                const barWidth = crate.size;
                const barHeight = 5;
                const barX = crate.x || 0;
                const barY = (crate.y || 0) - 10;

                // Background of health bar
                this.ctx.fillStyle = GameColors.health.background;
                this.ctx.fillRect(barX, barY, barWidth, barHeight);

                // Foreground of health bar
                this.ctx.fillStyle = healthPercentage > 0.5 ? GameColors.health.high : (healthPercentage > 0.2 ? GameColors.health.medium : GameColors.health.low);
                this.ctx.fillRect(barX, barY, barWidth * healthPercentage, barHeight);

                // Border for the health bar
                this.ctx.strokeStyle = GameColors.health.border;
                this.ctx.lineWidth = 1;
                this.ctx.strokeRect(barX, barY, barWidth, barHeight);
            }
        });
        this.ctx.restore();
    }

    drawCratePreview() {
        // Only show preview in Builder mode and when not spectating
        if (!this.gameState.info ||
            this.gameState.info.type !== 'Builder' ||
            this.isSpectator ||
            this.isLocalPlayerDead) {
            return;
        }

        const CRATE_SIZE = 30.0; // Match the server-side constant
        const PLAYER_RADIUS = 10.0;
        const PLACEMENT_DISTANCE = CRATE_SIZE * 2;

        if (!this.localPlayer) return;

        // Calculate placement position
        const dx = this.localPlayer.mouseX - this.localPlayer.x;
        const dy = this.localPlayer.mouseY - this.localPlayer.y;
        const length = Math.sqrt(dx * dx + dy * dy);
        const baseAngle = (length > 0.1) ? Math.atan2(dy, dx) : 0.0;

        // Calculate the ideal placement location
        const idealX = this.localPlayer.x + PLAYER_RADIUS - (CRATE_SIZE / 2.0) +
                      Math.cos(baseAngle) * PLACEMENT_DISTANCE;
        const idealY = this.localPlayer.y + PLAYER_RADIUS - (CRATE_SIZE / 2.0) +
                      Math.sin(baseAngle) * PLACEMENT_DISTANCE;

        // Snap to grid
        const snappedX = Math.round(idealX / CRATE_SIZE) * CRATE_SIZE;
        const snappedY = Math.round(idealY / CRATE_SIZE) * CRATE_SIZE;

        // Draw the preview
        this.ctx.save();
        this.ctx.strokeStyle = GameColors.overlays.previewStroke;
        this.ctx.fillStyle = GameColors.overlays.preview;
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([5, 5]);

        this.ctx.beginPath();
        this.ctx.rect(snappedX, snappedY, CRATE_SIZE, CRATE_SIZE);
        this.ctx.fill();
        this.ctx.stroke();

        this.ctx.restore();
    }

    drawFieldEffects() {
        if (!this.gameState.fieldEffects) return;

        this.ctx.save();
        this.gameState.fieldEffects.forEach(effect => {
            if(effect.type == 'EXPLOSION') {
                this.drawExplosion(effect);
            } else if (effect.type == 'POISON') {
                this.drawPoisonCloud(effect);
            } else if (effect.type == 'SMOKE') {
                this.drawSmokeField(effect);
            } else if (effect.type == 'MINE') {
                this.drawMine(effect);
            } else if (effect.type == 'GRAVITY_WELL') {
                this.drawGravityWell(effect);
            } else if (effect.type == 'TURRET') {
                this.drawTurret(effect);
            } else if (effect.type == 'GRID_POINT') {
                this.drawGridPoint(effect);
            } else if (effect.type == 'PORTAL') {
                this.drawPortal(effect);
            }
        });
    }

    drawExplosion(explosion) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return; // Don't draw if we don't have a server time yet

        // TODO: better math that doesn't rely on lifetime
        const lifetime = 300; // All explosions last for .3 seconds.
        const timeRemaining = explosion.expiration - now;

        if (timeRemaining < 0 || timeRemaining > lifetime) return;

        const progress = 1 - (timeRemaining / lifetime); // 0.0 to 1.0

        // Animate the radius to expand quickly
        const peakTime = 0.3; // Time to reach max size
        const radiusProgress = Math.min(1.0, progress / peakTime);
        const currentRadius = explosion.radius * radiusProgress;

        // Fade out over the whole duration
        const alpha = 1.0 - progress;

        const gradient = this.ctx.createRadialGradient(
            explosion.x, explosion.y, 0,
            explosion.x, explosion.y, currentRadius
        );
        gradient.addColorStop(0, GameColors.effects.explosion.core.replace('0.9', (alpha * 0.9).toString()));
        gradient.addColorStop(0.5, GameColors.effects.explosion.middle.replace('0.7', (alpha * 0.7).toString()));
        gradient.addColorStop(1, GameColors.effects.explosion.edge); // Fade to fully transparent at the edge

        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(explosion.x, explosion.y, currentRadius, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.restore();
    }

    drawPoisonCloud(cloud) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) {
            return;
        }

        // TODO: better math that doesn't rely on lifetime
        const lifetime = 5000; // All poison clouds last for 5 seconds.
        const timeRemaining = cloud.expiration - now;

        if (timeRemaining < 0 || timeRemaining > lifetime) return;

        const progress = 1 - (timeRemaining / lifetime); // 0.0 to 1.0

        // Let the cloud expand to full size then linger
        const expandTime = 0.2; // 20% of lifetime to expand
        const radiusProgress = Math.min(1.0, progress / expandTime);
        const currentRadius = cloud.radius * radiusProgress;

        // Fade out over the whole duration
        const alpha = (1.0 - progress) * 0.6; // Max alpha 0.6

        const gradient = this.ctx.createRadialGradient(
            cloud.x, cloud.y, 0,
            cloud.x, cloud.y, currentRadius
        );
        gradient.addColorStop(0, `rgba(129, 199, 132, ${alpha})`); // Light green
        gradient.addColorStop(0.7, `rgba(56, 142, 60, ${alpha * 0.8})`); // Darker green
        gradient.addColorStop(1, `rgba(27, 94, 32, 0)`); // Fade to transparent

        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(cloud.x, cloud.y, currentRadius, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.restore();
    }

    drawSmokeField(field) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return;

        // Smoke fields last for 8 seconds
        const lifetime = 5000;
        const timeRemaining = field.expiration - now;

        if (timeRemaining < 0 || timeRemaining > lifetime) return;

        const progress = 1 - (timeRemaining / lifetime);

        // Quick expansion to full size
        const expandTime = 0.15;
        const radiusProgress = Math.min(1.0, progress / expandTime);
        const currentRadius = field.radius * radiusProgress;

        // Fade in quickly, then slowly fade out
        const fadeInTime = 0.1;
        const alpha = (1.0 - progress) * 0.5;

        // Create a smoky gradient
        const gradient = this.ctx.createRadialGradient(
            field.x, field.y, 0,
            field.x, field.y, currentRadius
        );
        gradient.addColorStop(0, `rgba(180, 180, 180, ${alpha})`);     // Light grey center
        gradient.addColorStop(0.4, `rgba(120, 120, 120, ${alpha * 0.8})`); // Medium grey
        gradient.addColorStop(0.7, `rgba(80, 80, 80, ${alpha * 0.6})`);    // Darker grey
        gradient.addColorStop(1, 'rgba(60, 60, 60, 0)');              // Fade to transparent

        // Draw main smoke cloud
        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(field.x, field.y, currentRadius, 0, Math.PI * 2);
        this.ctx.fill();

        // Add swirling effect
        const time = now / 1000;
        const swirls = 3;
        this.ctx.strokeStyle = `rgba(160, 160, 160, ${alpha * 0.3})`;
        this.ctx.lineWidth = 8;
        this.ctx.setLineDash([10, 15]);

        for (let i = 0; i < swirls; i++) {
            const swirl = (time * 0.4 + i * (Math.PI * 2 / swirls)) % (Math.PI * 2);
            const radius = currentRadius * (0.3 + (i * 0.2));

            this.ctx.beginPath();
            this.ctx.arc(field.x, field.y, radius, swirl, swirl + Math.PI * 1.2);
            this.ctx.stroke();
        }

        this.ctx.restore();
    }

    drawMine(field) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return;

        // Set colors based on team
        const teamColors = {
            1: { base: GameColors.teams.team1.primary, dark: GameColors.teams.team1.dark }, // Green for team 1
            2: { base: GameColors.teams.team2.primary, dark: GameColors.teams.team2.dark }  // Red for team 2
        };
        const colors = teamColors[field.team] || GameColors.special.mine.neutral;

        // Draw main disc
        this.ctx.beginPath();
        this.ctx.arc(field.x, field.y, 10, 0, Math.PI * 2);
        this.ctx.fillStyle = colors.base;
        this.ctx.fill();
        this.ctx.lineWidth = 2;
        this.ctx.strokeStyle = colors.dark;
        this.ctx.stroke();

        // Draw exclamation mark
        this.ctx.fillStyle = 'white';
        this.ctx.font = 'bold 16px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.ctx.fillText('!', field.x, field.y);

        // Add pulsing highlight effect
        const pulseRate = 1000; // 1 second per pulse
        const pulsePhase = (now % pulseRate) / pulseRate;
        const pulseOpacity = 0.3 + Math.sin(pulsePhase * Math.PI * 2) * 0.1;

        this.ctx.beginPath();
        this.ctx.arc(field.x, field.y, field.radius, 0, Math.PI * 2);
        this.ctx.strokeStyle = `rgba(255, 255, 255, ${pulseOpacity})`;
        this.ctx.lineWidth = 1;
        this.ctx.stroke();

        this.ctx.restore();
    }

    drawGravityWell(gravityWell) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return;

        // Gravity wells last for 8 seconds
        const lifetime = 8000;
        const timeRemaining = gravityWell.expiration - now;

        if (timeRemaining < 0 || timeRemaining > lifetime) return;

        const progress = 1 - (timeRemaining / lifetime); // 0.0 to 1.0

        // Quick expansion to full size
        const expandTime = 0.1;
        const radiusProgress = Math.min(1.0, progress / expandTime);
        const currentRadius = gravityWell.radius * radiusProgress;

        // Fade in quickly, then slowly fade out
        const alpha = (1.5 - progress) * 0.8; // Max alpha 0.8

        // Create a gravity field gradient - darker center, purple/blue tones
        const gradient = this.ctx.createRadialGradient(
            gravityWell.x, gravityWell.y, 0,
            gravityWell.x, gravityWell.y, currentRadius
        );

        // Use dark purple/violet colors to suggest gravitational distortion
        gradient.addColorStop(0, `rgba(75, 0, 130, ${alpha})`);         // Indigo center (strong gravity)
        gradient.addColorStop(0.3, `rgba(138, 43, 226, ${alpha * 0.8})`); // Blue violet
        gradient.addColorStop(0.6, `rgba(148, 0, 211, ${alpha * 0.6})`);  // Dark violet
        gradient.addColorStop(1, 'rgba(25, 25, 112, 0)');               // Fade to transparent

        // Draw main gravity field
        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(gravityWell.x, gravityWell.y, currentRadius, 0, Math.PI * 2);
        this.ctx.fill();

        // Add swirling gravitational distortion effect
        const time = now / 1000;
        const spirals = 4;
        this.ctx.strokeStyle = `rgba(138, 43, 226, ${alpha * 0.4})`;
        this.ctx.lineWidth = 3;
        this.ctx.setLineDash([8, 12]);

        for (let i = 0; i < spirals; i++) {
            const spiralTime = time * 1.5 + i * (Math.PI * 2 / spirals);
            const spiralRadius = currentRadius * (0.2 + (i * 0.2));

            // Create inward spiral to show gravitational pull
            this.ctx.beginPath();
            let lastX = null, lastY = null;

            for (let t = 0; t <= Math.PI * 3; t += 0.2) {
                const radius = spiralRadius * (1 - t / (Math.PI * 3)) * 0.8;
                const angle = spiralTime + t;
                const x = gravityWell.x + Math.cos(angle) * radius;
                const y = gravityWell.y + Math.sin(angle) * radius;

                if (lastX !== null) {
                    this.ctx.moveTo(lastX, lastY);
                    this.ctx.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }
            this.ctx.stroke();
        }

        // Add pulsing center to show the gravitational core
        const pulseRate = 800; // Faster pulse for gravity effect
        const pulsePhase = (now % pulseRate) / pulseRate;
        const pulseBrightness = 0.6 + Math.sin(pulsePhase * Math.PI * 2) * 0.3;

        this.ctx.setLineDash([]);
        this.ctx.fillStyle = `rgba(138, 43, 226, ${alpha * pulseBrightness})`;
        this.ctx.beginPath();
        this.ctx.arc(gravityWell.x, gravityWell.y, 8 + Math.sin(pulsePhase * Math.PI * 2) * 3, 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.restore();
    }

    drawTurret(turret) {
        this.ctx.save();
        const turretX = turret.x;
        const turretY = turret.y;
        const turretRadius = turret.radius;

        this.ctx.save();
        this.ctx.translate(turretX, turretY);

        // Draw tripod legs
        this.ctx.strokeStyle = GameColors.special.turrets.legs;
        this.ctx.lineWidth = 4;
        for (let i = 0; i < 3; i++) {
            const legAngle = (i * 2 * Math.PI / 3) + Math.PI / 2;
            this.ctx.beginPath();
            this.ctx.moveTo(0, 0);
            this.ctx.lineTo(Math.cos(legAngle) * turretRadius, Math.sin(legAngle) * turretRadius);
            this.ctx.stroke();
        }

        // Draw base with team color
        const teamColor = turret.team === 1 ? GameColors.teams.team1.primary : GameColors.teams.team2.primary;
        const darkTeamColor = turret.team === 1 ? GameColors.teams.team1.dark : GameColors.teams.team2.dark;
        this.ctx.fillStyle = teamColor;
        this.ctx.strokeStyle = darkTeamColor;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.arc(0, 0, turretRadius * 0.6, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.stroke();

        // Draw rotating barrel
        this.ctx.rotate(turret.angle);
        this.ctx.fillStyle = GameColors.special.turrets.barrel;
        this.ctx.strokeStyle = GameColors.special.turrets.barrelStroke;
        this.ctx.lineWidth = 1;
        this.ctx.fillRect(0, -4, turretRadius * 1.2, 8);
        this.ctx.strokeRect(0, -4, turretRadius * 1.2, 8);

        this.ctx.restore(); // Restore from translate/rotate

        // Draw health bar
        if (turret.hp < turret.maxHp) {
            const healthPercentage = Math.max(0, turret.hp / turret.maxHp);
            const barWidth = turretRadius * 2;
            const barHeight = 5;
            const barX = turretX - turretRadius;
            const barY = turretY - turretRadius - 15;

            this.ctx.fillStyle = GameColors.health.background;
            this.ctx.fillRect(barX, barY, barWidth, barHeight);
            this.ctx.fillStyle = healthPercentage > 0.5 ? GameColors.health.high : (healthPercentage > 0.2 ? GameColors.health.medium : GameColors.health.low);
            this.ctx.fillRect(barX, barY, barWidth * healthPercentage, barHeight);
            this.ctx.strokeStyle = GameColors.health.border;
            this.ctx.lineWidth = 1;
            this.ctx.strokeRect(barX, barY, barWidth, barHeight);
        }
        this.ctx.restore();
    }

    drawGridPoint(gridPoint) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return;

        // Set colors based on team
        const teamColors = {
            1: { primary: GameColors.teams.team1.primary, secondary: GameColors.teams.team1.secondary },
            2: { primary: GameColors.teams.team2.primary, secondary: GameColors.teams.team2.secondary }
        };
        const colors = teamColors[gridPoint.team] || { primary: '#888888', secondary: '#666666' };

        const baseX = gridPoint.x;
        const baseY = gridPoint.y;
        const height = 15; // Height of the lightning rod
        const baseWidth = 8; // Width of the base
        const topWidth = 3; // Width of the tip

        // Draw the main rod body (trapezoid shape)
        this.ctx.fillStyle = colors.primary;
        this.ctx.beginPath();
        this.ctx.moveTo(baseX - baseWidth/2, baseY + height/2);  // Bottom left
        this.ctx.lineTo(baseX + baseWidth/2, baseY + height/2);  // Bottom right
        this.ctx.lineTo(baseX + topWidth/2, baseY - height/2);   // Top right
        this.ctx.lineTo(baseX - topWidth/2, baseY - height/2);   // Top left
        this.ctx.closePath();
        this.ctx.fill();

        // Draw the base platform
        this.ctx.fillStyle = colors.secondary;
        this.ctx.fillRect(baseX - baseWidth/2 - 2, baseY + height/2, baseWidth + 4, 3);

        // Draw the pointed tip
        this.ctx.fillStyle = '#FFFF88'; // Bright yellow tip
        this.ctx.beginPath();
        this.ctx.moveTo(baseX - topWidth/2, baseY - height/2);
        this.ctx.lineTo(baseX + topWidth/2, baseY - height/2);
        this.ctx.lineTo(baseX, baseY - height/2 - 4);
        this.ctx.closePath();
        this.ctx.fill();

        // Add energy crackling effect around the tip
        const time = now / 100; // Faster animation
        const crackleRadius = 8;
        
        for (let i = 0; i < 3; i++) {
            const angle = (time + i * Math.PI * 2 / 3) % (Math.PI * 2);
            const crackleX = baseX + Math.cos(angle) * crackleRadius;
            const crackleY = baseY - height/2 - 2 + Math.sin(angle) * 3;
            
            this.ctx.fillStyle = `rgba(255, 255, 136, ${0.3 + Math.sin(time + i) * 0.2})`;
            this.ctx.beginPath();
            this.ctx.arc(crackleX, crackleY, 1.5, 0, Math.PI * 2);
            this.ctx.fill();
        }

        // Draw team indicator ring around the base
        this.ctx.strokeStyle = colors.primary;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.arc(baseX, baseY, gridPoint.radius || 12, 0, Math.PI * 2);
        this.ctx.stroke();

        // Draw health bar if damaged
        if (gridPoint.hp && gridPoint.maxHp && gridPoint.hp < gridPoint.maxHp) {
            const healthPercentage = Math.max(0, gridPoint.hp / gridPoint.maxHp);
            const barWidth = baseWidth + 4; // Match the base platform width
            const barHeight = 4;
            const barX = baseX - barWidth / 2;
            const barY = baseY - height / 2 - 15; // Position above the grid point

            // Background of health bar
            this.ctx.fillStyle = GameColors.health.background;
            this.ctx.fillRect(barX, barY, barWidth, barHeight);

            // Foreground of health bar
            this.ctx.fillStyle = healthPercentage > 0.5 ? GameColors.health.high :
                               (healthPercentage > 0.2 ? GameColors.health.medium : GameColors.health.low);
            this.ctx.fillRect(barX, barY, barWidth * healthPercentage, barHeight);

            // Border for the health bar
            this.ctx.strokeStyle = GameColors.health.border;
            this.ctx.lineWidth = 1;
            this.ctx.strokeRect(barX, barY, barWidth, barHeight);
        }

        this.ctx.restore();
    }

    drawPortal(portal) {
        this.ctx.save();
        const now = this.gameState.serverTime;
        if (!now) return;

        // Set colors based on team
        const teamColors = {
            1: { primary: GameColors.teams.team1.primary, secondary: GameColors.teams.team1.secondary },
            2: { primary: GameColors.teams.team2.primary, secondary: GameColors.teams.team2.secondary }
        };
        const colors = teamColors[portal.team] || { primary: '#888888', secondary: '#666666' };

        const centerX = portal.x;
        const centerY = portal.y;
        const radius = portal.radius;
        const time = now / 1000; // Convert to seconds for smoother animation

        // Draw the inner portal ring
        this.ctx.strokeStyle = colors.secondary;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.arc(centerX, centerY, radius * 0.7, 0, Math.PI * 2);
        this.ctx.stroke();

        // Draw swirling portal effect
        const spiralCount = 8;
        const spiralTime = time * 3; // Faster rotation
        
        this.ctx.strokeStyle = `${colors.primary}80`; // Semi-transparent
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([4, 4]);

        for (let i = 0; i < spiralCount; i++) {
            const spiralAngle = (i / spiralCount) * Math.PI * 2 + spiralTime;
            const spiralRadius = radius * 0.8;
            
            this.ctx.beginPath();
            let lastX = null, lastY = null;
            
            for (let t = 0; t <= Math.PI; t += 0.3) {
                const currentRadius = spiralRadius * (1 - t / Math.PI) * 0.8;
                const angle = spiralAngle + t * 2;
                const x = centerX + Math.cos(angle) * currentRadius;
                const y = centerY + Math.sin(angle) * currentRadius;
                
                if (lastX !== null) {
                    this.ctx.moveTo(lastX, lastY);
                    this.ctx.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }
            this.ctx.stroke();
        }

        // Draw the portal center with energy effect
        const energyAlpha = 0.3 + Math.sin(time * 4) * 0.2;
        const gradient = this.ctx.createRadialGradient(
            centerX, centerY, 0,
            centerX, centerY, radius * 0.5
        );
        gradient.addColorStop(0, `${colors.primary}${Math.floor(energyAlpha * 255).toString(16).padStart(2, '0')}`);
        gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

        this.ctx.setLineDash([]);
        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(centerX, centerY, radius * 0.5, 0, Math.PI * 2);
        this.ctx.fill();

        this.ctx.restore();
    }

    drawBullets(bullets){
        bullets?.forEach(bullet => {
            this.ctx.fillStyle = bullet.team === 1 ? GameColors.teams.team1.secondary : GameColors.teams.team2.secondary;
            this.ctx.beginPath();
            this.ctx.arc(bullet.x, bullet.y, 3, 0, 2 * Math.PI);
            this.ctx.fill();
        });
    }

    drawLaserBlasts() {
        if (!this.gameState.laserBlasts) return;

        this.ctx.save();
        this.gameState.laserBlasts.forEach(blast => {
            const teamColors = {
                1: [GameColors.laser.team1.core, GameColors.laser.team1.glow],
                2: [GameColors.laser.team2.core, GameColors.laser.team2.glow]
            };
            const [coreColor, glowColor] = teamColors[blast.team] || [GameColors.laser.neutral.core, GameColors.laser.neutral.glow];

            // Draw the outer glow
            this.ctx.beginPath();
            this.ctx.lineWidth = 8;
            this.ctx.strokeStyle = glowColor;
            this.ctx.moveTo(blast.start.x, blast.start.y);
            this.ctx.lineTo(blast.end.x, blast.end.y);
            this.ctx.stroke();

            // Draw the core beam
            this.ctx.beginPath();
            this.ctx.lineWidth = 2;
            this.ctx.strokeStyle = coreColor;
            this.ctx.moveTo(blast.start.x, blast.start.y);
            this.ctx.lineTo(blast.end.x, blast.end.y);
            this.ctx.stroke();

            // Add bright impact points at start and end
            const impactRadius = 3;
            this.ctx.beginPath();
            this.ctx.arc(blast.end.x, blast.end.y, impactRadius, 0, Math.PI * 2);
            this.ctx.fillStyle = coreColor;
            this.ctx.fill();
        });
        this.ctx.restore();
    }

    drawHill() {
        const gameInfo = this.gameState.info;
        
        // Handle King of the Hill mode (single hill)
        if (gameInfo && gameInfo.hill) {
            this.drawSingleHill(gameInfo.hill);
        }
        
        // Handle Blitz mode (multiple capture points)
        if (gameInfo && gameInfo.type === 'Blitz') {
            if (gameInfo.team1CapturePoints) {
                gameInfo.team1CapturePoints.forEach(hill => this.drawSingleHill(hill));
            }
            if (gameInfo.team2CapturePoints) {
                gameInfo.team2CapturePoints.forEach(hill => this.drawSingleHill(hill));
            }
        }
    }

    drawSingleHill(hill, label = null) {
        const pos = hill.position;
        const radius = hill.radius;

        this.ctx.save();
        this.ctx.globalAlpha = 0.25;

        if (hill.contested) {
            this.ctx.fillStyle = GameColors.special.hill.contested;
        } else if (hill.controllingTeam === 1) {
            this.ctx.fillStyle = GameColors.teams.team1.primary;
        } else if (hill.controllingTeam === 2) {
            this.ctx.fillStyle = GameColors.teams.team2.primary;
        } else {
            this.ctx.fillStyle = GameColors.special.hill.neutral;
        }

        this.ctx.beginPath();
        this.ctx.arc(pos.x, pos.y, radius, 0, 2 * Math.PI);
        this.ctx.fill();

        this.ctx.globalAlpha = 0.8;
        this.ctx.lineWidth = 3;
        this.ctx.strokeStyle = this.ctx.fillStyle;
        this.ctx.stroke();

        this.ctx.globalAlpha = 1.0;

        this.ctx.strokeStyle = GameColors.special.hill.flag;
        this.ctx.lineWidth = 3;
        this.ctx.beginPath();
        this.ctx.moveTo(pos.x, pos.y + 15);
        this.ctx.lineTo(pos.x, pos.y - 25);
        this.ctx.stroke();

        this.ctx.fillStyle = this.ctx.strokeStyle;
        this.ctx.beginPath();
        this.ctx.moveTo(pos.x + 2, pos.y - 25);
        this.ctx.lineTo(pos.x + 22, pos.y - 18);
        this.ctx.lineTo(pos.x + 2, pos.y - 11);
        this.ctx.closePath();
        this.ctx.fill();

        this.ctx.restore();
    }

    drawMotorPools() {
        const gameInfo = this.gameState.info;
        if (!gameInfo || (!gameInfo.motorPools || (gameInfo.type !== 'Base Destruction'))) {
            return;
        }

        gameInfo.motorPools.forEach(motorPool => {
            this.drawSingleMotorPool(motorPool, gameInfo);
        });
    }

    drawSingleMotorPool(motorPool, gameInfo) {
        const pos = motorPool.position;
        const radius = motorPool.radius;
        const currentTime = this.gameState.serverTime || Date.now();

        this.ctx.save();

        // Determine colors based on control state
        let fillColor, strokeColor, fillAlpha = 0.25, strokeAlpha = 0.8;

        if (motorPool.contested) {
            fillColor = GameColors.special.hill.contested;
            strokeColor = '#FF8F00';
        } else if (motorPool.controllingTeam === 1) {
            fillColor = GameColors.teams.team1.primary;
            strokeColor = GameColors.teams.team1.secondary;
        } else if (motorPool.controllingTeam === 2) {
            fillColor = GameColors.teams.team2.primary;
            strokeColor = GameColors.teams.team2.secondary;
        } else {
            fillColor = GameColors.special.hill.neutral;
            strokeColor = '#616161';
        }

        // Draw main motor pool area
        this.ctx.globalAlpha = fillAlpha;
        this.ctx.fillStyle = fillColor;
        this.ctx.beginPath();
        this.ctx.arc(pos.x, pos.y, radius, 0, 2 * Math.PI);
        this.ctx.fill();

        // Draw control progress ring if being captured
        if (!motorPool.contested && motorPool.controllingTeam > 0) {
            // Calculate control progress based on time elapsed
            let progress = 0;
            if (motorPool.controlStartTime > 0 && motorPool.timeToControl > 0) {
                const elapsed = Math.max(0, currentTime - motorPool.controlStartTime);
                progress = Math.min(1.0, elapsed / motorPool.timeToControl);
            }
            if (progress > 0 && progress < 1) {
                this.ctx.globalAlpha = 0.6;
                this.ctx.strokeStyle = strokeColor;
                this.ctx.lineWidth = 8;
                this.ctx.beginPath();
                this.ctx.arc(pos.x, pos.y, radius + 5, -Math.PI / 2, -Math.PI / 2 + (progress * 2 * Math.PI));
                this.ctx.stroke();
            }
        }

        // Draw border
        this.ctx.globalAlpha = strokeAlpha;
        this.ctx.lineWidth = 3;
        this.ctx.strokeStyle = strokeColor;
        this.ctx.beginPath();
        this.ctx.arc(pos.x, pos.y, radius, 0, 2 * Math.PI);
        this.ctx.stroke();

        this.ctx.globalAlpha = 1.0;

        // Draw motor pool icon - mechanical gear-like symbol
        this.ctx.font = '20px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.ctx.fillText('⚙️', pos.x, pos.y);
    }

    drawOddball() {
        const gameInfo = this.gameState.info;
        if (!gameInfo || gameInfo.type !== 'Oddball' || !gameInfo.oddball) {
            return;
        }

        const oddball = gameInfo.oddball;

        if (oddball.state === 'CARRIED') {
            return;
        }

        const pos = oddball.position;
        this.ctx.save();

        const pulse = Math.abs(Math.sin(this.gameState.serverTime / 200));
        this.ctx.fillStyle = '#FFD700';
        this.ctx.strokeStyle = 'white';
        this.ctx.lineWidth = 2 + pulse * 2;
        this.ctx.globalAlpha = 0.7 + pulse * 0.3;

        this.ctx.beginPath();
        this.ctx.arc(pos.x, pos.y, 12, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.stroke();

        this.ctx.lineWidth = 3;
        this.ctx.strokeStyle = '#FFA000';
        for (let i = 0; i < 6; i++) {
            const angle = (i * Math.PI / 3) + (this.gameState.serverTime / 1000);
            this.ctx.beginPath();
            this.ctx.moveTo(pos.x, pos.y);
            this.ctx.lineTo(
                pos.x + Math.cos(angle) * 20,
                pos.y + Math.sin(angle) * 20
            );
            this.ctx.stroke();
        }

        this.ctx.restore();
    }

    drawFlags() {
        const gameInfo = this.gameState.info;
        if (!gameInfo || gameInfo.type !== 'Capture the Flag') {
            return;
        }

        if (gameInfo.team1Flag) {
            this.drawSingleFlag(gameInfo.team1Flag);
        }
        if (gameInfo.team2Flag) {
            this.drawSingleFlag(gameInfo.team2Flag);
        }
    }

    drawSingleFlag(flag) {
        if (flag.state === 'CARRIED') {
            return;
        }

        const pos = flag.position;
        const basePos = flag.basePosition;
        const teamColor = flag.team === 1 ? '#4CAF50' : '#F44336';

        this.ctx.save();

        if (flag.state === 'AT_BASE') {
            this.ctx.fillStyle = '#444';
            this.ctx.strokeStyle = '#666';
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            this.ctx.rect(basePos.x - 10, basePos.y - 10, 20, 20);
            this.ctx.fill();
            this.ctx.stroke();
        }

        if (flag.state === 'DROPPED') {
            const pulse = Math.abs(Math.sin(this.gameState.serverTime / 300));
            this.ctx.strokeStyle = teamColor;
            this.ctx.lineWidth = 2 + pulse * 2;
            this.ctx.globalAlpha = 0.5 + pulse * 0.4;
            this.ctx.beginPath();
            this.ctx.arc(pos.x, pos.y, 15 + pulse * 5, 0, 2 * Math.PI);
            this.ctx.stroke();
        }

        this.ctx.globalAlpha = 1.0;

        this.ctx.strokeStyle = '#616161';
        this.ctx.lineWidth = 4;
        this.ctx.beginPath();
        this.ctx.moveTo(pos.x, pos.y + 15);
        this.ctx.lineTo(pos.x, pos.y - 25);
        this.ctx.stroke();

        this.ctx.fillStyle = teamColor;
        this.ctx.beginPath();
        this.ctx.moveTo(pos.x + 2, pos.y - 25);
        this.ctx.lineTo(pos.x + 25, pos.y - 15);
        this.ctx.lineTo(pos.x + 2, pos.y - 5);
        this.ctx.closePath();
        this.ctx.fill();

        this.ctx.restore();
    }

    drawGameEvents() {
        const now = Date.now();
        // Filter out expired events in-place for performance
        let i = this.gameEvents.length;
        while (i--) {
            if (now >= this.gameEvents[i].expirationTime) {
                this.gameEvents.splice(i, 1);
            }
        }

        if (this.gameEvents.length === 0) {
            return;
        }

        this.ctx.save();
        this.ctx.textAlign = 'center';
        this.ctx.font = 'bold 18px Arial';
        this.ctx.shadowColor = 'black';
        this.ctx.shadowBlur = 5;

        const startY = 50;
        this.gameEvents.slice(-6).forEach((event, index) => {
            const yPos = startY + (index * 25);
            switch (event.type) {
                case 'GREEN':        this.ctx.fillStyle = GameColors.events.green; break;
                case 'RED':          this.ctx.fillStyle = GameColors.events.red; break;
                case 'YELLOW':       this.ctx.fillStyle = GameColors.events.yellow; break;
                case 'BLUE':         this.ctx.fillStyle = GameColors.events.blue; break;
                default: this.ctx.fillStyle = GameColors.events.default; break;
            }
            this.ctx.fillText(event.message, this.canvas.width / 2, yPos);
        });

        this.ctx.restore();
    }

    drawVehicles() {
        if (!this.gameState.vehicles) {
            return;
        }

        this.ctx.save();
        this.gameState.vehicles.forEach(vehicle => {
            if (vehicle.hp <= 0) {
                return;
            }

            const vehicleX = vehicle.x;
            const vehicleY = vehicle.y;

            // Get vehicle style
            const style = this.getVehicleStyle(vehicle.vehicleType);

            // Draw vehicle using its polygon shape (vertices are already rotated on server)
            this.drawVehiclePolygon(vehicle, style);

            // Draw cannon barrels for vehicles that have them
            this.drawVehicleCannon(vehicle, style);

            // Calculate bounding radius for UI elements
            const vehicleRadius = vehicle.radius;

            // Draw health bar
            if (vehicle.hp < vehicle.maxHp) {
                const healthPercentage = Math.max(0, vehicle.hp / vehicle.maxHp);
                const barWidth = vehicleRadius * 2.4; // Use radius * 2.4 instead of width * 1.2
                const barHeight = 6;
                const barX = vehicleX - barWidth / 2;
                const barY = vehicleY - vehicleRadius - 20;

                this.ctx.fillStyle = GameColors.health.background;
                this.ctx.fillRect(barX, barY, barWidth, barHeight);
                this.ctx.fillStyle = healthPercentage > 0.5 ? GameColors.health.high : (healthPercentage > 0.2 ? GameColors.health.medium : GameColors.health.low);
                this.ctx.fillRect(barX, barY, barWidth * healthPercentage, barHeight);
                this.ctx.strokeStyle = GameColors.health.border;
                this.ctx.lineWidth = 1;
                this.ctx.strokeRect(barX, barY, barWidth, barHeight);
            }

            // Draw vehicle name and passenger count
            this.ctx.fillStyle = GameColors.text.primary;
            this.ctx.font = '12px Arial';
            this.ctx.textAlign = 'center';
            this.ctx.textBaseline = 'bottom';
            const passengerCount = vehicle.passengerIds?.length || 0;
            const maxPassengers = vehicle.maxPassengers;
            this.ctx.fillText(`${vehicle.vehicleName} (${passengerCount}/${maxPassengers})`, vehicleX, vehicleY - vehicleRadius - 25);

            // Draw repair indicator if vehicle is being repaired
            if (this.gameState.info && 
                this.gameState.info.type === 'Base Destruction' &&
                this.gameState.info.motorPools) {
                const teamPool = this.gameState.info.motorPools.find(pool => pool.team === vehicle.team);
                if (teamPool) {
                    const distanceToPool = Math.sqrt(
                        Math.pow(vehicleX - teamPool.position.x, 2) +
                        Math.pow(vehicleY - teamPool.position.y, 2)
                    );

                    if (distanceToPool <= teamPool.radius && vehicle.hp < vehicle.maxHp) {
                        // Draw repair effect
                        const time = this.gameState.serverTime / 1000;
                        const sparkles = 6;

                        this.ctx.save();
                        this.ctx.fillStyle = GameColors.accent.gold;
                        this.ctx.font = '16px Arial';
                        this.ctx.textAlign = 'center';

                        for (let i = 0; i < sparkles; i++) {
                            const angle = (i * 2 * Math.PI / sparkles) + (time * 2);
                            const radius = vehicleRadius + 15 + Math.sin(time * 4) * 5;
                            const sparkleX = vehicleX + Math.cos(angle) * radius;
                            const sparkleY = vehicleY + Math.sin(angle) * radius;

                            this.ctx.fillText('✨', sparkleX, sparkleY);
                        }

                        // Draw "REPAIRING" text
                        this.ctx.fillStyle = GameColors.accent.gold;
                        this.ctx.font = 'bold 12px Arial';
                        this.ctx.fillText('REPAIRING', vehicleX, vehicleY + vehicleRadius + 35);
                        this.ctx.restore();
                    }
                }
            }

            // Draw interaction hint if player is nearby
            if (this.localPlayer && !this.localPlayer.dead && !this.localPlayer.vehicleId) {
                const distance = Math.sqrt(
                    Math.pow(vehicleX - this.localPlayer.x, 2) +
                    Math.pow(vehicleY - this.localPlayer.y, 2)
                );
                if (distance <= vehicle.radius) {
                    this.ctx.fillStyle = GameColors.overlays.interaction;
                    this.ctx.font = '14px Arial';
                    this.ctx.textAlign = 'center';
                    this.ctx.fillText('Press F/A to enter', vehicleX, vehicleY + vehicleRadius + 20);
                }
            }
        });
        this.ctx.restore();
    }

    drawVehiclePolygon(vehicle, style) {
        if (!vehicle.vertices || vehicle.vertices.length < 3) {
            return;
        }

        // Draw main vehicle body using vertices (already rotated on server)
        this.ctx.fillStyle = style.bodyColor;
        this.ctx.strokeStyle = style.strokeColor;
        this.ctx.lineWidth = 2;

        this.ctx.beginPath();
        // Use world coordinates directly - vertices are already properly positioned and rotated
        this.ctx.moveTo(vehicle.vertices[0].x, vehicle.vertices[0].y);
        for (let i = 1; i < vehicle.vertices.length; i++) {
            this.ctx.lineTo(vehicle.vertices[i].x, vehicle.vertices[i].y);
        }
        this.ctx.closePath();
        this.ctx.fill();
        this.ctx.stroke();

        // Draw team color band if vehicle is controlled
        this.drawTeamColorBand(vehicle, style);
    }

    drawVehicleCannon(vehicle, style) {
        if (!vehicle.mountedWeapons || vehicle.mountedWeapons.length === 0) {
            return;
        }

        // Draw each mounted weapon
        vehicle.mountedWeapons.forEach((mountedWeapon, index) => {
            this.drawSingleMountedWeapon(vehicle, mountedWeapon, style, index);
        });
    }

    drawSingleMountedWeapon(vehicle, mountedWeapon, style, weaponIndex) {
        // Use the server-provided current angle instead of calculating client-side
        const currentAngle = mountedWeapon.currentAngle || (vehicle.angle + mountedWeapon.defaultAngle);

        // Get weapon visual properties based on vehicle type and weapon index
        const weaponProps = this.getWeaponVisualProperties(vehicle, weaponIndex);

        // Draw traverse limit indicator (if limited traverse)
        if (mountedWeapon.maximumRadian < Math.PI * 2) {
            this.drawTraverseLimits(vehicle, mountedWeapon, weaponProps);
        }

        // Draw turret base
        this.drawTurretBase(vehicle, mountedWeapon, style, weaponProps);

        // Draw weapon barrel
        this.drawWeaponBarrel(mountedWeapon, currentAngle, weaponProps);
    }



    getWeaponVisualProperties(vehicle, weaponIndex) {
        const baseProps = {
            length: vehicle.radius * 1.0,
            width: 5,
            turretRadius: vehicle.radius * 0.2,
            barrelColor: '#2A2A2A',
            barrelStroke: '#1A1A1A'
        };

        switch (vehicle.vehicleType) {
            case 'TANK':
                if (weaponIndex === 0) {
                    // Main gun
                    return { ...baseProps, length: vehicle.radius * 1.2, width: 6, turretRadius: vehicle.radius * 0.4 };
                } else {
                    // Side machine guns
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 3, turretRadius: vehicle.radius * 0.15 };
                }
            case 'JEEP':
                // Machine gun
                return { ...baseProps, length: vehicle.radius * 0.8, width: 4, turretRadius: vehicle.radius * 0.15 };
            case 'FIXED_CANNON':
                // Artillery
                return { ...baseProps, length: vehicle.radius * 1.5, width: 8, turretRadius: vehicle.radius * 0.3 };
            case 'MECH':
                // Laser weapons
                return { ...baseProps, length: vehicle.radius * 0.6, width: 5, turretRadius: vehicle.radius * 0.1,
                        barrelColor: '#4A90E2', barrelStroke: '#2E5C8A' };
            case 'DAVINCI':
                // Pentagon vehicle with 5 different weapons at vertices
                if (weaponIndex === 0) {
                    // Driver - Rocket Launcher (front vertex)
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 5, turretRadius: vehicle.radius * 0.015,
                            barrelColor: '#D32F2F', barrelStroke: '#B71C1C' }; // Red for rockets
                } else if (weaponIndex === 1) {
                    // Sniper Rifle (front-right vertex)
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 2, turretRadius: vehicle.radius * 0.01,
                            barrelColor: '#1976D2', barrelStroke: '#0D47A1' }; // Blue for precision
                } else if (weaponIndex === 2) {
                    // Minigun (rear-right vertex)
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 4, turretRadius: vehicle.radius * 0.01,
                            barrelColor: '#388E3C', barrelStroke: '#1B5E20' }; // Green for sustained fire
                } else if (weaponIndex === 3) {
                    // Flamethrower (rear-left vertex)
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 6, turretRadius: vehicle.radius * 0.011,
                            barrelColor: '#F57C00', barrelStroke: '#E65100' }; // Orange for fire
                } else {
                    // Laser Minigun (front-left vertex)
                    return { ...baseProps, length: vehicle.radius * 0.4, width: 3, turretRadius: vehicle.radius * 0.012,
                            barrelColor: '#7B1FA2', barrelStroke: '#4A148C' }; // Purple for energy
                }
            default:
                return baseProps;
        }
    }

    drawTraverseLimits(vehicle, mountedWeapon, weaponProps) {
        if (mountedWeapon.maximumRadian >= Math.PI * 2) return;

        this.ctx.save();

        // Calculate default angle in world coordinates
        const defaultWorldAngle = vehicle.angle + mountedWeapon.defaultAngle;
        const halfTraverse = mountedWeapon.maximumRadian / 2.0;
        const currentAngle = mountedWeapon.currentAngle || defaultWorldAngle;

        // Draw traverse arc - much more subtle
        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
        this.ctx.lineWidth = 1;
        this.ctx.setLineDash([8, 8]);

        const arcRadius = weaponProps.length * 1.1;
        this.ctx.beginPath();
        this.ctx.arc(
            mountedWeapon.x,
            mountedWeapon.y,
            arcRadius,
            defaultWorldAngle - halfTraverse,
            defaultWorldAngle + halfTraverse
        );
        this.ctx.stroke();

        // Draw current weapon direction indicator
        if (mountedWeapon.controllerId) {
            this.ctx.setLineDash([]);
            this.ctx.strokeStyle = 'rgba(100, 200, 255, 0.4)';
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            this.ctx.moveTo(mountedWeapon.x, mountedWeapon.y);
            this.ctx.lineTo(
                mountedWeapon.x + Math.cos(currentAngle) * (arcRadius * 0.8),
                mountedWeapon.y + Math.sin(currentAngle) * (arcRadius * 0.8)
            );
            this.ctx.stroke();
        }

        // Draw limit lines - very subtle
        this.ctx.setLineDash([]);
        this.ctx.strokeStyle = 'rgba(255, 100, 100, 0.15)';
        this.ctx.lineWidth = 1;

        // Left limit
        this.ctx.beginPath();
        this.ctx.moveTo(mountedWeapon.x, mountedWeapon.y);
        this.ctx.lineTo(
            mountedWeapon.x + Math.cos(defaultWorldAngle - halfTraverse) * arcRadius,
            mountedWeapon.y + Math.sin(defaultWorldAngle - halfTraverse) * arcRadius
        );
        this.ctx.stroke();

        // Right limit
        this.ctx.beginPath();
        this.ctx.moveTo(mountedWeapon.x, mountedWeapon.y);
        this.ctx.lineTo(
            mountedWeapon.x + Math.cos(defaultWorldAngle + halfTraverse) * arcRadius,
            mountedWeapon.y + Math.sin(defaultWorldAngle + halfTraverse) * arcRadius
        );
        this.ctx.stroke();

        this.ctx.restore();
    }

    drawTurretBase(vehicle, mountedWeapon, style, weaponProps) {
        this.ctx.save();

        // Choose turret base style based on vehicle type
        if (vehicle.vehicleType === 'TANK' && weaponProps.turretRadius > vehicle.radius * 0.3) {
            // Main tank turret
            this.ctx.fillStyle = style.accentColor;
            this.ctx.strokeStyle = style.strokeColor;
            this.ctx.lineWidth = 2;
        } else if (vehicle.vehicleType === 'JEEP') {
            // Machine gun mount with tripod
            this.ctx.fillStyle = '#616161';
            this.ctx.strokeStyle = '#424242';
            this.ctx.lineWidth = 1.5;
        } else {
            // Other weapon mounts
            this.ctx.fillStyle = '#555555';
            this.ctx.strokeStyle = '#333333';
            this.ctx.lineWidth = 1.5;
        }

        this.ctx.beginPath();
        this.ctx.arc(mountedWeapon.x, mountedWeapon.y, weaponProps.turretRadius, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.stroke();

        // Add tripod legs for machine gun mounts
        if (vehicle.vehicleType === 'JEEP' || weaponProps.turretRadius <= vehicle.radius * 0.2) {
            this.ctx.strokeStyle = '#525252';
            this.ctx.lineWidth = 2;
            for (let i = 0; i < 3; i++) {
                const legAngle = (i * 2 * Math.PI / 3) + Math.PI / 2;
                this.ctx.beginPath();
                this.ctx.moveTo(mountedWeapon.x, mountedWeapon.y);
                this.ctx.lineTo(
                    mountedWeapon.x + Math.cos(legAngle) * weaponProps.turretRadius * 1.5,
                    mountedWeapon.y + Math.sin(legAngle) * weaponProps.turretRadius * 1.5
                );
                this.ctx.stroke();
            }
        }

        this.ctx.restore();
    }

    drawWeaponBarrel(mountedWeapon, currentAngle, weaponProps) {
        this.ctx.save();
        this.ctx.translate(mountedWeapon.x, mountedWeapon.y);
        this.ctx.rotate(currentAngle);

        // Draw main barrel
        this.ctx.fillStyle = weaponProps.barrelColor;
        this.ctx.strokeStyle = weaponProps.barrelStroke;
        this.ctx.lineWidth = 1;
        this.ctx.fillRect(0, -weaponProps.width / 2, weaponProps.length, weaponProps.width);
        this.ctx.strokeRect(0, -weaponProps.width / 2, weaponProps.length, weaponProps.width);

        // Draw muzzle/tip based on weapon type
        const tipLength = weaponProps.length * 0.1;
        const tipWidth = weaponProps.width * 1.3;
        this.ctx.fillRect(weaponProps.length - tipLength, -tipWidth / 2, tipLength, tipWidth);
        this.ctx.strokeRect(weaponProps.length - tipLength, -tipWidth / 2, tipLength, tipWidth);

        this.ctx.restore();
    }

    drawTeamColorBand(vehicle, style) {
        // Determine the controlling team by finding any passenger
        let controllingTeam = vehicle.team;

        // Only draw team band if vehicle is controlled
        if (!controllingTeam || controllingTeam === -1) {
            return;
        }

        // Get team colors
        const teamColors = {
            1: { primary: '#4CAF50', secondary: '#2E7D32' }, // Green for team 1
            2: { primary: '#F44336', secondary: '#C62828' }  // Red for team 2
        };

        const colors = teamColors[controllingTeam];
        if (!colors) return;

        this.ctx.save();

        // Calculate vehicle center and radius for band positioning
        const vehicleX = vehicle.x;
        const vehicleY = vehicle.y;
        const vehicleRadius = vehicle.radius;

        // Draw team color band as a ring around the vehicle
        const bandInnerRadius = vehicleRadius;
        const bandOuterRadius = vehicleRadius + 3;

        // Create radial gradient for the band
        const gradient = this.ctx.createRadialGradient(
            vehicleX, vehicleY, bandInnerRadius,
            vehicleX, vehicleY, bandOuterRadius
        );
        gradient.addColorStop(0, colors.primary);
        gradient.addColorStop(1, colors.secondary);

        // Draw the band ring
        this.ctx.strokeStyle = gradient;
        this.ctx.lineWidth = 6;
        this.ctx.globalAlpha = 0.8;
        this.ctx.beginPath();
        this.ctx.arc(vehicleX, vehicleY, bandInnerRadius + 3, 0, 2 * Math.PI);
        this.ctx.stroke();

        // Add a subtle inner highlight
        this.ctx.strokeStyle = colors.primary;
        this.ctx.lineWidth = 2;
        this.ctx.globalAlpha = 0.6;
        this.ctx.beginPath();
        this.ctx.arc(vehicleX, vehicleY, bandInnerRadius + 1, 0, 2 * Math.PI);
        this.ctx.stroke();

        this.ctx.restore();
    }

    getVehicleControllingPlayer(vehicle) {
        // Find the driver (primary controller) of the vehicle
        if (!vehicle.driverId) {
            return null;
        }

        // Look for the driver in the players list
        if (this.gameState.players) {
            for (const player of this.gameState.players) {
                if (player.id === vehicle.driverId) {
                    return player;
                }
            }
        }
        return null;
    }

    getVehicleStyle(vehicleType) {
        switch (vehicleType) {
            case 'TANK':
                return {
                    bodyColor: '#4A4A4A',
                    strokeColor: '#2A2A2A',
                    accentColor: '#5A5A5A'
                };
            case 'MECH':
                return {
                    bodyColor: '#2E7D32',
                    strokeColor: '#1B5E20',
                    accentColor: '#4CAF50'
                };
            case 'JEEP':
                return {
                    bodyColor: '#8D6E63',
                    strokeColor: '#5D4037',
                    accentColor: '#A1887F'
                };
            case 'FIXED_CANNON':
                return {
                    bodyColor: '#616161',
                    strokeColor: '#424242',
                    accentColor: '#757575'
                };
            case 'DAVINCI':
                return {
                    bodyColor: '#7a1f93',  // Purple - artistic/renaissance theme
                    strokeColor: '#4A148C', // Dark purple
                    accentColor: '#BA68C8'  // Light purple accent
                };
            default:
                return {
                    bodyColor: '#666666',
                    strokeColor: '#333333',
                    accentColor: '#999999'
                };
        }
    }

    drawPlayer(player) {
        if (player.dead) {
            this.ctx.save();
            this.ctx.strokeStyle = 'rgba(200, 0, 0, 0.6)';
            this.ctx.lineWidth = 3;
            const markerSize = 10;
            // draw death marker
            this.ctx.beginPath();
            this.ctx.moveTo(player.x - markerSize, player.y - markerSize);
            this.ctx.lineTo(player.x + markerSize, player.y + markerSize);
            this.ctx.stroke();

            this.ctx.beginPath();
            this.ctx.moveTo(player.x + markerSize, player.y - markerSize);
            this.ctx.lineTo(player.x - markerSize, player.y + markerSize);
            this.ctx.stroke();
            this.ctx.restore();
            return;
        }

        if (player.vehicleId) {
            return;
        }

        const myself = player.id === this.playerId;
        const playerSize = 20;
        const playerCenterX = player.x;
        const playerCenterY = player.y;

        if (myself) {
            this.ctx.save();
            this.ctx.strokeStyle = `rgba(255, 215, 0, 0.8)`; // Gold, fading out
            this.ctx.lineWidth = 3;
            this.ctx.beginPath();
            this.ctx.arc(playerCenterX, playerCenterY, 20, 0, 2 * Math.PI);
            this.ctx.stroke();
            this.ctx.restore();
        }

        this.ctx.fillStyle = player.team === 1 ? GameColors.teams.team1.primary : GameColors.teams.team2.primary;
        this.ctx.strokeStyle = player.team === 1 ? GameColors.teams.team1.darker : GameColors.teams.team2.darker;
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.arc(playerCenterX, playerCenterY, playerSize / 2, 0, 2 * Math.PI);
        this.ctx.fill();
        this.ctx.stroke();

        this.ctx.save();
        this.ctx.translate(playerCenterX, playerCenterY);

        let angle = 0;
        if (myself && this.isGamepadActive) {
            angle = Math.atan2(this.gamepadAim.y, this.gamepadAim.x);
        } else if (myself && !this.isGamepadActive) {
            // For the local player, use the live mouse position for maximum responsiveness.
            angle = Math.atan2(this.mouse.y - playerCenterY, this.mouse.x - playerCenterX);
        } else {
            // For other players (human or AI), use the server-replicated mouse position.
            angle = Math.atan2(player.mouseY - playerCenterY, player.mouseX - playerCenterX);
        }
        this.ctx.rotate(angle);

        // --- Draw Weapon ---
        this.ctx.fillStyle = '#9E9E9E';
        this.ctx.strokeStyle = '#616161';
        this.ctx.lineWidth = 1;
        // Draw the weapon extending slightly from the player's center
        this.ctx.fillRect(3, -3, 13, 6);
        this.ctx.strokeRect(3, -3, 13, 6);
        this.ctx.restore();

        const gameInfo = this.gameState.info;
        if (gameInfo && gameInfo.type === 'Capture the Flag') {
            const enemyFlag = player.team === 1 ? gameInfo.team2Flag : gameInfo.team1Flag;
            if (enemyFlag && enemyFlag.state === 'CARRIED' && enemyFlag.carrierId === player.id) {
                this.ctx.save();
                this.ctx.font = '18px Arial';
                this.ctx.textAlign = 'center';
                // Use red flag for team 1 and green flag for team 2
                const flagEmoji = player.team === 1 ? '🔴🚩' : '🟢🚩';
                this.ctx.fillText(flagEmoji, playerCenterX, player.y - 30);
                this.ctx.restore();
            }
        }

        if (gameInfo && gameInfo.type === 'Oddball' && gameInfo.oddball) {
            const oddball = gameInfo.oddball;
            if (oddball.state === 'CARRIED' && oddball.carrierId === player.id) {
                this.ctx.save();
                this.ctx.font = '18px Arial';
                this.ctx.textAlign = 'center';
                this.ctx.fillText('🔆', playerCenterX, player.y - 30);
                this.ctx.restore();
            }
        }

        if (gameInfo && gameInfo.type === 'Juggernaut'
                && (gameInfo.team1Juggernaut === player.id || gameInfo.team2Juggernaut == player.id)) {
            this.ctx.save();
            this.ctx.font = '18px Arial';
            this.ctx.textAlign = 'center';
            this.ctx.fillText('👑', playerCenterX, player.y - 30);
            this.ctx.restore();
        }

        this.ctx.fillStyle = '#fff';
        this.ctx.font = '12px Arial';
        this.ctx.textAlign = 'center';
        const baseName = player.name || (player.id.startsWith('ai-') ? 'AI' : 'Player');
        let displayName = `${player.powerUpIcons || ''}${baseName} | ${player.weapon.shortName}`;
        this.ctx.fillText(displayName, playerCenterX, player.y - 15);

        if (myself) {
            this.ctx.font = 'bold 12px Arial';
            this.ctx.textAlign = 'center';
            this.ctx.textBaseline = 'middle';
            this.ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
            const text = player.reloading ? 'R' : player.ammoInMag || 0;
            this.ctx.fillText(text, playerCenterX + 1, playerCenterY + 1);
            this.ctx.fillStyle = player.reloading ? '#FFC107' : '#FFFFFF';
            this.ctx.fillText(text, playerCenterX, playerCenterY);
            this.ctx.textBaseline = 'alphabetic';
        }

        const maxHp = player.maxHp || 100;
        const healthPercentage = Math.max(0, player.hp / maxHp);
        const healthBarRadius = playerSize / 2 + 4;
        const healthBarThickness = 3;

        const arcSpan = Math.PI / 2;
        const startAngle = -Math.PI / 2 - (arcSpan / 2);
        const endAngle = startAngle + arcSpan;

        this.ctx.strokeStyle = 'rgba(0, 0, 0, 0.5)';
        this.ctx.lineWidth = healthBarThickness + 1;
        this.ctx.beginPath();
        this.ctx.arc(playerCenterX, playerCenterY, healthBarRadius, startAngle, endAngle);
        this.ctx.stroke();

        if (healthPercentage > 0) {
            const healthEndAngle = startAngle + (healthPercentage * arcSpan);
            this.ctx.strokeStyle = healthPercentage > 0.5 ? '#4CAF50' : (healthPercentage > 0.2 ? '#FFC107' : '#F44336');
            this.ctx.lineWidth = healthBarThickness;
            this.ctx.beginPath();
            this.ctx.arc(playerCenterX, playerCenterY, healthBarRadius, startAngle, healthEndAngle);
            this.ctx.stroke();
        }

        const deadzone = 0.25;
        const rightStickX = this.gamepad ? this.gamepad.axes[2] || 0 : 0;
        const rightStickY = this.gamepad ? this.gamepad.axes[3] || 0 : 0;
        const aimMagnitude = Math.sqrt(rightStickX * rightStickX + rightStickY * rightStickY);

        if (!this.isSpectator && this.isGamepadActive && aimMagnitude > deadzone) {
            if (this.localPlayer && !this.localPlayer.dead) {
                // Get the normalized direction of the stick
                const aimDirX = rightStickX / aimMagnitude;
                const aimDirY = rightStickY / aimMagnitude;

                // Remap the magnitude from [deadzone, 1.0] to [0, 1.0] for a smooth response
                const effectiveMagnitude = (aimMagnitude - deadzone) / (1 - deadzone);

                // This makes the crosshair appear close for small movements and move further out for larger ones.
                const crosshairDist = 40 + (effectiveMagnitude * 60);

                const crosshairX = this.localPlayer.x + aimDirX * crosshairDist;
                const crosshairY = this.localPlayer.y + aimDirY * crosshairDist;

                this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.7)';
                this.ctx.lineWidth = 2;
                this.ctx.beginPath();
                this.ctx.moveTo(crosshairX - 8, crosshairY);
                this.ctx.lineTo(crosshairX + 8, crosshairY);
                this.ctx.moveTo(crosshairX, crosshairY - 8);
                this.ctx.lineTo(crosshairX, crosshairY + 8);
                this.ctx.stroke();
            }
        } else if (!this.isSpectator && !this.isGamepadActive && this.mouse.x && this.mouse.y) {
            this.ctx.strokeStyle = GameColors.text.primary;
            this.ctx.lineWidth = 1;
            this.ctx.beginPath();
            this.ctx.moveTo(this.mouse.x - 10, this.mouse.y);
            this.ctx.lineTo(this.mouse.x + 10, this.mouse.y);
            this.ctx.moveTo(this.mouse.x, this.mouse.y - 10);
            this.ctx.lineTo(this.mouse.x, this.mouse.y + 10);
            this.ctx.stroke();
        }
    }

    drawPayload() {
        const gameInfo = this.gameState.info;
        if (!gameInfo || gameInfo.type !== 'Escort' || !gameInfo.payload) {
            return;
        }

        const payload = gameInfo.payload;
        if (!payload.vertices || payload.vertices.length < 3) {
            return;
        }

        const payloadCenter = payload.vertices.reduce((acc, v) => ({ x: acc.x + v.x, y: acc.y + v.y }), { x: 0, y: 0 });
        payloadCenter.x /= payload.vertices.length;
        payloadCenter.y /= payload.vertices.length;

        const captureRadius = gameInfo.captureRadius;

        this.ctx.save();
        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([10, 10]);

        this.ctx.beginPath();
        this.ctx.arc(payloadCenter.x, payloadCenter.y, captureRadius, 0, 2 * Math.PI);
        this.ctx.stroke();
        this.ctx.restore();

        this.ctx.save();
        this.ctx.fillStyle = '#B0BEC5';
        this.ctx.strokeStyle = '#546E7A';
        this.ctx.lineWidth = 3;
        this.ctx.lineJoin = 'round';

        this.ctx.beginPath();
        this.ctx.moveTo(payload.vertices[0].x, payload.vertices[0].y);
        for (let i = 1; i < payload.vertices.length; i++) {
            this.ctx.lineTo(payload.vertices[i].x, payload.vertices[i].y);
        }
        this.ctx.closePath();
        this.ctx.fill();
        this.ctx.stroke();
        this.ctx.restore();
    }

    drawBase() {
        const gameInfo = this.gameState.info;
        // Handle bases (Base Destruction mode)
        if (gameInfo && gameInfo.type === 'Base Destruction') {
            if (gameInfo.team1Base && !gameInfo.team1BaseDestroyed) {
                this.drawSingleBase(gameInfo.team1Base, 1);
            }
            if (gameInfo.team2Base && !gameInfo.team2BaseDestroyed) {
                this.drawSingleBase(gameInfo.team2Base, 2);
            }
        }
    }

    drawSingleBase(base, team = null) {
        this.ctx.save();

        // Draw base structure using vertices (octagonal shape)
        if (base.vertices && base.vertices.length >= 3) {
            // Choose colors based on team (if specified) or use neutral colors
            let baseColor, strokeColor, accentColor;
            if (team === 1) {
                baseColor = '#2E7D32';   // Dark green for Team 1
                strokeColor = '#1B5E20'; // Darker green
                accentColor = '#4CAF50'; // Bright green accent
            } else if (team === 2) {
                baseColor = '#C62828';   // Dark red for Team 2
                strokeColor = '#B71C1C'; // Darker red
                accentColor = '#F44336'; // Bright red accent
            } else {
                baseColor = '#607D8B';   // Blue Grey for neutral/single base
                strokeColor = '#37474F'; // Darker blue grey
                accentColor = '#78909C'; // Light blue grey accent
            }
            
            // Main base body
            this.ctx.fillStyle = baseColor;
            this.ctx.strokeStyle = strokeColor;
            this.ctx.lineWidth = 4;
            this.ctx.lineJoin = 'round';

            this.ctx.beginPath();
            this.ctx.moveTo(base.vertices[0].x, base.vertices[0].y);
            for (let i = 1; i < base.vertices.length; i++) {
                this.ctx.lineTo(base.vertices[i].x, base.vertices[i].y);
            }
            this.ctx.closePath();
            this.ctx.fill();
            this.ctx.stroke();

            // Add structural details - inner octagon
            this.ctx.strokeStyle = accentColor;
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            const innerRadius = base.radius * 0.7;
            for (let i = 0; i < 8; i++) {
                const angle = (i * Math.PI * 2) / 8;
                const x = base.x + innerRadius * Math.cos(angle);
                const y = base.y + innerRadius * Math.sin(angle);
                if (i === 0) {
                    this.ctx.moveTo(x, y);
                } else {
                    this.ctx.lineTo(x, y);
                }
            }
            this.ctx.closePath();
            this.ctx.stroke();

            // Add center core
            this.ctx.fillStyle = accentColor;
            this.ctx.beginPath();
            this.ctx.arc(base.x, base.y, base.radius * 0.3, 0, Math.PI * 2);
            this.ctx.fill();
            this.ctx.strokeStyle = strokeColor;
            this.ctx.lineWidth = 2;
            this.ctx.stroke();

            // Add defensive spikes/details around the perimeter
            this.ctx.strokeStyle = accentColor;
            this.ctx.lineWidth = 3;
            for (let i = 0; i < 8; i++) {
                const angle = (i * Math.PI * 2) / 8;
                const startX = base.x + (base.radius * 0.8) * Math.cos(angle);
                const startY = base.y + (base.radius * 0.8) * Math.sin(angle);
                const endX = base.x + (base.radius * 1.1) * Math.cos(angle);
                const endY = base.y + (base.radius * 1.1) * Math.sin(angle);

                this.ctx.beginPath();
                this.ctx.moveTo(startX, startY);
                this.ctx.lineTo(endX, endY);
                this.ctx.stroke();
            }
        }

        // Draw health bar
        const healthPercentage = Math.max(0, base.hp / base.maxHp);
        const barWidth = base.radius * 2;
        const barHeight = 8;
        const barX = base.x - base.radius;
        const barY = base.y - base.radius - 20;

        // Health bar background
        this.ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
        this.ctx.fillRect(barX, barY, barWidth, barHeight);

        // Health bar foreground
        if (healthPercentage > 0) {
            this.ctx.fillStyle = healthPercentage > 0.5 ? '#4CAF50' :
                               healthPercentage > 0.25 ? '#FFC107' : '#F44336';
            this.ctx.fillRect(barX, barY, barWidth * healthPercentage, barHeight);
        }

        // Health bar border
        this.ctx.strokeStyle = '#FFFFFF';
        this.ctx.lineWidth = 1;
        this.ctx.strokeRect(barX, barY, barWidth, barHeight);

        // Display health text
        this.ctx.fillStyle = '#FFFFFF';
        this.ctx.font = 'bold 12px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'bottom';
        this.ctx.fillText(`${Math.round(base.hp)}/${Math.round(base.maxHp)}`, base.x, barY - 2);

        // Add pulsing effect if health is critical
        if (healthPercentage <= 0.25) {
            const pulse = Math.abs(Math.sin(this.gameState.serverTime / 200));
            this.ctx.strokeStyle = `rgba(244, 67, 54, ${0.3 + pulse * 0.4})`;
            this.ctx.lineWidth = 4 + pulse * 4;
            this.ctx.beginPath();
            this.ctx.arc(base.x, base.y, base.radius + 10, 0, Math.PI * 2);
            this.ctx.stroke();
        }

        this.ctx.restore();
    }

    drawDebugInfo(currentTime) {
        // Optional FPS display (can be toggled with a key)
        if (this.keys['F3'] || localStorage.getItem('showFPS') === 'true') {
            this.ctx.save();
            this.ctx.fillStyle = GameColors.overlays.debug;
            this.ctx.fillRect(10, this.canvas.height - 110, 200, 100);

            this.ctx.fillStyle = GameColors.teams.team1.bright;
            this.ctx.font = '14px monospace';
            this.ctx.textAlign = 'left';
            this.ctx.textBaseline = 'top';

            const timeSinceLastUpdate = this.lastUpdateTime ? (currentTime - this.lastUpdateTime) : 0;

            this.ctx.fillText(`FPS: ${this.fps}`, 15, this.canvas.height - 105);
            this.ctx.fillText(`Ping: ${this.latency}ms`, 15, this.canvas.height - 90);
            this.ctx.fillText(`Last Update: ${timeSinceLastUpdate.toFixed(0)}ms ago`, 15, this.canvas.height - 75);
            this.ctx.fillText(`Interp Factor: ${(this.lastInterpolationFactor || 0).toFixed(2)}`, 15, this.canvas.height - 60);
            this.ctx.fillText(`Players: ${this.gameState?.players?.length || 0}`, 15, this.canvas.height - 45);
            this.ctx.fillText(`Vehicles: ${this.gameState?.vehicles?.length || 0}`, 15, this.canvas.height - 30);
            this.ctx.fillText(`Bullets: ${this.gameState?.bullets?.length || 0}`, 15, this.canvas.height - 15);

            this.ctx.restore();
        }
    }

    drawRespawnOverlay() {
        if (this.isLocalPlayerDead && this.shouldDrawRespawnOverlay && this.localPlayer.respawnTime > 0) {
            if (this.localPlayer && this.localPlayer.respawnTime && this.gameState.serverTime > 0) {
                const remainingTime = Math.ceil(Math.max(0, (this.localPlayer.respawnTime - this.gameState.serverTime) / 1000));
                this.ctx.fillStyle = GameColors.overlays.respawn;
                this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
                this.ctx.fillStyle = GameColors.text.primary;
                this.ctx.font = '48px Arial';
                this.ctx.textAlign = 'center';
                this.ctx.textBaseline = 'middle';
                this.ctx.fillText(`Respawning in ${remainingTime.toFixed(0)}s`, this.canvas.width / 2, this.canvas.height / 2);
            }
        }
    }

    startGameLoop() {
        // Start input sending loop (20 Hz)
        setInterval(() => this.sendInput(), 50);

        // Start independent render loop
        this.startRenderLoop();
    }

    startRenderLoop() {
        if (this.isRenderLoopRunning) {
            return;
        }

        this.isRenderLoopRunning = true;
        this.lastFrameTime = performance.now();
        this.lastFpsTime = this.lastFrameTime;

        const renderLoop = (currentTime) => {
            if (!this.isRenderLoopRunning) return;

            // Calculate frame timing
            const deltaTime = currentTime - this.lastFrameTime;
            this.lastFrameTime = currentTime;

            // Update FPS counter
            this.frameCount++;
            if (currentTime - this.lastFpsTime >= 1000) {
                this.fps = Math.round((this.frameCount * 1000) / (currentTime - this.lastFpsTime));
                this.frameCount = 0;
                this.lastFpsTime = currentTime;
            }

            // Update game systems independent of network
            this.update(deltaTime);

            // Render frame
            this.render(currentTime);

            // Schedule next frame
            requestAnimationFrame(renderLoop);
        };

        renderLoop(this.lastFrameTime);
    }

    stopRenderLoop() {
        this.isRenderLoopRunning = false;
    }

    update(deltaTime) {
        this.pollGamepad();
    }
}

/**
 * High-efficiency binary state decoder for Protocol FSB1 (Full Steam Binary v1).
 *
 * Unpacks binary ArrayBuffer frames into the JavaScript object representation
 * expected by GameEngine.js.
 */
class BinaryStateDecoder {

    static FIELD_EFFECT_TYPES = [
        'EXPLOSION', 'FIRE', 'ELECTRIC', 'FREEZE', 'FRAGMENTATION',
        'POISON', 'LASER', 'PLASMA', 'HEAL_ZONE', 'SLOW_FIELD',
        'SHIELD_BARRIER', 'GRAVITY_WELL', 'SPEED_BOOST', 'PROXIMITY_MINE',
        'SMOKE', 'WARNING_ZONE', 'EARTHQUAKE'
    ];

    static BULLET_EFFECTS = [
        'EXPLOSIVE', 'INCENDIARY', 'ELECTRIC', 'FREEZING', 'POISON',
        'BOUNCY', 'PIERCING', 'FRAGMENTING', 'HOMING', 'STRIKE', 'SMOKE'
    ];

    static KOTH_ZONE_STATES = ['NEUTRAL', 'CONTROLLED', 'CONTESTED'];

    static ODDBALL_PERSONALITIES = ['RAMPAGE', 'SEEKER'];

    static textDecoder = new TextDecoder('utf-8');

    /**
     * Decode binary ArrayBuffer frame into GameState object.
     * @param {ArrayBuffer} buffer
     * @returns {Object|null}
     */
    static decode(buffer) {
        if (!buffer || buffer.byteLength < 18) {
            return null;
        }

        const view = new DataView(buffer);
        const ptr = { offset: 0 };

        // 1. Magic Header Verification ('F' 'S' 'B' 1)
        const m0 = view.getUint8(ptr.offset++);
        const m1 = view.getUint8(ptr.offset++);
        const m2 = view.getUint8(ptr.offset++);
        const version = view.getUint8(ptr.offset++);

        if (m0 !== 70 || m1 !== 83 || m2 !== 66 || version !== 1) { // 'F', 'S', 'B', 1
            console.warn("Invalid binary header magic or version:", m0, m1, m2, version);
            return null;
        }

        // 2. Header Flags & Metadata
        const headerFlags = view.getUint8(ptr.offset++);
        const visionObscured = (headerFlags & 1) !== 0;
        const awaitingSpawn = (headerFlags & 2) !== 0;
        const isCountdownFlag = (headerFlags & 4) !== 0;
        const isGameOver = (headerFlags & 8) !== 0;
        const gameTimed = (headerFlags & 16) !== 0;

        const stateCode = view.getUint8(ptr.offset++);
        const gameStateStr = stateCode === 2 ? 'COUNTDOWN' : (stateCode === 1 ? 'PLAYING' : 'WAITING');

        // BigInt64 for timestamp
        const timestamp = Number(view.getBigInt64(ptr.offset));
        ptr.offset += 8;

        const gameTimeRemaining = view.getFloat32(ptr.offset); ptr.offset += 4;
        const startCountdownRemaining = view.getFloat32(ptr.offset); ptr.offset += 4;
        const winningTeam = view.getInt8(ptr.offset++);
        const winningPlayerId = view.getInt16(ptr.offset); ptr.offset += 2;

        const scoreStyle = BinaryStateDecoder.readString8(view, ptr);
        const sortBy = BinaryStateDecoder.readString8(view, ptr);
        const compCount = view.getUint8(ptr.offset++);
        const components = [];
        for (let c = 0; c < compCount; c++) {
            components.push(BinaryStateDecoder.readString8(view, ptr));
        }

        const teamScoreCount = view.getUint8(ptr.offset++);
        const teamScores = {};
        for (let t = 0; t < teamScoreCount; t++) {
            const teamId = view.getUint8(ptr.offset++);
            const teamScore = view.getInt32(ptr.offset); ptr.offset += 4;
            teamScores[teamId] = teamScore;
        }

        const state = {
            type: 'gameState',
            timestamp: timestamp,
            gameState: gameStateStr,
            isCountdown: isCountdownFlag || stateCode === 2,
            gameTimed: gameTimed,
            timeRemaining: gameTimeRemaining,
            gameTimeRemaining: gameTimeRemaining,
            startCountdownRemaining: startCountdownRemaining,
            isGameOver: isGameOver,
            winningTeam: winningTeam,
            winningPlayerId: winningPlayerId,
            scoreStyle: scoreStyle,
            scoringConfig: {
                components: components,
                scoreStyle: scoreStyle,
                sortBy: sortBy
            },
            teamScores: teamScores,
            visionObscured: visionObscured,
            awaitingSpawn: awaitingSpawn,
            players: [],
            projectiles: [],
            fieldEffects: [],
            turrets: [],
            nets: [],
            defenseLasers: [],
            kothZones: [],
            headquarters: [],
            flags: [],
            oddballNpcs: [],
            hits: []
        };

        // 3. Section 1: Players
        const playerCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < playerCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const team = view.getUint8(ptr.offset++);

            const pFlags = view.getUint8(ptr.offset++);
            const active = (pFlags & 1) !== 0;
            const reloading = (pFlags & 2) !== 0;
            const eliminated = (pFlags & 4) !== 0;
            const respawnWaiting = (pFlags & 8) !== 0;
            const isVip = (pFlags & 16) !== 0;

            const name = BinaryStateDecoder.readString8(view, ptr);

            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;

            const vx = view.getInt16(ptr.offset) / 10.0; ptr.offset += 2;
            const vy = view.getInt16(ptr.offset) / 10.0; ptr.offset += 2;

            const rotation = view.getInt16(ptr.offset) / 1000.0; ptr.offset += 2;

            const health = view.getUint8(ptr.offset++) / 100.0;
            const ammo = view.getUint8(ptr.offset++);
            const maxAmmo = view.getUint8(ptr.offset++);
            const reloadPercent = view.getUint8(ptr.offset++) / 100.0;
            const utilityCooldownPercent = view.getUint8(ptr.offset++) / 100.0;
            const weaponRange = view.getInt16(ptr.offset); ptr.offset += 2;

            const respawnTime = view.getFloat32(ptr.offset); ptr.offset += 4;
            const livesRemaining = view.getInt8(ptr.offset++);

            // Scoring (10 shorts)
            const kills = view.getInt16(ptr.offset); ptr.offset += 2;
            const deaths = view.getInt16(ptr.offset); ptr.offset += 2;
            const captures = view.getInt16(ptr.offset); ptr.offset += 2;
            const koth = view.getInt16(ptr.offset); ptr.offset += 2;
            const oddball = view.getInt16(ptr.offset); ptr.offset += 2;
            const hqDamage = view.getInt16(ptr.offset); ptr.offset += 2;
            const hqDestroyed = view.getInt16(ptr.offset); ptr.offset += 2;
            const vipKills = view.getInt16(ptr.offset); ptr.offset += 2;
            const bonus = view.getInt16(ptr.offset); ptr.offset += 2;
            const total = view.getInt16(ptr.offset); ptr.offset += 2;

            // Active PowerUps
            const powerUpCount = view.getUint8(ptr.offset++);
            const activePowerUps = [];
            for (let p = 0; p < powerUpCount; p++) {
                activePowerUps.push(BinaryStateDecoder.readString8(view, ptr));
            }

            state.players.push({
                id: id,
                name: name,
                team: team,
                active: active,
                reloading: reloading,
                eliminated: eliminated,
                respawnWaiting: respawnWaiting,
                isVip: isVip,
                x: x,
                y: y,
                vx: vx,
                vy: vy,
                rotation: rotation,
                health: health,
                ammo: ammo,
                maxAmmo: maxAmmo,
                reloadPercent: reloadPercent,
                utilityCooldownPercent: utilityCooldownPercent,
                weaponRange: weaponRange,
                respawnTime: respawnTime,
                livesRemaining: livesRemaining,
                kills: kills,
                deaths: deaths,
                activePowerUps: activePowerUps,
                score: {
                    kills: kills,
                    deaths: deaths,
                    captures: captures,
                    koth: koth,
                    oddball: oddball,
                    hqDamage: hqDamage,
                    hqDestroyed: hqDestroyed,
                    vipKills: vipKills,
                    bonus: bonus,
                    total: total
                }
            });
        }

        // 4. Section 2: Projectiles
        const projCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < projCount; i++) {
            const id = view.getInt32(ptr.offset); ptr.offset += 4;
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const vx = view.getFloat32(ptr.offset); ptr.offset += 4;
            const vy = view.getFloat32(ptr.offset); ptr.offset += 4;
            const caliber = view.getFloat32(ptr.offset); ptr.offset += 4;

            const effectMask = view.getUint16(ptr.offset); ptr.offset += 2;
            const bulletEffects = [];
            for (let e = 0; e < BinaryStateDecoder.BULLET_EFFECTS.length; e++) {
                if ((effectMask & (1 << e)) !== 0) {
                    bulletEffects.push(BinaryStateDecoder.BULLET_EFFECTS[e]);
                }
            }

            state.projectiles.push({
                id: id,
                x: x,
                y: y,
                vx: vx,
                vy: vy,
                caliber: caliber,
                bulletEffects: bulletEffects
            });
        }

        // 5. Section 3: Field Effects
        const feCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < feCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const typeOrdinal = view.getUint8(ptr.offset++);
            const typeStr = BinaryStateDecoder.FIELD_EFFECT_TYPES[typeOrdinal] || 'EXPLOSION';
            const ownerTeam = view.getUint8(ptr.offset++);

            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const rotation = view.getFloat32(ptr.offset); ptr.offset += 4;
            const radius = view.getFloat32(ptr.offset); ptr.offset += 4;
            const progress = view.getFloat32(ptr.offset); ptr.offset += 4;

            const feFlags = view.getUint8(ptr.offset++);
            const active = (feFlags & 1) !== 0;
            const isArmed = (feFlags & 2) !== 0;

            const shapes = BinaryStateDecoder.readBodyShapes(view, ptr);

            state.fieldEffects.push({
                id: id,
                type: typeStr,
                ownerTeam: ownerTeam,
                x: x,
                y: y,
                rotation: rotation,
                radius: radius,
                progress: progress,
                active: active,
                isArmed: isArmed,
                shapes: shapes
            });
        }

        // 6. Section 4: Turrets
        const turretCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < turretCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const ownerTeam = view.getUint8(ptr.offset++);
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const rotation = view.getFloat32(ptr.offset); ptr.offset += 4;
            const health = view.getUint8(ptr.offset++) / 100.0;
            const active = view.getUint8(ptr.offset++) !== 0;

            state.turrets.push({
                id: id,
                type: 'TURRET',
                team: ownerTeam,
                ownerTeam: ownerTeam,
                x: x,
                y: y,
                rotation: rotation,
                health: health,
                active: active
            });
        }

        // 7. Section 5: Nets
        const netCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < netCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const rotation = view.getFloat32(ptr.offset); ptr.offset += 4;
            const active = view.getUint8(ptr.offset++) !== 0;

            state.nets.push({
                id: id,
                type: 'NET',
                x: x,
                y: y,
                rotation: rotation,
                active: active
            });
        }

        // 8. Section 6: Defense Lasers
        const laserCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < laserCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const ownerTeam = view.getUint8(ptr.offset++);
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const rotation = view.getFloat32(ptr.offset); ptr.offset += 4;
            const active = view.getUint8(ptr.offset++) !== 0;

            state.defenseLasers.push({
                id: id,
                type: 'DEFENSE_LASER',
                team: ownerTeam,
                ownerTeam: ownerTeam,
                x: x,
                y: y,
                rotation: rotation,
                active: active
            });
        }

        // 9. Section 7: KOTH Zones
        const zoneCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < zoneCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const zoneNumber = view.getUint8(ptr.offset++);
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const radius = view.getFloat32(ptr.offset); ptr.offset += 4;
            const controllingTeam = view.getInt8(ptr.offset++);
            const stateOrdinal = view.getUint8(ptr.offset++);
            const stateStr = BinaryStateDecoder.KOTH_ZONE_STATES[stateOrdinal] || 'NEUTRAL';
            const playerCount = view.getUint8(ptr.offset++);

            state.kothZones.push({
                id: id,
                zoneNumber: zoneNumber,
                x: x,
                y: y,
                radius: radius,
                controllingTeam: controllingTeam,
                state: stateStr,
                playerCount: playerCount
            });
        }

        // 10. Section 8: Headquarters
        const hqCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < hqCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const ownerTeam = view.getUint8(ptr.offset++);
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const health = view.getUint8(ptr.offset++) / 100.0;
            const isDestroyed = view.getUint8(ptr.offset++) !== 0;
            const shapes = BinaryStateDecoder.readBodyShapes(view, ptr);

            state.headquarters.push({
                id: id,
                type: 'HEADQUARTERS',
                team: ownerTeam,
                ownerTeam: ownerTeam,
                x: x,
                y: y,
                health: health,
                active: !isDestroyed,
                isDestroyed: isDestroyed,
                shapes: shapes
            });
        }

        // 11. Section 9: Flags
        const flagCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < flagCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const ownerTeam = view.getUint8(ptr.offset++);
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const carriedByPlayerId = view.getInt16(ptr.offset); ptr.offset += 2;
            const isAtHome = view.getUint8(ptr.offset++) !== 0;

            state.flags.push({
                id: id,
                team: ownerTeam,
                ownerTeam: ownerTeam,
                x: x,
                y: y,
                carriedByPlayerId: carriedByPlayerId,
                isAtHome: isAtHome
            });
        }

        // 12. Section 10: Oddball NPCs
        const oddballCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < oddballCount; i++) {
            const id = view.getInt16(ptr.offset); ptr.offset += 2;
            const personalityOrdinal = view.getUint8(ptr.offset++);
            const personalityStr = BinaryStateDecoder.ODDBALL_PERSONALITIES[personalityOrdinal] || 'RAMPAGE';
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const vx = view.getFloat32(ptr.offset); ptr.offset += 4;
            const vy = view.getFloat32(ptr.offset); ptr.offset += 4;
            const radius = view.getFloat32(ptr.offset); ptr.offset += 4;
            const health = view.getUint8(ptr.offset++) / 100.0;

            state.oddballNpcs.push({
                id: id,
                type: 'ODDBALL',
                personality: personalityStr,
                x: x,
                y: y,
                vx: vx,
                vy: vy,
                radius: radius,
                health: health
            });
        }

        // 13. Section 11: Hits
        const hitCount = view.getUint16(ptr.offset); ptr.offset += 2;
        for (let i = 0; i < hitCount; i++) {
            const x = view.getFloat32(ptr.offset); ptr.offset += 4;
            const y = view.getFloat32(ptr.offset); ptr.offset += 4;
            const damage = view.getFloat32(ptr.offset); ptr.offset += 4;
            const attackerId = view.getInt16(ptr.offset); ptr.offset += 2;
            const victimId = view.getInt16(ptr.offset); ptr.offset += 2;
            const hitFlags = view.getUint8(ptr.offset++);
            const kill = (hitFlags & 1) !== 0;

            state.hits.push({
                x: x,
                y: y,
                damage: damage,
                attackerId: attackerId,
                victimId: victimId,
                kill: kill
            });
        }

        return state;
    }

    static readString8(view, ptr) {
        const len = view.getUint8(ptr.offset++);
        if (len === 0) return '';
        const str = BinaryStateDecoder.textDecoder.decode(new Uint8Array(view.buffer, view.byteOffset + ptr.offset, len));
        ptr.offset += len;
        return str;
    }

    static readBodyShapes(view, ptr) {
        const fixtureCount = view.getUint8(ptr.offset++);
        if (fixtureCount === 0) {
            return [];
        }
        const shapes = [];
        for (let i = 0; i < fixtureCount; i++) {
            const shapeType = view.getUint8(ptr.offset++);
            if (shapeType === 0) { // Polygon
                const vertexCount = view.getUint8(ptr.offset++);
                const points = [];
                for (let j = 0; j < vertexCount; j++) {
                    const vx = view.getFloat32(ptr.offset); ptr.offset += 4;
                    const vy = view.getFloat32(ptr.offset); ptr.offset += 4;
                    points.push([vx, vy]);
                }
                shapes.push({ type: 'polygon', points: points });
            } else if (shapeType === 1) { // Circle
                const cx = view.getFloat32(ptr.offset); ptr.offset += 4;
                const cy = view.getFloat32(ptr.offset); ptr.offset += 4;
                const r = view.getFloat32(ptr.offset); ptr.offset += 4;
                shapes.push({ type: 'circle', cx: cx, cy: cy, r: r });
            }
        }
        return shapes;
    }

    /**
     * Generalized shape parser that converts string shorthand or shape data arrays into standard shape objects.
     * @param {string|Array|null|undefined} shapesData
     * @returns {Array<{type: string, cx?: number, cy?: number, r?: number, points?: Array<[number, number]>}>}
     */
    static parseShapes(shapesData) {
        if (!shapesData) return [];
        if (Array.isArray(shapesData)) return shapesData;
        if (typeof shapesData !== 'string') return [];
        return shapesData.split(';').filter(s => s.length > 0).map(fixtureStr => {
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
}

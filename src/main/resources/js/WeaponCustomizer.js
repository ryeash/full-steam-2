/**
 * Weapon customization UI extracted from the legacy /config.html page.
 *
 * Renders the loadout picker (attribute sliders, bullet-effect checkboxes,
 * ordinance radio group, utility-weapon radio group, preset chooser, point
 * tracker) into a host container. The host page owns the "Ready" button and
 * listens for validity changes via `onValidityChange`.
 *
 * Element IDs and CSS class names are preserved from the original config page
 * so existing styling continues to work without modification.
 *
 * Usage:
 *   const customizer = new WeaponCustomizer(rootEl, {
 *     onValidityChange: (isValid) => { readyButton.disabled = !isValid; }
 *   });
 *   await customizer.init();           // fetches /api/weapon-customization
 *   const cfg = customizer.getPlayerConfig();   // { weaponConfig, utilityWeapon }
 */
class WeaponCustomizer {
    constructor(rootEl, options = {}) {
        if (!rootEl) {
            throw new Error('WeaponCustomizer requires a root element');
        }
        this.root = rootEl;
        this.onValidityChange = options.onValidityChange || (() => {});

        this.weaponData = null;
        this.currentWeapon = { attributes: {}, effects: [], ordinance: 'PROJECTILE' };
        this.currentUtilityWeapon = 'HEAL_ZONE';
        this._lastValid = false;
        this._resolveTimer = null;
    }

    async init() {
        this._renderShell();
        try {
            const response = await fetch('/api/weapon-customization');
            this.weaponData = await response.json();
        } catch (error) {
            console.error('Failed to load weapon data:', error);
            const loading = this.root.querySelector('#loading-message');
            if (loading) loading.textContent = 'Failed to load weapon data. Please refresh the page.';
            return;
        }
        this._initializeUI();
    }

    /**
     * Returns true when the currently selected loadout fits inside the points
     * budget. Used by the host to gate the Ready button.
     */
    isValid() {
        if (!this.weaponData) return false;
        return this._totalPoints() <= this.weaponData.maxPoints;
    }

    /**
     * Build the weapon-config payload the server expects in `readyToSpawn` /
     * `configChange`. Maps the enum-style attribute keys to Java field names.
     */
    getPlayerConfig() {
        const attributeMapping = {
            'DAMAGE': 'damage',
            'FIRE_RATE': 'fireRate',
            'RANGE': 'range',
            'ACCURACY': 'accuracy',
            'MAGAZINE_SIZE': 'magazineSize',
            'RELOAD_TIME': 'reloadTime',
            'PROJECTILE_SPEED': 'projectileSpeed',
            'BULLETS_PER_SHOT': 'bulletsPerShot',
            'LINEAR_DAMPING': 'linearDamping',
            'HANDLING': 'handling',
            'CALIBER': 'caliber'
        };
        const mappedAttributes = {};
        Object.entries(this.currentWeapon.attributes).forEach(([enumName, value]) => {
            const fieldName = attributeMapping[enumName];
            if (fieldName) mappedAttributes[fieldName] = value;
        });
        const weaponConfig = {
            type: 'Custom Weapon',
            bulletEffects: this.currentWeapon.effects,
            ordinance: this.currentWeapon.ordinance,
            ...mappedAttributes
        };
        return {
            weaponConfig,
            utilityWeapon: this.currentUtilityWeapon
        };
    }

    // ---- Internal: render shell HTML so existing CSS hooks line up ----

    _renderShell() {
        this.root.innerHTML = `
            <div class="weapon-customizer-root">
                <div id="loading-message" class="loading">
                    Loading weapon customization data...
                </div>

                <div id="customization-content" class="hidden">
                    <div class="preset-section">
                        <h3>Weapon Presets</h3>
                        <div class="preset-tabs">
                            <button type="button" class="preset-tab active" data-category="kinetic">Kinetic</button>
                            <button type="button" class="preset-tab" data-category="effects">Effects</button>
                            <button type="button" class="preset-tab" data-category="beams">Beams</button>
                        </div>
                        <div class="preset-category active" id="category-kinetic">
                            <div class="preset-buttons" id="preset-buttons-kinetic"></div>
                        </div>
                        <div class="preset-category" id="category-effects">
                            <div class="preset-buttons" id="preset-buttons-effects"></div>
                        </div>
                        <div class="preset-category" id="category-beams">
                            <div class="preset-buttons" id="preset-buttons-beams"></div>
                        </div>
                    </div>

                    <div class="customization-grid">
                        <!-- Column 1: name, points, then attributes -->
                        <div class="customization-section">
                            <div id="name-picker">
                                <h3>Your Name</h3>
                                <div class="name-picker-row">
                                    <select id="name-select" aria-label="Choose a name">
                                        <option value="">Loading…</option>
                                    </select>
                                    <button id="name-randomize" type="button" title="Pick a random name">🎲</button>
                                </div>
                            </div>
                            <div class="point-tracker">
                                <div class="point-status-line">
                                    <span id="points-used" class="pt-used">0</span><span class="pt-sep">/</span><span id="points-max" class="pt-max">100</span><span class="pt-label">pts</span>
                                    <span id="alloc-icon" class="alloc-icon">⚠</span>
                                    <span class="pt-breakdown-label">Attr:</span><span id="attr-points" class="pt-breakdown-value">0</span>
                                    <span class="pt-breakdown-label">FX:</span><span id="effect-points" class="pt-breakdown-value">0</span>
                                    <span class="pt-breakdown-label">Ord:</span><span id="ordinance-points" class="pt-breakdown-value">0</span>
                                </div>
                            </div>
                            <h3>Primary Weapon Attributes</h3>
                            <div id="attribute-sliders"></div>
                        </div>
                        <div class="customization-section">
                            <h3>Bullet Effects</h3>
                            <div id="effect-checkboxes"></div>
                        </div>
                        <div class="customization-section">
                            <h3>Ordinance Type</h3>
                            <div id="ordinance-radios"></div>
                        </div>
                        <div class="customization-section">
                            <h3>Utility Weapon</h3>
                            <div id="utility-weapon-selection"></div>
                        </div>
                        <div class="customization-section">
                            <h3>Resulting Stats</h3>
                            <div id="resolved-stats" class="resolved-stats">Adjust your loadout to preview…</div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    _initializeUI() {
        this._q('#loading-message').classList.add('hidden');
        this._q('#customization-content').classList.remove('hidden');

        this._createAttributeSliders();
        this._createEffectCheckboxes();
        this._createOrdinanceRadios();
        this._createUtilityWeaponSelection();
        this._createPresetButtons();

        if (!this._loadSavedConfiguration()) {
            this._resetToDefaults();
        }
        this._applyOrdinanceEffectGating();
        this._updatePointDisplay();
    }

    _q(selector) { return this.root.querySelector(selector); }
    _qa(selector) { return this.root.querySelectorAll(selector); }

    /**
     * Slider labels show the raw allocated points only. The actual resolved game
     * values (incl. bullet count, coupling effects, etc.) come from the server
     * via /api/weapon-customization/resolve and are shown in the Resulting Stats
     * panel — no weapon math is computed client-side.
     */
    _formatAttrValue(key, points) {
        return `${points} pt${points === 1 || points === -1 ? '' : 's'}`;
    }

    _createAttributeSliders() {
        const container = this._q('#attribute-sliders');
        container.innerHTML = '';
        Object.entries(this.weaponData.attributes).forEach(([key, attr]) => {
            const step = key === 'BULLETS_PER_SHOT' ? 'step="5"' : '';
            const sliderDiv = document.createElement('div');
            sliderDiv.className = 'attribute-slider';
            sliderDiv.innerHTML = `
                <label for="attr-${key}">${attr.displayName}</label>
                <div class="slider-container">
                    <input type="range" id="attr-${key}" class="slider"
                           min="${attr.min}" max="${attr.max}" value="0" ${step}
                           data-attribute="${key}">
                    <span class="slider-value" id="value-${key}">${this._formatAttrValue(key, 0)}</span>
                </div>
            `;
            container.appendChild(sliderDiv);
            const slider = sliderDiv.querySelector('.slider');
            const valueSpan = sliderDiv.querySelector('.slider-value');
            slider.addEventListener('input', (e) => {
                const value = parseInt(e.target.value, 10);
                valueSpan.textContent = this._formatAttrValue(key, value);
                this.currentWeapon.attributes[key] = value;
                this._updatePointDisplay();
            });
        });
    }

    _createEffectCheckboxes() {
        const container = this._q('#effect-checkboxes');
        container.innerHTML = '';
        this.weaponData.effects.forEach(effect => {
            const div = document.createElement('div');
            div.className = 'effect-checkbox';
            div.innerHTML = `
                <input type="checkbox" id="effect-${effect.name}" data-effect="${effect.name}">
                <div class="effect-info">
                    <div class="effect-name">${effect.displayName}</div>
                    <div class="effect-description">${effect.description}</div>
                </div>
                <div class="effect-cost">${effect.cost} pts</div>
            `;
            container.appendChild(div);
            const checkbox = div.querySelector('input[type="checkbox"]');
            checkbox.addEventListener('change', (e) => {
                if (e.target.checked) {
                    if (!this.currentWeapon.effects.includes(effect.name)) {
                        this.currentWeapon.effects.push(effect.name);
                    }
                } else {
                    this.currentWeapon.effects = this.currentWeapon.effects.filter(eff => eff !== effect.name);
                }
                this._updatePointDisplay();
            });
            div.addEventListener('click', (e) => {
                if (e.target.type !== 'checkbox') checkbox.click();
            });
        });
    }

    _createOrdinanceRadios() {
        const container = this._q('#ordinance-radios');
        container.innerHTML = '';
        this.weaponData.ordinances.forEach(ord => {
            const div = document.createElement('div');
            div.className = 'ordinance-option';
            div.innerHTML = `
                <input type="radio" name="ordinance" id="ord-${ord.name}"
                       value="${ord.name}" ${ord.name === 'PROJECTILE' ? 'checked' : ''}>
                <div class="ordinance-info">
                    <div class="ordinance-name">${ord.displayName}</div>
                    <div class="ordinance-description">${ord.description}</div>
                </div>
                <div class="ordinance-cost">${ord.cost} pts</div>
            `;
            container.appendChild(div);
            const radio = div.querySelector('input[type="radio"]');
            radio.addEventListener('change', (e) => {
                if (e.target.checked) {
                    this.currentWeapon.ordinance = ord.name;
                    this._applyOrdinanceEffectGating();
                    this._updatePointDisplay();
                }
            });
            div.addEventListener('click', (e) => {
                if (e.target.type !== 'radio') radio.click();
            });
        });
    }

    _createUtilityWeaponSelection() {
        const container = this._q('#utility-weapon-selection');
        container.innerHTML = '';
        if (!this.weaponData.utilityWeapons) {
            console.error('No utility weapon data available');
            return;
        }
        this.weaponData.utilityWeapons.forEach(utility => {
            const div = document.createElement('div');
            div.className = 'utility-weapon-option';
            div.innerHTML = `
                <input type="radio" name="utilityWeapon" id="utility-${utility.name}"
                       value="${utility.name}"
                       ${utility.name === this.currentUtilityWeapon ? 'checked' : ''}>
                <div class="utility-info">
                    <div class="utility-name">${utility.displayName}</div>
                    <div class="utility-category">${utility.category}</div>
                    <div class="utility-description">${utility.description}</div>
                </div>
                <div class="utility-cooldown">${utility.cooldown}s</div>
            `;
            container.appendChild(div);
            const radio = div.querySelector('input[type="radio"]');
            radio.addEventListener('change', (e) => {
                if (e.target.checked) {
                    this.currentUtilityWeapon = utility.name;
                    this._saveConfiguration();
                }
            });
            div.addEventListener('click', (e) => {
                if (e.target.type !== 'radio') radio.click();
            });
        });
    }

    _createPresetButtons() {
        const categories = {
            kinetic: ['ASSAULT_RIFLE', 'HAND_CANNON', 'SNIPER_RIFLE', 'MINIGUN', 'SHOTGUN', 'TWIN_SIXES'],
            effects: ['ROCKET_LAUNCHER', 'INCENDIARY_SHOTGUN', 'ARC_PISTOL', 'ICE_CANNON', 'TOXIC_SPRAYER',
                      'PIERCING_RIFLE', 'BOUNCY_SMG', 'SEEKER_DART', 'CLUSTER_MORTAR',
                      'NAPALM_LAUNCHER', 'STORM_CALLER', 'VENOM_NEEDLER', 'FROST_LANCE', 'SHRAPNEL_CANNON', 'PHANTOM_NEEDLES'],
            beams: ['LASER_RIFLE', 'PLASMA_CANNON', 'ARC_LASER', 'RAILGUN']
        };
        Object.entries(categories).forEach(([categoryName, presetKeys]) => {
            const container = this._q(`#preset-buttons-${categoryName}`);
            container.innerHTML = '';
            presetKeys.forEach(key => {
                const preset = this.weaponData.presets[key];
                if (!preset) return;
                const button = document.createElement('div');
                button.className = 'preset-button';
                button.innerHTML = `
                    <div class="preset-name">${preset.displayName}</div>
                    <div class="preset-points">${preset.totalPoints}/100</div>
                `;
                button.addEventListener('click', () => this._applyPreset(preset));
                container.appendChild(button);
            });
        });
        this._qa('.preset-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                this._qa('.preset-tab').forEach(t => t.classList.remove('active'));
                this._qa('.preset-category').forEach(c => c.classList.remove('active'));
                tab.classList.add('active');
                this._q(`#category-${tab.dataset.category}`).classList.add('active');
            });
        });
    }

    _applyPreset(preset) {
        this._resetToDefaults();
        Object.entries(preset.attributes).forEach(([key, value]) => {
            const slider = this._q(`#attr-${key}`);
            const valueSpan = this._q(`#value-${key}`);
            if (slider && valueSpan) {
                slider.value = value;
                // Read back the snapped value (browser clamps to step)
                const snapped = parseInt(slider.value, 10);
                this.currentWeapon.attributes[key] = snapped;
                valueSpan.textContent = this._formatAttrValue(key, snapped);
            } else {
                this.currentWeapon.attributes[key] = value;
            }
        });
        this.currentWeapon.effects = [...preset.effects];
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (checkbox) checkbox.checked = preset.effects.includes(effect.name);
        });
        this.currentWeapon.ordinance = preset.ordinance;
        const radio = this._q(`#ord-${preset.ordinance}`);
        if (radio) radio.checked = true;
        this._applyOrdinanceEffectGating();
        this._updatePointDisplay();
    }

    /**
     * Disable bullet effects that don't apply to the current ordnance: beam
     * ordnance can't use the flight-only behaviors (HOMING/BOUNCY/FRAGMENTING).
     * Forbidden effects are unchecked, removed from the loadout, and dimmed. The
     * server's BulletEffect.validFor() enforces the same rule — this is the UX
     * mirror so points are never spent on an inert effect.
     */
    _applyOrdinanceEffectGating() {
        const ord = this.weaponData.ordinances.find(o => o.name === this.currentWeapon.ordinance);
        const isBeam = !!(ord && ord.beam);
        let changed = false;
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (!checkbox) return;
            const row = checkbox.closest('.effect-checkbox');
            const forbidden = isBeam && !effect.validForBeams;
            checkbox.disabled = forbidden;
            if (row) {
                row.classList.toggle('disabled', forbidden);
                row.title = forbidden ? 'Not available for beam weapons' : '';
            }
            if (forbidden && checkbox.checked) {
                checkbox.checked = false;
                this.currentWeapon.effects = this.currentWeapon.effects.filter(e => e !== effect.name);
                changed = true;
            }
        });
        if (changed) this._updatePointDisplay();
    }

    _resetToDefaults() {
        this.currentWeapon = { attributes: {}, effects: [], ordinance: 'PROJECTILE' };
        Object.keys(this.weaponData.attributes).forEach(key => {
            this.currentWeapon.attributes[key] = 0;
            const slider = this._q(`#attr-${key}`);
            const valueSpan = this._q(`#value-${key}`);
            if (slider && valueSpan) {
                slider.value = 0;
                valueSpan.textContent = this._formatAttrValue(key, 0);
            }
        });
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (checkbox) checkbox.checked = false;
        });
        const projectileRadio = this._q('#ord-PROJECTILE');
        if (projectileRadio) projectileRadio.checked = true;
        this._applyOrdinanceEffectGating();
    }

    _totalPoints() {
        const attrPoints = Object.values(this.currentWeapon.attributes).reduce((sum, val) => sum + val, 0);
        const effectPoints = this.currentWeapon.effects.reduce((sum, effectName) => {
            const effect = this.weaponData.effects.find(e => e.name === effectName);
            return sum + (effect ? effect.cost : 0);
        }, 0);
        const ordinance = this.weaponData.ordinances.find(o => o.name === this.currentWeapon.ordinance);
        const ordinancePoints = ordinance ? ordinance.cost : 0;
        return attrPoints + effectPoints + ordinancePoints;
    }

    _updatePointDisplay() {
        const attrPoints = Object.values(this.currentWeapon.attributes).reduce((sum, val) => sum + val, 0);
        const effectPoints = this.currentWeapon.effects.reduce((sum, effectName) => {
            const effect = this.weaponData.effects.find(e => e.name === effectName);
            return sum + (effect ? effect.cost : 0);
        }, 0);
        const ordinance = this.weaponData.ordinances.find(o => o.name === this.currentWeapon.ordinance);
        const ordinancePoints = ordinance ? ordinance.cost : 0;

        const totalPoints = attrPoints + effectPoints + ordinancePoints;
        const maxPoints = this.weaponData.maxPoints;

        const pointsUsedEl = this._q('#points-used');
        const iconEl       = this._q('#alloc-icon');

        pointsUsedEl.textContent = totalPoints;
        this._q('#points-max').textContent = maxPoints;
        this._q('#attr-points').textContent = attrPoints;
        this._q('#effect-points').textContent = effectPoints;
        this._q('#ordinance-points').textContent = ordinancePoints;

        let valid;
        if (totalPoints > maxPoints) {
            pointsUsedEl.className = 'pt-used points-over';
            iconEl.textContent = '❗';
            iconEl.className = 'alloc-icon alloc-over';
            valid = false;
        } else if (totalPoints < maxPoints) {
            pointsUsedEl.className = 'pt-used points-under';
            iconEl.textContent = '⚠';
            iconEl.className = 'alloc-icon alloc-under';
            valid = true;
        } else {
            pointsUsedEl.className = 'pt-used points-used';
            iconEl.textContent = '✓';
            iconEl.className = 'alloc-icon alloc-perfect';
            valid = true;
        }

        if (totalPoints <= maxPoints) this._saveConfiguration();

        if (valid !== this._lastValid) {
            this._lastValid = valid;
            try {
                this.onValidityChange(valid);
            } catch (e) {
                console.error('onValidityChange threw:', e);
            }
        }

        // Ask the server for the real resolved stats (debounced).
        this._scheduleResolve();
    }

    /** Debounced request for the server-resolved end-result stats. */
    _scheduleResolve() {
        if (this._resolveTimer) clearTimeout(this._resolveTimer);
        this._resolveTimer = setTimeout(() => this._fetchResolved(), 150);
    }

    async _fetchResolved() {
        const panel = this._q('#resolved-stats');
        if (!panel) return;
        try {
            const { weaponConfig } = this.getPlayerConfig();
            const resp = await fetch('/api/weapon-customization/resolve', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(weaponConfig)
            });
            if (!resp.ok) { panel.textContent = 'Could not resolve loadout.'; return; }
            this._renderResolved(panel, await resp.json());
        } catch (e) {
            panel.textContent = 'Could not resolve loadout.';
        }
    }

    /** Render the server's resolved stats: end values, couplings, derived combat numbers. */
    _renderResolved(panel, data) {
        const attrs = data.attributes || {};
        const order = ['DAMAGE', 'FIRE_RATE', 'BULLETS_PER_SHOT', 'MAGAZINE_SIZE', 'RELOAD_TIME',
                       'RANGE', 'PROJECTILE_SPEED', 'ACCURACY', 'HANDLING', 'LINEAR_DAMPING'];
        const statRows = order.filter(k => attrs[k]).map(k => {
            const a = attrs[k];
            // When a stat is coupled, show its un-coupled base for context (unit-correct).
            const tag = a.coupled ? ` <span class="coupling-tag">(base ${a.baseDisplay})</span>` : '';
            return `<div class="rs-row"><span class="rs-label">${a.label}</span><span class="rs-val">${a.display}${tag}</span></div>`;
        }).join('');

        const d = data.derived || {};
        const derivedRows = `
            <div class="rs-row"><span class="rs-label">DPS</span><span class="rs-val">${Math.round(d.dps || 0)}</span></div>
            <div class="rs-row"><span class="rs-label">Damage / bullet</span><span class="rs-val">${(d.damagePerBullet || 0).toFixed(1)}</span></div>
            <div class="rs-row"><span class="rs-label">Move speed</span><span class="rs-val">${Math.round((d.moveSpeedMultiplier || 1) * 100)}%</span></div>`;

        const couplings = (data.couplings || []).map(c =>
            `<div class="rs-coupling ${c.delta > 0 ? 'up' : 'down'}">${c.label}: ${c.display}</div>`
        ).join('') || '<div class="rs-coupling none">No couplings active</div>';

        const b = data.budget || {};
        panel.innerHTML = `
            <div class="rs-section">${statRows}</div>
            <div class="rs-section rs-derived">${derivedRows}</div>
            <div class="rs-section"><div class="rs-subhead">Active couplings</div>${couplings}</div>
            <div class="rs-budget ${data.valid ? 'ok' : 'over'}">${b.total}/${b.max} pts${data.valid ? '' : ' — over budget'}</div>`;
    }

    _saveConfiguration() {
        try {
            const config = {
                weapon: this.currentWeapon,
                utilityWeapon: this.currentUtilityWeapon,
                timestamp: Date.now()
            };
            localStorage.setItem('weaponConfig', JSON.stringify(config));
        } catch (error) {
            console.error('Failed to save configuration:', error);
        }
    }

    _loadSavedConfiguration() {
        try {
            const saved = localStorage.getItem('weaponConfig');
            if (!saved) return false;
            const config = JSON.parse(saved);
            if (!config.weapon || !config.weapon.attributes || !config.weapon.effects || !config.weapon.ordinance) {
                return false;
            }
            // Coerce a stale/removed ordinance (e.g. a legacy 'BULLET'/'ROCKET' in
            // localStorage) to the default so it stays valid after the collapse.
            const validOrdinances = (this.weaponData.ordinances || []).map(o => o.name);
            const savedOrdinance = validOrdinances.includes(config.weapon.ordinance)
                ? config.weapon.ordinance : 'PROJECTILE';
            this.currentWeapon = {
                attributes: { ...config.weapon.attributes },
                effects: [...config.weapon.effects],
                ordinance: savedOrdinance
            };
            if (config.utilityWeapon) {
                this.currentUtilityWeapon = config.utilityWeapon;
            }
            this._applyConfigurationToUI();
            return true;
        } catch (error) {
            console.error('Failed to load saved configuration:', error);
            return false;
        }
    }

    _applyConfigurationToUI() {
        Object.entries(this.currentWeapon.attributes).forEach(([key, value]) => {
            const slider = this._q(`#attr-${key}`);
            const valueSpan = this._q(`#value-${key}`);
            if (slider && valueSpan) {
                slider.value = value;
                // Read back the snapped value so currentWeapon stays consistent
                // with whatever the browser rounded to (relevant for BULLETS_PER_SHOT step=5)
                const snapped = parseInt(slider.value, 10);
                this.currentWeapon.attributes[key] = snapped;
                valueSpan.textContent = this._formatAttrValue(key, snapped);
            }
        });
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (checkbox) checkbox.checked = this.currentWeapon.effects.includes(effect.name);
        });
        const radio = this._q(`#ord-${this.currentWeapon.ordinance}`);
        if (radio) radio.checked = true;
        this._applyOrdinanceEffectGating();
        const utilityRadio = this._q(`#utility-${this.currentUtilityWeapon}`);
        if (utilityRadio) utilityRadio.checked = true;
    }
}

// Expose globally for non-module consumers (game.html loads this with a plain <script>)
window.WeaponCustomizer = WeaponCustomizer;

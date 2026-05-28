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
        this.currentWeapon = { attributes: {}, effects: [], ordinance: 'BULLET' };
        this.currentUtilityWeapon = 'HEAL_ZONE';
        this._lastValid = false;
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
            'LINEAR_DAMPING': 'linearDamping'
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
                <div class="point-tracker">
                    <div class="point-display">
                        <span id="points-used">0</span> / <span id="points-max">100</span> Points
                    </div>
                    <div id="validation-message" class="validation-message hidden"></div>
                    <div class="point-breakdown">
                        <div class="breakdown-item">
                            <span class="breakdown-label">Attributes</span>
                            <span class="breakdown-value" id="attr-points">0</span>
                        </div>
                        <div class="breakdown-item">
                            <span class="breakdown-label">Effects</span>
                            <span class="breakdown-value" id="effect-points">0</span>
                        </div>
                        <div class="breakdown-item">
                            <span class="breakdown-label">Ordinance</span>
                            <span class="breakdown-value" id="ordinance-points">0</span>
                        </div>
                    </div>
                </div>

                <div id="loading-message" class="loading">
                    Loading weapon customization data...
                </div>

                <div id="customization-content" class="hidden">
                    <div class="preset-section">
                        <h3>Weapon Presets</h3>
                        <div class="preset-tabs">
                            <button type="button" class="preset-tab active" data-category="basic">Basic</button>
                            <button type="button" class="preset-tab" data-category="effects">Effects</button>
                            <button type="button" class="preset-tab" data-category="explosive">Explosive</button>
                            <button type="button" class="preset-tab" data-category="beam">Beam</button>
                            <button type="button" class="preset-tab" data-category="combo">Combo</button>
                        </div>
                        <div class="preset-category active" id="category-basic">
                            <div class="preset-buttons" id="preset-buttons-basic"></div>
                        </div>
                        <div class="preset-category" id="category-effects">
                            <div class="preset-buttons" id="preset-buttons-effects"></div>
                        </div>
                        <div class="preset-category" id="category-explosive">
                            <div class="preset-buttons" id="preset-buttons-explosive"></div>
                        </div>
                        <div class="preset-category" id="category-beam">
                            <div class="preset-buttons" id="preset-buttons-beam"></div>
                        </div>
                        <div class="preset-category" id="category-combo">
                            <div class="preset-buttons" id="preset-buttons-combo"></div>
                        </div>
                    </div>

                    <div class="customization-grid">
                        <div class="customization-section">
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
        this._updatePointDisplay();
    }

    _q(selector) { return this.root.querySelector(selector); }
    _qa(selector) { return this.root.querySelectorAll(selector); }

    _createAttributeSliders() {
        const container = this._q('#attribute-sliders');
        container.innerHTML = '';
        Object.entries(this.weaponData.attributes).forEach(([key, attr]) => {
            const sliderDiv = document.createElement('div');
            sliderDiv.className = 'attribute-slider';
            sliderDiv.innerHTML = `
                <label for="attr-${key}">${attr.displayName}</label>
                <div class="slider-container">
                    <input type="range" id="attr-${key}" class="slider"
                           min="${attr.min}" max="${attr.max}" value="0"
                           data-attribute="${key}">
                    <span class="slider-value" id="value-${key}">0</span>
                </div>
            `;
            container.appendChild(sliderDiv);
            const slider = sliderDiv.querySelector('.slider');
            const valueSpan = sliderDiv.querySelector('.slider-value');
            slider.addEventListener('input', (e) => {
                const value = parseInt(e.target.value, 10);
                valueSpan.textContent = value;
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
                       value="${ord.name}" ${ord.name === 'BULLET' ? 'checked' : ''}>
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
            basic: ['ASSAULT_RIFLE', 'HAND_CANNON', 'SNIPER_RIFLE', 'PLASMA_RIFLE', 'TWIN_SIXES', 'PRECISION_DART_GUN', 'FLAME_PROJECTOR', 'MINIGUN'],
            effects: ['BOUNCY_SMG', 'PIERCING_RIFLE', 'INCENDIARY_SHOTGUN', 'SEEKER_DART', 'ARC_PISTOL', 'TOXIC_SPRAYER', 'ICE_CANNON', 'RICOCHET_RIFLE'],
            explosive: ['EXPLOSIVE_SNIPER', 'ROCKET_LAUNCHER', 'GRENADE_LAUNCHER', 'CLUSTER_MORTAR'],
            beam: ['LASER_RIFLE', 'PLASMA_CANNON', 'MEDIC_BEAM', 'RAIL_CANNON'],
            combo: ['STORM_CALLER', 'NAPALM_LAUNCHER', 'CRYO_SHOTGUN', 'VENOM_NEEDLER', 'THUNDERBOLT_CANNON',
                    'PLAGUE_MORTAR', 'WILDFIRE_SPRAYER', 'FROST_LANCE', 'SHRAPNEL_CANNON',
                    'SEEKING_INFERNO', 'EMP_BURST_GUN', 'GLACIAL_MORTAR', 'PHANTOM_NEEDLES', 'CORROSIVE_CANNON']
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
            this.currentWeapon.attributes[key] = value;
            const slider = this._q(`#attr-${key}`);
            const valueSpan = this._q(`#value-${key}`);
            if (slider && valueSpan) {
                slider.value = value;
                valueSpan.textContent = value;
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
        this._updatePointDisplay();
    }

    _resetToDefaults() {
        this.currentWeapon = { attributes: {}, effects: [], ordinance: 'BULLET' };
        Object.keys(this.weaponData.attributes).forEach(key => {
            this.currentWeapon.attributes[key] = 0;
            const slider = this._q(`#attr-${key}`);
            const valueSpan = this._q(`#value-${key}`);
            if (slider && valueSpan) {
                slider.value = 0;
                valueSpan.textContent = 0;
            }
        });
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (checkbox) checkbox.checked = false;
        });
        const bulletRadio = this._q('#ord-BULLET');
        if (bulletRadio) bulletRadio.checked = true;
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

        this._q('#points-used').textContent = totalPoints;
        this._q('#points-max').textContent = maxPoints;
        this._q('#attr-points').textContent = attrPoints;
        this._q('#effect-points').textContent = effectPoints;
        this._q('#ordinance-points').textContent = ordinancePoints;

        const pointsUsed = this._q('#points-used');
        const validation = this._q('#validation-message');

        pointsUsed.className = '';
        validation.className = 'validation-message';

        let valid;
        if (totalPoints > maxPoints) {
            pointsUsed.classList.add('points-over');
            validation.classList.add('validation-error');
            validation.textContent = `Over budget by ${totalPoints - maxPoints} points. Reduce allocations.`;
            validation.classList.remove('hidden');
            valid = false;
        } else if (totalPoints < maxPoints) {
            pointsUsed.classList.add('points-under');
            validation.classList.add('validation-warning');
            validation.textContent = `${maxPoints - totalPoints} points remaining.`;
            validation.classList.remove('hidden');
            valid = true;
        } else {
            pointsUsed.classList.add('points-used');
            validation.classList.add('validation-success');
            validation.textContent = 'Perfect allocation.';
            validation.classList.remove('hidden');
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
            this.currentWeapon = {
                attributes: { ...config.weapon.attributes },
                effects: [...config.weapon.effects],
                ordinance: config.weapon.ordinance
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
                valueSpan.textContent = value;
            }
        });
        this.weaponData.effects.forEach(effect => {
            const checkbox = this._q(`#effect-${effect.name}`);
            if (checkbox) checkbox.checked = this.currentWeapon.effects.includes(effect.name);
        });
        const radio = this._q(`#ord-${this.currentWeapon.ordinance}`);
        if (radio) radio.checked = true;
        const utilityRadio = this._q(`#utility-${this.currentUtilityWeapon}`);
        if (utilityRadio) utilityRadio.checked = true;
    }
}

// Expose globally for non-module consumers (game.html loads this with a plain <script>)
window.WeaponCustomizer = WeaponCustomizer;

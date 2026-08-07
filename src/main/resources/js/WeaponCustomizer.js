/**
 * Weapon customization UI — render-from-state, no dependencies.
 *
 * Renders the loadout picker (attribute sliders, bullet-effect checkboxes,
 * ordnance/utility dropdowns, preset chooser, point tracker) into a host
 * container. The host page owns the "Ready" button and listens for validity
 * changes via `onValidityChange`. Element IDs and CSS class names match
 * unified.css, and #name-select/#name-randomize are populated by the host.
 *
 * Architecture — state is the single source of truth:
 *
 *     state is authoritative → every interaction mutates state and calls
 *     _commit() → _commit() normalizes state, then _render() syncs the DOM to
 *     match. Handlers NEVER touch the DOM directly.
 *
 * Consequences:
 *   - _applyPreset / load / reset just assign state and _commit() — no hand-poking
 *     of individual sliders/checkboxes/selects.
 *   - All validation (stale-ordnance coercion, step-snapping, dropping effects
 *     forbidden by the current ordnance) lives in one place: _normalizeState().
 *   - There is exactly one DOM-writing path (_render()), so "set the value but
 *     forget the detail line / gating / budget" desync bugs can't occur.
 *
 * Build-once + sync-update (not innerHTML-rebuild) means sliders/selects keep
 * focus and don't get torn down mid-drag — _render() only sets .value/.checked/
 * .disabled/text on nodes that already exist, which is idempotent and cheap.
 *
 * Usage:
 *   const customizer = new WeaponCustomizer(rootEl, {
 *     onValidityChange: (isValid) => { readyButton.disabled = !isValid; }
 *   });
 *   await customizer.init();                     // fetches /api/weapon-customization
 *   const cfg = customizer.getPlayerConfig();    // { weaponConfig, utilityWeapon }
 */
class WeaponCustomizer {
    constructor(rootEl, options = {}) {
        if (!rootEl) {
            throw new Error('WeaponCustomizer requires a root element');
        }
        this.root = rootEl;
        this.onValidityChange = options.onValidityChange || (() => {});

        this.weaponData = null;

        // THE state. currentWeapon/currentUtilityWeapon in the old version were the
        // same data, just spread across two fields and mutated in many places.
        this.state = {
            attributes: {},          // { MIN_DAMAGE: 0, MAX_DAMAGE: 0, FIRE_RATE: 0, ... }
            effects: [],             // ['EXPLOSIVE', ...]
            ordinance: 'PROJECTILE',
            varianceFormula: 'UNIFORM',
            utility: 'HEAL_ZONE'
        };

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

    isValid() {
        if (!this.weaponData) return false;
        return this._totalPoints() <= this.weaponData.maxPoints;
    }

    /**
     * Build the weapon-config payload the server expects. Reads straight from
     * state — the DOM is never consulted, because state is authoritative.
     */
    getPlayerConfig() {
        const attributeMapping = {
            'MIN_DAMAGE': 'minDamage', 'MAX_DAMAGE': 'maxDamage', 'FIRE_RATE': 'fireRate', 'RANGE': 'range',
            'ACCURACY': 'accuracy', 'MAGAZINE_SIZE': 'magazineSize', 'RELOAD_TIME': 'reloadTime',
            'PROJECTILE_SPEED': 'projectileSpeed', 'BULLETS_PER_SHOT': 'bulletsPerShot',
            'LINEAR_DAMPING': 'linearDamping', 'HANDLING': 'handling', 'CALIBER': 'caliber',
            'KNOCKBACK': 'knockback'
        };
        const mappedAttributes = {};
        Object.entries(this.state.attributes).forEach(([enumName, value]) => {
            const fieldName = attributeMapping[enumName];
            if (fieldName) mappedAttributes[fieldName] = value;
        });
        const weaponConfig = {
            type: 'Custom Weapon',
            bulletEffects: this.state.effects,
            ordinance: this.state.ordinance,
            varianceFormula: this.state.varianceFormula,
            ...mappedAttributes
        };
        return { weaponConfig, utilityWeapon: this.state.utility };
    }

    // ===================================================================
    //  Shell (static structure) — identical markup so CSS/host hooks line up
    // ===================================================================

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
                                    <span class="pt-breakdown-label">Var:</span><span id="variance-points" class="pt-breakdown-value">0</span>
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
                            <h3>Munitions & Variance</h3>
                            <div class="munitions-field">
                                <label class="munitions-label" for="ordinance-select">Ordnance Type</label>
                                <select id="ordinance-select" class="loadout-select" aria-label="Ordnance type"></select>
                                <div id="ordinance-detail" class="munitions-detail"></div>
                            </div>
                            <div class="munitions-field">
                                <label class="munitions-label" for="variance-select">Damage Variance Formula</label>
                                <select id="variance-select" class="loadout-select" aria-label="Damage variance formula"></select>
                                <div id="variance-detail" class="munitions-detail"></div>
                            </div>
                            <div class="munitions-field">
                                <label class="munitions-label" for="utility-select">Utility Weapon</label>
                                <select id="utility-select" class="loadout-select" aria-label="Utility weapon"></select>
                                <div id="utility-detail" class="munitions-detail"></div>
                            </div>
                        </div>
                        <div class="customization-section rs-rail">
                            <h3>Resulting Stats</h3>
                            <div id="resolved-stats" class="resolved-stats">Adjust your loadout to preview…</div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    _q(selector) { return this.root.querySelector(selector); }
    _qa(selector) { return this.root.querySelectorAll(selector); }

    _initializeUI() {
        this._q('#loading-message').classList.add('hidden');
        this._q('#customization-content').classList.remove('hidden');

        // Build the controls ONCE; listeners only mutate state + _commit().
        this._buildAttributeSliders();
        this._buildEffectCheckboxes();
        this._buildOrdinanceOptions();
        this._buildVarianceOptions();
        this._buildUtilityOptions();
        this._buildPresetButtons();

        // Seed from saved config if present (state only — no DOM poking).
        this._loadSavedConfiguration();

        // First paint. _commit() normalizes (fills defaults, coerces stale
        // ordinance, drops forbidden effects, snaps steps) then renders.
        this._commit();
    }

    // ===================================================================
    //  Build controls once (structure + listeners; NO value-setting here)
    // ===================================================================

    _buildAttributeSliders() {
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
                    <span class="slider-value" id="value-${key}"></span>
                </div>
            `;
            container.appendChild(sliderDiv);
            sliderDiv.querySelector('.slider').addEventListener('input', (e) => {
                this.state.attributes[key] = parseInt(e.target.value, 10);
                this._commit();
            });
        });
    }

    _buildEffectCheckboxes() {
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
            checkbox.addEventListener('change', () => {
                const set = new Set(this.state.effects);
                if (checkbox.checked) set.add(effect.name); else set.delete(effect.name);
                this.state.effects = [...set];
                this._commit();
            });
            div.addEventListener('click', (e) => {
                if (e.target.type !== 'checkbox') checkbox.click();
            });
        });
    }

    _buildOrdinanceOptions() {
        const select = this._q('#ordinance-select');
        if (!select) return;
        select.innerHTML = '';
        this.weaponData.ordinances.forEach(ord => {
            const opt = document.createElement('option');
            opt.value = ord.name;
            opt.textContent = ord.cost > 0 ? `${ord.displayName} — ${ord.cost} pts` : ord.displayName;
            select.appendChild(opt);
        });
        select.addEventListener('change', () => {
            this.state.ordinance = select.value;
            this._commit();
        });
    }

    _buildVarianceOptions() {
        const select = this._q('#variance-select');
        if (!select || !this.weaponData.varianceFormulas) return;
        select.innerHTML = '';
        this.weaponData.varianceFormulas.forEach(vf => {
            const opt = document.createElement('option');
            opt.value = vf.name;
            opt.textContent = vf.cost !== 0 ? `${vf.displayName} — ${vf.cost > 0 ? '+' : ''}${vf.cost} pts` : vf.displayName;
            select.appendChild(opt);
        });
        select.addEventListener('change', () => {
            this.state.varianceFormula = select.value;
            this._commit();
        });
    }

    _buildUtilityOptions() {
        const select = this._q('#utility-select');
        if (!select) return;
        if (!this.weaponData.utilityWeapons) {
            console.error('No utility weapon data available');
            return;
        }
        select.innerHTML = '';
        this.weaponData.utilityWeapons.forEach(utility => {
            const opt = document.createElement('option');
            opt.value = utility.name;
            opt.textContent = utility.displayName;
            select.appendChild(opt);
        });
        select.addEventListener('change', () => {
            this.state.utility = select.value;
            this._commit();
        });
    }

    _buildPresetButtons() {
        const categories = {
            kinetic: ['ASSAULT_RIFLE', 'HAND_CANNON', 'SNIPER_RIFLE', 'MINIGUN', 'SHOTGUN', 'TWIN_SIXES', 'CONCUSSION_CANNON'],
            effects: ['ROCKET_LAUNCHER', 'INCENDIARY_SHOTGUN', 'ARC_PISTOL', 'ICE_CANNON', 'TOXIC_SPRAYER',
                      'PIERCING_RIFLE', 'BOUNCY_SMG', 'SEEKER_DART', 'CLUSTER_MORTAR',
                      'NAPALM_LAUNCHER', 'STORM_CALLER', 'VENOM_NEEDLER', 'FROST_LANCE', 'SHRAPNEL_CANNON', 'PHANTOM_NEEDLES'],
            beams: ['LASER_RIFLE', 'PLASMA_CANNON', 'ARC_LASER', 'RAILGUN', 'RICOCHET_LASER']
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
        // Preset tabs are pure presentation (which category is visible) — not
        // loadout state, so they stay direct DOM toggles.
        this._qa('.preset-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                this._qa('.preset-tab').forEach(t => t.classList.remove('active'));
                this._qa('.preset-category').forEach(c => c.classList.remove('active'));
                tab.classList.add('active');
                this._q(`#category-${tab.dataset.category}`).classList.add('active');
            });
        });
    }

    /** Apply a preset = replace loadout state, then commit. No DOM poking. */
    _applyPreset(preset) {
        this.state.attributes = { ...preset.attributes }; // _normalize fills the rest with 0
        this.state.effects = [...preset.effects];
        this.state.ordinance = preset.ordinance;
        this.state.varianceFormula = preset.varianceFormula || 'UNIFORM';
        this._commit();
    }

    /** Mutate-then-commit: normalize state, render it, persist + resolve. */
    _commit() {
        this._normalizeState();
        this._render();
        if (this.isValid()) this._save();
        this._scheduleResolve();
    }

    /**
     * Make state internally consistent. This is the ONE place all the validation
     * that used to be scattered across load/preset/gating now lives:
     *   - every known attribute present, snapped to its step, clamped to range
     *   - ordinance coerced to a valid value (legacy/stale → PROJECTILE)
     *   - effects filtered to known + valid-for-current-ordnance (beam gating)
     *   - utility coerced to a valid value
     */
    _normalizeState() {
        const d = this.weaponData;

        // Ordnance first, so attribute/effect gating below can depend on it.
        const ordNames = d.ordinances.map(o => o.name);
        if (!ordNames.includes(this.state.ordinance)) this.state.ordinance = 'PROJECTILE';
        const ord = d.ordinances.find(o => o.name === this.state.ordinance);
        const isBeam = !!(ord && ord.beam);

        // Attributes: snap to step, clamp to range, and zero any that don't apply
        // to the current ordnance (e.g. KNOCKBACK on a beam).
        const attrs = {};
        Object.entries(d.attributes).forEach(([key, meta]) => {
            const step = key === 'BULLETS_PER_SHOT' ? 5 : 1;
            let v = Number(this.state.attributes[key]) || 0;
            v = Math.round(v / step) * step;
            v = Math.max(meta.min, Math.min(meta.max, v));
            if (isBeam && meta.validForBeams === false) v = 0;
            attrs[key] = v;
        });
        this.state.attributes = attrs;

        this.state.effects = this.state.effects.filter(name => {
            const e = d.effects.find(x => x.name === name);
            if (!e) return false;                       // unknown effect
            return !(isBeam && !e.validForBeams);       // beam-forbidden
        });

        const vfNames = (d.varianceFormulas || []).map(v => v.name);
        if (vfNames.length && !vfNames.includes(this.state.varianceFormula)) {
            this.state.varianceFormula = vfNames[0];
        }

        const utilNames = (d.utilityWeapons || []).map(u => u.name);
        if (utilNames.length && !utilNames.includes(this.state.utility)) {
            this.state.utility = utilNames[0];
        }
    }

    /** Pure DOM sync: DOM ← state. Idempotent; mutates no state. */
    _render() {
        const d = this.weaponData;

        // Ordnance drives both attribute and effect gating below.
        const ord = d.ordinances.find(o => o.name === this.state.ordinance);
        const isBeam = !!(ord && ord.beam);

        // Attribute sliders + labels (gate off projectile-only attrs on beams).
        Object.entries(d.attributes).forEach(([key, meta]) => {
            const slider = this._q(`#attr-${key}`);
            const label = this._q(`#value-${key}`);
            const v = this.state.attributes[key];
            const gated = isBeam && meta.validForBeams === false;
            if (slider) {
                slider.value = v;
                slider.disabled = gated;
                const row = slider.closest('.attribute-slider');
                if (row) {
                    row.classList.toggle('disabled', gated);
                    row.title = gated ? 'Not applicable to beam weapons' : '';
                }
            }
            if (label) label.textContent = this._formatAttrValue(key, v);
        });

        // Effect checkboxes (checked + beam gating)
        d.effects.forEach(effect => {
            const cb = this._q(`#effect-${effect.name}`);
            if (!cb) return;
            const forbidden = isBeam && !effect.validForBeams;
            cb.checked = this.state.effects.includes(effect.name);
            cb.disabled = forbidden;
            const row = cb.closest('.effect-checkbox');
            if (row) {
                row.classList.toggle('disabled', forbidden);
                row.title = forbidden ? 'Not available for beam weapons' : '';
            }
        });

        // Munitions dropdowns + detail lines
        const ordSelect = this._q('#ordinance-select');
        if (ordSelect) ordSelect.value = this.state.ordinance;
        const ordDetail = this._q('#ordinance-detail');
        if (ordDetail) ordDetail.textContent = ord ? ord.description : '';

        const varSelect = this._q('#variance-select');
        if (varSelect) varSelect.value = this.state.varianceFormula;
        const varDetail = this._q('#variance-detail');
        const vf = (d.varianceFormulas || []).find(x => x.name === this.state.varianceFormula);
        if (varDetail) varDetail.textContent = vf ? `${vf.description}` : '';

        const utilSelect = this._q('#utility-select');
        if (utilSelect) utilSelect.value = this.state.utility;
        const utilDetail = this._q('#utility-detail');
        const u = (d.utilityWeapons || []).find(x => x.name === this.state.utility);
        if (utilDetail) utilDetail.textContent = u ? `${u.category} · ${u.cooldown}s cooldown — ${u.description}` : '';

        // Point tracker + validity
        this._renderPoints();
    }

    _renderPoints() {
        const attrPoints = this._attrPoints();
        const effectPoints = this._effectPoints();
        const ordinancePoints = this._ordinancePoints();
        const variancePoints = this._variancePoints();
        const totalPoints = attrPoints + effectPoints + ordinancePoints + variancePoints;
        const maxPoints = this.weaponData.maxPoints;

        const pointsUsedEl = this._q('#points-used');
        const iconEl = this._q('#alloc-icon');
        pointsUsedEl.textContent = totalPoints;
        this._q('#points-max').textContent = maxPoints;
        this._q('#attr-points').textContent = attrPoints;
        this._q('#effect-points').textContent = effectPoints;
        this._q('#ordinance-points').textContent = ordinancePoints;
        const varPtsEl = this._q('#variance-points');
        if (varPtsEl) varPtsEl.textContent = variancePoints;

        let valid;
        if (totalPoints > maxPoints) {
            pointsUsedEl.className = 'pt-used points-over';
            iconEl.textContent = '❗'; iconEl.className = 'alloc-icon alloc-over';
            valid = false;
        } else if (totalPoints < maxPoints) {
            pointsUsedEl.className = 'pt-used points-under';
            iconEl.textContent = '⚠'; iconEl.className = 'alloc-icon alloc-under';
            valid = true;
        } else {
            pointsUsedEl.className = 'pt-used points-used';
            iconEl.textContent = '✓'; iconEl.className = 'alloc-icon alloc-perfect';
            valid = true;
        }

        if (valid !== this._lastValid) {
            this._lastValid = valid;
            try { this.onValidityChange(valid); }
            catch (e) { console.error('onValidityChange threw:', e); }
        }
    }

    _formatAttrValue(key, points) {
        return `${points} pt${points === 1 || points === -1 ? '' : 's'}`;
    }

    // ---- point math (all read from state) ----
    _attrPoints() {
        return Object.values(this.state.attributes).reduce((s, v) => s + v, 0);
    }
    _effectPoints() {
        return this.state.effects.reduce((s, name) => {
            const e = this.weaponData.effects.find(x => x.name === name);
            return s + (e ? e.cost : 0);
        }, 0);
    }
    _ordinancePoints() {
        const o = this.weaponData.ordinances.find(x => x.name === this.state.ordinance);
        return o ? o.cost : 0;
    }
    _variancePoints() {
        if (!this.weaponData || !this.weaponData.varianceFormulas) return 0;
        const vf = this.weaponData.varianceFormulas.find(x => x.name === this.state.varianceFormula);
        return vf ? vf.cost : 0;
    }
    _totalPoints() {
        return this._attrPoints() + this._effectPoints() + this._ordinancePoints() + this._variancePoints();
    }

    // ===================================================================
    //  Server-resolved stats panel (debounced) — unchanged behavior
    // ===================================================================

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

    _renderResolved(panel, data) {
        const attrs = data.attributes || {};
        const order = ['MIN_DAMAGE', 'MAX_DAMAGE', 'FIRE_RATE', 'BULLETS_PER_SHOT', 'MAGAZINE_SIZE', 'RELOAD_TIME',
                       'RANGE', 'PROJECTILE_SPEED', 'ACCURACY', 'HANDLING', 'LINEAR_DAMPING', 'CALIBER', 'KNOCKBACK'];
        const statRows = order.filter(k => attrs[k]).map(k => {
            const a = attrs[k];
            const tag = a.coupled ? ` <span class="coupling-tag">(base ${a.baseDisplay})</span>` : '';
            return `<div class="rs-row"><span class="rs-label">${a.label}</span><span class="rs-val">${a.display}${tag}</span></div>`;
        }).join('');

        const d = data.derived || {};
        const derivedRows = `
            <div class="rs-row"><span class="rs-label">DPS</span><span class="rs-val">${Math.round(d.dps || 0)}</span></div>
            <div class="rs-row"><span class="rs-label">Expected Damage</span><span class="rs-val">${Math.round(d.expectedDamage || 0)}</span></div>
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

    // ===================================================================
    //  Persistence — same localStorage shape as the production version
    // ===================================================================

    _save() {
        try {
            localStorage.setItem('weaponConfig', JSON.stringify({
                weapon: {
                    attributes: this.state.attributes,
                    effects: this.state.effects,
                    ordinance: this.state.ordinance,
                    varianceFormula: this.state.varianceFormula
                },
                utilityWeapon: this.state.utility,
                timestamp: Date.now()
            }));
        } catch (error) {
            console.error('Failed to save configuration:', error);
        }
    }

    /** Load saved config into state only — _commit()'s normalize handles coercion. */
    _loadSavedConfiguration() {
        try {
            const saved = localStorage.getItem('weaponConfig');
            if (!saved) return false;
            const config = JSON.parse(saved);
            if (!config.weapon || !config.weapon.attributes || !config.weapon.effects || !config.weapon.ordinance) {
                return false;
            }
            this.state.attributes = { ...config.weapon.attributes };
            this.state.effects = [...config.weapon.effects];
            this.state.ordinance = config.weapon.ordinance;       // normalize coerces if stale
            if (config.weapon.varianceFormula) this.state.varianceFormula = config.weapon.varianceFormula;
            if (config.utilityWeapon) this.state.utility = config.utilityWeapon;
            return true;
        } catch (error) {
            console.error('Failed to load saved configuration:', error);
            return false;
        }
    }
}

// Expose globally for non-module consumers (game.html loads this with a plain <script>).
window.WeaponCustomizer = WeaponCustomizer;

package com.fullsteam.physics;

import com.fullsteam.Config;
import com.fullsteam.games.StatusEffectManager;
import com.fullsteam.model.ArmorType;
import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.HasWeapon;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Scoring;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponConfig;
import lombok.Getter;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter
@Setter
public class Player extends OwnedGameEntity implements HasWeapon {
    private String playerName;
    private int team; // 0 = no team (FFA), 1+ = team number
    private Weapon weapon;
    private UtilityWeapon utilityWeapon;
    private boolean isReloading = false;
    private double reloadTimeRemaining = 0;
    private Vector2 aimDirection = new Vector2(1, 0);
    private long lastShotTime = 0;
    private long lastUtilityUseTime = 0;
    private Scoring scoring = new Scoring();
    private long respawnTime = 0;
    private Vector2 respawnPoint;
    private double maxSpeed = Config.PLAYER_SPEED;
    private ArmorType armorType = ArmorType.NONE;
    private double armor = 0.0;
    private double maxArmor = 0.0;
    private boolean lastDamageArmorMitigated = false;
    private final Set<AttributeModification> attributeModifications = new ConcurrentSkipListSet<>();

    private boolean visionObscured = false; // Set true each tick while inside SMOKE field, reset before collision processing

    private int livesRemaining = -1; // -1 = unlimited, 0 = eliminated
    private boolean eliminated = false; // Permanently eliminated (no more respawns)
    private long eliminationTime = 0; // Timestamp when player was eliminated (for Battle Royale ranking)
    private int placement = 0; // Final placement in elimination modes (1 = winner, 2 = 2nd place, etc.)

    public Player(int id, String playerName, double x, double y, int team, double maxHealth) {
        super(id, createPlayerBody(x, y), maxHealth, id, team);
        this.playerName = playerName != null ? playerName : "Player " + id;
        this.team = team;
        this.respawnPoint = new Vector2(x, y);

        // Default weapons
        this.weapon = WeaponConfig.ASSAULT_RIFLE_PRESET.buildWeapon();
        this.utilityWeapon = UtilityWeapon.HEAL_ZONE; // Default utility weapon
    }

    private static Body createPlayerBody(double x, double y) {
        Body body = new Body();
        Circle circle = new Circle(Config.PLAYER_RADIUS);
        body.addFixture(circle);
        body.setMass(MassType.NORMAL);
        body.getTransform().setTranslation(x, y);

        // Physics-based movement configuration
        body.setLinearDamping(Config.PLAYER_LINEAR_DAMPING);
        body.setAngularDamping(Config.PLAYER_ANGULAR_DAMPING);
        body.setAngularVelocity(0.0);

        return body;
    }

    @Override
    public void update(double deltaTime) {
        attributeModifications.removeIf(am -> {
            if (am.isExpired()) {
                am.revert(this);
                return true; // Remove expired modifications
            }
            am.update(this, deltaTime);
            return false; // Keep active modifications
        });

        // Handle reloading
        if (isReloading) {
            reloadTimeRemaining -= deltaTime;
            if (reloadTimeRemaining <= 0) {
                getCurrentWeapon().reload();
                isReloading = false;
            }
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    public void processInput(PlayerInput input) {
        if (!active) {
            return;
        }

        // Physics-based dynamic movement
        Vector2 moveVector = new Vector2(input.getMoveX(), input.getMoveY());
        processMovement(moveVector);

        // Aiming
        Vector2 playerPos = getPosition();
        aimDirection = new Vector2(
                input.getWorldX() - playerPos.x,
                input.getWorldY() - playerPos.y
        );
        if (aimDirection.getMagnitude() > 0) {
            aimDirection.normalize();
            setRotation(Math.atan2(aimDirection.y, aimDirection.x));
            // Ensure no residual angular velocity interferes with controlled rotation
            body.setAngularVelocity(0.0);
        }

        // Reloading
        if (Boolean.TRUE.equals(input.getReload()) && !isReloading) {
            startReload();
        }
    }

    /**
     * Physics-based movement using forces for realistic acceleration and deceleration
     */
    private void processMovement(Vector2 moveVector) {
        if (moveVector.getMagnitude() > 0) {
            // Player wants to move - apply force toward target velocity
            moveVector.normalize();
            Vector2 targetVelocity = moveVector.multiply(maxSpeed);
            Vector2 currentVelocity = getVelocity();
            Vector2 velocityDiff = targetVelocity.subtract(currentVelocity);

            // Apply force proportional to velocity difference (PD controller)
            Vector2 force = velocityDiff.multiply(Config.PLAYER_ACCELERATION);
            body.applyForce(force);
        } else {
            // Player wants to stop - apply braking force
            Vector2 currentVelocity = getVelocity();
            if (currentVelocity.getMagnitude() > 1.0) { // Only brake if moving significantly
                Vector2 brakingForce = currentVelocity.multiply(-Config.PLAYER_BRAKING_FORCE);
                body.applyForce(brakingForce);
            }
        }
    }

    public void applyWeaponConfig(WeaponConfig primary, UtilityWeapon utility) {
        applyWeaponConfig(primary, utility, this.armorType);
    }

    public void applyWeaponConfig(WeaponConfig primary, UtilityWeapon utility, ArmorType armorType) {
        if (armorType != null) {
            this.armorType = armorType;
            this.maxArmor = armorType.getMaxArmor();
            this.armor = this.maxArmor;
        }
        if (primary != null) {
            weapon = primary.buildWeapon();
            weapon.reload();
        }
        if (utility != null) {
            this.utilityWeapon = utility;
        }
        double weaponHandling = weapon != null ? weapon.getHandling() : 1.0;
        double armorHandling = this.armorType != null ? this.armorType.getHandlingModifier() : 1.0;
        this.maxSpeed = Config.PLAYER_SPEED * weaponHandling * armorHandling;
    }

    public void resetArmor() {
        this.armor = this.maxArmor;
    }

    public double armorPercent() {
        return maxArmor > 0 ? Math.max(0.0, armor / maxArmor) : 0.0;
    }

    public StatusEffectManager.RiotShieldAttributeModification getRiotShieldModification() {
        for (AttributeModification mod : attributeModifications) {
            if (mod instanceof StatusEffectManager.RiotShieldAttributeModification rsm && !rsm.isExpired()) {
                return rsm;
            }
        }
        return null;
    }

    public boolean isRiotShieldActive() {
        StatusEffectManager.RiotShieldAttributeModification rsm = getRiotShieldModification();
        if (rsm != null) {
            return !rsm.isExpired();
        }
        return attributeModifications.stream().anyMatch(am -> "riotShield".equals(am.uniqueKey()) && !am.isExpired());
    }

    public boolean damageRiotShield(double damage) {
        StatusEffectManager.RiotShieldAttributeModification rsm = getRiotShieldModification();
        if (rsm != null && !rsm.isExpired()) {
            rsm.damageShield(damage);
            if (rsm.isExpired()) {
                rsm.revert(this);
                attributeModifications.remove(rsm);
            }
            return true;
        }
        // Fallback for generic riotShield modifications
        return attributeModifications.removeIf(am -> {
            if ("riotShield".equals(am.uniqueKey())) {
                am.revert(this);
                return true;
            }
            return false;
        });
    }

    public double getRiotShieldHealth() {
        StatusEffectManager.RiotShieldAttributeModification rsm = getRiotShieldModification();
        return rsm != null ? rsm.getHealth() : 0.0;
    }

    public double getRiotShieldMaxHealth() {
        StatusEffectManager.RiotShieldAttributeModification rsm = getRiotShieldModification();
        return rsm != null ? rsm.getMaxHealth() : 0.0;
    }

    public boolean canShoot() {
        Weapon weapon = this.getCurrentWeapon();
        long now = System.currentTimeMillis();
        double fireInterval = 1000.0 / weapon.getFireRate();
        // Check if we have enough ammo for at least one bullet (partial bursts are allowed)
        return isActive()
                && health > 0
                && !isReloading
                && weapon.getCurrentAmmo() > 0
                && (now - lastShotTime) >= fireInterval;
    }

    public boolean canUseUtility() {
        if (!isActive() || health <= 0 || utilityWeapon == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        double cooldownMs = utilityWeapon.getCooldown() * 1000.0;
        return (now - lastUtilityUseTime) >= cooldownMs;
    }

    /**
     * Utility-weapon cooldown progress for the HUD ring: 0.0 just after use,
     * ramping to 1.0 when the utility is ready again. Returns 1.0 when there is no
     * utility or no cooldown.
     */
    public double getUtilityCooldownProgress() {
        if (utilityWeapon == null) {
            return 1.0;
        }
        double cooldownMs = utilityWeapon.getCooldown() * 1000.0;
        if (cooldownMs <= 0) {
            return 1.0;
        }
        double elapsed = System.currentTimeMillis() - lastUtilityUseTime;
        return Math.max(0.0, Math.min(1.0, elapsed / cooldownMs));
    }

    /**
     * Refund the utility cooldown (e.g. when placement fails).
     * Resets the cooldown timer to allow immediate reuse.
     */
    public void refundUtilityCooldown() {
        lastUtilityUseTime = 0;
    }

    /**
     * Use the utility weapon. Returns data needed to create the utility effect.
     *
     * @return UtilityActivation data, or null if utility cannot be used
     */
    public UtilityActivation useUtility() {
        if (!canUseUtility()) {
            return null;
        }

        lastUtilityUseTime = System.currentTimeMillis();
        Vector2 pos = getPosition();

        return new UtilityActivation(
                utilityWeapon,
                pos.copy(),
                aimDirection.copy(),
                id,
                team
        );
    }

    public void startReload() {
        Weapon weapon = this.getCurrentWeapon();
        if (weapon.needsReload()) {
            isReloading = true;
            reloadTimeRemaining = weapon.getReloadTime();
        }
    }

    /**
     * Reload progress for player HUD / reload bar: 0.0 when reloading just started,
     * ramping to 1.0 when reload is complete. Returns 1.0 when not reloading.
     */
    public double getReloadPercent() {
        if (!isReloading || weapon == null) {
            return 1.0;
        }
        double totalTime = weapon.getReloadTime();
        if (totalTime <= 0) {
            return 1.0;
        }
        double elapsed = totalTime - reloadTimeRemaining;
        return Math.max(0.0, Math.min(1.0, elapsed / totalTime));
    }

    public void die() {
        active = false;
        scoring.addDeath();
        health = 0;
        attributeModifications.removeIf(am -> {
            am.revert(this);
            return true;
        });
    }

    public void addKill() {
        scoring.addKill();
    }

    public void addCapture() {
        scoring.addCapture();
    }

    // --- Convenience delegators so existing call sites keep working while all
    //     scoring state lives in the Scoring object. ---

    public int getKills() {
        return scoring.getKills();
    }

    public void setKills(int kills) {
        scoring.setKills(kills);
    }

    public int getDeaths() {
        return scoring.getDeaths();
    }

    public void setDeaths(int deaths) {
        scoring.setDeaths(deaths);
    }

    public int getCaptures() {
        return scoring.getFlagCaptures();
    }

    public void setCaptures(int captures) {
        scoring.setFlagCaptures(captures);
    }

    /**
     * Initialize lives for LIMITED respawn mode.
     */
    public void initializeLives(int maxLives) {
        this.livesRemaining = maxLives;
        this.eliminated = false;
    }

    /**
     * Consume one life. Returns true if player is now eliminated. Modes without
     * limited lives leave livesRemaining = -1 (unlimited), so this is a no-op for
     * them; LIMITED with maxLives = 1 eliminates on first death (one-life play).
     */
    public boolean loseLife() {
        if (livesRemaining > 0) {
            // LIMITED mode: consume a life
            livesRemaining--;
            if (livesRemaining == 0) {
                eliminated = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Check if player has lives remaining.
     */
    public boolean hasLivesRemaining() {
        return livesRemaining != 0; // -1 (unlimited) or > 0
    }

    public Weapon getCurrentWeapon() {
        // Always return primary weapon (utility weapons are handled separately)
        Weapon w = weapon;
        for (AttributeModification attributeModification : attributeModifications) {
            w = attributeModification.update(w);
        }
        return w;
    }

    /**
     * Check if this player is on the same team as another player.
     *
     * @param otherPlayer The other player to check
     * @return true if on same team, false if enemies or FFA
     */
    public boolean isTeammate(Player otherPlayer) {
        if (otherPlayer == null || this.team == 0 || otherPlayer.team == 0) {
            return false; // FFA mode or null player
        }
        return this.team == otherPlayer.team;
    }

    /**
     * Override damage handling to account for damage resistance and armor.
     */
    public boolean takeDamage(double damage, boolean bypassArmor) {
        lastDamageArmorMitigated = false;
        if (!active) {
            return false;
        }
        double modifiedDamage = damage;
        for (AttributeModification attributeModification : attributeModifications) {
            modifiedDamage = attributeModification.modifyDamageReceived(modifiedDamage);
        }
        if (!bypassArmor && modifiedDamage > 0 && armor > 0) {
            double absorbed = Math.min(armor, modifiedDamage);
            armor -= absorbed;
            modifiedDamage -= absorbed;
            lastDamageArmorMitigated = true;
        }
        if (modifiedDamage <= 0) {
            return false;
        }
        return super.takeDamage(modifiedDamage);
    }

    @Override
    public boolean takeDamage(double damage) {
        return takeDamage(damage, false);
    }

}
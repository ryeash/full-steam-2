package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * Damage-over-time plasma beam weapon
 */
public class PlasmaBeam extends Beam {

    public PlasmaBeam(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                      int ownerId, int ownerTeam, double beamDuration, double damageInterval) {
        super(id, startPoint, direction, range, damage, ownerId, ownerTeam, 
              DamageApplicationType.DAMAGE_OVER_TIME, damageInterval, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        // Small initial damage when beam first hits
        if (canAffectPlayer(player)) {
            player.takeDamage(damage * 0.1);
        }
    }

    @Override
    public void processContinuousDamage(Player player, double deltaTime) {
        if (canAffectPlayer(player)) {
            // Apply damage over time - total damage spread over beam duration
            double damagePerSecond = damage / beamDuration;
            player.takeDamage(damagePerSecond * deltaTime);
        }
    }

    @Override
    public void processBurstDamage(Player player) {
        // Not used for DOT beams
    }

    @Override
    public boolean isHealingBeam() {
        return false;
    }

    @Override
    public boolean canPierceTargets() {
        return true; // Plasma beam can affect multiple targets
    }
}

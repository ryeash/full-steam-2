package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * Burst-damage pulse laser that fires discrete damage pulses
 */
public class PulseLaser extends Beam {

    public PulseLaser(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                      int ownerId, int ownerTeam, double beamDuration, double pulseInterval) {
        super(id, startPoint, direction, range, damage, ownerId, ownerTeam, 
              DamageApplicationType.BURST, pulseInterval, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        // First pulse happens immediately
        if (canAffectPlayer(player)) {
            double pulseCount = beamDuration / damageInterval;
            double damagePerPulse = damage / pulseCount;
            player.takeDamage(damagePerPulse);
        }
    }

    @Override
    public void processContinuousDamage(Player player, double deltaTime) {
        // Not used for burst beams
    }

    @Override
    public void processBurstDamage(Player player) {
        if (canAffectPlayer(player)) {
            // Calculate damage per pulse based on total pulses over duration
            double pulseCount = beamDuration / damageInterval;
            double damagePerPulse = damage / pulseCount;
            player.takeDamage(damagePerPulse);
        }
    }

    @Override
    public boolean isHealingBeam() {
        return false;
    }

    @Override
    public boolean canPierceTargets() {
        return false; // Pulse laser focuses on single target
    }
}

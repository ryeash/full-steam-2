package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * High-damage piercing railgun beam
 */
public class RailgunBeam extends Beam {

    public RailgunBeam(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                       int ownerId, int ownerTeam, double beamDuration) {
        super(id, startPoint, direction, range, damage, ownerId, ownerTeam, 
              DamageApplicationType.INSTANT, 0.0, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        if (canAffectPlayer(player)) {
            // Full damage instantly - railgun is high-risk, high-reward
            player.takeDamage(damage);
        }
    }

    @Override
    public void processContinuousDamage(Player player, double deltaTime) {
        // Not used for instant damage beams
    }

    @Override
    public void processBurstDamage(Player player) {
        // Not used for instant damage beams
    }

    @Override
    public boolean isHealingBeam() {
        return false;
    }

    @Override
    public boolean canPierceTargets() {
        return true; // Railgun pierces through all targets in its path
    }
}

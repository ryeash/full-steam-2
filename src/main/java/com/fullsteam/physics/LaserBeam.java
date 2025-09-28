package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * Instant-damage laser beam weapon
 */
public class LaserBeam extends Beam {

    public LaserBeam(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                     int ownerId, int ownerTeam, double beamDuration) {
        super(id, startPoint, direction, range, damage, ownerId, ownerTeam, 
              DamageApplicationType.INSTANT, 0.0, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        if (canAffectPlayer(player)) {
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
        return false; // Standard laser doesn't pierce
    }
}

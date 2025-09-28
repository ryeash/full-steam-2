package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * Healing beam weapon that restores health to allies
 */
public class HealBeam extends Beam {

    public HealBeam(int id, Vector2 startPoint, Vector2 direction, double range, double healAmount, 
                    int ownerId, int ownerTeam, double beamDuration, double healInterval) {
        super(id, startPoint, direction, range, healAmount, ownerId, ownerTeam, 
              DamageApplicationType.DAMAGE_OVER_TIME, healInterval, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        // Small initial heal when beam first connects
        if (canAffectPlayer(player)) {
            player.heal(damage * 0.1);
        }
    }

    @Override
    public void processContinuousDamage(Player player, double deltaTime) {
        if (canAffectPlayer(player)) {
            // Apply healing over time - total healing spread over beam duration
            double healPerSecond = damage / beamDuration;
            player.heal(healPerSecond * deltaTime);
        }
    }

    @Override
    public void processBurstDamage(Player player) {
        // Not used for DOT beams
    }

    @Override
    public boolean isHealingBeam() {
        return true;
    }

    @Override
    public boolean canPierceTargets() {
        return true; // Heal beam can affect multiple allies
    }
}

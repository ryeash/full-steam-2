package com.fullsteam.physics;

import com.fullsteam.model.DamageApplicationType;
import org.dyn4j.geometry.Vector2;

/**
 * Chain lightning beam that can jump between nearby targets
 */
public class ArcBeam extends Beam {

    public ArcBeam(int id, Vector2 startPoint, Vector2 direction, double range, double damage, 
                   int ownerId, int ownerTeam, double beamDuration, double arcInterval) {
        super(id, startPoint, direction, range, damage, ownerId, ownerTeam, 
              DamageApplicationType.BURST, arcInterval, beamDuration);
    }

    @Override
    public void processInitialHit(Player player) {
        // Initial arc damage
        if (canAffectPlayer(player)) {
            double arcCount = beamDuration / damageInterval;
            double damagePerArc = damage / arcCount;
            player.takeDamage(damagePerArc);
        }
    }

    @Override
    public void processContinuousDamage(Player player, double deltaTime) {
        // Not used for burst beams
    }

    @Override
    public void processBurstDamage(Player player) {
        if (canAffectPlayer(player)) {
            // Arc damage with potential chain effect
            double arcCount = beamDuration / damageInterval;
            double damagePerArc = damage / arcCount;
            player.takeDamage(damagePerArc);
            
            // TODO: Implement chain lightning logic to nearby enemies
            // This would require access to other players in range
        }
    }

    @Override
    public boolean isHealingBeam() {
        return false;
    }

    @Override
    public boolean canPierceTargets() {
        return true; // Arc beam can chain to multiple targets
    }
}

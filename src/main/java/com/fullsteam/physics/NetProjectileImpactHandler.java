package com.fullsteam.physics;

import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;

/**
 * Handles physical collisions involving NetProjectiles (taser/root nets).
 */
public class NetProjectileImpactHandler {

    /**
     * Handle NetProjectile colliding with any other game entity.
     */
    public boolean handleNetImpact(NetProjectile net, GameEntity entity) {
        switch (entity) {
            case Player player -> {
                if (net.canAffectPlayer(player)) {
                    net.hitPlayer(player);
                    return false;
                }
                return true;
            }
            case Obstacle _ -> {
                net.setActive(false);
                return false;
            }
            case FieldEffect fe -> {
                if (fe.getType() == FieldEffectType.SHIELD_BARRIER) {
                    net.setActive(false);
                    return false;
                }
                return true;
            }
            case null, default -> {
                return !(entity instanceof Projectile);
            }
        }
    }
}

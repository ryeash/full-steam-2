package com.fullsteam.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the beam ↔ bullet-effect compatibility rules: AOE effects, SMOKE,
 * PIERCING, and BOUNCY (which reflects the beam off walls) apply to beams; the
 * remaining flight-only behaviors (FRAGMENTING/HOMING) do not, and are stripped
 * at weapon-build time so they can never reach gameplay.
 */
class BeamEffectSupportTest {

    @Test
    @DisplayName("Flight-only effects are forbidden on beams; everything else is allowed")
    void validForBeamsFlags() {
        // Forbidden — flight behaviors meaningless for an instant ray
        assertFalse(BulletEffect.HOMING.isValidForBeams());
        assertFalse(BulletEffect.FRAGMENTING.isValidForBeams());

        // Allowed — AOE spawners, smoke, beam pass-through, and reflection
        assertTrue(BulletEffect.EXPLOSIVE.isValidForBeams());
        assertTrue(BulletEffect.INCENDIARY.isValidForBeams());
        assertTrue(BulletEffect.ELECTRIC.isValidForBeams());
        assertTrue(BulletEffect.FREEZING.isValidForBeams());
        assertTrue(BulletEffect.POISON.isValidForBeams());
        assertTrue(BulletEffect.SMOKE.isValidForBeams());
        assertTrue(BulletEffect.PIERCING.isValidForBeams());
        assertTrue(BulletEffect.BOUNCY.isValidForBeams());
    }

    @Test
    @DisplayName("validFor() leaves projectile loadouts untouched")
    void validForProjectileKeepsEverything() {
        Set<BulletEffect> all = Set.of(BulletEffect.values());
        assertEquals(all, BulletEffect.validFor(Ordinance.PROJECTILE, all));
    }

    @Test
    @DisplayName("validFor() drops only the forbidden effects for beam ordnance")
    void validForBeamDropsForbidden() {
        Set<BulletEffect> mixed = Set.of(
                BulletEffect.EXPLOSIVE, BulletEffect.SMOKE, BulletEffect.PIERCING, BulletEffect.BOUNCY,
                BulletEffect.HOMING, BulletEffect.FRAGMENTING);
        Set<BulletEffect> kept = Set.of(
                BulletEffect.EXPLOSIVE, BulletEffect.SMOKE, BulletEffect.PIERCING, BulletEffect.BOUNCY);

        assertEquals(kept, BulletEffect.validFor(Ordinance.LASER, mixed));
        assertEquals(kept, BulletEffect.validFor(Ordinance.PLASMA_BEAM, mixed));
    }

    @Test
    @DisplayName("buildWeapon strips forbidden effects for beams but keeps them for projectiles")
    void buildWeaponSanitizesEffects() {
        WeaponConfig beam = new WeaponConfig();
        beam.ordinance = Ordinance.LASER;
        beam.bulletEffects = Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING, BulletEffect.BOUNCY);
        Set<BulletEffect> beamEffects = beam.buildWeapon().getBulletEffects();
        assertTrue(beamEffects.contains(BulletEffect.INCENDIARY));
        assertTrue(beamEffects.contains(BulletEffect.BOUNCY));   // beams reflect now
        assertFalse(beamEffects.contains(BulletEffect.HOMING));  // still forbidden

        WeaponConfig proj = new WeaponConfig();
        proj.ordinance = Ordinance.PROJECTILE;
        proj.bulletEffects = Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING, BulletEffect.BOUNCY);
        Set<BulletEffect> projEffects = proj.buildWeapon().getBulletEffects();
        assertTrue(projEffects.contains(BulletEffect.INCENDIARY));
        assertTrue(projEffects.contains(BulletEffect.HOMING));
        assertTrue(projEffects.contains(BulletEffect.BOUNCY));
    }
}

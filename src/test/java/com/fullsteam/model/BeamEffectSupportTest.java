package com.fullsteam.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the beam ↔ bullet-effect compatibility rules: AOE effects, SMOKE, and
 * PIERCING apply to beams; the flight-only behaviors (BOUNCY/FRAGMENTING/HOMING)
 * do not, and are stripped at weapon-build time so they can never reach gameplay.
 */
class BeamEffectSupportTest {

    @Test
    @DisplayName("Flight-only effects are forbidden on beams; everything else is allowed")
    void validForBeamsFlags() {
        // Forbidden — flight behaviors meaningless for an instant ray
        assertFalse(BulletEffect.HOMING.isValidForBeams());
        assertFalse(BulletEffect.BOUNCY.isValidForBeams());
        assertFalse(BulletEffect.FRAGMENTING.isValidForBeams());

        // Allowed — AOE spawners, smoke, and beam pass-through
        assertTrue(BulletEffect.EXPLOSIVE.isValidForBeams());
        assertTrue(BulletEffect.INCENDIARY.isValidForBeams());
        assertTrue(BulletEffect.ELECTRIC.isValidForBeams());
        assertTrue(BulletEffect.FREEZING.isValidForBeams());
        assertTrue(BulletEffect.POISON.isValidForBeams());
        assertTrue(BulletEffect.SMOKE.isValidForBeams());
        assertTrue(BulletEffect.PIERCING.isValidForBeams());
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
                BulletEffect.EXPLOSIVE, BulletEffect.SMOKE, BulletEffect.PIERCING,
                BulletEffect.HOMING, BulletEffect.BOUNCY, BulletEffect.FRAGMENTING);

        Set<BulletEffect> laser = BulletEffect.validFor(Ordinance.LASER, mixed);
        assertEquals(Set.of(BulletEffect.EXPLOSIVE, BulletEffect.SMOKE, BulletEffect.PIERCING), laser);

        Set<BulletEffect> plasma = BulletEffect.validFor(Ordinance.PLASMA_BEAM, mixed);
        assertEquals(Set.of(BulletEffect.EXPLOSIVE, BulletEffect.SMOKE, BulletEffect.PIERCING), plasma);
    }

    @Test
    @DisplayName("buildWeapon strips forbidden effects for beams but keeps them for projectiles")
    void buildWeaponSanitizesEffects() {
        WeaponConfig beam = new WeaponConfig();
        beam.ordinance = Ordinance.LASER;
        beam.bulletEffects = Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING, BulletEffect.BOUNCY);
        Set<BulletEffect> beamEffects = beam.buildWeapon().getBulletEffects();
        assertTrue(beamEffects.contains(BulletEffect.INCENDIARY));
        assertFalse(beamEffects.contains(BulletEffect.HOMING));
        assertFalse(beamEffects.contains(BulletEffect.BOUNCY));

        WeaponConfig proj = new WeaponConfig();
        proj.ordinance = Ordinance.PROJECTILE;
        proj.bulletEffects = Set.of(BulletEffect.INCENDIARY, BulletEffect.HOMING, BulletEffect.BOUNCY);
        Set<BulletEffect> projEffects = proj.buildWeapon().getBulletEffects();
        assertTrue(projEffects.contains(BulletEffect.INCENDIARY));
        assertTrue(projEffects.contains(BulletEffect.HOMING));
        assertTrue(projEffects.contains(BulletEffect.BOUNCY));
    }
}

package com.fullsteam.physics;

import com.fullsteam.model.Ordinance;
import org.dyn4j.geometry.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

/**
 * Test beam piercing behavior for different beam types
 */
public class BeamPiercingTest {

    @Test
    public void testLaserPiercingBehavior() {
        Beam laser = new Beam(1, new Vector2(0, 0), new Vector2(1, 0), 100.0, 25.0, 1, 1, Ordinance.LASER, Set.of());
        
        // Laser should pierce players but not obstacles
        assertTrue(laser.canPiercePlayers(), "Laser should pierce through players");
        assertFalse(laser.canPierceObstacles(), "Laser should not pierce through obstacles");
    }

    @Test
    public void testRailgunPiercingBehavior() {
        Beam railgun = new Beam(2, new Vector2(0, 0), new Vector2(1, 0), 100.0, 30.0, 1, 1, Ordinance.RAILGUN, Set.of());
        
        // Railgun should pierce everything
        assertTrue(railgun.canPiercePlayers(), "Railgun should pierce through players");
        assertTrue(railgun.canPierceObstacles(), "Railgun should pierce through obstacles");
    }


    @Test
    public void testPlasmaBeamPiercingBehavior() {
        Beam plasmaBeam = new Beam(4, new Vector2(0, 0), new Vector2(1, 0), 100.0, 20.0, 1, 1, Ordinance.PLASMA_BEAM, Set.of());
        
        // Plasma beam should pierce players but not obstacles
        assertTrue(plasmaBeam.canPiercePlayers(), "Plasma beam should pierce through players");
        assertFalse(plasmaBeam.canPierceObstacles(), "Plasma beam should not pierce through obstacles");
    }

    @Test
    public void testHealBeamPiercingBehavior() {
        Beam healBeam = new Beam(5, new Vector2(0, 0), new Vector2(1, 0), 100.0, 15.0, 1, 1, Ordinance.HEAL_BEAM, Set.of());
        
        // Heal beam should pierce players to heal multiple teammates, but not obstacles or turrets
        assertTrue(healBeam.canPiercePlayers(), "Heal beam should pierce through players to heal multiple teammates");
        assertFalse(healBeam.canPierceObstacles(), "Heal beam should not pierce through obstacles");
    }
}

package com.fullsteam.ai;

import com.fullsteam.RandomNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AIPersonalityNameTest {

    @Test
    @DisplayName("Personality determination from name is 100% deterministic")
    public void testDeterministicPersonalityFromName() {
        String name1 = "Icebox Cake";
        String name2 = "Pudding";

        AIPersonality.Type type1First = AIPersonality.typeFromName(name1);
        AIPersonality.Type type1Second = AIPersonality.typeFromName(name1);
        assertEquals(type1First, type1Second, "Same name must always produce identical personality type");

        AIPersonality.Type type2First = AIPersonality.typeFromName(name2);
        AIPersonality.Type type2Second = AIPersonality.typeFromName(name2);
        assertEquals(type2First, type2Second, "Same name must always produce identical personality type");

        int expectedIndex1 = Math.floorMod(name1.hashCode(), AIPersonality.Type.values().length);
        assertEquals(AIPersonality.Type.values()[expectedIndex1], type1First);
    }

    @Test
    @DisplayName("Verify archetype distribution across all names")
    public void testArchetypeDistribution() {
        var names = RandomNames.getNames();
        assertFalse(names.isEmpty(), "Name list should not be empty");

        java.util.Map<AIPersonality.Type, Integer> counts = new java.util.EnumMap<>(AIPersonality.Type.class);
        for (AIPersonality.Type type : AIPersonality.Type.values()) {
            counts.put(type, 0);
        }

        for (String name : names) {
            AIPersonality.Type type = AIPersonality.typeFromName(name);
            counts.put(type, counts.get(type) + 1);
        }

        int total = names.size();
        System.out.println("=== AI Personality Archetype Distribution (" + total + " total names) ===");
        for (AIPersonality.Type type : AIPersonality.Type.values()) {
            int count = counts.get(type);
            double pct = (count * 100.0) / total;
            System.out.printf("  %-12s: %3d names (%5.1f%%)%n", type, count, pct);
            assertTrue(count > 0, "Each archetype should have at least one name assigned");
            assertTrue(pct >= 10.0 && pct <= 30.0, "Distribution for " + type + " should be reasonably balanced (found " + pct + "%)");
        }

        System.out.println("\n=== Sample Name Personality Assignments ===");
        String[] samples = {"Icebox Cake", "Pudding", "Affogato", "Baklava", "Cannoli", "Cheesecake", "Churro"};
        for (String sample : samples) {
            double skill = AIPersonality.generateSkillLevelForName(sample);
            AIPersonality p = AIPersonality.createForName(sample);
            System.out.printf("  %-15s -> Type: %-10s | Archetype: %-10s | Skill: %.2f | Acc: %.2f | React: %.2f%n",
                    sample, AIPersonality.typeFromName(sample), p.getPersonalityType(), skill, p.getAccuracy(), p.getReactionSpeed());
        }
    }

    @Test
    @DisplayName("Verify skill level generation and variance across all names")
    public void testSkillLevelDistributionAndVariance() {
        var names = RandomNames.getNames();
        assertFalse(names.isEmpty(), "Name list should not be empty");

        double minSkill = 1.0;
        double maxSkill = 0.0;
        double sumSkill = 0.0;

        for (String name : names) {
            double skill = AIPersonality.generateSkillLevelForName(name);
            assertTrue(skill >= 0.15 && skill <= 0.85, "Skill level must be between 0.15 and 0.85");
            minSkill = Math.min(minSkill, skill);
            maxSkill = Math.max(maxSkill, skill);
            sumSkill += skill;

            // Verify determinism
            assertEquals(skill, AIPersonality.generateSkillLevelForName(name), "Skill generation must be deterministic");
        }

        double avgSkill = sumSkill / names.size();
        System.out.printf("=== AI Skill Level Stats (%d names) ===%n", names.size());
        System.out.printf("  Min Skill: %.2f%n", minSkill);
        System.out.printf("  Max Skill: %.2f%n", maxSkill);
        System.out.printf("  Avg Skill: %.2f%n", avgSkill);

        assertTrue(minSkill <= 0.20, "Min skill should be low (~0.15)");
        assertTrue(maxSkill >= 0.80, "Max skill should be high (~0.85)");
        assertTrue(avgSkill >= 0.40 && avgSkill <= 0.60, "Average skill should be centered around ~0.50");
    }
}

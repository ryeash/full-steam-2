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
            System.out.printf("  %-15s -> %s%n", sample, AIPersonality.typeFromName(sample));
        }
    }
}

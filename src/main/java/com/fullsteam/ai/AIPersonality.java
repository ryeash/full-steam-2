package com.fullsteam.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines the personality traits that influence AI decision-making and behavior.
 * Each trait is a value between 0.0 and 1.0 that affects how the AI behaves.
 */
@Getter
@Builder
public class AIPersonality {

    // Combat-related traits
    @Builder.Default
    private double aggressiveness = 0.5; // 0.0 = passive, 1.0 = very aggressive
    
    @Builder.Default
    private double accuracy = 0.7; // 0.0 = poor aim, 1.0 = perfect aim
    
    @Builder.Default
    private double reactionSpeed = 0.6; // 0.0 = slow, 1.0 = instant reactions
    
    @Builder.Default
    private double preferredCombatRange = 150.0; // Preferred distance for combat
    
    // Strategic traits
    @Builder.Default
    private double strategicThinking = 0.5; // 0.0 = reactive, 1.0 = highly strategic
    
    @Builder.Default
    private double teamwork = 0.5; // 0.0 = lone wolf, 1.0 = team player
    
    @Builder.Default
    private double riskTolerance = 0.5; // 0.0 = cautious, 1.0 = reckless
    
    // Movement and positioning traits
    @Builder.Default
    private double mobility = 0.6; // 0.0 = stationary, 1.0 = constantly moving
    
    @Builder.Default
    private double coverUsage = 0.7; // 0.0 = ignores cover, 1.0 = always seeks cover
    
    // Decision-making traits
    @Builder.Default
    private double adaptability = 0.5; // 0.0 = rigid, 1.0 = highly adaptive
    
    @Builder.Default
    private double patience = 0.5; // 0.0 = impatient, 1.0 = very patient
    
    /**
     * Creates a random personality with realistic trait distributions.
     */
    public static AIPersonality createRandom() {
        return AIPersonality.builder()
            .aggressiveness(randomTrait(0.3, 0.8))
            .accuracy(randomTrait(0.4, 0.9))
            .reactionSpeed(randomTrait(0.4, 0.8))
            .preferredCombatRange(100 + ThreadLocalRandom.current().nextDouble() * 200) // 100-300 range
            .strategicThinking(randomTrait(0.3, 0.9))
            .teamwork(randomTrait(0.2, 0.8))
            .riskTolerance(randomTrait(0.3, 0.8))
            .mobility(randomTrait(0.4, 0.9))
            .coverUsage(randomTrait(0.5, 0.9))
            .adaptability(randomTrait(0.4, 0.8))
            .patience(randomTrait(0.3, 0.8))
            .build();
    }
    
    /**
     * Creates an aggressive, combat-focused personality.
     */
    public static AIPersonality createAggressive() {
        return AIPersonality.builder()
            .aggressiveness(0.9)
            .accuracy(0.8)
            .reactionSpeed(0.8)
            .preferredCombatRange(120.0)
            .strategicThinking(0.4)
            .teamwork(0.3)
            .riskTolerance(0.8)
            .mobility(0.8)
            .coverUsage(0.4)
            .adaptability(0.6)
            .patience(0.2)
            .build();
    }
    
    /**
     * Creates a defensive, strategic personality.
     */
    public static AIPersonality createDefensive() {
        return AIPersonality.builder()
            .aggressiveness(0.2)
            .accuracy(0.9)
            .reactionSpeed(0.7)
            .preferredCombatRange(200.0)
            .strategicThinking(0.9)
            .teamwork(0.8)
            .riskTolerance(0.3)
            .mobility(0.4)
            .coverUsage(0.9)
            .adaptability(0.7)
            .patience(0.8)
            .build();
    }
    
    /**
     * Creates a balanced, adaptable personality.
     */
    public static AIPersonality createBalanced() {
        return AIPersonality.builder()
            .aggressiveness(0.5)
            .accuracy(0.7)
            .reactionSpeed(0.6)
            .preferredCombatRange(150.0)
            .strategicThinking(0.6)
            .teamwork(0.6)
            .riskTolerance(0.5)
            .mobility(0.6)
            .coverUsage(0.7)
            .adaptability(0.8)
            .patience(0.5)
            .build();
    }
    
    /**
     * Creates a sniper-like personality focused on long-range combat.
     */
    public static AIPersonality createSniper() {
        return AIPersonality.builder()
            .aggressiveness(0.4)
            .accuracy(0.95)
            .reactionSpeed(0.6)
            .preferredCombatRange(300.0)
            .strategicThinking(0.8)
            .teamwork(0.5)
            .riskTolerance(0.3)
            .mobility(0.3)
            .coverUsage(0.9)
            .adaptability(0.5)
            .patience(0.9)
            .build();
    }
    
    /**
     * Creates a rusher personality focused on close-range, high-mobility combat.
     */
    public static AIPersonality createRusher() {
        return AIPersonality.builder()
            .aggressiveness(0.9)
            .accuracy(0.6)
            .reactionSpeed(0.9)
            .preferredCombatRange(80.0)
            .strategicThinking(0.3)
            .teamwork(0.4)
            .riskTolerance(0.9)
            .mobility(0.9)
            .coverUsage(0.3)
            .adaptability(0.7)
            .patience(0.1)
            .build();
    }
    
    private static double randomTrait(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }
    
    /**
     * Get a descriptive name for this personality based on dominant traits.
     */
    public String getPersonalityType() {
        if (aggressiveness > 0.8 && riskTolerance > 0.7) {
            return "Berserker";
        } else if (accuracy > 0.9 && preferredCombatRange > 250) {
            return "Sniper";
        } else if (aggressiveness > 0.8 && mobility > 0.8) {
            return "Rusher";
        } else if (strategicThinking > 0.8 && aggressiveness < 0.3) {
            return "Strategist";
        } else if (teamwork > 0.8 && strategicThinking > 0.6) {
            return "Support";
        } else if (coverUsage > 0.8 && patience > 0.7) {
            return "Guardian";
        } else {
            return "Soldier";
        }
    }
}

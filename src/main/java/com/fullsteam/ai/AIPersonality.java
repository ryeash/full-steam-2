package com.fullsteam.ai;

import lombok.Builder;
import lombok.Getter;

/**
 * Defines the personality traits that influence AI decision-making and behavior.
 * Each trait is a value between 0.0 and 1.0 that affects how the AI behaves.
 */
@Getter
@Builder
public class AIPersonality {

    public enum Type {
        aggressive,
        defensive,
        sniper,
        rusher,
        balanced
    }

    public static Type typeFromName(String name) {
        if (name == null || name.isEmpty()) {
            return Type.balanced;
        }
        int index = Math.floorMod(name.hashCode(), Type.values().length);
        return Type.values()[index];
    }

    /**
     * Generate a deterministic skill level between 0.15 and 0.85 based on player name.
     */
    public static double generateSkillLevelForName(String name) {
        if (name == null || name.isEmpty()) {
            return 0.50;
        }
        int hash = Math.abs(name.hashCode() * 31 + 17);
        double normalized = (hash % 1000) / 1000.0;
        return 0.15 + normalized * 0.70;
    }

    public static AIPersonality createForType(Type type) {
        return createForType(type, 0.50);
    }

    public static AIPersonality createForType(Type type, double skillLevel) {
        return switch (type) {
            case aggressive -> createAggressive(skillLevel);
            case defensive -> createDefensive(skillLevel);
            case sniper -> createSniper(skillLevel);
            case rusher -> createRusher(skillLevel);
            default -> createBalanced(skillLevel);
        };
    }

    public static AIPersonality createForName(String name) {
        Type type = typeFromName(name);
        double skillLevel = generateSkillLevelForName(name);
        return createForType(type, skillLevel);
    }

    @Builder.Default
    private double skillLevel = 0.50; // 0.0 = rookie/novice, 1.0 = veteran/expert

    @Builder.Default
    private double aggressiveness = 0.5; // 0.0 = passive, 1.0 = very aggressive

    @Builder.Default
    private double accuracy = 0.40; // 0.0 = poor aim, 1.0 = perfect aim

    @Builder.Default
    private double reactionSpeed = 0.45; // 0.0 = slow, 1.0 = instant reactions

    @Builder.Default
    private double preferredCombatRange = 150.0; // Preferred distance for combat

    @Builder.Default
    private double strategicThinking = 0.5; // 0.0 = reactive, 1.0 = highly strategic

    @Builder.Default
    private double teamwork = 0.5; // 0.0 = lone wolf, 1.0 = team player

    @Builder.Default
    private double riskTolerance = 0.5; // 0.0 = cautious, 1.0 = reckless

    @Builder.Default
    private double mobility = 0.6; // 0.0 = stationary, 1.0 = constantly moving

    @Builder.Default
    private double coverUsage = 0.7; // 0.0 = ignores cover, 1.0 = always seeks cover

    @Builder.Default
    private double adaptability = 0.5; // 0.0 = rigid, 1.0 = highly adaptive

    @Builder.Default
    private double patience = 0.5; // 0.0 = impatient, 1.0 = very patient

    /**
     * Creates an aggressive, combat-focused personality.
     */
    public static AIPersonality createAggressive() {
        return createAggressive(0.50);
    }

    public static AIPersonality createAggressive(double skillLevel) {
        double acc = Math.min(0.85, Math.max(0.12, 0.45 * (0.35 + 0.9 * skillLevel)));
        double react = Math.min(0.85, Math.max(0.12, 0.50 * (0.35 + 0.9 * skillLevel)));
        return AIPersonality.builder()
                .skillLevel(skillLevel)
                .aggressiveness(0.85)
                .accuracy(acc)
                .reactionSpeed(react)
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

    public static AIPersonality createDefensive() {
        return createDefensive(0.50);
    }

    public static AIPersonality createDefensive(double skillLevel) {
        double acc = Math.min(0.85, Math.max(0.12, 0.50 * (0.35 + 0.9 * skillLevel)));
        double react = Math.min(0.85, Math.max(0.12, 0.45 * (0.35 + 0.9 * skillLevel)));
        return AIPersonality.builder()
                .skillLevel(skillLevel)
                .aggressiveness(0.2)
                .accuracy(acc)
                .reactionSpeed(react)
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

    public static AIPersonality createBalanced() {
        return createBalanced(0.50);
    }

    public static AIPersonality createBalanced(double skillLevel) {
        double acc = Math.min(0.85, Math.max(0.12, 0.40 * (0.35 + 0.9 * skillLevel)));
        double react = Math.min(0.85, Math.max(0.12, 0.40 * (0.35 + 0.9 * skillLevel)));
        return AIPersonality.builder()
                .skillLevel(skillLevel)
                .aggressiveness(0.5)
                .accuracy(acc)
                .reactionSpeed(react)
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

    public static AIPersonality createSniper() {
        return createSniper(0.50);
    }

    public static AIPersonality createSniper(double skillLevel) {
        double acc = Math.min(0.85, Math.max(0.12, 0.55 * (0.35 + 0.9 * skillLevel)));
        double react = Math.min(0.85, Math.max(0.12, 0.40 * (0.35 + 0.9 * skillLevel)));
        return AIPersonality.builder()
                .skillLevel(skillLevel)
                .aggressiveness(0.4)
                .accuracy(acc)
                .reactionSpeed(react)
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

    public static AIPersonality createRusher() {
        return createRusher(0.50);
    }

    public static AIPersonality createRusher(double skillLevel) {
        double acc = Math.min(0.85, Math.max(0.12, 0.30 * (0.35 + 0.9 * skillLevel)));
        double react = Math.min(0.85, Math.max(0.12, 0.55 * (0.35 + 0.9 * skillLevel)));
        return AIPersonality.builder()
                .skillLevel(skillLevel)
                .aggressiveness(0.9)
                .accuracy(acc)
                .reactionSpeed(react)
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

    /**
     * Get a descriptive name for this personality based on dominant traits.
     */
    public String getPersonalityType() {
        if (aggressiveness > 0.8 && riskTolerance > 0.7) {
            return "Berserker";
        } else if (preferredCombatRange > 250) {
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

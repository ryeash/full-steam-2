package com.fullsteam.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Captures a player's score at the end of a round.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundScore {
    private int playerId;
    private String playerName;
    private int team;
    private int kills;
    private int deaths;
    private int captures; // Flag captures (CTF mode)
    private int bonusPoints; // Team bonus points (HQ damage, objectives, etc.) - only populated for round summaries

    /**
     * Full per-component score breakdown (kills, captures, koth, oddball,
     * vipKills, hqDamage, hqDestroyed, bonus, total) matching the live gameState
     * player {@code score} map, so the round-end screen can render exactly the
     * contributing components.
     */
    private Map<String, Object> score;
}


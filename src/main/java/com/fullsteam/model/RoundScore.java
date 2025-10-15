package com.fullsteam.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private double hqDamage;
}


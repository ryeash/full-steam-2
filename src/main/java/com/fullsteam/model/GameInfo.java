package com.fullsteam.model;

import com.fullsteam.games.GameConfig;

public record GameInfo(
    String gameId,
    int playerCount,
    int maxPlayers,
    long createdTime,
    String status,
    GameConfig gameConfig
) {}



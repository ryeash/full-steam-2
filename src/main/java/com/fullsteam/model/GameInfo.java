package com.fullsteam.model;

public record GameInfo(
    String gameId,
    String gameType,
    int playerCount,
    int maxPlayers,
    long createdTime,
    String status
) {}



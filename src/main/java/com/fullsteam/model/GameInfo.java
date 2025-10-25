package com.fullsteam.model;

public record GameInfo(
    String gameId,
    int playerCount,
    int maxPlayers,
    long createdTime,
    String status
) {}



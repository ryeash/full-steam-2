package com.fullsteam.model;

import java.util.List;

public record LobbyInfo(
    long globalPlayerCount,
    int maxGlobalPlayers,
    List<GameInfo> activeGames
) {}



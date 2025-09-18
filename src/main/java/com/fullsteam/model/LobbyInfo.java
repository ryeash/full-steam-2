package com.fullsteam.model;

import java.util.List;

public record LobbyInfo(
    long globalPlayerCount,
    int maxGlobalPlayers,
    List<String> gameTypes,
    List<GameInfo> activeGames
) {}



package com.fullsteam.games;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class GameConfig {
    @Builder.Default
    private int maxPlayers = 10;
    @Builder.Default
    private int teamCount = 2;
    @Builder.Default
    private double worldWidth = 2000.0;
    @Builder.Default
    private double worldHeight = 2000.0;
    @Builder.Default
    private double playerSpeed = 150.0; // pixels per second
    @Builder.Default
    private double playerSize = 10.0;
    @Builder.Default
    private int strategicLocationsCount = 5;
    @Builder.Default
    private double captureRadius = 50.0;
    @Builder.Default
    private double captureTime = 3.0; // seconds
}



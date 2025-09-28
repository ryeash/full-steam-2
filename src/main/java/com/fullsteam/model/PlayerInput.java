package com.fullsteam.model;

import lombok.Data;

@Data
public class PlayerInput {
    private String type = "playerInput";
    private double moveX = 0.0; // -1.0 = left, +1.0 = right
    private double moveY = 0.0; // -1.0 = down, +1.0 = up
    private Boolean reload;
    @Deprecated
    private boolean shift = false;
    @Deprecated
    private boolean space = false;
    private double mouseX = 0;
    private double mouseY = 0;
    private double worldX = 0;
    private double worldY = 0;
    private boolean left = false; // Primary fire
    private boolean altFire = false; // Right click, space, or RB - fires utility weapon
    private boolean right = false; // Legacy - now mapped to altFire
}



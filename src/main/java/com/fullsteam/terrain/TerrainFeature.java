package com.fullsteam.terrain;

import lombok.Getter;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a terrain feature like trees, rocks, buildings, etc.
 * Used for both visual rendering and gameplay collision/cover.
 */
@Getter
@AllArgsConstructor
public class TerrainFeature {
    private final String type;
    private final double x;
    private final double y;
    private final double size;
    private final int color;
    
    /**
     * Convert terrain feature to map for client transmission.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("x", x);
        map.put("y", y);
        map.put("size", size);
        map.put("color", String.format("#%06X", color));
        return map;
    }
    
    /**
     * Check if this feature provides cover for gameplay.
     */
    public boolean providesCover() {
        switch (type.toLowerCase()) {
            case "treecluster":
            case "building":
            case "rockmesa":
            case "lavarock":
            case "icesheet":
            case "rockoutcrop":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get the cover effectiveness (0.0 to 1.0).
     */
    public double getCoverEffectiveness() {
        if (!providesCover()) return 0.0;
        
        switch (type.toLowerCase()) {
            case "building":
            case "rockmesa":
                return 0.9; // Excellent cover
            case "treecluster":
            case "lavarock":
                return 0.7; // Good cover
            case "icesheet":
            case "rockoutcrop":
                return 0.5; // Moderate cover
            default:
                return 0.3; // Light cover
        }
    }
    
    /**
     * Check if this feature blocks movement.
     */
    public boolean blocksMovement() {
        switch (type.toLowerCase()) {
            case "building":
            case "rockmesa":
            case "lavarock":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get the visual layer for rendering order.
     */
    public int getRenderLayer() {
        switch (type.toLowerCase()) {
            case "building":
                return 3; // Highest layer
            case "treecluster":
            case "rockmesa":
                return 2; // Mid-high layer
            case "thickbrush":
            case "tallgrass":
                return 1; // Mid layer
            default:
                return 0; // Ground layer
        }
    }
    
    /**
     * Get opacity for rendering (some features are semi-transparent).
     */
    public double getOpacity() {
        switch (type.toLowerCase()) {
            case "clearing":
            case "flowerpatch":
                return 0.3;
            case "thickbrush":
            case "tallgrass":
                return 0.6;
            default:
                return 1.0;
        }
    }
}

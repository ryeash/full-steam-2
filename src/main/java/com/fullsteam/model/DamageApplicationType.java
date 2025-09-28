package com.fullsteam.model;

/**
 * Defines how beam weapons apply their damage to targets
 */
public enum DamageApplicationType {
    /**
     * Applies all damage immediately on hit (e.g., railgun, sniper laser)
     */
    INSTANT("Applies all damage immediately on hit"),
    
    /**
     * Applies damage continuously while beam is active (e.g., plasma beam, heal beam)
     */
    DAMAGE_OVER_TIME("Applies damage continuously while beam is active"),
    
    /**
     * Applies damage in discrete intervals while active (e.g., pulse laser)
     */
    BURST("Applies damage in discrete intervals while active");
    
    private final String description;
    
    DamageApplicationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

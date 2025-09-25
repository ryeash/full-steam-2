package com.fullsteam.model;

public abstract class BaseAttributeModification implements AttributeModification {

    private final long expiration;

    protected BaseAttributeModification(long expiration) {
        this.expiration = expiration;
    }

    @Override
    public boolean isExpired() {
        // zero is magic, never expire
        if (expiration == 0) {
            return false;
        } else {
            return System.currentTimeMillis() > expiration;
        }
    }
}

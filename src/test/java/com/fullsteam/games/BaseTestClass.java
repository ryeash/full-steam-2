package com.fullsteam.games;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base test class for all game system tests.
 * Provides a shared Micronaut application context with dependency injection.
 * 
 * Benefits:
 * - Single application startup for all test classes
 * - Shared application context and beans
 * - Faster test execution
 * - Better test isolation
 * - Random port selection to avoid conflicts
 */
@MicronautTest(propertySources = "classpath:test.properties")
public abstract class BaseTestClass {

    /**
     * Setup method that runs before each test.
     * Override this in subclasses for specific test setup.
     */
    @BeforeEach
    void baseSetUp() {
        // Base setup - can be overridden by subclasses
    }
}

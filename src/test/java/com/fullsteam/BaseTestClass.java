package com.fullsteam;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;

@MicronautTest(propertySources = "classpath:test.properties")
public abstract class BaseTestClass {

    /**
     * Setup method that runs before each test.
     * Override this in subclasses for specific test setup.
     */
    @BeforeEach
    protected void baseSetUp() {
    }
}

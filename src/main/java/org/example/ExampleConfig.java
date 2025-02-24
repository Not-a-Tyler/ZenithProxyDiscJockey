package org.example;

/**
 * Example configuration POJO.
 *
 * Configurations are saved and loaded to JSON files
 *
 * All fields should be public and mutable.
 *
 * Fields to static inner classes generate nested JSON objects.
 */
public class ExampleConfig {
    public final ExampleModuleConfig moduleConfig = new ExampleModuleConfig();
    public static class ExampleModuleConfig {
        public boolean enabled = true;
        public int delayTicks = 250;
    }
}

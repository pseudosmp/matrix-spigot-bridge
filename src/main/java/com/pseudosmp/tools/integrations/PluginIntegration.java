package com.pseudosmp.tools.integrations;

public interface PluginIntegration {
    String getName();
    boolean isEnabled();
    void setup();
}

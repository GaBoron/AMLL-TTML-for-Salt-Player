package com.xuncorp.spw.workshop.api;

import org.pf4j.Plugin;

public abstract class SpwPlugin extends Plugin {
    protected final PluginContext pluginContext;

    public SpwPlugin(PluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    public void update() {
    }
}

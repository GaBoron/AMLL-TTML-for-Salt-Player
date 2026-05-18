package com.xuncorp.spw.workshop.api.config;

import java.util.function.Consumer;

public interface ConfigManager {
    ConfigHelper getConfig();

    ConfigHelper getConfig(String fileName);

    void addConfigChangeListener(Consumer<ConfigHelper> listener);

    void addConfigChangeListener(String fileName, Consumer<ConfigHelper> listener);

    void removeConfigChangeListener(Consumer<ConfigHelper> listener);
}

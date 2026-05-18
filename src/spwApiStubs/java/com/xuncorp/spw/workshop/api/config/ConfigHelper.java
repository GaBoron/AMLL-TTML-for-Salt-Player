package com.xuncorp.spw.workshop.api.config;

import java.nio.file.Path;

public interface ConfigHelper {
    <T> T get(String key, T defaultValue);

    void set(String key, Object value);

    boolean save();

    boolean reload();

    Path getConfigPath();
}

package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.WorkshopApi;
import com.xuncorp.spw.workshop.api.config.ConfigHelper;
import com.xuncorp.spw.workshop.api.config.ConfigManager;

/**
 * Bridges SPW-rendered preference controls to the existing loader settings.
 */
final class PluginPreferences {
    static final String CONFIG_FILE = "amll-ttml-settings.json";
    private static final String LOGGING_ENABLED_KEY = "logging.enabled";
    private static final String LOGGING_LEVEL_KEY = "logging.level";
    private static final String VERBOSE_LEVEL = "verbose";

    private PluginPreferences() {
    }

    static void install() {
        try {
            ConfigManager manager = WorkshopApi.manager().createConfigManager();
            ConfigHelper config = manager.getConfig(CONFIG_FILE);
            sync(config);
            manager.addConfigChangeListener(CONFIG_FILE, PluginPreferences::sync);
        } catch (Throwable error) {
            AmllLogger.warn("CONFIG", "SPW preference bridge is unavailable; using local setting files.");
        }
    }

    private static void sync(ConfigHelper config) {
        try {
            config.reload();
            boolean loggingEnabled = config.get(LOGGING_ENABLED_KEY, true);
            String loggingLevel = config.get(LOGGING_LEVEL_KEY, "normal");
            AmllLogger.applyPreferences(loggingEnabled, VERBOSE_LEVEL.equals(loggingLevel));
        } catch (Throwable error) {
            AmllLogger.warn("CONFIG", "Failed to sync SPW preferences.");
        }
    }
}

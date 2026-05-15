package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PluginContext;
import com.xuncorp.spw.workshop.api.SpwPlugin;

public final class AmllTtmlPlugin extends SpwPlugin {
    public AmllTtmlPlugin(PluginContext pluginContext) {
        super(pluginContext);
        AmllLogger.info("INIT", "Plugin initialized.");
    }

    public static void openManualMatcher() {
        ManualMatcher.open();
    }

    public static void enableRuntimeLogs() {
        AmllLogger.setEnabled(true);
    }

    public static void disableRuntimeLogs() {
        AmllLogger.setEnabled(false);
    }

    public static void setNormalLogs() {
        AmllLogger.setVerbose(false);
    }

    public static void setVerboseLogs() {
        AmllLogger.setVerbose(true);
    }

    public static void openLogDirectory() {
        AmllLogger.openLogDirectory();
    }

    public static void clearLogs() {
        AmllLogger.clearLogs();
    }
}

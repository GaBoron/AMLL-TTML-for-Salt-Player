package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PluginContext;
import com.xuncorp.spw.workshop.api.SpwPlugin;

/**
 * Salt Player 插件入口；公开静态方法供 preference_config.json 中的按钮调用。
 */
public final class AmllTtmlPlugin extends SpwPlugin {
    public AmllTtmlPlugin(PluginContext pluginContext) {
        super(pluginContext);
        AmllLogger.info("INIT", "Plugin initialized.");
    }

    @Override
    public void start() {
        super.start();
        PluginPreferences.install();
        AmllLogger.info("INIT", "Plugin preferences initialized.");
    }

    public static void openManualMatcher() {
        ManualMatcher.open();
    }

    public static void openLyricOffsetDialog() {
        LyricSettings.openOffsetDialog();
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

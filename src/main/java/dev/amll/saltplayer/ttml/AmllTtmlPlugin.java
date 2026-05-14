package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PluginContext;
import com.xuncorp.spw.workshop.api.SpwPlugin;

public final class AmllTtmlPlugin extends SpwPlugin {
    public AmllTtmlPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    public static void openManualMatcher() {
        ManualMatcher.open();
    }
}

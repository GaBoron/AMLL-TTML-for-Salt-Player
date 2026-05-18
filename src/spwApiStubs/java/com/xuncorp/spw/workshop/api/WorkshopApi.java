package com.xuncorp.spw.workshop.api;

import com.xuncorp.spw.workshop.api.config.ConfigManager;

public interface WorkshopApi {
    Ui getUi();

    Manager getManager();

    static Ui ui() {
        return Companion.ui();
    }

    static Manager manager() {
        return Companion.manager();
    }

    final class Companion {
        private Companion() {
        }

        public static Ui ui() {
            return null;
        }

        public static Manager manager() {
            return null;
        }
    }

    interface Manager {
        ConfigManager createConfigManager();

        ConfigManager createConfigManager(String pluginId);
    }

    interface Ui {
        void toast(String text, ToastType type);

        enum ToastType {
            Success,
            Warning,
            Error
        }
    }
}

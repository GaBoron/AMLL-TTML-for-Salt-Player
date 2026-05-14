package com.xuncorp.spw.workshop.api;

public interface WorkshopApi {
    Ui getUi();

    static Ui ui() {
        return Companion.ui();
    }

    final class Companion {
        private Companion() {
        }

        public static Ui ui() {
            return null;
        }
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

package dev.amll.saltplayer.ttml;

import javax.swing.JOptionPane;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class LyricSettings {
    private static final int MIN_OFFSET_MILLIS = -300_000;
    private static final int MAX_OFFSET_MILLIS = 300_000;
    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path OFFSET_FILE = CACHE_ROOT.resolve("lyric-offset-ms.txt");

    private LyricSettings() {
    }

    static int offsetMillis() {
        try {
            if (!Files.isRegularFile(OFFSET_FILE)) return 0;
            String value = Files.readString(OFFSET_FILE, StandardCharsets.UTF_8).trim();
            if (value.isBlank()) return 0;
            return clamp(Integer.parseInt(value));
        } catch (Exception error) {
            AmllLogger.warn("CONFIG", "Failed to read lyric offset; using 0 ms.");
            return 0;
        }
    }

    static void openOffsetDialog() {
        String current = Integer.toString(offsetMillis());
        String input = JOptionPane.showInputDialog(
                null,
                "输入歌词偏移毫秒数：正数延后显示，负数提前显示。",
                current
        );
        if (input == null) return;

        try {
            int offset = clamp(Integer.parseInt(input.trim()));
            Files.createDirectories(CACHE_ROOT);
            if (offset == 0) {
                Files.deleteIfExists(OFFSET_FILE);
            } else {
                Files.writeString(OFFSET_FILE, Integer.toString(offset), StandardCharsets.UTF_8);
            }
            AmllLogger.info("CONFIG", "Lyric offset set to " + offset + " ms.");
            JOptionPane.showMessageDialog(null, "歌词偏移已设置为 " + offset + " ms。", "AMLL TTML Loader", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException error) {
            JOptionPane.showMessageDialog(null, "请输入整数毫秒数，例如 500 或 -300。", "AMLL TTML Loader", JOptionPane.ERROR_MESSAGE);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(null, "保存歌词偏移失败：" + error.getMessage(), "AMLL TTML Loader", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_OFFSET_MILLIS, Math.min(MAX_OFFSET_MILLIS, value));
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }
}

package dev.amll.saltplayer.ttml;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一管理插件运行日志、日志设置入口和日志目录清理。
 */
final class AmllLogger {
    private static final int MAX_LOG_FILES = 10;
    private static final int RETAIN_DAYS = 7;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOG_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final AtomicBoolean CLEANUP_DONE = new AtomicBoolean(false);

    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path LOG_ROOT = CACHE_ROOT.resolve("logs");
    private static final Path DISABLED_FLAG = CACHE_ROOT.resolve("logging-disabled.flag");
    private static final Path VERBOSE_FLAG = CACHE_ROOT.resolve("logging-verbose.flag");

    private AmllLogger() {
    }

    static void info(String module, String message) {
        log("INFO", module, message, null, false);
    }

    static void verbose(String module, String message) {
        log("INFO", module, message, null, true);
    }

    static void warn(String module, String message) {
        log("WARN", module, message, null, false);
    }

    static void error(String module, String message, Throwable error) {
        log("ERROR", module, message, error, false);
    }

    static void setEnabled(boolean enabled) {
        try {
            applyEnabled(enabled);
            info("CONFIG", enabled ? "Runtime logging enabled." : "Runtime logging disabled.");
            showMessage(enabled ? "运行日志已启用。" : "运行日志已关闭。");
        } catch (Exception error) {
            showError("更新日志设置失败：" + safeMessage(error));
        }
    }

    static void setVerbose(boolean verbose) {
        try {
            applyVerbose(verbose);
            info("CONFIG", verbose ? "Verbose logging enabled." : "Normal logging enabled.");
            showMessage(verbose ? "日志详细程度已设为详细。" : "日志详细程度已设为普通。");
        } catch (Exception error) {
            showError("更新日志详细程度失败：" + safeMessage(error));
        }
    }

    static void applyPreferences(boolean enabled, boolean verbose) {
        try {
            applyEnabled(enabled);
            applyVerbose(verbose);
            info("CONFIG", "Runtime logging preferences synced.");
        } catch (Exception error) {
            showError("同步日志设置失败：" + safeMessage(error));
        }
    }

    static void openLogDirectory() {
        try {
            Files.createDirectories(LOG_ROOT);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(LOG_ROOT.toFile());
                info("CONFIG", "Opened log directory.");
                return;
            }
            showMessage("日志目录：" + LOG_ROOT);
        } catch (Exception error) {
            showError("打开日志目录失败：" + safeMessage(error));
        }
    }

    static void clearLogs() {
        try {
            Files.createDirectories(LOG_ROOT);
            int deleted = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOG_ROOT, "amll-ttml-loader-*.log")) {
                for (Path file : stream) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
            showMessage("已清理 " + deleted + " 个日志文件。");
        } catch (Exception error) {
            showError("清理日志失败：" + safeMessage(error));
        }
    }

    static String safeText(String value) {
        if (value == null || value.isBlank()) return "";
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) + "..." : cleaned;
    }

    static Path logRoot() {
        return LOG_ROOT;
    }

    private static void log(String level, String module, String message, Throwable error, boolean verboseOnly) {
        if (isDisabled() || verboseOnly && !isVerbose()) return;
        try {
            Files.createDirectories(LOG_ROOT);
            cleanupOldLogs();
            String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
            StringBuilder line = new StringBuilder()
                    .append('[').append(timestamp).append("] ")
                    .append('[').append(level).append("] ")
                    .append('[').append(module).append("] ")
                    .append(sanitize(message)).append(System.lineSeparator());
            if (error != null) {
                line.append(sanitize(stackTrace(error))).append(System.lineSeparator());
            }
            Files.writeString(currentLogFile(), line.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static void cleanupOldLogs() {
        // 每个进程只清理一次，避免频繁写日志时反复扫描日志目录。
        if (!CLEANUP_DONE.compareAndSet(false, true)) return;
        try {
            Files.createDirectories(LOG_ROOT);
            Instant oldestAllowed = Instant.now().minusSeconds(RETAIN_DAYS * 24L * 60L * 60L);
            List<Path> logs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOG_ROOT, "amll-ttml-loader-*.log")) {
                for (Path file : stream) {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(oldestAllowed)) {
                        Files.deleteIfExists(file);
                    } else {
                        logs.add(file);
                    }
                }
            }
            logs.sort(Comparator.comparing(AmllLogger::lastModified).reversed());
            for (int index = MAX_LOG_FILES; index < logs.size(); index++) {
                Files.deleteIfExists(logs.get(index));
            }
        } catch (Exception ignored) {
        }
    }

    private static Path currentLogFile() {
        String date = LocalDate.now().format(LOG_FILE_DATE_FORMAT);
        return LOG_ROOT.resolve("amll-ttml-loader-" + date + ".log");
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    private static boolean isDisabled() {
        return Files.isRegularFile(DISABLED_FLAG);
    }

    private static boolean isVerbose() {
        return Files.isRegularFile(VERBOSE_FLAG);
    }

    private static void applyEnabled(boolean enabled) throws IOException {
        Files.createDirectories(CACHE_ROOT);
        if (enabled) {
            Files.deleteIfExists(DISABLED_FLAG);
        } else {
            Files.writeString(DISABLED_FLAG, Instant.now().toString(), StandardCharsets.UTF_8);
        }
    }

    private static void applyVerbose(boolean verbose) throws IOException {
        Files.createDirectories(CACHE_ROOT);
        if (verbose) {
            Files.writeString(VERBOSE_FLAG, Instant.now().toString(), StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(VERBOSE_FLAG);
        }
    }

    private static String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        // 日志可能随 issue 一起公开，先把常见本机路径替换成占位符。
        String sanitized = value;
        sanitized = replaceKnownPath(sanitized, System.getenv("APPDATA"), "%APPDATA%");
        sanitized = replaceKnownPath(sanitized, System.getenv("USERPROFILE"), "%USERPROFILE%");
        sanitized = replaceKnownPath(sanitized, System.getProperty("user.home"), "%USERPROFILE%");
        sanitized = sanitized.replaceAll("(?i)[A-Z]:\\\\[^\\r\\n\\t]+", "<local-path>");
        sanitized = sanitized.replaceAll("(?i)[A-Z]:/[^\\r\\n\\t ]+", "<local-path>");
        return sanitized;
    }

    private static String replaceKnownPath(String value, String path, String replacement) {
        if (path == null || path.isBlank()) return value;
        return value.replace(path, replacement).replace(path.replace('\\', '/'), replacement);
    }

    private static String safeMessage(Throwable error) {
        return sanitize(safeText(error.getMessage()));
    }

    private static void showMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "AMLL TTML Loader", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "AMLL TTML Loader", JOptionPane.ERROR_MESSAGE);
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }
}

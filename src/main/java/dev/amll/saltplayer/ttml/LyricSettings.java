package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理全局和单曲歌词偏移设置，单位为毫秒。
 */
final class LyricSettings {
    private static final int MIN_OFFSET_MILLIS = -300_000;
    private static final int MAX_OFFSET_MILLIS = 300_000;
    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path OFFSET_FILE = CACHE_ROOT.resolve("lyric-offset-ms.txt");
    private static final Path TRACK_OFFSETS_FILE = CACHE_ROOT.resolve("lyric-offsets.tsv");

    private LyricSettings() {
    }

    static int offsetMillis() {
        return globalOffsetMillis();
    }

    static int offsetMillis(PlaybackExtensionPoint.MediaItem mediaItem) {
        Integer trackOffset = trackOffsetMillis(mediaItem);
        return trackOffset != null ? trackOffset : globalOffsetMillis();
    }

    private static int globalOffsetMillis() {
        try {
            // 文件不存在等同于 0 偏移，便于恢复默认行为。
            if (!Files.isRegularFile(OFFSET_FILE)) return 0;
            String value = Files.readString(OFFSET_FILE, StandardCharsets.UTF_8).trim();
            if (value.isBlank()) return 0;
            return clamp(Integer.parseInt(value));
        } catch (Exception error) {
            AmllLogger.warn("CONFIG", "Failed to read lyric offset; using 0 ms.");
            return 0;
        }
    }

    private static Integer trackOffsetMillis(PlaybackExtensionPoint.MediaItem mediaItem) {
        if (mediaItem == null) return null;
        String songKey = AmllTtmlLoader.cacheKey(mediaItem);
        try {
            if (!Files.isRegularFile(TRACK_OFFSETS_FILE)) return null;
            for (String line : Files.readAllLines(TRACK_OFFSETS_FILE, StandardCharsets.UTF_8)) {
                int firstTab = line.indexOf('\t');
                if (firstTab < 0 || !line.substring(0, firstTab).equals(songKey)) continue;
                String[] fields = line.split("\t");
                if (fields.length < 2) return null;
                return clamp(Integer.parseInt(fields[1]));
            }
        } catch (Exception error) {
            AmllLogger.warn("CONFIG", "Failed to read current track lyric offset.");
        }
        return null;
    }

    static void openOffsetDialog() {
        javax.swing.SwingUtilities.invokeLater(LyricSettings::showOffsetDialog);
    }

    private static void showOffsetDialog() {
        JDialog dialog = Win11Swing.createDialog("歌词偏移调整", 700, 600);
        PlaybackExtensionPoint.MediaItem currentTrack = CurrentTrackStore.get();
        int globalOffset = globalOffsetMillis();
        Integer trackOffset = trackOffsetMillis(currentTrack);
        OffsetEditor globalEditor = createOffsetEditor("全局歌词时间偏移", "未设置单曲偏移的歌曲会使用此数值。", globalOffset);
        OffsetEditor trackEditor = createOffsetEditor(
                "当前歌曲时间偏移",
                currentTrack == null ? "还没有收到当前歌曲，播放或切换歌曲后可单独保存。" : trackSummary(currentTrack),
                trackOffset != null ? trackOffset : globalOffset
        );
        trackEditor.setEnabled(currentTrack != null);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(globalEditor.panel);
        content.add(Box.createVerticalStrut(12));
        content.add(trackEditor.panel);

        JButton cancelButton = Win11Swing.button("取消", false);
        JButton resetButton = Win11Swing.button("重置为 0", false);
        JButton useGlobalButton = Win11Swing.button("当前歌使用全局", false);
        JButton saveCurrentButton = Win11Swing.button("保存当前歌", false);
        JButton saveGlobalButton = Win11Swing.button("保存全局", true);
        useGlobalButton.setEnabled(currentTrack != null && trackOffset != null);
        saveCurrentButton.setEnabled(currentTrack != null);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(cancelButton);
        footer.add(resetButton);
        footer.add(useGlobalButton);
        footer.add(saveCurrentButton);
        footer.add(saveGlobalButton);

        cancelButton.addActionListener(event -> dialog.dispose());
        resetButton.addActionListener(event -> {
            globalEditor.setValue(0);
            trackEditor.setValue(0);
        });
        useGlobalButton.addActionListener(event -> {
            try {
                clearTrackOffset(currentTrack);
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "当前歌曲已恢复使用全局歌词偏移。重新播放当前歌曲后生效。", false);
                dialog.dispose();
            } catch (Exception error) {
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "清除单曲歌词偏移失败：" + error.getMessage(), true);
            }
        });
        saveCurrentButton.addActionListener(event -> {
            try {
                int offset = trackEditor.value();
                saveTrackOffset(currentTrack, offset);
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "当前歌曲歌词偏移已设置为 " + offset + " ms。重新播放当前歌曲后生效。", false);
                dialog.dispose();
            } catch (Exception error) {
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "保存单曲歌词偏移失败：" + error.getMessage(), true);
            }
        });
        saveGlobalButton.addActionListener(event -> {
            try {
                int offset = globalEditor.value();
                saveGlobalOffset(offset);
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "全局歌词偏移已设置为 " + offset + " ms。重新播放当前歌曲后生效。", false);
                dialog.dispose();
            } catch (Exception error) {
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "保存全局歌词偏移失败：" + error.getMessage(), true);
            }
        });

        dialog.setContentPane(Win11Swing.dialogRoot(
                dialog,
                "歌词偏移调整",
                "正数延后显示，负数提前显示。单位：毫秒。",
                content,
                footer
        ));
        dialog.pack();
        dialog.setSize(new Dimension(700, 600));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static OffsetEditor createOffsetEditor(String titleText, String summary, int value) {
        JLabel valueLabel = Win11Swing.label(formatOffset(value));
        valueLabel.setFont(Win11Swing.TITLE_FONT);

        SpinnerNumberModel numberModel = new SpinnerNumberModel(value, MIN_OFFSET_MILLIS, MAX_OFFSET_MILLIS, 100);
        JSpinner spinner = new JSpinner(numberModel);
        spinner.setFont(Win11Swing.BODY_FONT);
        spinner.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JSlider slider = new JSlider(-5000, 5000, clampForSlider(value));
        slider.setOpaque(false);
        slider.setMajorTickSpacing(1000);
        slider.setMinorTickSpacing(250);
        slider.setPaintTicks(true);

        ChangeListener spinnerListener = event -> {
            int next = clamp((Integer) spinner.getValue());
            valueLabel.setText(formatOffset(next));
            if (next >= slider.getMinimum() && next <= slider.getMaximum() && slider.getValue() != next) {
                slider.setValue(next);
            }
        };
        spinner.addChangeListener(spinnerListener);
        slider.addChangeListener(event -> {
            int next = slider.getValue();
            if (!spinner.getValue().equals(next)) {
                spinner.setValue(next);
            }
        });

        JPanel panel = Win11Swing.card(new BorderLayout(0, 12));
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        JLabel title = Win11Swing.label(titleText);
        title.setFont(Win11Swing.BODY_FONT.deriveFont(Font.BOLD));
        header.add(title, BorderLayout.NORTH);
        header.add(Win11Swing.mutedLabel(summary), BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(0, 10));
        controls.setOpaque(false);
        JPanel numberRow = new JPanel(new BorderLayout(10, 0));
        numberRow.setOpaque(false);
        numberRow.add(Win11Swing.label("精确数值"), BorderLayout.WEST);
        numberRow.add(spinner, BorderLayout.CENTER);
        controls.add(numberRow, BorderLayout.NORTH);
        controls.add(slider, BorderLayout.CENTER);

        JPanel quick = new JPanel(new GridLayout(1, 5, 8, 0));
        quick.setOpaque(false);
        addQuickButton(quick, spinner, -1000);
        addQuickButton(quick, spinner, -500);
        addQuickButton(quick, spinner, 0);
        addQuickButton(quick, spinner, 500);
        addQuickButton(quick, spinner, 1000);
        controls.add(quick, BorderLayout.SOUTH);
        panel.add(controls, BorderLayout.SOUTH);
        return new OffsetEditor(panel, spinner);
    }

    private static int clamp(int value) {
        return Math.max(MIN_OFFSET_MILLIS, Math.min(MAX_OFFSET_MILLIS, value));
    }

    private static int clampForSlider(int value) {
        return Math.max(-5000, Math.min(5000, value));
    }

    private static void addQuickButton(JPanel panel, JSpinner spinner, int value) {
        JButton button = Win11Swing.button(value > 0 ? "+" + value : Integer.toString(value), false);
        button.addActionListener(event -> spinner.setValue(value));
        panel.add(button);
    }

    private static void saveGlobalOffset(int offset) throws Exception {
        Files.createDirectories(CACHE_ROOT);
        if (offset == 0) {
            Files.deleteIfExists(OFFSET_FILE);
        } else {
            Files.writeString(OFFSET_FILE, Integer.toString(offset), StandardCharsets.UTF_8);
        }
        AmllTtmlLoader.clearMemoryCache();
        AmllLogger.info("CONFIG", "Lyric offset set to " + offset + " ms.");
    }

    private static void saveTrackOffset(PlaybackExtensionPoint.MediaItem mediaItem, int offset) throws Exception {
        if (mediaItem == null) throw new IllegalStateException("还没有收到当前歌曲。");
        String songKey = AmllTtmlLoader.cacheKey(mediaItem);
        Files.createDirectories(CACHE_ROOT);
        List<String> lines = Files.isRegularFile(TRACK_OFFSETS_FILE)
                ? new ArrayList<>(Files.readAllLines(TRACK_OFFSETS_FILE, StandardCharsets.UTF_8))
                : new ArrayList<>();
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        lines.add(String.join("\t", songKey, Integer.toString(clamp(offset)), Instant.now().toString()));
        Files.write(TRACK_OFFSETS_FILE, lines, StandardCharsets.UTF_8);
        AmllTtmlLoader.clearMemoryCache(songKey);
        AmllLogger.info("CONFIG", "Current track lyric offset set to " + offset + " ms.");
    }

    private static void clearTrackOffset(PlaybackExtensionPoint.MediaItem mediaItem) throws Exception {
        if (mediaItem == null || !Files.isRegularFile(TRACK_OFFSETS_FILE)) return;
        String songKey = AmllTtmlLoader.cacheKey(mediaItem);
        List<String> lines = new ArrayList<>(Files.readAllLines(TRACK_OFFSETS_FILE, StandardCharsets.UTF_8));
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        Files.write(TRACK_OFFSETS_FILE, lines, StandardCharsets.UTF_8);
        AmllTtmlLoader.clearMemoryCache(songKey);
        AmllLogger.info("CONFIG", "Current track lyric offset cleared.");
    }

    private static String formatOffset(int offset) {
        if (offset == 0) return "0 ms";
        return (offset > 0 ? "+" : "") + offset + " ms";
    }

    private static String trackSummary(PlaybackExtensionPoint.MediaItem mediaItem) {
        String title = mediaItem.getTitle() == null || mediaItem.getTitle().isBlank() ? "未知歌名" : mediaItem.getTitle();
        String artist = mediaItem.getArtist() == null || mediaItem.getArtist().isBlank() ? "未知歌手" : mediaItem.getArtist();
        return title + " - " + artist;
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }

    private static final class OffsetEditor {
        private final JPanel panel;
        private final JSpinner spinner;

        private OffsetEditor(JPanel panel, JSpinner spinner) {
            this.panel = panel;
            this.spinner = spinner;
        }

        private int value() {
            return clamp((Integer) spinner.getValue());
        }

        private void setValue(int value) {
            spinner.setValue(clamp(value));
        }

        private void setEnabled(boolean enabled) {
            setComponentEnabled(panel, enabled);
        }

        private static void setComponentEnabled(java.awt.Component component, boolean enabled) {
            component.setEnabled(enabled);
            if (component instanceof java.awt.Container container) {
                for (java.awt.Component child : container.getComponents()) {
                    setComponentEnabled(child, enabled);
                }
            }
        }
    }
}

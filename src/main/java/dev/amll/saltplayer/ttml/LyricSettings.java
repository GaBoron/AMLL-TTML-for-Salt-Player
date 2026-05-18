package dev.amll.saltplayer.ttml;

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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 管理全局歌词偏移设置，单位为毫秒。
 */
final class LyricSettings {
    private static final int MIN_OFFSET_MILLIS = -300_000;
    private static final int MAX_OFFSET_MILLIS = 300_000;
    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path OFFSET_FILE = CACHE_ROOT.resolve("lyric-offset-ms.txt");

    private LyricSettings() {
    }

    static int offsetMillis() {
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

    static void openOffsetDialog() {
        javax.swing.SwingUtilities.invokeLater(LyricSettings::showOffsetDialog);
    }

    private static void showOffsetDialog() {
        JDialog dialog = Win11Swing.createDialog("歌词偏移调整", 520, 360);
        int current = offsetMillis();

        JLabel valueLabel = Win11Swing.label(formatOffset(current));
        valueLabel.setFont(Win11Swing.TITLE_FONT);

        SpinnerNumberModel numberModel = new SpinnerNumberModel(current, MIN_OFFSET_MILLIS, MAX_OFFSET_MILLIS, 100);
        JSpinner spinner = new JSpinner(numberModel);
        spinner.setFont(Win11Swing.BODY_FONT);
        spinner.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JSlider slider = new JSlider(-5000, 5000, clampForSlider(current));
        slider.setOpaque(false);
        slider.setMajorTickSpacing(1000);
        slider.setMinorTickSpacing(250);
        slider.setPaintTicks(true);

        ChangeListener spinnerListener = event -> {
            int value = clamp((Integer) spinner.getValue());
            valueLabel.setText(formatOffset(value));
            if (value >= slider.getMinimum() && value <= slider.getMaximum() && slider.getValue() != value) {
                slider.setValue(value);
            }
        };
        spinner.addChangeListener(spinnerListener);
        slider.addChangeListener(event -> {
            int value = slider.getValue();
            if (!spinner.getValue().equals(value)) {
                spinner.setValue(value);
            }
        });

        JPanel valuePanel = Win11Swing.card(new BorderLayout(0, 12));
        JLabel title = Win11Swing.label("全局歌词时间偏移");
        title.setFont(Win11Swing.BODY_FONT.deriveFont(Font.BOLD));
        valuePanel.add(title, BorderLayout.NORTH);
        valuePanel.add(valueLabel, BorderLayout.CENTER);
        valuePanel.add(Win11Swing.mutedLabel("正数延后显示，负数提前显示。单位：毫秒。"), BorderLayout.SOUTH);

        JPanel controls = Win11Swing.card(new BorderLayout(0, 12));
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

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(valuePanel);
        content.add(Box.createVerticalStrut(12));
        content.add(controls);

        JButton cancelButton = Win11Swing.button("取消", false);
        JButton resetButton = Win11Swing.button("重置为 0", false);
        JButton saveButton = Win11Swing.button("保存", true);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(cancelButton);
        footer.add(resetButton);
        footer.add(saveButton);

        cancelButton.addActionListener(event -> dialog.dispose());
        resetButton.addActionListener(event -> spinner.setValue(0));
        saveButton.addActionListener(event -> {
            try {
                int offset = clamp((Integer) spinner.getValue());
                saveOffset(offset);
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "歌词偏移已设置为 " + offset + " ms。", false);
                dialog.dispose();
            } catch (Exception error) {
                Win11Swing.showMessage(dialog, "AMLL TTML Loader", "保存歌词偏移失败：" + error.getMessage(), true);
            }
        });

        dialog.setContentPane(Win11Swing.dialogRoot(
                dialog,
                "歌词偏移调整",
                "用于修正歌词与播放进度不同步的问题。",
                content,
                footer
        ));
        dialog.pack();
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
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

    private static void saveOffset(int offset) throws Exception {
        Files.createDirectories(CACHE_ROOT);
        if (offset == 0) {
            Files.deleteIfExists(OFFSET_FILE);
        } else {
            Files.writeString(OFFSET_FILE, Integer.toString(offset), StandardCharsets.UTF_8);
        }
        AmllLogger.info("CONFIG", "Lyric offset set to " + offset + " ms.");
    }

    private static String formatOffset(int offset) {
        if (offset == 0) return "0 ms";
        return (offset > 0 ? "+" : "") + offset + " ms";
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }
}

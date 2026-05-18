package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Component;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 手动匹配窗口：允许用户搜索 AMLL 结果、预览歌词并保存当前歌曲覆盖规则。
 */
final class ManualMatcher {
    private static final AmllTtmlLoader LOADER = new AmllTtmlLoader();
    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path CURRENT_MEDIA_FILE = CACHE_ROOT.resolve("current-media.tsv");
    private static volatile PlaybackExtensionPoint.MediaItem currentMediaItem;

    private ManualMatcher() {
    }

    static void setCurrentMediaItem(PlaybackExtensionPoint.MediaItem mediaItem) {
        currentMediaItem = mediaItem;
        saveCurrentMediaItem(mediaItem);
    }

    static void open() {
        // Swing UI 必须在 EDT 上创建和更新。
        SwingUtilities.invokeLater(ManualMatcher::showDialog);
    }

    private static void showDialog() {
        closeExistingDialogs();
        PlaybackExtensionPoint.MediaItem mediaItem = currentMediaItem != null ? currentMediaItem : loadCurrentMediaItem();
        if (mediaItem == null) {
            AmllLogger.warn("MANUAL", "Manual matcher opened without a current media item.");
            Win11Swing.showMessage(null, "AMLL 手动匹配", "还没有收到播放器的当前曲目。请切换或重新播放当前歌曲后再打开手动匹配。", false);
            return;
        }
        currentMediaItem = mediaItem;
        AmllLogger.info("MANUAL", "Manual matcher opened.");

        JDialog dialog = Win11Swing.createDialog("AMLL 手动匹配", 820, 590);
        JTextField titleField = Win11Swing.textField(mediaItem.getTitle(), 28);
        JTextField artistField = Win11Swing.textField(mediaItem.getArtist(), 28);
        JTextField albumField = Win11Swing.textField(mediaItem.getAlbum(), 28);

        DefaultListModel<AmllTtmlLoader.SearchResult> model = new DefaultListModel<>();
        JList<AmllTtmlLoader.SearchResult> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);
        list.setFont(Win11Swing.BODY_FONT);
        list.setFixedCellHeight(56);
        list.setBackground(Win11Swing.FIELD);
        list.setForeground(Win11Swing.TEXT);
        list.setCellRenderer(new SearchResultRenderer());

        JTextArea preview = Win11Swing.textArea(10, 36);
        preview.setText("点击搜索后选择一个结果查看预览。");

        JButton searchButton = Win11Swing.button("搜索", true);
        JButton useAmllButton = Win11Swing.button("使用选中 AMLL", true);
        JButton useLocalButton = Win11Swing.button("使用本地/元数据", false);
        JButton closeButton = Win11Swing.button("关闭", false);

        JPanel fields = Win11Swing.card(new GridBagLayout());
        addField(fields, 0, "歌名", titleField);
        addField(fields, 1, "歌手", artistField);
        addField(fields, 2, "专辑", albumField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(closeButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(searchButton);
        buttons.add(useLocalButton);
        buttons.add(useAmllButton);

        JPanel searchPane = Win11Swing.card(new BorderLayout(0, 10));
        searchPane.add(sectionHeader("搜索结果", "选择一个候选项查看前几句歌词。"), BorderLayout.NORTH);
        searchPane.add(Win11Swing.scroll(list), BorderLayout.CENTER);

        JPanel previewPane = Win11Swing.card(new BorderLayout(0, 10));
        previewPane.add(sectionHeader("歌词预览", "保存后重新播放当前歌曲生效。"), BorderLayout.NORTH);
        previewPane.add(Win11Swing.scroll(preview), BorderLayout.CENTER);

        JPanel results = new JPanel(new GridLayout(1, 2, 12, 0));
        results.setOpaque(false);
        results.add(searchPane);
        results.add(previewPane);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.add(fields, BorderLayout.NORTH);
        content.add(results, BorderLayout.CENTER);

        dialog.setContentPane(Win11Swing.dialogRoot(
                dialog,
                "AMLL 手动匹配",
                "编辑当前曲目信息，选择在线 AMLL 歌词或固定使用本地歌词。",
                content,
                buttons
        ));
        dialog.pack();
        dialog.setSize(new Dimension(820, 590));
        dialog.setLocationRelativeTo(null);

        searchButton.addActionListener(event -> search(titleField, artistField, albumField, model, preview, searchButton));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                // 选择变化后异步加载预览，避免列表选择时卡住窗口。
                loadPreview(mediaItem, list.getSelectedValue(), preview);
            }
        });
        useAmllButton.addActionListener(event -> {
            AmllTtmlLoader.SearchResult result = list.getSelectedValue();
            if (result == null) {
                Win11Swing.showMessage(dialog, "AMLL 手动匹配", "请先选择一个 AMLL 结果。", false);
                return;
            }
            try {
                LOADER.saveOverride(AmllTtmlLoader.cacheKey(mediaItem), result.rawLyricFile());
                Win11Swing.showMessage(dialog, "AMLL 手动匹配", "已保存：来源 AMLL。重新播放当前歌曲后生效。", false);
                dialog.dispose();
            } catch (Exception error) {
                AmllLogger.error("MANUAL", "Failed to save manual AMLL override.", error);
                showError(dialog, error);
            }
        });
        useLocalButton.addActionListener(event -> {
            try {
                LOADER.saveLocalOverride(AmllTtmlLoader.cacheKey(mediaItem));
                Win11Swing.showMessage(dialog, "AMLL 手动匹配", "已保存：使用本地/元数据歌词。重新播放当前歌曲后生效。", false);
                dialog.dispose();
            } catch (Exception error) {
                AmllLogger.error("MANUAL", "Failed to save manual local/default override.", error);
                showError(dialog, error);
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());

        dialog.setVisible(true);
        search(titleField, artistField, albumField, model, preview, searchButton);
    }

    private static void addField(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 0, 5, 10);
        JLabel fieldLabel = Win11Swing.label(label);
        fieldLabel.setPreferredSize(new Dimension(42, 26));
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(5, 0, 5, 0);
        panel.add(field, fieldConstraints);
    }

    private static JComponent sectionHeader(String title, String summary) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel titleLabel = Win11Swing.label(title);
        titleLabel.setFont(Win11Swing.BODY_FONT.deriveFont(Font.BOLD));
        JLabel summaryLabel = Win11Swing.mutedLabel(summary);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(3));
        panel.add(summaryLabel);
        return panel;
    }

    private static void search(
            JTextField titleField,
            JTextField artistField,
            JTextField albumField,
            DefaultListModel<AmllTtmlLoader.SearchResult> model,
            JTextArea preview,
            JButton searchButton
    ) {
        searchButton.setEnabled(false);
        preview.setText("搜索中...");
        model.clear();

        new SwingWorker<List<AmllTtmlLoader.SearchResult>, Void>() {
            @Override
            protected List<AmllTtmlLoader.SearchResult> doInBackground() throws Exception {
                // 搜索会读取索引和可能触发网络下载，因此放到后台线程。
                return LOADER.search(titleField.getText(), artistField.getText(), albumField.getText(), 30);
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<AmllTtmlLoader.SearchResult> results = get();
                    for (AmllTtmlLoader.SearchResult result : results) model.addElement(result);
                    preview.setText(results.isEmpty() ? "没有找到结果。可以修改歌名/歌手/专辑后再搜。" : "选择一个结果查看前几句歌词。");
                } catch (Exception error) {
                    AmllLogger.error("MANUAL", "Manual search failed.", error);
                    preview.setText("搜索失败：" + error.getMessage());
                }
            }
        }.execute();
    }

    private static void loadPreview(PlaybackExtensionPoint.MediaItem mediaItem, AmllTtmlLoader.SearchResult result, JTextArea preview) {
        if (result == null) return;
        preview.setText("加载预览...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return LOADER.preview(mediaItem, result.rawLyricFile(), 6);
            }

            @Override
            protected void done() {
                try {
                    preview.setText(get());
                } catch (Exception error) {
                    AmllLogger.error("MANUAL", "Manual preview failed.", error);
                    preview.setText("预览失败：" + error.getMessage());
                }
            }
        }.execute();
    }

    private static void showError(JDialog parent, Exception error) {
        Win11Swing.showMessage(parent, "AMLL 手动匹配", error.getMessage(), true);
    }

    private static void closeExistingDialogs() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Dialog dialog && "AMLL 手动匹配".equals(dialog.getTitle())) {
                // 关闭旧版本或重复打开的手动匹配窗口，避免新旧 Swing 界面叠在一起。
                dialog.dispose();
            }
        }
    }

    private static void saveCurrentMediaItem(PlaybackExtensionPoint.MediaItem mediaItem) {
        if (mediaItem == null) return;
        try {
            Files.createDirectories(CACHE_ROOT);
            // 使用 Base64 存储，避免标题或路径中的制表符、换行破坏快照格式。
            String line = String.join("\t",
                    encode(mediaItem.getTitle()),
                    encode(mediaItem.getArtist()),
                    encode(mediaItem.getAlbum()),
                    encode(mediaItem.getAlbumArtist()),
                    encode(mediaItem.getPath())
            );
            Files.writeString(CURRENT_MEDIA_FILE, line, StandardCharsets.UTF_8);
        } catch (Exception error) {
            AmllLogger.warn("MANUAL", "Failed to persist current media snapshot.");
        }
    }

    private static PlaybackExtensionPoint.MediaItem loadCurrentMediaItem() {
        try {
            if (!Files.isRegularFile(CURRENT_MEDIA_FILE)) return null;
            String[] fields = Files.readString(CURRENT_MEDIA_FILE, StandardCharsets.UTF_8).trim().split("\t", -1);
            if (fields.length != 5) return null;
            PlaybackExtensionPoint.MediaItem mediaItem = new PlaybackExtensionPoint.MediaItem(
                    decode(fields[0]),
                    decode(fields[1]),
                    decode(fields[2]),
                    decode(fields[3]),
                    decode(fields[4])
            );
            return isUsable(mediaItem) ? mediaItem : null;
        } catch (Exception error) {
            AmllLogger.warn("MANUAL", "Failed to read current media snapshot.");
            return null;
        }
    }

    private static boolean isUsable(PlaybackExtensionPoint.MediaItem mediaItem) {
        return mediaItem != null
                && (!blank(mediaItem.getTitle()) || !blank(mediaItem.getArtist()) || !blank(mediaItem.getPath()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }

    private static final class SearchResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setOpaque(true);
            label.setFont(Win11Swing.BODY_FONT);
            label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            label.setForeground(isSelected ? Color.WHITE : Win11Swing.TEXT);
            label.setBackground(isSelected ? Win11Swing.ACCENT : Win11Swing.FIELD);
            if (value instanceof AmllTtmlLoader.SearchResult result) {
                label.setText("<html><b>" + escape(result.title()) + "</b> - " + escape(result.artist())
                        + "<br><span style='color:" + (isSelected ? "#e8f2ff" : "#666666") + "'>"
                        + escape(result.album()) + "   #" + result.score() + "</span></html>");
            }
            return label;
        }

        private static String escape(String value) {
            if (value == null || value.isBlank()) return "";
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}

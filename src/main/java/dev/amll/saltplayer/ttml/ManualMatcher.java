package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

final class ManualMatcher {
    private static final AmllTtmlLoader LOADER = new AmllTtmlLoader();
    private static volatile PlaybackExtensionPoint.MediaItem currentMediaItem;

    private ManualMatcher() {
    }

    static void setCurrentMediaItem(PlaybackExtensionPoint.MediaItem mediaItem) {
        currentMediaItem = mediaItem;
    }

    static void open() {
        SwingUtilities.invokeLater(ManualMatcher::showDialog);
    }

    private static void showDialog() {
        installSystemLookAndFeel();
        PlaybackExtensionPoint.MediaItem mediaItem = currentMediaItem;
        if (mediaItem == null) {
            AmllLogger.warn("MANUAL", "Manual matcher opened without a current media item.");
            JOptionPane.showMessageDialog(null, "请先播放一首歌。", "AMLL 手动匹配", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        AmllLogger.info("MANUAL", "Manual matcher opened.");

        JDialog dialog = new JDialog((JFrame) null, "AMLL 手动匹配", false);
        JTextField titleField = new JTextField(mediaItem.getTitle(), 28);
        JTextField artistField = new JTextField(mediaItem.getArtist(), 28);
        JTextField albumField = new JTextField(mediaItem.getAlbum(), 28);

        DefaultListModel<AmllTtmlLoader.SearchResult> model = new DefaultListModel<>();
        JList<AmllTtmlLoader.SearchResult> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);

        JTextArea preview = new JTextArea(7, 42);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setText("点击搜索后选择一个结果查看预览。");

        JButton searchButton = new JButton("搜索");
        JButton useAmllButton = new JButton("使用选中 AMLL");
        JButton useLocalButton = new JButton("使用本地/元数据");
        JButton closeButton = new JButton("关闭");

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        addField(fields, 0, "歌名", titleField);
        addField(fields, 1, "歌手", artistField);
        addField(fields, 2, "专辑", albumField);

        JPanel buttons = new JPanel();
        buttons.add(searchButton);
        buttons.add(useAmllButton);
        buttons.add(useLocalButton);
        buttons.add(closeButton);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        center.add(new JScrollPane(preview), BorderLayout.SOUTH);

        dialog.setLayout(new BorderLayout());
        dialog.add(fields, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setMinimumSize(new Dimension(720, 520));
        dialog.setLocationRelativeTo(null);

        searchButton.addActionListener(event -> search(titleField, artistField, albumField, model, preview, searchButton));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadPreview(mediaItem, list.getSelectedValue(), preview);
            }
        });
        useAmllButton.addActionListener(event -> {
            AmllTtmlLoader.SearchResult result = list.getSelectedValue();
            if (result == null) {
                JOptionPane.showMessageDialog(dialog, "请先选择一个 AMLL 结果。", "AMLL 手动匹配", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                LOADER.saveOverride(AmllTtmlLoader.cacheKey(mediaItem), result.rawLyricFile());
                JOptionPane.showMessageDialog(dialog, "已保存：来源 AMLL。重新播放当前歌曲后生效。", "AMLL 手动匹配", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (Exception error) {
                AmllLogger.error("MANUAL", "Failed to save manual AMLL override.", error);
                showError(dialog, error);
            }
        });
        useLocalButton.addActionListener(event -> {
            try {
                LOADER.saveLocalOverride(AmllTtmlLoader.cacheKey(mediaItem));
                JOptionPane.showMessageDialog(dialog, "已保存：使用本地/元数据歌词。重新播放当前歌曲后生效。", "AMLL 手动匹配", JOptionPane.INFORMATION_MESSAGE);
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
        labelConstraints.insets = new Insets(4, 0, 4, 8);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 0, 4, 0);
        panel.add(field, fieldConstraints);
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
        JOptionPane.showMessageDialog(parent, error.getMessage(), "AMLL 手动匹配", JOptionPane.ERROR_MESSAGE);
    }

    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}

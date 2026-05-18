package dev.amll.saltplayer.ttml;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;

final class Win11Swing {
    private static final String UI_FONT_RESOURCE = "/fonts/NotoSansCJKsc-Regular.otf";
    static final Color TEXT = new Color(31, 31, 31);
    static final Color MUTED_TEXT = new Color(96, 96, 96);
    static final Color SURFACE = new Color(249, 251, 245, 238);
    static final Color CARD = new Color(255, 255, 255, 228);
    static final Color FIELD = new Color(255, 255, 255, 242);
    static final Color BORDER = new Color(196, 204, 190, 170);
    static final Color ACCENT = new Color(0, 95, 184);
    private static final Font UI_FONT = loadBundledFont();
    static final Font BODY_FONT = UI_FONT.deriveFont(Font.PLAIN, 13f);
    static final Font TITLE_FONT = UI_FONT.deriveFont(Font.BOLD, 19f);
    static final Font SMALL_FONT = UI_FONT.deriveFont(Font.PLAIN, 12f);

    private Win11Swing() {
    }

    static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    static JDialog createDialog(String title, int width, int height) {
        installLookAndFeel();
        JDialog dialog = new JDialog((JFrame) null, title, false);
        // SPW runs on Windows, so an undecorated transparent Swing window gives us a closer Win11 mica-style shell.
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setMinimumSize(new Dimension(width, height));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        return dialog;
    }

    static void showMessage(Component parent, String title, String message, boolean error) {
        installLookAndFeel();
        JDialog dialog = new JDialog((JFrame) null, title, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextArea body = textArea(4, 32);
        body.setText(message == null ? "" : message);
        body.setBackground(CARD);

        JPanel content = card(new BorderLayout(0, 10));
        JLabel heading = label(error ? "操作失败" : "操作完成");
        heading.setFont(BODY_FONT.deriveFont(Font.BOLD));
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);

        JButton okButton = button("确定", true);
        okButton.addActionListener(event -> dialog.dispose());

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(okButton, BorderLayout.EAST);

        dialog.setContentPane(dialogRoot(dialog, title, "", content, footer));
        dialog.pack();
        dialog.setSize(new Dimension(420, 250));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    static JPanel dialogRoot(JDialog dialog, String title, String subtitle, JComponent content, JComponent footer) {
        JPanel root = new RoundedPanel(new BorderLayout(0, 14), SURFACE, 18);
        root.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER, 18, 1),
                BorderFactory.createEmptyBorder(14, 18, 18, 18)
        ));
        root.add(titleBar(dialog, title, subtitle), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    static JPanel card(LayoutManager layout) {
        JPanel panel = new RoundedPanel(layout, CARD, 12);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER, 12, 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));
        return panel;
    }

    static JButton button(String text, boolean primary) {
        JButton button = new ModernButton(text, primary ? ACCENT : FIELD, primary ? Color.WHITE : TEXT, primary);
        button.setFont(BODY_FONT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(Math.max(90, text.length() * 16 + 32), 34));
        return button;
    }

    static JTextField textField(String text, int columns) {
        JTextField field = new JTextField(text == null ? "" : text, columns);
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setBackground(FIELD);
        field.setCaretColor(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER, 8, 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        return field;
    }

    static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(BODY_FONT);
        area.setForeground(TEXT);
        area.setBackground(FIELD);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return area;
    }

    static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setBorder(new RoundBorder(BORDER, 10, 1));
        return pane;
    }

    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BODY_FONT);
        label.setForeground(TEXT);
        return label;
    }

    static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(MUTED_TEXT);
        return label;
    }

    private static JPanel titleBar(JDialog dialog, String title, String subtitle) {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);

        JPanel text = new JPanel(new BorderLayout(0, 3));
        text.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT);
        text.add(titleLabel, BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isBlank()) {
            text.add(mutedLabel(subtitle), BorderLayout.SOUTH);
        }

        JButton close = new CloseButton();
        close.setPreferredSize(new Dimension(38, 30));
        close.addActionListener(event -> dialog.dispose());

        bar.add(text, BorderLayout.CENTER);
        bar.add(close, BorderLayout.EAST);
        installDrag(bar, dialog);
        installDrag(text, dialog);
        return bar;
    }

    private static void installDrag(Component component, Window window) {
        MouseAdapter adapter = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent event) {
                start = event.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (start == null) return;
                Point location = event.getLocationOnScreen();
                window.setLocation(location.x - start.x, location.y - start.y);
            }
        };
        component.addMouseListener(adapter);
        component.addMouseMotionListener(adapter);
    }

    private static Font loadBundledFont() {
        try (InputStream stream = Win11Swing.class.getResourceAsStream(UI_FONT_RESOURCE)) {
            if (stream == null) throw new IOException("Font resource not found: " + UI_FONT_RESOURCE);
            // Register the bundled open-source CJK font so Swing does not fall back to mixed system glyphs.
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (FontFormatException | IOException error) {
            return new Font("SansSerif", Font.PLAIN, 13);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;

        private RoundedPanel(LayoutManager layout, Color fill, int radius) {
            super(layout);
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ModernButton extends JButton {
        private final Color fill;
        private final Color foreground;
        private final boolean primary;

        private ModernButton(String text, Color fill, Color foreground, boolean primary) {
            super(text);
            this.fill = fill;
            this.foreground = foreground;
            this.primary = primary;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            setForeground(foreground);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = fill;
            if (getModel().isPressed()) {
                color = primary ? new Color(0, 75, 145) : new Color(224, 230, 222);
            } else if (getModel().isRollover()) {
                color = primary ? new Color(0, 105, 205) : new Color(238, 243, 235);
            }
            g.setColor(color);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (!primary) {
                g.setColor(BORDER);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class CloseButton extends JButton {
        private CloseButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("关闭");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (getModel().isPressed()) {
                g.setColor(new Color(214, 219, 211));
            } else if (getModel().isRollover()) {
                g.setColor(new Color(232, 236, 229));
            } else {
                g.setColor(new Color(255, 255, 255, 0));
            }
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int size = 5;
            g.setColor(TEXT);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(centerX - size, centerY - size, centerX + size, centerY + size);
            g.drawLine(centerX + size, centerY - size, centerX - size, centerY + size);
            g.dispose();
        }
    }

    private static final class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final int thickness;

        private RoundBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            g.draw(new RoundRectangle2D.Float(
                    x + thickness / 2f,
                    y + thickness / 2f,
                    width - thickness,
                    height - thickness,
                    radius,
                    radius
            ));
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(thickness, thickness, thickness, thickness);
            return insets;
        }
    }
}

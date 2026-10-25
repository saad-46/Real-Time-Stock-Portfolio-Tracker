package com.portfolio.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Locale;

/**
 * StockVault's design system: one palette, one type scale and a few reusable components, so every window can look and
 * behave the same. Dark, restrained, financial-dashboard styling. No images, no animation libraries.
 *
 * Contrast (against white or the listed background): {@link #TEXT} on {@link #SURFACE} about 15:1, {@link #TEXT_DIM} on
 * {@link #SURFACE} about 7:1, white on {@link #PRIMARY} about 5.4:1 (above the 4.5:1 AA minimum).
 */
public final class Theme {
    private Theme() {
    }

    // ---- palette (dark by default; switched by ThemeMode before the windows that use it are built)
    public static Color BG = new Color(18, 18, 24);
    public static Color SURFACE = new Color(30, 30, 46);
    public static Color SURFACE_HOVER = new Color(40, 40, 62);
    public static Color BORDER = new Color(64, 64, 96);
    public static Color TEXT = new Color(240, 240, 248);
    public static Color TEXT_DIM = new Color(176, 176, 200);
    public static Color PRIMARY = new Color(72, 90, 204);
    public static Color PRIMARY_HOVER = new Color(58, 74, 178);
    public static Color ACCENT = new Color(124, 146, 250);
    public static Color GREEN = new Color(74, 222, 128);
    public static Color RED = new Color(248, 113, 113);
    public static Color AMBER = new Color(251, 191, 36);

    /** Light or dark palette. Windows already on screen keep their colours; build new ones after calling this. */
    static synchronized void setDark(boolean dark) {
        if (dark) {
            BG = new Color(18, 18, 24); SURFACE = new Color(30, 30, 46); SURFACE_HOVER = new Color(40, 40, 62);
            BORDER = new Color(64, 64, 96); TEXT = new Color(240, 240, 248); TEXT_DIM = new Color(176, 176, 200);
            PRIMARY = new Color(72, 90, 204); PRIMARY_HOVER = new Color(58, 74, 178); ACCENT = new Color(124, 146, 250);
            GREEN = new Color(74, 222, 128); RED = new Color(248, 113, 113); AMBER = new Color(251, 191, 36);
        } else {
            BG = new Color(244, 246, 252); SURFACE = new Color(255, 255, 255); SURFACE_HOVER = new Color(236, 240, 252);
            BORDER = new Color(200, 208, 230); TEXT = new Color(22, 28, 50); TEXT_DIM = new Color(92, 102, 134);
            PRIMARY = new Color(72, 90, 204); PRIMARY_HOVER = new Color(58, 74, 178); ACCENT = new Color(72, 90, 204);
            GREEN = new Color(22, 140, 70); RED = new Color(200, 40, 40); AMBER = new Color(176, 108, 0);
        }
    }

    // ---- type scale (one family, three weights of use)
    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String wanted : new String[] { "Segoe UI", "Helvetica Neue", "Inter", "Roboto", "Arial" }) {
            if (Arrays.asList(available).contains(wanted)) {
                return wanted;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, 1).deriveFont(style, size);
    }

    public static final Font H1 = font(Font.BOLD, 26f);
    public static final Font H2 = font(Font.BOLD, 16f);
    public static final Font BODY = font(Font.PLAIN, 14f);
    public static final Font SMALL = font(Font.PLAIN, 12f);
    public static final Font VALUE = font(Font.BOLD, 24f);

    public static final int RADIUS = 14;
    public static final int GAP = 16;

    // ---- formatting
    /** Money with a currency symbol where one is well known, otherwise the ISO code: "₹1,234.50", "$12.00", "CHF 5.00". */
    public static String money(double value, String currency) {
        String c = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
        String symbol = switch (c) {
            case "INR" -> "₹";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            default -> c.isEmpty() ? "" : c + " ";
        };
        return String.format(Locale.US, "%s%,.2f", symbol, value);
    }

    public static String signedMoney(double value, String currency) {
        return (value >= 0 ? "+" : "−") + money(Math.abs(value), currency);
    }

    public static Color gainLoss(double v) {
        return v > 0 ? GREEN : v < 0 ? RED : TEXT_DIM;
    }

    // ---- components
    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        // Swing measures text with integer metrics but paints it with anti-aliased ones, so the last glyph can be clipped
        // when a layout gives a label exactly its measured width. A few pixels of right padding prevent that.
        l.setBorder(new EmptyBorder(0, 0, 0, 8));
        return l;
    }

    /** A rounded surface that groups related content. */
    public static class Card extends JPanel {
        private static final long serialVersionUID = 1L;

        public Card() {
            setOpaque(false);
            setBorder(new EmptyBorder(16, 18, 16, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SURFACE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public enum Kind { PRIMARY, SECONDARY }

    /**
     * Button with hover feedback and a clearly visible keyboard-focus ring. Keyboard users get the same affordances
     * as mouse users (mnemonic via {@link JButton#setMnemonic}, Space/Enter to press).
     */
    public static class Button extends JButton {
        private static final long serialVersionUID = 1L;
        private final Kind kind;
        private boolean hover;

        public Button(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(font(Font.BOLD, 13f));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(9, 16, 9, 16));
            getAccessibleContext().setAccessibleName(text);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill;
            if (!isEnabled()) {
                fill = SURFACE;
            } else if (kind == Kind.PRIMARY) {
                fill = hover || getModel().isPressed() ? PRIMARY_HOVER : PRIMARY;
            } else {
                fill = hover || getModel().isPressed() ? SURFACE_HOVER : SURFACE;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(kind == Kind.PRIMARY ? PRIMARY_HOVER : BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 8, 8);
            }
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? (kind == Kind.PRIMARY ? Color.WHITE : TEXT) : TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    public static Button button(String text, Kind kind) {
        return new Button(text, kind);
    }

    /** Applies the dark palette to a window-level container. */
    public static void style(JComponent c) {
        c.setBackground(BG);
        c.setForeground(TEXT);
    }
}

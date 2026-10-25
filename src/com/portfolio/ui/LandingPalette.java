package com.portfolio.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Colours and small painting helpers shared by the landing page components (dark navy, restrained blue and purple). */
final class LandingPalette {
    static Color NAVY_TOP = new Color(9, 13, 30);
    static Color NAVY_BOTTOM = new Color(14, 20, 46);
    static Color CARD = new Color(24, 32, 64, 215);
    static Color CARD_EDGE = new Color(70, 84, 140, 150);
    static Color BLUE = new Color(70, 130, 245);
    static Color PURPLE = new Color(140, 98, 245);
    static Color TEXT = new Color(244, 246, 255);
    static Color TEXT_DIM = new Color(168, 177, 207);
    static Color GRID = new Color(120, 135, 190, 45);
    static Color UP = new Color(74, 222, 128);
    static Color DOWN = new Color(248, 113, 113);
    static Color WARN = new Color(251, 191, 36);
    /** Input-field background. */
    static Color FIELD = new Color(12, 17, 38);
    /** Text on a filled accent (gradient button, selected range): white in both themes. */
    static final Color ON_ACCENT = Color.WHITE;
    static boolean dark = true;

    /** Colour of small text links: lighter than the accent on dark, darker on light, so both read clearly. */
    static Color link() {
        return dark ? BLUE.brighter() : BLUE.darker();
    }

    static synchronized void setDark(boolean useDark) {
        dark = useDark;
        if (useDark) {
            NAVY_TOP = new Color(9, 13, 30); NAVY_BOTTOM = new Color(14, 20, 46); CARD = new Color(24, 32, 64, 215);
            CARD_EDGE = new Color(70, 84, 140, 150); TEXT = new Color(244, 246, 255); TEXT_DIM = new Color(168, 177, 207);
            GRID = new Color(120, 135, 190, 45); UP = new Color(74, 222, 128); DOWN = new Color(248, 113, 113);
            WARN = new Color(251, 191, 36); FIELD = new Color(12, 17, 38);
        } else {
            NAVY_TOP = new Color(246, 248, 253); NAVY_BOTTOM = new Color(228, 233, 248); CARD = new Color(255, 255, 255, 235);
            CARD_EDGE = new Color(196, 205, 232, 220); TEXT = new Color(22, 28, 50); TEXT_DIM = new Color(88, 98, 130);
            GRID = new Color(70, 90, 150, 40); UP = new Color(18, 140, 66); DOWN = new Color(205, 40, 40);
            WARN = new Color(176, 104, 0); FIELD = new Color(255, 255, 255);
        }
    }

    private LandingPalette() {
    }

    /** A label whose preferred width leaves room for the last glyph (anti-aliased text can overhang the measured width). */
    static javax.swing.JLabel label(String text, java.awt.Font font, Color color) {
        javax.swing.JLabel l = new javax.swing.JLabel(text) {
            private static final long serialVersionUID = 1L;

            @Override public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension d = super.getPreferredSize();
                return new java.awt.Dimension(d.width + 10, d.height);
            }

            @Override public java.awt.Dimension getMaximumSize() { // BoxLayout asks for this, and the UI's own maximum ignores the padding
                return getPreferredSize();
            }
        };
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    static void hints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** A rounded glass-like card: translucent fill and a hairline edge. */
    static void card(Graphics2D g2, int w, int h, int radius) {
        RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius);
        g2.setColor(CARD);
        g2.fill(r);
        g2.setColor(CARD_EDGE);
        g2.draw(r);
    }
}

package com.portfolio.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small neutral pill: a coloured dot and a short text ("Cached · 14:02"). Not a banner; it never takes more than one line. */
final class StatusChip extends javax.swing.JPanel {
    private DataStatus status = DataStatus.LOCAL;
    private String text = "";

    StatusChip() {
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setOpaque(false);
        setVisible(false);
    }

    StatusChip(DataStatus status, String detail, String tooltip) {
        this();
        set(status, detail, tooltip);
    }

    /** Shows the status; {@code tooltip} (optional) explains it in a sentence. */
    void set(DataStatus s, String detail, String tooltip) {
        this.status = s;
        this.text = s.text(detail);
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName("Data status: " + text);
        setVisible(true);
        revalidate();
        repaint();
    }

    DataStatus status() {
        return status;
    }

    String text() {
        return text;
    }

    void clear() {
        setVisible(false);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 30, Math.max(22, fm.getHeight() + 6));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(PremiumStockDashboard.BORDER());
            g2.drawRoundRect(0, 1, getWidth() - 1, h - 3, h, h);
            g2.setColor(status.dot);
            g2.fillOval(9, h / 2 - 3, 7, 7);
            g2.setColor(PremiumStockDashboard.TEXT_DIM());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 22, (h + fm.getAscent() - fm.getDescent()) / 2);
        } finally {
            g2.dispose();
        }
    }
}

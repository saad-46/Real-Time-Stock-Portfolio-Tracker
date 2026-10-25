package com.portfolio.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** An icon (drawn with Java2D, no images or emoji), a title and one line of detail. */
public class FeatureHighlight extends JPanel {
    private static final long serialVersionUID = 1L;

    public enum Icon { CHART, BOLT, SPARK, SHIELD }

    public FeatureHighlight(Icon icon, String title, String detail) {
        setOpaque(false);
        setLayout(new BorderLayout(10, 0));
        setName("feature." + title);
        add(new IconCanvas(icon), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel t = LandingPalette.label(title, Theme.font(java.awt.Font.BOLD, 13f), LandingPalette.TEXT);
        JLabel d = LandingPalette.label(detail, Theme.font(java.awt.Font.PLAIN, 12f), LandingPalette.TEXT_DIM);
        text.add(t);
        text.add(d);
        add(text, BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName(title);
        getAccessibleContext().setAccessibleDescription(detail);
        // informational only: a restrained hover glow, no click behaviour and no hand cursor
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }

            @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
        });
    }

    private boolean hover;

    @Override
    protected void paintComponent(Graphics g) {
        if (hover) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            g2.setColor(new java.awt.Color(110, 130, 240, 26));
            g2.fillRoundRect(-4, -2, getWidth() + 8, getHeight() + 4, 14, 14);
            g2.setColor(new java.awt.Color(110, 130, 240, 90));
            g2.drawRoundRect(-4, -2, getWidth() + 7, getHeight() + 3, 14, 14);
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private static final class IconCanvas extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Icon icon;

        IconCanvas(Icon icon) {
            this.icon = icon;
            setOpaque(false);
            setPreferredSize(new Dimension(40, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            g2.setPaint(new GradientPaint(0, 0, new java.awt.Color(70, 130, 245, 70), 40, 40, new java.awt.Color(140, 98, 245, 70)));
            g2.fill(new RoundRectangle2D.Float(0, 0, 38, 38, 12, 12));
            g2.setPaint(new GradientPaint(0, 0, LandingPalette.BLUE, 40, 40, LandingPalette.PURPLE));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.translate(9, 9);
            Path2D p = new Path2D.Float();
            switch (icon) {
                case CHART -> {
                    p.moveTo(0, 18); p.lineTo(6, 10); p.lineTo(11, 14); p.lineTo(20, 2);
                    g2.draw(p);
                    g2.drawLine(15, 2, 20, 2);
                    g2.drawLine(20, 2, 20, 7);
                }
                case BOLT -> {
                    p.moveTo(12, 0); p.lineTo(3, 11); p.lineTo(10, 11); p.lineTo(8, 20); p.lineTo(18, 8); p.lineTo(11, 8); p.closePath();
                    g2.draw(p);
                }
                case SPARK -> {
                    p.moveTo(10, 0); p.lineTo(12, 8); p.lineTo(20, 10); p.lineTo(12, 12); p.lineTo(10, 20); p.lineTo(8, 12);
                    p.lineTo(0, 10); p.lineTo(8, 8); p.closePath();
                    g2.draw(p);
                }
                case SHIELD -> {
                    p.moveTo(10, 0); p.lineTo(19, 3); p.lineTo(19, 10); p.curveTo(19, 15, 15, 19, 10, 21);
                    p.curveTo(5, 19, 1, 15, 1, 10); p.lineTo(1, 3); p.closePath();
                    g2.draw(p);
                    g2.drawLine(6, 10, 9, 13);
                    g2.drawLine(9, 13, 14, 7);
                }
            }
            g2.dispose();
        }
    }
}

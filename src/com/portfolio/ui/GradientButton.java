package com.portfolio.ui;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A button painted with Java2D: a blue-to-purple gradient (primary) or an outline (secondary), with hover, pressed,
 * disabled and keyboard-focus states. It is an ordinary {@link JButton}: Space/Enter, mnemonics and accessibility work.
 */
public class GradientButton extends JButton {
    private static final long serialVersionUID = 1L;

    public enum Style { PRIMARY, SECONDARY }

    private final Style style;
    private boolean hover;

    public GradientButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(Theme.font(java.awt.Font.BOLD, 15f));
        setForeground(Color.WHITE);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(12, 28, 12, 28));
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
        return new Dimension(Math.max(d.width, 150), Math.max(d.height, 46));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        int w = getWidth();
        int h = getHeight();
        boolean pressed = getModel().isPressed();
        RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 14, 14);
        if (!isEnabled()) {
            g2.setColor(new Color(45, 52, 84));
            g2.fill(shape);
        } else if (style == Style.PRIMARY) {
            Color a = LandingPalette.BLUE;
            Color b = LandingPalette.PURPLE;
            if (pressed) {
                a = a.darker();
                b = b.darker();
            } else if (hover) {
                a = brighter(a);
                b = brighter(b);
            }
            g2.setPaint(new GradientPaint(0, 0, a, w, h, b));
            g2.fill(shape);
        } else {
            g2.setColor(pressed ? new Color(60, 74, 130, 120) : hover ? new Color(60, 74, 130, 70) : new Color(0, 0, 0, 0));
            g2.fill(shape);
            g2.setColor(hover ? LandingPalette.BLUE : LandingPalette.CARD_EDGE.darker());
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(shape);
        }
        if (isFocusOwner()) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 3f, 3f }, 0f));
            g2.draw(new RoundRectangle2D.Float(3.5f, 3.5f, w - 7f, h - 7f, 10, 10));
        }
        g2.setFont(getFont());
        g2.setColor(!isEnabled() ? LandingPalette.TEXT_DIM : style == Style.PRIMARY ? Color.WHITE : LandingPalette.TEXT);
        FontMetrics fm = g2.getFontMetrics();
        int x = (w - fm.stringWidth(getText())) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), x, y);
        g2.dispose();
    }

    private static Color brighter(Color c) {
        return new Color(Math.min(255, c.getRed() + 22), Math.min(255, c.getGreen() + 22), Math.min(255, c.getBlue() + 22));
    }
}

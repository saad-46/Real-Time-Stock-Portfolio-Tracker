package com.portfolio.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** A small line chart without axes. With fewer than two values it draws only a dashed baseline: it never invents a shape. */
public class SparklineComponent extends JComponent {
    private static final long serialVersionUID = 1L;

    private double[] values = new double[0];
    private Color color = LandingPalette.BLUE;

    public SparklineComponent() {
        setOpaque(false);
        setPreferredSize(new Dimension(120, 36));
        setMinimumSize(new Dimension(60, 24));
    }

    public void setValues(double[] values, Color color) {
        this.values = values == null ? new double[0] : values.clone();
        this.color = color == null ? LandingPalette.BLUE : color;
        repaint();
    }

    public boolean hasData() {
        return values.length >= 2;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        int w = getWidth();
        int h = getHeight();
        if (values.length < 2 || w < 4 || h < 4) {
            g2.setColor(LandingPalette.GRID);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 3f, 4f }, 0f));
            g2.drawLine(0, h / 2, w, h / 2);
            g2.dispose();
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double span = max - min;
        int pad = 3;
        Path2D line = new Path2D.Double();
        for (int i = 0; i < values.length; i++) {
            double x = pad + (w - 2.0 * pad) * i / (values.length - 1);
            double y = span == 0 ? h / 2.0 : pad + (h - 2.0 * pad) * (1 - (values[i] - min) / span);
            if (i == 0) {
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
        }
        Path2D area = new Path2D.Double(line);
        area.lineTo(w - pad, h);
        area.lineTo(pad, h);
        area.closePath();
        g2.setPaint(new GradientPaint(0, 0, new Color(color.getRed(), color.getGreen(), color.getBlue(), 70), 0, h,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)));
        g2.fill(area);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        g2.dispose();
    }
}

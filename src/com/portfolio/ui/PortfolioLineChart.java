package com.portfolio.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Locale;

/**
 * An interactive line chart drawn with Java2D: smooth line, gradient area, grid, axis labels, a highlighted latest point and a
 * hover crosshair with a tooltip (label, exact value, unit, data status). Click (or Enter) pins a point, Left/Right/Home/End move
 * the selection, Escape clears it. The chart only draws the series it is given; a series marked illustrative carries a visible
 * badge and a tooltip that says so. Pointer-to-data mapping is pure geometry ({@link #plotArea}, {@link #xFor}, {@link #indexAt}) so
 * it can be tested without a window.
 */
public class PortfolioLineChart extends JPanel {
    private static final long serialVersionUID = 1L;

    /** What the chart shows. {@code labels} has one entry per value (already formatted for the tooltip and the x axis). */
    public record Series(String title, String unit, String status, boolean illustrative, String[] labels, double[] values) {
        public Series {
            labels = labels.clone();
            values = values.clone();
            if (labels.length != values.length) {
                throw new IllegalArgumentException("one label per value");
            }
        }

        @Override public String[] labels() { return labels.clone(); }

        @Override public double[] values() { return values.clone(); }
    }

    static final int LEFT = 58;
    static final int RIGHT = 18;
    static final int TOP = 14;
    static final int BOTTOM = 28;

    private Series series;
    private String emptyMessage = "No data to show";
    private int hover = -1;
    private int selected = -1;

    public PortfolioLineChart() {
        setOpaque(false);
        setFocusable(true);
        setPreferredSize(new Dimension(520, 260));
        setMinimumSize(new Dimension(280, 180));
        getAccessibleContext().setAccessibleName("Interactive line chart");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(indexAt(e.getX())); }

            @Override public void mouseExited(MouseEvent e) { setHover(-1); }

            @Override public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                int i = indexAt(e.getX());
                select(i == selected ? -1 : i);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }

            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int n = pointCount();
                if (n == 0) {
                    return;
                }
                int cur = selected >= 0 ? selected : n - 1;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> select(Math.max(0, cur - 1));
                    case KeyEvent.VK_RIGHT -> select(Math.min(n - 1, selected < 0 ? n - 1 : cur + 1));
                    case KeyEvent.VK_HOME -> select(0);
                    case KeyEvent.VK_END -> select(n - 1);
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> select(selected < 0 ? n - 1 : -1);
                    case KeyEvent.VK_ESCAPE -> select(-1);
                    default -> { }
                }
            }
        });
    }

    // ------------------------------------------------------------------ data

    public void setSeries(Series s) {
        this.series = s;
        this.hover = -1;
        this.selected = -1;
        getAccessibleContext().setAccessibleDescription(s == null ? emptyMessage
                : s.title() + (s.illustrative() ? ", illustrative sample, not real data" : "") + ", " + s.values().length + " points");
        repaint();
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
        repaint();
    }

    public Series series() {
        return series;
    }

    public boolean isIllustrative() {
        return series != null && series.illustrative();
    }

    public int pointCount() {
        return series == null ? 0 : series.values.length;
    }

    public int hoverIndex() {
        return hover;
    }

    public int selectedIndex() {
        return selected;
    }

    /** The point whose tooltip is showing (pinned selection first, else hover), or -1. */
    public int activeIndex() {
        return selected >= 0 ? selected : hover;
    }

    /** The tooltip text for a point, exactly the label and value of the series being drawn. */
    public String tooltipText(int i) {
        if (series == null || i < 0 || i >= series.values.length) {
            return null;
        }
        double first = series.values[0];
        String since = first == 0 ? "" : String.format(Locale.ROOT, "%+.2f%% since %s", (series.values[i] / first - 1) * 100, series.labels[0]);
        return series.labels[i] + "  " + formatValue(series.values[i]) + (series.unit().isEmpty() ? "" : " " + series.unit())
                + "\n" + since + "\n" + (series.illustrative() ? "Illustrative sample, not real data" : series.status());
    }

    public void select(int i) {
        int n = pointCount();
        selected = i < 0 || i >= n ? -1 : i;
        repaint();
    }

    private void setHover(int i) {
        if (i != hover) {
            hover = i;
            repaint();
        }
    }

    /** A deterministic smooth 30-point shape indexed to 100, used only for the clearly labelled sample preview. */
    public static double[] illustrativeSeries() {
        double[] v = new double[30];
        for (int i = 0; i < v.length; i++) {
            double t = i / (double) (v.length - 1);
            v[i] = 100 + 9 * t + 3.2 * Math.sin(t * 7.0) + 1.4 * Math.sin(t * 17.0 + 1.0);
        }
        return v;
    }

    /** The labelled sample as a series (relative day labels, never real dates). */
    public static Series illustrativeSample() {
        double[] v = illustrativeSeries();
        String[] labels = new String[v.length];
        for (int i = 0; i < v.length; i++) {
            int back = v.length - 1 - i;
            labels[i] = back == 0 ? "Today" : back + "d ago";
        }
        return new Series("Portfolio performance", "(index, start = 100)", "Illustrative sample", true, labels, v);
    }

    // ------------------------------------------------------------------ geometry (pure)

    /** The drawing area inside a component of the given size. */
    static Rectangle plotArea(int width, int height) {
        return new Rectangle(LEFT, TOP, Math.max(10, width - LEFT - RIGHT), Math.max(10, height - TOP - BOTTOM));
    }

    static double xFor(int index, int count, Rectangle plot) {
        return count <= 1 ? plot.x : plot.x + plot.width * index / (double) (count - 1);
    }

    /** The point nearest to a pointer x position, or -1 outside the plot (with a small margin) or without data. */
    static int indexAt(int x, int count, Rectangle plot) {
        if (count < 1 || x < plot.x - 12 || x > plot.x + plot.width + 12) {
            return -1;
        }
        if (count == 1) {
            return 0;
        }
        int i = (int) Math.round((x - plot.x) * (count - 1) / (double) plot.width);
        return Math.max(0, Math.min(count - 1, i));
    }

    private int indexAt(int x) {
        return indexAt(x, pointCount(), plotArea(getWidth(), getHeight()));
    }

    // ------------------------------------------------------------------ painting

    /** The exact value as shown in tooltips: two decimals, thousands separators. */
    static String formatValue(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    /** Axis labels: fewer decimals when the visible range is wide. */
    private String formatAxis(double v) {
        double span = range();
        return String.format(Locale.ROOT, span >= 100 ? "%,.0f" : span >= 10 ? "%,.1f" : "%,.2f", v);
    }

    private double range() {
        if (series == null || series.values.length == 0) {
            return 0;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : series.values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        int w = getWidth();
        int h = getHeight();
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        if (series == null || series.values.length < 2) {
            g2.setColor(LandingPalette.TEXT_DIM);
            String msg = emptyMessage;
            g2.drawString(msg, Math.max(8, (w - fm.stringWidth(msg)) / 2), h / 2);
            focusRing(g2, w, h);
            g2.dispose();
            return;
        }
        double[] values = series.values;
        Rectangle plot = plotArea(w, h);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double pad = Math.max((max - min) * 0.12, Math.abs(max) * 1e-6 + 1e-9);
        min -= pad;
        max += pad;

        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            int y = plot.y + plot.height * i / 4;
            g2.setColor(LandingPalette.GRID);
            g2.drawLine(plot.x, y, plot.x + plot.width, y);
            String s = formatAxis(max - (max - min) * i / 4);
            g2.setColor(LandingPalette.TEXT_DIM);
            g2.drawString(s, plot.x - 8 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
        }
        int[] idx = { 0, values.length / 2, values.length - 1 };
        for (int k = 0; k < idx.length; k++) {
            String s = series.labels[idx[k]];
            int x = (int) xFor(idx[k], values.length, plot);
            int sw = fm.stringWidth(s);
            g2.setColor(LandingPalette.TEXT_DIM);
            g2.drawString(s, k == 0 ? x : k == 2 ? x - sw : x - sw / 2, plot.y + plot.height + 18);
        }

        double[] xs = new double[values.length];
        double[] ys = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            xs[i] = xFor(i, values.length, plot);
            ys[i] = plot.y + plot.height * (1 - (values[i] - min) / (max - min));
        }
        Path2D line = smooth(xs, ys);
        Path2D area = new Path2D.Double(line);
        area.lineTo(xs[xs.length - 1], plot.y + plot.height);
        area.lineTo(xs[0], plot.y + plot.height);
        area.closePath();
        g2.setPaint(new GradientPaint(0, plot.y, new Color(90, 120, 245, 110), 0, plot.y + plot.height, new Color(140, 98, 245, 0)));
        g2.fill(area);
        g2.setPaint(new GradientPaint(plot.x, 0, LandingPalette.BLUE, plot.x + plot.width, 0, LandingPalette.PURPLE));
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);

        // latest point
        marker(g2, xs[xs.length - 1], ys[ys.length - 1], false);

        int active = activeIndex();
        if (active >= 0 && active < values.length) {
            g2.setColor(new Color(220, 228, 255, 110));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 4f, 4f }, 0f));
            g2.drawLine((int) xs[active], plot.y, (int) xs[active], plot.y + plot.height);
            g2.drawLine(plot.x, (int) ys[active], plot.x + plot.width, (int) ys[active]);
            marker(g2, xs[active], ys[active], true);
            tooltip(g2, tooltipText(active), xs[active], ys[active], plot, selected >= 0);
        }

        if (series.illustrative()) {
            String badge = "ILLUSTRATIVE SAMPLE";
            g2.setFont(Theme.font(java.awt.Font.BOLD, 10f));
            FontMetrics bm = g2.getFontMetrics();
            int bw = bm.stringWidth(badge) + 16;
            int bx = plot.x + 10;
            g2.setColor(new Color(251, 191, 36, 40));
            g2.fillRoundRect(bx, plot.y + 4, bw, 20, 10, 10);
            g2.setColor(LandingPalette.WARN);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(bx, plot.y + 4, bw, 20, 10, 10);
            g2.drawString(badge, bx + 8, plot.y + 4 + (20 - bm.getHeight()) / 2 + bm.getAscent());
        }
        focusRing(g2, w, h);
        g2.dispose();
    }

    private void focusRing(Graphics2D g2, int w, int h) {
        if (isFocusOwner()) {
            g2.setColor(new Color(255, 255, 255, 170));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 3f, 3f }, 0f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        }
    }

    private static void marker(Graphics2D g2, double x, double y, boolean strong) {
        g2.setColor(new Color(140, 98, 245, strong ? 110 : 70));
        g2.fill(new Ellipse2D.Double(x - 9, y - 9, 18, 18));
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
        g2.setColor(LandingPalette.PURPLE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
    }

    private static void tooltip(Graphics2D g2, String text, double px, double py, Rectangle plot, boolean pinned) {
        if (text == null) {
            return;
        }
        String[] lines = text.split("\n");
        g2.setFont(Theme.font(java.awt.Font.BOLD, 12f));
        FontMetrics bold = g2.getFontMetrics();
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11f));
        FontMetrics plain = g2.getFontMetrics();
        int w = bold.stringWidth(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            w = Math.max(w, plain.stringWidth(lines[i]));
        }
        w += 20;
        int h = 14 + bold.getHeight() + (lines.length - 1) * plain.getHeight();
        int x = (int) px + 14;
        if (x + w > plot.x + plot.width + RIGHT - 2) {
            x = (int) px - 14 - w;
        }
        int y = (int) py - h - 12;
        if (y < 2) {
            y = (int) py + 16;
        }
        x = Math.max(2, x);
        g2.setColor(new Color(10, 14, 32, 235));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(pinned ? LandingPalette.PURPLE : LandingPalette.CARD_EDGE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        g2.setFont(Theme.font(java.awt.Font.BOLD, 12f));
        g2.setColor(Color.WHITE);
        g2.drawString(lines[0], x + 10, y + 7 + bold.getAscent());
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11f));
        int ly = y + 7 + bold.getHeight();
        for (int i = 1; i < lines.length; i++) {
            boolean change = i == 1 && lines[i].contains("since");
            g2.setColor(change ? (lines[i].startsWith("-") ? LandingPalette.DOWN : LandingPalette.UP) : LandingPalette.TEXT_DIM);
            g2.drawString(lines[i], x + 10, ly + plain.getAscent());
            ly += plain.getHeight();
        }
    }

    /** Catmull-Rom spline through the points, as cubic Bezier segments. */
    private static Path2D smooth(double[] xs, double[] ys) {
        Path2D p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 0; i < xs.length - 1; i++) {
            int i0 = Math.max(0, i - 1);
            int i2 = i + 1;
            int i3 = Math.min(xs.length - 1, i + 2);
            p.curveTo(xs[i] + (xs[i2] - xs[i0]) / 6.0, ys[i] + (ys[i2] - ys[i0]) / 6.0,
                    xs[i2] - (xs[i3] - xs[i]) / 6.0, ys[i2] - (ys[i3] - ys[i]) / 6.0, xs[i2], ys[i2]);
        }
        return p;
    }
}

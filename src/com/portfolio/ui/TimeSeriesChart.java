package com.portfolio.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * A time-based line chart drawn with Java2D, shared by the portfolio, wallet and stock-analysis screens.
 *
 * <p>It plots only the points it is given (never generates or interpolates data), keeps the time axis proportional to the real
 * timestamps, shows a crosshair with a tooltip on hover (and with the arrow keys), and shows a message instead of a chart when
 * there is nothing honest to draw.
 */
public class TimeSeriesChart extends JPanel {
    /** One line: parallel arrays of epoch milliseconds (ascending) and values. {@code step} draws balances as steps. */
    public record Series(String name, Color color, long[] times, double[] values, boolean step, boolean fill) {
        public Series {
            if (times.length != values.length) {
                throw new IllegalArgumentException("times and values differ in length");
            }
            for (int i = 1; i < times.length; i++) {
                if (times[i] < times[i - 1]) {
                    throw new IllegalArgumentException("times must be ascending");
                }
            }
            times = times.clone();
            values = values.clone();
        }

        @Override public long[] times() { return times.clone(); }

        @Override public double[] values() { return values.clone(); }

        public int size() { return values.length; }

        /** The part of this series at or after {@code fromMillis}, keeping the last earlier point so the line starts at the left edge. */
        public Series since(long fromMillis) {
            int first = 0;
            while (first < times.length && times[first] < fromMillis) {
                first++;
            }
            int start = Math.max(0, first - 1);
            if (first == 0) {
                return this;
            }
            return new Series(name, color, java.util.Arrays.copyOfRange(times, start, times.length),
                    java.util.Arrays.copyOfRange(values, start, values.length), step, fill);
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private List<Series> series = List.of();
    private String message = "No data";
    private String footnote = "";
    private String axisPrefix = "";
    private DoubleFunction<String> valueFormat = v -> String.format(Locale.ENGLISH, "%,.2f", v);
    private ZoneId zone = ZoneId.systemDefault();
    private boolean dailyData = true;
    private long hoverTime = Long.MIN_VALUE;
    private int keyboardIndex = -1;

    public TimeSeriesChart() {
        setOpaque(false);
        setFocusable(true);
        setPreferredSize(new Dimension(400, 260));
        getAccessibleContext().setAccessibleName("Price history chart");
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoverTo(e.getX()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { clearHover(); }

            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { repaint(); }

            @Override public void focusGained(FocusEvent e) { repaint(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                Series first = primary();
                if (first == null) {
                    return;
                }
                int n = first.size();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> keyboardIndex = keyboardIndex < 0 ? n - 1 : Math.max(0, keyboardIndex - 1);
                    case KeyEvent.VK_RIGHT -> keyboardIndex = keyboardIndex < 0 ? n - 1 : Math.min(n - 1, keyboardIndex + 1);
                    case KeyEvent.VK_HOME -> keyboardIndex = 0;
                    case KeyEvent.VK_END -> keyboardIndex = n - 1;
                    case KeyEvent.VK_ESCAPE -> {
                        keyboardIndex = -1;
                        hoverTime = Long.MIN_VALUE;
                        repaint();
                        return;
                    }
                    default -> {
                        return;
                    }
                }
                hoverTime = first.times[keyboardIndex];
                repaint();
            }
        });
    }

    // ------------------------------------------------------------------ state

    /** Shows these lines. An empty list, or lines with fewer than two points, show the current message instead. */
    public void setSeries(List<Series> lines, boolean dailyData) {
        this.series = List.copyOf(lines);
        this.dailyData = dailyData;
        this.hoverTime = Long.MIN_VALUE;
        this.keyboardIndex = -1;
        repaint();
    }

    public void setMessage(String text) {
        this.message = text == null ? "" : text;
        this.series = List.of();
        repaint();
    }

    /** Small text at the bottom right, e.g. the data source and freshness. */
    public void setFootnote(String text) {
        this.footnote = text == null ? "" : text;
        repaint();
    }

    public void setValueFormat(DoubleFunction<String> format) {
        this.valueFormat = format;
    }

    /** Currency symbol shown before the value-axis labels. */
    public void setAxisPrefix(String prefix) {
        this.axisPrefix = prefix == null ? "" : prefix;
    }

    public void setZone(ZoneId zone) {
        this.zone = zone;
    }

    public void setChartName(String name) {
        getAccessibleContext().setAccessibleName(name);
    }

    public boolean hasChart() {
        return primary() != null;
    }

    private Series primary() {
        for (Series s : series) {
            if (s.size() >= 2) {
                return s;
            }
        }
        return null;
    }

    private void hoverTo(int x) {
        if (primary() == null) {
            return;
        }
        Rectangle plot = plotArea(getWidth(), getHeight(), series.size() > 1);
        long[] span = timeSpan(series);
        hoverTime = timeAt(x, span[0], span[1], plot);
        keyboardIndex = -1;
        repaint();
    }

    private void clearHover() {
        hoverTime = Long.MIN_VALUE;
        repaint();
    }

    public long hoverTime() {
        return hoverTime;
    }

    // ------------------------------------------------------------------ pure helpers (unit tested)

    static Rectangle plotArea(int w, int h, boolean legend) {
        int left = 64, right = 16, top = legend ? 30 : 14, bottom = 34;
        return new Rectangle(left, top, Math.max(10, w - left - right), Math.max(10, h - top - bottom));
    }

    static long[] timeSpan(List<Series> lines) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Series s : lines) {
            if (s.size() == 0) {
                continue;
            }
            min = Math.min(min, s.times[0]);
            max = Math.max(max, s.times[s.size() - 1]);
        }
        if (min == Long.MAX_VALUE) {
            return new long[] { 0, 1 };
        }
        return new long[] { min, Math.max(max, min + 1) };
    }

    static double[] valueSpan(List<Series> lines) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Series s : lines) {
            for (double v : s.values) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (min > max) {
            return new double[] { 0, 1 };
        }
        if (min == max) {
            double pad = Math.max(1, Math.abs(min) * 0.05);
            return new double[] { min - pad, max + pad };
        }
        double pad = (max - min) * 0.08;
        return new double[] { min - pad, max + pad };
    }

    static double xFor(long t, long t0, long t1, Rectangle plot) {
        return plot.x + (double) (t - t0) / (t1 - t0) * plot.width;
    }

    static long timeAt(int x, long t0, long t1, Rectangle plot) {
        double f = (x - plot.x) / (double) plot.width;
        f = Math.max(0, Math.min(1, f));
        return t0 + Math.round(f * (t1 - t0));
    }

    static double yFor(double v, double lo, double hi, Rectangle plot) {
        return plot.y + plot.height - (v - lo) / (hi - lo) * plot.height;
    }

    /** Index of the point whose time is closest to {@code t}. */
    static int nearestIndex(long[] times, long t) {
        int lo = 0, hi = times.length - 1;
        if (hi < 0) {
            return -1;
        }
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] < t) lo = mid + 1;
            else hi = mid;
        }
        if (lo > 0 && Math.abs(times[lo - 1] - t) <= Math.abs(times[lo] - t)) {
            return lo - 1;
        }
        return lo;
    }

    /** Value of a series at time {@code t}: the last point at or before it (balances hold their value), or NaN before the first. */
    static double valueAt(Series s, long t) {
        int i = nearestIndex(s.times, t);
        if (i < 0) {
            return Double.NaN;
        }
        if (s.times[i] > t) {
            i--;
        }
        return i < 0 ? Double.NaN : s.values[i];
    }

    /** "Nice" axis values covering [lo, hi]. */
    static double[] niceTicks(double lo, double hi, int target) {
        double range = hi - lo;
        double rough = range / Math.max(1, target);
        double mag = Math.pow(10, Math.floor(Math.log10(rough)));
        double norm = rough / mag;
        double step = (norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10) * mag;
        double first = Math.ceil(lo / step) * step;
        List<Double> out = new ArrayList<>();
        for (double v = first; v <= hi + step * 1e-9; v += step) {
            out.add(Math.abs(v) < step * 1e-9 ? 0.0 : v);
        }
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    String formatAxisTime(long t, long spanMillis) {
        var z = Instant.ofEpochMilli(t).atZone(zone);
        if (spanMillis <= 2L * 24 * 3600 * 1000 && !dailyData) {
            return TIME.format(z);
        }
        if (spanMillis > 400L * 24 * 3600 * 1000) {
            return DAY_YEAR.format(z);
        }
        return DAY.format(z);
    }

    String tooltipText(long t) {
        Series p = primary();
        if (p == null) {
            return "";
        }
        var z = Instant.ofEpochMilli(t).atZone(zone);
        StringBuilder sb = new StringBuilder(dailyData ? FULL_DAY.format(z) : FULL.format(z));
        for (Series s : series) {
            double v = valueAt(s, t);
            if (!Double.isNaN(v)) {
                sb.append("\n").append(s.name()).append(": ").append(valueFormat.apply(v));
            }
        }
        int i = nearestIndex(p.times, t);
        if (i > 0 && p.values[i - 1] != 0) {
            double change = (p.values[i] / p.values[i - 1] - 1) * 100;
            sb.append('\n').append(String.format(Locale.ENGLISH, "Change: %+.2f%%", change));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color text = PremiumStockDashboard.TEXT_DIM();
            Color grid = PremiumStockDashboard.BORDER();
            if (primary() == null) {
                drawMessage(g2, text);
                return;
            }
            boolean legend = series.size() > 1;
            Rectangle plot = plotArea(getWidth(), getHeight(), legend);
            long[] span = timeSpan(series);
            double[] vs = valueSpan(series);
            Font small = new Font("Segoe UI", Font.PLAIN, 11);
            g2.setFont(small);
            FontMetrics fm = g2.getFontMetrics();

            // grid and value axis
            for (double v : niceTicks(vs[0], vs[1], 5)) {
                int y = (int) Math.round(yFor(v, vs[0], vs[1], plot));
                g2.setColor(withAlpha(grid, 90));
                g2.drawLine(plot.x, y, plot.x + plot.width, y);
                String label = axisPrefix + shortValue(v);
                g2.setColor(text);
                g2.drawString(label, plot.x - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
            }
            // time axis
            int ticks = Math.max(2, Math.min(6, plot.width / 110));
            for (int i = 0; i <= ticks; i++) {
                long t = span[0] + (span[1] - span[0]) * i / ticks;
                int x = (int) Math.round(xFor(t, span[0], span[1], plot));
                g2.setColor(withAlpha(grid, 50));
                g2.drawLine(x, plot.y, x, plot.y + plot.height);
                String label = formatAxisTime(t, span[1] - span[0]);
                g2.setColor(text);
                int lx = Math.max(plot.x, Math.min(plot.x + plot.width - fm.stringWidth(label), x - fm.stringWidth(label) / 2));
                g2.drawString(label, lx, plot.y + plot.height + 18);
            }

            // lines
            g2.setClip(plot.x - 1, plot.y - 2, plot.width + 2, plot.height + 4);
            boolean first = true;
            for (Series s : series) {
                if (s.size() < 2 && s.size() != 1) {
                    continue;
                }
                drawSeries(g2, s, span, vs, plot, first);
                first = false;
            }
            g2.setClip(null);

            if (legend) {
                int x = plot.x;
                for (Series s : series) {
                    g2.setColor(s.color());
                    g2.fillRoundRect(x, 10, 16, 4, 4, 4);
                    g2.setColor(PremiumStockDashboard.TEXT());
                    g2.drawString(s.name(), x + 22, 16);
                    x += 30 + fm.stringWidth(s.name()) + 14;
                }
            }
            if (!footnote.isEmpty()) {
                g2.setColor(withAlpha(text, 200));
                g2.drawString(footnote, getWidth() - 16 - fm.stringWidth(footnote), getHeight() - 4);
            }
            if (hoverTime != Long.MIN_VALUE) {
                drawHover(g2, span, vs, plot, fm);
            }
            if (isFocusOwner()) {
                g2.setColor(PremiumStockDashboard.ACCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawMessage(Graphics2D g2, Color text) {
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g2.setColor(text);
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = message.split("\n");
        int y = getHeight() / 2 - (lines.length - 1) * fm.getHeight() / 2;
        for (String line : lines) {
            g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
            y += fm.getHeight();
        }
    }

    private void drawSeries(Graphics2D g2, Series s, long[] span, double[] vs, Rectangle plot, boolean isFirst) {
        int n = s.size();
        Path2D line = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double x = xFor(s.times[i], span[0], span[1], plot);
            double y = yFor(s.values[i], vs[0], vs[1], plot);
            if (i == 0) {
                line.moveTo(x, y);
            } else if (s.step()) {
                line.lineTo(x, yFor(s.values[i - 1], vs[0], vs[1], plot));
                line.lineTo(x, y);
            } else {
                line.lineTo(x, y);
            }
        }
        if (s.step() && n > 0) {
            line.lineTo(xFor(span[1], span[0], span[1], plot), yFor(s.values[n - 1], vs[0], vs[1], plot));
        }
        if (s.fill() && n > 1) {
            Path2D area = new Path2D.Double(line);
            double endX = s.step() ? xFor(span[1], span[0], span[1], plot) : xFor(s.times[n - 1], span[0], span[1], plot);
            area.lineTo(endX, plot.y + plot.height);
            area.lineTo(xFor(s.times[0], span[0], span[1], plot), plot.y + plot.height);
            area.closePath();
            g2.setPaint(new GradientPaint(0, plot.y, withAlpha(s.color(), 70), 0, plot.y + plot.height, withAlpha(s.color(), 0)));
            g2.fill(area);
        }
        g2.setColor(s.color());
        g2.setStroke(new BasicStroke(isFirst ? 2.2f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        if (n == 1) {
            g2.fillOval((int) xFor(s.times[0], span[0], span[1], plot) - 3, (int) yFor(s.values[0], vs[0], vs[1], plot) - 3, 6, 6);
        }
    }

    private void drawHover(Graphics2D g2, long[] span, double[] vs, Rectangle plot, FontMetrics fm) {
        Series p = primary();
        int idx = nearestIndex(p.times, hoverTime);
        long t = p.times[idx];
        double x = xFor(t, span[0], span[1], plot);
        g2.setColor(withAlpha(PremiumStockDashboard.TEXT(), 120));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f));
        g2.drawLine((int) x, plot.y, (int) x, plot.y + plot.height);
        for (Series s : series) {
            double v = valueAt(s, t);
            if (Double.isNaN(v)) {
                continue;
            }
            double y = yFor(v, vs[0], vs[1], plot);
            g2.setColor(PremiumStockDashboard.CARD_BG());
            g2.fillOval((int) x - 5, (int) y - 5, 10, 10);
            g2.setColor(s.color());
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int) x - 4, (int) y - 4, 8, 8);
        }
        String[] lines = tooltipText(t).split("\n");
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, fm.stringWidth(l));
        }
        int h = lines.length * (fm.getHeight() + 1) + 10;
        int bx = (int) x + 14;
        if (bx + w + 16 > plot.x + plot.width) {
            bx = (int) x - w - 30;
        }
        int by = Math.max(plot.y, plot.y + 8);
        g2.setColor(new Color(15, 18, 38, 235));
        g2.fillRoundRect(bx, by, w + 18, h, 10, 10);
        g2.setColor(PremiumStockDashboard.ACCENT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(bx, by, w + 18, h, 10, 10);
        int ty = by + 5 + fm.getAscent();
        for (int i = 0; i < lines.length; i++) {
            g2.setColor(i == 0 ? new Color(190, 200, 230) : Color.WHITE);
            g2.drawString(lines[i], bx + 9, ty);
            ty += fm.getHeight() + 1;
        }
    }

    private String shortValue(double v) {
        double a = Math.abs(v);
        if (a >= 1_00_00_000) return String.format(Locale.ENGLISH, "%.1fCr", v / 1_00_00_000);
        if (a >= 1_00_000) return String.format(Locale.ENGLISH, "%.1fL", v / 1_00_000);
        if (a >= 10_000) return String.format(Locale.ENGLISH, "%,.0f", v);
        return String.format(Locale.ENGLISH, a < 100 ? "%,.2f" : "%,.0f", v);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}

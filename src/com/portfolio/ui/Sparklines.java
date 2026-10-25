package com.portfolio.ui;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * Hoverable price sparklines for table cells. A cell value is either a {@link Data} (real timestamped prices), or a String
 * explaining why there is none ("Loading...", "No history"). Painting never touches the network or the database.
 */
final class Sparklines {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /** Real observations: epoch milliseconds ascending, with the price at each. */
    record Data(long[] times, double[] values) {
        Data {
            if (times.length != values.length) {
                throw new IllegalArgumentException("times and values differ in length");
            }
            times = times.clone();
            values = values.clone();
        }

        boolean usable() {
            return values.length >= 2;
        }

        boolean rising() {
            return values[values.length - 1] >= values[0];
        }
    }

    private Sparklines() {
    }

    /** Inset rectangle inside a cell where the line is drawn. */
    static Rectangle area(int cellWidth, int cellHeight) {
        return new Rectangle(8, 6, Math.max(10, cellWidth - 16), Math.max(10, cellHeight - 12));
    }

    /** Index of the observation under cell-x {@code x}, proportionally to time. */
    static int indexAt(Data d, Rectangle area, int x) {
        long t0 = d.times[0], t1 = d.times[d.times.length - 1];
        double f = Math.max(0, Math.min(1, (x - area.x) / (double) area.width));
        long t = t0 + Math.round(f * (t1 - t0));
        return TimeSeriesChart.nearestIndex(d.times, t);
    }

    static String tooltip(Data d, int i, DoubleFunction<String> money, ZoneId zone) {
        String when = DAY.format(Instant.ofEpochMilli(d.times[i]).atZone(zone));
        StringBuilder sb = new StringBuilder("<html><b>").append(when).append("</b><br>").append(money.apply(d.values[i]));
        if (i > 0 && d.values[i - 1] != 0) {
            double change = (d.values[i] / d.values[i - 1] - 1) * 100;
            sb.append(String.format(Locale.ENGLISH, "<br>%+.2f%% vs previous close", change));
        }
        return sb.append("</html>").toString();
    }

    static void paint(Graphics2D g2, Rectangle a, Data d, int hover, Color up, Color down) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double v : d.values) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (hi == lo) {
            hi = lo + 1;
        }
        long t0 = d.times[0], t1 = d.times[d.times.length - 1];
        Color c = d.rising() ? up : down;
        Path2D line = new Path2D.Double();
        for (int i = 0; i < d.values.length; i++) {
            double x = a.x + (double) (d.times[i] - t0) / Math.max(1, t1 - t0) * a.width;
            double y = a.y + a.height - (d.values[i] - lo) / (hi - lo) * a.height;
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        Path2D area = new Path2D.Double(line);
        area.lineTo(a.x + a.width, a.y + a.height);
        area.lineTo(a.x, a.y + a.height);
        area.closePath();
        g2.setPaint(new GradientPaint(0, a.y, new Color(c.getRed(), c.getGreen(), c.getBlue(), 80), 0, a.y + a.height,
                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)));
        g2.fill(area);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        if (hover >= 0 && hover < d.values.length) {
            double x = a.x + (double) (d.times[hover] - t0) / Math.max(1, t1 - t0) * a.width;
            double y = a.y + a.height - (d.values[hover] - lo) / (hi - lo) * a.height;
            g2.setColor(new Color(255, 255, 255, 110));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine((int) x, a.y, (int) x, a.y + a.height);
            g2.setColor(PremiumStockDashboard.CARD_BG());
            g2.fillOval((int) x - 4, (int) y - 4, 8, 8);
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int) x - 4, (int) y - 4, 8, 8);
        }
    }

    /** Renders {@link Data} cells; strings are shown as dim text. */
    static final class CellRenderer implements TableCellRenderer {
        private final JLabel text = new JLabel("", SwingConstants.CENTER);
        private final Canvas canvas = new Canvas();
        private final Color up;
        private final Color down;

        CellRenderer(Color up, Color down) {
            this.up = up;
            this.down = down;
            text.setOpaque(true);
            text.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        }

        private final class Canvas extends javax.swing.JPanel {
            Data data;
            int hover = -1;

            Canvas() {
                setOpaque(true);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (data != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        Sparklines.paint(g2, area(getWidth(), getHeight()), data, hover, up, down);
                    } finally {
                        g2.dispose();
                    }
                }
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Color bg = isSelected ? PremiumStockDashboard.CARD_HOVER() : PremiumStockDashboard.CARD_BG();
            if (value instanceof Data d && d.usable()) {
                canvas.data = d;
                canvas.setBackground(bg);
                Object hr = table.getClientProperty("spark.row");
                Object hi = table.getClientProperty("spark.index");
                canvas.hover = hr instanceof Integer r && r == row && hi instanceof Integer i ? i : -1;
                return canvas;
            }
            text.setBackground(bg);
            text.setForeground(PremiumStockDashboard.TEXT_DIM());
            text.setText(value instanceof String s ? s : "No history");
            return text;
        }
    }

    /** Wires hover to a table column: crosshair on the line and a tooltip with the timestamp and exact price. */
    static void install(JTable table, int column, DoubleFunction<String> money) {
        table.getColumnModel().getColumn(column).setCellRenderer(new CellRenderer(PremiumStockDashboard.GREEN, PremiumStockDashboard.RED));
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                Object index = null;
                String tip = null;
                if (row >= 0 && col >= 0 && table.convertColumnIndexToModel(col) == column
                        && table.getValueAt(row, col) instanceof Data d && d.usable()) {
                    Rectangle cell = table.getCellRect(row, col, false);
                    int i = indexAt(d, area(cell.width, cell.height), e.getX() - cell.x);
                    index = i;
                    tip = tooltip(d, i, money, ZoneId.systemDefault());
                }
                Object oldRow = table.getClientProperty("spark.row");
                Object oldIndex = table.getClientProperty("spark.index");
                table.putClientProperty("spark.row", index == null ? null : row);
                table.putClientProperty("spark.index", index);
                table.setToolTipText(tip);
                if (!java.util.Objects.equals(oldIndex, index) || !java.util.Objects.equals(oldRow, index == null ? null : row)) {
                    table.repaint(table.getCellRect(row >= 0 ? row : 0, column, false));
                    if (oldRow instanceof Integer r) {
                        table.repaint(table.getCellRect(r, column, false));
                    }
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Object oldRow = table.getClientProperty("spark.row");
                table.putClientProperty("spark.row", null);
                table.putClientProperty("spark.index", null);
                table.setToolTipText(null);
                if (oldRow instanceof Integer r) {
                    table.repaint(table.getCellRect(r, column, false));
                }
            }
        };
        table.addMouseMotionListener(hover);
        table.addMouseListener(hover);
    }
}

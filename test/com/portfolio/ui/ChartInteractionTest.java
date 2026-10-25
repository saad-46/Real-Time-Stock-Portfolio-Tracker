package com.portfolio.ui;

import com.portfolio.service.PriceHistory.Point;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Chart geometry, ranges, hover, selection, keyboard and states. Deterministic: no network, no real data. */
class ChartInteractionTest {
    private static List<Point> daily(int days) {
        List<Point> pts = new ArrayList<>();
        LocalDate end = LocalDate.of(2026, 3, 1);
        for (int i = days - 1; i >= 0; i--) {
            pts.add(new Point(end.minusDays(i), 100 + (days - i)));
        }
        return pts;
    }

    private static PortfolioLineChart.Series series(int n) {
        String[] labels = new String[n];
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            labels[i] = "d" + i;
            values[i] = 10 + i;
        }
        return new PortfolioLineChart.Series("T", "pts", "Daily close · Fixture", false, labels, values);
    }

    private static PortfolioLineChart chart(int n, int w, int h) throws Exception {
        PortfolioLineChart c = new PortfolioLineChart();
        SwingUtilities.invokeAndWait(() -> {
            c.setSize(w, h);
            c.setSeries(series(n));
        });
        return c;
    }

    private static void mouse(PortfolioLineChart c, int id, int x, int y) throws Exception {
        SwingUtilities.invokeAndWait(() -> c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), 0, x, y, 1, false)));
    }

    // ---------------------------------------------------------------- ranges

    @Test
    void rangesSelectFromTheNewestPointAndNeverInventData() {
        List<Point> h = daily(400);
        assertEquals(8, ChartRange.W1.select(h).size(), "7 days back from the newest day, inclusive");
        assertEquals(31, ChartRange.M1.select(h).size());
        assertEquals(400, ChartRange.ALL.select(h).size());
        assertEquals(h.get(399), ChartRange.M3.select(h).get(ChartRange.M3.select(h).size() - 1), "always ends at the newest point");
        assertTrue(ChartRange.M1.select(List.of()).isEmpty());
    }

    @Test
    void sixMonthsIsAnAvailableRange() {
        assertEquals(181, ChartRange.M6.select(daily(400)).size());
        assertTrue(ChartRange.M6.supportedBy(daily(400)));
        assertFalse(ChartRange.M6.supportedBy(daily(100)));
    }

    @Test
    void aRangeIsSupportedOnlyWhenTheHistoryReallyCoversIt() {
        assertFalse(ChartRange.D1.supportedBy(daily(400)), "providers return daily data only");
        assertTrue(ChartRange.Y1.supportedBy(daily(400)));
        assertFalse(ChartRange.Y1.supportedBy(daily(120)), "120 days cannot answer a 1Y request");
        assertTrue(ChartRange.M3.supportedBy(daily(120)));
        assertTrue(ChartRange.ALL.supportedBy(daily(5)));
        assertFalse(ChartRange.ALL.supportedBy(daily(1)));
        assertFalse(ChartRange.W1.supportedBy(List.of()));
    }

    // ---------------------------------------------------------------- geometry

    @Test
    void pointerToIndexMappingIsExactAtTheEdgesAndStaysCorrectAfterResizing() {
        for (int[] size : new int[][] { { 400, 240 }, { 900, 300 }, { 1500, 500 } }) {
            Rectangle plot = PortfolioLineChart.plotArea(size[0], size[1]);
            int n = 11;
            assertEquals(0, PortfolioLineChart.indexAt(plot.x, n, plot));
            assertEquals(n - 1, PortfolioLineChart.indexAt(plot.x + plot.width, n, plot));
            for (int i = 0; i < n; i++) {
                int x = (int) Math.round(PortfolioLineChart.xFor(i, n, plot));
                assertEquals(i, PortfolioLineChart.indexAt(x, n, plot), "point " + i + " at width " + size[0]);
            }
            assertEquals(-1, PortfolioLineChart.indexAt(plot.x - 40, n, plot), "left of the plot");
            assertEquals(-1, PortfolioLineChart.indexAt(plot.x + plot.width + 40, n, plot), "right of the plot");
        }
        assertEquals(-1, PortfolioLineChart.indexAt(100, 0, PortfolioLineChart.plotArea(400, 240)), "no data");
        assertEquals(0, PortfolioLineChart.indexAt(100, 1, PortfolioLineChart.plotArea(400, 240)), "a single point");
    }

    // ---------------------------------------------------------------- hover, click, keyboard

    @Test
    void hoverSelectsTheNearestPointAndTheTooltipMatchesTheDrawnSeries() throws Exception {
        PortfolioLineChart c = chart(21, 800, 300);
        Rectangle plot = PortfolioLineChart.plotArea(800, 300);
        int x = (int) PortfolioLineChart.xFor(7, 21, plot);
        mouse(c, MouseEvent.MOUSE_MOVED, x + 2, 100);
        assertEquals(7, c.hoverIndex());
        assertEquals("d7  17.00 pts\n+70.00% since d0\nDaily close · Fixture", c.tooltipText(c.activeIndex()));
        mouse(c, MouseEvent.MOUSE_EXITED, 0, 0);
        assertEquals(-1, c.hoverIndex());
        assertEquals(-1, c.activeIndex());
        assertNull(c.tooltipText(99));
        assertNull(c.tooltipText(-1));
    }

    @Test
    void clickPinsAPointAndAClickOnItAgainReleasesIt() throws Exception {
        PortfolioLineChart c = chart(21, 800, 300);
        Rectangle plot = PortfolioLineChart.plotArea(800, 300);
        int x = (int) PortfolioLineChart.xFor(15, 21, plot);
        mouse(c, MouseEvent.MOUSE_CLICKED, x, 100);
        assertEquals(15, c.selectedIndex());
        mouse(c, MouseEvent.MOUSE_MOVED, (int) PortfolioLineChart.xFor(3, 21, plot), 100);
        assertEquals(15, c.activeIndex(), "a pinned point stays while the pointer moves");
        mouse(c, MouseEvent.MOUSE_CLICKED, x, 100);
        assertEquals(-1, c.selectedIndex());
    }

    @Test
    void keyboardMovesThroughPointsAndEscapeClears() throws Exception {
        PortfolioLineChart c = chart(5, 600, 260);
        key(c, KeyEvent.VK_LEFT);
        assertEquals(3, c.selectedIndex(), "starts from the newest point");
        key(c, KeyEvent.VK_HOME);
        assertEquals(0, c.selectedIndex());
        key(c, KeyEvent.VK_RIGHT);
        key(c, KeyEvent.VK_RIGHT);
        assertEquals(2, c.selectedIndex());
        key(c, KeyEvent.VK_END);
        assertEquals(4, c.selectedIndex());
        key(c, KeyEvent.VK_RIGHT);
        assertEquals(4, c.selectedIndex(), "never runs past the last point");
        key(c, KeyEvent.VK_ESCAPE);
        assertEquals(-1, c.selectedIndex());
        assertTrue(c.isFocusable());
    }

    private static void key(PortfolioLineChart c, int code) throws Exception {
        // deliver straight to the chart's listeners: the focus manager would drop a key event for a window that is not showing
        KeyEvent ev = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        SwingUtilities.invokeAndWait(() -> {
            for (java.awt.event.KeyListener l : c.getKeyListeners()) {
                l.keyPressed(ev);
            }
        });
    }

    // ---------------------------------------------------------------- data honesty and states

    @Test
    void theIllustrativeSampleIsLabelledInTheSeriesAndTheTooltip() throws Exception {
        PortfolioLineChart c = new PortfolioLineChart();
        SwingUtilities.invokeAndWait(() -> { c.setSize(600, 260); c.setSeries(PortfolioLineChart.illustrativeSample()); });
        assertTrue(c.isIllustrative());
        assertEquals(30, c.pointCount());
        assertTrue(c.tooltipText(29).contains("Illustrative sample, not real data"));
        assertTrue(c.tooltipText(29).startsWith("Today  "));
        assertArrayEquals(PortfolioLineChart.illustrativeSeries(), PortfolioLineChart.illustrativeSeries(), 0.0, "deterministic");
    }

    @Test
    void emptyLoadingFlatAndNormalSeriesAllPaintCleanlyAtSeveralSizes() throws Exception {
        PortfolioLineChart c = new PortfolioLineChart();
        SparklineComponent spark = new SparklineComponent();
        for (int[] size : new int[][] { { 300, 180 }, { 520, 260 }, { 1200, 500 } }) {
            for (int n : new int[] { 0, 1, 2, 30 }) {
                SwingUtilities.invokeAndWait(() -> {
                    c.setSize(size[0], size[1]);
                    c.setEmptyMessage("Loading…");
                    c.setSeries(n == 0 ? null : series(n));
                    spark.setSize(120, 36);
                    spark.setValues(n < 2 ? new double[0] : new double[] { 3, 3, 3 }, null);
                    BufferedImage b = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                    c.paint(b.getGraphics());
                    spark.paint(b.getGraphics());
                });
            }
        }
        assertEquals(0, c.pointCount() == 30 ? 0 : 1);
        SwingUtilities.invokeAndWait(() -> c.setSeries(null));
        assertEquals(0, c.pointCount());
        assertNull(c.tooltipText(0), "no series, no tooltip");
    }

    @Test
    void aSeriesWithMismatchedLabelsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PortfolioLineChart.Series("t", "u", "s", false, new String[1], new double[2]));
    }
}

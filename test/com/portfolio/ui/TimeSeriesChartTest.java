package com.portfolio.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesChartTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static long day(int d) {
        return LocalDate.of(2026, 1, 1).plusDays(d).atStartOfDay(UTC).toInstant().toEpochMilli();
    }

    private static TimeSeriesChart.Series series(String name, int days, double start, double step) {
        long[] t = new long[days];
        double[] v = new double[days];
        for (int i = 0; i < days; i++) {
            t[i] = day(i);
            v[i] = start + i * step;
        }
        return new TimeSeriesChart.Series(name, Color.GREEN, t, v, false, true);
    }

    @Test
    void seriesRejectsUnorderedOrMismatchedData() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSeriesChart.Series("x", Color.RED, new long[] { 2, 1 }, new double[] { 1, 2 }, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSeriesChart.Series("x", Color.RED, new long[] { 1, 2 }, new double[] { 1 }, false, false));
    }

    @Test
    void sinceKeepsTheLastEarlierPointSoTheLineStartsAtTheEdge() {
        TimeSeriesChart.Series s = series("p", 10, 100, 1);
        TimeSeriesChart.Series cut = s.since(day(5) + 1);
        assertEquals(day(5), cut.times()[0], "the point just before the cut is kept");
        assertEquals(5, cut.size());
        assertSame(s, s.since(Long.MIN_VALUE), "nothing is cut when the range covers everything");
    }

    @Test
    void timeAndValueMappingRoundTripsAndStaysInsideThePlot() {
        Rectangle plot = TimeSeriesChart.plotArea(800, 300, true);
        long t0 = day(0), t1 = day(30);
        assertEquals(plot.x, TimeSeriesChart.xFor(t0, t0, t1, plot), 1e-9);
        assertEquals(plot.x + plot.width, TimeSeriesChart.xFor(t1, t0, t1, plot), 1e-9);
        long mid = TimeSeriesChart.timeAt(plot.x + plot.width / 2, t0, t1, plot);
        assertEquals((t0 + t1) / 2.0, mid, 86_400_000L / 2.0 + plot.width, "the middle of the plot is the middle of the time span");
        assertEquals(t0, TimeSeriesChart.timeAt(-500, t0, t1, plot), "left of the plot clamps to the start");
        assertEquals(t1, TimeSeriesChart.timeAt(5000, t0, t1, plot), "right of the plot clamps to the end");
        assertEquals(plot.y + plot.height, TimeSeriesChart.yFor(10, 10, 20, plot), 1e-9);
        assertEquals(plot.y, TimeSeriesChart.yFor(20, 10, 20, plot), 1e-9);
    }

    @Test
    void nearestIndexAndValueAtFollowTheTimestamps() {
        long[] t = { day(0), day(1), day(5), day(9) };
        assertEquals(0, TimeSeriesChart.nearestIndex(t, day(0) - 1000));
        assertEquals(1, TimeSeriesChart.nearestIndex(t, day(2)));
        assertEquals(2, TimeSeriesChart.nearestIndex(t, day(4)));
        assertEquals(3, TimeSeriesChart.nearestIndex(t, day(100)));
        assertEquals(-1, TimeSeriesChart.nearestIndex(new long[0], 5));

        TimeSeriesChart.Series s = new TimeSeriesChart.Series("bal", Color.BLUE, t, new double[] { 10, 20, 30, 40 }, true, false);
        assertEquals(20, TimeSeriesChart.valueAt(s, day(3)), "a balance holds its value until the next point");
        assertEquals(40, TimeSeriesChart.valueAt(s, day(9)));
        assertTrue(Double.isNaN(TimeSeriesChart.valueAt(s, day(0) - 1)), "no value before the first point");
    }

    @Test
    void niceTicksCoverTheRangeWithRoundNumbers() {
        double[] ticks = TimeSeriesChart.niceTicks(2678.7, 2958.8, 5);
        assertTrue(ticks.length >= 3 && ticks.length <= 8);
        assertTrue(ticks[0] >= 2678.7 && ticks[ticks.length - 1] <= 2958.8 + 1e-6);
        for (double t : ticks) {
            assertEquals(0, t % 10, 1e-6, "ticks are round numbers: " + t);
        }
    }

    @Test
    void tooltipShowsTheExactTimestampValueAndChange() {
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setZone(UTC);
        chart.setValueFormat(v -> String.format("INR %.2f", v));
        chart.setSeries(List.of(series("RELIANCE", 5, 100, 10)), true);
        assertTrue(chart.hasChart());
        String tip = chart.tooltipText(day(3));
        assertTrue(tip.startsWith("04 Jan 2026"), tip);
        assertTrue(tip.contains("RELIANCE: INR 130.00"), tip);
        assertTrue(tip.contains("Change: +8.33%"), tip); // 130 vs 120
    }

    @Test
    void twoSeriesAreBothShownAtTheHoveredTime() {
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setZone(UTC);
        chart.setSeries(List.of(series("Market value", 5, 100, 10), series("Invested capital", 5, 90, 0)), true);
        String tip = chart.tooltipText(day(2));
        assertTrue(tip.contains("Market value: 120.00") && tip.contains("Invested capital: 90.00"), tip);
    }

    @Test
    void fewerThanTwoPointsMeansNoChartNotAFakeOne() {
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setSeries(List.of(series("one", 1, 100, 0)), true);
        assertFalse(chart.hasChart());
        chart.setSeries(List.of(), true);
        assertFalse(chart.hasChart());
        chart.setMessage("No history");
        assertFalse(chart.hasChart());
    }

    @Test
    void axisTimeLabelsFitTheSpan() {
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setZone(UTC);
        long t = Instant.parse("2026-03-05T14:30:00Z").toEpochMilli();
        assertEquals("05 Mar", chart.formatAxisTime(t, 30L * 86_400_000L));
        assertEquals("Mar 2026", chart.formatAxisTime(t, 800L * 86_400_000L));
        chart.setSeries(List.of(series("x", 3, 1, 1)), false);
        assertEquals("14:30", chart.formatAxisTime(t, 6L * 3_600_000L), "intraday data shows the time of day");
    }
}

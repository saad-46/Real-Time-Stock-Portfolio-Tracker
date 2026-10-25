package com.portfolio.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryChartPanelSparklineTest {
    private static TimeSeriesChart.Series daily(int days) {
        long[] t = new long[days];
        double[] v = new double[days];
        for (int i = 0; i < days; i++) {
            t[i] = LocalDate.of(2026, 1, 1).plusDays(i).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            v[i] = 100 + i;
        }
        return new TimeSeriesChart.Series("p", Color.GREEN, t, v, false, true);
    }

    @Test
    void aRangeIsOfferedOnlyWhenTheDataCoversIt() {
        HistoryChartPanel panel = new HistoryChartPanel();
        panel.showSeries(List.of(daily(40)), true, "test");
        assertFalse(panel.covers(HistoryChartPanel.Range.D1), "daily closes cannot show 1D");
        assertTrue(panel.covers(HistoryChartPanel.Range.W1));
        assertTrue(panel.covers(HistoryChartPanel.Range.M1));
        assertFalse(panel.covers(HistoryChartPanel.Range.M6), "40 days of data is not 6 months");
        assertFalse(panel.covers(HistoryChartPanel.Range.Y1));
        assertTrue(panel.covers(HistoryChartPanel.Range.ALL));
    }

    @Test
    void selectingARangeShowsOnlyThatPartAndUnavailableRangesAreIgnored() {
        HistoryChartPanel panel = new HistoryChartPanel();
        panel.showSeries(List.of(daily(40)), true, "test");
        panel.select(HistoryChartPanel.Range.W1);
        assertEquals(HistoryChartPanel.Range.W1, panel.selectedRange());
        assertTrue(panel.chart().hasChart());
        panel.select(HistoryChartPanel.Range.Y1);
        assertEquals(HistoryChartPanel.Range.W1, panel.selectedRange(), "a range the data does not cover is refused");
    }

    @Test
    void emptyLoadingAndErrorStatesShowNoChart() {
        HistoryChartPanel panel = new HistoryChartPanel();
        panel.showLoading("history");
        assertFalse(panel.chart().hasChart());
        assertFalse(panel.covers(HistoryChartPanel.Range.ALL));
        panel.showMessage("Price history unavailable: quota used");
        assertFalse(panel.chart().hasChart());
    }

    @Test
    void sparklineHoverMapsXToTheNearestObservationInTime() {
        long d0 = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        long day = 86_400_000L;
        // uneven spacing: the 3rd point is far to the right
        Sparklines.Data data = new Sparklines.Data(new long[] { d0, d0 + day, d0 + 9 * day }, new double[] { 10, 12, 11 });
        assertTrue(data.usable());
        Rectangle area = Sparklines.area(116, 45);
        assertEquals(0, Sparklines.indexAt(data, area, area.x));
        assertEquals(2, Sparklines.indexAt(data, area, area.x + area.width));
        assertEquals(1, Sparklines.indexAt(data, area, area.x + area.width / 9), "x position follows time, not point count");
        assertEquals(0, Sparklines.indexAt(data, area, -50), "outside the cell clamps");
    }

    @Test
    void sparklineTooltipHasTheDateExactPriceAndChange() {
        long d0 = LocalDate.of(2026, 3, 5).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        Sparklines.Data data = new Sparklines.Data(new long[] { d0, d0 + 86_400_000L }, new double[] { 200, 210 });
        String tip = Sparklines.tooltip(data, 1, v -> String.format("Rs %.2f", v), ZoneId.of("UTC"));
        assertTrue(tip.contains("06 Mar 2026") && tip.contains("Rs 210.00") && tip.contains("+5.00%"), tip);
        assertFalse(new Sparklines.Data(new long[] { d0 }, new double[] { 1 }).usable(), "one point is not a trend");
        assertTrue(data.rising());
    }
}

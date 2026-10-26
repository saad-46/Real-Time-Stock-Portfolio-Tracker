package com.portfolio.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A {@link TimeSeriesChart} with a row of range buttons (1D, 1W, 1M, 6M, 1Y, ALL). A range is offered only when the loaded data
 * really covers it, so a button never shows less (or invented) history than it promises.
 */
public class HistoryChartPanel extends JPanel {
    public enum Range {
        D1("1D", 1), W1("1W", 7), M1("1M", 30), M6("6M", 182), Y1("1Y", 365), ALL("ALL", -1);

        final String label;
        final int days;

        Range(String label, int days) {
            this.label = label;
            this.days = days;
        }
    }

    private final TimeSeriesChart chart = new TimeSeriesChart();
    private final JPanel rangeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final StatusChip chip = new StatusChip();
    private final List<JButton> buttons = new ArrayList<>();
    private List<TimeSeriesChart.Series> full = List.of();
    private boolean daily = true;
    private Range selected = Range.ALL;
    private Instant now = Instant.now();

    public HistoryChartPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        rangeBar.setOpaque(false);
        for (Range r : Range.values()) {
            JButton b = new JButton(r.label);
            b.setFocusPainted(true);
            b.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            b.setContentAreaFilled(false);
            b.setFont(new Font("Segoe UI", Font.BOLD, 12));
            b.getAccessibleContext().setAccessibleName("Show " + r.label);
            b.addActionListener(e -> select(r));
            buttons.add(b);
            rangeBar.add(b);
        }
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel chipSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        chipSlot.setOpaque(false);
        chipSlot.add(chip);
        header.add(rangeBar, BorderLayout.WEST);
        header.add(chipSlot, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        styleButtons();
    }

    public TimeSeriesChart chart() {
        return chart;
    }

    public Range selectedRange() {
        return selected;
    }

    public void setAxisPrefix(String prefix) {
        chart.setAxisPrefix(prefix);
    }

    public void setValueFormat(DoubleFunction<String> format) {
        chart.setValueFormat(format);
    }

    /** For tests: the instant treated as "now" when cutting a range. */
    void setNow(Instant instant) {
        this.now = instant;
    }

    StatusChip chip() {
        return chip;
    }

    /** Shows the data with its provenance as a compact chip (live / cached / demo / your records) instead of a footnote. */
    public void showSeries(List<TimeSeriesChart.Series> lines, boolean dailyData, DataStatus status, String detail, String tooltip) {
        showSeries(lines, dailyData, "");
        chip.set(status, detail, tooltip);
    }

    /** Nothing to draw, and why; the chip says so too. */
    public void showUnavailable(String reason) {
        showMessage(reason);
        chip.set(DataStatus.UNAVAILABLE, null, reason);
    }

    public void showLoading(String what) {
        chip.clear();
        full = List.of();
        chart.setMessage("Loading " + what + "...");
        updateEnabled();
    }

    public void showMessage(String text) {
        chip.clear();
        full = List.of();
        chart.setMessage(text);
        updateEnabled();
    }

    /** Shows the data, keeping the chosen range when the data still covers it, else falling back to ALL. */
    public void showSeries(List<TimeSeriesChart.Series> lines, boolean dailyData, String footnote) {
        this.full = List.copyOf(lines);
        this.daily = dailyData;
        chart.setFootnote(footnote);
        updateEnabled();
        if (!covers(selected)) {
            selected = Range.ALL;
        }
        apply();
    }

    /** Whether the loaded data spans at least the range. */
    boolean covers(Range r) {
        if (r == Range.ALL) {
            return !full.isEmpty();
        }
        long[] span = TimeSeriesChart.timeSpan(full);
        return full.stream().anyMatch(s -> s.size() >= 2) && (span[1] - span[0]) >= r.days * 86_400_000L * 0.9
                && !(daily && r == Range.D1);
    }

    void select(Range r) {
        if (!covers(r)) {
            return;
        }
        selected = r;
        apply();
    }

    private void apply() {
        if (full.isEmpty()) {
            return;
        }
        List<TimeSeriesChart.Series> cut = new ArrayList<>();
        long[] span = TimeSeriesChart.timeSpan(full);
        long from = selected == Range.ALL ? Long.MIN_VALUE : span[1] - selected.days * 86_400_000L;
        for (TimeSeriesChart.Series s : full) {
            cut.add(selected == Range.ALL ? s : s.since(from));
        }
        chart.setSeries(cut, daily);
        styleButtons();
    }

    private void updateEnabled() {
        for (int i = 0; i < buttons.size(); i++) {
            Range r = Range.values()[i];
            boolean ok = covers(r);
            buttons.get(i).setEnabled(ok);
            buttons.get(i).setToolTipText(ok ? null : r == Range.D1 && daily ? "1D needs intraday prices, which this data does not have"
                    : "Not enough history for " + r.label);
        }
        styleButtons();
    }

    private void styleButtons() {
        for (int i = 0; i < buttons.size(); i++) {
            JButton b = buttons.get(i);
            boolean on = Range.values()[i] == selected && b.isEnabled();
            b.setForeground(!b.isEnabled() ? withAlpha(PremiumStockDashboard.TEXT_DIM(), 110)
                    : on ? PremiumStockDashboard.ACCENT : PremiumStockDashboard.TEXT_DIM());
            b.setBorder(on ? BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, PremiumStockDashboard.ACCENT), BorderFactory.createEmptyBorder(4, 12, 2, 12))
                    : BorderFactory.createEmptyBorder(4, 12, 4, 12));
        }
        rangeBar.repaint();
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}

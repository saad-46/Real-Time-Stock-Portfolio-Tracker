package com.portfolio.ui;

import com.portfolio.service.PriceHistory.Point;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Time ranges of the featured chart. Ranges are applied to the data a provider actually returned; a range the data cannot
 * answer is reported unsupported instead of being filled with invented points. Provider history is daily, so 1D (intraday) is
 * never supported by it.
 */
public enum ChartRange {
    D1("1D", 1), W1("1W", 7), M1("1M", 30), M3("3M", 90), M6("6M", 180), Y1("1Y", 365), ALL("ALL", Integer.MAX_VALUE);

    private final String label;
    private final int days;

    ChartRange(String label, int days) {
        this.label = label;
        this.days = days;
    }

    public String label() {
        return label;
    }

    /** The points inside the range, ending at the newest point (never extended or interpolated). */
    public List<Point> select(List<Point> history) {
        if (history.isEmpty()) {
            return List.of();
        }
        if (this == ALL) {
            return history;
        }
        LocalDate newest = history.get(history.size() - 1).date();
        LocalDate from = newest.minusDays(days);
        List<Point> out = new ArrayList<>();
        for (Point p : history) {
            if (!p.date().isBefore(from)) {
                out.add(p);
            }
        }
        return out;
    }

    /** True when the range holds at least two real points, and (for ranges longer than the data) the data really covers it. */
    public boolean supportedBy(List<Point> history) {
        if (this == D1 || history.size() < 2) {
            return false;
        }
        if (this == ALL) {
            return true;
        }
        LocalDate oldest = history.get(0).date();
        LocalDate newest = history.get(history.size() - 1).date();
        // the history must reach back at least ~80% of the requested window, otherwise the button would mislead
        long covered = java.time.temporal.ChronoUnit.DAYS.between(oldest, newest);
        return covered >= days * 0.8 && select(history).size() >= 2;
    }
}

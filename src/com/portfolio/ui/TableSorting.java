package com.portfolio.ui;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Click-to-sort for the formatted tables, and the colour rule for signed figures. The cells hold display strings ("₹3,67,241.30",
 * "+1.12%", "25 Oct 2026, 20:26"), so sorting reads the number or date inside the text instead of comparing characters, and only
 * a figure that carries an explicit sign is coloured as a gain or a loss: a plain price or a date is never green or red.
 */
final class TableSorting {
    private static final Pattern NUMBER = Pattern.compile("([-+−]?)\\s*\\d[\\d,]*(\\.\\d+)?");
    private static final Pattern SIGNED_GAIN = Pattern.compile("^\\s*\\+\\s*[^\\d]{0,3}\\d.*");
    private static final Pattern SIGNED_LOSS = Pattern.compile("^\\s*[^\\d+\\-−]{0,3}[-−]\\s*[^\\d]{0,3}\\d.*");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private TableSorting() {
    }

    /** Green for an explicitly positive figure, red for an explicitly negative one, otherwise the neutral colour. */
    static Color semanticColor(String text, Color gain, Color loss, Color neutral) {
        if (text == null) {
            return neutral;
        }
        if (SIGNED_GAIN.matcher(text).matches()) {
            return gain;
        }
        if (SIGNED_LOSS.matcher(text).matches() && !looksLikeDate(text)) {
            return loss;
        }
        return neutral;
    }

    private static boolean looksLikeDate(String s) {
        return s.matches("^\\s*\\d{4}-\\d{2}-\\d{2}.*");
    }

    /** A sortable key for a cell: a date as minutes, or the first number in the text; NaN if there is neither. */
    static double key(Object cell) {
        if (cell == null) {
            return Double.NaN;
        }
        if (cell instanceof Number n) {
            return n.doubleValue();
        }
        String s = cell.toString().trim();
        try {
            return LocalDateTime.parse(s, DATE_TIME).toEpochSecond(ZoneOffset.UTC) / 60.0;
        } catch (DateTimeParseException ignored) {
            // not a date-time
        }
        try {
            return LocalDate.parse(s, DATE).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60.0;
        } catch (DateTimeParseException ignored) {
            // not a date
        }
        if (looksLikeDate(s)) {
            try {
                return LocalDate.parse(s.substring(0, 10)).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60.0;
            } catch (DateTimeParseException ignored) {
                return Double.NaN;
            }
        }
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) {
            return Double.NaN;
        }
        String digits = m.group().replace(",", "").replace(" ", "").replace('−', '-');
        try {
            return Double.parseDouble(digits.startsWith("+") ? digits.substring(1) : digits);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Orders numbers and dates by value, everything else alphabetically; empty cells first. */
    static int compare(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        double x = key(a);
        double y = key(b);
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            return Double.compare(x, y);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    /** Makes every column sortable by clicking its header. */
    static void install(JTable table) {
        TableModel model = table.getModel();
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        for (int i = 0; i < model.getColumnCount(); i++) {
            sorter.setComparator(i, TableSorting::compare);
        }
        table.setRowSorter(sorter);
    }
}

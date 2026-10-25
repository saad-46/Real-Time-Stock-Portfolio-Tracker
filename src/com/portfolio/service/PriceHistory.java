package com.portfolio.service;

import com.portfolio.service.PriceUnavailableException.Reason;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Daily closing prices parsed from a provider's raw history JSON (Twelve Data "values" or Alpha Vantage
 * "Time Series (Daily)"). Rows the provider reported with an unusable close are skipped; nothing is ever generated,
 * and a response with no usable rows is an error, not an empty "demo" series.
 */
public final class PriceHistory {
    private PriceHistory() {
    }

    public record Point(LocalDate date, double close) {
    }

    /** @return usable points, oldest first, never empty */
    public static List<Point> parse(String json) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Malformed history response", e);
        }
        List<Point> points = new ArrayList<>();
        JSONArray values = root.optJSONArray("values"); // Twelve Data
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject row = values.optJSONObject(i);
                if (row != null) {
                    add(points, row.optString("datetime", ""), row.optString("close", ""));
                }
            }
        }
        JSONObject series = root.optJSONObject("Time Series (Daily)"); // Alpha Vantage
        if (series != null) {
            for (Iterator<String> it = series.keys(); it.hasNext();) {
                String day = it.next();
                JSONObject row = series.optJSONObject(day);
                if (row != null) {
                    add(points, day, row.optString("4. close", ""));
                }
            }
        }
        if (points.isEmpty()) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "The provider returned no usable price history");
        }
        points.sort(Comparator.comparing(Point::date));
        return points;
    }

    /** The most recent {@code days} points (all of them if fewer exist). */
    public static List<Point> lastDays(List<Point> points, int days) {
        int from = Math.max(0, points.size() - Math.max(1, days));
        return new ArrayList<>(points.subList(from, points.size()));
    }

    private static void add(List<Point> out, String date, String close) {
        try {
            double c = Double.parseDouble(close);
            if (Double.isFinite(c) && c > 0) {
                out.add(new Point(LocalDate.parse(date.length() > 10 ? date.substring(0, 10) : date), c));
            }
        } catch (RuntimeException e) {
            // unusable row: skipped, never replaced by an invented value
        }
    }
}

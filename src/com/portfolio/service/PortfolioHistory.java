package com.portfolio.service;

import com.portfolio.model.Transaction;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the portfolio's history from facts: the transaction ledger (what was held and what it cost on each day) and real
 * daily closes. Nothing is estimated: a day's market value is the sum of quantity x price over the holdings of that day, where
 * the price is the symbol's last real close on or before the day, or else the price of the symbol's latest trade on or before
 * it. If some held symbol has neither, that day has no market value and is left out of the value line.
 */
public final class PortfolioHistory {
    /** One chartable day. {@code value} is NaN when it cannot be determined from real prices. */
    public record Day(LocalDate date, double invested, double value) {
    }

    public record Result(List<Day> days, List<String> symbolsWithoutHistory) {
        public Result {
            days = List.copyOf(days);
            symbolsWithoutHistory = List.copyOf(symbolsWithoutHistory);
        }
    }

    private PortfolioHistory() {
    }

    /**
     * Whether the history can be charted truthfully. Daily closes carry no exchange rate, so a history is only meaningful when
     * every holding is in the currency the chart is drawn in. Returns null when it is, otherwise the reason to show the user.
     */
    public static String currencyProblem(java.util.Collection<String> holdingCurrencies, String baseCurrency) {
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (String c : holdingCurrencies) {
            seen.add(c == null || c.isBlank() ? "unknown" : c.trim().toUpperCase(java.util.Locale.ROOT));
        }
        String base = baseCurrency == null ? "" : baseCurrency.trim().toUpperCase(java.util.Locale.ROOT);
        if (seen.isEmpty() || (seen.size() == 1 && seen.contains(base))) {
            return null;
        }
        if (seen.size() > 1) {
            return "Portfolio history is not shown: your holdings are in more than one currency (" + String.join(", ", seen)
                    + ") and no reliable historical exchange rates are available.";
        }
        return "Portfolio history is not shown: your holdings are in " + seen.iterator().next() + " but amounts are shown in "
                + base + ", and no reliable historical exchange rates are available.";
    }

    public static Result compute(List<Transaction> ledger, Map<String, List<PriceHistory.Point>> closes, LocalDate today) {
        List<Transaction> txs = new ArrayList<>(ledger);
        txs.removeIf(t -> t.getTimestamp() == null || (!"BUY".equals(t.getType()) && !"SELL".equals(t.getType())));
        txs.sort(Comparator.comparing(Transaction::getTimestamp));
        if (txs.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        LocalDate start = txs.get(0).getTimestamp().toLocalDate();
        Map<String, Integer> qty = new HashMap<>();
        Map<String, Double> lastTradePrice = new HashMap<>();
        double invested = 0;
        int next = 0;
        List<Day> days = new ArrayList<>();
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            while (next < txs.size() && !txs.get(next).getTimestamp().toLocalDate().isAfter(d)) {
                Transaction t = txs.get(next++);
                String s = t.getSymbol().toUpperCase(java.util.Locale.ROOT);
                if ("BUY".equals(t.getType())) {
                    qty.merge(s, t.getQuantity(), Integer::sum);
                    invested += t.getQuantity() * t.getPrice();
                } else {
                    qty.merge(s, -t.getQuantity(), Integer::sum);
                    invested -= t.getQuantity() * t.getPrice() - (t.getRealizedPl() == null ? 0 : t.getRealizedPl());
                }
                lastTradePrice.put(s, t.getPrice());
            }
            double value = 0;
            boolean known = true;
            for (Map.Entry<String, Integer> e : qty.entrySet()) {
                if (e.getValue() <= 0) {
                    continue;
                }
                Double p = priceOn(closes.get(e.getKey()), d);
                if (p == null) {
                    p = lastTradePrice.get(e.getKey());
                }
                if (p == null) {
                    known = false;
                    missing.add(e.getKey());
                    break;
                }
                value += e.getValue() * p;
            }
            days.add(new Day(d, Math.max(0, invested), known ? value : Double.NaN));
        }
        return new Result(days, new ArrayList<>(missing));
    }

    /** Last close on or before {@code day}; null if the series starts later or is absent. */
    static Double priceOn(List<PriceHistory.Point> series, LocalDate day) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        Double found = null;
        for (PriceHistory.Point p : series) {
            if (p.date().isAfter(day)) {
                break;
            }
            found = p.close();
        }
        return found;
    }

    public static long millis(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

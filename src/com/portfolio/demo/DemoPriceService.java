package com.portfolio.demo;

import com.portfolio.model.Stock;
import com.portfolio.service.StockPriceService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Fixed, clearly simulated quotes and price history for demo mode. Nothing here is market data: the figures are chosen so the
 * demo portfolio's arithmetic is easy to check, the history is a deterministic walk (same every run), and the application
 * labels everything shown from this service as demo data.
 */
public final class DemoPriceService implements StockPriceService {
    /** symbol -> {name, current price, day change percent} */
    private static final Map<String, Object[]> QUOTES = new LinkedHashMap<>();
    /** symbol -> {days ago the demo position was bought, the price paid}; the demo history passes through this price on that day */
    private static final Map<String, double[]> ANCHORS = new LinkedHashMap<>();

    static {
        QUOTES.put("RELIANCE", new Object[] { "Reliance Industries", 2914.50, 0.84 });
        QUOTES.put("HDFCBANK", new Object[] { "HDFC Bank", 1688.20, 1.12 });
        QUOTES.put("ICICIBANK", new Object[] { "ICICI Bank", 1142.75, 0.65 });
        QUOTES.put("TCS", new Object[] { "Tata Consultancy Services", 3792.40, -0.78 });
        QUOTES.put("INFY", new Object[] { "Infosys", 1512.30, -1.34 });
        QUOTES.put("SBIN", new Object[] { "State Bank of India", 842.60, 1.90 });
        QUOTES.put("ITC", new Object[] { "ITC", 468.15, 0.42 });
        QUOTES.put("LT", new Object[] { "Larsen & Toubro", 3655.80, -0.31 });
        QUOTES.put("BHARTIARTL", new Object[] { "Bharti Airtel", 1624.40, 0.97 });
        ANCHORS.put("RELIANCE", new double[] { 80, 2780.00 });
        ANCHORS.put("HDFCBANK", new double[] { 72, 1610.00 });
        ANCHORS.put("ICICIBANK", new double[] { 64, 1085.00 });
        ANCHORS.put("TCS", new double[] { 56, 3850.00 });
        ANCHORS.put("INFY", new double[] { 48, 1540.00 });
        ANCHORS.put("SBIN", new double[] { 40, 790.00 });
    }

    @Override
    public Stock getStock(String symbol) {
        String key = tickerOf(symbol);
        Object[] q = QUOTES.get(key);
        if (q == null) {
            return null;
        }
        Stock stock = new Stock(symbol.trim().toUpperCase(Locale.ROOT), (String) q[0], (Double) q[1]);
        stock.setChangePercent((Double) q[2]);
        stock.setCurrency("INR");
        return stock;
    }

    @Override
    public boolean isValidStock(String symbol) {
        return getStock(symbol) != null;
    }

    @Override
    public String getHistoricalData(String symbol) {
        return historyJson(symbol, LocalDate.now());
    }

    /** One simulated close per weekday for about 120 days up to {@code today}, ending at the demo quote. */
    static String historyJson(String symbol, LocalDate today) {
        List<double[]> pts = history(symbol, today); // {epochDay, close}
        StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = pts.size() - 1; i >= 0; i--) {
            sb.append("{\"datetime\":\"").append(LocalDate.ofEpochDay((long) pts.get(i)[0])).append("\",\"close\":\"")
                    .append(String.format(Locale.ROOT, "%.2f", pts.get(i)[1])).append("\"}");
            if (i > 0) {
                sb.append(',');
            }
        }
        return sb.append("]}").toString();
    }

    /** The bare ticker of a stored symbol: the demo knows companies, not exchanges. */
    private static String tickerOf(String symbol) {
        try {
            return com.portfolio.service.SymbolResolver.parse(symbol).ticker();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static List<double[]> history(String symbol, LocalDate today) {
        String key = tickerOf(symbol);
        Object[] q = QUOTES.get(key);
        if (q == null) {
            throw new com.portfolio.service.PriceUnavailableException(
                    com.portfolio.service.PriceUnavailableException.Reason.SYMBOL_NOT_FOUND, "No demo history for " + symbol);
        }
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = today.minusDays(120); !d.isAfter(today); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(d);
            }
        }
        int n = days.size();
        double[] w = new double[n];
        w[n - 1] = (Double) q[1];
        SplittableRandom rnd = new SplittableRandom(key.hashCode() * 31L + 7);
        for (int i = n - 2; i >= 0; i--) { // walk backwards from today's price
            w[i] = w[i + 1] / (1 + normal(rnd) * 0.011 + 0.0004);
        }
        double[] anchor = ANCHORS.get(key);
        if (anchor != null) { // pass through the price paid on the purchase day
            LocalDate buyDay = today.minusDays((long) anchor[0]);
            int ib = 0;
            while (ib < n - 1 && days.get(ib).isBefore(buyDay)) {
                ib++;
            }
            double offset = anchor[1] - w[ib];
            for (int i = 0; i <= ib; i++) {
                w[i] += offset;
            }
            for (int i = ib + 1; i < n - 1; i++) {
                w[i] += offset * (n - 1 - i) / (double) (n - 1 - ib);
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new double[] { days.get(i).toEpochDay(), Math.max(1, w[i]) });
        }
        return out;
    }

    /** Roughly normal (mean 0, deviation 1) from a seeded generator: the sum of twelve uniforms minus six. */
    private static double normal(SplittableRandom rnd) {
        double sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += rnd.nextDouble();
        }
        return sum - 6;
    }

    static int anchorDays(String symbol) {
        return (int) ANCHORS.get(symbol)[0];
    }

    static String nameOf(String symbol) {
        return (String) QUOTES.get(symbol)[0];
    }

    static double priceOf(String symbol) {
        return (Double) QUOTES.get(symbol)[1];
    }
}

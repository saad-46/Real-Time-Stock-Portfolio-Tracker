package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Finnhub quotes. Never fabricates a price: a quote is returned only if Finnhub reported a positive price,
 * otherwise this throws {@link PriceUnavailableException} (or returns null when Finnhub reports no data for the symbol).
 */
public class FinnhubService implements StockPriceService {
    private static final String BASE_URL = "https://finnhub.io/api/v1";
    private final HttpClient httpClient;
    private final String baseUrl;
    private final java.util.function.Supplier<String> keys;

    public FinnhubService() {
        this(BASE_URL, ApiKeyManager::getFinnhubKey);
    }

    /** Package-private for tests: a local fake server and a fixed key. */
    FinnhubService(String baseUrl, java.util.function.Supplier<String> keys) {
        this.baseUrl = baseUrl;
        this.keys = keys;
        this.httpClient = Net.client();
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 403 && response.body() != null && response.body().toLowerCase().contains("access to this resource")) {
                // Finnhub's documented answer for a valid key whose plan does not include the symbol or endpoint
                throw new PriceUnavailableException(Reason.UNSUPPORTED, "Finnhub: this symbol is not included in the current plan");
            }
            if (status == 401 || status == 403) {
                throw new PriceUnavailableException(Reason.INVALID_KEY, "Finnhub rejected the API key (HTTP " + status + ")");
            }
            if (status == 429) {
                throw new PriceUnavailableException(Reason.RATE_LIMIT, "Finnhub rate limit reached (HTTP 429)");
            }
            if (status != 200) {
                throw new PriceUnavailableException(Reason.HTTP_ERROR, "Finnhub returned HTTP " + status);
            }
            return response.body();
        } catch (IOException e) {
            throw new PriceUnavailableException(Reason.NETWORK, "Finnhub network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PriceUnavailableException(Reason.INTERRUPTED, "Interrupted while contacting Finnhub", e);
        }
    }

    private static String requireKey(String key) {
        if (!ApiKeyManager.isConfigured(key)) {
            throw new PriceUnavailableException(Reason.NO_KEY, "No Finnhub API key configured (FINNHUB_API_KEY)");
        }
        return key;
    }

    @Override
    public Stock getStock(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String key = requireKey(keys.get());
        String body = get(String.format("%s/quote?symbol=%s&token=%s", baseUrl,
                URLEncoder.encode(symbol.trim().toUpperCase(), StandardCharsets.UTF_8), URLEncoder.encode(key, StandardCharsets.UTF_8)));
        double price = parseJsonDouble(body, "\"c\":");
        if (!(price > 0) || Double.isInfinite(price)) {
            // Finnhub answers unknown symbols with all-zero fields: no data, not a price of zero.
            return null;
        }
        Stock stock = new Stock(symbol.trim().toUpperCase(), symbol.trim().toUpperCase(), price);
        stock.setChangePercent(parseJsonDouble(body, "\"dp\":"));
        stock.setHigh24h(parseJsonDouble(body, "\"h\":"));
        stock.setLow24h(parseJsonDouble(body, "\"l\":"));
        // Finnhub's /quote does not state a currency; its free tier serves US listings in USD. Suffixed symbols: unknown.
        stock.setCurrency(symbol.contains(".") ? null : "USD");
        return stock;
    }

    @Override
    public boolean isValidStock(String symbol) {
        return getStock(symbol) != null;
    }

    /** Refresh the quote, then (best effort) fill in name, sector and market cap from the company profile. */
    @Override
    public void updateStockPrice(Stock stock) {
        StockPriceService.super.updateStockPrice(stock);
        if (stock.getName() == null || stock.getName().equals(stock.getSymbol()) || stock.getMarketCap() == null) {
            try {
                String key = requireKey(keys.get());
                String body = get(String.format("%s/stock/profile2?symbol=%s&token=%s", baseUrl,
                        URLEncoder.encode(stock.getSymbol().toUpperCase(), StandardCharsets.UTF_8), URLEncoder.encode(key, StandardCharsets.UTF_8)));
                String name = parseJsonString(body, "\"name\":");
                double marketCapInMillions = parseJsonDouble(body, "\"marketCapitalization\":");
                String capCurrency = parseJsonString(body, "\"currency\":"); // the profile states it; the figure is in millions of it
                String capLabel = capCurrency == null || !capCurrency.matches("[A-Za-z]{3}") ? "" : capCurrency.toUpperCase() + " ";
                String sector = parseJsonString(body, "\"finnhubIndustry\":");

                if (name != null && !name.isEmpty())
                    stock.setName(name);
                if (sector != null && !sector.isEmpty())
                    stock.setSector(sector);

                if (marketCapInMillions > 0) {
                    if (marketCapInMillions >= 1000000) {
                        stock.setMarketCap(String.format("%s%.2fT", capLabel, marketCapInMillions / 1000000));
                    } else if (marketCapInMillions >= 1000) {
                        stock.setMarketCap(String.format("%s%.2fB", capLabel, marketCapInMillions / 1000));
                    } else {
                        stock.setMarketCap(String.format("%s%.2fM", capLabel, marketCapInMillions));
                    }
                }
            } catch (PriceUnavailableException e) {
                // Descriptive extras only; the live price above is already set.
                System.err.println("Finnhub profile unavailable for " + stock.getSymbol() + ": " + e.getMessage());
            }
        }
    }

    private String parseJsonString(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return null;
            int start = index + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"')
                end++;
            return json.substring(start, end).trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String searchStocks(String query) {
        String key = requireKey(keys.get());
        return get(String.format("%s/search?q=%s&token=%s", baseUrl, URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(key, StandardCharsets.UTF_8)));
    }

    /** Numeric field, or 0.0 when absent/null/unparseable (callers treat 0 as "not reported", never as a price). */
    private double parseJsonDouble(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;
            int start = index + key.length();
            while (start < json.length() && json.charAt(start) == ' ')
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            String valueStr = json.substring(start, end).trim();
            if (valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    /** Finnhub's free tier has no candle history; say so instead of returning an empty placeholder as if it were data. */
    @Override
    public String getHistoricalData(String symbol) {
        throw new PriceUnavailableException(Reason.UNSUPPORTED, "Finnhub historical data is not available on the free tier");
    }
}

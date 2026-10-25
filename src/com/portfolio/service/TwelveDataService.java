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
 * Twelve Data quotes and daily history. Never fabricates a price: a quote is returned only if the provider
 * reported a positive price, otherwise this throws {@link PriceUnavailableException}.
 */
public class TwelveDataService implements StockPriceService {
    private static final String BASE_URL = "https://api.twelvedata.com";
    private final HttpClient httpClient;
    private final String baseUrl;
    private final java.util.function.Supplier<String> keys;

    public TwelveDataService() {
        this(BASE_URL, ApiKeyManager::getTwelveDataKey);
    }

    /** Package-private for tests: a local fake server and a fixed key. */
    TwelveDataService(String baseUrl, java.util.function.Supplier<String> keys) {
        this.baseUrl = baseUrl;
        this.keys = keys;
        this.httpClient = Net.client();
    }

    private String get(String url, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new PriceUnavailableException(Reason.INVALID_KEY, "Twelve Data rejected the API key (HTTP " + status + ")");
            }
            if (status == 429) {
                throw new PriceUnavailableException(Reason.RATE_LIMIT, "Twelve Data rate limit reached (HTTP 429)");
            }
            if (status == 404) {
                // Twelve Data's documented answer for a symbol outside the plan, or one it does not know
                String text = response.body() == null ? "" : response.body().toLowerCase();
                if (text.contains("starting with") || text.contains("plan")) {
                    throw new PriceUnavailableException(Reason.UNSUPPORTED, "Twelve Data: this symbol is not included in the current plan");
                }
                throw new PriceUnavailableException(Reason.SYMBOL_NOT_FOUND, "Twelve Data does not know this symbol");
            }
            if (status != 200) {
                throw new PriceUnavailableException(Reason.HTTP_ERROR, "Twelve Data returned HTTP " + status);
            }
            String body = response.body();
            if (body.contains("\"code\"") && body.contains("\"status\":\"error\"")) {
                // Twelve Data reports quota (429), key (401) and unknown symbol (400/404) with HTTP 200 and an error body.
                if (body.contains("\"code\":429")) {
                    throw new PriceUnavailableException(Reason.RATE_LIMIT, "Twelve Data rate limit reached");
                }
                if (body.contains("\"code\":401")) {
                    throw new PriceUnavailableException(Reason.INVALID_KEY, "Twelve Data rejected the API key");
                }
                if (body.contains("\"code\":403")) {
                    // e.g. a symbol that needs a higher plan
                    throw new PriceUnavailableException(Reason.UNSUPPORTED, "Twelve Data: this symbol is not included in the current plan");
                }
                if (body.contains("\"code\":404")) {
                    throw new PriceUnavailableException(Reason.SYMBOL_NOT_FOUND, "Twelve Data does not know this symbol");
                }
                throw new PriceUnavailableException(Reason.PROVIDER_MESSAGE, "Twelve Data returned an error instead of a quote");
            }
            return body;
        } catch (IOException e) {
            throw new PriceUnavailableException(Reason.NETWORK, "Twelve Data network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PriceUnavailableException(Reason.INTERRUPTED, "Interrupted while contacting Twelve Data", e);
        }
    }

    private String requireKey() {
        String key = keys.get();
        if (!ApiKeyManager.isConfigured(key)) {
            throw new PriceUnavailableException(Reason.NO_KEY, "No Twelve Data API key configured (TWELVEDATA_API_KEY)");
        }
        return URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    @Override
    public Stock getStock(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String sym = symbol.trim().toUpperCase();
        String body = get(String.format("%s/quote?%s&apikey=%s", baseUrl, symbolParams(sym), requireKey()), 10);
        double price = parseJsonStringValue(body, "\"close\":");
        if (!(price > 0)) {
            price = parseJsonStringValue(body, "\"price\":");
        }
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Twelve Data returned no valid price for " + sym);
        }
        Stock stock = new Stock(sym, sym, price);
        stock.setChangePercent(parseJsonStringValue(body, "\"percent_change\":"));
        stock.setHigh24h(parseJsonStringValue(body, "\"high\":"));
        stock.setLow24h(parseJsonStringValue(body, "\"low\":"));
        java.util.regex.Matcher cur = java.util.regex.Pattern.compile("\"currency\"\\s*:\\s*\"([A-Za-z]{3})\"").matcher(body);
        stock.setCurrency(cur.find() ? cur.group(1) : null); // stated by the provider, or unknown
        return stock;
    }

    @Override
    public boolean isValidStock(String symbol) {
        return getStock(symbol) != null;
    }

    @Override
    public String getHistoricalData(String symbol) {
        return getHistoricalData(symbol, 30);
    }

    public String getHistoricalData(String symbol, int days) {
        return get(String.format("%s/time_series?%s&interval=1day&outputsize=%d&apikey=%s", baseUrl,
                symbolParams(symbol.toUpperCase()), days, requireKey()), 15);
    }

    /** {@code SYMBOL} becomes {@code symbol=SYMBOL}; {@code SYMBOL:EXCHANGE} adds Twelve Data's documented {@code exchange} parameter. */
    static String symbolParams(String symbol) {
        int colon = symbol.indexOf(':');
        if (colon > 0 && colon < symbol.length() - 1) {
            return "symbol=" + URLEncoder.encode(symbol.substring(0, colon), StandardCharsets.UTF_8)
                    + "&exchange=" + URLEncoder.encode(symbol.substring(colon + 1), StandardCharsets.UTF_8);
        }
        return "symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8);
    }

    /** Numeric (possibly quoted) field, or 0.0 when absent/null/unparseable (0 means "not reported", never a price). */
    private double parseJsonStringValue(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;
            int start = index + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            String valueStr = json.substring(start, end).trim();
            if (valueStr.isEmpty() || valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }
}

package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Fetches live quotes from Alpha Vantage.
 *
 * The API key is read from the ALPHAVANTAGE_API_KEY environment variable. This class never
 * fabricates prices: if a live quote cannot be obtained it throws PriceUnavailableException with a
 * {@link Reason}, or returns null when the provider reports the symbol does not exist.
 *
 * Retries: at most {@value #MAX_ATTEMPTS} attempts, only for connection failures/timeouts and HTTP
 * 5xx, with exponential backoff. Rate limits, key problems and 4xx are never retried.
 *
 * The provider-message classification below is based on Alpha Vantage's documented/observed wording
 * and has NOT been verified against the live service.
 */
public class AlphaVantageService implements StockPriceService {
    public static final String API_KEY_ENV = "ALPHAVANTAGE_API_KEY";
    private static final String DEFAULT_BASE_URL = "https://www.alphavantage.co/query";
    static final int MAX_BODY_BYTES = 1_000_000;
    static final int MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BACKOFF_MILLIS = 500;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final long backoffMillis;

    /** Key from the ALPHAVANTAGE_API_KEY environment variable or the ignored local settings file. */
    public AlphaVantageService() {
        this(ApiKeyManager.getAlphaVantageKey());
    }

    public AlphaVantageService(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, Duration.ofSeconds(10), DEFAULT_BACKOFF_MILLIS);
    }

    /** Package-private for tests (local fake server, short timeout, 1 ms backoff). */
    AlphaVantageService(String apiKey, String baseUrl, Duration timeout) {
        this(apiKey, baseUrl, timeout, 1);
    }

    AlphaVantageService(String apiKey, String baseUrl, Duration timeout, long backoffMillis) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.backoffMillis = backoffMillis;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /**
     * @return the live Stock, or null if the provider says the symbol does not exist
     * @throws PriceUnavailableException if no live price could be obtained
     */
    @Override
    public Stock getStock(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new PriceUnavailableException(Reason.NO_KEY,
                    "No API key configured. Set the " + API_KEY_ENV + " environment variable.");
        }
        String sym = symbol.trim();
        String url = baseUrl + "?function=GLOBAL_QUOTE&symbol="
                + URLEncoder.encode(sym, StandardCharsets.UTF_8)
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return withRetries(() -> parseGlobalQuote(sym, fetchBody(url, sym)));
    }

    /**
     * Daily price history as the provider's raw JSON (TIME_SERIES_DAILY). Provider error/limit messages are
     * raised as PriceUnavailableException, never returned as if they were data.
     */
    /** Set once the provider has refused the full series (a premium feature on some plans): later calls go straight to compact. */
    private volatile boolean fullHistoryRefused;

    @Override
    public String getHistoricalData(String symbol, int days) {
        if (days <= 100 || fullHistoryRefused) {
            return getHistoricalData(symbol);
        }
        try {
            return fetchDaily(symbol, "full");
        } catch (PriceUnavailableException e) {
            if (e.getReason() == Reason.UNSUPPORTED) {
                fullHistoryRefused = true; // not a limit or outage: this plan only has the compact series
                return fetchDaily(symbol, "compact");
            }
            throw e;
        }
    }

    @Override
    public String getHistoricalData(String symbol) {
        return fetchDaily(symbol, "compact");
    }

    private String fetchDaily(String symbol, String outputSize) {
        if (symbol == null || symbol.isBlank()) {
            throw new PriceUnavailableException(Reason.SYMBOL_NOT_FOUND, "Symbol is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new PriceUnavailableException(Reason.NO_KEY,
                    "No API key configured. Set the " + API_KEY_ENV + " environment variable.");
        }
        String sym = symbol.trim();
        String url = baseUrl + "?function=TIME_SERIES_DAILY&outputsize=" + outputSize + "&symbol="
                + URLEncoder.encode(sym, StandardCharsets.UTF_8)
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return withRetries(() -> {
            String body = fetchBody(url, sym);
            requireNoProviderMessage(body);
            return body;
        });
    }

    private <T> T withRetries(java.util.function.Supplier<T> call) {
        PriceUnavailableException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (PriceUnavailableException e) {
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                last = e;
                pause(backoffMillis << (attempt - 1));
            }
        }
        throw last; // unreachable: loop either returns or throws
    }

    /** Throws if the body is a provider limit/key/error message instead of data. */
    static void requireNoProviderMessage(String body) {
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (JSONException e) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Malformed provider response", e);
        }
        if (json.has("Note")) {
            throw new PriceUnavailableException(Reason.RATE_LIMIT, "The provider call-frequency limit was reached");
        }
        if (json.has("Information")) {
            throw classifyInformation(json.optString("Information", ""));
        }
        if (json.has("Error Message")) {
            throw new PriceUnavailableException(Reason.SYMBOL_NOT_FOUND, "The provider has no data for that symbol");
        }
    }

    private static boolean isRetryable(PriceUnavailableException e) {
        // Only connection failures/timeouts and HTTP 5xx are transient; 4xx, key and quota problems are not.
        return e.getReason() == Reason.NETWORK
                || (e.getReason() == Reason.HTTP_ERROR && e.getMessage().startsWith("Provider returned HTTP 5"));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PriceUnavailableException(Reason.INTERRUPTED, "Interrupted while waiting to retry", ie);
        }
    }

    /** One HTTP GET with status classification and a body size cap. */
    private String fetchBody(String url, String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(timeout).build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                response.body().close();
                if (status == 401 || status == 403) {
                    throw new PriceUnavailableException(Reason.INVALID_KEY, "The provider rejected the API key (HTTP " + status + ")");
                }
                if (status == 429) {
                    throw new PriceUnavailableException(Reason.RATE_LIMIT, "The provider rate limit was reached (HTTP 429)");
                }
                throw new PriceUnavailableException(Reason.HTTP_ERROR, "Provider returned HTTP " + status);
            }
            byte[] bytes;
            try (InputStream in = response.body()) {
                bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (bytes.length > MAX_BODY_BYTES) {
                throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Provider response too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PriceUnavailableException(Reason.NETWORK, "Network error fetching " + symbol + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PriceUnavailableException(Reason.INTERRUPTED, "Interrupted while fetching " + symbol, e);
        }
    }

    @Override
    public boolean isValidStock(String symbol) {
        return getStock(symbol) != null;
    }

    /**
     * Parse an Alpha Vantage GLOBAL_QUOTE body. Pure function, no I/O.
     *
     * @return the Stock, or null if the symbol was not found
     * @throws PriceUnavailableException for rate limits, key problems, provider errors, malformed data
     */
    static Stock parseGlobalQuote(String symbol, String body) {
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (JSONException e) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Malformed provider response", e);
        }
        // Alpha Vantage reports rate limits and key problems with HTTP 200 and these keys.
        if (json.has("Note")) {
            throw new PriceUnavailableException(Reason.RATE_LIMIT, "The provider call-frequency limit was reached");
        }
        if (json.has("Information")) {
            throw classifyInformation(json.optString("Information", ""));
        }
        if (json.has("Error Message")) {
            String msg = json.optString("Error Message", "").toLowerCase(Locale.ROOT);
            if (msg.contains("apikey") || msg.contains("api key")) {
                throw new PriceUnavailableException(Reason.INVALID_KEY, "The provider says the API key is invalid or missing");
            }
            return null; // invalid symbol
        }
        JSONObject quote = json.optJSONObject("Global Quote");
        if (quote == null) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Unexpected provider response (no Global Quote)");
        }
        if (quote.length() == 0) {
            return null; // symbol not found
        }
        String priceStr = quote.optString("05. price", "");
        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Provider returned an unparseable price", e);
        }
        if (!Double.isFinite(price) || price <= 0) {
            throw new PriceUnavailableException(Reason.BAD_RESPONSE, "Provider returned an invalid price: " + priceStr);
        }
        Stock stock = new Stock(symbol, symbol, price);
        stock.setChangePercent(parsePercent(quote.optString("10. change percent", "")));
        // GLOBAL_QUOTE reports no currency. A bare US ticker is quoted in USD; a symbol with an exchange suffix
        // (e.g. ".BSE") could be any currency, so it is left unknown and trading on it is blocked.
        stock.setCurrency(symbol.contains(".") ? null : "USD");
        return stock;
    }

    /** "1.2345%" -> 1.2345; anything else is "not reported" (0), never an error: the price is what matters. */
    private static double parsePercent(String text) {
        try {
            double v = Double.parseDouble(text.replace("%", "").trim());
            return Double.isFinite(v) ? v : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static PriceUnavailableException classifyInformation(String info) {
        String m = info.toLowerCase(Locale.ROOT);
        // The notice for the full price series says "outputsize=full ... premium": a plan limit, not a quota or an outage.
        if (m.contains("outputsize")) {
            return new PriceUnavailableException(Reason.UNSUPPORTED, "The provider's plan does not include the full price history");
        }
        // Quota wording is checked first: quota messages also mention "API key".
        if (m.contains("per day") || m.contains("daily")) {
            return new PriceUnavailableException(Reason.RATE_LIMIT, "The provider's daily quota was reached");
        }
        if (m.contains("rate limit") || m.contains("requests per") || m.contains("call frequency") || m.contains("premium")) {
            return new PriceUnavailableException(Reason.RATE_LIMIT, "The provider quota or rate limit was reached");
        }
        if (m.contains("api key") || m.contains("apikey")) {
            return new PriceUnavailableException(Reason.INVALID_KEY, "The provider says the API key is invalid, missing or demo-only");
        }
        return new PriceUnavailableException(Reason.PROVIDER_MESSAGE, "The provider returned a message instead of a quote");
    }
}

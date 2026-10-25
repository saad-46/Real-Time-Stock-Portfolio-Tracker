package com.portfolio.service;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CurrencyService - exchange rates.
 *
 * Two different uses, deliberately kept apart:
 * <ul>
 * <li>{@link #requireRate} is for MONEY MOVEMENTS (trades). It returns only a validated rate (finite, positive) that a
 *     named source reported no more than {@link #RATE_MAX_AGE} ago, and otherwise throws; there is never a fallback.</li>
 * <li>{@link #convert} is for DISPLAY aggregates (portfolio totals in the chosen display currency). If the live rate is
 *     unavailable it falls back to fixed approximate rates and sets {@link #usedFallbackRates()}; those numbers must
 *     never be used to move money.</li>
 * </ul>
 */
public class CurrencyService {
    /** A rate older than this is not used for a trade. */
    public static final Duration RATE_MAX_AGE = Duration.ofHours(1);
    /** How long a failed lookup is remembered, so a screen full of conversions makes one slow attempt, not dozens. */
    public static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);
    public static final String DEFAULT_SOURCE = "exchangerate-api.com";

    /** A display conversion: the value and whether it is approximate (fixed fallback rates, NOT a validated live rate). */
    public record Converted(double value, boolean approximate) {
    }

    /** A validated exchange rate: 1 unit of {@code from} = {@code rate} units of {@code to}. */
    public record Rate(String from, String to, double rate, Instant fetchedAt, String source) {
    }

    /** Where rates come from (HTTP in production, a fake in tests). Implementations throw {@link IOException} on failure. */
    public interface RateSource {
        Rate fetch(String from, String to) throws IOException;
    }

    private final RateSource source;
    private final Clock clock;
    private final Map<String, Rate> cache = new ConcurrentHashMap<>();
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();

    private record Failure(Instant at, String message) {
    }
    private volatile boolean usedFallback;
    private volatile Instant lastFallbackAt;
    /** How long after the last approximate conversion the "approximate rates in use" state lasts. */
    public static final Duration APPROXIMATION_WINDOW = Duration.ofMinutes(5);
    private final HttpClient client = Net.client();

    private String baseUrl = "https://v6.exchangerate-api.com/v6/";
    private java.util.function.Supplier<String> keySupplier = ApiKeyManager::getExchangeRateApiKey;

    public CurrencyService() {
        this(null, Clock.systemUTC());
    }

    /** Package-private for tests: the live HTTP source against a local fake server with a fixed key. */
    static CurrencyService forTesting(String baseUrl, String key, Clock clock) {
        CurrencyService c = new CurrencyService(null, clock);
        c.baseUrl = baseUrl;
        c.keySupplier = () -> key;
        return c;
    }

    /** @param source null = live HTTP source */
    public CurrencyService(RateSource source, Clock clock) {
        this.source = source != null ? source : this::fetchOverHttp;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ strict path (trades)

    /**
     * A validated, fresh exchange rate.
     *
     * @throws TradeBlockedException (RATE_UNAVAILABLE) if none can be obtained: the source failed, the answer was
     *         invalid, or the only rate known is older than {@link #RATE_MAX_AGE}
     */
    public Rate requireRate(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            throw new TradeBlockedException(TradeBlockedException.Reason.RATE_UNAVAILABLE, "Currency missing for conversion");
        }
        String f = from.trim().toUpperCase();
        String t = to.trim().toUpperCase();
        Rate identity = f.equals(t) ? new Rate(f, t, 1.0, clock.instant(), "same currency") : null;
        if (identity != null) {
            return identity;
        }
        String key = f + ">" + t;
        Instant now = clock.instant();
        Rate cached = cache.get(key);
        if (cached != null && isUsable(cached, now)) {
            return cached;
        }
        Failure recent = failures.get(key);
        if (recent != null && Duration.between(recent.at(), now).compareTo(FAILURE_MEMORY) < 0 && !Duration.between(recent.at(), now).isNegative()) {
            throw new TradeBlockedException(TradeBlockedException.Reason.RATE_UNAVAILABLE, recent.message());
        }
        try {
            Rate fresh = source.fetch(f, t);
            if (fresh == null || !isUsable(fresh, now) || !fresh.from().equalsIgnoreCase(f) || !fresh.to().equalsIgnoreCase(t)) {
                throw new IOException("the exchange-rate source returned an invalid or outdated rate");
            }
            cache.put(key, fresh);
            failures.remove(key);
            return fresh;
        } catch (IOException | RuntimeException e) {
            String message = "No valid exchange rate " + f + " to " + t + " is available (" + e.getMessage()
                    + "). The trade was blocked; nothing was changed.";
            failures.put(key, new Failure(now, message));
            throw new TradeBlockedException(TradeBlockedException.Reason.RATE_UNAVAILABLE, message, e);
        }
    }

    private static boolean isUsable(Rate r, Instant now) {
        if (!(r.rate() > 0) || Double.isInfinite(r.rate()) || r.fetchedAt() == null || r.source() == null || r.source().isBlank()) {
            return false;
        }
        Duration age = Duration.between(r.fetchedAt(), now);
        return !age.isNegative() && age.compareTo(RATE_MAX_AGE) <= 0;
    }

    private Rate fetchOverHttp(String from, String to) throws IOException {
        String key = keySupplier.get();
        if (!ApiKeyManager.isConfigured(key)) {
            throw new IOException("no EXCHANGERATE_API_KEY configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + URLEncoder.encode(key, StandardCharsets.UTF_8)
                            + "/latest/" + URLEncoder.encode(from, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("exchange-rate service returned HTTP " + response.statusCode());
            }
            JSONObject json = new JSONObject(response.body());
            if (!"success".equals(json.optString("result"))) {
                throw new IOException("exchange-rate service reported: " + json.optString("error-type", "an error"));
            }
            JSONObject rates = json.optJSONObject("conversion_rates");
            if (rates == null || !rates.has(to)) {
                throw new IOException("no " + to + " rate in the response");
            }
            return new Rate(from, to, rates.getDouble(to), clock.instant(), DEFAULT_SOURCE);
        } catch (JSONException e) {
            throw new IOException("unreadable exchange-rate response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    // ------------------------------------------------------------------ display path

    /**
     * Converts for DISPLAY. Uses a live rate when one can be validated, otherwise fixed approximate rates (and
     * {@link #usedFallbackRates()} becomes true). Never use this to move money; use {@link #requireRate}.
     */
    public double convert(double amount, String fromCurrency, String toCurrency) {
        return convertChecked(amount, fromCurrency, toCurrency).value();
    }

    /** Like {@link #convert} but says whether the figure is approximate, so the UI can mark it. */
    public Converted convertChecked(double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return new Converted(amount, false);
        }
        try {
            return new Converted(amount * requireRate(fromCurrency, toCurrency).rate(), false);
        } catch (TradeBlockedException e) {
            usedFallback = true;
            lastFallbackAt = clock.instant();
            return new Converted(fallbackConvert(amount, fromCurrency.toUpperCase(), toCurrency.toUpperCase()), true);
        }
    }

    /** True while approximate rates have been used for a displayed figure within {@link #APPROXIMATION_WINDOW}; clears once live rates work again. */
    public boolean approximateRatesInUse() {
        Instant t = lastFallbackAt;
        return t != null && Duration.between(t, clock.instant()).compareTo(APPROXIMATION_WINDOW) <= 0;
    }

    /** Forget remembered lookup failures so the next conversion tries the source again (an explicit user "refresh"). */
    public void forgetFailures() {
        failures.clear();
    }

    /** True once any displayed conversion had to use the fixed approximate rates. */
    public boolean usedFallbackRates() {
        return usedFallback;
    }

    // Hardcoded approximate rates, display only
    private double fallbackConvert(double amount, String from, String to) {
        // Base USD
        double inUsd = amount;
        if (from.equals("INR"))
            inUsd = amount / 83.0;
        else if (from.equals("EUR"))
            inUsd = amount / 0.92;
        else if (from.equals("SAR"))
            inUsd = amount / 3.75;

        if (to.equals("USD"))
            return inUsd;
        if (to.equals("INR"))
            return inUsd * 83.0;
        if (to.equals("EUR"))
            return inUsd * 0.92;
        if (to.equals("SAR"))
            return inUsd * 3.75;

        return amount; // Unsupported
    }
}

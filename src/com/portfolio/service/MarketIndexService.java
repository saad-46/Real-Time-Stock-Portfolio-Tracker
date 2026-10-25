package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market index cards for the landing page, read through the existing price providers (same no-fake-prices rule): a card carries a
 * value only if a configured provider reported one for a symbol that provider is asked about, and every other outcome is an explicit,
 * distinct state.
 *
 * <p>Index symbols differ per provider, so each index lists the symbol to try for each provider. Those symbols follow each
 * provider's usual conventions but have <b>not</b> been verified against a live account here; a provider that rejects a symbol is
 * reported as "does not support this symbol" and the next configured provider is tried. Nothing is guessed, defaulted or reused.
 *
 * <p>Requests are bounded: one quote per (provider, symbol) per refresh, results are cached for {@code cacheFor}, a provider that
 * reported a rate limit is not asked again until its back-off ends, and a refresh already running is not started twice.
 */
public final class MarketIndexService implements MarketSource {
    public enum State { LOADING, FRESH, STALE, UNAVAILABLE }

    /** Why a card has no value, so the UI can say something accurate and actionable. */
    public enum Problem { NONE, NOT_CONFIGURED, INVALID_KEY, RATE_LIMIT, QUOTA, NETWORK, UNSUPPORTED, BAD_RESPONSE, PROVIDER_ERROR }

    /** An index and the symbol to ask each provider (by provider id) for. */
    public record IndexDef(String id, String name, Map<String, String> symbols) {
        public IndexDef {
            symbols = Map.copyOf(symbols);
        }

        /** The plain ticker shown on the card (the symbol without exchange decoration). */
        public String ticker() {
            String any = symbols.values().stream().sorted().findFirst().orElse(id.toUpperCase());
            int cut = any.length();
            for (char c : new char[] { ':', '.' }) {
                int i = any.indexOf(c);
                if (i > 0) cut = Math.min(cut, i);
            }
            return any.substring(0, cut).replace("^", "");
        }

        public IndexDef(String id, String name, String singleSymbolForAnyProvider) {
            this(id, name, Map.of("*", singleSymbolForAnyProvider));
        }

        String symbolFor(String providerId) {
            String s = symbols.get(providerId);
            return s != null ? s : symbols.get("*");
        }
    }

    /** One configured way to reach a price provider. */
    public interface Provider {
        String id();

        String name();

        /** True when a key is configured (never when it is merely a non-empty guess: the provider decides validity). */
        boolean configured();

        /** The shortest gap this provider's plan tolerates between two requests (0 = none). Alpha Vantage's free plan allows about one per second. */
        default long minIntervalMillis() {
            return 0;
        }

        Stock quote(String symbol);

        /** Raw daily-history JSON for {@code days} days; throws {@link PriceUnavailableException} when unsupported. */
        String history(String symbol, int days);
    }

    /** What one card shows. {@code value}/{@code changePercent} are NaN when not known. */
    public record Snapshot(IndexDef index, State state, double value, double changePercent, List<PriceHistory.Point> history,
            Instant fetchedAt, String provider, Problem problem, String message, String currency) {
        public Snapshot {
            history = history == null ? List.of() : List.copyOf(history);
        }

        /** Without a provider-stated currency. */
        public Snapshot(IndexDef index, State state, double value, double changePercent, List<PriceHistory.Point> history,
                Instant fetchedAt, String provider, Problem problem, String message) {
            this(index, state, value, changePercent, history, fetchedAt, provider, problem, message, null);
        }

        public boolean hasValue() { return !Double.isNaN(value); }

        public boolean hasChange() { return !Double.isNaN(changePercent); }

        /** Closing values for a sparkline. */
        public double[] series() {
            double[] v = new double[history.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = history.get(i).close();
            }
            return v;
        }

        public static Snapshot loading(IndexDef d) {
            return new Snapshot(d, State.LOADING, Double.NaN, Double.NaN, null, null, null, Problem.NONE, "Loading…");
        }

        public static Snapshot unavailable(IndexDef d, Problem p, String why) {
            return new Snapshot(d, State.UNAVAILABLE, Double.NaN, Double.NaN, null, null, null, p, why);
        }

        public static Snapshot unavailable(IndexDef d, String why) {
            return unavailable(d, Problem.PROVIDER_ERROR, why);
        }
    }

    /**
     * The eight landing-page instruments: two Indian indices and six large Indian companies. Symbols per provider follow each
     * provider's conventions (Twelve Data {@code SYMBOL:NSE}, Finnhub {@code SYMBOL.NS} or {@code ^INDEX}, Alpha Vantage
     * {@code SYMBOL.BSE}). Checked on 2026-10-25 with the user's own free keys: Alpha Vantage serves the company symbols; it has no
     * symbol for NIFTY 50 or SENSEX (only an ETF named like SENSEX, which is not the index and is never shown as it). The Twelve Data
     * and Finnhub keys were rejected with HTTP 401 at that time, so their index and stock symbols could not be tested.
     */
    public static final List<IndexDef> DEFAULT_INDICES = List.of(
            new IndexDef("nifty50", "NIFTY 50", Map.of("twelvedata", "NIFTY:NSE", "finnhub", "^NSEI")),
            new IndexDef("sensex", "SENSEX", Map.of("twelvedata", "SENSEX:BSE", "finnhub", "^BSESN")),
            stock("reliance", "Reliance Industries", "RELIANCE"),
            stock("hdfcbank", "HDFC Bank", "HDFCBANK"),
            stock("icicibank", "ICICI Bank", "ICICIBANK"),
            stock("tcs", "Tata Consultancy Services", "TCS"),
            stock("infosys", "Infosys", "INFY"),
            stock("sbi", "State Bank of India", "SBIN"));

    private static IndexDef stock(String id, String name, String ticker) {
        return new IndexDef(id, name, Map.of("twelvedata", ticker + ":NSE", "finnhub", ticker + ".NS", "alphavantage", ticker + ".BSE"));
    }

    static final int HISTORY_DAYS = 400;
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofSeconds(90);
    /** A daily quota does not come back for hours: stop asking (and remember that across restarts) instead of burning requests. */
    private static final Duration QUOTA_BACKOFF = Duration.ofHours(6);
    private static final Duration HISTORY_MAX_AGE = Duration.ofHours(12);

    private final List<Provider> providers;
    private final List<IndexDef> indices;
    private final Duration cacheFor;
    private final Duration staleAfter;
    private final Clock clock;
    private final Map<String, Snapshot> last = new LinkedHashMap<>();
    private final Map<String, Instant> providerBackoffUntil = new LinkedHashMap<>();
    private boolean running;

    /** The configured providers of the existing {@link MultiSourceStockService}, in its order. */
    public MarketIndexService(MultiSourceStockService prices) {
        this(defaultProviders(prices), DEFAULT_INDICES, Duration.ofMinutes(10), QuotePolicy.DISPLAY_FRESH_MAX_AGE, Clock.systemUTC());
        String override = System.getProperty("stockvault.marketcache");
        withCacheFile(override != null && !override.isBlank() ? java.nio.file.Path.of(override)
                : com.portfolio.AppPaths.userDataDir().resolve("market-cache.properties"));
    }

    public MarketIndexService(List<Provider> providers, List<IndexDef> indices, Duration cacheFor, Duration staleAfter, Clock clock) {
        this.providers = List.copyOf(providers);
        this.indices = List.copyOf(indices);
        this.cacheFor = cacheFor;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Override
    public List<IndexDef> indices() {
        return indices;
    }

    public List<Provider> providers() {
        return providers;
    }

    /** Names of the providers that have a key configured (never the keys). */
    @Override
    public List<String> configuredProviderNames() {
        List<String> names = new ArrayList<>();
        for (Provider p : providers) {
            if (p.configured()) {
                names.add(p.name());
            }
        }
        return names;
    }

    /**
     * Ask the providers for every index. Blocking network access: never call on the Swing event thread. A call while another is
     * running returns the last known snapshots instead of starting a second round. {@code force} ignores the cache but still
     * respects rate-limit back-off.
     */
    @Override
    public List<Snapshot> refresh(boolean force) {
        synchronized (this) {
            if (running) {
                return current();
            }
            running = true;
        }
        try {
            List<Snapshot> out = new ArrayList<>();
            for (IndexDef d : indices) {
                out.add(loadOne(d, force));
            }
            return out;
        } finally {
            synchronized (this) {
                running = false;
            }
        }
    }

    /** The latest snapshots without any network access; old values are re-labelled stale. */
    public synchronized List<Snapshot> current() {
        List<Snapshot> out = new ArrayList<>();
        for (IndexDef d : indices) {
            Snapshot s = last.get(d.id());
            out.add(s == null ? Snapshot.loading(d) : aged(s));
        }
        return out;
    }

    private Snapshot loadOne(IndexDef d, boolean force) {
        Snapshot cached;
        synchronized (this) {
            cached = last.get(d.id());
        }
        if (!force && cached != null && cached.state() == State.FRESH && cached.fetchedAt() != null
                && Duration.between(cached.fetchedAt(), clock.instant()).compareTo(cacheFor) < 0) {
            return cached;
        }
        Snapshot fresh = fetch(d);
        if (fresh.state() == State.FRESH && fresh.history().isEmpty() && cached != null && !cached.history().isEmpty()
                && java.util.Objects.equals(cached.provider(), fresh.provider())) {
            fresh = new Snapshot(d, State.FRESH, fresh.value(), fresh.changePercent(), cached.history(), fresh.fetchedAt(), fresh.provider(),
                    fresh.problem(), fresh.message(), fresh.currency());
        }
        synchronized (this) {
            if (fresh.state() == State.UNAVAILABLE && cached != null && cached.hasValue()) {
                // keep the last good value but say it is old and why the refresh failed; never relabel it live
                Snapshot kept = new Snapshot(d, State.STALE, cached.value(), cached.changePercent(), cached.history(), cached.fetchedAt(),
                        cached.provider(), fresh.problem(), "Refresh failed: " + fresh.message(), cached.currency());
                last.put(d.id(), kept);
                return kept;
            }
            last.put(d.id(), fresh);
        }
        if (fresh.state() == State.FRESH) {
            saveCache();
        }
        return fresh;
    }

    /**
     * Daily history for one instrument, from the provider that gave its quote (or the next configured one). Blocking. Cached in the
     * snapshot; asks again only if the snapshot has none. Returns the updated snapshot, or null when the instrument has no value.
     */
    @Override
    public Snapshot history(String indexId) {
        IndexDef def = null;
        for (IndexDef d : indices) {
            if (d.id().equals(indexId)) {
                def = d;
            }
        }
        Snapshot cur;
        synchronized (this) {
            cur = last.get(indexId);
        }
        if (def == null || cur == null || !cur.hasValue()) {
            return null;
        }
        Instant haveAt;
        synchronized (this) {
            haveAt = historyFetchedAt.get(indexId);
        }
        if (!cur.history().isEmpty() && (haveAt == null || Duration.between(haveAt, clock.instant()).compareTo(HISTORY_MAX_AGE) < 0)) {
            return cur;
        }
        List<Provider> order = new ArrayList<>();
        for (Provider p : providers) {
            if (p.name().equals(cur.provider())) {
                order.add(0, p);
            } else {
                order.add(p);
            }
        }
        List<String> why = new ArrayList<>();
        for (Provider p : order) {
            String symbol = def.symbolFor(p.id());
            if (!p.configured() || symbol == null) {
                continue;
            }
            Instant until;
            synchronized (this) {
                until = providerBackoffUntil.get(p.id());
            }
            if (until != null && clock.instant().isBefore(until)) {
                why.add(pausedText(p, until));
                continue;
            }
            try {
                pace(p);
                List<PriceHistory.Point> hist = PriceHistory.parse(p.history(symbol, HISTORY_DAYS));
                Snapshot updated = new Snapshot(def, cur.state(), cur.value(), cur.changePercent(), hist, cur.fetchedAt(), cur.provider(),
                        cur.problem(), cur.message(), cur.currency());
                synchronized (this) {
                    last.put(indexId, updated);
                    historyFetchedAt.put(indexId, clock.instant());
                }
                saveCache();
                return updated;
            } catch (PriceUnavailableException e) {
                Problem prob = classify(e);
                backOff(p, prob);
                why.add(describe(p, prob));
            } catch (RuntimeException e) {
                why.add(p.name() + " failed (" + e.getClass().getSimpleName() + ")");
            }
        }
        if (!cur.history().isEmpty()) {
            return cur; // an older series is better than none, and is labelled by its own dates
        }
        // keep the quote, record why there is no history (the chart explains it)
        Snapshot noHist = new Snapshot(def, cur.state(), cur.value(), cur.changePercent(), List.of(), cur.fetchedAt(), cur.provider(),
                cur.problem(), why.isEmpty() ? "No daily history available from your providers" : "No daily history: " + String.join("; ", new java.util.LinkedHashSet<>(why)),
                cur.currency());
        synchronized (this) {
            last.put(indexId, noHist);
        }
        return noHist;
    }

    // ------------------------------------------------------------------ on-disk quote cache (prices only, never keys)

    private java.nio.file.Path cacheFile;
    private final Map<String, Instant> historyFetchedAt = new LinkedHashMap<>();

    /** Restore the last quotes from a previous run so a restart neither wastes the providers' daily quota nor shows blanks. */
    public MarketIndexService withCacheFile(java.nio.file.Path file) {
        this.cacheFile = file;
        if (file != null && java.nio.file.Files.isRegularFile(file)) {
            java.util.Properties p = new java.util.Properties();
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                p.load(in);
                for (IndexDef d : indices) {
                    String v = p.getProperty(d.id() + ".value");
                    String t = p.getProperty(d.id() + ".fetched");
                    if (v == null || t == null) {
                        continue;
                    }
                    double value = Double.parseDouble(v);
                    double change = Double.parseDouble(p.getProperty(d.id() + ".change", "NaN"));
                    String cur = p.getProperty(d.id() + ".currency");
                    if (!(value > 0) || Double.isInfinite(value)) {
                        continue;
                    }
                    List<PriceHistory.Point> hist = parseHistory(p.getProperty(d.id() + ".hist"));
                    last.put(d.id(), new Snapshot(d, State.FRESH, value, change, hist, Instant.parse(t), p.getProperty(d.id() + ".provider"),
                            Problem.NONE, "Last quote from a previous run", cur == null || cur.isBlank() ? null : cur));
                    String histAt = p.getProperty(d.id() + ".histAt");
                    if (!hist.isEmpty() && histAt != null) {
                        historyFetchedAt.put(d.id(), Instant.parse(histAt));
                    }
                }
                for (Provider pr : providers) {
                    String until = p.getProperty("quota." + pr.id());
                    if (until != null && Instant.parse(until).isAfter(clock.instant())) {
                        providerBackoffUntil.put(pr.id(), Instant.parse(until));
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                // an unreadable cache is simply ignored
            }
        }
        return this;
    }

    private static List<PriceHistory.Point> parseHistory(String text) {
        List<PriceHistory.Point> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split(";")) {
            int colon = part.indexOf(':');
            if (colon > 0) {
                try {
                    out.add(new PriceHistory.Point(java.time.LocalDate.parse(part.substring(0, colon)), Double.parseDouble(part.substring(colon + 1))));
                } catch (RuntimeException e) {
                    return new ArrayList<>(); // a damaged series is dropped whole
                }
            }
        }
        return out;
    }

    private void saveCache() {
        java.nio.file.Path file = cacheFile;
        if (file == null) {
            return;
        }
        java.util.Properties p = new java.util.Properties();
        synchronized (this) {
            for (Snapshot s : last.values()) {
                if (s.hasValue() && s.fetchedAt() != null && s.provider() != null) {
                    String id = s.index().id();
                    p.setProperty(id + ".value", Double.toString(s.value()));
                    p.setProperty(id + ".change", Double.toString(s.changePercent()));
                    p.setProperty(id + ".provider", s.provider());
                    p.setProperty(id + ".fetched", s.fetchedAt().toString());
                    if (s.currency() != null) {
                        p.setProperty(id + ".currency", s.currency());
                    }
                    if (!s.history().isEmpty() && historyFetchedAt.containsKey(id)) {
                        StringBuilder h = new StringBuilder();
                        for (PriceHistory.Point pt : s.history()) {
                            h.append(pt.date()).append(':').append(pt.close()).append(';');
                        }
                        p.setProperty(id + ".hist", h.toString());
                        p.setProperty(id + ".histAt", historyFetchedAt.get(id).toString());
                    }
                }
            }
            for (Map.Entry<String, Instant> e : providerBackoffUntil.entrySet()) {
                if (Duration.between(clock.instant(), e.getValue()).compareTo(Duration.ofHours(1)) > 0) {
                    p.setProperty("quota." + e.getKey(), e.getValue().toString()); // only a long (daily) pause is worth remembering
                }
            }
        }
        try {
            java.nio.file.Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(file)) {
                p.store(out, "StockVault last quotes (prices only)");
            }
        } catch (java.io.IOException e) {
            // the cache is an optimisation only
        }
    }

    private Snapshot fetch(IndexDef d) {
        Problem worst = Problem.NOT_CONFIGURED;
        List<String> reasons = new ArrayList<>();
        boolean anyConfigured = false;
        for (Provider p : providers) {
            if (!p.configured()) {
                continue;
            }
            anyConfigured = true;
            String symbol = d.symbolFor(p.id());
            if (symbol == null) {
                worst = rank(worst, Problem.UNSUPPORTED);
                reasons.add(p.name() + " does not offer this instrument");
                continue;
            }
            Instant until;
            synchronized (this) {
                until = providerBackoffUntil.get(p.id());
            }
            if (until != null && clock.instant().isBefore(until)) {
                worst = rank(worst, pausedProblem(until));
                reasons.add(pausedText(p, until));
                continue;
            }
            Stock quote;
            try {
                pace(p);
                quote = p.quote(symbol);
            } catch (PriceUnavailableException e) {
                Problem prob = classify(e);
                backOff(p, prob);
                worst = rank(worst, prob);
                reasons.add(describe(p, prob));
                continue;
            } catch (RuntimeException e) {
                worst = rank(worst, Problem.PROVIDER_ERROR);
                reasons.add(p.name() + " failed (" + e.getClass().getSimpleName() + ")");
                continue;
            }
            if (quote == null) {
                worst = rank(worst, Problem.UNSUPPORTED);
                reasons.add(p.name() + " does not support this symbol");
                continue;
            }
            double value = quote.getCurrentPrice();
            if (!(value > 0) || Double.isInfinite(value)) {
                worst = rank(worst, Problem.BAD_RESPONSE);
                reasons.add(p.name() + " returned no usable value");
                continue;
            }
            double change = quote.getChangePercent();
            List<PriceHistory.Point> hist = List.of();
            String cur = quote.getCurrency();
            return new Snapshot(d, State.FRESH, value, Double.isNaN(change) || Double.isInfinite(change) ? Double.NaN : change, hist,
                    clock.instant(), p.name(), Problem.NONE, "Reported by " + p.name(), cur == null || cur.isBlank() ? null : cur);
        }
        if (!anyConfigured) {
            return Snapshot.unavailable(d, Problem.NOT_CONFIGURED, "API key not configured");
        }
        // every configured provider failed: say what each one said, so one provider's problem never hides another's
        return Snapshot.unavailable(d, worst, String.join("; ", new java.util.LinkedHashSet<>(reasons)));
    }

    private final Map<String, Long> lastRequestNanos = new LinkedHashMap<>();

    /** Waits (on the calling background thread) so one provider is never asked faster than its plan allows. */
    private void pace(Provider p) {
        long gap = p.minIntervalMillis();
        if (gap <= 0) {
            return;
        }
        long waitMs;
        synchronized (this) {
            Long last = lastRequestNanos.get(p.id());
            long sinceMs = last == null ? Long.MAX_VALUE : (System.nanoTime() - last) / 1_000_000L;
            waitMs = Math.max(0, gap - sinceMs);
            lastRequestNanos.put(p.id(), System.nanoTime() + waitMs * 1_000_000L);
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** The same snapshot, re-labelled STALE once older than the freshness window (never promoted back to live). */
    public Snapshot aged(Snapshot s) {
        if (s.state() == State.FRESH && s.fetchedAt() != null && Duration.between(s.fetchedAt(), clock.instant()).compareTo(staleAfter) > 0) {
            return new Snapshot(s.index(), State.STALE, s.value(), s.changePercent(), s.history(), s.fetchedAt(), s.provider(), s.problem(),
                    "Older than " + staleAfter.toMinutes() + " minutes; refresh to update", s.currency());
        }
        return s;
    }

    // ------------------------------------------------------------------ classification

    /** A daily-quota answer is told apart from a short burst limit by the provider client's wording. */
    static Problem classify(PriceUnavailableException e) {
        Problem p = classify(e.getReason());
        if (p == Problem.RATE_LIMIT && e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("daily quota")) {
            return Problem.QUOTA;
        }
        return p;
    }

    private void backOff(Provider p, Problem prob) {
        Duration d = prob == Problem.QUOTA ? QUOTA_BACKOFF : prob == Problem.RATE_LIMIT ? RATE_LIMIT_BACKOFF : null;
        if (d == null) {
            return;
        }
        synchronized (this) {
            providerBackoffUntil.put(p.id(), clock.instant().plus(d));
        }
        if (prob == Problem.QUOTA) {
            saveCache();
        }
    }

    private Problem pausedProblem(Instant until) {
        return Duration.between(clock.instant(), until).compareTo(Duration.ofHours(1)) > 0 ? Problem.QUOTA : Problem.RATE_LIMIT;
    }

    private String pausedText(Provider p, Instant until) {
        return pausedProblem(until) == Problem.QUOTA ? p.name() + ": daily quota used up; paused until it resets"
                : p.name() + ": rate limit reached; paused for a while";
    }

    static Problem classify(Reason r) {
        return switch (r) {
            case NO_KEY -> Problem.NOT_CONFIGURED;
            case INVALID_KEY -> Problem.INVALID_KEY;
            case RATE_LIMIT -> Problem.RATE_LIMIT;
            case NETWORK, INTERRUPTED -> Problem.NETWORK;
            case SYMBOL_NOT_FOUND, UNSUPPORTED -> Problem.UNSUPPORTED;
            case BAD_RESPONSE -> Problem.BAD_RESPONSE;
            default -> Problem.PROVIDER_ERROR;
        };
    }

    private static String describe(Provider p, Problem prob) {
        return switch (prob) {
            case NOT_CONFIGURED -> "API key not configured";
            case INVALID_KEY -> p.name() + ": invalid API key";
            case RATE_LIMIT -> p.name() + ": rate limit reached";
            case QUOTA -> p.name() + ": daily quota used up (resets later)";
            case NETWORK -> "Network unavailable";
            case UNSUPPORTED -> p.name() + " does not support this symbol";
            case BAD_RESPONSE -> p.name() + " returned an unreadable response";
            default -> p.name() + " reported an error";
        };
    }

    /** The more actionable problem wins when several providers fail differently. */
    private static Problem rank(Problem current, Problem candidate) {
        return priority(candidate) > priority(current) ? candidate : current;
    }

    private static int priority(Problem p) {
        return switch (p) {
            case NONE -> 0;
            case NOT_CONFIGURED -> 1;
            case UNSUPPORTED -> 2;
            case BAD_RESPONSE -> 3;
            case PROVIDER_ERROR -> 4;
            case NETWORK -> 5;
            case RATE_LIMIT -> 6;
            case QUOTA -> 7;
            case INVALID_KEY -> 8;
        };
    }

    // ------------------------------------------------------------------ real providers

    static List<Provider> defaultProviders(MultiSourceStockService prices) {
        List<Provider> out = new ArrayList<>();
        if (prices.getTwelveDataService() != null) {
            out.add(twelveData(prices.getTwelveDataService(), () -> ApiKeyManager.isConfigured(ApiKeyManager.getTwelveDataKey())));
        }
        if (prices.getAlphaVantageService() != null) {
            out.add(alphaVantage(prices.getAlphaVantageService(), () -> ApiKeyManager.isConfigured(ApiKeyManager.getAlphaVantageKey())));
        }
        if (prices.getFinnhubService() != null) {
            out.add(finnhub(prices.getFinnhubService(), () -> ApiKeyManager.isConfigured(ApiKeyManager.get("FINNHUB_API_KEY"))));
        }
        return out;
    }

    static Provider twelveData(TwelveDataService td, java.util.function.BooleanSupplier configured) {
        return new Provider() {
            @Override public String id() { return "twelvedata"; }
            @Override public String name() { return "Twelve Data"; }
            @Override public boolean configured() { return configured.getAsBoolean(); }
            @Override public Stock quote(String symbol) { return td.getStock(symbol); }
            @Override public String history(String symbol, int days) { return td.getHistoricalData(symbol, days); }
        };
    }

    static Provider alphaVantage(AlphaVantageService av, java.util.function.BooleanSupplier configured) {
        return new Provider() {
            @Override public String id() { return "alphavantage"; }
            @Override public String name() { return "Alpha Vantage"; }
            @Override public long minIntervalMillis() { return 1200; }
            @Override public boolean configured() { return configured.getAsBoolean(); }
            @Override public Stock quote(String symbol) { return av.getStock(symbol); }
            @Override public String history(String symbol, int days) { return av.getHistoricalData(symbol); }
        };
    }

    static Provider finnhub(FinnhubService fh, java.util.function.BooleanSupplier configured) {
        return new Provider() {
            @Override public String id() { return "finnhub"; }
            @Override public String name() { return "Finnhub"; }
            @Override public boolean configured() { return configured.getAsBoolean(); }
            @Override public Stock quote(String symbol) { return fh.getStock(symbol); }
            @Override public String history(String symbol, int days) {
                throw new PriceUnavailableException(Reason.UNSUPPORTED, "Finnhub's free tier has no daily history");
            }
        };
    }
}

package com.portfolio.service;

import com.portfolio.database.PriceHistoryDAO;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily price history for charts: real provider data only. A fetched series is stored (see {@link PriceHistoryDAO}) and reused
 * for {@link #MAX_AGE}; when a refresh fails the stored series is returned marked stale; when there is nothing at all the
 * caller gets a {@link PriceUnavailableException} to show as "unavailable". Fetches are paced and a failed symbol is not
 * retried for {@link #RETRY_AFTER}, so a rate-limited provider is not hammered.
 *
 * <p>All methods block (network and database): call them off the Swing event thread.
 */
public final class HistoryService {
    public static final Duration MAX_AGE = Duration.ofHours(12);
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    /** Calendar days requested: enough for the 1Y range (providers return fewer if that is all they offer). */
    public static final int REQUESTED_DAYS = 400;
    private static final long MIN_FETCH_GAP_MILLIS = 1_300; // free tiers allow only a few calls per minute

    /** A series with its provenance. */
    public record Result(List<PriceHistory.Point> points, Instant fetchedAt, boolean stale, String source) {
        public Result {
            points = List.copyOf(points);
        }
    }

    private final StockPriceService prices;
    private final PriceHistoryDAO dao;
    private final Clock clock;
    private final String source;
    private final Map<String, Object> locks = new HashMap<>();
    private final Map<String, Instant> failedAt = new HashMap<>();
    private final Map<String, String> failedWhy = new HashMap<>();
    private long lastFetchMillis;

    public HistoryService(StockPriceService prices, PriceHistoryDAO dao, Clock clock, String source) {
        this.prices = prices;
        this.dao = dao;
        this.clock = clock;
        this.source = source;
    }

    private synchronized Object lockFor(String symbol) {
        return locks.computeIfAbsent(symbol, k -> new Object());
    }

    /** Stored series only (no network); null if none. */
    public Result cached(String symbol) {
        try {
            List<PriceHistory.Point> stored = dao.load(symbol);
            Instant at = dao.fetchedAt(symbol);
            if (stored.isEmpty() || at == null) {
                return null;
            }
            return new Result(stored, at, Duration.between(at, clock.instant()).compareTo(MAX_AGE) > 0, source);
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * The series for a symbol, refreshing it when the stored one is older than {@link #MAX_AGE}.
     *
     * @throws PriceUnavailableException if there is no stored series and the provider could not supply one
     */
    public Result get(String symbol) {
        String key = symbol.trim().toUpperCase(java.util.Locale.ROOT);
        synchronized (lockFor(key)) { // one request per symbol at a time
            Result have = cached(key);
            if (have != null && !have.stale()) {
                return have;
            }
            Instant failed;
            String why;
            synchronized (this) {
                failed = failedAt.get(key);
                why = failedWhy.get(key);
            }
            if (failed != null && Duration.between(failed, clock.instant()).compareTo(RETRY_AFTER) < 0) {
                if (have != null) {
                    return have;
                }
                throw new PriceUnavailableException(PriceUnavailableException.Reason.PROVIDER_MESSAGE, why);
            }
            try {
                pace();
                List<PriceHistory.Point> fresh = PriceHistory.parse(prices.getHistoricalData(key, REQUESTED_DAYS));
                Instant now = clock.instant();
                try {
                    dao.store(key, fresh, now, source);
                } catch (SQLException e) {
                    // the series is still good for this session even if it could not be saved
                }
                synchronized (this) {
                    failedAt.remove(key);
                    failedWhy.remove(key);
                }
                return new Result(fresh, now, false, source);
            } catch (PriceUnavailableException e) {
                synchronized (this) {
                    failedAt.put(key, clock.instant());
                    failedWhy.put(key, e.getMessage());
                }
                if (have != null) {
                    return have; // stale but real, and labelled as such
                }
                throw e;
            }
        }
    }

    private void pace() {
        long wait;
        synchronized (this) {
            long now = System.currentTimeMillis();
            wait = Math.max(0, lastFetchMillis + MIN_FETCH_GAP_MILLIS - now);
            lastFetchMillis = now + wait;
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PriceUnavailableException(PriceUnavailableException.Reason.INTERRUPTED, "Interrupted");
            }
        }
    }
}

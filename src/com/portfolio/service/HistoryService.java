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
    /** A daily quota will not come back for hours: asking again would only waste requests. */
    public static final Duration QUOTA_RETRY_AFTER = Duration.ofHours(6);
    /** A missing or rejected key may be fixed by the user at any moment, so look again soon. */
    public static final Duration KEY_RETRY_AFTER = Duration.ofSeconds(30);
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
    private final Map<String, Instant> failedUntil = new HashMap<>();
    private final Map<String, String> failedWhy = new HashMap<>();
    private final Map<String, PriceUnavailableException.Reason> failedReason = new HashMap<>();
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
            Instant until;
            String why;
            PriceUnavailableException.Reason reason;
            synchronized (this) {
                until = failedUntil.get(key);
                why = failedWhy.get(key);
                reason = failedReason.get(key);
            }
            if (until != null && clock.instant().isBefore(until)) {
                if (have != null) {
                    return have;
                }
                throw new PriceUnavailableException(reason == null ? PriceUnavailableException.Reason.PROVIDER_MESSAGE : reason, why);
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
                    failedUntil.remove(key);
                    failedWhy.remove(key);
                    failedReason.remove(key);
                }
                return new Result(fresh, now, false, source);
            } catch (PriceUnavailableException e) {
                synchronized (this) {
                    failedUntil.put(key, clock.instant().plus(cooldown(e)));
                    failedWhy.put(key, e.getMessage());
                    failedReason.put(key, e.getReason());
                }
                if (have != null) {
                    return have; // stale but real, and labelled as such
                }
                throw e;
            }
        }
    }

    /** How long to leave a failed symbol alone, by the kind of failure. */
    static Duration cooldown(PriceUnavailableException e) {
        PriceUnavailableException.Reason r = e.getReason();
        if (r == PriceUnavailableException.Reason.NO_KEY || r == PriceUnavailableException.Reason.INVALID_KEY) {
            return KEY_RETRY_AFTER;
        }
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (r == PriceUnavailableException.Reason.RATE_LIMIT && (m.contains("daily") || m.contains("per day"))) {
            return QUOTA_RETRY_AFTER;
        }
        return RETRY_AFTER;
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

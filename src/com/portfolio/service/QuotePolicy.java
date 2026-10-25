package com.portfolio.service;

import java.time.Duration;
import java.time.Instant;

/**
 * The one freshness policy, used by both the trading service (enforcement) and the dashboards (display).
 *
 * <ul>
 * <li><b>Trading:</b> a quote may be used to execute a trade only for {@link #TRADE_MAX_AGE} after it was fetched.
 *     The service rejects an older one even if a UI let a confirmation dialog sit open.</li>
 * <li><b>Display:</b> a stored price is FRESH for {@link #DISPLAY_FRESH_MAX_AGE} after the last successful refresh,
 *     STALE after that, and UNAVAILABLE if no real price was ever obtained for it (the number shown is only the
 *     price the holding was bought at).</li>
 * </ul>
 */
public final class QuotePolicy {
    public static final Duration TRADE_MAX_AGE = Duration.ofSeconds(60);
    public static final Duration DISPLAY_FRESH_MAX_AGE = Duration.ofMinutes(15);

    public enum Status {
        FRESH("Fresh"), STALE("Stale"), UNAVAILABLE("Unavailable");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private QuotePolicy() {
    }

    /** Display status of a price last refreshed at {@code updatedAt} (null = never refreshed). */
    public static Status displayStatus(Instant updatedAt, Instant now) {
        if (updatedAt == null) {
            return Status.UNAVAILABLE;
        }
        Duration age = Duration.between(updatedAt, now);
        // a timestamp in the future (clock skew) cannot be trusted as fresh
        return !age.isNegative() && age.compareTo(DISPLAY_FRESH_MAX_AGE) <= 0 ? Status.FRESH : Status.STALE;
    }

    /** Short text such as "Fresh (3 min ago)", "Stale (2 h ago)" or "Unavailable (never refreshed)". */
    public static String describe(Instant updatedAt, Instant now) {
        Status s = displayStatus(updatedAt, now);
        if (updatedAt == null) {
            return s.label() + " (never refreshed)";
        }
        long mins = Math.max(0, Duration.between(updatedAt, now).toMinutes());
        String age = mins < 1 ? "just now" : mins < 60 ? mins + " min ago" : mins < 48 * 60 ? (mins / 60) + " h ago" : (mins / 1440) + " d ago";
        return s.label() + " (" + age + ")";
    }

    /**
     * @throws TradeBlockedException (STALE_QUOTE) if the quote is too old to trade on, or its time is in the future
     */
    public static void requireTradable(Quote quote, Instant now) {
        Duration age = Duration.between(quote.getFetchedAt(), now);
        if (age.isNegative() || age.compareTo(TRADE_MAX_AGE) > 0) {
            throw new TradeBlockedException(TradeBlockedException.Reason.STALE_QUOTE,
                    "The quote for " + quote.getSymbol() + " is " + (age.isNegative() ? "from the future" : age.toSeconds() + " s old")
                            + "; quotes older than " + TRADE_MAX_AGE.toSeconds() + " s cannot be traded on. Get a new quote.");
        }
    }
}

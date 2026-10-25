package com.portfolio.service;

/**
 * A trade was refused before anything changed because the price, its currency or the exchange rate cannot be trusted.
 * Nothing was written: no wallet, holding or ledger row.
 */
public class TradeBlockedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** The quote is older than {@link QuotePolicy#TRADE_MAX_AGE}. */
        STALE_QUOTE,
        /** The provider did not say what currency the price is in. */
        QUOTE_CURRENCY_UNKNOWN,
        /** The quote's currency differs from the holding's currency (no cross-currency price mixing). */
        CURRENCY_MISMATCH,
        /** Money would need converting between currencies but no valid, fresh exchange rate is available. */
        RATE_UNAVAILABLE
    }

    private final Reason reason;

    public TradeBlockedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TradeBlockedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

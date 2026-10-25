package com.portfolio.service;

/**
 * Thrown when a price could not be obtained. Distinct from "symbol not found", which getStock
 * reports as null. {@link #getReason()} says why, so callers can show an accurate message and
 * stop a batch early when continuing would be pointless.
 */
public class PriceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** No API key configured. */
        NO_KEY(true),
        /** The provider rejected the key (invalid/missing/demo key, or HTTP 401/403). */
        INVALID_KEY(true),
        /** Quota or call-frequency limit reached (provider message or HTTP 429). */
        RATE_LIMIT(true),
        /** Non-success HTTP status. */
        HTTP_ERROR(false),
        /** Connection failure or timeout. */
        NETWORK(false),
        /** Unparseable or unexpected response, or an invalid price. */
        BAD_RESPONSE(false),
        /** An unrecognised provider message with no quote. */
        PROVIDER_MESSAGE(false),
        INTERRUPTED(false),
        /** The provider has no such symbol (raised by the convenience methods that cannot return null). */
        SYMBOL_NOT_FOUND(false),
        /** This provider does not offer the requested data. */
        UNSUPPORTED(false);

        private final boolean batchFatal;

        Reason(boolean batchFatal) {
            this.batchFatal = batchFatal;
        }

        /** True when every further request in the same batch would fail the same way. */
        public boolean isBatchFatal() {
            return batchFatal;
        }
    }

    private final Reason reason;

    public PriceUnavailableException(String message) {
        this(Reason.BAD_RESPONSE, message, null);
    }

    public PriceUnavailableException(String message, Throwable cause) {
        this(Reason.BAD_RESPONSE, message, cause);
    }

    public PriceUnavailableException(Reason reason, String message) {
        this(reason, message, null);
    }

    public PriceUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

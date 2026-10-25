package com.portfolio.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Exchange-aware symbols. A stored symbol is whatever the user (or the catalogue) entered, so existing holdings keep working:
 * {@code RELIANCE.NS} / {@code RELIANCE.NSE} are NSE listings, {@code RELIANCE.BSE} / {@code RELIANCE.BO} are BSE listings, and a
 * bare {@code AAPL} is passed to every provider unchanged, because a bare ticker is ambiguous and is never given a guessed suffix.
 *
 * <p>Each provider spells exchanges differently, so {@link #forProvider} translates a listing into the form that provider
 * documents, or says that the provider does not cover that exchange (an honest "unsupported", not a different listing).
 */
public final class SymbolResolver {
    public enum Exchange { NSE, BSE, UNSPECIFIED }

    public enum Provider { ALPHA_VANTAGE, TWELVE_DATA, FINNHUB }

    /** A ticker and the exchange it is listed on (UNSPECIFIED when the symbol carried no exchange). */
    public record Instrument(String ticker, Exchange exchange) {
        /** The symbol as stored in portfolios: suffix form for NSE/BSE, bare otherwise. */
        public String canonical() {
            return switch (exchange) {
                case NSE -> ticker + ".NS";
                case BSE -> ticker + ".BSE";
                case UNSPECIFIED -> ticker;
            };
        }
    }

    private static final Pattern VALID = Pattern.compile("[A-Z0-9&-]{1,20}");

    private SymbolResolver() {
    }

    /**
     * Reads a stored or typed symbol.
     *
     * @throws IllegalArgumentException for an empty or malformed symbol
     */
    public static Instrument parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        Exchange exchange = Exchange.UNSPECIFIED;
        int cut = Math.max(s.lastIndexOf('.'), s.lastIndexOf(':'));
        if (cut > 0 && cut < s.length() - 1) {
            String suffix = s.substring(cut + 1);
            Exchange found = switch (suffix) {
                case "NS", "NSE" -> Exchange.NSE;
                case "BSE", "BO" -> Exchange.BSE;
                default -> null;
            };
            if (found != null) {
                exchange = found;
                s = s.substring(0, cut);
            }
        }
        if (!VALID.matcher(s).matches()) {
            throw new IllegalArgumentException("Not a valid symbol: " + raw.trim());
        }
        return new Instrument(s, exchange);
    }

    /**
     * The symbol to send to {@code provider}, or empty if the provider does not cover that listing.
     * Alpha Vantage lists BSE as {@code .BSE}; Twelve Data takes {@code TICKER:EXCHANGE}; Finnhub uses {@code .NS} / {@code .BO}.
     * Alpha Vantage's documented India coverage is BSE only, so an NSE listing is reported as unsupported there.
     */
    public static Optional<String> forProvider(Instrument i, Provider provider) {
        if (i.exchange() == Exchange.UNSPECIFIED) {
            return Optional.of(i.ticker());
        }
        boolean nse = i.exchange() == Exchange.NSE;
        return switch (provider) {
            case ALPHA_VANTAGE -> nse ? Optional.empty() : Optional.of(i.ticker() + ".BSE");
            case TWELVE_DATA -> Optional.of(i.ticker() + ":" + (nse ? "NSE" : "BSE"));
            case FINNHUB -> Optional.of(i.ticker() + (nse ? ".NS" : ".BO"));
        };
    }

    /** Human description for messages and tooltips. */
    public static String describe(Instrument i) {
        return switch (i.exchange()) {
            case NSE -> i.ticker() + " (NSE)";
            case BSE -> i.ticker() + " (BSE)";
            case UNSPECIFIED -> i.ticker() + " (exchange not specified)";
        };
    }
}

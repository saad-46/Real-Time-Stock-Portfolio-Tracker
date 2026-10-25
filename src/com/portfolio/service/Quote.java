package com.portfolio.service;

import com.portfolio.model.Stock;

import java.time.Instant;

/**
 * A live price a provider actually reported, with the currency it is quoted in and when it was obtained. Trades that
 * execute at a quote are checked against {@link QuotePolicy} by the trading service itself.
 */
public final class Quote {
    private final String symbol;
    private final double price;
    private final String currency;
    private final Instant fetchedAt;

    public Quote(String symbol, double price, String currency, Instant fetchedAt) {
        this.symbol = symbol;
        this.price = price;
        this.currency = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase();
        this.fetchedAt = fetchedAt;
    }

    /** Wrap a provider's Stock, stamping the time the quote was obtained. */
    public static Quote of(Stock stock, Instant fetchedAt) {
        return new Quote(stock.getSymbol(), stock.getCurrentPrice(), stock.getCurrency(), fetchedAt);
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    /** Quote currency (upper case ISO code), or null if the provider did not say. */
    public String getCurrency() {
        return currency;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    @Override
    public String toString() {
        return String.format("%s %.2f %s @ %s", symbol, price, currency == null ? "(currency unknown)" : currency, fetchedAt);
    }
}

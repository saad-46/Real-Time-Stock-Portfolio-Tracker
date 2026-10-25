package com.portfolio.service;

import com.portfolio.model.Stock;

/**
 * StockPriceService defines how live stock prices are obtained. Implementations are interchangeable (Alpha Vantage,
 * Finnhub, Twelve Data, or the failover chain) without changing the rest of the code.
 *
 * NO-FAKE-PRICES RULE: an implementation either returns a price the provider actually reported, or fails. It must
 * never invent, randomise, default or reuse a price. "Symbol does not exist" is reported as null from
 * {@link #getStock}; "could not get a price" is a {@link PriceUnavailableException}.
 */
public interface StockPriceService {
    /**
     * Fetch a stock and its current price from the provider.
     *
     * @return the live Stock, or null if the provider says the symbol does not exist
     * @throws PriceUnavailableException if no live price could be obtained
     */
    Stock getStock(String symbol);

    /**
     * Check if a stock symbol is valid. Returns true if the stock exists, false if the provider says it does not.
     *
     * @throws PriceUnavailableException if that could not be determined
     */
    boolean isValidStock(String symbol);

    /**
     * Current price of a symbol.
     *
     * @throws PriceUnavailableException if the symbol is unknown or no live price could be obtained
     */
    default double getCurrentPrice(String symbol) {
        Stock stock = getStock(symbol);
        if (stock == null) {
            throw new PriceUnavailableException(PriceUnavailableException.Reason.SYMBOL_NOT_FOUND,
                    "Unknown symbol: " + symbol);
        }
        return stock.getCurrentPrice();
    }

    /**
     * Refresh a stock object in place with the latest live quote (price, daily change, high/low where the
     * provider reports them). The object is left unchanged if no live quote is available.
     *
     * @throws PriceUnavailableException if the symbol is unknown or no live price could be obtained
     */
    default void updateStockPrice(Stock stock) {
        Stock live = getStock(stock.getSymbol());
        if (live == null) {
            throw new PriceUnavailableException(PriceUnavailableException.Reason.SYMBOL_NOT_FOUND,
                    "Unknown symbol: " + stock.getSymbol());
        }
        stock.setCurrentPrice(live.getCurrentPrice());
        stock.setChangePercent(live.getChangePercent());
        if (live.getHigh24h() > 0) {
            stock.setHigh24h(live.getHigh24h());
        }
        if (live.getLow24h() > 0) {
            stock.setLow24h(live.getLow24h());
        }
    }

    /**
     * Historical daily prices as the provider's raw JSON.
     *
     * @throws PriceUnavailableException if this provider offers no history or the call failed
     */
    default String getHistoricalData(String symbol) {
        throw new PriceUnavailableException(PriceUnavailableException.Reason.UNSUPPORTED,
                "This price provider does not offer historical data");
    }

    /**
     * Daily history asking for at least {@code days} calendar days when the provider can supply them. A provider that cannot
     * returns what it has (the caller judges the real span); the default is {@link #getHistoricalData(String)}.
     */
    default String getHistoricalData(String symbol, int days) {
        return getHistoricalData(symbol);
    }
}

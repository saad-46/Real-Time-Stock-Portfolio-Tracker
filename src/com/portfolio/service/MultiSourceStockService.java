package com.portfolio.service;

import com.portfolio.model.Stock;

/**
 * MultiSourceStockService - Failover aggregator for stock price APIs.
 * Tries sources in order: Finnhub → Twelve Data → Alpha Vantage.
 * If one API fails or rate-limits, automatically tries the next.
 * 
 * This gives access to virtually unlimited stock symbols across all major
 * exchanges.
 */
public class MultiSourceStockService implements StockPriceService {

    private final FinnhubService finnhubService;
    private final TwelveDataService twelveDataService;
    private final AlphaVantageService alphaVantageService;

    public MultiSourceStockService() {
        this.finnhubService = new FinnhubService();
        this.twelveDataService = new TwelveDataService();
        this.alphaVantageService = new AlphaVantageService();
    }

    /**
     * Gets the current price using failover chain:
     * Finnhub (60/min) → Twelve Data (800/day) → Alpha Vantage (25/day)
     */
    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        // Try 1: Finnhub (Primary)
        try {
            return finnhubService.getCurrentPrice(symbol);
        } catch (Exception ignored) {
        }

        // Try 2: Twelve Data (Secondary)
        try {
            return twelveDataService.getCurrentPrice(symbol);
        } catch (Exception ignored) {
        }

        // Try 3: Alpha Vantage (Last resort)
        try {
            return alphaVantageService.getCurrentPrice(symbol);
        } catch (Exception ignored) {
        }

        throw new Exception("All stock APIs failed for " + symbol + ". Please try again later.");
    }

    /**
     * Updates stock price using failover chain.
     */
    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        // Try 1: Finnhub
        try {
            finnhubService.updateStockPrice(stock);
            return;
        } catch (Exception ignored) {
        }

        // Try 2: Twelve Data
        try {
            twelveDataService.updateStockPrice(stock);
            return;
        } catch (Exception ignored) {
        }

        // Try 3: Alpha Vantage
        try {
            alphaVantageService.updateStockPrice(stock);
            return;
        } catch (Exception ignored) {
        }

        throw new Exception("All stock APIs failed to update " + stock.getSymbol());
    }

    /**
     * Gets historical data. Tries Twelve Data first (better historical), then Alpha
     * Vantage.
     */
    public String getHistoricalData(String symbol) throws Exception {
        // Try Twelve Data first (better for historical data)
        try {
            return twelveDataService.getHistoricalData(symbol, 30);
        } catch (Exception e) {
            System.out.println("⚠️ TwelveData historical failed for " + symbol);
        }

        // Fallback to Alpha Vantage
        try {
            return alphaVantageService.getHistoricalData(symbol);
        } catch (Exception e) {
            System.out.println("⚠️ AlphaVantage historical failed for " + symbol);
        }

        throw new Exception("Historical data unavailable for " + symbol);
    }

    /**
     * Search for stocks. Uses Finnhub (broader coverage).
     */
    public String searchStocks(String query) throws Exception {
        return finnhubService.searchStocks(query);
    }

    // Expose individual services for advanced use
    public FinnhubService getFinnhubService() {
        return finnhubService;
    }

    public TwelveDataService getTwelveDataService() {
        return twelveDataService;
    }

    public AlphaVantageService getAlphaVantageService() {
        return alphaVantageService;
    }
}

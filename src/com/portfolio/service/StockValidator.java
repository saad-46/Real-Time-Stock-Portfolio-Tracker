package com.portfolio.service;

import com.portfolio.model.Stock;

/**
 * StockValidator validates stock symbols before adding them to the portfolio.
 * Prevents adding invalid stocks that don't exist.
 */
public class StockValidator {
    // Service for checking if stock is valid
    private StockPriceService priceService;

    /**
     * Constructor initializes with a price service.
     */
    public StockValidator() {
        this.priceService = new AlphaVantageService();
    }

    /**
     * Validate a stock symbol.
     * Returns the Stock object if valid, null if invalid.
     *
     * Example:
     * Stock stock = validator.validate("AAPL");
     * if (stock != null) {
     *     System.out.println("Valid stock: " + stock);
     * } else {
     *     System.out.println("Invalid stock");
     * }
     */
    public Stock validate(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        try {
            // Fetch the stock from API
            Stock stock = priceService.getStock(symbol.toUpperCase());
            return stock;
        } catch (Exception e) {
            System.out.println("Error validating stock: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if stock symbol is valid (exists in API).
     *
     * Example:
     * if (validator.isValid("AAPL")) {
     *     // Add stock to portfolio
     * }
     */
    public boolean isValid(String symbol) {
        return validate(symbol) != null;
    }
}

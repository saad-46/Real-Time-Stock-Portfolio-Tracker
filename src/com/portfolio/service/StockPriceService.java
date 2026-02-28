package com.portfolio.service; // This file belongs to the "service" folder - services do work/actions

import com.portfolio.model.Stock;  // Import the Stock class so we can use it

// This is an INTERFACE - it's like a contract that says "any price service must have these methods"
// Think of it like a menu at a restaurant - it lists what's available, but doesn't cook the food
public interface StockPriceService {
    
    /**
     * Gets the current price for a stock symbol from the internet
     * Example: getCurrentPrice("AAPL") might return 278.12
     * 
     * @param symbol The stock symbol (ex: "AAPL", "TSLA")
     * @return The current price (ex: 278.12)
     * @throws Exception if something goes wrong (ex: no internet, invalid symbol)
     */
    double getCurrentPrice(String symbol) throws Exception;

    /**
     * Updates a stock object with the latest price from the internet
     * Example: If Apple stock is $150, this will fetch the real price ($278) and update it
     * 
     * @param stock The stock to update (ex: Apple stock object)
     * @throws Exception if something goes wrong
     */
    void updateStockPrice(Stock stock) throws Exception;
}

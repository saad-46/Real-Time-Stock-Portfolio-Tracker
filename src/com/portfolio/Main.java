package com.portfolio; // This is the main package for the entire application

import com.portfolio.service.*;  // Import all service classes (the * means "everything")

/**
 * Main class - This is where the program starts running
 * Think of this as the "front door" of your application
 */
public class Main {

    // The main method - Java always starts here when you run the program
    // "throws Exception" means if something goes wrong, Java will show the error
    public static void main(String[] args) throws Exception {

        // Step 1: Create a price service to get real stock prices from the internet
        // This uses Alpha Vantage API to fetch live prices
        StockPriceService priceService =
            new AlphaVantageService();  // Create the service that talks to Alpha Vantage

        // Step 2: Create a portfolio manager and give it the price service
        // This will manage all your stocks and transactions
        PortfolioService portfolio =
            new PortfolioService(priceService);  // Create portfolio manager

        // Step 3: Simulate buying stocks
        // buy(symbol, quantity, price) - records a stock purchase
        portfolio.buy("AAPL", 10, 150);  // Buy 10 Apple shares at $150 each = $1,500 invested
        portfolio.buy("TSLA", 5, 200);   // Buy 5 Tesla shares at $200 each = $1,000 invested

        // Step 4: Show initial investment summary (before updating prices)
        System.out.println("\n💰 Initial Investment Summary:");
        System.out.println("Total Investment: $" + portfolio.calculateTotalInvestment());  // Shows $2,500
        System.out.println("Current Value: $" + portfolio.calculateCurrentValue());        // Shows $2,500 (same as investment)
        System.out.println("Profit/Loss: $" + portfolio.calculateProfitLoss());            // Shows $0 (no change yet)

        // Step 5: Fetch real, live prices from the internet
        System.out.println("\n🔄 Fetching live prices from Alpha Vantage API...");
        portfolio.updateAllPrices();  // This contacts Alpha Vantage and updates all stock prices

        // Step 6: Show updated portfolio summary (after getting real prices)
        System.out.println("\n💰 Updated Portfolio Summary:");
        System.out.println("Total Investment: $" + portfolio.calculateTotalInvestment());  // Still $2,500 (what you paid)
        System.out.println("Current Value: $" + portfolio.calculateCurrentValue());        // Now shows real value (ex: $3,780)
        System.out.println("Profit/Loss: $" + portfolio.calculateProfitLoss());            // Shows profit/loss (ex: +$1,280)
    }
}

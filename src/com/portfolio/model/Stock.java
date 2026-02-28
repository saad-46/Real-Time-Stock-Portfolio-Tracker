package com.portfolio.model; // This file belongs to the "model" folder - models represent real-world things

// This class represents a single stock (like Apple, Tesla, etc.)
public class Stock {
    // Private variables - only this class can directly access them
    private String symbol;        // Stock ticker symbol, ex: "AAPL" for Apple
    private String name;          // Full company name, ex: "Apple Inc."
    private double currentPrice;  // Current price per share, ex: 150.50

    // Constructor - runs when you create a new Stock object
    // Example: new Stock("AAPL", "Apple Inc.")
    public Stock(String symbol, String name) {
        this.symbol = symbol;     // Store the symbol (ex: "AAPL")
        this.name = name;         // Store the name (ex: "Apple Inc.")
    }

    // Getter method - returns the stock symbol
    // Example: stock.getSymbol() returns "AAPL"
    public String getSymbol() {
        return symbol;            // Return the symbol we stored
    }

    // Getter method - returns the company name
    // Example: stock.getName() returns "Apple Inc."
    public String getName() {
        return name;              // Return the name we stored
    }

    // Getter method - returns the current price
    // Example: stock.getCurrentPrice() returns 150.50
    public double getCurrentPrice() {
        return currentPrice;      // Return the current price
    }

    // Setter method - updates the current price
    // Example: stock.setCurrentPrice(278.12) sets price to $278.12
    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;  // Update the price with new value
    }

    // Override toString - this runs when you print the stock object
    // Example: System.out.println(stock) shows "AAPL - Apple Inc. ($150.50)"
    @Override
    public String toString() {
        return symbol + " - " + name + " ($" + currentPrice + ")";  // Format: "AAPL - Apple Inc. ($150.50)"
    }
}

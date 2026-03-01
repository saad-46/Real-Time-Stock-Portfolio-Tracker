package com.portfolio.model; // This file belongs to the "model" folder - models represent real-world things

// This class represents a single stock (like Apple, Tesla, etc.)
public class Stock {
    // Private variables - only this class can directly access them
    private String symbol;
    private String name;
    private double currentPrice;
    private double changePercent;
    private String sector;
    private String marketCap; // Small, Mid, Large
    private String riskLevel; // Low, Medium, High

    // Constructor - runs when you create a new Stock object
    // Example: new Stock("AAPL", "Apple Inc.")
    public Stock(String symbol, String name) {
        this.symbol = symbol; // Store the symbol (ex: "AAPL")
        this.name = name; // Store the name (ex: "Apple Inc.")
    }

    // Getter method - returns the stock symbol
    // Example: stock.getSymbol() returns "AAPL"
    public String getSymbol() {
        return symbol; // Return the symbol we stored
    }

    // Getter method - returns the company name
    // Example: stock.getName() returns "Apple Inc."
    public String getName() {
        return name; // Return the name we stored
    }

    // Getter method - returns the current price
    // Example: stock.getCurrentPrice() returns 150.50
    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(String marketCap) {
        this.marketCap = marketCap;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    @Override
    public String toString() {
        return symbol + " - " + name + " (" + sector + ")";
    }
}

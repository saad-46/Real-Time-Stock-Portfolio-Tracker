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
    private double high24h;
    private double low24h;
    // Currency the price is quoted in (ISO code, e.g. "USD"); null = the provider did not say, which blocks trading
    private String currency;

    // Constructor - runs when you create a new Stock object
    // Example: new Stock("AAPL", "Apple Inc.")
    public Stock(String symbol, String name) {
        this.symbol = symbol; // Store the symbol (ex: "AAPL")
        this.name = name; // Store the name (ex: "Apple Inc.")
    }

    /** Create a stock with a known price. Example: new Stock("AAPL", "Apple Inc.", 150.50) */
    public Stock(String symbol, String name, double currentPrice) {
        this(symbol, name);
        this.currentPrice = currentPrice;
    }

    // Getter method - returns the stock symbol
    // Example: stock.getSymbol() returns "AAPL"
    public String getSymbol() {
        return symbol; // Return the symbol we stored
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    // Getter method - returns the company name
    // Example: stock.getName() returns "Apple Inc."
    public String getName() {
        return name; // Return the name we stored
    }

    public void setName(String name) {
        this.name = name;
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

    /** Currency of {@link #getCurrentPrice()} as reported or implied by the provider; null if unknown. */
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase();
    }

    public double getHigh24h() {
        return high24h;
    }

    public void setHigh24h(double high24h) {
        this.high24h = high24h;
    }

    public double getLow24h() {
        return low24h;
    }

    public void setLow24h(double low24h) {
        this.low24h = low24h;
    }

    @Override
    public String toString() {
        return symbol + " - " + name + " (" + sector + ")";
    }
}

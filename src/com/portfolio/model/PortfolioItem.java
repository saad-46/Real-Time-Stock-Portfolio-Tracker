package com.portfolio.model;

/**
 * PortfolioItem represents one holding: a stock, how many shares, and what was paid per share.
 *
 * It exposes both access styles used by the application: the Stock-wrapping accessors
 * ({@link #getStock()}, {@link #getTotalValue()}, {@link #getGainLoss()}) used by the dashboard, and the flat
 * accessors ({@link #getSymbol()}, {@link #getCurrentPrice()}, {@link #getProfitLoss()}) used by the trade and
 * ledger code. Both read the same underlying fields.
 */
public class PortfolioItem {
    // Unique ID in database (0 = not saved yet)
    private int id;
    private final Stock stock; // The stock you own (symbol, name, live price, sector, ...)
    private int quantity; // How many shares you own
    private double purchasePrice; // Weighted-average price you paid per share
    private String originalCurrency; // E.g., "USD"
    // When the current price was last refreshed from a provider; null = never refreshed (price is the purchase price)
    private volatile java.time.Instant priceUpdatedAt;

    public PortfolioItem(Stock stock, int quantity, double purchasePrice, String originalCurrency) {
        this.stock = stock;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.originalCurrency = originalCurrency != null ? originalCurrency : "USD";
    }

    // Constructor without currency for backward compatibility
    public PortfolioItem(Stock stock, int quantity, double purchasePrice) {
        this(stock, quantity, purchasePrice, "USD");
    }

    /** Example: new PortfolioItem("AAPL", "Apple Inc.", 10, 150.0, 155.0) */
    public PortfolioItem(String symbol, String name, int quantity, double purchasePrice, double currentPrice) {
        this(new Stock(symbol, name, currentPrice), quantity, purchasePrice, "USD");
    }

    /** Used when loading from the database. */
    public PortfolioItem(int id, String symbol, String name, int quantity, double purchasePrice, double currentPrice) {
        this(symbol, name, quantity, purchasePrice, currentPrice);
        this.id = id;
    }

    // ----- Stock-wrapping accessors -----

    public Stock getStock() {
        return stock;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public void setOriginalCurrency(String originalCurrency) {
        this.originalCurrency = originalCurrency != null ? originalCurrency : "USD";
    }

    /** Current price x shares. */
    public double getTotalValue() {
        return stock.getCurrentPrice() * quantity;
    }

    /** (Current - purchase) x quantity. */
    public double getGainLoss() {
        return (stock.getCurrentPrice() - purchasePrice) * quantity;
    }

    public double getGainLossPercent() {
        if (purchasePrice == 0) return 0;
        return ((stock.getCurrentPrice() - purchasePrice) / purchasePrice) * 100;
    }

    // ----- Flat accessors -----

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return stock.getSymbol();
    }

    public void setSymbol(String symbol) {
        stock.setSymbol(symbol);
    }

    public String getName() {
        return stock.getName();
    }

    public void setName(String name) {
        stock.setName(name);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public double getCurrentPrice() {
        return stock.getCurrentPrice();
    }

    public void setCurrentPrice(double currentPrice) {
        stock.setCurrentPrice(currentPrice);
    }

    public java.time.Instant getPriceUpdatedAt() {
        return priceUpdatedAt;
    }

    public void setPriceUpdatedAt(java.time.Instant priceUpdatedAt) {
        this.priceUpdatedAt = priceUpdatedAt;
    }

    /** What you paid: quantity x purchasePrice. */
    public double getTotalInvestment() {
        return quantity * purchasePrice;
    }

    /** What it is worth now: quantity x currentPrice. */
    public double getCurrentValue() {
        return quantity * stock.getCurrentPrice();
    }

    /** currentValue - investment. Positive = profit, negative = loss. */
    public double getProfitLoss() {
        return getCurrentValue() - getTotalInvestment();
    }

    /** (profitLoss / investment) x 100. */
    public double getReturnPercentage() {
        if (getTotalInvestment() == 0) return 0;
        return (getProfitLoss() / getTotalInvestment()) * 100;
    }

    @Override
    public String toString() {
        return "PortfolioItem [symbol=" + stock.getSymbol() + ", quantity=" + quantity + ", purchasePrice="
                + purchasePrice + ", currentPrice=" + stock.getCurrentPrice() + "]";
    }
}

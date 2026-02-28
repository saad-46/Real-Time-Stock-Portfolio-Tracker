package com.portfolio.model; // This file belongs to the "model" folder

// This class represents one holding in your portfolio
// Example: You own 10 shares of Apple that you bought at $150 each
public class PortfolioItem {
    // Private variables - the data for this portfolio item
    private Stock stock;           // The stock you own (ex: Apple)
    private int quantity;          // How many shares you own (ex: 10 shares)
    private double purchasePrice;  // Price you paid per share (ex: $150)

    // Constructor - creates a new portfolio item
    // Example: new PortfolioItem(appleStock, 10, 150.0) means "I own 10 Apple shares bought at $150 each"
    public PortfolioItem(Stock stock, int quantity, double purchasePrice) {
        this.stock = stock;              // Store which stock this is (ex: Apple)
        this.quantity = quantity;        // Store how many shares (ex: 10)
        this.purchasePrice = purchasePrice;  // Store purchase price (ex: $150)
    }

    // Getter - returns the stock object
    // Example: item.getStock() returns the Apple stock object
    public Stock getStock() {
        return stock;  // Return the stock we're holding
    }

    // Getter - returns how many shares you own
    // Example: item.getQuantity() returns 10
    public int getQuantity() {
        return quantity;  // Return number of shares
    }

    // Setter - updates how many shares you own
    // Example: item.setQuantity(5) sets quantity to 5
    public void setQuantity(int quantity) {
        this.quantity = quantity;  // Update number of shares
    }

    // Getter - returns the price you paid per share
    // Example: item.getPurchasePrice() returns 150.0
    public double getPurchasePrice() {
        return purchasePrice;  // Return what you paid per share
    }

    // Calculates total current value of this holding
    // Example: 10 shares × $278 current price = $2,780
    public double getTotalValue() {
        return stock.getCurrentPrice() * quantity;  // Current price × number of shares
    }

    // Calculates your profit or loss on this holding
    // Example: (Current $278 - Bought at $150) × 10 shares = $1,280 profit
    public double getGainLoss() {
        return (stock.getCurrentPrice() - purchasePrice) * quantity;  // (Current - Purchase) × Quantity
    }

    // Override toString - formats the item for display
    // Example: "AAPL x10 @ $150.0 (Current: $278.12)"
    @Override
    public String toString() {
        return stock.getSymbol() + " x" + quantity +      // "AAPL x10"
               " @ $" + purchasePrice +                   // "@ $150.0"
               " (Current: $" + stock.getCurrentPrice() + ")";  // "(Current: $278.12)"
    }
}

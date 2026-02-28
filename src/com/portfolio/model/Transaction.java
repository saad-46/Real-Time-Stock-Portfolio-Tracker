package com.portfolio.model; // This file belongs to the "model" folder

import java.time.LocalDateTime;  // Import Java's date/time class to record when transactions happen

// This class represents a buy or sell transaction
// Example: "Bought 10 shares of Apple at $150 on Jan 15, 2024"
public class Transaction {
    // Private variables - the transaction details
    private String symbol;              // Stock symbol (ex: "AAPL")
    private String type;                // Transaction type: "BUY" or "SELL"
    private int quantity;               // How many shares (ex: 10)
    private double price;               // Price per share (ex: $150)
    private LocalDateTime timestamp;    // When this happened (ex: 2024-01-15 10:30:00)

    // Constructor - creates a new transaction record
    // Example: new Transaction("AAPL", "BUY", 10, 150.0) means "Bought 10 Apple shares at $150"
    public Transaction(String symbol, String type, int quantity, double price) {
        this.symbol = symbol;           // Store stock symbol (ex: "AAPL")
        this.type = type;               // Store type (ex: "BUY")
        this.quantity = quantity;       // Store quantity (ex: 10)
        this.price = price;             // Store price (ex: $150)
        this.timestamp = LocalDateTime.now();  // Record current date/time automatically
    }

    // Getter - returns the stock symbol
    // Example: transaction.getSymbol() returns "AAPL"
    public String getSymbol() {
        return symbol;  // Return the stock symbol
    }

    // Getter - returns the transaction type
    // Example: transaction.getType() returns "BUY"
    public String getType() {
        return type;  // Return "BUY" or "SELL"
    }

    // Getter - returns how many shares
    // Example: transaction.getQuantity() returns 10
    public int getQuantity() {
        return quantity;  // Return number of shares
    }

    // Getter - returns the price per share
    // Example: transaction.getPrice() returns 150.0
    public double getPrice() {
        return price;  // Return the price
    }

    // Getter - returns when this transaction happened
    // Example: transaction.getTimestamp() returns "2024-01-15T10:30:00"
    public LocalDateTime getTimestamp() {
        return timestamp;  // Return the date/time
    }

    // Override toString - formats the transaction for display
    // Example: "BUY 10 AAPL @ $150.0 on 2024-01-15T10:30:00"
    @Override
    public String toString() {
        return type + " " + quantity + " " + symbol +     // "BUY 10 AAPL"
               " @ $" + price + " on " + timestamp;       // "@ $150.0 on 2024-01-15T10:30:00"
    }
}

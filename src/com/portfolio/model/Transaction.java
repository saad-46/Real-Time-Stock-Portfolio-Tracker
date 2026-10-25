package com.portfolio.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Transaction class represents a buy or sell transaction in the portfolio.
 * Tracks the history of all stock trades.
 */
public class Transaction {
    // Unique ID in database
    private int id;
    // Stock symbol (ex: "AAPL")
    private String symbol;
    // Type of transaction: "BUY" or "SELL"
    private String type;
    // Number of shares traded
    private int quantity;
    // Price per share at time of transaction
    private double price;
    // When the transaction happened
    private LocalDateTime timestamp;
    // Realized profit/loss for SELL transactions; null for BUY
    private Double realizedPl;

    /**
     * Constructor for creating a new transaction.
     * Example: Transaction buy = new Transaction("AAPL", "BUY", 10, 150.0);
     */
    public Transaction(String symbol, String type, int quantity, double price) {
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with timestamp (used when loading from database).
     */
    public Transaction(int id, String symbol, String type, int quantity, double price, LocalDateTime timestamp) {
        this.id = id;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    /** Loading from database, including realized P/L (null for BUY). */
    public Transaction(int id, String symbol, String type, int quantity, double price, LocalDateTime timestamp, Double realizedPl) {
        this(id, symbol, type, quantity, price, timestamp);
        this.realizedPl = realizedPl;
    }

    /** New transaction carrying its realized P/L (SELL). */
    public Transaction(String symbol, String type, int quantity, double price, Double realizedPl) {
        this(symbol, type, quantity, price);
        this.realizedPl = realizedPl;
    }

    /** New transaction with an explicit time (lets the service use an injectable clock). */
    public Transaction(String symbol, String type, int quantity, double price, Double realizedPl, LocalDateTime timestamp) {
        this(symbol, type, quantity, price, realizedPl);
        this.timestamp = timestamp;
    }

    public Double getRealizedPl() {
        return realizedPl;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Calculate total value of transaction.
     * Formula: quantity * price
     */
    public double getTransactionValue() {
        return quantity * price;
    }

    /**
     * Get formatted timestamp string.
     * Example: "2024-01-15 10:30:45"
     */
    public String getFormattedTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return timestamp.format(formatter);
    }

    @Override
    public String toString() {
        return "Transaction [symbol=" + symbol + ", type=" + type + ", quantity=" + quantity + ", price=" + price
                + ", timestamp=" + timestamp + "]";
    }
}

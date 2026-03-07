package com.portfolio.model;

import java.time.LocalDateTime;

public class WalletTransaction {
    private int id;
    private String type; // DEPOSIT, WITHDRAW, BUY_STOCK, SELL_STOCK, TAX_PAYMENT
    private double amount;
    private double balanceAfter;
    private String description;
    private LocalDateTime timestamp;

    public WalletTransaction(int id, String type, double amount, double balanceAfter, String description,
            LocalDateTime timestamp) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.timestamp = timestamp;
    }

    public WalletTransaction(String type, double amount, double balanceAfter, String description) {
        this(0, type, amount, balanceAfter, description, LocalDateTime.now());
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}

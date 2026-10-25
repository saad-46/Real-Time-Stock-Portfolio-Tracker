package com.portfolio.service;

/**
 * Thrown when a purchase costs more than the wallet balance. The trade was rolled back as a whole: no holding,
 * ledger or wallet row changed.
 */
public class InsufficientFundsException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final double required;
    private final double available;

    public InsufficientFundsException(double required, double available) {
        super(String.format("Insufficient wallet balance: need %.2f but only %.2f is available", required, available));
        this.required = required;
        this.available = available;
    }

    public double getRequired() {
        return required;
    }

    public double getAvailable() {
        return available;
    }
}

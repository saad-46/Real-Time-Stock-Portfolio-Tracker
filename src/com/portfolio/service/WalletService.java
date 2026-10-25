package com.portfolio.service;

import com.portfolio.database.WalletDAO;
import com.portfolio.model.WalletTransaction;
import java.util.ArrayList;
import java.util.List;

/**
 * Wallet: cash balance used to pay for purchases and receive sale proceeds.
 *
 * The database is the source of truth. Deposits and withdrawals are single database transactions, and trades move
 * money inside the trade's own transaction (see PortfolioDAO.recordTrade); after any change this class reloads
 * from the database instead of adjusting its cached numbers by arithmetic.
 */
public class WalletService {
    private final WalletDAO walletDAO;
    private double balance;
    private double totalDeposited;
    private double totalWithdrawn;
    private String currency = "INR";
    private List<WalletTransaction> transactions = new ArrayList<>();

    public WalletService() {
        this.walletDAO = new WalletDAO();
        reload();
    }

    /** Re-read balance, totals and history from the database. */
    public synchronized void reload() {
        try {
            double[] data = walletDAO.getWalletData();
            this.balance = data[0];
            this.totalDeposited = data[1];
            this.totalWithdrawn = data[2];
            this.currency = walletDAO.getCurrency();
            this.transactions = walletDAO.loadAllTransactions();
        } catch (Exception e) {
            System.err.println("⚠️ Could not load wallet data: " + e.getMessage());
        }
    }

    /**
     * The one currency every wallet amount is in (ISO code, default INR for wallets that predate this setting).
     * Trades in any other currency are converted at a validated exchange rate or blocked.
     */
    public synchronized String getCurrency() {
        return currency;
    }

    /**
     * Set the wallet currency; only possible while the wallet is empty and has no history.
     *
     * @return false if the wallet already has money or history (nothing changed)
     */
    public synchronized boolean setCurrency(String newCurrency) {
        if (newCurrency == null || !newCurrency.trim().matches("[A-Za-z]{3}")) {
            return false;
        }
        try {
            boolean ok = walletDAO.setCurrencyIfPristine(newCurrency);
            reload();
            return ok;
        } catch (Exception e) {
            System.err.println("❌ Could not set the wallet currency: " + e.getMessage());
            return false;
        }
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized double getTotalDeposited() {
        return totalDeposited;
    }

    public synchronized double getTotalWithdrawn() {
        return totalWithdrawn;
    }

    /** Wallet history, newest first. */
    public synchronized List<WalletTransaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    /** Whole cents, half-up; NaN/infinite/huge values become 0 so they are rejected. */
    public static double cents(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || Math.abs(amount) > 1e12) {
            return 0;
        }
        return java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    public synchronized boolean addFunds(double rawAmount, String method) {
        double amount = cents(rawAmount);
        if (!(amount > 0)) {
            return false;
        }
        try {
            boolean ok = walletDAO.applyCashMovement(true, amount, "Added funds via " + method);
            if (ok) {
                System.out.println("✅ Added ₹" + amount + " to wallet via " + method);
            }
            reload();
            return ok;
        } catch (Exception e) {
            System.err.println("❌ Failed to add funds: " + e.getMessage());
            reload();
            return false;
        }
    }

    public synchronized boolean withdrawFunds(double rawAmount) {
        return withdrawFunds(rawAmount, "Withdrawal to bank account");
    }

    /** Withdraw whole cents with an honest description for the wallet history. */
    public synchronized boolean withdrawFunds(double rawAmount, String description) {
        double amount = cents(rawAmount);
        if (!(amount > 0) || amount > cents(balance)) {
            return false;
        }
        try {
            boolean ok = walletDAO.applyCashMovement(false, amount, description);
            if (ok) {
                System.out.println("✅ Withdrew ₹" + amount + " from wallet");
            }
            reload();
            return ok;
        } catch (Exception e) {
            System.err.println("❌ Failed to withdraw funds: " + e.getMessage());
            reload();
            return false;
        }
    }
}

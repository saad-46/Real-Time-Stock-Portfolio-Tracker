package com.portfolio.service;

import com.portfolio.database.WalletDAO;
import com.portfolio.model.WalletTransaction;
import java.util.List;

public class WalletService {
    private WalletDAO walletDAO;
    private double balance;
    private double totalDeposited;
    private double totalWithdrawn;
    private List<WalletTransaction> transactions;

    public WalletService() {
        this.walletDAO = new WalletDAO();
        loadWalletData();
    }

    private void loadWalletData() {
        try {
            double[] data = walletDAO.getWalletData();
            this.balance = data[0];
            this.totalDeposited = data[1];
            this.totalWithdrawn = data[2];
            this.transactions = walletDAO.loadAllTransactions();
        } catch (Exception e) {
            System.err.println("⚠️ Could not load wallet data: " + e.getMessage());
        }
    }

    public double getBalance() {
        return balance;
    }

    public double getTotalDeposited() {
        return totalDeposited;
    }

    public double getTotalWithdrawn() {
        return totalWithdrawn;
    }

    public List<WalletTransaction> getTransactions() {
        return transactions;
    }

    public boolean addFunds(double amount, String method) {
        if (amount <= 0)
            return false;

        this.balance += amount;
        this.totalDeposited += amount;

        try {
            walletDAO.updateWalletData(balance, totalDeposited, totalWithdrawn);

            String desc = "Added funds via " + method;
            WalletTransaction tx = new WalletTransaction("DEPOSIT", amount, balance, desc);
            walletDAO.logTransaction(tx);
            transactions.add(0, tx); // Add to top of list

            System.out.println("✅ Added ₹" + amount + " to wallet via " + method);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to add funds: " + e.getMessage());
            // Revert on failure
            this.balance -= amount;
            this.totalDeposited -= amount;
            return false;
        }
    }

    public boolean withdrawFunds(double amount) {
        if (amount <= 0 || amount > balance)
            return false;

        this.balance -= amount;
        this.totalWithdrawn += amount;

        try {
            walletDAO.updateWalletData(balance, totalDeposited, totalWithdrawn);

            String desc = "Withdrawal to bank account";
            WalletTransaction tx = new WalletTransaction("WITHDRAW", amount, balance, desc);
            walletDAO.logTransaction(tx);
            transactions.add(0, tx); // Add to top of list

            System.out.println("✅ Withdrew ₹" + amount + " from wallet");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to withdraw funds: " + e.getMessage());
            // Revert
            this.balance += amount;
            this.totalWithdrawn -= amount;
            return false;
        }
    }

    public boolean processStockPurchase(double totalCost, String symbol, int quantity) {
        if (totalCost > balance)
            return false;

        this.balance -= totalCost;

        try {
            walletDAO.updateWalletData(balance, totalDeposited, totalWithdrawn);

            String desc = "Bought " + quantity + " shares of " + symbol;
            WalletTransaction tx = new WalletTransaction("STOCK BUY", totalCost, balance, desc);
            walletDAO.logTransaction(tx);
            transactions.add(0, tx);

            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to process stock purchase in wallet: " + e.getMessage());
            this.balance += totalCost;
            return false;
        }
    }

    public void processStockSale(double totalProceeds, String symbol, int quantity) {
        this.balance += totalProceeds;

        try {
            walletDAO.updateWalletData(balance, totalDeposited, totalWithdrawn);

            String desc = "Sold " + quantity + " shares of " + symbol;
            WalletTransaction tx = new WalletTransaction("STOCK SELL", totalProceeds, balance, desc);
            walletDAO.logTransaction(tx);
            transactions.add(0, tx);
        } catch (Exception e) {
            System.err.println("❌ Failed to process stock sale in wallet: " + e.getMessage());
            this.balance -= totalProceeds;
        }
    }
}

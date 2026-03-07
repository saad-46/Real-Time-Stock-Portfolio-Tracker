package com.portfolio; // This is the main package for the entire application

import com.portfolio.service.*;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;

/**
 * Main class - This is where the program starts running
 * Think of this as the "front door" of your application
 */
public class Main {

    // The main method - Java always starts here when you run the program
    // "throws Exception" means if something goes wrong, Java will show the error
    public static void main(String[] args) throws Exception {

        // Step 1: Create a multi-source price service for maximum stock coverage
        // Uses Finnhub (primary) → Twelve Data → Alpha Vantage failover chain
        StockPriceService priceService = new MultiSourceStockService(); // Multi-API failover service

        // Step 2: Create a portfolio manager and give it the price service
        // This will manage all your stocks and transactions
        PortfolioService portfolio = new PortfolioService(priceService); // Create portfolio manager

        // Launch Graphical User Interface
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                com.formdev.flatlaf.FlatDarkLaf.setup();
                new com.portfolio.ui.PremiumStockDashboard(portfolio).setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("\n🚀 StockVault Premium Core Launched");
        System.out.println("📊 GUI Window is now open.");
    }
}

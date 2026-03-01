package com.portfolio; // Main package

import com.portfolio.service.*; // Import service classes
import com.portfolio.ui.*; // Import UI classes
import javax.swing.*; // Import Swing for UI
import com.formdev.flatlaf.FlatDarkLaf; // Import FlatLaf

/**
 * MainUI class - This launches the graphical user interface (GUI)
 * This is the new starting point that shows windows instead of console text
 */
public class MainUI {

    // The main method - Java starts here
    public static void main(String[] args) {
        // Set the look and feel to modern FlatLaf Dark Theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // This is important for thread safety in Swing applications
        SwingUtilities.invokeLater(() -> {

            StockPriceService priceService = new AlphaVantageService();

            PortfolioService portfolioService = new PortfolioService(priceService);

            // In a real app, this would load from a database or file
            portfolioService.buy("AAPL", 10, 150); // Buy 10 Apple shares at $150
            portfolioService.buy("TSLA", 5, 200); // Buy 5 Tesla shares at $200
            portfolioService.buy("GOOGL", 3, 2800); // Buy 3 Google shares at $2800

            PremiumStockDashboard ui = new PremiumStockDashboard(portfolioService);

            ui.setVisible(true);

            System.out.println("✅ Portfolio UI launched successfully!");
            System.out.println("📊 Window is now open. Check your screen!");
        });
    }
}

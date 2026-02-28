package com.portfolio; // Main package

import com.portfolio.service.*;  // Import service classes
import com.portfolio.ui.*;       // Import UI classes
import javax.swing.*;            // Import Swing for UI

/**
 * MainUI class - This launches the graphical user interface (GUI)
 * This is the new starting point that shows windows instead of console text
 */
public class MainUI {
    
    // The main method - Java starts here
    public static void main(String[] args) {
        
        // Set the look and feel to match your operating system
        // This makes the app look like a native Windows/Mac/Linux application
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // If setting look and feel fails, just use default
            e.printStackTrace();
        }
        
        // SwingUtilities.invokeLater ensures UI is created on the correct thread
        // This is important for thread safety in Swing applications
        SwingUtilities.invokeLater(() -> {
            
            // Step 1: Create the price service (gets real stock prices from internet)
            StockPriceService priceService = new AlphaVantageService();
            
            // Step 2: Create the portfolio service (manages your stocks)
            PortfolioService portfolioService = new PortfolioService(priceService);
            
            // Step 3: Add some sample stocks so the UI has data to display
            // In a real app, this would load from a database or file
            portfolioService.buy("AAPL", 10, 150);   // Buy 10 Apple shares at $150
            portfolioService.buy("TSLA", 5, 200);    // Buy 5 Tesla shares at $200
            portfolioService.buy("GOOGL", 3, 2800);  // Buy 3 Google shares at $2800
            
            // Step 4: Create the MODERN UI window
            ModernPortfolioUI ui = new ModernPortfolioUI(portfolioService);
            
            // Step 5: Make the window visible (show it on screen)
            ui.setVisible(true);
            
            // Print message to console
            System.out.println("✅ Portfolio UI launched successfully!");
            System.out.println("📊 Window is now open. Check your screen!");
        });
    }
}

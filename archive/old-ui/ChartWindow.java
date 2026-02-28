package com.portfolio.ui; // This file belongs to the "ui" folder

import com.portfolio.model.*;        // Import model classes
import com.portfolio.service.*;      // Import service classes
import javax.swing.*;                // Import Swing for windows
import java.awt.*;                   // Import AWT for layouts and colors
import org.jfree.chart.*;            // Import JFreeChart - library for creating charts
import org.jfree.chart.plot.*;       // Import plot classes for chart customization
import org.jfree.data.general.*;     // Import data classes for charts
import org.jfree.data.category.*;    // Import category data for bar charts
import java.util.List;               // Import List

// This window shows charts visualizing your portfolio data
// It displays pie charts and bar charts to help you understand your investments
public class ChartWindow extends JFrame {  // JFrame = a window
    
    private PortfolioService portfolioService;  // The service with portfolio data
    
    // Constructor - creates the chart window
    public ChartWindow(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;  // Store the portfolio service
        
        // Set up window properties
        setTitle("📊 Portfolio Charts");           // Window title
        setSize(1200, 700);                        // Window size: 1200px wide, 700px tall
        setLocationRelativeTo(null);               // Center on screen
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);  // Close only this window, not entire app
        
        // Create the charts and add them to the window
        initializeCharts();
    }
    
    // Creates and arranges all the charts
    private void initializeCharts() {
        // Create main panel to hold all charts
        // GridLayout: 2 rows, 2 columns, 10px gaps
        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // 10px padding
        
        // Create four different charts
        ChartPanel pieChart1 = createPortfolioDistributionChart();  // Pie chart: portfolio distribution
        ChartPanel pieChart2 = createProfitLossChart();             // Pie chart: profit vs loss
        ChartPanel barChart1 = createStockValueChart();             // Bar chart: stock values
        ChartPanel barChart2 = createGainLossChart();               // Bar chart: gains and losses
        
        // Add all charts to the main panel
        mainPanel.add(pieChart1);
        mainPanel.add(pieChart2);
        mainPanel.add(barChart1);
        mainPanel.add(barChart2);
        
        // Add main panel to window
        add(mainPanel);
    }
    
    // Creates a pie chart showing how your money is distributed across stocks
    // Example: Apple 60%, Tesla 40%
    private ChartPanel createPortfolioDistributionChart() {
        // Create dataset to hold the data
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        // Get all portfolio items
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        // Add each stock's value to the dataset
        for (PortfolioItem item : items) {
            // Add: Stock symbol → Total value
            // Example: "AAPL" → 2781.20
            dataset.setValue(item.getStock().getSymbol(), item.getTotalValue());
        }
        
        // Create the pie chart
        JFreeChart chart = ChartFactory.createPieChart(
            "Portfolio Distribution by Value",  // Chart title
            dataset,                             // The data
            true,                                // Show legend (color key)
            true,                                // Show tooltips on hover
            false                                // No URLs
        );
        
        // Customize the chart appearance
        chart.setBackgroundPaint(Color.WHITE);  // White background
        PiePlot plot = (PiePlot) chart.getPlot();  // Get the plot (the actual pie)
        plot.setBackgroundPaint(new Color(240, 248, 255));  // Light blue plot background
        plot.setOutlineVisible(false);  // No border around pie
        
        // Return the chart wrapped in a ChartPanel (makes it displayable)
        return new ChartPanel(chart);
    }
    
    // Creates a pie chart showing total profit vs total loss
    // Example: Profit $1000 (green), Loss $200 (red)
    private ChartPanel createProfitLossChart() {
        // Create dataset
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        double totalProfit = 0;  // Track total profit
        double totalLoss = 0;    // Track total loss
        
        // Get all portfolio items
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        // Calculate total profit and loss
        for (PortfolioItem item : items) {
            double gainLoss = item.getGainLoss();  // Get gain/loss for this stock
            if (gainLoss > 0) {
                totalProfit += gainLoss;  // Add to profit if positive
            } else {
                totalLoss += Math.abs(gainLoss);  // Add to loss if negative (make positive)
            }
        }
        
        // Add data to dataset
        if (totalProfit > 0) {
            dataset.setValue("Profit", totalProfit);  // Add profit slice
        }
        if (totalLoss > 0) {
            dataset.setValue("Loss", totalLoss);      // Add loss slice
        }
        
        // If no profit or loss, show message
        if (totalProfit == 0 && totalLoss == 0) {
            dataset.setValue("No Change", 1);  // Show "No Change" slice
        }
        
        // Create the pie chart
        JFreeChart chart = ChartFactory.createPieChart(
            "Total Profit vs Loss",  // Chart title
            dataset,                 // The data
            true,                    // Show legend
            true,                    // Show tooltips
            false                    // No URLs
        );
        
        // Customize appearance
        chart.setBackgroundPaint(Color.WHITE);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(240, 248, 255));
        plot.setOutlineVisible(false);
        
        // Set custom colors for slices
        plot.setSectionPaint("Profit", new Color(60, 179, 113));   // Green for profit
        plot.setSectionPaint("Loss", new Color(220, 20, 60));      // Red for loss
        plot.setSectionPaint("No Change", new Color(128, 128, 128));  // Gray for no change
        
        return new ChartPanel(chart);
    }
    
    // Creates a bar chart showing the value of each stock
    // Example: AAPL bar = $2781, TSLA bar = $1000
    private ChartPanel createStockValueChart() {
        // Create dataset for bar chart
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        // Get all portfolio items
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        // Add each stock's value as a bar
        for (PortfolioItem item : items) {
            // Add: Value, "Value" category, Stock symbol
            // Example: 2781.20, "Value", "AAPL"
            dataset.addValue(item.getTotalValue(), "Value", item.getStock().getSymbol());
        }
        
        // Create the bar chart
        JFreeChart chart = ChartFactory.createBarChart(
            "Stock Values",          // Chart title
            "Stock Symbol",          // X-axis label
            "Value ($)",             // Y-axis label
            dataset,                 // The data
            PlotOrientation.VERTICAL,  // Vertical bars
            false,                   // No legend needed
            true,                    // Show tooltips
            false                    // No URLs
        );
        
        // Customize appearance
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(240, 248, 255));
        plot.setRangeGridlinePaint(Color.GRAY);  // Gray grid lines
        
        return new ChartPanel(chart);
    }
    
    // Creates a bar chart showing gain/loss for each stock
    // Green bars = profit, Red bars = loss
    private ChartPanel createGainLossChart() {
        // Create dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        // Get all portfolio items
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        // Add each stock's gain/loss as a bar
        for (PortfolioItem item : items) {
            double gainLoss = item.getGainLoss();
            // Add: Gain/Loss value, "Gain/Loss" category, Stock symbol
            dataset.addValue(gainLoss, "Gain/Loss", item.getStock().getSymbol());
        }
        
        // Create the bar chart
        JFreeChart chart = ChartFactory.createBarChart(
            "Gain/Loss by Stock",    // Chart title
            "Stock Symbol",          // X-axis label
            "Gain/Loss ($)",         // Y-axis label
            dataset,                 // The data
            PlotOrientation.VERTICAL,  // Vertical bars
            false,                   // No legend
            true,                    // Show tooltips
            false                    // No URLs
        );
        
        // Customize appearance
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(240, 248, 255));
        plot.setRangeGridlinePaint(Color.GRAY);
        
        // TODO: Color bars green for profit, red for loss
        // This requires custom renderer (advanced topic)
        
        return new ChartPanel(chart);
    }
}

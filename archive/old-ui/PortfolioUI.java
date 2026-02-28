package com.portfolio.ui; // This file belongs to the "ui" folder - UI means User Interface (what you see on screen)

import com.portfolio.model.*;           // Import all model classes (Stock, PortfolioItem, Transaction)
import com.portfolio.service.*;         // Import all service classes
import javax.swing.*;                   // Import Swing - Java's library for creating windows and buttons
import javax.swing.table.*;             // Import table components to display data in rows/columns
import java.awt.*;                      // Import AWT - Java's library for colors, fonts, layouts
import java.awt.event.*;                // Import event handling - responds to button clicks, etc.
import java.util.List;                  // Import List to work with lists of items

// This is the main window of your portfolio application
// It shows your stocks, charts, and buttons to interact with
public class PortfolioUI extends JFrame {  // JFrame = a window with title bar, close button, etc.
    
    // Components - these are the visual elements you see on screen
    private PortfolioService portfolioService;  // The service that manages your portfolio data
    private JTable portfolioTable;              // Table to display your stocks (like Excel spreadsheet)
    private DefaultTableModel tableModel;       // Model that holds the data for the table
    private JLabel totalInvestmentLabel;        // Label showing total money invested
    private JLabel currentValueLabel;           // Label showing current portfolio value
    private JLabel profitLossLabel;             // Label showing profit or loss
    private JButton refreshButton;              // Button to update stock prices
    private JButton addStockButton;             // Button to buy new stocks
    private JButton viewChartButton;            // Button to show charts
    
    // Constructor - runs when you create new PortfolioUI()
    // This sets up the entire window and all its components
    public PortfolioUI(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;  // Store the portfolio service
        
        // Set up the main window properties
        setTitle("📊 Stock Portfolio Tracker");  // Window title at the top
        setSize(1000, 600);                      // Window size: 1000 pixels wide, 600 pixels tall
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // Close program when X is clicked
        setLocationRelativeTo(null);             // Center window on screen
        
        // Initialize all the UI components (buttons, tables, labels, etc.)
        initializeComponents();
        
        // Load initial data into the table
        refreshPortfolioTable();
        updateSummaryLabels();
    }
    
    // This method creates and arranges all the visual components
    private void initializeComponents() {
        // Create the main container that holds everything
        // BorderLayout divides screen into 5 areas: NORTH, SOUTH, EAST, WEST, CENTER
        setLayout(new BorderLayout(10, 10));  // 10 pixel gaps between areas
        
        // ===== TOP PANEL (NORTH) - Shows summary information =====
        JPanel summaryPanel = createSummaryPanel();  // Create the summary panel
        add(summaryPanel, BorderLayout.NORTH);       // Put it at the top
        
        // ===== CENTER PANEL - Shows the table of stocks =====
        JPanel tablePanel = createTablePanel();      // Create the table panel
        add(tablePanel, BorderLayout.CENTER);        // Put it in the center (takes most space)
        
        // ===== BOTTOM PANEL (SOUTH) - Shows action buttons =====
        JPanel buttonPanel = createButtonPanel();    // Create the button panel
        add(buttonPanel, BorderLayout.SOUTH);        // Put it at the bottom
    }
    
    // Creates the summary panel at the top showing investment totals
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel();                 // Create a new panel (container)
        panel.setLayout(new GridLayout(1, 3, 20, 10));  // 1 row, 3 columns, 20px horizontal gap
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // Add 10px padding around edges
        panel.setBackground(new Color(240, 248, 255));  // Light blue background color
        
        // Create three info boxes showing investment, value, and profit/loss
        totalInvestmentLabel = createInfoLabel("Total Investment: $0.00", new Color(70, 130, 180));  // Steel blue
        currentValueLabel = createInfoLabel("Current Value: $0.00", new Color(60, 179, 113));        // Medium sea green
        profitLossLabel = createInfoLabel("Profit/Loss: $0.00", new Color(220, 20, 60));             // Crimson red
        
        // Add the three labels to the panel
        panel.add(totalInvestmentLabel);
        panel.add(currentValueLabel);
        panel.add(profitLossLabel);
        
        return panel;  // Return the completed panel
    }
    
    // Creates a styled label for displaying information
    // Example: createInfoLabel("Total: $1000", Color.BLUE) makes a blue label saying "Total: $1000"
    private JLabel createInfoLabel(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);  // Create label, center the text
        label.setFont(new Font("Arial", Font.BOLD, 16));         // Set font: Arial, Bold, size 16
        label.setForeground(color);                              // Set text color
        label.setBorder(BorderFactory.createLineBorder(color, 2));  // Add colored border, 2 pixels thick
        label.setOpaque(true);                                   // Make background visible
        label.setBackground(Color.WHITE);                        // White background
        label.setPreferredSize(new Dimension(300, 60));          // Set size: 300px wide, 60px tall
        return label;  // Return the styled label
    }
    
    // Creates the center panel with the table showing all stocks
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());  // Create panel with BorderLayout
        
        // Define column names for the table (like Excel column headers)
        String[] columnNames = {"Symbol", "Name", "Quantity", "Purchase Price", "Current Price", "Total Value", "Gain/Loss"};
        
        // Create table model - this holds the data
        // false = cells are not editable (user can't change values by clicking)
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;  // Make all cells read-only
            }
        };
        
        // Create the actual table using the model
        portfolioTable = new JTable(tableModel);
        portfolioTable.setFont(new Font("Arial", Font.PLAIN, 14));           // Set font for table data
        portfolioTable.setRowHeight(30);                                     // Make rows 30 pixels tall
        portfolioTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));  // Bold font for headers
        portfolioTable.getTableHeader().setBackground(new Color(70, 130, 180));     // Blue header background
        portfolioTable.getTableHeader().setForeground(Color.WHITE);          // White header text
        
        // Add scroll bars if table is too big to fit
        JScrollPane scrollPane = new JScrollPane(portfolioTable);
        panel.add(scrollPane, BorderLayout.CENTER);  // Add scrollable table to center
        
        return panel;  // Return the completed panel
    }
    
    // Creates the bottom panel with action buttons
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));  // Flow layout, center aligned, 20px gaps
        panel.setBackground(new Color(240, 248, 255));  // Light blue background
        
        // Create "Refresh Prices" button
        refreshButton = createStyledButton("🔄 Refresh Prices", new Color(70, 130, 180));
        refreshButton.addActionListener(e -> refreshPrices());  // When clicked, run refreshPrices()
        
        // Create "Add Stock" button
        addStockButton = createStyledButton("➕ Add Stock", new Color(60, 179, 113));
        addStockButton.addActionListener(e -> showAddStockDialog());  // When clicked, show dialog to add stock
        
        // Create "View Charts" button
        viewChartButton = createStyledButton("📈 View Charts", new Color(255, 140, 0));
        viewChartButton.addActionListener(e -> showCharts());  // When clicked, show charts window
        
        // Add all buttons to the panel
        panel.add(refreshButton);
        panel.add(addStockButton);
        panel.add(viewChartButton);
        
        return panel;  // Return the completed panel
    }
    
    // Creates a styled button with custom color
    // Example: createStyledButton("Click Me", Color.BLUE) makes a blue button
    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);                    // Create button with text
        button.setFont(new Font("Arial", Font.BOLD, 14));      // Set font
        button.setBackground(color);                           // Set background color
        button.setForeground(Color.WHITE);                     // Set text color to white
        button.setFocusPainted(false);                         // Remove focus border
        button.setBorderPainted(false);                        // Remove border
        button.setPreferredSize(new Dimension(180, 40));       // Set size: 180px wide, 40px tall
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));      // Change cursor to hand when hovering
        return button;  // Return the styled button
    }
    
    // Refreshes the table with current portfolio data
    private void refreshPortfolioTable() {
        tableModel.setRowCount(0);  // Clear all existing rows from table
        
        // Get all portfolio items from the service
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        // Loop through each stock and add a row to the table
        for (PortfolioItem item : items) {
            Stock stock = item.getStock();  // Get the stock object
            
            // Create array of values for this row
            Object[] row = {
                stock.getSymbol(),                                    // Column 1: Symbol (ex: "AAPL")
                stock.getName(),                                      // Column 2: Name (ex: "Apple Inc.")
                item.getQuantity(),                                   // Column 3: Quantity (ex: 10)
                String.format("$%.2f", item.getPurchasePrice()),      // Column 4: Purchase price (ex: "$150.00")
                String.format("$%.2f", stock.getCurrentPrice()),      // Column 5: Current price (ex: "$278.12")
                String.format("$%.2f", item.getTotalValue()),         // Column 6: Total value (ex: "$2,781.20")
                String.format("$%.2f", item.getGainLoss())            // Column 7: Gain/Loss (ex: "$1,281.20")
            };
            
            tableModel.addRow(row);  // Add this row to the table
        }
    }
    
    // Updates the summary labels at the top with current totals
    private void updateSummaryLabels() {
        // Calculate totals using portfolio service
        double investment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        
        // Update label texts with formatted values
        totalInvestmentLabel.setText(String.format("💰 Total Investment: $%.2f", investment));
        currentValueLabel.setText(String.format("📊 Current Value: $%.2f", currentValue));
        
        // Change profit/loss label color based on positive/negative
        if (profitLoss >= 0) {
            profitLossLabel.setForeground(new Color(60, 179, 113));  // Green for profit
            profitLossLabel.setText(String.format("📈 Profit: $%.2f", profitLoss));
        } else {
            profitLossLabel.setForeground(new Color(220, 20, 60));   // Red for loss
            profitLossLabel.setText(String.format("📉 Loss: $%.2f", Math.abs(profitLoss)));
        }
    }
    
    // Called when "Refresh Prices" button is clicked
    private void refreshPrices() {
        // Show loading message
        JOptionPane.showMessageDialog(this, 
            "Fetching latest prices from Alpha Vantage...\nThis may take a few seconds.",
            "Updating Prices", 
            JOptionPane.INFORMATION_MESSAGE);
        
        // Update prices in background thread (so UI doesn't freeze)
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                portfolioService.updateAllPrices();  // Fetch real prices from API
                return null;
            }
            
            @Override
            protected void done() {
                // After prices are updated, refresh the display
                refreshPortfolioTable();
                updateSummaryLabels();
                JOptionPane.showMessageDialog(PortfolioUI.this, 
                    "Prices updated successfully!", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };
        worker.execute();  // Start the background task
    }
    
    // Shows dialog to add a new stock
    private void showAddStockDialog() {
        // Create input dialog with fields for stock details
        JTextField symbolField = new JTextField(10);      // Field for symbol (ex: "AAPL")
        JTextField quantityField = new JTextField(10);    // Field for quantity (ex: "10")
        JTextField priceField = new JTextField(10);       // Field for price (ex: "150.00")
        
        // Create panel to hold the input fields
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.add(new JLabel("Stock Symbol:"));
        panel.add(symbolField);
        panel.add(new JLabel("Quantity:"));
        panel.add(quantityField);
        panel.add(new JLabel("Purchase Price:"));
        panel.add(priceField);
        
        // Show dialog and get user input
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Add New Stock", JOptionPane.OK_CANCEL_OPTION);
        
        // If user clicked OK, process the input
        if (result == JOptionPane.OK_OPTION) {
            try {
                String symbol = symbolField.getText().toUpperCase();  // Get symbol, make uppercase
                int quantity = Integer.parseInt(quantityField.getText());  // Convert text to number
                double price = Double.parseDouble(priceField.getText());   // Convert text to decimal
                
                // Add stock to portfolio
                portfolioService.buy(symbol, quantity, price);
                
                // Refresh display
                refreshPortfolioTable();
                updateSummaryLabels();
                
                // Show success message
                JOptionPane.showMessageDialog(this, 
                    "Stock added successfully!", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                
            } catch (NumberFormatException ex) {
                // If user entered invalid numbers, show error
                JOptionPane.showMessageDialog(this, 
                    "Please enter valid numbers for quantity and price.", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    // Shows the charts window
    private void showCharts() {
        // Create and show the chart window
        ChartWindow chartWindow = new ChartWindow(portfolioService);
        chartWindow.setVisible(true);  // Make the window visible
    }
}

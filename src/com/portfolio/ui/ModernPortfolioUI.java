package com.portfolio.ui;

import com.portfolio.model.PortfolioItem;
import com.portfolio.service.PortfolioService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * ModernPortfolioUI provides a beautiful, modern dark theme interface.
 * Features: Dark theme, gradient backgrounds, color-coded gains/losses, summary cards
 */
public class ModernPortfolioUI extends JFrame {
    // Service for portfolio operations
    private PortfolioService portfolioService;
    
    // UI Components
    private JTable stockTable;
    private JPanel summaryPanel;
    private JLabel totalInvestmentLabel;
    private JLabel currentValueLabel;
    private JLabel profitLossLabel;
    private JLabel returnPercentageLabel;
    private JLabel realizedLabel;
    private JButton refreshButton;

    /**
     * Constructor creates the modern UI.
     */
    public ModernPortfolioUI() {
        this(new PortfolioService());
    }

    /** Uses the given service (tests pass one with a fake price provider). */
    public ModernPortfolioUI(PortfolioService service) {
        this.portfolioService = service;

        // Configure frame
        setTitle("Stock Portfolio Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setResizable(true);

        // Create UI
        createUI();

        // Show frame
        setVisible(true);

        // Load data on startup
        refreshTable();
    }

    /**
     * Create all UI components
     */
    private void createUI() {
        // Set dark theme colors
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("Label.foreground", new Color(255, 255, 255));
        UIManager.put("Button.background", Theme.PRIMARY);
        UIManager.put("Button.foreground", new Color(255, 255, 255));

        // Main panel with dark background
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Theme.BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBackground(Theme.BG);

        // Buttons
        JButton addButton = createStyledButton("+ Add Stock");
        addButton.addActionListener(e -> showAddStockDialog());

        refreshButton = createStyledButton("Refresh Prices");
        refreshButton.addActionListener(e -> refreshPrices());

        JButton ledgerButton = createStyledButton("Ledger");
        ledgerButton.addActionListener(e -> new TransactionLedgerWindow(portfolioService));

        JButton sellButton = createStyledButton("Sell Selected");
        sellButton.addActionListener(e -> showSellDialog());

        JButton chartButton = createStyledButton("Charts");
        chartButton.addActionListener(e -> showChartsWindow());

        topPanel.add(addButton);
        topPanel.add(refreshButton);
        topPanel.add(sellButton);
        topPanel.add(ledgerButton);
        topPanel.add(chartButton);

        // Summary panel
        summaryPanel = new JPanel(new GridLayout(1, 5, 10, 5));
        summaryPanel.setBackground(Theme.BG);
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Summary cards
        totalInvestmentLabel = createSummaryCard("Total Investment", "$0.00");
        currentValueLabel = createSummaryCard("Current Value", "$0.00");
        profitLossLabel = createSummaryCard("Unrealized P/L", "$0.00");
        realizedLabel = createSummaryCard("Realized P/L (sales)", "$0.00");
        returnPercentageLabel = createSummaryCard("Return %", "0.00%");

        summaryPanel.add(totalInvestmentLabel.getParent());
        summaryPanel.add(currentValueLabel.getParent());
        summaryPanel.add(profitLossLabel.getParent());
        summaryPanel.add(realizedLabel.getParent());
        summaryPanel.add(returnPercentageLabel.getParent());

        // Table for stocks
        createStockTable();
        JScrollPane tableScroll = new JScrollPane(stockTable);
        tableScroll.getViewport().setBackground(Theme.BG);

        // Add panels to main
        // Both panels used to be added to NORTH, which silently dropped the button toolbar.
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(Theme.BG);
        northPanel.add(topPanel, BorderLayout.NORTH);
        northPanel.add(summaryPanel, BorderLayout.CENTER);
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(tableScroll, BorderLayout.CENTER);

        // Add main panel to frame
        add(mainPanel);
    }

    /**
     * Create a styled button with modern appearance
     */
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Theme.PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setMargin(new Insets(8, 15, 8, 15));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(Theme.PRIMARY_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(Theme.PRIMARY);
            }
        });
        
        return button;
    }

    /**
     * Create a summary card displaying a metric
     */
    private JLabel createSummaryCard(String title, String value) {
        // Card panel
        JPanel cardPanel = new JPanel(new BorderLayout(5, 5));
        cardPanel.setBackground(new Color(45, 45, 45));
        cardPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
        cardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title label
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(150, 150, 150));
        titleLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        // Value label
        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 16));

        cardPanel.add(titleLabel, BorderLayout.NORTH);
        cardPanel.add(valueLabel, BorderLayout.CENTER);

        return valueLabel;
    }

    /**
     * Create the stocks table
     */
    private void createStockTable() {
        // Table columns
        String[] columns = {"Symbol", "Name", "Quantity", "Purchase Price", "Current Price", "Price Updated", "Total Investment", "Current Value", "Unrealized P/L"};
        
        // Create table model
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;  // Make table read-only
            }
        };

        // Create table
        stockTable = new JTable(model);
        stockTable.setBackground(new Color(45, 45, 45));
        stockTable.setForeground(Color.WHITE);
        stockTable.setGridColor(new Color(60, 60, 60));
        stockTable.getTableHeader().setBackground(new Color(50, 50, 50));
        stockTable.getTableHeader().setForeground(Color.WHITE);
        stockTable.setFont(new Font("Arial", Font.PLAIN, 12));
        stockTable.setRowHeight(25);
    }

    /**
     * Refresh the table with data from service
     */
    private void refreshTable() {
        // Get model
        javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) stockTable.getModel();
        model.setRowCount(0);  // Clear table

        // Get all portfolio items
        List<PortfolioItem> items = portfolioService.getAllPortfolioItems();

        // Add rows
        for (PortfolioItem item : items) {
            Object[] row = {
                item.getSymbol(),
                item.getName(),
                item.getQuantity(),
                String.format("$%.2f", item.getPurchasePrice()),
                String.format("$%.2f", item.getCurrentPrice()),
                priceAge(item.getPriceUpdatedAt(), java.time.Instant.now()),
                String.format("$%.2f", item.getTotalInvestment()),
                String.format("$%.2f", item.getCurrentValue()),
                String.format("$%.2f", item.getProfitLoss())
            };
            model.addRow(row);
        }

        // Update summary
        updateSummary();
    }

    /**
     * Update summary cards with portfolio totals
     */
    private void updateSummary() {
        double totalInvestment = portfolioService.getTotalInvestment();
        double currentValue = portfolioService.getTotalPortfolioValue();
        double profitLoss = portfolioService.getTotalProfitLoss();
        double returnPercentage = portfolioService.getReturnPercentage();

        totalInvestmentLabel.setText(String.format("$%.2f", totalInvestment));
        currentValueLabel.setText(String.format("$%.2f", currentValue));
        
        // Color code profit/loss
        if (profitLoss >= 0) {
            profitLossLabel.setText(String.format("$%.2f", profitLoss));
            profitLossLabel.setForeground(new Color(0, 255, 100));  // Green for profit
        } else {
            profitLossLabel.setText(String.format("$%.2f", profitLoss));
            profitLossLabel.setForeground(new Color(255, 50, 50));   // Red for loss
        }

        realizedLabel.setText("$" + portfolioService.getTotalRealizedProfitLoss().toPlainString());
        returnPercentageLabel.setText(String.format("%.2f%%", returnPercentage));
        if (returnPercentage >= 0) {
            returnPercentageLabel.setForeground(new Color(0, 255, 100));
        } else {
            returnPercentageLabel.setForeground(new Color(255, 50, 50));
        }
    }

    /**
     * Show dialog to add a new stock
     */
    private void showAddStockDialog() {
        JDialog dialog = new JDialog(this, "Add Stock", true);
        dialog.setSize(300, 250);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(Theme.BG);

        // Symbol input
        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.WHITE);
        JTextField symbolField = new JTextField();
        symbolField.setBackground(new Color(45, 45, 45));
        symbolField.setForeground(Color.WHITE);

        // Quantity input
        JLabel quantityLabel = new JLabel("Quantity:");
        quantityLabel.setForeground(Color.WHITE);
        JTextField quantityField = new JTextField();
        quantityField.setBackground(new Color(45, 45, 45));
        quantityField.setForeground(Color.WHITE);

        // Price input
        JLabel priceLabel = new JLabel("Purchase Price:");
        priceLabel.setForeground(Color.WHITE);
        JTextField priceField = new JTextField();
        priceField.setBackground(new Color(45, 45, 45));
        priceField.setForeground(Color.WHITE);

        // Validate button
        JButton validateButton = createStyledButton("Validate");
        validateButton.addActionListener(e -> {
            String symbol = symbolField.getText().trim().toUpperCase();
            validateButton.setEnabled(false);
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return portfolioService.isValidStock(symbol);
                }

                @Override
                protected void done() {
                    validateButton.setEnabled(true);
                    try {
                        if (get()) {
                            JOptionPane.showMessageDialog(dialog, "Stock is valid.", "Validation", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(dialog, "Stock not found.", "Validation", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(dialog, "Could not validate (live price unavailable): " + cause.getMessage(), "Validation unavailable", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });

        // Add button
        JButton addButton = createStyledButton("Add Stock");
        addButton.addActionListener(e -> {
            try {
                String symbol = symbolField.getText().toUpperCase();
                int quantity = Integer.parseInt(quantityField.getText());
                double price = Double.parseDouble(priceField.getText());

                PortfolioItem item = new PortfolioItem(symbol, symbol, quantity, price, price);
                portfolioService.buyStock(item);
                refreshTable();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Stock added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid input format", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(symbolLabel);
        panel.add(symbolField);
        panel.add(quantityLabel);
        panel.add(quantityField);
        panel.add(priceLabel);
        panel.add(priceField);
        panel.add(validateButton);
        panel.add(addButton);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    /**
     * Refresh prices from the provider off the EDT. The button is disabled while a refresh runs
     * (and the service itself rejects overlapping refreshes).
     */
    private void refreshPrices() {
        if (portfolioService.isRefreshInProgress()) {
            return;
        }
        refreshButton.setEnabled(false);
        refreshButton.setText("Refreshing...");
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return portfolioService.updateAllPrices();
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("Refresh Prices");
                try {
                    int failed = get();
                    refreshTable();
                    int total = portfolioService.getAllPortfolioItems().size();
                    if (total == 0) {
                        JOptionPane.showMessageDialog(ModernPortfolioUI.this, "No holdings to refresh.", "Refresh", JOptionPane.INFORMATION_MESSAGE);
                    } else if (failed == 0) {
                        JOptionPane.showMessageDialog(ModernPortfolioUI.this, "Prices updated.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ModernPortfolioUI.this, (total - failed) + " of " + total + " holding(s) updated; " + failed
                                + " could not be updated and keep their last known price. " + (portfolioService.getLastRefreshProblem() == null ? "" : " First problem: " + portfolioService.getLastRefreshProblem()),
                                "Prices partly updated", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    refreshTable();
                    JOptionPane.showMessageDialog(ModernPortfolioUI.this, "Refresh failed: " + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Human-readable age of a price; flags prices older than 24 h and never-refreshed prices. */
    static String priceAge(java.time.Instant updated, java.time.Instant now) {
        if (updated == null) {
            return "Not refreshed";
        }
        long mins = Math.max(0, java.time.Duration.between(updated, now).toMinutes());
        if (mins < 60) {
            return mins + " min ago";
        }
        long hours = mins / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        return (hours / 24) + " d ago (STALE)";
    }

    /**
     * Sell shares of the selected holding. Realized P/L uses weighted-average cost.
     */
    private void showSellDialog() {
        int row = stockTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a holding to sell first.", "Sell", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String symbol = (String) stockTable.getValueAt(row, 0);
        // The price is never typed: a sale uses a live quote that is fetched, shown (price, currency, wallet effect) and confirmed.
        String qtyText = JOptionPane.showInputDialog(this,
                "Quantity to sell (the live price is shown for confirmation):", "Sell " + symbol, JOptionPane.QUESTION_MESSAGE);
        if (qtyText == null) {
            return;
        }
        try {
            int qty = Integer.parseInt(qtyText.trim());
            TradeFlow.sell(this, portfolioService, symbol, qty, this::refreshTable);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid input format", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Show charts window
     */
    private void showChartsWindow() {
        new ModernChartWindow(portfolioService);
    }
}

package com.portfolio.ui; // UI package

import com.portfolio.model.*;
import com.portfolio.service.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.border.*;

// Modern, beautiful portfolio UI with gradients and professional styling
// This looks like a real financial application (like Robinhood or E*TRADE)
public class ModernPortfolioUI extends JFrame {
    
    // Color scheme - Modern dark theme with accent colors
    private static final Color DARK_BG = new Color(18, 18, 18);           // Almost black background
    private static final Color CARD_BG = new Color(30, 30, 30);           // Dark gray for cards
    private static final Color ACCENT_GREEN = new Color(0, 200, 83);      // Bright green for profit
    private static final Color ACCENT_RED = new Color(255, 59, 48);       // Bright red for loss
    private static final Color ACCENT_BLUE = new Color(10, 132, 255);     // iOS blue
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);   // White text
    private static final Color TEXT_SECONDARY = new Color(152, 152, 157); // Gray text
    
    private PortfolioService portfolioService;
    private JTable portfolioTable;
    private DefaultTableModel tableModel;
    private JLabel totalInvestmentLabel;
    private JLabel currentValueLabel;
    private JLabel profitLossLabel;
    private JLabel profitPercentLabel;
    
    // Constructor - creates the modern UI
    public ModernPortfolioUI(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
        
        // Window setup
        setTitle("Portfolio Tracker");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Set dark background
        getContentPane().setBackground(DARK_BG);
        
        // Initialize components
        initializeModernUI();
        
        // Load data
        refreshPortfolioTable();
        updateSummaryLabels();
    }
    
    // Creates the modern UI layout
    private void initializeModernUI() {
        setLayout(new BorderLayout(0, 0));
        
        // Top bar with title and actions
        add(createTopBar(), BorderLayout.NORTH);
        
        // Main content area
        JPanel mainContent = new JPanel(new BorderLayout(20, 20));
        mainContent.setBackground(DARK_BG);
        mainContent.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Left side - Summary cards
        mainContent.add(createSummaryPanel(), BorderLayout.WEST);
        
        // Center - Table
        mainContent.add(createModernTable(), BorderLayout.CENTER);
        
        add(mainContent, BorderLayout.CENTER);
    }
    
    // Creates modern top bar with gradient
    private JPanel createTopBar() {
        JPanel topBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                // Gradient from dark to slightly lighter
                GradientPaint gp = new GradientPaint(0, 0, new Color(25, 25, 25), 
                                                     0, getHeight(), new Color(35, 35, 35));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        topBar.setLayout(new BorderLayout());
        topBar.setPreferredSize(new Dimension(0, 80));
        
        // Left side - Title
        JLabel titleLabel = new JLabel("  💼 Portfolio Tracker");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_PRIMARY);
        topBar.add(titleLabel, BorderLayout.WEST);
        
        // Right side - Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        buttonPanel.setOpaque(false);
        
        JButton refreshBtn = createModernButton("🔄 Refresh", ACCENT_BLUE);
        refreshBtn.addActionListener(e -> refreshPrices());
        
        JButton addBtn = createModernButton("+ Add Stock", ACCENT_GREEN);
        addBtn.addActionListener(e -> showAddStockDialog());
        
        JButton chartBtn = createModernButton("📊 Charts", new Color(255, 149, 0));
        chartBtn.addActionListener(e -> showCharts());
        
        buttonPanel.add(refreshBtn);
        buttonPanel.add(addBtn);
        buttonPanel.add(chartBtn);
        
        topBar.add(buttonPanel, BorderLayout.EAST);
        
        return topBar;
    }
    
    // Creates modern button with rounded corners and hover effect
    private JButton createModernButton(String text, Color color) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Button background with rounded corners
                if (getModel().isPressed()) {
                    g2d.setColor(color.darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(color.brighter());
                } else {
                    g2d.setColor(color);
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                // Button text
                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2d.drawString(getText(), x, y);
                
                g2d.dispose();
            }
        };
        
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setPreferredSize(new Dimension(140, 40));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        return button;
    }
    
    // Creates summary panel with modern cards
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(DARK_BG);
        panel.setPreferredSize(new Dimension(320, 0));
        
        // Title
        JLabel summaryTitle = new JLabel("Portfolio Summary");
        summaryTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        summaryTitle.setForeground(TEXT_PRIMARY);
        summaryTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(summaryTitle);
        panel.add(Box.createVerticalStrut(20));
        
        // Create cards
        totalInvestmentLabel = createSummaryCard("Total Investment", "$0.00", "💰", new Color(100, 100, 255));
        currentValueLabel = createSummaryCard("Current Value", "$0.00", "📊", new Color(0, 200, 83));
        profitLossLabel = createSummaryCard("Profit/Loss", "$0.00", "📈", new Color(255, 149, 0));
        profitPercentLabel = createSummaryCard("Return", "0%", "🎯", new Color(255, 59, 48));
        
        panel.add(totalInvestmentLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(currentValueLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(profitLossLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(profitPercentLabel);
        
        return panel;
    }
    
    // Creates a modern card with gradient background
    private JLabel createSummaryCard(String title, String value, String emoji, Color accentColor) {
        JLabel card = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Card background with rounded corners
                g2d.setColor(CARD_BG);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                
                // Accent line on left
                g2d.setColor(accentColor);
                g2d.fillRoundRect(0, 0, 5, getHeight(), 20, 20);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        card.setLayout(new BorderLayout(10, 5));
        card.setPreferredSize(new Dimension(300, 100));
        card.setMaximumSize(new Dimension(300, 100));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        // Top - emoji and title
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        topPanel.setOpaque(false);
        
        JLabel emojiLabel = new JLabel(emoji);
        emojiLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(TEXT_SECONDARY);
        
        topPanel.add(emojiLabel);
        topPanel.add(titleLabel);
        
        // Bottom - value
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valueLabel.setForeground(TEXT_PRIMARY);
        
        card.add(topPanel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }
    
    // Creates modern table with dark theme
    private JScrollPane createModernTable() {
        String[] columns = {"Symbol", "Quantity", "Avg Cost", "Current Price", "Total Value", "Gain/Loss", "Return %"};
        
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        portfolioTable = new JTable(tableModel);
        
        // Modern table styling
        portfolioTable.setBackground(CARD_BG);
        portfolioTable.setForeground(TEXT_PRIMARY);
        portfolioTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        portfolioTable.setRowHeight(50);
        portfolioTable.setShowGrid(false);
        portfolioTable.setIntercellSpacing(new Dimension(0, 0));
        portfolioTable.setSelectionBackground(new Color(50, 50, 50));
        portfolioTable.setSelectionForeground(TEXT_PRIMARY);
        
        // Header styling
        JTableHeader header = portfolioTable.getTableHeader();
        header.setBackground(new Color(40, 40, 40));
        header.setForeground(TEXT_SECONDARY);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setPreferredSize(new Dimension(0, 45));
        
        // Custom cell renderer for colors
        portfolioTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : new Color(35, 35, 35));
                }
                
                setForeground(TEXT_PRIMARY);
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                
                // Color gain/loss columns
                if (column == 5 || column == 6) {
                    String val = value.toString();
                    if (val.contains("-")) {
                        setForeground(ACCENT_RED);
                    } else if (!val.equals("$0.00") && !val.equals("0.00%")) {
                        setForeground(ACCENT_GREEN);
                    }
                }
                
                // Bold symbol column
                if (column == 0) {
                    setFont(new Font("Segoe UI", Font.BOLD, 15));
                }
                
                return c;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(portfolioTable);
        scrollPane.setBackground(DARK_BG);
        scrollPane.getViewport().setBackground(CARD_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 1));
        
        return scrollPane;
    }
    
    // Refreshes table data
    private void refreshPortfolioTable() {
        tableModel.setRowCount(0);
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        for (PortfolioItem item : items) {
            Stock stock = item.getStock();
            double gainLoss = item.getGainLoss();
            double returnPercent = ((stock.getCurrentPrice() - item.getPurchasePrice()) / item.getPurchasePrice()) * 100;
            
            Object[] row = {
                stock.getSymbol(),
                item.getQuantity(),
                String.format("$%.2f", item.getPurchasePrice()),
                String.format("$%.2f", stock.getCurrentPrice()),
                String.format("$%.2f", item.getTotalValue()),
                String.format("$%.2f", gainLoss),
                String.format("%.2f%%", returnPercent)
            };
            
            tableModel.addRow(row);
        }
    }
    
    // Updates summary cards
    private void updateSummaryLabels() {
        double investment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        double returnPercent = investment > 0 ? (profitLoss / investment) * 100 : 0;
        
        // Update card values
        updateCardValue(totalInvestmentLabel, String.format("$%.2f", investment));
        updateCardValue(currentValueLabel, String.format("$%.2f", currentValue));
        updateCardValue(profitLossLabel, String.format("$%.2f", profitLoss));
        updateCardValue(profitPercentLabel, String.format("%.2f%%", returnPercent));
    }
    
    // Helper to update card value
    private void updateCardValue(JLabel card, String newValue) {
        Component[] components = card.getComponents();
        if (components.length > 1 && components[1] instanceof JLabel) {
            ((JLabel) components[1]).setText(newValue);
        }
    }
    
    // Refresh prices from API
    private void refreshPrices() {
        // Create non-modal loading dialog (doesn't block UI)
        JDialog loadingDialog = createLoadingDialog();
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Show loading dialog on UI thread
                SwingUtilities.invokeLater(() -> loadingDialog.setVisible(true));
                
                // Update prices in background (this takes time)
                portfolioService.updateAllPrices();
                return null;
            }
            
            @Override
            protected void done() {
                // Close loading dialog
                loadingDialog.dispose();
                
                // Update UI with new data
                refreshPortfolioTable();
                updateSummaryLabels();
                
                // Show success message
                showSuccessMessage("Prices updated successfully!");
            }
        };
        
        // Start the background task
        worker.execute();
    }
    
    // Creates modern loading dialog (non-modal so it doesn't block UI)
    private JDialog createLoadingDialog() {
        // false = non-modal (doesn't block the UI thread)
        JDialog dialog = new JDialog(this, "Updating Prices", false);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);
        dialog.setUndecorated(true);
        dialog.getContentPane().setBackground(CARD_BG);
        
        JLabel label = new JLabel("Fetching latest prices...", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        label.setForeground(TEXT_PRIMARY);
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBackground(DARK_BG);
        progressBar.setForeground(ACCENT_BLUE);
        
        dialog.add(label, BorderLayout.CENTER);
        dialog.add(progressBar, BorderLayout.SOUTH);
        
        return dialog;
    }
    
    // Shows success message
    private void showSuccessMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }
    
    // Shows add stock dialog with validation
    private void showAddStockDialog() {
        JDialog dialog = new JDialog(this, "Add New Stock", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG);
        
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 15));
        formPanel.setBackground(CARD_BG);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JTextField symbolField = createModernTextField();
        JTextField quantityField = createModernTextField();
        JTextField priceField = createModernTextField();
        JLabel validationLabel = createLabel("");
        validationLabel.setForeground(ACCENT_GREEN);
        
        // Add "Validate" button to check stock before adding
        JButton validateBtn = createModernButton("🔍 Validate", ACCENT_BLUE);
        validateBtn.setPreferredSize(new Dimension(120, 35));
        
        validateBtn.addActionListener(e -> {
            String symbol = symbolField.getText().trim().toUpperCase();
            if (symbol.isEmpty()) {
                validationLabel.setText("Please enter a symbol");
                validationLabel.setForeground(ACCENT_RED);
                return;
            }
            
            // Validate stock in background thread
            SwingWorker<StockValidator.StockInfo, Void> worker = new SwingWorker<StockValidator.StockInfo, Void>() {
                @Override
                protected StockValidator.StockInfo doInBackground() throws Exception {
                    validationLabel.setText("Validating...");
                    validationLabel.setForeground(TEXT_SECONDARY);
                    StockValidator validator = new StockValidator();
                    return validator.validateAndGetInfo(symbol);
                }
                
                @Override
                protected void done() {
                    try {
                        StockValidator.StockInfo info = get();
                        validationLabel.setText("✓ " + info.toString());
                        validationLabel.setForeground(ACCENT_GREEN);
                        priceField.setText(String.format("%.2f", info.getCurrentPrice()));
                    } catch (Exception ex) {
                        validationLabel.setText("✗ " + ex.getMessage());
                        validationLabel.setForeground(ACCENT_RED);
                    }
                }
            };
            worker.execute();
        });
        
        formPanel.add(createLabel("Symbol:"));
        JPanel symbolPanel = new JPanel(new BorderLayout(5, 0));
        symbolPanel.setBackground(CARD_BG);
        symbolPanel.add(symbolField, BorderLayout.CENTER);
        symbolPanel.add(validateBtn, BorderLayout.EAST);
        formPanel.add(symbolPanel);
        
        formPanel.add(createLabel("Quantity:"));
        formPanel.add(quantityField);
        formPanel.add(createLabel("Price:"));
        formPanel.add(priceField);
        formPanel.add(new JLabel());
        formPanel.add(validationLabel);
        
        JButton addButton = createModernButton("Add Stock", ACCENT_GREEN);
        addButton.addActionListener(e -> {
            try {
                String symbol = symbolField.getText().toUpperCase();
                int quantity = Integer.parseInt(quantityField.getText());
                double price = Double.parseDouble(priceField.getText());
                
                if (symbol.isEmpty()) {
                    throw new Exception("Please enter a stock symbol");
                }
                
                portfolioService.buy(symbol, quantity, price);
                refreshPortfolioTable();
                updateSummaryLabels();
                dialog.dispose();
                showSuccessMessage("Stock added successfully!");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter valid numbers!", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(addButton, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
    
    // Creates modern text field
    private JTextField createModernTextField() {
        JTextField field = new JTextField();
        field.setBackground(DARK_BG);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        return field;
    }
    
    // Creates label
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_SECONDARY);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return label;
    }
    
    // Shows charts
    private void showCharts() {
        ModernChartWindow chartWindow = new ModernChartWindow(portfolioService);
        chartWindow.setVisible(true);
    }
}

package com.portfolio.ui;

import com.portfolio.model.*;
import com.portfolio.service.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.border.*;

/**
 * Enhanced Portfolio UI - Pure Java Swing Application
 * Features: Sidebar navigation, search, multiple pages, charts
 * NO HTML, CSS, or JavaScript - 100% Java!
 */
public class EnhancedPortfolioUI extends JFrame {
    
    // Modern color scheme
    private static final Color DARK_BG = new Color(15, 15, 15);
    private static final Color SIDEBAR_BG = new Color(26, 26, 26);
    private static final Color CARD_BG = new Color(42, 42, 42);
    private static final Color ACCENT_PURPLE = new Color(102, 126, 234);
    private static final Color ACCENT_GREEN = new Color(0, 200, 83);
    private static final Color ACCENT_RED = new Color(255, 59, 48);
    private static final Color TEXT_WHITE = new Color(255, 255, 255);
    private static final Color TEXT_GRAY = new Color(153, 153, 153);
    
    private PortfolioService portfolioService;
    private JPanel mainPanel;
    private JPanel contentPanel;
    private String currentPage = "dashboard";
    
    // Stock database for search
    private String[][] stockDatabase = {
        {"AAPL", "Apple Inc.", "Technology"},
        {"GOOGL", "Alphabet Inc.", "Technology"},
        {"MSFT", "Microsoft Corp.", "Technology"},
        {"TSLA", "Tesla Inc.", "Automotive"},
        {"AMZN", "Amazon.com Inc.", "E-commerce"},
        {"META", "Meta Platforms", "Technology"},
        {"NVDA", "NVIDIA Corp.", "Technology"},
        {"NFLX", "Netflix Inc.", "Entertainment"},
        {"JPM", "JPMorgan Chase", "Finance"},
        {"BAC", "Bank of America", "Finance"},
        {"V", "Visa Inc.", "Finance"},
        {"JNJ", "Johnson & Johnson", "Healthcare"},
        {"PFE", "Pfizer Inc.", "Healthcare"},
        {"XOM", "Exxon Mobil", "Energy"},
        {"CVX", "Chevron Corp.", "Energy"}
    };
    
    public EnhancedPortfolioUI(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
        
        setTitle("📈 Portfolio Tracker - Pure Java Edition");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initializeUI();
        showPage("dashboard");
    }
    
    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(DARK_BG);
        
        // Create sidebar
        JPanel sidebar = createSidebar();
        mainPanel.add(sidebar, BorderLayout.WEST);
        
        // Create top bar
        JPanel topBar = createTopBar();
        mainPanel.add(topBar, BorderLayout.NORTH);
        
        // Create content area
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(DARK_BG);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        add(mainPanel);
    }
    
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(250, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(51, 51, 51)));
        
        // Logo
        JLabel logo = new JLabel("📈 Portfolio");
        logo.setFont(new Font("Arial", Font.BOLD, 24));
        logo.setForeground(ACCENT_PURPLE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        logo.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        sidebar.add(logo);
        
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Menu items
        addMenuItem(sidebar, "🏠 Dashboard", "dashboard");
        addMenuItem(sidebar, "💼 My Portfolio", "portfolio");
        addMenuItem(sidebar, "📊 Market", "market");
        addMenuItem(sidebar, "⭐ Watchlist", "watchlist");
        addMenuItem(sidebar, "📜 Transactions", "transactions");
        addMenuItem(sidebar, "📈 Analytics", "analytics");
        addMenuItem(sidebar, "⚙️ Settings", "settings");
        
        sidebar.add(Box.createVerticalGlue());
        
        return sidebar;
    }
    
    private void addMenuItem(JPanel sidebar, String text, String page) {
        JButton menuItem = new JButton(text);
        menuItem.setAlignmentX(Component.LEFT_ALIGNMENT);
        menuItem.setMaximumSize(new Dimension(250, 50));
        menuItem.setBackground(SIDEBAR_BG);
        menuItem.setForeground(TEXT_GRAY);
        menuItem.setFont(new Font("Arial", Font.PLAIN, 16));
        menuItem.setHorizontalAlignment(SwingConstants.LEFT);
        menuItem.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        menuItem.setFocusPainted(false);
        menuItem.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        menuItem.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (!currentPage.equals(page)) {
                    menuItem.setBackground(ACCENT_PURPLE);
                    menuItem.setForeground(TEXT_WHITE);
                }
            }
            public void mouseExited(MouseEvent e) {
                if (!currentPage.equals(page)) {
                    menuItem.setBackground(SIDEBAR_BG);
                    menuItem.setForeground(TEXT_GRAY);
                }
            }
        });
        
        menuItem.addActionListener(e -> showPage(page));
        sidebar.add(menuItem);
    }
    
    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(SIDEBAR_BG);
        topBar.setPreferredSize(new Dimension(0, 70));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 51, 51)));
        
        // Search bar
        JTextField searchField = new JTextField("🔍 Search stocks (e.g., AAPL, Tesla...)");
        searchField.setFont(new Font("Arial", Font.PLAIN, 14));
        searchField.setBackground(DARK_BG);
        searchField.setForeground(TEXT_GRAY);
        searchField.setCaretColor(TEXT_WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 51, 51)),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        
        searchField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (searchField.getText().startsWith("🔍")) {
                    searchField.setText("");
                    searchField.setForeground(TEXT_WHITE);
                }
            }
            public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText("🔍 Search stocks (e.g., AAPL, Tesla...)");
                    searchField.setForeground(TEXT_GRAY);
                }
            }
        });
        
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                String query = searchField.getText().toLowerCase();
                if (!query.startsWith("🔍") && query.length() > 0) {
                    showSearchResults(query);
                }
            }
        });
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(SIDEBAR_BG);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        topBar.add(searchPanel, BorderLayout.CENTER);
        
        // User avatar
        JLabel avatar = new JLabel("👤");
        avatar.setFont(new Font("Arial", Font.PLAIN, 24));
        avatar.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        topBar.add(avatar, BorderLayout.EAST);
        
        return topBar;
    }
    
    private void showSearchResults(String query) {
        java.util.List<String[]> results = new ArrayList<>();
        for (String[] stock : stockDatabase) {
            if (stock[0].toLowerCase().contains(query) || 
                stock[1].toLowerCase().contains(query)) {
                results.add(stock);
            }
        }
        
        if (!results.isEmpty()) {
            String[] options = new String[results.size()];
            for (int i = 0; i < results.size(); i++) {
                options[i] = results.get(i)[0] + " - " + results.get(i)[1];
            }
            
            String selected = (String) JOptionPane.showInputDialog(
                this,
                "Select a stock:",
                "Search Results",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
            );
            
            if (selected != null) {
                String symbol = selected.split(" - ")[0];
                showChartForStock(symbol);
            }
        }
    }
    
    private void showPage(String page) {
        currentPage = page;
        contentPanel.removeAll();
        
        switch (page) {
            case "dashboard":
                contentPanel.add(createDashboardPage());
                break;
            case "portfolio":
                contentPanel.add(createPortfolioPage());
                break;
            case "market":
                contentPanel.add(createMarketPage());
                break;
            case "watchlist":
                contentPanel.add(createWatchlistPage());
                break;
            case "transactions":
                contentPanel.add(createTransactionsPage());
                break;
            case "analytics":
                contentPanel.add(createAnalyticsPage());
                break;
            case "settings":
                contentPanel.add(createSettingsPage());
                break;
        }
        
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private JPanel createDashboardPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(DARK_BG);
        page.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        
        // Title
        JLabel title = new JLabel("Dashboard");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(TEXT_WHITE);
        page.add(title, BorderLayout.NORTH);
        
        // Stats panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 20, 0));
        statsPanel.setBackground(DARK_BG);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(30, 0, 30, 0));
        
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        int stockCount = portfolioService.getPortfolioItems().size();
        
        statsPanel.add(createStatCard("💰 Total Investment", String.format("₹%.2f", totalInvestment)));
        statsPanel.add(createStatCard("📊 Current Value", String.format("₹%.2f", currentValue)));
        statsPanel.add(createStatCard("📈 Profit/Loss", String.format("₹%.2f", profitLoss), profitLoss >= 0));
        statsPanel.add(createStatCard("📊 Total Stocks", String.valueOf(stockCount)));
        
        page.add(statsPanel, BorderLayout.CENTER);
        
        return page;
    }
    
    private JPanel createStatCard(String label, String value) {
        return createStatCard(label, value, true);
    }
    
    private JPanel createStatCard(String label, String value, boolean positive) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 51, 51)),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        
        JLabel labelText = new JLabel(label);
        labelText.setFont(new Font("Arial", Font.PLAIN, 14));
        labelText.setForeground(TEXT_GRAY);
        
        JLabel valueText = new JLabel(value);
        valueText.setFont(new Font("Arial", Font.BOLD, 28));
        valueText.setForeground(positive ? ACCENT_GREEN : ACCENT_RED);
        
        card.add(labelText, BorderLayout.NORTH);
        card.add(valueText, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createPortfolioPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(DARK_BG);
        page.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        
        JLabel title = new JLabel("My Portfolio");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(TEXT_WHITE);
        page.add(title, BorderLayout.NORTH);
        
        // Portfolio table
        String[] columns = {"Symbol", "Quantity", "Avg Cost", "Current Price", "Total Value", "Gain/Loss"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            model.addRow(new Object[]{
                item.getStock().getSymbol(),
                item.getQuantity(),
                String.format("₹%.2f", item.getPurchasePrice()),
                String.format("₹%.2f", item.getStock().getCurrentPrice()),
                String.format("₹%.2f", item.getTotalValue()),
                String.format("₹%.2f", item.getGainLoss())
            });
        }
        
        JTable table = new JTable(model);
        table.setBackground(CARD_BG);
        table.setForeground(TEXT_WHITE);
        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setRowHeight(40);
        table.setGridColor(new Color(51, 51, 51));
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }
    
    private JPanel createMarketPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(DARK_BG);
        page.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        
        JLabel title = new JLabel("📊 Stock Market");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(TEXT_WHITE);
        page.add(title, BorderLayout.NORTH);
        
        // Stock grid
        JPanel stockGrid = new JPanel(new GridLayout(0, 3, 20, 20));
        stockGrid.setBackground(DARK_BG);
        stockGrid.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));
        
        for (String[] stock : stockDatabase) {
            stockGrid.add(createStockCard(stock[0], stock[1], stock[2]));
        }
        
        JScrollPane scrollPane = new JScrollPane(stockGrid);
        scrollPane.setBorder(null);
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }
    
    private JPanel createStockCard(String symbol, String name, String category) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 51, 51)),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        JLabel symbolLabel = new JLabel(symbol);
        symbolLabel.setFont(new Font("Arial", Font.BOLD, 20));
        symbolLabel.setForeground(ACCENT_PURPLE);
        
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        nameLabel.setForeground(TEXT_GRAY);
        
        JButton viewChartBtn = new JButton("📊 View Chart");
        viewChartBtn.setBackground(ACCENT_PURPLE);
        viewChartBtn.setForeground(TEXT_WHITE);
        viewChartBtn.setFocusPainted(false);
        viewChartBtn.addActionListener(e -> showChartForStock(symbol));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(CARD_BG);
        topPanel.add(symbolLabel, BorderLayout.NORTH);
        topPanel.add(nameLabel, BorderLayout.CENTER);
        
        card.add(topPanel, BorderLayout.CENTER);
        card.add(viewChartBtn, BorderLayout.SOUTH);
        
        return card;
    }
    
    private void showChartForStock(String symbol) {
        // Show chart in a dialog
        JOptionPane.showMessageDialog(this, 
            "Chart for " + symbol + "\n\nShowing 6-month price history...\n\n" +
            "This would display the chart using ModernChartWindow.",
            "Stock Chart",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private JPanel createWatchlistPage() {
        return createPlaceholderPage("⭐ Watchlist", "Track stocks without buying them");
    }
    
    private JPanel createTransactionsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(DARK_BG);
        page.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        
        JLabel title = new JLabel("📜 Transaction History");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(TEXT_WHITE);
        page.add(title, BorderLayout.NORTH);
        
        // Transactions table
        String[] columns = {"Date", "Type", "Symbol", "Quantity", "Price", "Total"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        
        List<Transaction> transactions = portfolioService.getTransactions();
        for (Transaction t : transactions) {
            model.addRow(new Object[]{
                t.getTimestamp(),
                t.getType(),
                t.getSymbol(),
                t.getQuantity(),
                String.format("₹%.2f", t.getPrice()),
                String.format("₹%.2f", t.getPrice() * t.getQuantity())
            });
        }
        
        JTable table = new JTable(model);
        table.setBackground(CARD_BG);
        table.setForeground(TEXT_WHITE);
        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setRowHeight(40);
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }
    
    private JPanel createAnalyticsPage() {
        return createPlaceholderPage("📈 Analytics", "Performance charts and insights");
    }
    
    private JPanel createSettingsPage() {
        return createPlaceholderPage("⚙️ Settings", "Customize your experience");
    }
    
    private JPanel createPlaceholderPage(String title, String subtitle) {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(DARK_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(DARK_BG);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(TEXT_WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        subtitleLabel.setForeground(TEXT_GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        content.add(titleLabel);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        content.add(subtitleLabel);
        
        page.add(content);
        return page;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StockPriceService priceService = new AlphaVantageService();
            PortfolioService portfolioService = new PortfolioService(priceService);
            
            EnhancedPortfolioUI ui = new EnhancedPortfolioUI(portfolioService);
            ui.setVisible(true);
        });
    }
}

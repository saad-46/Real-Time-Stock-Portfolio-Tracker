package com.portfolio.ui;

import com.portfolio.model.*;
import com.portfolio.service.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.category.*;

/**
 * Premium Stock Portfolio Dashboard - Pure Java Swing
 * Features: Sidebar navigation, dark theme, real-time data, charts
 * NO HTML/CSS/JS - 100% Java
 */
public class PremiumStockDashboard extends JFrame {
    
    // Color Palette - IMPROVED CONTRAST
    static final Color BG = new Color(15, 15, 15);
    static final Color SIDEBAR_BG = new Color(26, 26, 46);
    static final Color CARD_BG = new Color(30, 30, 46);
    static final Color CARD_HOVER = new Color(42, 42, 62);
    static final Color ACCENT = new Color(102, 126, 234);
    static final Color ACCENT2 = new Color(118, 75, 162);
    static final Color TEXT = new Color(255, 255, 255);  // Brighter white
    static final Color TEXT_DIM = new Color(180, 180, 200);  // Lighter gray
    static final Color GREEN = new Color(74, 222, 128);
    static final Color RED = new Color(248, 113, 113);
    static final Color BORDER = new Color(60, 60, 90);  // Lighter border
    
    // Fonts with emoji support - LARGER SIZES
    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 28);
    static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 18);
    static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 15);
    static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_EMOJI = new Font("Segoe UI Emoji", Font.PLAIN, 24);
    static final Font FONT_SIDEBAR = new Font("Segoe UI", Font.BOLD, 16);  // NEW: Larger sidebar
    
    // State
    private CardLayout cardLayout;
    private JPanel contentArea;
    private JLabel pageTitle;
    private List<NavButton> navButtons = new ArrayList<>();
    private PortfolioService portfolioService;
    private JTextField searchField;
    private JPopupMenu searchPopup;
    private AssemblyAIVoiceService voiceService;
    private GroqAIService groqAIService;
    private TextToSpeechService ttsService;
    private volatile boolean isRecording = false;
    private volatile boolean isSpeaking = false;
    private String lastUserCommand = "";  // Store last command for Implement button
    
    // Stock autocomplete data
    static final String[] STOCKS = {
        "AAPL - Apple Inc", "GOOGL - Alphabet Inc", "MSFT - Microsoft Corp",
        "NVDA - NVIDIA Corp", "TSLA - Tesla Inc", "AMZN - Amazon.com",
        "META - Meta Platforms", "NFLX - Netflix", "AMD - Advanced Micro Devices",
        "INTC - Intel Corporation", "CRM - Salesforce", "PYPL - PayPal",
        "SHOP - Shopify", "UBER - Uber Technologies", "SQ - Block Inc",
        "COIN - Coinbase", "DIS - Disney", "BABA - Alibaba", "V - Visa Inc",
        "JPM - JPMorgan Chase", "BA - Boeing", "IBM - IBM Corp"
    };
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { 
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); 
            } catch (Exception ignored) {}
            
            // Show welcome screen first
            WelcomeScreen welcomeScreen = new WelcomeScreen(() -> {
                // After login, show main dashboard
                StockPriceService priceService = new AlphaVantageService();
                PortfolioService portfolioService = new PortfolioService(priceService);
                new PremiumStockDashboard(portfolioService).setVisible(true);
            });
            welcomeScreen.setVisible(true);
        });
    }
    
    public PremiumStockDashboard(PortfolioService portfolioService) {
        super("StockVault — Portfolio Dashboard");
        this.portfolioService = portfolioService;
        this.voiceService = new AssemblyAIVoiceService();
        this.groqAIService = new GroqAIService(portfolioService);
        this.ttsService = new TextToSpeechService();
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMain(), BorderLayout.CENTER);
        
        navigate("Dashboard");
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel() {
            @Override 
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, SIDEBAR_BG, 0, getHeight(), new Color(13, 13, 26));
                g2.setPaint(gp); 
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setLayout(new BorderLayout());
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, BORDER));
        
        // Logo with emoji
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        logoPanel.setOpaque(false);
        JLabel logo = new JLabel("💎 StockVault");
        logo.setFont(new Font("Segoe UI Emoji", Font.BOLD, 22));
        logo.setForeground(ACCENT);
        logoPanel.add(logo);
        sidebar.add(logoPanel, BorderLayout.NORTH);
        
        // Navigation
        JPanel navPanel = new JPanel();
        navPanel.setOpaque(false);
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        String[][] items = {
            {"📊", "Dashboard"}, 
            {"💼", "My Portfolio"}, 
            {"🌐", "Market"},
            {"⭐", "Watchlist"}, 
            {"💳", "Transactions"}, 
            {"📈", "Analytics"}, 
            {"⚙️", "Settings"}
        };
        
        for (String[] item : items) {
            NavButton btn = new NavButton(item[0], item[1]);
            btn.addActionListener(e -> navigate(item[1]));
            navPanel.add(btn);
            navButtons.add(btn);
        }
        
        sidebar.add(navPanel, BorderLayout.CENTER);
        
        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        footer.setOpaque(false);
        JLabel ver = new JLabel("v2.1.0  •  🟢 Market Open");
        ver.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11)); 
        ver.setForeground(TEXT_DIM);
        footer.add(ver);
        sidebar.add(footer, BorderLayout.SOUTH);
        
        return sidebar;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MAIN CONTENT AREA
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        
        // Top bar
        JPanel topbar = new JPanel(new BorderLayout());
        topbar.setBackground(new Color(20, 20, 40));
        topbar.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(15, 25, 15, 25)
        ));
        
        pageTitle = new JLabel("Dashboard");
        pageTitle.setFont(FONT_TITLE);
        pageTitle.setForeground(TEXT);
        topbar.add(pageTitle, BorderLayout.WEST);
        
        // Search bar
        topbar.add(buildSearchBar(), BorderLayout.EAST);
        
        main.add(topbar, BorderLayout.NORTH);
        
        // Content pages
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);
        
        contentArea.add(buildDashboardPage(), "Dashboard");
        contentArea.add(buildPortfolioPage(), "My Portfolio");
        contentArea.add(buildMarketPage(), "Market");
        contentArea.add(buildWatchlistPage(), "Watchlist");
        contentArea.add(buildTransactionsPage(), "Transactions");
        contentArea.add(buildAnalyticsPage(), "Analytics");
        contentArea.add(buildSettingsPage(), "Settings");
        
        main.add(contentArea, BorderLayout.CENTER);
        
        return main;
    }
    
    private void navigate(String page) {
        pageTitle.setText(page);
        cardLayout.show(contentArea, page);
        for (NavButton nb : navButtons) {
            nb.setActive(nb.label.equals(page));
        }
    }

    
    // ═══════════════════════════════════════════════════════════════════════
    // SEARCH BAR WITH AUTOCOMPLETE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildSearchBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setOpaque(false);
        
        searchField = new RoundedTextField(20, 20);  // ROUNDED CORNERS!
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        searchField.setForeground(TEXT);
        searchField.setBackground(CARD_BG);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(new EmptyBorder(12, 20, 12, 20));  // More padding
        searchField.setPreferredSize(new Dimension(320, 45));  // Taller
        
        // Autocomplete popup
        searchPopup = new JPopupMenu();
        searchPopup.setBackground(CARD_BG);
        searchPopup.setBorder(new LineBorder(BORDER, 1));
        
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String text = searchField.getText().trim();
                if (text.length() > 0) {
                    showAutocomplete(text);
                } else {
                    searchPopup.setVisible(false);
                }
            }
        });
        
        RoundedButton searchBtn = new RoundedButton("Search", 15);  // ROUNDED!
        searchBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setBackground(ACCENT);
        searchBtn.setPreferredSize(new Dimension(100, 45));
        searchBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchBtn.addActionListener(e -> performSearch());
        
        panel.add(searchField);
        panel.add(searchBtn);
        
        return panel;
    }
    
    private void showAutocomplete(String query) {
        searchPopup.removeAll();
        String lowerQuery = query.toLowerCase();
        int count = 0;
        
        for (String stock : STOCKS) {
            if (stock.toLowerCase().contains(lowerQuery)) {
                JMenuItem item = new JMenuItem(stock);
                item.setFont(FONT_BODY);
                item.setForeground(TEXT);
                item.setBackground(CARD_BG);
                item.setBorder(new EmptyBorder(8, 12, 8, 12));
                item.addActionListener(e -> {
                    searchField.setText(stock.split(" - ")[0]);
                    searchPopup.setVisible(false);
                });
                searchPopup.add(item);
                count++;
                if (count >= 8) break;
            }
        }
        
        if (count > 0) {
            searchPopup.show(searchField, 0, searchField.getHeight());
            searchPopup.setPreferredSize(new Dimension(searchField.getWidth(), count * 35));
            searchPopup.revalidate();
        } else {
            searchPopup.setVisible(false);
        }
    }
    
    private void performSearch() {
        String symbol = searchField.getText().trim().toUpperCase();
        if (symbol.contains(" - ")) {
            symbol = symbol.split(" - ")[0];
        }
        if (!symbol.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Searching for: " + symbol + "\n(Feature coming soon)", 
                "Search", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DASHBOARD PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildDashboardPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        // Stats cards
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 20, 0));
        statsPanel.setBackground(BG);
        statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        double totalValue = portfolioService.calculateCurrentValue();
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double profitLoss = portfolioService.calculateProfitLoss();
        double profitPercent = totalInvestment > 0 ? (profitLoss / totalInvestment) * 100 : 0;
        
        statsPanel.add(createStatCard("Total Value", String.format("₹%.2f", totalValue), GREEN));
        statsPanel.add(createStatCard("Invested", String.format("₹%.2f", totalInvestment), ACCENT));
        statsPanel.add(createStatCard("Profit/Loss", String.format("₹%.2f", profitLoss), profitLoss >= 0 ? GREEN : RED));
        statsPanel.add(createStatCard("Return", String.format("%.2f%%", profitPercent), profitLoss >= 0 ? GREEN : RED));
        
        content.add(statsPanel);
        content.add(Box.createVerticalStrut(25));
        
        // Recent stocks
        JPanel recentPanel = createCard("Recent Stocks");
        recentPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        
        String[] columns = {"Symbol", "Name", "Quantity", "Price", "Value", "Gain/Loss"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            PortfolioItem item = items.get(i);
            model.addRow(new Object[]{
                item.getStock().getSymbol(),
                item.getStock().getName(),
                item.getQuantity(),
                String.format("₹%.2f", item.getStock().getCurrentPrice()),
                String.format("₹%.2f", item.getTotalValue()),
                String.format("₹%.2f", item.getGainLoss())
            });
        }
        
        JTable table = createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(CARD_BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(0, 220));
        
        recentPanel.add(scroll, BorderLayout.CENTER);
        content.add(recentPanel);
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MY PORTFOLIO PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildPortfolioPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionPanel.setBackground(BG);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        RoundedButton addBtn = new RoundedButton("➕ Add Stock", 15);  // ROUNDED!
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        addBtn.setForeground(Color.WHITE);
        addBtn.setBackground(ACCENT);
        addBtn.setPreferredSize(new Dimension(150, 45));
        addBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> showAddStockDialog());
        
        RoundedButton refreshBtn = new RoundedButton("🔄 Refresh Prices", 15);  // ROUNDED!
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setBackground(ACCENT2);
        refreshBtn.setPreferredSize(new Dimension(180, 45));
        refreshBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refreshPrices());
        
        RoundedButton voiceBtn = new RoundedButton("🎤 Voice Assistant", 15);  // ROUNDED!
        voiceBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        voiceBtn.setForeground(Color.WHITE);
        voiceBtn.setBackground(new Color(220, 38, 127));
        voiceBtn.setPreferredSize(new Dimension(180, 45));
        voiceBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        voiceBtn.addActionListener(e -> showVoiceAssistant());
        
        actionPanel.add(addBtn);
        actionPanel.add(refreshBtn);
        actionPanel.add(voiceBtn);
        content.add(actionPanel);
        content.add(Box.createVerticalStrut(20));
        
        // Portfolio table
        JPanel tableCard = createCard("Your Holdings");
        
        String[] columns = {"Symbol", "Name", "Quantity", "Buy Price", "Current Price", "Total Value", "Gain/Loss", "Return %"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            double returnPercent = ((item.getStock().getCurrentPrice() - item.getPurchasePrice()) / item.getPurchasePrice()) * 100;
            model.addRow(new Object[]{
                item.getStock().getSymbol(),
                item.getStock().getName(),
                item.getQuantity(),
                String.format("₹%.2f", item.getPurchasePrice()),
                String.format("₹%.2f", item.getStock().getCurrentPrice()),
                String.format("₹%.2f", item.getTotalValue()),
                String.format("₹%.2f", item.getGainLoss()),
                String.format("%.2f%%", returnPercent)
            });
        }
        
        JTable table = createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(CARD_BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(null);
        
        tableCard.add(scroll, BorderLayout.CENTER);
        content.add(tableCard);
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }

    
    // ═══════════════════════════════════════════════════════════════════════
    // MARKET PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildMarketPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        // Popular stocks grid
        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 20, 20));
        gridPanel.setBackground(BG);
        
        Object[][] marketData = {
            {"AAPL", "Apple Inc", "₹198.45", "+1.23%", true},
            {"GOOGL", "Alphabet", "₹3,012.00", "+0.87%", true},
            {"MSFT", "Microsoft", "₹415.32", "+2.14%", true},
            {"NVDA", "NVIDIA", "₹875.50", "+5.67%", true},
            {"TSLA", "Tesla", "₹185.20", "-3.21%", false},
            {"AMZN", "Amazon", "₹3,780.00", "+1.55%", true},
            {"META", "Meta", "₹505.00", "+3.44%", true},
            {"NFLX", "Netflix", "₹615.30", "-0.92%", false},
            {"AMD", "AMD", "₹178.40", "+4.12%", true}
        };
        
        for (Object[] stock : marketData) {
            gridPanel.add(createStockCard(
                (String) stock[0], 
                (String) stock[1], 
                (String) stock[2], 
                (String) stock[3], 
                (Boolean) stock[4]
            ));
        }
        
        content.add(gridPanel);
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);
        
        return page;
    }
    
    private JPanel createStockCard(String symbol, String name, String price, String change, boolean isPositive) {
        JPanel card = new RoundedPanel(20);  // Rounded corners
        card.setLayout(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(20, 20, 20, 20));  // More padding
        card.setPreferredSize(new Dimension(0, 200));  // Taller cards
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        
        JLabel symbolLabel = new JLabel(symbol);
        symbolLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        symbolLabel.setForeground(TEXT);
        
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT_SMALL);
        nameLabel.setForeground(TEXT_DIM);
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.add(symbolLabel);
        leftPanel.add(nameLabel);
        
        header.add(leftPanel, BorderLayout.WEST);
        
        // Price info
        JPanel pricePanel = new JPanel();
        pricePanel.setLayout(new BoxLayout(pricePanel, BoxLayout.Y_AXIS));
        pricePanel.setOpaque(false);
        
        JLabel priceLabel = new JLabel(price);
        priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        priceLabel.setForeground(TEXT);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel changeLabel = new JLabel(change);
        changeLabel.setFont(FONT_BODY);
        changeLabel.setForeground(isPositive ? GREEN : RED);
        changeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        pricePanel.add(priceLabel);
        pricePanel.add(changeLabel);
        
        // Mini chart placeholder
        JPanel chartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g2.setColor(isPositive ? GREEN : RED);
                g2.setStroke(new BasicStroke(2));
                
                int[] xPoints = new int[20];
                int[] yPoints = new int[20];
                Random rand = new Random(symbol.hashCode());
                
                for (int i = 0; i < 20; i++) {
                    xPoints[i] = i * (getWidth() / 19);
                    yPoints[i] = getHeight() / 2 + (rand.nextInt(40) - 20);
                }
                
                for (int i = 0; i < 19; i++) {
                    g2.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
                }
            }
        };
        chartPanel.setBackground(CARD_BG);
        chartPanel.setPreferredSize(new Dimension(0, 60));
        
        // View button
        JButton viewBtn = createStyledButton("View Chart", ACCENT);
        viewBtn.addActionListener(e -> showStockChart(symbol));
        
        card.add(header, BorderLayout.NORTH);
        card.add(pricePanel, BorderLayout.CENTER);
        card.add(chartPanel, BorderLayout.SOUTH);
        
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 5));
        bottomPanel.setOpaque(false);
        bottomPanel.add(viewBtn);
        card.add(bottomPanel, BorderLayout.PAGE_END);
        
        return card;
    }
    
    private void showStockChart(String symbol) {
        JDialog dialog = new JDialog(this, symbol + " - Price Chart", true);
        dialog.setSize(900, 600);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Create sample chart
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Random rand = new Random(symbol.hashCode());
        double basePrice = 100 + rand.nextInt(200);
        
        for (int i = 0; i < 30; i++) {
            basePrice += (rand.nextDouble() - 0.5) * 10;
            dataset.addValue(basePrice, "Price", "Day " + (i + 1));
        }
        
        JFreeChart chart = ChartFactory.createLineChart(
            symbol + " - 30 Day Price History",
            "Time Period",
            "Price (₹)",
            dataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );
        
        chart.setBackgroundPaint(CARD_BG);
        chart.getTitle().setPaint(TEXT);
        chart.getTitle().setFont(FONT_TITLE);
        
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(BG);
        plot.setRangeGridlinePaint(BORDER);
        plot.setOutlineVisible(false);
        plot.getDomainAxis().setLabelPaint(TEXT);
        plot.getDomainAxis().setTickLabelPaint(TEXT);
        plot.getRangeAxis().setLabelPaint(TEXT);
        plot.getRangeAxis().setTickLabelPaint(TEXT);
        
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(BG);
        panel.add(chartPanel, BorderLayout.CENTER);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // WATCHLIST PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildWatchlistPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        JPanel card = createCard("Your Watchlist");
        
        JLabel placeholder = new JLabel("⭐ Add stocks to your watchlist to track them");
        placeholder.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        placeholder.setForeground(TEXT_DIM);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        
        card.add(placeholder, BorderLayout.CENTER);
        content.add(card);
        
        page.add(content, BorderLayout.CENTER);
        return page;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // TRANSACTIONS PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildTransactionsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        JPanel card = createCard("Transaction History");
        
        String[] columns = {"Date", "Type", "Symbol", "Quantity", "Price", "Total"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        
        List<Transaction> transactions = portfolioService.getTransactions();
        for (Transaction t : transactions) {
            model.addRow(new Object[]{
                t.getTimestamp().toString(),
                t.getType(),
                t.getSymbol(),
                t.getQuantity(),
                String.format("₹%.2f", t.getPrice()),
                String.format("₹%.2f", t.getQuantity() * t.getPrice())
            });
        }
        
        JTable table = createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(CARD_BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setBorder(null);
        
        card.add(scroll, BorderLayout.CENTER);
        content.add(card);
        
        page.add(content, BorderLayout.CENTER);
        return page;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ANALYTICS PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildAnalyticsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel(new GridLayout(2, 2, 20, 20));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        content.add(createPortfolioDistributionChart());
        content.add(createProfitLossChart());
        content.add(createStockValueChart());
        content.add(createGainLossChart());
        
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    
    private ChartPanel createPortfolioDistributionChart() {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        if (items.isEmpty()) {
            dataset.setValue("No Data", 1);
        } else {
            for (PortfolioItem item : items) {
                dataset.setValue(item.getStock().getSymbol(), item.getTotalValue());
            }
        }
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Portfolio Distribution",
            dataset, true, true, false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private ChartPanel createProfitLossChart() {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        
        double totalProfit = 0;
        double totalLoss = 0;
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            double gainLoss = item.getGainLoss();
            if (gainLoss > 0) totalProfit += gainLoss;
            else totalLoss += Math.abs(gainLoss);
        }
        
        if (totalProfit > 0) dataset.setValue("Profit", totalProfit);
        if (totalLoss > 0) dataset.setValue("Loss", totalLoss);
        if (totalProfit == 0 && totalLoss == 0) dataset.setValue("No Change", 1);
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Profit vs Loss",
            dataset, true, true, false
        );
        
        styleChart(chart);
        org.jfree.chart.plot.PiePlot plot = (org.jfree.chart.plot.PiePlot) chart.getPlot();
        plot.setSectionPaint("Profit", GREEN);
        plot.setSectionPaint("Loss", RED);
        
        return new ChartPanel(chart);
    }
    
    private ChartPanel createStockValueChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        if (items.isEmpty()) {
            dataset.addValue(0, "Value", "No Data");
        } else {
            for (PortfolioItem item : items) {
                dataset.addValue(item.getTotalValue(), "Value", item.getStock().getSymbol());
            }
        }
        
        JFreeChart chart = ChartFactory.createBarChart(
            "Stock Values",
            "Stock", "Value (₹)",
            dataset, PlotOrientation.VERTICAL,
            false, true, false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private ChartPanel createGainLossChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        if (items.isEmpty()) {
            dataset.addValue(0, "Gain/Loss", "No Data");
        } else {
            for (PortfolioItem item : items) {
                dataset.addValue(item.getGainLoss(), "Gain/Loss", item.getStock().getSymbol());
            }
        }
        
        JFreeChart chart = ChartFactory.createBarChart(
            "Gain/Loss by Stock",
            "Stock", "Gain/Loss (₹)",
            dataset, PlotOrientation.VERTICAL,
            false, true, false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(CARD_BG);
        chart.getTitle().setPaint(TEXT);
        chart.getTitle().setFont(FONT_HEADING);
        
        org.jfree.chart.plot.Plot plot = chart.getPlot();
        plot.setBackgroundPaint(BG);
        plot.setOutlineVisible(false);
        
        if (plot instanceof org.jfree.chart.plot.PiePlot) {
            org.jfree.chart.plot.PiePlot piePlot = (org.jfree.chart.plot.PiePlot) plot;
            piePlot.setLabelFont(FONT_SMALL);
            piePlot.setLabelPaint(TEXT);
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot catPlot = (CategoryPlot) plot;
            catPlot.setRangeGridlinePaint(BORDER);
            catPlot.getDomainAxis().setLabelPaint(TEXT);
            catPlot.getDomainAxis().setTickLabelPaint(TEXT);
            catPlot.getRangeAxis().setLabelPaint(TEXT);
            catPlot.getRangeAxis().setTickLabelPaint(TEXT);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // SETTINGS PAGE
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel buildSettingsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        JPanel card = createCard("Settings");
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 1, true),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        JLabel title = new JLabel("⚙️ Application Settings");
        title.setFont(new Font("Segoe UI Emoji", Font.BOLD, 18));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(20));
        
        String[] settings = {
            "Currency: Indian Rupee (₹)",
            "Theme: Dark Mode",
            "Auto-refresh: Enabled",
            "Notifications: Enabled"
        };
        
        for (String setting : settings) {
            JLabel label = new JLabel("• " + setting);
            label.setFont(FONT_BODY);
            label.setForeground(TEXT_DIM);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(label);
            card.add(Box.createVerticalStrut(10));
        }
        
        content.add(card);
        page.add(content, BorderLayout.CENTER);
        return page;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 1, true),  // Rounded already
            new EmptyBorder(20, 20, 20, 20)  // More padding
        ));
        
        if (title != null && !title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));  // Larger title
            titleLabel.setForeground(TEXT);
            titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
            card.add(titleLabel, BorderLayout.NORTH);
        }
        
        return card;
    }
    
    private JPanel createStatCard(String label, String value, Color color) {
        RoundedPanel card = new RoundedPanel(20);  // ROUNDED CORNERS!
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(25, 25, 25, 25));  // More padding
        
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.BOLD, 16));  // Larger
        labelComp.setForeground(TEXT_DIM);
        labelComp.setAlignmentX(Component.CENTER_ALIGNMENT);  // CENTER!
        
        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 32));  // MUCH LARGER!
        valueComp.setForeground(color);
        valueComp.setAlignmentX(Component.CENTER_ALIGNMENT);  // CENTER!
        
        card.add(labelComp);
        card.add(Box.createVerticalStrut(10));
        card.add(valueComp);
        
        return card;
    }
    
    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (c instanceof JLabel) {
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);  // CENTER ALL!
                }
                return c;
            }
        };
        
        table.setBackground(CARD_BG);
        table.setForeground(TEXT);
        table.setFont(new Font("Segoe UI", Font.BOLD, 15));  // BOLD!
        table.setRowHeight(45);  // TALLER!
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(CARD_HOVER);
        table.setSelectionForeground(TEXT);
        
        // Center align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 15));  // BOLD!
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        
        JTableHeader header = table.getTableHeader();
        header.setBackground(BG);
        header.setForeground(TEXT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));  // BOLD HEADER!
        header.setBorder(new MatteBorder(0, 0, 2, 0, BORDER));
        header.setPreferredSize(new Dimension(0, 50));  // TALLER HEADER!
        
        // Center align headers
        ((DefaultTableCellRenderer)header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
        
        return table;
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(FONT_BODY);
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(10, 20, 10, 20));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });
        
        return button;
    }
    
    private void showAddStockDialog() {
        JDialog dialog = new JDialog(this, "Add Stock", true);
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        JLabel title = new JLabel("Add Stock to Portfolio");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(20));
        
        JTextField symbolField = createInputField("Symbol (e.g., AAPL)");
        JTextField quantityField = createInputField("Quantity");
        JTextField priceField = createInputField("Purchase Price");
        
        panel.add(createFieldPanel("Symbol:", symbolField));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Quantity:", quantityField));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Price:", priceField));
        panel.add(Box.createVerticalStrut(25));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton cancelBtn = createStyledButton("Cancel", new Color(100, 100, 120));
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        JButton addBtn = createStyledButton("Add Stock", ACCENT);
        addBtn.addActionListener(e -> {
            try {
                String symbol = symbolField.getText().trim().toUpperCase();
                int quantity = Integer.parseInt(quantityField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                
                if (symbol.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Please enter a symbol", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                portfolioService.buyStock(symbol, symbol, quantity, price);
                JOptionPane.showMessageDialog(dialog, "Stock added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
                
                // Refresh the portfolio page
                contentArea.remove(1);
                contentArea.add(buildPortfolioPage(), "My Portfolio", 1);
                navigate("My Portfolio");
                
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter valid numbers", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        buttonPanel.add(cancelBtn);
        buttonPanel.add(addBtn);
        panel.add(buttonPanel);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    private JPanel createFieldPanel(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(FONT_BODY);
        labelComp.setForeground(TEXT);
        
        panel.add(labelComp, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JTextField createInputField(String placeholder) {
        JTextField field = new RoundedTextField(20);
        field.setFont(FONT_BODY);
        field.setForeground(TEXT);
        field.setBackground(CARD_BG);
        field.setCaretColor(TEXT);
        field.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 1, true),
            new EmptyBorder(10, 15, 10, 15)
        ));
        return field;
    }
    
    private void refreshPrices() {
        JDialog progressDialog = new JDialog(this, "Refreshing Prices", false);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.getContentPane().setBackground(BG);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel label = new JLabel("Updating stock prices...");
        label.setFont(FONT_BODY);
        label.setForeground(TEXT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        panel.add(label);
        panel.add(Box.createVerticalStrut(15));
        panel.add(progress);
        
        progressDialog.add(panel);
        progressDialog.setVisible(true);
        
        new Thread(() -> {
            portfolioService.updateAllPrices();
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                
                // Refresh pages
                contentArea.remove(0);
                contentArea.add(buildDashboardPage(), "Dashboard", 0);
                contentArea.remove(1);
                contentArea.add(buildPortfolioPage(), "My Portfolio", 1);
                
                navigate("My Portfolio");
                JOptionPane.showMessageDialog(this, "Prices updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // VOICE ASSISTANT WITH AI CONVERSATION
    // ═══════════════════════════════════════════════════════════════════════
    
    private void showVoiceAssistant() {
        JDialog dialog = new JDialog(this, "🎤 AI Voice Assistant", false);
        dialog.setSize(600, 550);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);
        
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        // Title
        JLabel title = new JLabel("🤖 StockVault AI Assistant");
        title.setFont(new Font("Segoe UI Emoji", Font.BOLD, 22));
        title.setForeground(TEXT);
        panel.add(title, BorderLayout.NORTH);
        
        // Conversation area
        JTextArea conversationArea = new JTextArea();
        conversationArea.setFont(FONT_BODY);
        conversationArea.setForeground(TEXT);
        conversationArea.setBackground(CARD_BG);
        conversationArea.setCaretColor(TEXT);
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversationArea.setEditable(false);
        conversationArea.setBorder(new EmptyBorder(15, 15, 15, 15));
        conversationArea.setText("👋 Hi! I'm your AI assistant. I can help you with:\n\n" +
            "📊 Information Queries (answered directly):\n" +
            "• 'What are trending stocks?'\n" +
            "• 'What's my portfolio worth?'\n" +
            "• 'Which stocks should I invest in?'\n" +
            "• 'How is my portfolio performing?'\n\n" +
            "⚡ Action Commands (need Implement):\n" +
            "• 'Buy 10 Apple at 150'\n" +
            "• 'Sell all Google shares'\n" +
            "• 'Remove all my stocks'\n" +
            "• 'Purchase 5 Tesla at 200'\n\n" +
            "💡 How it works:\n" +
            "• Ask questions → Get instant answers\n" +
            "• Give commands → Click ✅ Implement to execute\n\n" +
            "🎤 Speak or ⌨️ Type to start!");
        
        JScrollPane scroll = new JScrollPane(conversationArea);
        scroll.setBorder(new LineBorder(BORDER, 1, true));
        scroll.setBackground(CARD_BG);
        scroll.getViewport().setBackground(CARD_BG);
        panel.add(scroll, BorderLayout.CENTER);
        
        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setOpaque(false);
        
        JTextField voiceInput = createInputField("Type your message or click 🎤 to speak");
        voiceInput.setPreferredSize(new Dimension(0, 45));
        
        // Handle Enter key
        voiceInput.addActionListener(e -> {
            String message = voiceInput.getText().trim();
            if (!message.isEmpty()) {
                // Check if this is an action command or information query
                String lowerMessage = message.toLowerCase();
                boolean isActionCommand = lowerMessage.contains("buy") || 
                                         lowerMessage.contains("sell") || 
                                         lowerMessage.contains("purchase") ||
                                         lowerMessage.contains("remove") ||
                                         lowerMessage.contains("add") ||
                                         lowerMessage.contains("delete") ||
                                         lowerMessage.contains("clear");
                
                if (isActionCommand) {
                    // Store for Implement button
                    lastUserCommand = message;
                    conversationArea.append("\n\n👤 You: " + message);
                    conversationArea.append("\n\n💡 Click '✅ Implement' to execute this action");
                    conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                } else {
                    // Information query - process directly
                    processAIConversation(message, conversationArea, dialog);
                }
                voiceInput.setText("");
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        
        RoundedButton micBtn = new RoundedButton("🎤 Speak", 15);
        micBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        micBtn.setForeground(Color.WHITE);
        micBtn.setBackground(new Color(220, 38, 127));
        micBtn.setPreferredSize(new Dimension(120, 45));
        micBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        micBtn.addActionListener(e -> {
            if (isRecording) {
                // Stop recording
                stopRecording();
                micBtn.setText("🎤 Speak");
                micBtn.setBackground(new Color(220, 38, 127));
            } else {
                // Start recording
                micBtn.setText("⏹️ Stop");
                micBtn.setBackground(RED);
                startAIVoiceConversation(conversationArea, dialog);
            }
        });
        
        RoundedButton sendBtn = new RoundedButton("✅ Implement", 15);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setBackground(GREEN);  // Green color for implement
        sendBtn.setPreferredSize(new Dimension(140, 45));
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> {
            // Use text from field OR last voice command
            String message = voiceInput.getText().trim();
            if (message.isEmpty() && !lastUserCommand.isEmpty()) {
                message = lastUserCommand;
            }
            
            if (!message.isEmpty()) {
                // Execute action and close dialog
                executeAndClose(message, dialog);
                voiceInput.setText("");
                lastUserCommand = "";  // Clear after use
            } else {
                JOptionPane.showMessageDialog(dialog, 
                    "Please speak a command or type a message first!", 
                    "No Command", 
                    JOptionPane.WARNING_MESSAGE);
            }
        });
        
        RoundedButton clearBtn = new RoundedButton("Clear", 15);
        clearBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.setBackground(new Color(100, 100, 120));
        clearBtn.setPreferredSize(new Dimension(100, 45));
        clearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            groqAIService.clearHistory();
            conversationArea.setText("🔄 Conversation cleared!\n\n" +
                "Start a new conversation by speaking or typing...");
        });
        
        RoundedButton stopBtn = new RoundedButton("⏸️ Stop AI", 15);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setBackground(new Color(255, 87, 34));  // Orange
        stopBtn.setPreferredSize(new Dimension(120, 45));
        stopBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        stopBtn.addActionListener(e -> {
            stopAllAI();
            conversationArea.append("\n\n⏸️ AI interrupted by user");
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        });
        
        buttonPanel.add(clearBtn);
        buttonPanel.add(stopBtn);
        buttonPanel.add(micBtn);
        buttonPanel.add(sendBtn);
        
        inputPanel.add(voiceInput, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        panel.add(inputPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    /**
     * Execute action and close dialog
     */
    private void executeAndClose(String userMessage, JDialog dialog) {
        // Show progress
        JDialog progressDialog = new JDialog(this, "Executing...", true);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.getContentPane().setBackground(BG);
        progressDialog.setUndecorated(true);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new CompoundBorder(
            new LineBorder(BORDER, 2, true),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        JLabel label = new JLabel("⚡ Executing your request...");
        label.setFont(FONT_BODY);
        label.setForeground(TEXT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        panel.add(label);
        panel.add(Box.createVerticalStrut(15));
        panel.add(progress);
        
        progressDialog.add(panel);
        
        new Thread(() -> {
            try {
                // Execute the action
                String actionResult = groqAIService.executeAction(userMessage);
                
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    dialog.dispose();
                    
                    // Refresh dashboard
                    refreshDashboardAfterAction(userMessage);
                    
                    // Show result
                    if (actionResult.contains("✅")) {
                        JOptionPane.showMessageDialog(this, 
                            actionResult, 
                            "Action Completed", 
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        // Speak the result
                        ttsService.speak(actionResult.replace("✅", "Success:"));
                    } else if (actionResult.contains("❌")) {
                        JOptionPane.showMessageDialog(this, 
                            actionResult, 
                            "Action Failed", 
                            JOptionPane.ERROR_MESSAGE);
                    } else {
                        // Just information, no action
                        JOptionPane.showMessageDialog(this, 
                            actionResult, 
                            "Information", 
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    dialog.dispose();
                    JOptionPane.showMessageDialog(this, 
                        "❌ Error: " + e.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
        progressDialog.setVisible(true);
    }
    
    /**
     * Process AI conversation (text or voice)
     */
    private void processAIConversation(String userMessage, JTextArea conversationArea, JDialog dialog) {
        // Add user message to conversation
        conversationArea.append("\n\n👤 You: " + userMessage);
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        
        // Show thinking indicator
        conversationArea.append("\n\n🤖 AI: Thinking...");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        
        new Thread(() -> {
            try {
                // Get AI response
                String aiResponse = groqAIService.chat(userMessage);
                
                SwingUtilities.invokeLater(() -> {
                    // Remove "Thinking..." and add real response
                    String text = conversationArea.getText();
                    text = text.substring(0, text.lastIndexOf("🤖 AI: Thinking..."));
                    conversationArea.setText(text);
                    conversationArea.append("\n\n🤖 AI: " + aiResponse);
                    conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                    
                    // Speak the response (in background)
                    new Thread(() -> {
                        try {
                            ttsService.speak(aiResponse);
                        } catch (Exception e) {
                            System.err.println("TTS Error: " + e.getMessage());
                        }
                    }).start();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    String text = conversationArea.getText();
                    text = text.substring(0, text.lastIndexOf("🤖 AI: Thinking..."));
                    conversationArea.setText(text);
                    conversationArea.append("\n\n❌ Error: " + e.getMessage());
                    conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                });
            }
        }).start();
    }
    
    /**
     * Refresh dashboard after AI action
     */
    private void refreshDashboardAfterAction(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        // If action involved buying/selling stocks, refresh portfolio pages
        if (lowerMessage.contains("buy") || lowerMessage.contains("sell") || 
            lowerMessage.contains("add") || lowerMessage.contains("remove")) {
            
            // Refresh Dashboard page
            contentArea.remove(0);
            contentArea.add(buildDashboardPage(), "Dashboard", 0);
            
            // Refresh My Portfolio page
            contentArea.remove(1);
            contentArea.add(buildPortfolioPage(), "My Portfolio", 1);
            
            // Refresh Transactions page
            contentArea.remove(4);
            contentArea.add(buildTransactionsPage(), "Transactions", 4);
            
            System.out.println("✅ Dashboard refreshed after action");
        }
        
        // If action involved price refresh
        if (lowerMessage.contains("refresh") || lowerMessage.contains("update")) {
            // Refresh all pages
            contentArea.remove(0);
            contentArea.add(buildDashboardPage(), "Dashboard", 0);
            
            contentArea.remove(1);
            contentArea.add(buildPortfolioPage(), "My Portfolio", 1);
            
            System.out.println("✅ Dashboard refreshed after price update");
        }
        
        // Navigate to pages if requested
        if (lowerMessage.contains("show") || lowerMessage.contains("go to") || lowerMessage.contains("open")) {
            if (lowerMessage.contains("portfolio")) {
                navigate("My Portfolio");
            } else if (lowerMessage.contains("market")) {
                navigate("Market");
            } else if (lowerMessage.contains("analytic") || lowerMessage.contains("chart")) {
                navigate("Analytics");
            } else if (lowerMessage.contains("transaction") || lowerMessage.contains("history")) {
                navigate("Transactions");
            } else if (lowerMessage.contains("dashboard") || lowerMessage.contains("home")) {
                navigate("Dashboard");
            }
        }
    }
    
    /**
     * Start AI voice conversation with VAD
     */
    private void startAIVoiceConversation(JTextArea conversationArea, JDialog dialog) {
        isRecording = true;
        conversationArea.append("\n\n🎤 Listening... (speak now, will stop when you finish)");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        
        new Thread(() -> {
            try {
                // Record with Voice Activity Detection
                String transcription = voiceService.recordAndTranscribeWithVAD();
                
                if (!isRecording) {
                    // User interrupted
                    SwingUtilities.invokeLater(() -> {
                        String text = conversationArea.getText();
                        if (text.contains("🎤 Listening...")) {
                            text = text.substring(0, text.lastIndexOf("🎤 Listening..."));
                            conversationArea.setText(text);
                            conversationArea.append("\n\n⏸️ Recording stopped by user");
                            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                        }
                    });
                    return;
                }
                
                if (transcription != null && !transcription.trim().isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        // Remove "Listening..." message
                        String text = conversationArea.getText();
                        text = text.substring(0, text.lastIndexOf("🎤 Listening..."));
                        conversationArea.setText(text);
                        
                        // Add transcription
                        conversationArea.append("\n\n👤 You: " + transcription);
                        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                        
                        // Check if this is an action command or information query
                        String lowerTranscription = transcription.toLowerCase();
                        boolean isActionCommand = lowerTranscription.contains("buy") || 
                                                 lowerTranscription.contains("sell") || 
                                                 lowerTranscription.contains("purchase") ||
                                                 lowerTranscription.contains("remove") ||
                                                 lowerTranscription.contains("add") ||
                                                 lowerTranscription.contains("delete") ||
                                                 lowerTranscription.contains("clear");
                        
                        if (isActionCommand) {
                            // Store command for Implement button
                            lastUserCommand = transcription;
                            
                            // Show message to click Implement
                            conversationArea.append("\n\n💡 Click '✅ Implement' to execute this action");
                            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                            
                            // Speak the transcription back
                            new Thread(() -> {
                                try {
                                    ttsService.speak("I heard: " + transcription + ". Click Implement to execute.");
                                } catch (Exception e) {
                                    System.err.println("TTS Error: " + e.getMessage());
                                }
                            }).start();
                        } else {
                            // Information query - get AI response directly
                            conversationArea.append("\n\n🤖 AI: Thinking...");
                            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                            
                            new Thread(() -> {
                                try {
                                    String aiResponse = groqAIService.chat(transcription);
                                    
                                    SwingUtilities.invokeLater(() -> {
                                        // Remove "Thinking..."
                                        String txt = conversationArea.getText();
                                        txt = txt.substring(0, txt.lastIndexOf("🤖 AI: Thinking..."));
                                        conversationArea.setText(txt);
                                        conversationArea.append("\n\n🤖 AI: " + aiResponse);
                                        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                                        
                                        // Speak the response
                                        new Thread(() -> {
                                            try {
                                                ttsService.speak(aiResponse);
                                            } catch (Exception e) {
                                                System.err.println("TTS Error: " + e.getMessage());
                                            }
                                        }).start();
                                    });
                                } catch (Exception e) {
                                    SwingUtilities.invokeLater(() -> {
                                        String txt = conversationArea.getText();
                                        txt = txt.substring(0, txt.lastIndexOf("🤖 AI: Thinking..."));
                                        conversationArea.setText(txt);
                                        conversationArea.append("\n\n❌ Error: " + e.getMessage());
                                        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                                    });
                                }
                            }).start();
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        String text = conversationArea.getText();
                        text = text.substring(0, text.lastIndexOf("🎤 Listening..."));
                        conversationArea.setText(text);
                        conversationArea.append("\n\n❌ No speech detected. Please try again.");
                        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                    });
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    String text = conversationArea.getText();
                    if (text.contains("🎤 Listening...")) {
                        text = text.substring(0, text.lastIndexOf("🎤 Listening..."));
                        conversationArea.setText(text);
                        
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && errorMsg.contains("Microphone")) {
                            conversationArea.append("\n\n❌ Microphone Error\n" +
                                "Please check:\n" +
                                "1. Microphone is connected\n" +
                                "2. Microphone permissions are granted\n" +
                                "3. No other app is using the microphone");
                        } else {
                            conversationArea.append("\n\n❌ Error: " + errorMsg);
                        }
                        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                    }
                });
            } finally {
                isRecording = false;
            }
        }).start();
    }
    
    /**
     * Stop recording
     */
    private void stopRecording() {
        isRecording = false;
        voiceService.stopRecording();
    }
    
    /**
     * Stop all AI activities (recording and speaking)
     */
    private void stopAllAI() {
        isRecording = false;
        isSpeaking = false;
        voiceService.stopRecording();
        ttsService.stop();
    }
    
    
    // ═══════════════════════════════════════════════════════════════════════
    // CUSTOM COMPONENTS
    // ═══════════════════════════════════════════════════════════════════════
    
    class NavButton extends JButton {
        String label;
        boolean active = false;
        
        NavButton(String icon, String label) {
            super(icon + "  " + label);
            this.label = label;
            
            setFont(FONT_SIDEBAR);  // Larger font
            setForeground(TEXT_DIM);
            setBackground(SIDEBAR_BG);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(15, 25, 15, 25));  // More padding
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));  // Taller buttons
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!active) {
                        setForeground(TEXT);
                        setBackground(CARD_BG);
                        setContentAreaFilled(true);
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    if (!active) {
                        setForeground(TEXT_DIM);
                        setContentAreaFilled(false);
                    }
                }
            });
        }
        
        void setActive(boolean active) {
            this.active = active;
            if (active) {
                setForeground(ACCENT);
                setFont(new Font("Segoe UI", Font.BOLD, 17));  // BOLD when active
                setBackground(CARD_BG);
                setContentAreaFilled(true);
            } else {
                setForeground(TEXT_DIM);
                setFont(FONT_SIDEBAR);
                setContentAreaFilled(false);
            }
        }
    }
    
    class RoundedTextField extends JTextField {
        private int radius;
        
        RoundedTextField(int columns) {
            this(columns, 10);
        }
        
        RoundedTextField(int columns, int radius) {
            super(columns);
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
    }
    
    // Rounded Panel for cards
    class RoundedPanel extends JPanel {
        private int radius;
        
        RoundedPanel(int radius) {
            super();
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
    }
    
    // Rounded Button class
    class RoundedButton extends JButton {
        private int radius;
        
        RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (getModel().isPressed()) {
                g2.setColor(getBackground().darker());
            } else if (getModel().isRollover()) {
                g2.setColor(getBackground().brighter());
            } else {
                g2.setColor(getBackground());
            }
            
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
    }
}

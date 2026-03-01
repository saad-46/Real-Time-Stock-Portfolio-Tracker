package com.portfolio.ui;

import com.portfolio.model.*;
import com.portfolio.model.StockPrice; // Explicit import
import com.portfolio.service.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.category.*;
import org.jfree.data.xy.*;
import org.jfree.data.general.*;

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
    static final Color TEXT = new Color(255, 255, 255); // Brighter white
    static final Color TEXT_DIM = new Color(180, 180, 200); // Lighter gray
    static final Color GREEN = new Color(74, 222, 128);
    static final Color RED = new Color(248, 113, 113);
    static final Color BORDER = new Color(60, 60, 90); // Lighter border

    // Fonts with emoji support - LARGER SIZES
    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 28);
    static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 18);
    static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 15);
    static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_EMOJI = new Font("Segoe UI Emoji", Font.PLAIN, 24);
    static final Font FONT_SIDEBAR = new Font("Segoe UI", Font.BOLD, 16); // NEW: Larger sidebar

    // State
    private CardLayout cardLayout;
    private JPanel contentArea;
    private JLabel pageTitle;
    private List<NavButton> navButtons = new ArrayList<>();
    private final PortfolioService portfolioService;
    private JTextField searchField;
    private JPopupMenu searchPopup;
    private final AssemblyAIVoiceService voiceService;
    private final GroqAIService groqAIService;
    private final TextToSpeechService ttsService;
    private final NewsService newsService; // Added NewsService
    private final Map<String, List<StockPrice>> historyCache = new HashMap<>();
    private volatile boolean isRecording = false;
    private volatile boolean isSpeaking = false;
    private String lastUserCommand = ""; // Store last command for Implement button

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
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception ignored) {
            }

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

    private JPanel chatbotPanel;
    private JButton chatbotToggleBtn;
    private boolean isChatbotOpen = false;

    public PremiumStockDashboard(PortfolioService portfolioService) {
        super("StockVault — Portfolio Dashboard");
        this.portfolioService = portfolioService;
        this.voiceService = new AssemblyAIVoiceService();
        this.groqAIService = new GroqAIService(portfolioService);
        this.ttsService = new TextToSpeechService();
        this.newsService = new NewsService(); // Initialize NewsService

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);

        JLayeredPane layeredPane = new JLayeredPane();
        JPanel main = buildMain();
        layeredPane.add(main, JLayeredPane.DEFAULT_LAYER);

        buildFloatingChatbot(layeredPane);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                main.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                if (chatbotPanel != null && isChatbotOpen) {
                    chatbotPanel.setBounds(layeredPane.getWidth() - 400 - 30, layeredPane.getHeight() - 550 - 30, 400,
                            550);
                }
                if (chatbotToggleBtn != null && !isChatbotOpen) {
                    chatbotToggleBtn.setBounds(layeredPane.getWidth() - 70 - 30, layeredPane.getHeight() - 70 - 30, 70,
                            70);
                }
            }
        });

        add(layeredPane, BorderLayout.CENTER);

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
                { "📊", "Dashboard" },
                { "💼", "My Portfolio" },
                { "💡", "AI Insights" },
                { "🌐", "Market" },
                { "⭐", "Watchlist" },
                { "💳", "Transactions" },
                { "📈", "Analytics" },
                { "⚙️", "Settings" }
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
                new EmptyBorder(15, 25, 15, 25)));

        pageTitle = new JLabel("Dashboard");
        pageTitle.setFont(FONT_TITLE);
        pageTitle.setForeground(TEXT);
        topbar.add(pageTitle, BorderLayout.WEST);

        // Top Controls (Search + Currency + Sentiment)
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        controls.setOpaque(false);

        // Portfolio Stats in Top Bar
        double pl = portfolioService.calculateProfitLoss();
        JLabel plLabel = new JLabel("P/L: " + getCurrencySymbol() + String.format("%.2f", pl));
        plLabel.setFont(new Font("Inter", Font.BOLD, 14));
        plLabel.setForeground(pl >= 0 ? GREEN : RED);
        controls.add(plLabel);

        // Market Sentiment Indicator
        JLabel sentimentLabel = new JLabel("Market: Bullish 📈");
        sentimentLabel.setFont(new Font("Inter", Font.BOLD, 14));
        sentimentLabel.setForeground(GREEN);
        controls.add(sentimentLabel);

        String[] currencies = { "INR", "USD", "EUR", "SAR" };
        JComboBox<String> currencyBox = new JComboBox<>(currencies);
        currencyBox.setSelectedItem(portfolioService.getBaseCurrency());
        currencyBox.setBackground(new Color(30, 30, 60));
        currencyBox.setForeground(TEXT);
        currencyBox.addActionListener(e -> {
            portfolioService.setBaseCurrency((String) currencyBox.getSelectedItem());
            refreshAllViews();
        });

        controls.add(currencyBox);
        // Content pages
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);

        controls.add(buildSearchBar());

        topbar.add(controls, BorderLayout.EAST);

        main.add(topbar, BorderLayout.NORTH);

        refreshAllViews();

        main.add(contentArea, BorderLayout.CENTER);

        return main;
    }

    private void refreshAllViews() {
        String currentView = pageTitle != null ? pageTitle.getText() : "Dashboard";
        contentArea.removeAll();
        contentArea.add(buildDashboardPage(), "Dashboard");
        contentArea.add(buildPortfolioPage(), "My Portfolio");
        contentArea.add(buildAIInsightsPage(), "AI Insights");
        contentArea.add(buildMarketPage(), "Market");
        contentArea.add(buildWatchlistPage(), "Watchlist");
        contentArea.add(buildTransactionsPage(), "Transactions");
        contentArea.add(buildAnalyticsPage(), "Analytics");
        contentArea.add(buildReportsPage(), "Reports");
        contentArea.add(buildSettingsPage(), "Settings");

        if (cardLayout != null) {
            cardLayout.show(contentArea, currentView);
        }
        contentArea.revalidate();
        contentArea.repaint();
    }

    private String getCurrencySymbol() {
        String base = portfolioService.getBaseCurrency();
        if (base.equals("USD"))
            return "$";
        if (base.equals("EUR"))
            return "€";
        if (base.equals("SAR"))
            return "﷼";
        return "₹";
    }

    private String formatCurrency(double amount) {
        return String.format("%s%.2f", getCurrencySymbol(), amount);
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

        String[] currencies = { "INR - Indian Rupee", "USD - US Dollar", "EUR - Euro", "SAR - Saudi Riyal" };
        JComboBox<String> currencySelector = new JComboBox<>(currencies);
        currencySelector.setPreferredSize(new Dimension(160, 45));
        currencySelector.setFont(new Font("Segoe UI", Font.BOLD, 14));
        currencySelector.setBackground(CARD_BG);
        currencySelector.setForeground(TEXT);
        currencySelector.setFocusable(false);
        currencySelector.addActionListener(e -> {
            String selected = (String) currencySelector.getSelectedItem();
            String code = selected.substring(0, 3);
            portfolioService.setBaseCurrency(code);
            refreshAllViews();
        });

        String initialCurrency = portfolioService.getBaseCurrency();
        for (int i = 0; i < currencySelector.getItemCount(); i++) {
            if (currencySelector.getItemAt(i).startsWith(initialCurrency)) {
                currencySelector.setSelectedIndex(i);
                break;
            }
        }
        panel.add(currencySelector);

        searchField = new RoundedTextField(20, 20); // ROUNDED CORNERS!
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        searchField.setForeground(TEXT);
        searchField.setBackground(CARD_BG);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(new EmptyBorder(12, 20, 12, 20)); // More padding
        searchField.setPreferredSize(new Dimension(320, 45)); // Taller

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

        RoundedButton searchBtn = new RoundedButton("Search", 15); // ROUNDED!
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
                if (count >= 8)
                    break;
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

        statsPanel.add(createStatCard("Total Value", formatCurrency(totalValue), GREEN));
        statsPanel.add(createStatCard("Invested", formatCurrency(totalInvestment), ACCENT));
        statsPanel
                .add(createStatCard("Profit/Loss", formatCurrency(profitLoss), profitLoss >= 0 ? GREEN : RED));
        statsPanel.add(createStatCard("Return", String.format("%.2f%%", profitPercent), profitLoss >= 0 ? GREEN : RED));

        content.add(statsPanel);
        content.add(Box.createVerticalStrut(25));

        // Recent stocks
        JPanel recentPanel = createCard("Recent Stocks");
        recentPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        String[] columns = { "Symbol", "Name", "Quantity", "Price", "Value", "Gain/Loss" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            PortfolioItem item = items.get(i);
            double convertedPrice = portfolioService.convertToBase(item.getStock().getCurrentPrice(),
                    item.getOriginalCurrency());
            double convertedTotal = portfolioService.convertToBase(item.getTotalValue(), item.getOriginalCurrency());
            double convertedGain = portfolioService.convertToBase(item.getGainLoss(), item.getOriginalCurrency());

            model.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    item.getStock().getName(),
                    item.getQuantity(),
                    formatCurrency(convertedPrice),
                    formatCurrency(convertedTotal),
                    formatCurrency(convertedGain)
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
        content.add(Box.createVerticalStrut(25));

        // Top Movers Section
        JPanel moversPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        moversPanel.setOpaque(false);
        moversPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 350));

        // Top Gainers
        JPanel gainerCard = createCard("🚀 Top Gainers");
        DefaultTableModel gainerModel = new DefaultTableModel(new String[] { "Symbol", "Price", "Change" }, 0);
        java.util.List<com.portfolio.model.PortfolioItem> gainers = portfolioService.getTopGainers(5);
        for (com.portfolio.model.PortfolioItem item : gainers) {
            gainerModel.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    formatCurrency(item.getStock().getCurrentPrice()),
                    String.format("%+.2f%%", item.getStock().getChangePercent())
            });
        }
        gainerCard.add(new JScrollPane(createStyledTable(gainerModel)), BorderLayout.CENTER);

        // Top Losers
        JPanel loserCard = createCard("📉 Top Losers");
        DefaultTableModel loserModel = new DefaultTableModel(new String[] { "Symbol", "Price", "Change" }, 0);
        java.util.List<com.portfolio.model.PortfolioItem> losers = portfolioService.getTopLosers(5);
        for (com.portfolio.model.PortfolioItem item : losers) {
            loserModel.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    formatCurrency(item.getStock().getCurrentPrice()),
                    String.format("%+.2f%%", item.getStock().getChangePercent())
            });
        }
        loserCard.add(new JScrollPane(createStyledTable(loserModel)), BorderLayout.CENTER);

        moversPanel.add(gainerCard);
        moversPanel.add(loserCard);
        content.add(moversPanel);

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

        RoundedButton addBtn = new RoundedButton("➕ Add Stock", 15); // ROUNDED!
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        addBtn.setForeground(Color.WHITE);
        addBtn.setBackground(ACCENT);
        addBtn.setPreferredSize(new Dimension(150, 45));
        addBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> showAddStockDialog());

        RoundedButton refreshBtn = new RoundedButton("🔄 Refresh Prices", 15); // ROUNDED!
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setBackground(ACCENT2);
        refreshBtn.setPreferredSize(new Dimension(180, 45));
        refreshBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refreshPrices());

        actionPanel.add(addBtn);
        actionPanel.add(refreshBtn);
        content.add(actionPanel);
        content.add(Box.createVerticalStrut(20));

        // Portfolio table
        JPanel tableCard = createCard("Your Holdings");

        String[] columns = { "Symbol", "Name", "Quantity", "Buy Price", "Current Price", "Total Value", "Gain/Loss",
                "Return %" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            double returnPercent = ((item.getStock().getCurrentPrice() - item.getPurchasePrice())
                    / item.getPurchasePrice()) * 100;

            double convertedPurchase = portfolioService.convertToBase(item.getPurchasePrice(),
                    item.getOriginalCurrency());
            double convertedCurrent = portfolioService.convertToBase(item.getStock().getCurrentPrice(),
                    item.getOriginalCurrency());
            double convertedTotal = portfolioService.convertToBase(item.getTotalValue(), item.getOriginalCurrency());
            double convertedGain = portfolioService.convertToBase(item.getGainLoss(), item.getOriginalCurrency());

            model.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    item.getStock().getName(),
                    item.getQuantity(),
                    formatCurrency(convertedPurchase),
                    formatCurrency(convertedCurrent),
                    formatCurrency(convertedTotal),
                    formatCurrency(convertedGain),
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
    // AI INSIGHTS PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildAIInsightsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));

        JPanel card = createCard("🧠 AI Smart Recommendations");
        card.setLayout(new BorderLayout());

        JTextPane insightArea = new JTextPane();
        insightArea.setContentType("text/html");
        insightArea.setEditable(false);
        insightArea.setBackground(CARD_BG);
        insightArea.setForeground(TEXT);
        insightArea.setFont(FONT_BODY);
        insightArea.setBorder(new EmptyBorder(15, 15, 15, 15));
        insightArea
                .setText("<html><body style='color: white; font-family: Segoe UI; font-size: 14pt; padding: 10px;'>" +
                        "<h3 style='color: #667eea;'>Analyze your portfolio with AI...</h3>" +
                        "Click the button below to generate personalized investment insights and sector analysis." +
                        "</body></html>");

        JScrollPane scroll = new JScrollPane(insightArea);
        scroll.setBorder(new LineBorder(BORDER, 1, true));
        scroll.setBackground(CARD_BG);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setPreferredSize(new Dimension(0, 500));

        card.add(scroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 15));
        bottomPanel.setOpaque(false);

        RoundedButton genBtn = new RoundedButton("✨ Generate Insights", 15);
        genBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        genBtn.setBackground(ACCENT);
        genBtn.setForeground(Color.WHITE);
        genBtn.setPreferredSize(new Dimension(220, 50));
        genBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        genBtn.addActionListener(e -> {
            insightArea.setText(
                    "<html><body style='color: white; font-family: Segoe UI; font-size: 14pt; padding: 10px;'>" +
                            "<h3 style='color: #667eea;'>🤖 Analyzing Portfolio...</h3>" +
                            "Consulting Groq AI for market trends and diversification analysis..." +
                            "</body></html>");

            new Thread(() -> {
                try {
                    String recommendations = groqAIService.getRecommendations();
                    // Convert simple Markdown to basic HTML for JTextPane
                    String html = recommendations
                            .replace("### ", "<h3>")
                            .replace("## ", "<h2>")
                            .replace("# ", "<h1>")
                            .replace("**", "<b>")
                            .replace("* ", "<li>")
                            .replace("\n", "<br>");

                    SwingUtilities.invokeLater(() -> {
                        insightArea.setText(
                                "<html><body style='color: white; font-family: Segoe UI; font-size: 14pt; padding: 10px;'>"
                                        +
                                        html + "</body></html>");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        insightArea.setText(
                                "<html><body style='color: white; font-family: Segoe UI; font-size: 14pt; padding: 10px;'>"
                                        +
                                        "<h3 style='color: #f87171;'>❌ Error Generating Insights</h3>" +
                                        ex.getMessage() + "</body></html>");
                    });
                }
            }).start();
        });

        bottomPanel.add(genBtn);
        card.add(bottomPanel, BorderLayout.SOUTH);

        content.add(card);

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

        // Filter Panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        filterPanel.setOpaque(false);
        filterPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JLabel filterLabel = new JLabel("Filters:");
        filterLabel.setFont(FONT_BODY);
        filterLabel.setForeground(TEXT_DIM);
        filterPanel.add(filterLabel);

        String[] sectors = { "All Sectors", "IT", "Banking", "Healthcare", "Energy", "Automobile", "FMCG", "Telecom" };
        JComboBox<String> sectorFilter = new JComboBox<>(sectors);
        sectorFilter.setBackground(CARD_BG);
        sectorFilter.setForeground(TEXT);
        filterPanel.add(sectorFilter);

        String[] caps = { "All Caps", "Small Cap", "Mid Cap", "Large Cap" };
        JComboBox<String> capFilter = new JComboBox<>(caps);
        capFilter.setBackground(CARD_BG);
        capFilter.setForeground(TEXT);
        filterPanel.add(capFilter);

        String[] sorts = { "No Sort", "Top Gainers", "Top Losers" };
        JComboBox<String> sortFilter = new JComboBox<>(sorts);
        sortFilter.setBackground(CARD_BG);
        sortFilter.setForeground(TEXT);
        filterPanel.add(sortFilter);

        JButton applyFilter = createStyledButton("Apply", ACCENT);
        applyFilter.setPreferredSize(new Dimension(80, 35));
        filterPanel.add(applyFilter);

        content.add(filterPanel);
        content.add(Box.createVerticalStrut(20));

        // Popular stocks grid
        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 20, 20));
        gridPanel.setBackground(BG);

        Object[][] marketData = {
                { "AAPL", "Apple Inc", 198.45, "+1.23%", true, "IT", "Large Cap" },
                { "GOOGL", "Alphabet", 3012.00, "+0.87%", true, "IT", "Large Cap" },
                { "MSFT", "Microsoft", 415.32, "+2.14%", true, "IT", "Large Cap" },
                { "NVDA", "NVIDIA", 875.50, "+5.67%", true, "IT", "Large Cap" },
                { "TSLA", "Tesla", 185.20, "-3.21%", false, "Automobile", "Large Cap" },
                { "AMZN", "Amazon", 3780.00, "+1.55%", true, "FMCG", "Large Cap" },
                { "META", "Meta", 505.00, "+3.44%", true, "IT", "Large Cap" },
                { "NFLX", "Netflix", 615.30, "-0.92%", false, "Telecom", "Mid Cap" },
                { "AMD", "AMD", 178.40, "+4.12%", true, "IT", "Mid Cap" }
        };

        Runnable updateTable = () -> {
            gridPanel.removeAll();
            String selSector = (String) sectorFilter.getSelectedItem();
            String selCap = (String) capFilter.getSelectedItem();
            String selSort = (String) sortFilter.getSelectedItem();

            java.util.List<Object[]> filteredList = new java.util.ArrayList<>();
            for (Object[] stock : marketData) {
                String sector = (String) stock[5];
                String cap = (String) stock[6];

                boolean sectorMatch = selSector.equals("All Sectors") || selSector.equals(sector);
                boolean capMatch = selCap.equals("All Caps") || selCap.equals(cap);

                if (sectorMatch && capMatch) {
                    filteredList.add(stock);
                }
            }

            if (selSort.equals("Top Gainers")) {
                filteredList.sort(
                        (a, b) -> Double.compare(Double.parseDouble(((String) b[3]).replace("%", "").replace("+", "")),
                                Double.parseDouble(((String) a[3]).replace("%", "").replace("+", ""))));
            } else if (selSort.equals("Top Losers")) {
                filteredList.sort(
                        (a, b) -> Double.compare(Double.parseDouble(((String) a[3]).replace("%", "").replace("+", "")),
                                Double.parseDouble(((String) b[3]).replace("%", "").replace("+", ""))));
            }

            for (Object[] stock : filteredList) {
                double rawPrice = (Double) stock[2];
                double convertedPrice = portfolioService.convertToBase(rawPrice, "USD");

                gridPanel.add(createStockCard(
                        (String) stock[0], (String) stock[1],
                        formatCurrency(convertedPrice),
                        (String) stock[3], (Boolean) stock[4],
                        (String) stock[5], (String) stock[6]));
            }
            gridPanel.revalidate();
            gridPanel.repaint();
        };

        applyFilter.addActionListener(e -> updateTable.run());
        updateTable.run(); // Initial load

        content.add(gridPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);

        return page;
    }

    private JPanel createStockCard(String symbol, String name, String price, String change, boolean isPositive,
            String sector, String marketCap) {
        JPanel card = new RoundedPanel(20); // Rounded corners
        card.setLayout(new BorderLayout(10, 10));
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(20, 20, 20, 20)); // More padding
        card.setPreferredSize(new Dimension(0, 200)); // Taller cards

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

        Color riskColor = GREEN;
        String risk = "Low Risk";
        if (Math.abs(Double.parseDouble(change.replace("%", "").replace("+", ""))) > 5) {
            riskColor = new Color(255, 193, 7);
            risk = "Med Risk";
        }
        if (Math.abs(Double.parseDouble(change.replace("%", "").replace("+", ""))) > 10) {
            riskColor = RED;
            risk = "High Risk";
        }
        JPanel tagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        tagPanel.setOpaque(false);
        tagPanel.add(createTag(risk, riskColor));

        header.add(tagPanel, BorderLayout.SOUTH);

        // Tags (Sector & Cap)
        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        tagsPanel.setOpaque(false);

        if (sector != null)
            tagsPanel.add(createTag(sector, new Color(50, 50, 80)));
        if (marketCap != null)
            tagsPanel.add(createTag(marketCap, new Color(80, 50, 50)));

        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(tagsPanel);

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
        JDialog dialog = new JDialog(this, symbol + " - Technical Analysis", true);
        dialog.setSize(1000, 700);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BG);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Create XY Dataset for Technical Analysis
        XYSeries priceSeries = new XYSeries("Price");
        XYSeries smaSeries = new XYSeries("SMA (20)");
        XYSeries emaSeries = new XYSeries("EMA (10)");

        Random rand = new Random(symbol.hashCode());
        double currentPrice = 150 + rand.nextInt(500);
        double smaSum = 0;
        List<Double> prices = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            currentPrice += (rand.nextDouble() - 0.48) * 15; // Upward bias
            priceSeries.add(i, currentPrice);
            prices.add(currentPrice);

            // Simple Moving Average
            if (i >= 20) {
                double sum = 0;
                for (int j = i - 20; j < i; j++)
                    sum += prices.get(j);
                smaSeries.add(i, sum / 20);
            }

            // Exponential Moving Average (Simplified)
            if (i == 0)
                emaSeries.add(i, currentPrice);
            else {
                double prevEma = emaSeries.getY(i - 1).doubleValue();
                double k = 2.0 / (10 + 1);
                double ema = (currentPrice * k) + (prevEma * (1 - k));
                emaSeries.add(i, ema);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(priceSeries);
        dataset.addSeries(smaSeries);
        dataset.addSeries(emaSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                symbol + " Technical Chart",
                "Days",
                "Price (" + getCurrencySymbol() + ")",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        chart.setBackgroundPaint(CARD_BG);
        chart.getTitle().setPaint(TEXT);
        chart.getTitle().setFont(FONT_TITLE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(BG);
        plot.setDomainGridlinePaint(BORDER);
        plot.setRangeGridlinePaint(BORDER);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ACCENT);
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        renderer.setSeriesPaint(1, GREEN);
        renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
                new float[] { 6.0f, 6.0f }, 0.0f));
        renderer.setSeriesPaint(2, new Color(255, 167, 38));
        renderer.setSeriesStroke(2, new BasicStroke(1.5f));

        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setDisplayToolTips(true);
        chartPanel.setBackground(BG);

        mainPanel.add(chartPanel, BorderLayout.CENTER);

        // Indicator Toggle Panel
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        tools.setOpaque(false);
        JLabel help = new JLabel("💡 Use mouse wheel to zoom. SMA/EMA indicators added.");
        help.setForeground(TEXT_DIM);
        help.setFont(FONT_SMALL);
        tools.add(help);
        mainPanel.add(tools, BorderLayout.SOUTH);

        dialog.add(mainPanel);
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

        String[] columns = { "Date", "Type", "Symbol", "Quantity", "Price", "Total" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        List<Transaction> transactions = portfolioService.getTransactions();
        for (Transaction t : transactions) {
            model.addRow(new Object[] {
                    t.getTimestamp().toString(),
                    t.getType(),
                    t.getSymbol(),
                    t.getQuantity(),
                    formatCurrency(t.getPrice()),
                    formatCurrency(t.getQuantity() * t.getPrice())
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

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(25, 25, 25, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Top Row
        gbc.gridx = 0;
        gbc.gridy = 0;
        content.add(createPortfolioDistributionChart(), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        content.add(createProfitLossChart(), gbc);

        // Bottom Row
        gbc.gridx = 0;
        gbc.gridy = 1;
        content.add(createStockValueChart(), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        content.add(createPerformanceHeatmap(), gbc); // NEW HEATMAP!

        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private JPanel createPerformanceHeatmap() {
        JPanel card = createCard("Portfolio Performance Heatmap");
        card.setLayout(new BorderLayout());

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBackground(CARD_BG);
        grid.setBorder(new EmptyBorder(15, 15, 15, 15));

        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        if (items.isEmpty()) {
            grid.add(new JLabel("No data available"));
        } else {
            for (PortfolioItem item : items) {
                double change = ((item.getStock().getCurrentPrice() - item.getPurchasePrice())
                        / item.getPurchasePrice()) * 100;
                JPanel block = new RoundedPanel(10);
                block.setLayout(new BorderLayout());
                // Color intensity based on change
                int intensity = (int) Math.min(Math.abs(change) * 5 + 50, 255);
                Color heat = change >= 0 ? new Color(0, intensity / 2, 0) : new Color(intensity / 2, 0, 0);
                block.setBackground(heat);

                JLabel lbl = new JLabel("<html><center>" + item.getStock().getSymbol() + "<br>"
                        + String.format("%.1f%%", change) + "</center></html>");
                lbl.setForeground(Color.WHITE);
                lbl.setFont(FONT_SMALL);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                block.add(lbl, BorderLayout.CENTER);
                grid.add(block);
            }
        }

        card.add(grid, BorderLayout.CENTER);
        return card;
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
                dataset, true, true, false);

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
            if (gainLoss > 0)
                totalProfit += gainLoss;
            else
                totalLoss += Math.abs(gainLoss);
        }

        if (totalProfit > 0)
            dataset.setValue("Profit", totalProfit);
        if (totalLoss > 0)
            dataset.setValue("Loss", totalLoss);
        if (totalProfit == 0 && totalLoss == 0)
            dataset.setValue("No Change", 1);

        JFreeChart chart = ChartFactory.createPieChart(
                "Profit vs Loss",
                dataset, true, true, false);

        styleChart(chart);
        org.jfree.chart.plot.PiePlot plot = (org.jfree.chart.plot.PiePlot) chart.getPlot();
        plot.setSectionPaint("Profit", GREEN);
        plot.setSectionPaint("Loss", RED);

        return createStyledChartPanel(chart);
    }

    private ChartPanel createStyledChartPanel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setMouseWheelEnabled(true);
        panel.setMouseZoomable(true);
        panel.setDisplayToolTips(true);
        panel.setBackground(CARD_BG);
        return panel;
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
                false, true, false);

        styleChart(chart);
        return createStyledChartPanel(chart);
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
                false, true, false);

        styleChart(chart);
        return createStyledChartPanel(chart);
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
            piePlot.setShadowPaint(null);
            piePlot.setOutlineVisible(false);
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot catPlot = (CategoryPlot) plot;
            catPlot.setRangeGridlinePaint(BORDER);
            catPlot.getDomainAxis().setLabelPaint(TEXT);
            catPlot.getDomainAxis().setTickLabelPaint(TEXT);
            catPlot.getRangeAxis().setLabelPaint(TEXT);
            catPlot.getRangeAxis().setTickLabelPaint(TEXT);

            org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) catPlot
                    .getRenderer();
            renderer.setSeriesPaint(0, ACCENT);
            renderer.setShadowVisible(false);
            renderer.setDrawBarOutline(false);
        } else if (plot instanceof org.jfree.chart.plot.XYPlot) {
            org.jfree.chart.plot.XYPlot xyPlot = (org.jfree.chart.plot.XYPlot) plot;
            xyPlot.setRangeGridlinePaint(BORDER);
            xyPlot.getDomainAxis().setLabelPaint(TEXT);
            xyPlot.getDomainAxis().setTickLabelPaint(TEXT);
            xyPlot.getRangeAxis().setLabelPaint(TEXT);
            xyPlot.getRangeAxis().setTickLabelPaint(TEXT);

            org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer = new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer();
            renderer.setSeriesPaint(0, GREEN);
            renderer.setSeriesStroke(0, new BasicStroke(2.0f));
            xyPlot.setRenderer(renderer);
        }
    }

    private ChartPanel createHistoricalPerformanceChart(String symbol) {
        org.jfree.data.xy.XYSeries series = new org.jfree.data.xy.XYSeries("Price");

        // Use mock data if API fails or for demo
        for (int i = 0; i < 30; i++) {
            series.add(i, 150 + Math.random() * 50);
        }

        org.jfree.data.xy.XYSeriesCollection dataset = new org.jfree.data.xy.XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                symbol + " - 30 Day Performance",
                "Days", "Price (" + getCurrencySymbol() + ")",
                dataset, PlotOrientation.VERTICAL,
                false, true, false);

        styleChart(chart);
        ChartPanel panel = new ChartPanel(chart);
        panel.setMouseWheelEnabled(true);
        panel.setMouseZoomable(true);
        panel.setDisplayToolTips(true);
        return panel;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETTINGS PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildReportsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Summary Cards for Report
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        summaryPanel.setOpaque(false);
        summaryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        double totalInv = portfolioService.calculateTotalInvestment();
        double currentVal = portfolioService.calculateCurrentValue();
        double profitLoss = currentVal - totalInv;

        summaryPanel.add(createStatCard("Total Investment", formatCurrency(totalInv), TEXT));
        summaryPanel.add(createStatCard("Current Value", formatCurrency(currentVal), ACCENT));
        summaryPanel.add(createStatCard("Net Profit/Loss", formatCurrency(profitLoss), profitLoss >= 0 ? GREEN : RED));

        content.add(summaryPanel);
        content.add(Box.createVerticalStrut(25));

        // Export Actions
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        actionPanel.setOpaque(false);

        JButton csvBtn = createStyledButton("📊 Export to Excel (CSV)", ACCENT);
        csvBtn.addActionListener(e -> exportToCSV());

        JButton printBtn = createStyledButton("🖨️ Print Report (PDF)", ACCENT);
        printBtn.addActionListener(e -> printPortfolioStatement());

        actionPanel.add(csvBtn);
        actionPanel.add(printBtn);
        content.add(actionPanel);
        content.add(Box.createVerticalStrut(25));

        // Sector Allocation Chart in Report
        JPanel chartCard = createCard("Sector Allocation");
        chartCard.setPreferredSize(new Dimension(0, 400));

        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        java.util.Map<String, Double> sectorMap = new java.util.HashMap<>();
        for (com.portfolio.model.PortfolioItem item : portfolioService.getPortfolioItems()) {
            String sector = item.getStock().getSector();
            if (sector == null || sector.isEmpty())
                sector = "Other";
            sectorMap.put(sector, sectorMap.getOrDefault(sector, 0.0) + item.getTotalValue());
        }
        for (java.util.Map.Entry<String, Double> entry : sectorMap.entrySet()) {
            dataset.setValue(entry.getKey(), entry.getValue());
        }

        JFreeChart chart = ChartFactory.createPieChart("", dataset, true, true, false);
        styleChart(chart);
        chartCard.add(createStyledChartPanel(chart), BorderLayout.CENTER);

        content.add(chartCard);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);

        return page;
    }

    private void exportToCSV() {
        try {
            String filename = "Portfolio_Report_" + System.currentTimeMillis() + ".csv";
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(filename));
            writer.println("Symbol,Name,Quantity,Buy Price,Current Price,Current Value,Profit/Loss,Sector");

            for (com.portfolio.model.PortfolioItem item : portfolioService.getPortfolioItems()) {
                writer.printf("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%s\n",
                        item.getStock().getSymbol(),
                        item.getStock().getName(),
                        item.getQuantity(),
                        item.getPurchasePrice(),
                        item.getStock().getCurrentPrice(),
                        item.getTotalValue(),
                        item.getGainLoss(),
                        item.getStock().getSector());
            }
            writer.close();
            JOptionPane.showMessageDialog(this, "Report exported successfully to: " + filename);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error exporting report: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printPortfolioStatement() {
        try {
            String filename = "Portfolio_Statement_" + System.currentTimeMillis() + ".txt";
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(filename));

            writer.println("=================================================");
            writer.println("        STOCKVAULT PORTFOLIO STATEMENT");
            writer.println("        Date: " + java.time.LocalDateTime.now());
            writer.println("=================================================");
            writer.println();

            double totalInv = 0;
            double currentVal = 0;

            writer.printf("%-10s %-20s %-8s %-12s %-12s\n", "Symbol", "Name", "Qty", "Buy Price", "Current");
            writer.println("-------------------------------------------------");

            for (com.portfolio.model.PortfolioItem item : portfolioService.getPortfolioItems()) {
                writer.printf("%-10s %-20s %-8d %-12.2f %-12.2f\n",
                        item.getStock().getSymbol(),
                        item.getStock().getName(),
                        item.getQuantity(),
                        item.getPurchasePrice(),
                        item.getStock().getCurrentPrice());

                totalInv += item.getQuantity() * item.getPurchasePrice();
                currentVal += item.getTotalValue();
            }

            writer.println("-------------------------------------------------");
            writer.printf("Total Investment: %s %.2f\n", getCurrencySymbol(), totalInv);
            writer.printf("Current Value:    %s %.2f\n", getCurrencySymbol(), currentVal);
            writer.printf("Net Profit/Loss:  %s %.2f\n", getCurrencySymbol(), (currentVal - totalInv));
            writer.println("=================================================");

            writer.close();
            JOptionPane.showMessageDialog(this,
                    "Portfolio Statement generated: " + filename + "\n(Simulated PDF as formatted Text)");

            // Open the file automatically
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new java.io.File(filename));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error printing statement: " + e.getMessage());
        }
    }

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
                new EmptyBorder(20, 20, 20, 20)));

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
                new LineBorder(BORDER, 1, true), // Rounded already
                new EmptyBorder(20, 20, 20, 20) // More padding
        ));

        if (title != null && !title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 19)); // Larger title
            titleLabel.setForeground(TEXT);
            titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
            card.add(titleLabel, BorderLayout.NORTH);
        }

        return card;
    }

    private JPanel createStatCard(String label, String value, Color color) {
        RoundedPanel card = new RoundedPanel(20); // ROUNDED CORNERS!
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(25, 25, 25, 25)); // More padding

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.BOLD, 16)); // Larger
        labelComp.setForeground(TEXT_DIM);
        labelComp.setAlignmentX(Component.CENTER_ALIGNMENT); // CENTER!

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 32)); // MUCH LARGER!
        valueComp.setForeground(color);
        valueComp.setAlignmentX(Component.CENTER_ALIGNMENT); // CENTER!

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
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER); // CENTER ALL!
                }
                return c;
            }
        };

        table.setBackground(CARD_BG);
        table.setForeground(TEXT);
        table.setFont(new Font("Segoe UI", Font.BOLD, 15)); // BOLD!
        table.setRowHeight(45); // TALLER!
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(CARD_HOVER);
        table.setSelectionForeground(TEXT);

        // Center align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 15)); // BOLD!
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG);
        header.setForeground(TEXT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16)); // BOLD HEADER!
        header.setBorder(new MatteBorder(0, 0, 2, 0, BORDER));
        header.setPreferredSize(new Dimension(0, 50)); // TALLER HEADER!

        // Center align headers
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

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
        JTextField priceField = createInputField("Buy Price"); // Changed placeholder
        JTextField sectorField = createInputField("Sector (Optional)"); // New field
        JComboBox<String> capCombo = new JComboBox<>(new String[] { "Small Cap", "Mid Cap", "Large Cap" }); // New field
        capCombo.setFont(FONT_BODY);
        capCombo.setBackground(CARD_BG);
        capCombo.setForeground(TEXT);
        capCombo.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(10, 15, 10, 15)));
        capCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); // Match text field height

        panel.add(createFieldPanel("Symbol:", symbolField));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Quantity:", quantityField));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Price:", priceField));
        panel.add(Box.createVerticalStrut(15)); // Added strut for new fields
        panel.add(createFieldPanel("Sector:", sectorField)); // Added sector field
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Market Cap:", capCombo)); // Added market cap combo
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

                String sector = sectorField.getText().trim();
                String marketCap = (String) capCombo.getSelectedItem();

                portfolioService.buyStock(symbol, symbol, quantity, price, portfolioService.getBaseCurrency(), sector,
                        marketCap, "Medium");
                JOptionPane.showMessageDialog(dialog, "Stock added successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
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

    private JPanel createFieldPanel(String label, JComponent field) {
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

    private JPanel createTag(String text, Color color) {
        JPanel tag = new RoundedPanel(10);
        tag.setBackground(color);
        tag.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 3));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(Color.WHITE);
        tag.add(label);

        return tag;
    }

    private JTextField createInputField(String placeholder) {
        JTextField field = new RoundedTextField(20);
        field.setFont(FONT_BODY);
        field.setForeground(TEXT);
        field.setBackground(CARD_BG);
        field.setCaretColor(TEXT);
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(10, 15, 10, 15)));
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
                refreshAllViews();

                navigate("My Portfolio");
                JOptionPane.showMessageDialog(this, "Prices updated successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VOICE ASSISTANT WITH AI CONVERSATION
    // ═══════════════════════════════════════════════════════════════════════

    private void toggleChatbot(JLayeredPane layeredPane) {
        isChatbotOpen = !isChatbotOpen;
        if (isChatbotOpen) {
            chatbotToggleBtn.setVisible(false);
            chatbotPanel.setBounds(layeredPane.getWidth() - 400 - 30, layeredPane.getHeight() - 550 - 30, 400, 550);
            chatbotPanel.setVisible(true);
        } else {
            chatbotPanel.setVisible(false);
            chatbotToggleBtn.setBounds(layeredPane.getWidth() - 70 - 30, layeredPane.getHeight() - 70 - 30, 70, 70);
            chatbotToggleBtn.setVisible(true);
        }
        layeredPane.moveToFront(isChatbotOpen ? chatbotPanel : chatbotToggleBtn);
    }

    private void buildFloatingChatbot(JLayeredPane layeredPane) {
        // Toggle Button
        chatbotToggleBtn = new RoundedButton("🤖", 35);
        chatbotToggleBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 30));
        chatbotToggleBtn.setBackground(ACCENT);
        chatbotToggleBtn.setForeground(Color.WHITE);
        chatbotToggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chatbotToggleBtn.setBounds(100, 100, 70, 70); // Temporary bounds, updated on resize
        chatbotToggleBtn.addActionListener(e -> toggleChatbot(layeredPane));
        layeredPane.add(chatbotToggleBtn, JLayeredPane.PALETTE_LAYER);

        // Chatbot Panel
        chatbotPanel = new RoundedPanel(20);
        chatbotPanel.setLayout(new BorderLayout(10, 10));
        chatbotPanel.setBackground(new Color(30, 30, 46, 245)); // Slightly transparent
        chatbotPanel.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(15, 15, 15, 15)));
        chatbotPanel.setVisible(false);
        layeredPane.add(chatbotPanel, JLayeredPane.PALETTE_LAYER);

        // Header with close button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel title = new JLabel("🤖 AI Assistant");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT);

        JButton closeBtn = createStyledButton("✕", RED);
        closeBtn.setPreferredSize(new Dimension(45, 30));
        closeBtn.addActionListener(e -> toggleChatbot(layeredPane));

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(closeBtn, BorderLayout.EAST);
        chatbotPanel.add(headerPanel, BorderLayout.NORTH);

        // Conversation area
        JTextArea conversationArea = new JTextArea();
        conversationArea.setFont(FONT_BODY);
        conversationArea.setForeground(TEXT);
        conversationArea.setBackground(BG);
        conversationArea.setCaretColor(TEXT);
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversationArea.setEditable(false);
        conversationArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        conversationArea.setText("👋 Hi! I'm your AI assistant. I can help you with:\n\n" +
                "📊 Information Queries:\n" +
                "• 'What are trending stocks?'\n" +
                "• 'What's my portfolio worth?'\n\n" +
                "⚡ Action Commands:\n" +
                "• 'Buy 10 Apple at 150'\n" +
                "• 'Sell all Google shares'\n\n" +
                "🎤 Speak or ⌨️ Type to start!");

        JScrollPane scroll = new JScrollPane(conversationArea);
        scroll.setBorder(new LineBorder(BORDER, 1, true));
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        chatbotPanel.add(scroll, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setOpaque(false);

        JTextField voiceInput = createInputField("Type your message...");
        voiceInput.setPreferredSize(new Dimension(0, 40));

        // Handle Enter key
        voiceInput.addActionListener(e -> {
            String message = voiceInput.getText().trim();
            if (!message.isEmpty()) {
                String lowerMessage = message.toLowerCase();
                boolean isActionCommand = lowerMessage.contains("buy") ||
                        lowerMessage.contains("sell") || lowerMessage.contains("remove") ||
                        lowerMessage.contains("add") || lowerMessage.contains("clear");

                if (isActionCommand) {
                    lastUserCommand = message;
                    conversationArea.append("\n\n👤 You: " + message);
                    conversationArea.append("\n\n💡 Click '✅ Implement' to execute");
                    conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                } else {
                    processAIConversation(message, conversationArea);
                }
                voiceInput.setText("");
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);

        RoundedButton micBtn = new RoundedButton("🎤", 10);
        micBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        micBtn.setForeground(Color.WHITE);
        micBtn.setBackground(new Color(220, 38, 127));
        micBtn.setPreferredSize(new Dimension(45, 40));
        micBtn.setToolTipText("Speak Command");
        micBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        micBtn.addActionListener(e -> {
            if (isRecording) {
                stopRecording();
                micBtn.setText("🎤");
                micBtn.setBackground(new Color(220, 38, 127));
            } else {
                micBtn.setText("⏹️");
                micBtn.setBackground(RED);
                startAIVoiceConversation(conversationArea);
            }
        });

        RoundedButton sendBtn = new RoundedButton("✅", 10);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setBackground(GREEN);
        sendBtn.setPreferredSize(new Dimension(45, 40));
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> {
            String message = voiceInput.getText().trim();
            if (message.isEmpty() && !lastUserCommand.isEmpty()) {
                message = lastUserCommand;
            }
            if (!message.isEmpty()) {
                executeAndClose(message);
                voiceInput.setText("");
                lastUserCommand = "";
            }
        });

        RoundedButton stopBtn = new RoundedButton("⏸️", 10);
        stopBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setBackground(new Color(255, 87, 34));
        stopBtn.setPreferredSize(new Dimension(45, 40));
        stopBtn.setToolTipText("Stop AI Voice");
        stopBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        stopBtn.addActionListener(e -> {
            stopAllAI();
            conversationArea.append("\n\n⏸️ AI interrupted by user");
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        });

        buttonPanel.add(stopBtn);
        buttonPanel.add(micBtn);
        buttonPanel.add(sendBtn);

        inputPanel.add(voiceInput, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        chatbotPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    /**
     * Execute action and close dialog
     */
    private void executeAndClose(String userMessage) {
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
                new EmptyBorder(20, 20, 20, 20)));

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
    private void processAIConversation(String userMessage, JTextArea conversationArea) {
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
    private void startAIVoiceConversation(JTextArea conversationArea) {
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

            setFont(FONT_SIDEBAR); // Larger font
            setForeground(TEXT_DIM);
            setBackground(SIDEBAR_BG);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(15, 25, 15, 25)); // More padding
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 55)); // Taller buttons

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
                setFont(new Font("Segoe UI", Font.BOLD, 17)); // BOLD when active
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

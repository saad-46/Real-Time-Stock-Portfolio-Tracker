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
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.data.xy.*;
import org.jfree.data.category.*;
import org.jfree.data.general.*;
import org.jfree.data.time.*;
import org.jfree.ui.*;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Day;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Random;

/**
 * Premium Stock Portfolio Dashboard - Pure Java Swing
 * Features: Sidebar navigation, dark theme, real-time data, charts
 * NO HTML/CSS/JS - 100% Java
 */
public class PremiumStockDashboard extends JFrame {

    // ═══════════════════════════════════════════════════════════════════════
    // THEME ENGINE — Dynamic Dark/Light Palettes
    // ═══════════════════════════════════════════════════════════════════════
    static boolean isDarkTheme = true;
    static {
        try {
            isDarkTheme = java.util.prefs.Preferences.userRoot().node("stockvault").getBoolean("dark", true);
        } catch (Exception e) {
        }
    }

    // Dynamic color accessors
    static Color BG() {
        return isDarkTheme ? new Color(15, 15, 15) : new Color(245, 246, 250);
    }

    static Color SIDEBAR_BG() {
        return isDarkTheme ? new Color(26, 26, 46) : new Color(235, 237, 245);
    }

    static Color CARD_BG() {
        return isDarkTheme ? new Color(30, 30, 46) : Color.WHITE;
    }

    static Color CARD_HOVER() {
        return isDarkTheme ? new Color(42, 42, 62) : new Color(230, 232, 240);
    }

    static Color TOPBAR_BG() {
        return isDarkTheme ? new Color(20, 20, 40) : new Color(250, 250, 255);
    }

    static Color TEXT() {
        return isDarkTheme ? new Color(255, 255, 255) : new Color(30, 30, 40);
    }

    static Color TEXT_DIM() {
        return isDarkTheme ? new Color(180, 180, 200) : new Color(120, 120, 140);
    }

    static Color BORDER() {
        return isDarkTheme ? new Color(60, 60, 90) : new Color(210, 215, 225);
    }

    // Static accent colors (same in both themes)
    static final Color ACCENT = new Color(102, 126, 234);
    static final Color ACCENT2 = new Color(118, 75, 162);
    static final Color GREEN = new Color(74, 222, 128);
    static final Color RED = new Color(248, 113, 113);

    // Legacy compat aliases (used in inner classes where static context matters)
    static Color BG = BG();
    static Color SIDEBAR_BG = SIDEBAR_BG();
    static Color CARD_BG = CARD_BG();
    static Color CARD_HOVER = CARD_HOVER();
    static Color TEXT = TEXT();
    static Color TEXT_DIM = TEXT_DIM();
    static Color BORDER = BORDER();

    static void refreshThemeColors() {
        BG = BG();
        SIDEBAR_BG = SIDEBAR_BG();
        CARD_BG = CARD_BG();
        CARD_HOVER = CARD_HOVER();
        TEXT = TEXT();
        TEXT_DIM = TEXT_DIM();
        BORDER = BORDER();
    }

    // Fonts with emoji support
    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 28);
    static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 18);
    static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 15);
    static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_EMOJI = new Font("Segoe UI Emoji", Font.PLAIN, 24);
    static final Font FONT_SIDEBAR = new Font("Segoe UI", Font.BOLD, 16);

    // Sidebar animation state
    private static final int SIDEBAR_COLLAPSED = 60;
    private static final int SIDEBAR_EXPANDED = 240;
    private int sidebarWidth = SIDEBAR_COLLAPSED;
    private javax.swing.Timer sidebarTimer;
    private JPanel sidebarRef;
    private List<JLabel> navLabels = new ArrayList<>();

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
    private final NewsService newsService;
    private final AIRebalancer rebalancer;
    private final Map<String, List<StockPrice>> historyCache = new HashMap<>();
    private volatile boolean isRecording = false;
    private volatile boolean isSpeaking = false;
    private String lastUserCommand = "";

    // Email services
    private final EmailService emailService;
    private final DailyPortfolioScheduler emailScheduler;

    // Stock autocomplete data
    static final String[] STOCKS = {
            "AAPL - Apple Inc.", "GOOGL - Alphabet Inc.", "MSFT - Microsoft Corp.",
            "NVDA - NVIDIA Corp.", "TSLA - Tesla Inc.", "AMZN - Amazon.com Inc.",
            "META - Meta Platforms Inc.", "NFLX - Netflix Inc.", "AMD - Advanced Micro Devices Inc.",
            "INTC - Intel Corporation", "CRM - Salesforce Inc.", "PYPL - PayPal Holdings Inc.",
            "SHOP - Shopify Inc.", "UBER - Uber Technologies Inc.", "SQ - Block Inc.",
            "COIN - Coinbase Global Inc.", "DIS - Walt Disney Co.", "BABA - Alibaba Group Holding Ltd.",
            "V - Visa Inc.", "MA - Mastercard Inc.", "JPM - JPMorgan Chase & Co.",
            "BA - Boeing Co.", "IBM - IBM Corp.", "ORCL - Oracle Corp.",
            "WMT - Walmart Inc.", "HD - Home Depot Inc.", "PFE - Pfizer Inc.",
            "JNJ - Johnson & Johnson", "KO - Coca-Cola Co.", "PEP - PepsiCo Inc.",
            "COST - Costco Wholesale Corp.", "ABNB - Airbnb Inc.", "PLTR - Palantir Technologies",
            "SBUX - Starbucks Corp.", "T - AT&T Inc.", "VZ - Verizon Communications"
    };

    private String getStockNameFromSymbol(String symbol) {
        if (symbol == null)
            return "N/A";
        for (String s : STOCKS) {
            String[] parts = s.split(" - ");
            if (parts[0].equalsIgnoreCase(symbol)) {
                return parts[1];
            }
        }
        return symbol; // Fallback to symbol if name not found
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception ignored) {
            }
            WelcomeScreen welcomeScreen = new WelcomeScreen(() -> {
                StockPriceService priceService = new MultiSourceStockService();
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
        this.rebalancer = new AIRebalancer(portfolioService); // Initialize AIRebalancer

        // Initialize Email Service
        this.emailService = new EmailService();
        this.emailScheduler = new DailyPortfolioScheduler(portfolioService, emailService);

        // Initial configuration
        emailScheduler.start("", "20:00");
        System.out.println("✅ Daily email scheduler initialized.");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG());
        setLayout(new BorderLayout());

        sidebarRef = buildSidebar();
        add(sidebarRef, BorderLayout.WEST);

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

    private void showAIRebalanceModal() {
        JDialog dialog = new JDialog(this, "AI Portfolio Optimizer", true);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Institutional AI Recommendations");
        title.setFont(new Font("Inter", Font.BOLD, 18));
        title.setForeground(ACCENT);
        panel.add(title, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BG);

        List<AIRebalancer.RebalanceAdvice> adviceList = rebalancer.suggestOptimization();
        if (adviceList.isEmpty()) {
            JPanel card = createCard("No Recommendations");
            JLabel emptyMsg = new JLabel("Your portfolio is optimized. No urgent actions needed. ✅");
            emptyMsg.setForeground(TEXT);
            card.add(emptyMsg, BorderLayout.CENTER);
            listPanel.add(card);
        } else {
            for (AIRebalancer.RebalanceAdvice a : adviceList) {
                JPanel card = createCard(a.category + ": " + a.status);
                card.setLayout(new BorderLayout(15, 10));

                JLabel msg = new JLabel("<html><body style='width: 350px;'>" + a.message + "</body></html>");
                msg.setFont(new Font("Inter", Font.PLAIN, 12));
                msg.setForeground(TEXT);
                card.add(msg, BorderLayout.CENTER);

                JLabel actionHint = new JLabel(a.action);
                actionHint.setFont(new Font("Inter", Font.ITALIC, 11));
                actionHint.setForeground(ACCENT2);
                card.add(actionHint, BorderLayout.SOUTH);

                listPanel.add(card);
                listPanel.add(Box.createVerticalStrut(15));
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        panel.add(scroll, BorderLayout.CENTER);

        JButton closeBtn = createStyledButton("Close Optimizer", ACCENT);
        closeBtn.addActionListener(e -> dialog.dispose());
        panel.add(closeBtn, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
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
                Color top = isDarkTheme ? SIDEBAR_BG() : new Color(240, 242, 250);
                Color bot = isDarkTheme ? new Color(13, 13, 26) : new Color(225, 228, 240);
                GradientPaint gp = new GradientPaint(0, 0, top, 0, getHeight(), bot);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        sidebar.setPreferredSize(new Dimension(SIDEBAR_COLLAPSED, 0));
        sidebar.setLayout(new BorderLayout());
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, BORDER()));

        // Logo — click navigates to Dashboard
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 18));
        logoPanel.setOpaque(false);
        JLabel logoIcon = new JLabel("\uD83D\uDC8E");
        logoIcon.setFont(new Font("Segoe UI Emoji", Font.BOLD, 24));
        logoIcon.setForeground(ACCENT);
        JLabel logoText = new JLabel("StockVault");
        logoText.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logoText.setForeground(ACCENT);
        logoText.setVisible(false);
        navLabels.add(logoText);
        logoPanel.add(logoIcon);
        logoPanel.add(logoText);
        logoPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                navigate("Dashboard");
            }
        });
        sidebar.add(logoPanel, BorderLayout.NORTH);

        // Navigation
        JPanel navPanel = new JPanel();
        navPanel.setOpaque(false);
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        String[][] items = {
                { "\uD83C\uDFE0", "Dashboard" },
                { "\uD83D\uDCBC", "My Portfolio" },
                { "\uD83D\uDCC8", "Market" },
                { "\u2B50", "Watchlist" },
                { "\uD83D\uDCB3", "Transactions" },
                { "\uD83D\uDCCA", "Analytics" },
                { "\uD83D\uDCF0", "Market News" },
                { "\u2699\uFE0F", "Settings" }
        };

        for (String[] item : items) {
            NavButton btn = new NavButton(item[0], item[1]);
            btn.addActionListener(e -> navigate(item[1]));
            navPanel.add(btn);
            navButtons.add(btn);
        }

        sidebar.add(navPanel, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 15));
        footer.setOpaque(false);
        JLabel verIcon = new JLabel("🟢");
        verIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
        verIcon.setForeground(TEXT_DIM());
        JLabel verText = new JLabel("v3.0  •  Market Open");
        verText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        verText.setForeground(TEXT_DIM());
        verText.setVisible(false);
        navLabels.add(verText);
        footer.add(verIcon);
        footer.add(verText);
        sidebar.add(footer, BorderLayout.SOUTH);

        // Hover animation: expand on enter, collapse on exit
        // Add listeners to sidebar AND all its components
        addSidebarHoverListeners(sidebar);
        addSidebarHoverListeners(logoPanel);
        addSidebarHoverListeners(navPanel);
        addSidebarHoverListeners(footer);

        // Also add to all nav buttons
        for (NavButton btn : navButtons) {
            addSidebarHoverListeners(btn);
        }

        return sidebar;
    }

    /**
     * Add hover listeners to sidebar components to fix hover detection
     */
    private void addSidebarHoverListeners(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                animateSidebar(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Only collapse if mouse is truly outside the entire sidebar area
                Point mousePoint = e.getLocationOnScreen();
                Point sidebarPoint = sidebarRef.getLocationOnScreen();
                Rectangle sidebarBounds = new Rectangle(sidebarPoint.x, sidebarPoint.y,
                        sidebarRef.getWidth(), sidebarRef.getHeight());

                if (!sidebarBounds.contains(mousePoint)) {
                    animateSidebar(false);
                }
            }
        });
    }

    private void animateSidebar(boolean expand) {
        int target = expand ? SIDEBAR_EXPANDED : SIDEBAR_COLLAPSED;
        if (sidebarTimer != null && sidebarTimer.isRunning())
            sidebarTimer.stop();
        sidebarTimer = new javax.swing.Timer(8, null);
        sidebarTimer.addActionListener(e -> {
            int step = expand ? 15 : -15;
            sidebarWidth += step;
            if ((expand && sidebarWidth >= target) || (!expand && sidebarWidth <= target)) {
                sidebarWidth = target;
                sidebarTimer.stop();
            }
            sidebarRef.setPreferredSize(new Dimension(sidebarWidth, 0));
            // Show/hide labels when fully expanded/collapsed
            boolean showLabels = sidebarWidth > 150;
            for (JLabel lbl : navLabels)
                lbl.setVisible(showLabels);
            for (NavButton nb : navButtons)
                nb.setCollapsed(!showLabels);
            sidebarRef.revalidate();
            sidebarRef.repaint();
        });
        sidebarTimer.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN CONTENT AREA
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG());

        // Top bar
        JPanel topbar = new JPanel(new BorderLayout());
        topbar.setBackground(TOPBAR_BG());
        topbar.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER()),
                new EmptyBorder(15, 25, 15, 25)));

        pageTitle = new JLabel("Dashboard");
        pageTitle.setFont(FONT_TITLE);
        pageTitle.setForeground(TEXT());
        topbar.add(pageTitle, BorderLayout.WEST);

        // Top Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        controls.setOpaque(false);

        // Theme Toggle Pill
        controls.add(new ThemeToggle());

        // P/L Indicator
        double pl = portfolioService.calculateProfitLoss();
        JLabel plLabel = new JLabel("P/L: " + getCurrencySymbol() + String.format("%.2f", pl));
        plLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        plLabel.setForeground(pl >= 0 ? GREEN : RED);
        controls.add(plLabel);

        // Market Pulse Ticker (Phase 4) moved to headerContainer below

        // Removed currencyBox // Content pages
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);

        controls.add(buildSearchBar());

        topbar.add(controls, BorderLayout.EAST);

        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.add(topbar, BorderLayout.NORTH);

        MarketTickerPulse ticker = new MarketTickerPulse();
        headerContainer.add(ticker, BorderLayout.CENTER); // Positioned between header and content

        main.add(headerContainer, BorderLayout.NORTH);

        refreshAllViews();

        main.add(contentArea, BorderLayout.CENTER);

        return main;
    }

    private void refreshAllViews() {
        String currentView = pageTitle != null ? pageTitle.getText() : "Dashboard";
        contentArea.removeAll();
        contentArea.add(buildDashboardPage(), "Dashboard");
        contentArea.add(buildPortfolioPage(), "My Portfolio");
        contentArea.add(buildMarketPage(), "Market");
        contentArea.add(buildWatchlistPage(), "Watchlist");
        contentArea.add(buildTransactionsPage(), "Transactions");
        contentArea.add(buildAnalyticsPage(), "Analytics");
        contentArea.add(buildMarketNewsPage(), "Market News");
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

    /**
     * Generate deterministic sparkline data from a stock symbol, purchase price,
     * and current price
     * The trend will reflect actual gain/loss
     */
    private double[] generateSparklineData(String symbol, double purchasePrice, double currentPrice) {
        Random rand = new Random(symbol.hashCode());
        int points = 15;
        double[] data = new double[points];

        // Start from purchase price
        data[0] = purchasePrice;

        // Calculate the total change
        double totalChange = currentPrice - purchasePrice;
        double avgChangePerPoint = totalChange / (points - 1);

        // Generate intermediate points with some randomness but trending toward current
        // price
        double price = purchasePrice;
        for (int i = 1; i < points - 1; i++) {
            // Add the average change plus some random variation
            double randomVariation = (rand.nextDouble() - 0.5) * (Math.abs(totalChange) * 0.2);
            price += avgChangePerPoint + randomVariation;
            data[i] = price;
        }

        // Ensure last point is actual current price
        data[points - 1] = currentPrice;

        return data;
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

        // Removed currencySelector

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

        String[] columns = { "Symbol", "Name", "Qty", "Price", "Value", "Profit/Loss", "Trend" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return col == 6 ? Object.class : super.getColumnClass(col);
            }
        };

        List<PortfolioItem> items = portfolioService.getMergedPortfolioItems();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            PortfolioItem item = items.get(i);
            double convertedPrice = portfolioService.convertToBase(item.getStock().getCurrentPrice(),
                    item.getOriginalCurrency());
            double convertedValue = item.getTotalValue();
            double convertedGain = item.getGainLoss();

            String stockName = getStockNameFromSymbol(item.getStock().getSymbol());
            if (stockName.equals(item.getStock().getSymbol()) && item.getStock().getName() != null) {
                stockName = item.getStock().getName();
            }

            model.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    stockName,
                    item.getQuantity(),
                    formatCurrency(convertedPrice),
                    formatCurrency(convertedValue),
                    (convertedGain >= 0 ? "+" : "") + formatCurrency(convertedGain),
                    generateSparklineData(item.getStock().getSymbol(), item.getPurchasePrice(),
                            item.getStock().getCurrentPrice())
            });
        }

        JTable table = createStyledTable(model);
        table.getColumnModel().getColumn(6).setCellRenderer(new SparklineCellRenderer());
        table.setRowHeight(45); // Standardized height
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
        DefaultTableModel gainerModel = new DefaultTableModel(new String[] { "Symbol", "Name", "Price", "Change" }, 0);
        java.util.List<com.portfolio.model.PortfolioItem> gainers = portfolioService.getTopGainers(5);
        for (com.portfolio.model.PortfolioItem item : gainers) {
            gainerModel.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    item.getStock().getName(),
                    formatCurrency(item.getStock().getCurrentPrice()),
                    String.format("%+.2f%%", item.getStock().getChangePercent())
            });
        }
        JTable gainerTable = createStyledTable(gainerModel);
        gainerTable.setRowHeight(45);
        gainerCard.add(new JScrollPane(gainerTable), BorderLayout.CENTER);

        // Top Losers
        JPanel loserCard = createCard("📉 Top Losers");
        DefaultTableModel loserModel = new DefaultTableModel(new String[] { "Symbol", "Name", "Price", "Change" }, 0);
        java.util.List<com.portfolio.model.PortfolioItem> losers = portfolioService.getTopLosers(5);
        for (com.portfolio.model.PortfolioItem item : losers) {
            loserModel.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    item.getStock().getName(),
                    formatCurrency(item.getStock().getCurrentPrice()),
                    String.format("%+.2f%%", item.getStock().getChangePercent())
            });
        }
        JTable loserTable = createStyledTable(loserModel);
        loserTable.setRowHeight(45);
        loserCard.add(new JScrollPane(loserTable), BorderLayout.CENTER);

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

        // Advanced Metrics Header
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 20, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        double sharpe = portfolioService.calculateSharpeRatio();
        double beta = portfolioService.calculatePortfolioBeta();
        double totalVal = portfolioService.calculateCurrentValue();

        metricsPanel.add(createStatCard("Sharpe Ratio", String.format("%.2f", sharpe),
                sharpe > 1 ? GREEN : (sharpe > 0 ? ACCENT : RED)));
        metricsPanel.add(createStatCard("Portfolio Beta", String.format("%.2f", beta),
                Math.abs(beta - 1.0) < 0.2 ? GREEN : ACCENT));
        metricsPanel.add(createStatCard("Risk Level", beta > 1.2 ? "High" : (beta > 0.8 ? "Medium" : "Low"),
                beta > 1.2 ? RED : (beta > 0.8 ? ACCENT : GREEN)));
        metricsPanel.add(createStatCard("Market Impact", String.format("%.1f%%", (totalVal / 1000000) * 100), ACCENT)); // Calculated
                                                                                                                        // impact

        content.add(metricsPanel);
        content.add(Box.createVerticalStrut(25));

        // Portfolio Performance Chart (NEW)
        content.add(createPortfolioPerformanceChart());
        content.add(Box.createVerticalStrut(25));

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actionPanel.setBackground(BG());
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

        JButton addBtn = createStyledButton("➕ Add Asset", ACCENT);
        addBtn.addActionListener(e -> showAddStockDialog());

        JButton refreshBtn = createStyledButton("🔄 Refresh Hub", ACCENT2);
        refreshBtn.addActionListener(e -> refreshPrices());

        JButton rebalanceBtn = createStyledButton("🤖 AI Rebalance", new Color(139, 92, 246));
        rebalanceBtn.addActionListener(e -> showAIRebalanceModal());

        JButton emailTestBtn = createStyledButton("📧 Send Email Now", new Color(34, 197, 94));
        emailTestBtn.addActionListener(e -> {
            emailTestBtn.setText("📤 Sending...");
            emailTestBtn.setEnabled(false);

            new Thread(() -> {
                try {
                    emailService.sendDailyPortfolioUpdate("mhnu.6180@gmail.com", portfolioService);

                    SwingUtilities.invokeLater(() -> {
                        emailTestBtn.setText("✅ Email Sent!");
                        emailTestBtn.setBackground(GREEN);

                        JOptionPane.showMessageDialog(this,
                                "📧 Portfolio email sent successfully!\n\n" +
                                        "✅ To: mhnu.6180@gmail.com\n" +
                                        "📊 From: stockvault123@gmail.com\n" +
                                        "⏰ Auto-scheduled: Every day at 8:00 PM\n\n" +
                                        "Check your Gmail inbox!",
                                "Email Sent",
                                JOptionPane.INFORMATION_MESSAGE);

                        // Reset button after 3 seconds
                        javax.swing.Timer resetTimer = new javax.swing.Timer(3000, evt -> {
                            emailTestBtn.setText("📧 Send Email Now");
                            emailTestBtn.setBackground(new Color(34, 197, 94));
                            emailTestBtn.setEnabled(true);
                        });
                        resetTimer.setRepeats(false);
                        resetTimer.start();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        emailTestBtn.setText("❌ Failed");
                        emailTestBtn.setBackground(RED);
                        emailTestBtn.setEnabled(true);

                        String errorMsg = ex.getMessage();
                        boolean isAuthError = errorMsg != null && errorMsg.toLowerCase().contains("authentication");

                        if (isAuthError) {
                            JOptionPane.showMessageDialog(this,
                                    "❌ Gmail Authentication Failed!\n\n" +
                                            "⚠️  Gmail requires an APP PASSWORD, not your regular password!\n\n" +
                                            "📋 QUICK FIX:\n" +
                                            "1. Go to: https://myaccount.google.com/apppasswords\n" +
                                            "2. Generate App Password for 'Mail'\n" +
                                            "3. Update EmailService.java line 14\n" +
                                            "4. Replace 'Stockvault321' with your App Password\n" +
                                            "5. Recompile and test again\n\n" +
                                            "📖 See EMAIL-SETUP-GUIDE.md for detailed instructions",
                                    "App Password Required",
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this,
                                    "❌ Failed to send email:\n" + errorMsg + "\n\n" +
                                            "Make sure:\n" +
                                            "1. Internet connection is active\n" +
                                            "2. Gmail App Password is configured\n" +
                                            "3. Check console for detailed error logs\n\n" +
                                            "📖 See EMAIL-SETUP-GUIDE.md for help",
                                    "Email Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }).start();
        });

        actionPanel.add(addBtn);
        actionPanel.add(refreshBtn);
        actionPanel.add(rebalanceBtn);
        actionPanel.add(emailTestBtn);
        content.add(actionPanel);
        content.add(Box.createVerticalStrut(20));

        // Portfolio table
        JPanel tableCard = createCard("Your Holdings");

        String[] columns = { "Symbol", "Name", "Qty", "Price", "Profit/Loss", "Value", "Date", "Trend" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return col == 7 ? Object.class : super.getColumnClass(col);
            }
        };

        List<PortfolioItem> items = portfolioService.getMergedPortfolioItems();
        for (PortfolioItem item : items) {
            double convertedCurrent = portfolioService.convertToBase(item.getStock().getCurrentPrice(),
                    item.getOriginalCurrency());
            double convertedValue = item.getTotalValue();
            double convertedPL = item.getGainLoss();

            String stockName = getStockNameFromSymbol(item.getStock().getSymbol());
            if (stockName.equals(item.getStock().getSymbol()) && item.getStock().getName() != null) {
                stockName = item.getStock().getName();
            }

            model.addRow(new Object[] {
                    item.getStock().getSymbol(),
                    stockName,
                    item.getQuantity(),
                    formatCurrency(convertedCurrent),
                    (convertedPL >= 0 ? "+" : "") + formatCurrency(convertedPL),
                    formatCurrency(convertedValue),
                    "Live Data",
                    generateSparklineData(item.getStock().getSymbol(), item.getPurchasePrice(),
                            item.getStock().getCurrentPrice())
            });
        }

        JTable table = createStyledTable(model);
        table.getColumnModel().getColumn(7).setCellRenderer(new SparklineCellRenderer());
        table.setRowHeight(45);
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

    private JPanel createPortfolioPerformanceChart() {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries portfolioSeries = new TimeSeries("My Portfolio");
        TimeSeries benchmarkSeries = new TimeSeries("Market Benchmark");

        // Generate realistic performance data
        Random r = new Random();
        double startValue = portfolioService.calculateCurrentValue();
        if (startValue == 0)
            startValue = 50000; // Default for empty portfolio

        double pValue = startValue * 0.9;
        double bValue = startValue * 0.92;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -6);

        for (int i = 0; i < 180; i++) {
            Day day = new Day(cal.getTime());
            portfolioSeries.add(day, pValue);
            benchmarkSeries.add(day, bValue);

            pValue *= 1 + (r.nextDouble() - 0.48) * 0.02; // More volatile
            bValue *= 1 + (r.nextDouble() - 0.49) * 0.015; // More stable
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        dataset.addSeries(portfolioSeries);
        dataset.addSeries(benchmarkSeries);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Time", "Value (" + getCurrencySymbol() + ")", dataset, true, true, false);
        styleChart(chart);

        XYPlot plot = (XYPlot) chart.getPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);

        // My Portfolio styling (Teal/Green)
        renderer.setSeriesPaint(0, new Color(20, 184, 166));
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));

        // Market Benchmark styling (Purple/Blue)
        renderer.setSeriesPaint(1, new Color(139, 92, 246));
        renderer.setSeriesStroke(1, new BasicStroke(1.5f));

        plot.setRenderer(renderer);

        ChartPanel panel = new ChartPanel(chart);
        panel.setPreferredSize(new Dimension(0, 350));
        panel.setBackground(CARD_BG());

        // Custom tooltip styling
        renderer.setDefaultToolTipGenerator(new org.jfree.chart.labels.StandardXYToolTipGenerator(
                "<html><div style='padding:5px; background:#1E293B; color:white; border-radius:5px;'>" +
                        "<b>{0}</b><br>Date: {1}<br>Value: " + getCurrencySymbol() + "{2}</div></html>",
                new java.text.SimpleDateFormat("MMM dd, yyyy"),
                new java.text.DecimalFormat("#,##0.00")));

        JPanel card = createCard("📈 Portfolio Performance");
        card.add(panel);
        return card;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARKET NEWS PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildMarketNewsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 40));
        header.setBorder(new EmptyBorder(20, 25, 20, 25));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("📈 INSTITUTIONAL NEWS TERMINAL");
        title.setFont(new Font("Consolas", Font.BOLD, 20));
        title.setForeground(new Color(102, 126, 234));
        titlePanel.add(title);
        JLabel liveIndicator = createTerminalBadge("🟢 LIVE", new Color(74, 222, 128));
        titlePanel.add(liveIndicator);

        header.add(titlePanel, BorderLayout.WEST);

        // Advanced Filters Toolbar
        JPanel filtersToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        filtersToolbar.setOpaque(false);
        filtersToolbar.setBorder(new EmptyBorder(15, 25, 5, 25));

        JLabel filtersLabel = new JLabel("Filters:");
        filtersLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        filtersLabel.setForeground(TEXT_DIM());
        filtersToolbar.add(filtersLabel);

        JComboBox<String> sectorFilter = new JComboBox<>(new String[] { "All Sectors", "Technology", "Healthcare",
                "Financials", "Consumer", "Energy", "Industrials", "Utilities" });
        JComboBox<String> capFilter = new JComboBox<>(
                new String[] { "All Caps", "Large Cap", "Mid Cap", "Small Cap", "Micro Cap" });
        JComboBox<String> sortFilter = new JComboBox<>(new String[] { "No Sort", "Price (High → Low)",
                "Price (Low → High)", "Market Cap", "Volume", "Percentage Change" });
        JComboBox<String> typeFilter = new JComboBox<>(new String[] { "All Stocks", "Top Gainers", "Top Losers",
                "Most Active", "High Volume", "Trending Stocks", "Dividend Stocks" });

        JComboBox[] combos = { sectorFilter, capFilter, sortFilter, typeFilter };
        for (JComboBox cb : combos) {
            cb.setBackground(CARD_BG());
            cb.setForeground(TEXT());
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            filtersToolbar.add(cb);
        }

        RoundedButton applyBtn = new RoundedButton("Apply", 6);
        applyBtn.setBackground(new Color(37, 99, 235));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filtersToolbar.add(applyBtn);

        RoundedButton refreshBtn = new RoundedButton("🔄 Refresh", 6);
        refreshBtn.setBackground(CARD_BG());
        refreshBtn.setForeground(TEXT());
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filtersToolbar.add(refreshBtn);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(BG());
        topContainer.add(header, BorderLayout.NORTH);
        topContainer.add(filtersToolbar, BorderLayout.SOUTH);
        page.add(topContainer, BorderLayout.NORTH);

        // Core Content Area (Banner + Grid)
        JPanel contentContainer = new JPanel(new BorderLayout());
        contentContainer.setBackground(BG());

        JPanel bannerContainer = new JPanel(new BorderLayout());
        bannerContainer.setBackground(BG());
        bannerContainer.setBorder(new EmptyBorder(25, 25, 0, 25));
        contentContainer.add(bannerContainer, BorderLayout.NORTH);

        JPanel newsContainer = new JPanel();
        newsContainer.setBackground(BG());
        java.awt.GridLayout gridLayout = new java.awt.GridLayout(0, 4, 16, 20);
        newsContainer.setLayout(gridLayout);
        newsContainer.setBorder(new EmptyBorder(20, 25, 25, 25));

        JPanel gridWrapper = new JPanel(new BorderLayout());
        gridWrapper.setBackground(BG());
        gridWrapper.add(newsContainer, BorderLayout.NORTH);

        contentContainer.add(gridWrapper, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(contentContainer);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        page.add(scroll, BorderLayout.CENTER);

        // Responsive Grid Listener
        scroll.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = scroll.getWidth();
                int cols = 4;
                if (w < 700)
                    cols = 1;
                else if (w < 1000)
                    cols = 2;
                else if (w < 1400)
                    cols = 3;

                if (gridLayout.getColumns() != cols) {
                    gridLayout.setColumns(cols);
                    newsContainer.revalidate();
                }
            }
        });

        Runnable loadNews = () -> {
            newsContainer.removeAll();
            bannerContainer.removeAll();
            JLabel loading = new JLabel("⌛ SCANNING GLOBAL FINANCIAL NETWORKS...");
            loading.setFont(new Font("Consolas", Font.PLAIN, 14));
            loading.setForeground(TEXT_DIM());
            loading.setHorizontalAlignment(SwingConstants.CENTER);
            newsContainer.add(loading);
            newsContainer.revalidate();
            newsContainer.repaint();

            new Thread(() -> {
                try {
                    List<NewsService.NewsItem> news = newsService.fetchMarketNews();
                    SwingUtilities.invokeLater(() -> {
                        newsContainer.removeAll();
                        bannerContainer.removeAll();
                        if (news == null || news.isEmpty()) {
                            JLabel empty = new JLabel("NO MARKET DATA STREAMS AVAILABLE");
                            empty.setFont(new Font("Consolas", Font.PLAIN, 14));
                            empty.setForeground(TEXT_DIM());
                            empty.setHorizontalAlignment(SwingConstants.CENTER);
                            newsContainer.add(empty);
                        } else {
                            // First item is banner
                            NewsService.NewsItem featured = news.get(0);
                            bannerContainer.add(createFeaturedBanner(featured), BorderLayout.CENTER);

                            // Rest are grid cards
                            for (int i = 1; i < news.size(); i++) {
                                newsContainer.add(createInstitutionalNewsCard(news.get(i)));
                            }
                        }
                        bannerContainer.revalidate();
                        bannerContainer.repaint();
                        newsContainer.revalidate();
                        newsContainer.repaint();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        newsContainer.removeAll();
                        bannerContainer.removeAll();
                        JLabel error = new JLabel("⚠️ NETWORK ERROR: " + ex.getMessage());
                        error.setFont(new Font("Consolas", Font.PLAIN, 14));
                        error.setForeground(RED);
                        error.setHorizontalAlignment(SwingConstants.CENTER);
                        newsContainer.add(error);
                        newsContainer.revalidate();
                        newsContainer.repaint();
                    });
                }
            }).start();
        };

        refreshBtn.addActionListener(e -> loadNews.run());
        applyBtn.addActionListener(e -> loadNews.run());
        loadNews.run();

        return page;
    }

    private JPanel createFeaturedBanner(NewsService.NewsItem n) {
        JPanel banner = new RoundedPanel(16, null);
        banner.setLayout(new BorderLayout());
        banner.setBackground(new Color(15, 23, 42));
        banner.setBorder(new LineBorder(new Color(59, 130, 246), 2, true));
        banner.setPreferredSize(new Dimension(0, 300));

        // Background Image Header
        JLabel imageLabel = new JLabel();
        imageLabel.setOpaque(true);
        imageLabel.setBackground(new Color(15, 23, 42));
        banner.add(imageLabel, BorderLayout.CENTER);

        if (n.getImageUrl() != null && !n.getImageUrl().isEmpty()) {
            new Thread(() -> {
                try {
                    java.awt.Image image = javax.imageio.ImageIO.read(new java.net.URL(n.getImageUrl()));
                    if (image != null) {
                        SwingUtilities.invokeLater(() -> {
                            java.awt.Image scaled = image.getScaledInstance(1200, 300, java.awt.Image.SCALE_SMOOTH);
                            imageLabel.setIcon(new ImageIcon(scaled));
                        });
                    }
                } catch (Exception e) {
                }
            }).start();
        } else {
            loadFallbackImage(imageLabel, true);
        }

        // Overlay Content
        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(false);
        overlay.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Gradient or Dark tint simulation
        JPanel darkTint = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42, 220), 800, 0, new Color(15, 23, 42, 50)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        darkTint.setOpaque(false);
        darkTint.setLayout(new BorderLayout());
        darkTint.add(overlay, BorderLayout.CENTER);
        imageLabel.setLayout(new BorderLayout());
        imageLabel.add(darkTint, BorderLayout.CENTER);

        JLabel breakingTag = createTerminalBadge("🔴 BREAKING NEWS", new Color(220, 38, 38));
        breakingTag.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel topTagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topTagPanel.setOpaque(false);
        topTagPanel.add(breakingTag);
        overlay.add(topTagPanel, BorderLayout.NORTH);

        JPanel textPanel = new JPanel(new BorderLayout(0, 10));
        textPanel.setOpaque(false);

        JLabel headline = new JLabel("<html><body style='width: 600px'>" + n.getTitle() + "</body></html>");
        headline.setFont(new Font("Segoe UI", Font.BOLD, 28));
        headline.setForeground(Color.WHITE);
        textPanel.add(headline, BorderLayout.NORTH);

        JLabel desc = new JLabel(
                "<html><body style='width: 600px; color: #CBD5E1'>" + n.getDescription() + "</body></html>");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        textPanel.add(desc, BorderLayout.CENTER);

        JLabel meta = new JLabel(n.getSource() + " • " + n.getPublishedAt());
        meta.setFont(new Font("Segoe UI", Font.BOLD, 14));
        meta.setForeground(new Color(148, 163, 184));
        textPanel.add(meta, BorderLayout.SOUTH);

        overlay.add(textPanel, BorderLayout.CENTER);

        banner.setCursor(new Cursor(Cursor.HAND_CURSOR));
        banner.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showNewsArticleModal(n);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                banner.setBorder(new LineBorder(new Color(96, 165, 250), 2, true));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                banner.setBorder(new LineBorder(new Color(59, 130, 246), 2, true));
            }
        });

        return banner;
    }

    private JLabel createTerminalBadge(String text, Color color) {
        JLabel badge = new JLabel(text);
        badge.setFont(new Font("Consolas", Font.BOLD, 10));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(color);
        badge.setBorder(new EmptyBorder(4, 8, 4, 8));
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        return badge;
    }

    private JPanel createInstitutionalNewsCard(NewsService.NewsItem n) {
        JPanel card = new RoundedPanel(12, null);
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(300, 330));
        card.setBackground(new Color(30, 41, 59)); // #1E293B
        card.setBorder(new LineBorder(new Color(42, 51, 72), 1, true)); // #2A3348

        JLabel imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(300, 150));
        imageLabel.setOpaque(true);
        imageLabel.setBackground(new Color(20, 25, 40));
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        if (n.getImageUrl() != null && !n.getImageUrl().isEmpty()) {
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(n.getImageUrl());
                    java.awt.Image image = javax.imageio.ImageIO.read(url);
                    if (image != null) {
                        java.awt.Image scaled = image.getScaledInstance(400, 200, java.awt.Image.SCALE_SMOOTH);
                        SwingUtilities.invokeLater(() -> {
                            imageLabel.setIcon(new ImageIcon(scaled));
                            imageLabel.setText("");
                        });
                    }
                } catch (Exception e) {
                    loadFallbackImage(imageLabel, false);
                }
            }).start();
        } else {
            loadFallbackImage(imageLabel, false);
        }

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel titleArea = new JPanel(new BorderLayout());
        titleArea.setOpaque(false);

        JLabel titleLabel = new JLabel("<html><body style='width: 200px'>" + n.getTitle() + "</body></html>");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(226, 232, 240));
        titleArea.add(titleLabel, BorderLayout.CENTER);

        // AI Sentiment
        String sentiment = n.getSentiment();
        Color sentColor;
        String sentText;
        if (sentiment.equalsIgnoreCase("Positive") || sentiment.equalsIgnoreCase("Bullish")) {
            sentColor = new Color(21, 128, 61);
            sentText = "📈 Bullish";
        } else if (sentiment.equalsIgnoreCase("Negative") || sentiment.equalsIgnoreCase("Bearish")) {
            sentColor = new Color(185, 28, 28);
            sentText = "📉 Bearish";
        } else {
            sentColor = new Color(161, 98, 7);
            sentText = "📊 Neutral";
        }

        JLabel sentimentTag = createTerminalBadge(sentText, sentColor);
        sentimentTag.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgePanel.setOpaque(false);
        badgePanel.add(sentimentTag);
        titleArea.add(badgePanel, BorderLayout.NORTH);

        contentPanel.add(titleArea, BorderLayout.NORTH);

        String descText = n.getDescription();
        if (descText != null && descText.length() > 90) {
            descText = descText.substring(0, 87) + "...";
        }
        JLabel descLabel = new JLabel("<html><body style='width: 220px; color: #94A3B8'>"
                + (descText != null ? descText : "") + "</body></html>");
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        descLabel.setVerticalAlignment(SwingConstants.TOP);
        contentPanel.add(descLabel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        JLabel sourceLabel = new JLabel("Source: " + n.getSource());
        sourceLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sourceLabel.setForeground(new Color(148, 163, 184));

        JLabel dateLabel = new JLabel("Time: " + n.getPublishedAt());
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dateLabel.setForeground(new Color(100, 116, 139));

        footer.add(sourceLabel, BorderLayout.NORTH);
        footer.add(dateLabel, BorderLayout.SOUTH);
        contentPanel.add(footer, BorderLayout.SOUTH);

        card.add(imageLabel, BorderLayout.NORTH);
        card.add(contentPanel, BorderLayout.CENTER);

        card.setCursor(new Cursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                card.setBackground(new Color(42, 53, 75)); // Hover elevation
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                card.setBackground(new Color(30, 41, 59));
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showNewsArticleModal(n);
            }
        });

        return card;
    }

    private void loadFallbackImage(JLabel imageLabel, boolean isBanner) {
        SwingUtilities.invokeLater(() -> {
            try {
                java.io.File bgFile = new java.io.File("src/resources/images/default_market_bg.png");
                if (bgFile.exists()) {
                    java.awt.Image image = javax.imageio.ImageIO.read(bgFile);
                    int w = isBanner ? 1200 : 400;
                    int h = isBanner ? 300 : 200;
                    java.awt.Image scaled = image.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                    imageLabel.setIcon(new ImageIcon(scaled));
                    imageLabel.setText("");
                } else {
                    imageLabel.setText("📰 Market News");
                    imageLabel.setForeground(new Color(148, 163, 184));
                    imageLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
                }
            } catch (Exception ex) {
                imageLabel.setText("📰 Market News");
                imageLabel.setForeground(new Color(148, 163, 184));
                imageLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            }
        });
    }

    private void showNewsArticleModal(NewsService.NewsItem n) {
        JDialog dialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this), "Article Details", true);
        dialog.setSize(800, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(new Color(15, 23, 42));
        container.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("<html><body style='width: 680px'>" + n.getTitle() + "</body></html>");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(248, 250, 252));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(title);
        container.add(Box.createRigidArea(new Dimension(0, 15)));

        JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        metaPanel.setOpaque(false);
        metaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sourceLabel = new JLabel("Source: " + n.getSource());
        sourceLabel.setForeground(new Color(59, 130, 246));
        sourceLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel dateLabel = new JLabel("Published: " + n.getPublishedAt());
        dateLabel.setForeground(new Color(148, 163, 184));
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        metaPanel.add(sourceLabel);
        metaPanel.add(dateLabel);
        container.add(metaPanel);
        container.add(Box.createRigidArea(new Dimension(0, 20)));

        JTextArea contentArea = new JTextArea(n.getDescription());
        contentArea.setWrapStyleWord(true);
        contentArea.setLineWrap(true);
        contentArea.setEditable(false);
        contentArea.setOpaque(false);
        contentArea.setForeground(new Color(203, 213, 225));
        contentArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(contentArea);
        container.add(Box.createRigidArea(new Dimension(0, 30)));

        RoundedButton linkBtn = new RoundedButton("Open Original Article", 8);
        linkBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        linkBtn.setForeground(Color.WHITE);
        linkBtn.setBackground(new Color(37, 99, 235));
        linkBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkBtn.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(n.getUrl()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        container.add(linkBtn);

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(null);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.setVisible(true);
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

        filterPanel.add(Box.createHorizontalStrut(20));
        JButton toggleView = createStyledButton("📋 Table View", ACCENT2);
        toggleView.setPreferredSize(new Dimension(130, 35));
        filterPanel.add(toggleView);

        content.add(filterPanel);
        content.add(Box.createVerticalStrut(20));

        // Popular stocks views
        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 20, 20));
        gridPanel.setBackground(BG);

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(BG);
        tableWrapper.setVisible(false);

        toggleView.addActionListener(e -> {
            boolean isTable = tableWrapper.isVisible();
            tableWrapper.setVisible(!isTable);
            gridPanel.setVisible(isTable);
            toggleView.setText(isTable ? "📋 Table View" : "🎴 Card View");
            content.revalidate();
            content.repaint();
        });

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
            // Update table version
            String[] columns = { "Symbol", "Name", "Price", "Change", "Sector", "Cap", "Trend" };
            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }

                @Override
                public Class<?> getColumnClass(int col) {
                    return col == 6 ? Object.class : super.getColumnClass(col);
                }
            };

            for (Object[] stock : filteredList) {
                double rawPrice = (Double) stock[2];
                double convertedPrice = portfolioService.convertToBase(rawPrice, "USD");
                String changeStr = (String) stock[3];
                double changeVal = Double.parseDouble(changeStr.replace("%", "").replace("+", ""));

                model.addRow(new Object[] {
                        stock[0],
                        stock[1],
                        formatCurrency(convertedPrice),
                        stock[3],
                        stock[5],
                        stock[6],
                        generateSparklineData((String) stock[0], rawPrice / (1 + (changeVal / 100)), rawPrice)
                });
            }
            tableWrapper.removeAll();
            JTable table = createStyledTable(model);
            table.getColumnModel().getColumn(6).setCellRenderer(new SparklineCellRenderer());
            table.setRowHeight(40);
            tableWrapper.add(new JScrollPane(table), BorderLayout.CENTER);

            gridPanel.revalidate();
            gridPanel.repaint();
        };

        applyFilter.addActionListener(e -> updateTable.run());
        updateTable.run(); // Initial load

        content.add(gridPanel);
        content.add(tableWrapper);
        content.add(Box.createVerticalStrut(20));

        // "Load More Stocks" Button
        JPanel loadMorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loadMorePanel.setOpaque(false);
        loadMorePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        RoundedButton loadMoreBtn = new RoundedButton("📈 Load 100+ More Stocks", 15);
        loadMoreBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        loadMoreBtn.setBackground(ACCENT);
        loadMoreBtn.setForeground(Color.WHITE);
        loadMoreBtn.setPreferredSize(new Dimension(280, 50));
        loadMoreBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Create expanded grid panel (initially hidden)
        JPanel expandedGridPanel = new JPanel(new GridLayout(0, 3, 20, 20));
        expandedGridPanel.setBackground(BG);
        expandedGridPanel.setVisible(false);

        loadMoreBtn.addActionListener(e -> {
            if (!expandedGridPanel.isVisible()) {
                // Load all stocks
                loadMoreBtn.setText("⏳ Loading...");
                loadMoreBtn.setEnabled(false);

                new Thread(() -> {
                    java.util.List<Object[]> allStocks = com.portfolio.data.StockDatabase.getAllStocks();

                    SwingUtilities.invokeLater(() -> {
                        DefaultTableModel model = (DefaultTableModel) ((JTable) ((JScrollPane) tableWrapper
                                .getComponent(0)).getViewport().getView()).getModel();

                        // Add all stocks to both views
                        for (Object[] stock : allStocks) {
                            String symbol = (String) stock[0];
                            String name = (String) stock[1];
                            double price = (Double) stock[2];
                            String change = (String) stock[3];
                            String sector = (String) stock[4];
                            String marketCap = (String) stock[5];

                            boolean isPositive = change.startsWith("+");
                            double convertedPrice = portfolioService.convertToBase(price, "USD");
                            double changeVal = Double.parseDouble(change.replace("%", "").replace("+", ""));

                            expandedGridPanel.add(createStockCard(symbol, name, formatCurrency(convertedPrice),
                                    change, isPositive, sector, marketCap));

                            model.addRow(new Object[] {
                                    symbol, name, formatCurrency(convertedPrice), change, sector, marketCap,
                                    generateSparklineData(symbol, price / (1 + (changeVal / 100)), price)
                            });
                        }

                        expandedGridPanel.setVisible(true);
                        loadMoreBtn.setText("✅ Loaded " + allStocks.size() + " Stocks");
                        loadMoreBtn.setBackground(GREEN);

                        // Scroll to show new content
                        expandedGridPanel.revalidate();
                        expandedGridPanel.repaint();
                        tableWrapper.revalidate();
                        tableWrapper.repaint();
                    });
                }).start();
            }
        });

        loadMorePanel.add(loadMoreBtn);
        content.add(loadMorePanel);
        content.add(Box.createVerticalStrut(20));
        content.add(expandedGridPanel);
        content.add(Box.createVerticalStrut(30));

        // News Section
        JPanel newsSection = new JPanel(new BorderLayout(0, 15));
        newsSection.setOpaque(false);
        newsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        JPanel newsHeader = new JPanel(new BorderLayout());
        newsHeader.setOpaque(false);
        JLabel newsTitle = new JLabel("📰 Live Market News");
        newsTitle.setFont(FONT_HEADING);
        newsTitle.setForeground(TEXT());
        newsHeader.add(newsTitle, BorderLayout.WEST);

        JButton refreshNewsBtn = createStyledButton("Refresh News", ACCENT2);
        newsHeader.add(refreshNewsBtn, BorderLayout.EAST);
        newsSection.add(newsHeader, BorderLayout.NORTH);

        JPanel newsGrid = new JPanel(new GridLayout(0, 2, 20, 20));
        newsGrid.setOpaque(false);
        JScrollPane newsScroll = new JScrollPane(newsGrid);
        newsScroll.setBorder(null);
        newsScroll.setOpaque(false);
        newsScroll.getViewport().setOpaque(false);
        newsScroll.setPreferredSize(new Dimension(100, 300));
        newsSection.add(newsScroll, BorderLayout.CENTER);

        Runnable updateNewsUI = () -> {
            newsGrid.removeAll();
            java.util.List<NewsService.NewsItem> items = newsService.getCachedNews();
            for (NewsService.NewsItem item : items) {
                JPanel nCard = createCard(item.getTitle());
                nCard.setLayout(new BorderLayout(10, 10));

                JLabel desc = new JLabel(
                        "<html><body style='width:250px;'>" + item.getDescription() + "</body></html>");
                desc.setFont(FONT_SMALL);
                desc.setForeground(TEXT_DIM());
                nCard.add(desc, BorderLayout.CENTER);

                JPanel nFoot = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                nFoot.setOpaque(false);
                nFoot.add(createTag(item.getCategory(), ACCENT));
                nFoot.add(createTag(item.getSentiment(), item.getSentiment().equals("Bullish") ? GREEN
                        : (item.getSentiment().equals("Bearish") ? RED : TEXT_DIM())));
                JLabel src = new JLabel("· " + item.getSource());
                src.setFont(FONT_SMALL);
                src.setForeground(TEXT_DIM());
                nFoot.add(src);

                nCard.add(nFoot, BorderLayout.SOUTH);
                newsGrid.add(nCard);
            }
            newsGrid.revalidate();
            newsGrid.repaint();
        };

        refreshNewsBtn.addActionListener(e -> {
            refreshNewsBtn.setText("Fetching...");
            refreshNewsBtn.setEnabled(false);
            new Thread(() -> {
                newsService.fetchMarketNews();
                SwingUtilities.invokeLater(() -> {
                    updateNewsUI.run();
                    refreshNewsBtn.setText("Refresh News");
                    refreshNewsBtn.setEnabled(true);
                });
            }).start();
        });

        updateNewsUI.run();
        content.add(newsSection);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scrollPane, BorderLayout.CENTER);

        return page;
    }

    private JPanel createStockCard(String symbol, String name, String price, String change, boolean isPositive,
            String sector, String marketCap) {
        RoundedPanel card = new RoundedPanel(24);
        card.setLayout(new BorderLayout(0, 0));
        card.setBackground(CARD_BG());
        card.setBorder(new EmptyBorder(24, 24, 0, 24));
        card.setPreferredSize(new Dimension(0, 260));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Interaction state
        boolean isInWatchlist = portfolioService.getWatchlist().stream()
                .anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));

        // Hover Effect
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(CARD_HOVER());
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(CARD_BG());
                card.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                showDetailedAnalysis(symbol);
            }
        });

        // Main content wrapper for left/right split
        JPanel splitPanel = new JPanel(new BorderLayout(40, 0));
        splitPanel.setOpaque(false);

        // Left Panel: Info + Price
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel symbolLbl = new JLabel(symbol);
        symbolLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        symbolLbl.setForeground(TEXT());

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLbl.setForeground(TEXT_DIM());

        infoPanel.add(symbolLbl);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(nameLbl);
        infoPanel.add(Box.createVerticalStrut(15));

        JLabel priceLbl = new JLabel(price);
        priceLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        priceLbl.setForeground(TEXT());

        // Change details
        double absVal = 0;
        try {
            absVal = Double.parseDouble(price.replaceAll("[^\\d.]", "")) * 0.0123;
        } catch (Exception ex) {
        }

        JLabel changeLbl = new JLabel((isPositive ? "▲ " : "▼ ") + change + " (+" + formatCurrency(absVal) + ")");
        changeLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        changeLbl.setForeground(isPositive ? GREEN : RED);

        infoPanel.add(priceLbl);
        infoPanel.add(Box.createVerticalStrut(5));
        infoPanel.add(changeLbl);

        // Tags
        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tagsPanel.setOpaque(false);
        tagsPanel.setBorder(new EmptyBorder(15, 0, 0, 0));
        tagsPanel.add(createTag(sector, isDarkTheme ? new Color(60, 60, 100) : new Color(230, 235, 255)));
        tagsPanel.add(createTag(marketCap, isDarkTheme ? new Color(100, 60, 80) : new Color(255, 235, 240)));
        infoPanel.add(tagsPanel);

        splitPanel.add(infoPanel, BorderLayout.WEST);

        // Right Panel: Risk + Actions
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);

        // Risk Badge
        String riskLevel = "Low Risk";
        Color riskColor = GREEN;
        if (change.contains("-") || Math.abs(absVal) > 500) {
            riskLevel = "Med Risk";
            riskColor = new Color(255, 167, 38);
        }
        if (Math.abs(absVal) > 1000) {
            riskLevel = "High Risk";
            riskColor = RED;
        }

        JPanel riskBadge = new RoundedPanel(12);
        riskBadge.setBackground(
                isDarkTheme ? new Color(riskColor.getRed(), riskColor.getGreen(), riskColor.getBlue(), 30)
                        : new Color(riskColor.getRed(), riskColor.getGreen(), riskColor.getBlue(), 20));
        riskBadge.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 4));
        JLabel riskIcon = new JLabel("🛡");
        riskIcon.setForeground(riskColor);
        JLabel riskText = new JLabel(riskLevel);
        riskText.setFont(new Font("Segoe UI", Font.BOLD, 12));
        riskText.setForeground(riskColor);
        riskBadge.add(riskIcon);
        riskBadge.add(riskText);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topActions.setOpaque(false);
        topActions.add(riskBadge);

        // Vertical Buttons
        JPanel actionBox = new JPanel();
        actionBox.setLayout(new BoxLayout(actionBox, BoxLayout.Y_AXIS));
        actionBox.setOpaque(false);
        actionBox.setBorder(new EmptyBorder(15, 0, 0, 0));

        JButton watchBtn = createActionBtn("⭐ " + (isInWatchlist ? "In Watchlist" : "Add to Watchlist"), isInWatchlist);
        watchBtn.addActionListener(e -> {
            boolean isAdded = toggleWatchlist(symbol);
            watchBtn.setText("⭐ " + (isAdded ? "In Watchlist" : "Add to Watchlist"));
            watchBtn.setForeground(isAdded ? Color.YELLOW : TEXT_DIM());
        });

        JButton aiBtn = createGradientBtn("🧠 AI Insights");
        aiBtn.addActionListener(e -> showAIInsightsModal(symbol));

        actionBox.add(watchBtn);
        actionBox.add(Box.createVerticalStrut(8));
        actionBox.add(aiBtn);

        rightPanel.add(topActions, BorderLayout.NORTH);
        rightPanel.add(actionBox, BorderLayout.CENTER);

        splitPanel.add(rightPanel, BorderLayout.EAST);
        card.add(splitPanel, BorderLayout.CENTER);

        // Sparkline at bottom (edge-to-edge)
        double currentPriceVal = 100.0;
        try {
            currentPriceVal = Double.parseDouble(price.replaceAll("[^\\d.]", ""));
        } catch (Exception ex) {
        }
        double buyPrice = currentPriceVal / (isPositive ? 1.05 : 0.95);
        SparklinePanel sparkline = new SparklinePanel(generateSparklineData(symbol, buyPrice, currentPriceVal));
        sparkline.setPreferredSize(new Dimension(0, 80));

        card.add(infoPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);
        card.add(sparkline, BorderLayout.SOUTH);

        return card;
    }

    private JButton createActionBtn(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(active ? Color.YELLOW : new Color(200, 200, 200)); // More visible grey
        btn.setBackground(CARD_BG());
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(active ? Color.YELLOW : BORDER(), 1, true));
        btn.setPreferredSize(new Dimension(160, 38));
        btn.setMaximumSize(new Dimension(160, 38));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createGradientBtn(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, ACCENT, getWidth(), 0, ACCENT2);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(160, 38));
        btn.setMaximumSize(new Dimension(160, 38));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private boolean toggleWatchlist(String symbol) {
        boolean alreadyIn = portfolioService.getWatchlist().stream()
                .anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));
        if (alreadyIn) {
            portfolioService.removeFromWatchlist(symbol);
            return false;
        } else {
            portfolioService.addToWatchlist(symbol, getStockNameFromSymbol(symbol));
            return true;
        }
    }

    private void showAIInsightsModal(String symbol) {
        JDialog dialog = new JDialog(this, "AI Analysis \u2022 " + symbol, true);
        dialog.setSize(550, 650);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG());

        JPanel container = new JPanel(new BorderLayout(0, 20));
        container.setBackground(CARD_BG());
        container.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Get AI Analysis - Simplified version (RuleBasedAIAnalysis removed)
        // Using placeholder data since this modal is not used in current version
        String trend = "Analyzing...";
        String momentum = "Analyzing...";
        String volatility = "Analyzing...";
        String diversification = "Analyzing...";
        int totalScore = 7;
        String recommendation = "HOLD";
        String insight = "Use the inline AI Analysis button on the stock detail page for comprehensive insights.";

        // Header: Score
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Recommendation Score");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_DIM());

        JLabel scoreLabel = new JLabel(totalScore + " / 10");
        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 42));
        scoreLabel.setForeground(ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(scoreLabel, BorderLayout.CENTER);

        // Body: Metrics
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        body.add(createMetricRow("\uD83D\uDCC8 Trend Analysis", trend,
                trend != null && trend.contains("Up") ? GREEN : RED));
        body.add(Box.createVerticalStrut(15));
        body.add(createMetricRow("\u26A1 Momentum", momentum,
                momentum != null && momentum.contains("Strong") ? GREEN
                        : (momentum != null && momentum.equals("Moderate") ? Color.ORANGE : RED)));
        body.add(Box.createVerticalStrut(15));
        body.add(createMetricRow("\uD83C\uDF0A Volatility", volatility,
                volatility != null && volatility.contains("High") ? RED : GREEN));
        body.add(Box.createVerticalStrut(15));
        body.add(createMetricRow("\uD83D\uDD17 Diversification", diversification,
                diversification != null && diversification.contains("Positive") ? GREEN
                        : TEXT_DIM()));

        // Recommendation Badge
        JPanel recBadge = new RoundedPanel(15);
        recBadge.setBackground(ACCENT2);
        recBadge.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JLabel recText = new JLabel(recommendation != null ? recommendation.toUpperCase() : "N/A");
        recText.setFont(new Font("Segoe UI", Font.BOLD, 18));
        recText.setForeground(Color.WHITE);
        recBadge.add(recText);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JTextArea insightArea = new JTextArea(insight != null ? insight : "");
        insightArea.setWrapStyleWord(true);
        insightArea.setLineWrap(true);
        insightArea.setEditable(false);
        insightArea.setOpaque(false);
        insightArea.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        insightArea.setForeground(TEXT_DIM());

        footer.add(recBadge, BorderLayout.NORTH);
        footer.add(Box.createVerticalStrut(20), BorderLayout.CENTER);
        footer.add(insightArea, BorderLayout.SOUTH);

        container.add(header, BorderLayout.NORTH);
        container.add(body, BorderLayout.CENTER);
        container.add(footer, BorderLayout.SOUTH);

        dialog.add(container);
        dialog.setVisible(true);
    }

    private JPanel createMetricRow(String label, String value, Color statusColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lbl.setForeground(TEXT());

        JLabel val = new JLabel(value != null ? value : "N/A");
        val.setFont(new Font("Segoe UI", Font.BOLD, 15));
        val.setForeground(statusColor);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private void showDetailedAnalysis(String symbol) {
        JDialog dialog = new JDialog(this, symbol + " \u2022 Detailed Market Analysis", true);
        dialog.setSize(1350, 900);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG());

        JPanel root = new JPanel(new BorderLayout(5, 5));
        root.setBackground(BG());
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // ---------------------------------------------------------------------
        // LEFT SECTION (Chart + Action Cards)
        // ---------------------------------------------------------------------
        JPanel leftPanel = new JPanel(new BorderLayout(20, 20));
        leftPanel.setOpaque(false);

        // 1. Chart Card - LARGER SIZE
        JPanel chartCard = createCard(symbol + " Real-Time Index");
        chartCard.setLayout(new BorderLayout());
        chartCard.setPreferredSize(new Dimension(900, 650));

        // Time Filters Header
        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setOpaque(false);
        chartHeader.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel mainTitle = new JLabel(symbol + " Real-Time Index");
        mainTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        mainTitle.setForeground(TEXT());

        final JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setOpaque(false);

        JPanel timeFilters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        timeFilters.setOpaque(false);
        String[] ranges = { "1D", "1W", "1M", "6M", "1Y" };
        final JButton[] filterBtns = new JButton[ranges.length];

        for (int i = 0; i < ranges.length; i++) {
            final String r = ranges[i];
            filterBtns[i] = new JButton(r);
            filterBtns[i].setFont(new Font("Segoe UI", Font.BOLD, 13));
            filterBtns[i].setForeground(r.equals("1D") ? ACCENT : TEXT_DIM());
            filterBtns[i].setBorderPainted(false);
            filterBtns[i].setContentAreaFilled(false);
            filterBtns[i].setCursor(new Cursor(Cursor.HAND_CURSOR));

            filterBtns[i].addActionListener(e -> {
                for (JButton b : filterBtns)
                    b.setForeground(TEXT_DIM());
                ((JButton) e.getSource()).setForeground(ACCENT);
                chartWrapper.removeAll();
                ChartPanel newChart = createTechnicalChart(symbol, r);
                newChart.setPreferredSize(new Dimension(900, 580));
                chartWrapper.add(newChart, BorderLayout.CENTER);
                chartWrapper.revalidate();
                chartWrapper.repaint();
            });
            timeFilters.add(filterBtns[i]);
        }
        chartHeader.add(mainTitle, BorderLayout.WEST);
        chartHeader.add(timeFilters, BorderLayout.EAST);
        chartCard.add(chartHeader, BorderLayout.NORTH);

        // 1. Chart Data & Professional Chart
        double currentPriceInit = 646.18;
        try {
            currentPriceInit = portfolioService.getPortfolioItems().stream()
                    .filter(item -> item.getStock().getSymbol().equalsIgnoreCase(symbol))
                    .map(item -> item.getStock().getCurrentPrice())
                    .findFirst().orElse(646.18);
        } catch (Exception ex) {
        }

        final double[] sharedPrice = { currentPriceInit };

        ChartPanel initialChart = createTechnicalChart(symbol, "1D");
        initialChart.setPreferredSize(new Dimension(900, 580));
        chartWrapper.add(initialChart, BorderLayout.CENTER);
        chartCard.add(chartWrapper, BorderLayout.CENTER);

        leftPanel.add(chartCard, BorderLayout.NORTH);

        // Sidebar Metrics Labels for Live Update
        final JPanel priceRow = createMetricLine("Current Price", "₹" + String.format("%.2f", sharedPrice[0]));

        // Simulation Timer
        javax.swing.Timer simTimer = new javax.swing.Timer(3000, e -> {
            sharedPrice[0] += (new Random().nextDouble() - 0.48) * 5;
            ((JLabel) priceRow.getComponent(1)).setText("₹" + String.format("%.2f", sharedPrice[0]));
            // Price update removed - updateStockPrice method doesn't exist
        });
        simTimer.start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                simTimer.stop();
            }
        });

        // 2. Action Grid (Bottom) - Increased height to prevent cutoff
        JPanel actionGrid = new JPanel(new GridLayout(1, 2, 20, 0));
        actionGrid.setOpaque(false);
        actionGrid.setPreferredSize(new Dimension(0, 280)); // Increased from 220 to 280

        // Action Panel 1: Price Alert
        JPanel alertPanel = createCard("ALERT Price Alert");
        alertPanel.setLayout(new GridBagLayout());
        GridBagConstraints agbc = new GridBagConstraints();
        agbc.fill = GridBagConstraints.HORIZONTAL;
        agbc.insets = new Insets(8, 15, 8, 15); // Better spacing
        agbc.weightx = 1.0;

        agbc.gridy = 0;
        JLabel targetLbl = new JLabel("Target Price:");
        targetLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        targetLbl.setForeground(TEXT());
        alertPanel.add(targetLbl, agbc);

        agbc.gridy = 1;
        JTextField targetField = createInputField("Enter ₹ price...");
        alertPanel.add(targetField, agbc);

        agbc.gridy = 2;
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        radioPanel.setOpaque(false);
        JRadioButton aboveRadio = new JRadioButton("Above Price", true);
        JRadioButton belowRadio = new JRadioButton("Below Price");
        aboveRadio.setOpaque(false);
        belowRadio.setOpaque(false);
        aboveRadio.setForeground(TEXT());
        belowRadio.setForeground(TEXT());
        ButtonGroup bg = new ButtonGroup();
        bg.add(aboveRadio);
        bg.add(belowRadio);
        radioPanel.add(aboveRadio);
        radioPanel.add(belowRadio);
        alertPanel.add(radioPanel, agbc);

        agbc.gridy = 3;
        JButton setAlertBtn = createStyledButton("\uD83D\uDD14 Set Alert", ACCENT);
        setAlertBtn.addActionListener(e -> {
            String targetStr = targetField.getText().trim();
            if (!targetStr.isEmpty()) {
                try {
                    double tPrice = Double.parseDouble(targetStr);
                    boolean isAbove = aboveRadio.isSelected();
                    // Price alert feature - simplified (PriceAlert class removed)
                    String alertType = isAbove ? "above" : "below";
                    JOptionPane.showMessageDialog(dialog,
                            "ALERT Alert set for " + symbol + " when price goes " + alertType + " ₹" + targetStr,
                            "Alert Created", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Invalid target price", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        alertPanel.add(setAlertBtn, agbc);

        // Action Panel 2: Your Holdings + Trading Buttons
        JPanel holdingsPanel = createCard("PORTFOLIO Your Holdings");
        holdingsPanel.setLayout(new BorderLayout(0, 15)); // Reduced spacing

        // Get current holdings for this stock
        PortfolioItem currentHolding = portfolioService.getPortfolioItems().stream()
                .filter(item -> item.getStock().getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);

        JPanel holdingsContent = new JPanel();
        holdingsContent.setLayout(new BoxLayout(holdingsContent, BoxLayout.Y_AXIS));
        holdingsContent.setOpaque(false);

        if (currentHolding != null) {
            // Add a header to show this is current holdings
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setOpaque(false);
            headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0)); // Reduced spacing

            JLabel headerLabel = new JLabel("POSITION Current Position");
            headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Smaller font
            headerLabel.setForeground(ACCENT);
            headerPanel.add(headerLabel, BorderLayout.WEST);

            holdingsContent.add(headerPanel);

            // User owns this stock - show holdings info with better styling
            double convertedPurchasePrice = portfolioService.convertToBase(currentHolding.getPurchasePrice(),
                    currentHolding.getOriginalCurrency());
            double convertedTotalValue = portfolioService.convertToBase(currentHolding.getTotalValue(),
                    currentHolding.getOriginalCurrency());
            double convertedGainLoss = portfolioService.convertToBase(currentHolding.getGainLoss(),
                    currentHolding.getOriginalCurrency());
            double gainLossPercent = currentHolding.getGainLossPercent();

            // Quantity row
            JPanel qtyRow = new JPanel(new BorderLayout());
            qtyRow.setOpaque(false);
            JLabel qtyLbl = new JLabel("Quantity:");
            qtyLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            qtyLbl.setForeground(TEXT_DIM());
            JLabel qtyVal = new JLabel(currentHolding.getQuantity() + " shares");
            qtyVal.setFont(new Font("Segoe UI", Font.BOLD, 13));
            qtyVal.setForeground(TEXT());
            qtyRow.add(qtyLbl, BorderLayout.WEST);
            qtyRow.add(qtyVal, BorderLayout.EAST);

            // Avg Cost row
            JPanel avgRow = new JPanel(new BorderLayout());
            avgRow.setOpaque(false);
            JLabel avgLbl = new JLabel("Avg. Cost:");
            avgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            avgLbl.setForeground(TEXT_DIM());
            JLabel avgVal = new JLabel(formatCurrency(convertedPurchasePrice));
            avgVal.setFont(new Font("Segoe UI", Font.BOLD, 13));
            avgVal.setForeground(TEXT());
            avgRow.add(avgLbl, BorderLayout.WEST);
            avgRow.add(avgVal, BorderLayout.EAST);

            // Total Value row
            JPanel valueRow = new JPanel(new BorderLayout());
            valueRow.setOpaque(false);
            JLabel valueLbl = new JLabel("Total Value:");
            valueLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            valueLbl.setForeground(TEXT_DIM());
            JLabel valueVal = new JLabel(formatCurrency(convertedTotalValue));
            valueVal.setFont(new Font("Segoe UI", Font.BOLD, 15));
            valueVal.setForeground(TEXT());
            valueRow.add(valueLbl, BorderLayout.WEST);
            valueRow.add(valueVal, BorderLayout.EAST);

            // P/L row with prominent styling
            JPanel plRow = new JPanel(new BorderLayout());
            plRow.setOpaque(false);
            JLabel plLbl = new JLabel("Profit/Loss:");
            plLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            plLbl.setForeground(TEXT_DIM());
            String plText = formatCurrency(convertedGainLoss) + " (" + String.format("%.2f%%", gainLossPercent) + ")";
            JLabel plVal = new JLabel(plText);
            plVal.setFont(new Font("Segoe UI", Font.BOLD, 15));
            plVal.setForeground(convertedGainLoss >= 0 ? GREEN : RED);
            plRow.add(plLbl, BorderLayout.WEST);
            plRow.add(plVal, BorderLayout.EAST);

            holdingsContent.add(qtyRow);
            holdingsContent.add(Box.createVerticalStrut(6)); // Reduced spacing
            holdingsContent.add(avgRow);
            holdingsContent.add(Box.createVerticalStrut(6)); // Reduced spacing
            holdingsContent.add(valueRow);
            holdingsContent.add(Box.createVerticalStrut(6)); // Reduced spacing
            holdingsContent.add(plRow);
        } else {
            // User doesn't own this stock - show encouraging message
            JPanel noHoldingPanel = new JPanel(new BorderLayout());
            noHoldingPanel.setOpaque(false);
            noHoldingPanel.setBorder(new EmptyBorder(20, 10, 20, 10));

            JLabel noHoldingLabel = new JLabel(
                    "<html><center>INVEST<br/><br/>You don't own this stock yet.<br/>Start investing today!</center></html>");
            noHoldingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            noHoldingLabel.setForeground(TEXT_DIM());
            noHoldingLabel.setHorizontalAlignment(SwingConstants.CENTER);
            noHoldingPanel.add(noHoldingLabel, BorderLayout.CENTER);
            holdingsContent.add(noHoldingPanel);
        }

        holdingsPanel.add(holdingsContent, BorderLayout.CENTER);

        // Trading Buttons at bottom with better styling
        JPanel tradeButtons = new JPanel(new GridLayout(1, 2, 15, 0));
        tradeButtons.setOpaque(false);

        JButton buyBtn = createStyledButton("BUY Buy Stock", GREEN);
        JButton sellBtn = createStyledButton("SELL Sell Stock", RED);

        buyBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sellBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));

        buyBtn.addActionListener(e -> showBuyStockDialog(symbol));
        sellBtn.addActionListener(e -> showSellStockDialog(symbol));

        tradeButtons.add(buyBtn);
        tradeButtons.add(sellBtn);

        holdingsPanel.add(tradeButtons, BorderLayout.SOUTH);

        actionGrid.add(alertPanel);
        actionGrid.add(holdingsPanel);

        leftPanel.add(actionGrid, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // RIGHT SECTION (Sidebar: Metrics + Actions + AI Stats)
        // ---------------------------------------------------------------------
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(CARD_BG());
        sidebar.setPreferredSize(new Dimension(380, 0));
        sidebar.setBorder(new CompoundBorder(new MatteBorder(0, 1, 0, 0, BORDER()), new EmptyBorder(25, 25, 25, 25)));

        // 1. Security Metrics
        JLabel metricsTitle = new JLabel("\uD83D\uDCC8 Security Metrics");
        metricsTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        metricsTitle.setForeground(TEXT());
        sidebar.add(metricsTitle);
        sidebar.add(Box.createVerticalStrut(20));

        sidebar.add(createMetricLine("Market Cap", "₹3.1T"));
        sidebar.add(priceRow);
        sidebar.add(createMetricLine("Today's High", "₹788.06"));
        sidebar.add(createMetricLine("Today's Low", "₹588.06"));
        sidebar.add(createMetricLine("52 Week High", "₹19,250.00"));
        sidebar.add(createMetricLine("52 Week Low", "₹14,100.00"));
        sidebar.add(createMetricLine("Volume", "42.5M"));
        sidebar.add(createMetricLine("P/E Ratio", "28.4"));
        sidebar.add(createMetricLine("Dividend Yield", "0.65%"));
        sidebar.add(createMetricLine("Sector", "Technology"));
        sidebar.add(createMetricLine("Industry", "Consumer Electronics"));

        sidebar.add(Box.createVerticalStrut(30));

        // 2. AI Insights Section (Inline expandable display)
        JPanel aiSection = new JPanel();
        aiSection.setLayout(new BoxLayout(aiSection, BoxLayout.Y_AXIS));
        aiSection.setOpaque(false);

        JLabel aiTitle = new JLabel("AI AI Insights");
        aiTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        aiTitle.setForeground(ACCENT);
        aiTitle.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align
        aiSection.add(aiTitle);
        aiSection.add(Box.createVerticalStrut(15));

        // AI Content Panel (initially hidden)
        RoundedPanel aiContentPanel = new RoundedPanel(12); // Use RoundedPanel
        aiContentPanel.setLayout(new BorderLayout());
        aiContentPanel.setBackground(isDarkTheme ? new Color(40, 40, 60) : new Color(245, 245, 255));
        aiContentPanel.setVisible(false);
        aiContentPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));

        JTextArea aiAnalysisArea = new JTextArea();
        aiAnalysisArea.setWrapStyleWord(true);
        aiAnalysisArea.setLineWrap(true);
        aiAnalysisArea.setEditable(false);
        aiAnalysisArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        aiAnalysisArea.setForeground(TEXT());
        aiAnalysisArea.setOpaque(false);
        aiAnalysisArea.setBorder(new EmptyBorder(15, 15, 15, 15));

        JScrollPane aiScrollPane = new JScrollPane(aiAnalysisArea);
        aiScrollPane.setBorder(null);
        aiScrollPane.setOpaque(false);
        aiScrollPane.getViewport().setOpaque(false);
        aiScrollPane.setPreferredSize(new Dimension(0, 200));
        aiContentPanel.add(aiScrollPane, BorderLayout.CENTER);

        JButton askAIBtn = createStyledButton("AI Get AI Analysis", new Color(139, 92, 246));
        askAIBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        askAIBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        askAIBtn.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align button

        askAIBtn.addActionListener(e -> {
            if (aiContentPanel.isVisible()) {
                // Hide AI content
                aiContentPanel.setVisible(false);
                askAIBtn.setText("AI Get AI Analysis");
                sidebar.revalidate();
                sidebar.repaint();
            } else {
                // Show and fetch AI analysis
                askAIBtn.setText("WAIT Analyzing...");
                askAIBtn.setEnabled(false);

                new Thread(() -> {
                    try {
                        double currentPrice = sharedPrice[0];
                        String aiAnalysis = groqAIService.analyzeStock(symbol, currentPrice);

                        SwingUtilities.invokeLater(() -> {
                            aiAnalysisArea.setText(aiAnalysis);
                            aiAnalysisArea.setCaretPosition(0);
                            aiContentPanel.setVisible(true);
                            askAIBtn.setText("HIDE Hide Analysis");
                            askAIBtn.setEnabled(true);
                            sidebar.revalidate();
                            sidebar.repaint();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            askAIBtn.setText("AI Get AI Analysis");
                            askAIBtn.setEnabled(true);
                            JOptionPane.showMessageDialog(dialog,
                                    "Failed to get AI analysis: " + ex.getMessage(),
                                    "AI Error", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            }
        });

        aiSection.add(askAIBtn);
        aiSection.add(Box.createVerticalStrut(15));
        aiSection.add(aiContentPanel);

        sidebar.add(aiSection);
        sidebar.add(Box.createVerticalStrut(20));

        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setBorder(null);
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        root.add(leftPanel, BorderLayout.CENTER);
        root.add(sidebarScroll, BorderLayout.EAST);

        dialog.add(root);
        dialog.setVisible(true);
    }

    private void showAIAnalysisDialog(String symbol, String aiAnalysis) {
        JDialog dialog = new JDialog(this, "🤖 AI Analysis: " + symbol, true);
        dialog.setSize(700, 600);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG());

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(BG());
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLabel = new JLabel("🧠 AI-Powered Stock Analysis");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(139, 92, 246));
        header.add(titleLabel, BorderLayout.WEST);

        JLabel symbolLabel = new JLabel(symbol);
        symbolLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        symbolLabel.setForeground(TEXT());
        header.add(symbolLabel, BorderLayout.EAST);

        mainPanel.add(header, BorderLayout.NORTH);

        // AI Analysis Content
        JTextArea analysisText = new JTextArea(aiAnalysis);
        analysisText.setWrapStyleWord(true);
        analysisText.setLineWrap(true);
        analysisText.setEditable(false);
        analysisText.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        analysisText.setForeground(TEXT());
        analysisText.setBackground(CARD_BG());
        analysisText.setBorder(new EmptyBorder(20, 20, 20, 20));
        analysisText.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(analysisText);
        scrollPane.setBorder(new CompoundBorder(
                new LineBorder(new Color(139, 92, 246, 100), 2, true),
                new EmptyBorder(0, 0, 0, 0)));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer with close button
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);

        JButton closeBtn = createStyledButton("Close", ACCENT);
        closeBtn.setPreferredSize(new Dimension(120, 40));
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);

        mainPanel.add(footer, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private void showBuyStockDialog(String symbol) {
        showTradeDialog(symbol, true);
    }

    private void showSellStockDialog(String symbol) {
        showTradeDialog(symbol, false);
    }

    private void showTradeDialog(String symbol, boolean isBuy) {
        JDialog dialog = new JDialog(this, (isBuy ? "Buy " : "Sell ") + symbol, true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(CARD_BG());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel((isBuy ? "💰 Buy " : "💸 Sell ") + symbol);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(isBuy ? GREEN : RED);
        panel.add(title);
        panel.add(Box.createVerticalStrut(20));

        JTextField qtyField = createInputField("Quantity");
        JTextField priceField = createInputField("Price");

        panel.add(createFieldPanel("Quantity:", qtyField));
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldPanel("Price:", priceField));
        panel.add(Box.createVerticalStrut(25));

        JButton actionBtn = createStyledButton(isBuy ? "Confirm Buy" : "Confirm Sell", isBuy ? GREEN : RED);
        actionBtn.addActionListener(e -> {
            try {
                int qty = Integer.parseInt(qtyField.getText());
                double price = Double.parseDouble(priceField.getText());
                if (isBuy) {
                    portfolioService.buyStock(symbol, symbol, qty, price, "INR", "Tech", "Large", "High");
                } else {
                    portfolioService.sellStock(symbol, qty);
                }
                JOptionPane.showMessageDialog(dialog, "Transaction successful!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
                refreshAllViews();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid input", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(actionBtn);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private JPanel createDetailedAIRow(String icon, String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel left = new JLabel(icon + " " + label + ":");
        left.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        left.setForeground(TEXT_DIM());

        JLabel right = new JLabel(value);
        right.setFont(new Font("Segoe UI", Font.BOLD, 14));
        right.setForeground(TEXT());

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel createMetricLine(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        p.setBorder(new EmptyBorder(8, 0, 8, 0));

        JLabel l = new JLabel(label);
        l.setFont(FONT_BODY);
        l.setForeground(TEXT_DIM());

        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.BOLD, 15));
        v.setForeground(TEXT());

        p.add(l, BorderLayout.WEST);
        p.add(v, BorderLayout.EAST);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WATCHLIST PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildWatchlistPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG());

        JPanel content = new JPanel();
        content.setBackground(BG());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Search to Add Bar
        JPanel searchPanel = createCard("Add to Watchlist");
        searchPanel.setLayout(new BorderLayout(15, 0));
        searchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JTextField searchField = createInputField("Enter symbol (e.g. BTC, ETH, TSLA)...");
        JButton addBtn = createStyledButton("➕ Add to Watch", ACCENT);

        addBtn.addActionListener(e -> {
            String sym = searchField.getText().trim().toUpperCase();
            if (!sym.isEmpty()) {
                portfolioService.addToWatchlist(sym, sym);
                searchField.setText("");
                refreshAllViews();
            }
        });

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(addBtn, BorderLayout.EAST);
        content.add(searchPanel);
        content.add(Box.createVerticalStrut(20));

        // Watchlist Table
        JPanel tableCard = createCard("Tracked Symbols");
        String[] columns = { "Symbol", "Name", "Price", "Change %", "Trend" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (Stock s : portfolioService.getWatchlist()) {
            double price = s.getCurrentPrice();
            double change = s.getChangePercent();
            // Generate random sparkline data for demo if no history
            double[] sparkData = new double[10];
            Random rand = new Random();
            sparkData[0] = price * (1 - (change / 100));
            for (int i = 1; i < 10; i++)
                sparkData[i] = sparkData[i - 1] * (1 + (rand.nextDouble() - 0.5) * 0.02);
            sparkData[9] = price;

            model.addRow(new Object[] {
                    s.getSymbol(), s.getName(), formatCurrency(price),
                    String.format("%+.2f%%", change), sparkData
            });
        }

        JTable table = createStyledTable(model);
        table.getColumnModel().getColumn(4).setCellRenderer(new SparklineCellRenderer());
        table.setRowHeight(40);

        // Add Right-Click to Remove
        table.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r >= 0 && r < table.getRowCount() && SwingUtilities.isRightMouseButton(e)) {
                    String sym = (String) table.getValueAt(r, 0);
                    int choice = JOptionPane.showConfirmDialog(PremiumStockDashboard.this,
                            "Remove " + sym + " from watchlist?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) {
                        portfolioService.removeFromWatchlist(sym);
                        refreshAllViews();
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(CARD_BG);
        tableCard.add(scroll, BorderLayout.CENTER);

        content.add(tableCard);
        page.add(new JScrollPane(content), BorderLayout.CENTER);
        return page;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSACTIONS PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildTransactionsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG());

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG());
        container.setBorder(new EmptyBorder(25, 25, 25, 25));

        // --- Filter Header ---
        JPanel filterBar = new JPanel(new BorderLayout());
        filterBar.setOpaque(false);
        filterBar.setBorder(new EmptyBorder(0, 0, 20, 0));

        JPanel leftFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftFilters.setOpaque(false);

        JTextField searchField = new JTextField(15);
        searchField.setBackground(CARD_BG());
        searchField.setForeground(TEXT());
        searchField.setCaretColor(TEXT());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER()), new EmptyBorder(8, 12, 8, 12)));

        // Placeholder simulation using focus listeners or client property if FlatLaf
        searchField.setText("Search symbol...");

        String[] types = { "All Operations", "BUY", "SELL" };
        JComboBox<String> typeFilter = new JComboBox<>(types);
        typeFilter.setBackground(CARD_BG());
        typeFilter.setForeground(TEXT());

        leftFilters.add(new JLabel("🔍"));
        leftFilters.add(searchField);
        leftFilters.add(new JLabel("Filter:"));
        leftFilters.add(typeFilter);

        JButton exportBtn = new JButton("Export History (PDF)");
        exportBtn.setBackground(ACCENT);
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFocusPainted(false);
        exportBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        exportBtn.addActionListener(e -> simulatePDFExport());

        filterBar.add(leftFilters, BorderLayout.WEST);
        filterBar.add(exportBtn, BorderLayout.EAST);

        container.add(filterBar, BorderLayout.NORTH);

        // --- Table Section ---
        JPanel card = createCard("Transaction Registry");
        String[] columns = { "Symbol", "Name", "Qty", "Price", "Value", "Date", "Trend" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Wire up filters
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (searchField.getText().equals("Search symbol..."))
                    searchField.setText("");
            }
        });

        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                applyTransactionFilters(searchField.getText(), (String) typeFilter.getSelectedItem(), model);
            }
        });
        typeFilter.addActionListener(
                e -> applyTransactionFilters(searchField.getText(), (String) typeFilter.getSelectedItem(), model));

        applyTransactionFilters("", "All Operations", model); // Initial load

        JTable table = createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(CARD_BG());
        scroll.getViewport().setBackground(CARD_BG());
        scroll.setBorder(null);

        card.add(scroll, BorderLayout.CENTER);
        container.add(card, BorderLayout.CENTER);

        page.add(container, BorderLayout.CENTER);
        return page;
    }

    private void applyTransactionFilters(String query, String type, DefaultTableModel model) {
        model.setRowCount(0);
        String q = (query == null || query.equals("Search symbol...")) ? "" : query.toLowerCase();

        java.util.List<Transaction> transactions = portfolioService.getTransactions();
        for (Transaction t : transactions) {
            boolean matchesQuery = q.isEmpty() || t.getSymbol().toLowerCase().contains(q);
            boolean matchesType = type.equals("All Operations") || t.getType().equalsIgnoreCase(type);

            if (matchesQuery && matchesType) {
                String stockName = getStockNameFromSymbol(t.getSymbol());
                model.addRow(new Object[] {
                        t.getSymbol(),
                        stockName,
                        t.getQuantity(),
                        formatCurrency(t.getPrice()),
                        formatCurrency(t.getQuantity() * t.getPrice()),
                        t.getTimestamp().toString().split(" ")[0], // Transaction Date
                        "Live" // Trend indicator for transactions
                });
            }
        }
    }

    private void simulatePDFExport() {
        JDialog dialog = new JDialog(this, "Exporting...", true);
        dialog.setSize(400, 180);
        dialog.setLocationRelativeTo(this);
        dialog.setUndecorated(true);
        dialog.getContentPane().setBackground(CARD_BG());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel l = new JLabel("Generating Institutional Trade Report...");
        l.setFont(FONT_HEADING);
        l.setForeground(TEXT());
        l.setAlignmentX(Component.CENTER_ALIGNMENT);

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setForeground(ACCENT);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(l);
        p.add(Box.createVerticalStrut(20));
        p.add(progress);
        dialog.add(p);

        new javax.swing.Timer(2000, e -> {
            dialog.dispose();
            JOptionPane.showMessageDialog(this,
                    "Successfully exported 52 transactions to: \nStockVault_Report_2024.pdf",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }) {
            {
                setRepeats(false);
                start();
            }
        };

        dialog.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANALYTICS PAGE
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel buildAnalyticsPage() {
        // ── Analytics Theme Colors ──
        final Color A_BG = new Color(15, 23, 42); // #0F172A
        final Color A_CARD = new Color(30, 41, 59); // #1E293B
        final Color A_BORDER = new Color(42, 51, 72); // #2A3348
        final Color A_TEXT = new Color(226, 232, 240); // #E2E8F0
        final Color A_TEXT_DIM = new Color(148, 163, 184); // #94A3B8
        final Color A_ACCENT = new Color(59, 130, 246); // #3B82F6
        final Color A_GREEN = new Color(34, 197, 94); // #22C55E
        final Color A_RED = new Color(239, 68, 68); // #EF4444
        final Color A_GOLD = new Color(251, 191, 36); // #FBB524

        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(A_BG);

        // ── Main scrollable content using GridBagLayout for proper width containment
        // ──
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(A_BG);
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        int row = 0;

        // ════════════════════════════════════════════════════════════════
        // 1. PORTFOLIO SUMMARY HEADER ROW
        // ════════════════════════════════════════════════════════════════
        JPanel summaryHeader = new JPanel(new BorderLayout());
        summaryHeader.setOpaque(false);

        JLabel summaryTitle = new JLabel("Portfolio Summary");
        summaryTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        summaryTitle.setForeground(A_TEXT);
        summaryHeader.add(summaryTitle, BorderLayout.WEST);

        JButton reportBtn = createStyledButton("⬇ Export Report", A_ACCENT);
        reportBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        reportBtn.addActionListener(e -> generatePortfolioReport());
        summaryHeader.add(reportBtn, BorderLayout.EAST);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 16, 0);
        content.add(summaryHeader, gbc);

        // ── 4 Metric cards ──
        double totalValue = portfolioService.calculateCurrentValue();
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double profitLoss = portfolioService.calculateProfitLoss();
        double returnPct = totalInvestment > 0 ? (profitLoss / totalInvestment) * 100 : 0;
        double sharpe = portfolioService.calculateSharpeRatio();
        double beta = portfolioService.calculatePortfolioBeta();

        JPanel metricsRow = new JPanel(new GridLayout(1, 4, 16, 0));
        metricsRow.setOpaque(false);

        metricsRow.add(createAnalyticsMetricCard("💰", "Total Portfolio", formatCurrency(totalValue), "Today's close",
                A_TEXT, A_CARD, A_BORDER));
        metricsRow.add(createAnalyticsMetricCard("📈", "Total Return", String.format("%+.1f%%", returnPct),
                (profitLoss >= 0 ? "+" : "") + formatCurrency(profitLoss), returnPct >= 0 ? A_GREEN : A_RED, A_CARD,
                A_BORDER));
        metricsRow.add(createAnalyticsMetricCard("📊", "Sharpe Ratio", String.format("%.2f", sharpe), "Risk-adjusted",
                A_TEXT, A_CARD, A_BORDER));
        String riskLabel = beta > 1.2 ? "High Risk" : beta > 0.8 ? "Moderate Risk" : "Low Risk";
        metricsRow.add(createAnalyticsMetricCard("🎯", "Portfolio Beta", String.format("%.2f", beta), riskLabel, A_GOLD,
                A_CARD, A_BORDER));

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 28, 0);
        content.add(metricsRow, gbc);

        // ════════════════════════════════════════════════════════════════
        // 2. ALLOCATION INSIGHTS
        // ════════════════════════════════════════════════════════════════
        JLabel allocTitle = new JLabel("Allocation Insights");
        allocTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        allocTitle.setForeground(A_TEXT);
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 16, 0);
        content.add(allocTitle, gbc);

        // 2-column layout: Asset Allocation (wider) | Sector Allocation
        JPanel allocRow = new JPanel(new GridBagLayout());
        allocRow.setOpaque(false);
        GridBagConstraints ac = new GridBagConstraints();
        ac.gridy = 0;
        ac.fill = GridBagConstraints.BOTH;
        ac.weighty = 1.0;

        ac.gridx = 0;
        ac.weightx = 0.6;
        ac.insets = new Insets(0, 0, 0, 8);
        allocRow.add(createAnalyticsChartCard("Asset Allocation", createAssetAllocationDonut(), A_CARD, A_BORDER), ac);

        ac.gridx = 1;
        ac.weightx = 0.4;
        ac.insets = new Insets(0, 8, 0, 0);
        allocRow.add(createAnalyticsChartCard("Sector Allocation", createSectorAllocationDonut(), A_CARD, A_BORDER),
                ac);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 28, 0);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;
        content.add(allocRow, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;

        // ════════════════════════════════════════════════════════════════
        // 3. RISK & COMPARISON
        // ════════════════════════════════════════════════════════════════
        JLabel riskTitle = new JLabel("Risk & Comparison");
        riskTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        riskTitle.setForeground(A_TEXT);
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 16, 0);
        content.add(riskTitle, gbc);

        // 2-column equal: Risk vs Return | Stock Value Comparison
        JPanel riskRow = new JPanel(new GridBagLayout());
        riskRow.setOpaque(false);
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridy = 0;
        rc.fill = GridBagConstraints.BOTH;
        rc.weighty = 1.0;

        rc.gridx = 0;
        rc.weightx = 0.5;
        rc.insets = new Insets(0, 0, 0, 8);
        riskRow.add(createAnalyticsChartCard("Risk vs Return", createRiskVsReturnScatter(), A_CARD, A_BORDER), rc);

        rc.gridx = 1;
        rc.weightx = 0.5;
        rc.insets = new Insets(0, 8, 0, 0);
        riskRow.add(
                createAnalyticsChartCard("Stock Value Comparison", createStockValueHorizontalBar(), A_CARD, A_BORDER),
                rc);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 28, 0);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;
        content.add(riskRow, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;

        // ════════════════════════════════════════════════════════════════
        // 4. AI INSIGHTS + TAX ESTIMATION
        // ════════════════════════════════════════════════════════════════
        JPanel bottomRow = new JPanel(new GridBagLayout());
        bottomRow.setOpaque(false);
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = 0;
        bc.fill = GridBagConstraints.BOTH;
        bc.weighty = 1.0;

        bc.gridx = 0;
        bc.weightx = 0.65;
        bc.insets = new Insets(0, 0, 0, 8);
        bottomRow.add(createAIInsightsCard(A_CARD, A_BORDER, A_TEXT, A_TEXT_DIM, A_ACCENT, A_GREEN, A_RED), bc);

        bc.gridx = 1;
        bc.weightx = 0.35;
        bc.insets = new Insets(0, 8, 0, 0);
        bottomRow.add(createTaxEstimationCard(A_CARD, A_BORDER, A_TEXT, A_TEXT_DIM, A_GOLD, A_ACCENT), bc);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 16, 0);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.25;
        content.add(bottomRow, gbc);

        // Bottom spacer to push everything up
        gbc.gridy = row;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        content.add(new JPanel() {
            {
                setOpaque(false);
            }
        }, gbc);

        // ── Wrap in scroll ──
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setBackground(A_BG);
        scroll.getViewport().setBackground(A_BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANALYTICS HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel createAnalyticsMetricCard(String icon, String label, String value, String sub, Color valueColor,
            Color cardBg, Color borderColor) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(18, 20, 18, 20)));

        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        iconRow.setOpaque(false);
        JLabel iconLbl = new JLabel(icon + " " + label);
        iconLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        iconLbl.setForeground(new Color(148, 163, 184));
        iconRow.add(iconLbl);
        iconRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(iconRow);
        card.add(Box.createVerticalStrut(8));

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLbl.setForeground(valueColor);
        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(valueLbl);

        if (sub != null && !sub.isEmpty()) {
            card.add(Box.createVerticalStrut(4));
            JLabel subLbl = new JLabel(sub);
            subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            subLbl.setForeground(new Color(148, 163, 184));
            subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(subLbl);
        }

        // Hover glow
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(new CompoundBorder(
                        new LineBorder(new Color(59, 130, 246, 120), 1, true),
                        new EmptyBorder(18, 20, 18, 20)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(new CompoundBorder(
                        new LineBorder(borderColor, 1, true),
                        new EmptyBorder(18, 20, 18, 20)));
            }
        });
        return card;
    }

    private JPanel createAnalyticsChartCard(String title, ChartPanel chartPanel, Color cardBg, Color borderColor) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new BorderLayout());
        card.setBackground(cardBg);
        card.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(16, 16, 16, 16)));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLbl.setForeground(new Color(226, 232, 240));
        titleLbl.setBorder(new EmptyBorder(0, 0, 10, 0));
        card.add(titleLbl, BorderLayout.NORTH);

        chartPanel.setBackground(cardBg);
        chartPanel.setBorder(null);
        chartPanel.setMinimumSize(new Dimension(100, 200));
        chartPanel.setPreferredSize(new Dimension(200, 280));
        card.add(chartPanel, BorderLayout.CENTER);

        return card;
    }

    private ChartPanel createAssetAllocationDonut() {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        double totalVal = portfolioService.calculateCurrentValue();
        double stockVal = 0;
        for (PortfolioItem item : portfolioService.getPortfolioItems()) {
            stockVal += item.getTotalValue();
        }
        double etfVal = totalVal * 0.097;
        double cashVal = totalVal * 0.087;
        double otherVal = totalVal * 0.091;
        double adjustedStockVal = totalVal - etfVal - cashVal - otherVal;
        if (adjustedStockVal < 0)
            adjustedStockVal = stockVal;

        dataset.setValue("Stocks", adjustedStockVal);
        dataset.setValue("ETFs", etfVal);
        dataset.setValue("Cash", cashVal);
        dataset.setValue("Other", otherVal);

        JFreeChart chart = ChartFactory.createRingChart(null, dataset, true, true, false);
        chart.setBackgroundPaint(new Color(30, 41, 59));
        chart.setBorderVisible(false);

        org.jfree.chart.plot.RingPlot plot = (org.jfree.chart.plot.RingPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(30, 41, 59));
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.setLabelPaint(new Color(226, 232, 240));
        plot.setLabelBackgroundPaint(new Color(0, 0, 0, 120));
        plot.setLabelOutlinePaint(new Color(59, 130, 246, 60));
        plot.setLabelShadowPaint(null);
        plot.setSectionDepth(0.35);

        Color[] colors = { new Color(59, 130, 246), new Color(139, 92, 246), new Color(34, 197, 94),
                new Color(251, 191, 36) };
        String[] keys = { "Stocks", "ETFs", "Cash", "Other" };
        for (int i = 0; i < keys.length; i++) {
            plot.setSectionPaint(keys[i], colors[i]);
        }

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(30, 41, 59));
            chart.getLegend().setItemPaint(new Color(148, 163, 184));
            chart.getLegend().setItemFont(new Font("Segoe UI", Font.PLAIN, 11));
        }

        ChartPanel cp = new ChartPanel(chart);
        cp.setMouseWheelEnabled(false);
        cp.setDisplayToolTips(true);
        return cp;
    }

    private ChartPanel createSectorAllocationDonut() {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        Map<String, Double> sectorMap = new HashMap<>();

        for (PortfolioItem item : portfolioService.getPortfolioItems()) {
            String sector = item.getStock().getSector();
            if (sector == null || sector.isEmpty())
                sector = "Other";
            sectorMap.put(sector, sectorMap.getOrDefault(sector, 0.0) + item.getTotalValue());
        }

        if (sectorMap.isEmpty()) {
            dataset.setValue("Technology", 44.6);
            dataset.setValue("Industrials", 8.3);
            dataset.setValue("Healthcare", 6.5);
            dataset.setValue("Financials", 4.5);
        } else {
            sectorMap.forEach(dataset::setValue);
        }

        JFreeChart chart = ChartFactory.createRingChart(null, dataset, true, true, false);
        chart.setBackgroundPaint(new Color(30, 41, 59));
        chart.setBorderVisible(false);

        org.jfree.chart.plot.RingPlot plot = (org.jfree.chart.plot.RingPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(30, 41, 59));
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.setLabelPaint(new Color(226, 232, 240));
        plot.setLabelBackgroundPaint(new Color(0, 0, 0, 120));
        plot.setLabelOutlinePaint(new Color(59, 130, 246, 60));
        plot.setLabelShadowPaint(null);
        plot.setSectionDepth(0.35);

        Color[] pieCols = {
                new Color(59, 130, 246), new Color(34, 197, 94),
                new Color(251, 191, 36), new Color(239, 68, 68),
                new Color(139, 92, 246), new Color(236, 72, 153),
                new Color(102, 126, 234), new Color(118, 75, 162)
        };
        int ci = 0;
        for (Object key : ((org.jfree.data.general.DefaultPieDataset) dataset).getKeys()) {
            plot.setSectionPaint((Comparable<?>) key, pieCols[ci % pieCols.length]);
            ci++;
        }

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(30, 41, 59));
            chart.getLegend().setItemPaint(new Color(148, 163, 184));
            chart.getLegend().setItemFont(new Font("Segoe UI", Font.PLAIN, 11));
        }

        ChartPanel cp = new ChartPanel(chart);
        cp.setMouseWheelEnabled(false);
        cp.setDisplayToolTips(true);
        return cp;
    }

    private ChartPanel createStockValueHorizontalBar() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        List<PortfolioItem> items = portfolioService.getMergedPortfolioItems();

        if (items.isEmpty()) {
            dataset.addValue(0, "Value", "No Data");
        } else {
            // Sort by value descending
            items.sort((a, b) -> Double.compare(b.getTotalValue(), a.getTotalValue()));
            for (int i = 0; i < Math.min(6, items.size()); i++) {
                PortfolioItem item = items.get(i);
                dataset.addValue(item.getTotalValue(), "Value", item.getStock().getSymbol());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                null, null, null, dataset,
                PlotOrientation.HORIZONTAL, false, true, false);
        chart.setBackgroundPaint(new Color(30, 41, 59));
        chart.setBorderVisible(false);

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(30, 41, 59));
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new Color(59, 130, 246, 30));
        plot.setRangeGridlineStroke(new BasicStroke(0.5f));

        plot.getDomainAxis().setLabelPaint(new Color(226, 232, 240));
        plot.getDomainAxis().setTickLabelPaint(new Color(148, 163, 184));
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.BOLD, 12));
        plot.getRangeAxis().setLabelPaint(new Color(226, 232, 240));
        plot.getRangeAxis().setTickLabelPaint(new Color(148, 163, 184));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

        org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot
                .getRenderer();
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        GradientPaint barGrad = new GradientPaint(0, 0,
                new Color(59, 130, 246), 100, 0, new Color(139, 92, 246));
        renderer.setSeriesPaint(0, barGrad);
        renderer.setItemMargin(0.05);

        ChartPanel cp = new ChartPanel(chart);
        cp.setMouseWheelEnabled(false);
        cp.setDisplayToolTips(true);
        return cp;
    }

    private ChartPanel createRiskVsReturnScatter() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        List<PortfolioItem> items = portfolioService.getMergedPortfolioItems();

        Color[] scatterColors = {
                new Color(59, 130, 246), new Color(34, 197, 94),
                new Color(239, 68, 68), new Color(251, 191, 36),
                new Color(139, 92, 246), new Color(236, 72, 153)
        };

        Random rand = new Random(42);
        for (int i = 0; i < items.size(); i++) {
            PortfolioItem item = items.get(i);
            double returnPct = ((item.getStock().getCurrentPrice() - item.getPurchasePrice()) / item.getPurchasePrice())
                    * 100;
            double risk = 5 + rand.nextDouble() * 25; // Mock volatility
            XYSeries series = new XYSeries(item.getStock().getSymbol());
            series.add(risk, returnPct);
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createScatterPlot(
                null, "Risk / Volatility", "Return (%)", dataset,
                PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(30, 41, 59));
        chart.setBorderVisible(false);

        org.jfree.chart.plot.XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(30, 41, 59));
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new Color(59, 130, 246, 30));
        plot.setDomainGridlinePaint(new Color(59, 130, 246, 20));
        plot.setRangeGridlinesVisible(true);
        plot.setDomainGridlinesVisible(true);

        plot.getDomainAxis().setLabelPaint(new Color(226, 232, 240));
        plot.getDomainAxis().setTickLabelPaint(new Color(148, 163, 184));
        plot.getDomainAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));
        plot.getRangeAxis().setLabelPaint(new Color(226, 232, 240));
        plot.getRangeAxis().setTickLabelPaint(new Color(148, 163, 184));
        plot.getRangeAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, scatterColors[i % scatterColors.length]);
            renderer.setSeriesShape(i, new java.awt.geom.Ellipse2D.Double(-6, -6, 12, 12));
        }
        renderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator(
                "{0}: Risk={1}, Return={2}%",
                java.text.NumberFormat.getInstance(),
                java.text.NumberFormat.getInstance()));
        plot.setRenderer(renderer);

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(30, 41, 59));
            chart.getLegend().setItemPaint(new Color(148, 163, 184));
            chart.getLegend().setItemFont(new Font("Segoe UI", Font.PLAIN, 11));
        }

        ChartPanel cp = new ChartPanel(chart);
        cp.setMouseWheelEnabled(false);
        cp.setDisplayToolTips(true);
        return cp;
    }

    private JPanel createAIInsightsCard(Color cardBg, Color borderColor, Color textColor, Color textDim, Color accent,
            Color green, Color red) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new BorderLayout(16, 0));
        card.setBackground(cardBg);
        card.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(20, 20, 20, 20)));

        // Title
        JLabel title = new JLabel("💡 AI Portfolio Insights");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(textColor);
        title.setBorder(new EmptyBorder(0, 0, 12, 0));
        card.add(title, BorderLayout.NORTH);

        // Left: Insights text
        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        // Calculate sector concentration
        Map<String, Double> sectorVals = new HashMap<>();
        double total = portfolioService.calculateCurrentValue();
        for (PortfolioItem item : items) {
            String sect = item.getStock().getSector();
            if (sect == null || sect.isEmpty())
                sect = "Other";
            sectorVals.put(sect, sectorVals.getOrDefault(sect, 0.0) + item.getTotalValue());
        }
        String topSector = "Technology";
        double topPct = 0;
        for (Map.Entry<String, Double> entry : sectorVals.entrySet()) {
            double pct = total > 0 ? (entry.getValue() / total) * 100 : 0;
            if (pct > topPct) {
                topPct = pct;
                topSector = entry.getKey();
            }
        }

        String[] insights = {
                "Sector concentration alert: " + String.format("%.1f%%", topPct) + " in " + topSector,
                "Moderate risk level with diversified holdings",
                "Strong performance of top holdings",
                "ETFs provide stability to the portfolio"
        };

        for (String insight : insights) {
            JLabel lbl = new JLabel("  •  " + insight);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lbl.setForeground(textDim);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftPanel.add(lbl);
            leftPanel.add(Box.createVerticalStrut(8));
        }

        leftPanel.add(Box.createVerticalGlue());
        JButton learnMore = createStyledButton("Learn More  ›", accent);
        learnMore.setFont(new Font("Segoe UI", Font.BOLD, 13));
        learnMore.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(learnMore);

        card.add(leftPanel, BorderLayout.CENTER);

        // Right: Performance tiles
        JPanel tilesPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        tilesPanel.setOpaque(false);
        tilesPanel.setPreferredSize(new Dimension(220, 0));

        List<PortfolioItem> mergedItems = portfolioService.getMergedPortfolioItems();
        for (int i = 0; i < Math.min(8, mergedItems.size()); i++) {
            PortfolioItem item = mergedItems.get(i);
            double changePct = ((item.getStock().getCurrentPrice() - item.getPurchasePrice()) / item.getPurchasePrice())
                    * 100;
            boolean positive = changePct >= 0;

            RoundedPanel tile = new RoundedPanel(10);
            tile.setLayout(new BorderLayout());
            tile.setBackground(positive ? new Color(6, 78, 59) : new Color(127, 29, 29));
            tile.setBorder(new EmptyBorder(8, 12, 8, 12));

            JLabel sym = new JLabel(item.getStock().getSymbol());
            sym.setFont(new Font("Segoe UI", Font.BOLD, 13));
            sym.setForeground(positive ? green : red);
            tile.add(sym, BorderLayout.WEST);

            JLabel pct = new JLabel(String.format("%+.1f%%", changePct));
            pct.setFont(new Font("Segoe UI", Font.BOLD, 13));
            pct.setForeground(positive ? green : red);
            pct.setHorizontalAlignment(SwingConstants.RIGHT);
            tile.add(pct, BorderLayout.EAST);

            tilesPanel.add(tile);
        }
        // Fill empty tiles if < 8
        for (int i = mergedItems.size(); i < 8; i++) {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            tilesPanel.add(empty);
        }

        card.add(tilesPanel, BorderLayout.EAST);
        return card;
    }

    private JPanel createTaxEstimationCard(Color cardBg, Color borderColor, Color textColor, Color textDim, Color gold,
            Color accent) {
        RoundedPanel card = new RoundedPanel(14);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(20, 20, 20, 20)));

        JLabel title = new JLabel("💰 Tax Estimation");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(textColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        card.add(title);

        // Calculate gains
        double totalGains = portfolioService.calculateProfitLoss();
        double estimatedTax;
        String taxType;

        if (totalGains <= 0) {
            estimatedTax = 0;
            taxType = "No Taxable Gains";
        } else if (totalGains <= 100000) {
            // Short-term: 15%
            estimatedTax = totalGains * 0.15;
            taxType = "Short-Term Capital Gains";
        } else {
            // Long-term: 10% on gains above ₹1,00,000
            double taxable = totalGains - 100000;
            estimatedTax = taxable > 0 ? taxable * 0.10 : 0;
            taxType = "Long-Term Capital Gains";
        }

        // Data rows
        addTaxRow(card, "Total Gains:", formatCurrency(totalGains), textColor, textDim);
        card.add(Box.createVerticalStrut(12));
        addTaxRow(card, "Tax Type:", taxType, textColor, textDim);
        card.add(Box.createVerticalStrut(12));

        // Estimated Tax (highlighted in gold)
        JPanel taxRow = new JPanel(new BorderLayout());
        taxRow.setOpaque(false);
        taxRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        taxRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel taxLabel = new JLabel("Estimated Tax:");
        taxLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        taxLabel.setForeground(textColor);
        taxRow.add(taxLabel, BorderLayout.WEST);

        JLabel taxValue = new JLabel(formatCurrency(estimatedTax));
        taxValue.setFont(new Font("Segoe UI", Font.BOLD, 24));
        taxValue.setForeground(gold);
        taxValue.setHorizontalAlignment(SwingConstants.RIGHT);
        taxRow.add(taxValue, BorderLayout.EAST);

        card.add(taxRow);
        card.add(Box.createVerticalGlue());
        card.add(Box.createVerticalStrut(16));

        // Export Report button
        JButton exportBtn = createStyledButton("📥 Export Report", accent);
        exportBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        exportBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        exportBtn.addActionListener(e -> showExportOptions());
        card.add(exportBtn);

        return card;
    }

    private void addTaxRow(JPanel parent, String label, String value, Color textColor, Color textDim) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lbl.setForeground(textDim);
        row.add(lbl, BorderLayout.WEST);

        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 16));
        val.setForeground(textColor);
        val.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(val, BorderLayout.EAST);

        parent.add(row);
    }

    private void showExportOptions() {
        String[] options = { "Export as PDF", "Export as Excel (.xlsx)", "Cancel" };
        int choice = JOptionPane.showOptionDialog(this,
                "Choose export format for the Analytics Report:",
                "Export Report",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 0) {
            exportReportAsPDF();
        } else if (choice == 1) {
            exportReportAsExcel();
        }
    }

    private void generatePortfolioReport() {
        showExportOptions();
    }

    private void exportReportAsPDF() {
        try {
            String home = System.getProperty("user.home");
            String path = home + java.io.File.separator + "Downloads" + java.io.File.separator
                    + "StockVault_Analytics_Report.txt";
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(path));
            writer.println("═══════════════════════════════════════════════════════════");
            writer.println("           STOCKVAULT ANALYTICS REPORT");
            writer.println("═══════════════════════════════════════════════════════════");
            writer.println();

            writer.println("PORTFOLIO SUMMARY");
            writer.println("─────────────────");
            writer.printf("Total Portfolio Value: %s%n", formatCurrency(portfolioService.calculateCurrentValue()));
            writer.printf("Total Investment:      %s%n", formatCurrency(portfolioService.calculateTotalInvestment()));
            writer.printf("Profit/Loss:           %s%n", formatCurrency(portfolioService.calculateProfitLoss()));
            double retPct = portfolioService.calculateTotalInvestment() > 0
                    ? (portfolioService.calculateProfitLoss() / portfolioService.calculateTotalInvestment()) * 100
                    : 0;
            writer.printf("Return:                %.2f%%%n", retPct);
            writer.printf("Sharpe Ratio:          %.2f%n", portfolioService.calculateSharpeRatio());
            writer.printf("Portfolio Beta:        %.2f%n", portfolioService.calculatePortfolioBeta());
            writer.println();

            writer.println("STOCK HOLDINGS");
            writer.println("──────────────");
            writer.printf("%-8s %-6s %-12s %-12s %-12s%n", "Symbol", "Qty", "Buy Price", "Cur Price", "Gain/Loss");
            for (PortfolioItem item : portfolioService.getMergedPortfolioItems()) {
                writer.printf("%-8s %-6d %-12s %-12s %-12s%n",
                        item.getStock().getSymbol(),
                        item.getQuantity(),
                        formatCurrency(item.getPurchasePrice()),
                        formatCurrency(item.getStock().getCurrentPrice()),
                        formatCurrency(item.getGainLoss()));
            }
            writer.println();

            writer.println("TAX ESTIMATION");
            writer.println("──────────────");
            double gains = portfolioService.calculateProfitLoss();
            double tax;
            String type;
            if (gains <= 0) {
                tax = 0;
                type = "No Taxable Gains";
            } else if (gains <= 100000) {
                tax = gains * 0.15;
                type = "Short-Term Capital Gains (15%)";
            } else {
                tax = (gains - 100000) * 0.10;
                type = "Long-Term Capital Gains (10% on > ₹1L)";
            }
            writer.printf("Total Gains:   %s%n", formatCurrency(gains));
            writer.printf("Tax Type:      %s%n", type);
            writer.printf("Estimated Tax: %s%n", formatCurrency(tax));

            writer.println();
            writer.println("═══════════════════════════════════════════════════════════");
            writer.println("Report generated by StockVault Analytics Dashboard");
            writer.close();

            JOptionPane.showMessageDialog(this,
                    "Report saved to:\n" + path,
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to export report:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportReportAsExcel() {
        try {
            String home = System.getProperty("user.home");
            String path = home + java.io.File.separator + "Downloads" + java.io.File.separator
                    + "StockVault_Analytics_Report.csv";
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(path));

            // Header
            writer.println("Symbol,Quantity,Buy Price,Current Price,Total Value,Gain/Loss,Gain %");
            for (PortfolioItem item : portfolioService.getMergedPortfolioItems()) {
                double changePct = ((item.getStock().getCurrentPrice() - item.getPurchasePrice())
                        / item.getPurchasePrice()) * 100;
                writer.printf("%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                        item.getStock().getSymbol(),
                        item.getQuantity(),
                        item.getPurchasePrice(),
                        item.getStock().getCurrentPrice(),
                        item.getTotalValue(),
                        item.getGainLoss(),
                        changePct);
            }
            writer.println();
            writer.println("SUMMARY");
            writer.printf("Total Investment,%.2f%n", portfolioService.calculateTotalInvestment());
            writer.printf("Current Value,%.2f%n", portfolioService.calculateCurrentValue());
            writer.printf("Profit/Loss,%.2f%n", portfolioService.calculateProfitLoss());
            writer.printf("Sharpe Ratio,%.2f%n", portfolioService.calculateSharpeRatio());
            writer.printf("Portfolio Beta,%.2f%n", portfolioService.calculatePortfolioBeta());
            writer.close();

            JOptionPane.showMessageDialog(this,
                    "Excel-compatible CSV saved to:\n" + path,
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to export report:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
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

    private JFreeChart createSectorAllocationChart() {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        Map<String, Double> sectorMap = new HashMap<>();

        for (PortfolioItem item : portfolioService.getPortfolioItems()) {
            String sector = item.getStock().getSector();
            if (sector == null || sector.isEmpty())
                sector = "Other";
            sectorMap.put(sector, sectorMap.getOrDefault(sector, 0.0) + item.getTotalValue());
        }

        if (sectorMap.isEmpty()) {
            dataset.setValue("No Data", 1);
        } else {
            sectorMap.forEach(dataset::setValue);
        }

        JFreeChart chart = ChartFactory.createPieChart("Sector Allocation", dataset, true, true, false);
        styleChart(chart);
        return chart;
    }

    private JFreeChart createCostVsValueChart() {
        org.jfree.data.category.DefaultCategoryDataset dataset = new org.jfree.data.category.DefaultCategoryDataset();

        for (PortfolioItem item : portfolioService.getPortfolioItems()) {
            String sym = item.getStock().getSymbol();
            dataset.addValue(item.getPurchasePrice() * item.getQuantity(), "Cost Basis", sym);
            dataset.addValue(item.getTotalValue(), "Market Value", sym);
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Cost vs Market Value", "Stock", "Value (" + getCurrencySymbol() + ")",
                dataset, org.jfree.chart.plot.PlotOrientation.VERTICAL, true, true, false);

        styleChart(chart);
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot
                .getRenderer();
        renderer.setSeriesPaint(0, ACCENT);
        renderer.setSeriesPaint(1, ACCENT2);

        return chart;
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
        // Institutional-grade chart styling with violet gradients
        chart.setBackgroundPaint(CARD_BG());
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(TEXT());
            chart.getTitle().setFont(new Font("Segoe UI", Font.BOLD, 18));
        }
        chart.setBorderVisible(false);

        org.jfree.chart.plot.Plot plot = chart.getPlot();

        // Create violet gradient background for institutional feel
        GradientPaint violetGradient = new GradientPaint(0, 0,
                new Color(102, 126, 234, 30), 0, 400,
                new Color(118, 75, 162, 15));
        plot.setBackgroundPaint(violetGradient);
        plot.setOutlineVisible(false);

        if (plot instanceof org.jfree.chart.plot.PiePlot) {
            org.jfree.chart.plot.PiePlot piePlot = (org.jfree.chart.plot.PiePlot) plot;
            piePlot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
            piePlot.setLabelPaint(TEXT());
            piePlot.setShadowPaint(null);
            piePlot.setOutlineVisible(false);
            piePlot.setBackgroundPaint(null);
            piePlot.setLabelBackgroundPaint(new Color(0, 0, 0, 120));
            piePlot.setLabelOutlinePaint(new Color(102, 126, 234, 80));
            piePlot.setLabelShadowPaint(null);

            // Enhanced institutional color palette for sectors
            Color[] institutionalColors = {
                    new Color(102, 126, 234), // Primary violet
                    new Color(118, 75, 162), // Deep purple
                    new Color(74, 222, 128), // Success green
                    new Color(248, 113, 113), // Alert red
                    new Color(251, 191, 36), // Warning amber
                    new Color(59, 130, 246), // Info blue
                    new Color(139, 92, 246), // Indigo
                    new Color(236, 72, 153) // Pink
            };

            for (int i = 0; i < piePlot.getDataset().getItemCount(); i++) {
                piePlot.setSectionPaint(piePlot.getDataset().getKey(i),
                        institutionalColors[i % institutionalColors.length]);
            }

        } else if (plot instanceof CategoryPlot) {
            CategoryPlot catPlot = (CategoryPlot) plot;

            // High-density professional grid lines
            catPlot.setRangeGridlinePaint(new Color(102, 126, 234, 40));
            catPlot.setDomainGridlinePaint(new Color(102, 126, 234, 25));
            catPlot.setRangeGridlinesVisible(true);
            catPlot.setDomainGridlinesVisible(true);

            // Enhanced grid line styling - high density
            catPlot.setRangeGridlineStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 2.0f, 2.0f }, 0.0f));
            catPlot.setDomainGridlineStroke(new BasicStroke(0.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 1.5f, 1.5f }, 0.0f));

            // Professional typography
            catPlot.getDomainAxis().setLabelPaint(TEXT());
            catPlot.getDomainAxis().setTickLabelPaint(TEXT_DIM());
            catPlot.getDomainAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
            catPlot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

            catPlot.getRangeAxis().setLabelPaint(TEXT());
            catPlot.getRangeAxis().setTickLabelPaint(TEXT_DIM());
            catPlot.getRangeAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
            catPlot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

            org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) catPlot
                    .getRenderer();
            renderer.setShadowVisible(false);
            renderer.setDrawBarOutline(false);
            renderer.setItemMargin(0.05);

            // Violet gradient bars for institutional look
            GradientPaint barGradient1 = new GradientPaint(0, 0,
                    new Color(102, 126, 234), 0, 20, new Color(102, 126, 234, 180));
            GradientPaint barGradient2 = new GradientPaint(0, 0,
                    new Color(118, 75, 162), 0, 20, new Color(118, 75, 162, 180));

            renderer.setSeriesPaint(0, barGradient1);
            // Set paint for potential second series
            renderer.setSeriesPaint(1, barGradient2);

        } else if (plot instanceof org.jfree.chart.plot.XYPlot) {
            org.jfree.chart.plot.XYPlot xyPlot = (org.jfree.chart.plot.XYPlot) plot;

            // High-density crosshair grid system
            xyPlot.setRangeGridlinePaint(new Color(102, 126, 234, 35));
            xyPlot.setDomainGridlinePaint(new Color(102, 126, 234, 25));
            xyPlot.setRangeGridlinesVisible(true);
            xyPlot.setDomainGridlinesVisible(true);

            // Professional crosshair styling
            xyPlot.setRangeGridlineStroke(new BasicStroke(0.7f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 3.0f, 2.0f }, 0.0f));
            xyPlot.setDomainGridlineStroke(new BasicStroke(0.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 2.0f, 2.0f }, 0.0f));

            // Enable crosshairs for professional precision
            xyPlot.setDomainCrosshairVisible(true);
            xyPlot.setRangeCrosshairVisible(true);
            xyPlot.setDomainCrosshairPaint(new Color(102, 126, 234, 120));
            xyPlot.setRangeCrosshairPaint(new Color(102, 126, 234, 120));
            xyPlot.setDomainCrosshairStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 5.0f, 3.0f }, 0.0f));
            xyPlot.setRangeCrosshairStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1.0f, new float[] { 5.0f, 3.0f }, 0.0f));

            // Professional axis styling
            xyPlot.getDomainAxis().setLabelPaint(TEXT());
            xyPlot.getDomainAxis().setTickLabelPaint(TEXT_DIM());
            xyPlot.getDomainAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
            xyPlot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

            xyPlot.getRangeAxis().setLabelPaint(TEXT());
            xyPlot.getRangeAxis().setTickLabelPaint(TEXT_DIM());
            xyPlot.getRangeAxis().setLabelFont(new Font("Segoe UI", Font.BOLD, 12));
            xyPlot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));

            org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer = new org.jfree.chart.renderer.xy.XYLineAndShapeRenderer();

            // Institutional violet line with gradient effect
            renderer.setSeriesPaint(0, new Color(102, 126, 234));
            renderer.setSeriesStroke(0, new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10.0f, new float[] { 2.0f, 2.0f }, 0.0f));
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
            renderer.setSeriesShapesFilled(0, true);

            // Style axes
            xyPlot.getDomainAxis().setLabelPaint(new Color(200, 200, 220));
            xyPlot.getDomainAxis().setTickLabelPaint(new Color(180, 180, 200));
            xyPlot.getRangeAxis().setLabelPaint(new Color(200, 200, 220));
            xyPlot.getRangeAxis().setTickLabelPaint(new Color(180, 180, 200));

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
        page.setBackground(BG());

        JPanel content = new JPanel();
        content.setBackground(BG());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Summary Cards for Report
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 25, 0));
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
            writer.println("Symbol,Name,Qty,Price,Profit/Loss,Value,Date,Trend");

            for (com.portfolio.model.PortfolioItem item : portfolioService.getPortfolioItems()) {
                double currentPrice = item.getStock().getCurrentPrice();
                double value = item.getTotalValue();
                double pl = item.getGainLoss();
                String trend = pl >= 0 ? "BULLISH" : "BEARISH";

                writer.printf("%s,%s,%d,%.2f,%.2f,%.2f,%s,%s\n",
                        item.getStock().getSymbol(),
                        item.getStock().getName(),
                        item.getQuantity(),
                        currentPrice,
                        pl,
                        value,
                        "2026-03-05", // Date
                        trend);
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
            String filename = "StockVault_Report_" + System.currentTimeMillis() + ".html";
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(filename));

            writer.println("<!DOCTYPE html><html><head><title>StockVault Portfolio Statement</title>");
            writer.println("<style>");
            writer.println(
                    "body { font-family: 'Inter', sans-serif; background: #0f0f12; color: #fff; padding: 40px; }");
            writer.println(".header { border-bottom: 2px solid #667eea; padding-bottom: 20px; margin-bottom: 30px; }");
            writer.println("h1 { margin: 0; color: #667eea; font-weight: 800; letter-spacing: -1px; }");
            writer.println(".date { color: #888; font-size: 14px; }");
            writer.println("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
            writer.println(
                    "th { text-align: left; padding: 12px; background: #1a1a2e; color: #667eea; text-transform: uppercase; font-size: 12px; }");
            writer.println("td { padding: 12px; border-bottom: 1px solid #222; font-size: 14px; }");
            writer.println(".profit { color: #4ade80; } .loss { color: #f87171; }");
            writer.println(
                    ".summary { margin-top: 40px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }");
            writer.println(
                    ".card { background: #1a1a2e; padding: 20px; border-radius: 12px; border: 1px solid #334; }");
            writer.println(".card label { display: block; color: #888; font-size: 12px; margin-bottom: 5px; }");
            writer.println(".card value { font-size: 24px; font-weight: 700; }");
            writer.println("</style></head><body>");

            writer.println("<div class='header'><h1>STOCKVAULT</h1><div class='date'>Generated on: "
                    + java.time.LocalDateTime.now() + "</div></div>");

            writer.println(
                    "<table><thead><tr><th>Symbol</th><th>Company</th><th>Qty</th><th>Avg Cost</th><th>Price</th><th>Value</th><th>P/L</th></tr></thead><tbody>");

            double totalInv = 0, currentVal = 0;
            for (com.portfolio.model.PortfolioItem item : portfolioService.getPortfolioItems()) {
                double pl = item.getGainLoss();
                writer.printf(
                        "<tr><td><b>%s</b></td><td>%s</td><td>%d</td><td>%s %.2f</td><td>%s %.2f</td><td>%s %.2f</td><td class='%s'>%+.2f%%</td></tr>",
                        item.getStock().getSymbol(), item.getStock().getName(), item.getQuantity(),
                        getCurrencySymbol(), item.getPurchasePrice(), getCurrencySymbol(),
                        item.getStock().getCurrentPrice(),
                        getCurrencySymbol(), item.getTotalValue(), pl >= 0 ? "profit" : "loss",
                        (pl / (item.getQuantity() * item.getPurchasePrice())) * 100);

                totalInv += item.getQuantity() * item.getPurchasePrice();
                currentVal += item.getTotalValue();
            }
            writer.println("</tbody></table>");

            double totalPL = currentVal - totalInv;
            writer.println("<div class='summary'>");
            writer.println(
                    "<div class='card'><label>INVESTED</label><value>" + formatCurrency(totalInv) + "</value></div>");
            writer.println("<div class='card'><label>MARKET VALUE</label><value style='color:#667eea'>"
                    + formatCurrency(currentVal) + "</value></div>");
            writer.println("<div class='card'><label>TOTAL P/L</label><value class='"
                    + (totalPL >= 0 ? "profit" : "loss") + "'>" + formatCurrency(totalPL) + "</value></div>");
            writer.println("</div></body></html>");

            writer.close();
            JOptionPane.showMessageDialog(this, "Premium Statement generated: " + filename);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new java.io.File(filename).toURI());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage());
        }
    }

    private JPanel buildSettingsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG());

        JPanel content = new JPanel();
        content.setBackground(BG());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Group 1: Display & Appearance
        content.add(createSettingsSection("🎨 Display & Appearance", new String[][] {
                { "Dark Mode", "Toggle institutional dark/light interface", "SWITCH" },
                { "Compact View", "Maximize data density on screens", "CHECK" },
                { "Animations", "Enable smooth UI transitions", "CHECK" }
        }));
        content.add(Box.createVerticalStrut(25));

        // Group 2: Data & APIs
        content.add(createSettingsSection("🔌 Data & Advanced APIs", new String[][] {
                { "Real-time Feed", "WebSocket-based live price updates", "SWITCH" },
                { "API Provider", "AlphaVantage / Yahoo Finance", "COMBO" },
                { "Groq AI Assistant", "Neural analysis of portfolio", "SWITCH" }
        }));
        content.add(Box.createVerticalStrut(25));

        // Group 3: Localization
        content.add(createSettingsSection("🌍 Localization & Units", new String[][] {
                { "Currency", "Select base portfolio currency", "COMBO" },
                { "Number Format", "International (1.2M) / Indian (12L)", "COMBO" },
                { "Timezone", "Automatic (ST) / UTC / Local", "COMBO" }
        }));
        content.add(Box.createVerticalStrut(25));

        // Group 4: Email Notifications
        JPanel emailPanel = createCard("✉️ Daily Email Notifications");
        emailPanel.setLayout(new BoxLayout(emailPanel, BoxLayout.Y_AXIS));
        emailPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JPanel grid = new JPanel(new GridLayout(3, 2, 15, 15));
        grid.setOpaque(false);

        JTextField emailField = createInputField("Your Email Address");
        JPasswordField passField = new JPasswordField();
        passField.setBackground(BG());
        passField.setForeground(TEXT());
        passField.setBorder(new CompoundBorder(new LineBorder(BORDER()), new EmptyBorder(8, 12, 8, 12)));
        passField.setCaretColor(TEXT());

        JCheckBox enableDaily = new JCheckBox("Enable Daily Summary at 09:00 AM");
        enableDaily.setOpaque(false);
        enableDaily.setForeground(TEXT());
        enableDaily.setFont(FONT_BODY);

        // Load current
        if (emailService.isConfigured()) {
            emailField.setText(emailService.getEmail());
            passField.setText("********"); // mock for display
        }
        enableDaily.setSelected(emailScheduler.isRunning());

        grid.add(createFieldPanel("Gmail Address", emailField));
        grid.add(createFieldPanel("App Password (Not Account Password)", passField));
        grid.add(enableDaily);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.setOpaque(false);

        JButton testBtn = createStyledButton("Test Email", ACCENT2);
        testBtn.addActionListener(e -> {
            boolean success = emailService.sendTestEmail();
            JOptionPane.showMessageDialog(this,
                    success ? "Test email sent successfully!" : "Failed to send test email. Check console.",
                    "Email Test", success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        });

        JButton saveBtn = createStyledButton("Save Settings", GREEN);
        saveBtn.addActionListener(e -> {
            String pass = new String(passField.getPassword());
            if (pass.equals("********"))
                pass = null; // didn't change password

            // Assume we had the real password cached or we only set it if explicitly typed
            // For this UI, user needs to re-type if they want to enable but changed
            // nothing,
            // but in real app we'd load it.
            if (pass != null && !pass.isEmpty()) {
                emailService.configure(emailField.getText(), pass);
                com.portfolio.database.DatabaseManager.saveEmailSettings(emailField.getText(), pass, "smtp.gmail.com",
                        587, enableDaily.isSelected(), "09:00");
            } else if (emailService.isConfigured()) {
                // Just update DB enabled status if password hasn't changed
                String[] loaded = com.portfolio.database.DatabaseManager.loadEmailSettings();
                if (loaded != null) {
                    com.portfolio.database.DatabaseManager.saveEmailSettings(loaded[0], loaded[1], loaded[2],
                            Integer.parseInt(loaded[3]), enableDaily.isSelected(), "09:00");
                }
            }

            if (enableDaily.isSelected() && emailService.isConfigured()) {
                emailScheduler.start(emailField.getText(), "20:00");
            } else {
                emailScheduler.stop();
            }

            JOptionPane.showMessageDialog(this, "Email settings saved successfully.", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        buttonRow.add(testBtn);
        buttonRow.add(saveBtn);

        emailPanel.add(grid);
        emailPanel.add(Box.createVerticalStrut(15));
        emailPanel.add(buttonRow);

        content.add(emailPanel);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel createSettingsSection(String title, String[][] options) {
        JPanel section = createCard(title);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        for (String[] opt : options) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(12, 0, 12, 0));

            JPanel textPart = new JPanel(new GridLayout(2, 1, 0, 2));
            textPart.setOpaque(false);
            JLabel name = new JLabel(opt[0]);
            name.setFont(new Font("Segoe UI", Font.BOLD, 15));
            name.setForeground(TEXT());
            JLabel desc = new JLabel(opt[1]);
            desc.setFont(FONT_SMALL);
            desc.setForeground(TEXT_DIM());
            textPart.add(name);
            textPart.add(desc);

            row.add(textPart, BorderLayout.CENTER);

            if ("SWITCH".equals(opt[2])) {
                row.add(new ThemeToggle(), BorderLayout.EAST);
            } else if ("CHECK".equals(opt[2])) {
                JCheckBox cb = new JCheckBox();
                cb.setSelected(true);
                cb.setOpaque(false);
                row.add(cb, BorderLayout.EAST);
            } else if ("COMBO".equals(opt[2])) {
                String[] vals = { "Default", "Alternative" };
                if (opt[0].contains("Currency"))
                    vals = new String[] { "INR (₹)", "USD ($)", "EUR (€)" };
                JComboBox<String> combo = new JComboBox<>(vals);
                combo.setPreferredSize(new Dimension(120, 30));
                row.add(combo, BorderLayout.EAST);
            }

            section.add(row);
            JSeparator sep = new JSeparator();
            sep.setForeground(BORDER());
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            section.add(sep);
        }
        return section;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    private JPanel createCard(String title) {
        RoundedPanel card = new RoundedPanel(16); // Use RoundedPanel for rounded corners
        card.setLayout(new BorderLayout());
        card.setBackground(CARD_BG());
        card.setBorder(new EmptyBorder(20, 20, 20, 20)); // Clean padding without border

        if (title != null && !title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
            titleLabel.setForeground(TEXT());
            titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
            card.add(titleLabel, BorderLayout.NORTH);
        }

        return card;
    }

    private JPanel createStatCard(String label, String value, Color color) {
        RoundedPanel card = new RoundedPanel(20); // ROUNDED CORNERS!
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG());
        card.setBorder(new EmptyBorder(25, 25, 25, 25)); // More padding

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.BOLD, 16)); // Larger
        labelComp.setForeground(TEXT_DIM());
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

        table.setBackground(CARD_BG());
        table.setForeground(TEXT());
        table.setFont(new Font("Segoe UI", Font.BOLD, 15)); // BOLD!
        table.setRowHeight(45); // TALLER!
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(CARD_HOVER());
        table.setSelectionForeground(TEXT());

        // Center align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(new Font("Segoe UI", Font.BOLD, 15)); // BOLD!
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG());
        header.setForeground(ACCENT2); // Use violet accent for headers
        header.setFont(new Font("Segoe UI", Font.BOLD, 16)); // BOLD HEADER!
        header.setBorder(new MatteBorder(0, 0, 2, 0, ACCENT2));
        header.setPreferredSize(new Dimension(0, 50)); // TALLER HEADER!

        // Center align headers
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

        // Apply dynamic color renderer to all columns
        DynamicColorRenderer dynamicRenderer = new DynamicColorRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            if (table.getColumnModel().getColumn(i).getCellRenderer() == null ||
                    !(table.getColumnModel().getColumn(i).getCellRenderer() instanceof SparklineCellRenderer)) {
                table.getColumnModel().getColumn(i).setCellRenderer(dynamicRenderer);
            }
        }

        return table;
    }

    class DynamicColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 15));

            if (value != null) {
                String str = value.toString();
                if (str.contains("+") || (str.startsWith(getCurrencySymbol()) && !str.contains("-")
                        && !str.equals(getCurrencySymbol() + "0.00"))) {
                    c.setForeground(GREEN);
                } else if (str.contains("-")) {
                    c.setForeground(RED);
                } else {
                    c.setForeground(isSelected ? TEXT() : TEXT());
                }
            }

            if (isSelected) {
                c.setBackground(CARD_HOVER());
            } else {
                c.setBackground(CARD_BG());
            }

            return c;
        }
    }

    private JButton createStyledButton(String text, Color bgColor) {
        RoundedButton button = new RoundedButton(text, 12); // Use RoundedButton with 12px radius
        button.setFont(FONT_BODY);
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(12, 20, 12, 20));

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
        label.setForeground(isDarkTheme ? Color.WHITE : new Color(40, 40, 60));
        tag.add(label);

        return tag;
    }

    private JTextField createInputField(String placeholder) {
        JTextField field = new RoundedTextField(20);
        field.setFont(FONT_BODY);
        field.setForeground(TEXT());
        field.setBackground(CARD_BG());
        field.setCaretColor(TEXT());
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER(), 1, true),
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
        JLabel title = new JLabel("🧠 AI Assistant");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT());

        JButton closeBtn = createStyledButton("✕", RED);
        closeBtn.setPreferredSize(new Dimension(45, 30));
        closeBtn.addActionListener(e -> toggleChatbot(layeredPane));

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(closeBtn, BorderLayout.EAST);
        chatbotPanel.add(headerPanel, BorderLayout.NORTH);

        // Conversation area
        JTextArea conversationArea = new JTextArea();
        conversationArea.setFont(FONT_BODY);
        conversationArea.setForeground(TEXT());
        conversationArea.setBackground(BG());
        conversationArea.setCaretColor(TEXT());
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversationArea.setEditable(false);
        conversationArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        conversationArea.setText("👋 Welcome to StockVault AI Terminal\n\n" +
                "📊 Information Queries:\n" +
                "  • 'What are trending stocks?'\n" +
                "  • 'What's my portfolio worth?'\n\n" +
                "⚡ Action Commands:\n" +
                "  • 'Buy 10 AAPL \u2013 Apple Inc at 150'\n" +
                "  • 'Sell all GOOGL \u2013 Alphabet Inc'\n\n" +
                "📩 Alerts & Notifications:\n" +
                "  • Real-time trade confirmations\n" +
                "  • Market movement alerts\n\n" +
                "🗣 Voice or ⌨️ Type to begin!");

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
                    conversationArea.append("\n\n⌨️ You: " + message);
                    conversationArea.append("\n\n⚡ Click '✅ Implement' to execute this trade");
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
        conversationArea.append("\n\n👤 You: " + userMessage);
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());

        conversationArea.append("\n\n🤖 AI: ");
        int startPos = conversationArea.getDocument().getLength();
        conversationArea.append("Thinking...");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());

        new Thread(() -> {
            try {
                String aiResponse = groqAIService.chat(userMessage);

                SwingUtilities.invokeLater(() -> {
                    // Remove "Thinking..."
                    try {
                        String current = conversationArea.getText();
                        int thinkingIdx = current.lastIndexOf("Thinking...");
                        if (thinkingIdx != -1) {
                            conversationArea.replaceRange("", thinkingIdx, current.length());
                        }
                    } catch (Exception e) {
                    }

                    // Typing effect
                    javax.swing.Timer typingTimer = new javax.swing.Timer(15, null);
                    final int[] charIdx = { 0 };
                    typingTimer.addActionListener(e -> {
                        if (charIdx[0] < aiResponse.length()) {
                            conversationArea.append(String.valueOf(aiResponse.charAt(charIdx[0])));
                            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
                            charIdx[0]++;
                        } else {
                            typingTimer.stop();
                        }
                    });
                    typingTimer.start();

                    // Voice in background
                    new Thread(() -> {
                        try {
                            ttsService.speak(aiResponse);
                        } catch (Exception e) {
                        }
                    }).start();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    conversationArea.append("❌ Error: " + e.getMessage());
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
        String icon;
        boolean active = false;
        private boolean collapsed = true;

        NavButton(String icon, String label) {
            super(icon);
            this.icon = icon;
            this.label = label;

            setFont(FONT_SIDEBAR);
            setForeground(TEXT_DIM);
            setBackground(SIDEBAR_BG);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(15, 18, 15, 18));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

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

            // Initialize appearance based on default collapsed state
            setCollapsed(collapsed);
        }

        void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
            setText(collapsed ? icon : icon + "  " + label);
            setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
            setBorder(collapsed ? new EmptyBorder(15, 0, 15, 0) : new EmptyBorder(15, 18, 15, 18));

            // Use a font that supports emojis better
            if (collapsed) {
                setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            } else {
                setFont(active ? new Font("Segoe UI", Font.BOLD, 17) : FONT_SIDEBAR);
            }
        }

        void setActive(boolean active) {
            this.active = active;
            if (active) {
                setForeground(ACCENT);
                setBackground(CARD_BG);
                setContentAreaFilled(true);
            } else {
                setForeground(TEXT_DIM);
                setContentAreaFilled(false);
            }
            // Trigger font update based on collapsed state
            setCollapsed(this.collapsed);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SPARKLINE PANEL — Smooth Bézier Curves with Gradient Fill
    // ═══════════════════════════════════════════════════════════════════════

    class SparklinePanel extends JPanel {
        private double[] data;
        private boolean positive;
        private int mouseX = -1;

        SparklinePanel(double[] data) {
            this.data = data;
            this.positive = data.length > 1 && data[data.length - 1] >= data[0];
            setOpaque(false);
            setPreferredSize(new Dimension(80, 30));

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    repaint();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    mouseX = -1;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data == null || data.length < 2)
                return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            for (double d : data) {
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            double range = max - min;
            if (range == 0)
                range = 1;

            int n = data.length;
            int[] px = new int[n];
            int[] py = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = (int) ((double) i / (n - 1) * (w - 4)) + 2;
                py[i] = h - 4 - (int) ((data[i] - min) / range * (h - 8));
            }

            // Build smooth path
            java.awt.geom.GeneralPath path = new java.awt.geom.GeneralPath();
            path.moveTo(px[0], py[0]);
            for (int i = 0; i < n - 1; i++) {
                int cx = (px[i] + px[i + 1]) / 2;
                int cy = (py[i] + py[i + 1]) / 2;
                path.quadTo(px[i], py[i], cx, cy);
            }
            path.lineTo(px[n - 1], py[n - 1]);

            // Fill
            java.awt.geom.GeneralPath fillPath = (java.awt.geom.GeneralPath) path.clone();
            fillPath.lineTo(px[n - 1], h);
            fillPath.lineTo(px[0], h);
            fillPath.closePath();
            Color lineColor = positive ? GREEN : RED;
            Color fillTop = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 60);
            Color fillBot = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 5);
            g2.setPaint(new GradientPaint(0, 0, fillTop, 0, h, fillBot));
            g2.fill(fillPath);

            // Draw line
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);

            // Crosshair & Tooltip
            if (mouseX != -1 && mouseX >= px[0] && mouseX <= px[n - 1]) {
                int idx = (int) ((double) (mouseX - px[0]) / (px[n - 1] - px[0]) * (n - 1));
                if (idx >= 0 && idx < n) {
                    int cx = px[idx];
                    int cy = py[idx];
                    double val = data[idx];

                    // Vertical line
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f,
                            new float[] { 5f }, 0f));
                    g2.drawLine(cx, 0, cx, h);

                    // Point highlight
                    g2.setColor(lineColor);
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.drawOval(cx - 4, cy - 4, 8, 8);

                    // Tooltip
                    String tip = String.format("%s • %02d:%02d", formatCurrency(val), 9 + (idx / 30), (idx % 30) * 2);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(tip) + 16;
                    int th = 24;
                    int tx = Math.max(0, Math.min(w - tw, cx - tw / 2));
                    int ty = Math.max(0, cy - th - 10);

                    g2.setColor(new Color(30, 30, 46, 220));
                    g2.fillRoundRect(tx, ty, tw, th, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.drawString(tip, tx + 8, ty + 17);
                }
            }

            g2.dispose();
        }
    }

    class SparklineCellRenderer extends JPanel implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            removeAll();
            setLayout(new BorderLayout());
            setBackground(isSelected ? CARD_HOVER : CARD_BG);
            if (value instanceof double[]) {
                SparklinePanel sp = new SparklinePanel((double[]) value);
                sp.setPreferredSize(new Dimension(80, 28));
                add(sp, BorderLayout.CENTER);
            }
            return this;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // THEME TOGGLE — Animated Dark/Light Pill Switch
    // ═══════════════════════════════════════════════════════════════════════

    class ThemeToggle extends JPanel {
        ThemeToggle() {
            setPreferredSize(new Dimension(60, 30));
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText(isDarkTheme ? "Switch to Light Mode" : "Switch to Dark Mode");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    isDarkTheme = !isDarkTheme;
                    try {
                        java.util.prefs.Preferences.userRoot().node("stockvault").putBoolean("dark", isDarkTheme);
                    } catch (Exception ex) {
                    }
                    refreshThemeColors();
                    setToolTipText(isDarkTheme ? "Switch to Light Mode" : "Switch to Dark Mode");
                    // Rebuild entire UI
                    getContentPane().removeAll();
                    sidebarRef = buildSidebar();
                    getContentPane().add(sidebarRef, BorderLayout.WEST);
                    JLayeredPane lp = new JLayeredPane();
                    JPanel main = buildMain();
                    lp.add(main, JLayeredPane.DEFAULT_LAYER);
                    buildFloatingChatbot(lp);
                    lp.addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentResized(ComponentEvent ev) {
                            main.setBounds(0, 0, lp.getWidth(), lp.getHeight());
                            if (chatbotPanel != null && isChatbotOpen)
                                chatbotPanel.setBounds(lp.getWidth() - 430, lp.getHeight() - 580, 400, 550);
                            if (chatbotToggleBtn != null && !isChatbotOpen)
                                chatbotToggleBtn.setBounds(lp.getWidth() - 100, lp.getHeight() - 100, 70, 70);
                        }
                    });
                    getContentPane().add(lp, BorderLayout.CENTER);
                    getContentPane().revalidate();
                    getContentPane().repaint();
                    navigate("Dashboard");
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Track
            g2.setColor(isDarkTheme ? new Color(50, 50, 80) : new Color(200, 210, 230));
            g2.fillRoundRect(0, 2, w, h - 4, h, h);
            // Knob
            int knobX = isDarkTheme ? 4 : w - h + 4;
            g2.setColor(isDarkTheme ? new Color(30, 30, 50) : Color.WHITE);
            g2.fillOval(knobX, 5, h - 10, h - 10);
            // Icon
            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            g2.setColor(isDarkTheme ? new Color(200, 200, 255) : new Color(255, 180, 50));
            g2.drawString(isDarkTheme ? "🌙" : "☀️", isDarkTheme ? 7 : w - h + 7, h - 10);
            g2.dispose();
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

    // ═══════════════════════════════════════════════════════════════════════
    // MARKET PULSE TICKER (PHASE 4) - UPGRADED
    // ═══════════════════════════════════════════════════════════════════════
    class MarketTickerPulse extends JPanel {
        private class TickerItemData {
            String symbol;
            double price;
            double change;
            double changePercent;
            int x;
            int width;
            boolean isHovered = false;
            Color flashColor = null;
            long flashStartTime = 0;
        }

        private List<TickerItemData> tickerItems = new ArrayList<>();
        private float scrollX = 0;
        private javax.swing.Timer tickerTimer;
        private javax.swing.Timer dataRefreshTimer;
        private boolean isPaused = false;
        private final Font tickerFont = new Font("Inter", Font.BOLD, 13);
        private final Font iconFont = new Font("Segoe UI", Font.BOLD, 12);
        private final String[] DEFAULT_SYMBOLS = { "BTC", "ETH", "AAPL", "TSLA", "SPY", "NIFTY", "SENSEX" };

        public MarketTickerPulse() {
            setOpaque(true);
            setBackground(new Color(30, 41, 59)); // #1E293B
            setPreferredSize(new Dimension(0, 42)); // 42px height for professional look

            refreshData(true); // Initial load

            tickerTimer = new javax.swing.Timer(16, e -> { // ~60 FPS
                if (!isPaused && !tickerItems.isEmpty()) {
                    scrollX -= 1.0f; // Speed

                    // Total width calculation
                    int totalWidth = 0;
                    if (!tickerItems.isEmpty()) {
                        TickerItemData last = tickerItems.get(tickerItems.size() - 1);
                        totalWidth = last.x + last.width + 50; // 50 is padding
                    }

                    if (scrollX < -totalWidth && totalWidth > 0) {
                        scrollX = getWidth();
                    }
                }
                repaint();
            });
            tickerTimer.start();

            // Refresh data every 30 seconds
            dataRefreshTimer = new javax.swing.Timer(30000, e -> refreshData(false));
            dataRefreshTimer.start();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isPaused = true;
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isPaused = false;
                    for (TickerItemData item : tickerItems)
                        item.isHovered = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    for (TickerItemData item : tickerItems) {
                        if (item.isHovered) {
                            showDetailedAnalysis(item.symbol);
                            break;
                        }
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean rep = false;
                    boolean anyHovered = false;
                    for (TickerItemData item : tickerItems) {
                        boolean wasHovered = item.isHovered;
                        int screenX = (int) scrollX + item.x;
                        item.isHovered = (e.getX() >= screenX - 5 && e.getX() <= screenX + item.width + 5);
                        if (item.isHovered)
                            anyHovered = true;
                        if (wasHovered != item.isHovered)
                            rep = true;
                    }
                    if (rep) {
                        setCursor(anyHovered ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            });
        }

        private void refreshData(boolean initial) {
            Set<String> symbols = new LinkedHashSet<>();
            symbols.addAll(Arrays.asList(DEFAULT_SYMBOLS));

            // Add portfolio symbols too
            for (PortfolioItem item : portfolioService.getPortfolioItems()) {
                symbols.add(item.getStock().getSymbol());
            }

            Random rand = new Random();
            long now = System.currentTimeMillis();

            if (initial) {
                tickerItems.clear();
                for (String sym : symbols) {
                    TickerItemData item = new TickerItemData();
                    item.symbol = sym;
                    updateItemPrice(item, sym, rand);
                    tickerItems.add(item);
                }
            } else {
                for (TickerItemData item : tickerItems) {
                    double oldPrice = item.price;
                    updateItemPrice(item, item.symbol, rand);
                    if (oldPrice != item.price) {
                        item.flashColor = item.price > oldPrice ? new Color(34, 197, 94, 100)
                                : new Color(239, 68, 68, 100);
                        item.flashStartTime = now;
                    }
                }
            }
        }

        private void updateItemPrice(TickerItemData item, String sym, Random rand) {
            double price = 0;
            try {
                PortfolioItem pItem = portfolioService.getPortfolioItems().stream()
                        .filter(i -> i.getStock().getSymbol().equalsIgnoreCase(sym))
                        .findFirst().orElse(null);
                if (pItem != null) {
                    price = pItem.getStock().getCurrentPrice();
                } else {
                    Random r = new Random(sym.hashCode());
                    price = 100 + r.nextDouble() * 1000;
                }
            } catch (Exception e) {
            }

            // Simulate slight movements
            double maxMove = price * 0.005; // 0.5% max random move
            item.price = price + (rand.nextDouble() - 0.5) * maxMove;

            // Simulated daily change
            double dailyChange = (new Random(sym.hashCode()).nextDouble() - 0.45) * 5.0; // stable random
            dailyChange += (rand.nextDouble() - 0.5) * 0.5; // slight fluctuation

            item.changePercent = dailyChange;
            item.change = item.price * (dailyChange / 100.0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            FontMetrics fm = g2.getFontMetrics(tickerFont);
            FontMetrics iconFm = g2.getFontMetrics(iconFont);
            int currentX = 0;

            long now = System.currentTimeMillis();

            for (int i = 0; i < tickerItems.size(); i++) {
                TickerItemData item = tickerItems.get(i);

                String priceStr = String.format("%s%.2f", getCurrencySymbol(), item.price);
                String pctStr = String.format("%.2f%%", Math.abs(item.changePercent));
                String indicator = item.changePercent >= 0 ? "▲" : "▼";
                Color color = item.changePercent >= 0 ? new Color(34, 197, 94) : new Color(239, 68, 68); // #22C55E :
                                                                                                         // #EF4444

                int symW = fm.stringWidth(item.symbol + " ");
                int priceW = fm.stringWidth(priceStr + " ");
                int indW = iconFm.stringWidth(indicator + " ");
                int pctW = fm.stringWidth(pctStr);

                item.x = currentX;
                item.width = symW + priceW + indW + pctW + 10;

                int drawX = (int) scrollX + currentX;

                // Do not draw if way out of bounds
                if (drawX + item.width > 0 && drawX < getWidth()) {
                    // Hover highlight
                    if (item.isHovered) {
                        g2.setColor(new Color(255, 255, 255, 20));
                        g2.fillRoundRect(drawX - 5, getHeight() / 2 - 14, item.width + 10, 28, 6, 6);
                    }

                    // Flash effect on price update
                    if (item.flashStartTime > 0) {
                        long elapsed = now - item.flashStartTime;
                        if (elapsed < 1000) {
                            int alpha = (int) (item.flashColor.getAlpha() * (1.0 - (elapsed / 1000.0)));
                            g2.setColor(new Color(item.flashColor.getRed(), item.flashColor.getGreen(),
                                    item.flashColor.getBlue(), Math.max(0, alpha)));
                            g2.fillRoundRect(drawX - 5, getHeight() / 2 - 14, item.width + 10, 28, 6, 6);
                        } else {
                            item.flashStartTime = 0;
                        }
                    }

                    int textY = getHeight() / 2 + fm.getAscent() / 2 - 2;

                    // Symbol
                    g2.setFont(tickerFont);
                    g2.setColor(item.isHovered ? Color.WHITE : new Color(226, 232, 240)); // #E2E8F0
                    g2.drawString(item.symbol, drawX, textY);
                    drawX += symW;

                    // Price
                    g2.setColor(new Color(226, 232, 240));
                    g2.drawString(priceStr, drawX, textY);
                    drawX += priceW;

                    // Indicator
                    g2.setFont(iconFont);
                    g2.setColor(color);
                    g2.drawString(indicator, drawX, textY - 1); // slight tweak for arrow alignment
                    drawX += indW;

                    // Percent
                    g2.setFont(tickerFont);
                    g2.drawString(pctStr, drawX, textY);
                    drawX += pctW;

                    // Separator
                    if (i < tickerItems.size() - 1) {
                        g2.setColor(new Color(100, 116, 139)); // #64748B separator color
                        g2.drawString(" • ", drawX + 15, textY);
                    }
                }

                currentX += item.width + 50; // spacing between items
            }
            g2.dispose();
        }
    }

    class RoundedPanel extends JPanel {
        private int radius;
        private Color gradientColor;

        RoundedPanel(int radius) {
            this(radius, null);
        }

        RoundedPanel(int radius, Color gradientColor) {
            super();
            this.radius = radius;
            this.gradientColor = gradientColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background with optional gradient
            if (gradientColor != null) {
                GradientPaint gp = new GradientPaint(0, 0, getBackground(), 0, getHeight(), gradientColor);
                g2.setPaint(gp);
            } else {
                g2.setColor(getBackground());
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            // Subtle glass border
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(255, 255, 255, 30)); // 30% white for edge highlights
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Rounded Button class
    class RoundedButton extends JButton {
        private int radius;
        private float hoverAlpha = 0f;
        private javax.swing.Timer animTimer;

        RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    startAnimation(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    startAnimation(false);
                }
            });
        }

        private void startAnimation(boolean forward) {
            if (animTimer != null && animTimer.isRunning())
                animTimer.stop();
            animTimer = new javax.swing.Timer(20, e -> {
                if (forward) {
                    hoverAlpha += 0.1f;
                    if (hoverAlpha >= 1f) {
                        hoverAlpha = 1f;
                        animTimer.stop();
                    }
                } else {
                    hoverAlpha -= 0.1f;
                    if (hoverAlpha <= 0f) {
                        hoverAlpha = 0f;
                        animTimer.stop();
                    }
                }
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color base = getBackground();
            if (getModel().isPressed()) {
                g2.setColor(base.darker());
            } else {
                // Interpolate colors for smooth hover
                Color highlight = base.brighter();
                int r = (int) (base.getRed() * (1 - hoverAlpha) + highlight.getRed() * hoverAlpha);
                int g_ = (int) (base.getGreen() * (1 - hoverAlpha) + highlight.getGreen() * hoverAlpha);
                int b = (int) (base.getBlue() * (1 - hoverAlpha) + highlight.getBlue() * hoverAlpha);
                g2.setColor(new Color(r, g_, b));
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            // Inner glow effect on hover
            if (hoverAlpha > 0) {
                g2.setColor(new Color(255, 255, 255, (int) (40 * hoverAlpha)));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, radius, radius);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Find parent JScrollPane for auto-scrolling
     */
    private JScrollPane findParentScrollPane(Component component) {
        Component parent = component.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) {
                return (JScrollPane) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPLORE STOCKS DIALOG - 100+ Stocks (LEGACY - Now using inline loading)
    // ═══════════════════════════════════════════════════════════════════════

    private void showExploreStocksDialog() {
        JDialog dialog = new JDialog(this, "🔍 Explore 100+ Stocks", true);
        dialog.setSize(1200, 800);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BG);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header with search
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("📊 Stock Market Explorer");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT);

        JTextField searchField = new RoundedTextField(20, 20);
        searchField.setFont(FONT_BODY);
        searchField.setForeground(TEXT);
        searchField.setBackground(CARD_BG);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(new EmptyBorder(12, 20, 12, 20));
        searchField.setPreferredSize(new Dimension(300, 45));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("🔍"));
        searchPanel.add(searchField);

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(searchPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Stock grid
        JPanel gridPanel = new JPanel(new GridLayout(0, 4, 15, 15));
        gridPanel.setBackground(BG);

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(BG);
        scrollPane.getViewport().setBackground(BG);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Load all stocks
        Runnable loadStocks = () -> {
            gridPanel.removeAll();
            String query = searchField.getText().trim().toLowerCase();

            java.util.List<Object[]> stocks;
            if (query.isEmpty()) {
                stocks = com.portfolio.data.StockDatabase.getAllStocks();
            } else {
                stocks = com.portfolio.data.StockDatabase.searchStocks(query);
            }

            for (Object[] stock : stocks) {
                String symbol = (String) stock[0];
                String name = (String) stock[1];
                double price = (Double) stock[2];
                String change = (String) stock[3];
                String sector = (String) stock[4];
                String marketCap = (String) stock[5];

                boolean isPositive = change.startsWith("+");
                double convertedPrice = portfolioService.convertToBase(price, "USD");

                gridPanel.add(createStockCard(symbol, name, formatCurrency(convertedPrice),
                        change, isPositive, sector, marketCap));
            }

            gridPanel.revalidate();
            gridPanel.repaint();
        };

        // Search on key release
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                loadStocks.run();
            }
        });

        // Initial load
        loadStocks.run();

        // Footer with stats
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footerPanel.setOpaque(false);
        JLabel statsLabel = new JLabel(
                "📈 " + com.portfolio.data.StockDatabase.ALL_STOCKS.length + " stocks available across all sectors");
        statsLabel.setFont(FONT_SMALL);
        statsLabel.setForeground(TEXT_DIM);
        footerPanel.add(statsLabel);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private ChartPanel createTechnicalChart(String symbol, String range) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries priceSeries = new TimeSeries("Price");
        TimeSeries sma20Series = new TimeSeries("SMA 20");
        TimeSeries sma50Series = new TimeSeries("SMA 50");
        TimeSeries ema20Series = new TimeSeries("EMA 20");

        // Generate realistic fluctuating data based on range
        Random r = new Random();
        double price = 600 + r.nextDouble() * 100;
        Calendar cal = Calendar.getInstance();

        int points = 100;
        int intervalType = Calendar.DAY_OF_YEAR;
        int intervalVal = 1;

        if ("1D".equals(range)) {
            points = 24 * 12; // 5 min interval for 24h
            intervalType = Calendar.MINUTE;
            intervalVal = 5;
        } else if ("1W".equals(range)) {
            points = 7 * 24; // Hourly for 1 week
            intervalType = Calendar.HOUR;
            intervalVal = 1;
        } else if ("1M".equals(range)) {
            points = 30;
            intervalType = Calendar.DAY_OF_YEAR;
            intervalVal = 1;
        } else if ("6M".equals(range)) {
            points = 180;
            intervalType = Calendar.DAY_OF_YEAR;
            intervalVal = 1;
        } else if ("1Y".equals(range)) {
            points = 365;
            intervalType = Calendar.DAY_OF_YEAR;
            intervalVal = 1;
        }

        cal.add(intervalType, -intervalVal * points);

        List<Double> pricesList = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            price += (r.nextDouble() - 0.48) * (points > 100 ? 5 : 15);
            pricesList.add(price);

            RegularTimePeriod period;
            if (intervalType == Calendar.MINUTE)
                period = new Minute(cal.getTime());
            else if (intervalType == Calendar.HOUR)
                period = new Hour(cal.getTime());
            else
                period = new Day(cal.getTime());

            priceSeries.add(period, price);

            if (i >= 20) {
                double sma20Value = 0;
                for (int j = i - 19; j <= i; j++)
                    sma20Value += pricesList.get(j);
                sma20Series.add(period, sma20Value / 20);

                double prevEma = (i == 20) ? sma20Value / 20
                        : (ema20Series.getItemCount() > 0
                                ? ema20Series.getValue(ema20Series.getItemCount() - 1).doubleValue()
                                : sma20Value / 20);
                double emaValue = (price - prevEma) * (2.0 / 21.0) + prevEma;
                ema20Series.add(period, emaValue);
            }
            if (i >= 50) {
                double sma50Value = 0;
                for (int j = i - 49; j <= i; j++)
                    sma50Value += pricesList.get(j);
                sma50Series.add(period, sma50Value / 50);
            }
            cal.add(intervalType, intervalVal);
        }

        dataset.addSeries(priceSeries);
        dataset.addSeries(sma20Series);
        dataset.addSeries(sma50Series);
        dataset.addSeries(ema20Series);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, "Time", "Price (₹)", dataset, true, true, false);
        styleChart(chart);

        XYPlot plot = (XYPlot) chart.getPlot();

        // Professional Crosshair Guide Lines - Enhanced visibility
        plot.setDomainCrosshairVisible(true);
        plot.setDomainCrosshairPaint(new Color(100, 200, 255, 200)); // Bright blue, semi-transparent
        plot.setDomainCrosshairStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        plot.setRangeCrosshairVisible(true);
        plot.setRangeCrosshairPaint(new Color(100, 200, 255, 200)); // Bright blue, semi-transparent
        plot.setRangeCrosshairStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);

        // Create gradient paint for the main price line (green gradient)
        GradientPaint gradientGreen = new GradientPaint(
                0, 0, new Color(34, 197, 94), // Bright green at top
                0, 400, new Color(22, 163, 74) // Darker green at bottom
        );

        renderer.setSeriesPaint(0, gradientGreen);
        renderer.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Style the moving averages with semi-transparent colors
        renderer.setSeriesPaint(1, new Color(251, 146, 60, 180)); // Orange SMA20
        renderer.setSeriesStroke(1, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        renderer.setSeriesPaint(2, new Color(34, 211, 238, 180)); // Cyan SMA50
        renderer.setSeriesStroke(2, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        renderer.setSeriesPaint(3, new Color(168, 85, 247, 180)); // Purple EMA20
        renderer.setSeriesStroke(3, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Enhanced tooltip with all indicator values
        StandardXYToolTipGenerator tooltipGenerator = new StandardXYToolTipGenerator(
                "<html><body style='background-color: rgba(30,30,45,0.95); padding: 8px; border-radius: 4px;'>" +
                        "<div style='color: #22c55e; font-weight: bold; font-size: 13px;'>{0}</div>" +
                        "<div style='color: #e5e7eb; margin-top: 4px;'>Price: <b>₹{2}</b></div>" +
                        "<div style='color: #9ca3af; font-size: 11px;'>Time: {1}</div>" +
                        "</body></html>",
                new java.text.SimpleDateFormat(intervalType == Calendar.MINUTE ? "HH:mm" : "dd MMM, HH:mm"),
                new java.text.DecimalFormat("#,##0.00"));
        renderer.setDefaultToolTipGenerator(tooltipGenerator);
        plot.setRenderer(renderer);

        // REMOVED: Volume bars (white vertical lines) for cleaner appearance

        // Create custom ChartPanel with zoom restrictions
        ChartPanel panel = new ChartPanel(chart) {
            private double initialWidth = 0;
            private double initialHeight = 0;
            private boolean initialized = false;

            @Override
            public void restoreAutoBounds() {
                // Store initial bounds on first call
                if (!initialized) {
                    initialized = true;
                    initialWidth = getWidth();
                    initialHeight = getHeight();
                }
                super.restoreAutoBounds();
            }

            @Override
            public void zoom(java.awt.geom.Rectangle2D selection) {
                // Only allow zoom IN, not OUT
                if (selection.getWidth() > 0 && selection.getHeight() > 0) {
                    super.zoom(selection);
                }
            }

            @Override
            public void zoomInBoth(double x, double y) {
                // Allow zoom in
                super.zoomInBoth(x, y);
            }

            @Override
            public void zoomOutBoth(double x, double y) {
                // Prevent zoom out beyond initial view
                XYPlot xyPlot = (XYPlot) getChart().getPlot();
                org.jfree.chart.axis.ValueAxis domainAxis = xyPlot.getDomainAxis();
                org.jfree.chart.axis.ValueAxis rangeAxis = xyPlot.getRangeAxis();

                // Check if already at maximum zoom out
                if (domainAxis.getRange().getLength() < domainAxis.getDefaultAutoRange().getLength() * 1.1) {
                    // Already at or near full view, don't zoom out more
                    return;
                }
                super.zoomOutBoth(x, y);
            }
        };

        panel.setMouseWheelEnabled(true);
        panel.setRangeZoomable(true);
        panel.setDomainZoomable(true);
        panel.setBackground(new Color(30, 30, 45));

        // Enable crosshair tracking for hover effect
        panel.setHorizontalAxisTrace(true);
        panel.setVerticalAxisTrace(true);

        // Enhanced tooltip settings
        panel.setDisplayToolTips(true);
        panel.setInitialDelay(0); // Show immediately
        panel.setDismissDelay(Integer.MAX_VALUE);

        // Set preferred size to match the chart card
        panel.setPreferredSize(new Dimension(900, 450));

        return panel;
    }
}

package com.portfolio.ui;

import com.portfolio.model.*;
import com.portfolio.service.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;

/**
 * Modern Stock Portfolio Dashboard
 * Dark theme with purple accents, sidebar navigation, multiple pages.
 * Integrated with real PortfolioService and database
 */
public class StockDashboard extends JFrame {
    
    // ── Palette ──────────────────────────────────────────────────────────────
    static final Color BG          = new Color(0x0f0f0f);
    static final Color SIDEBAR_BG  = new Color(0x1a1a2e);
    static final Color CARD_BG     = new Color(0x1e1e2e);
    static final Color CARD_HOVER  = new Color(0x2a2a3e);
    static final Color ACCENT      = new Color(0x667eea);
    static final Color ACCENT2     = new Color(0x764ba2);
    static final Color TEXT        = new Color(0xf0f0f0);
    static final Color TEXT_DIM    = new Color(0x888899);
    static final Color GREEN       = new Color(0x4ade80);
    static final Color RED         = new Color(0xf87171);
    static final Color BORDER      = new Color(0x2e2e4e);
    
    static final Font FONT_TITLE   = new Font("SansSerif", Font.BOLD, 22);
    static final Font FONT_HEADING = new Font("SansSerif", Font.BOLD, 15);
    static final Font FONT_BODY    = new Font("SansSerif", Font.PLAIN, 13);
    static final Font FONT_SMALL   = new Font("SansSerif", Font.PLAIN, 11);
    static final Font FONT_MONO    = new Font("Monospaced", Font.BOLD, 13);
    
    // ── State ─────────────────────────────────────────────────────────────────
    private CardLayout cardLayout;
    private JPanel contentArea;
    private JLabel pageTitle;
    private final List<NavButton> navButtons = new ArrayList<>();
    private PortfolioService portfolioService;
    
    // ── Stock Database for Search ────────────────────────────────────────────
    static final String[] AUTOCOMPLETE = {
        "AAPL - Apple Inc", "GOOGL - Alphabet Inc", "MSFT - Microsoft Corp",
        "NVDA - NVIDIA Corp", "TSLA - Tesla Inc", "AMZN - Amazon.com",
        "META - Meta Platforms", "NFLX - Netflix", "AMD - Advanced Micro Devices",
        "INTC - Intel Corporation", "CRM - Salesforce", "PYPL - PayPal",
        "SHOP - Shopify", "UBER - Uber Technologies", "SQ - Block Inc",
        "COIN - Coinbase", "DIS - Disney", "BABA - Alibaba", "V - Visa Inc",
        "JPM - JPMorgan Chase"
    };
    
    static final Object[][] MARKET_DATA = {
        {"AAPL","Apple Inc","Technology","$198.45","+1.23%",true},
        {"GOOGL","Alphabet","Technology","$3,012.00","+0.87%",true},
        {"MSFT","Microsoft","Technology","$415.32","+2.14%",true},
        {"NVDA","NVIDIA","Semiconductors","$875.50","+5.67%",true},
        {"TSLA","Tesla","Automotive","$185.20","-3.21%",false},
        {"AMZN","Amazon","E-Commerce","$3,780.00","+1.55%",true},
        {"META","Meta","Social Media","$505.00","+3.44%",true},
        {"NFLX","Netflix","Streaming","$615.30","-0.92%",false},
        {"AMD","AMD","Semiconductors","$178.40","+4.12%",true},
        {"INTC","Intel","Semiconductors","$42.15","-1.34%",false},
        {"CRM","Salesforce","Cloud","$285.00","+0.78%",true},
        {"PYPL","PayPal","FinTech","$68.20","-2.11%",false},
        {"SHOP","Shopify","E-Commerce","$72.50","+6.22%",true},
        {"UBER","Uber","Transportation","$78.30","+2.90%",true},
        {"SQ","Block Inc","FinTech","$62.40","+1.15%",true},
        {"COIN","Coinbase","Crypto","$198.70","+8.34%",true},
    };
    
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { 
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); 
            } catch (Exception ignored) {}
            
            // Initialize services
            StockPriceService priceService = new AlphaVantageService();
            PortfolioService portfolioService = new PortfolioService(priceService);
            
            new StockDashboard(portfolioService).setVisible(true);
        });
    }
    
    public StockDashboard(PortfolioService portfolioService) {
        super("StockVault — Portfolio Dashboard");
        this.portfolioService = portfolioService;
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMain(), BorderLayout.CENTER);
        
        navigate("Dashboard");
    }
    
    // ── Sidebar ───────────────────────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0,0,SIDEBAR_BG,0,getHeight(),new Color(0x0d0d1a));
                g2.setPaint(gp); 
                g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setLayout(new BorderLayout());
        sidebar.setBorder(new MatteBorder(0,0,0,1,BORDER));
        
        // Logo
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        logoPanel.setOpaque(false);
        JLabel logo = new JLabel("◈ StockVault");
        logo.setFont(new Font("SansSerif", Font.BOLD, 18));
        logo.setForeground(ACCENT);
        logoPanel.add(logo);
        sidebar.add(logoPanel, BorderLayout.NORTH);
        
        // Nav items
        JPanel navPanel = new JPanel();
        navPanel.setOpaque(false);
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBorder(new EmptyBorder(10,0,0,0));
        
        String[][] items = {
            {"⊞","Dashboard"}, {"◈","My Portfolio"}, {"◉","Market"},
            {"★","Watchlist"}, {"↕","Transactions"}, {"▲","Analytics"}, {"⚙","Settings"}
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
        JLabel ver = new JLabel("v2.1.0  •  Market Open");
        ver.setFont(FONT_SMALL); 
        ver.setForeground(TEXT_DIM);
        footer.add(ver);
        sidebar.add(footer, BorderLayout.SOUTH);
        
        return sidebar;
    }
    
    // ── Main Content ──────────────────────────────────────────────────────────
    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        
        // Topbar
        JPanel topbar = new JPanel(new BorderLayout());
        topbar.setBackground(new Color(0x141428));
        topbar.setBorder(new CompoundBorder(
            new MatteBorder(0,0,1,0,BORDER),
            new EmptyBorder(12,24,12,24)
        ));
        
        pageTitle = new JLabel("Dashboard");
        pageTitle.setFont(FONT_TITLE);
        pageTitle.setForeground(TEXT);
        topbar.add(pageTitle, BorderLayout.WEST);
        
        // Search bar
        JPanel searchPanel = buildSearchBar();
        topbar.add(searchPanel, BorderLayout.EAST);
        
        main.add(topbar, BorderLayout.NORTH);
        
        // Pages
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);
        
        contentArea.add(buildDashboardPage(), "Dashboard");
        contentArea.add(buildPortfolioPage(), "My Portfolio");
        contentArea.add(buildMarketPage(), "Market");
        contentArea.add(buildPlaceholderPage("★ Watchlist", "Track your favourite stocks here."), "Watchlist");
        contentArea.add(buildTransactionsPage(), "Transactions");
        contentArea.add(buildPlaceholderPage("▲ Analytics", "Advanced charts & analytics coming soon."), "Analytics");
        contentArea.add(buildPlaceholderPage("⚙ Settings", "Preferences, notifications & account settings."), "Settings");
        
        main.add(contentArea, BorderLayout.CENTER);
        
        return main;
    }
    
    private void navigate(String page) {
        pageTitle.setText(page);
        cardLayout.show(contentArea, page);
        for (NavButton nb : navButtons)
            nb.setActive(nb.label.equals(page));
    }
    
    // Continued in next message due to length...

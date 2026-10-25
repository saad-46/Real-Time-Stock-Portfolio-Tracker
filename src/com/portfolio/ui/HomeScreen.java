package com.portfolio.ui;

import com.portfolio.AppPaths;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.PortfolioSummary;
import com.portfolio.service.QuotePolicy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutionException;

/**
 * StockVault's home screen: the application's entry point. It shows a verified summary of the portfolio and gives
 * one-click access to every working part of the app.
 *
 * States, each shown honestly: <b>loading</b> (the database is opened off the Swing thread), <b>error</b> (database
 * could not be opened: reason, file location, Retry), <b>empty</b> (nothing yet: clear next steps, no zero-filled sample
 * numbers), and <b>content</b> (totals flagged when approximate, per-holding price freshness counts, movers only from
 * real refreshed quotes). All business logic is in {@code PortfolioService} / {@code PortfolioSummary}; no network or
 * database call runs on the event thread.
 */
public final class HomeScreen extends JFrame {
    private static final long serialVersionUID = 1L;

    /** Creates the service (opens the database). May throw; runs on a worker thread. */
    @FunctionalInterface
    public interface ServiceFactory {
        PortfolioService create() throws Exception;
    }

    private enum BannerKind { INFO, OK, WARN, ERROR }

    private final ServiceFactory factory;
    private volatile PortfolioService service;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JPanel contentHolder = new JPanel(new BorderLayout());
    private final JPanel errorPanel = new JPanel(new BorderLayout());
    private final JLabel loadingText = Theme.label("Opening your portfolio…", Theme.BODY, Theme.TEXT_DIM);
    private final Theme.Button refreshButton = Theme.button("Refresh prices", Theme.Kind.SECONDARY);
    private PremiumStockDashboard dashboard;
    private PortfolioSummary lastSummary;   // what is on screen, so a resize can re-lay-out without reloading
    private boolean compact;                // narrow layout below COMPACT_BELOW pixels
    private String shown = "loading";
    private static final int COMPACT_BELOW = 1100;
    private String pendingPage;             // dashboard page to open once loaded
    private String pendingBanner;           // survives a re-render
    private BannerKind pendingBannerKind = BannerKind.INFO;
    private boolean busy;

    public HomeScreen(ServiceFactory factory) {
        super("StockVault");
        this.factory = factory;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 640));
        setSize(1180, 780);
        setLocationRelativeTo(null);
        Theme.style(getRootPane());
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);

        body.setBackground(Theme.BG);
        body.add(buildLoading(), "loading");
        errorPanel.setName("home.error");
        errorPanel.setBackground(Theme.BG);
        body.add(errorPanel, "error");
        contentHolder.setBackground(Theme.BG);
        contentHolder.setName("home.content");
        body.add(contentHolder, "content");
        add(body, BorderLayout.CENTER);

        // F5 refreshes prices from anywhere in the window
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refresh");
        getRootPane().getActionMap().put("refresh", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { refreshPrices(); }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boolean narrow = getWidth() < COMPACT_BELOW;
                if (narrow != compact && lastSummary != null && "content".equals(shown)) {
                    render(lastSummary); // same data, other arrangement
                }
            }
        });
        load();
    }

    /** The scrolled page: as wide as the window (so nothing is clipped sideways) and at least as tall as it. */
    private static final class ScrollablePage extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return Math.max(40, r.height - 40); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof javax.swing.JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    // ------------------------------------------------------------------ frame furniture

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG);
        header.setBorder(new EmptyBorder(18, 28, 8, 28));
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel name = Theme.label("StockVault", Theme.H1, Theme.TEXT);
        name.setName("home.brand");
        brand.add(name);
        brand.add(Theme.label("Your portfolio, with verified prices only", Theme.SMALL, Theme.TEXT_DIM));
        header.add(brand, BorderLayout.WEST);

        refreshButton.setName("home.refresh");
        refreshButton.setMnemonic(KeyEvent.VK_R);
        refreshButton.setToolTipText("Fetch current prices from the price providers (F5)");
        refreshButton.setEnabled(false);
        refreshButton.addActionListener(e -> refreshPrices());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(refreshButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildLoading() {
        JPanel p = new JPanel();
        p.setName("home.loading");
        p.setBackground(Theme.BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalGlue());
        loadingText.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingText.setName("home.loading.text");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setMaximumSize(new Dimension(280, 8));
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.getAccessibleContext().setAccessibleName("Loading");
        p.add(loadingText);
        p.add(Box.createVerticalStrut(14));
        p.add(bar);
        p.add(Box.createVerticalGlue());
        return p;
    }

    // ------------------------------------------------------------------ loading and states

    /** Open this dashboard page as soon as the portfolio has loaded (used after sign-in from the landing page). */
    public void openPageWhenReady(String page) {
        pendingPage = page;
    }

    /** (Re)load: open the database if needed and compute the summary, all off the event thread. */
    public void load() {
        showCard("loading");
        loadingText.setText(service == null ? "Opening your portfolio…" : "Updating…");
        refreshButton.setEnabled(false);
        new SwingWorker<PortfolioSummary, Void>() {
            @Override
            protected PortfolioSummary doInBackground() throws Exception {
                if (service == null) {
                    service = factory.create();
                }
                return PortfolioSummary.of(service);
            }

            @Override
            protected void done() {
                try {
                    render(get());
                    if (pendingPage != null) {
                        String page = pendingPage;
                        pendingPage = null;
                        openDashboard(page, false);
                    }
                } catch (ExecutionException e) {
                    service = null;
                    showError(e.getCause() == null ? e : e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void showCard(String name) {
        shown = name;
        cards.show(body, name);
    }

    private void showError(Throwable t) {
        errorPanel.removeAll();
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(60, 80, 60, 80));
        JLabel title = Theme.label("The portfolio could not be opened", Theme.H1, Theme.RED);
        title.setName("home.error.title");
        box.add(title);
        box.add(Box.createVerticalStrut(10));
        JTextArea why = text(readable(t), Theme.BODY, Theme.TEXT);
        why.setName("home.error.reason");
        box.add(why);
        box.add(Box.createVerticalStrut(10));
        JTextArea where = text("Database file: " + AppPaths.resolveDatabasePath()
                + "\nYour data has not been changed. If the file is locked by another copy of StockVault, close it and retry.",
                Theme.SMALL, Theme.TEXT_DIM);
        where.setName("home.error.where");
        box.add(where);
        box.add(Box.createVerticalStrut(18));
        Theme.Button retry = Theme.button("Retry", Theme.Kind.PRIMARY);
        retry.setName("home.error.retry");
        retry.setMnemonic(KeyEvent.VK_R);
        retry.addActionListener(e -> load());
        box.add(retry);
        errorPanel.add(box, BorderLayout.NORTH);
        errorPanel.revalidate();
        showCard("error");
        SwingUtilities.invokeLater(retry::requestFocusInWindow);
    }

    private static String readable(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        return root != t && root.getMessage() != null && !m.contains(root.getMessage()) ? m + " (" + root.getMessage() + ")" : m;
    }

    private static JTextArea text(String s, java.awt.Font f, Color c) {
        JTextArea a = new JTextArea(s);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFont(f);
        a.setForeground(c);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        a.setBorder(null);
        return a;
    }

    // ------------------------------------------------------------------ content

    private void render(PortfolioSummary s) {
        lastSummary = s;
        compact = getWidth() < COMPACT_BELOW;
        contentHolder.removeAll();
        JPanel page = new ScrollablePage();
        page.setBackground(Theme.BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(4, 28, 28, 28));

        page.add(fixedHeight(statusChips(s)));
        page.add(Box.createVerticalStrut(10));
        if (pendingBanner != null) {
            page.add(fixedHeight(banner(pendingBanner, pendingBannerKind)));
            page.add(Box.createVerticalStrut(10));
        } else if (s.unavailable() > 0 && s.holdings() > 0) {
            page.add(fixedHeight(banner(s.unavailable() + " of " + s.holdings() + " holding(s) have no live price yet and are valued at what you "
                    + "paid. Press \"Refresh prices\" (F5) to fetch real prices.", BannerKind.INFO)));
            page.add(Box.createVerticalStrut(10));
        }

        if (s.isEmpty()) {
            page.add(fixedHeight(emptyState()));
        } else {
            page.add(fixedHeight(summaryRow(s)));
        }
        page.add(Box.createVerticalStrut(Theme.GAP));
        page.add(fixedHeight(actions()));
        if (!s.isEmpty()) {
            page.add(Box.createVerticalStrut(Theme.GAP));
            page.add(fixedHeight(lowerRow(s)));
        }
        page.add(Box.createVerticalGlue()); // spare height goes at the bottom, never between sections
        JScrollPane scroll = new JScrollPane(page);
        scroll.setName("home.scroll");
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentHolder.add(scroll, BorderLayout.CENTER);
        contentHolder.revalidate();
        contentHolder.repaint();
        refreshButton.setEnabled(true);
        refreshButton.setText("Refresh prices");
        showCard("content");
    }

    /** Keep a row at its natural height (full width): BoxLayout otherwise stretches rows to fill spare space. */
    private static JComponent fixedHeight(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private JComponent chip(String name, String text, Color color, String tip) {
        JLabel l = Theme.label(text, Theme.SMALL, color);
        l.setName(name);
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, 1, true), new EmptyBorder(3, 10, 3, 10)));
        if (tip != null) {
            l.setToolTipText(tip);
        }
        l.getAccessibleContext().setAccessibleName(text);
        return l;
    }

    private JComponent statusChips(PortfolioSummary s) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (s.holdings() > 0) {
            String text = "Prices: " + s.fresh() + " fresh · " + s.stale() + " stale · " + s.unavailable() + " unavailable";
            Color c = s.unavailable() > 0 ? Theme.RED : s.stale() > 0 ? Theme.AMBER : Theme.GREEN;
            row.add(chip("home.chip.prices", text, c, "Fresh: refreshed within " + QuotePolicy.DISPLAY_FRESH_MAX_AGE.toMinutes()
                    + " min. Stale: older. Unavailable: never refreshed. Trades always fetch a new quote."));
        }
        if (s.approximate()) {
            row.add(chip("home.chip.rates", "≈ Approximate exchange rates", Theme.AMBER,
                    "Live exchange rates are unavailable, so totals use fixed rates and are NOT validated figures."));
        } else if (s.holdings() > 0) {
            row.add(chip("home.chip.rates", "Exchange rates validated", Theme.GREEN, "Totals use validated rates (or need none)."));
        }
        java.nio.file.Path db = java.nio.file.Paths.get(AppPaths.resolveDatabasePath());
        row.add(chip("home.chip.db", "Database: " + db.getFileName(), Theme.TEXT_DIM, db.toAbsolutePath().toString()));
        return row;
    }

    private JComponent banner(String text, BannerKind kind) {
        Color c = switch (kind) {
            case OK -> Theme.GREEN;
            case WARN -> Theme.AMBER;
            case ERROR -> Theme.RED;
            default -> Theme.ACCENT;
        };
        JPanel p = new JPanel(new BorderLayout());
        p.setName("home.banner");
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c, 1, true), new EmptyBorder(8, 12, 8, 12)));
        JTextArea t = text(text, Theme.SMALL, Theme.TEXT);
        t.setName("home.banner.text");
        p.add(t, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 40));
        return p;
    }

    private JComponent emptyState() {
        Theme.Card card = new Theme.Card();
        card.setName("home.empty");
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(Theme.label("Your portfolio is empty", Theme.H1, Theme.TEXT));
        card.add(Box.createVerticalStrut(6));
        JTextArea t = text("Nothing to show yet, and nothing is made up. Add funds to your wallet, then record your first holding. "
                + "Prices appear once you refresh them from a price provider.", Theme.BODY, Theme.TEXT_DIM);
        card.add(t);
        card.add(Box.createVerticalStrut(14));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Theme.Button add = Theme.button("Add your first holding", Theme.Kind.PRIMARY);
        add.setName("home.empty.add");
        add.addActionListener(e -> openDashboard("My Portfolio", true));
        Theme.Button fund = Theme.button("Add funds to the wallet", Theme.Kind.SECONDARY);
        fund.setName("home.empty.fund");
        fund.addActionListener(e -> openDashboard("My Wallet", false));
        row.add(add);
        row.add(fund);
        card.add(row);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 20));
        return card;
    }

    private JComponent metricCard(String id, String title, String value, Color valueColor, String sub, String tip) {
        Theme.Card c = new Theme.Card();
        c.setName("home.card." + id);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.add(Theme.label(title, Theme.SMALL, Theme.TEXT_DIM));
        c.add(Box.createVerticalStrut(6));
        JLabel v = Theme.label(value, Theme.VALUE, valueColor);
        v.setName("home.value." + id);
        c.add(v);
        c.add(Box.createVerticalStrut(4));
        JLabel sl = Theme.label(sub == null ? " " : sub, Theme.SMALL, Theme.TEXT_DIM);
        sl.setName("home.sub." + id);
        c.add(sl);
        if (tip != null) {
            c.setToolTipText(tip);
            v.setToolTipText(tip);
        }
        c.getAccessibleContext().setAccessibleName(title + " " + value);
        return c;
    }

    private JComponent summaryRow(PortfolioSummary s) {
        String cur = s.displayCurrency();
        String approxTip = "Approximate: live exchange rates were unavailable, so this uses fixed rates. It is not a validated total.";
        PortfolioService.Valuation v = s.totalValue(), i = s.invested(), p = s.profitLoss();
        JPanel row = new JPanel(compact ? new GridLayout(0, 3, Theme.GAP, Theme.GAP) : new GridLayout(1, 0, Theme.GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(metricCard("totalValue", "Total value", v.mark() + Theme.money(v.value(), cur), Theme.TEXT,
                s.holdings() + " holding(s)" + (s.valuedAtCost() > 0 ? " · " + s.valuedAtCost() + " valued at cost" : ""),
                v.approximate() ? approxTip : (s.valuedAtCost() > 0 ? "Holdings without a live price are valued at what you paid." : null)));
        row.add(metricCard("profitLoss", "Profit / loss", p.mark() + Theme.signedMoney(p.value(), cur), Theme.gainLoss(p.value()),
                String.format("%+.2f%% on invested", s.returnPercent()), p.approximate() ? approxTip : null));
        row.add(metricCard("invested", "Invested", i.mark() + Theme.money(i.value(), cur), Theme.TEXT, "cost basis", i.approximate() ? approxTip : null));
        row.add(metricCard("cash", "Wallet cash", Theme.money(s.walletBalance(), s.walletCurrency()), Theme.TEXT, "available to invest", null));
        row.add(metricCard("realized", "Realized P/L", Theme.signedMoney(s.realizedPl().doubleValue(), cur), Theme.gainLoss(s.realizedPl().doubleValue()),
                "from completed sales", "In the currency each holding is held in; summed as recorded."));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 10));
        return row;
    }

    private JComponent actions() {
        Theme.Card card = new Theme.Card();
        card.setName("home.actions");
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setLayout(new BorderLayout(0, 10));
        card.add(Theme.label("Go to", Theme.H2, Theme.TEXT), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);
        Object[][] defs = {
                { "portfolio", "Portfolio", KeyEvent.VK_P, "My Portfolio", false, Theme.Kind.PRIMARY, "Holdings with price freshness" },
                { "add", "Add holding", KeyEvent.VK_A, "My Portfolio", true, Theme.Kind.PRIMARY, "Record a purchase (paid from the wallet)" },
                { "wallet", "Wallet", KeyEvent.VK_W, "My Wallet", false, Theme.Kind.SECONDARY, "Add or withdraw funds, see wallet history" },
                { "transactions", "Transactions", KeyEvent.VK_T, "Transactions", false, Theme.Kind.SECONDARY, "Ledger with filters and CSV export" },
                { "analytics", "Analytics", KeyEvent.VK_N, "Analytics", false, Theme.Kind.SECONDARY, "Allocation and performance views" },
                { "watchlist", "Watchlist", KeyEvent.VK_L, "Watchlist", false, Theme.Kind.SECONDARY, "Symbols you follow" },
                { "market", "Find stocks", KeyEvent.VK_F, "Market", false, Theme.Kind.SECONDARY, "Browse and search symbols" },
        };
        for (Object[] d : defs) {
            Theme.Button b = Theme.button((String) d[1], (Theme.Kind) d[5]);
            b.setName("home.action." + d[0]);
            b.setMnemonic((Integer) d[2]);
            b.setToolTipText((String) d[6]);
            final String page = (String) d[3];
            final boolean addHolding = (Boolean) d[4];
            b.addActionListener(e -> openDashboard(page, addHolding));
            grid.add(b);
        }
        Theme.Button classic = Theme.button("Classic view", Theme.Kind.SECONDARY);
        classic.setName("home.action.classic");
        classic.setMnemonic(KeyEvent.VK_C);
        classic.setToolTipText("The simpler holdings window with ledger and CSV export");
        classic.addActionListener(e -> openClassic());
        grid.add(classic);
        card.add(grid, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 20));
        return card;
    }

    private JComponent lowerRow(PortfolioSummary s) {
        JPanel row = new JPanel(new GridLayout(1, 3, Theme.GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(watchlistCard(s));
        row.add(recentCard(s));
        row.add(alertsAndMoversCard(s));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(row.getPreferredSize().height, 230) + 10));
        return row;
    }

    private Theme.Card listCard(String id, String title) {
        Theme.Card c = new Theme.Card();
        c.setName("home." + id);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.add(Theme.label(title, Theme.H2, Theme.TEXT));
        c.add(Box.createVerticalStrut(8));
        return c;
    }

    private static JLabel line(String text, Color color) {
        JLabel l = Theme.label(text, Theme.BODY, color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(2, 0, 2, 0));
        return l;
    }

    private JComponent watchlistCard(PortfolioSummary s) {
        Theme.Card c = listCard("watchlist", "Watchlist");
        if (s.watchlist().isEmpty()) {
            c.add(line("No symbols yet.", Theme.TEXT_DIM));
        } else {
            int shown = 0;
            for (Stock w : s.watchlist()) {
                if (shown++ == 6) {
                    c.add(line("+ " + (s.watchlist().size() - 6) + " more", Theme.TEXT_DIM));
                    break;
                }
                c.add(w.getCurrentPrice() > 0
                        ? line(String.format("%-8s %s  %+.2f%%", w.getSymbol(), Theme.money(w.getCurrentPrice(), w.getCurrency()), w.getChangePercent()),
                                Theme.gainLoss(w.getChangePercent()))
                        : line(w.getSymbol() + "   no quote yet", Theme.TEXT_DIM));
            }
        }
        return c;
    }

    private JComponent recentCard(PortfolioSummary s) {
        Theme.Card c = listCard("recent", "Recent activity");
        if (s.recent().isEmpty()) {
            c.add(line("No transactions yet.", Theme.TEXT_DIM));
        }
        for (Transaction t : s.recent()) {
            boolean buy = "BUY".equals(t.getType());
            c.add(line(String.format("%s %d %s @ %.2f  %s", t.getType(), t.getQuantity(), t.getSymbol(), t.getPrice(),
                    t.getTimestamp().toLocalDate()), buy ? Theme.TEXT : Theme.ACCENT));
        }
        return c;
    }

    private JComponent alertsAndMoversCard(PortfolioSummary s) {
        Theme.Card c = listCard("movers", "Alerts & movers");
        c.add(line(s.activeAlerts() + " active price alert(s)"
                + (s.triggeredAlerts() > 0 ? " · " + s.triggeredAlerts() + " triggered" : ""), s.triggeredAlerts() > 0 ? Theme.AMBER : Theme.TEXT));
        c.add(Box.createVerticalStrut(6));
        if (s.topGainers().isEmpty() && s.topLosers().isEmpty()) {
            c.add(line("Movers appear after a price refresh,", Theme.TEXT_DIM));
            c.add(line("using each provider's reported daily change.", Theme.TEXT_DIM));
        }
        for (PortfolioItem g : s.topGainers()) {
            c.add(line(String.format("▲ %-8s %+.2f%%", g.getSymbol(), g.getStock().getChangePercent()), Theme.GREEN));
        }
        for (PortfolioItem l : s.topLosers()) {
            c.add(line(String.format("▼ %-8s %+.2f%%", l.getSymbol(), l.getStock().getChangePercent()), Theme.RED));
        }
        return c;
    }

    // ------------------------------------------------------------------ actions

    private void refreshPrices() {
        final PortfolioService svc = service;
        if (svc == null || busy || !refreshButton.isEnabled()) {
            return;
        }
        busy = true;
        refreshButton.setEnabled(false);
        refreshButton.setText("Refreshing…");
        new SwingWorker<PortfolioSummary, Void>() {
            private int failed;

            @Override
            protected PortfolioSummary doInBackground() {
                failed = svc.updateAllPrices();
                return PortfolioSummary.of(svc);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    PortfolioSummary s = get();
                    if (s.holdings() == 0 && s.watchlist().isEmpty()) {
                        remember("There is nothing to refresh yet.", BannerKind.INFO);
                    } else if (failed == 0) {
                        remember("Prices refreshed.", BannerKind.OK);
                    } else {
                        String why = s.refreshProblem() == null ? "the provider did not answer" : s.refreshProblem();
                        remember(failed + " of " + s.holdings() + " price(s) could not be updated and still show their last known value. Reason: "
                                + why + (why.contains("API key") ? " (see README, Configuration)." : ""), BannerKind.WARN);
                    }
                    render(s);
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    remember(c instanceof IllegalStateException && String.valueOf(c.getMessage()).contains("already in progress")
                            ? "A refresh is already running." : "Refresh failed: " + readable(c), BannerKind.ERROR);
                    load();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void remember(String text, BannerKind kind) {
        pendingBanner = text;
        pendingBannerKind = kind;
    }

    private void openDashboard(String page, boolean addHolding) {
        final PortfolioService svc = service;
        if (svc == null) {
            return;
        }
        pendingBanner = null;
        try {
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
            if (dashboard == null || !dashboard.isDisplayable()) {
                dashboard = new PremiumStockDashboard(svc);
                dashboard.setDefaultCloseOperation(DISPOSE_ON_CLOSE); // closing it returns to the home screen
                dashboard.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent e) { SwingUtilities.invokeLater(HomeScreen.this::load); }
                });
            }
            dashboard.setVisible(true);
            dashboard.toFront();
            dashboard.showPage(page);
            if (addHolding) {
                SwingUtilities.invokeLater(dashboard::openAddHolding);
            }
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
        }
    }

    private void openClassic() {
        final PortfolioService svc = service;
        if (svc != null) {
            ModernPortfolioUI classic = new ModernPortfolioUI(svc);
            classic.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            classic.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { SwingUtilities.invokeLater(HomeScreen.this::load); }
            });
        }
    }
}

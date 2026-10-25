package com.portfolio.ui;

import com.portfolio.service.MarketIndexService.IndexDef;
import com.portfolio.service.MarketIndexService.Problem;
import com.portfolio.service.MarketIndexService.Snapshot;
import com.portfolio.service.MarketIndexService.State;
import com.portfolio.service.MarketSource;
import com.portfolio.service.NewsService;
import com.portfolio.service.PriceHistory;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The StockVault landing page (shown before sign-in). On the left the hero (headline, description, benefits, Sign In / Create
 * Account, and the team footer) over a photograph of the BSE building; on the right eight market cards (two indices and six large
 * Indian companies), the selected instrument's chart beside a Recent Market News feed. Pure Swing with Java2D painting.
 *
 * <p>Honesty rules: cards, chart and news show only what the providers returned, with explicit loading, stale and unavailable
 * states and the provider and time of the data; a stale value is never labelled live and no sample series is ever drawn. All
 * provider access happens on background threads; this class only receives finished results on the event thread.
 */
public class StockVaultLandingPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int WIDE_FROM = 1100;
    private static final String NO_HISTORY = "No daily history";

    /** What the page asks of its host; the page itself never touches authentication or the database. */
    public interface Listener {
        void onSignIn();

        void onCreateAccount();

        /** The light/dark button was pressed; the host switches the theme and rebuilds the page. */
        default void onToggleTheme() {
        }
    }

    private final Listener listener;
    private final MarketSource market;
    private final Supplier<NewsService.Live> news;
    private final Consumer<String> linkOpener;
    private final Supplier<String> aiStatus;
    private final List<IndexDef> indices;
    private final List<MarketIndexCard> cards = new ArrayList<>();
    private final PortfolioLineChart chart = new PortfolioLineChart();
    private final JLabel chartTitle = LandingPalette.label("", Theme.font(Font.BOLD, 15f), LandingPalette.TEXT);
    private final JLabel chartCaption = LandingPalette.label(" ", Theme.font(Font.PLAIN, 12f), LandingPalette.TEXT_DIM);
    private final List<RangeButton> rangeButtons = new ArrayList<>();
    private final GradientButton signIn = new GradientButton("Sign In  →", GradientButton.Style.PRIMARY);
    private final GradientButton createAccount = new GradientButton("Create Account", GradientButton.Style.SECONDARY);
    private final JButton refreshMarket = new LinkButton("Refresh");
    private final JButton configure = new LinkButton("API setup");
    private final ThemeToggle themeToggle = new ThemeToggle();
    private final RecentNewsCard newsCard;
    private final ScrollContent content = new ScrollContent();
    private final JComponent hero;
    private final JComponent marketColumn;
    private final Set<String> historyInFlight = new HashSet<>();
    private List<Snapshot> snapshots = new ArrayList<>();
    private String selectedId;          // the instrument shown in the chart
    private boolean autoSelected;       // the first load may move the selection to an instrument that has data; after that the user decides
    private ChartRange range = ChartRange.M3;
    private boolean wide;
    private boolean marketBusy;
    private boolean newsBusy;
    private boolean closed;
    private final List<SwingWorker<?, ?>> workers = new ArrayList<>();

    /**
     * @param market provider access (blocking, called off the event thread); null means market data is not available at all
     * @param news   live headlines supplier (blocking); null means no news source
     * @param aiStatus one honest line about AI insights (configuration only; nothing is claimed to work)
     * @param linkOpener opens a headline URL; called only after the user pressed that headline
     */
    public StockVaultLandingPanel(Listener listener, MarketSource market, Supplier<NewsService.Live> news, Supplier<String> aiStatus,
            Consumer<String> linkOpener) {
        super(new BorderLayout());
        this.listener = listener;
        this.market = market;
        this.news = news;
        this.linkOpener = linkOpener;
        this.aiStatus = aiStatus;
        this.indices = market == null ? com.portfolio.service.MarketIndexService.DEFAULT_INDICES : market.indices();
        setName("landing");
        setOpaque(true);
        setBackground(LandingPalette.NAVY_TOP);
        this.selectedId = indices.isEmpty() ? null : indices.get(0).id();
        newsCard = new RecentNewsCard(this::open);

        add(buildTopBar(), BorderLayout.NORTH);
        hero = buildHero();
        marketColumn = buildMarket();
        content.setOpaque(false);
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);

        wide = true; // the first window is wide; the resize listener switches below WIDE_FROM
        arrange();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                boolean nowWide = getWidth() >= WIDE_FROM;
                if (nowWide != wide) {
                    wide = nowWide;
                    arrange();
                }
            }
        });
        applyChart();
    }

    private void open(String url) {
        if (linkOpener != null) {
            linkOpener.accept(url);
        }
    }

    // ------------------------------------------------------------------ pieces

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(14, 28, 4, 28));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.add(new LogoMark());
        JLabel name = LandingPalette.label("StockVault", Theme.font(Font.BOLD, 20f), LandingPalette.TEXT);
        name.setName("landing.brand");
        brand.add(name);
        bar.add(brand, BorderLayout.WEST);
        themeToggle.addActionListener(e -> listener.onToggleTheme());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(themeToggle);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildHero() {
        JPanel p = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.max(d.width, 470), d.height);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                HeroPhoto.paint(g2, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setName("landing.hero");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(8, 32, 14, 16));

        p.add(headline("Track.", false, "landing.headline.track", this::getWidth));
        p.add(headline("Analyse.", true, "landing.headline.analyse", this::getWidth));
        p.add(headline("Grow.", true, "landing.headline.grow", this::getWidth));
        p.add(Box.createVerticalStrut(10));
        JTextArea blurb = wrapped("Your personal stock portfolio tracker with market data from the providers you choose, powerful insights "
                + "and a cleaner way to manage your investments.");
        blurb.setName("landing.description");
        blurb.setMaximumSize(new Dimension(400, 200));
        p.add(blurb);
        p.add(Box.createVerticalStrut(18));

        JPanel list = new JPanel(new GridLayout(4, 1, 0, 8));
        list.setOpaque(false);
        list.setAlignmentX(LEFT_ALIGNMENT);
        list.setMaximumSize(new Dimension(400, 200));
        list.add(new FeatureHighlight(FeatureHighlight.Icon.CHART, "Track Your Investments", "Holdings, wallet, transactions"));
        list.add(new FeatureHighlight(FeatureHighlight.Icon.BOLT, "Real-Time Market Data", "Prices and charts from your providers"));
        list.add(new FeatureHighlight(FeatureHighlight.Icon.SPARK, "AI-Powered Insights", aiDetail()));
        list.add(new FeatureHighlight(FeatureHighlight.Icon.SHIELD, "Secure & Private", "Your data stays on your computer"));
        p.add(list);
        p.add(Box.createVerticalStrut(20));

        JPanel cta = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cta.setOpaque(false);
        cta.setAlignmentX(LEFT_ALIGNMENT);
        signIn.setName("landing.signin");
        signIn.setMnemonic(KeyEvent.VK_S);
        signIn.addActionListener(e -> listener.onSignIn());
        createAccount.setName("landing.create");
        createAccount.setMnemonic(KeyEvent.VK_C);
        createAccount.addActionListener(e -> listener.onCreateAccount());
        cta.add(signIn);
        cta.add(Box.createHorizontalStrut(12));
        cta.add(createAccount);
        p.add(cta);
        p.add(Box.createVerticalGlue());
        p.add(Box.createVerticalStrut(16));
        p.add(footer());
        return p;
    }

    /** The team credit, kept inside the hero below the buttons. */
    private JComponent footer() {
        JPanel f = new JPanel();
        f.setOpaque(false);
        f.setName("landing.footer");
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        f.setMaximumSize(new Dimension(420, 70));
        JLabel l1 = LandingPalette.label("Made with love by StockVault Team", Theme.font(Font.PLAIN, 12f), LandingPalette.TEXT_DIM);
        JLabel l2 = LandingPalette.label("Saad . Nabeel . Ruqayyah", Theme.font(Font.BOLD, 13f), LandingPalette.TEXT);
        JLabel l3 = LandingPalette.label("Made with Java", Theme.font(Font.PLAIN, 11f), LandingPalette.TEXT_DIM);
        l1.setName("landing.footer.1");
        l2.setName("landing.footer.2");
        l3.setName("landing.footer.3");
        f.add(l1);
        f.add(l2);
        f.add(l3);
        return f;
    }

    /** The AI benefit's one-line detail: configuration only (a key being present does not prove the provider accepts it). */
    private String aiDetail() {
        String status = aiStatus == null ? null : aiStatus.get();
        return status != null && !status.contains("not configured") ? "Your AI key is configured" : "Needs your own AI key";
    }

    private JComponent buildMarket() {
        JPanel p = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override public Dimension getPreferredSize() { // flexible content: never let the cards push the hero aside
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, 600), d.height);
            }
        };
        p.setOpaque(false);
        p.setName("landing.market");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(4, 8, 12, 28));

        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (IndexDef d : indices) {
            MarketIndexCard c = new MarketIndexCard(d.id(), d.name(), d.ticker());
            c.onSelect(() -> select(d.id()));
            cards.add(c);
            grid.add(c);
        }
        p.add(grid);
        p.add(Box.createVerticalStrut(10));

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.gridy = 0;
        gc.weighty = 1;
        gc.gridx = 0;
        gc.weightx = 1.7;
        gc.insets = new Insets(0, 0, 0, 10);
        row.add(chartCard(), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        newsCard.setPreferredSize(new Dimension(250, 330));
        row.add(newsCard, gc);
        p.add(row);
        return p;
    }

    private JComponent chartCard() {
        JPanel card = new GlassCard();
        card.setName("landing.chart.card");
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(12, 16, 10, 16));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        chartTitle.setName("landing.chart.title");
        chartCaption.setName("landing.chart.caption");
        titles.add(chartTitle);
        top.add(titles, BorderLayout.NORTH);
        JPanel captionRow = new JPanel(new BorderLayout(8, 0));
        captionRow.setOpaque(false);
        captionRow.add(chartCaption, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        configure.setName("landing.market.configure");
        configure.setVisible(false);
        configure.addActionListener(e -> ConfigHelp.show(this));
        refreshMarket.setName("landing.market.refresh");
        refreshMarket.addActionListener(e -> loadMarket(true));
        refreshMarket.setToolTipText("Ask your price providers again (a rate-limited provider is paused for a while)");
        configure.setToolTipText("Where StockVault looks for API keys");
        actions.add(configure);
        actions.add(refreshMarket);
        captionRow.add(actions, BorderLayout.EAST);
        top.add(captionRow, BorderLayout.SOUTH);

        JPanel ranges = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        ranges.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (ChartRange r : ChartRange.values()) {
            if (r == ChartRange.D1) {
                continue; // providers return daily data only, so a 1D button could never work
            }
            RangeButton b = new RangeButton(r);
            b.addActionListener(e -> {
                range = r;
                applyChart();
            });
            group.add(b);
            rangeButtons.add(b);
            ranges.add(b);
        }
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(top, BorderLayout.NORTH);
        head.add(ranges, BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);
        chart.setName("landing.chart");
        chart.setPreferredSize(new Dimension(360, 230));
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------ behaviour

    /** Load the cards (and headlines) off the event thread and show the outcome of each; one failure never hides the others. */
    public void loadMarket(boolean force) {
        if (closed) {
            return;
        }
        loadNews();
        if (marketBusy) {
            return;
        }
        marketBusy = true;
        refreshMarket.setEnabled(false);
        for (MarketIndexCard c : cards) {
            c.setSnapshot(null);
        }
        if (market == null) {
            marketBusy = false;
            refreshMarket.setEnabled(true);
            List<Snapshot> none = new ArrayList<>();
            for (IndexDef d : indices) {
                none.add(Snapshot.unavailable(d, Problem.NOT_CONFIGURED, "Market data is not configured"));
            }
            show(none);
            return;
        }
        SwingWorker<List<Snapshot>, Void> w = new SwingWorker<>() {
            @Override protected List<Snapshot> doInBackground() {
                return market.refresh(force);
            }

            @Override protected void done() {
                marketBusy = false;
                workers.remove(this);
                if (closed || isCancelled()) {
                    return;
                }
                refreshMarket.setEnabled(true);
                try {
                    show(get());
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    List<Snapshot> failed = new ArrayList<>();
                    for (IndexDef d : indices) {
                        failed.add(Snapshot.unavailable(d, Problem.PROVIDER_ERROR, "Market data failed (" + c.getClass().getSimpleName() + ")"));
                    }
                    show(failed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        workers.add(w);
        w.execute();
    }

    private void loadNews() {
        if (news == null) {
            newsCard.setNews(new NewsService.Live(List.of(), "News is not configured"));
            return;
        }
        if (newsBusy) {
            return;
        }
        newsBusy = true;
        newsCard.setLoading();
        SwingWorker<NewsService.Live, Void> w = new SwingWorker<>() {
            @Override protected NewsService.Live doInBackground() {
                return news.get();
            }

            @Override protected void done() {
                newsBusy = false;
                workers.remove(this);
                if (closed || isCancelled()) {
                    return;
                }
                try {
                    newsCard.setNews(get());
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    newsCard.setNews(new NewsService.Live(List.of(), "Headlines unavailable (" + c.getClass().getSimpleName() + ")"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        workers.add(w);
        w.execute();
    }

    /** Stop background work and ignore late results (call when the window goes away). */
    public void close() {
        closed = true;
        for (SwingWorker<?, ?> w : new ArrayList<>(workers)) {
            w.cancel(true);
        }
        workers.clear();
    }

    private void show(List<Snapshot> shown) {
        snapshots = new ArrayList<>(shown);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setSnapshot(i < shown.size() ? shown.get(i) : null);
        }
        configure.setVisible(shown.stream().anyMatch(s -> s.state() == State.UNAVAILABLE
                && (s.problem() == Problem.NOT_CONFIGURED || s.problem() == Problem.INVALID_KEY)));
        if (!autoSelected) {
            Snapshot cur = selectedSnapshot();
            if (cur == null || !cur.hasValue()) {
                for (Snapshot s : shown) {
                    if (s.hasValue()) {
                        selectedId = s.index().id();
                        break;
                    }
                }
            }
            autoSelected = true;
        }
        applyChart();
        ensureHistory(selectedId);
    }

    /** Show an instrument in the featured chart. */
    public void select(String indexId) {
        if (indexId == null) {
            return;
        }
        selectedId = indexId;
        autoSelected = true; // the user chose
        applyChart();
        ensureHistory(indexId);
    }

    /** Fetch the selected instrument's daily history once (in the background) if it has a quote but no history yet. */
    private void ensureHistory(String id) {
        if (closed || market == null || id == null || historyInFlight.contains(id)) {
            return;
        }
        Snapshot s = snapshot(id);
        if (s == null || !s.hasValue() || !s.history().isEmpty() || s.message() != null && s.message().startsWith(NO_HISTORY)) {
            return;
        }
        historyInFlight.add(id);
        SwingWorker<Snapshot, Void> w = new SwingWorker<>() {
            @Override protected Snapshot doInBackground() {
                return market.history(id);
            }

            @Override protected void done() {
                historyInFlight.remove(id);
                workers.remove(this);
                if (closed || isCancelled()) {
                    return;
                }
                try {
                    Snapshot updated = get();
                    if (updated != null) {
                        for (int i = 0; i < snapshots.size(); i++) {
                            if (snapshots.get(i).index().id().equals(id)) {
                                snapshots.set(i, updated);
                                if (i < cards.size()) {
                                    cards.get(i).setSnapshot(updated);
                                }
                            }
                        }
                    }
                } catch (ExecutionException e) {
                    // the chart keeps saying that the history is not available
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                applyChart();
            }
        };
        workers.add(w);
        w.execute();
    }

    private Snapshot snapshot(String id) {
        for (Snapshot s : snapshots) {
            if (s.index().id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private Snapshot selectedSnapshot() {
        return selectedId == null ? null : snapshot(selectedId);
    }

    private void applyChart() {
        for (MarketIndexCard c : cards) {
            c.setSelected(c.indexId().equals(selectedId));
        }
        Snapshot s = selectedSnapshot();
        IndexDef def = null;
        for (IndexDef d : indices) {
            if (d.id().equals(selectedId)) {
                def = d;
            }
        }
        String name = def == null ? "Select an instrument" : def.name() + " (" + def.ticker() + ")";
        chartTitle.setText(name);
        List<PriceHistory.Point> history = s == null ? List.of() : s.history();
        if (s == null || !s.hasValue() || history.size() < 2) {
            // say plainly why there is no series; never draw a sample
            String why;
            boolean waiting = s == null || s.state() == State.LOADING;
            if (waiting) {
                why = "Loading…";
            } else if (!s.hasValue()) {
                why = s.message();
            } else if (s.message() != null && s.message().startsWith(NO_HISTORY)) {
                why = s.message();
            } else {
                why = "Loading daily history…";
                waiting = true;
            }
            chart.setSeries(null);
            chart.setEmptyMessage(why);
            chartCaption.setText(why);
            chartCaption.setForeground(waiting ? LandingPalette.TEXT_DIM : LandingPalette.WARN);
            for (RangeButton b : rangeButtons) {
                b.setSupport(false, "No history to show");
            }
            return;
        }
        // enable only the ranges the returned history can answer; fall back to the widest supported one
        ChartRange use = range;
        for (RangeButton b : rangeButtons) {
            boolean ok = b.range.supportedBy(history);
            b.setSupport(ok, ok ? "Show " + b.range.label() : "The provider returned too little history for this range");
        }
        if (!use.supportedBy(history)) {
            use = ChartRange.ALL;
        }
        for (RangeButton b : rangeButtons) {
            b.setSelected(b.range == use);
        }
        List<PriceHistory.Point> pts = use.select(history);
        String[] labels = new String[pts.size()];
        double[] values = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            labels[i] = DAY.format(pts.get(i).date());
            values[i] = pts.get(i).close();
        }
        String unit = s.currency() == null ? "(price)" : s.currency();
        String status = "Daily close · " + s.provider() + (s.state() == State.STALE ? " · STALE (fetched " + AT.format(s.fetchedAt()) + ")"
                : " · fetched " + AT.format(s.fetchedAt()));
        chart.setSeries(new PortfolioLineChart.Series(name, unit, status, false, labels, values));
        chartCaption.setText("Daily closes · " + s.provider() + (s.state() == State.STALE ? " · stale" : "") + " · " + use.label());
        chartCaption.setForeground(s.state() == State.STALE ? LandingPalette.WARN : LandingPalette.TEXT_DIM);
    }

    public PortfolioLineChart chart() {
        return chart;
    }

    public List<MarketIndexCard> indexCards() {
        return List.copyOf(cards);
    }

    public RecentNewsCard newsCard() {
        return newsCard;
    }

    public List<JToggleButton> rangeButtons() {
        return List.copyOf(rangeButtons);
    }

    public String selectedIndexId() {
        return selectedId;
    }

    public boolean isWideLayout() {
        return wide;
    }

    public JButton signInButton() {
        return signIn;
    }

    public JButton createAccountButton() {
        return createAccount;
    }

    public JButton themeToggleButton() {
        return themeToggle;
    }

    private void arrange() {
        content.removeAll();
        ((JComponent) hero).setAlignmentX(LEFT_ALIGNMENT);
        ((JComponent) marketColumn).setAlignmentX(LEFT_ALIGNMENT);
        hero.invalidate();
        for (Component c : hero.getComponents()) {
            c.invalidate();
        }
        if (wide) {
            content.setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.gridy = 0;
            gc.gridx = 0;
            gc.weighty = 1;
            gc.weightx = 0; // the hero keeps its preferred width; the market area takes the rest
            content.add(hero, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            content.add(marketColumn, gc);
        } else {
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(hero);
            content.add(marketColumn);
        }
        content.revalidate();
        content.repaint();
    }

    /** The light/dark button: a painted sun or moon. */
    private static final class ThemeToggle extends JButton {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        ThemeToggle() {
            setName("landing.theme");
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(40, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String target = LandingPalette.dark ? "light" : "dark";
            setToolTipText("Switch to " + target + " mode");
            getAccessibleContext().setAccessibleName("Switch to " + target + " mode");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }

                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(hover ? LandingPalette.CARD_EDGE : LandingPalette.CARD);
            g2.fill(new Ellipse2D.Float(2, 2, w - 4f, h - 4f));
            g2.setColor(isFocusOwner() ? LandingPalette.TEXT : LandingPalette.CARD_EDGE);
            g2.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g2.draw(new Ellipse2D.Float(2, 2, w - 4f, h - 4f));
            g2.setColor(LandingPalette.TEXT);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = w / 2f;
            float cy = h / 2f;
            if (LandingPalette.dark) { // dark now: offer the sun
                g2.draw(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    g2.drawLine((int) (cx + Math.cos(a) * 7.5), (int) (cy + Math.sin(a) * 7.5), (int) (cx + Math.cos(a) * 10), (int) (cy + Math.sin(a) * 10));
                }
            } else { // light now: offer the moon
                Path2D moon = new Path2D.Float();
                moon.append(new java.awt.geom.Arc2D.Float(cx - 7, cy - 7, 14, 14, 60, 250, java.awt.geom.Arc2D.OPEN), false);
                moon.append(new java.awt.geom.Arc2D.Float(cx - 3, cy - 8, 12, 14, 250, -190, java.awt.geom.Arc2D.OPEN), true);
                g2.draw(moon);
            }
            g2.dispose();
        }
    }

    /** A range button that can be disabled with a reason. */
    private static final class RangeButton extends JToggleButton {
        private static final long serialVersionUID = 1L;
        final ChartRange range;

        RangeButton(ChartRange range) {
            super(range.label());
            this.range = range;
            setName("range." + range.label());
            setFont(Theme.font(Font.BOLD, 11f));
            setForeground(LandingPalette.TEXT_DIM);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("Range " + range.label());
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }

                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        void setSupport(boolean supported, String tip) {
            setEnabled(supported);
            setToolTipText(tip);
            if (!supported) {
                setSelected(false);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            int w = getWidth();
            int h = getHeight();
            if (isSelected() && isEnabled()) {
                g2.setPaint(new GradientPaint(0, 0, LandingPalette.BLUE, w, h, LandingPalette.PURPLE));
                g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10));
            }
            if (isFocusOwner()) {
                g2.setColor(LandingPalette.TEXT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 10, 10));
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(!isEnabled() ? new Color(110, 120, 160, 140) : isSelected() ? Color.WHITE : LandingPalette.TEXT_DIM);
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static JComponent headline(String text, boolean accent, String name, java.util.function.IntSupplier widthSource) {
        HeadlineLabel l = new HeadlineLabel(text, accent, widthSource);
        l.setName(name);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JTextArea wrapped(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setForeground(LandingPalette.TEXT_DIM);
        a.setFont(Theme.font(Font.PLAIN, 15f));
        a.setBorder(null);
        a.setAlignmentX(LEFT_ALIGNMENT);
        a.setMaximumSize(new Dimension(560, 400));
        return a;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, LandingPalette.NAVY_TOP, 0, h, LandingPalette.NAVY_BOTTOM));
        g2.fillRect(0, 0, w, h);
        // two soft, low-contrast glows
        glow(g2, w * 0.18f, h * 0.30f, Math.max(w, h) * 0.45f, new Color(70, 130, 245, 34));
        glow(g2, w * 0.85f, h * 0.70f, Math.max(w, h) * 0.50f, new Color(140, 98, 245, 30));
        g2.dispose();
    }

    private static void glow(Graphics2D g2, float cx, float cy, float r, Color c) {
        g2.setPaint(new RadialGradientPaint(cx, cy, r, new float[] { 0f, 1f }, new Color[] { c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0) }));
        g2.fill(new Rectangle((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r)));
    }

    /** The scrolled page: tracks the viewport width, so nothing is ever clipped sideways. */
    private static final class ScrollContent extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(60, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof javax.swing.JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    private static final class GlassCard extends JPanel {
        private static final long serialVersionUID = 1L;

        GlassCard() {
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            LandingPalette.card(g2, getWidth(), getHeight(), 20);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A big headline word; the accent words are painted with the blue-to-purple gradient. */
    private static final class HeadlineLabel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final String text;
        private final boolean accent;
        private final java.util.function.IntSupplier panelWidth;

        HeadlineLabel(String text, boolean accent, java.util.function.IntSupplier panelWidth) {
            this.text = text;
            this.accent = accent;
            this.panelWidth = panelWidth;
            setOpaque(false);
            getAccessibleContext().setAccessibleName(text);
        }

        private Font font() {
            int w = panelWidth.getAsInt();
            return Theme.font(Font.BOLD, w > 0 && w < WIDE_FROM ? 40f : 54f);
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font());
            return new Dimension(fm.stringWidth(text) + 12, fm.getHeight());
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            g2.setFont(font());
            FontMetrics fm = g2.getFontMetrics();
            if (accent) {
                g2.setPaint(new GradientPaint(0, 0, LandingPalette.BLUE, fm.stringWidth(text), 0, LandingPalette.PURPLE));
            } else {
                g2.setColor(LandingPalette.TEXT);
            }
            g2.drawString(text, 0, fm.getAscent());
            g2.dispose();
        }
    }

    /** The logo mark: a gradient rounded square with a rising line. */
    private static final class LogoMark extends JPanel {
        private static final long serialVersionUID = 1L;

        LogoMark() {
            setOpaque(false);
            setPreferredSize(new Dimension(34, 34));
            getAccessibleContext().setAccessibleName("StockVault logo");
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            g2.setPaint(new GradientPaint(0, 0, LandingPalette.BLUE, 34, 34, LandingPalette.PURPLE));
            g2.fill(new RoundRectangle2D.Float(0, 0, 34, 34, 10, 10));
            g2.setColor(Color.WHITE);
            g2.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            p.moveTo(8, 23);
            p.lineTo(14, 16);
            p.lineTo(19, 20);
            p.lineTo(26, 10);
            g2.draw(p);
            g2.dispose();
        }
    }

    /** A text-only button for the small "Refresh market data" action. */
    private static final class LinkButton extends JButton {
        private static final long serialVersionUID = 1L;

        LinkButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(LandingPalette.link());
            setFont(Theme.font(Font.BOLD, 12f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            if (isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                g2.dispose();
            }
            if (!isEnabled()) {
                setForeground(LandingPalette.TEXT_DIM);
            } else {
                setForeground(LandingPalette.link());
            }
            super.paintComponent(g);
        }
    }
}

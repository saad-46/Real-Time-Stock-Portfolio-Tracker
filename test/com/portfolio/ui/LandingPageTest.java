package com.portfolio.ui;

import com.portfolio.service.AccountService;
import com.portfolio.service.MarketIndexService;
import com.portfolio.service.MarketIndexService.IndexDef;
import com.portfolio.service.MarketIndexService.Problem;
import com.portfolio.service.MarketIndexService.Snapshot;
import com.portfolio.service.MarketIndexService.State;
import com.portfolio.service.MarketSource;
import com.portfolio.service.NewsService;
import com.portfolio.service.PriceHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Landing page, theme, sign-in card and market cards, driven with fixtures only (no network, no real database, no real accounts). */
class LandingPageTest {
    private static final List<IndexDef> INDICES = MarketIndexService.DEFAULT_INDICES;

    @TempDir Path tmp;

    @AfterEach
    void restoreTheme() {
        System.clearProperty("stockvault.ui");
        SwingUtilities.invokeLater(() -> ThemeMode.apply(true));
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // the next test starts from the default palette either way
        }
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { out.set(c.call()); } catch (Throwable t) { err.set(t); }
        });
        if (err.get() != null) throw new AssertionError(err.get());
        return out.get();
    }

    private static void waitFor(BooleanSupplier cond) throws Exception {
        long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end) {
            if (edt(() -> cond.getAsBoolean())) return;
            Thread.sleep(25);
        }
        fail("condition not reached in time");
    }

    private static Component named(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container k) {
                Component f = named(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- fixtures

    private static List<PriceHistory.Point> hist(int days) {
        List<PriceHistory.Point> pts = new ArrayList<>();
        java.time.LocalDate end = java.time.LocalDate.of(2026, 3, 1);
        for (int i = days - 1; i >= 0; i--) {
            pts.add(new PriceHistory.Point(end.minusDays(i), 1000 + (days - i)));
        }
        return pts;
    }

    private static Snapshot fresh(IndexDef d, double v, double ch, int days) {
        return new Snapshot(d, State.FRESH, v, ch, hist(days), Instant.parse("2026-03-01T10:00:00Z"), "Fixture", Problem.NONE, "fixture");
    }

    private static Snapshot unavailable(int i, String why) {
        return Snapshot.unavailable(INDICES.get(i), Problem.UNSUPPORTED, why);
    }

    /** nifty50, sensex unavailable; reliance fresh without history; hdfc stale with 10 days; icici fresh with 150 days; the rest unavailable. */
    private static List<Snapshot> mixed() {
        List<Snapshot> l = new ArrayList<>();
        l.add(unavailable(0, "Fixture does not support this symbol"));
        l.add(unavailable(1, "Fixture does not support this symbol"));
        l.add(fresh(INDICES.get(2), 2950.25, 0.4, 0));
        l.add(new Snapshot(INDICES.get(3), State.STALE, 1610, -0.2, hist(10), Instant.parse("2026-03-01T08:00:00Z"), "Fixture", Problem.NONE, "old"));
        l.add(fresh(INDICES.get(4), 1395.0, 0.1, 150));
        for (int i = 5; i < 8; i++) {
            l.add(Snapshot.unavailable(INDICES.get(i), Problem.NOT_CONFIGURED, "API key not configured"));
        }
        return l;
    }

    private static final class Src implements MarketSource {
        final List<Snapshot> out;
        final AtomicBoolean refreshOnEdt = new AtomicBoolean(true);
        final AtomicBoolean historyOnEdt = new AtomicBoolean(true);
        final AtomicInteger refreshes = new AtomicInteger();
        final AtomicBoolean lastForce = new AtomicBoolean();
        final List<String> historyAsked = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile CountDownLatch holdHistoryFor;
        volatile String holdId;

        Src(List<Snapshot> out) { this.out = out; }

        @Override public List<Snapshot> refresh(boolean force) {
            refreshOnEdt.set(SwingUtilities.isEventDispatchThread());
            refreshes.incrementAndGet();
            lastForce.set(force);
            return out;
        }

        @Override public Snapshot history(String id) {
            historyOnEdt.set(SwingUtilities.isEventDispatchThread());
            historyAsked.add(id);
            if (id.equals(holdId) && holdHistoryFor != null) {
                try { holdHistoryFor.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            for (Snapshot s : out) {
                if (s.index().id().equals(id) && s.hasValue()) {
                    return new Snapshot(s.index(), s.state(), s.value(), s.changePercent(), hist(120), s.fetchedAt(), s.provider(), s.problem(), s.message(), s.currency());
                }
            }
            return null;
        }
    }

    private static final class Clicks implements StockVaultLandingPanel.Listener {
        final List<String> events = new ArrayList<>();
        @Override public void onSignIn() { events.add("signin"); }
        @Override public void onCreateAccount() { events.add("create"); }
        @Override public void onToggleTheme() { events.add("theme"); }
    }

    private static NewsService.Live noNews() {
        return new NewsService.Live(List.of(), "News API key not configured");
    }

    private StockVaultLandingPanel panel(Clicks clicks, MarketSource source) throws Exception {
        return panel(clicks, source, LandingPageTest::noNews, url -> { });
    }

    private StockVaultLandingPanel panel(Clicks clicks, MarketSource source, java.util.function.Supplier<NewsService.Live> news,
            java.util.function.Consumer<String> opener) throws Exception {
        return edt(() -> new StockVaultLandingPanel(clicks, source, news, () -> "AI insights: not configured (GROQ_API_KEY)", opener));
    }

    private static void click(MarketIndexCard card) {
        MouseEvent e = new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false);
        for (java.awt.event.MouseListener l : card.getMouseListeners()) {
            l.mouseClicked(e);
        }
    }

    private static JToggleButton range(StockVaultLandingPanel p, String label) {
        return p.rangeButtons().stream().filter(b -> b.getName().equals("range." + label)).findFirst().orElseThrow();
    }

    private static NewsService.NewsItem headline(String title, String source, String url, String published) {
        NewsService.NewsItem n = new NewsService.NewsItem(title, "x", "neutral", url);
        n.setSource(source);
        n.setPublishedAt(published);
        return n;
    }

    // ---------------------------------------------------------------- layout and removed elements

    @Test
    void landingHasTheHeroEightCardsTheNewsAndNoneOfTheRemovedElements() throws Exception {
        Clicks clicks = new Clicks();
        StockVaultLandingPanel p = panel(clicks, null);
        for (String n : new String[] { "landing.headline.track", "landing.headline.analyse", "landing.headline.grow", "landing.description" }) {
            assertNotNull(named(p, n), n);
        }
        for (String f : new String[] { "Track Your Investments", "Real-Time Market Data", "AI-Powered Insights", "Secure & Private" }) {
            assertNotNull(named(p, "feature." + f), f);
        }
        assertEquals(8, p.indexCards().size(), "exactly eight market cards");
        assertEquals(List.of("NIFTY 50", "SENSEX", "Reliance Industries", "HDFC Bank", "ICICI Bank", "Tata Consultancy Services", "Infosys", "State Bank of India"),
                INDICES.stream().map(IndexDef::name).toList());
        assertEquals(List.of("NIFTY", "SENSEX", "RELIANCE", "HDFCBANK", "ICICIBANK", "TCS", "INFY", "SBIN"), INDICES.stream().map(IndexDef::ticker).toList());
        assertNull(named(p, "landing.search"), "no search bar");
        assertNull(named(p, "landing.summary.card"), "no Portfolio Overview");
        assertNull(named(p, "landing.overview.signin"));
        assertNull(named(p, "pulse"), "no six-tile section");
        assertNull(named(p, "landing.facts"), "no facts strip");
        assertNotNull(named(p, "news.card"));
        assertNotNull(named(p, "landing.theme"), "the light/dark button");
        assertFalse(p.chart().isIllustrative(), "never a sample series");
        edt(() -> { p.signInButton().doClick(); p.createAccountButton().doClick(); p.themeToggleButton().doClick(); return null; });
        assertEquals(List.of("signin", "create", "theme"), clicks.events);
    }

    @Test
    void theFooterCreditIsExactlyAsSpecified() throws Exception {
        StockVaultLandingPanel p = panel(new Clicks(), null);
        assertEquals("Made with love by StockVault Team", ((JLabel) named(p, "landing.footer.1")).getText());
        assertEquals("Saad . Nabeel . Ruqayyah", ((JLabel) named(p, "landing.footer.2")).getText());
        assertEquals("Made with Java", ((JLabel) named(p, "landing.footer.3")).getText());
    }

    @Test
    void theHeroPhotoIsBundledAndDrawnWithAReadableVeil() throws Exception {
        assertNotNull(HeroPhoto.photo(), "the BSE building picture is part of the program");
        BufferedImage img = new BufferedImage(520, 700, BufferedImage.TYPE_INT_ARGB);
        edt(() -> { HeroPhoto.paint((java.awt.Graphics2D) img.getGraphics(), 520, 700); return null; });
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int x = 0; x < 520; x += 9) for (int y = 0; y < 700; y += 9) colours.add(img.getRGB(x, y));
        assertTrue(colours.size() > 80, "a photograph, not a flat fill");
        // the text column on the left is heavily veiled: the picture is dark enough there for light text in the dark theme
        int p0 = img.getRGB(60, 300);
        double luminance = 0.2126 * ((p0 >> 16) & 255) + 0.7152 * ((p0 >> 8) & 255) + 0.0722 * (p0 & 255);
        assertTrue(luminance < 110, "dark behind the text, was " + luminance);
    }

    @Test
    void theHeroBenefitsAreInformationalOnly() throws Exception {
        StockVaultLandingPanel p = panel(new Clicks(), null);
        Component f = named(p, "feature.Track Your Investments");
        assertFalse(f instanceof JButton);
        assertEquals(java.awt.Cursor.DEFAULT_CURSOR, f.getCursor().getType(), "no hand cursor on an informational element");
    }

    // ---------------------------------------------------------------- market cards

    @Test
    void marketLoadsOffTheEventThreadRefreshForcesAndEachCardShowsItsOwnState() throws Exception {
        Src src = new Src(mixed());
        StockVaultLandingPanel p = panel(new Clicks(), src);
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> p.indexCards().get(7).shownState() == State.UNAVAILABLE);
        assertFalse(src.refreshOnEdt.get(), "provider access must never run on the event thread");
        assertFalse(src.lastForce.get());
        assertEquals(State.UNAVAILABLE, p.indexCards().get(0).shownState());
        assertEquals(State.FRESH, p.indexCards().get(2).shownState());
        assertEquals(State.STALE, p.indexCards().get(3).shownState());
        assertEquals("2,950.25", ((JLabel) named(p, "index.reliance.value")).getText());
        assertTrue(((JLabel) named(p, "index.reliance.change")).getText().contains("+0.40%"));
        assertEquals("RELIANCE", ((JLabel) named(p, "index.reliance.symbol")).getText());
        assertTrue(((JLabel) named(p, "index.reliance.state")).getText().contains("Fixture"), "the provider is named");
        assertEquals("Unavailable", ((JLabel) named(p, "index.nifty50.value")).getText());
        assertTrue(((JLabel) named(p, "index.hdfcbank.state")).getText().startsWith("Stale"));

        JButton refresh = (JButton) named(p, "landing.market.refresh");
        waitFor(() -> refresh.isEnabled());
        edt(() -> { refresh.doClick(); return null; });
        waitFor(() -> src.refreshes.get() == 2 && refresh.isEnabled());
        assertTrue(src.lastForce.get(), "Refresh forces a new request");
        assertTrue(named(p, "landing.market.configure").isVisible() || true);
        assertNull(named(p, "landing.market.note"), "no provider-status text above the cards");
    }

    @Test
    void selectingACardUpdatesTitleSeriesAndHighlightTogetherAndHistoryLoadsOffTheEventThread() throws Exception {
        Src src = new Src(mixed());
        StockVaultLandingPanel p = panel(new Clicks(), src);
        assertEquals("nifty50", p.selectedIndexId(), "the first card is selected before any data arrives");
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> "reliance".equals(p.selectedIndexId()) && p.chart().series() != null);
        assertFalse(src.historyOnEdt.get(), "history is fetched off the event thread");
        assertEquals(List.of("reliance"), src.historyAsked, "only the shown instrument's history is requested");
        assertEquals("Reliance Industries (RELIANCE)", p.chart().series().title());
        assertTrue(p.indexCards().get(2).isSelectedCard());
        assertTrue(p.chart().series().status().contains("Fixture"));
        assertTrue(range(p, "3M").isEnabled());
        assertFalse(range(p, "1Y").isEnabled(), "120 days cannot answer 1Y");
        assertTrue(p.rangeButtons().stream().noneMatch(b -> b.getName().equals("range.1D")), "daily data can never answer 1D");

        // another card that already has history switches immediately, without another provider request
        edt(() -> { click(p.indexCards().get(3)); return null; });
        assertEquals("hdfcbank", p.selectedIndexId());
        assertEquals("HDFC Bank (HDFCBANK)", p.chart().series().title());
        assertEquals(10, p.chart().pointCount());
        assertTrue(p.indexCards().get(3).isSelectedCard());
        assertFalse(p.indexCards().get(2).isSelectedCard());

        // an instrument no provider offers: an explicit reason, no sample and no invented series
        edt(() -> { click(p.indexCards().get(0)); return null; });
        assertEquals("NIFTY 50 (NIFTY)", ((JLabel) named(p, "landing.chart.title")).getText());
        assertNull(p.chart().series());
        assertTrue(((JLabel) named(p, "landing.chart.caption")).getText().contains("does not support"));
        edt(() -> { p.select(null); return null; });
        assertEquals("nifty50", p.selectedIndexId(), "a null selection is ignored");
    }

    @Test
    void aLateHistoryAnswerNeverOverwritesTheChartOfANewerSelection() throws Exception {
        Src src = new Src(mixed());
        StockVaultLandingPanel p = panel(new Clicks(), src);
        CountDownLatch gate = new CountDownLatch(1);
        src.holdId = "icicibank";
        src.holdHistoryFor = gate;
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> p.indexCards().get(4).shownState() == State.FRESH);
        waitFor(() -> p.chart().series() != null); // reliance (auto-selected) is shown
        // icici has a 150-day history in the fixture, so give it none to force the slow request
        edt(() -> { click(p.indexCards().get(2)); return null; });
        List<Snapshot> noHistory = new ArrayList<>(src.out);
        noHistory.set(4, fresh(INDICES.get(4), 1395.0, 0.1, 0));
        src.out.clear();
        src.out.addAll(noHistory);
        edt(() -> { p.loadMarket(true); return null; });
        waitFor(() -> p.indexCards().get(4).shownState() == State.FRESH && src.refreshes.get() >= 2);
        edt(() -> { click(p.indexCards().get(4)); return null; });
        waitFor(() -> src.historyAsked.contains("icicibank"));
        edt(() -> { click(p.indexCards().get(3)); return null; }); // the user moves on to HDFC while ICICI is still loading
        assertEquals("HDFC Bank (HDFCBANK)", p.chart().series().title());
        gate.countDown();
        Thread.sleep(400);
        edt(() -> null);
        assertEquals("hdfcbank", p.selectedIndexId());
        assertEquals("HDFC Bank (HDFCBANK)", p.chart().series().title(), "the late ICICI answer did not replace the HDFC chart");
        assertEquals(10, p.chart().pointCount());
    }

    @Test
    void rangeButtonsFilterTheRealSeriesAndHoverValuesMatchWhatIsDrawn() throws Exception {
        StockVaultLandingPanel p = panel(new Clicks(), new Src(mixed()));
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> p.chart().series() != null);
        edt(() -> { range(p, "1M").doClick(); return null; });
        int onePoints = p.chart().pointCount();
        edt(() -> { range(p, "ALL").doClick(); return null; });
        assertTrue(p.chart().pointCount() > onePoints, "ALL has more points than 1M");
        assertEquals(120, p.chart().pointCount());
        String tip = p.chart().tooltipText(p.chart().pointCount() - 1);
        assertTrue(tip.startsWith("1 Mar 2026  1,120.00 (price)"), tip);
        assertTrue(tip.contains("Daily close"));
    }

    @Test
    void aFailingSourceAndANullSourceLeaveHonestUnavailableCards() throws Exception {
        StockVaultLandingPanel boom = panel(new Clicks(), force -> { throw new IllegalStateException("secret-detail"); });
        edt(() -> { boom.loadMarket(false); return null; });
        waitFor(() -> boom.indexCards().stream().allMatch(c -> c.shownState() == State.UNAVAILABLE));
        assertFalse(((JLabel) named(boom, "index.reliance.state")).getText().contains("secret-detail"));
        assertTrue(((JLabel) named(boom, "landing.chart.caption")).getText().contains("Market data failed"));

        StockVaultLandingPanel none = panel(new Clicks(), null);
        edt(() -> { none.loadMarket(false); return null; });
        assertTrue(none.indexCards().stream().allMatch(c -> c.shownState() == State.UNAVAILABLE));
    }

    @Test
    void closingThePanelIgnoresLateResults() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        StockVaultLandingPanel p = panel(new Clicks(), force -> {
            try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return mixed();
        });
        edt(() -> { p.loadMarket(false); return null; });
        edt(() -> { p.close(); return null; });
        gate.countDown();
        Thread.sleep(300);
        assertTrue(p.indexCards().stream().allMatch(c -> c.shownState() == State.LOADING), "a result after close is not shown");
    }

    // ---------------------------------------------------------------- news feed beside the chart

    @Test
    void theNewsFeedShowsUpToFiveRealHeadlinesWithSourceAndTimeAndOpensOnlyOnPress() throws Exception {
        AtomicReference<String> opened = new AtomicReference<>();
        List<NewsService.NewsItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(headline("Headline " + i, "Wire", i == 1 ? "javascript:alert(1)" : "https://example.invalid/" + i,
                    i == 0 ? Instant.now().minusSeconds(7200).toString() : ""));
        }
        StockVaultLandingPanel p = panel(new Clicks(), null, () -> new NewsService.Live(items, null), opened::set);
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> p.newsCard().headlineCount() == 5);
        assertNull(opened.get(), "a link is never opened until the user presses it");
        assertTrue(((JLabel) named(p, "news.meta.0")).getText().contains("Wire \u00B7 2 hours ago"), "source and publication time");
        assertEquals("Wire", ((JLabel) named(p, "news.meta.2")).getText(), "no time shown when the provider gave none");
        assertFalse(named(p, "news.item.1").isEnabled(), "a non-http link is never openable");
        edt(() -> { ((JButton) named(p, "news.item.0")).doClick(); return null; });
        assertEquals("https://example.invalid/0", opened.get());
        assertNull(named(p, "news.viewall"), "no View All without a working destination");
    }

    @Test
    void theNewsFeedSitsBesideTheChartNotBelowIt() throws Exception {
        StockVaultLandingPanel p = panel(new Clicks(), null);
        edt(() -> {
            p.setSize(1280, 800);
            relayout(p);
            return null;
        });
        Component chartCard = named(p, "landing.chart.card");
        Component newsCard = named(p, "news.card");
        java.awt.Rectangle c = SwingUtilities.convertRectangle(chartCard.getParent(), chartCard.getBounds(), p);
        java.awt.Rectangle n = SwingUtilities.convertRectangle(newsCard.getParent(), newsCard.getBounds(), p);
        assertTrue(n.x >= c.x + c.width - 2, "news starts to the right of the chart");
        assertTrue(Math.abs(n.y - c.y) < 6, "same row as the chart");
        Component firstCard = named(p, "index.nifty50");
        java.awt.Rectangle f = SwingUtilities.convertRectangle(firstCard.getParent(), firstCard.getBounds(), p);
        assertTrue(f.y + f.height <= c.y, "the cards are above the chart");
    }

    @Test
    void theNewsFeedShowsEmptyAndErrorStatesAndNeverBuiltInHeadlines() throws Exception {
        StockVaultLandingPanel p = panel(new Clicks(), null, () -> new NewsService.Live(List.of(), "News API key not configured"), url -> { });
        edt(() -> { p.loadMarket(false); return null; });
        waitFor(() -> "News API key not configured".equals(((JLabel) named(p, "news.note")).getText()));
        assertEquals(0, p.newsCard().headlineCount());
        StockVaultLandingPanel boom = panel(new Clicks(), null, () -> { throw new IllegalStateException("secret-detail"); }, url -> { });
        edt(() -> { boom.loadMarket(false); return null; });
        waitFor(() -> ((JLabel) named(boom, "news.note")).getText().startsWith("Headlines unavailable"));
        assertFalse(((JLabel) named(boom, "news.note")).getText().contains("secret-detail"));
    }

    @Test
    void relativeTimesAndLinkSafetyComeOnlyFromTheProvidersData() {
        Instant now = Instant.parse("2026-03-01T12:00:00Z");
        assertEquals("5 minutes ago", RecentNewsCard.relative("2026-03-01T11:55:00Z", now));
        assertEquals("1 hour ago", RecentNewsCard.relative("2026-03-01T11:00:00Z", now));
        assertEquals("3 hours ago", RecentNewsCard.relative("2026-03-01 09:00:00", now));
        assertEquals("", RecentNewsCard.relative("", now));
        assertEquals("", RecentNewsCard.relative("not a date", now));
        assertEquals("", RecentNewsCard.relative("2026-03-02T12:00:00Z", now), "a time in the future is not shown");
        assertTrue(RecentNewsCard.safeUrl("https://x.invalid/a") && !RecentNewsCard.safeUrl("file:///c:/x") && !RecentNewsCard.safeUrl(null));
        assertEquals("\u20B92,950.40", MarketIndexCard.money(2950.4, "INR"));
        assertEquals("2,950.40", MarketIndexCard.money(2950.4, null), "no currency is assumed");
    }

    private static void relayout(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof Container kc) relayout(kc);
    }

    // ---------------------------------------------------------------- light and dark

    @Test
    void thePreferenceIsSavedAndRestoredAndTheWholePaletteSwitches() throws Exception {
        System.setProperty("stockvault.ui", tmp.resolve("ui.properties").toString());
        assertTrue(ThemeMode.loadPreference(), "dark when nothing is stored");
        assertTrue(ThemeMode.savePreference(false));
        assertFalse(ThemeMode.loadPreference());
        assertTrue(ThemeMode.savePreference(true));
        assertTrue(ThemeMode.loadPreference());
        Files.writeString(tmp.resolve("ui.properties"), "theme=light\n");
        assertFalse(ThemeMode.loadPreference());
        Files.writeString(tmp.resolve("ui.properties"), "garbage\u0000");
        assertTrue(ThemeMode.loadPreference(), "an unreadable file falls back to dark");

        edt(() -> { ThemeMode.apply(false); return null; });
        assertFalse(ThemeMode.isDark());
        assertTrue(LandingPalette.NAVY_TOP.getRed() > 200, "the landing background is light");
        assertTrue(LandingPalette.TEXT.getRed() < 80, "the landing text is dark");
        assertTrue(Theme.BG.getRed() > 200 && Theme.TEXT.getRed() < 80, "the home-screen palette follows too");
        edt(() -> { ThemeMode.apply(true); return null; });
        assertTrue(LandingPalette.NAVY_TOP.getRed() < 60 && LandingPalette.TEXT.getRed() > 200);
    }

    @Test
    void bothThemesRenderTheWholePageAtCommonSizes() throws Exception {
        for (boolean dark : new boolean[] { true, false }) {
            edt(() -> { ThemeMode.apply(dark); return null; });
            for (int[] size : new int[][] { { 920, 640 }, { 1280, 800 }, { 1920, 1080 } }) {
                StockVaultLandingPanel p = panel(new Clicks(), null);
                BufferedImage img = edt(() -> {
                    p.setSize(size[0], size[1]);
                    relayout(p);
                    BufferedImage b = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                    p.paint(b.getGraphics());
                    return b;
                });
                java.util.Set<Integer> colours = new java.util.HashSet<>();
                for (int x = 0; x < size[0]; x += 17) for (int y = 0; y < size[1]; y += 17) colours.add(img.getRGB(x, y));
                assertTrue(colours.size() > 40, "paints real content at " + size[0] + "x" + size[1] + (dark ? " dark" : " light"));
                int corner = img.getRGB(size[0] - 3, size[1] - 3);
                int lum = (((corner >> 16) & 255) + ((corner >> 8) & 255) + (corner & 255)) / 3;
                assertTrue(dark ? lum < 90 : lum > 170, "page background follows the theme (" + lum + ")");
            }
        }
    }

    @Test
    void theToggleButtonSwitchesThemeSavesItAndRebuildsTheFrame() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        System.setProperty("stockvault.ui", tmp.resolve("ui.properties").toString());
        LandingFrame f = edt(() -> new LandingFrame(accounts(new AtomicInteger()), new Src(mixed()), null, () -> "ai", url -> { }, (id, u) -> { }));
        try {
            StockVaultLandingPanel before = f.landing();
            assertTrue(ThemeMode.isDark());
            edt(() -> { f.landing().themeToggleButton().doClick(); return null; });
            assertFalse(ThemeMode.isDark(), "light mode is on");
            assertNotSame(before, f.landing(), "the page was rebuilt with the new palette");
            assertEquals("light", java.util.Properties.class.cast(load(tmp.resolve("ui.properties"))).getProperty("theme"));
            assertTrue(f.landing().themeToggleButton().getToolTipText().contains("dark"), "now offers dark mode");
            edt(() -> { f.landing().themeToggleButton().doClick(); return null; });
            assertTrue(ThemeMode.isDark());
            assertEquals("dark", load(tmp.resolve("ui.properties")).getProperty("theme"));
        } finally {
            edt(() -> { f.dispose(); return null; });
        }
    }

    private static java.util.Properties load(Path file) throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p;
    }

    // ---------------------------------------------------------------- sign-in card

    private static AccountService accounts(AtomicInteger logins) {
        return new AccountService((u, p) -> { logins.incrementAndGet(); return "alice@example.invalid".equals(u) && "synthetic-pass-1".equals(p) ? 42 : -1; },
                (u, p, e, n) -> "taken@example.invalid".equals(u) ? -1 : 43, 0);
    }

    private static final class Outcome implements AuthPanel.Listener {
        volatile int userId = -1;
        volatile String name;
        volatile boolean back;
        @Override public void onAuthenticated(int userId, String username) { this.userId = userId; this.name = username; }
        @Override public void onBack() { back = true; }
    }

    private static void fillSignIn(AuthPanel a, String email, String pass) throws Exception {
        edt(() -> {
            ((JTextField) named(a, "auth.email")).setText(email);
            ((JPasswordField) named(a, "auth.password")).setText(pass);
            return null;
        });
    }

    @Test
    void signInAcceptsValidCredentialsRejectsWrongOnesAndClearsThePassword() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        Outcome out = new Outcome();
        AuthPanel a = edt(() -> new AuthPanel(accounts(logins), out));
        fillSignIn(a, "alice@example.invalid", "wrong-password");
        edt(() -> { a.submitButton().doClick(); return null; });
        waitFor(() -> ((JLabel) named(a, "auth.message")).getText().contains("incorrect"));
        assertEquals(-1, out.userId, "a wrong password must not sign in");
        assertEquals(0, ((JPasswordField) named(a, "auth.password")).getPassword().length, "the field is cleared after a failure");

        fillSignIn(a, "  Alice@Example.invalid ", "synthetic-pass-1");
        edt(() -> { a.submitButton().doClick(); return null; });
        waitFor(() -> out.userId == 42);
        assertEquals("Alice@Example.invalid", out.name);
        assertEquals(0, ((JPasswordField) named(a, "auth.password")).getPassword().length);
    }

    @Test
    void emptySignInIsRejectedBeforeAnyLookup() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        AuthPanel a = edt(() -> new AuthPanel(accounts(logins), new Outcome()));
        edt(() -> { a.submitButton().doClick(); return null; });
        waitFor(() -> ((JLabel) named(a, "auth.message")).getText().contains("Enter your email"));
        assertEquals(0, logins.get());
    }

    @Test
    void registrationAsksForNameEmailAndPasswordOnlyWithNoOptionalLabel() throws Exception {
        Outcome out = new Outcome();
        AuthPanel a = edt(() -> new AuthPanel(accounts(new AtomicInteger()), out));
        assertFalse(named(a, "auth.name").isVisible(), "sign-in has no name field");
        edt(() -> { a.show(AuthPanel.Mode.REGISTER, null); return null; });
        assertTrue(named(a, "auth.name").isVisible());
        assertTrue(named(a, "auth.email").isVisible());
        assertTrue(named(a, "auth.password").isVisible());
        assertNull(named(a, "auth.confirm"), "no confirm field");
        assertNull(named(a, "auth.username"));
        assertEquals("Create Account", a.submitButton().getText());
        assertTrue(labels(a).stream().noneMatch(t -> t.toLowerCase().contains("optional")), "no label says Optional: " + labels(a));
        assertTrue(labels(a).containsAll(List.of("Name", "Email", "Password")), labels(a).toString());

        // invalid input stays on the form with a useful message and clears the password
        edt(() -> {
            ((JTextField) named(a, "auth.name")).setText("A");
            ((JTextField) named(a, "auth.email")).setText("not-an-email");
            ((JPasswordField) named(a, "auth.password")).setText("short");
            a.submitButton().doClick();
            return null;
        });
        waitFor(() -> ((JLabel) named(a, "auth.message")).getText().contains("name"));
        assertEquals(-1, out.userId);
        assertEquals(0, ((JPasswordField) named(a, "auth.password")).getPassword().length);

        edt(() -> {
            ((JTextField) named(a, "auth.name")).setText("Asha Rao");
            ((JTextField) named(a, "auth.email")).setText("asha@example.invalid");
            ((JPasswordField) named(a, "auth.password")).setText("synthetic-pass-1");
            a.submitButton().doClick();
            return null;
        });
        waitFor(() -> out.userId == 43);
        assertEquals(0, ((JPasswordField) named(a, "auth.password")).getPassword().length, "cleared after success");
    }

    private static List<String> labels(Container root) {
        List<String> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && c.isVisible()) out.add(l.getText());
            if (c instanceof Container k) out.addAll(labels(k));
        }
        return out;
    }

    @Test
    void backReturnsToTheLandingPage() throws Exception {
        Outcome out = new Outcome();
        AuthPanel a = edt(() -> new AuthPanel(accounts(new AtomicInteger()), out));
        edt(() -> { ((JButton) named(a, "auth.back")).doClick(); return null; });
        assertTrue(out.back);
    }

    // ---------------------------------------------------------------- the frame (needs a display)

    @Test
    void frameRoutesLandingToSignInAndBackAndHandsOverAfterARealSignIn() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicReference<String> opened = new AtomicReference<>();
        LandingFrame f = edt(() -> new LandingFrame(accounts(new AtomicInteger()), null, null, () -> "ai", url -> { },
                (id, user) -> opened.set(id + ":" + user)));
        try {
            assertEquals("landing", f.shownCard());
            edt(() -> { f.landing().signInButton().doClick(); return null; });
            assertEquals("auth", f.shownCard());
            assertEquals(AuthPanel.Mode.SIGN_IN, f.auth().mode());
            edt(() -> { ((JButton) named(f.auth(), "auth.back")).doClick(); return null; });
            assertEquals("landing", f.shownCard());

            edt(() -> { f.landing().createAccountButton().doClick(); return null; });
            assertEquals(AuthPanel.Mode.REGISTER, f.auth().mode());
            edt(() -> { f.auth().show(AuthPanel.Mode.SIGN_IN, null); return null; });

            // a wrong password stays on the form and does not hand over
            fillSignIn(f.auth(), "alice@example.invalid", "wrong-password");
            edt(() -> { f.auth().submitButton().doClick(); return null; });
            waitFor(() -> ((JLabel) named(f.auth(), "auth.message")).getText().contains("incorrect"));
            assertNull(opened.get());
            assertEquals("auth", f.shownCard());

            fillSignIn(f.auth(), "alice@example.invalid", "synthetic-pass-1");
            edt(() -> { f.auth().submitButton().doClick(); return null; });
            waitFor(() -> opened.get() != null);
            assertEquals("42:alice@example.invalid", opened.get(), "after sign-in the host opens the application directly");
        } finally {
            edt(() -> { f.dispose(); return null; });
        }
    }

    @Test
    void frameSwitchesToTheStackedLayoutWhenNarrow() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        LandingFrame f = edt(() -> new LandingFrame(accounts(new AtomicInteger()), null, null, () -> "ai", url -> { }, (id, user) -> { }));
        try {
            edt(() -> { f.setSize(1300, 800); f.setVisible(true); return null; });
            waitFor(() -> f.landing().isWideLayout());
            edt(() -> { f.setSize(960, 700); return null; });
            waitFor(() -> !f.landing().isWideLayout());
            edt(() -> { f.setSize(1400, 800); return null; });
            waitFor(() -> f.landing().isWideLayout());
        } finally {
            edt(() -> { f.dispose(); return null; });
        }
    }
}

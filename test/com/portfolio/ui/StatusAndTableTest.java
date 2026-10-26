package com.portfolio.ui;

import com.portfolio.database.DatabaseManager;
import com.portfolio.demo.DemoPriceService;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.StockPriceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StatusAndTableTest {
    @TempDir Path tmp;
    private static final Color GAIN = Color.GREEN;
    private static final Color LOSS = Color.RED;
    private static final Color NEUTRAL = Color.WHITE;

    @BeforeEach
    void db() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("status.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    // ------------------------------------------------------------------ colour rule and sorting

    @Test
    void onlyAnExplicitlySignedFigureIsAGainOrALoss() {
        assertEquals(GAIN, TableSorting.semanticColor("+₹3,362.50", GAIN, LOSS, NEUTRAL));
        assertEquals(GAIN, TableSorting.semanticColor("+1.12%", GAIN, LOSS, NEUTRAL));
        assertEquals(LOSS, TableSorting.semanticColor("-1.34%", GAIN, LOSS, NEUTRAL));
        assertEquals(LOSS, TableSorting.semanticColor("₹-691.20", GAIN, LOSS, NEUTRAL));
        assertEquals(NEUTRAL, TableSorting.semanticColor("₹2,914.50", GAIN, LOSS, NEUTRAL), "a price is not a gain");
        assertEquals(NEUTRAL, TableSorting.semanticColor("₹3,67,241.30", GAIN, LOSS, NEUTRAL), "a total is not a gain");
        assertEquals(NEUTRAL, TableSorting.semanticColor("25 Oct 2026, 20:26", GAIN, LOSS, NEUTRAL));
        assertEquals(NEUTRAL, TableSorting.semanticColor("2026-10-06T03:24:56", GAIN, LOSS, NEUTRAL), "a date is never a loss");
        assertEquals(NEUTRAL, TableSorting.semanticColor("Not provided", GAIN, LOSS, NEUTRAL));
        assertEquals(NEUTRAL, TableSorting.semanticColor(null, GAIN, LOSS, NEUTRAL));
    }

    @Test
    void sortingReadsTheNumberOrDateInsideTheText() {
        assertTrue(TableSorting.compare("₹999.00", "₹1,000.00") < 0, "999 sorts before 1,000 although '9' > '1'");
        assertTrue(TableSorting.compare("₹-691.20", "₹100.00") < 0);
        assertTrue(TableSorting.compare("+0.84%", "-1.34%") > 0);
        assertTrue(TableSorting.compare("₹3,67,241.30", "₹92,000.00") > 0, "Indian digit grouping is understood");
        assertTrue(TableSorting.compare("07 Aug 2026, 10:00", "16 Sep 2026, 10:00") < 0);
        assertTrue(TableSorting.compare("31 Dec 2025, 10:00", "01 Jan 2026, 09:00") < 0, "dates are ordered as dates, not by day number");
        assertTrue(TableSorting.compare("RELIANCE", "TCS") < 0);
        assertTrue(TableSorting.compare(null, "x") < 0);
        assertEquals(0, TableSorting.compare(null, null));
        assertTrue(Double.isNaN(TableSorting.key("Not provided")));
        assertEquals(12.5, TableSorting.key(12.5), 0);
    }

    // ------------------------------------------------------------------ status chip and chart provenance

    @Test
    void statusTextsAreHonestAndSimulationIsNeverCalledLive() {
        assertEquals("Demo data · simulated", DataStatus.DEMO.text("simulated"));
        assertEquals("Cached · as of 14:02", DataStatus.CACHED.text("as of 14:02"));
        assertEquals("Unavailable", DataStatus.UNAVAILABLE.text(null));
        assertTrue(DataStatus.DEMO.simulated());
        for (DataStatus s : DataStatus.values()) {
            if (s.simulated()) {
                assertFalse(s.text("x").toLowerCase().contains("live"), s.text("x"));
            }
        }
    }

    @Test
    void theChartPanelShowsItsProvenanceAndClearsItWhileLoading() {
        HistoryChartPanel panel = new HistoryChartPanel();
        assertFalse(panel.chip().isVisible());
        long[] t = { 0, 86_400_000L, 2 * 86_400_000L };
        panel.showSeries(List.of(new TimeSeriesChart.Series("p", Color.GREEN, t, new double[] { 1, 2, 3 }, false, true)), true,
                DataStatus.CACHED, "as of 14:02", "stored");
        assertTrue(panel.chip().isVisible());
        assertEquals(DataStatus.CACHED, panel.chip().status());
        assertEquals("Cached · as of 14:02", panel.chip().text());
        panel.showLoading("history");
        assertFalse(panel.chip().isVisible(), "no provenance claim while loading");
        panel.showUnavailable("quota used");
        assertEquals(DataStatus.UNAVAILABLE, panel.chip().status());
        assertFalse(panel.chart().hasChart());
    }

    // ------------------------------------------------------------------ the dashboard: demo/live separation and responsiveness

    private static void collect(Container root, List<Component> out) {
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container k) {
                collect(k, out);
            }
        }
    }

    private static List<Component> all(PremiumStockDashboard d) {
        List<Component> out = new ArrayList<>();
        collect(d.getContentPane(), out);
        return out;
    }

    /** A provider that never answers a quote and, if gated, blocks on history until released. */
    private static final class Silent implements StockPriceService {
        volatile CountDownLatch gate;

        @Override public Stock getStock(String symbol) { return null; }

        @Override public boolean isValidStock(String symbol) { return true; }

        @Override public String getHistoricalData(String symbol) {
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new com.portfolio.service.PriceUnavailableException(com.portfolio.service.PriceUnavailableException.Reason.NETWORK, "offline");
        }
    }

    @Test
    void demoDataCarriesANeutralChipNotAYellowBannerAndLiveDataCarriesNone() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        PremiumStockDashboard[] demo = new PremiumStockDashboard[1];
        SwingUtilities.invokeAndWait(() -> demo[0] = new PremiumStockDashboard(new PortfolioService(new DemoPriceService())));
        try {
            List<Component> parts = all(demo[0]);
            assertTrue(parts.stream().anyMatch(c -> c instanceof StatusChip chip && chip.status() == DataStatus.DEMO),
                    "simulated data is marked by a neutral status chip");
            for (Component c : parts) {
                if (c instanceof JLabel l && l.getText() != null && l.getText().equals("DEMO DATA")) {
                    assertNotEquals(new Color(251, 191, 36), l.getForeground(), "no yellow DEMO DATA box");
                }
                if (c instanceof JLabel l && l.getText() != null) {
                    assertFalse(l.getText().contains("Market Open"), "no invented market status");
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> demo[0].dispose());
        }
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("live.db").toString());
        PremiumStockDashboard[] live = new PremiumStockDashboard[1];
        SwingUtilities.invokeAndWait(() -> live[0] = new PremiumStockDashboard(new PortfolioService(new Silent())));
        try {
            assertTrue(all(live[0]).stream().noneMatch(c -> c instanceof StatusChip chip && chip.status() == DataStatus.DEMO),
                    "a real provider is never labelled as demo data");
        } finally {
            SwingUtilities.invokeAndWait(() -> live[0].dispose());
        }
    }

    @Test
    void aProviderThatHangsDoesNotFreezeTheWindowAndFailureNeverDrawsAMarketValueLine() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Silent provider = new Silent();
        provider.gate = new CountDownLatch(1);
        // the trade is five days old, so the ledger alone makes a chart
        PortfolioService seeding = new PortfolioService(provider, java.time.Clock.offset(java.time.Clock.systemDefaultZone(),
                java.time.Duration.ofDays(-5)), new com.portfolio.service.CurrencyService());
        new com.portfolio.service.WalletService().addFunds(100_000, "UPI");
        seeding.buyStock("AAA", "AAA Ltd", 2, 100.0);
        PortfolioService portfolio = new PortfolioService(provider);
        PremiumStockDashboard[] d = new PremiumStockDashboard[1];
        long t0 = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> d[0] = new PremiumStockDashboard(portfolio));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        try {
            assertTrue(ms < 4_000, "building the dashboard must not wait for the provider (" + ms + " ms)");
            HistoryChartPanel perf = (HistoryChartPanel) all(d[0]).stream()
                    .filter(c -> c instanceof HistoryChartPanel h
                            && "Portfolio performance chart".equals(h.chart().getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertFalse(perf.chart().hasChart(), "nothing is drawn while the provider has not answered");
            provider.gate.countDown(); // now the provider fails
            long end2 = System.currentTimeMillis() + 10_000;
            while (!(perf.chip().isVisible() && perf.chip().status() == DataStatus.LOCAL) && System.currentTimeMillis() < end2) {
                Thread.sleep(100);
            }
            assertEquals(DataStatus.LOCAL, perf.chip().status(), "with no provider data only your own records are shown");
            assertTrue(perf.chip().text().contains("invested capital only"), perf.chip().text());
            assertEquals(List.of("Invested capital"), perf.chart().seriesNames(), "no market-value line is drawn without real prices");
        } finally {
            provider.gate.countDown();
            SwingUtilities.invokeAndWait(() -> d[0].dispose());
        }
    }
}

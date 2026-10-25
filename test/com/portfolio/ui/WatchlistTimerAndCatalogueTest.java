package com.portfolio.ui;

import com.portfolio.database.DatabaseManager;
import com.portfolio.demo.DemoPriceService;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioHistory;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.SymbolResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WatchlistTimerAndCatalogueTest {
    @TempDir Path tmp;

    @BeforeEach
    void db() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("timer.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    // ------------------------------------------------------------------ F1: one managed timer per window

    @Test
    void startingAManagedTimerTwiceCreatesOneScheduleAndStopIsFinal() {
        int before = ManagedTimer.running();
        ManagedTimer t = new ManagedTimer(60_000, () -> { });
        t.start();
        t.start();
        assertEquals(before + 1, ManagedTimer.running());
        assertTrue(t.isRunning());
        t.stop();
        t.stop();
        assertEquals(before, ManagedTimer.running());
        assertFalse(t.isRunning());
    }

    @Test
    void rebuildingEveryPageRepeatedlyKeepsExactlyOneWatchlistTimerAndTheWindowCloseStopsIt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        int before = ManagedTimer.running();
        PremiumStockDashboard[] d = new PremiumStockDashboard[1];
        SwingUtilities.invokeAndWait(() -> d[0] = new PremiumStockDashboard(new PortfolioService(new DemoPriceService())));
        try {
            Method refresh = PremiumStockDashboard.class.getDeclaredMethod("refreshAllViews");
            refresh.setAccessible(true);
            for (int i = 0; i < 6; i++) {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        refresh.invoke(d[0]);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            assertEquals(before + 1, ManagedTimer.running(), "six rebuilds, still one watchlist timer");
        } finally {
            SwingUtilities.invokeAndWait(() -> d[0].dispose());
        }
        long end = System.currentTimeMillis() + 3000; // the window-closed event is delivered asynchronously
        while (ManagedTimer.running() != before && System.currentTimeMillis() < end) {
            Thread.sleep(25);
        }
        assertEquals(before, ManagedTimer.running(), "closing the window stops its timer");
    }

    // ------------------------------------------------------------------ F3: currency guard

    @Test
    void historyIsChartedOnlyWhenEveryHoldingIsInTheChartCurrency() {
        assertNull(PortfolioHistory.currencyProblem(List.of("INR", "inr"), "INR"));
        assertNull(PortfolioHistory.currencyProblem(List.of(), "INR"), "nothing held, nothing to mislead");
        String mixed = PortfolioHistory.currencyProblem(List.of("INR", "USD"), "INR");
        assertNotNull(mixed);
        assertTrue(mixed.contains("INR") && mixed.contains("USD") && mixed.contains("exchange rates"), mixed);
        String foreign = PortfolioHistory.currencyProblem(List.of("USD"), "INR");
        assertNotNull(foreign);
        assertTrue(foreign.contains("USD") && foreign.contains("INR"), foreign);
        assertNotNull(PortfolioHistory.currencyProblem(java.util.Arrays.asList("INR", null), "INR"), "an unknown currency is not assumed");
    }

    // ------------------------------------------------------------------ F5: catalogue is exchange-aware and holds no prices

    @Test
    void theDemoProviderAnswersForStoredExchangeSuffixes() {
        DemoPriceService demo = new DemoPriceService();
        Stock q = demo.getStock("RELIANCE.NS");
        assertNotNull(q);
        assertEquals("RELIANCE.NS", q.getSymbol());
        assertEquals(2914.50, q.getCurrentPrice(), 0.0001);
        assertNull(demo.getStock("NOSUCH.NS"));
        assertNull(demo.getStock("not a symbol"), "malformed input is 'unknown', not an exception");
    }

    @Test
    void everyCatalogueEntryHasAReadableSymbolAndTheIndianOnesCarryTheirExchange() {
        int indian = 0;
        for (Object[] row : com.portfolio.data.StockDatabase.getAllStocks()) {
            SymbolResolver.Instrument i = SymbolResolver.parse((String) row[0]);
            if (i.exchange() != SymbolResolver.Exchange.UNSPECIFIED) {
                indian++;
            }
        }
        assertTrue(indian >= 10, "the Indian listings are tagged with an exchange: " + indian);
    }

    private static java.awt.Component find(java.awt.Container root, java.util.function.Predicate<java.awt.Component> test) {
        for (java.awt.Component c : root.getComponents()) {
            if (test.test(c)) {
                return c;
            }
            if (c instanceof java.awt.Container k) {
                java.awt.Component f = find(k, test);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    @Test
    void theMarketPageListsIndianInstrumentsWithoutPricesUntilQuotesAreLoadedFromTheProvider() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        PremiumStockDashboard[] d = new PremiumStockDashboard[1];
        SwingUtilities.invokeAndWait(() -> d[0] = new PremiumStockDashboard(new PortfolioService(new DemoPriceService())));
        try {
            Method navigate = PremiumStockDashboard.class.getDeclaredMethod("navigate", String.class);
            navigate.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    navigate.invoke(d[0], "Market");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            // default view for a rupee portfolio: India, with no price anywhere
            javax.swing.JLabel status = (javax.swing.JLabel) find(d[0].getContentPane(),
                    c -> c instanceof javax.swing.JLabel l && l.getText() != null && l.getText().startsWith("Showing "));
            assertNotNull(status, "the Market page shows how many instruments match");
            assertTrue(status.getText().contains("Prices appear only for your holdings"), status.getText());
            javax.swing.JButton load = (javax.swing.JButton) find(d[0].getContentPane(),
                    c -> c instanceof javax.swing.JButton b && "Load quotes".equals(b.getText()));
            assertNotNull(load);
            SwingUtilities.invokeAndWait(load::doClick);
            long end = System.currentTimeMillis() + 8000;
            String text = "";
            while (System.currentTimeMillis() < end) {
                Thread.sleep(100);
                javax.swing.JLabel now = (javax.swing.JLabel) find(d[0].getContentPane(),
                        c -> c instanceof javax.swing.JLabel l && l.getText() != null && l.getText().startsWith("Showing ") && l.getText().contains("Loaded"));
                if (now != null) {
                    text = now.getText();
                    break;
                }
            }
            assertTrue(text.contains("Loaded 8 of 12 quotes") && text.contains("DEMO DATA (simulated)") && text.contains("unavailable"), text);
        } finally {
            SwingUtilities.invokeAndWait(() -> d[0].dispose());
        }
    }
}

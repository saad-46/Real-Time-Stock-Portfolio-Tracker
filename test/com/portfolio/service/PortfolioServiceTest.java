package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioServiceTest {
    /** Deterministic stub: price per symbol; missing symbol -> unavailable. */
    private static StockPriceService stub(Map<String, Double> prices) {
        return new StockPriceService() {
            public Stock getStock(String s) {
                Double p = prices.get(s);
                if (p == null) throw new PriceUnavailableException("down");
                Stock st = new Stock(s, s, p);
                st.setCurrency("USD");
                return st;
            }
            public boolean isValidStock(String s) { return getStock(s) != null; }
        };
    }

    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("test.db").toString());
        com.portfolio.TestWallet.fund();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    @Test
    void totalsAreExactForBinaryUnfriendlyPrices() {
        PortfolioService svc = new PortfolioService(stub(Map.of()));
        svc.buyStock(new PortfolioItem("A", "A", 3, 0.10, 0.10));
        svc.buyStock(new PortfolioItem("B", "B", 7, 19.99, 21.01));
        assertEquals(140.23, svc.getTotalInvestment(), 0.0);   // 0.30 + 139.93
        assertEquals(147.37, svc.getTotalPortfolioValue(), 0.0); // 0.30 + 147.07
        assertEquals(7.14, svc.getTotalProfitLoss(), 0.0);
        assertEquals(5.09, svc.getReturnPercentage(), 0.0);
    }

    @Test
    void emptyPortfolioReturnIsZero() {
        PortfolioService svc = new PortfolioService(stub(Map.of()));
        assertEquals(0.0, svc.getReturnPercentage(), 0.0);
    }

    @Test
    void rejectsInvalidBuysAndLeavesNoState() {
        PortfolioService svc = new PortfolioService(stub(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> svc.buyStock(new PortfolioItem("A", "A", 0, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> svc.buyStock(new PortfolioItem("A", "A", -2, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> svc.buyStock(new PortfolioItem("A", "A", 1, -1.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> svc.buyStock(new PortfolioItem("A", "A", 1, Double.NaN, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> svc.buyStock(new PortfolioItem(" ", "A", 1, 1.0, 1.0)));
        assertTrue(svc.getAllPortfolioItems().isEmpty());
        assertTrue(svc.getAllTransactions().isEmpty());
    }

    @Test
    void buyPersistsHoldingAndTransactionAcrossRestart() {
        new PortfolioService(stub(Map.of())).buyStock(new PortfolioItem("AAPL", "AAPL", 10, 150.0, 150.0));
        DatabaseManager.closeConnection();
        PortfolioService reloaded = new PortfolioService(stub(Map.of()));
        assertEquals(1, reloaded.getAllPortfolioItems().size());
        assertEquals(1, reloaded.getAllTransactions().size());
        assertEquals("BUY", reloaded.getAllTransactions().get(0).getType());
    }

    @Test
    void databaseFailureDoesNotCreatePhantomHolding() throws Exception {
        PortfolioService svc = new PortfolioService(stub(Map.of()));
        DatabaseManager.getConnection().createStatement().execute("DROP TABLE transactions");
        assertThrows(IllegalStateException.class, () -> svc.buyStock(new PortfolioItem("A", "A", 1, 1.0, 1.0)));
        assertTrue(svc.getAllPortfolioItems().isEmpty());
        // rollback: the holding row must not have been committed either
        try (var rs = DatabaseManager.getConnection().createStatement().executeQuery("SELECT COUNT(*) FROM portfolio_items")) {
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void updateAllPricesReportsFailuresAndKeepsLastKnownPrice() {
        PortfolioService svc = new PortfolioService(stub(Map.of("A", 12.5)));
        svc.buyStock(new PortfolioItem("A", "A", 2, 10.0, 10.0));
        svc.buyStock(new PortfolioItem("B", "B", 2, 20.0, 20.0));
        assertEquals(1, svc.updateAllPrices());
        PortfolioItem a = svc.getAllPortfolioItems().stream().filter(i -> i.getSymbol().equals("A")).findFirst().get();
        PortfolioItem b = svc.getAllPortfolioItems().stream().filter(i -> i.getSymbol().equals("B")).findFirst().get();
        assertEquals(12.5, a.getCurrentPrice(), 0.0);
        assertEquals(20.0, b.getCurrentPrice(), 0.0); // untouched, not a fabricated price
    }
}

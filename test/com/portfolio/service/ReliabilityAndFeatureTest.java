package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReliabilityAndFeatureTest {
    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("r.db").toString());
        com.portfolio.TestWallet.fund();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static StockPriceService fixed(Map<String, Double> prices) {
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

    private static PortfolioItem buy(String s, int q, double p) {
        return new PortfolioItem(s, s, q, p, p);
    }

    // ---- database reliability ----

    @Test
    void failedLoadIsReportedNotShownAsEmptyPortfolio() throws Exception {
        PortfolioService ok = new PortfolioService(fixed(Map.of()));
        ok.buyStock(buy("A", 1, 1.0));
        DatabaseManager.getConnection().createStatement().execute("DROP TABLE portfolio_items");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new PortfolioService(fixed(Map.of())));
        assertTrue(e.getMessage().contains("Could not load"));
    }

    // ---- refresh concurrency / partial success / freshness ----

    @Test
    void overlappingRefreshIsRejectedAndServiceRecovers() throws Exception {
        CountDownLatch inFetch = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        StockPriceService slow = new StockPriceService() {
            public Stock getStock(String s) {
                inFetch.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                Stock st = new Stock(s, s, 50.0);
                st.setCurrency("USD");
                return st;
            }
            public boolean isValidStock(String s) { return true; }
        };
        PortfolioService svc = new PortfolioService(slow);
        svc.buyStock(buy("A", 1, 10.0));
        Thread t = new Thread(svc::updateAllPrices);
        t.start();
        assertTrue(inFetch.await(5, TimeUnit.SECONDS));
        assertTrue(svc.isRefreshInProgress());
        assertThrows(IllegalStateException.class, svc::updateAllPrices);
        release.countDown();
        t.join(5000);
        assertFalse(svc.isRefreshInProgress());
        assertEquals(0, svc.updateAllPrices()); // usable again
        assertEquals(50.0, svc.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
    }

    @Test
    void refreshStampsFreshnessOnlyForUpdatedHoldingsAndPersistsIt() {
        PortfolioService svc = new PortfolioService(fixed(Map.of("A", 12.0)));
        svc.buyStock(buy("A", 1, 10.0));
        svc.buyStock(buy("B", 1, 10.0));
        assertNull(svc.getAllPortfolioItems().get(0).getPriceUpdatedAt());
        assertEquals(1, svc.updateAllPrices());
        for (PortfolioItem i : svc.getAllPortfolioItems()) {
            if (i.getSymbol().equals("A")) assertNotNull(i.getPriceUpdatedAt());
            else assertNull(i.getPriceUpdatedAt());
        }
        DatabaseManager.closeConnection();
        PortfolioService reloaded = new PortfolioService(fixed(Map.of()));
        for (PortfolioItem i : reloaded.getAllPortfolioItems()) {
            if (i.getSymbol().equals("A")) {
                assertNotNull(i.getPriceUpdatedAt());
                assertEquals(12.0, i.getCurrentPrice(), 0.0);
            } else {
                assertNull(i.getPriceUpdatedAt());
            }
        }
    }

    @Test
    void tradesPreserveFreshnessTimestamp() {
        PortfolioService svc = new PortfolioService(fixed(Map.of("A", 12.0)));
        svc.buyStock(buy("A", 10, 10.0));
        svc.updateAllPrices();
        java.time.Instant stamp = svc.getAllPortfolioItems().get(0).getPriceUpdatedAt();
        svc.buyStock(buy("A", 5, 11.0));
        svc.sellStock("A", 3, 12.0);
        assertEquals(stamp, svc.getAllPortfolioItems().get(0).getPriceUpdatedAt());
        assertEquals(12.0, svc.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
    }

    // ---- ledger ----

    @Test
    void ledgerFiltersBySymbolTypeAndDate() {
        PortfolioService svc = new PortfolioService(fixed(Map.of()));
        svc.buyStock(buy("AAPL", 10, 10.0));
        svc.buyStock(buy("MSFT", 5, 20.0));
        svc.sellStock("AAPL", 4, 15.0);
        assertEquals(3, svc.searchTransactions(null, null, null, null).size());
        assertEquals(2, svc.searchTransactions("aap", null, null, null).size());
        assertEquals(1, svc.searchTransactions("", "SELL", null, null).size());
        assertEquals(2, svc.searchTransactions(null, "buy", null, null).size());
        LocalDate today = LocalDate.now();
        assertEquals(3, svc.searchTransactions(null, null, today, today).size());
        assertEquals(0, svc.searchTransactions(null, null, today.plusDays(1), null).size());
        assertEquals(0, svc.searchTransactions(null, null, null, today.minusDays(1)).size());
        assertEquals("SELL", svc.searchTransactions(null, null, null, null).get(0).getType()); // newest first
    }

    @Test
    void transactionTimeIsStableAcrossRestart() {
        PortfolioService svc = new PortfolioService(fixed(Map.of()));
        svc.buyStock(buy("A", 1, 1.0));
        java.time.LocalDateTime before = svc.getAllTransactions().get(0).getTimestamp().withNano(0);
        DatabaseManager.closeConnection();
        java.time.LocalDateTime after = new PortfolioService(fixed(Map.of())).getAllTransactions().get(0).getTimestamp();
        assertEquals(before, after);
    }

    // ---- CSV ----

    @Test
    void csvQuotesAndNeutralisesFormulaCells() {
        assertEquals("'=1+1", CsvExporter.text("=1+1"));
        assertEquals("'@cmd", CsvExporter.text("@cmd"));
        assertEquals("\"a,b\"", CsvExporter.text("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.text("say \"hi\""));
        assertEquals("", CsvExporter.text(null));
    }

    @Test
    void csvTransactionsHaveHeaderPlainNumbersAndEmptyRealizedForBuys() throws Exception {
        PortfolioService svc = new PortfolioService(fixed(Map.of()));
        svc.buyStock(buy("A", 10, 10.0));
        svc.sellStock("A", 4, 12.5);
        List<Transaction> all = svc.searchTransactions(null, null, null, null);
        String csv = CsvExporter.transactionsCsv(all);
        String[] lines = csv.split("\r\n");
        assertEquals("timestamp,symbol,type,quantity,price,realized_pl", lines[0]);
        assertTrue(lines[1].endsWith(",A,SELL,4,12.50,10.00"), lines[1]); // (12.5-10)*4
        assertTrue(lines[2].endsWith(",A,BUY,10,10.00,"), lines[2]);
        Path out = tmp.resolve("t.csv");
        CsvExporter.write(out, csv);
        assertEquals(csv, Files.readString(out));
    }

    @Test
    void csvTimestampsHaveSecondPrecisionLikeAfterRestart() {
        PortfolioService svc = new PortfolioService(fixed(Map.of()));
        svc.buyStock(buy("A", 1, 1.0));
        String row = CsvExporter.transactionsCsv(svc.getAllTransactions()).split("\r\n")[1];
        assertTrue(row.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2},A,BUY,1,1.00,"), row);
    }

    @Test
    void csvHoldingsExport() {
        PortfolioService svc = new PortfolioService(fixed(Map.of()));
        svc.buyStock(buy("A", 3, 10.0));
        String[] lines = CsvExporter.holdingsCsv(svc.getAllPortfolioItems()).split("\r\n");
        assertEquals("symbol,name,quantity,avg_cost,current_price,price_updated_at", lines[0]);
        assertEquals("A,A,3,10.0000,10.00,", lines[1]);
    }
}

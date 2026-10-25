package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for an intermittent failure: on Windows consecutive LocalDateTime.now() calls can be equal,
 * and unsaved transactions all have id 0, so the "newest first" ledger order fell back to insertion
 * order (oldest first). A frozen clock reproduces the tie deterministically.
 */
class LedgerOrderingTest {
    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("o.db").toString());
        com.portfolio.TestWallet.fund();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static PortfolioService frozen() {
        Clock c = Clock.fixed(Instant.parse("2025-03-01T10:00:00Z"), ZoneId.of("UTC"));
        return new PortfolioService(new AlphaVantageService(null), c);
    }

    @Test
    void identicalTimestampsStillListNewestFirst() {
        PortfolioService s = frozen();
        s.buyStock(new PortfolioItem("AAPL", "AAPL", 10, 10.0, 10.0));
        s.buyStock(new PortfolioItem("MSFT", "MSFT", 5, 20.0, 20.0));
        s.sellStock("AAPL", 4, 15.0);
        List<Transaction> l = s.searchTransactions(null, null, null, null);
        assertEquals(List.of("SELL", "BUY", "BUY"), l.stream().map(Transaction::getType).toList());
        assertEquals("MSFT", l.get(1).getSymbol());
    }

    @Test
    void identicalTimestampsKeepNewestFirstAfterRestart() {
        PortfolioService s = frozen();
        s.buyStock(new PortfolioItem("AAPL", "AAPL", 10, 10.0, 10.0));
        s.sellStock("AAPL", 4, 15.0);
        DatabaseManager.closeConnection();
        List<Transaction> l = frozen().searchTransactions(null, null, null, null);
        assertEquals(List.of("SELL", "BUY"), l.stream().map(Transaction::getType).toList());
    }

    /** LocalDateTime.toString() drops ":00" seconds; exports and the ledger must always show them. */
    @Test
    void timestampsKeepSecondsOnTheExactMinute() {
        PortfolioService s = frozen(); // 10:00:00 exactly
        s.buyStock(new PortfolioItem("AAPL", "AAPL", 1, 10.0, 10.0));
        String row = CsvExporter.transactionsCsv(s.getAllTransactions()).split("\r\n")[1];
        assertEquals("2025-03-01T10:00:00,AAPL,BUY,1,10.00,", row);
        assertEquals("2025-03-01 10:00:00", s.getAllTransactions().get(0).getFormattedTimestamp());
    }
}

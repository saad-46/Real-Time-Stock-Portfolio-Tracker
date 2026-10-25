package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {
    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("t.db").toString());
        com.portfolio.TestWallet.fund();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static PortfolioService svc() {
        return new PortfolioService(new AlphaVantageService(null));
    }

    private static PortfolioItem buy(String s, int q, double p) {
        return new PortfolioItem(s, s, q, p, p);
    }

    @Test
    void repeatedBuysUseWeightedAverageCost() {
        PortfolioService s = svc();
        s.buyStock(buy("A", 10, 10.0));
        s.buyStock(buy("A", 30, 20.0));
        assertEquals(1, s.getAllPortfolioItems().size());
        PortfolioItem a = s.getAllPortfolioItems().get(0);
        assertEquals(40, a.getQuantity());
        assertEquals(17.5, a.getPurchasePrice(), 0.0); // (100+600)/40
        assertEquals(2, s.getAllTransactions().size());
    }

    @Test
    void repeatedBuyKeepsExistingCurrentPrice() {
        PortfolioService s = svc();
        s.buyStock(new PortfolioItem("A", "A", 1, 10.0, 99.0));
        s.buyStock(new PortfolioItem("A", "A", 1, 20.0, 20.0));
        assertEquals(99.0, s.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
    }

    @Test
    void weightedAverageRoundsHalfUpToFourDecimals() {
        assertEquals(1.3334, CostBasis.weightedAverage(2, 1.0, 1, 2.0002), 0.0); // 4.0002/3 = 1.3334
        assertEquals(1.0001, CostBasis.weightedAverage(1, 1.0001, 1, 1.0001), 0.0);
        assertEquals(3.3333, CostBasis.weightedAverage(1, 1.0, 2, 4.5), 0.0); // 10/3
    }

    @Test
    void partialSellRealizesPlFromAverageCostAndKeepsAverage() {
        PortfolioService s = svc();
        s.buyStock(buy("A", 10, 10.0));
        s.buyStock(buy("A", 30, 20.0)); // avg 17.5
        BigDecimal r = s.sellStock("A", 10, 20.0);
        assertEquals(new BigDecimal("25.00"), r); // (20-17.5)*10
        PortfolioItem a = s.getAllPortfolioItems().get(0);
        assertEquals(30, a.getQuantity());
        assertEquals(17.5, a.getPurchasePrice(), 0.0);
        assertEquals(new BigDecimal("25.00"), s.getTotalRealizedProfitLoss());
        Transaction t = s.getAllTransactions().get(2);
        assertEquals("SELL", t.getType());
        assertEquals(25.0, t.getRealizedPl(), 0.0);
        assertNull(s.getAllTransactions().get(0).getRealizedPl());
    }

    @Test
    void fullSellRemovesHoldingAndLossIsNegative() {
        PortfolioService s = svc();
        s.buyStock(buy("A", 3, 10.0));
        assertEquals(new BigDecimal("-3.00"), s.sellStock("A", 3, 9.0));
        assertTrue(s.getAllPortfolioItems().isEmpty());
        assertEquals(0.0, s.getTotalInvestment(), 0.0);
        assertEquals(new BigDecimal("-3.00"), s.getTotalRealizedProfitLoss());
    }

    @Test
    void realizedPlRoundsHalfUpToCents() {
        assertEquals(new BigDecimal("0.01"), CostBasis.realizedPl(1.0, 1.005, 1));
        assertEquals(new BigDecimal("-0.01"), CostBasis.realizedPl(1.005, 1.0, 1));
    }

    @Test
    void invalidSellsAreRejectedWithoutChangingState() {
        PortfolioService s = svc();
        s.buyStock(buy("A", 5, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("A", 6, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("A", 0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("A", -1, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("A", 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("A", 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("ZZ", 1, 1.0));
        assertEquals(5, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, s.getAllTransactions().size());
        assertEquals(new BigDecimal("0.00"), s.getTotalRealizedProfitLoss());
    }

    @Test
    void sellPersistenceFailureRollsBackAndLeavesMemoryUnchanged() throws Exception {
        PortfolioService s = svc();
        s.buyStock(buy("A", 5, 10.0));
        DatabaseManager.getConnection().createStatement().execute("DROP TABLE transactions");
        assertThrows(IllegalStateException.class, () -> s.sellStock("A", 2, 12.0));
        assertEquals(5, s.getAllPortfolioItems().get(0).getQuantity());
        try (var rs = DatabaseManager.getConnection().createStatement()
                .executeQuery("SELECT quantity FROM portfolio_items WHERE symbol='A'")) {
            assertEquals(5, rs.getInt(1)); // the DELETE was rolled back
        }
    }

    @Test
    void buyAndSellSurviveRestart() {
        PortfolioService s = svc();
        s.buyStock(buy("A", 10, 10.0));
        s.buyStock(buy("A", 10, 20.0));
        s.sellStock("A", 5, 30.0); // avg 15 -> (30-15)*5 = 75.00
        DatabaseManager.closeConnection();
        PortfolioService r = svc();
        assertEquals(1, r.getAllPortfolioItems().size());
        assertEquals(15, r.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(15.0, r.getAllPortfolioItems().get(0).getPurchasePrice(), 0.0);
        assertEquals(3, r.getAllTransactions().size());
        assertEquals(new BigDecimal("75.00"), r.getTotalRealizedProfitLoss());
    }

    @Test
    void emptyDatabaseGetsRealizedPlColumn() throws Exception {
        try (var rs = DatabaseManager.getConnection().createStatement().executeQuery("PRAGMA table_info(transactions)")) {
            boolean found = false;
            while (rs.next()) {
                found |= "realized_pl".equals(rs.getString("name"));
            }
            assertTrue(found);
        }
    }

    @Test
    void legacyDatabaseIsMigratedAndDuplicateRowsMerged() throws Exception {
        // setUp funded the wallet (creating the modern schema); this test needs a pristine legacy file instead
        DatabaseManager.closeConnection();
        java.nio.file.Files.delete(tmp.resolve("t.db"));
        String url = "jdbc:sqlite:" + tmp.resolve("t.db");
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE portfolio_items (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, name TEXT NOT NULL, quantity INTEGER NOT NULL, purchase_price REAL NOT NULL, current_price REAL NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, type TEXT NOT NULL, quantity INTEGER NOT NULL, price REAL NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price) VALUES ('A','A',10,10.0,11.0)");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price) VALUES ('A','A',30,20.0,25.0)");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price) VALUES ('A','BUY',10,10.0)");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price) VALUES ('A','BUY',30,20.0)");
        }
        PortfolioService s = svc();
        com.portfolio.TestWallet.fund(); // the migrated database starts with an empty INR wallet; these holdings are USD
        assertEquals(1, s.getAllPortfolioItems().size());
        assertEquals(40, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(17.5, s.getAllPortfolioItems().get(0).getPurchasePrice(), 0.0);
        assertEquals(2, s.getAllTransactions().size());
        assertNull(s.getAllTransactions().get(0).getRealizedPl());
        // a trade consolidates the legacy rows
        s.sellStock("A", 40, 17.5);
        try (var rs = DatabaseManager.getConnection().createStatement().executeQuery("SELECT COUNT(*) FROM portfolio_items")) {
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void closeThenReopenConnectionWorks() {
        Connection c1 = DatabaseManager.getConnection();
        DatabaseManager.closeConnection();
        Connection c2 = DatabaseManager.getConnection();
        assertNotSame(c1, c2);
        assertNotNull(c2);
    }
}

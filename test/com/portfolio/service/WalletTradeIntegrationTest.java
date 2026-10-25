package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trades move money, shares and the ledger together or not at all. Synthetic temporary databases and fake price
 * providers only; no network, no real database.
 */
class WalletTradeIntegrationTest {
    @TempDir Path tmp;

    /** Fake provider: returns exactly what the test sets, or fails when told to. Never invents a price. */
    static final class FakePrices implements StockPriceService {
        final Map<String, Double> prices = new HashMap<>();
        RuntimeException failure;

        @Override
        public Stock getStock(String symbol) {
            if (failure != null) {
                throw failure;
            }
            Double p = prices.get(symbol);
            if (p == null) {
                return null;
            }
            Stock s = new Stock(symbol, symbol, p);
            s.setCurrency("USD");
            return s;
        }

        @Override
        public boolean isValidStock(String symbol) {
            return getStock(symbol) != null;
        }
    }

    FakePrices prices = new FakePrices();

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("w.db").toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private PortfolioService serviceWithFunds(double funds) {
        com.portfolio.TestWallet.fund(funds);
        return new PortfolioService(prices);
    }

    private static PortfolioItem item(String symbol, int qty, double price) {
        return new PortfolioItem(symbol, symbol, qty, price, price);
    }

    private static long count(String table) throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getLong(1);
        }
    }

    private static double balance() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT balance FROM wallet WHERE id = 1")) {
            return rs.getDouble(1);
        }
    }

    /** Nothing but the funding row may exist: no holding, no ledger row, no trade wallet row. */
    private static void assertNoTradeEffects(double expectedBalance) throws SQLException {
        assertEquals(expectedBalance, balance(), 0.0);
        assertEquals(0, count("portfolio_items"));
        assertEquals(0, count("transactions"));
        assertEquals(1, count("wallet_transactions"), "only the funding row");
    }

    @Test
    void buyDebitsWalletAndPersistsEverythingTogether() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 25.5));
        assertEquals(745.0, balance(), 0.0);
        assertEquals(745.0, s.getWalletService().getBalance(), 0.0);
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT type, amount, balance_after FROM wallet_transactions ORDER BY id DESC LIMIT 1")) {
            assertEquals("STOCK BUY", rs.getString("type"));
            assertEquals(255.0, rs.getDouble("amount"), 0.0);
            assertEquals(745.0, rs.getDouble("balance_after"), 0.0);
        }
        // restart: wallet, holding and ledger all come back
        DatabaseManager.closeConnection();
        PortfolioService again = new PortfolioService(prices);
        assertEquals(745.0, again.getWalletService().getBalance(), 0.0);
        assertEquals(10, again.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, again.getAllTransactions().size());
    }

    @Test
    void insufficientFundsChangesNothing() throws Exception {
        PortfolioService s = serviceWithFunds(100);
        InsufficientFundsException e = assertThrows(InsufficientFundsException.class, () -> s.buyStock(item("AAA", 10, 25.0)));
        assertEquals(250.0, e.getRequired(), 0.0);
        assertEquals(100.0, e.getAvailable(), 0.0);
        assertTrue(s.getAllPortfolioItems().isEmpty());
        assertTrue(s.getAllTransactions().isEmpty());
        assertEquals(100.0, s.getWalletService().getBalance(), 0.0);
        assertNoTradeEffects(100.0);
    }

    @Test
    void spendingExactlyTheBalanceIsAllowed() throws Exception {
        PortfolioService s = serviceWithFunds(255);
        s.buyStock(item("AAA", 10, 25.5));
        assertEquals(0.0, balance(), 0.0);
    }

    @Test
    void sellCreditsWalletAndRealizesPlFromWeightedAverageCost() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 10.0)); // -100
        s.buyStock(item("AAA", 10, 20.0)); // -200, average cost 15
        assertEquals(700.0, balance(), 0.0);
        BigDecimal realized = s.sellStock("AAA", 5, 30.0); // +150
        assertEquals(new BigDecimal("75.00"), realized);
        assertEquals(850.0, balance(), 0.0);
        assertEquals(850.0, s.getWalletService().getBalance(), 0.0);
        assertEquals(new BigDecimal("75.00"), s.getTotalRealizedProfitLoss());
        assertEquals(15, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(15.0, s.getAllPortfolioItems().get(0).getPurchasePrice(), 0.0);
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT type, amount, balance_after FROM wallet_transactions ORDER BY id DESC LIMIT 1")) {
            assertEquals("STOCK SELL", rs.getString("type"));
            assertEquals(150.0, rs.getDouble("amount"), 0.0);
            assertEquals(850.0, rs.getDouble("balance_after"), 0.0);
        }
    }

    @Test
    void sellingMoreThanHeldOrUnknownSymbolChangesNothing() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("AAA", 11, 10.0));
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("ZZZ", 1, 10.0));
        assertEquals(900.0, balance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, count("transactions"));
    }

    @Test
    void invalidQuantitiesAndPricesAreRejectedWithoutEffects() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        for (int qty : new int[] { 0, -1, Integer.MIN_VALUE }) {
            assertThrows(IllegalArgumentException.class, () -> s.buyStock(item("AAA", qty, 10.0)), "buy qty " + qty);
        }
        for (double price : new double[] { 0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> s.buyStock(item("AAA", 1, price)), "buy price " + price);
        }
        s.buyStock(item("BBB", 5, 10.0)); // one real holding to sell from
        double after = balance();
        for (int qty : new int[] { 0, -3 }) {
            assertThrows(IllegalArgumentException.class, () -> s.sellStock("BBB", qty, 10.0), "sell qty " + qty);
        }
        for (double price : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> s.sellStock("BBB", 1, price), "sell price " + price);
        }
        assertThrows(IllegalArgumentException.class, () -> s.buyStock((PortfolioItem) null));
        assertThrows(IllegalArgumentException.class, () -> s.buyStock(item(" ", 1, 1.0)));
        assertEquals(after, balance(), 0.0);
        assertEquals(1, count("portfolio_items"));
        assertEquals(1, count("transactions")); // only the one valid buy
    }

    @Test
    void databaseFailureDuringBuyRollsBackWalletHoldingAndLedger() throws Exception {
        PortfolioService s = serviceWithFunds(500);
        DatabaseManager.getConnection().createStatement().execute(
                "CREATE TRIGGER boom BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
        assertThrows(IllegalStateException.class, () -> s.buyStock(item("AAA", 1, 100.0)));
        assertTrue(s.getAllPortfolioItems().isEmpty());
        assertTrue(s.getAllTransactions().isEmpty());
        assertEquals(500.0, s.getWalletService().getBalance(), 0.0, "cached wallet must match the database");
        assertNoTradeEffects(500.0);
        assertEquals(1, count("wallet_transactions"));
    }

    @Test
    void databaseFailureDuringSellRollsBackWalletHoldingAndLedger() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 10.0)); // balance 900
        DatabaseManager.getConnection().createStatement().execute(
                "CREATE TRIGGER boom BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
        assertThrows(IllegalStateException.class, () -> s.sellStock("AAA", 4, 50.0));
        assertEquals(900.0, balance(), 0.0);
        assertEquals(900.0, s.getWalletService().getBalance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, count("transactions"));
        assertEquals(2, count("wallet_transactions")); // funding + the buy; no sell row
        assertEquals(new BigDecimal("0.00"), s.getTotalRealizedProfitLoss());
    }

    @Test
    void sellWithoutAValidLiveQuoteIsAbortedAndNothingChanges() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 10.0));
        double before = balance();

        prices.failure = new PriceUnavailableException(PriceUnavailableException.Reason.NO_KEY, "no key");
        assertFalse(s.sellStock("AAA", 5));
        assertNotNull(s.getLastTradeProblem());
        prices.failure = null;

        assertFalse(s.sellStock("AAA", 5), "provider has no quote (null)");
        for (double bad : new double[] { 0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            prices.prices.put("AAA", bad);
            assertFalse(s.sellStock("AAA", 5), "invalid quote " + bad);
        }
        assertEquals(before, balance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, count("transactions"));
        assertThrows(PriceUnavailableException.class, () -> s.fetchValidatedQuote("AAA"));
    }

    @Test
    void sellWithAValidQuoteExecutesAtExactlyThatPrice() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 10, 10.0)); // balance 900
        prices.prices.put("AAA", 12.25);
        assertEquals(12.25, s.fetchValidatedQuote("AAA").getPrice(), 0.0);
        assertTrue(s.sellStock("AAA", 4));
        assertNull(s.getLastTradeProblem());
        assertEquals(12.25, s.getAllTransactions().get(1).getPrice(), 0.0);
        assertEquals(949.0, balance(), 0.0); // 900 + 4 * 12.25
        assertEquals(new BigDecimal("9.00"), s.getTotalRealizedProfitLoss()); // (12.25 - 10) * 4
    }

    @Test
    void sellAllUsesAQuoteAndReportsFailure() throws Exception {
        PortfolioService s = serviceWithFunds(1000);
        s.buyStock(item("AAA", 3, 10.0));
        assertFalse(s.sellAllStock("AAA"), "no quote");
        assertFalse(s.sellAllStock("NOPE"), "no holding");
        prices.prices.put("AAA", 11.0);
        assertTrue(s.sellAllStock("AAA"));
        assertTrue(s.getAllPortfolioItems().isEmpty());
    }

    @Test
    void depositsAndWithdrawalsAreValidatedAndAtomic() throws Exception {
        PortfolioService s = serviceWithFunds(100);
        WalletService w = s.getWalletService();
        assertFalse(w.addFunds(0, "x"));
        assertFalse(w.addFunds(-5, "x"));
        assertFalse(w.addFunds(Double.NaN, "x"));
        assertFalse(w.withdrawFunds(100.01));
        assertFalse(w.withdrawFunds(-1));
        assertEquals(100.0, w.getBalance(), 0.0);
        assertTrue(w.addFunds(50, "test"));
        assertTrue(w.withdrawFunds(25));
        assertEquals(125.0, balance(), 0.0);
        assertEquals(50.0 + 100.0, w.getTotalDeposited(), 0.0);
        assertEquals(25.0, w.getTotalWithdrawn(), 0.0);
        assertEquals(3, count("wallet_transactions")); // funding, deposit, withdrawal; rejected ones leave no row
    }

    @Test
    void freshDatabaseHasEveryTableAndAnEmptyWallet() throws Exception {
        assertNotNull(DatabaseManager.getConnection());
        for (String t : new String[] { "portfolio_items", "transactions", "watchlist", "users", "email_settings", "wallet",
                "wallet_transactions" }) {
            try (Statement st = DatabaseManager.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + t + "'")) {
                assertTrue(rs.next(), "missing table " + t);
            }
        }
        assertEquals(0.0, balance(), 0.0);
    }

    @Test
    void connectionIsAvailableToTradeAfterReopen() throws Exception {
        PortfolioService s = serviceWithFunds(100);
        Connection first = DatabaseManager.getConnection();
        DatabaseManager.closeConnection();
        s.buyStock(item("AAA", 1, 10.0)); // DAO re-acquires the connection per call
        assertNotSame(first, DatabaseManager.getConnection());
        assertEquals(90.0, balance(), 0.0);
    }
}

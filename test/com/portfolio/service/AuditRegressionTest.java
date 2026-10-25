package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for defects confirmed by the full audit. Temporary databases and fakes only. */
class AuditRegressionTest {
    @TempDir Path tmp;
    Path db;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        db = tmp.resolve("audit.db");
        System.setProperty("portfolio.db.path", db.toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    private static double balance() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT balance FROM wallet WHERE id = 1")) {
            return rs.getDouble(1);
        }
    }

    // ------------------------------------------------------------------ wallet precision

    @Test
    void tenDepositsOfTenCentsThenAOneUnitWithdrawalWorksAndLeavesExactlyZero() throws Exception {
        WalletService w = new WalletService();
        for (int i = 0; i < 10; i++) {
            assertTrue(w.addFunds(0.10, "test"));
        }
        assertEquals(1.0, balance(), 0.0, "ten x 0.10 must be exactly 1.00, not 0.9999999999999999");
        assertTrue(w.withdrawFunds(1.0), "a withdrawal of exactly the balance must be accepted");
        assertEquals(0.0, balance(), 0.0);
        assertEquals(0.0, w.getTotalDeposited() - 1.0 - (w.getTotalWithdrawn() - 1.0), 0.0);
    }

    @Test
    void amountsAreRoundedToWholeCentsAndSubCentAmountsAreRejected() throws Exception {
        WalletService w = new WalletService();
        assertFalse(w.addFunds(0.004, "test"), "rounds to 0.00: not a deposit");
        assertTrue(w.addFunds(10.005, "test")); // half-up to 10.01
        assertEquals(10.01, balance(), 0.0);
        assertFalse(w.withdrawFunds(0.004));
        assertTrue(w.withdrawFunds(0.01));
        assertEquals(10.00, balance(), 0.0);
    }

    @Test
    void aWithdrawalCarriesTheDescriptionItWasGivenAndTheDefaultStaysABankWithdrawal() throws Exception {
        WalletService w = new WalletService();
        assertTrue(w.addFunds(100, "test"));
        assertTrue(w.withdrawFunds(10, "Tax set-aside (flat 15% estimate; not a tax payment)"));
        assertTrue(w.withdrawFunds(5));
        java.util.List<com.portfolio.model.WalletTransaction> h = w.getTransactions(); // newest first
        assertEquals("Withdrawal to bank account", h.get(0).getDescription());
        assertTrue(h.get(1).getDescription().startsWith("Tax set-aside"));
        assertEquals(85.0, balance(), 0.0);
        assertEquals(12.35, WalletService.cents(12.345), 0.0);
        assertEquals(0.0, WalletService.cents(Double.NaN), 0.0);
        assertEquals(0.0, WalletService.cents(Double.POSITIVE_INFINITY), 0.0);
    }

    // ------------------------------------------------------------------ database lock / corruption

    @Test
    void aBriefLockHeldByAnotherConnectionIsWaitedFor() throws Exception {
        WalletService w = new WalletService(); // creates the schema
        try (Connection other = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            other.createStatement().execute("BEGIN IMMEDIATE"); // holds the write lock
            Thread release = new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    other.createStatement().execute("COMMIT");
                } catch (Exception ignored) {
                }
            });
            release.start();
            assertTrue(w.addFunds(5, "test"), "the write must wait for the lock instead of failing at once");
            release.join();
        }
        assertEquals(5.0, balance(), 0.0);
    }

    @Test
    void aFileThatIsNotADatabaseIsReportedAndLeftUntouched() throws Exception {
        Files.write(db, "this is not a sqlite database, just text".repeat(50).getBytes(StandardCharsets.UTF_8));
        String before = sha(db);
        assertNull(DatabaseManager.getConnection(), "must refuse, not overwrite");
        DatabaseManager.closeConnection();
        assertEquals(before, sha(db), "the unreadable file is never modified");
        assertThrows(IllegalStateException.class, PortfolioServiceFactory::create);
    }

    /** Small indirection so the assertThrows above reads clearly. */
    static final class PortfolioServiceFactory {
        static PortfolioService create() {
            return new PortfolioService(new StockPriceService() {
                @Override public com.portfolio.model.Stock getStock(String s) { return null; }
                @Override public boolean isValidStock(String s) { return false; }
            });
        }
    }

    @Test
    void repeatedStartupsAreIdempotentAndNeverDuplicateTheWalletRow() throws Exception {
        for (int i = 0; i < 4; i++) {
            DatabaseManager.closeConnection();
            assertNotNull(DatabaseManager.getConnection());
        }
        try (Statement st = DatabaseManager.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM wallet")) {
            assertEquals(1, rs.getInt(1));
        }
    }

    // ------------------------------------------------------------------ exchange-rate failures must not stall callers

    @Test
    void aFailedRateLookupIsRememberedBrieflyInsteadOfRetriedOnEveryCall() {
        TradePolicyTest.MutableClock clock = new TradePolicyTest.MutableClock();
        AtomicInteger fetches = new AtomicInteger();
        CurrencyService cs = new CurrencyService((from, to) -> {
            fetches.incrementAndGet();
            throw new IOException("down");
        }, clock);
        for (int i = 0; i < 25; i++) {
            assertTrue(cs.convertChecked(10, "USD", "INR").approximate());
            assertThrows(TradeBlockedException.class, () -> cs.requireRate("USD", "INR"));
        }
        assertEquals(1, fetches.get(), "one attempt, then the failure is remembered (a table of rows must not make 50 slow calls)");
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
        cs.convertChecked(10, "USD", "INR");
        assertEquals(2, fetches.get(), "after the memory period the source is tried again");
    }

    @Test
    void aRecoveredSourceIsUsedAsSoonAsTheFailureMemoryExpires() {
        TradePolicyTest.MutableClock clock = new TradePolicyTest.MutableClock();
        boolean[] up = { false };
        CurrencyService cs = new CurrencyService((from, to) -> {
            if (!up[0]) throw new IOException("down");
            return new CurrencyService.Rate(from, to, 80.0, clock.instant(), "test");
        }, clock);
        assertTrue(cs.convertChecked(1, "USD", "INR").approximate());
        up[0] = true;
        assertTrue(cs.convertChecked(1, "USD", "INR").approximate(), "still within the failure memory");
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
        CurrencyService.Converted c = cs.convertChecked(1, "USD", "INR");
        assertFalse(c.approximate());
        assertEquals(80.0, c.value(), 0.0);
        assertEquals(Duration.ofSeconds(30), CurrencyService.FAILURE_MEMORY);
    }
}

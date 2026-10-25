package com.portfolio.database;

import com.portfolio.model.PortfolioItem;
import com.portfolio.service.AlphaVantageService;
import com.portfolio.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic legacy databases only; the real portfolio.db is never used. */
class MigrationSafetyTest {
    @TempDir Path tmp;
    Path db;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        db = tmp.resolve("legacy.db");
        System.setProperty("portfolio.db.path", db.toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    /** Exactly the schema the very first version of the app created. */
    private void createLegacyDb() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE portfolio_items (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, name TEXT NOT NULL, quantity INTEGER NOT NULL, purchase_price REAL NOT NULL, current_price REAL NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, type TEXT NOT NULL, quantity INTEGER NOT NULL, price REAL NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price,created_at) VALUES ('A','Alpha Inc',10,10.1,11.25,'2025-01-02 03:04:05')");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price,created_at) VALUES ('A','Alpha Inc',30,20.0,25.5,'2025-02-03 04:05:06')");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price,created_at) VALUES ('B','Beta, Ltd',5,100.0,90.0,'2025-03-04 05:06:07')");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price,timestamp) VALUES ('A','BUY',10,10.1,'2025-01-02 03:04:05')");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price,timestamp) VALUES ('A','BUY',30,20.0,'2025-02-03 04:05:06')");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price,timestamp) VALUES ('B','BUY',5,100.0,'2025-03-04 05:06:07')");
        }
    }

    private static List<String> rows(Path file, String sql, int cols) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    sb.append(rs.getString(i)).append('|');
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    private static final String ITEMS = "SELECT id,symbol,name,quantity,purchase_price,current_price,created_at FROM portfolio_items ORDER BY id";
    private static final String TXS = "SELECT id,symbol,type,quantity,price,timestamp FROM transactions ORDER BY id";

    private List<Path> backups() throws IOException {
        try (Stream<Path> s = Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().contains(".pre-migration-")).toList();
        }
    }

    private static boolean hasColumn(Path file, String table, String col) throws SQLException {
        for (String r : rows(file, "SELECT name FROM pragma_table_info('" + table + "')", 1)) {
            if (r.equals(col + "|")) return true;
        }
        return false;
    }

    @Test
    void migrationPreservesEveryLegacyValueAndAddsNullColumns() throws Exception {
        createLegacyDb();
        List<String> itemsBefore = rows(db, ITEMS, 7);
        List<String> txBefore = rows(db, TXS, 6);
        assertNotNull(DatabaseManager.getConnection());
        DatabaseManager.closeConnection();
        assertEquals(itemsBefore, rows(db, ITEMS, 7));
        assertEquals(txBefore, rows(db, TXS, 6));
        assertTrue(hasColumn(db, "portfolio_items", "price_updated_at"));
        assertTrue(hasColumn(db, "transactions", "realized_pl"));
        assertEquals(List.of("null|", "null|", "null|"), rows(db, "SELECT realized_pl FROM transactions ORDER BY id", 1));
        assertEquals(List.of("null|", "null|", "null|"), rows(db, "SELECT price_updated_at FROM portfolio_items ORDER BY id", 1));
    }

    @Test
    void backupIsCreatedFirstAndHoldsTheOriginalLegacyData() throws Exception {
        createLegacyDb();
        List<String> itemsBefore = rows(db, ITEMS, 7);
        DatabaseManager.getConnection();
        DatabaseManager.closeConnection();
        List<Path> bak = backups();
        assertEquals(1, bak.size());
        assertEquals(itemsBefore, rows(bak.get(0), ITEMS, 7));
        assertFalse(hasColumn(bak.get(0), "transactions", "realized_pl"), "backup must be the pre-migration schema");
    }

    @Test
    void repeatStartupIsIdempotentAndMakesNoFurtherBackup() throws Exception {
        createLegacyDb();
        DatabaseManager.getConnection();
        DatabaseManager.closeConnection();
        List<String> afterFirst = rows(db, ITEMS, 7);
        for (int i = 0; i < 3; i++) {
            assertNotNull(DatabaseManager.getConnection());
            DatabaseManager.closeConnection();
        }
        assertEquals(1, backups().size());
        assertEquals(afterFirst, rows(db, ITEMS, 7));
    }

    @Test
    void freshDatabaseNeedsNoBackup() throws Exception {
        assertNotNull(DatabaseManager.getConnection());
        DatabaseManager.closeConnection();
        assertTrue(backups().isEmpty());
    }

    @Test
    void failedMigrationRollsBackCompletelyAndKeepsDataAndBackup() throws Exception {
        createLegacyDb();
        // Make the SECOND ALTER fail: 'transactions' becomes a view, which cannot be altered.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            st.execute("ALTER TABLE transactions RENAME TO transactions_real");
            st.execute("CREATE VIEW transactions AS SELECT * FROM transactions_real");
        }
        List<String> itemsBefore = rows(db, ITEMS, 7);
        assertNull(DatabaseManager.getConnection(), "migration must fail");
        // first ALTER (portfolio_items) must have been rolled back, data untouched
        assertFalse(hasColumn(db, "portfolio_items", "price_updated_at"));
        assertEquals(itemsBefore, rows(db, ITEMS, 7));
        assertEquals(1, backups().size());
    }

    @Test
    void restoringTheBackupRecoversTheLegacyStateAndMigratesAgain() throws Exception {
        createLegacyDb();
        List<String> itemsBefore = rows(db, ITEMS, 7);
        PortfolioService s = new PortfolioService(new AlphaVantageService(null));
        com.portfolio.TestWallet.fund(); // the migrated database starts with an empty INR wallet; make it a funded USD wallet for these USD items
        s.buyStock(new PortfolioItem("C", "C", 1, 5.0, 5.0)); // user keeps working after migration
        DatabaseManager.closeConnection();
        Path bak = backups().get(0);

        Files.copy(bak, db, StandardCopyOption.REPLACE_EXISTING); // the documented restore step
        assertFalse(hasColumn(db, "transactions", "realized_pl"));
        PortfolioService again = new PortfolioService(new AlphaVantageService(null));
        assertEquals(2, again.getAllPortfolioItems().size()); // A merged + B; no C (made after the backup)
        PortfolioItem a = again.getAllPortfolioItems().stream().filter(i -> i.getSymbol().equals("A")).findFirst().orElseThrow();
        assertEquals(40, a.getQuantity());
        assertEquals(17.525, a.getPurchasePrice(), 1e-4); // (10*10.1 + 30*20.0) / 40
        DatabaseManager.closeConnection();
        assertEquals(itemsBefore, rows(db, ITEMS, 7)); // raw rows are exactly the legacy rows
    }

    @Test
    void serviceLoadsMigratedLegacyHistory() throws Exception {
        createLegacyDb();
        PortfolioService s = new PortfolioService(new AlphaVantageService(null));
        assertEquals(3, s.getAllTransactions().size());
        assertEquals(java.time.LocalDateTime.parse("2025-01-02T03:04:05"), s.getAllTransactions().get(0).getTimestamp());
        assertEquals(new java.math.BigDecimal("0.00"), s.getTotalRealizedProfitLoss());
        assertNull(s.getAllPortfolioItems().get(0).getPriceUpdatedAt());
    }
}

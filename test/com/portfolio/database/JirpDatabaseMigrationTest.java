package com.portfolio.database;

import com.portfolio.service.AlphaVantageService;
import com.portfolio.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A database written by the original jirp app (wallet, watchlist, users, email settings, currency/sector columns, but
 * none of stock_project's freshness/realized-P/L columns) is backed up, upgraded in place, and loses nothing.
 * The database here is synthetic; the real portfolio.db is never opened.
 */
class JirpDatabaseMigrationTest {
    @TempDir Path tmp;
    Path db;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        db = tmp.resolve("jirp.db");
        System.setProperty("portfolio.db.path", db.toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private void createJirpDb() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE portfolio_items (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, name TEXT NOT NULL, quantity INTEGER NOT NULL, purchase_price REAL NOT NULL, current_price REAL NOT NULL, original_currency TEXT DEFAULT 'USD', sector TEXT, market_cap TEXT, risk_level TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, user_id INTEGER DEFAULT 1)");
            st.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, type TEXT NOT NULL, quantity INTEGER NOT NULL, price REAL NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, user_id INTEGER DEFAULT 1)");
            st.execute("CREATE TABLE watchlist (symbol TEXT PRIMARY KEY, name TEXT, added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, user_id INTEGER DEFAULT 1)");
            st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, email TEXT, full_name TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, last_login TIMESTAMP)");
            st.execute("CREATE TABLE email_settings (id INTEGER PRIMARY KEY, email TEXT, smtp_host TEXT DEFAULT 'smtp.gmail.com', smtp_port INTEGER DEFAULT 587, app_password TEXT, daily_email_enabled INTEGER DEFAULT 0, send_time TEXT DEFAULT '09:00')");
            st.execute("CREATE TABLE wallet (id INTEGER PRIMARY KEY DEFAULT 1, balance REAL DEFAULT 0.0, total_deposited REAL DEFAULT 0.0, total_withdrawn REAL DEFAULT 0.0)");
            st.execute("CREATE TABLE wallet_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount REAL NOT NULL, balance_after REAL NOT NULL, description TEXT, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO wallet (id, balance, total_deposited, total_withdrawn) VALUES (1, 500.0, 600.0, 100.0)");
            st.execute("INSERT INTO wallet_transactions (type, amount, balance_after, description) VALUES ('DEPOSIT', 600.0, 600.0, 'seed')");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price,original_currency,sector,market_cap,risk_level) VALUES ('AAA','Alpha Co',10,10.0,12.0,'INR','IT & Tech','Large Cap','Low')");
            st.execute("INSERT INTO portfolio_items (symbol,name,quantity,purchase_price,current_price,original_currency,sector,market_cap,risk_level) VALUES ('AAA','Alpha Co',30,20.0,25.0,'INR','IT & Tech','Large Cap','Low')");
            st.execute("INSERT INTO transactions (symbol,type,quantity,price) VALUES ('AAA','BUY',10,10.0)");
            st.execute("INSERT INTO watchlist (symbol,name) VALUES ('WWW','Watch Co')");
            st.execute("INSERT INTO users (username,password_hash,email,full_name) VALUES ('tester','hash','t@example.com','T')");
            st.execute("INSERT INTO email_settings (id,email,smtp_host,smtp_port,app_password,daily_email_enabled,send_time) VALUES (1,'t@example.com','smtp.example.com',587,'synthetic',1,'08:30')");
        }
    }

    private List<Path> backups() throws IOException {
        try (Stream<Path> s = Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().contains(".pre-migration-")).toList();
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void jirpDatabaseIsBackedUpUpgradedAndKeepsAllItsData() throws Exception {
        createJirpDb();
        PortfolioService s = new PortfolioService(new AlphaVantageService(null));

        assertEquals(1, backups().size(), "exactly one pre-migration backup");
        // backup is the pre-migration state: no new columns in it
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + backups().get(0))) {
            assertFalse(hasColumn(c, "transactions", "realized_pl"));
            assertFalse(hasColumn(c, "portfolio_items", "price_updated_at"));
        }

        Connection live = DatabaseManager.getConnection();
        assertTrue(hasColumn(live, "transactions", "realized_pl"));
        assertTrue(hasColumn(live, "portfolio_items", "price_updated_at"));

        // wallet untouched by migration
        assertEquals(500.0, s.getWalletService().getBalance(), 0.0);
        assertEquals(600.0, s.getWalletService().getTotalDeposited(), 0.0);
        assertEquals(1, s.getWalletService().getTransactions().size());
        // duplicate legacy rows merge by weighted average: (10*10 + 30*20) / 40 = 17.5; metadata and currency kept
        assertEquals(1, s.getAllPortfolioItems().size());
        assertEquals(40, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(17.5, s.getAllPortfolioItems().get(0).getPurchasePrice(), 0.0);
        assertEquals("INR", s.getAllPortfolioItems().get(0).getOriginalCurrency());
        assertEquals("IT & Tech", s.getAllPortfolioItems().get(0).getStock().getSector());
        assertNull(s.getAllPortfolioItems().get(0).getPriceUpdatedAt(), "legacy price has no freshness stamp");
        assertEquals(1, s.getWatchlist().size());
        // users and email settings survive
        assertNotNull(DatabaseManager.getUserInfo(1));
        assertEquals("08:30", DatabaseManager.loadEmailSettings()[5]);
    }

    @Test
    void upgradedJirpDatabaseCanTradeAgainstItsOwnWallet() throws Exception {
        createJirpDb();
        PortfolioService s = new PortfolioService(new AlphaVantageService(null));
        assertEquals("INR", s.getWalletService().getCurrency(), "a migrated rupee wallet stays a rupee wallet");
        com.portfolio.model.PortfolioItem beta = new com.portfolio.model.PortfolioItem("BBB", "Beta", 5, 20.0, 20.0);
        beta.setOriginalCurrency("INR"); // like every holding in this database
        s.buyStock(beta); // costs 100 of the 500
        assertEquals(400.0, s.getWalletService().getBalance(), 0.0);
        com.portfolio.model.PortfolioItem gamma = new com.portfolio.model.PortfolioItem("CCC", "Gamma", 100, 20.0, 20.0);
        gamma.setOriginalCurrency("INR");
        assertThrows(com.portfolio.service.InsufficientFundsException.class, () -> s.buyStock(gamma));
        assertEquals(400.0, s.getWalletService().getBalance(), 0.0);
        assertEquals(2, s.getAllPortfolioItems().size());
    }

    @Test
    void migrationIsIdempotentAndBacksUpOnlyOnce() throws Exception {
        createJirpDb();
        new PortfolioService(new AlphaVantageService(null));
        DatabaseManager.closeConnection();
        new PortfolioService(new AlphaVantageService(null));
        assertEquals(1, backups().size(), "an up-to-date database is not backed up again");
    }

    @Test
    void failedMigrationLeavesTheDatabaseUntouched() throws Exception {
        createJirpDb();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            // a VIEW named like a table the migration must alter makes ALTER TABLE fail halfway through
            st.execute("DROP TABLE wallet_transactions");
            st.execute("CREATE VIEW wallet_transactions AS SELECT 1 AS id");
            st.execute("ALTER TABLE watchlist RENAME TO watchlist_old");
            st.execute("CREATE VIEW watchlist AS SELECT 'x' AS symbol");
        }
        assertNull(DatabaseManager.getConnection(), "migration must fail rather than half-apply");
        DatabaseManager.closeConnection();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            assertFalse(hasColumn(c, "transactions", "realized_pl"), "all-or-nothing: no partial column additions");
            assertFalse(hasColumn(c, "portfolio_items", "price_updated_at"));
        }
        assertFalse(backups().isEmpty(), "the backup was taken before the failed attempt");
    }
}

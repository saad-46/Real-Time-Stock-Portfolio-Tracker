package com.portfolio.database; // Database package for all database-related code

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DatabaseManager - Manages the SQLite connection and the database schema.
 *
 * SQLite is a lightweight database that stores everything in one file (default: portfolio.db in the working
 * directory; override with the system property portfolio.db.path).
 *
 * Schema migration is additive and idempotent, runs in a single transaction (all or nothing), and is preceded by a
 * consistent backup copy whenever an existing database is about to be upgraded. See docs/INTEGRATION_DESIGN.md.
 */
public class DatabaseManager {

    // Connection object - shared across the application
    private static Connection connection = null;

    /** Columns that must exist on the tables that older versions of either app created without them. */
    private static final Map<String, Map<String, String>> ADDED_COLUMNS = new LinkedHashMap<>();
    /** Tables that older databases may lack entirely. */
    private static final String[] LATER_TABLES = { "watchlist", "users", "email_settings", "wallet", "wallet_transactions" };

    static {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("price_updated_at", "INTEGER");
        items.put("original_currency", "TEXT DEFAULT 'USD'");
        items.put("sector", "TEXT");
        items.put("market_cap", "TEXT");
        items.put("risk_level", "TEXT");
        items.put("user_id", "INTEGER DEFAULT 1");
        ADDED_COLUMNS.put("portfolio_items", items);
        Map<String, String> tx = new LinkedHashMap<>();
        tx.put("realized_pl", "REAL");
        tx.put("user_id", "INTEGER DEFAULT 1");
        ADDED_COLUMNS.put("transactions", tx);
        Map<String, String> wl = new LinkedHashMap<>();
        wl.put("user_id", "INTEGER DEFAULT 1");
        ADDED_COLUMNS.put("watchlist", wl);
        Map<String, String> wallet = new LinkedHashMap<>();
        wallet.put("currency", "TEXT DEFAULT 'INR'"); // the wallet's one currency; existing wallets were rupee wallets
        ADDED_COLUMNS.put("wallet", wallet);
    }

    /**
     * Monitor guarding multi-statement database work on the shared connection. Anything that toggles
     * auto-commit (a trade) and every write that must not join someone else's open transaction takes this lock.
     */
    public static Object lock() {
        return DatabaseManager.class;
    }

    /**
     * Gets the shared connection, opening it (and migrating the schema) if needed.
     *
     * @return the connection, or null if the database could not be opened or migrated; a failed attempt leaves
     *         the file untouched and the next call retries
     */
    public static synchronized Connection getConnection() {
        if (connection == null) {
            try {
                // Load SQLite JDBC driver
                Class.forName("org.sqlite.JDBC");

                // Create connection to database file (SQLite creates it if it doesn't exist; the folder is made if needed)
                java.nio.file.Path parent = java.nio.file.Paths.get(dbPath()).toAbsolutePath().getParent();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent);
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath());
                System.out.println("✅ Database connected successfully!");

                // Initialize / migrate tables
                initializeTables();
            } catch (Exception e) {
                System.out.println("❌ Error connecting to database: " + e.getMessage());
                closeQuietly();
            }
        }
        return connection;
    }

    /** Like {@link #getConnection()} but throws instead of returning null (for code that already handles SQLException). */
    public static Connection requireConnection() throws SQLException {
        Connection c = getConnection();
        if (c == null) {
            throw new SQLException("Database unavailable");
        }
        return c;
    }

    /** See {@link com.portfolio.AppPaths}: property, STOCKVAULT_DB, an existing ./portfolio.db, else the per-user data folder. */
    private static String dbPath() {
        return com.portfolio.AppPaths.resolveDatabasePath();
    }

    /**
     * Creates every table if it doesn't exist and adds columns older databases lack.
     * All of it commits together or not at all.
     */
    private static void initializeTables() {
        try (Statement stmt = connection.createStatement()) {
            backupBeforeMigration(stmt);
            connection.setAutoCommit(false);
            try {
                stmt.execute("CREATE TABLE IF NOT EXISTS portfolio_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "name TEXT NOT NULL," +
                        "quantity INTEGER NOT NULL," +
                        "purchase_price REAL NOT NULL," +
                        "current_price REAL NOT NULL," +
                        "original_currency TEXT DEFAULT 'USD'," +
                        "sector TEXT," +
                        "market_cap TEXT," +
                        "risk_level TEXT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "symbol TEXT NOT NULL," +
                        "type TEXT NOT NULL," +
                        "quantity INTEGER NOT NULL," +
                        "price REAL NOT NULL," +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS watchlist (" +
                        "symbol TEXT PRIMARY KEY," +
                        "name TEXT," +
                        "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "username TEXT UNIQUE NOT NULL," +
                        "password_hash TEXT NOT NULL," +
                        "email TEXT," +
                        "full_name TEXT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "last_login TIMESTAMP" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS email_settings (" +
                        "id INTEGER PRIMARY KEY," +
                        "email TEXT," +
                        "smtp_host TEXT DEFAULT 'smtp.gmail.com'," +
                        "smtp_port INTEGER DEFAULT 587," +
                        "app_password TEXT," +
                        "daily_email_enabled INTEGER DEFAULT 0," +
                        "send_time TEXT DEFAULT '09:00'" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS wallet (" +
                        "id INTEGER PRIMARY KEY DEFAULT 1," +
                        "balance REAL DEFAULT 0.0," +
                        "total_deposited REAL DEFAULT 0.0," +
                        "total_withdrawn REAL DEFAULT 0.0," +
                        "currency TEXT DEFAULT 'INR'" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS wallet_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "type TEXT NOT NULL," + // DEPOSIT, WITHDRAW, STOCK BUY, STOCK SELL
                        "amount REAL NOT NULL," +
                        "balance_after REAL NOT NULL," +
                        "description TEXT," +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");

                PriceHistoryDAO.createTables(stmt); // additive: charts' stored daily closes

                for (Map.Entry<String, Map<String, String>> table : ADDED_COLUMNS.entrySet()) {
                    for (Map.Entry<String, String> col : table.getValue().entrySet()) {
                        addColumnIfMissing(stmt, table.getKey(), col.getKey(), col.getValue());
                    }
                }

                // The single wallet row; an existing wallet is never overwritten.
                stmt.execute("INSERT OR IGNORE INTO wallet (id, balance, total_deposited, total_withdrawn) VALUES (1, 0.0, 0.0, 0.0)");
                connection.commit();
                System.out.println("✅ Database schema ready");
            } catch (SQLException e) {
                // DDL is transactional in SQLite: a failed migration leaves the schema exactly as it was
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
            throw new IllegalStateException("Could not initialize database schema", e);
        }
    }

    /** Drop a half-initialised connection so the next call retries and callers never see a broken schema. */
    private static void closeQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // best effort
        }
        connection = null;
    }

    /**
     * Before changing an existing (older-format) database, write a consistent copy next to it
     * ("<file>.pre-migration-<timestamp>.bak", via SQLite VACUUM INTO). If the backup fails the
     * migration is aborted and the database is left untouched.
     */
    private static void backupBeforeMigration(Statement stmt) throws SQLException {
        if (!isExistingDatabaseBehind(stmt)) {
            return;
        }
        String target = dbPath() + ".pre-migration-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".bak";
        try (PreparedStatement backup = connection.prepareStatement("VACUUM INTO ?")) {
            backup.setString(1, target); // bound parameter: no string concatenation into SQL
            backup.execute();
        }
        System.out.println("Backed up the database before upgrading it: " + target);
    }

    /** True only for an existing database (portfolio/ledger data present) that lacks a table or column this version adds. */
    private static boolean isExistingDatabaseBehind(Statement stmt) throws SQLException {
        if (!tableExists(stmt, "portfolio_items") && !tableExists(stmt, "transactions")) {
            return false; // new, empty database: nothing to protect
        }
        for (String table : LATER_TABLES) {
            if (!tableExists(stmt, table)) {
                return true;
            }
        }
        for (Map.Entry<String, Map<String, String>> table : ADDED_COLUMNS.entrySet()) {
            if (!tableExists(stmt, table.getKey())) {
                continue; // created fresh with the full column set
            }
            for (String column : table.getValue().keySet()) {
                if (!hasColumn(stmt, table.getKey(), column)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tableExists(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            return rs.next();
        }
    }

    private static boolean hasColumn(Statement stmt, String table, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Additive, idempotent migration helper. */
    private static void addColumnIfMissing(Statement stmt, String table, String column, String type) throws SQLException {
        if (!hasColumn(stmt, table, column)) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    /**
     * Saves email settings to the database.
     */
    public static void saveEmailSettings(String email, String appPassword, String smtpHost,
            int smtpPort, boolean dailyEnabled, String sendTime) {
        synchronized (lock()) {
            try {
                Connection conn = requireConnection();
                conn.setAutoCommit(false);
                try (Statement delStmt = conn.createStatement();
                        PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO email_settings (id, email, smtp_host, smtp_port, app_password, daily_email_enabled, send_time) "
                                        + "VALUES (1, ?, ?, ?, ?, ?, ?)")) {
                    delStmt.execute("DELETE FROM email_settings");
                    pstmt.setString(1, email);
                    pstmt.setString(2, smtpHost);
                    pstmt.setInt(3, smtpPort);
                    pstmt.setString(4, appPassword);
                    pstmt.setInt(5, dailyEnabled ? 1 : 0);
                    pstmt.setString(6, sendTime);
                    pstmt.executeUpdate();
                    conn.commit(); // delete + insert together: never lose the old settings on a failed save
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                System.out.println("✅ Email settings saved");
            } catch (SQLException e) {
                System.err.println("❌ Error saving email settings: " + e.getMessage());
            }
        }
    }

    /**
     * Replaces only the stored value of the email password column (a credential reference, or null to clear it).
     * Returns false when there is no settings row or the update failed; nothing else in the row changes.
     */
    public static boolean updateEmailPasswordValue(String value) {
        synchronized (lock()) {
            try (PreparedStatement ps = requireConnection().prepareStatement("UPDATE email_settings SET app_password = ? WHERE id = 1")) {
                ps.setString(1, value);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                System.err.println("❌ Error updating email settings: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Loads email settings from the database.
     * Returns a String array: [email, appPassword, smtpHost, smtpPort, dailyEnabled, sendTime]
     * Returns null if no settings exist.
     */
    public static String[] loadEmailSettings() {
        try (Statement stmt = requireConnection().createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT email, app_password, smtp_host, smtp_port, daily_email_enabled, send_time FROM email_settings WHERE id = 1")) {
            if (rs.next()) {
                String[] settings = new String[6];
                settings[0] = rs.getString("email");
                settings[1] = rs.getString("app_password");
                settings[2] = rs.getString("smtp_host");
                settings[3] = String.valueOf(rs.getInt("smtp_port"));
                settings[4] = String.valueOf(rs.getInt("daily_email_enabled"));
                settings[5] = rs.getString("send_time");
                return settings;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error loading email settings: " + e.getMessage());
        }
        return null;
    }

    /**
     * Closes the database connection. Call this when shutting down the application.
     */
    public static synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error closing connection: " + e.getMessage());
        } finally {
            connection = null;
        }
    }

    /**
     * Clears portfolio holdings and the trade ledger (for testing purposes). Wallet balance and its history are
     * NOT touched, so this cannot silently destroy money records.
     * WARNING: This deletes every holding and transaction!
     */
    public static void clearAllData() {
        synchronized (lock()) {
            try (Statement stmt = requireConnection().createStatement()) {
                stmt.execute("DELETE FROM portfolio_items");
                stmt.execute("DELETE FROM transactions");
                System.out.println("✅ All holdings and transactions cleared from database");
            } catch (SQLException e) {
                System.err.println("❌ Error clearing data: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // USER AUTHENTICATION METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Register a new user
     *
     * @return user ID if successful, -1 if username already exists
     */
    public static int registerUser(String username, String password, String email, String fullName) {
        try {
            Connection conn = requireConnection();

            // Check if username already exists
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                checkStmt.setString(1, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return -1;
                    }
                }
            }

            // PBKDF2 with a random salt (see PasswordHasher)
            String passwordHash = PasswordHasher.hash(password);

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, email, full_name) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, username);
                pstmt.setString(2, passwordHash);
                pstmt.setString(3, email);
                pstmt.setString(4, fullName);
                pstmt.executeUpdate();

                int userId = -1;
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        userId = generatedKeys.getInt(1);
                    }
                }
                System.out.println("✅ User registered: " + username + " (ID: " + userId + ")");
                return userId;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error registering user: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Login user
     *
     * @return user ID if successful, -1 if login fails
     */
    public static int loginUser(String username, String password) {
        try {
            Connection conn = requireConnection();
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT id, password_hash FROM users WHERE username = ?")) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int userId = rs.getInt("id");
                        String storedHash = rs.getString("password_hash");
                        if (PasswordHasher.verify(password, storedHash)) {
                            if (PasswordHasher.needsUpgrade(storedHash)) {
                                upgradePasswordHash(userId, password); // old unsalted hash replaced after a correct password
                            }
                            updateLastLogin(userId);
                            System.out.println("✅ User logged in: " + username + " (ID: " + userId + ")");
                            return userId;
                        }
                    }
                }
            }
            System.out.println("❌ Login failed for: " + username);
            return -1;
        } catch (SQLException e) {
            System.err.println("❌ Error during login: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Update last login timestamp
     */
    private static void updateLastLogin(int userId) {
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
                "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?")) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating last login: " + e.getMessage());
        }
    }

    /**
     * Get user info
     *
     * @return String array: [username, email, fullName, createdAt, lastLogin]
     */
    public static String[] getUserInfo(int userId) {
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
                "SELECT username, email, full_name, created_at, last_login FROM users WHERE id = ?")) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String[] info = new String[5];
                    info[0] = rs.getString("username");
                    info[1] = rs.getString("email");
                    info[2] = rs.getString("full_name");
                    info[3] = rs.getString("created_at");
                    info[4] = rs.getString("last_login");
                    return info;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting user info: " + e.getMessage());
        }
        return null;
    }

    /** Replace a legacy hash with a PBKDF2 hash. Only called right after the correct password was verified. */
    private static void upgradePasswordHash(int userId, String password) {
        try (PreparedStatement pstmt = requireConnection().prepareStatement("UPDATE users SET password_hash = ? WHERE id = ?")) {
            pstmt.setString(1, PasswordHasher.hash(password));
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Could not upgrade the stored password hash: " + e.getMessage());
        }
    }
}

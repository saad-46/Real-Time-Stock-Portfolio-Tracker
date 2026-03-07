package com.portfolio.database; // Database package for all database-related code

import java.sql.*; // Import all SQL classes (Connection, Statement, ResultSet, etc.)

/**
 * DatabaseManager - Manages database connection and initialization
 * This class handles connecting to SQLite database and creating tables
 * 
 * JDBC (Java Database Connectivity) - Standard Java API for database access
 * Think of JDBC like a translator between Java and databases
 * 
 * We use SQLite - a lightweight database that stores data in a single file
 * No need to install MySQL or PostgreSQL - SQLite is built-in!
 */
public class DatabaseManager {

    // Database file path - where the database file will be stored
    // Example: "portfolio.db" creates a file in your project folder
    private static final String DB_URL = "jdbc:sqlite:portfolio.db";

    // Connection object - represents the connection to the database
    // Think of it like a phone line to the database
    private static Connection connection = null;

    /**
     * Gets a connection to the database
     * If connection doesn't exist, creates a new one
     * 
     * @return Connection object to interact with database
     * @throws SQLException if connection fails
     * 
     *                      Example: Connection conn =
     *                      DatabaseManager.getConnection();
     */
    public static Connection getConnection() throws SQLException {
        // If connection is null or closed, create new connection
        if (connection == null || connection.isClosed()) {
            try {
                // Load SQLite JDBC driver - tells Java how to talk to SQLite
                // This is like installing a language translator
                Class.forName("org.sqlite.JDBC");

                // Create connection to database
                // If database file doesn't exist, SQLite creates it automatically
                connection = DriverManager.getConnection(DB_URL);

                System.out.println("✅ Database connected successfully!");

                // Initialize database tables (create if they don't exist)
                initializeTables();

            } catch (ClassNotFoundException e) {
                // If SQLite driver not found, throw exception
                System.err.println("❌ SQLite JDBC driver not found!");
                throw new SQLException("SQLite driver not found", e);
            }
        }

        return connection; // Return the connection
    }

    /**
     * Initializes database tables
     * Creates tables if they don't exist
     * 
     * We create two tables:
     * 1. portfolio_items - stores your stock holdings
     * 2. transactions - stores buy/sell history
     */
    private static void initializeTables() {
        try {
            // Get connection to database
            Connection conn = getConnection();

            // Statement - used to execute SQL commands
            // Think of it like typing commands in a database terminal
            Statement stmt = conn.createStatement();

            // ===== CREATE PORTFOLIO_ITEMS TABLE =====
            // This table stores your current stock holdings
            String createPortfolioTable = "CREATE TABLE IF NOT EXISTS portfolio_items (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," + // Auto-incrementing ID (1, 2, 3...)
                    "    symbol TEXT NOT NULL," + // Stock symbol (ex: "AAPL")
                    "    name TEXT NOT NULL," + // Company name (ex: "Apple Inc.")
                    "    quantity INTEGER NOT NULL," + // Number of shares (ex: 10)
                    "    purchase_price REAL NOT NULL," + // Price you paid per share (ex: 150.00)
                    "    current_price REAL NOT NULL," + // Current price per share (ex: 278.12)
                    "    original_currency TEXT DEFAULT 'USD'," + // Store currency
                    "    sector TEXT," + // NEW: Sector (Healthcare, IT, etc.)
                    "    market_cap TEXT," + // NEW: Market Cap (Small, Mid, Large)
                    "    risk_level TEXT," + // NEW: Risk Level (Low, Med, High)
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" + // When you bought it
                    ")";

            // Execute the SQL command to create table
            stmt.execute(createPortfolioTable);

            // Ensure new columns exist if table was already created
            try {
                stmt.execute("ALTER TABLE portfolio_items ADD COLUMN original_currency TEXT DEFAULT 'USD'");
            } catch (SQLException e) {
            }
            try {
                stmt.execute("ALTER TABLE portfolio_items ADD COLUMN sector TEXT");
            } catch (SQLException e) {
            }
            try {
                stmt.execute("ALTER TABLE portfolio_items ADD COLUMN market_cap TEXT");
            } catch (SQLException e) {
            }
            try {
                stmt.execute("ALTER TABLE portfolio_items ADD COLUMN risk_level TEXT");
            } catch (SQLException e) {
            }

            System.out.println("✅ Portfolio table ready");

            // ===== CREATE TRANSACTIONS TABLE =====
            // This table stores your transaction history (buy/sell records)
            String createTransactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," + // Auto-incrementing ID
                    "    symbol TEXT NOT NULL," + // Stock symbol
                    "    type TEXT NOT NULL," + // "BUY" or "SELL"
                    "    quantity INTEGER NOT NULL," + // Number of shares
                    "    price REAL NOT NULL," + // Price per share
                    "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" + // When transaction happened
                    ")";

            // Execute the SQL command to create table
            stmt.execute(createTransactionsTable);
            System.out.println("✅ Transactions table ready");

            // ===== CREATE WATCHLIST TABLE =====
            String createWatchlistTable = "CREATE TABLE IF NOT EXISTS watchlist (" +
                    "    symbol TEXT PRIMARY KEY," +
                    "    name TEXT," +
                    "    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(createWatchlistTable);
            System.out.println("✅ Watchlist table ready");

            // ===== CREATE USERS TABLE =====
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    username TEXT UNIQUE NOT NULL," +
                    "    password_hash TEXT NOT NULL," +
                    "    email TEXT," +
                    "    full_name TEXT," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    last_login TIMESTAMP" +
                    ")";
            stmt.execute(createUsersTable);
            System.out.println("✅ Users table ready");

            // Add user_id column to existing tables if not exists
            try {
                stmt.execute("ALTER TABLE portfolio_items ADD COLUMN user_id INTEGER DEFAULT 1");
            } catch (SQLException e) {
                // Column already exists
            }
            try {
                stmt.execute("ALTER TABLE transactions ADD COLUMN user_id INTEGER DEFAULT 1");
            } catch (SQLException e) {
                // Column already exists
            }
            try {
                stmt.execute("ALTER TABLE watchlist ADD COLUMN user_id INTEGER DEFAULT 1");
            } catch (SQLException e) {
                // Column already exists
            }

            // ===== CREATE EMAIL_SETTINGS TABLE =====
            String createEmailSettingsTable = "CREATE TABLE IF NOT EXISTS email_settings (" +
                    "    id INTEGER PRIMARY KEY," +
                    "    email TEXT," +
                    "    smtp_host TEXT DEFAULT 'smtp.gmail.com'," +
                    "    smtp_port INTEGER DEFAULT 587," +
                    "    app_password TEXT," +
                    "    daily_email_enabled INTEGER DEFAULT 0," +
                    "    send_time TEXT DEFAULT '09:00'" +
                    ")";
            stmt.execute(createEmailSettingsTable);
            System.out.println("✅ Email settings table ready");

            // ===== CREATE WALLET TABLE =====
            String createWalletTable = "CREATE TABLE IF NOT EXISTS wallet (" +
                    "    id INTEGER PRIMARY KEY DEFAULT 1," +
                    "    balance REAL DEFAULT 0.0," +
                    "    total_deposited REAL DEFAULT 0.0," +
                    "    total_withdrawn REAL DEFAULT 0.0" +
                    ")";
            stmt.execute(createWalletTable);

            // Initialize wallet with ID 1 if it doesn't exist
            try {
                stmt.execute(
                        "INSERT OR IGNORE INTO wallet (id, balance, total_deposited, total_withdrawn) VALUES (1, 0.0, 0.0, 0.0)");
            } catch (SQLException e) {
            }
            System.out.println("✅ Wallet table ready");

            // ===== CREATE WALLET TRANSACTIONS TABLE =====
            String createWalletTransactionsTable = "CREATE TABLE IF NOT EXISTS wallet_transactions (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    type TEXT NOT NULL," + // DEPOSIT, WITHDRAW, BUY_STOCK, SELL_STOCK, TAX_PAYMENT
                    "    amount REAL NOT NULL," +
                    "    balance_after REAL NOT NULL," +
                    "    description TEXT," +
                    "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(createWalletTransactionsTable);
            System.out.println("✅ Wallet transactions table ready");

            // Close statement (good practice to free resources)
            stmt.close();

        } catch (SQLException e) {
            // If table creation fails, print error
            System.err.println("❌ Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves email settings to the database.
     */
    public static void saveEmailSettings(String email, String appPassword, String smtpHost,
            int smtpPort, boolean dailyEnabled, String sendTime) {
        try {
            Connection conn = getConnection();
            // Delete existing settings
            Statement delStmt = conn.createStatement();
            delStmt.execute("DELETE FROM email_settings");
            delStmt.close();

            // Insert new settings
            String sql = "INSERT INTO email_settings (id, email, smtp_host, smtp_port, app_password, daily_email_enabled, send_time) "
                    +
                    "VALUES (1, ?, ?, ?, ?, ?, ?)";
            java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, email);
            pstmt.setString(2, smtpHost);
            pstmt.setInt(3, smtpPort);
            pstmt.setString(4, appPassword);
            pstmt.setInt(5, dailyEnabled ? 1 : 0);
            pstmt.setString(6, sendTime);
            pstmt.executeUpdate();
            pstmt.close();
            System.out.println("✅ Email settings saved");
        } catch (SQLException e) {
            System.err.println("❌ Error saving email settings: " + e.getMessage());
        }
    }

    /**
     * Loads email settings from the database.
     * Returns a String array: [email, appPassword, smtpHost, smtpPort,
     * dailyEnabled, sendTime]
     * Returns null if no settings exist.
     */
    public static String[] loadEmailSettings() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT email, app_password, smtp_host, smtp_port, daily_email_enabled, send_time FROM email_settings WHERE id = 1");
            if (rs.next()) {
                String[] settings = new String[6];
                settings[0] = rs.getString("email");
                settings[1] = rs.getString("app_password");
                settings[2] = rs.getString("smtp_host");
                settings[3] = String.valueOf(rs.getInt("smtp_port"));
                settings[4] = String.valueOf(rs.getInt("daily_email_enabled"));
                settings[5] = rs.getString("send_time");
                rs.close();
                stmt.close();
                return settings;
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("❌ Error loading email settings: " + e.getMessage());
        }
        return null;
    }

    /**
     * Closes the database connection
     * Call this when shutting down the application
     * 
     * Example: DatabaseManager.closeConnection();
     */
    public static void closeConnection() {
        try {
            // If connection exists and is open, close it
            if (connection != null && !connection.isClosed()) {
                connection.close(); // Close the connection
                System.out.println("✅ Database connection closed");
            }
        } catch (SQLException e) {
            // If closing fails, print error
            System.err.println("❌ Error closing connection: " + e.getMessage());
        }
    }

    /**
     * Clears all data from database (for testing purposes)
     * WARNING: This deletes everything!
     * 
     * Example: DatabaseManager.clearAllData();
     */
    public static void clearAllData() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            // Delete all rows from both tables
            stmt.execute("DELETE FROM portfolio_items"); // Clear portfolio
            stmt.execute("DELETE FROM transactions"); // Clear transactions

            System.out.println("✅ All data cleared from database");
            stmt.close();

        } catch (SQLException e) {
            System.err.println("❌ Error clearing data: " + e.getMessage());
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
            Connection conn = getConnection();

            // Check if username already exists
            String checkSql = "SELECT id FROM users WHERE username = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Username already exists
                rs.close();
                checkStmt.close();
                return -1;
            }
            rs.close();
            checkStmt.close();

            // Hash password (simple hash for demo - use BCrypt in production)
            String passwordHash = hashPassword(password);

            // Insert new user
            String insertSql = "INSERT INTO users (username, password_hash, email, full_name) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, email);
            pstmt.setString(4, fullName);
            pstmt.executeUpdate();

            // Get generated user ID
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            int userId = -1;
            if (generatedKeys.next()) {
                userId = generatedKeys.getInt(1);
            }
            generatedKeys.close();
            pstmt.close();

            System.out.println("✅ User registered: " + username + " (ID: " + userId + ")");
            return userId;

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
            Connection conn = getConnection();

            String sql = "SELECT id, password_hash FROM users WHERE username = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("id");
                String storedHash = rs.getString("password_hash");
                String inputHash = hashPassword(password);

                if (storedHash.equals(inputHash)) {
                    // Update last login
                    updateLastLogin(userId);
                    rs.close();
                    pstmt.close();
                    System.out.println("✅ User logged in: " + username + " (ID: " + userId + ")");
                    return userId;
                }
            }

            rs.close();
            pstmt.close();
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
        try {
            Connection conn = getConnection();
            String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
            pstmt.close();
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
        try {
            Connection conn = getConnection();
            String sql = "SELECT username, email, full_name, created_at, last_login FROM users WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String[] info = new String[5];
                info[0] = rs.getString("username");
                info[1] = rs.getString("email");
                info[2] = rs.getString("full_name");
                info[3] = rs.getString("created_at");
                info[4] = rs.getString("last_login");
                rs.close();
                pstmt.close();
                return info;
            }

            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            System.err.println("❌ Error getting user info: " + e.getMessage());
        }
        return null;
    }

    /**
     * Simple password hashing (use BCrypt in production!)
     */
    private static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}

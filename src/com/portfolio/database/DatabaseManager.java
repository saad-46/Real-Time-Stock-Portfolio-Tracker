package com.portfolio.database; // Database package for all database-related code

import java.sql.*;  // Import all SQL classes (Connection, Statement, ResultSet, etc.)

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
     * Example: Connection conn = DatabaseManager.getConnection();
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
        
        return connection;  // Return the connection
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
            String createPortfolioTable = 
                "CREATE TABLE IF NOT EXISTS portfolio_items (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +  // Auto-incrementing ID (1, 2, 3...)
                "    symbol TEXT NOT NULL," +                  // Stock symbol (ex: "AAPL")
                "    name TEXT NOT NULL," +                    // Company name (ex: "Apple Inc.")
                "    quantity INTEGER NOT NULL," +             // Number of shares (ex: 10)
                "    purchase_price REAL NOT NULL," +          // Price you paid per share (ex: 150.00)
                "    current_price REAL NOT NULL," +           // Current price per share (ex: 278.12)
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +  // When you bought it
                ")";
            
            // Execute the SQL command to create table
            stmt.execute(createPortfolioTable);
            System.out.println("✅ Portfolio table ready");
            
            // ===== CREATE TRANSACTIONS TABLE =====
            // This table stores your transaction history (buy/sell records)
            String createTransactionsTable = 
                "CREATE TABLE IF NOT EXISTS transactions (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +  // Auto-incrementing ID
                "    symbol TEXT NOT NULL," +                  // Stock symbol
                "    type TEXT NOT NULL," +                    // "BUY" or "SELL"
                "    quantity INTEGER NOT NULL," +             // Number of shares
                "    price REAL NOT NULL," +                   // Price per share
                "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +  // When transaction happened
                ")";
            
            // Execute the SQL command to create table
            stmt.execute(createTransactionsTable);
            System.out.println("✅ Transactions table ready");
            
            // Close statement (good practice to free resources)
            stmt.close();
            
        } catch (SQLException e) {
            // If table creation fails, print error
            System.err.println("❌ Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
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
                connection.close();  // Close the connection
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
            stmt.execute("DELETE FROM portfolio_items");  // Clear portfolio
            stmt.execute("DELETE FROM transactions");     // Clear transactions
            
            System.out.println("✅ All data cleared from database");
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("❌ Error clearing data: " + e.getMessage());
        }
    }
}

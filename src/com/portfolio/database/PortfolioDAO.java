package com.portfolio.database; // Database package

import com.portfolio.model.*; // Import model classes
import java.sql.*; // Import SQL classes
import java.util.ArrayList; // Import ArrayList
import java.util.List; // Import List interface

/**
 * PortfolioDAO - Data Access Object for Portfolio operations
 * DAO Pattern - Separates database logic from business logic
 * 
 * This class handles all database operations for portfolio:
 * - Save stock to database
 * - Load stocks from database
 * - Update stock prices
 * - Delete stocks
 * 
 * Think of DAO like a librarian who manages books (data) in a library
 * (database)
 */
public class PortfolioDAO {

    /**
     * Saves a portfolio item to the database
     * 
     * @param item The portfolio item to save
     * @throws SQLException if database operation fails
     * 
     *                      Example: dao.savePortfolioItem(appleStock);
     */
    public void savePortfolioItem(PortfolioItem item) throws SQLException {
        // SQL INSERT statement - adds a new row to the table
        // ? are placeholders - we'll fill them in later (prevents SQL injection
        // attacks)
        String sql = "INSERT INTO portfolio_items (symbol, name, quantity, purchase_price, current_price, original_currency, sector, market_cap, risk_level) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // Get database connection
        Connection conn = DatabaseManager.getConnection();

        // PreparedStatement - safer than regular Statement (prevents SQL injection)
        // Think of it like a form with blank fields to fill in
        PreparedStatement pstmt = conn.prepareStatement(sql);

        // Fill in the placeholders (? marks)
        // Index starts at 1 (not 0!)
        pstmt.setString(1, item.getStock().getSymbol()); // Fill 1st ? with symbol
        pstmt.setString(2, item.getStock().getName()); // Fill 2nd ? with name
        pstmt.setInt(3, item.getQuantity()); // Fill 3rd ? with quantity
        pstmt.setDouble(4, item.getPurchasePrice()); // Fill 4th ? with purchase price
        pstmt.setDouble(5, item.getStock().getCurrentPrice());
        pstmt.setString(6, item.getOriginalCurrency());
        pstmt.setString(7, item.getStock().getSector());
        pstmt.setString(8, item.getStock().getMarketCap());
        pstmt.setString(9, item.getStock().getRiskLevel());

        // Execute the INSERT statement
        // executeUpdate() returns number of rows affected (should be 1)
        int rowsAffected = pstmt.executeUpdate();

        if (rowsAffected > 0) {
            System.out.println("✅ Saved to database: " + item.getStock().getSymbol());
        }

        // Close statement to free resources
        pstmt.close();
    }

    /**
     * Loads all portfolio items from the database
     * 
     * @return List of all portfolio items
     * @throws SQLException if database operation fails
     * 
     *                      Example: List<PortfolioItem> items =
     *                      dao.loadAllPortfolioItems();
     */
    public List<PortfolioItem> loadAllPortfolioItems() throws SQLException {
        // Create empty list to store items
        List<PortfolioItem> items = new ArrayList<>();

        // SQL SELECT statement - retrieves data from table
        // * means "all columns"
        String sql = "SELECT * FROM portfolio_items";

        // Get database connection
        Connection conn = DatabaseManager.getConnection();

        // Create statement
        Statement stmt = conn.createStatement();

        // Execute query and get results
        // ResultSet - like a cursor that points to rows of data
        // Think of it like reading rows in an Excel spreadsheet
        ResultSet rs = stmt.executeQuery(sql);

        // Loop through all rows in the result
        // rs.next() moves to next row, returns false when no more rows
        while (rs.next()) {
            // Extract data from current row
            // Column names match table definition
            String symbol = rs.getString("symbol"); // Get symbol column
            String name = rs.getString("name"); // Get name column
            int quantity = rs.getInt("quantity"); // Get quantity column
            double purchasePrice = rs.getDouble("purchase_price"); // Get purchase_price column
            double currentPrice = rs.getDouble("current_price"); // Get current_price column

            // Handle null currency gracefully (for older DB versions before this update)
            String currency = rs.getString("original_currency");
            if (currency == null)
                currency = "USD";

            // Create Stock object from database data
            Stock stock = new Stock(symbol, name);
            stock.setCurrentPrice(currentPrice);
            stock.setSector(rs.getString("sector"));
            stock.setMarketCap(rs.getString("market_cap"));
            stock.setRiskLevel(rs.getString("risk_level"));

            // Create PortfolioItem object with currency
            PortfolioItem item = new PortfolioItem(stock, quantity, purchasePrice, currency);

            // Add to list
            items.add(item);
        }

        System.out.println("✅ Loaded " + items.size() + " items from database");

        // Close resources
        rs.close();
        stmt.close();

        return items; // Return the list
    }

    /**
     * Updates the current price of a stock in the database
     * 
     * @param symbol   The stock symbol to update
     * @param newPrice The new current price
     * @throws SQLException if database operation fails
     * 
     *                      Example: dao.updateStockPrice("AAPL", 278.12);
     */
    public void updateStockPrice(String symbol, double newPrice) throws SQLException {
        // SQL UPDATE statement - modifies existing rows
        // SET changes the value, WHERE specifies which rows to update
        String sql = "UPDATE portfolio_items SET current_price = ? WHERE symbol = ?";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);

        // Fill in placeholders
        pstmt.setDouble(1, newPrice); // New price
        pstmt.setString(2, symbol); // Which stock to update

        // Execute update
        int rowsAffected = pstmt.executeUpdate();

        if (rowsAffected > 0) {
            System.out.println("✅ Updated price in database: " + symbol + " = $" + newPrice);
        }

        pstmt.close();
    }

    /**
     * Deletes a portfolio item from the database
     * 
     * @param symbol The stock symbol to delete
     * @throws SQLException if database operation fails
     * 
     *                      Example: dao.deletePortfolioItem("AAPL");
     */
    public void deletePortfolioItem(String symbol) throws SQLException {
        // SQL DELETE statement - removes rows from table
        String sql = "DELETE FROM portfolio_items WHERE symbol = ?";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);

        pstmt.setString(1, symbol); // Which stock to delete

        int rowsAffected = pstmt.executeUpdate();

        if (rowsAffected > 0) {
            System.out.println("✅ Deleted from database: " + symbol);
        }

        pstmt.close();
    }

    /**
     * Updates the quantity of a portfolio item
     * 
     * @param symbol      The stock symbol to update
     * @param newQuantity The new quantity
     * @throws SQLException if database operation fails
     * 
     *                      Example: dao.updatePortfolioItemQuantity("AAPL", 5);
     */
    public void updatePortfolioItemQuantity(String symbol, int newQuantity) throws SQLException {
        String sql = "UPDATE portfolio_items SET quantity = ? WHERE symbol = ?";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);

        pstmt.setInt(1, newQuantity); // New quantity
        pstmt.setString(2, symbol); // Which stock to update

        int rowsAffected = pstmt.executeUpdate();

        if (rowsAffected > 0) {
            System.out.println("✅ Updated quantity in database: " + symbol + " = " + newQuantity);
        }

        pstmt.close();
    }

    /**
     * Saves a transaction to the database
     * 
     * @param transaction The transaction to save
     * @throws SQLException if database operation fails
     * 
     *                      Example: dao.saveTransaction(buyTransaction);
     */
    public void saveTransaction(Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions (symbol, type, quantity, price) VALUES (?, ?, ?, ?)";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);

        pstmt.setString(1, transaction.getSymbol());
        pstmt.setString(2, transaction.getType());
        pstmt.setInt(3, transaction.getQuantity());
        pstmt.setDouble(4, transaction.getPrice());

        pstmt.executeUpdate();
        System.out.println("✅ Transaction saved: " + transaction.getType() + " " + transaction.getSymbol());

        pstmt.close();
    }

    /**
     * Loads all transactions from the database
     * 
     * @return List of all transactions
     * @throws SQLException if database operation fails
     * 
     *                      Example: List<Transaction> history =
     *                      dao.loadAllTransactions();
     */
    public List<Transaction> loadAllTransactions() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();

        String sql = "SELECT * FROM transactions ORDER BY timestamp DESC"; // ORDER BY sorts by date, newest first

        Connection conn = DatabaseManager.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        while (rs.next()) {
            String symbol = rs.getString("symbol");
            String type = rs.getString("type");
            int quantity = rs.getInt("quantity");
            double price = rs.getDouble("price");

            // Create transaction object
            Transaction transaction = new Transaction(symbol, type, quantity, price);
            transactions.add(transaction);
        }

        System.out.println("✅ Loaded " + transactions.size() + " transactions from database");

        rs.close();
        stmt.close();

        return transactions;
    }
}

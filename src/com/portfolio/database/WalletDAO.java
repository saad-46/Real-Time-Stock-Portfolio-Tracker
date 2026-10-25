package com.portfolio.database;

import com.portfolio.model.WalletTransaction;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WalletDAO {

    public double[] getWalletData() throws SQLException {
        double[] data = new double[] { 0.0, 0.0, 0.0 }; // [balance, total_deposited, total_withdrawn]
        String sql = "SELECT balance, total_deposited, total_withdrawn FROM wallet WHERE id = 1";

        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = DatabaseManager.requireConnection().createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    data[0] = rs.getDouble("balance");
                    data[1] = rs.getDouble("total_deposited");
                    data[2] = rs.getDouble("total_withdrawn");
                }
            }
        }
        return data;
    }

    /** The wallet's single currency (ISO code); wallets created before the currency existed are rupee wallets. */
    public String getCurrency() throws SQLException {
        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = DatabaseManager.requireConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT currency FROM wallet WHERE id = 1")) {
                String c = rs.next() ? rs.getString(1) : null;
                return c == null || c.isBlank() ? "INR" : c.trim().toUpperCase();
            }
        }
    }

    /**
     * Change the wallet currency. Only allowed while the wallet is pristine (balance 0 and no history), because
     * relabelling money that already exists would silently change its value.
     *
     * @return false if the wallet already has a balance or history (nothing changed)
     */
    public boolean setCurrencyIfPristine(String currency) throws SQLException {
        synchronized (DatabaseManager.lock()) {
            Connection conn = DatabaseManager.requireConnection();
            try (PreparedStatement p = conn.prepareStatement(
                    "UPDATE wallet SET currency = ? WHERE id = 1 AND balance = 0 AND total_deposited = 0 AND total_withdrawn = 0 "
                            + "AND NOT EXISTS (SELECT 1 FROM wallet_transactions)")) {
                p.setString(1, currency.trim().toUpperCase());
                return p.executeUpdate() == 1;
            }
        }
    }

    public void updateWalletData(double balance, double totalDeposited, double totalWithdrawn) throws SQLException {
        String sql = "UPDATE wallet SET balance = ?, total_deposited = ?, total_withdrawn = ? WHERE id = 1";

        synchronized (DatabaseManager.lock()) {
            Connection conn = DatabaseManager.requireConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, balance);
                pstmt.setDouble(2, totalDeposited);
                pstmt.setDouble(3, totalWithdrawn);

                int rows = pstmt.executeUpdate();
                if (rows == 0) {
                    // Just in case it was missing
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO wallet (id, balance, total_deposited, total_withdrawn) VALUES (1, ?, ?, ?)")) {
                        insertStmt.setDouble(1, balance);
                        insertStmt.setDouble(2, totalDeposited);
                        insertStmt.setDouble(3, totalWithdrawn);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    public void logTransaction(WalletTransaction tx) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (type, amount, balance_after, description) VALUES (?, ?, ?, ?)";

        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = DatabaseManager.requireConnection().prepareStatement(sql)) {
                pstmt.setString(1, tx.getType());
                pstmt.setDouble(2, tx.getAmount());
                pstmt.setDouble(3, tx.getBalanceAfter());
                pstmt.setString(4, tx.getDescription());
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * Deposit or withdraw cash atomically: the balance, the running totals and the history row change together or
     * not at all. A withdrawal larger than the balance changes nothing.
     *
     * @param deposit true = DEPOSIT, false = WITHDRAW
     * @return false only when a withdrawal exceeded the balance
     */
    public boolean applyCashMovement(boolean deposit, double amount, String description) throws SQLException {
        String update = deposit
                // ROUND(..., 2): money is kept in whole cents, so repeated additions can never drift (0.1 x 10 = 1.00 exactly)
                ? "UPDATE wallet SET balance = ROUND(balance + ?, 2), total_deposited = ROUND(total_deposited + ?, 2) WHERE id = 1"
                : "UPDATE wallet SET balance = ROUND(balance - ?, 2), total_withdrawn = ROUND(total_withdrawn + ?, 2) "
                        + "WHERE id = 1 AND ROUND(balance, 2) >= ?";
        synchronized (DatabaseManager.lock()) {
            Connection conn = DatabaseManager.requireConnection();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement u = conn.prepareStatement(update)) {
                    u.setDouble(1, amount);
                    u.setDouble(2, amount);
                    if (!deposit) {
                        u.setDouble(3, amount);
                    }
                    if (u.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                double balanceAfter;
                try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT balance FROM wallet WHERE id = 1")) {
                    balanceAfter = rs.next() ? rs.getDouble(1) : 0.0;
                }
                try (PreparedStatement log = conn.prepareStatement(
                        "INSERT INTO wallet_transactions (type, amount, balance_after, description) VALUES (?, ?, ?, ?)")) {
                    log.setString(1, deposit ? "DEPOSIT" : "WITHDRAW");
                    log.setDouble(2, amount);
                    log.setDouble(3, balanceAfter);
                    log.setString(4, description);
                    log.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<WalletTransaction> loadAllTransactions() throws SQLException {
        List<WalletTransaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM wallet_transactions ORDER BY id DESC"; // newest first

        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = DatabaseManager.requireConnection().createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String type = rs.getString("type");
                    double amount = rs.getDouble("amount");
                    double balanceAfter = rs.getDouble("balance_after");
                    String description = rs.getString("description");

                    Timestamp ts = rs.getTimestamp("timestamp");
                    LocalDateTime timestamp = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

                    transactions.add(new WalletTransaction(id, type, amount, balanceAfter, description, timestamp));
                }
            }
        }
        return transactions;
    }
}

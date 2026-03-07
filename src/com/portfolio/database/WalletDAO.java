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

        Connection conn = DatabaseManager.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        if (rs.next()) {
            data[0] = rs.getDouble("balance");
            data[1] = rs.getDouble("total_deposited");
            data[2] = rs.getDouble("total_withdrawn");
        }

        rs.close();
        stmt.close();
        return data;
    }

    public void updateWalletData(double balance, double totalDeposited, double totalWithdrawn) throws SQLException {
        String sql = "UPDATE wallet SET balance = ?, total_deposited = ?, total_withdrawn = ? WHERE id = 1";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setDouble(1, balance);
        pstmt.setDouble(2, totalDeposited);
        pstmt.setDouble(3, totalWithdrawn);

        int rows = pstmt.executeUpdate();
        if (rows == 0) {
            // Just in case it was missing
            String insertSql = "INSERT INTO wallet (id, balance, total_deposited, total_withdrawn) VALUES (1, ?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setDouble(1, balance);
            insertStmt.setDouble(2, totalDeposited);
            insertStmt.setDouble(3, totalWithdrawn);
            insertStmt.executeUpdate();
            insertStmt.close();
        }
        pstmt.close();
    }

    public void logTransaction(WalletTransaction tx) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (type, amount, balance_after, description) VALUES (?, ?, ?, ?)";

        Connection conn = DatabaseManager.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, tx.getType());
        pstmt.setDouble(2, tx.getAmount());
        pstmt.setDouble(3, tx.getBalanceAfter());
        pstmt.setString(4, tx.getDescription());

        pstmt.executeUpdate();
        pstmt.close();
    }

    public List<WalletTransaction> loadAllTransactions() throws SQLException {
        List<WalletTransaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM wallet_transactions ORDER BY timestamp DESC";

        Connection conn = DatabaseManager.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

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

        rs.close();
        stmt.close();

        return transactions;
    }
}

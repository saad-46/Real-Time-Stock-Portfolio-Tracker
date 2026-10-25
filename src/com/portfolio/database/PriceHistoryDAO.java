package com.portfolio.database;

import com.portfolio.service.PriceHistory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Stores the daily closes a provider actually returned, so charts survive restarts without asking the provider again. */
public class PriceHistoryDAO {

    /** Replaces the stored series for a symbol, in one transaction. */
    public void store(String symbol, List<PriceHistory.Point> points, Instant fetchedAt, String source) throws SQLException {
        synchronized (DatabaseManager.lock()) {
            java.sql.Connection conn = DatabaseManager.requireConnection();
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM price_history WHERE symbol = ?");
                    PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO price_history (symbol, day, close) VALUES (?, ?, ?)");
                    PreparedStatement meta = conn.prepareStatement(
                            "INSERT OR REPLACE INTO price_history_meta (symbol, fetched_at, source) VALUES (?, ?, ?)")) {
                del.setString(1, symbol);
                del.executeUpdate();
                for (PriceHistory.Point p : points) {
                    ins.setString(1, symbol);
                    ins.setString(2, p.date().toString());
                    ins.setDouble(3, p.close());
                    ins.addBatch();
                }
                ins.executeBatch();
                meta.setString(1, symbol);
                meta.setLong(2, fetchedAt.toEpochMilli());
                meta.setString(3, source);
                meta.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(auto);
            }
        }
    }

    /** Stored closes, oldest first; empty when none. */
    public List<PriceHistory.Point> load(String symbol) throws SQLException {
        List<PriceHistory.Point> out = new ArrayList<>();
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement ps = DatabaseManager.requireConnection()
                    .prepareStatement("SELECT day, close FROM price_history WHERE symbol = ? ORDER BY day")) {
                ps.setString(1, symbol);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new PriceHistory.Point(LocalDate.parse(rs.getString(1)), rs.getDouble(2)));
                    }
                }
            }
        }
        return out;
    }

    /** When the stored series was fetched, or null if there is none. */
    public Instant fetchedAt(String symbol) throws SQLException {
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement ps = DatabaseManager.requireConnection()
                    .prepareStatement("SELECT fetched_at FROM price_history_meta WHERE symbol = ?")) {
                ps.setString(1, symbol);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Instant.ofEpochMilli(rs.getLong(1)) : null;
                }
            }
        }
    }

    static void createTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS price_history ("
                + "symbol TEXT NOT NULL, day TEXT NOT NULL, close REAL NOT NULL, PRIMARY KEY (symbol, day))");
        stmt.execute("CREATE TABLE IF NOT EXISTS price_history_meta ("
                + "symbol TEXT PRIMARY KEY, fetched_at INTEGER NOT NULL, source TEXT)");
    }
}

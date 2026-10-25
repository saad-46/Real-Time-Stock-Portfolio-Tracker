package com.portfolio.database;

import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import com.portfolio.service.CostBasis;
import com.portfolio.service.InsufficientFundsException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PortfolioDAO (Data Access Object) handles all portfolio database operations, keeping SQL out of the business
 * logic. Failures are reported as IllegalStateException so a failed read is never mistaken for "no data".
 *
 * Trades go through {@link #recordTrade(String, PortfolioItem, Transaction, WalletLeg)}, which changes the wallet,
 * the holding and the ledger in ONE transaction.
 */
public class PortfolioDAO {

    /** The wallet side of a trade: money out (buy) or in (sell), applied inside the trade's transaction. */
    public static final class WalletLeg {
        private final double amount;
        private final boolean debit;
        private final String description;

        private WalletLeg(double amount, boolean debit, String description) {
            this.amount = amount;
            this.debit = debit;
            this.description = description;
        }

        /** Take {@code amount} from the wallet; fails (rolling back the trade) if the balance is lower. */
        public static WalletLeg debit(double amount, String description) {
            return new WalletLeg(amount, true, description);
        }

        /** Add {@code amount} to the wallet. */
        public static WalletLeg credit(double amount, String description) {
            return new WalletLeg(amount, false, description);
        }
    }

    /**
     * Constructor checks the database is usable.
     *
     * @throws IllegalStateException if the database cannot be opened or migrated
     */
    public PortfolioDAO() {
        if (DatabaseManager.getConnection() == null) {
            throw new IllegalStateException("Database unavailable");
        }
    }

    private static Connection connection() {
        Connection c = DatabaseManager.getConnection();
        if (c == null) {
            throw new IllegalStateException("Database unavailable");
        }
        return c;
    }

    /**
     * Save a new portfolio item to database.
     */
    public void savePortfolioItem(PortfolioItem item) {
        String sql = "INSERT INTO portfolio_items (symbol, name, quantity, purchase_price, current_price, "
                + "original_currency, sector, market_cap, risk_level, price_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement(sql)) {
                bindHolding(pstmt, item);
                pstmt.executeUpdate();
                System.out.println("✅ Saved " + item.getSymbol() + " to database");
            } catch (SQLException e) {
                System.out.println("Error saving portfolio item: " + e.getMessage());
                throw new IllegalStateException("Could not save portfolio item", e);
            }
        }
    }

    private static void bindHolding(PreparedStatement p, PortfolioItem item) throws SQLException {
        Stock s = item.getStock();
        p.setString(1, item.getSymbol());
        p.setString(2, item.getName());
        p.setInt(3, item.getQuantity());
        p.setDouble(4, item.getPurchasePrice());
        p.setDouble(5, item.getCurrentPrice());
        p.setString(6, item.getOriginalCurrency());
        p.setString(7, s.getSector());
        p.setString(8, s.getMarketCap());
        p.setString(9, s.getRiskLevel());
        if (item.getPriceUpdatedAt() == null) {
            p.setNull(10, Types.INTEGER);
        } else {
            p.setLong(10, item.getPriceUpdatedAt().getEpochSecond());
        }
    }

    /**
     * Apply one trade with no wallet effect (holding + ledger only). The application itself always uses the
     * four-argument form so money and shares can never disagree; this one exists for data-layer tests.
     */
    public void recordTrade(String symbol, PortfolioItem after, Transaction transaction) {
        recordTrade(symbol, after, transaction, null);
    }

    /**
     * Apply one trade atomically: move money in the wallet (when {@code wallet} is non-null), replace the symbol's
     * holding row(s) with {@code after} (or just delete them when {@code after} is null, i.e. fully sold) and
     * insert the transaction. Either everything is committed or nothing is.
     *
     * @throws InsufficientFundsException if a debit exceeds the wallet balance (nothing is changed)
     * @throws IllegalStateException on any database failure (nothing is changed)
     */
    public void recordTrade(String symbol, PortfolioItem after, Transaction transaction, WalletLeg wallet) {
        String itemSql = "INSERT INTO portfolio_items (symbol, name, quantity, purchase_price, current_price, "
                + "original_currency, sector, market_cap, risk_level, price_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String txSql = "INSERT INTO transactions (symbol, type, quantity, price, realized_pl, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        synchronized (DatabaseManager.lock()) {
            Connection connection = connection();
            try {
                connection.setAutoCommit(false);
                try {
                    if (wallet != null) {
                        applyWalletLeg(connection, wallet);
                    }
                    try (PreparedStatement del = connection.prepareStatement("DELETE FROM portfolio_items WHERE symbol = ?");
                            PreparedStatement p1 = connection.prepareStatement(itemSql);
                            PreparedStatement p2 = connection.prepareStatement(txSql)) {
                        del.setString(1, symbol);
                        del.executeUpdate();
                        if (after != null) {
                            bindHolding(p1, after);
                            p1.executeUpdate();
                        }
                        p2.setString(1, transaction.getSymbol());
                        p2.setString(2, transaction.getType());
                        p2.setInt(3, transaction.getQuantity());
                        p2.setDouble(4, transaction.getPrice());
                        if (transaction.getRealizedPl() == null) {
                            p2.setNull(5, Types.REAL);
                        } else {
                            p2.setDouble(5, transaction.getRealizedPl());
                        }
                        // Local time, second precision (matches what is shown before and after a restart)
                        p2.setString(6, transaction.getTimestamp().withNano(0)
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        p2.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    if (e instanceof InsufficientFundsException) {
                        throw (InsufficientFundsException) e;
                    }
                    throw new IllegalStateException("Could not record trade; rolled back", e);
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database transaction failed", e);
            }
        }
    }

    /** Inside the caller's transaction: check-and-move money, then log it with the resulting balance. */
    private static void applyWalletLeg(Connection c, WalletLeg wallet) throws SQLException {
        String update = wallet.debit
                ? "UPDATE wallet SET balance = ROUND(balance - ?, 2) WHERE id = 1 AND ROUND(balance, 2) >= ?"
                : "UPDATE wallet SET balance = ROUND(balance + ?, 2) WHERE id = 1";
        try (PreparedStatement u = c.prepareStatement(update)) {
            u.setDouble(1, wallet.amount);
            if (wallet.debit) {
                u.setDouble(2, wallet.amount);
            }
            if (u.executeUpdate() != 1) {
                if (wallet.debit) {
                    throw new InsufficientFundsException(wallet.amount, currentBalance(c));
                }
                throw new SQLException("Wallet row missing");
            }
        }
        try (PreparedStatement log = c.prepareStatement(
                "INSERT INTO wallet_transactions (type, amount, balance_after, description) VALUES (?, ?, ?, ?)")) {
            log.setString(1, wallet.debit ? "STOCK BUY" : "STOCK SELL");
            log.setDouble(2, wallet.amount);
            log.setDouble(3, currentBalance(c));
            log.setString(4, wallet.description);
            log.executeUpdate();
        }
    }

    private static double currentBalance(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT balance FROM wallet WHERE id = 1")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    /**
     * Load all portfolio items from database.
     * Legacy databases may hold several rows per symbol (one per purchase): those are merged by weighted
     * average cost.
     */
    public List<PortfolioItem> loadAllPortfolioItems() {
        List<PortfolioItem> items = new ArrayList<>();
        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = connection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM portfolio_items ORDER BY id")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String symbol = rs.getString("symbol");
                    Stock stock = new Stock(symbol, rs.getString("name"), rs.getDouble("current_price"));
                    stock.setSector(rs.getString("sector"));
                    stock.setMarketCap(rs.getString("market_cap"));
                    stock.setRiskLevel(rs.getString("risk_level"));
                    String currency = rs.getString("original_currency"); // null on rows written before the column existed
                    PortfolioItem item = new PortfolioItem(stock, rs.getInt("quantity"), rs.getDouble("purchase_price"), currency);
                    item.setId(id);
                    long updated = rs.getLong("price_updated_at");
                    if (!rs.wasNull()) {
                        item.setPriceUpdatedAt(java.time.Instant.ofEpochSecond(updated));
                    }
                    PortfolioItem existing = null;
                    for (PortfolioItem i : items) {
                        if (i.getSymbol().equals(symbol)) {
                            existing = i;
                            break;
                        }
                    }
                    if (existing != null) {
                        items.remove(existing);
                        item = CostBasis.merge(existing, item);
                    }
                    items.add(item);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database error while loading portfolio items", e);
            }
        }
        return items;
    }

    /**
     * Update stock price in database. Called when you refresh prices from the API.
     */
    public void updateStockPrice(String symbol, double newPrice) {
        updateStockPrice(symbol, newPrice, java.time.Instant.now());
    }

    /** Same, stamping the price with the given time (the service passes its own clock, so memory and database agree). */
    public void updateStockPrice(String symbol, double newPrice, java.time.Instant at) {
        String sql = "UPDATE portfolio_items SET current_price = ?, price_updated_at = ? WHERE symbol = ?";
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement(sql)) {
                pstmt.setDouble(1, newPrice);
                pstmt.setLong(2, at.getEpochSecond());
                pstmt.setString(3, symbol);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("✅ Updated " + symbol + " price to $" + newPrice);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database error while updating stock price", e);
            }
        }
    }

    /**
     * Delete a portfolio item from database (a removal, not a sale: no ledger or wallet effect).
     */
    public void deletePortfolioItem(String symbol) {
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement("DELETE FROM portfolio_items WHERE symbol = ?")) {
                pstmt.setString(1, symbol);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("✅ Deleted " + symbol + " from database");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database error while deleting portfolio item", e);
            }
        }
    }

    /**
     * Save a transaction to database (ledger row only; trades use {@code recordTrade}).
     */
    public void saveTransaction(Transaction transaction) {
        String sql = "INSERT INTO transactions (symbol, type, quantity, price) VALUES (?, ?, ?, ?)";
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement(sql)) {
                pstmt.setString(1, transaction.getSymbol());
                pstmt.setString(2, transaction.getType());
                pstmt.setInt(3, transaction.getQuantity());
                pstmt.setDouble(4, transaction.getPrice());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Error saving transaction: " + e.getMessage());
                throw new IllegalStateException("Could not save transaction", e);
            }
        }
    }

    /**
     * Load all transactions from database, oldest first (insertion order).
     */
    public List<Transaction> loadAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = connection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM transactions ORDER BY id")) {
                while (rs.next()) {
                    LocalDateTime timestamp = LocalDateTime.parse(rs.getString("timestamp").replace(" ", "T"));
                    double rpl = rs.getDouble("realized_pl");
                    Double realizedPl = rs.wasNull() ? null : rpl;
                    transactions.add(new Transaction(rs.getInt("id"), rs.getString("symbol"), rs.getString("type"),
                            rs.getInt("quantity"), rs.getDouble("price"), timestamp, realizedPl));
                }
            } catch (SQLException | RuntimeException e) {
                throw new IllegalStateException("Database error while loading transactions", e);
            }
        }
        return transactions;
    }

    // ----- Watchlist -----

    public void addToWatchlist(String symbol, String name) {
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement("INSERT OR REPLACE INTO watchlist (symbol, name) VALUES (?, ?)")) {
                pstmt.setString(1, symbol);
                pstmt.setString(2, name);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not add to watchlist", e);
            }
        }
    }

    public void removeFromWatchlist(String symbol) {
        synchronized (DatabaseManager.lock()) {
            try (PreparedStatement pstmt = connection().prepareStatement("DELETE FROM watchlist WHERE symbol = ?")) {
                pstmt.setString(1, symbol);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not remove from watchlist", e);
            }
        }
    }

    public List<Stock> loadWatchlist() {
        List<Stock> watchlist = new ArrayList<>();
        synchronized (DatabaseManager.lock()) {
            try (Statement stmt = connection().createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM watchlist")) {
                while (rs.next()) {
                    watchlist.add(new Stock(rs.getString("symbol"), rs.getString("name")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database error while loading the watchlist", e);
            }
        }
        return watchlist;
    }
}

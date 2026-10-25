import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.StockPriceService;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/**
 * Migration dry run for a database COPY (never point this at the live file). It upgrades the copy with the real
 * application code and prints only pass/fail results and counts, never data values:
 *   java -cp stock-portfolio-tracker.jar;lib/* MigrationDryRun <copy-of-portfolio.db>
 * Checks: nothing lost (row counts, per-symbol quantity and cost, wallet and ledger), backup written and restorable,
 * integrity ok, schema upgraded, second open is a no-op, the app loads it, and a wallet-currency summary.
 */
public class MigrationDryRun {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    /** Aggregates that must survive migration, as comparable strings (not printed). */
    static Map<String, String> metrics(Path db) throws Exception {
        Map<String, String> m = new TreeMap<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + db.toString().replace('\\', '/') + "?mode=ro");
             Statement st = c.createStatement()) {
            for (String t : new String[] { "portfolio_items", "transactions", "wallet_transactions", "watchlist", "users", "email_settings", "wallet" }) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    m.put("rows:" + t, rs.getString(1));
                } catch (SQLException e) {
                    m.put("rows:" + t, "absent");
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT symbol, SUM(quantity), ROUND(SUM(quantity * purchase_price), 4) FROM portfolio_items GROUP BY symbol ORDER BY symbol")) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) sb.append(rs.getString(1)).append('|').append(rs.getLong(2)).append('|').append(rs.getDouble(3)).append(';');
                m.put("holdings", sb.toString());
            } catch (SQLException ignored) { }
            try (ResultSet rs = st.executeQuery("SELECT balance, total_deposited, total_withdrawn FROM wallet WHERE id = 1")) {
                if (rs.next()) m.put("wallet", rs.getDouble(1) + "|" + rs.getDouble(2) + "|" + rs.getDouble(3));
            } catch (SQLException ignored) { }
            try (ResultSet rs = st.executeQuery("SELECT group_concat(type || amount || balance_after, ';') FROM (SELECT * FROM wallet_transactions ORDER BY id)")) {
                m.put("wallet_history", String.valueOf(rs.getString(1)));
            } catch (SQLException ignored) { }
            try (ResultSet rs = st.executeQuery("SELECT group_concat(symbol || type || quantity || price, ';') FROM (SELECT * FROM transactions ORDER BY id)")) {
                m.put("ledger", String.valueOf(rs.getString(1)));
            } catch (SQLException ignored) { }
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                m.put("integrity", rs.getString(1));
            }
        }
        return m;
    }

    static boolean has(Path db, String table, String col) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + db.toString().replace('\\', '/') + "?mode=ro");
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (col.equals(rs.getString("name"))) return true;
        }
        return false;
    }

    public static void main(String[] a) throws Exception {
        Path db = Path.of(a[0]).toAbsolutePath();
        if (!Files.isRegularFile(db)) { System.out.println("no such file"); System.exit(2); }
        Path dir = db.getParent();
        String before = sha(db);
        Map<String, String> legacy = metrics(db);
        System.out.println("copy sha256 (first 12): " + before.substring(0, 12) + " | integrity before: " + legacy.get("integrity"));

        System.setProperty("portfolio.db.path", db.toString());
        StockPriceService noPrices = new StockPriceService() {   // no network: the migration needs no prices
            @Override public Stock getStock(String s) { return null; }
            @Override public boolean isValidStock(String s) { return false; }
        };
        PortfolioService svc = new PortfolioService(noPrices);
        List<Path> backups;
        try (var s = Files.list(dir)) { backups = s.filter(p -> p.getFileName().toString().contains(".pre-migration-")).toList(); }
        check("one pre-migration backup was written before the upgrade", backups.size() == 1);

        Map<String, String> after = metrics(db);
        check("integrity_check ok after upgrade", "ok".equals(after.get("integrity")));
        for (String k : new String[] { "rows:transactions", "rows:wallet_transactions", "rows:watchlist", "rows:users", "rows:email_settings", "rows:wallet", "holdings", "wallet", "wallet_history", "ledger" }) {
            check("unchanged by the upgrade: " + k, Objects.equals(legacy.get(k), after.get(k)));
        }
        check("holding rows were NOT rewritten by the upgrade (merged only in memory until the next trade)", Objects.equals(legacy.get("rows:portfolio_items"), after.get("rows:portfolio_items")));
        check("new columns exist (realized_pl, price_updated_at, wallet.currency)", has(db, "transactions", "realized_pl") && has(db, "portfolio_items", "price_updated_at") && has(db, "wallet", "currency"));

        // what the application sees
        Map<String, Long> qty = new TreeMap<>();
        for (PortfolioItem i : svc.getAllPortfolioItems()) qty.put(i.getSymbol(), (long) i.getQuantity());
        StringBuilder legacyQty = new StringBuilder();
        for (String row : legacy.get("holdings").split(";")) if (!row.isBlank()) legacyQty.append(row.split("\\|")[0]).append('=').append(row.split("\\|")[1]).append(',');
        StringBuilder appQty = new StringBuilder();
        qty.forEach((k, v) -> appQty.append(k).append('=').append(v).append(','));
        check("the app shows one merged holding per symbol with the same total quantity", legacyQty.toString().contentEquals(appQty));
        double appCost = 0, legacyCost = 0;
        for (PortfolioItem i : svc.getAllPortfolioItems()) appCost += i.getQuantity() * i.getPurchasePrice();
        for (String row : legacy.get("holdings").split(";")) if (!row.isBlank()) legacyCost += Double.parseDouble(row.split("\\|")[2]);
        check("weighted-average merge preserves total cost (within 0.01)", Math.abs(appCost - legacyCost) < 0.01);
        check("no holding has a freshness stamp, so every one displays 'Unavailable' until a real refresh",
                svc.getAllPortfolioItems().stream().allMatch(i -> svc.quoteStatus(i) == com.portfolio.service.QuotePolicy.Status.UNAVAILABLE));
        check("the wallet currency is a single known currency", svc.getWalletService().getCurrency().matches("[A-Z]{3}"));
        System.out.println("INFO wallet currency after upgrade: " + svc.getWalletService().getCurrency() + "; distinct holding currencies: "
                + new TreeSet<>(svc.getAllPortfolioItems().stream().map(PortfolioItem::getOriginalCurrency).toList()));
        boolean allSameAsWallet = svc.getAllPortfolioItems().stream().allMatch(i -> i.getOriginalCurrency().equalsIgnoreCase(svc.getWalletService().getCurrency()));
        check("every holding is in the wallet's currency, so trades need no exchange rate", allSameAsWallet);

        // second open: idempotent, no second backup
        DatabaseManager.closeConnection();
        new PortfolioService(noPrices);
        DatabaseManager.closeConnection();
        try (var s = Files.list(dir)) { backups = s.filter(p -> p.getFileName().toString().contains(".pre-migration-")).toList(); }
        check("second open changes nothing and writes no second backup", backups.size() == 1 && Objects.equals(after.get("ledger"), metrics(db).get("ledger")));

        // the documented restore: the backup holds exactly the legacy data
        Path restored = dir.resolve("verify").resolve("restored-check.db"); // evidence kept apart from the database itself
        Files.createDirectories(restored.getParent());
        Files.copy(backups.get(0), restored, StandardCopyOption.REPLACE_EXISTING);
        Map<String, String> back = metrics(restored);
        boolean same = "ok".equals(back.get("integrity"));
        for (String k : new String[] { "rows:portfolio_items", "rows:transactions", "rows:wallet_transactions", "holdings", "wallet", "wallet_history", "ledger" }) same &= Objects.equals(legacy.get(k), back.get(k));
        check("restoring the backup recovers the legacy data exactly (rows, holdings, wallet, wallet history, ledger)", same);
        check("the backup has none of the new columns (it really is the pre-upgrade state)", !has(restored, "transactions", "realized_pl"));
        System.out.println("SUMMARY pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}

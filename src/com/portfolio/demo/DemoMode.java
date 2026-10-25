package com.portfolio.demo;

import com.portfolio.database.DatabaseManager;
import com.portfolio.service.NewsService;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.WalletService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Demo mode ({@code --demo}): a complete, deterministic sample portfolio for demonstrations and screenshots.
 *
 * <p>Protection of real data: demo mode points the database, configuration and cache files at a brand-new folder under the
 * system temp directory, before anything is opened, so it cannot read or write the real database, wallet, settings or
 * credentials. It uses {@link DemoPriceService} (no network, no API keys). The folder is deleted when the program exits.
 */
public final class DemoMode {
    private static volatile boolean active;
    private static Path folder;

    private DemoMode() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean requested(String[] args) {
        return args != null && List.of(args).contains("--demo");
    }

    /** Switches to the disposable data folder. Must run before any database or configuration access. */
    public static synchronized void activate() throws IOException {
        if (active) {
            return;
        }
        folder = Files.createTempDirectory("stockvault-demo-");
        System.setProperty("portfolio.db.path", folder.resolve("demo.db").toString());
        System.setProperty("stockvault.env", folder.resolve("no-env-file").toString());
        System.setProperty("stockvault.config", folder.resolve("no-config-file").toString());
        System.setProperty("stockvault.ui", folder.resolve("ui.properties").toString());
        System.setProperty("stockvault.marketcache", folder.resolve("market-cache.properties").toString());
        active = true;
        Runtime.getRuntime().addShutdownHook(new Thread(DemoMode::cleanUp, "demo-cleanup"));
    }

    private static void cleanUp() {
        DatabaseManager.closeConnection();
        Path dir = folder;
        // only the unique folder this run created
        if (dir == null || !dir.getFileName().toString().startsWith("stockvault-demo-")) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // a leftover temp file is harmless
                }
            });
        } catch (IOException ignored) {
            // a leftover temp folder is harmless
        }
    }

    /** One holding of the demo portfolio: bought at {@code buy}, now quoted by {@link DemoPriceService}. */
    record Holding(String symbol, int quantity, double buy) {
    }

    static final List<Holding> HOLDINGS = List.of(
            new Holding("RELIANCE", 25, 2780.00),
            new Holding("HDFCBANK", 40, 1610.00),
            new Holding("ICICIBANK", 60, 1085.00),
            new Holding("TCS", 12, 3850.00),
            new Holding("INFY", 40, 1540.00),
            new Holding("SBIN", 80, 790.00));

    static final double DEPOSIT_1 = 500_000.00;
    static final double DEPOSIT_2 = 250_000.00;
    static final double WITHDRAWAL = 50_000.00;
    static final int INFY_SOLD = 10;
    static final double INFY_SELL_PRICE = 1575.00;
    static final List<String> WATCHLIST = List.of("ITC", "LT", "BHARTIARTL");

    /** Writes the sample wallet, trades and watchlist into the disposable database and returns the service that sees them. */
    public static PortfolioService seededPortfolio() {
        if (!active) {
            throw new IllegalStateException("Demo data is only written in demo mode, to its disposable database");
        }
        return seed();
    }

    /** Writes the sample data into whatever database is currently configured. Callers must have pointed it at a disposable one. */
    static PortfolioService seed() {
        WalletService wallet = new WalletService();
        wallet.addFunds(DEPOSIT_1, "UPI (demo)");
        wallet.addFunds(DEPOSIT_2, "Bank transfer (demo)");
        wallet.withdrawFunds(WITHDRAWAL, "Withdrawal to bank account (demo)");
        SteppingClock clock = new SteppingClock();
        PortfolioService portfolio = new PortfolioService(new DemoPriceService(), clock, new com.portfolio.service.CurrencyService());
        for (Holding h : HOLDINGS) {
            clock.daysAgo(DemoPriceService.anchorDays(h.symbol()));
            portfolio.buyStock(h.symbol(), DemoPriceService.nameOf(h.symbol()), h.quantity(), h.buy());
        }
        clock.daysAgo(INFY_SOLD_DAYS_AGO);
        portfolio.sellStock("INFY", INFY_SOLD, INFY_SELL_PRICE);
        clock.daysAgo(0);
        for (String symbol : WATCHLIST) {
            portfolio.addToWatchlist(symbol, DemoPriceService.nameOf(symbol));
        }
        portfolio.updateAllPrices();
        backdateWalletRows();
        portfolio.getWalletService().reload(); // pick up the dated rows
        return portfolio;
    }

    static final int INFY_SOLD_DAYS_AGO = 20;
    private static final int[] WALLET_DAYS_AGO = { 88, 87, 86, 80, 72, 64, 56, 48, 40, INFY_SOLD_DAYS_AGO };

    /**
     * The wallet rows are written with the current time; in the disposable demo database they are dated to match the trades,
     * so the demo wallet history has a realistic timeline. Only ever runs against the demo database.
     */
    private static void backdateWalletRows() {
        if (!active) {
            return;
        }
        synchronized (DatabaseManager.lock()) {
            try (java.sql.Statement st = DatabaseManager.requireConnection().createStatement()) {
                for (int i = 0; i < WALLET_DAYS_AGO.length; i++) {
                    st.executeUpdate("UPDATE wallet_transactions SET timestamp = datetime('now', 'localtime', '-" + WALLET_DAYS_AGO[i]
                            + " days', 'start of day', '+10 hours') WHERE id = (SELECT id FROM wallet_transactions ORDER BY id LIMIT 1 OFFSET " + i + ")");
                }
            } catch (java.sql.SQLException e) {
                System.err.println("Demo wallet history could not be dated: " + e.getMessage());
            }
        }
    }

    /** A clock whose "now" is moved back a number of days, so demo trades get a real-looking ledger. */
    private static final class SteppingClock extends java.time.Clock {
        private volatile long daysBack;

        void daysAgo(int days) {
            daysBack = days;
        }

        @Override public java.time.ZoneId getZone() {
            return java.time.ZoneId.systemDefault();
        }

        @Override public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public java.time.Instant instant() {
            return java.time.Instant.now().minus(java.time.Duration.ofDays(daysBack));
        }
    }

    /** Fixed headlines, labelled as demo content wherever they are shown. */
    public static List<NewsService.NewsItem> news() {
        String[][] rows = {
                { "Banking stocks lead a broad market advance", "Markets", "Bullish" },
                { "IT services firms trim near-term guidance", "Technology", "Bearish" },
                { "Crude oil steadies as demand outlook improves", "Energy", "Neutral" },
                { "Regulator proposes simpler disclosure norms for mutual funds", "Regulation", "Neutral" },
                { "Large-cap earnings season begins next week", "Markets", "Neutral" },
        };
        List<NewsService.NewsItem> out = new ArrayList<>();
        for (String[] r : rows) {
            NewsService.NewsItem item = new NewsService.NewsItem(r[0], r[1], r[2], "");
            item.setSource("StockVault demo data");
            item.setDescription("Simulated headline for demonstration. Not real news.");
            item.setPublishedAt("Demo");
            out.add(item);
        }
        return out;
    }
}

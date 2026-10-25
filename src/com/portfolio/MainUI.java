package com.portfolio;

import com.portfolio.service.AccountService;
import com.portfolio.service.MarketIndexService;
import com.portfolio.service.MultiSourceStockService;
import com.portfolio.service.NewsService;
import com.portfolio.service.PortfolioService;
import com.portfolio.ui.HomeScreen;
import com.portfolio.ui.LandingFrame;
import com.portfolio.ui.ModernPortfolioUI;
import com.portfolio.ui.PremiumStockDashboard;
import com.portfolio.ui.ThemeMode;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * MainUI launches the graphical user interface (entry point of the desktop application).
 *
 * <pre>
 *   java -jar stock-portfolio-tracker.jar            StockVault landing page, then sign-in, then the application (dashboard)
 *   java -jar stock-portfolio-tracker.jar --home     straight to the home screen (summary + quick access), no sign-in
 *   java -jar stock-portfolio-tracker.jar --dashboard  straight to the full dashboard
 *   java -jar stock-portfolio-tracker.jar --classic  simpler "Modern" window (ledger, CSV export)
 * </pre>
 *
 * Nothing is bought, sold or seeded at startup: the portfolio shown is exactly what is in the database.
 */
public class MainUI {
    public static void main(String[] args) {
        boolean demo = com.portfolio.demo.DemoMode.requested(args);
        if (demo) {
            try {
                // before anything opens the database or reads configuration: demo data never touches the real files
                com.portfolio.demo.DemoMode.activate();
            } catch (java.io.IOException e) {
                System.err.println("Demo mode could not create its temporary folder: " + e.getMessage());
                return;
            }
        }
        boolean classic = args != null && java.util.Arrays.asList(args).contains("--classic");
        boolean direct = args != null && java.util.Arrays.asList(args).contains("--dashboard");
        boolean home = args != null && java.util.Arrays.asList(args).contains("--home");
        // close the database cleanly however the program ends
        Runtime.getRuntime().addShutdownHook(new Thread(com.portfolio.database.DatabaseManager::closeConnection, "close-database"));
        // Run on the Event Dispatch Thread (required for Swing)
        SwingUtilities.invokeLater(() -> {
            com.portfolio.AppThreads.exitWhenNoWindowsRemain();
            try {
                if (demo) {
                    ThemeMode.apply(true);
                    PremiumStockDashboard dashboard = new PremiumStockDashboard(com.portfolio.demo.DemoMode.seededPortfolio());
                    dashboard.setVisible(true);
                    bringToFront(dashboard);
                } else if (classic) {
                    new ModernPortfolioUI();
                } else if (direct) {
                    ThemeMode.apply(true); // the dashboard window has its own dark palette
                    openDashboard();
                } else if (home) {
                    ThemeMode.apply(ThemeMode.loadPreference());
                    openHome(null);
                } else {
                    ThemeMode.apply(ThemeMode.loadPreference());
                    openLanding();
                }
                System.out.println("✅ Portfolio UI launched successfully!");
            } catch (Throwable e) {
                // e.g. database could not be opened/loaded, or a library is missing: tell the user instead of
                // showing an empty portfolio or silently doing nothing
                String why = startupFailureText(e);
                System.err.println("Error launching UI: " + why);
                e.printStackTrace();
                try {
                    JOptionPane.showMessageDialog(null, "The application could not start: " + why,
                            "Startup error", JOptionPane.ERROR_MESSAGE);
                } catch (Throwable dialogProblem) {
                    // no display available: the message above on the console is all there is
                    System.err.println("(could not show the error dialog: " + dialogProblem.getClass().getSimpleName() + ")");
                }
            }
        });
    }


    /** The home screen opens the database itself (off the event thread) and shows any failure on screen. */
    private static void openHome(String pendingPage) {
        HomeScreen screen = new HomeScreen(() -> new PortfolioService(new MultiSourceStockService()));
        if (pendingPage != null) {
            screen.openPageWhenReady(pendingPage);
        }
        screen.setVisible(true);
        bringToFront(screen);
    }

    /**
     * The full application window. After a successful sign-in this is what opens (no intermediate welcome page). It has its own dark
     * palette, so the dark look and feel is selected for it. If it cannot be built (for example the database cannot be read) the
     * home screen opens instead, because that one shows the reason on screen and offers a retry.
     */
    private static void openDashboard() {
        ThemeMode.apply(true);
        try {
            PortfolioService portfolio = new PortfolioService(new MultiSourceStockService());
            PremiumStockDashboard dashboard = new PremiumStockDashboard(portfolio);
            dashboard.setVisible(true);
            bringToFront(dashboard);
        } catch (RuntimeException | LinkageError e) {
            System.err.println("The dashboard could not open: " + startupFailureText(e));
            openHome(null);
        }
    }

    /** Landing page first; a successful sign-in replaces it with the application (the dashboard). */
    private static void openLanding() {
        final MultiSourceStockService prices = new MultiSourceStockService();
        final MarketIndexService market = new MarketIndexService(prices);
        final NewsService news = new NewsService();
        final java.util.function.Supplier<NewsService.Live> cachedNews = cached(news::fetchLive, java.time.Duration.ofMinutes(5));
        final LandingFrame[] frame = new LandingFrame[1];
        frame[0] = new LandingFrame(new AccountService(), market, cachedNews, LandingFrame::aiStatusLine,
                LandingFrame::openInBrowser, (userId, username) -> {
                    openDashboard();
                    frame[0].dispose();
                });
        frame[0].setVisible(true);
        bringToFront(frame[0]);
        frame[0].start();
    }

    /** Remembers a successful news answer for a while, so rebuilding the page (theme switch) does not ask the provider again. */
    static java.util.function.Supplier<NewsService.Live> cached(java.util.function.Supplier<NewsService.Live> source, java.time.Duration keep) {
        final Object lock = new Object();
        final NewsService.Live[] last = new NewsService.Live[1];
        final long[] at = new long[1];
        return () -> {
            synchronized (lock) {
                if (last[0] != null && System.nanoTime() - at[0] < keep.toNanos()) {
                    return last[0];
                }
            }
            NewsService.Live fresh = source.get();
            if (!fresh.items().isEmpty()) {
                synchronized (lock) {
                    last[0] = fresh;
                    at[0] = System.nanoTime();
                }
            }
            return fresh;
        };
    }

    /**
     * A window opened from a terminal or an IDE can appear behind it, which looks like "the program did not start".
     * Briefly marking it always-on-top is the standard way to raise it; it is then returned to a normal window.
     */
    private static void bringToFront(java.awt.Window w) {
        try {
            w.setAlwaysOnTop(true);
            w.toFront();
            w.requestFocus();
            w.setAlwaysOnTop(false);
        } catch (RuntimeException e) {
            // raising the window is a convenience only
        }
    }

    /** A readable reason for a startup failure: a null or empty message would otherwise show as "null". */
    static String startupFailureText(Throwable e) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = e; t != null && text.length() < 400; t = t.getCause() == t ? null : t.getCause()) {
            if (text.length() > 0) {
                text.append(" <- ");
            }
            String m = t.getMessage();
            text.append(t.getClass().getSimpleName());
            if (m != null && !m.isBlank()) {
                text.append(": ").append(m.strip());
            }
        }
        return text.toString();
    }
}

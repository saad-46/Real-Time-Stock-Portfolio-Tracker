import com.portfolio.database.DatabaseManager;
import com.portfolio.database.WalletDAO;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.PriceUnavailableException;
import com.portfolio.service.StockPriceService;
import com.portfolio.ui.HomeScreen;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Scripted smoke test of the home screen (scratch tooling, NOT human acceptance): drives real Swing components in-process
 * with a fake price provider and temporary databases. Reuses GuiAccept's helpers.
 *   javac -cp target/classes;target/lib/* -d out tools/GuiAccept.java tools/HomeSmoke.java
 *   java -Dportfolio.db.path=TEMP1.db -Dhomesmoke.db2=TEMP2.db -cp out;target/classes;target/lib/* HomeSmoke
 */
public class HomeSmoke {
    static volatile boolean noKey = false;
    static volatile double quote = 150.0;
    static volatile double change = 2.0;
    static final List<String> providerThreads = Collections.synchronizedList(new ArrayList<>());

    static final StockPriceService PROVIDER = new StockPriceService() {
        @Override
        public Stock getStock(String symbol) {
            providerThreads.add(Thread.currentThread().getName());
            if (noKey) {
                throw new PriceUnavailableException(PriceUnavailableException.Reason.NO_KEY, "No API key configured (smoke test)");
            }
            if (!symbol.equals("AAPL")) {
                return null;
            }
            Stock s = new Stock(symbol, symbol, quote);
            s.setCurrency("USD");
            s.setChangePercent(change);
            return s;
        }

        @Override
        public boolean isValidStock(String symbol) {
            return getStock(symbol) != null;
        }
    };

    static Component find(Container root, String name) throws Exception {
        return GuiAccept.edtGet(() -> {
            for (Component c : GuiAccept.all(root, Component.class)) {
                if (name.equals(c.getName())) return c;
            }
            return null;
        });
    }

    static boolean showing(Container root, String name) throws Exception {
        Component c = find(root, name);
        return c != null && GuiAccept.edtGet(c::isShowing);
    }

    static String textOf(Container root, String name) throws Exception {
        Component c = find(root, name);
        if (c == null) return "<missing>";
        return GuiAccept.edtGet(() -> c instanceof JLabel l ? l.getText() : c instanceof javax.swing.text.JTextComponent t ? t.getText()
                : c instanceof AbstractButton b ? b.getText() : "<n/a>");
    }

    /** All text of every label and text area under a named container. */
    static String allText(Container root, String containerName) throws Exception {
        Component c = find(root, containerName);
        if (!(c instanceof Container cont)) return "<missing>";
        return GuiAccept.edtGet(() -> {
            StringBuilder sb = new StringBuilder();
            for (Component k : GuiAccept.all(cont, Component.class)) {
                if (k instanceof JLabel l && l.getText() != null) sb.append(l.getText()).append(" | ");
                if (k instanceof javax.swing.text.JTextComponent t && t.getText() != null) sb.append(t.getText()).append(" | ");
            }
            return sb.toString();
        });
    }

    static boolean waitFor(Supplier<Boolean> cond, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.get()) return true;
            Thread.sleep(50);
        }
        return cond.get();
    }

    static void click(Container root, String name) throws Exception {
        Component c = find(root, name);
        SwingUtilities.invokeLater(() -> ((AbstractButton) c).doClick());
    }

    static JFrame windowTitled(String prefix) throws Exception {
        return GuiAccept.edtGet(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame f && f.isShowing() && f.getTitle() != null && f.getTitle().startsWith(prefix)) return f;
            }
            return null;
        });
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> GuiAccept.edtErrors.add(th.getName() + ": " + e));

        // ================= 1. loading, then the empty state (nothing made up)
        WalletDAO wdao = new WalletDAO();
        wdao.setCurrencyIfPristine("USD");
        CountDownLatch gate = new CountDownLatch(1);
        AtomicReference<PortfolioService> svcRef = new AtomicReference<>();
        HomeScreen[] h = new HomeScreen[1];
        GuiAccept.edt(() -> h[0] = new HomeScreen(() -> {
            gate.await(10, TimeUnit.SECONDS);
            PortfolioService s = new PortfolioService(PROVIDER);
            svcRef.set(s);
            return s;
        }));
        GuiAccept.edt(() -> h[0].setVisible(true));
        Container root = h[0].getContentPane();
        GuiAccept.check("1 loading state is shown while the database opens", showing(root, "home.loading") && !showing(root, "home.content"), "");
        final AbstractButton refreshBtn = (AbstractButton) find(root, "home.refresh");
        GuiAccept.check("1 refresh is disabled while loading", !GuiAccept.edtGet(refreshBtn::isEnabled), "");
        gate.countDown();
        GuiAccept.check("1 empty portfolio shows the empty state", waitFor(() -> {
            try { return showing(root, "home.empty"); } catch (Exception e) { return false; }
        }, 8000), "");
        GuiAccept.check("1 empty state shows no summary numbers at all", find(root, "home.value.totalValue") == null && find(root, "home.card.cash") == null, "");
        GuiAccept.check("1 empty state offers the first steps", showing(root, "home.empty.add") && showing(root, "home.empty.fund"), "");
        GuiAccept.check("1 navigation actions are present", showing(root, "home.action.portfolio") && showing(root, "home.action.wallet")
                && showing(root, "home.action.transactions") && showing(root, "home.action.analytics") && showing(root, "home.action.watchlist")
                && showing(root, "home.action.market") && showing(root, "home.action.classic") && showing(root, "home.action.add"), "");
        GuiAccept.check("1 the page never claims live data", !allText(root, "home.content").toLowerCase().contains("live data"), "");

        // ================= 2. populated: unrefreshed holding valued at cost, approximate rates flagged
        wdao.applyCashMovement(true, 5000, "smoke deposit");
        PortfolioService svc = svcRef.get();
        svc.buyStock(new PortfolioItem("AAPL", "Apple", 10, 100.0, 100.0));      // USD holding, wallet is USD, display is INR
        GuiAccept.edt(() -> h[0].load());
        GuiAccept.check("2 summary cards replace the empty state", waitFor(() -> {
            try { return showing(root, "home.value.totalValue"); } catch (Exception e) { return false; }
        }, 8000), "");
        GuiAccept.check("2 holding without a live price is reported as valued at cost", textOf(root, "home.sub.totalValue").contains("1 valued at cost"), textOf(root, "home.sub.totalValue"));
        GuiAccept.check("2 price chip says unavailable (never refreshed)", textOf(root, "home.chip.prices").contains("1 unavailable"), textOf(root, "home.chip.prices"));
        GuiAccept.check("2 an explanatory note tells the user prices must be refreshed", allText(root, "home.content").contains("valued at what you paid"), "");
        GuiAccept.check("2 approximate exchange rates are flagged on the chip and on the figures",
                textOf(root, "home.chip.rates").startsWith("≈") && textOf(root, "home.value.totalValue").startsWith("≈"),
                textOf(root, "home.chip.rates") + " / " + textOf(root, "home.value.totalValue"));
        GuiAccept.check("2 wallet cash is shown in the wallet's own currency", textOf(root, "home.value.cash").startsWith("$"), textOf(root, "home.value.cash"));

        // ================= 3. refresh failure is explained, not hidden
        noKey = true;
        click(root, "home.refresh");
        GuiAccept.check("3 refresh failure shows a warning that names the cause", waitFor(() -> {
            try { return textOf(root, "home.banner.text").contains("could not be updated") && textOf(root, "home.banner.text").contains("No API key"); } catch (Exception e) { return false; }
        }, 8000), textOf(root, "home.banner.text"));
        GuiAccept.check("3 the failure left the price status honest", textOf(root, "home.chip.prices").contains("1 unavailable"), textOf(root, "home.chip.prices"));
        GuiAccept.check("3 refresh is usable again afterwards", waitFor(() -> {
            try { return GuiAccept.edtGet(refreshBtn::isEnabled); } catch (Exception e) { return false; }
        }, 5000), "");
        noKey = false;

        // ================= 4. refresh success updates status and movers
        click(root, "home.refresh");
        GuiAccept.check("4 successful refresh says so", waitFor(() -> {
            try { return textOf(root, "home.banner.text").equals("Prices refreshed."); } catch (Exception e) { return false; }
        }, 8000), textOf(root, "home.banner.text"));
        GuiAccept.check("4 price chip now counts the holding as fresh", textOf(root, "home.chip.prices").contains("1 fresh") && textOf(root, "home.chip.prices").contains("0 unavailable"), textOf(root, "home.chip.prices"));
        GuiAccept.check("4 the mover comes from the provider's reported change", allText(root, "home.movers").contains("AAPL") && allText(root, "home.movers").contains("+2.00%"), allText(root, "home.movers"));
        GuiAccept.check("4 recent activity lists the real purchase", allText(root, "home.recent").contains("BUY 10 AAPL"), allText(root, "home.recent"));

        // ================= 5. navigation opens the real screens
        click(root, "home.action.portfolio");
        GuiAccept.check("5 'Portfolio' opens the full dashboard", waitFor(() -> {
            try { return windowTitled("StockVault — Portfolio Dashboard") != null; } catch (Exception e) { return false; }
        }, 20000), "");
        JFrame dash = windowTitled("StockVault — Portfolio Dashboard");
        if (dash != null) GuiAccept.edt(dash::dispose);
        GuiAccept.check("5 closing the dashboard returns to the home screen, which stays open", waitFor(() -> {
            try { return h[0].isShowing() && showing(root, "home.value.totalValue"); } catch (Exception e) { return false; }
        }, 8000), "");
        click(root, "home.action.classic");
        GuiAccept.check("5 'Classic view' opens the classic window", waitFor(() -> {
            try { return windowTitled("Stock Portfolio Tracker") != null; } catch (Exception e) { return false; }
        }, 10000), "");
        JFrame classic = windowTitled("Stock Portfolio Tracker");
        if (classic != null) GuiAccept.edt(classic::dispose);

        // ================= 6. keyboard and accessibility
        boolean keyboardOk = GuiAccept.edtGet(() -> {
            for (Component c : GuiAccept.all(root, Component.class)) {
                if (c.getName() != null && c.getName().startsWith("home.action.") && c instanceof AbstractButton b) {
                    if (!b.isFocusable() || b.getMnemonic() == 0 || b.getAccessibleContext().getAccessibleName() == null) return false;
                }
            }
            return true;
        });
        GuiAccept.check("6 every action button is focusable, has a mnemonic and an accessible name", keyboardOk, "");
        GuiAccept.check("6 refresh has a mnemonic and an F5 shortcut", GuiAccept.edtGet(() -> refreshBtn.getMnemonic() != 0)
                && GuiAccept.edtGet(() -> h[0].getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0)) != null), "");

        // ================= 7. resizing
        GuiAccept.edt(() -> h[0].setSize(920, 640));
        GuiAccept.edt(() -> h[0].validate());
        boolean small = GuiAccept.edtGet(() -> h[0].getWidth() >= 920 && h[0].getMinimumSize().width >= 900);
        GuiAccept.edt(() -> h[0].setSize(1700, 1000));
        GuiAccept.edt(() -> h[0].validate());
        GuiAccept.check("7 the window resizes between its minimum and a large size without errors", small && GuiAccept.edtErrors.isEmpty(), GuiAccept.edtErrors.toString());
        GuiAccept.check("7 the content scrolls rather than clipping", find(root, "home.scroll") != null, "");
        GuiAccept.edt(() -> h[0].setSize(920, 640));
        Thread.sleep(400);
        JScrollPane sp = (JScrollPane) find(root, "home.scroll");
        GuiAccept.check("7 at the minimum width nothing is clipped sideways (no horizontal scrollbar, all five cards visible)",
                sp != null && !GuiAccept.edtGet(() -> sp.getHorizontalScrollBar().isVisible()) && showing(root, "home.card.realized"), "");
        boolean allInside = GuiAccept.edtGet(() -> {
            Component c = null;
            for (Component k : GuiAccept.all(root, Component.class)) if ("home.card.realized".equals(k.getName())) c = k;
            if (c == null) return false;
            Point p = SwingUtilities.convertPoint(c.getParent(), c.getLocation(), sp.getViewport());
            return p.x >= 0 && p.x + c.getWidth() <= sp.getViewport().getWidth();
        });
        GuiAccept.check("7 the last card lies fully inside the visible area at 920 px", allInside, "");
        GuiAccept.edt(h[0]::dispose);

        // ================= 8. database error: reason, location, data untouched, retry
        DatabaseManager.closeConnection();
        String db2 = System.getProperty("homesmoke.db2");
        System.setProperty("portfolio.db.path", db2);
        int[] attempts = { 0 };
        HomeScreen[] h2 = new HomeScreen[1];
        GuiAccept.edt(() -> h2[0] = new HomeScreen(() -> {
            if (attempts[0]++ == 0) {
                throw new IllegalStateException("Could not initialize database schema", new java.sql.SQLException("database is locked"));
            }
            return new PortfolioService(PROVIDER);
        }));
        GuiAccept.edt(() -> h2[0].setVisible(true));
        Container root2 = h2[0].getContentPane();
        GuiAccept.check("8 a database failure is shown as an error state", waitFor(() -> {
            try { return showing(root2, "home.error.title"); } catch (Exception e) { return false; }
        }, 8000), "");
        GuiAccept.check("8 the error explains the cause and where the data is", textOf(root2, "home.error.reason").contains("Could not initialize database schema")
                && textOf(root2, "home.error.reason").contains("database is locked") && textOf(root2, "home.error.where").contains("Your data has not been changed"),
                textOf(root2, "home.error.reason"));
        GuiAccept.check("8 no numbers are shown in the error state", find(root2, "home.value.totalValue") == null && !showing(root2, "home.empty"), "");
        click(root2, "home.error.retry");
        GuiAccept.check("8 Retry recovers once the problem is gone", waitFor(() -> {
            try { return showing(root2, "home.empty"); } catch (Exception e) { return false; }
        }, 8000) && attempts[0] == 2, "attempts=" + attempts[0]);
        GuiAccept.edt(h2[0]::dispose);

        // ================= 9. threading discipline across everything above
        List<String> onEdt = new ArrayList<>();
        synchronized (providerThreads) {
            for (String t : providerThreads) if (t.startsWith("AWT-EventQueue")) onEdt.add(t);
        }
        GuiAccept.check("9 no price-provider call ever ran on the Swing event thread", onEdt.isEmpty() && !providerThreads.isEmpty(), providerThreads.size() + " calls, on EDT: " + onEdt.size());
        GuiAccept.check("9 no uncaught exceptions on any thread", GuiAccept.edtErrors.isEmpty(), GuiAccept.edtErrors.toString());
        DatabaseManager.closeConnection();
        System.out.println("HOME SMOKE RESULT: " + GuiAccept.pass + " passed, " + GuiAccept.fail + " failed");
        System.exit(GuiAccept.fail == 0 ? 0 : 1);
    }
}

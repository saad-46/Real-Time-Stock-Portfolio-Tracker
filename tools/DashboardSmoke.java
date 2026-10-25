import com.portfolio.database.DatabaseManager;
import com.portfolio.database.WalletDAO;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.PriceUnavailableException;
import com.portfolio.service.StockPriceService;
import com.portfolio.ui.PremiumStockDashboard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated smoke test of the premium dashboard (scratch tooling, NOT human acceptance). Drives real Swing components
 * in-process against a temporary database with a fake quote provider; no network, no real database.
 * Compile together with GuiAccept (it reuses that class's helpers):
 *   javac -cp target/classes;target/lib/* -d out tools/GuiAccept.java tools/DashboardSmoke.java
 *   java -Dportfolio.db.path=TEMP.db -cp out;target/classes;target/lib/* DashboardSmoke
 */
public class DashboardSmoke {
    static volatile Double quote = 150.0;      // null = provider has no quote
    static volatile boolean noKey = false;     // true = provider reports a missing key

    static final StockPriceService PROVIDER = new StockPriceService() {
        @Override
        public Stock getStock(String symbol) {
            if (noKey) {
                throw new PriceUnavailableException(PriceUnavailableException.Reason.NO_KEY, "No API key configured (smoke test)");
            }
            if (quote == null || !symbol.equals("AAPL")) {
                return null;
            }
            Stock st = new Stock(symbol, symbol, quote);
            st.setCurrency("USD");
            return st;
        }

        @Override
        public boolean isValidStock(String symbol) {
            return getStock(symbol) != null;
        }
    };

    static double balance(PortfolioService s) {
        s.getWalletService().reload();
        return s.getWalletService().getBalance();
    }

    /** True if any table anywhere in the window has a cell whose text starts with {@code prefix}. */
    static boolean anyCellStartsWith(Container root, String prefix) throws Exception {
        return GuiAccept.edtGet(() -> {
            for (JTable t : GuiAccept.all(root, JTable.class)) {
                for (int r = 0; r < t.getRowCount(); r++) {
                    for (int c = 0; c < t.getColumnCount(); c++) {
                        Object v = t.getValueAt(r, c);
                        if (v != null && v.toString().startsWith(prefix)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }

    static int shares(PortfolioService s, String sym) {
        return s.getAllPortfolioItems().stream().filter(i -> i.getSymbol().equals(sym)).mapToInt(PortfolioItem::getQuantity).sum();
    }

    static void invokeLater(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, types);
        m.setAccessible(true);
        SwingUtilities.invokeLater(() -> {
            try {
                m.invoke(target, args);
            } catch (Exception e) {
                GuiAccept.edtErrors.add(method + ": " + e);
            }
        });
    }

    static void invokeNow(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        GuiAccept.edt(() -> {
            try {
                m.invoke(target);
            } catch (Exception e) {
                GuiAccept.edtErrors.add(method + ": " + e.getCause());
            }
        });
    }

    static void fill(JDialog d, int fieldIndex, String text) throws Exception {
        GuiAccept.edt(() -> GuiAccept.all(d.getContentPane(), JTextField.class).get(fieldIndex).setText(text));
    }

    /** Clicks without waiting: the handler may open a modal message dialog, which would block invokeAndWait. */
    static void click(JDialog d, String buttonText) throws Exception {
        JButton b = GuiAccept.edtGet(() -> GuiAccept.button(d.getContentPane(), buttonText));
        SwingUtilities.invokeLater(b::doClick);
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> GuiAccept.edtErrors.add(th.getName() + ": " + e));

        WalletDAO wallet = new WalletDAO();
        wallet.setCurrencyIfPristine("USD"); // the seeded holdings and fake quotes are dollars
        wallet.applyCashMovement(true, 10_000, "smoke funding");
        PortfolioService service = new PortfolioService(PROVIDER);
        service.buyStock(new PortfolioItem("AAPL", "Apple", 10, 100.0, 100.0)); // wallet 10000 -> 9000
        service.addToWatchlist("MSFT", "Microsoft");
        GuiAccept.check("0 seeded: 10 AAPL, wallet 9000", shares(service, "AAPL") == 10 && balance(service) == 9000.0, "");

        // ---- construct the real dashboard
        AtomicReference<PremiumStockDashboard> ref = new AtomicReference<>();
        GuiAccept.edt(() -> ref.set(new PremiumStockDashboard(service)));
        PremiumStockDashboard dash = ref.get();
        GuiAccept.edt(() -> dash.setVisible(true));
        Thread.sleep(1500);
        GuiAccept.check("1 dashboard builds with data and is visible", GuiAccept.edtGet(dash::isVisible)
                && GuiAccept.edtGet(() -> GuiAccept.all(dash.getContentPane(), JLabel.class).size()) > 20, "");

        // ---- every navigation entry
        List<Component> navs = GuiAccept.edtGet(() -> GuiAccept.all(dash.getContentPane(), Component.class).stream()
                .filter(c -> c.getClass().getSimpleName().equals("NavButton")).toList());
        for (Component nav : navs) {
            GuiAccept.edt(() -> nav.dispatchEvent(new MouseEvent(nav, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false)));
            Thread.sleep(150);
        }
        GuiAccept.check("2 all " + navs.size() + " navigation entries open without errors", navs.size() >= 8 && GuiAccept.edtErrors.isEmpty(),
                navs.size() + " " + GuiAccept.edtErrors);

        // ---- rebuild every page with data present (ticker, sparklines, charts, catalogue, wallet, analytics)
        invokeNow(dash, "refreshAllViews");
        Thread.sleep(800);
        GuiAccept.check("3 rebuilding all pages with data raises no errors", GuiAccept.edtErrors.isEmpty(), GuiAccept.edtErrors.toString());

        GuiAccept.check("3b holdings show 'Unavailable' until a real refresh (not a made-up 'Live Data')",
                anyCellStartsWith(dash.getContentPane(), "Unavailable") && !anyCellStartsWith(dash.getContentPane(), "Live Data"), "");

        // The default currency service has no exchange-rate key here, so display conversions of dollar holdings into the
        // rupee display currency must fall back to fixed rates AND say so.
        GuiAccept.check("3c approximate figures carry a '≈' mark in the tables", anyCellStartsWith(dash.getContentPane(), "≈"), "");
        GuiAccept.check("3c the 'APPROXIMATE RATES' banner is visible", GuiAccept.edtGet(() -> GuiAccept.all(dash.getContentPane(), JLabel.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().contains("APPROXIMATE RATES") && l.isShowing())), "");
        GuiAccept.check("3c the stat cards and note flag the totals as approximate", GuiAccept.edtGet(() -> GuiAccept.all(dash.getContentPane(), JLabel.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().startsWith("≈ Approximate: live exchange rates are unavailable"))), "");

        // ---- buy through the dialog: wallet debited
        invokeLater(dash, "showTradeDialog", new Class<?>[] { String.class, boolean.class }, "AAPL", true);
        JDialog buy = GuiAccept.waitDialog(5000);
        GuiAccept.check("4 buy dialog opens", buy != null && buy.getTitle().contains("Buy AAPL"), buy == null ? "" : buy.getTitle());
        fill(buy, 0, "2");
        fill(buy, 1, "100");
        click(buy, "Confirm Buy");
        String m = GuiAccept.dismiss();
        GuiAccept.check("4 buy succeeds, wallet 9000 -> 8800, shares 12", m.contains("successful") && balance(service) == 8800.0 && shares(service, "AAPL") == 12,
                m + " bal=" + balance(service) + " sh=" + shares(service, "AAPL"));

        // ---- buy more than the wallet holds: refused with a clear message, nothing changes
        invokeLater(dash, "showTradeDialog", new Class<?>[] { String.class, boolean.class }, "AAPL", true);
        JDialog buy2 = GuiAccept.waitDialog(5000);
        fill(buy2, 0, "1000");
        fill(buy2, 1, "100");
        click(buy2, "Confirm Buy");
        m = GuiAccept.dismiss();
        GuiAccept.check("5 insufficient funds is reported and nothing changes", m.contains("Insufficient wallet balance") && balance(service) == 8800.0 && shares(service, "AAPL") == 12, m);
        GuiAccept.edt(buy2::dispose);

        // ---- sell with a live quote: price shown, confirmed, executed at exactly that price
        quote = 150.0;
        invokeLater(dash, "showTradeDialog", new Class<?>[] { String.class, boolean.class }, "AAPL", false);
        JDialog sell = GuiAccept.waitDialog(5000);
        GuiAccept.check("6 sell dialog does not take a typed price", sell != null && !GuiAccept.edtGet(() -> GuiAccept.all(sell.getContentPane(), JTextField.class).get(1).isEnabled()), "");
        fill(sell, 0, "4");
        click(sell, "Confirm Sell");
        String confirm = GuiAccept.dismiss(); // the confirmation: first button is "Yes"
        GuiAccept.check("6 confirmation shows the live price, currency and wallet effect", confirm.contains("150.00 USD") && confirm.contains("600.00") && confirm.contains("Wallet credit"), confirm);
        String done = GuiAccept.dismiss();
        GuiAccept.check("6 sale executed at the quoted price: wallet 8800 -> 9400, shares 8", done.contains("Sold 4") && balance(service) == 9400.0 && shares(service, "AAPL") == 8,
                done + " bal=" + balance(service));
        GuiAccept.check("6 ledger price is exactly the quote", service.getAllTransactions().get(service.getAllTransactions().size() - 1).getPrice() == 150.0, "");

        // ---- sell with no valid quote: aborted, nothing changes
        quote = null;
        invokeLater(dash, "showTradeDialog", new Class<?>[] { String.class, boolean.class }, "AAPL", false);
        JDialog sell2 = GuiAccept.waitDialog(5000);
        fill(sell2, 0, "1");
        click(sell2, "Confirm Sell");
        m = GuiAccept.dismiss();
        GuiAccept.check("7 no live quote aborts the sale with a clear message", m.contains("cancelled") && m.contains("Nothing was changed")
                && balance(service) == 9400.0 && shares(service, "AAPL") == 8, m);
        GuiAccept.edt(sell2::dispose);

        // ---- refresh failure is reported, not shown as success
        quote = 150.0;
        noKey = true;
        invokeLater(dash, "refreshPrices", new Class<?>[0]);
        m = GuiAccept.dismiss();
        GuiAccept.check("8 refresh failure warns and names the cause", m.contains("could not be updated") && m.contains("No API key"), m);
        noKey = false;

        // ---- a successful refresh makes the status Fresh
        invokeLater(dash, "refreshPrices", new Class<?>[0]);
        m = GuiAccept.dismiss();
        GuiAccept.check("8b successful refresh reports success", m.contains("updated successfully"), m);
        Thread.sleep(500);
        GuiAccept.check("8b holdings now show 'Fresh'", anyCellStartsWith(dash.getContentPane(), "Fresh"), "");

        GuiAccept.check("9 no uncaught exceptions on any thread", GuiAccept.edtErrors.isEmpty(), GuiAccept.edtErrors.toString());
        GuiAccept.edt(dash::dispose);
        DatabaseManager.closeConnection();
        System.out.println("SMOKE RESULT: " + GuiAccept.pass + " passed, " + GuiAccept.fail + " failed");
        System.exit(GuiAccept.fail == 0 ? 0 : 1);
    }
}

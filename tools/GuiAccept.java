import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Automated GUI interaction harness (scratch, not part of the repo). Drives real Swing components
 * (doClick / setText) in-process against a temporary database. This is NOT human acceptance.
 */
public class GuiAccept {
    /** Fake quote provider: AAPL in USD at {@code quote}, or a missing-key failure when {@code noKey}. Never touches a network. */
    static volatile double quote = 130.0;
    static volatile boolean noKey = false;
    static final com.portfolio.service.StockPriceService PROVIDER = new com.portfolio.service.StockPriceService() {
        @Override public com.portfolio.model.Stock getStock(String symbol) {
            if (noKey) throw new com.portfolio.service.PriceUnavailableException(com.portfolio.service.PriceUnavailableException.Reason.NO_KEY, "No API key configured (harness)");
            if (!symbol.equals("AAPL")) return null;
            com.portfolio.model.Stock st = new com.portfolio.model.Stock(symbol, symbol, quote);
            st.setCurrency("USD");
            return st;
        }
        @Override public boolean isValidStock(String symbol) { return getStock(symbol) != null; }
    };

    static int pass = 0, fail = 0;
    static final List<String> edtErrors = Collections.synchronizedList(new ArrayList<>());

    static void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
    }

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static <T> T edtGet(Supplier<T> s) throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> box[0] = s.get());
        @SuppressWarnings("unchecked") T t = (T) box[0];
        return t;
    }

    static <T extends Component> List<T> all(Container c, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) out.add(type.cast(k));
            if (k instanceof Container cc) out.addAll(all(cc, type));
        }
        return out;
    }

    static JButton button(Container c, String textContains) {
        for (JButton b : all(c, JButton.class)) if (b.getText() != null && b.getText().contains(textContains)) return b;
        return null;
    }

    static JDialog waitDialog(long ms) throws Exception { return waitDialog(ms, false); }

    static JDialog waitDialog(long ms, boolean optionPaneOnly) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            JDialog d = edtGet(() -> {
                for (Window w : Window.getWindows()) if (w instanceof JDialog jd && jd.isVisible() && (!optionPaneOnly || !all(jd.getContentPane(), JOptionPane.class).isEmpty())) return jd;
                return null;
            });
            if (d != null) return d;
            Thread.sleep(50);
        }
        return null;
    }

    /** Wait for a JOptionPane message dialog, return "title | message", and click its first button. */
    static String dismiss() throws Exception {
        JDialog d = waitDialog(6000, true);
        if (d == null) return "<no dialog>";
        String s = edtGet(() -> {
            String msg = "";
            for (JOptionPane p : all(d.getContentPane(), JOptionPane.class)) {
                Object m = p.getMessage();
                msg = m instanceof Object[] arr ? Arrays.toString(arr) : String.valueOf(m);
            }
            return d.getTitle() + " | " + msg;
        });
        edt(() -> { JButton b = all(d.getContentPane(), JButton.class).get(0); b.doClick(); });
        waitGone(d);
        return s;
    }

    static void waitGone(JDialog d) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end && edtGet(d::isVisible)) Thread.sleep(50);
    }

    static void later(Runnable r) { SwingUtilities.invokeLater(r); }

    static String cell(JTable t, int row, int col) throws Exception {
        return edtGet(() -> String.valueOf(t.getValueAt(row, col)));
    }

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> edtErrors.add(th.getName() + ": " + e));
        Path csv = Path.of(args[0]);

        // Trades now move money through the wallet: fund the temporary database's wallet before driving the UI.
        com.portfolio.database.WalletDAO wdao = new com.portfolio.database.WalletDAO();
        wdao.setCurrencyIfPristine("USD"); // this window prices everything in dollars
        wdao.applyCashMovement(true, 1_000_000, "GUI harness funding");

        // ---- phase 1: empty database
        JFrame[] f = new JFrame[1];
        edt(() -> f[0] = new com.portfolio.ui.ModernPortfolioUI(new com.portfolio.service.PortfolioService(PROVIDER)));
        Container root = f[0].getContentPane();
        JTable table = all(root, JTable.class).get(0);
        String[] names = {"Add Stock", "Refresh Prices", "Sell Selected", "Ledger", "Charts"};
        StringBuilder missing = new StringBuilder();
        for (String n : names) if (button(root, n) == null) missing.append(n).append(';');
        check("1 toolbar shows all 5 buttons", missing.length() == 0, missing.toString());
        check("1 empty table at start", edtGet(table::getRowCount) == 0, "");

        // 2 invalid add
        later(() -> button(root, "Add Stock").doClick());
        JDialog add = waitDialog(5000);
        check("2 Add dialog opens", add != null, "");
        later(() -> {
            List<JTextField> tf = all(add.getContentPane(), JTextField.class);
            tf.get(0).setText("aapl"); tf.get(1).setText("0"); tf.get(2).setText("100");
            for (JButton b : all(add.getContentPane(), JButton.class)) if (b.getText().equals("Add Stock")) b.doClick();
        });
        String m = dismiss();
        check("2 quantity 0 rejected with message", m.contains("Quantity must be positive"), m);
        check("2 table still empty", edtGet(table::getRowCount) == 0, "");
        // dialog is still open: cancel by disposing
        edt(add::dispose); waitGone(add);

        // 3/4 valid adds
        for (String[] t : new String[][]{{"10", "100"}, {"10", "120"}}) {
            later(() -> button(root, "Add Stock").doClick());
            JDialog d = waitDialog(5000);
            later(() -> {
                List<JTextField> tf = all(d.getContentPane(), JTextField.class);
                tf.get(0).setText("aapl"); tf.get(1).setText(t[0]); tf.get(2).setText(t[1]);
                for (JButton b : all(d.getContentPane(), JButton.class)) if (b.getText().equals("Add Stock")) b.doClick();
            });
            dismiss(); // "Stock added successfully"
        }
        check("3 repeated buys merge into one row", edtGet(table::getRowCount) == 1, "rows=" + edtGet(table::getRowCount));
        check("4 qty 20, weighted avg $110.00", cell(table, 0, 2).equals("20") && cell(table, 0, 3).equals("$110.00"), cell(table, 0, 2) + " " + cell(table, 0, 3));
        check("4 price flagged 'Not refreshed'", cell(table, 0, 5).equals("Not refreshed"), cell(table, 0, 5));

        // 5 sell without selection
        later(() -> button(root, "Sell Selected").doClick());
        m = dismiss();
        check("5 sell with no selection explains itself", m.contains("Select a holding"), m);

        // 6 oversell
        edt(() -> table.setRowSelectionInterval(0, 0));
        later(() -> button(root, "Sell Selected").doClick());
        JDialog sd0 = waitDialog(5000); final JDialog sd = sd0;
        edt(() -> { List<JTextField> tf = all(sd.getContentPane(), JTextField.class); tf.get(0).setText("25"); });
        edt(() -> { for (JButton b : all(sd.getContentPane(), JButton.class)) if (b.getText().equals("OK")) b.doClick(); });
        m = dismiss();
        check("6 selling 25 of 20 is refused before any confirmation", m.contains("only 20 held") && m.contains("Nothing was changed"), m);
        check("6 holding unchanged", cell(table, 0, 2).equals("20"), cell(table, 0, 2));

        // 7 valid sell
        edt(() -> table.setRowSelectionInterval(0, 0));
        later(() -> button(root, "Sell Selected").doClick());
        JDialog sd2 = waitDialog(5000);
        edt(() -> { List<JTextField> tf = all(sd2.getContentPane(), JTextField.class); tf.get(0).setText("5"); });
        edt(() -> { for (JButton b : all(sd2.getContentPane(), JButton.class)) if (b.getText().equals("OK")) b.doClick(); });
        String confirm7 = dismiss(); // the confirmation (first button = Yes)
        check("7 confirmation shows the live price, currency and wallet effect", confirm7.contains("130.00 USD") && confirm7.contains("Wallet credit"), confirm7);
        m = dismiss();
        check("7 sale dialog reports realized P/L 100.00", m.contains("Realized P/L: 100.00"), m);
        check("7 qty now 15", cell(table, 0, 2).equals("15"), cell(table, 0, 2));
        String realized = edtGet(() -> {
            for (JLabel l : all(root, JLabel.class)) if ("$100.00".equals(l.getText())) return l.getText();
            return "";
        });
        check("7 Realized P/L card shows $100.00", realized.equals("$100.00"), realized);

        // 8 refresh without key
        noKey = true;
        JButton refresh = button(root, "Refresh");
        later(refresh::doClick);
        m = dismiss();
        check("8 refresh without key warns and names the cause", m.contains("could not be updated") && m.contains("No API key"), m);
        check("8 refresh button re-enabled", edtGet(refresh::isEnabled) && refresh.getText().equals("Refresh Prices"), refresh.getText());
        check("8 price still 'Not refreshed' (failure not shown as success)", cell(table, 0, 5).equals("Not refreshed"), cell(table, 0, 5));
        noKey = false;

        // 10 ledger
        later(() -> button(root, "Ledger").doClick());
        Thread.sleep(800);
        JFrame ledger = edtGet(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JFrame jf && jf.isVisible() && "Transaction Ledger".equals(jf.getTitle())) return jf;
            return null;
        });
        check("10 ledger window opens", ledger != null, "");
        JTable lt = all(ledger.getContentPane(), JTable.class).get(0);
        check("10 ledger lists 3 transactions, newest (SELL) first", edtGet(lt::getRowCount) == 3 && cell(lt, 0, 2).equals("SELL"), edtGet(lt::getRowCount) + " " + cell(lt, 0, 2));
        JTextField symF = all(ledger.getContentPane(), JTextField.class).get(0);
        JComboBox<?> typeBox = all(ledger.getContentPane(), JComboBox.class).get(0);
        edt(() -> { symF.setText("zzz"); button(ledger.getContentPane(), "Apply").doClick(); });
        check("10 symbol filter with no match -> 0 rows", edtGet(lt::getRowCount) == 0, "");
        edt(() -> { symF.setText(""); typeBox.setSelectedItem("SELL"); button(ledger.getContentPane(), "Apply").doClick(); });
        check("10 type filter SELL -> 1 row", edtGet(lt::getRowCount) == 1 && cell(lt, 0, 5).equals("$100.00"), cell(lt, 0, 5));
        List<JTextField> dates = all(ledger.getContentPane(), JTextField.class);
        edt(() -> { dates.get(1).setText("2025-13-01"); button(ledger.getContentPane(), "Apply").doClick(); });
        String status = edtGet(() -> { for (JLabel l : all(ledger.getContentPane(), JLabel.class)) if (l.getText() != null && l.getText().contains("must be a date")) return l.getText(); return ""; });
        check("10 invalid date is reported, not crashed", status.contains("From must be a date"), status);
        edt(() -> { dates.get(1).setText(""); typeBox.setSelectedItem("All"); button(ledger.getContentPane(), "Apply").doClick(); });

        // 11 CSV export through the real file chooser
        Files.deleteIfExists(csv);
        later(() -> button(ledger.getContentPane(), "Export CSV").doClick());
        JDialog cd = waitDialog(5000);
        boolean chooser = cd != null;
        if (chooser) edt(() -> { JFileChooser fc = all(cd.getContentPane(), JFileChooser.class).get(0); fc.setSelectedFile(csv.toFile()); fc.approveSelection(); });
        Thread.sleep(800);
        check("11 export writes a CSV file", chooser && Files.exists(csv), "chooser=" + chooser);
        if (Files.exists(csv)) {
            String content = Files.readString(csv);
            String[] lines = content.split("\r\n");
            check("11 CSV has header + 3 rows, SELL first with realized 100.00", lines.length == 4 && lines[0].startsWith("timestamp,symbol,type") && lines[1].contains(",SELL,5,130.00,100.00"), lines.length + " lines; " + (lines.length > 1 ? lines[1] : ""));
        }
        // overwrite prompt
        later(() -> button(ledger.getContentPane(), "Export CSV").doClick());
        JDialog cd2 = waitDialog(5000);
        edt(() -> { JFileChooser fc = all(cd2.getContentPane(), JFileChooser.class).get(0); fc.setSelectedFile(csv.toFile()); fc.approveSelection(); });
        m = dismiss(); // "exists. Overwrite?" confirm (Yes)
        check("11 overwrite asks for confirmation", m.contains("Overwrite"), m);
        Thread.sleep(300);
        edt(ledger::dispose);

        // 12 charts
        later(() -> button(root, "Charts").doClick());
        Thread.sleep(1200);
        boolean charts = edtGet(() -> {
            for (Window w : Window.getWindows()) if (w.isVisible() && w.getClass().getSimpleName().equals("ModernChartWindow")) return true;
            return false;
        });
        check("12 charts window opens", charts, "");
        edt(() -> { for (Window w : Window.getWindows()) if (w.getClass().getSimpleName().equals("ModernChartWindow")) w.dispose(); });

        // 13 sell remaining -> row disappears (quote equals the average cost, so realized P/L is zero)
        quote = 110.0;
        edt(() -> table.setRowSelectionInterval(0, 0));
        later(() -> button(root, "Sell Selected").doClick());
        JDialog sd3 = waitDialog(5000);
        edt(() -> { List<JTextField> tf = all(sd3.getContentPane(), JTextField.class); tf.get(0).setText("15"); });
        edt(() -> { for (JButton b : all(sd3.getContentPane(), JButton.class)) if (b.getText().equals("OK")) b.doClick(); });
        dismiss(); // confirmation
        m = dismiss();
        check("13 selling everything removes the row", edtGet(table::getRowCount) == 0 && m.contains("Realized P/L: 0.00"), m);

        // ---- phase 2: restart persistence + stale display
        edt(f[0]::dispose);
        com.portfolio.database.DatabaseManager.closeConnection();
        // seed a holding whose price was refreshed 3 days ago
        var svc = new com.portfolio.service.PortfolioService(new com.portfolio.service.AlphaVantageService(null));
        svc.buyStock(new com.portfolio.model.PortfolioItem("MSFT", "MSFT", 2, 50.0, 50.0));
        com.portfolio.database.DatabaseManager.getConnection().createStatement().execute(
                "UPDATE portfolio_items SET price_updated_at = " + (java.time.Instant.now().getEpochSecond() - 3 * 86400L) + " WHERE symbol='MSFT'");
        com.portfolio.database.DatabaseManager.closeConnection();
        JFrame[] f2 = new JFrame[1];
        edt(() -> f2[0] = new com.portfolio.ui.ModernPortfolioUI(new com.portfolio.service.PortfolioService(PROVIDER)));
        JTable t2 = all(f2[0].getContentPane(), JTable.class).get(0);
        check("14 restart shows persisted holding", edtGet(t2::getRowCount) == 1 && cell(t2, 0, 0).equals("MSFT"), "");
        check("14 3-day-old price is flagged STALE", cell(t2, 0, 5).contains("STALE"), cell(t2, 0, 5));
        String rc = edtGet(() -> { for (JLabel l : all(f2[0].getContentPane(), JLabel.class)) if ("$100.00".equals(l.getText())) return "ok"; return ""; });
        check("14 realized P/L ($100.00 from earlier sale) survives restart", rc.equals("ok"), rc);

        check("15 no uncaught exceptions on any thread", edtErrors.isEmpty(), edtErrors.toString());
        System.out.println("SUMMARY pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}

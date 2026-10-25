package com.portfolio.ui;

import com.portfolio.database.DatabaseManager;
import com.portfolio.service.AccountService;
import com.portfolio.service.MultiSourceStockService;
import com.portfolio.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Register a real account in a throw-away database through the real forms, sign in again with it, and build the real dashboard
 * the way the application does after a successful sign-in. No real database, no keys (the test run has none), no network.
 */
class RegistrationToDashboardTest {
    @TempDir Path tmp;

    @BeforeEach
    void open() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("flow.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static Component named(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container k) {
                Component f = named(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void waitFor(BooleanSupplier cond) throws Exception {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(30);
        }
        fail("condition not reached in time");
    }

    @Test
    void registerThenSignInThenTheRealDashboardOpens() throws Exception {
        AtomicReference<String> signedIn = new AtomicReference<>();
        AtomicReference<PremiumStockDashboard> dashboard = new AtomicReference<>();
        LandingFrame[] f = new LandingFrame[1];
        edt(() -> f[0] = new LandingFrame(new AccountService(), null, null, () -> "ai", url -> { }, (id, user) -> {
            signedIn.set(id + ":" + user);
            // what MainUI does after a successful sign-in: open the application window directly
            dashboard.set(new PremiumStockDashboard(new PortfolioService(new MultiSourceStockService())));
        }));
        try {
            // create the account through the real form (name, email, password only)
            edt(() -> {
                f[0].landing().createAccountButton().doClick();
                ((JTextField) named(f[0].auth(), "auth.name")).setText("Flow Tester");
                ((JTextField) named(f[0].auth(), "auth.email")).setText("flow.tester@example.invalid");
                ((JPasswordField) named(f[0].auth(), "auth.password")).setText("synthetic-pass-1");
                f[0].auth().submitButton().doClick();
            });
            waitFor(() -> signedIn.get() != null && dashboard.get() != null);
            assertTrue(signedIn.get().endsWith(":flow.tester@example.invalid"));
            assertNotNull(dashboard.get(), "the dashboard was built for the new account");
            edt(() -> dashboard.get().dispose());

            // the same credentials sign in afterwards; a wrong password does not
            signedIn.set(null);
            edt(() -> {
                f[0].auth().show(AuthPanel.Mode.SIGN_IN, null);
                ((JTextField) named(f[0].auth(), "auth.email")).setText("flow.tester@example.invalid");
                ((JPasswordField) named(f[0].auth(), "auth.password")).setText("wrong-password-1");
                f[0].auth().submitButton().doClick();
            });
            Thread.sleep(1500);
            assertNull(signedIn.get(), "a wrong password never signs in");
            edt(() -> {
                ((JTextField) named(f[0].auth(), "auth.email")).setText("Flow.Tester@example.invalid");
                ((JPasswordField) named(f[0].auth(), "auth.password")).setText("synthetic-pass-1");
                f[0].auth().submitButton().doClick();
            });
            waitFor(() -> signedIn.get() != null);
            edt(() -> {
                if (dashboard.get() != null) dashboard.get().dispose();
            });

            // a duplicate registration is refused
            signedIn.set(null);
            edt(() -> {
                f[0].auth().show(AuthPanel.Mode.REGISTER, null);
                ((JTextField) named(f[0].auth(), "auth.name")).setText("Someone Else");
                ((JTextField) named(f[0].auth(), "auth.email")).setText("flow.tester@example.invalid");
                ((JPasswordField) named(f[0].auth(), "auth.password")).setText("synthetic-pass-2");
                f[0].auth().submitButton().doClick();
            });
            Thread.sleep(1500);
            assertNull(signedIn.get(), "a second account with the same email is refused");
        } finally {
            edt(() -> {
                f[0].dispose();
                if (dashboard.get() != null) dashboard.get().dispose();
            });
        }
    }
}

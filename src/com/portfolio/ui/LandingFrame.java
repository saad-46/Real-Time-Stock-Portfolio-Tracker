package com.portfolio.ui;

import com.portfolio.service.AccountService;
import com.portfolio.service.ApiKeyManager;
import com.portfolio.service.MarketSource;
import com.portfolio.service.NewsService;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The first window: the landing page, with sign-in and account creation on a second card of the same window (no extra
 * windows). A successful sign-in hands over to the host, which opens the application. The light/dark button switches the theme,
 * saves the choice and rebuilds both cards so every colour follows.
 */
public class LandingFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    /** Called on the event thread after a successful sign-in or registration. */
    public interface SignedIn {
        void signedIn(int userId, String username);
    }

    private final AccountService accounts;
    private final MarketSource market;
    private final Supplier<NewsService.Live> news;
    private final Supplier<String> aiStatus;
    private final Consumer<String> linkOpener;
    private final SignedIn onSignedIn;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private StockVaultLandingPanel landing;
    private AuthPanel auth;
    private String shown = "landing";

    public LandingFrame(AccountService accounts, MarketSource market, Supplier<NewsService.Live> news, Supplier<String> aiStatus,
            Consumer<String> linkOpener, SignedIn onSignedIn) {
        super("StockVault");
        this.accounts = accounts;
        this.market = market;
        this.news = news;
        this.aiStatus = aiStatus;
        this.linkOpener = linkOpener;
        this.onSignedIn = onSignedIn;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        Theme.style(getRootPane());
        setContentPane(root);
        build();
    }

    /** (Re)create the two cards with the current palette. */
    private void build() {
        if (landing != null) {
            landing.close();
        }
        root.removeAll();
        Theme.style(getRootPane());
        landing = new StockVaultLandingPanel(new StockVaultLandingPanel.Listener() {
            @Override public void onSignIn() { showAuth(AuthPanel.Mode.SIGN_IN); }

            @Override public void onCreateAccount() { showAuth(AuthPanel.Mode.REGISTER); }

            @Override public void onToggleTheme() { toggleTheme(); }
        }, market, news, aiStatus, linkOpener);

        auth = new AuthPanel(accounts, new AuthPanel.Listener() {
            @Override public void onAuthenticated(int userId, String username) {
                onSignedIn.signedIn(userId, username);
            }

            @Override public void onBack() { showLanding(); }
        });
        root.add(landing, "landing");
        root.add(auth, "auth");
        // Escape on the sign-in card goes back to the landing page
        auth.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        auth.getActionMap().put("back", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showLanding(); }
        });
        shown = "landing";
        cards.show(root, "landing");
        root.revalidate();
        root.repaint();
    }

    /** Light/dark: switch, remember, rebuild, and refill the market cards from the cache (no needless provider calls). */
    void toggleTheme() {
        boolean dark = !ThemeMode.isDark();
        ThemeMode.apply(dark);
        ThemeMode.savePreference(dark);
        build();
        SwingUtilities.updateComponentTreeUI(this);
        landing.loadMarket(false);
    }

    /** Begin loading the market cards (off the event thread). Call after the window is visible. */
    public void start() {
        landing.loadMarket(false);
    }

    /** Stops background loading before the window goes away. */
    @Override
    public void dispose() {
        if (landing != null) {
            landing.close();
        }
        super.dispose();
    }

    public StockVaultLandingPanel landing() {
        return landing;
    }

    public AuthPanel auth() {
        return auth;
    }

    /** "landing" or "auth". */
    public String shownCard() {
        return shown;
    }

    private void showAuth(AuthPanel.Mode mode) {
        auth.show(mode, null);
        shown = "auth";
        cards.show(root, "auth");
    }

    private void showLanding() {
        shown = "landing";
        cards.show(root, "landing");
        landing.signInButton().requestFocusInWindow();
    }

    /** One honest line about AI insights: configuration only. A key being present does not mean the provider accepts it. */
    public static String aiStatusLine() {
        return ApiKeyManager.isConfigured(ApiKeyManager.getGroqKey())
                ? "AI insights: key configured (checked when you first use it)"
                : "AI insights: not configured (GROQ_API_KEY)";
    }

    /** Opens a headline in the default browser. Only ever called after the user clicked that headline. */
    public static void openInBrowser(String url) {
        try {
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))
                    && java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception e) {
            // the headline simply does not open
        }
    }
}

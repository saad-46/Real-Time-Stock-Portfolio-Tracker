package com.portfolio.ui;

import com.portfolio.AppPaths;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Explains, without ever showing a key, where StockVault looks for API keys and which names it reads. Opening the data folder
 * happens only when the user presses the button.
 */
public final class ConfigHelp {
    private ConfigHelp() {
    }

    /** The help text (testable; contains key names and file locations only). */
    public static String text() {
        Path project = AppPaths.resolveDotEnvFile();
        return "StockVault reads API keys, in this order, from:\n"
                + "  1. Windows environment variables\n"
                + "  2. stockvault.local.properties (" + AppPaths.resolveSettingsFile() + ")\n"
                + "  3. a .env file: " + (project == null ? "the project folder (not available when running outside the project)"
                        : project) + "\n"
                + "     or, for a packaged app, " + AppPaths.userDotEnvFile() + "\n\n"
                + "Market indices need at least one of these (one NAME=value per line, no quotes needed):\n"
                + "  TWELVEDATA_API_KEY   FINNHUB_API_KEY   ALPHAVANTAGE_API_KEY\n"
                + "Headlines need GNEWS_API_KEY or NEWSDATA_API_KEY. AI insights need GROQ_API_KEY.\n\n"
                + "Free plans often exclude index data; an index a provider does not offer is reported as unsupported.\n"
                + "Keys are read when StockVault starts, so restart after editing. Never share or commit these files.";
    }

    public static void show(Component parent) {
        Object[] options = { "Open data folder", "Close" };
        int choice = JOptionPane.showOptionDialog(parent, text(), "Set up API keys", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) {
            try {
                Path dir = AppPaths.userDataDir();
                Files.createDirectories(dir);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir.toFile());
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, "The folder could not be opened (" + e.getClass().getSimpleName() + ").",
                        "Set up API keys", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
}

package com.portfolio.ui;

import com.portfolio.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The light/dark choice: one place that switches FlatLaf (so standard dialogs and controls follow), the home-screen {@link Theme}
 * and the landing page's {@link LandingPalette}, and remembers the choice in a small properties file in the per-user data folder
 * (the {@code stockvault.ui} system property points elsewhere, which tests and the launcher's diagnostic mode use). Windows that
 * are already built keep the colours they were built with, so the host rebuilds them after a change.
 *
 * <p>The large dashboard window ({@code PremiumStockDashboard}) has its own fixed dark palette and does not follow this setting.
 */
public final class ThemeMode {
    static final String KEY = "theme";
    private static boolean dark = true;

    private ThemeMode() {
    }

    public static boolean isDark() {
        return dark;
    }

    /** Switch everything that follows the setting. Call on the event thread. */
    public static void apply(boolean useDark) {
        dark = useDark;
        Theme.setDark(useDark);
        LandingPalette.setDark(useDark);
        try {
            if (useDark) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
            com.formdev.flatlaf.FlatLaf.updateUI();
        } catch (RuntimeException e) {
            // the look and feel is a convenience: the custom-painted parts already follow the palette
        }
    }

    /** The stored choice; dark when nothing is stored or the file cannot be read. */
    public static boolean loadPreference() {
        Path file = file();
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Properties p = new Properties();
                p.load(in);
                return !"light".equalsIgnoreCase(p.getProperty(KEY, "dark").trim());
            } catch (IOException | RuntimeException e) {
                return true;
            }
        }
        return true;
    }

    /** Remember the choice. Failure to write is not an error (the choice then lasts for this run only). */
    public static boolean savePreference(boolean useDark) {
        Path file = file();
        if (file == null) {
            return false;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties p = new Properties();
            p.setProperty(KEY, useDark ? "dark" : "light");
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "StockVault display preferences");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static Path file() {
        String override = System.getProperty("stockvault.ui");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return AppPaths.userDataDir().resolve("ui.properties");
    }
}

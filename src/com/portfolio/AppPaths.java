package com.portfolio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where StockVault keeps its files, independent of the directory the program was started from (a packaged
 * executable's working directory is not the user's).
 *
 * Database, in order: the {@code portfolio.db.path} system property; the {@code STOCKVAULT_DB} environment variable; an
 * existing {@code portfolio.db} in the working directory (so installs that already work keep working); otherwise
 * {@code portfolio.db} in the per-user data folder.
 *
 * Local settings file (API keys), in order: system property {@code stockvault.config}; environment variable
 * {@code STOCKVAULT_CONFIG}; an existing {@code stockvault.local.properties} in the working directory; otherwise the
 * file of that name in the per-user data folder.
 *
 * Per-user data folder: {@code %APPDATA%\StockVault} on Windows, {@code ~/Library/Application Support/StockVault} on macOS,
 * {@code $XDG_DATA_HOME/stockvault} or {@code ~/.local/share/stockvault} elsewhere.
 */
public final class AppPaths {
    public static final String DB_FILE = "portfolio.db";
    public static final String SETTINGS_FILE = "stockvault.local.properties";
    public static final String DOTENV_FILE = ".env";

    private AppPaths() {
    }

    public static Path userDataDir() {
        return userDataDir(System.getProperty("os.name", ""), System.getenv("APPDATA"), System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home", "."));
    }

    static Path userDataDir(String osName, String appData, String xdgDataHome, String home) {
        String os = osName.toLowerCase();
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            return Paths.get(appData, "StockVault");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "StockVault");
        }
        if (xdgDataHome != null && !xdgDataHome.isBlank()) {
            return Paths.get(xdgDataHome, "stockvault");
        }
        return os.contains("win") ? Paths.get(home, "StockVault") : Paths.get(home, ".local", "share", "stockvault");
    }

    /** The database file to use (see class comment). Does not create anything. */
    public static String resolveDatabasePath() {
        return resolveDatabasePath(System.getProperty("portfolio.db.path"), System.getenv("STOCKVAULT_DB"),
                Paths.get("").toAbsolutePath(), userDataDir());
    }

    static String resolveDatabasePath(String property, String env, Path workingDir, Path userDir) {
        if (property != null && !property.isBlank()) {
            return property;
        }
        if (env != null && !env.isBlank()) {
            return env;
        }
        Path legacy = workingDir.resolve(DB_FILE);
        if (Files.isRegularFile(legacy)) {
            return DB_FILE; // relative, exactly as before
        }
        return userDir.resolve(DB_FILE).toString();
    }

    /**
     * The project-root {@code .env} file, or null if there is none to consult. The project root is found from where this
     * program's code lives (the folder above {@code target/}, i.e. the nearest parent holding {@code pom.xml}), never from the
     * launch directory. The system property {@code stockvault.env} overrides it (used by tests). A packaged app that is not
     * inside a project tree has no project root, so no {@code .env} is read. May name a file that does not exist.
     */
    public static Path resolveDotEnvFile() {
        Path code = null;
        try {
            code = Paths.get(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            // no usable code location: no project root
        }
        return resolveDotEnvFile(System.getProperty("stockvault.env"), code);
    }

    static Path resolveDotEnvFile(String property, Path codeLocation) {
        if (property != null && !property.isBlank()) {
            return Paths.get(property);
        }
        Path dir = codeLocation == null ? null : (Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent());
        for (int i = 0; dir != null && i < 6; i++, dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("pom.xml"))) {
                return dir.resolve(DOTENV_FILE);
            }
        }
        return null;
    }

    /** A {@code .env} in the per-user data folder: the place for keys when the program runs outside the project tree. May not exist. */
    public static Path userDotEnvFile() {
        return userDataDir().resolve(DOTENV_FILE);
    }

    /** The local settings (API keys) file to read (see class comment). May not exist. */
    public static Path resolveSettingsFile() {
        return resolveSettingsFile(System.getProperty("stockvault.config"), System.getenv("STOCKVAULT_CONFIG"),
                Paths.get("").toAbsolutePath(), userDataDir());
    }

    static Path resolveSettingsFile(String property, String env, Path workingDir, Path userDir) {
        if (property != null && !property.isBlank()) {
            return Paths.get(property);
        }
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        Path local = workingDir.resolve(SETTINGS_FILE);
        return Files.isRegularFile(local) ? local : userDir.resolve(SETTINGS_FILE);
    }
}

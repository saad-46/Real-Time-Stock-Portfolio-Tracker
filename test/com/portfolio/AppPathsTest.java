package com.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The app must not depend on the directory it was started from. */
class AppPathsTest {
    @Test
    void dotEnvIsFoundFromTheProjectRootNotTheLaunchDirectory(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("proj");
        Files.createDirectories(project.resolve("target").resolve("classes"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        assertEquals(project.resolve(".env"), AppPaths.resolveDotEnvFile(null, project.resolve("target").resolve("classes")));
        Path jar = project.resolve("target").resolve("app.jar");
        Files.writeString(jar, "x");
        assertEquals(project.resolve(".env"), AppPaths.resolveDotEnvFile("", jar));
        assertNull(AppPaths.resolveDotEnvFile(null, tmp.resolve("elsewhere")), "no project root, no .env");
        assertNull(AppPaths.resolveDotEnvFile(null, null));
        assertEquals(Paths.get("x", ".env"), AppPaths.resolveDotEnvFile(Paths.get("x", ".env").toString(), jar));
    }

    @TempDir Path tmp;

    @Test
    void explicitSettingsBeatEverythingElse() throws Exception {
        Path cwd = Files.createDirectory(tmp.resolve("cwd"));
        Files.createFile(cwd.resolve("portfolio.db"));
        assertEquals("P.db", AppPaths.resolveDatabasePath("P.db", "E.db", cwd, tmp.resolve("user")));
        assertEquals("E.db", AppPaths.resolveDatabasePath(null, "E.db", cwd, tmp.resolve("user")));
        assertEquals("E.db", AppPaths.resolveDatabasePath("  ", "E.db", cwd, tmp.resolve("user")));
    }

    @Test
    void anExistingDatabaseInTheWorkingDirectoryKeepsWorking() throws Exception {
        Path cwd = Files.createDirectory(tmp.resolve("cwd"));
        Files.createFile(cwd.resolve("portfolio.db"));
        assertEquals("portfolio.db", AppPaths.resolveDatabasePath(null, null, cwd, tmp.resolve("user")));
    }

    @Test
    void otherwiseTheDatabaseLivesInThePerUserFolderNotInWhateverDirectoryTheAppStartedIn() throws Exception {
        Path cwd = Files.createDirectory(tmp.resolve("somewhere-else"));
        Path user = tmp.resolve("user");
        String chosen = AppPaths.resolveDatabasePath(null, null, cwd, user);
        assertEquals(user.resolve("portfolio.db").toString(), chosen);
        assertFalse(Paths.get(chosen).startsWith(cwd));
    }

    @Test
    void settingsFileFollowsTheSameIdea() throws Exception {
        Path cwd = Files.createDirectory(tmp.resolve("cwd"));
        Path user = tmp.resolve("user");
        assertEquals(user.resolve("stockvault.local.properties"), AppPaths.resolveSettingsFile(null, null, cwd, user));
        Files.createFile(cwd.resolve("stockvault.local.properties"));
        assertEquals(cwd.resolve("stockvault.local.properties"), AppPaths.resolveSettingsFile(null, null, cwd, user));
        assertEquals(Paths.get("X.properties"), AppPaths.resolveSettingsFile("X.properties", "Y", cwd, user));
        assertEquals(Paths.get("Y"), AppPaths.resolveSettingsFile(null, "Y", cwd, user));
    }

    @Test
    void userDataFolderPerPlatform() {
        assertEquals(Paths.get("C:\\Users\\a\\AppData\\Roaming", "StockVault"),
                AppPaths.userDataDir("Windows 11", "C:\\Users\\a\\AppData\\Roaming", null, "C:\\Users\\a"));
        assertEquals(Paths.get("/Users/a", "Library", "Application Support", "StockVault"),
                AppPaths.userDataDir("Mac OS X", null, null, "/Users/a"));
        assertEquals(Paths.get("/xdg", "stockvault"), AppPaths.userDataDir("Linux", null, "/xdg", "/home/a"));
        assertEquals(Paths.get("/home/a", ".local", "share", "stockvault"), AppPaths.userDataDir("Linux", null, null, "/home/a"));
    }
}

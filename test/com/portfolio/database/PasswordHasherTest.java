package com.portfolio.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Password storage: PBKDF2 for new hashes, the old unsalted SHA-256 still accepted and upgraded after a correct sign-in. */
class PasswordHasherTest {
    @TempDir Path tmp;

    @BeforeEach
    void open() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("users.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    @Test
    void newHashesAreSaltedPbkdf2AndVerifyOnlyTheRightPassword() {
        String a = PasswordHasher.hash("synthetic-pass-1");
        String b = PasswordHasher.hash("synthetic-pass-1");
        assertTrue(a.startsWith("pbkdf2-sha256$"));
        assertNotEquals(a, b, "a random salt makes equal passwords hash differently");
        assertFalse(a.contains("synthetic-pass-1"));
        assertTrue(PasswordHasher.verify("synthetic-pass-1", a));
        assertFalse(PasswordHasher.verify("synthetic-pass-2", a));
        assertFalse(PasswordHasher.needsUpgrade(a));
    }

    @Test
    void legacyHashesStillVerifyAndAreMarkedForUpgrade() {
        String legacy = PasswordHasher.legacySha256("synthetic-pass-1");
        assertEquals(64, legacy.length());
        assertTrue(PasswordHasher.verify("synthetic-pass-1", legacy));
        assertFalse(PasswordHasher.verify("nope-nope-nope", legacy));
        assertTrue(PasswordHasher.needsUpgrade(legacy));
    }

    @Test
    void malformedStoredValuesNeverVerify() {
        for (String bad : new String[] { null, "", "pbkdf2-sha256$", "pbkdf2-sha256$x$y$z", "pbkdf2-sha256$0$AA==$AA==", "pbkdf2-sha256$99999999$AA==$AA==" }) {
            assertFalse(PasswordHasher.verify("synthetic-pass-1", bad), String.valueOf(bad));
        }
    }

    @Test
    void aLegacyAccountSignsInAndIsUpgradedInTheDatabaseOnlyAfterTheCorrectPassword() throws Exception {
        assertNotNull(DatabaseManager.getConnection());
        try (PreparedStatement p = DatabaseManager.requireConnection()
                .prepareStatement("INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
            p.setString(1, "olduser");
            p.setString(2, PasswordHasher.legacySha256("synthetic-pass-1"));
            p.executeUpdate();
        }
        assertEquals(-1, DatabaseManager.loginUser("olduser", "wrong-password"));
        assertEquals(PasswordHasher.legacySha256("synthetic-pass-1"), stored("olduser"), "a failed sign-in changes nothing");
        assertTrue(DatabaseManager.loginUser("olduser", "synthetic-pass-1") > 0);
        String after = stored("olduser");
        assertTrue(after.startsWith("pbkdf2-sha256$"), "upgraded after the correct password");
        assertTrue(DatabaseManager.loginUser("olduser", "synthetic-pass-1") > 0, "still signs in after the upgrade");
        assertEquals(-1, DatabaseManager.loginUser("olduser", "wrong-password"));
    }

    @Test
    void newRegistrationsStorePbkdf2() throws Exception {
        assertTrue(DatabaseManager.registerUser("newuser", "synthetic-pass-1", null, "newuser") > 0);
        assertTrue(stored("newuser").startsWith("pbkdf2-sha256$"));
        assertTrue(DatabaseManager.loginUser("newuser", "synthetic-pass-1") > 0);
    }

    private static String stored(String user) throws Exception {
        try (Statement st = DatabaseManager.requireConnection().createStatement();
                ResultSet rs = st.executeQuery("SELECT password_hash FROM users WHERE username='" + user + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}

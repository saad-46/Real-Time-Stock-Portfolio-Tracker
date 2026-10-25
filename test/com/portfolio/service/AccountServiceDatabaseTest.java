package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** The real account functions against a throw-away database file in a temp folder (never a real portfolio). */
class AccountServiceDatabaseTest {
    @TempDir Path tmp;

    @BeforeEach
    void open() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("accounts.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void createSignInWrongPasswordAndDuplicateAgainstTheRealUserTable() throws Exception {
        AccountService s = new AccountService(DatabaseManager::loginUser, DatabaseManager::registerUser, 0);
        assertTrue(s.createAccount("Test User", "Tester_1@example.invalid", pw("synthetic-pass-1")).ok());
        assertEquals(AccountService.Status.USERNAME_TAKEN, s.createAccount("Test User", "tester_1@example.invalid", pw("synthetic-pass-1")).status(),
                "the email is unique regardless of case");
        assertTrue(s.signIn("TESTER_1@example.invalid", pw("synthetic-pass-1")).ok(), "sign-in by email, any case");
        assertEquals(AccountService.Status.BAD_CREDENTIALS, s.signIn("tester_1@example.invalid", pw("synthetic-pass-2")).status());
        assertEquals(AccountService.Status.BAD_CREDENTIALS, s.signIn("nobody@example.invalid", pw("synthetic-pass-1")).status());

        // the password itself is never stored; the name and email are
        try (Statement st = DatabaseManager.requireConnection().createStatement();
                ResultSet rs = st.executeQuery("SELECT password_hash, full_name, email FROM users WHERE username='tester_1@example.invalid'")) {
            assertTrue(rs.next());
            assertNotEquals("synthetic-pass-1", rs.getString(1));
            assertFalse(rs.getString(1).contains("synthetic-pass-1"));
            assertTrue(rs.getString(1).startsWith("pbkdf2-sha256$"));
            assertEquals("Test User", rs.getString(2));
            assertEquals("tester_1@example.invalid", rs.getString(3));
        }
    }
}

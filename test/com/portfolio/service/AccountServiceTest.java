package com.portfolio.service;

import com.portfolio.service.AccountService.Result;
import com.portfolio.service.AccountService.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Input checks and routing around the existing user table functions. Synthetic values only; no database. */
class AccountServiceTest {
    private final List<String> loginCalls = new ArrayList<>();
    private final List<String> registerCalls = new ArrayList<>();

    private AccountService service(int loginId, int registerId) {
        return new AccountService((u, p) -> { loginCalls.add(u); return loginId; },
                (u, p, e, n) -> { registerCalls.add(u + "|" + e + "|" + n); return registerId; }, 0);
    }

    @Test
    void signInSucceedsOnlyWhenTheBackendAcceptsAndWipesThePassword() {
        char[] pw = "synthetic-pass-1".toCharArray();
        Result ok = service(7, -1).signIn("  Alice@Example.invalid ", pw);
        assertTrue(ok.ok());
        assertEquals(7, ok.userId());
        assertEquals(List.of("alice@example.invalid"), loginCalls, "an email is trimmed and lower-cased like the stored one");
        assertEquals('\0', pw[0], "the caller's password array is cleared");

        Result bad = service(-1, -1).signIn("alice@example.invalid", "wrong-password".toCharArray());
        assertEquals(Status.BAD_CREDENTIALS, bad.status());
        assertFalse(bad.message().contains("wrong-password"));
        loginCalls.clear();
        service(1, 1).signIn("OldUser", "x".toCharArray());
        assertEquals(List.of("OldUser"), loginCalls, "an older account's plain username is looked up as typed");
    }

    @Test
    void emptyInputNeverReachesTheBackend() {
        AccountService s = service(1, 1);
        assertEquals(Status.INVALID_INPUT, s.signIn("", "x".toCharArray()).status());
        assertEquals(Status.INVALID_INPUT, s.signIn("a@b.co", new char[0]).status());
        assertEquals(Status.INVALID_INPUT, s.signIn(null, null).status());
        assertTrue(loginCalls.isEmpty());
    }

    @Test
    void registrationNeedsAValidNameEmailAndPasswordAndNothingElse() {
        AccountService s = service(1, 5);
        assertEquals(Status.INVALID_INPUT, s.createAccount("", "a@b.co", pw("long-enough-1")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("A", "a@b.co", pw("long-enough-1")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("<script>", "a@b.co", pw("long-enough-1")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("Asha Rao", "", pw("long-enough-1")).status(), "the email is required");
        assertEquals(Status.INVALID_INPUT, s.createAccount("Asha Rao", "not-an-email", pw("long-enough-1")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("Asha Rao", "a@b", pw("long-enough-1")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("Asha Rao", "a@b.co", pw("short")).status());
        assertEquals(Status.INVALID_INPUT, s.createAccount("Asha Rao", "a@b.co", null).status());
        assertTrue(registerCalls.isEmpty(), "invalid input is never stored");

        Result ok = s.createAccount("  Asha   Rao ", "Asha@Example.INVALID", pw("long-enough-1"));
        assertTrue(ok.ok());
        assertEquals(List.of("asha@example.invalid|asha@example.invalid|Asha Rao"), registerCalls,
                "the lower-cased email is the sign-in name and is stored with the tidied name");
        assertTrue(s.createAccount("Renée O'Neil-Smith", "r@example.invalid", pw("long-enough-1")).ok(), "accents, apostrophes and hyphens are fine");
    }

    @Test
    void takenEmailAndBackendExceptionsAreReportedWithoutLeaking() {
        Result taken = service(1, -1).createAccount("Asha Rao", "a@b.co", pw("long-enough-1"));
        assertEquals(Status.USERNAME_TAKEN, taken.status());
        assertTrue(taken.message().toLowerCase().contains("email"));

        AccountService boom = new AccountService((u, p) -> { throw new IllegalStateException("secret-detail " + p); },
                (u, p, e, n) -> { throw new IllegalStateException("secret-detail " + p); }, 0);
        Result r1 = boom.signIn("a@b.co", "synthetic-pass-1".toCharArray());
        Result r2 = boom.createAccount("Asha Rao", "a@b.co", pw("synthetic-pass-1"));
        assertEquals(Status.UNAVAILABLE, r1.status());
        assertEquals(Status.UNAVAILABLE, r2.status());
        assertFalse(r1.message().contains("synthetic-pass-1") || r1.message().contains("secret-detail"));
        assertFalse(r2.message().contains("synthetic-pass-1") || r2.message().contains("secret-detail"));
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }
}

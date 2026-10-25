package com.portfolio.service;

import com.portfolio.database.DatabaseManager;

import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * Sign-in and account creation for the landing page, on top of the existing {@link DatabaseManager#loginUser} and
 * {@link DatabaseManager#registerUser} (the {@code users} table). It adds only input checks and a short pause after a
 * wrong password; it does not store or log a password, and it zeroes the characters it was given.
 *
 * Known limitation (inherited, not introduced here): the stored hash is an unsalted SHA-256. See docs/audit.
 */
public final class AccountService {
    public enum Status { OK, INVALID_INPUT, BAD_CREDENTIALS, USERNAME_TAKEN, UNAVAILABLE }

    /** Outcome shown to the user; the message never contains the password. */
    public record Result(Status status, String message, int userId) {
        public boolean ok() { return status == Status.OK; }
    }

    public static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 128;

    private final BiFunction<String, String, Integer> login;
    private final RegisterFn register;
    private final long failureDelayMillis;

    /** Registration seam: (username, password, email, full name) to user id, -1 if taken or failed. */
    @FunctionalInterface
    public interface RegisterFn {
        int register(String username, String password, String email, String fullName);
    }

    /** The real accounts in the application database. */
    public AccountService() {
        this(DatabaseManager::loginUser, DatabaseManager::registerUser, 600);
    }

    public AccountService(BiFunction<String, String, Integer> login, RegisterFn register, long failureDelayMillis) {
        this.login = login;
        this.register = register;
        this.failureDelayMillis = failureDelayMillis;
    }

    /** Blocking (opens the database on first use): call off the Swing event thread. {@code login} is the email (or an older account's username). */
    public Result signIn(String login, char[] password) {
        try {
            String name = login == null ? "" : login.strip();
            if (name.contains("@")) {
                name = name.toLowerCase(java.util.Locale.ROOT); // accounts store the email in lower case
            }
            if (name.isEmpty() || password == null || password.length == 0) {
                return new Result(Status.INVALID_INPUT, "Enter your email and password.", -1);
            }
            int id = this.login.apply(name, new String(password));
            if (id > 0) {
                return new Result(Status.OK, "Signed in.", id);
            }
            pause();
            return new Result(Status.BAD_CREDENTIALS, "Email or password is incorrect.", -1);
        } catch (RuntimeException e) {
            return new Result(Status.UNAVAILABLE, "Sign-in is unavailable: " + e.getClass().getSimpleName(), -1);
        } finally {
            wipe(password);
        }
    }

    /**
     * Create an account from a name, an email and a password (nothing else is asked). The email, in lower case, is the sign-in
     * name. Blocking (writes to the database): call off the Swing event thread.
     */
    public Result createAccount(String fullName, String email, char[] password) {
        try {
            String name = fullName == null ? "" : fullName.strip().replaceAll("\\s+", " ");
            String mail = email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT);
            if (!name.matches("\\p{L}[\\p{L} .'\\-]{1,59}")) {
                return new Result(Status.INVALID_INPUT, "Enter your name (2 to 60 letters).", -1);
            }
            if (mail.length() > 120 || !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}")) {
                return new Result(Status.INVALID_INPUT, "Enter a valid email address.", -1);
            }
            if (password == null || password.length < MIN_PASSWORD || password.length > MAX_PASSWORD) {
                return new Result(Status.INVALID_INPUT, "Password must be " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters.", -1);
            }
            int id = register.register(mail, new String(password), mail, name);
            if (id > 0) {
                return new Result(Status.OK, "Account created.", id);
            }
            return new Result(Status.USERNAME_TAKEN, "An account with this email already exists, or it could not be saved.", -1);
        } catch (RuntimeException e) {
            return new Result(Status.UNAVAILABLE, "Account creation is unavailable: " + e.getClass().getSimpleName(), -1);
        } finally {
            wipe(password);
        }
    }

    private void pause() {
        if (failureDelayMillis > 0) {
            try {
                Thread.sleep(failureDelayMillis); // slows down password guessing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void wipe(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }
}

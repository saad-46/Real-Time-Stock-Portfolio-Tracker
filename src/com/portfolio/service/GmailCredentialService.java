package com.portfolio.service;

import com.portfolio.database.DatabaseManager;

import java.util.Optional;

/**
 * Keeps the Gmail App Password in a {@link CredentialStore} and only a non-secret reference in the ordinary settings row.
 * A value in {@code email_settings.app_password} that starts with {@link #REFERENCE_PREFIX} is a reference; anything else is
 * a legacy plaintext password that {@link #migrateLegacy()} moves. No method logs or returns a secret except {@link #load()},
 * which exists for the mail sender.
 */
public final class GmailCredentialService {
    public static final String TARGET = "StockVault/GmailAppPassword";
    public static final String REFERENCE_PREFIX = "credman:";
    public static final String REFERENCE = REFERENCE_PREFIX + TARGET;

    public enum Status { OK, NOT_FOUND, UNAVAILABLE, FAILED }

    /** Outcome with a message safe to show (never contains the secret). */
    public record Result(Status status, String message) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    public enum Migration { NOTHING_TO_MIGRATE, ALREADY_MIGRATED, MIGRATED, FAILED }

    public record MigrationResult(Migration outcome, String message) {
    }

    private final CredentialStore store;

    public GmailCredentialService(CredentialStore store) {
        this.store = store;
    }

    public static boolean isReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX);
    }

    /** Saves (or replaces) the password and checks it by reading it back. The settings row is not touched. */
    public Result save(String email, String password) {
        if (password == null || password.isBlank()) {
            return new Result(Status.FAILED, "No password was entered.");
        }
        try {
            store.write(TARGET, email, password);
            Optional<String> back = store.read(TARGET);
            if (back.isEmpty() || !back.get().equals(password)) {
                return new Result(Status.FAILED, "The password could not be verified after saving, so it was not kept.");
            }
            return new Result(Status.OK, "Saved in Windows Credential Manager.");
        } catch (CredentialStoreException e) {
            return failure(e);
        }
    }

    /** The stored password for the mail sender, or empty when none is stored. */
    public Optional<String> load() throws CredentialStoreException {
        return store.read(TARGET);
    }

    public boolean isStored() {
        try {
            return store.read(TARGET).isPresent();
        } catch (CredentialStoreException e) {
            return false;
        }
    }

    /**
     * Gives the mail service the stored password, but only when it has none: an explicitly configured GMAIL_APP_PASSWORD
     * (environment, local properties or .env) always takes precedence. Returns true when the stored password was applied.
     */
    public boolean applyStoredPassword(EmailService mail, String savedAddress) throws CredentialStoreException {
        if (mail.isConfigured()) {
            return false;
        }
        Optional<String> stored = load();
        if (stored.isEmpty()) {
            return false;
        }
        mail.configure(EmailService.resolveSender(savedAddress), stored.get());
        return true;
    }

    /** Removes the stored password and the reference. The value is never shown. */
    public Result remove() {
        try {
            store.delete(TARGET);
        } catch (CredentialStoreException e) {
            return failure(e);
        }
        String current = currentSettingValue();
        if (isReference(current)) {
            DatabaseManager.updateEmailPasswordValue(null);
        }
        return new Result(Status.OK, "The saved password was removed.");
    }

    /**
     * Moves a legacy plaintext password into the store. The plaintext is replaced by the reference only after the store
     * has been read back successfully and equals it; on any failure the plaintext is left exactly as it was.
     */
    public MigrationResult migrateLegacy() {
        String[] settings = DatabaseManager.loadEmailSettings();
        String value = settings == null ? null : settings[1];
        if (value == null || value.isBlank()) {
            return new MigrationResult(Migration.NOTHING_TO_MIGRATE, "No saved password.");
        }
        if (isReference(value)) {
            return new MigrationResult(Migration.ALREADY_MIGRATED, "Already stored in Windows Credential Manager.");
        }
        Result saved = save(settings[0], value);
        if (!saved.ok()) {
            return new MigrationResult(Migration.FAILED, saved.message()
                    + " The old saved password was left in place. Re-enter it under Settings once the credential store works,"
                    + " or set GMAIL_APP_PASSWORD, then revoke the old one at https://myaccount.google.com/apppasswords.");
        }
        if (!DatabaseManager.updateEmailPasswordValue(REFERENCE) || !isReference(currentSettingValue())) {
            return new MigrationResult(Migration.FAILED, "The password is in Windows Credential Manager, but the settings could not be updated;"
                    + " the old saved copy remains. Re-enter the password under Settings to finish.");
        }
        return new MigrationResult(Migration.MIGRATED, "The saved password moved to Windows Credential Manager.");
    }

    private static String currentSettingValue() {
        String[] s = DatabaseManager.loadEmailSettings();
        return s == null ? null : s[1];
    }

    private static Result failure(CredentialStoreException e) {
        return new Result(e.failure() == CredentialStore.Failure.UNAVAILABLE ? Status.UNAVAILABLE : Status.FAILED, e.getMessage() + ".");
    }
}

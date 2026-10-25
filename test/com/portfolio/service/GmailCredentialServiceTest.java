package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.service.CredentialStore.Failure;
import com.portfolio.service.GmailCredentialService.Migration;
import com.portfolio.service.GmailCredentialService.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Credential handling against an in-memory store and a throw-away database: no Windows credentials, no real data. */
class GmailCredentialServiceTest {
    // synthetic values only
    private static final String PLAIN = "abcdwxyzabcdwxyz";

    @TempDir Path tmp;

    /** In-memory store with switchable failures. */
    static final class FakeStore implements CredentialStore {
        final Map<String, String> data = new HashMap<>();
        Failure failWrites;
        Failure failReads;
        boolean dropWrites; // accepts the write but stores nothing (verification must catch it)

        @Override public void write(String target, String user, String secret) throws CredentialStoreException {
            if (failWrites != null) throw new CredentialStoreException(failWrites, "store write failed");
            if (!dropWrites) data.put(target, secret);
        }

        @Override public Optional<String> read(String target) throws CredentialStoreException {
            if (failReads != null) throw new CredentialStoreException(failReads, "store read failed");
            return Optional.ofNullable(data.get(target));
        }

        @Override public void delete(String target) {
            data.remove(target);
        }
    }

    @BeforeEach
    void db() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("creds.db").toString());
    }

    @AfterEach
    void closeDb() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static String rowValue() {
        return DatabaseManager.loadEmailSettings()[1];
    }

    private static void legacyRow() {
        DatabaseManager.saveEmailSettings("me@example.invalid", PLAIN, "smtp.gmail.com", 587, true, "08:30");
    }

    @Test
    void saveStoresAndVerifiesWithoutTouchingTheSettingsRow() {
        FakeStore store = new FakeStore();
        GmailCredentialService svc = new GmailCredentialService(store);
        assertTrue(svc.save("me@example.invalid", PLAIN).ok());
        assertEquals(PLAIN, store.data.get(GmailCredentialService.TARGET));
        assertTrue(svc.isStored());
        assertNull(DatabaseManager.loadEmailSettings());
    }

    @Test
    void unavailableStoreAndUnverifiableWritesAreReportedNotHidden() {
        FakeStore store = new FakeStore();
        store.failWrites = Failure.UNAVAILABLE;
        GmailCredentialService.Result r = new GmailCredentialService(store).save("a@b.invalid", PLAIN);
        assertEquals(Status.UNAVAILABLE, r.status());
        assertFalse(r.message().contains(PLAIN));

        FakeStore silent = new FakeStore();
        silent.dropWrites = true;
        assertEquals(Status.FAILED, new GmailCredentialService(silent).save("a@b.invalid", PLAIN).status());
        assertEquals(Status.FAILED, new GmailCredentialService(new FakeStore()).save("a@b.invalid", " ").status());
    }

    @Test
    void migrationMovesPlaintextOnlyAfterTheStoreReadsBack() {
        legacyRow();
        FakeStore store = new FakeStore();
        GmailCredentialService.MigrationResult m = new GmailCredentialService(store).migrateLegacy();
        assertEquals(Migration.MIGRATED, m.outcome());
        assertEquals(PLAIN, store.data.get(GmailCredentialService.TARGET));
        assertEquals(GmailCredentialService.REFERENCE, rowValue());
        String[] row = DatabaseManager.loadEmailSettings();
        assertEquals("me@example.invalid", row[0]);
        assertEquals("08:30", row[5], "the other settings are untouched");
        assertEquals(Migration.ALREADY_MIGRATED, new GmailCredentialService(store).migrateLegacy().outcome());
    }

    @Test
    void failedMigrationKeepsThePlaintextAndSaysWhatToDo() {
        legacyRow();
        FakeStore down = new FakeStore();
        down.failWrites = Failure.UNAVAILABLE;
        GmailCredentialService.MigrationResult m = new GmailCredentialService(down).migrateLegacy();
        assertEquals(Migration.FAILED, m.outcome());
        assertEquals(PLAIN, rowValue(), "recoverable: the old value is still there");
        assertTrue(m.message().contains("Re-enter"), m.message());
        assertFalse(m.message().contains(PLAIN));

        FakeStore silent = new FakeStore();
        silent.dropWrites = true;
        assertEquals(Migration.FAILED, new GmailCredentialService(silent).migrateLegacy().outcome());
        assertEquals(PLAIN, rowValue());
    }

    @Test
    void nothingToMigrateWhenThereIsNoRowOrNoPassword() {
        GmailCredentialService svc = new GmailCredentialService(new FakeStore());
        assertEquals(Migration.NOTHING_TO_MIGRATE, svc.migrateLegacy().outcome());
        DatabaseManager.saveEmailSettings("me@example.invalid", null, "smtp.gmail.com", 587, false, "09:00");
        assertEquals(Migration.NOTHING_TO_MIGRATE, svc.migrateLegacy().outcome());
    }

    @Test
    void replaceAndRemoveNeverRevealTheValue() throws Exception {
        legacyRow();
        FakeStore store = new FakeStore();
        GmailCredentialService svc = new GmailCredentialService(store);
        svc.migrateLegacy();
        assertTrue(svc.save("me@example.invalid", "newnewnewnewnew1").ok());
        assertEquals("newnewnewnewnew1", svc.load().orElseThrow());
        assertEquals(GmailCredentialService.REFERENCE, rowValue(), "the row still holds only the reference");

        assertTrue(svc.remove().ok());
        assertFalse(svc.isStored());
        assertTrue(svc.load().isEmpty());
        assertNull(rowValue(), "the reference is cleared too");
        assertTrue(svc.remove().ok(), "removing twice is harmless");
    }

    @Test
    void anUnreadableStoreIsNotTreatedAsStored() {
        FakeStore store = new FakeStore();
        store.data.put(GmailCredentialService.TARGET, PLAIN);
        store.failReads = Failure.UNAVAILABLE;
        GmailCredentialService svc = new GmailCredentialService(store);
        assertFalse(svc.isStored());
        assertThrows(CredentialStoreException.class, svc::load);
    }

    @Test
    void referenceDetection() {
        assertTrue(GmailCredentialService.isReference(GmailCredentialService.REFERENCE));
        assertFalse(GmailCredentialService.isReference(PLAIN));
        assertFalse(GmailCredentialService.isReference(null));
    }

    @Test
    void windowsStoreRefusesBadSecretsBeforeAnyProcessStarts() {
        CredentialStoreException e = assertThrows(CredentialStoreException.class,
                () -> new WindowsCredentialStore().write("StockVault/test", "u", "line1\nline2"));
        assertEquals(Failure.FAILED, e.failure());
        assertFalse(e.getMessage().contains("line1"));
    }
}

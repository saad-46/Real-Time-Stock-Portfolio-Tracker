package com.portfolio.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    @Test
    void unusableDatabasePathYieldsNoConnectionAndDaoFailsLoudly() {
        // a directory cannot be opened as a database file
        System.setProperty("portfolio.db.path", tmp.toString());
        assertNull(DatabaseManager.getConnection());
        assertThrows(IllegalStateException.class, PortfolioDAO::new);
    }

    @Test
    void recoversOnceAUsablePathIsConfigured() {
        System.setProperty("portfolio.db.path", tmp.toString());
        assertNull(DatabaseManager.getConnection());
        System.setProperty("portfolio.db.path", tmp.resolve("ok.db").toString());
        assertNotNull(DatabaseManager.getConnection());
    }

    @Test
    void schemaInitIsIdempotentAcrossReopens() {
        System.setProperty("portfolio.db.path", tmp.resolve("idem.db").toString());
        assertNotNull(DatabaseManager.getConnection());
        DatabaseManager.closeConnection();
        assertNotNull(DatabaseManager.getConnection()); // migration helper must not fail on an up-to-date schema
    }
}

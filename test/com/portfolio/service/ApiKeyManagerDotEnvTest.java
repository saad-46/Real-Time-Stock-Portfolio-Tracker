package com.portfolio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** .env fallback in ApiKeyManager. Temporary files and synthetic values only; the real .env and environment are never read. */
class ApiKeyManagerDotEnvTest {
    @TempDir Path tmp;

    @AfterEach
    void clean() {
        System.clearProperty("stockvault.env");
        System.clearProperty("stockvault.config");
        ApiKeyManager.resetForTests();
    }

    private void use(String dotEnv, String properties) throws Exception {
        if (dotEnv != null) {
            Files.writeString(tmp.resolve(".env"), dotEnv);
        }
        System.setProperty("stockvault.env", tmp.resolve(".env").toString());
        Path props = tmp.resolve("local.properties");
        Files.writeString(props, properties == null ? "" : properties);
        System.setProperty("stockvault.config", props.toString());
        ApiKeyManager.resetForTests();
    }

    @Test
    void dotEnvSuppliesValuesWhenHigherSourcesAreAbsent() throws Exception {
        use("SV_TEST_A=from-dotenv\n", null);
        assertEquals("from-dotenv", ApiKeyManager.get("SV_TEST_A"));
    }

    @Test
    void propertiesFileOverridesDotEnv() throws Exception {
        use("SV_TEST_A=from-dotenv\nSV_TEST_B=only-dotenv\n", "SV_TEST_A=from-properties\n");
        assertEquals("from-properties", ApiKeyManager.get("SV_TEST_A"));
        assertEquals("only-dotenv", ApiKeyManager.get("SV_TEST_B"));
    }

    @Test
    void blankPropertiesValueFallsThroughToDotEnv() throws Exception {
        use("SV_TEST_A=from-dotenv\n", "SV_TEST_A=\n");
        assertEquals("from-dotenv", ApiKeyManager.get("SV_TEST_A"));
    }

    @Test
    void environmentVariableOverridesPropertiesAndDotEnv() throws Exception {
        String name = null;
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            if (e.getKey().matches("[A-Za-z_][A-Za-z0-9_]*") && !e.getValue().isBlank()) {
                name = e.getKey();
                break;
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(name != null, "no usable environment variable on this machine");
        use(name + "=from-dotenv\n", name + "=from-properties\n");
        assertEquals(System.getenv(name).trim(), ApiKeyManager.get(name));
    }

    @Test
    void parsesCommentsBlankLinesWhitespaceAndQuotes() throws Exception {
        use("# comment\n\n  SV_PLAIN =  spaced value  \nSV_DQ=\"double quoted\"\nSV_SQ='single quoted'\n"
                + "SV_MIXED=\"not matched'\nSV_HASH=abc#def\nSV_EMPTY=\nSV_EMPTYQ=\"\"\nno-equals-line\n=novalue\n#SV_COMMENTED=x\n", null);
        assertEquals("spaced value", ApiKeyManager.get("SV_PLAIN"));
        assertEquals("double quoted", ApiKeyManager.get("SV_DQ"));
        assertEquals("single quoted", ApiKeyManager.get("SV_SQ"));
        assertEquals("\"not matched'", ApiKeyManager.get("SV_MIXED"), "only matching quotes are removed");
        assertEquals("abc#def", ApiKeyManager.get("SV_HASH"));
        assertEquals("", ApiKeyManager.get("SV_EMPTY"), "empty value means not configured");
        assertEquals("", ApiKeyManager.get("SV_EMPTYQ"));
        assertEquals("", ApiKeyManager.get("SV_COMMENTED"));
        assertEquals("", ApiKeyManager.get("SV_MISSING"));
        assertFalse(ApiKeyManager.isConfigured(ApiKeyManager.get("SV_EMPTY")));
    }

    @Test
    void handlesWindowsLineEndingsAndByteOrderMark() throws Exception {
        use("﻿SV_TEST_A=one\r\nSV_TEST_B=two\r\n", null);
        assertEquals("one", ApiKeyManager.get("SV_TEST_A"));
        assertEquals("two", ApiKeyManager.get("SV_TEST_B"));
    }

    @Test
    void missingDotEnvIsHarmless() throws Exception {
        use(null, null);
        assertEquals("", ApiKeyManager.get("SV_TEST_A"));
        System.setProperty("stockvault.env", tmp.resolve("no-such-folder").resolve(".env").toString());
        ApiKeyManager.resetForTests();
        assertEquals("", ApiKeyManager.get("SV_TEST_A"));
        System.setProperty("stockvault.env", tmp.toString()); // a directory, not a file
        ApiKeyManager.resetForTests();
        assertEquals("", ApiKeyManager.get("SV_TEST_A"));
    }

    @Test
    void dotEnvExampleIsNeverACredentialSource() throws Exception {
        Path example = tmp.resolve(".env.example");
        Files.writeString(example, "SV_TEST_A=template-value\n");
        assertTrue(ApiKeyManager.readDotEnv(example).isEmpty());
        System.setProperty("stockvault.env", example.toString());
        System.setProperty("stockvault.config", tmp.resolve("none.properties").toString());
        ApiKeyManager.resetForTests();
        assertEquals("", ApiKeyManager.get("SV_TEST_A"));
    }

    @Test
    void unreadableContentFailsSafelyWithoutLeakingValues() throws Exception {
        Path env = tmp.resolve(".env");
        byte[] head = "SV_TEST_A=synthetic-secret-value\nSV_TEST_B=".getBytes(StandardCharsets.UTF_8);
        byte[] bad = new byte[head.length + 2];
        System.arraycopy(head, 0, bad, 0, head.length);
        bad[head.length] = (byte) 0xFF; // not valid UTF-8
        bad[head.length + 1] = (byte) 0xFE;
        Files.write(env, bad);
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            assertTrue(ApiKeyManager.readDotEnv(env).isEmpty(), "an unreadable file contributes nothing");
        } finally {
            System.setErr(original);
        }
        String log = captured.toString(StandardCharsets.UTF_8);
        assertFalse(log.isEmpty(), "the problem is reported");
        assertFalse(log.contains("synthetic-secret-value"), "no value in the log");
    }

    @Test
    void valuesNeverAppearInTheStartupLogsWhenReadingSucceeds() throws Exception {
        use("SV_TEST_A=synthetic-secret-value\n", null);
        PrintStream original = System.err;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
        System.setErr(sink);
        System.setOut(sink);
        try {
            assertEquals("synthetic-secret-value", ApiKeyManager.get("SV_TEST_A"));
        } finally {
            System.setErr(original);
            System.setOut(originalOut);
        }
        assertFalse(captured.toString(StandardCharsets.UTF_8).contains("synthetic-secret-value"));
    }
}

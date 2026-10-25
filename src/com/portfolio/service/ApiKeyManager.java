package com.portfolio.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * ApiKeyManager - the single place that resolves API keys and secrets. It contains NO key values.
 *
 * Each setting is looked up, in order, in:
 * 1. the environment variable (e.g. ALPHAVANTAGE_API_KEY)
 * 2. the local properties file (default ./stockvault.local.properties, or the path in the STOCKVAULT_CONFIG
 *    environment variable / stockvault.config system property). That file is git-ignored; never commit it.
 * 3. .env files as a fallback (NAME=value lines, git-ignored): the project-root .env (see AppPaths.resolveDotEnvFile), then the
 *    per-user .env in the application data folder, which is where a packaged app outside the project tree reads its keys
 *
 * Both files are read once and cached, so a restart is needed after changing a key. Neither the environment nor any
 * file is modified.
 *
 * A missing setting yields an empty string; each service then reports "no API key configured" instead of calling
 * the provider with a bogus key. Key values are never logged by this class.
 *
 * Settings: ALPHAVANTAGE_API_KEY, FINNHUB_API_KEY (comma-separated list allowed, used round-robin),
 * TWELVEDATA_API_KEY, GNEWS_API_KEY, NEWSDATA_API_KEY, GROQ_API_KEY, ASSEMBLYAI_API_KEY, ELEVENLABS_API_KEY,
 * EXCHANGERATE_API_KEY, GMAIL_APP_PASSWORD.
 */
public class ApiKeyManager {
    public static final String DEFAULT_CONFIG_FILE = "stockvault.local.properties";

    private static Properties local;
    private static Map<String, String> dotEnv;
    private static int finnhubKeyIndex = 0;

    private ApiKeyManager() {
    }

    /** Resolve one setting; empty string if it is not configured anywhere. */
    public static String get(String name) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String fromFile = localProperties().getProperty(name);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        String fromDotEnv = dotEnv().get(name);
        return fromDotEnv == null ? "" : fromDotEnv;
    }

    private static synchronized Map<String, String> dotEnv() {
        if (dotEnv == null) {
            Map<String, String> merged = new HashMap<>();
            // lowest priority first: the per-user .env (works for a packaged app), then the project-root .env
            // (an explicit stockvault.env property replaces the project-root location and disables the per-user one)
            Path explicit = System.getProperty("stockvault.env") == null ? null : com.portfolio.AppPaths.resolveDotEnvFile();
            if (explicit == null) {
                merged.putAll(readDotEnv(com.portfolio.AppPaths.userDotEnvFile()));
                Path project = com.portfolio.AppPaths.resolveDotEnvFile();
                if (project != null) {
                    merged.putAll(readDotEnv(project));
                }
            } else {
                merged.putAll(readDotEnv(explicit));
            }
            dotEnv = Map.copyOf(merged);
        }
        return dotEnv;
    }

    /**
     * Read a {@code .env} file: UTF-8, one {@code NAME=value} per line; blank lines and lines starting with # are ignored;
     * names and values are trimmed; a value wrapped in matching single or double quotes loses them. A # after a value is
     * part of the value. Missing, unreadable or non-UTF-8 files yield an empty map; the warning names the file and the
     * exception type only, never file content. {@code .env.example} is a template and is never used as a source.
     */
    static Map<String, String> readDotEnv(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null || fileName.toString().endsWith(".example") || !Files.isRegularFile(path)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.startsWith("﻿")) {
                    s = s.substring(1).strip();
                }
                int eq = s.indexOf('=');
                if (s.isEmpty() || s.startsWith("#") || eq <= 0) {
                    continue;
                }
                String key = s.substring(0, eq).strip();
                String value = s.substring(eq + 1).strip();
                if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                        && value.charAt(value.length() - 1) == value.charAt(0)) {
                    value = value.substring(1, value.length() - 1).strip();
                }
                if (!key.isEmpty() && !value.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("⚠️ Could not read the .env file " + path + " (" + e.getClass().getSimpleName() + ")");
            return Map.of();
        }
        return Map.copyOf(values);
    }

    /** True if the value is a usable (non-empty) setting. */
    public static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private static synchronized Properties localProperties() {
        if (local == null) {
            Properties p = new Properties();
            Path path = com.portfolio.AppPaths.resolveSettingsFile();
            if (Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    p.load(in);
                } catch (IOException e) {
                    System.err.println("⚠️ Could not read the local settings file " + path + ": " + e.getMessage());
                }
            }
            local = p;
        }
        return local;
    }

    /** Test hook: forget the cached local settings so the next lookup re-reads the (possibly different) file. */
    static synchronized void resetForTests() {
        local = null;
        dotEnv = null;
        finnhubKeyIndex = 0;
    }

    /** Next Finnhub key, round-robin over a comma-separated list (higher combined rate limit). */
    public static synchronized String getFinnhubKey() {
        String[] keys = get("FINNHUB_API_KEY").split(",");
        String key = keys[Math.floorMod(finnhubKeyIndex, keys.length)].trim();
        finnhubKeyIndex = (finnhubKeyIndex + 1) % keys.length;
        return key;
    }

    public static String getTwelveDataKey() {
        return get("TWELVEDATA_API_KEY");
    }

    public static String getAlphaVantageKey() {
        return get("ALPHAVANTAGE_API_KEY");
    }

    public static String getGNewsKey() {
        return get("GNEWS_API_KEY");
    }

    public static String getNewsDataKey() {
        return get("NEWSDATA_API_KEY");
    }

    public static String getGroqKey() {
        return get("GROQ_API_KEY");
    }

    public static String getAssemblyAIKey() {
        return get("ASSEMBLYAI_API_KEY");
    }

    public static String getElevenLabsKey() {
        return get("ELEVENLABS_API_KEY");
    }

    public static String getExchangeRateApiKey() {
        return get("EXCHANGERATE_API_KEY");
    }

    public static String getGmailAppPassword() {
        return get("GMAIL_APP_PASSWORD");
    }
}

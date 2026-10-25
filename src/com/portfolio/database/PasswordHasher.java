package com.portfolio.database;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Password storage for local accounts. New hashes are PBKDF2-HMAC-SHA256 with a random salt (JDK built-in, no library):
 * {@code pbkdf2-sha256$iterations$salt$hash}. Hashes written by the original app (64 hex characters of unsalted SHA-256) are
 * still accepted so existing accounts keep working; {@link #needsUpgrade} tells the caller to store a PBKDF2 hash after a
 * successful sign-in. Nothing here logs or returns a password.
 */
public final class PasswordHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    public static boolean verify(String password, String stored) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith(PREFIX + "$")) {
            String[] parts = stored.split("[$]");
            if (parts.length != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 1 || iterations > 5_000_000) {
                    return false;
                }
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, derive(password, salt, iterations));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), legacySha256(password).getBytes(StandardCharsets.UTF_8));
    }

    /** True for a legacy unsalted SHA-256 hash (or a PBKDF2 hash with fewer iterations than the current standard). */
    public static boolean needsUpgrade(String stored) {
        if (stored == null) {
            return false;
        }
        if (!stored.startsWith(PREFIX + "$")) {
            return true;
        }
        String[] parts = stored.split("[$]");
        try {
            return parts.length == 4 && Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java runtime", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, (char) 0);
        }
    }

    /** The original app's format, kept only to verify existing accounts. */
    static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

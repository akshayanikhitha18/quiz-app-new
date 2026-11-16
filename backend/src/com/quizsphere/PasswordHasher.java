package com.quizsphere;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * PBKDF2-HMAC-SHA256 with a random 16-byte salt per user and a high iteration count
 * (slows down brute-force guessing). Uses only the Java standard library.
 */
public final class PasswordHasher {
    public record Hashed(String hash, String salt, int iterations) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final int iterations;

    public PasswordHasher(int iterations) { this.iterations = iterations; }

    public int getIterations() { return iterations; }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    public Hashed hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        HexFormat hex = HexFormat.of();
        return new Hashed(hex.formatHex(pbkdf2(password, salt, iterations)), hex.formatHex(salt), iterations);
    }

    public boolean verify(String password, String hashHex, String saltHex, int iterations) {
        HexFormat hex = HexFormat.of();
        byte[] digest = pbkdf2(password, hex.parseHex(saltHex), iterations);
        return MessageDigest.isEqual(digest, hex.parseHex(hashHex));      // constant-time comparison
    }

    /** Hash anyway when the user does not exist, so response time does not reveal registered usernames. */
    public void burnTime(String password) {
        pbkdf2(password, new byte[16], iterations);
    }
}

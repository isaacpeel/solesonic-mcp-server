package com.solesonic.mcp.jira.token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * File-based TokenStore with AES-GCM encryption using a master key provided via env ENCRYPTION_KEY (Base64).
 * If misconfigured, prefer not to create this bean and fallback to InMemoryTokenStore.
 */
public class FileTokenStore implements TokenStore {

    private final SecretKey masterKey;
    private final Path dir;
    private final Map<String, StoredToken> cache = new ConcurrentHashMap<>();

    public FileTokenStore(Path dir, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Missing ENCRYPTION_KEY for FileTokenStore");
        }
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("ENCRYPTION_KEY must be 128/192/256-bit");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create token store dir: " + dir, e);
        }
    }

    @Override
    public Optional<StoredToken> get(String userProfileId, String cloudId) {
        String k = key(userProfileId, cloudId);
        StoredToken t = cache.get(k);
        if (t != null) return Optional.of(t);
        Path p = fileFor(k);
        if (!Files.exists(p)) return Optional.empty();
        try {
            byte[] enc = Files.readAllBytes(p);
            StoredToken st = decrypt(enc);
            cache.put(k, st);
            return Optional.of(st);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(String userProfileId, String cloudId, StoredToken token) {
        String k = key(userProfileId, cloudId);
        Path p = fileFor(k);
        try {
            byte[] enc = encrypt(token);
            Files.write(p, enc);
            cache.put(k, token);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist token", e);
        }
    }

    @Override
    public void delete(String userProfileId, String cloudId) {
        String k = key(userProfileId, cloudId);
        cache.remove(k);
        Path p = fileFor(k);
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private Path fileFor(String k) {
        return dir.resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(k.getBytes(StandardCharsets.UTF_8)) + ".token");
    }

    private static String key(String userProfileId, String cloudId) {
        return userProfileId + ":" + cloudId;
    }

    private byte[] encrypt(StoredToken token) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] nonce = new byte[12];

        new SecureRandom().nextBytes(nonce);
        GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);
        String payload = toPayload(token);
        byte[] cipherText = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedToken = new byte[nonce.length + cipherText.length];
        System.arraycopy(nonce, 0, encryptedToken, 0, nonce.length);
        System.arraycopy(cipherText, 0, encryptedToken, nonce.length, cipherText.length);
        return encryptedToken;
    }

    private StoredToken decrypt(byte[] data) throws GeneralSecurityException {
        byte[] initializationVector = new byte[12];
        byte[] ciphertext = new byte[data.length - 12];
        System.arraycopy(data, 0, initializationVector, 0, 12);
        System.arraycopy(data, 12, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, initializationVector));
        byte[] decryptedData = cipher.doFinal(ciphertext);
        return fromPayload(new String(decryptedData, StandardCharsets.UTF_8));
    }

    private String toPayload(StoredToken t) {
        String scopes = t.scopes() == null ? "" : String.join(" ", t.scopes());
        long exp = t.expiresAt() == null ? 0L : t.expiresAt().getEpochSecond();
        return String.join("\n",
                nullToEmpty(t.tokenType()),
                nullToEmpty(t.accessToken()),
                nullToEmpty(t.refreshToken()),
                Long.toString(exp),
                scopes
        );
    }

    private StoredToken fromPayload(String s) {
        String[] lines = s.split("\n", -1);
        String tokenType = emptyToNull(lines.length > 0 ? lines[0] : null);
        String access = emptyToNull(lines.length > 1 ? lines[1] : null);
        String refresh = emptyToNull(lines.length > 2 ? lines[2] : null);
        long exp = 0L;
        try { exp = Long.parseLong(lines.length > 3 ? lines[3] : "0"); } catch (Exception ignored) {}
        Instant expires = exp > 0 ? Instant.ofEpochSecond(exp) : null;
        List<String> scopes = List.of();
        if (lines.length > 4 && lines[4] != null && !lines[4].isBlank()) {
            scopes = List.of(lines[4].split(" "));
        }
        return new StoredToken(tokenType, access, refresh, expires, scopes);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }
}

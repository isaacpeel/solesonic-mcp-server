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
    private final Path storageDirectory;
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
        this.storageDirectory = dir;
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create token store dir: " + storageDirectory, exception);
        }
    }

    @Override
    public Optional<StoredToken> get(String userProfileId, String cloudId) {
        String compositeKey = key(userProfileId, cloudId);
        StoredToken cachedToken = cache.get(compositeKey);

        if (cachedToken != null) {
            return Optional.of(cachedToken);
        }

        Path filePath = fileFor(compositeKey);

        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            byte[] encryptedBytes = Files.readAllBytes(filePath);
            StoredToken storedToken = decrypt(encryptedBytes);
            cache.put(compositeKey, storedToken);
            return Optional.of(storedToken);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    @Override
    public void save(String userProfileId, String cloudId, StoredToken token) {
        String compositeKey = key(userProfileId, cloudId);
        Path filePath = fileFor(compositeKey);
        try {
            byte[] encryptedBytes = encrypt(token);
            Files.write(filePath, encryptedBytes);
            cache.put(compositeKey, token);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist token", exception);
        }
    }

    @Override
    public void delete(String userProfileId, String cloudId) {
        String compositeKey = key(userProfileId, cloudId);
        cache.remove(compositeKey);
        Path filePath = fileFor(compositeKey);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignoredException) {}
    }

    private Path fileFor(String compositeKey) {
        return storageDirectory.resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(compositeKey.getBytes(StandardCharsets.UTF_8)) + ".token");
    }

    private static String key(String userProfileId, String cloudId) {
        return userProfileId + ":" + cloudId;
    }

    private byte[] encrypt(StoredToken token) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] nonce = new byte[12];

        new SecureRandom().nextBytes(nonce);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmParameterSpec);
        String payload = toPayload(token);
        byte[] cipherText = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedToken = new byte[nonce.length + cipherText.length];
        System.arraycopy(nonce, 0, encryptedToken, 0, nonce.length);
        System.arraycopy(cipherText, 0, encryptedToken, nonce.length, cipherText.length);
        return encryptedToken;
    }

    private StoredToken decrypt(byte[] encryptedData) throws GeneralSecurityException {
        byte[] initializationVector = new byte[12];
        byte[] ciphertext = new byte[encryptedData.length - 12];
        System.arraycopy(encryptedData, 0, initializationVector, 0, 12);
        System.arraycopy(encryptedData, 12, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, initializationVector));
        byte[] decryptedData = cipher.doFinal(ciphertext);
        return fromPayload(new String(decryptedData, StandardCharsets.UTF_8));
    }

    private String toPayload(StoredToken token) {
        String scopesJoined = token.scopes() == null ? "" : String.join(" ", token.scopes());
        long expiresEpochSeconds = token.expiresAt() == null ? 0L : token.expiresAt().getEpochSecond();
        return String.join("\n",
                nullToEmpty(token.tokenType()),
                nullToEmpty(token.accessToken()),
                nullToEmpty(token.refreshToken()),
                Long.toString(expiresEpochSeconds),
                scopesJoined
        );
    }

    private StoredToken fromPayload(String payloadString) {
        String[] payloadLines = payloadString.split("\n", -1);
        String tokenType = emptyToNull(payloadLines.length > 0 ? payloadLines[0] : null);
        String accessToken = emptyToNull(payloadLines.length > 1 ? payloadLines[1] : null);
        String refreshToken = emptyToNull(payloadLines.length > 2 ? payloadLines[2] : null);
        long expiresEpochSeconds = 0L;
        try { expiresEpochSeconds = Long.parseLong(payloadLines.length > 3 ? payloadLines[3] : "0"); } catch (Exception ignored) {}
        Instant expiresAt = expiresEpochSeconds > 0 ? Instant.ofEpochSecond(expiresEpochSeconds) : null;
        List<String> scopes = List.of();
        if (payloadLines.length > 4 && payloadLines[4] != null && !payloadLines[4].isBlank()) {
            scopes = List.of(payloadLines[4].split(" "));
        }
        return new StoredToken(tokenType, accessToken, refreshToken, expiresAt, scopes);
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String emptyToNull(String value) { return (value == null || value.isEmpty()) ? null : value; }
}

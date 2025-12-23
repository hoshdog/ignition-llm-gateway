package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages API key creation, storage, and validation.
 * Keys are hashed immediately; the raw key is returned only once upon creation.
 */
public class ApiKeyManager {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyManager.class);

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final String HASH_ALGORITHM = "SHA-256";

    // In-memory storage (TODO: Replace with Ignition PersistenceInterface)
    private final Map<String, ApiKey> keysById = new ConcurrentHashMap<>();
    private final Map<String, ApiKey> keysByHash = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom;

    public ApiKeyManager() {
        this.secureRandom = new SecureRandom();
        logger.info("ApiKeyManager initialized");
    }

    /**
     * Creates a new API key with the given name and permissions.
     * Returns the result containing the raw key (shown only once).
     */
    public ApiKeyCreationResult createKey(String name, Set<Permission> permissions) {
        return createKey(name, permissions, null);
    }

    /**
     * Creates a new API key with optional expiration.
     */
    public ApiKeyCreationResult createKey(String name, Set<Permission> permissions, Instant expiresAt) {
        // Generate random key bytes
        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
        secureRandom.nextBytes(keyBytes);

        // Create the raw key string with prefix
        String rawKey = ApiKey.KEY_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(keyBytes);

        // Generate salt
        byte[] saltBytes = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        // Hash the key
        String keyHash = hashKey(rawKey, salt);

        // Extract prefix for identification (first 8 chars after the prefix)
        String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), ApiKey.KEY_PREFIX.length() + 8));

        // Build the ApiKey entity
        ApiKey apiKey = ApiKey.builder()
                .name(name)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .salt(salt)
                .permissions(permissions)
                .expiresAt(expiresAt)
                .enabled(true)
                .build();

        // Store
        keysById.put(apiKey.getId(), apiKey);
        keysByHash.put(keyHash, apiKey);

        logger.info("Created API key: {} (id={}, permissions={})",
                name, apiKey.getId(), permissions.size());

        // Return the raw key - this is the only time it's available!
        return new ApiKeyCreationResult(apiKey, rawKey);
    }

    /**
     * Validates a raw API key and returns the corresponding ApiKey entity if valid.
     */
    public Optional<ApiKey> validateKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return Optional.empty();
        }

        // Strip the prefix if present
        String keyToValidate = rawKey.trim();
        if (!keyToValidate.startsWith(ApiKey.KEY_PREFIX)) {
            logger.debug("Key missing required prefix");
            return Optional.empty();
        }

        // Try to find the key by testing hashes
        for (ApiKey apiKey : keysById.values()) {
            String testHash = hashKey(keyToValidate, apiKey.getSalt());
            if (testHash.equals(apiKey.getKeyHash())) {
                // Found matching key
                if (!apiKey.isValid()) {
                    logger.debug("Key found but invalid (disabled or expired): {}", apiKey.getKeyPrefix());
                    return Optional.empty();
                }
                apiKey.updateLastUsed();
                return Optional.of(apiKey);
            }
        }

        logger.debug("No matching key found");
        return Optional.empty();
    }

    /**
     * Gets an API key by its ID.
     */
    public Optional<ApiKey> getKeyById(String id) {
        return Optional.ofNullable(keysById.get(id));
    }

    /**
     * Gets all API keys (for admin listing).
     */
    public Collection<ApiKey> getAllKeys() {
        return keysById.values();
    }

    /**
     * Gets an API key by its prefix (for lookup in admin UI).
     */
    public Optional<ApiKey> getKeyByPrefix(String keyPrefix) {
        for (ApiKey key : keysById.values()) {
            if (key.getKeyPrefix().equals(keyPrefix)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Lists all active (enabled and not expired) API keys.
     */
    public Collection<ApiKey> listActiveKeys() {
        return keysById.values().stream()
                .filter(ApiKey::isValid)
                .toList();
    }

    /**
     * Revokes (disables) an API key by ID or prefix.
     */
    public boolean revokeKey(String keyIdOrPrefix) {
        // Try by ID first
        ApiKey key = keysById.get(keyIdOrPrefix);
        if (key != null) {
            key.setEnabled(false);
            logger.info("Revoked API key: {} ({})", key.getName(), keyIdOrPrefix);
            return true;
        }
        // Try by prefix
        for (ApiKey apiKey : keysById.values()) {
            if (apiKey.getKeyPrefix().equals(keyIdOrPrefix)) {
                apiKey.setEnabled(false);
                logger.info("Revoked API key by prefix: {} ({})", apiKey.getName(), keyIdOrPrefix);
                return true;
            }
        }
        return false;
    }

    /**
     * Re-enables a revoked API key.
     */
    public boolean enableKey(String keyId) {
        ApiKey key = keysById.get(keyId);
        if (key != null) {
            key.setEnabled(true);
            logger.info("Enabled API key: {} ({})", key.getName(), keyId);
            return true;
        }
        return false;
    }

    /**
     * Permanently deletes an API key.
     */
    public boolean deleteKey(String keyId) {
        ApiKey removed = keysById.remove(keyId);
        if (removed != null) {
            keysByHash.remove(removed.getKeyHash());
            logger.info("Deleted API key: {} ({})", removed.getName(), keyId);
            return true;
        }
        return false;
    }

    /**
     * Updates permissions for an existing key.
     */
    public Optional<ApiKey> updateKeyPermissions(String keyId, Set<Permission> newPermissions) {
        ApiKey existing = keysById.get(keyId);
        if (existing == null) {
            return Optional.empty();
        }

        // Create new key with updated permissions
        ApiKey updated = ApiKey.builder()
                .id(existing.getId())
                .name(existing.getName())
                .keyHash(existing.getKeyHash())
                .keyPrefix(existing.getKeyPrefix())
                .salt(existing.getSalt())
                .permissions(newPermissions)
                .createdAt(existing.getCreatedAt())
                .expiresAt(existing.getExpiresAt())
                .lastUsedAt(existing.getLastUsedAt())
                .enabled(existing.isEnabled())
                .metadata(existing.getMetadata())
                .build();

        keysById.put(keyId, updated);
        keysByHash.put(updated.getKeyHash(), updated);

        logger.info("Updated permissions for API key: {} ({})", updated.getName(), keyId);
        return Optional.of(updated);
    }

    /**
     * Hashes a raw key with the given salt.
     */
    private String hashKey(String rawKey, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * Result of creating a new API key.
     * Contains both the stored key entity and the raw key string.
     */
    public static class ApiKeyCreationResult {
        private final ApiKey apiKey;
        private final String rawKey;

        public ApiKeyCreationResult(ApiKey apiKey, String rawKey) {
            this.apiKey = apiKey;
            this.rawKey = rawKey;
        }

        public ApiKey getApiKey() {
            return apiKey;
        }

        /**
         * Returns the raw API key. This is the ONLY time it's available!
         * Must be shown to the user immediately and never stored.
         */
        public String getRawKey() {
            return rawKey;
        }
    }
}

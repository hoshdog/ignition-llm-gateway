package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiKeyManager.
 */
class ApiKeyManagerTest {

    private ApiKeyManager apiKeyManager;

    @BeforeEach
    void setUp() {
        apiKeyManager = new ApiKeyManager();
    }

    @Nested
    @DisplayName("API Key Creation")
    class ApiKeyCreation {

        @Test
        @DisplayName("should create a valid API key with specified permissions")
        void shouldCreateApiKeyWithPermissions() {
            Set<Permission> permissions = Set.of(Permission.TAG_READ, Permission.TAG_CREATE);

            ApiKeyManager.ApiKeyCreationResult result = apiKeyManager.createKey("test-key", permissions);

            assertNotNull(result);
            assertNotNull(result.getRawKey());
            assertNotNull(result.getApiKey());
            assertTrue(result.getRawKey().startsWith(ApiKey.KEY_PREFIX));
            assertEquals("test-key", result.getApiKey().getName());
            assertTrue(result.getApiKey().getPermissions().contains(Permission.TAG_READ));
            assertTrue(result.getApiKey().getPermissions().contains(Permission.TAG_CREATE));
        }

        @Test
        @DisplayName("should generate unique keys on each creation")
        void shouldGenerateUniqueKeys() {
            Set<Permission> permissions = Set.of(Permission.TAG_READ);

            ApiKeyManager.ApiKeyCreationResult result1 = apiKeyManager.createKey("key1", permissions);
            ApiKeyManager.ApiKeyCreationResult result2 = apiKeyManager.createKey("key2", permissions);

            assertNotEquals(result1.getRawKey(), result2.getRawKey());
            assertNotEquals(result1.getApiKey().getKeyHash(), result2.getApiKey().getKeyHash());
        }

        @Test
        @DisplayName("should create key with DRY_RUN_ONLY permission")
        void shouldCreateDryRunOnlyKey() {
            Set<Permission> permissions = Set.of(Permission.DRY_RUN_ONLY, Permission.TAG_READ);

            ApiKeyManager.ApiKeyCreationResult result = apiKeyManager.createKey("dry-run-key", permissions);

            assertTrue(result.getApiKey().getPermissions().contains(Permission.DRY_RUN_ONLY));
            assertTrue(result.getApiKey().isDryRunOnly());
        }
    }

    @Nested
    @DisplayName("API Key Validation")
    class ApiKeyValidation {

        @Test
        @DisplayName("should validate a correct API key")
        void shouldValidateCorrectKey() {
            ApiKeyManager.ApiKeyCreationResult created = apiKeyManager.createKey(
                    "valid-key", Set.of(Permission.TAG_READ));
            String rawKey = created.getRawKey();

            Optional<ApiKey> validated = apiKeyManager.validateKey(rawKey);

            assertTrue(validated.isPresent());
            assertEquals("valid-key", validated.get().getName());
        }

        @Test
        @DisplayName("should reject an invalid API key")
        void shouldRejectInvalidKey() {
            Optional<ApiKey> validated = apiKeyManager.validateKey("invalid-key-12345");

            assertTrue(validated.isEmpty());
        }

        @Test
        @DisplayName("should reject a null API key")
        void shouldRejectNullKey() {
            Optional<ApiKey> validated = apiKeyManager.validateKey(null);

            assertTrue(validated.isEmpty());
        }

        @Test
        @DisplayName("should reject an empty API key")
        void shouldRejectEmptyKey() {
            Optional<ApiKey> validated = apiKeyManager.validateKey("");

            assertTrue(validated.isEmpty());
        }

        @Test
        @DisplayName("should reject a tampered API key")
        void shouldRejectTamperedKey() {
            ApiKeyManager.ApiKeyCreationResult created = apiKeyManager.createKey(
                    "tampered-key", Set.of(Permission.TAG_READ));
            String rawKey = created.getRawKey();
            String tamperedKey = rawKey.substring(0, rawKey.length() - 1) + "X";

            Optional<ApiKey> validated = apiKeyManager.validateKey(tamperedKey);

            assertTrue(validated.isEmpty());
        }
    }

    @Nested
    @DisplayName("API Key Revocation")
    class ApiKeyRevocation {

        @Test
        @DisplayName("should revoke an existing API key")
        void shouldRevokeExistingKey() {
            ApiKeyManager.ApiKeyCreationResult created = apiKeyManager.createKey(
                    "revoke-key", Set.of(Permission.TAG_READ));
            String keyPrefix = created.getApiKey().getKeyPrefix();

            boolean revoked = apiKeyManager.revokeKey(keyPrefix);

            assertTrue(revoked);
            Optional<ApiKey> validated = apiKeyManager.validateKey(created.getRawKey());
            assertTrue(validated.isEmpty());
        }

        @Test
        @DisplayName("should return false when revoking non-existent key")
        void shouldReturnFalseForNonExistentKey() {
            boolean revoked = apiKeyManager.revokeKey("nonexistent_prefix");

            assertFalse(revoked);
        }
    }

    @Nested
    @DisplayName("API Key Lookup")
    class ApiKeyLookup {

        @Test
        @DisplayName("should find key by prefix")
        void shouldFindKeyByPrefix() {
            ApiKeyManager.ApiKeyCreationResult created = apiKeyManager.createKey(
                    "lookup-key", Set.of(Permission.TAG_READ));
            String keyPrefix = created.getApiKey().getKeyPrefix();

            Optional<ApiKey> found = apiKeyManager.getKeyByPrefix(keyPrefix);

            assertTrue(found.isPresent());
            assertEquals("lookup-key", found.get().getName());
        }

        @Test
        @DisplayName("should list all active keys")
        void shouldListAllActiveKeys() {
            apiKeyManager.createKey("key1", Set.of(Permission.TAG_READ));
            apiKeyManager.createKey("key2", Set.of(Permission.TAG_CREATE));

            var keys = apiKeyManager.listActiveKeys();

            assertEquals(2, keys.size());
        }
    }
}

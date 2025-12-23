package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthContext.
 */
class AuthContextTest {

    @Nested
    @DisplayName("AuthContext Builder")
    class AuthContextBuilder {

        @Test
        @DisplayName("should build AuthContext with user ID and permissions")
        void shouldBuildWithUserIdAndPermissions() {
            AuthContext context = AuthContext.builder()
                    .userId("test-user")
                    .permissions(Set.of(Permission.TAG_READ, Permission.TAG_CREATE))
                    .build();

            assertEquals("test-user", context.getUserId());
            assertTrue(context.hasPermission(Permission.TAG_READ));
            assertTrue(context.hasPermission(Permission.TAG_CREATE));
            assertFalse(context.hasPermission(Permission.TAG_DELETE));
        }

        @Test
        @DisplayName("should build AuthContext from ApiKey")
        void shouldBuildFromApiKey() {
            ApiKey apiKey = ApiKey.create(
                    "key-hash",
                    "key-salt",
                    "test-api-key",
                    Set.of(Permission.TAG_READ, Permission.VIEW_READ)
            );

            AuthContext context = AuthContext.builder()
                    .fromApiKey(apiKey)
                    .build();

            // UserId is prefixed with "api-key:" when created from ApiKey
            assertEquals("api-key:test-api-key", context.getUserId());
            assertTrue(context.hasPermission(Permission.TAG_READ));
            assertTrue(context.hasPermission(Permission.VIEW_READ));
        }

        @Test
        @DisplayName("should include client address")
        void shouldIncludeClientAddress() {
            AuthContext context = AuthContext.builder()
                    .userId("user")
                    .permissions(Set.of())
                    .clientAddress("192.168.1.100")
                    .build();

            assertEquals("192.168.1.100", context.getClientAddress());
        }

        @Test
        @DisplayName("should include custom attributes")
        void shouldIncludeAttributes() {
            AuthContext context = AuthContext.builder()
                    .userId("user")
                    .permissions(Set.of())
                    .attribute("custom.key", "custom-value")
                    .build();

            assertEquals("custom-value", context.getAttribute("custom.key"));
        }
    }

    @Nested
    @DisplayName("Permission Checks")
    class PermissionChecks {

        @Test
        @DisplayName("should grant all permissions to admin")
        void shouldGrantAllToAdmin() {
            AuthContext context = AuthContext.builder()
                    .userId("admin")
                    .permissions(Set.of(Permission.ADMIN))
                    .build();

            assertTrue(context.isAdmin());
            // Admin should effectively have all permissions
            assertTrue(context.hasPermission(Permission.ADMIN));
        }

        @Test
        @DisplayName("should identify dry-run only context")
        void shouldIdentifyDryRunOnly() {
            AuthContext context = AuthContext.builder()
                    .userId("limited-user")
                    .permissions(Set.of(Permission.DRY_RUN_ONLY, Permission.TAG_READ))
                    .build();

            assertTrue(context.isDryRunOnly());
            assertTrue(context.hasPermission(Permission.TAG_READ));
        }

        @Test
        @DisplayName("should check multiple permissions")
        void shouldCheckMultiplePermissions() {
            AuthContext context = AuthContext.builder()
                    .userId("user")
                    .permissions(Set.of(Permission.TAG_READ, Permission.TAG_CREATE))
                    .build();

            assertTrue(context.hasAllPermissions(Set.of(Permission.TAG_READ, Permission.TAG_CREATE)));
            assertFalse(context.hasAllPermissions(Set.of(Permission.TAG_READ, Permission.TAG_DELETE)));
        }

        @Test
        @DisplayName("should check any permission")
        void shouldCheckAnyPermission() {
            AuthContext context = AuthContext.builder()
                    .userId("user")
                    .permissions(Set.of(Permission.TAG_READ))
                    .build();

            assertTrue(context.hasAnyPermission(Set.of(Permission.TAG_READ, Permission.TAG_DELETE)));
            assertFalse(context.hasAnyPermission(Set.of(Permission.VIEW_READ, Permission.SCRIPT_READ)));
        }
    }

    @Nested
    @DisplayName("Key Prefix Tracking")
    class KeyPrefixTracking {

        @Test
        @DisplayName("should track key prefix from API key")
        void shouldTrackKeyPrefix() {
            ApiKey apiKey = ApiKey.create(
                    "hash", "salt", "api-key",
                    Set.of(Permission.TAG_READ)
            );

            AuthContext context = AuthContext.builder()
                    .fromApiKey(apiKey)
                    .build();

            assertNotNull(context.getKeyPrefix());
        }
    }
}

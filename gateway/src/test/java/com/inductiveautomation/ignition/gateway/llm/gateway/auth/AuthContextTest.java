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
        @DisplayName("should build AuthContext with user name and source")
        void shouldBuildWithUserNameAndSource() {
            AuthContext context = AuthContext.builder()
                    .userId("admin")
                    .userName("Administrator")
                    .userSource("default")
                    .permissions(Set.of(Permission.ADMIN))
                    .build();

            assertEquals("admin", context.getUserId());
            assertEquals("Administrator", context.getUserName());
            assertEquals("default", context.getUserSource());
            assertTrue(context.isAdmin());
        }

        @Test
        @DisplayName("should use userId as userName when userName not set")
        void shouldUseUserIdAsUserNameFallback() {
            AuthContext context = AuthContext.builder()
                    .userId("testuser")
                    .permissions(Set.of())
                    .build();

            assertEquals("testuser", context.getUserId());
            assertEquals("testuser", context.getUserName());
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

        @Test
        @DisplayName("should generate audit string with user info")
        void shouldGenerateAuditString() {
            AuthContext context = AuthContext.builder()
                    .userId("testuser")
                    .userSource("default")
                    .clientAddress("10.0.0.1")
                    .permissions(Set.of())
                    .build();

            String auditString = context.toAuditString();
            assertTrue(auditString.contains("user=testuser"));
            assertTrue(auditString.contains("source=default"));
            assertTrue(auditString.contains("from=10.0.0.1"));
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
            assertTrue(context.hasPermission(Permission.TAG_READ));
            assertTrue(context.hasPermission(Permission.VIEW_DELETE));
            assertTrue(context.hasPermission(Permission.SCRIPT_CREATE));
        }

        @Test
        @DisplayName("should grant read permissions when READ_ALL is set")
        void shouldGrantReadPermissionsWithReadAll() {
            AuthContext context = AuthContext.builder()
                    .userId("viewer")
                    .permissions(Set.of(Permission.READ_ALL))
                    .build();

            assertTrue(context.hasPermission(Permission.TAG_READ));
            assertTrue(context.hasPermission(Permission.VIEW_READ));
            assertTrue(context.hasPermission(Permission.SCRIPT_READ));
            assertFalse(context.hasPermission(Permission.TAG_CREATE));
            assertFalse(context.hasPermission(Permission.VIEW_DELETE));
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

        @Test
        @DisplayName("should not grant admin permissions without ADMIN permission")
        void shouldNotGrantAdminWithoutPermission() {
            AuthContext context = AuthContext.builder()
                    .userId("regular-user")
                    .permissions(Set.of(Permission.TAG_READ, Permission.TAG_CREATE))
                    .build();

            assertFalse(context.isAdmin());
            assertFalse(context.hasPermission(Permission.ADMIN));
        }
    }

    @Nested
    @DisplayName("Anonymous Context")
    class AnonymousContext {

        @Test
        @DisplayName("should create anonymous context with no permissions")
        void shouldCreateAnonymousContext() {
            AuthContext context = AuthContext.anonymous("127.0.0.1");

            assertEquals("anonymous", context.getUserId());
            assertEquals("127.0.0.1", context.getClientAddress());
            assertTrue(context.getPermissions().isEmpty());
            assertFalse(context.isAdmin());
        }
    }
}

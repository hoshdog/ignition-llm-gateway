package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import com.inductiveautomation.ignition.common.user.AuthenticatedUser;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated session context.
 * Created after successful authentication and used for authorization decisions.
 *
 * <p>This context is created by {@link BasicAuthenticationService} after
 * validating credentials against Ignition's user management system.</p>
 */
public final class AuthContext {

    private final String userId;
    private final String userName;
    private final String userSource;
    private final Set<Permission> permissions;
    private final Instant authenticatedAt;
    private final String clientAddress;
    private final Map<String, Object> attributes;

    private AuthContext(Builder builder) {
        this.userId = Objects.requireNonNull(builder.userId, "userId cannot be null");
        this.userName = builder.userName;
        this.userSource = builder.userSource;
        this.permissions = Collections.unmodifiableSet(builder.permissions);
        this.authenticatedAt = builder.authenticatedAt != null ? builder.authenticatedAt : Instant.now();
        this.clientAddress = builder.clientAddress;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    /**
     * Returns the user ID (typically the username used for authentication).
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the display name of the user (may be different from userId).
     */
    public String getUserName() {
        return userName != null ? userName : userId;
    }

    /**
     * Returns the Ignition user source profile used for authentication.
     */
    public String getUserSource() {
        return userSource;
    }

    /**
     * Returns the set of permissions granted to this user.
     */
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Checks if this context has the specified permission.
     *
     * <p>ADMIN permission grants access to everything.
     * READ_ALL permission grants access to any *_READ permission.</p>
     */
    public boolean hasPermission(Permission permission) {
        if (permissions.contains(Permission.ADMIN)) {
            return true;
        }
        if (permission.name().endsWith("_READ") && permissions.contains(Permission.READ_ALL)) {
            return true;
        }
        return permissions.contains(permission);
    }

    /**
     * Checks if this user has administrative access.
     */
    public boolean isAdmin() {
        return permissions.contains(Permission.ADMIN);
    }

    /**
     * Checks if this context only allows dry-run operations.
     */
    public boolean isDryRunOnly() {
        return permissions.contains(Permission.DRY_RUN_ONLY);
    }

    /**
     * Checks if this context has all of the specified permissions.
     */
    public boolean hasAllPermissions(Set<Permission> required) {
        for (Permission permission : required) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if this context has any of the specified permissions.
     */
    public boolean hasAnyPermission(Set<Permission> permissions) {
        for (Permission permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns when this context was created/authenticated.
     */
    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    /**
     * Returns the client IP address.
     */
    public String getClientAddress() {
        return clientAddress;
    }

    /**
     * Returns additional attributes attached to this context.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Gets a specific attribute by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Creates a string suitable for audit logging.
     */
    public String toAuditString() {
        return String.format("user=%s, source=%s, from=%s",
                userId,
                userSource != null ? userSource : "unknown",
                clientAddress != null ? clientAddress : "unknown");
    }

    @Override
    public String toString() {
        return "AuthContext{" +
                "userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", userSource='" + userSource + '\'' +
                ", permissions=" + permissions.size() +
                ", clientAddress='" + clientAddress + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an anonymous context for unauthenticated requests.
     * Has no permissions.
     */
    public static AuthContext anonymous(String clientAddress) {
        return builder()
                .userId("anonymous")
                .clientAddress(clientAddress)
                .permissions(Collections.emptySet())
                .build();
    }

    public static class Builder {
        private String userId;
        private String userName;
        private String userSource;
        private Set<Permission> permissions = Collections.emptySet();
        private Instant authenticatedAt;
        private String clientAddress;
        private Map<String, Object> attributes = new HashMap<>();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder userSource(String userSource) {
            this.userSource = userSource;
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder authenticatedAt(Instant authenticatedAt) {
            this.authenticatedAt = authenticatedAt;
            return this;
        }

        public Builder clientAddress(String clientAddress) {
            this.clientAddress = clientAddress;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = new HashMap<>(attributes);
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Populates the builder from an Ignition AuthenticatedUser.
         *
         * @param user The authenticated user from Ignition's user source
         * @param userSourceProfile The user source profile name used for authentication
         * @return This builder
         */
        public Builder fromIgnitionUser(AuthenticatedUser user, String userSourceProfile) {
            this.userId = user.getUsername();
            // AuthenticatedUser may have a display name
            this.userName = user.getUsername(); // Could use user.get("firstName") etc. if available
            this.userSource = userSourceProfile;
            return this;
        }

        public AuthContext build() {
            return new AuthContext(this);
        }
    }
}

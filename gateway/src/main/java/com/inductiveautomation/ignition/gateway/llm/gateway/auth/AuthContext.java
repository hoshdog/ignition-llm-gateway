package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated session context.
 * Created after successful authentication and used for authorization decisions.
 */
public final class AuthContext {

    private final String userId;
    private final String keyId;
    private final String keyName;
    private final Set<Permission> permissions;
    private final Instant authenticatedAt;
    private final String clientAddress;
    private final Map<String, Object> attributes;

    private AuthContext(Builder builder) {
        this.userId = Objects.requireNonNull(builder.userId, "userId cannot be null");
        this.keyId = builder.keyId;
        this.keyName = builder.keyName;
        this.permissions = Collections.unmodifiableSet(builder.permissions);
        this.authenticatedAt = builder.authenticatedAt != null ? builder.authenticatedAt : Instant.now();
        this.clientAddress = builder.clientAddress;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    public String getUserId() {
        return userId;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeyName() {
        return keyName;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        if (permissions.contains(Permission.ADMIN)) {
            return true;
        }
        if (permission.name().endsWith("_READ") && permissions.contains(Permission.READ_ALL)) {
            return true;
        }
        return permissions.contains(permission);
    }

    public boolean isAdmin() {
        return permissions.contains(Permission.ADMIN);
    }

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
     * Gets the API key prefix (first characters of the key ID).
     */
    public String getKeyPrefix() {
        return keyId != null && keyId.length() >= 8 ? keyId.substring(0, 8) : keyId;
    }

    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Creates a string suitable for audit logging.
     */
    public String toAuditString() {
        return String.format("user=%s, key=%s (%s), from=%s",
                userId,
                keyName != null ? keyName : "unknown",
                keyId != null ? keyId.substring(0, 8) + "..." : "none",
                clientAddress != null ? clientAddress : "unknown");
    }

    @Override
    public String toString() {
        return "AuthContext{" +
                "userId='" + userId + '\'' +
                ", keyName='" + keyName + '\'' +
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
        private String keyId;
        private String keyName;
        private Set<Permission> permissions = Collections.emptySet();
        private Instant authenticatedAt;
        private String clientAddress;
        private Map<String, Object> attributes = new HashMap<>();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public Builder keyName(String keyName) {
            this.keyName = keyName;
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
         * Creates an AuthContext from an ApiKey.
         */
        public Builder fromApiKey(ApiKey apiKey) {
            this.keyId = apiKey.getId();
            this.keyName = apiKey.getName();
            this.permissions = apiKey.getPermissions();
            this.userId = "api-key:" + apiKey.getName();
            return this;
        }

        public AuthContext build() {
            return new AuthContext(this);
        }
    }
}

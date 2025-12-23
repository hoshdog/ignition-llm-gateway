package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an API key for LLM Gateway authentication.
 * The actual key is never stored - only a hash.
 */
public final class ApiKey {

    public static final String KEY_PREFIX = "llmgw_";

    private final String id;
    private final String name;
    private final String keyHash;
    private final String keyPrefix;
    private final String salt;
    private final Set<Permission> permissions;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Map<String, String> metadata;
    private volatile Instant lastUsedAt;
    private volatile boolean enabled;

    private ApiKey(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.keyHash = Objects.requireNonNull(builder.keyHash, "keyHash cannot be null");
        this.keyPrefix = Objects.requireNonNull(builder.keyPrefix, "keyPrefix cannot be null");
        this.salt = Objects.requireNonNull(builder.salt, "salt cannot be null");
        this.permissions = Collections.unmodifiableSet(new HashSet<>(builder.permissions));
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expiresAt = builder.expiresAt;
        this.lastUsedAt = builder.lastUsedAt;
        this.enabled = builder.enabled;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the SHA-256 hash of the API key.
     * The actual key is never stored.
     */
    public String getKeyHash() {
        return keyHash;
    }

    /**
     * Returns the first 8 characters of the key for identification.
     * Example: "llmgw_a3" (visible in UI for user reference)
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getSalt() {
        return salt;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        if (permissions.contains(Permission.ADMIN)) {
            return true;
        }
        return permissions.contains(permission);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Checks if this key is valid (enabled and not expired).
     */
    public boolean isValid() {
        if (!enabled) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Checks if this key has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKey apiKey = (ApiKey) o;
        return Objects.equals(id, apiKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApiKey{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", enabled=" + enabled +
                ", permissions=" + permissions.size() +
                '}';
    }

    /**
     * Checks if this key only allows dry-run operations.
     */
    public boolean isDryRunOnly() {
        return permissions.contains(Permission.DRY_RUN_ONLY);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory method to create an ApiKey with essential fields.
     * Mainly for testing purposes.
     */
    public static ApiKey create(String keyHash, String salt, String name, Set<Permission> permissions) {
        return builder()
                .keyHash(keyHash)
                .salt(salt)
                .name(name)
                .keyPrefix(KEY_PREFIX + keyHash.substring(0, Math.min(8, keyHash.length())))
                .permissions(permissions)
                .build();
    }

    public static class Builder {
        private String id;
        private String name;
        private String keyHash;
        private String keyPrefix;
        private String salt;
        private Set<Permission> permissions = new HashSet<>();
        private Instant createdAt;
        private Instant expiresAt;
        private Instant lastUsedAt;
        private boolean enabled = true;
        private Map<String, String> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder keyHash(String keyHash) {
            this.keyHash = keyHash;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder salt(String salt) {
            this.salt = salt;
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            this.permissions = new HashSet<>(permissions);
            return this;
        }

        public Builder addPermission(Permission permission) {
            this.permissions.add(permission);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder lastUsedAt(Instant lastUsedAt) {
            this.lastUsedAt = lastUsedAt;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public ApiKey build() {
            return new ApiKey(this);
        }
    }
}

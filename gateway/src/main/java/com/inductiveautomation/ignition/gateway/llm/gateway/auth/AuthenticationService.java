package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationException.AuthFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Main authentication service for the LLM Gateway.
 * Handles API key authentication and creates authenticated contexts.
 */
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PARAM = "api_key";

    private final ApiKeyManager apiKeyManager;

    public AuthenticationService(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
    }

    /**
     * Authenticates an incoming HTTP request.
     * Checks for API key in Authorization header, X-API-Key header, or query param.
     */
    public AuthContext authenticate(HttpServletRequest request) throws AuthenticationException {
        String clientAddress = getClientAddress(request);

        // Try to extract credentials
        String apiKey = extractApiKey(request);

        if (apiKey == null || apiKey.isEmpty()) {
            throw new AuthenticationException(
                    "No authentication credentials provided",
                    AuthFailureReason.MISSING_CREDENTIALS);
        }

        // Validate the API key
        Optional<ApiKey> validatedKey = apiKeyManager.validateKey(apiKey);

        if (validatedKey.isEmpty()) {
            logger.warn("Invalid API key attempt from {}", clientAddress);
            throw new AuthenticationException(
                    "Invalid API key",
                    AuthFailureReason.INVALID_CREDENTIALS);
        }

        ApiKey key = validatedKey.get();

        // Check if key is disabled
        if (!key.isEnabled()) {
            logger.warn("Disabled API key used from {}: {}", clientAddress, key.getKeyPrefix());
            throw new AuthenticationException(
                    "API key has been disabled",
                    AuthFailureReason.DISABLED_KEY);
        }

        // Check if key is expired
        if (key.isExpired()) {
            logger.warn("Expired API key used from {}: {}", clientAddress, key.getKeyPrefix());
            throw new AuthenticationException(
                    "API key has expired",
                    AuthFailureReason.EXPIRED_CREDENTIALS);
        }

        // Build authenticated context
        AuthContext context = AuthContext.builder()
                .fromApiKey(key)
                .clientAddress(clientAddress)
                .attribute("request.method", request.getMethod())
                .attribute("request.path", request.getRequestURI())
                .build();

        logger.debug("Authenticated request from {}: key={}", clientAddress, key.getKeyPrefix());

        return context;
    }

    /**
     * Authenticates a raw API key string directly (for testing or programmatic use).
     */
    public AuthContext authenticateKey(String rawKey) throws AuthenticationException {
        if (rawKey == null || rawKey.isEmpty()) {
            throw new AuthenticationException(
                    "No API key provided",
                    AuthFailureReason.MISSING_CREDENTIALS);
        }

        Optional<ApiKey> validatedKey = apiKeyManager.validateKey(rawKey);

        if (validatedKey.isEmpty()) {
            throw new AuthenticationException(
                    "Invalid API key",
                    AuthFailureReason.INVALID_CREDENTIALS);
        }

        ApiKey key = validatedKey.get();

        if (!key.isValid()) {
            throw new AuthenticationException(
                    "API key is not valid (disabled or expired)",
                    key.isExpired() ? AuthFailureReason.EXPIRED_CREDENTIALS : AuthFailureReason.DISABLED_KEY);
        }

        return AuthContext.builder()
                .fromApiKey(key)
                .build();
    }

    /**
     * Extracts API key from the request.
     * Priority: Authorization header > X-API-Key header > query param
     */
    private String extractApiKey(HttpServletRequest request) throws AuthenticationException {
        // 1. Check Authorization header (preferred)
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && !authHeader.isEmpty()) {
            if (authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length()).trim();
            } else {
                throw new AuthenticationException(
                        "Invalid Authorization header format. Expected: Bearer <token>",
                        AuthFailureReason.MALFORMED_HEADER);
            }
        }

        // 2. Check X-API-Key header
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);
        if (apiKeyHeader != null && !apiKeyHeader.isEmpty()) {
            return apiKeyHeader.trim();
        }

        // 3. Check query parameter (discouraged, log warning)
        String apiKeyParam = request.getParameter(API_KEY_PARAM);
        if (apiKeyParam != null && !apiKeyParam.isEmpty()) {
            logger.warn("API key provided via query parameter - this is insecure! " +
                    "Use Authorization header instead. Client: {}", getClientAddress(request));
            return apiKeyParam.trim();
        }

        return null;
    }

    /**
     * Gets the client IP address, accounting for proxies.
     */
    private String getClientAddress(HttpServletRequest request) {
        // Check for forwarded header (when behind proxy/load balancer)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            // Take the first IP in the chain
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Gets the underlying API key manager.
     */
    public ApiKeyManager getApiKeyManager() {
        return apiKeyManager;
    }
}

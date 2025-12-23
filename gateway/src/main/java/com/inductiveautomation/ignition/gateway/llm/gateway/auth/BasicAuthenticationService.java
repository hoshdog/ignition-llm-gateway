package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

import com.inductiveautomation.ignition.common.user.AuthChallenge;
import com.inductiveautomation.ignition.common.user.AuthenticatedUser;
import com.inductiveautomation.ignition.common.user.SimpleAuthChallenge;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.user.UserSourceManager;
import com.inductiveautomation.ignition.gateway.user.UserSourceProfile;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationException.AuthFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Authentication service using HTTP Basic Auth against Ignition's user management system.
 *
 * <p>This service authenticates requests using the Authorization header with Basic auth,
 * validating credentials against Ignition's configured user sources. User roles are
 * mapped to module permissions.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * curl -u admin:password http://localhost:8088/system/llm-gateway/info
 * </pre>
 *
 * <p><b>Security Note:</b> Basic auth transmits credentials Base64-encoded (NOT encrypted).
 * HTTPS should be used in production environments.</p>
 */
public class BasicAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(BasicAuthenticationService.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";

    /**
     * Default user source profile name. Can be configured if needed.
     */
    private static final String DEFAULT_USER_SOURCE = "default";

    private final GatewayContext gatewayContext;
    private final String userSourceProfile;
    private final Map<String, Set<Permission>> rolePermissionMap;

    /**
     * Creates a new BasicAuthenticationService using the default user source.
     */
    public BasicAuthenticationService(GatewayContext gatewayContext) {
        this(gatewayContext, DEFAULT_USER_SOURCE);
    }

    /**
     * Creates a new BasicAuthenticationService with a specific user source.
     */
    public BasicAuthenticationService(GatewayContext gatewayContext, String userSourceProfile) {
        this.gatewayContext = gatewayContext;
        this.userSourceProfile = userSourceProfile;
        this.rolePermissionMap = buildDefaultRoleMapping();
        logger.info("BasicAuthenticationService initialized with user source: {}", userSourceProfile);
    }

    /**
     * Builds the default role-to-permission mapping.
     *
     * <p>This mapping can be extended or overridden through configuration in future versions.</p>
     */
    private Map<String, Set<Permission>> buildDefaultRoleMapping() {
        Map<String, Set<Permission>> mapping = new HashMap<>();

        // Administrator - full access
        mapping.put("Administrator", EnumSet.of(Permission.ADMIN));

        // Developer - full CRUD on all resource types, no admin
        mapping.put("Developer", EnumSet.of(
                Permission.TAG_READ, Permission.TAG_CREATE, Permission.TAG_UPDATE,
                Permission.TAG_DELETE, Permission.TAG_WRITE_VALUE,
                Permission.VIEW_READ, Permission.VIEW_CREATE, Permission.VIEW_UPDATE, Permission.VIEW_DELETE,
                Permission.SCRIPT_READ, Permission.SCRIPT_CREATE, Permission.SCRIPT_UPDATE, Permission.SCRIPT_DELETE,
                Permission.NAMED_QUERY_READ, Permission.NAMED_QUERY_CREATE,
                Permission.NAMED_QUERY_UPDATE, Permission.NAMED_QUERY_DELETE, Permission.NAMED_QUERY_EXECUTE,
                Permission.PROJECT_READ
        ));

        // Operator - read/write for tags and views, read-only for others
        mapping.put("Operator", EnumSet.of(
                Permission.TAG_READ, Permission.TAG_WRITE_VALUE,
                Permission.VIEW_READ,
                Permission.PROJECT_READ
        ));

        // Default/Viewer - read-only access
        mapping.put("Viewer", EnumSet.of(Permission.READ_ALL));

        return mapping;
    }

    /**
     * Authenticates an incoming HTTP request using Basic auth.
     *
     * @param request The HTTP request to authenticate
     * @return AuthContext with user information and permissions
     * @throws AuthenticationException if authentication fails
     */
    public AuthContext authenticate(HttpServletRequest request) throws AuthenticationException {
        String clientAddress = getClientAddress(request);

        // Extract and validate Authorization header
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || authHeader.isEmpty()) {
            throw new AuthenticationException(
                    "No authentication credentials provided. Use Basic auth with Ignition credentials.",
                    AuthFailureReason.MISSING_CREDENTIALS);
        }

        if (!authHeader.startsWith(BASIC_PREFIX)) {
            throw new AuthenticationException(
                    "Invalid Authorization header format. Expected: Basic <base64(username:password)>",
                    AuthFailureReason.MALFORMED_HEADER);
        }

        // Decode Base64 credentials
        String base64Credentials = authHeader.substring(BASIC_PREFIX.length()).trim();
        String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(
                    "Invalid Base64 encoding in Authorization header",
                    AuthFailureReason.MALFORMED_HEADER);
        }

        // Split into username:password
        int colonIndex = credentials.indexOf(':');
        if (colonIndex < 0) {
            throw new AuthenticationException(
                    "Invalid credentials format. Expected: username:password",
                    AuthFailureReason.MALFORMED_HEADER);
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        if (username.isEmpty()) {
            throw new AuthenticationException(
                    "Username cannot be empty",
                    AuthFailureReason.MISSING_CREDENTIALS);
        }

        // Authenticate against Ignition's user source
        AuthenticatedUser authenticatedUser = authenticateWithIgnition(username, password, clientAddress);

        // Map roles to permissions
        Set<Permission> permissions = mapRolesToPermissions(authenticatedUser.getRoles());

        // Build and return AuthContext
        AuthContext context = AuthContext.builder()
                .fromIgnitionUser(authenticatedUser, userSourceProfile)
                .clientAddress(clientAddress)
                .permissions(permissions)
                .attribute("request.method", request.getMethod())
                .attribute("request.path", request.getRequestURI())
                .build();

        logger.debug("Authenticated user {} from {} with {} permissions",
                username, clientAddress, permissions.size());

        return context;
    }

    /**
     * Authenticates credentials against Ignition's user source.
     */
    private AuthenticatedUser authenticateWithIgnition(String username, String password, String clientAddress)
            throws AuthenticationException {
        try {
            UserSourceManager userSourceManager = gatewayContext.getUserSourceManager();
            if (userSourceManager == null) {
                logger.error("UserSourceManager not available");
                throw new AuthenticationException(
                        "Authentication service not available",
                        AuthFailureReason.INVALID_CREDENTIALS);
            }

            // Get the user source profile
            UserSourceProfile profile = userSourceManager.getProfile(userSourceProfile);
            if (profile == null) {
                logger.error("User source profile '{}' not found", userSourceProfile);
                throw new AuthenticationException(
                        "User source '" + userSourceProfile + "' not configured",
                        AuthFailureReason.INVALID_CREDENTIALS);
            }

            // Create auth challenge and authenticate
            AuthChallenge challenge = new SimpleAuthChallenge(username, password);
            AuthenticatedUser user = profile.authenticate(challenge);

            if (user == null) {
                logger.warn("Authentication failed for user '{}' from {}", username, clientAddress);
                throw new AuthenticationException(
                        "Invalid username or password",
                        AuthFailureReason.INVALID_CREDENTIALS);
            }

            logger.info("Successfully authenticated user '{}' from {} with roles: {}",
                    username, clientAddress, user.getRoles());
            return user;

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Authentication error for user '{}': {}", username, e.getMessage(), e);
            throw new AuthenticationException(
                    "Authentication error: " + e.getMessage(),
                    AuthFailureReason.INVALID_CREDENTIALS);
        }
    }

    /**
     * Maps Ignition roles to module permissions.
     *
     * <p>If a user has the Administrator role, they get ADMIN permission (full access).
     * Otherwise, permissions are aggregated from all matching roles.</p>
     */
    private Set<Permission> mapRolesToPermissions(Collection<String> roles) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

        if (roles == null || roles.isEmpty()) {
            // No roles = default read access
            permissions.add(Permission.READ_ALL);
            return permissions;
        }

        for (String role : roles) {
            // Check for exact match first
            if (rolePermissionMap.containsKey(role)) {
                permissions.addAll(rolePermissionMap.get(role));
            }
            // Check for case-insensitive Administrator match
            else if (role.equalsIgnoreCase("Administrator") || role.equalsIgnoreCase("Admin")) {
                permissions.add(Permission.ADMIN);
            }
            // Check for case-insensitive Developer match
            else if (role.equalsIgnoreCase("Developer") || role.equalsIgnoreCase("Dev")) {
                permissions.addAll(rolePermissionMap.get("Developer"));
            }
            // Check for case-insensitive Operator match
            else if (role.equalsIgnoreCase("Operator") || role.equalsIgnoreCase("Op")) {
                permissions.addAll(rolePermissionMap.get("Operator"));
            }
        }

        // If no specific roles matched, grant default read access
        if (permissions.isEmpty()) {
            permissions.add(Permission.READ_ALL);
        }

        return permissions;
    }

    /**
     * Gets the client IP address, accounting for proxies.
     */
    private String getClientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Returns the user source profile being used for authentication.
     */
    public String getUserSourceProfile() {
        return userSourceProfile;
    }

    /**
     * Returns a copy of the current role-to-permission mapping.
     */
    public Map<String, Set<Permission>> getRolePermissionMap() {
        Map<String, Set<Permission>> copy = new HashMap<>();
        for (Map.Entry<String, Set<Permission>> entry : rolePermissionMap.entrySet()) {
            copy.put(entry.getKey(), EnumSet.copyOf(entry.getValue()));
        }
        return copy;
    }
}

package com.inductiveautomation.ignition.gateway.llm.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inductiveautomation.ignition.gateway.llm.gateway.LLMGatewayContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.LLMGatewayModuleHolder;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationService;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoint to trigger project resource scanning.
 *
 * <p>This endpoint allows external callers (MCP server, scripts, etc.) to trigger
 * a project resource scan after creating or modifying resources via filesystem.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /system/llm-gateway-scan/ - Triggers a project resource scan</li>
 *   <li>GET /system/llm-gateway-scan/ - Returns endpoint information</li>
 * </ul>
 *
 * <p>Authentication is required via API key in the Authorization header.</p>
 */
public class ProjectScanServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProjectScanServlet.class);

    private LLMGatewayContext llmContext;
    private ObjectMapper objectMapper;

    /**
     * No-arg constructor required for servlet container instantiation.
     */
    public ProjectScanServlet() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initializes dependencies from the module holder if not already set.
     */
    private void ensureInitialized() {
        if (llmContext == null) {
            llmContext = LLMGatewayModuleHolder.getContext();
            if (llmContext == null) {
                logger.warn("LLMGatewayContext not available - module may not be fully started");
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ensureInitialized();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("endpoint", "project-scan");
        info.put("method", "POST");
        info.put("description", "Triggers Gateway project resource scan");
        info.put("authentication", "Required - API key via Authorization header");
        info.put("usage", "POST to this endpoint after creating/modifying resources via filesystem");

        resp.getWriter().write(objectMapper.writeValueAsString(info));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ensureInitialized();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Authenticate request
        if (llmContext == null) {
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "LLM Gateway module not available");
            return;
        }

        AuthContext authContext;
        try {
            AuthenticationService authService = llmContext.getAuthenticationService();
            authContext = authService.authenticate(req);
        } catch (Exception e) {
            logger.warn("Authentication failed: {}", e.getMessage());
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication failed: " + e.getMessage());
            return;
        }

        // Trigger project scan
        try {
            ProjectManager projectManager = llmContext.getGatewayContext().getProjectManager();
            if (projectManager == null) {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "ProjectManager not available");
                return;
            }

            projectManager.requestScan();
            logger.info("Project scan triggered via REST endpoint by user: {}", authContext.getUserId());

            // Log to audit
            llmContext.getAuditLogger().logSystemEvent(
                    "PROJECT_SCAN_TRIGGERED",
                    "Project resource scan triggered via REST endpoint",
                    Map.of("user", authContext.getUserId())
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Project resource scan triggered successfully");
            result.put("timestamp", Instant.now().toString());
            result.put("triggeredBy", authContext.getUserId());
            result.put("note", "Use File > Update Project in Designer to see changes immediately");

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(objectMapper.writeValueAsString(result));

        } catch (Exception e) {
            logger.error("Failed to trigger project scan", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to trigger project scan: " + e.getMessage());
        }
    }

    /**
     * Sends an error response.
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", Instant.now().toString());

        resp.getWriter().write(objectMapper.writeValueAsString(error));
    }
}

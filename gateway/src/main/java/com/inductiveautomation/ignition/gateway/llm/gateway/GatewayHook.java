package com.inductiveautomation.ignition.gateway.llm.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
// Note: IConfigTab was removed in Ignition 8.3.0
// Configuration panels would use the new ConfigCategory / ConfigPanel API
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.gateway.api.LLMEndpoint;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The GatewayHook is the entry point for the LLM Gateway module.
 * It manages the module lifecycle and registers the REST API endpoints.
 */
public class GatewayHook extends AbstractGatewayModuleHook {

    private static final Logger logger = LoggerFactory.getLogger(GatewayHook.class);

    /**
     * Servlet mount point - the servlet will be available at /system/llm-gateway/*
     */
    private static final String SERVLET_NAME = "llm-gateway";

    private GatewayContext gatewayContext;
    private LLMGatewayContext llmContext;
    private AuditLogger auditLogger;

    @Override
    public void setup(GatewayContext context) {
        this.gatewayContext = context;
        logger.info("LLM Gateway module setup starting...");

        try {
            // Initialize audit logger first (everything depends on traceability)
            this.auditLogger = new AuditLogger(context);
            logger.debug("Audit logger initialized");

            // Initialize the LLM Gateway context
            this.llmContext = new LLMGatewayContext(context, auditLogger);
            logger.debug("LLM Gateway context initialized");

            auditLogger.logSystemEvent(
                    "MODULE_SETUP",
                    "LLM Gateway module setup completed successfully",
                    null
            );

            logger.info("LLM Gateway module setup completed");

        } catch (Exception e) {
            logger.error("Failed to setup LLM Gateway module", e);
            throw new RuntimeException("LLM Gateway setup failed", e);
        }
    }

    @Override
    public void startup(LicenseState licenseState) {
        logger.info("LLM Gateway module starting up with license state: {}", licenseState);

        try {
            // Set up the static holder so LLMEndpoint can access module context
            // This is needed because WebResourceManager.addServlet() only takes a Class,
            // not an instance, so the servlet must get its dependencies via the holder
            LLMGatewayModuleHolder.setContext(llmContext);
            LLMGatewayModuleHolder.setConversationManager(llmContext.getConversationManager());
            logger.debug("LLM Gateway module holder initialized");

            // Register the servlet with the Gateway's web server
            // The servlet will be available at /system/llm-gateway/*
            gatewayContext.getWebResourceManager().addServlet(
                    SERVLET_NAME,
                    LLMEndpoint.class
            );
            logger.info("LLM Gateway servlet registered at /system/{}/", SERVLET_NAME);

            auditLogger.logSystemEvent(
                    "MODULE_STARTUP",
                    "LLM Gateway module started successfully",
                    null
            );

            logger.info("LLM Gateway module startup completed");

        } catch (Exception e) {
            logger.error("Failed to start LLM Gateway module", e);
            auditLogger.logSystemEvent(
                    "MODULE_STARTUP_FAILED",
                    "LLM Gateway module startup failed: " + e.getMessage(),
                    null
            );
            throw new RuntimeException("LLM Gateway startup failed", e);
        }
    }

    @Override
    public void shutdown() {
        logger.info("LLM Gateway module shutting down...");

        try {
            // Unregister the servlet from the Gateway's web server
            if (gatewayContext != null) {
                try {
                    gatewayContext.getWebResourceManager().removeServlet(SERVLET_NAME);
                    logger.info("LLM Gateway servlet unregistered");
                } catch (Exception e) {
                    logger.warn("Error unregistering servlet: {}", e.getMessage());
                }
            }

            // Clear the static holder
            LLMGatewayModuleHolder.clear();
            logger.debug("LLM Gateway module holder cleared");

            // Clean up the LLM context
            if (llmContext != null) {
                llmContext.shutdown();
                logger.debug("LLM Gateway context shut down");
            }

            if (auditLogger != null) {
                auditLogger.logSystemEvent(
                        "MODULE_SHUTDOWN",
                        "LLM Gateway module shut down successfully",
                        null
                );
            }

            logger.info("LLM Gateway module shutdown completed");

        } catch (Exception e) {
            logger.error("Error during LLM Gateway module shutdown", e);
        }
    }

    @Override
    public boolean isFreeModule() {
        return true; // For development; change for production licensing
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    /**
     * Returns the module display name.
     */
    public String getModuleDisplayName() {
        return LLMGatewayConstants.MODULE_NAME;
    }

    /**
     * Provides access to the LLM Gateway context for internal use.
     */
    public Optional<LLMGatewayContext> getLLMContext() {
        return Optional.ofNullable(llmContext);
    }

    // Note: getConfigPanels() removed - IConfigTab was deprecated in 8.3.0
    // TODO: Implement configuration using the new ConfigCategory / ConfigPanel API if needed

}

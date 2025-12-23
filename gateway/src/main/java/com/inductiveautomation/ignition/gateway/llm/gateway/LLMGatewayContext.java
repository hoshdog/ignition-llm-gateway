package com.inductiveautomation.ignition.gateway.llm.gateway;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.ApiKeyManager;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationService;
import com.inductiveautomation.ignition.gateway.llm.gateway.conversation.ConversationManager;
import com.inductiveautomation.ignition.gateway.llm.gateway.execution.ActionExecutor;
import com.inductiveautomation.ignition.gateway.llm.gateway.policy.EnvironmentMode;
import com.inductiveautomation.ignition.gateway.llm.gateway.policy.PolicyEngine;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProviderFactory;
import com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit.RateLimiter;
import com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central context for the LLM Gateway module.
 * Holds references to all major subsystems and provides lifecycle management.
 */
public class LLMGatewayContext {

    private static final Logger logger = LoggerFactory.getLogger(LLMGatewayContext.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final PolicyEngine policyEngine;
    private final ApiKeyManager apiKeyManager;
    private final AuthenticationService authenticationService;
    private final ActionExecutor actionExecutor;
    private final LLMProviderFactory providerFactory;
    private final ConversationManager conversationManager;
    private final RateLimiter rateLimiter;
    private final ExecutorService executorService;
    private final EnvironmentMode environmentMode;

    private volatile boolean running = false;

    public LLMGatewayContext(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;

        // Determine environment mode from system property or default
        this.environmentMode = determineEnvironmentMode();
        logger.info("LLM Gateway running in {} mode", environmentMode);

        // Initialize policy engine
        this.policyEngine = new PolicyEngine(environmentMode);
        logger.debug("Policy engine initialized");

        // Initialize authentication services
        this.apiKeyManager = new ApiKeyManager();
        this.authenticationService = new AuthenticationService(apiKeyManager);
        logger.debug("Authentication services initialized");

        // Create executor service for async operations
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "LLMGateway-Worker");
                    t.setDaemon(true);
                    return t;
                }
        );

        // Initialize action executor
        this.actionExecutor = new ActionExecutor(this);
        logger.debug("Action executor initialized");

        // Initialize LLM provider factory
        this.providerFactory = new LLMProviderFactory();
        logger.debug("LLM provider factory initialized");

        // Initialize rate limiter with default configuration
        this.rateLimiter = new RateLimiter(RateLimitConfig.defaultConfig());
        logger.debug("Rate limiter initialized");

        // Initialize conversation manager
        this.conversationManager = new ConversationManager(
                providerFactory, actionExecutor, auditLogger, policyEngine);
        logger.debug("Conversation manager initialized");

        this.running = true;
    }

    /**
     * Determines the environment mode based on system properties or configuration.
     */
    private EnvironmentMode determineEnvironmentMode() {
        String modeStr = System.getProperty("llm.gateway.environment", LLMGatewayConstants.ENV_MODE_DEV);
        try {
            return EnvironmentMode.fromString(modeStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown environment mode '{}', defaulting to DEVELOPMENT", modeStr);
            return EnvironmentMode.DEVELOPMENT;
        }
    }

    /**
     * Returns the Ignition GatewayContext.
     */
    public GatewayContext getGatewayContext() {
        return gatewayContext;
    }

    /**
     * Returns the audit logger for recording all actions.
     */
    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * Returns the policy engine for authorization decisions.
     */
    public PolicyEngine getPolicyEngine() {
        return policyEngine;
    }

    /**
     * Returns the API key manager.
     */
    public ApiKeyManager getApiKeyManager() {
        return apiKeyManager;
    }

    /**
     * Returns the authentication service.
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * Returns the action executor for processing action requests.
     */
    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    /**
     * Returns the executor service for async operations.
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Returns the current environment mode.
     */
    public EnvironmentMode getEnvironmentMode() {
        return environmentMode;
    }

    /**
     * Returns the LLM provider factory.
     */
    public LLMProviderFactory getProviderFactory() {
        return providerFactory;
    }

    /**
     * Returns the conversation manager.
     */
    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    /**
     * Returns the rate limiter.
     */
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Returns whether the module is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Shuts down the context and releases resources.
     */
    public void shutdown() {
        if (!running) {
            return;
        }

        running = false;
        logger.info("Shutting down LLM Gateway context...");

        // Shutdown executor service gracefully
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                logger.warn("Executor service did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("LLM Gateway context shutdown complete");
    }
}

package com.inductiveautomation.ignition.gateway.llm.gateway;

import com.inductiveautomation.ignition.gateway.llm.gateway.conversation.ConversationManager;

/**
 * Static holder for module dependencies.
 * This allows the LLMEndpoint servlet to access module components
 * when instantiated by the Gateway's servlet container.
 *
 * The Gateway's WebResourceManager only supports addServlet(name, class),
 * which means servlets must have a no-arg constructor. This holder
 * provides a way for the servlet to access module dependencies after
 * construction.
 */
public final class LLMGatewayModuleHolder {

    private static volatile LLMGatewayContext context;
    private static volatile ConversationManager conversationManager;

    private LLMGatewayModuleHolder() {
        // Utility class - no instantiation
    }

    /**
     * Sets the module context. Called by GatewayHook during startup.
     */
    public static void setContext(LLMGatewayContext ctx) {
        context = ctx;
    }

    /**
     * Gets the module context. Called by LLMEndpoint during initialization.
     */
    public static LLMGatewayContext getContext() {
        return context;
    }

    /**
     * Sets the conversation manager. Called by GatewayHook during startup.
     */
    public static void setConversationManager(ConversationManager mgr) {
        conversationManager = mgr;
    }

    /**
     * Gets the conversation manager. Called by LLMEndpoint during initialization.
     */
    public static ConversationManager getConversationManager() {
        return conversationManager;
    }

    /**
     * Clears all references. Called by GatewayHook during shutdown.
     */
    public static void clear() {
        context = null;
        conversationManager = null;
    }

    /**
     * Checks if the module is initialized and ready.
     */
    public static boolean isInitialized() {
        return context != null;
    }
}

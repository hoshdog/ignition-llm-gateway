package com.inductiveautomation.ignition.gateway.llm.gateway.util;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Utility for notifying connected Designers of resource changes.
 *
 * <p>This class provides a mechanism to log and track resource changes made via
 * the LLM Gateway. In future versions, this could be extended to send actual
 * push notifications to connected Designers if a Designer-scoped module hook
 * is implemented.</p>
 *
 * <p>For now, this class primarily serves as:</p>
 * <ul>
 *   <li>A logging mechanism for resource changes</li>
 *   <li>A placeholder for future Designer notification functionality</li>
 *   <li>Documentation of the intended notification pattern</li>
 * </ul>
 *
 * <p>For immediate visibility of changes, the recommended workflow is:</p>
 * <ol>
 *   <li>Create/update resources via filesystem</li>
 *   <li>Call ProjectManager.requestScan() to detect changes</li>
 *   <li>User clicks File &gt; Update Project in Designer</li>
 * </ol>
 */
public class DesignerNotifier {
    private static final Logger logger = LoggerFactory.getLogger(DesignerNotifier.class);
    private static final String MODULE_ID = "llm-gateway";

    private final GatewayContext gatewayContext;

    public DesignerNotifier(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    /**
     * Logs a resource change notification. In future versions, this could
     * send actual push notifications to connected Designers.
     *
     * @param resourceType The type of resource changed (e.g., "view", "script", "named-query")
     * @param resourcePath The full path of the resource that changed
     */
    public void notifyResourceChanged(String resourceType, String resourcePath) {
        // Log the notification - in future versions, this could send actual push notifications
        // to Designers using GatewaySessionManager.sendNotification() if a Designer module hook
        // is implemented to receive and act on the notifications.
        logger.info("Resource changed notification: type={}, path={}", resourceType, resourcePath);
        logger.debug("Designer notification logged - users should click File > Update Project to see changes");
    }

    /**
     * Logs a batch resource change notification. In future versions, this could
     * send actual push notifications to connected Designers.
     *
     * @param resourceType The type of resources changed
     * @param projectName The project where changes occurred
     * @param changeCount The number of resources changed
     */
    public void notifyBatchResourceChange(String resourceType, String projectName, int changeCount) {
        logger.info("Batch resource change notification: type={}, project={}, count={}",
            resourceType, projectName, changeCount);
        logger.debug("Designer notification logged for batch change - users should click File > Update Project");
    }

    /**
     * Serializable notification payload for single resource changes.
     * Kept for future use when Designer push notifications are implemented.
     */
    public static class ResourceChangeNotification implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String resourceType;
        public final String resourcePath;
        public final long timestamp;

        public ResourceChangeNotification(String resourceType, String resourcePath, long timestamp) {
            this.resourceType = resourceType;
            this.resourcePath = resourcePath;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "ResourceChangeNotification{" +
                    "resourceType='" + resourceType + '\'' +
                    ", resourcePath='" + resourcePath + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * Serializable notification payload for batch resource changes.
     * Kept for future use when Designer push notifications are implemented.
     */
    public static class BatchResourceChangeNotification implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String resourceType;
        public final String projectName;
        public final int changeCount;
        public final long timestamp;

        public BatchResourceChangeNotification(String resourceType, String projectName,
                                                int changeCount, long timestamp) {
            this.resourceType = resourceType;
            this.projectName = projectName;
            this.changeCount = changeCount;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "BatchResourceChangeNotification{" +
                    "resourceType='" + resourceType + '\'' +
                    ", projectName='" + projectName + '\'' +
                    ", changeCount=" + changeCount +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}

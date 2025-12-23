package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates Perspective view configurations.
 */
public class ViewConfigValidator {

    // Known component types (a subset for validation)
    private static final Set<String> KNOWN_COMPONENT_TYPES = new HashSet<>();

    static {
        // Container components
        KNOWN_COMPONENT_TYPES.add("ia.container.flex");
        KNOWN_COMPONENT_TYPES.add("ia.container.coord");
        KNOWN_COMPONENT_TYPES.add("ia.container.column");
        KNOWN_COMPONENT_TYPES.add("ia.container.row");
        KNOWN_COMPONENT_TYPES.add("ia.container.split");
        KNOWN_COMPONENT_TYPES.add("ia.container.tab");
        KNOWN_COMPONENT_TYPES.add("ia.container.card");
        KNOWN_COMPONENT_TYPES.add("ia.container.breakpoint");

        // Display components
        KNOWN_COMPONENT_TYPES.add("ia.display.label");
        KNOWN_COMPONENT_TYPES.add("ia.display.icon");
        KNOWN_COMPONENT_TYPES.add("ia.display.image");
        KNOWN_COMPONENT_TYPES.add("ia.display.markdown");
        KNOWN_COMPONENT_TYPES.add("ia.display.gauge");
        KNOWN_COMPONENT_TYPES.add("ia.display.led-display");
        KNOWN_COMPONENT_TYPES.add("ia.display.progress-bar");

        // Input components
        KNOWN_COMPONENT_TYPES.add("ia.input.button");
        KNOWN_COMPONENT_TYPES.add("ia.input.text-field");
        KNOWN_COMPONENT_TYPES.add("ia.input.text-area");
        KNOWN_COMPONENT_TYPES.add("ia.input.dropdown");
        KNOWN_COMPONENT_TYPES.add("ia.input.checkbox");
        KNOWN_COMPONENT_TYPES.add("ia.input.radio-group");
        KNOWN_COMPONENT_TYPES.add("ia.input.slider");
        KNOWN_COMPONENT_TYPES.add("ia.input.toggle-switch");
        KNOWN_COMPONENT_TYPES.add("ia.input.date-time-input");
        KNOWN_COMPONENT_TYPES.add("ia.input.numeric-entry-field");

        // Chart components
        KNOWN_COMPONENT_TYPES.add("ia.chart.pie");
        KNOWN_COMPONENT_TYPES.add("ia.chart.bar");
        KNOWN_COMPONENT_TYPES.add("ia.chart.xy-chart");
        KNOWN_COMPONENT_TYPES.add("ia.chart.time-series");

        // Table components
        KNOWN_COMPONENT_TYPES.add("ia.table.table");
        KNOWN_COMPONENT_TYPES.add("ia.table.power-table");

        // Embedding components
        KNOWN_COMPONENT_TYPES.add("ia.display.view");
        KNOWN_COMPONENT_TYPES.add("ia.display.iframe");
    }

    /**
     * Validates a view configuration.
     */
    public ValidationResult validate(Map<String, Object> config) {
        ValidationResult.Builder result = ValidationResult.builder();

        if (config == null || config.isEmpty()) {
            result.addError("root", "View configuration cannot be empty");
            return result.build();
        }

        // Root component is required
        if (!config.containsKey("root")) {
            // If there's no explicit root, check if this might be a root component itself
            if (config.containsKey("type")) {
                // This is a root component
                validateComponent(config, "root", result);
            } else {
                result.addError("root", "View must have a root component");
            }
        } else {
            Object root = config.get("root");
            if (!(root instanceof Map)) {
                result.addError("root", "Root component must be an object");
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> rootComponent = (Map<String, Object>) root;
                validateComponent(rootComponent, "root", result);
            }
        }

        // Validate params if present
        if (config.containsKey("params")) {
            Object params = config.get("params");
            if (params != null && !(params instanceof Map)) {
                result.addError("params", "Params must be an object");
            }
        }

        return result.build();
    }

    /**
     * Validates a component configuration recursively.
     */
    @SuppressWarnings("unchecked")
    private void validateComponent(Map<String, Object> component, String path,
                                    ValidationResult.Builder result) {
        // Type is required
        if (!component.containsKey("type")) {
            result.addError(path + ".type", "Component must have a type");
            return;
        }

        String type = String.valueOf(component.get("type"));

        // Validate known types
        if (!KNOWN_COMPONENT_TYPES.contains(type) && !type.startsWith("ia.")) {
            result.addWarning(path + ".type",
                    "Unknown component type: " + type + ". This may be a custom component.");
        }

        // Validate props if present
        if (component.containsKey("props")) {
            Object props = component.get("props");
            if (props != null && !(props instanceof Map)) {
                result.addError(path + ".props", "Props must be an object");
            } else if (props instanceof Map) {
                validateProps((Map<String, Object>) props, path + ".props", result);
            }
        }

        // Validate children recursively
        if (component.containsKey("children")) {
            Object children = component.get("children");
            if (children instanceof List) {
                List<?> childList = (List<?>) children;
                for (int i = 0; i < childList.size(); i++) {
                    Object child = childList.get(i);
                    if (child instanceof Map) {
                        validateComponent((Map<String, Object>) child,
                                path + ".children[" + i + "]", result);
                    }
                }
            }
        }

        // Validate events if present
        if (component.containsKey("events")) {
            Object events = component.get("events");
            if (events instanceof Map) {
                validateEvents((Map<String, Object>) events, path + ".events", result);
            }
        }
    }

    /**
     * Validates component props.
     */
    @SuppressWarnings("unchecked")
    private void validateProps(Map<String, Object> props, String path,
                                ValidationResult.Builder result) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String propName = entry.getKey();
            Object propValue = entry.getValue();

            // Check for binding syntax
            if (propValue instanceof Map) {
                Map<String, Object> binding = (Map<String, Object>) propValue;
                if (binding.containsKey("type") && binding.containsKey("config")) {
                    validateBinding(binding, path + "." + propName, result);
                }
            }
        }
    }

    /**
     * Validates a binding configuration.
     */
    @SuppressWarnings("unchecked")
    private void validateBinding(Map<String, Object> binding, String path,
                                  ValidationResult.Builder result) {
        String type = String.valueOf(binding.get("type"));

        // Warn about potentially dangerous binding types
        if ("expr".equals(type) || "script".equals(type)) {
            result.addWarning(path,
                    "Binding type '" + type + "' executes code. Review carefully.");
        }

        // Check for tag bindings without proper paths
        if ("tag".equals(type)) {
            Object config = binding.get("config");
            if (config instanceof Map) {
                Map<String, Object> tagConfig = (Map<String, Object>) config;
                Object tagPath = tagConfig.get("path");
                if (tagPath == null || tagPath.toString().isEmpty()) {
                    result.addError(path + ".config.path",
                            "Tag binding requires a path");
                }
            }
        }

        // Check for property bindings
        if ("property".equals(type)) {
            Object config = binding.get("config");
            if (config instanceof Map) {
                Map<String, Object> propConfig = (Map<String, Object>) config;
                Object propPath = propConfig.get("path");
                if (propPath == null || propPath.toString().isEmpty()) {
                    result.addError(path + ".config.path",
                            "Property binding requires a path");
                }
            }
        }
    }

    /**
     * Validates event handlers.
     */
    @SuppressWarnings("unchecked")
    private void validateEvents(Map<String, Object> events, String path,
                                 ValidationResult.Builder result) {
        for (Map.Entry<String, Object> entry : events.entrySet()) {
            String eventName = entry.getKey();
            Object eventConfig = entry.getValue();

            if (eventConfig instanceof Map) {
                Map<String, Object> config = (Map<String, Object>) eventConfig;

                // Check for script actions
                if (config.containsKey("actions")) {
                    Object actions = config.get("actions");
                    if (actions instanceof List) {
                        List<Map<String, Object>> actionList = (List<Map<String, Object>>) actions;
                        for (int i = 0; i < actionList.size(); i++) {
                            Map<String, Object> action = actionList.get(i);
                            String actionType = String.valueOf(action.get("type"));

                            if ("script".equals(actionType)) {
                                result.addWarning(
                                        path + "." + eventName + ".actions[" + i + "]",
                                        "Script action detected. Review for security implications.");
                            }
                        }
                    }
                }
            }
        }
    }
}

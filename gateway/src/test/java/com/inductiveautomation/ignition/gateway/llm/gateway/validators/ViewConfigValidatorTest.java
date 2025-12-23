package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ViewConfigValidator.
 */
class ViewConfigValidatorTest {

    private ViewConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ViewConfigValidator();
    }

    @Test
    void testValidate_nullConfig() {
        ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "root".equals(e.getField())));
    }

    @Test
    void testValidate_emptyConfig() {
        Map<String, Object> config = new HashMap<>();
        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
    }

    @Test
    void testValidate_simpleValidView() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_rootComponentWithType() {
        // Config that is itself a root component
        Map<String, Object> config = new HashMap<>();
        config.put("type", "ia.container.flex");

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_rootComponentMissingType() {
        Map<String, Object> root = new HashMap<>();
        // Missing 'type' field

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("type")));
    }

    @Test
    void testValidate_invalidRootType() {
        Map<String, Object> config = new HashMap<>();
        config.put("root", "not an object");

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
    }

    @Test
    void testValidate_knownComponentType() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testValidate_unknownComponentType() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "custom.unknown.component");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid()); // Still valid, just with warning
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Unknown component type")));
    }

    @Test
    void testValidate_iaComponentType() {
        // Unknown but starts with ia. - should not warn
        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.new.component");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_invalidProps() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", "not an object");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Props must be an object")));
    }

    @Test
    void testValidate_validProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("text", "Hello World");
        props.put("style", new HashMap<>());

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_tagBinding() {
        Map<String, Object> tagConfig = new HashMap<>();
        tagConfig.put("path", "[default]MyTag");

        Map<String, Object> binding = new HashMap<>();
        binding.put("type", "tag");
        binding.put("config", tagConfig);

        Map<String, Object> props = new HashMap<>();
        props.put("value", binding);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_tagBindingMissingPath() {
        Map<String, Object> tagConfig = new HashMap<>();
        // Missing 'path'

        Map<String, Object> binding = new HashMap<>();
        binding.put("type", "tag");
        binding.put("config", tagConfig);

        Map<String, Object> props = new HashMap<>();
        props.put("value", binding);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("path")));
    }

    @Test
    void testValidate_propertyBindingMissingPath() {
        Map<String, Object> propConfig = new HashMap<>();
        // Missing 'path'

        Map<String, Object> binding = new HashMap<>();
        binding.put("type", "property");
        binding.put("config", propConfig);

        Map<String, Object> props = new HashMap<>();
        props.put("text", binding);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
    }

    @Test
    void testValidate_scriptBindingWarning() {
        Map<String, Object> scriptConfig = new HashMap<>();
        scriptConfig.put("script", "return 42");

        Map<String, Object> binding = new HashMap<>();
        binding.put("type", "script");
        binding.put("config", scriptConfig);

        Map<String, Object> props = new HashMap<>();
        props.put("value", binding);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid()); // Valid but with warning
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("script") && w.contains("code")));
    }

    @Test
    void testValidate_exprBindingWarning() {
        Map<String, Object> exprConfig = new HashMap<>();
        exprConfig.put("expression", "{this.custom.value} * 2");

        Map<String, Object> binding = new HashMap<>();
        binding.put("type", "expr");
        binding.put("config", exprConfig);

        Map<String, Object> props = new HashMap<>();
        props.put("value", binding);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.display.label");
        root.put("props", props);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void testValidate_childComponents() {
        Map<String, Object> child1 = new HashMap<>();
        child1.put("type", "ia.display.label");

        Map<String, Object> child2 = new HashMap<>();
        child2.put("type", "ia.input.button");

        List<Object> children = new ArrayList<>();
        children.add(child1);
        children.add(child2);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");
        root.put("children", children);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_childComponentMissingType() {
        Map<String, Object> child = new HashMap<>();
        // Missing 'type'

        List<Object> children = new ArrayList<>();
        children.add(child);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");
        root.put("children", children);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
    }

    @Test
    void testValidate_nestedChildren() {
        Map<String, Object> grandchild = new HashMap<>();
        grandchild.put("type", "ia.display.label");

        List<Object> innerChildren = new ArrayList<>();
        innerChildren.add(grandchild);

        Map<String, Object> child = new HashMap<>();
        child.put("type", "ia.container.flex");
        child.put("children", innerChildren);

        List<Object> children = new ArrayList<>();
        children.add(child);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");
        root.put("children", children);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_scriptEventWarning() {
        Map<String, Object> scriptAction = new HashMap<>();
        scriptAction.put("type", "script");
        scriptAction.put("config", new HashMap<>());

        List<Object> actions = new ArrayList<>();
        actions.add(scriptAction);

        Map<String, Object> onClick = new HashMap<>();
        onClick.put("actions", actions);

        Map<String, Object> events = new HashMap<>();
        events.put("onClick", onClick);

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.input.button");
        root.put("events", events);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Script action")));
    }

    @Test
    void testValidate_invalidParams() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);
        config.put("params", "not an object");

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "params".equals(e.getField())));
    }

    @Test
    void testValidate_validParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", new HashMap<>());
        params.put("mode", new HashMap<>());

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);
        config.put("params", params);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_allContainerTypes() {
        String[] containerTypes = {
                "ia.container.flex",
                "ia.container.coord",
                "ia.container.column",
                "ia.container.row",
                "ia.container.split",
                "ia.container.tab",
                "ia.container.card",
                "ia.container.breakpoint"
        };

        for (String type : containerTypes) {
            Map<String, Object> root = new HashMap<>();
            root.put("type", type);

            Map<String, Object> config = new HashMap<>();
            config.put("root", root);

            ValidationResult result = validator.validate(config);
            assertTrue(result.isValid(), "Expected valid for type: " + type);
            assertTrue(result.getWarnings().isEmpty(),
                    "Expected no warnings for known type: " + type);
        }
    }

    @Test
    void testValidate_allDisplayTypes() {
        String[] displayTypes = {
                "ia.display.label",
                "ia.display.icon",
                "ia.display.image",
                "ia.display.markdown",
                "ia.display.gauge",
                "ia.display.led-display",
                "ia.display.progress-bar"
        };

        for (String type : displayTypes) {
            Map<String, Object> root = new HashMap<>();
            root.put("type", type);

            Map<String, Object> config = new HashMap<>();
            config.put("root", root);

            ValidationResult result = validator.validate(config);
            assertTrue(result.isValid(), "Expected valid for type: " + type);
        }
    }

    @Test
    void testValidate_allInputTypes() {
        String[] inputTypes = {
                "ia.input.button",
                "ia.input.text-field",
                "ia.input.text-area",
                "ia.input.dropdown",
                "ia.input.checkbox",
                "ia.input.radio-group",
                "ia.input.slider",
                "ia.input.toggle-switch",
                "ia.input.date-time-input",
                "ia.input.numeric-entry-field"
        };

        for (String type : inputTypes) {
            Map<String, Object> root = new HashMap<>();
            root.put("type", type);

            Map<String, Object> config = new HashMap<>();
            config.put("root", root);

            ValidationResult result = validator.validate(config);
            assertTrue(result.isValid(), "Expected valid for type: " + type);
        }
    }

    @Test
    void testValidate_complexView() {
        // Build a realistic view structure
        Map<String, Object> labelProps = new HashMap<>();
        labelProps.put("text", "Dashboard");

        Map<String, Object> label = new HashMap<>();
        label.put("type", "ia.display.label");
        label.put("props", labelProps);

        Map<String, Object> buttonProps = new HashMap<>();
        buttonProps.put("text", "Refresh");

        Map<String, Object> button = new HashMap<>();
        button.put("type", "ia.input.button");
        button.put("props", buttonProps);

        List<Object> headerChildren = new ArrayList<>();
        headerChildren.add(label);
        headerChildren.add(button);

        Map<String, Object> headerProps = new HashMap<>();
        headerProps.put("direction", "row");

        Map<String, Object> header = new HashMap<>();
        header.put("type", "ia.container.flex");
        header.put("props", headerProps);
        header.put("children", headerChildren);

        Map<String, Object> table = new HashMap<>();
        table.put("type", "ia.table.table");

        List<Object> rootChildren = new ArrayList<>();
        rootChildren.add(header);
        rootChildren.add(table);

        Map<String, Object> rootProps = new HashMap<>();
        rootProps.put("direction", "column");

        Map<String, Object> root = new HashMap<>();
        root.put("type", "ia.container.flex");
        root.put("props", rootProps);
        root.put("children", rootChildren);

        Map<String, Object> config = new HashMap<>();
        config.put("root", root);

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().isEmpty());
    }
}

package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates tag configuration payloads.
 * Checks for valid tag types, data types, and configuration options.
 */
public class TagConfigValidator {

    private static final Set<String> VALID_TAG_TYPES = Set.of(
            "AtomicTag",
            "UdtInstance",
            "UdtDefinition",
            "Folder",
            "OpcTag",
            "ExpressionTag",
            "QueryTag",
            "DerivedTag",
            "ReferenceTag"
    );

    private static final Set<String> VALID_DATA_TYPES = Set.of(
            "Int1", "Int2", "Int4", "Int8",
            "Float4", "Float8",
            "Boolean", "String",
            "DateTime", "DataSet", "Document",
            "Text", "BooleanArray", "IntegerArray",
            "LongArray", "FloatArray", "DoubleArray",
            "StringArray", "DateTimeArray"
    );

    private static final Set<String> VALID_OPC_TYPES = Set.of(
            "ReadWrite", "ReadOnly", "WriteOnly"
    );

    // Pattern for valid tag names: starts with letter/underscore, alphanumeric after
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    // Characters not allowed in tag paths
    private static final Pattern INVALID_PATH_CHARS = Pattern.compile("[\\\\<>\"'|?*]");

    /**
     * Validates a tag configuration payload.
     */
    public ValidationResult validate(Map<String, Object> config) {
        ValidationResult.Builder result = ValidationResult.builder();

        // Check for required or common fields
        validateName(config, result);
        validateTagType(config, result);
        validateDataType(config, result);
        validateOpcSettings(config, result);
        validateExpression(config, result);
        validateEngineering(config, result);
        validateAlarmSettings(config, result);

        return result.build();
    }

    private void validateName(Map<String, Object> config, ValidationResult.Builder result) {
        Object name = config.get("name");

        if (name == null) {
            // Name might be derived from path, so only warn
            result.addWarning("Tag name not specified. Will be derived from path if creating.");
            return;
        }

        String nameStr = name.toString();

        if (nameStr.isEmpty()) {
            result.addError("name", "Tag name cannot be empty");
            return;
        }

        if (nameStr.contains("/") || nameStr.contains("\\")) {
            result.addError("name", "Tag name cannot contain path separators (/ or \\)");
        }

        if (INVALID_PATH_CHARS.matcher(nameStr).find()) {
            result.addError("name", "Tag name contains invalid characters");
        }

        if (!TAG_NAME_PATTERN.matcher(nameStr).matches()) {
            result.addWarning("name", "Tag name contains special characters. " +
                    "Recommended: use only letters, numbers, and underscores, starting with a letter or underscore.");
        }

        if (nameStr.length() > 100) {
            result.addWarning("name", "Tag name is very long (>100 chars). Consider a shorter name for clarity.");
        }
    }

    private void validateTagType(Map<String, Object> config, ValidationResult.Builder result) {
        Object tagType = config.get("tagType");

        if (tagType == null) {
            // Default to AtomicTag if not specified
            return;
        }

        String tagTypeStr = tagType.toString();

        if (!VALID_TAG_TYPES.contains(tagTypeStr)) {
            result.addError("tagType",
                    "Invalid tag type: " + tagTypeStr + ". Valid types: " + VALID_TAG_TYPES);
        }

        // Type-specific validation
        if ("UdtInstance".equals(tagTypeStr)) {
            if (!config.containsKey("typeId")) {
                result.addError("typeId", "UDT Instance requires typeId to be specified");
            }
        }

        if ("ExpressionTag".equals(tagTypeStr)) {
            if (!config.containsKey("expression")) {
                result.addError("expression", "Expression tag requires expression to be specified");
            }
        }

        if ("OpcTag".equals(tagTypeStr)) {
            if (!config.containsKey("opcItemPath")) {
                result.addError("opcItemPath", "OPC tag requires opcItemPath to be specified");
            }
        }

        if ("QueryTag".equals(tagTypeStr)) {
            if (!config.containsKey("queryType") && !config.containsKey("query")) {
                result.addError("query", "Query tag requires query configuration");
            }
        }
    }

    private void validateDataType(Map<String, Object> config, ValidationResult.Builder result) {
        Object dataType = config.get("dataType");

        if (dataType == null) {
            // Many tag types can infer data type
            return;
        }

        String dataTypeStr = dataType.toString();

        if (!VALID_DATA_TYPES.contains(dataTypeStr)) {
            result.addError("dataType",
                    "Invalid data type: " + dataTypeStr + ". Valid types: " + VALID_DATA_TYPES);
        }
    }

    private void validateOpcSettings(Map<String, Object> config, ValidationResult.Builder result) {
        Object opcItemPath = config.get("opcItemPath");

        if (opcItemPath != null) {
            String pathStr = opcItemPath.toString();

            if (pathStr.isEmpty()) {
                result.addError("opcItemPath", "OPC item path cannot be empty");
            }

            // Basic OPC path format validation
            if (!pathStr.contains("[") || !pathStr.contains("]")) {
                result.addWarning("opcItemPath",
                        "OPC item path may be malformed. Expected format: [ConnectionName]path/to/item");
            }
        }

        Object opcReadType = config.get("opcReadType");
        if (opcReadType != null && !VALID_OPC_TYPES.contains(opcReadType.toString())) {
            result.addWarning("opcReadType",
                    "Unknown OPC read type: " + opcReadType + ". Expected: " + VALID_OPC_TYPES);
        }
    }

    private void validateExpression(Map<String, Object> config, ValidationResult.Builder result) {
        Object expression = config.get("expression");

        if (expression == null) {
            return;
        }

        String exprStr = expression.toString();

        if (exprStr.isEmpty()) {
            result.addError("expression", "Expression cannot be empty");
            return;
        }

        // Basic expression syntax checks
        int openBraces = 0;
        int openBrackets = 0;
        int openParens = 0;

        for (char c : exprStr.toCharArray()) {
            switch (c) {
                case '{':
                    openBraces++;
                    break;
                case '}':
                    openBraces--;
                    break;
                case '[':
                    openBrackets++;
                    break;
                case ']':
                    openBrackets--;
                    break;
                case '(':
                    openParens++;
                    break;
                case ')':
                    openParens--;
                    break;
                default:
                    break;
            }
        }

        if (openBraces != 0) {
            result.addWarning("expression", "Expression may have unbalanced curly braces {}");
        }
        if (openBrackets != 0) {
            result.addWarning("expression", "Expression may have unbalanced square brackets []");
        }
        if (openParens != 0) {
            result.addWarning("expression", "Expression may have unbalanced parentheses ()");
        }

        // Check for runScript (often an anti-pattern)
        if (exprStr.toLowerCase().contains("runscript")) {
            result.addWarning("expression",
                    "Expression uses runScript(). Consider using expression functions instead for better performance.");
        }
    }

    private void validateEngineering(Map<String, Object> config, ValidationResult.Builder result) {
        Object engUnit = config.get("engUnit");
        Object engLow = config.get("engLow");
        Object engHigh = config.get("engHigh");
        Object engLimitMode = config.get("engLimitMode");

        // If engineering range is specified, validate consistency
        if (engLow != null && engHigh != null) {
            try {
                double low = Double.parseDouble(engLow.toString());
                double high = Double.parseDouble(engHigh.toString());

                if (low >= high) {
                    result.addError("engRange",
                            "Engineering low (" + low + ") must be less than high (" + high + ")");
                }
            } catch (NumberFormatException e) {
                result.addError("engRange", "Engineering limits must be numeric values");
            }
        }
    }

    private void validateAlarmSettings(Map<String, Object> config, ValidationResult.Builder result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> alarms = (Map<String, Object>) config.get("alarms");

        if (alarms == null) {
            return;
        }

        // Basic alarm configuration validation
        for (Map.Entry<String, Object> entry : alarms.entrySet()) {
            String alarmName = entry.getKey();

            if (!(entry.getValue() instanceof Map)) {
                result.addError("alarms." + alarmName, "Alarm configuration must be an object");
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> alarmConfig = (Map<String, Object>) entry.getValue();

            // Check for required setpoint
            if (!alarmConfig.containsKey("setpointA") && !alarmConfig.containsKey("setpoint")) {
                result.addWarning("alarms." + alarmName,
                        "Alarm should have a setpoint defined");
            }
        }
    }
}

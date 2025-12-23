package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TagConfigValidator.
 */
class TagConfigValidatorTest {

    private TagConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TagConfigValidator();
    }

    @Nested
    @DisplayName("Tag Name Validation")
    class TagNameValidation {

        @Test
        @DisplayName("should accept valid tag names")
        void shouldAcceptValidTagNames() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "MyValidTag");
            config.put("tagType", "AtomicTag");

            ValidationResult result = validator.validate(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should reject tag names with path separators")
        void shouldRejectPathSeparators() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "Folder/Tag");

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getMessage().contains("path separators")));
        }

        @Test
        @DisplayName("should reject empty tag names")
        void shouldRejectEmptyNames() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "");

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("should warn about special characters in names")
        void shouldWarnAboutSpecialCharacters() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "Tag-With-Dashes");
            config.put("tagType", "AtomicTag");

            ValidationResult result = validator.validate(config);

            // Valid but with warnings
            assertTrue(result.isValid());
            assertFalse(result.getWarnings().isEmpty());
        }
    }

    @Nested
    @DisplayName("Tag Type Validation")
    class TagTypeValidation {

        @Test
        @DisplayName("should accept valid tag types with required fields")
        void shouldAcceptValidTagTypes() {
            // Test basic tag types that don't require additional fields
            String[] basicTypes = {"AtomicTag", "UdtDefinition", "Folder", "DerivedTag", "ReferenceTag"};
            for (String type : basicTypes) {
                Map<String, Object> config = new HashMap<>();
                config.put("name", "TestTag");
                config.put("tagType", type);

                ValidationResult result = validator.validate(config);
                assertTrue(result.isValid(), "Failed for basic type: " + type);
            }

            // UdtInstance requires typeId
            Map<String, Object> udtConfig = new HashMap<>();
            udtConfig.put("name", "TestUdt");
            udtConfig.put("tagType", "UdtInstance");
            udtConfig.put("typeId", "MyUDT");
            assertTrue(validator.validate(udtConfig).isValid(), "Failed for UdtInstance with typeId");

            // OpcTag requires opcItemPath
            Map<String, Object> opcConfig = new HashMap<>();
            opcConfig.put("name", "TestOpc");
            opcConfig.put("tagType", "OpcTag");
            opcConfig.put("opcItemPath", "[Server]path/to/item");
            assertTrue(validator.validate(opcConfig).isValid(), "Failed for OpcTag with opcItemPath");

            // ExpressionTag requires expression
            Map<String, Object> exprConfig = new HashMap<>();
            exprConfig.put("name", "TestExpr");
            exprConfig.put("tagType", "ExpressionTag");
            exprConfig.put("expression", "{[.]ParentTag} + 1");
            assertTrue(validator.validate(exprConfig).isValid(), "Failed for ExpressionTag with expression");

            // QueryTag requires query config
            Map<String, Object> queryConfig = new HashMap<>();
            queryConfig.put("name", "TestQuery");
            queryConfig.put("tagType", "QueryTag");
            queryConfig.put("queryType", "simple");
            assertTrue(validator.validate(queryConfig).isValid(), "Failed for QueryTag with queryType");
        }

        @Test
        @DisplayName("should reject invalid tag types")
        void shouldRejectInvalidTagTypes() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestTag");
            config.put("tagType", "InvalidType");

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getMessage().contains("Invalid tag type")));
        }

        @Test
        @DisplayName("should require typeId for UdtInstance")
        void shouldRequireTypeIdForUdtInstance() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestUdt");
            config.put("tagType", "UdtInstance");
            // Missing typeId

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getField().equals("typeId")));
        }

        @Test
        @DisplayName("should require expression for ExpressionTag")
        void shouldRequireExpressionForExpressionTag() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestExpr");
            config.put("tagType", "ExpressionTag");
            // Missing expression

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getField().equals("expression")));
        }

        @Test
        @DisplayName("should require opcItemPath for OpcTag")
        void shouldRequireOpcItemPathForOpcTag() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestOpc");
            config.put("tagType", "OpcTag");
            // Missing opcItemPath

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getField().equals("opcItemPath")));
        }
    }

    @Nested
    @DisplayName("Data Type Validation")
    class DataTypeValidation {

        @Test
        @DisplayName("should accept valid data types")
        void shouldAcceptValidDataTypes() {
            String[] validTypes = {"Int1", "Int2", "Int4", "Int8",
                    "Float4", "Float8", "Boolean", "String", "DateTime"};

            for (String type : validTypes) {
                Map<String, Object> config = new HashMap<>();
                config.put("name", "TestTag");
                config.put("dataType", type);

                ValidationResult result = validator.validate(config);

                assertTrue(result.isValid(), "Failed for data type: " + type);
            }
        }

        @Test
        @DisplayName("should reject invalid data types")
        void shouldRejectInvalidDataTypes() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestTag");
            config.put("dataType", "InvalidDataType");

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getMessage().contains("Invalid data type")));
        }
    }

    @Nested
    @DisplayName("OPC Settings Validation")
    class OpcSettingsValidation {

        @Test
        @DisplayName("should accept valid OPC item path")
        void shouldAcceptValidOpcPath() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestOpc");
            config.put("tagType", "OpcTag");
            config.put("opcItemPath", "[Ignition OPC UA Server]ns=1;s=Channel.Device.Tag");

            ValidationResult result = validator.validate(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should warn about malformed OPC paths")
        void shouldWarnAboutMalformedOpcPaths() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestOpc");
            config.put("tagType", "OpcTag");
            config.put("opcItemPath", "MissingBrackets/path");

            ValidationResult result = validator.validate(config);

            // Still valid but with warning
            assertTrue(result.isValid());
            assertFalse(result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("should reject empty OPC item path")
        void shouldRejectEmptyOpcPath() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestOpc");
            config.put("tagType", "OpcTag");
            config.put("opcItemPath", "");

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
        }
    }

    @Nested
    @DisplayName("Expression Validation")
    class ExpressionValidation {

        @Test
        @DisplayName("should accept valid expressions")
        void shouldAcceptValidExpressions() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestExpr");
            config.put("tagType", "ExpressionTag");
            config.put("expression", "{[.]ParentTag} * 2 + 10");

            ValidationResult result = validator.validate(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should warn about unbalanced braces")
        void shouldWarnAboutUnbalancedBraces() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestExpr");
            config.put("tagType", "ExpressionTag");
            config.put("expression", "{[.]ParentTag * 2");

            ValidationResult result = validator.validate(config);

            // Valid but with warning about unbalanced
            assertTrue(result.isValid());
            assertTrue(result.getWarnings().stream()
                    .anyMatch(w -> w.contains("unbalanced")));
        }

        @Test
        @DisplayName("should warn about runScript usage")
        void shouldWarnAboutRunScript() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestExpr");
            config.put("tagType", "ExpressionTag");
            config.put("expression", "runScript('project.utils.calculate()')");

            ValidationResult result = validator.validate(config);

            // Valid but with warning
            assertTrue(result.isValid());
            assertTrue(result.getWarnings().stream()
                    .anyMatch(w -> w.toLowerCase().contains("runscript")));
        }
    }

    @Nested
    @DisplayName("Engineering Range Validation")
    class EngineeringRangeValidation {

        @Test
        @DisplayName("should accept valid engineering range")
        void shouldAcceptValidRange() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestTag");
            config.put("engLow", 0.0);
            config.put("engHigh", 100.0);

            ValidationResult result = validator.validate(config);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("should reject inverted engineering range")
        void shouldRejectInvertedRange() {
            Map<String, Object> config = new HashMap<>();
            config.put("name", "TestTag");
            config.put("engLow", 100.0);
            config.put("engHigh", 0.0);

            ValidationResult result = validator.validate(config);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> e.getField().equals("engRange")));
        }
    }
}

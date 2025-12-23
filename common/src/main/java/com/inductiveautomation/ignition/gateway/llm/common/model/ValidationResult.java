package com.inductiveautomation.ignition.gateway.llm.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating an action request.
 * Contains validation errors, warnings, and informational messages.
 */
public final class ValidationResult {

    private final boolean valid;
    private final List<ValidationError> errors;
    private final List<String> warnings;
    private final List<String> infos;

    private ValidationResult(boolean valid, List<ValidationError> errors,
                             List<String> warnings, List<String> infos) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors);
        this.warnings = Collections.unmodifiableList(warnings);
        this.infos = infos != null ? Collections.unmodifiableList(infos) : Collections.emptyList();
    }

    // Legacy constructor for backward compatibility
    private ValidationResult(boolean valid, List<ValidationError> errors, List<String> warnings) {
        this(valid, errors, warnings, Collections.emptyList());
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getInfos() {
        return infos;
    }

    /**
     * Returns true if there are any informational messages.
     */
    public boolean hasInfos() {
        return !infos.isEmpty();
    }

    /**
     * Creates a valid result with no errors.
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Creates a valid result with warnings.
     */
    public static ValidationResult validWithWarnings(List<String> warnings) {
        return new ValidationResult(true, Collections.emptyList(), warnings);
    }

    /**
     * Creates an invalid result with errors.
     */
    public static ValidationResult invalid(List<ValidationError> errors) {
        return new ValidationResult(false, errors, Collections.emptyList());
    }

    /**
     * Builder for constructing validation results.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ValidationError> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();

        public Builder addError(String field, String message) {
            errors.add(new ValidationError(field, message));
            return this;
        }

        public Builder addError(String field, String message, String code) {
            errors.add(new ValidationError(field, message, code));
            return this;
        }

        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }

        public Builder addWarning(String field, String message) {
            warnings.add(field + ": " + message);
            return this;
        }

        public Builder addInfo(String info) {
            infos.add(info);
            return this;
        }

        public Builder addInfo(String field, String message) {
            infos.add(field + ": " + message);
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(errors.isEmpty(), errors, warnings, infos);
        }
    }

    /**
     * Represents a single validation error.
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        private final String code;

        public ValidationError(String field, String message) {
            this(field, message, null);
        }

        public ValidationError(String field, String message, String code) {
            this.field = field;
            this.message = message;
            this.code = code;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            if (code != null) {
                return String.format("[%s] %s: %s", code, field, message);
            }
            return String.format("%s: %s", field, message);
        }
    }
}

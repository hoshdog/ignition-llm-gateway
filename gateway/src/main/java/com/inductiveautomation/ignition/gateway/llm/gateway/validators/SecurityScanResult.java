package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a security scan on script or query content.
 * Contains blocked patterns (security violations) and warnings (risky but allowed patterns).
 */
public final class SecurityScanResult {

    private final List<BlockedPattern> blockedPatterns;
    private final List<Warning> warnings;

    public SecurityScanResult() {
        this.blockedPatterns = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Adds a blocked pattern that was found.
     */
    public void addBlockedPattern(String pattern, String matchedText, int lineNumber) {
        blockedPatterns.add(new BlockedPattern(pattern, matchedText, lineNumber));
    }

    /**
     * Adds a warning for a risky pattern.
     */
    public void addWarning(String message, String matchedText, int lineNumber) {
        warnings.add(new Warning(message, matchedText, lineNumber));
    }

    /**
     * Returns true if any blocked patterns were found.
     */
    public boolean hasBlockedPatterns() {
        return !blockedPatterns.isEmpty();
    }

    /**
     * Returns true if any warnings were generated.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<BlockedPattern> getBlockedPatterns() {
        return Collections.unmodifiableList(blockedPatterns);
    }

    public List<Warning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Returns a summary of blocked patterns for error messages.
     */
    public String getBlockedPatternsSummary() {
        if (blockedPatterns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blockedPatterns.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            BlockedPattern bp = blockedPatterns.get(i);
            sb.append("'").append(bp.getMatchedText()).append("' at line ").append(bp.getLineNumber());
        }
        return sb.toString();
    }

    /**
     * Returns a summary of warnings for messages.
     */
    public List<String> getWarningSummaries() {
        List<String> summaries = new ArrayList<>();
        for (Warning w : warnings) {
            summaries.add(w.getMessage() + " at line " + w.getLineNumber() +
                    " (found: '" + w.getMatchedText() + "')");
        }
        return summaries;
    }

    /**
     * Represents a blocked security pattern.
     */
    public static final class BlockedPattern {
        private final String pattern;
        private final String matchedText;
        private final int lineNumber;

        public BlockedPattern(String pattern, String matchedText, int lineNumber) {
            this.pattern = pattern;
            this.matchedText = matchedText;
            this.lineNumber = lineNumber;
        }

        public String getPattern() {
            return pattern;
        }

        public String getMatchedText() {
            return matchedText;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public String toString() {
            return "BlockedPattern{" +
                    "pattern='" + pattern + '\'' +
                    ", matchedText='" + matchedText + '\'' +
                    ", lineNumber=" + lineNumber +
                    '}';
        }
    }

    /**
     * Represents a security warning.
     */
    public static final class Warning {
        private final String message;
        private final String matchedText;
        private final int lineNumber;

        public Warning(String message, String matchedText, int lineNumber) {
            this.message = message;
            this.matchedText = matchedText;
            this.lineNumber = lineNumber;
        }

        public String getMessage() {
            return message;
        }

        public String getMatchedText() {
            return matchedText;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public String toString() {
            return "Warning{" +
                    "message='" + message + '\'' +
                    ", matchedText='" + matchedText + '\'' +
                    ", lineNumber=" + lineNumber +
                    '}';
        }
    }
}

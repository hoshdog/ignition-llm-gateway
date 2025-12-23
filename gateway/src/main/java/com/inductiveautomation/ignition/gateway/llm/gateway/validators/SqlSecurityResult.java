package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a security scan on SQL query content.
 * Contains blocked patterns (DDL/dangerous SQL) and warnings (risky patterns).
 */
public final class SqlSecurityResult {

    private final List<BlockedPattern> blockedPatterns;
    private final List<String> warnings;

    public SqlSecurityResult() {
        this.blockedPatterns = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Adds a blocked SQL pattern that was found.
     */
    public void addBlockedPattern(String pattern, String matchedText) {
        blockedPatterns.add(new BlockedPattern(pattern, matchedText));
    }

    /**
     * Adds a warning for a risky SQL pattern.
     */
    public void addWarning(String message) {
        warnings.add(message);
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

    public List<String> getWarnings() {
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
            sb.append(bp.getDescription()).append(" (found: '").append(bp.getMatchedText()).append("')");
        }
        return sb.toString();
    }

    /**
     * Represents a blocked SQL pattern.
     */
    public static final class BlockedPattern {
        private final String description;
        private final String matchedText;

        public BlockedPattern(String description, String matchedText) {
            this.description = description;
            this.matchedText = matchedText;
        }

        public String getDescription() {
            return description;
        }

        public String getMatchedText() {
            return matchedText;
        }

        @Override
        public String toString() {
            return "BlockedPattern{" +
                    "description='" + description + '\'' +
                    ", matchedText='" + matchedText + '\'' +
                    '}';
        }
    }
}

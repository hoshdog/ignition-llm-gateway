package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Named Query configurations and SQL content.
 * Performs structural validation and SQL security scanning.
 */
public class NamedQueryValidator {

    // Valid query types in Ignition
    private static final Set<String> VALID_QUERY_TYPES = new HashSet<>(Arrays.asList(
            "Query", "Update", "Insert", "Delete", "Scalar"
    ));

    // Blocked SQL patterns - these are dangerous operations
    private static final List<BlockedSqlPattern> BLOCKED_SQL_PATTERNS = new ArrayList<>();

    static {
        // DDL operations - should not be done via LLM
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bDROP\\s+(TABLE|DATABASE|INDEX|VIEW|SCHEMA|PROCEDURE|FUNCTION)\\b"),
                "DROP statement - DDL operations not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bTRUNCATE\\s+TABLE\\b"),
                "TRUNCATE TABLE - dangerous bulk delete"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bALTER\\s+(TABLE|DATABASE|SCHEMA)\\b"),
                "ALTER statement - schema modification not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bCREATE\\s+(TABLE|DATABASE|INDEX|SCHEMA|PROCEDURE|FUNCTION)\\b"),
                "CREATE statement - DDL operations not allowed"));

        // System/admin operations
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bGRANT\\b"),
                "GRANT - permission management not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bREVOKE\\b"),
                "REVOKE - permission management not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bEXEC(UTE)?\\s*\\("),
                "EXECUTE - dynamic SQL execution not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bxp_cmdshell\\b"),
                "xp_cmdshell - system command execution not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bsp_configure\\b"),
                "sp_configure - server configuration not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bSHUTDOWN\\b"),
                "SHUTDOWN - server shutdown not allowed"));

        // Dangerous patterns
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i);\\s*--.*$", Pattern.MULTILINE),
                "SQL injection pattern (semicolon followed by comment)"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bUNION\\s+SELECT\\b.*\\bFROM\\s+information_schema\\b"),
                "Information schema enumeration via UNION"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bsleep\\s*\\("),
                "SLEEP - time-based SQL injection pattern"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bWAITFOR\\s+DELAY\\b"),
                "WAITFOR DELAY - time-based SQL injection pattern"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bBENCHMARK\\s*\\("),
                "BENCHMARK - time-based SQL injection pattern"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bLOAD_FILE\\s*\\("),
                "LOAD_FILE - file system access not allowed"));
        BLOCKED_SQL_PATTERNS.add(new BlockedSqlPattern(
                Pattern.compile("(?i)\\bINTO\\s+OUTFILE\\b"),
                "INTO OUTFILE - file system write not allowed"));
    }

    /**
     * Validates a named query configuration.
     */
    public ValidationResult validate(Map<String, Object> config) {
        ValidationResult.Builder result = ValidationResult.builder();

        if (config == null) {
            result.addError("config", "Named query configuration cannot be null");
            return result.build();
        }

        // Validate query type
        if (!config.containsKey("queryType")) {
            result.addError("queryType", "Query type is required");
        } else {
            String queryType = String.valueOf(config.get("queryType"));
            if (!VALID_QUERY_TYPES.contains(queryType)) {
                result.addError("queryType", "Invalid query type: " + queryType +
                        ". Valid types: " + VALID_QUERY_TYPES);
            }
        }

        // Validate database connection
        if (!config.containsKey("database")) {
            result.addError("database", "Database connection name is required");
        } else {
            String database = String.valueOf(config.get("database"));
            if (database.trim().isEmpty()) {
                result.addError("database", "Database connection name cannot be empty");
            }
        }

        // Validate query SQL
        if (!config.containsKey("query")) {
            result.addError("query", "SQL query is required");
        } else {
            String sql = String.valueOf(config.get("query"));
            if (sql.trim().isEmpty()) {
                result.addError("query", "SQL query cannot be empty");
            } else {
                validateSqlSyntax(sql, result);
                validateParameterSyntax(sql, config, result);
            }
        }

        // Validate parameters if present
        if (config.containsKey("parameters")) {
            validateParameters(config.get("parameters"), result);
        }

        // Validate optional fields
        if (config.containsKey("cacheExpiry")) {
            Object cacheExpiry = config.get("cacheExpiry");
            if (cacheExpiry instanceof Number) {
                int expiry = ((Number) cacheExpiry).intValue();
                if (expiry < 0) {
                    result.addError("cacheExpiry", "Cache expiry cannot be negative");
                }
            }
        }

        return result.build();
    }

    /**
     * Validates updates to an existing named query.
     */
    public ValidationResult validateUpdate(Map<String, Object> payload) {
        ValidationResult.Builder result = ValidationResult.builder();

        if (payload == null || payload.isEmpty()) {
            result.addError("payload", "Update payload cannot be empty");
            return result.build();
        }

        // If query type is being updated, validate it
        if (payload.containsKey("queryType")) {
            String queryType = String.valueOf(payload.get("queryType"));
            if (!VALID_QUERY_TYPES.contains(queryType)) {
                result.addError("queryType", "Invalid query type: " + queryType);
            }
        }

        // If query SQL is being updated, validate it
        if (payload.containsKey("query")) {
            String sql = String.valueOf(payload.get("query"));
            if (sql.trim().isEmpty()) {
                result.addError("query", "SQL query cannot be empty");
            } else {
                validateSqlSyntax(sql, result);
            }
        }

        return result.build();
    }

    /**
     * Performs security scanning on SQL query content.
     */
    public SqlSecurityResult scanSql(String sql) {
        SqlSecurityResult result = new SqlSecurityResult();

        if (sql == null || sql.trim().isEmpty()) {
            return result;
        }

        // Check blocked patterns
        for (BlockedSqlPattern bsp : BLOCKED_SQL_PATTERNS) {
            Matcher matcher = bsp.pattern.matcher(sql);
            if (matcher.find()) {
                result.addBlockedPattern(bsp.description, matcher.group());
            }
        }

        // Check for string concatenation (SQL injection risk)
        if (sql.contains("' + ") || sql.contains("\" + ") ||
                sql.contains("' || ") || sql.contains("\" || ") ||
                sql.contains("'+") || sql.contains("\"+")) {
            result.addWarning("Possible SQL injection: string concatenation detected. " +
                    "Use parameterized queries with :paramName instead.");
        }

        // Check for dynamic table/column names (risky)
        if (Pattern.compile("(?i)FROM\\s*\\+").matcher(sql).find() ||
                Pattern.compile("(?i)TABLE\\s*\\+").matcher(sql).find()) {
            result.addWarning("Dynamic table name detected - this may be a security risk");
        }

        // Check for DELETE without WHERE
        if (Pattern.compile("(?i)\\bDELETE\\s+FROM\\s+\\w+\\s*(;|$)").matcher(sql).find()) {
            result.addWarning("DELETE without WHERE clause will delete all rows");
        }

        // Check for UPDATE without WHERE
        if (Pattern.compile("(?i)\\bUPDATE\\s+\\w+\\s+SET\\s+[^W]*(;|$)").matcher(sql).find()) {
            if (!Pattern.compile("(?i)\\bWHERE\\b").matcher(sql).find()) {
                result.addWarning("UPDATE without WHERE clause will update all rows");
            }
        }

        return result;
    }

    /**
     * Validates basic SQL syntax.
     */
    private void validateSqlSyntax(String sql, ValidationResult.Builder result) {
        // Check for balanced quotes
        int singleQuotes = 0;
        int doubleQuotes = 0;
        boolean escaped = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' || (c == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'')) {
                escaped = true;
                continue;
            }

            if (c == '\'') {
                singleQuotes++;
            } else if (c == '"') {
                doubleQuotes++;
            }
        }

        if (singleQuotes % 2 != 0) {
            result.addWarning("query", "Unbalanced single quotes in SQL");
        }

        // Check for balanced parentheses
        int parens = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') parens++;
            else if (c == ')') parens--;
        }

        if (parens != 0) {
            result.addWarning("query", "Unbalanced parentheses in SQL");
        }
    }

    /**
     * Validates parameter references in SQL match declared parameters.
     */
    @SuppressWarnings("unchecked")
    private void validateParameterSyntax(String sql, Map<String, Object> config,
                                          ValidationResult.Builder result) {
        // Extract parameter placeholders from SQL (:paramName or {paramName})
        Set<String> sqlParams = extractParameters(sql);

        // Check against declared parameters
        if (config.containsKey("parameters")) {
            Object paramsObj = config.get("parameters");

            if (paramsObj instanceof List) {
                List<Map<String, Object>> declaredParams = (List<Map<String, Object>>) paramsObj;
                Set<String> declaredNames = new HashSet<>();

                for (Map<String, Object> param : declaredParams) {
                    String name = (String) param.get("name");
                    if (name != null) {
                        declaredNames.add(name);
                    }
                }

                // Check for undeclared parameters in SQL
                for (String sqlParam : sqlParams) {
                    if (!declaredNames.contains(sqlParam)) {
                        result.addWarning("parameters",
                                "Parameter '" + sqlParam + "' used in SQL but not declared");
                    }
                }

                // Check for unused declared parameters
                for (String declaredParam : declaredNames) {
                    if (!sqlParams.contains(declaredParam)) {
                        result.addInfo("parameters",
                                "Parameter '" + declaredParam + "' declared but not used in SQL");
                    }
                }
            }
        } else if (!sqlParams.isEmpty()) {
            result.addWarning("parameters",
                    "SQL uses parameters " + sqlParams + " but no parameters are declared");
        }
    }

    /**
     * Validates parameter definitions.
     */
    @SuppressWarnings("unchecked")
    private void validateParameters(Object params, ValidationResult.Builder result) {
        if (!(params instanceof List)) {
            result.addError("parameters", "Parameters must be a list");
            return;
        }

        List<Object> paramList = (List<Object>) params;
        Set<String> seenNames = new HashSet<>();

        for (int i = 0; i < paramList.size(); i++) {
            Object param = paramList.get(i);

            if (!(param instanceof Map)) {
                result.addError("parameters[" + i + "]", "Parameter must be an object");
                continue;
            }

            Map<String, Object> paramMap = (Map<String, Object>) param;

            // Check required 'name' field
            if (!paramMap.containsKey("name")) {
                result.addError("parameters[" + i + "].name", "Parameter name is required");
            } else {
                String name = String.valueOf(paramMap.get("name"));
                if (name.trim().isEmpty()) {
                    result.addError("parameters[" + i + "].name", "Parameter name cannot be empty");
                } else if (seenNames.contains(name)) {
                    result.addError("parameters[" + i + "].name", "Duplicate parameter name: " + name);
                } else {
                    seenNames.add(name);
                }

                // Validate name format
                if (!Pattern.matches("^[a-zA-Z][a-zA-Z0-9_]*$", name)) {
                    result.addWarning("parameters[" + i + "].name",
                            "Parameter name should start with a letter and contain only alphanumeric characters");
                }
            }

            // Validate type if present
            if (paramMap.containsKey("type")) {
                String type = String.valueOf(paramMap.get("type"));
                Set<String> validTypes = new HashSet<>(Arrays.asList(
                        "String", "Integer", "Long", "Double", "Float", "Boolean", "Date", "DateTime"
                ));
                if (!validTypes.contains(type)) {
                    result.addWarning("parameters[" + i + "].type",
                            "Unknown parameter type: " + type + ". Valid types: " + validTypes);
                }
            }
        }
    }

    /**
     * Extracts parameter names from SQL.
     */
    private Set<String> extractParameters(String sql) {
        Set<String> params = new HashSet<>();

        // Match :paramName (standard JDBC style)
        Matcher colonMatcher = Pattern.compile(":(\\w+)").matcher(sql);
        while (colonMatcher.find()) {
            params.add(colonMatcher.group(1));
        }

        // Match {paramName} (Ignition style)
        Matcher braceMatcher = Pattern.compile("\\{(\\w+)}").matcher(sql);
        while (braceMatcher.find()) {
            params.add(braceMatcher.group(1));
        }

        return params;
    }

    /**
     * Definition of a blocked SQL pattern.
     */
    private static class BlockedSqlPattern {
        final Pattern pattern;
        final String description;

        BlockedSqlPattern(Pattern pattern, String description) {
            this.pattern = pattern;
            this.description = description;
        }
    }
}

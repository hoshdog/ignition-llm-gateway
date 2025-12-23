package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ScriptPath;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Python/Jython script content for Ignition projects.
 * Performs both structural validation and security scanning.
 */
public class ScriptValidator {

    // Patterns that are BLOCKED - never allow these
    private static final List<BlockedPatternDef> BLOCKED_PATTERNS = new ArrayList<>();

    // Patterns that trigger WARNINGS - allowed but flagged for review
    private static final List<WarningPatternDef> WARNING_PATTERNS = new ArrayList<>();

    static {
        // System command execution - BLOCKED
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)Runtime\\.getRuntime\\(\\)\\.exec"),
                "Java Runtime.exec() - system command execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bProcessBuilder\\b"),
                "Java ProcessBuilder - system command execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bos\\.system\\s*\\("),
                "os.system() - system command execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bos\\.popen\\s*\\("),
                "os.popen() - system command execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bsubprocess\\."),
                "subprocess module - system command execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bos\\.spawn"),
                "os.spawn() - process spawning"));

        // File system access with absolute paths - BLOCKED
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bopen\\s*\\(\\s*['\"][/\\\\]"),
                "File access with absolute path"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bos\\.remove\\s*\\("),
                "os.remove() - file deletion"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bos\\.rmdir\\s*\\("),
                "os.rmdir() - directory deletion"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bshutil\\.rmtree\\s*\\("),
                "shutil.rmtree() - recursive directory deletion"));

        // Dynamic code execution - BLOCKED
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\b__import__\\s*\\("),
                "__import__() - dynamic import"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bimportlib\\."),
                "importlib - dynamic import"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\beval\\s*\\("),
                "eval() - arbitrary code execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bexec\\s*\\("),
                "exec() - arbitrary code execution"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bcompile\\s*\\("),
                "compile() - code compilation"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bexecfile\\s*\\("),
                "execfile() - file execution"));

        // Network access - BLOCKED (should use approved Ignition APIs)
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bsocket\\.socket\\s*\\("),
                "socket.socket() - raw network access"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\burllib\\.request\\.urlopen"),
                "urllib.request.urlopen() - direct HTTP requests"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\burllib2\\.urlopen"),
                "urllib2.urlopen() - direct HTTP requests"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\brequests\\.(?:get|post|put|delete|patch)\\s*\\("),
                "requests library - direct HTTP requests"));

        // Ignition security internals - BLOCKED
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bsystem\\.security\\."),
                "system.security.* - security configuration access"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bsystem\\.user\\.getUser\\b"),
                "system.user.getUser() - user credential access"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bPasswordAuthenticator\\b"),
                "PasswordAuthenticator - authentication bypass risk"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)getPassword\\s*\\(\\s*\\)"),
                "getPassword() - password access"));

        // Reflection and internal access - BLOCKED
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\bgetattr\\s*\\(.*,\\s*['\"]__"),
                "getattr() with dunder attribute - internal access"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\.__class__\\.__bases__"),
                "__class__.__bases__ - class hierarchy manipulation"));
        BLOCKED_PATTERNS.add(new BlockedPatternDef(
                Pattern.compile("(?i)\\.__subclasses__\\s*\\("),
                "__subclasses__() - class hierarchy access"));

        // WARNING patterns - allowed but flagged
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.db\\.runPrepQuery\\b"),
                "Direct database query - ensure parameterized queries are used"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.db\\.runUpdateQuery\\b"),
                "Database modification - verify query safety"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.db\\.runQuery\\b"),
                "Database query - prefer runPrepQuery for parameterized queries"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.tag\\.write\\b"),
                "Tag write operation - verify target tags"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.tag\\.writeBlocking\\b"),
                "Blocking tag write - verify target tags"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.opc\\.write\\b"),
                "OPC write operation - verify PLC safety"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.util\\.sendMessage\\b"),
                "Message handler invocation - verify message handlers"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.util\\.sendRequest\\b"),
                "Gateway message request - verify handlers"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bwhile\\s+True\\s*:"),
                "Infinite loop detected - ensure exit condition exists"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bwhile\\s+1\\s*:"),
                "Infinite loop detected - ensure exit condition exists"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\btime\\.sleep\\s*\\("),
                "Sleep in script - may block gateway threads"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bThread\\.sleep\\s*\\("),
                "Thread.sleep() - may block gateway threads"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.util\\.invokeAsynchronous\\b"),
                "Async execution - ensure proper error handling"));
        WARNING_PATTERNS.add(new WarningPatternDef(
                Pattern.compile("(?i)\\bsystem\\.perspective\\.sendMessage\\b"),
                "Perspective message - verify client impact"));
    }

    /**
     * Validates script content for syntax and structure.
     */
    public ValidationResult validate(String code, ScriptPath.ScriptType type) {
        ValidationResult.Builder builder = ValidationResult.builder();

        if (code == null || code.trim().isEmpty()) {
            builder.addError("code", "Script code cannot be empty");
            return builder.build();
        }

        // Check for basic Python syntax issues
        validateBasicSyntax(code, builder);

        // Check script type constraints
        validateScriptTypeConstraints(code, type, builder);

        // Check for common mistakes
        validateCommonPatterns(code, builder);

        return builder.build();
    }

    /**
     * Performs security scanning on script content.
     * Returns blocked patterns and warnings.
     */
    public SecurityScanResult securityScan(String code) {
        SecurityScanResult result = new SecurityScanResult();

        if (code == null) {
            return result;
        }

        // Check blocked patterns
        for (BlockedPatternDef bpd : BLOCKED_PATTERNS) {
            Matcher matcher = bpd.pattern.matcher(code);
            while (matcher.find()) {
                int lineNumber = getLineNumber(code, matcher.start());
                result.addBlockedPattern(bpd.description, matcher.group(), lineNumber);
            }
        }

        // Check warning patterns
        for (WarningPatternDef wpd : WARNING_PATTERNS) {
            Matcher matcher = wpd.pattern.matcher(code);
            while (matcher.find()) {
                int lineNumber = getLineNumber(code, matcher.start());
                result.addWarning(wpd.message, matcher.group(), lineNumber);
            }
        }

        return result;
    }

    /**
     * Validates basic Python syntax.
     */
    private void validateBasicSyntax(String code, ValidationResult.Builder result) {
        // Check for unmatched quotes
        int singleQuotes = 0;
        int doubleQuotes = 0;
        boolean inSingleString = false;
        boolean inDoubleString = false;
        boolean inTripleSingle = false;
        boolean inTripleDouble = false;
        boolean escaped = false;

        char[] chars = code.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            // Handle triple quotes
            if (i + 2 < chars.length) {
                String triple = code.substring(i, i + 3);
                if ("'''".equals(triple)) {
                    if (!inDoubleString && !inTripleDouble) {
                        inTripleSingle = !inTripleSingle;
                        i += 2;
                        continue;
                    }
                }
                if ("\"\"\"".equals(triple)) {
                    if (!inSingleString && !inTripleSingle) {
                        inTripleDouble = !inTripleDouble;
                        i += 2;
                        continue;
                    }
                }
            }

            // Skip if in triple-quoted string
            if (inTripleSingle || inTripleDouble) {
                continue;
            }

            // Handle regular quotes
            if (c == '\'' && !inDoubleString) {
                inSingleString = !inSingleString;
            } else if (c == '"' && !inSingleString) {
                inDoubleString = !inDoubleString;
            }
        }

        if (inSingleString || inDoubleString) {
            result.addWarning("syntax", "Possible unclosed string literal detected");
        }

        // Check for mismatched parentheses, brackets, braces
        int parens = 0;
        int brackets = 0;
        int braces = 0;
        inSingleString = false;
        inDoubleString = false;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            // Skip strings
            if (c == '\'' && !inDoubleString) {
                inSingleString = !inSingleString;
                continue;
            }
            if (c == '"' && !inSingleString) {
                inDoubleString = !inDoubleString;
                continue;
            }
            if (inSingleString || inDoubleString) {
                continue;
            }

            switch (c) {
                case '(':
                    parens++;
                    break;
                case ')':
                    parens--;
                    break;
                case '[':
                    brackets++;
                    break;
                case ']':
                    brackets--;
                    break;
                case '{':
                    braces++;
                    break;
                case '}':
                    braces--;
                    break;
            }
        }

        if (parens != 0) {
            result.addWarning("syntax", "Mismatched parentheses detected");
        }
        if (brackets != 0) {
            result.addWarning("syntax", "Mismatched brackets detected");
        }
        if (braces != 0) {
            result.addWarning("syntax", "Mismatched braces detected");
        }
    }

    /**
     * Validates constraints based on script type.
     */
    private void validateScriptTypeConstraints(String code, ScriptPath.ScriptType type, ValidationResult.Builder result) {
        switch (type) {
            case PROJECT_LIBRARY:
                // Library scripts should primarily define functions/classes
                if (!code.contains("def ") && !code.contains("class ")) {
                    result.addInfo("structure",
                            "Library scripts typically define functions or classes");
                }
                // Check for module-level execution that might cause issues
                String[] lines = code.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    // Skip comments, empty lines, imports, and definitions
                    if (line.isEmpty() || line.startsWith("#") ||
                            line.startsWith("import ") || line.startsWith("from ") ||
                            line.startsWith("def ") || line.startsWith("class ") ||
                            line.startsWith("@")) {
                        continue;
                    }
                    // Check if this is a top-level statement
                    if (!lines[i].startsWith(" ") && !lines[i].startsWith("\t")) {
                        if (line.contains("(") && !line.startsWith("if __name__")) {
                            result.addWarning("structure",
                                    "Line " + (i + 1) + ": Top-level function call in library script may execute on import");
                        }
                    }
                }
                break;

            case TAG_EVENT:
                // Tag event scripts have specific available variables
                result.addInfo("context",
                        "Tag event scripts have 'event' object with tagPath, previousValue, currentValue, initialChange");
                break;

            case GATEWAY_EVENT:
                // Timer scripts shouldn't have long-running operations
                if (code.contains("time.sleep") || code.contains("Thread.sleep")) {
                    result.addWarning("performance",
                            "Avoid sleep() in gateway event scripts - use proper scheduling instead");
                }
                break;

            case PERSPECTIVE:
                result.addInfo("context",
                        "Perspective scripts run in client scope - use self.session, self.page, self.view");
                break;

            case MESSAGE_HANDLER:
                if (!code.contains("payload")) {
                    result.addInfo("context",
                            "Message handlers receive 'payload' argument with message data");
                }
                break;
        }
    }

    /**
     * Checks for common coding mistakes.
     */
    private void validateCommonPatterns(String code, ValidationResult.Builder result) {
        // Check for common Python 2 vs 3 issues (Ignition uses Jython 2.7)
        if (code.contains("print(") && !code.contains("from __future__ import print_function")) {
            // This is actually fine in Jython 2.7, but note it
        }

        // Check for potential issues with global keyword
        if (code.contains("global ") && code.contains("def ")) {
            result.addInfo("style",
                    "Consider passing values as parameters instead of using 'global'");
        }

        // Check for bare except clauses
        if (Pattern.compile("except\\s*:").matcher(code).find()) {
            result.addWarning("exception",
                    "Bare 'except:' clause catches all exceptions including SystemExit - consider 'except Exception:'");
        }

        // Check for mutable default arguments
        Pattern mutableDefault = Pattern.compile("def\\s+\\w+\\s*\\([^)]*=\\s*(\\[|\\{)");
        if (mutableDefault.matcher(code).find()) {
            result.addWarning("style",
                    "Mutable default argument (list or dict) - this can cause unexpected behavior");
        }
    }

    /**
     * Gets the line number for a character index in the code.
     */
    private int getLineNumber(String code, int charIndex) {
        if (charIndex <= 0) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < charIndex && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * Definition of a blocked pattern.
     */
    private static class BlockedPatternDef {
        final Pattern pattern;
        final String description;

        BlockedPatternDef(Pattern pattern, String description) {
            this.pattern = pattern;
            this.description = description;
        }
    }

    /**
     * Definition of a warning pattern.
     */
    private static class WarningPatternDef {
        final Pattern pattern;
        final String message;

        WarningPatternDef(Pattern pattern, String message) {
            this.pattern = pattern;
            this.message = message;
        }
    }
}

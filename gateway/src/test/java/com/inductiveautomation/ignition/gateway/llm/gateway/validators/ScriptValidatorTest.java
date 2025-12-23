package com.inductiveautomation.ignition.gateway.llm.gateway.validators;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ScriptPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScriptValidator.
 */
class ScriptValidatorTest {

    private ScriptValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScriptValidator();
    }

    // ========== Basic Validation Tests ==========

    @Test
    void testValidate_nullCode() {
        ValidationResult result = validator.validate(null, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("empty")));
    }

    @Test
    void testValidate_emptyCode() {
        ValidationResult result = validator.validate("", ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("empty")));
    }

    @Test
    void testValidate_whitespaceOnlyCode() {
        ValidationResult result = validator.validate("   \n\t  ", ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertFalse(result.isValid());
    }

    @Test
    void testValidate_validSimpleFunction() {
        String code = "def add(a, b):\n    return a + b";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.isValid());
    }

    @Test
    void testValidate_validClassDefinition() {
        String code = "class MyHelper:\n    def __init__(self):\n        self.value = 0\n\n    def increment(self):\n        self.value += 1";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.isValid());
    }

    // ========== Syntax Validation Tests ==========

    @Test
    void testValidate_unmatchedParentheses() {
        String code = "def test(:\n    return 1";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("parentheses")));
    }

    @Test
    void testValidate_unmatchedBrackets() {
        String code = "data = [1, 2, 3";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("brackets")));
    }

    @Test
    void testValidate_unmatchedBraces() {
        String code = "data = {\"key\": \"value\"";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("braces")));
    }

    @Test
    void testValidate_unclosedString() {
        String code = "message = \"Hello world";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("string")));
    }

    @Test
    void testValidate_tripleQuoteString() {
        String code = "doc = \"\"\"This is a\nmultiline\ndocstring\"\"\"";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.isValid());
        // No warning about unclosed strings for valid triple quotes
    }

    // ========== Script Type Constraint Tests ==========

    @Test
    void testValidate_libraryScriptWithoutFunctionOrClass() {
        String code = "x = 5\ny = 10\nprint(x + y)";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.isValid()); // Still valid, but should have info message
    }

    @Test
    void testValidate_libraryScriptTopLevelCall() {
        String code = "import system\n\ndef helper():\n    pass\n\nsomeFunction()";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Top-level function call")));
    }

    @Test
    void testValidate_gatewayEventScriptWithSleep() {
        String code = "import time\ntime.sleep(5)";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.GATEWAY_EVENT);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("sleep")));
    }

    // ========== Common Pattern Warnings ==========

    @Test
    void testValidate_bareExceptClause() {
        String code = "try:\n    risky()\nexcept:\n    pass";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Bare 'except:'")));
    }

    @Test
    void testValidate_mutableDefaultArgument() {
        String code = "def process(items=[]):\n    items.append(1)\n    return items";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Mutable default argument")));
    }

    @Test
    void testValidate_mutableDictDefault() {
        String code = "def process(config={}):\n    return config";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Mutable default argument")));
    }

    @Test
    void testValidate_globalKeyword() {
        String code = "counter = 0\n\ndef increment():\n    global counter\n    counter += 1";
        ValidationResult result = validator.validate(code, ScriptPath.ScriptType.PROJECT_LIBRARY);
        // Should have info about using global
    }

    // ========== Security Scan Tests - BLOCKED Patterns ==========

    @Test
    void testSecurityScan_osSystem() {
        String code = "import os\nos.system('rm -rf /')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("os.system")));
    }

    @Test
    void testSecurityScan_osPopen() {
        String code = "import os\nresult = os.popen('ls').read()";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("os.popen")));
    }

    @Test
    void testSecurityScan_subprocess() {
        String code = "import subprocess\nsubprocess.call(['ls', '-la'])";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("subprocess")));
    }

    @Test
    void testSecurityScan_eval() {
        String code = "user_input = '2 + 2'\nresult = eval(user_input)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("eval")));
    }

    @Test
    void testSecurityScan_exec() {
        String code = "code = 'print(\"hello\")'\nexec(code)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("exec")));
    }

    @Test
    void testSecurityScan_compile() {
        String code = "code = compile('print(1)', '<string>', 'exec')\nexec(code)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("compile")));
    }

    @Test
    void testSecurityScan_dynamicImport() {
        String code = "mod = __import__('os')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("__import__")));
    }

    @Test
    void testSecurityScan_socket() {
        String code = "import socket\ns = socket.socket(socket.AF_INET, socket.SOCK_STREAM)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("socket")));
    }

    @Test
    void testSecurityScan_fileWithAbsolutePath() {
        String code = "f = open('/etc/passwd', 'r')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("File access")));
    }

    @Test
    void testSecurityScan_osRemove() {
        String code = "import os\nos.remove('/tmp/file.txt')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("os.remove")));
    }

    @Test
    void testSecurityScan_shutilRmtree() {
        String code = "import shutil\nshutil.rmtree('/tmp/mydir')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("shutil.rmtree")));
    }

    @Test
    void testSecurityScan_runtimeExec() {
        String code = "Runtime.getRuntime().exec('calc.exe')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("Runtime.exec")));
    }

    @Test
    void testSecurityScan_systemSecurity() {
        String code = "users = system.security.getUsers()";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("system.security")));
    }

    @Test
    void testSecurityScan_classHierarchyAccess() {
        String code = "bases = obj.__class__.__bases__";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("__class__.__bases__")));
    }

    @Test
    void testSecurityScan_subclasses() {
        String code = "subclasses = str.__subclasses__()";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertTrue(result.getBlockedPatterns().stream()
                .anyMatch(bp -> bp.getPattern().contains("__subclasses__")));
    }

    // ========== Security Scan Tests - WARNING Patterns ==========

    @Test
    void testSecurityScan_systemDbRunQuery() {
        String code = "results = system.db.runQuery('SELECT * FROM users')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Database query")));
    }

    @Test
    void testSecurityScan_systemTagWrite() {
        String code = "system.tag.write('[default]MyTag', 100)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Tag write")));
    }

    @Test
    void testSecurityScan_systemOpcWrite() {
        String code = "system.opc.write('[device]Address', 50)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("OPC write")));
    }

    @Test
    void testSecurityScan_infiniteWhileTrue() {
        String code = "while True:\n    process()";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Infinite loop")));
    }

    @Test
    void testSecurityScan_infiniteWhileOne() {
        String code = "while 1:\n    process()";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Infinite loop")));
    }

    @Test
    void testSecurityScan_timeSleep() {
        String code = "import time\ntime.sleep(10)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Sleep")));
    }

    @Test
    void testSecurityScan_invokeAsynchronous() {
        String code = "system.util.invokeAsynchronous(myFunction)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.getMessage().contains("Async execution")));
    }

    // ========== Security Scan - Safe Code ==========

    @Test
    void testSecurityScan_safeCode() {
        String code = "def calculate(x, y):\n    return x + y\n\nresult = calculate(5, 10)";
        SecurityScanResult result = validator.securityScan(code);
        assertFalse(result.hasBlockedPatterns());
        assertFalse(result.hasWarnings());
    }

    @Test
    void testSecurityScan_safeSystemTagRead() {
        String code = "value = system.tag.read('[default]MyTag').value";
        SecurityScanResult result = validator.securityScan(code);
        assertFalse(result.hasBlockedPatterns());
        // Tag read doesn't generate warnings
    }

    @Test
    void testSecurityScan_safePrintFunction() {
        // Using 'print' function shouldn't trigger 'eval' or 'exec' patterns
        String code = "print('Hello World')";
        SecurityScanResult result = validator.securityScan(code);
        assertFalse(result.hasBlockedPatterns());
    }

    // ========== Line Number Tests ==========

    @Test
    void testSecurityScan_correctLineNumber() {
        String code = "import os\n\ndef test():\n    pass\n\nos.system('ls')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        SecurityScanResult.BlockedPattern bp = result.getBlockedPatterns().get(0);
        assertEquals(6, bp.getLineNumber());
    }

    @Test
    void testSecurityScan_multipleBlockedPatterns() {
        String code = "os.system('ls')\neval('1+1')\nexec('print(1)')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
        assertEquals(3, result.getBlockedPatterns().size());
    }

    // ========== Summary/Reporting Tests ==========

    @Test
    void testSecurityScan_blockedPatternsSummary() {
        String code = "os.system('test')";
        SecurityScanResult result = validator.securityScan(code);
        String summary = result.getBlockedPatternsSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("os.system"));
        assertTrue(summary.contains("line 1"));
    }

    @Test
    void testSecurityScan_warningSummaries() {
        String code = "system.tag.write('[default]Test', 1)";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarningSummaries().size());
    }

    // ========== Null Safety Tests ==========

    @Test
    void testSecurityScan_nullCode() {
        SecurityScanResult result = validator.securityScan(null);
        assertFalse(result.hasBlockedPatterns());
        assertFalse(result.hasWarnings());
    }

    @Test
    void testSecurityScan_emptyCode() {
        SecurityScanResult result = validator.securityScan("");
        assertFalse(result.hasBlockedPatterns());
        assertFalse(result.hasWarnings());
    }

    // ========== Edge Cases ==========

    @Test
    void testSecurityScan_patternInComment() {
        // Patterns in comments might still be detected (that's okay for security)
        String code = "# os.system('test') - this is a comment";
        SecurityScanResult result = validator.securityScan(code);
        // We detect patterns even in comments for thoroughness
        assertTrue(result.hasBlockedPatterns());
    }

    @Test
    void testSecurityScan_patternInString() {
        // Patterns mentioned in strings (not executed) should not be blocked
        // This is good behavior - documentation or logging shouldn't trigger blocks
        String code = "help_text = \"Use os.system for commands\"";
        SecurityScanResult result = validator.securityScan(code);
        // The validator is sophisticated enough to not block patterns inside strings
        assertFalse(result.hasBlockedPatterns());
    }

    @Test
    void testSecurityScan_caseInsensitive() {
        String code = "EVAL('1+1')";
        SecurityScanResult result = validator.securityScan(code);
        assertTrue(result.hasBlockedPatterns());
    }
}

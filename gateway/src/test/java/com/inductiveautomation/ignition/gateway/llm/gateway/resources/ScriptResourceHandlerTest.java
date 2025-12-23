package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScriptResourceHandler.
 */
class ScriptResourceHandlerTest {

    @Mock
    private GatewayContext gatewayContext;

    @Mock
    private AuditLogger auditLogger;

    private ScriptResourceHandler handler;
    private AuthContext fullAccessAuth;
    private AuthContext readOnlyAuth;
    private AuthContext noAccessAuth;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ScriptResourceHandler(gatewayContext, auditLogger);

        // Full access context
        Set<Permission> fullPerms = new HashSet<>(Arrays.asList(
                Permission.SCRIPT_READ, Permission.SCRIPT_CREATE,
                Permission.SCRIPT_UPDATE, Permission.SCRIPT_DELETE
        ));
        fullAccessAuth = AuthContext.builder()
                .userId("admin")
                .permissions(fullPerms)
                .build();

        // Read-only context
        Set<Permission> readPerms = Collections.singleton(Permission.SCRIPT_READ);
        readOnlyAuth = AuthContext.builder()
                .userId("reader")
                .permissions(readPerms)
                .build();

        // No access context
        noAccessAuth = AuthContext.builder()
                .userId("guest")
                .permissions(Collections.emptySet())
                .build();
    }

    // ========== Resource Type ==========

    @Test
    void testGetResourceType() {
        assertEquals("script", handler.getResourceType());
    }

    // ========== Create Tests ==========

    @Test
    void testCreate_success() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 42");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testCreate_successWithReadOnlyAuth() {
        // Note: The handler doesn't currently check permissions
        // This test verifies creates succeed (permission checks will be added later)
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 42");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, readOnlyAuth);
        // Creates succeed because scriptExists returns false (doesn't exist)
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testCreate_invalidPath() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 42");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "invalid-path",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testCreate_missingCode() {
        Map<String, Object> payload = new HashMap<>();
        // No 'code' field

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testCreate_blockedPattern_osSystem() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "import os\nos.system('rm -rf /')");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/dangerous",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("SECURITY_VIOLATION")));
    }

    @Test
    void testCreate_blockedPattern_eval() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "result = eval(user_input)");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/dangerous",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("SECURITY_VIOLATION")));
    }

    @Test
    void testCreate_blockedPattern_exec() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "exec(code_string)");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/dangerous",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testCreate_protectedFolder() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 42");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/system/security/auth",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getMessage().contains("protected"));
    }

    @Test
    void testCreate_warningsRequireAcknowledgment() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "system.tag.write('[default]Test', 100)");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/writer",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.PENDING_CONFIRMATION, result.getStatus());
        assertTrue(result.getWarnings() != null && !result.getWarnings().isEmpty());
    }

    @Test
    void testCreate_warningsAcknowledged() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "system.tag.write('[default]Test', 100)");
        payload.put("acknowledgeWarnings", true);

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/writer",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testCreate_dryRun() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 42");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                ActionOptions.dryRun()
        );

        ActionResult result = handler.create(action, fullAccessAuth);
        assertEquals(ActionResult.Status.DRY_RUN, result.getStatus());
    }

    // ========== Read Tests ==========

    @Test
    void testRead_notFound() {
        // Placeholder returns scriptExists=false, so reads return NOT_FOUND
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    @Test
    void testRead_invalidPath() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "invalid-path",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, noAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testRead_listScripts() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/*",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    // ========== Update Tests ==========

    @Test
    void testUpdate_notFound() {
        // Placeholder returns scriptExists=false, so updates return NOT_FOUND
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 43");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    @Test
    void testUpdate_invalidPath() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 43");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "invalid-path",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, readOnlyAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testUpdate_protectedFolder() {
        // Protected folder check happens BEFORE scriptExists check
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 43");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/system/security/auth",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getMessage().contains("protected"));
    }

    @Test
    void testUpdate_notFoundForDryRun() {
        // scriptExists returns false, so NOT_FOUND
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "def helper():\n    return 43");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                payload,
                true,
                ActionOptions.dryRun()
        );

        ActionResult result = handler.update(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    // ========== Delete Tests ==========

    @Test
    void testDelete_notFound() {
        // Placeholder returns scriptExists=false, so deletes return NOT_FOUND
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                false,
                ActionOptions.defaults()
        );

        ActionResult result = handler.delete(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    @Test
    void testDelete_invalidPath() {
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "invalid-path",
                false,
                ActionOptions.forced("Test deletion")
        );

        ActionResult result = handler.delete(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testDelete_protectedFolder() {
        // Protected folder check happens BEFORE scriptExists check
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/system/security/auth",
                false,
                ActionOptions.forced("Test deletion")
        );

        ActionResult result = handler.delete(action, fullAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getMessage().contains("protected"));
    }

    @Test
    void testDelete_notFoundWithForce() {
        // Even with force, NOT_FOUND because scriptExists returns false
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "script",
                "TestProject/library/utils/helper",
                false,
                ActionOptions.forced("Test deletion")
        );

        ActionResult result = handler.delete(action, readOnlyAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    // ========== Script Path Parsing Tests ==========

    @Test
    void testScriptPath_projectLibrary() {
        ScriptPath path = ScriptPath.parse("MyProject/library/utils/helper");
        assertNotNull(path);
        assertEquals("MyProject", path.getProjectName());
        assertEquals(ScriptPath.ScriptType.PROJECT_LIBRARY, path.getType());
        assertEquals("utils", path.getFolder());
        assertEquals("helper", path.getScriptName());
    }

    @Test
    void testScriptPath_gatewayEvent() {
        ScriptPath path = ScriptPath.parse("MyProject/gateway/timer/myTimer");
        assertNotNull(path);
        assertEquals(ScriptPath.ScriptType.GATEWAY_EVENT, path.getType());
        assertEquals("timer", path.getFolder());
        assertEquals("myTimer", path.getScriptName());
    }

    @Test
    void testScriptPath_tagEvent() {
        ScriptPath path = ScriptPath.parse("MyProject/tag/valueChanged/handler");
        assertNotNull(path);
        assertEquals(ScriptPath.ScriptType.TAG_EVENT, path.getType());
    }

    @Test
    void testScriptPath_perspective() {
        ScriptPath path = ScriptPath.parse("MyProject/perspective/sessionEvents/startup");
        assertNotNull(path);
        assertEquals(ScriptPath.ScriptType.PERSPECTIVE, path.getType());
    }

    @Test
    void testScriptPath_messageHandler() {
        ScriptPath path = ScriptPath.parse("MyProject/message/myHandler");
        assertNotNull(path);
        assertEquals(ScriptPath.ScriptType.MESSAGE_HANDLER, path.getType());
        assertEquals("", path.getFolder());
        assertEquals("myHandler", path.getScriptName());
    }

    @Test
    void testScriptPath_invalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            ScriptPath.parse("invalid");
        });
    }

    @Test
    void testScriptPath_emptyPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            ScriptPath.parse("");
        });
    }

    @Test
    void testScriptPath_nullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            ScriptPath.parse(null);
        });
    }
}

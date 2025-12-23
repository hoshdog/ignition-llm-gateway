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
import com.inductiveautomation.ignition.gateway.system.SystemManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectResourceHandler.
 */
class ProjectResourceHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    private GatewayContext gatewayContext;

    @Mock
    private SystemManager systemManager;

    @Mock
    private AuditLogger auditLogger;

    private ProjectResourceHandler handler;
    private AuthContext adminAuth;
    private AuthContext updateAuth;
    private AuthContext readOnlyAuth;
    private AuthContext noAccessAuth;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Set up mock to return temp directory as data dir
        when(gatewayContext.getSystemManager()).thenReturn(systemManager);
        when(systemManager.getDataDir()).thenReturn(tempDir.toFile());

        // Create a test project in the temp directory
        Path projectsDir = tempDir.resolve("projects");
        Files.createDirectories(projectsDir);

        Path mainProjectDir = projectsDir.resolve("MainProject");
        Files.createDirectories(mainProjectDir);

        // Create project.json for MainProject
        String projectJson = "{\"title\":\"Main Project\",\"enabled\":true,\"inheritable\":false}";
        Files.writeString(mainProjectDir.resolve("project.json"), projectJson, StandardCharsets.UTF_8);

        handler = new ProjectResourceHandler(gatewayContext, auditLogger);

        // Admin context
        Set<Permission> adminPerms = new HashSet<>(Arrays.asList(
                Permission.ADMIN, Permission.PROJECT_UPDATE
        ));
        adminAuth = AuthContext.builder()
                .userId("admin")
                .permissions(adminPerms)
                .build();

        // Update context
        Set<Permission> updatePerms = Collections.singleton(Permission.PROJECT_UPDATE);
        updateAuth = AuthContext.builder()
                .userId("editor")
                .permissions(updatePerms)
                .build();

        // Read-only context
        readOnlyAuth = AuthContext.builder()
                .userId("reader")
                .permissions(Collections.emptySet())
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
        assertEquals("project", handler.getResourceType());
    }

    // ========== Create Tests - Project creation is disabled ==========

    @Test
    void testCreate_notImplemented() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "NewProject");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "NewProject",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, adminAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_IMPLEMENTED")));
        assertTrue(result.getMessage().contains("safety"));
    }

    @Test
    void testCreate_requiresAdmin() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "NewProject");

        CreateResourceAction action = new CreateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "NewProject",
                payload,
                ActionOptions.defaults()
        );

        ActionResult result = handler.create(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("FORBIDDEN")));
    }

    // ========== Read Tests ==========

    @Test
    void testRead_specificProject() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
        assertNotNull(result.getData());
    }

    @Test
    void testRead_projectNotFound() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "NonExistentProject",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    @Test
    void testRead_listAllProjects() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "*",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("count")); // Only MainProject exists
    }

    @Test
    void testRead_emptyPath_listsProjects() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    // ========== Update Tests ==========

    @Test
    void testUpdate_title() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Updated Project Title");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testUpdate_description() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("description", "New project description");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testUpdate_enabled() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("enabled", false);

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testUpdate_missingPermission() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Updated Title");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, noAccessAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("FORBIDDEN")));
    }

    @Test
    void testUpdate_disallowedField() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "RenamedProject");  // name is not allowed

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("FORBIDDEN_FIELDS")));
    }

    @Test
    void testUpdate_multipleDisallowedFields() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "RenamedProject");
        payload.put("parentProject", "OtherProject");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testUpdate_emptyPayload() {
        Map<String, Object> payload = new HashMap<>();

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testUpdate_nullPayload() {
        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                null,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
    }

    @Test
    void testUpdate_dryRun() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Test Title");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.dryRun()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.DRY_RUN, result.getStatus());
    }

    @Test
    void testUpdate_adminCanUpdate() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Admin Updated Title");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, adminAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testUpdate_internalFieldsAllowed() {
        // Fields starting with _ should be allowed (internal metadata)
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Updated Title");
        payload.put("_lastModified", "2024-01-01");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());
    }

    @Test
    void testUpdate_projectNotFound() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Updated Title");

        UpdateResourceAction action = new UpdateResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "NonExistentProject",
                payload,
                true,
                ActionOptions.defaults()
        );

        ActionResult result = handler.update(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    // ========== Delete Tests - Project deletion is disabled ==========

    @Test
    void testDelete_requiresAdmin() {
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                false,
                ActionOptions.defaults()
        );

        ActionResult result = handler.delete(action, updateAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("FORBIDDEN")));
    }

    @Test
    void testDelete_requiresForce() {
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                false,
                ActionOptions.defaults()
        );

        ActionResult result = handler.delete(action, adminAuth);
        assertEquals(ActionResult.Status.PENDING_CONFIRMATION, result.getStatus());
        assertTrue(result.getWarnings() != null && !result.getWarnings().isEmpty());
    }

    @Test
    void testDelete_notImplemented() {
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                false,
                ActionOptions.forced("Test deletion")
        );

        ActionResult result = handler.delete(action, adminAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_IMPLEMENTED")));
        assertTrue(result.getMessage().contains("safety"));
    }

    @Test
    void testDelete_projectNotFound() {
        DeleteResourceAction action = new DeleteResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "NonExistentProject",
                false,
                ActionOptions.defaults()
        );

        ActionResult result = handler.delete(action, adminAuth);
        assertEquals(ActionResult.Status.FAILURE, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND")));
    }

    // ========== Edge Cases ==========

    @Test
    void testRead_projectReturnsResourceCounts() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "MainProject",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data);
        assertTrue(data.containsKey("resourceCounts"));
    }

    @Test
    void testRead_listReturnsCount() {
        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "*",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data);
        assertTrue(data.containsKey("count"));
        assertTrue(data.containsKey("projects"));
    }

    @Test
    void testRead_projectMetadataFromProjectJson() throws Exception {
        // Create a project with specific metadata
        Path projectsDir = tempDir.resolve("projects");
        Path testProjectDir = projectsDir.resolve("TestProject");
        Files.createDirectories(testProjectDir);

        String projectJson = "{\"title\":\"Test Project Title\",\"description\":\"A test project\",\"enabled\":true,\"inheritable\":true,\"parent\":\"MainProject\"}";
        Files.writeString(testProjectDir.resolve("project.json"), projectJson, StandardCharsets.UTF_8);

        ReadResourceAction action = new ReadResourceAction(
                UUID.randomUUID().toString(),
                "project",
                "TestProject",
                null, false, null, ActionOptions.defaults()
        );

        ActionResult result = handler.read(action, readOnlyAuth);
        assertEquals(ActionResult.Status.SUCCESS, result.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("TestProject", data.get("name"));
        assertEquals("Test Project Title", data.get("title"));
        assertEquals("A test project", data.get("description"));
        assertEquals(true, data.get("enabled"));
        assertEquals(true, data.get("inheritable"));
        assertEquals("MainProject", data.get("parentProject"));
    }
}

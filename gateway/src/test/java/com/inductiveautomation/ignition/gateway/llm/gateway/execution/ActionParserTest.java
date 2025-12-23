package com.inductiveautomation.ignition.gateway.llm.gateway.execution;

import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActionParser.
 */
class ActionParserTest {

    private LLMToolCall createToolCall(String id, String name, String arguments) {
        return LLMToolCall.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    @Test
    void testParseCreateTag() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"tagPath\": \"[default]MyFolder/NewTag\"," +
                "\"tagType\": \"AtomicTag\"," +
                "\"dataType\": \"Int4\"," +
                "\"value\": 42" +
                "}";

        LLMToolCall toolCall = createToolCall("call_123", "create_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof CreateResourceAction);
        CreateResourceAction createAction = (CreateResourceAction) action;

        assertEquals("tag", createAction.getResourceType());
        assertEquals("create", createAction.getActionType());
        assertEquals("[default]MyFolder/NewTag", createAction.getResourcePath());
        assertEquals("AtomicTag", createAction.getPayload().get("tagType"));
        assertEquals("Int4", createAction.getPayload().get("dataType"));
        assertEquals(42, createAction.getPayload().get("value"));
    }

    @Test
    void testParseReadTag() throws ActionParser.ParseException {
        String arguments = "{\"tagPath\": \"[default]Folder/MyTag\"}";

        LLMToolCall toolCall = createToolCall("call_456", "read_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof ReadResourceAction);
        assertEquals("tag", action.getResourceType());
        assertEquals("read", action.getActionType());
        assertEquals("[default]Folder/MyTag", action.getResourcePath());
    }

    @Test
    void testParseUpdateTag() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"tagPath\": \"[default]MyTag\"," +
                "\"documentation\": \"Updated documentation\"" +
                "}";

        LLMToolCall toolCall = createToolCall("call_789", "update_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof UpdateResourceAction);
        UpdateResourceAction updateAction = (UpdateResourceAction) action;

        assertEquals("tag", updateAction.getResourceType());
        assertEquals("update", updateAction.getActionType());
        assertEquals("[default]MyTag", updateAction.getResourcePath());
        assertEquals("Updated documentation", updateAction.getPayload().get("documentation"));
    }

    @Test
    void testParseDeleteTag() throws ActionParser.ParseException {
        String arguments = "{\"tagPath\": \"[default]OldTag\", \"recursive\": true}";

        LLMToolCall toolCall = createToolCall("call_abc", "delete_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof DeleteResourceAction);
        DeleteResourceAction deleteAction = (DeleteResourceAction) action;

        assertEquals("tag", deleteAction.getResourceType());
        assertEquals("delete", deleteAction.getActionType());
        assertEquals("[default]OldTag", deleteAction.getResourcePath());
        assertTrue(deleteAction.isRecursive());
        assertTrue(deleteAction.isDestructive());
    }

    @Test
    void testParseWriteTagValue() throws ActionParser.ParseException {
        String arguments = "{\"tagPath\": \"[default]Counter\", \"value\": 100}";

        LLMToolCall toolCall = createToolCall("call_def", "write_tag_value", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof UpdateResourceAction);
        UpdateResourceAction updateAction = (UpdateResourceAction) action;

        assertEquals("tag", updateAction.getResourceType());
        assertEquals("update", updateAction.getActionType());
        assertEquals("[default]Counter", updateAction.getResourcePath());
        assertEquals(100, updateAction.getPayload().get("value"));
    }

    @Test
    void testParseListTags() throws ActionParser.ParseException {
        String arguments = "{\"parentPath\": \"Folder\", \"recursive\": true}";

        LLMToolCall toolCall = createToolCall("call_ghi", "list_tags", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof ReadResourceAction);
        ReadResourceAction readAction = (ReadResourceAction) action;

        assertEquals("tag", readAction.getResourceType());
        assertEquals("read", readAction.getActionType());
        assertTrue(readAction.getResourcePath().contains("Folder"));
        assertTrue(readAction.isIncludeChildren());
    }

    @Test
    void testParseCreateView() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"projectName\": \"MainProject\"," +
                "\"viewPath\": \"Dashboard/Overview\"," +
                "\"root\": {" +
                "  \"type\": \"ia.container.flex\"," +
                "  \"props\": {\"direction\": \"column\"}" +
                "}" +
                "}";

        LLMToolCall toolCall = createToolCall("call_jkl", "create_view", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof CreateResourceAction);
        CreateResourceAction createAction = (CreateResourceAction) action;

        assertEquals("view", createAction.getResourceType());
        assertEquals("create", createAction.getActionType());
        assertEquals("MainProject/Dashboard/Overview", createAction.getResourcePath());
        assertNotNull(createAction.getPayload().get("root"));
    }

    @Test
    void testParseReadView() throws ActionParser.ParseException {
        String arguments = "{\"projectName\": \"TestProject\", \"viewPath\": \"Views/MyView\"}";

        LLMToolCall toolCall = createToolCall("call_mno", "read_view", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof ReadResourceAction);
        assertEquals("view", action.getResourceType());
        assertEquals("read", action.getActionType());
        assertEquals("TestProject/Views/MyView", action.getResourcePath());
    }

    @Test
    void testParseUpdateView() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"projectName\": \"MainProject\"," +
                "\"viewPath\": \"Dashboard\"," +
                "\"jsonPatch\": [" +
                "  {\"op\": \"replace\", \"path\": \"/root/props/text\", \"value\": \"Updated\"}" +
                "]" +
                "}";

        LLMToolCall toolCall = createToolCall("call_pqr", "update_view", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof UpdateResourceAction);
        UpdateResourceAction updateAction = (UpdateResourceAction) action;

        assertEquals("view", updateAction.getResourceType());
        assertEquals("update", updateAction.getActionType());
        assertEquals("MainProject/Dashboard", updateAction.getResourcePath());
        assertNotNull(updateAction.getPayload().get("jsonPatch"));
    }

    @Test
    void testParseDeleteView() throws ActionParser.ParseException {
        String arguments = "{\"projectName\": \"OldProject\", \"viewPath\": \"Deprecated/View\"}";

        LLMToolCall toolCall = createToolCall("call_stu", "delete_view", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof DeleteResourceAction);
        assertEquals("view", action.getResourceType());
        assertEquals("delete", action.getActionType());
        assertEquals("OldProject/Deprecated/View", action.getResourcePath());
        assertTrue(action.isDestructive());
    }

    @Test
    void testParseListViews() throws ActionParser.ParseException {
        String arguments = "{\"projectName\": \"MainProject\", \"parentPath\": \"Dashboard\"}";

        LLMToolCall toolCall = createToolCall("call_vwx", "list_views", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof ReadResourceAction);
        ReadResourceAction readAction = (ReadResourceAction) action;

        assertEquals("view", readAction.getResourceType());
        assertTrue(readAction.isIncludeChildren());
    }

    @Test
    void testParseListProjects() throws ActionParser.ParseException {
        String arguments = "{}";

        LLMToolCall toolCall = createToolCall("call_yz", "list_projects", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof ReadResourceAction);
        assertEquals("project", action.getResourceType());
        assertEquals("read", action.getActionType());
    }

    @Test
    void testParseUnknownTool() {
        String arguments = "{}";
        LLMToolCall toolCall = createToolCall("call_unknown", "unknown_tool", arguments);

        ActionParser.ParseException exception = assertThrows(ActionParser.ParseException.class, () -> {
            ActionParser.parseToolCall(toolCall);
        });

        assertTrue(exception.getMessage().contains("Unknown tool") ||
                   exception.getMessage().contains("unknown"));
    }

    @Test
    void testParseMissingRequiredField() {
        // Missing required 'tagPath' field for create_tag
        String arguments = "{\"tagType\": \"AtomicTag\"}";
        LLMToolCall toolCall = createToolCall("call_missing", "create_tag", arguments);

        ActionParser.ParseException exception = assertThrows(ActionParser.ParseException.class, () -> {
            ActionParser.parseToolCall(toolCall);
        });

        assertTrue(exception.getMessage().contains("tagPath") ||
                   exception.getMessage().contains("required"));
    }

    @Test
    void testParseInvalidJson() {
        String arguments = "{ invalid json }";
        LLMToolCall toolCall = createToolCall("call_invalid", "read_tag", arguments);

        assertThrows(Exception.class, () -> {
            ActionParser.parseToolCall(toolCall);
        });
    }

    @Test
    void testParseTagWithAllOptions() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"tagPath\": \"[default]MyFolder/ComplexTag\"," +
                "\"tagType\": \"AtomicTag\"," +
                "\"dataType\": \"Float8\"," +
                "\"value\": 3.14159," +
                "\"documentation\": \"A test tag with all options\"," +
                "\"dryRun\": true" +
                "}";

        LLMToolCall toolCall = createToolCall("call_full", "create_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof CreateResourceAction);
        CreateResourceAction createAction = (CreateResourceAction) action;

        assertEquals("Float8", createAction.getPayload().get("dataType"));
        assertEquals("A test tag with all options", createAction.getPayload().get("documentation"));
        assertTrue(createAction.getOptions().isDryRun());
    }

    @Test
    void testParseViewWithFullConfig() throws ActionParser.ParseException {
        String arguments = "{" +
                "\"projectName\": \"TestProject\"," +
                "\"viewPath\": \"Pages/Main\"," +
                "\"root\": {" +
                "  \"type\": \"ia.container.flex\"," +
                "  \"props\": {" +
                "    \"direction\": \"column\"," +
                "    \"justify\": \"center\"" +
                "  }," +
                "  \"children\": [" +
                "    {\"type\": \"ia.display.label\", \"props\": {\"text\": \"Hello\"}}" +
                "  ]" +
                "}" +
                "}";

        LLMToolCall toolCall = createToolCall("call_config", "create_view", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertNotNull(action);
        assertTrue(action instanceof CreateResourceAction);
        CreateResourceAction createAction = (CreateResourceAction) action;

        assertEquals("view", createAction.getResourceType());
        assertNotNull(createAction.getPayload().get("root"));
    }

    @Test
    void testDestructiveActionsRequireConfirmation() throws ActionParser.ParseException {
        // Test delete_tag
        String deleteTagArgs = "{\"tagPath\": \"[default]Tag\"}";
        LLMToolCall deleteTagCall = createToolCall("call_1", "delete_tag", deleteTagArgs);
        Action deleteTag = ActionParser.parseToolCall(deleteTagCall);
        assertTrue(deleteTag.isDestructive());
        assertTrue(deleteTag.requiresConfirmation());

        // Test delete_view
        String deleteViewArgs = "{\"projectName\": \"Proj\", \"viewPath\": \"View\"}";
        LLMToolCall deleteViewCall = createToolCall("call_2", "delete_view", deleteViewArgs);
        Action deleteView = ActionParser.parseToolCall(deleteViewCall);
        assertTrue(deleteView.isDestructive());
        assertTrue(deleteView.requiresConfirmation());
    }

    @Test
    void testParseWithForceOption() throws ActionParser.ParseException {
        String arguments = "{\"tagPath\": \"[default]Tag\", \"force\": true, \"comment\": \"Forced update\"}";
        LLMToolCall toolCall = createToolCall("call_force", "delete_tag", arguments);
        Action action = ActionParser.parseToolCall(toolCall);

        assertTrue(action.getOptions().isForce());
        assertEquals("Forced update", action.getOptions().getComment());
    }
}

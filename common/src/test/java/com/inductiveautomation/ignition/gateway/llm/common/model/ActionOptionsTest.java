package com.inductiveautomation.ignition.gateway.llm.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActionOptions.
 */
class ActionOptionsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Constructor Tests ==========

    @Test
    void testConstructor_withAllValues() {
        ActionOptions options = new ActionOptions(true, true, true, "test comment");
        assertTrue(options.isDryRun());
        assertTrue(options.isForce());
        assertTrue(options.isRecursive());
        assertEquals("test comment", options.getComment());
    }

    @Test
    void testConstructor_withNullValues() {
        ActionOptions options = new ActionOptions(null, null, null, null);
        assertFalse(options.isDryRun());
        assertFalse(options.isForce());
        assertFalse(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testConstructor_withMixedValues() {
        ActionOptions options = new ActionOptions(true, false, true, "mixed");
        assertTrue(options.isDryRun());
        assertFalse(options.isForce());
        assertTrue(options.isRecursive());
        assertEquals("mixed", options.getComment());
    }

    // ========== Static Factory Tests ==========

    @Test
    void testDefaults() {
        ActionOptions options = ActionOptions.defaults();
        assertFalse(options.isDryRun());
        assertFalse(options.isForce());
        assertFalse(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testDryRun() {
        ActionOptions options = ActionOptions.dryRun();
        assertTrue(options.isDryRun());
        assertFalse(options.isForce());
        assertFalse(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testForced() {
        ActionOptions options = ActionOptions.forced("force reason");
        assertFalse(options.isDryRun());
        assertTrue(options.isForce());
        assertFalse(options.isRecursive());
        assertEquals("force reason", options.getComment());
    }

    @Test
    void testRecursive() {
        ActionOptions options = ActionOptions.recursive();
        assertFalse(options.isDryRun());
        assertFalse(options.isForce());
        assertTrue(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testForcedRecursive() {
        ActionOptions options = ActionOptions.forcedRecursive("deleting folder");
        assertFalse(options.isDryRun());
        assertTrue(options.isForce());
        assertTrue(options.isRecursive());
        assertEquals("deleting folder", options.getComment());
    }

    // ========== JSON Deserialization Tests ==========

    @Test
    void testJsonDeserialization_allFields() throws Exception {
        String json = "{\"dryRun\":true,\"force\":true,\"recursive\":true,\"comment\":\"test\"}";
        ActionOptions options = objectMapper.readValue(json, ActionOptions.class);

        assertTrue(options.isDryRun());
        assertTrue(options.isForce());
        assertTrue(options.isRecursive());
        assertEquals("test", options.getComment());
    }

    @Test
    void testJsonDeserialization_minimalFields() throws Exception {
        String json = "{}";
        ActionOptions options = objectMapper.readValue(json, ActionOptions.class);

        assertFalse(options.isDryRun());
        assertFalse(options.isForce());
        assertFalse(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testJsonDeserialization_partialFields() throws Exception {
        String json = "{\"force\":true,\"recursive\":false}";
        ActionOptions options = objectMapper.readValue(json, ActionOptions.class);

        assertFalse(options.isDryRun());
        assertTrue(options.isForce());
        assertFalse(options.isRecursive());
        assertNull(options.getComment());
    }

    @Test
    void testJsonDeserialization_onlyRecursive() throws Exception {
        String json = "{\"recursive\":true}";
        ActionOptions options = objectMapper.readValue(json, ActionOptions.class);

        assertFalse(options.isDryRun());
        assertFalse(options.isForce());
        assertTrue(options.isRecursive());
        assertNull(options.getComment());
    }

    // ========== toString Test ==========

    @Test
    void testToString() {
        ActionOptions options = new ActionOptions(true, false, true, "test");
        String result = options.toString();

        assertTrue(result.contains("dryRun=true"));
        assertTrue(result.contains("force=false"));
        assertTrue(result.contains("recursive=true"));
        assertTrue(result.contains("comment='test'"));
    }
}

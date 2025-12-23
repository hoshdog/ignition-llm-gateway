package com.inductiveautomation.ignition.gateway.llm.gateway.conversation;

import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamingResponseHandler.
 */
class StreamingResponseHandlerTest {

    @Test
    void testNoOpHandler_doesNotThrow() {
        StreamingResponseHandler handler = StreamingResponseHandler.noOp();

        // All methods should be callable without throwing
        assertDoesNotThrow(() -> handler.onToken("test"));
        assertDoesNotThrow(() -> handler.onToolCallStart(
                LLMToolCall.builder().id("1").name("test").arguments("{}").build()));
        assertDoesNotThrow(() -> handler.onToolCallComplete(
                LLMToolCall.builder().id("1").name("test").arguments("{}").build(),
                ActionResult.success("1", "test", null)));
        assertDoesNotThrow(() -> handler.onComplete(ConversationResponse.builder().build()));
        assertDoesNotThrow(() -> handler.onError(new RuntimeException("test")));
    }

    @Test
    void testCustomHandler_receivesTokens() {
        List<String> tokens = new ArrayList<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {}

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {}

            @Override
            public void onComplete(ConversationResponse response) {}

            @Override
            public void onError(Exception error) {}
        };

        handler.onToken("Hello");
        handler.onToken(" ");
        handler.onToken("World");

        assertEquals(3, tokens.size());
        assertEquals("Hello", tokens.get(0));
        assertEquals(" ", tokens.get(1));
        assertEquals("World", tokens.get(2));
    }

    @Test
    void testCustomHandler_receivesToolCallStart() {
        AtomicReference<LLMToolCall> receivedCall = new AtomicReference<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {}

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {
                receivedCall.set(toolCall);
            }

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {}

            @Override
            public void onComplete(ConversationResponse response) {}

            @Override
            public void onError(Exception error) {}
        };

        LLMToolCall toolCall = LLMToolCall.builder()
                .id("tool-1")
                .name("create_tag")
                .arguments("{\"path\": \"[default]Test\"}")
                .build();

        handler.onToolCallStart(toolCall);

        assertNotNull(receivedCall.get());
        assertEquals("tool-1", receivedCall.get().getId());
        assertEquals("create_tag", receivedCall.get().getName());
    }

    @Test
    void testCustomHandler_receivesToolCallComplete() {
        AtomicReference<ActionResult> receivedResult = new AtomicReference<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {}

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {}

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {
                receivedResult.set(result);
            }

            @Override
            public void onComplete(ConversationResponse response) {}

            @Override
            public void onError(Exception error) {}
        };

        LLMToolCall toolCall = LLMToolCall.builder()
                .id("tool-1")
                .name("create_tag")
                .arguments("{}")
                .build();

        ActionResult result = ActionResult.success("corr-1", "Tag created", null);

        handler.onToolCallComplete(toolCall, result);

        assertNotNull(receivedResult.get());
        assertEquals(ActionResult.Status.SUCCESS, receivedResult.get().getStatus());
    }

    @Test
    void testCustomHandler_receivesComplete() {
        AtomicReference<ConversationResponse> receivedResponse = new AtomicReference<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {}

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {}

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {}

            @Override
            public void onComplete(ConversationResponse response) {
                receivedResponse.set(response);
            }

            @Override
            public void onError(Exception error) {}
        };

        ConversationResponse response = ConversationResponse.builder()
                .message("Test response")
                .tokensUsed(100)
                .build();

        handler.onComplete(response);

        assertNotNull(receivedResponse.get());
        assertEquals("Test response", receivedResponse.get().getMessage());
    }

    @Test
    void testCustomHandler_receivesError() {
        AtomicReference<Exception> receivedError = new AtomicReference<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {}

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {}

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {}

            @Override
            public void onComplete(ConversationResponse response) {}

            @Override
            public void onError(Exception error) {
                receivedError.set(error);
            }
        };

        RuntimeException error = new RuntimeException("Test error");
        handler.onError(error);

        assertNotNull(receivedError.get());
        assertEquals("Test error", receivedError.get().getMessage());
    }

    @Test
    void testFullStreamingSequence() {
        List<String> events = new ArrayList<>();

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onToken(String token) {
                events.add("token:" + token);
            }

            @Override
            public void onToolCallStart(LLMToolCall toolCall) {
                events.add("tool_start:" + toolCall.getName());
            }

            @Override
            public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {
                events.add("tool_complete:" + toolCall.getName() + ":" + result.getStatus());
            }

            @Override
            public void onComplete(ConversationResponse response) {
                events.add("complete");
            }

            @Override
            public void onError(Exception error) {
                events.add("error:" + error.getMessage());
            }
        };

        // Simulate a streaming sequence
        handler.onToken("I'll ");
        handler.onToken("create ");
        handler.onToken("that tag.");

        LLMToolCall toolCall = LLMToolCall.builder()
                .id("1")
                .name("create_tag")
                .arguments("{}")
                .build();

        handler.onToolCallStart(toolCall);
        handler.onToolCallComplete(toolCall, ActionResult.success("1", "Done", null));

        handler.onToken(" Done!");
        handler.onComplete(ConversationResponse.builder().message("I'll create that tag. Done!").build());

        // Verify sequence
        assertEquals(7, events.size());
        assertEquals("token:I'll ", events.get(0));
        assertEquals("token:create ", events.get(1));
        assertEquals("token:that tag.", events.get(2));
        assertEquals("tool_start:create_tag", events.get(3));
        assertEquals("tool_complete:create_tag:SUCCESS", events.get(4));
        assertEquals("token: Done!", events.get(5));
        assertEquals("complete", events.get(6));
    }
}

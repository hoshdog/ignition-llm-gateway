package com.inductiveautomation.ignition.gateway.llm.gateway.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;

import java.util.Collections;
import java.util.List;

/**
 * Response from processing a conversation message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConversationResponse {

    private final String message;
    private final List<ActionResult> actionResults;
    private final boolean requiresConfirmation;
    private final String confirmationPrompt;
    private final int tokensUsed;

    private ConversationResponse(Builder builder) {
        this.message = builder.message;
        this.actionResults = builder.actionResults != null ?
                Collections.unmodifiableList(builder.actionResults) : Collections.emptyList();
        this.requiresConfirmation = builder.requiresConfirmation;
        this.confirmationPrompt = builder.confirmationPrompt;
        this.tokensUsed = builder.tokensUsed;
    }

    /**
     * The LLM's text response to the user.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Results from any actions that were executed.
     */
    public List<ActionResult> getActionResults() {
        return actionResults;
    }

    /**
     * Whether the response requires user confirmation before proceeding.
     */
    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    /**
     * Prompt to show the user if confirmation is required.
     */
    public String getConfirmationPrompt() {
        return confirmationPrompt;
    }

    /**
     * Number of tokens used in this response.
     */
    public int getTokensUsed() {
        return tokensUsed;
    }

    /**
     * Whether any actions were executed.
     */
    public boolean hasActions() {
        return !actionResults.isEmpty();
    }

    /**
     * Whether all actions succeeded.
     */
    public boolean allActionsSucceeded() {
        return actionResults.stream().allMatch(ActionResult::isSuccess);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String message;
        private List<ActionResult> actionResults;
        private boolean requiresConfirmation;
        private String confirmationPrompt;
        private int tokensUsed;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder actionResults(List<ActionResult> actionResults) {
            this.actionResults = actionResults;
            return this;
        }

        public Builder requiresConfirmation(boolean requiresConfirmation) {
            this.requiresConfirmation = requiresConfirmation;
            return this;
        }

        public Builder confirmationPrompt(String confirmationPrompt) {
            this.confirmationPrompt = confirmationPrompt;
            return this;
        }

        public Builder tokensUsed(int tokensUsed) {
            this.tokensUsed = tokensUsed;
            return this;
        }

        public ConversationResponse build() {
            return new ConversationResponse(this);
        }
    }
}

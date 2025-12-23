package com.inductiveautomation.ignition.gateway.llm.gateway.conversation;

import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an ongoing conversation with an LLM.
 * Tracks messages, context, and authentication.
 */
public class Conversation {

    private final String id;
    private final AuthContext authContext;
    private final Instant createdAt;
    private final List<LLMMessage> messages;

    private volatile Instant lastActivity;
    private String currentProject;
    private String currentPath;

    public Conversation(String id, AuthContext authContext) {
        this.id = id;
        this.authContext = authContext;
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
        this.messages = Collections.synchronizedList(new ArrayList<>());
    }

    public String getId() {
        return id;
    }

    public AuthContext getAuthContext() {
        return authContext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant lastActivity) {
        this.lastActivity = lastActivity;
    }

    /**
     * Gets a copy of the message list.
     */
    public List<LLMMessage> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    /**
     * Adds a message to the conversation.
     */
    public void addMessage(LLMMessage message) {
        messages.add(message);
        lastActivity = Instant.now();
    }

    /**
     * Clears all messages from the conversation.
     */
    public void clearMessages() {
        messages.clear();
    }

    /**
     * Gets the number of messages in the conversation.
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Gets the current project context, if set.
     */
    public String getCurrentProject() {
        return currentProject;
    }

    /**
     * Sets the current project context.
     */
    public void setCurrentProject(String currentProject) {
        this.currentProject = currentProject;
    }

    /**
     * Gets the current path context, if set.
     */
    public String getCurrentPath() {
        return currentPath;
    }

    /**
     * Sets the current path context.
     */
    public void setCurrentPath(String currentPath) {
        this.currentPath = currentPath;
    }

    /**
     * Checks if this conversation is expired based on the given timeout.
     */
    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - lastActivity.toEpochMilli() > timeoutMillis;
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id='" + id + '\'' +
                ", userId='" + authContext.getUserId() + '\'' +
                ", messages=" + messages.size() +
                ", lastActivity=" + lastActivity +
                '}';
    }
}
